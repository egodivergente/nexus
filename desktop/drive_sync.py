"""Private Google OAuth connection and background scheduling; project merging lives in Kotlin."""
import base64
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import re
from pathlib import Path
import secrets
import threading
import time
import urllib.parse
import urllib.request
import urllib.error

from native_operations import atomic_json

SCOPE = 'https://www.googleapis.com/auth/drive.file'
AUTH_CALLBACK_TIMEOUT = 30 * 60


class DriveAuthorizationRequired(ValueError):
    """A rejected/revoked authorization, as distinct from a temporary network error."""


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
        self.activity_file = self.root / 'activity.json'
        try:
            activity = json.loads(self.activity_file.read_text('utf-8'))
            self.activity = {key: value for key, value in activity.items()
                             if key in {'lastCompletedAt', 'lastSuccessAt', 'lastMissingMedia'} and isinstance(value, (int, float))}
        except (OSError, ValueError, AttributeError):
            self.activity = {}
        self.conflicts = []
        try:
            folder = str(self.config.get('folderId', ''))
            workspace = (self.root / 'workspaces' / folder).resolve()
            conflicts_file = workspace / 'conflicts.json'
            if folder and workspace.is_relative_to((self.root / 'workspaces').resolve()) and conflicts_file.stat().st_size <= 16_777_216:
                rows = json.loads(conflicts_file.read_text('utf-8'))
                if isinstance(rows, list) and all(isinstance(row, dict) and isinstance(row.get('key'), str)
                        and isinstance(row.get('versions'), list) and all(isinstance(version, dict)
                        and isinstance(version.get('id'), str) for version in row['versions']) for row in rows):
                    self.conflicts = rows
        except (OSError, ValueError):
            pass
        if self.conflicts:
            self.status = {'state': 'conflicts', 'historical': True,
                           'message': 'Hay versiones conservadas del último intercambio para revisar.'}
        elif self.activity.get('lastMissingMedia', 0) > 0:
            self.status = {'state': 'partial', 'historical': True,
                           'message': 'El último intercambio dejó originales pendientes de recuperar.'}
        self.failed_media = {}
        self.original_hashes = {}
        self.closed = threading.Event()
        if start_scheduler:
            threading.Thread(target=self._schedule, daemon=True).start()

    def _schedule(self):
        while not self.closed.wait(30):
            if self.config.get('enabled') and self.host.read_secret(self.secret_file).get('refresh_token'):
                self.run()

    def public(self):
        connected = bool(self.host.read_secret(self.secret_file).get('refresh_token'))
        return {**self.status, **self.activity, 'busy': self.lock.locked(),
                'connected': connected, 'configured': bool(self.config.get('client_id')),
                'enabled': self.config.get('enabled', False), 'folderId': self.config.get('folderId'),
                'missingMedia': self.status.get('missingMedia', self.activity.get('lastMissingMedia', 0)),
                'conflicts': self.status.get('conflicts', self.conflicts)}

    def command(self, args):
        op = args.get('op', 'status')
        if op == 'verifyConnection':
            return self.verify_connection()
        if op == 'diagnostics':
            # Local, session-protected support information; never returns token contents.
            return {'profile': str(self.root.resolve()), 'configurationExists': self.config_file.is_file(),
                    'tokenStoreExists': self.secret_file.is_file(), **self.public()}
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
            self.activity.clear()
            self.conflicts.clear()
            self.activity_file.unlink(missing_ok=True)
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

    def verify_connection(self):
        """Read the effective Google account and folder from this running instance."""
        token = self.token()
        def read(suffix):
            request = urllib.request.Request('https://www.googleapis.com/drive/v3/' + suffix,
                headers={'Authorization': 'Bearer ' + token})
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read(2_000_001)
                if len(raw) > 2_000_000:
                    raise ValueError('La respuesta de Drive supera el límite de diagnóstico.')
                return json.loads(raw)
        try:
            account = read('about?fields=user(emailAddress)').get('user', {}).get('emailAddress')
            folder = self.config.get('folderId')
            result = {'account': account, 'folderId': folder, 'clientId': self.config.get('client_id'),
                      'folderAccessible': False, 'files': 0, 'commits': 0}
            if not folder:
                return result
            try:
                read('files/' + urllib.parse.quote(folder, safe='') + '?fields=id')
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    return result
                raise
            result['folderAccessible'] = True
            page = read('files?' + urllib.parse.urlencode({'q': "'" + folder + "' in parents and trashed = false",
                'fields': 'nextPageToken,files(name)', 'pageSize': 1000}))
            result.update(files=len(page.get('files', [])), commits=sum(f.get('name', '').startswith('commit-') for f in page.get('files', [])),
                          hasMore=bool(page.get('nextPageToken')))
            return result
        except (OSError, ValueError):
            raise ValueError('No se pudo comprobar la conexión efectiva de Google Drive.') from None

    def _token_request(self, fields):
        data = urllib.parse.urlencode(fields).encode()
        request = urllib.request.Request('https://oauth2.googleapis.com/token', data=data,
                                         headers={'Content-Type': 'application/x-www-form-urlencoded'})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            try:
                reason = json.loads(error.read(8192)).get('error')
            except (ValueError, AttributeError, OSError):
                reason = None
            if error.code in {400, 401} and isinstance(reason, str) and reason in {'invalid_grant', 'invalid_client', 'unauthorized_client', 'access_denied'}:
                raise DriveAuthorizationRequired('Google no ha renovado la autorización. Vuelve a conectar Drive.') from None
            raise RuntimeError('Google Drive no está disponible. Se reintentará la conexión.') from None
        except (OSError, ValueError):
            raise RuntimeError('No se pudo conectar con Google Drive. Se reintentará la conexión.') from None
        if not result.get('access_token'):
            raise DriveAuthorizationRequired('Google no devolvió la autorización de Drive.')
        return result

    def token(self):
        with self.auth_lock:
            secret = self.host.read_secret(self.secret_file)
            if secret.get('expires_at', 0) > time.time() + 60:
                return secret['access_token']
            if not secret.get('refresh_token'):
                raise DriveAuthorizationRequired('Conecta Google Drive desde Ajustes.')
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
            deadline = time.monotonic() + AUTH_CALLBACK_TIMEOUT
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
        self.status = {'state': 'queued', 'lastAttemptAt': int(time.time() * 1000)}
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
                    archive=getattr(self.host,'original_archive',None)
                    confirmed_originals={}
                    if archive:
                        captured_assets={a.get('id'):a for a in snapshot['index'].get('assets',[])}
                        with archive.lock:
                            confirmed_originals={r['assetId']:{key:r.get(key) for key in ('sha256','uri','sizeBytes')}
                                for r in archive.records.values() if r.get('state')=='archived' and r.get('registered')
                                and captured_assets.get(r.get('assetId'),{}).get('uri') == r.get('uri')}
                    conversations = self.host.data / 'conversations'
                    chats = [json.loads(p.read_text('utf-8')) for p in conversations.glob('*.json') if not p.name.startswith('sync_')]
                    state_file = self.host.data.parent / 'state.json'
                    if state_file.exists():
                        current = json.loads(state_file.read_text('utf-8')).get('agentConversation')
                        if current:
                            chats.append(current)
                    with self.host.corte.lock:
                        self.captured_montages = {p.parent.name: json.loads(p.read_text('utf-8')) for p in self.host.corte.projects.glob('*/edit.json')}
                finally:
                    self.host.lock.release()
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
                # Resolving historical provider URLs may take longer than the
                # token's remaining lifetime. Renew after that preparation.
                token = self.token()
                if not self.host.lock.acquire(False):
                    self.status = {"state": "waiting", "message": "Se sincronizará cuando termine el turno."}
                    return
                try:
                    result = self.host.exchange('driveSync', {'accessToken': token, 'rootId': self.config.get('folderId', ''),
                                                             'paths': paths, 'resolve': resolution,
                                                             'chats': chats, 'montages': self.captured_montages})
                    self.config['folderId'] = result['rootId']
                    atomic_json(self.config_file, self.config)
                    self.status = result
                    self.conflicts = result.get('conflicts', [])
                    if result.get('state') in {'synced', 'partial', 'conflicts'}:
                        self.activity['lastCompletedAt'] = int(time.time() * 1000)
                        self.activity['lastMissingMedia'] = max(0, int(result.get('missingMedia', 0)))
                        if result['state'] == 'synced':
                            self.activity['lastSuccessAt'] = self.activity['lastCompletedAt']
                        atomic_json(self.activity_file, self.activity)
                finally:
                    self.host.lock.release()
                if result.get('state') in {'synced','partial','conflicts'} and archive and confirmed_originals:
                    try:
                        verified = self.verify_original_blobs(result['rootId'], confirmed_originals, paths)
                        if verified:
                            archive.cloud_verified(verified)
                        if len(verified) < len(confirmed_originals):
                            self.status = {**self.status, 'cloudVerificationPending':True}
                    except (OSError, ValueError, RuntimeError):
                        # The data exchange succeeded; an unavailable checksum must
                        # leave originals pending without claiming a verified copy.
                        self.status = {**self.status, 'cloudVerificationPending':True}
            except Exception as error:
                # Provider URLs, OAuth responses and credentials never become UI diagnostics.
                authorization = isinstance(error, DriveAuthorizationRequired) or str(error) in {
                    'Vuelve a conectar Google Drive.', 'Conecta Google Drive desde Ajustes.'}
                self.status = {'state': 'authorization_required' if authorization else 'retry',
                    'message': 'Vuelve a conectar Google Drive.' if authorization else 'No se ha completado la sincronización. Se reintentará sin borrar datos.'}
            finally:
                self.lock.release()
        threading.Thread(target=work, daemon=True).start()

    def verify_original_blobs(self, root_id, originals, paths):
        """Compare Drive's SHA-256+size with captured, locally verified originals.

        Kotlin SyncBlobs names files blob-<SHA256>.<extension>. A name, upload
        acknowledgement or successful journal exchange alone is not byte proof.
        Only checksum metadata is downloaded; media payloads are never re-read
        from the network for this check.
        """
        if not isinstance(root_id,str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,200}',root_id):
            raise ValueError('Carpeta de originales no válida.')
        expected = {}
        for asset_id,record in originals.items():
            sha = record.get('sha256');uri = record.get('uri');size = record.get('sizeBytes')
            source = paths.get(uri)
            if (not isinstance(sha,str) or not re.fullmatch('[a-f0-9]{64}',sha)
                    or type(size) is not int or size < 0 or not source):
                continue
            path = Path(source).resolve()
            media_root = (self.host.data.parent/'media').resolve()
            # Native adapters may use a verified cache outside media, but an
            # archived original's explicit /media URI must resolve inside media.
            if not path.is_relative_to(media_root):
                continue
            try:
                stat = path.stat()
                if stat.st_size != size or not path.is_file():continue
                key = (str(path),stat.st_size,stat.st_mtime_ns)
                if key not in self.original_hashes:
                    with path.open('rb') as stream:
                        self.original_hashes[key] = hashlib.file_digest(stream,'sha256').hexdigest()
                if self.original_hashes[key] != sha or path.stat().st_mtime_ns != stat.st_mtime_ns:continue
            except OSError:
                continue
            suffix = path.suffix.lower().lstrip('.') or 'bin'
            if not re.fullmatch('[a-z0-9]{1,8}',suffix):continue
            expected[asset_id] = ('blob-'+sha+'.'+suffix,sha,size)
        if not expected:return {}
        remote = {};page = None;seen = set();token = self.token()
        while True:
            params = {'q':"'"+root_id+"' in parents and trashed = false and name contains 'blob-'",
                      'fields':'nextPageToken,files(name,size,sha256Checksum)','pageSize':1000}
            if page:params['pageToken'] = page
            request = urllib.request.Request('https://www.googleapis.com/drive/v3/files?'+urllib.parse.urlencode(params),
                                             headers={'Authorization':'Bearer '+token})
            with urllib.request.urlopen(request,timeout=30) as response:
                raw = response.read(8_388_609)
                if len(raw)>8_388_608:raise ValueError('Respuesta de verificación demasiado grande.')
                payload = json.loads(raw)
            if not isinstance(payload,dict) or not isinstance(payload.get('files'),list):
                raise ValueError('Drive no confirmó los metadatos de los originales.')
            for row in payload['files']:
                if isinstance(row,dict) and isinstance(row.get('name'),str):
                    remote.setdefault(row['name'],[]).append(row)
            page = payload.get('nextPageToken')
            if not page:break
            if not isinstance(page,str) or page in seen or len(seen)>=100:
                raise ValueError('Paginación de verificación no válida.')
            seen.add(page)
        verified = {}
        for asset_id,(name,sha,size) in expected.items():
            candidates = remote.get(name,[])
            if candidates and all(row.get('sha256Checksum') == sha and str(row.get('size')) == str(size)
                                  for row in candidates):
                verified[asset_id] = sha
        return verified

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
