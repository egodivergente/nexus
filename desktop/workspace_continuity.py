"""Immutable, explicit work handoffs; receiving a snapshot never applies or submits it."""
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import threading
import time
import uuid
import urllib.parse

from native_operations import atomic_json, resolve_media

SCHEMA = 1
CAPABILITY = 'workspace-v1'
VIEWS = {'canvas', 'assistant', 'library', 'editor'}
SECRET_KEYS = {'apikey','accesstoken','refreshtoken','secretkey','secret','password','authorization','credentials','credential','token'}
SUFFIXES = {'png','jpg','jpeg','webp','gif','avif','bmp','mp4','webm','mov','m4v','mp3','wav','m4a','ogg','flac','opus','pdf','txt','md','csv','srt','json','bin'}


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8')


def fingerprint(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for part in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(part)
    return digest.hexdigest()


def text(value, maximum, name, empty=True):
    if not isinstance(value, str) or len(value) > maximum or (not empty and not value.strip()) or '\x00' in value:
        raise ValueError('Campo de trabajo no válido: ' + name)
    return value


def identifier(value, name, optional=False):
    if optional and (value is None or value == ''):
        return None
    value = text(value, 200, name, False)
    if not re.fullmatch(r'[A-Za-z0-9_:.\-]+', value):
        raise ValueError('Identificador de trabajo no válido: ' + name)
    return value


def inert_values(value, depth=0):
    if depth > 6:
        raise ValueError('Los parámetros del borrador son demasiado complejos.')
    if value is None or isinstance(value, bool):
        return value
    if isinstance(value, (float, int)) and not isinstance(value, bool):
        if not math.isfinite(value):
            raise ValueError('Parámetro numérico no válido.')
        return value
    if isinstance(value, str):
        if value.startswith(('https://','http://')):
            parsed = urllib.parse.urlsplit(value)
            query_keys = {re.sub('[^a-z0-9]', '', key.lower()) for key in urllib.parse.parse_qs(parsed.query)}
            if parsed.username or parsed.password or query_keys & SECRET_KEYS:
                raise ValueError('Las credenciales de una URL no forman parte de un trabajo compartido.')
        return text(value, 16000, 'parámetro')
    if isinstance(value, list) and len(value) <= 128:
        return [inert_values(v, depth + 1) for v in value]
    if isinstance(value, dict) and len(value) <= 128:
        result = {}
        for key, item in value.items():
            key = text(key, 100, 'nombre de parámetro', False)
            if re.sub('[^a-z0-9]', '', key.lower()) in SECRET_KEYS:
                raise ValueError('Las credenciales no forman parte de un trabajo compartido.')
            result[key] = inert_values(item, depth + 1)
        return result
    raise ValueError('Parámetros del borrador no válidos.')


class WorkspaceContinuity:
    def __init__(self, data, read_state, write_state=None, project_exists=None):
        self.data = Path(data)
        self.read_state, self.write_state = read_state, write_state
        self.android_handoff = None
        self.project_exists = project_exists
        self.root = self.data / 'workspace-continuity'
        self.snapshots = self.root / 'snapshots'
        self.blobs = self.root / 'blobs'
        self.lock = threading.RLock()
        self.verified = {}
        for directory in (self.snapshots, self.blobs):
            directory.mkdir(parents=True, exist_ok=True)
        identity = self._read(self.root / 'device.json', {})
        try:
            self.device_id = str(uuid.UUID(identity['deviceId']))
        except (KeyError, ValueError, TypeError):
            self.device_id = str(uuid.uuid4())
            atomic_json(self.root / 'device.json', {'deviceId': self.device_id})
        self.device_name = os.environ.get('COMPUTERNAME', 'Windows')[:80]
        self.peers = self._read(self.root / 'peers.json', {})
        if not isinstance(self.peers, dict):
            self.peers = {}

    @staticmethod
    def _read(path, default):
        try:
            return json.loads(path.read_text('utf-8-sig'))
        except (OSError, ValueError):
            return default

    def _index(self):
        index = self._read(self.data / 'assistant' / 'index.json', {})
        return index if isinstance(index, dict) else {}

    def validate(self, snapshot):
        if not isinstance(snapshot, dict) or set(snapshot) != {'schemaVersion','id','deviceId','deviceName','createdAt','projectId','title','view','canvas','editor','library','shared'}:
            raise ValueError('Formato de trabajo no compatible.')
        if snapshot['schemaVersion'] != SCHEMA or type(snapshot['shared']) is not bool:
            raise ValueError('Versión de trabajo no compatible.')
        result = {'schemaVersion': SCHEMA}
        for key in ('id', 'deviceId'):
            try:
                result[key] = str(uuid.UUID(snapshot[key]))
            except (ValueError, TypeError, AttributeError):
                raise ValueError('Identidad de trabajo no válida.') from None
        result['deviceName'] = text(snapshot['deviceName'], 80, 'dispositivo')
        if type(snapshot['createdAt']) is not int or not 0 <= snapshot['createdAt'] <= 253402300799000:
            raise ValueError('Fecha de trabajo no válida.')
        result['createdAt'] = snapshot['createdAt']
        result['projectId'] = identifier(snapshot['projectId'], 'proyecto', optional=True)
        result['title'] = text(snapshot['title'], 160, 'título', False)
        if not isinstance(snapshot['view'], str) or snapshot['view'] not in VIEWS:
            raise ValueError('Pantalla de trabajo no compatible.')
        result['view'] = snapshot['view']
        canvas = snapshot['canvas']
        if not isinstance(canvas, dict) or set(canvas) != {'modelId','providerId','prompt','values','references'}:
            raise ValueError('Borrador de trabajo no válido.')
        references = canvas['references']
        if not isinstance(references, list) or len(references) > 64:
            raise ValueError('Referencias de trabajo no válidas.')
        normalized = []
        for reference in references:
            if not isinstance(reference, dict) or set(reference) - {'assetId','sha256','name','mime','field','suffix','size','missing'}:
                raise ValueError('Referencia de trabajo no válida.')
            row = {key: text(reference.get(key, ''), limit, key) for key, limit in [('name',240),('mime',120),('field',100)]}
            row['assetId'] = identifier(reference.get('assetId'), 'archivo', optional=True)
            sha = reference.get('sha256')
            if sha is not None and not isinstance(sha, str) or sha is not None and not re.fullmatch('[a-f0-9]{64}', sha):
                raise ValueError('Huella de referencia no válida.')
            suffix = reference.get('suffix', 'bin')
            if not isinstance(suffix, str) or suffix not in SUFFIXES:
                raise ValueError('Formato de referencia no compatible.')
            size = reference.get('size', 0)
            if type(size) is not int or size < 0:
                raise ValueError('Tamaño de referencia no válido.')
            if type(reference.get('missing', False)) is not bool:
                raise ValueError('Estado de referencia no válido.')
            row.update(sha256=sha, suffix=suffix, size=size, missing=reference.get('missing', False))
            normalized.append(row)
        values = inert_values(canvas['values'])
        if not isinstance(values, dict):
            raise ValueError('Parámetros de trabajo no válidos.')
        result['canvas'] = {'modelId': text(canvas['modelId'], 400, 'modelo'),
                            'providerId': text(canvas['providerId'], 100, 'proveedor'),
                            'prompt': text(canvas['prompt'], 16000, 'prompt'), 'values': values,
                            'references': normalized}
        editor = snapshot['editor']
        if not isinstance(editor, dict) or set(editor) - {'projectId','playheadUs','zoom'}:
            raise ValueError('Punto de montaje no válido.')
        result['editor'] = {'projectId': identifier(editor.get('projectId'), 'montaje', optional=True)}
        for key in ('playheadUs', 'zoom'):
            value = editor.get(key, 0)
            if not isinstance(value, (float, int)) or isinstance(value, bool) or not math.isfinite(value) or value < 0:
                raise ValueError('Posición de montaje no válida.')
            result['editor'][key] = value
        library = inert_values(snapshot['library'])
        if not isinstance(library, dict) or set(library) - {'collection','favorites','sort','group','limit','viewer','query','kind','project','folder','source','role','sceneId','shotId'}:
            raise ValueError('Estado de biblioteca no válido.')
        for key in ('project','folder','sceneId','shotId'):
            if key in library:
                value=text(library[key],200,'carpeta de biblioteca')
                if value and not re.fullmatch(r'[A-Za-z0-9_:.\-]+',value):
                    raise ValueError('Carpeta de biblioteca no válida.')
        for key,allowed in (('source',{'','GENERATED','IMPORTED','EDITED','SHARED'}),
                            ('role',{'','KEYFRAME','REFERENCE','OUTPUT','FINAL','SOURCE','OTHER'})):
            if key in library and (not isinstance(library[key],str) or library[key] not in allowed):
                raise ValueError('Clasificación de biblioteca no válida.')
        result['library'] = library
        result['shared'] = snapshot['shared']
        if len(encoded(result)) > 1_000_000:
            raise ValueError('El trabajo supera el tamaño admitido.')
        return result

    def _capture_references(self, references):
        if not isinstance(references, list) or len(references) > 64:
            raise ValueError('Referencias de trabajo no válidas.')
        assets = {row['id']: row for row in self._index().get('assets', []) if isinstance(row, dict) and isinstance(row.get('id'), str)}
        result = []
        for reference in references:
            if not isinstance(reference, dict):
                raise ValueError('Referencia de borrador no válida.')
            asset_id = reference.get('assetId')
            uri = reference.get('url', reference.get('uri', ''))
            if not asset_id:
                asset_id = next((a['id'] for a in assets.values() if a.get('uri') == uri), None)
            source = None
            for candidate in (uri, assets.get(asset_id, {}).get('uri')):
                try:
                    if isinstance(candidate, str):
                        source = resolve_media(self.data, candidate)
                        break
                except (OSError, ValueError):
                    pass
            row = {'assetId': asset_id, 'name': reference.get('name', 'Referencia'), 'mime': reference.get('mime', ''),
                   'field': reference.get('field', ''), 'sha256': None, 'suffix': 'bin', 'size': 0, 'missing': source is None}
            if source is not None:
                sha = fingerprint(source);suffix = source.suffix.lower().lstrip('.')
                if suffix not in SUFFIXES:
                    suffix = 'bin'
                destination = self.blobs / (sha + '.' + suffix)
                if not destination.is_file():
                    temporary = self.blobs / (uuid.uuid4().hex + '.part')
                    try:
                        shutil.copyfile(source, temporary)
                        if fingerprint(temporary) != sha:
                            raise ValueError('La referencia cambió mientras se guardaba.')
                        os.replace(temporary, destination)
                    finally:
                        temporary.unlink(missing_ok=True)
                row.update(sha256=sha, suffix=suffix, size=source.stat().st_size, missing=False)
            result.append(row)
        return result

    def capture(self, args):
        with self.lock:
            canvas = args.get('canvas', {})
            if not isinstance(canvas, dict):
                raise ValueError('Borrador de trabajo no válido.')
            snapshot = {'schemaVersion':SCHEMA, 'id':str(uuid.uuid4()), 'deviceId':self.device_id,
                        'deviceName':self.device_name, 'createdAt':int(time.time()*1000),
                        'projectId':args.get('projectId'), 'title':args.get('title') or 'Trabajo guardado',
                        'view':args.get('view', 'canvas'), 'shared':args.get('shared') is True,
                        'canvas':{'modelId':canvas.get('modelId') or '', 'providerId':canvas.get('providerId') or '',
                                  'prompt':canvas.get('prompt') or '', 'values':canvas.get('values', {}),
                                  'references':self._capture_references(canvas.get('references', []))},
                        'editor':args.get('editor', {}), 'library':args.get('library', {})}
            snapshot = self.validate(snapshot)
            atomic_json(self.snapshots / (snapshot['id'] + '.json'), snapshot)
            return {'snapshot':self.summary(snapshot), 'paidExecutionEnabled':False}

    def _snapshot(self, snapshot_id):
        try:
            snapshot_id = str(uuid.UUID(snapshot_id))
        except (ValueError, TypeError, AttributeError):
            raise ValueError('Identificador de trabajo no válido.') from None
        value = self._read(self.snapshots / (snapshot_id + '.json'), None)
        if value is None:
            raise ValueError('El trabajo guardado no está disponible.')
        return self.validate(value)

    def blob(self, sha):
        if not isinstance(sha, str) or not re.fullmatch('[a-f0-9]{64}', sha):
            return None
        for path in self.blobs.glob(sha + '.*'):
            if not path.is_file():
                continue
            stat = path.stat();key = (str(path),stat.st_size,stat.st_mtime_ns)
            if key not in self.verified:
                self.verified[key] = fingerprint(path)
            if self.verified[key] == sha:
                return path
        return None

    def summary(self, snapshot):
        references = snapshot['canvas']['references']
        return {key:snapshot[key] for key in ('id','deviceId','deviceName','createdAt','projectId','title','view','shared')} | {
            'modelId':snapshot['canvas']['modelId'], 'providerId':snapshot['canvas']['providerId'],
            'promptPreview':snapshot['canvas']['prompt'][:140], 'referenceCount':len(references),
            'missingReferences':sum(not row.get('sha256') or self.blob(row['sha256']) is None for row in references),
            'localDevice':snapshot['deviceId'] == self.device_id}

    def list(self, project_id=None):
        rows = []
        for path in self.snapshots.glob('*.json'):
            try:
                snapshot = self.validate(self._read(path, None))
                if project_id is None or snapshot['projectId'] == project_id:
                    rows.append(self.summary(snapshot))
            except ValueError:
                continue
        return sorted(rows, key=lambda row:(row['createdAt'],row['id']), reverse=True)

    def export(self):
        return [self._snapshot(row['id']) for row in self.list() if row['shared']]

    def receive(self, snapshot, fetch_blob=None):
        snapshot = self.validate(snapshot)
        with self.lock:
            target = self.snapshots / (snapshot['id'] + '.json')
            if target.exists() and encoded(self._snapshot(snapshot['id'])) != encoded(snapshot):
                raise ValueError('Una versión de trabajo recibida ha cambiado de contenido.')
            if not target.exists():
                atomic_json(target, snapshot)
            if fetch_blob:
                for reference in snapshot['canvas']['references']:
                    sha = reference['sha256']
                    if not sha or self.blob(sha) is not None:
                        continue
                    temporary = self.blobs / (uuid.uuid4().hex + '.part')
                    try:
                        fetch_blob(sha, temporary)
                        if fingerprint(temporary) != sha or temporary.stat().st_size != reference['size']:
                            raise ValueError('La referencia recibida no supera la comprobación de integridad.')
                        os.replace(temporary, self.blobs / (sha + '.' + reference['suffix']))
                    finally:
                        temporary.unlink(missing_ok=True)
            return self.summary(snapshot)

    def observe_peer(self, device, scopes):
        try:
            device = str(uuid.UUID(device))
        except (ValueError, TypeError, AttributeError):
            return
        self.peers[device] = {'workspaceCompatible':CAPABILITY in scopes, 'lastSeenAt':int(time.time()*1000)}
        atomic_json(self.root / 'peers.json', self.peers)

    def open(self, snapshot_id, context=None):
        snapshot = self._snapshot(snapshot_id)
        canvas = snapshot['canvas'];media = [];missing = []
        for reference in canvas['references']:
            path = self.blob(reference.get('sha256'))
            if path is None:
                missing.append(reference);continue
            destination = self.data / 'media' / (reference['sha256'][:32] + '.' + reference['suffix'])
            destination.parent.mkdir(parents=True, exist_ok=True)
            if destination.exists() and fingerprint(destination) != reference['sha256']:
                destination = destination.with_name(uuid.uuid4().hex + destination.suffix)
            if not destination.exists():
                shutil.copyfile(path, destination)
            media.append({'id':destination.name, 'url':'/media/'+destination.name, 'name':reference['name'],
                          'mime':reference['mime'], 'field':reference['field'], 'remoteUrl':None})
        draft = {'modelId':canvas['modelId'] or None, 'prompt':canvas['prompt'], 'values':canvas['values'], 'media':media}
        current = self.read_state()
        old = current.get('draft', {}) if isinstance(current, dict) else {}
        backup_id = None
        if old and old != draft and (old.get('prompt') or old.get('media') or old.get('modelId')):
            backup_id = str(uuid.uuid4())
            context = context if isinstance(context, dict) else {}
            settings = self._read(self.data / 'assistant' / 'settings.json', {})
            if not isinstance(settings, dict):
                settings = {}
            previous_project = identifier(context.get('projectId', settings.get('projectId')), 'proyecto anterior', optional=True)
            previous_view = context.get('view', 'canvas')
            if not isinstance(previous_view, str) or previous_view not in VIEWS:
                previous_view = 'canvas'
            # This local-only backup is never exported through the wire protocol.
            atomic_json(self.root / 'local-backups' / (backup_id + '.json'), {'draft':old,'createdAt':int(time.time()*1000),
                                                                          'projectId':previous_project,'view':previous_view})
        index = self._index();project = snapshot['projectId']
        available = project is None or (self.project_exists(project) if self.project_exists else any(p.get('id') == project for p in index.get('projects', [])))
        return {'snapshot':self.summary(snapshot), 'draft':draft, 'projectId':project, 'projectAvailable':available,
                'view':snapshot['view'], 'editor':snapshot['editor'], 'library':snapshot['library'],
                'missingReferences':missing, 'preservedLocalDraft':backup_id, 'requiresReview':True, 'paidExecutionEnabled':False}

    def command(self, args):
        op = args.get('op', 'list')
        if op in {'devices', 'handoff'}:
            if self.android_handoff is None:
                raise ValueError('El enlace nativo con móviles no está disponible en este equipo.')
            if op == 'devices':
                return self.android_handoff.devices()
            return self.android_handoff.prepare(self._snapshot(args.get('id')), args.get('serial'), launch=args.get('launch') is True)
        if op == 'capture':
            return self.capture(args)
        if op == 'list':
            return {'snapshots':self.list(args.get('projectId')), 'deviceId':self.device_id, 'peers':self.peers}
        if op == 'open':
            return self.open(args.get('id'), args.get('context'))
        if op == 'restoreBackup':
            try:
                backup_id = str(uuid.UUID(args.get('id')))
            except (ValueError, TypeError, AttributeError):
                raise ValueError('Identificador de borrador no válido.') from None
            backup = self._read(self.root / 'local-backups' / (backup_id + '.json'), None)
            if not isinstance(backup, dict) or not isinstance(backup.get('draft'), dict):
                raise ValueError('El borrador anterior no está disponible.')
            project = backup.get('projectId')
            available = project is None or any(p.get('id') == project for p in self._index().get('projects', []))
            return {'draft':backup['draft'], 'view':backup.get('view','canvas'), 'projectId':project, 'projectAvailable':available,
                    'requiresReview':True, 'paidExecutionEnabled':False}
        if op == 'export':
            return {'snapshots':self.export()}
        if op == 'receive':
            return {'snapshot':self.receive(args.get('snapshot'))}
        raise ValueError('Operación de continuidad de trabajo no admitida.')
