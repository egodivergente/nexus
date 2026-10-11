"""Private stdio adapter for the original Nexus Kotlin agent."""
import atexit
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time
import uuid


class AgentHost:
    def __init__(self, root, data, read_secret, write_secret):
        self.root = Path(root) / 'agent'
        self.data = Path(data) / 'assistant'
        self.data.mkdir(parents=True, exist_ok=True)
        self.read_secret = read_secret
        self.write_secret = write_secret
        self.process = None
        self.lock = threading.Lock()
        self.pending_lock = threading.Lock()
        self.pending = {}
        self.lines = queue.Queue()
        self.owner = None
        self.native = None
        self.tunnel = None
        atexit.register(self.close)

    def close(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
        if self.tunnel and self.tunnel.poll() is None:
            self.tunnel.terminate()

    def settings(self):
        path = self.data / 'settings.json'
        defaults = {'backend': 'direct', 'region': 'us-east-1',
                    'modelId': 'us.anthropic.claude-sonnet-4-6', 'mode': 'COPILOT',
                    'maxTokens': 4096, 'sendImages': True, 'excludedFamilies': 'veo', 'endpoint': ''}
        if path.exists():
            defaults.update(json.loads(path.read_text(encoding='utf-8')))
        return defaults

    def save_settings(self, args):
        settings = self.settings()
        fields = {'backend', 'region', 'modelId', 'mode', 'maxTokens', 'sendImages',
                  'excludedFamilies', 'endpoint', 'temperature', 'projectId', 'perTurnUsd', 'dailyUsd', 'perTurnCalls'}
        settings.update({k: v for k, v in args.items() if k in fields})
        import re
        from urllib.parse import urlsplit
        endpoint = str(settings['endpoint']).strip().rstrip('/')
        parsed = urlsplit(endpoint)
        if endpoint and not (parsed.scheme == 'https' or
                             (parsed.scheme == 'http' and parsed.hostname in {'127.0.0.1', 'localhost'})):
            raise ValueError('El proxy requiere HTTPS o localhost.')
        if endpoint and (parsed.username or parsed.password or not parsed.hostname):
            raise ValueError('URL del proxy no valida.')
        if not re.fullmatch(r'[a-z]{2}(-[a-z]+)+-\d', str(settings['region'])):
            raise ValueError('Region de AWS no valida.')
        if settings['backend'] not in {'direct', 'proxy'} or settings['mode'] not in {'ASSISTANT', 'COPILOT', 'AGENT'}:
            raise ValueError('Modo no valido.')
        settings['endpoint'] = endpoint
        settings['maxTokens'] = max(256, min(32000, int(settings['maxTokens'])))
        for field in ('perTurnUsd', 'dailyUsd'):
            if field in settings:
                import math
                value = float(settings[field])
                if not math.isfinite(value) or value <= 0:
                    raise ValueError('El límite de consumo debe ser mayor que cero.')
                settings[field] = value
        if 'perTurnCalls' in settings:
            settings['perTurnCalls'] = max(1, min(64, int(settings['perTurnCalls'])))
        secret = self.read_secret(self.data / 'credentials.dpapi')
        for key in ('accessKey', 'secretKey', 'sessionToken', 'token'):
            if key in args:
                if str(args[key]).strip():
                    secret[key] = str(args[key]).strip()
                elif args.get('clearCredentials'):
                    secret.pop(key, None)
        self.write_secret(self.data / 'credentials.dpapi', secret)
        temporary = self.data / 'settings.json.tmp'
        temporary.write_text(json.dumps(settings, ensure_ascii=False), encoding='utf-8')
        os.replace(temporary, self.data / 'settings.json')
        return settings

    def public_settings(self):
        secret = self.read_secret(self.data / 'credentials.dpapi')
        return {**self.settings(), 'configured': [key for key, value in secret.items() if value]}

    def start(self):
        if self.process and self.process.poll() is None:
            return
        java = shutil.which('java')
        if not java:
            java = next(iter(Path('C:/Program Files/Microsoft').glob('jdk-17*/bin/java.exe')), None)
        if not java or not list((self.root / 'runtime').glob('*.jar')):
            raise RuntimeError('Falta Java 17 o el motor del asistente.')
        self.lines = queue.Queue()
        self.process = subprocess.Popen(
            [java, '-Xms32m', '-Xmx256m', '-Dfile.encoding=UTF-8', '-cp',
             str(self.root / 'runtime' / '*'), 'DesktopAgentKt', str(self.root), str(self.data)],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            text=True, encoding='utf-8', bufsize=1,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        process = self.process
        lines = self.lines

        def read_lines():
            for line in process.stdout:
                lines.put(line)
            lines.put(None)
        threading.Thread(target=read_lines, daemon=True).start()
        self.exchange('init', {'settings': self.settings(),
                              'secrets': self.read_secret(self.data / 'credentials.dpapi')})

    def events(self, owner):
        with self.pending_lock:
            if owner != self.owner:
                return []
            return [{'id': k, 'op': v['op'], 'args': v['args']}
                    for k, v in self.pending.items() if not v['event'].is_set()]

    def reply(self, args):
        with self.pending_lock:
            if args.get('owner') != self.owner:
                return False
            item = self.pending.get(args.get('id'))
            if not item or item['event'].is_set():
                return False
            item['reply'] = {'ok': args.get('ok') is True, 'value': args.get('value'),
                             'error': str(args.get('error', 'Operacion fallida'))[:500]}
            item['event'].set()
        return True

    def exchange(self, op, args):
        self.process.stdin.write(json.dumps({'op': op, 'args': args}) + '\n')
        self.process.stdin.flush()
        deadline = time.monotonic() + (600 if op in {'send', 'rewritePrompt'} else 170)
        while True:
            try:
                line = self.lines.get(timeout=max(0.1, deadline - time.monotonic()))
            except queue.Empty:
                self.close()
                raise TimeoutError('El asistente ha agotado el tiempo de espera. Comprueba el historial antes de repetir.')
            if line is None:
                raise RuntimeError('El motor del asistente se ha cerrado.')
            response = json.loads(line)
            if 'callback' not in response:
                if not response.get('ok'):
                    raise RuntimeError(response.get('error', 'Error del asistente'))
                return response.get('value')
            name = response['callback']
            if name in {'native', 'document', 'video', 'thumbnail'} and self.native is not None:
                try:
                    if name == 'native':
                        try:
                            value = self.native.run(response['args'])
                        except Exception as error:
                            value = {'isError': True, 'error': str(error)[:3500]}
                    else:
                        value = self.native.attachment(name, response['args'])
                    reply = {'ok': True, 'value': value}
                except Exception as error:
                    reply = {'ok': False, 'error': str(error)[:1500]}
                self.process.stdin.write(json.dumps(reply) + '\n')
                self.process.stdin.flush()
                continue
            if name not in {'models', 'schema', 'quote', 'submit', 'status', 'cancel', 'history', 'thumbnail'}:
                raise RuntimeError('Operacion del motor no admitida.')
            if name == 'submit' and op != 'confirm':
                submission = response.get('args') or {}
                direct_flow = op == 'send' and (submission.get('ref') or {}).get('providerId') == 'google_flow'
                if not direct_flow:
                    raise RuntimeError('Este proveedor requiere revisar y confirmar la generación.')
                count = float((submission.get('params') or {}).get('count', 1))
                if not count.is_integer() or not 1 <= count <= 20:
                    raise ValueError('Puedes pedir hasta 20 resultados de Flow por encargo.')
            reply = self.browser_reply(name, response['args'], deadline)
            self.process.stdin.write(json.dumps(reply) + '\n')
            self.process.stdin.flush()

    def browser_reply(self, name, args, deadline=None):
        identifier = uuid.uuid4().hex
        item = {'op': name, 'args': args, 'event': threading.Event()}
        with self.pending_lock:
            self.pending[identifier] = item
        answered = item['event'].wait(timeout=max(.1, (deadline or time.monotonic()+120)-time.monotonic()))
        with self.pending_lock:
            self.pending.pop(identifier, None)
        return item.get('reply') if answered else {'ok': False, 'error': 'La ventana no ha respondido.'}

    def browser_callback(self, name, args):
        result = self.browser_reply(name, args)
        if not result.get('ok'):
            raise ValueError(result.get('error', 'La operación falló.'))
        return result.get('value')

    def command(self, args):
        if not self.lock.acquire(blocking=False):
            raise RuntimeError('El asistente esta ocupado.')
        try:
            self.owner = args.get('owner')
            if not isinstance(self.owner, str) or not self.owner:
                raise ValueError('Falta la sesion de la ventana.')
            if args.get('op') in {'send', 'rewritePrompt'}:
                from sol_bridge import ensure
                ensure(self)
            self.start()
            op = args.get('op', 'snapshot')
            body = dict(args.get('args') or {})
            allowed = {'snapshot', 'send', 'new', 'mode', 'project', 'createProject', 'memory',
                       'apply', 'reject', 'discard', 'edit', 'confirm', 'sync', 'reload', 'settings', 'importHistory', 'importReferences', 'renameReference', 'rewritePrompt', 'importAssets', 'deleteLibrary', 'restoreHistory', 'projectNote'}
            if op not in allowed:
                raise ValueError('Operacion no admitida.')
            if op == 'importReferences':
                import re
                files = body.get('files')
                if not isinstance(files, list) or not files:
                    raise ValueError('Selecciona los archivos que quieres importar.')
                for file in files:
                    uri = str(file.get('uri', ''))
                    if not re.fullmatch(r'/media/[a-f0-9]{32}\.(png|jpe?g|webp|gif|mp4|mov|webm|mp3|wav|m4a|pdf|txt|md|csv|html|srt|ogg|flac)', uri):
                        raise ValueError('Referencia local no válida.')
                    from native_operations import resolve_media
                    path = resolve_media(self.data.parent, uri)
                    if not path.is_file():
                        raise ValueError('El archivo importado ya no está disponible.')
            if op == 'settings':
                body = {'settings': self.save_settings(body),
                        'secrets': self.read_secret(self.data / 'credentials.dpapi')}
            elif op in {'mode', 'project'}:
                self.save_settings({'mode': body['mode']} if op == 'mode' else {'projectId': body.get('id', '')})
            if op == 'send':
                body['runtimeSettings'] = self.settings()
            result = self.exchange(op, body)
            if op == 'createProject':
                self.save_settings({'projectId': result.get('projectId', '')})
            return {'result': result, 'settings': self.public_settings()}
        finally:
            self.owner = None
            self.lock.release()
