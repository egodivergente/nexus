"""Private Google OAuth connection and background scheduling; project merging lives in Kotlin."""
import base64
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import secrets
import threading
import time
import urllib.parse
import urllib.request

from native_operations import atomic_json

SCOPE = 'https://www.googleapis.com/auth/drive.file'


class DriveSync:
    def __init__(self, host, start_scheduler=True):
        self.host = host
        self.root = host.data / 'sync'
        self.root.mkdir(parents=True, exist_ok=True)
        self.config_file = self.root / 'connection.json'
        self.secret_file = self.root / 'google.dpapi'
        self.config = json.loads(self.config_file.read_text('utf-8')) if self.config_file.exists() else {}
        self.lock = threading.Lock()
        self.auth_lock = threading.Lock()
        self.status = {'state': 'idle'}
        self.failed_media = {}
        self.closed = threading.Event()
        if start_scheduler:
            threading.Thread(target=self._schedule, daemon=True).start()

    def _schedule(self):
        while not self.closed.wait(30):
            if self.config.get('enabled') and self.host.read_secret(self.secret_file).get('refresh_token'):
                self.run()

    def public(self):
        connected = bool(self.host.read_secret(self.secret_file).get('refresh_token'))
        return {**self.status, 'connected': connected, 'configured': bool(self.config.get('client_id')),
                'enabled': self.config.get('enabled', False), 'folderId': self.config.get('folderId'),
                'conflicts': self.status.get('conflicts', [])}

    def command(self, args):
        op = args.get('op', 'status')
        if op == 'configure':
            path = self.host.data.parent / 'media' / args['id']
            if path.parent != self.host.data.parent / 'media' or not path.is_file() or path.stat().st_size > 65536:
                raise ValueError('Selecciona el JSON de cliente de escritorio descargado de Google.')
            client = json.loads(path.read_text('utf-8')).get('installed', {})
            client_id = client.get('client_id', '')
            if not client_id.endswith('.apps.googleusercontent.com') or not client.get('client_secret'):
                raise ValueError('El archivo no es un cliente OAuth de escritorio de Google.')
            if self.config.get('client_id') not in (None, client_id) and self.public()['connected']:
                raise ValueError('Desconecta la cuenta antes de cambiar de aplicación OAuth.')
            self.config['client_id'] = client_id
            secret = self.host.read_secret(self.secret_file)
            secret['client_secret'] = client['client_secret']
            self.host.write_secret(self.secret_file, secret)
            atomic_json(self.config_file, self.config)
            # The picker creates a disposable copy in media; keep only its encrypted form.
            path.unlink()
        elif op == 'connect':
            return {'url': self.begin(), **self.public()}
        elif op == 'disconnect':
            if self.lock.locked():
                raise ValueError('Espera a que termine la sincronización antes de desconectar.')
            secret = self.host.read_secret(self.secret_file)
            self.host.write_secret(self.secret_file, {k: v for k, v in secret.items() if k == 'client_secret'})
            self.config.update(enabled=False, folderId=None)
            atomic_json(self.config_file, self.config)
            self.status = {'state': 'idle'}
        elif op == 'enabled':
            self.config['enabled'] = args.get('enabled') is True
            atomic_json(self.config_file, self.config)
        elif op == 'run':
            self.run()
        elif op == 'resolve':
            self.run({'key': args['key'], 'revision': args['revision']})
        elif op != 'status':
            raise ValueError('Operación de sincronización desconocida.')
        return self.public()

    def _token_request(self, fields):
        data = urllib.parse.urlencode(fields).encode()
        request = urllib.request.Request('https://oauth2.googleapis.com/token', data=data,
                                         headers={'Content-Type': 'application/x-www-form-urlencoded'})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.load(response)
        except Exception:
            raise ValueError('Google no ha renovado la autorización. Vuelve a conectar Drive.') from None
        if not result.get('access_token'):
            raise ValueError('Google no devolvió la autorización de Drive.')
        return result

    def token(self):
        with self.auth_lock:
            secret = self.host.read_secret(self.secret_file)
            if secret.get('expires_at', 0) > time.time() + 60:
                return secret['access_token']
            if not secret.get('refresh_token'):
                raise ValueError('Conecta Google Drive desde Ajustes.')
            result = self._token_request({'grant_type': 'refresh_token', 'refresh_token': secret['refresh_token'],
                                         'client_id': self.config['client_id'], 'client_secret': secret['client_secret']})
            secret.update(result, expires_at=time.time() + result.get('expires_in', 3600))
            self.host.write_secret(self.secret_file, secret)
            return secret['access_token']

    def begin(self):
        if not self.config.get('client_id'):
            raise ValueError('Importa primero el JSON OAuth de escritorio de Nexus.')
        if getattr(self, 'auth_server', None):
            raise ValueError('La conexión de Google ya está abierta. Termina ese paso o espera unos minutos.')
        owner = self
        state = secrets.token_urlsafe(32)
        verifier = secrets.token_urlsafe(64)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')

        class Callback(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_GET(self):
                parsed = urllib.parse.urlsplit(self.path)
                query = urllib.parse.parse_qs(parsed.query)
                if parsed.path != '/google/callback' or not secrets.compare_digest(query.get('state', [''])[0], state):
                    self.send_error(400)
                    return
                try:
                    code = query.get('code', [''])[0]
                    if not code:
                        raise ValueError('Conexión cancelada en Google.')
                    secret = owner.host.read_secret(owner.secret_file)
                    result = owner._token_request({'grant_type': 'authorization_code', 'code': code,
                        'redirect_uri': redirect, 'code_verifier': verifier,
                        'client_id': owner.config['client_id'], 'client_secret': secret['client_secret']})
                    if SCOPE not in result.get('scope', '').split():
                        raise ValueError('No se ha autorizado el acceso a los archivos de Nexus.')
                    secret.update(result, expires_at=time.time() + result.get('expires_in', 3600))
                    if not secret.get('refresh_token'):
                        raise ValueError('Google no concedió acceso en segundo plano. Vuelve a conectar.')
                    owner.host.write_secret(owner.secret_file, secret)
                    owner.config['enabled'] = True
                    atomic_json(owner.config_file, owner.config)
                    owner.status = {'state': 'ready'}
                    message = 'Google Drive conectado. Puedes volver a Nexus.'
                except Exception:
                    owner.status = {'state': 'authorization_required', 'message': 'No se ha completado la autorización de Google.'}
                    message = 'No se ha completado la conexión. Vuelve a Nexus y reintenta.'
                self.send_response(200)
                self.send_header('Content-Type', 'text/plain; charset=utf-8')
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                self.wfile.write(message.encode())
                owner.auth_done.set()

        server = HTTPServer(('127.0.0.1', 0), Callback)
        self.auth_server = server
        self.auth_done = threading.Event()
        redirect = f'http://127.0.0.1:{server.server_port}/google/callback'

        def serve():
            server.timeout = 1
            deadline = time.monotonic() + 300
            try:
                while not self.auth_done.is_set() and time.monotonic() < deadline:
                    server.handle_request()
            finally:
                server.server_close()
                self.auth_server = None

        threading.Thread(target=serve, daemon=True).start()
        return 'https://accounts.google.com/o/oauth2/v2/auth?' + urllib.parse.urlencode({
            'client_id': self.config['client_id'], 'redirect_uri': redirect, 'response_type': 'code',
            'scope': SCOPE, 'access_type': 'offline', 'prompt': 'consent', 'state': state,
            'code_challenge': challenge, 'code_challenge_method': 'S256'})

    def run(self, resolution=None):
        if not self.lock.acquire(False):
            return
        def work():
            try:
                token = self.token()
                if not self.host.lock.acquire(False):
                    self.status = {'state': 'waiting', 'message': 'Se sincronizará cuando termine el turno.'}
                    return
                try:
                    self.status = {'state': 'syncing'}
                    self.host.start()
                    snapshot = self.host.exchange('snapshot', {})
                    conversations = self.host.data / 'conversations'
                    chats = [json.loads(p.read_text('utf-8')) for p in conversations.glob('*.json') if not p.name.startswith('sync_')]
                    state_file = self.host.data.parent / 'state.json'
                    if state_file.exists():
                        current = json.loads(state_file.read_text('utf-8')).get('agentConversation')
                        if current:
                            chats.append(current)
                    with self.host.corte.lock:
                        self.captured_montages = {p.parent.name: json.loads(p.read_text('utf-8')) for p in self.host.corte.projects.glob('*/edit.json')}
                    paths = {}
                    for asset in snapshot['index'].get('assets', []):
                        uri = asset['uri']
                        if uri.startswith('nexus-sync:'):
                            continue
                        if self.failed_media.get(uri, 0) > time.time():
                            continue
                        try:
                            _, path = self.host.native.asset(asset['id'], [{**asset, 'libraryDeleted': False}])
                            paths[uri] = str(path)
                        except Exception:
                            self.failed_media[uri] = time.time() + 3600
                    result = self.host.exchange('driveSync', {'accessToken': token, 'rootId': self.config.get('folderId', ''),
                                                             'paths': paths, 'resolve': resolution,
                                                             'chats': chats, 'montages': self.captured_montages})
                    self.config['folderId'] = result['rootId']
                    atomic_json(self.config_file, self.config)
                    self.status = result
                finally:
                    self.host.lock.release()
            except Exception as error:
                # Provider URLs, OAuth responses and credentials never become UI diagnostics.
                authorization = str(error) in {'Vuelve a conectar Google Drive.', 'Conecta Google Drive desde Ajustes.',
                    'Google no ha renovado la autorización. Vuelve a conectar Drive.'}
                self.status = {'state': 'authorization_required' if authorization else 'retry',
                    'message': 'Vuelve a conectar Google Drive.' if authorization else 'No se ha completado la sincronización. Se reintentará sin borrar datos.'}
            finally:
                self.lock.release()
        threading.Thread(target=work, daemon=True).start()

    def apply_documents(self, rows):
        import re
        with self.host.corte.lock:
            targets = []
            for key, row in rows.items():
                kind, identifier = key.split(':', 1)
                if not re.fullmatch(r'[A-Za-z0-9_-]{1,100}', identifier):
                    raise ValueError('Identificador no válido.')
                if kind == 'montages':
                    path = self.host.corte.projects / identifier / 'edit.json'
                    current = json.loads(path.read_text('utf-8')) if path.exists() else None
                    document = json.loads(json.dumps(row['document']))
                    # Corte renders filesystem URIs; the library serves those same bytes over /media/.
                    from native_operations import resolve_media
                    for media in document.get('project', {}).get('media', []):
                        if str(media.get('uri', '')).startswith('/media/'):
                            media['uri'] = resolve_media(self.host.data.parent, media['uri']).resolve().as_uri()
                    if current == document:
                        continue
                    if current != self.captured_montages.get(identifier):
                        raise ValueError('El montaje cambió durante la transferencia.')
                    self.host.corte.exchange('validate_document', {'project_id': identifier, 'document': document})
                    targets.append((path, document, current))
                elif kind == 'conversations':
                    path = self.host.data / 'conversations' / ('sync_' + identifier + '.json')
                    targets.append((path, {'desktop': True, 'items': row['items'], 'draft': '', 'anchors': {}, 'resultPlans': []}, None))
                else:
                    raise ValueError('Documento sincronizado no admitido.')
            for path, value, previous in targets:
                if previous is not None:
                    atomic_json(self.root / 'montage-backups' / (path.parent.name + '.json'), previous)
                atomic_json(path, value)
        return True
