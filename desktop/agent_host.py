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
from jvm_protocol import decode_line


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
        self.write_lock = threading.Lock()
        self.active_exchange = None
        self.turn_id = None
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
                  'excludedFamilies', 'endpoint', 'temperature', 'projectId', 'perTurnUsd', 'dailyUsd', 'perTurnCalls', 'brainProfile', 'limits'}
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
        if settings['backend'] not in {'direct', 'proxy', 'profile', 'anthropic', 'openai', 'antigravity'} or settings['mode'] not in {'ASSISTANT', 'COPILOT', 'AGENT'}:
            raise ValueError('Modo no valido.')
        settings['endpoint'] = endpoint
        settings['maxTokens'] = max(1, int(settings['maxTokens']))
        for field in ('perTurnUsd', 'dailyUsd'):
            if field in settings:
                import math
                value = float(settings[field])
                if not math.isfinite(value) or value < 0:
                    raise ValueError('El límite de consumo no puede ser negativo.')
                settings[field] = value
        if 'perTurnCalls' in settings:
            settings['perTurnCalls'] = max(0, int(settings['perTurnCalls']))
        secret = self.read_secret(self.data / 'credentials.dpapi')
        profile = settings.get('brainProfile') or {}
        if args.get('brainProfile'):
            profiles = dict(settings.get('brainProfiles') or {})
            profiles[profile['id']] = profile
            settings['brainProfiles'] = profiles
        for key in ('accessKey', 'secretKey', 'sessionToken', 'token', 'apiKey', 'brainKey'):
            if key in args:
                stored_key = ('brain:'+profile['id']) if key == 'brainKey' and profile.get('id') else ('api:'+settings['backend']) if key == 'apiKey' else key
                if str(args[key]).strip():
                    secret[stored_key] = str(args[key]).strip()
                elif args.get('clearCredentials'):
                    secret.pop(stored_key, None)
        self.write_secret(self.data / 'credentials.dpapi', secret)
        temporary = self.data / 'settings.json.tmp'
        temporary.write_text(json.dumps(settings, ensure_ascii=False), encoding='utf-8')
        os.replace(temporary, self.data / 'settings.json')
        return settings

    def public_settings(self):
        secret = self.read_secret(self.data / 'credentials.dpapi')
        return {**self.settings(), 'configured': [key for key, value in secret.items() if value]}

    def runtime_secrets(self):
        secret = dict(self.read_secret(self.data / 'credentials.dpapi'))
        settings = self.settings()
        profile = settings.get('brainProfile') or {}
        secret['apiKey'] = secret.get('api:'+settings['backend'], secret.get('apiKey', ''))
        secret['brainKey'] = secret.get('brain:'+str(profile.get('id', '')), secret.get('brainKey', ''))
        return secret

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
            [java, '-Xms32m', '-Xmx256m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-Xlog:all=warning:stderr', '-Dfile.encoding=UTF-8', '-cp',
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
                              'secrets': self.runtime_secrets()})

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

    def write(self, message):
        with self.write_lock:
            self.process.stdin.write(json.dumps(message)+'\n')
            self.process.stdin.flush()

    def cancel(self, owner, turn_id):
        with self.pending_lock:
            active=self.active_exchange
            if not active or active['op'] not in {'send','rewritePrompt','testBrain'} or active['owner']!=owner or not turn_id or active['turn_id']!=turn_id:
                raise ValueError('Ese turno ya terminó o pertenece a otra ventana.')
            active['cancelled']=True
            self.write({'op':'__cancel','requestId':active['id']})
            for item in self.pending.values():
                item['reply']={'ok':False,'cancelled':True,'error':'Turno detenido'};item['event'].set()
        return {'requested':True,'remoteJobsCancelled':False}

    def exchange(self, op, args):
        request_id=uuid.uuid4().hex
        with self.pending_lock:
            self.active_exchange={'id':request_id,'op':op,'owner':self.owner,'turn_id':self.turn_id,'cancelled':False}
        try:return self._exchange(op,args,request_id)
        finally:
            with self.pending_lock:self.active_exchange=None

    def _exchange(self, op, args, request_id):
        self.write({'op':op,'args':args,'requestId':request_id})
        seconds = 600 if op == 'rewritePrompt' else 170
        if op == 'send':
            settings = self.settings()
            limits = settings.get('limits') or {}
            override = (settings.get('brainProfile') or {}).get('limits') or {} if settings.get('backend') == 'profile' else {}
            seconds = override.get('turnTimeoutSeconds', limits.get('turnTimeoutSeconds', 600))
        deadline = time.monotonic() + seconds + 30 if seconds else None
        while True:
            try:
                line = self.lines.get(timeout=max(0.1, deadline - time.monotonic()) if deadline else None)
            except queue.Empty:
                self.close()
                raise TimeoutError('El asistente ha agotado el tiempo de espera. Comprueba el historial antes de repetir.')
            if line is None:
                raise RuntimeError('El motor del asistente se ha cerrado.')
            try:
                response = decode_line(line)
            except ValueError:
                self.close()
                raise
            if response is None:
                continue
            if 'callback' not in response:
                if not response.get('ok'):
                    raise RuntimeError(response.get('error', 'Error del asistente'))
                return response.get('value')
            name = response['callback']
            if self.active_exchange and self.active_exchange.get('cancelled'):
                self.write({'ok':False,'cancelled':True,'error':'Turno detenido','requestId':request_id})
                continue
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
                self.write({**reply,'requestId':request_id})
                continue
            if name not in {'models', 'schema', 'quote', 'submit', 'status', 'cancel', 'history', 'thumbnail', 'progress', 'stream', 'uiEvent'}:
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
            self.write({**reply,'requestId':request_id})

    def browser_reply(self, name, args, deadline=None):
        identifier = uuid.uuid4().hex
        item = {'op': name, 'args': args, 'event': threading.Event()}
        with self.pending_lock:
            if self.active_exchange and self.active_exchange.get('cancelled'):
                return {'ok':False,'cancelled':True,'error':'Turno detenido'}
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
            self.turn_id = args.get('turn_id')
            if not isinstance(self.owner, str) or not self.owner:
                raise ValueError('Falta la sesion de la ventana.')
            if args.get('op') in {'send', 'rewritePrompt'}:
                from sol_bridge import ensure
                ensure(self)
            self.start()
            op = args.get('op', 'snapshot')
            body = dict(args.get('args') or {})
            allowed = {'snapshot', 'send', 'new', 'mode', 'project', 'createProject', 'memory',
                       'apply', 'reject', 'discard', 'edit', 'confirm', 'sync', 'reload', 'settings', 'importHistory', 'importReferences', 'renameReference', 'rewritePrompt', 'importAssets', 'deleteLibrary', 'restoreHistory', 'projectNote', 'validateBrain', 'discoverBrain', 'testBrain', 'validateLimits'}
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
                if body.get('limits') is not None:
                    check = self.exchange('validateLimits', {'limits': body['limits']})
                    if check.get('problems'):
                        raise ValueError('; '.join(check['problems']))
                if body.get('brainProfile') is not None:
                    check = self.exchange('validateBrain', {'profile': body['brainProfile']})
                    if check.get('problems'):
                        raise ValueError('; '.join(check['problems']))
                body = {'settings': self.save_settings(body),
                        'secrets': self.runtime_secrets()}
            elif op in {'mode', 'project'}:
                self.save_settings({'mode': body['mode']} if op == 'mode' else {'projectId': body.get('id', '')})
            if op == 'send':
                body['runtimeSettings'] = self.settings()
                project_id = body['runtimeSettings'].get('projectId')
                if getattr(self, 'corte', None) and project_id:
                    self.corte.run({'method':'create_project', 'project_id':project_id, 'name':project_id})
                    current=self.corte.run({'method':'read_project', 'project_id':project_id})
                    self.corte.agent_context={**current, 'agent_transaction_id':None}
                    body['text']=str(body.get('text',''))+'\n\nEstado explícito del montaje Corte (datos): '+json.dumps({k:current[k] for k in ('project_id','revision','playhead_us','selection')})
                elif getattr(self, 'corte', None):
                    self.corte.agent_context=None
            result = self.exchange(op, body)
            if op == 'createProject':
                self.save_settings({'projectId': result.get('projectId', '')})
            return {'result': result, 'settings': self.public_settings()}
        finally:
            self.owner = None
            self.turn_id = None
            self.lock.release()
