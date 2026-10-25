"""Private Windows desktop host. Uses the installed signed Python and Edge runtimes."""
import argparse
import base64
import ctypes
from ctypes import wintypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import http.client
import json
import math
import mimetypes
import os
from pathlib import Path
import re
import secrets
import shutil
import socketserver
import subprocess
import tempfile
import threading
import tkinter as tk
from tkinter import filedialog
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parent
UI = ROOT / 'ui'
DATA = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
MEDIA = DATA / 'media'
KEY_FILE = DATA / 'credentials.dpapi'
SESSION_FILE = DATA / 'local-session.json'
STATE_LOCK = threading.RLock()
OFFLINE_QA = False
SCHEMA_CACHE = {}
SCHEMA_LOCK = threading.Lock()
ALLOWED_PROVIDERS = {'atlas_cloud': 'https://api.atlascloud.ai',
                     'runway': 'https://api.dev.runwayml.com',
                     'crun': 'https://api.crun.ai'}
from media_import import MIME, MAX_IMPORT_BYTES, MediaImporter
_IMPORTERS = {}
_IMPORTERS_LOCK = threading.Lock()


def media_importer():
    with _IMPORTERS_LOCK:
        key = str(MEDIA)
        if key not in _IMPORTERS:
            _IMPORTERS[key] = MediaImporter(MEDIA)
        return _IMPORTERS[key]



class Blob(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_byte))]


def windows_keys(path=KEY_FILE):
    if not path.exists():
        return {}
    ciphertext = path.read_bytes()
    raw = ctypes.create_string_buffer(ciphertext)
    source = Blob(len(ciphertext), ctypes.cast(raw, ctypes.POINTER(ctypes.c_byte)))
    plain = Blob()
    ctypes.windll.crypt32.CryptUnprotectData.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p,
        ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    ctypes.windll.crypt32.CryptUnprotectData.restype = wintypes.BOOL
    ctypes.windll.kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    ctypes.windll.kernel32.LocalFree.restype = ctypes.c_void_p
    if not ctypes.windll.crypt32.CryptUnprotectData(ctypes.byref(source), None, None,
                                                     None, None, 0, ctypes.byref(plain)):
        raise RuntimeError('No se pudo abrir el almacén cifrado de Windows.')
    try:
        result = ctypes.string_at(plain.data, plain.size)
        return json.loads(result.decode('utf-8-sig'))
    finally:
        ctypes.windll.kernel32.LocalFree(ctypes.cast(plain.data, ctypes.c_void_p))


def write_secret(path, value):
    raw_bytes = json.dumps(value).encode('utf-8')
    raw = ctypes.create_string_buffer(raw_bytes)
    source = Blob(len(raw_bytes), ctypes.cast(raw, ctypes.POINTER(ctypes.c_byte)))
    encrypted = Blob()
    ctypes.windll.crypt32.CryptProtectData.argtypes = [ctypes.POINTER(Blob), wintypes.LPCWSTR,
        ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    ctypes.windll.crypt32.CryptProtectData.restype = wintypes.BOOL
    ctypes.windll.kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    ctypes.windll.kernel32.LocalFree.restype = ctypes.c_void_p
    if not ctypes.windll.crypt32.CryptProtectData(ctypes.byref(source), None, None, None, None, 0, ctypes.byref(encrypted)):
        raise RuntimeError('No se pudieron cifrar los ajustes del asistente.')
    try:
        temporary = path.with_suffix('.tmp')
        temporary.write_bytes(ctypes.string_at(encrypted.data, encrypted.size))
        os.replace(temporary, path)
    finally:
        ctypes.windll.kernel32.LocalFree(ctypes.cast(encrypted.data, ctypes.c_void_p))


_agent = None
_agent_lock = threading.Lock()


def agent_host():
    global _agent
    with _agent_lock:
        if _agent is None:
            from agent_host import AgentHost
            _agent = AgentHost(ROOT, DATA, windows_keys, write_secret)
            from native_operations import NativeOperations
            _agent.native = NativeOperations(DATA, _agent, read_state, write_state, provider_request, windows_keys)
            from corte_host import CorteHost
            _agent.corte = CorteHost(ROOT, DATA, _agent.native)
            if not OFFLINE_QA:
                from drive_sync import DriveSync
                _agent.drive_sync = DriveSync(_agent)
                from device_continuity import DeviceContinuity
                _agent.device_continuity = DeviceContinuity(_agent)
    return _agent


def read_state():
    with STATE_LOCK:
        path = DATA / 'state.json'
        return json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else None


def merge_jobs(incoming, previous, deleted_ids=()):
    """Keep jobs from other windows and prevent stale terminal-state regression."""
    deleted = set(deleted_ids or ())
    previous_by_id = {job.get('id'): job for job in previous if isinstance(job, dict) and job.get('id') and job.get('id') not in deleted}
    merged = []
    seen = set()
    terminal = {'COMPLETED', 'FAILED', 'CANCELLED'}
    for job in incoming:
        if not isinstance(job, dict) or not job.get('id'):
            raise ValueError('Trabajo sin identificador.')
        identifier = job['id']
        if identifier in seen or identifier in deleted:
            continue
        seen.add(identifier)
        old = previous_by_id.get(identifier)
        current = dict(job)
        if old:
            current = {**old, **current}
            # Completion can recover an uncertain failure, but an old active
            # snapshot must never erase an already confirmed terminal result.
            newer_recovery = job.get('flowRecoveryRevision', 0) > old.get('flowRecoveryRevision', 0)
            stale_recovery = job.get('flowRecoveryRevision', 0) < old.get('flowRecoveryRevision', 0)
            preserve = stale_recovery or (not newer_recovery and (old.get('status') == 'COMPLETED' and job.get('status') != 'COMPLETED'
                        or old.get('status') in terminal and job.get('status') not in terminal))
            if preserve:
                for key in ('status', 'outputs', 'error', 'uncertain', 'remoteId', 'flowRecoveryRevision'):
                    if key in old:
                        current[key] = old[key]
                    else:
                        current.pop(key, None)
            elif old.get('outputs'):
                outputs = list(old['outputs'])
                outputs.extend(output for output in job.get('outputs', []) if output not in outputs)
                def _output_rank(item):
                    u = item if isinstance(item, str) else (item.get('url', '') if isinstance(item, dict) else '')
                    if isinstance(u, str) and u.startswith('/media/'):
                        return 0
                    if isinstance(u, str) and ('X-Tos-Expires=' in u or 'Expires=' in u):
                        return 2
                    return 1
                outputs.sort(key=_output_rank)
                current['outputs'] = outputs
            if current.get('status') == 'COMPLETED':
                current.pop('error', None)
                current['uncertain'] = False
        merged.append(current)
    merged.extend(dict(job) for job in previous if isinstance(job, dict)
                  and job.get('id') and job['id'] not in seen and job['id'] not in deleted)
    return merged


def write_state(value):
    if not isinstance(value, dict) or not isinstance(value.get('jobs'), list):
        raise ValueError('Estado de biblioteca no válido.')
    with STATE_LOCK:
        path = DATA / 'state.json'
        previous = json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else {}
        deleted_ids = list(dict.fromkeys([*(previous.get('deletedJobIds') or []), *(value.get('deletedJobIds') or [])]))[-500:]
        value = {**value, 'jobs': merge_jobs(value['jobs'], previous.get('jobs', []), deleted_ids)}
        if deleted_ids:
            value['deletedJobIds'] = deleted_ids
        temporary = DATA / 'state.json.tmp'
        temporary.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')
        if path.exists():
            shutil.copy2(path, DATA / 'state.json.bak')
        os.replace(temporary, path)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise RuntimeError('Redirección no admitida en una petición con credenciales.')


OPENER = urllib.request.build_opener(NoRedirect)


def public_json(url, headers=None, body=None, method='GET', timeout=120):
    req_headers = {'User-Agent': 'Nexus/1.0.0 (Windows; Desktop)', 'Accept': 'application/json'}
    if headers:
        req_headers.update(headers)
    request = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    try:
        with OPENER.open(request, timeout=timeout) as response:
            raw = response.read(20 * 1024 * 1024 + 1)
            if len(raw) > 20 * 1024 * 1024:
                raise ValueError('Respuesta demasiado grande.')
            if not raw.strip():
                raise ValueError('El proveedor devolvió una respuesta vacía.')
            return json.loads(raw)
    except urllib.error.HTTPError as error:
        raise ValueError(f'El proveedor respondió HTTP {error.code}. Comprueba la configuración.') from None


def allowed(provider, path, method, paid=False):
    if provider not in ALLOWED_PROVIDERS or not path.startswith('/') or path.startswith('//') or '..' in path or ':' in path:
        return False
    if paid:
        if method != 'POST':
            return False
        if provider == 'atlas_cloud':
            return path in {'/api/v1/model/generateImage', '/api/v1/model/generateVideo',
                            '/api/v1/model/generateAudio', '/v1/chat/completions'}
        if provider == 'crun':
            return path in {'/api/v1/client/job/CreateTask', '/api/v1/chat/completions'}
        return bool(re.fullmatch(r'/v1/(text_to_image|image_to_image|text_to_video|image_to_video|video_to_video|video_to_hdr|image_upscale|video_upscale|character_performance|text_to_speech|speech_to_speech|voice_isolation|sound_effect|voice_dubbing)', path))
    if method == 'GET':
        return (provider == 'atlas_cloud' and (path in {'/api/v1/models', '/public/v1/balance'} or path.startswith('/api/v1/model/prediction/'))
                or provider == 'runway' and (path == '/v1/organization' or path.startswith('/v1/tasks/'))
                or provider == 'crun' and (path.startswith('/api/v1/client/job/TaskInfo?') or path.startswith('/api/v1/client/files/upload-url?') or path == '/api/v1/client/account/balance'))
    return method == 'POST' and (provider == 'atlas_cloud' and path == '/api/v1/model/calculate'
                                 or provider == 'crun' and path == '/api/v1/client/job/estimate-credits'
                                 or provider == 'runway' and path == '/v1/uploads')


def provider_request(keys, provider, path, method, body=None, paid=False):
    if not allowed(provider, path, method, paid):
        raise ValueError('Ruta no admitida por Nexus.')
    key = keys.get(provider)
    if not key:
        raise ValueError('No hay una clave configurada para este proveedor.')
    headers = {'Content-Type': 'application/json', 'User-Agent': 'Nexus/1.0.0 (Windows; Desktop)'}
    headers['x-api-key' if provider == 'crun' else 'Authorization'] = key if provider == 'crun' else 'Bearer ' + key
    if provider == 'runway':
        headers['X-Runway-Version'] = '2024-11-06'
    content = json.dumps(body).encode('utf-8') if body is not None else None
    return public_json(ALLOWED_PROVIDERS[provider] + path, headers, content, method)


def select_files():
    app = tk.Tk(); app.withdraw(); app.attributes('-topmost', True)
    try:
        names = app.tk.splitlist(filedialog.askopenfilenames(parent=app, title='Añadir archivos a Nexus'))
    finally:
        app.destroy()
    return [media_importer().copy(name) for name in names]



def media_path(identifier):
    from native_operations import resolve_media
    return resolve_media(DATA, identifier, MEDIA)


def https_stream(url, method, headers, prefix, file_path, suffix=b''):
    uri = urllib.parse.urlsplit(url)
    if uri.scheme != 'https' or not uri.hostname:
        raise ValueError('Destino de subida no seguro.')
    size = len(prefix) + file_path.stat().st_size + len(suffix)
    connection = http.client.HTTPSConnection(uri.hostname, uri.port or 443, timeout=120)
    try:
        connection.putrequest(method, uri.path + ('?' + uri.query if uri.query else ''))
        for key, value in {**headers, 'Content-Length': str(size)}.items():
            connection.putheader(key, str(value))
        connection.endheaders()
        connection.send(prefix)
        with file_path.open('rb') as source:
            while block := source.read(1024 * 1024):
                connection.send(block)
        connection.send(suffix)
        response = connection.getresponse()
        result = response.read(20 * 1024 * 1024 + 1)
        if response.status < 200 or response.status >= 300:
            raise ValueError(f'La subida respondió HTTP {response.status}.')
        if len(result) > 20 * 1024 * 1024:
            raise ValueError('Respuesta de subida demasiado grande.')
        return json.loads(result) if result.strip() else {}
    finally:
        connection.close()


def upload(keys, provider, identifier):
    path = media_path(identifier)
    mime = MIME[path.suffix]
    if provider == 'atlas_cloud':
        boundary = 'nexus' + secrets.token_hex(12)
        prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{identifier}"\r\nContent-Type: {mime}\r\n\r\n').encode()
        result = https_stream(ALLOWED_PROVIDERS[provider] + '/api/v1/model/uploadMedia', 'POST',
                              {'Authorization': 'Bearer ' + keys[provider], 'Content-Type': 'multipart/form-data; boundary=' + boundary},
                              prefix, path, ('\r\n--' + boundary + '--\r\n').encode())
        data = result.get('data', result)
        if isinstance(data, str):
            return data
        for field in ('url', 'download_url', 'file_url', 'image_url'):
            if data.get(field):
                return data[field]
        raise ValueError('Atlas no devolvió una URL de archivo.')
    if provider == 'runway':
        ticket = provider_request(keys, provider, '/v1/uploads', 'POST', {'filename': identifier, 'type': 'ephemeral'})
        boundary = 'nexus' + secrets.token_hex(12)
        pieces = []
        for field, value in ticket.get('fields', {}).items():
            pieces.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"\r\n\r\n{value}\r\n')
        pieces.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{identifier}"\r\nContent-Type: {mime}\r\n\r\n')
        https_stream(ticket['uploadUrl'], 'POST', {'Content-Type': 'multipart/form-data; boundary=' + boundary},
                     ''.join(pieces).encode(), path, ('\r\n--' + boundary + '--\r\n').encode())
        return ticket['runwayUri']
    if provider == 'crun':
        query = urllib.parse.urlencode({'content_type': mime, 'ext': path.suffix})
        result = provider_request(keys, provider, '/api/v1/client/files/upload-url?' + query, 'GET')
        ticket = result.get('data', result)
        https_stream(ticket['presigned_url'], 'PUT', {'Content-Type': mime}, b'', path)
        return ticket['file_url']
    raise ValueError('Proveedor desconocido.')


def save_remote(url):
    uri = urllib.parse.urlsplit(url)
    if uri.scheme != 'https':
        raise ValueError('URL no admitida.')
    app = tk.Tk(); app.withdraw(); app.attributes('-topmost', True)
    try:
        destination = filedialog.asksaveasfilename(parent=app, initialfile=Path(uri.path).name or 'resultado')
    finally:
        app.destroy()
    if not destination:
        return False
    request = urllib.request.Request(url, headers={'User-Agent': 'Nexus/1.0.0 (Windows; Desktop)'})
    # Download beside the destination so replacement is atomic on its volume.
    # Only this temporary file belongs to the operation; never remove the user's file.
    temporary = None
    try:
        with OPENER.open(request, timeout=120) as response:
            descriptor, temporary = tempfile.mkstemp(prefix='.nexus-download-', dir=Path(destination).parent)
            with os.fdopen(descriptor, 'wb') as target:
                shutil.copyfileobj(response, target, 1024 * 1024)
                expected = response.headers.get('Content-Length')
                if expected is not None and target.tell() != int(expected):
                    raise ValueError('La descarga está incompleta.')
                target.flush()
                os.fsync(target.fileno())
        os.replace(temporary, destination)
    finally:
        if temporary is not None:
            Path(temporary).unlink(missing_ok=True)
    return True


def dispatch(keys, operation, args):
    if operation == 'deviceContinuity':
        if OFFLINE_QA:
            return {'enabled': False, 'configured': False, 'status': 'Laboratorio sin conexion'}
        return agent_host().device_continuity.command(args)
    if operation == 'driveSync':
        if OFFLINE_QA:
            return {'state':'unavailable','connected':False,'configured':False,'enabled':False,'conflicts':[]}
        host = agent_host()
        with _agent_lock:
            if not hasattr(host, 'drive_sync'):
                from drive_sync import DriveSync
                host.drive_sync = DriveSync(host)
        return host.drive_sync.command(args)
    if operation == 'agentCancel':
        return agent_host().cancel(args.get('owner'),args.get('turn_id'))
    if operation == 'portableProject' or operation == 'exportProject' and 'assetIds' not in args:
        from portable_project import PortableProject
        return PortableProject(agent_host(),allow_network=not OFFLINE_QA).command(args['op'] if operation=='portableProject' else 'export',args)
    if operation == 'customMedia':
        if OFFLINE_QA and args.get('op') in {'submit', 'status', 'upload', 'test'}:
            raise ValueError('Operaciones de red desactivadas en el perfil de prueba.')
        host = agent_host()
        with _agent_lock:
            if not hasattr(host, 'custom_media'):
                from custom_media import CustomMedia
                host.custom_media = CustomMedia(DATA, host.corte.exchange, windows_keys, write_secret)
        return host.custom_media.command(args['op'], args.get('args', {}))
    if operation == 'readCredential':
        if args.get('provider') in ALLOWED_PROVIDERS:
            return {'value': keys.get(args['provider'], '')}
        if args.get('field') not in {'accessKey', 'secretKey', 'sessionToken', 'token'}:
            raise ValueError('Campo desconocido.')
        return {'value': windows_keys(DATA / 'assistant/credentials.dpapi').get(args['field'], '')}
    if operation == 'saveProviderCredential':
        provider=args.get('provider')
        if provider not in ALLOWED_PROVIDERS:
            raise ValueError('Proveedor desconocido.')
        value=str(args.get('value','')).strip()
        if not value:
            raise ValueError('Introduce la clave del proveedor.')
        keys[provider]=value
        write_secret(KEY_FILE,keys)
        return {'configured':list(keys)}
    if operation in {'archiveChat', 'listChats', 'loadChat', 'renameChat'}:
        from conversation_store import store_for
        store = store_for(DATA / 'assistant')
        if operation == 'archiveChat':
            return store.archive(args)
        if operation == 'loadChat':
            return store.load(args.get('id'))
        if operation == 'renameChat':
            return store.rename(args.get('id'), args.get('title'))
        return store.list(args.get('query', ''))
    if operation == 'exchangeRate':
        if OFFLINE_QA:
            raise ValueError('Laboratorio: consulta de cambio bloqueada.')
        url = 'https://api.frankfurter.dev/v1/latest?from=USD&symbols=EUR'
        with urllib.request.urlopen(url, timeout=20) as response:
            value = json.load(response)
        rate = value.get('rates', {}).get('EUR')
        if not isinstance(rate, (int, float)) or not math.isfinite(rate) or rate <= 0:
            raise ValueError('El servicio de divisas no devolvió un cambio válido.')
        return {'rate': rate, 'date': value['date'], 'source': 'Frankfurter / BCE'}
    if operation == 'exportProject':
        import zipfile
        index = json.loads((DATA / 'assistant/index.json').read_text('utf-8'))
        project = next((p for p in index.get('projects', []) if p['id'] == args.get('projectId')), None)
        if not project:
            raise ValueError('Proyecto desconocido.')
        native = agent_host().native
        assets = [a for a in index.get('assets', []) if a.get('projectId') == project['id'] and not a.get('libraryDeleted') and ('assetIds' not in args or a['id'] in args['assetIds'])]
        identifier = uuid.uuid4().hex + '.zip'
        with zipfile.ZipFile(MEDIA / identifier, 'w', zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('project.json', json.dumps({'project': project, 'assets': assets,
                'characters': [c for c in index.get('characters', []) if c.get('projectId') == project['id']]}, ensure_ascii=False))
            for asset in assets:
                _, source = native.asset(asset['id'], assets)
                archive.write(source, 'media/'+asset['id']+source.suffix)
        return {'url': '/media/'+identifier, 'name': project['name']+'.zip', 'assets': len(assets)}
    if operation == 'corte':
        return agent_host().corte.run(args)
    if operation == 'nativeOperation':
        if OFFLINE_QA and args.get('operation') in {'web_search', 'read_web_page', 'connect_mcp_server', 'call_mcp_tool', 'atlas_get_balance', 'diagnose_flow', 'repair_flow', 'recover_generation', 'run_windows_command', 'open_windows_item'}:
            raise ValueError('Laboratorio: operación de red bloqueada.')
        return agent_host().native.run(args)
    if operation == 'mediaJobs':
        native = agent_host().native
        return sorted([json.loads(path.read_text('utf-8')) for path in native.directory.glob('*.json')], key=lambda item: item['createdAt'], reverse=True)[:100]
    if operation == 'openAlexia':
        adb=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
        result=subprocess.run([str(adb),'-s','100.119.88.105:5555','shell','am','start','-n','com.axel.library/.MainActivity'],capture_output=True,text=True,timeout=15,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        if result.returncode or 'Error' in result.stdout:
            raise ValueError('No se pudo abrir Alexia en el S25. Comprueba su conexión por Tailscale.')
        return {'opened':True,'device':'S25'}
    if operation == 'agent':
        if args.get('op') == 'reload':
            with SCHEMA_LOCK:
                SCHEMA_CACHE.clear()
        return agent_host().command(args)
    if operation == 'agentEvents':
        return agent_host().events(args.get('owner'))
    if operation == 'agentReply':
        from flow_agent import reply
        return reply(agent_host(), args, read_state())
    if operation == 'init':
        import flow_provider
        flow_models = flow_provider.catalog()
        if flow_models and not OFFLINE_QA:
            flow_provider.automation_initialize(DATA)
        return {'qa': OFFLINE_QA, 'browserImport': True, 'maxImportBytes': MAX_IMPORT_BYTES, 'configured': list(keys) + (['google_flow'] if flow_models else []), 'flowModels':flow_models, 'state': read_state(),
                'version': '2026.10.26-desktop-modern.1', 'androidSource': '8ceb3aa7ab44be162d7fc1c8f82a047d33d71ce6',
                'desktopParity': 'integrated-20261026', 'reviewedAndroidSource': '3b6082cecde9fe37c3bc4b642f7c9114dc62bdaf',
                'platform': os.environ.get('COMPUTERNAME', 'Windows') + ' · Windows x64', 'localMedia': True}
    if operation in ('flowConnection', 'flowConnect'):
        if OFFLINE_QA:
            return {'status': 'unavailable', 'checked_at': 0}
        import flow_connection
        return flow_connection.snapshot(refresh=args.get('refresh') is True, interactive=operation=='flowConnect')
    if operation in ('flowQuote', 'flowSubmit', 'flowStatus', 'flowAssetOptions', 'flowRecover', 'flowAutomation', 'flowPolicy', 'flowCancel'):
        if OFFLINE_QA:
            raise ValueError('Laboratorio: Flow bloqueado.')
        import flow_provider
        if operation == 'flowAutomation':
            return flow_provider.automation_overview(DATA)
        if operation == 'flowPolicy':
            return flow_provider.automation_configure(DATA, args)
        if operation == 'flowCancel':
            return flow_provider.automation_cancel(DATA, args.get('request_id', ''))
        if operation == 'flowRecover':
            return flow_provider.recover(DATA, args.get('request_id', ''))
        if operation == 'flowAssetOptions':
            return flow_provider.asset_options(args.get('source_request_id', ''))
        if operation == 'flowQuote':
            return flow_provider.quote(args, DATA)
        if operation == 'flowSubmit':
            return flow_provider.submit(DATA, args)
        return flow_provider.status(DATA, args.get('request_id', ''))
    if operation == 'save':
        write_state(args); return True
    if operation == 'pick':
        return select_files()
    if operation == 'importBegin':
        return media_importer().begin(args.get('name', ''), args.get('size'))
    if operation == 'importChunk':
        return media_importer().append(args.get('import_id'), args.get('offset'), args.get('content'))
    if operation == 'importFinish':
        return media_importer().finish(args.get('import_id'))
    if operation == 'importAbort':
        return media_importer().abort(args.get('import_id'))
    if operation == 'importMedia':
        name = Path(str(args.get('name', 'image.png'))).name
        ext = Path(name).suffix.lower()
        if ext not in MIME or MIME[ext] != args.get('mime'):
            raise ValueError('Formato de archivo no admitido.')
        content = args.get('content', '')
        if not isinstance(content, str) or len(content) > 16 * 1024 * 1024:
            raise ValueError('El archivo supera los 12 MB.')
        try:
            payload = base64.b64decode(content, validate=True)
        except (ValueError, TypeError):
            raise ValueError('Archivo no valido.')
        if not payload or len(payload) > 12 * 1024 * 1024:
            raise ValueError('Archivo vacio o demasiado grande.')
        identifier = uuid.uuid4().hex + ext
        MEDIA.mkdir(parents=True, exist_ok=True)
        (MEDIA / identifier).write_bytes(payload)
        return {'id': identifier, 'name': name, 'size': len(payload),
                'mime': MIME[ext], 'url': '/media/' + identifier}
    if operation == 'schema':
        if OFFLINE_QA:
            fixture = json.loads((ROOT / 'tools' / 'agent-qa-schema.json').read_text(encoding='utf-8'))
            return fixture
        uri = urllib.parse.urlsplit(args['url'])
        if uri.scheme != 'https' or uri.hostname != 'static.atlascloud.ai':
            raise ValueError('El esquema no procede de Atlas.')
        with SCHEMA_LOCK:
            cached = SCHEMA_CACHE.get(args['url'])
            if cached and cached[0] > time.monotonic() and not args.get('refresh'):
                return json.loads(cached[1])
        value = public_json(args['url'], timeout=15)
        with SCHEMA_LOCK:
            if len(SCHEMA_CACHE) >= 64:
                SCHEMA_CACHE.pop(next(iter(SCHEMA_CACHE)))
            SCHEMA_CACHE[args['url']] = (time.monotonic() + 300, json.dumps(value))
        return value
    if operation == 'request':
        if OFFLINE_QA:
            raise ValueError('Laboratorio: red bloqueada.')
        return provider_request(keys, args['provider'], args['path'], args['method'], args.get('body'))
    if operation == 'generate':
        if OFFLINE_QA:
            raise ValueError('Laboratorio: generaciones bloqueadas.')
        body = args.get('body') or {}
        if not isinstance(body, dict):
            raise ValueError('Solicitud de generación no válida.')
        for field in ('model', 'model_id', 'modelId'):
            if re.search(r'nano[_ -]?banana.*lite|nano[_ -]?lite', str(body.get(field, '')), re.I):
                raise ValueError('Nano Banana Lite está desactivado. Elige otro modelo.')
        cost = args.get('cost')
        unavailable = cost is None and args.get('estimate_status') == 'unavailable'
        if not unavailable and (isinstance(cost, bool) or not isinstance(cost, (int, float)) or not math.isfinite(cost) or cost < 0):
            raise ValueError('Estimación no válida.')
        target_id = args.get('job_id')
        if not isinstance(target_id, str) or not target_id.strip():
            raise ValueError('Falta job_id en la solicitud de generación.')
        result = provider_request(keys, args['provider'], args['path'], 'POST', args['body'], paid=True)
        if isinstance(result, dict):
            payload = result.get('data') if isinstance(result.get('data'), dict) else result
            remote_id = payload.get('id') or payload.get('task_id')
            if remote_id:
                try:
                    with STATE_LOCK:
                        current_state = read_state()
                        if isinstance(current_state, dict) and isinstance(current_state.get('jobs'), list):
                            for item in current_state['jobs']:
                                if isinstance(item, dict) and item.get('id') == target_id:
                                    item['remoteId'] = str(remote_id)
                                    if item.get('status') == 'SENDING':
                                        item['status'] = 'PROCESSING'
                                    write_state(current_state)
                                    break
                except Exception:
                    pass
        return result
    if operation == 'upload':
        if OFFLINE_QA:
            raise ValueError('Laboratorio: subidas bloqueadas.')
        return upload(keys, args['provider'], args['id'])
    if operation == 'download':
        return save_remote(args['url'])
    if operation == 'openData':
        os.startfile(DATA); return True
    raise ValueError('Operación no admitida.')


class Handler(BaseHTTPRequestHandler):
    def handle(self):
        try:
            super().handle()
        except ConnectionError:
            # Closing a tab or cancelling playback can close an in-flight response.
            # The dispatched operation is not retried.
            pass

    server_version = 'NexusLocal/1.0'

    def log_message(self, *args):
        pass

    def checked(self):
        return self.headers.get('Host') == f'127.0.0.1:{self.server.server_port}'

    def do_GET(self):
        if not self.checked():
            self.send_error(403); return
        path = urllib.parse.urlsplit(self.path).path
        if path == '/health':
            self.respond(b'{"app":"Nexus Studio"}', 'application/json'); return
        if path == '/favicon.ico':
            self.respond((ROOT / 'nexus.ico').read_bytes(), 'image/x-icon'); return
        if path == '/session':
            self.respond(json.dumps({'token': self.server.token}).encode(), 'application/json'); return
        if path.startswith('/media/'):
            try:
                file_path = media_path(path.removeprefix('/media/'))
                self.send_media(file_path); return
            except ValueError:
                self.send_error(404); return
        if path == '/':
            path = '/index.html'
        if not re.fullmatch(r'/[a-zA-Z0-9_.\-/]+', path) or '..' in path:
            self.send_error(404); return
        file_path = (UI / path[1:]).resolve()
        if not file_path.is_relative_to(UI.resolve()) or not file_path.is_file() or file_path.name.startswith('qa'):
            self.send_error(404); return
        self.respond(file_path.read_bytes(), mimetypes.guess_type(file_path)[0] or 'application/octet-stream')

    def send_media(self, path):
        size = path.stat().st_size
        first, last = 0, size - 1
        wanted = self.headers.get('Range', '')
        if wanted:
            match = re.fullmatch(r'bytes=(\d+)-(\d*)', wanted)
            if not match:
                self.send_error(416); return
            first = int(match.group(1))
            last = min(int(match.group(2)), size - 1) if match.group(2) else min(first + 4 * 1024 * 1024 - 1, size - 1)
            if first >= size or last < first:
                self.send_error(416); return
        self.send_response(206 if wanted else 200)
        self.send_header('Content-Type', 'text/plain; charset=utf-8' if path.suffix == '.html' else MIME[path.suffix])
        self.send_header('Content-Length', str(max(0, last - first + 1)))
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Cache-Control', 'private, no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        if wanted:
            self.send_header('Content-Range', f'bytes {first}-{last}/{size}')
        self.end_headers()
        with path.open('rb') as source:
            source.seek(first)
            remaining = last - first + 1
            while remaining > 0:
                chunk = source.read(min(1024 * 1024, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)

    def respond(self, content, mime, status=200):
        self.send_response(status)
        self.send_header('Content-Type', mime + ('; charset=utf-8' if mime.startswith('text/') or mime == 'application/json' else ''))
        self.send_header('Content-Length', str(len(content)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('X-Frame-Options', 'DENY')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.end_headers(); self.wfile.write(content)

    def do_POST(self):
        origin = f'http://127.0.0.1:{self.server.server_port}'
        if (not self.checked() or self.path != '/rpc' or self.headers.get('Origin') != origin
                or not secrets.compare_digest(self.headers.get('X-Nexus-Session', ''), self.server.token)):
            self.send_error(403); return
        try:
            length = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            self.send_error(400); return
        if length < 2 or length > 20 * 1024 * 1024:
            self.send_error(413); return
        message = {}
        try:
            payload = json.loads(self.rfile.read(length))
            if (not isinstance(payload, dict) or not isinstance(payload.get('op'), str)
                    or not isinstance(payload.get('args', {}), dict)):
                self.send_error(400); return
            message = payload
            if not OFFLINE_QA:
                self.server.keys = windows_keys(KEY_FILE)
            value = dispatch(self.server.keys, message.get('op'), message.get('args') or {})
            answer = {'id': message.get('id'), 'ok': True, 'value': value}
        except Exception as error:
            reason = str(error) if isinstance(error, (ValueError, RuntimeError, KeyError)) else 'No se pudo completar la operación.'
            for key in self.server.keys.values():
                if key:
                    reason = reason.replace(key, '[oculto]')
            answer = {'id': message.get('id'), 'ok': False, 'error': reason}
        self.respond(json.dumps(answer, ensure_ascii=False).encode(), 'application/json')


def edge_path():
    for folder in (Path(os.environ.get('PROGRAMFILES(X86)', '')) / 'Microsoft/Edge/Application',
                   Path(os.environ.get('PROGRAMFILES', '')) / 'Microsoft/Edge/Application'):
        candidate = folder / 'msedge.exe'
        if candidate.is_file():
            return str(candidate)
    return shutil.which('msedge')


def is_same_installation_root(previous, current_root=ROOT):
    try:
        recorded = previous.get('root')
        if not recorded:
            return False
        return Path(str(recorded)).resolve() == Path(current_root).resolve()
    except Exception:
        return False


def _stop_foreign_host(previous):
    try:
        prev_pid = int(previous.get('pid') or 0)
    except (TypeError, ValueError):
        return
    if prev_pid <= 0 or prev_pid == os.getpid():
        return
    cmd = (
        f'$p = Get-CimInstance Win32_Process -Filter "ProcessId = {prev_pid}" -ErrorAction SilentlyContinue; '
        f'if ($p -and ($p.CommandLine -like "*local_host.py*")) {{ Stop-Process -Id {prev_pid} -Force -ErrorAction SilentlyContinue }}'
    )
    subprocess.run(
        ['powershell', '-NoProfile', '-Command', cmd],
        capture_output=True,
        timeout=10,
        creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0),
    )


def main():
    global DATA, MEDIA, KEY_FILE, SESSION_FILE, OFFLINE_QA
    parser = argparse.ArgumentParser()
    parser.add_argument('--headless', action='store_true', help='Start without opening Edge; for local smoke tests.')
    parser.add_argument('--port', type=int, default=0, help='Optional existing loopback port for a host update.')
    parser.add_argument('--offline-qa', action='store_true')
    parser.add_argument('--data-dir')
    options = parser.parse_args()
    OFFLINE_QA = options.offline_qa
    if options.data_dir:
        DATA = Path(options.data_dir).resolve()
        MEDIA = DATA / 'media'
        KEY_FILE = DATA / 'credentials.dpapi'
        SESSION_FILE = DATA / 'local-session.json'
    if OFFLINE_QA and not options.data_dir:
        raise ValueError('Las pruebas necesitan un directorio de datos aislado.')
    DATA.mkdir(parents=True, exist_ok=True); MEDIA.mkdir(exist_ok=True)
    kernel = ctypes.windll.kernel32
    kernel.CreateMutexW.argtypes = [ctypes.c_void_p, wintypes.BOOL, wintypes.LPCWSTR]
    kernel.CreateMutexW.restype = ctypes.c_void_p
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    kernel.ReleaseMutex.argtypes = [ctypes.c_void_p]
    mutex_name = 'Local\\NexusStudioPrivateHost'
    if options.data_dir:
        import hashlib
        mutex_name += hashlib.sha256(str(DATA).encode()).hexdigest()[:16]
    mutex = kernel.CreateMutexW(None, True, mutex_name)
    if not mutex:
        raise RuntimeError('No se pudo reservar la instancia local de Nexus.')
    if kernel.GetLastError() == 183:
        kernel.CloseHandle(mutex)
        mutex = None
        if not options.headless:
            previous = {}
            try:
                previous = json.loads(SESSION_FILE.read_text(encoding='utf-8'))
            except (OSError, ValueError):
                previous = {}
            if is_same_installation_root(previous, ROOT):
                for _ in range(20):
                    try:
                        previous = json.loads(SESSION_FILE.read_text(encoding='utf-8'))
                        previous_url = f"http://127.0.0.1:{int(previous['port'])}/"
                        with urllib.request.urlopen(previous_url + 'health', timeout=0.5) as answer:
                            if answer.read() == b'{"app":"Nexus Studio"}':
                                open_window(previous_url)
                                return
                    except (OSError, ValueError, KeyError, urllib.error.URLError):
                        time.sleep(0.15)
            else:
                if not options.port and previous.get('port'):
                    try:
                        options.port = int(previous['port'])
                    except (TypeError, ValueError):
                        pass
                _stop_foreign_host(previous)
                for _ in range(30):
                    time.sleep(0.15)
                    candidate = kernel.CreateMutexW(None, True, mutex_name)
                    if candidate and kernel.GetLastError() != 183:
                        mutex = candidate
                        break
                    if candidate:
                        kernel.CloseHandle(candidate)
        if not mutex:
            raise RuntimeError('Nexus ya se está iniciando. Vuelve a abrir el acceso directo.')
    try:
        run_server(options)
    finally:
        kernel.ReleaseMutex(mutex)
        kernel.CloseHandle(mutex)


class LoopbackHTTPServer(ThreadingHTTPServer):
    # Static UI modules and concurrent agent/editor RPCs share this loopback listener.
    request_queue_size = 64
    def server_bind(self):
        # The address is explicitly loopback; startup does not need reverse DNS.
        socketserver.TCPServer.server_bind(self)
        self.server_name = 'localhost'
        self.server_port = self.server_address[1]


def run_server(options):
    keys = {} if OFFLINE_QA else windows_keys(KEY_FILE)
    try:
        server = LoopbackHTTPServer(('127.0.0.1', options.port), Handler)
    except OSError:
        server = LoopbackHTTPServer(('127.0.0.1', 0), Handler)
    server.token = secrets.token_urlsafe(32)
    server.keys = keys
    if not OFFLINE_QA:
        agent_host()  # Resume configured background sync even before opening Agent/Settings.
    url = f'http://127.0.0.1:{server.server_port}/'
    SESSION_FILE.write_text(
        json.dumps({'pid': os.getpid(), 'port': server.server_port, 'root': str(ROOT.resolve()), 'version': '2026.10.26-desktop-modern.1'}),
        encoding='utf-8',
    )
    if options.headless:
        print(json.dumps({'url': url, 'configured': list(keys), 'paidCalls': 0}), flush=True)
    else:
        open_window(url)
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        if _agent is not None:
            _agent.close()
        server.server_close()
        if SESSION_FILE.exists():
            current = json.loads(SESSION_FILE.read_text(encoding='utf-8'))
            if current.get('pid') == os.getpid():
                SESSION_FILE.unlink()


def open_window(url):
    edge = edge_path()
    if edge:
        profile = DATA / 'EdgeProfile'
        profile.mkdir(parents=True, exist_ok=True)
        subprocess.Popen([edge, '--app=' + url, '--user-data-dir=' + str(profile),
                          '--no-first-run', '--no-default-browser-check', '--force-dark-mode',
                          '--window-size=1280,840'],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        os.startfile(url)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        DATA.mkdir(parents=True, exist_ok=True)
        (DATA / 'local-startup-error.txt').write_text(type(error).__name__ + ': ' + str(error)[:300], encoding='utf-8')
        ctypes.windll.user32.MessageBoxW(None, 'Nexus no pudo iniciarse. Consulta local-startup-error.txt en la carpeta de datos.', 'Nexus Studio', 0)
