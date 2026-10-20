"""Encrypted supplement to Drive: personal skills, settings, provider keys and AGY.

Uses the Android continuity wire protocol. No credential is written to the blob
cache or Google Drive; pairing, merge bases and conflicts use Windows DPAPI.
"""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import struct
import threading
import time
import urllib.request
import urllib.parse
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

AGENT_SLOTS = {'accessKey': 'nexus_bedrock_access_key', 'secretKey': 'nexus_bedrock_secret_key',
               'token': 'nexus_assistant_proxy', 'api:anthropic': 'nexus_llm_anthropic_key',
               'api:openai': 'nexus_llm_openai_compatible_key'}
LIMIT_SLOTS = dict(maxToolRounds='lim_rounds', turnTimeoutSeconds='lim_turn_timeout_s', maxTokens='max_tokens',
                   flowMaxResults='lim_flow_results', maxImagesPerTurn='lim_images', maxVideosPerTurn='lim_videos',
                   maxDocumentsPerTurn='lim_documents', maxMessages='lim_messages', maxToolResultChars='lim_tool_chars',
                   maxUiContextChars='lim_ui_chars', jobTrackingMinutes='lim_tracking_min')

def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf-8')


def unbase(value):
    return base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))


def digest(value):
    return hashlib.sha256(value).hexdigest()


def exact(stream, length):
    result = bytearray()
    while len(result) < length:
        part = stream.read(length - len(result))
        if not part:
            raise ValueError('Truncated encrypted frame')
        result.extend(part)
    return bytes(result)


def encrypt(source, target, secret, context='nexus-sync-request-v1'):
    cipher = AESGCM(unbase(secret))
    stream = secrets.token_bytes(16)
    target.write(stream)
    sequence = 0
    while True:
        block = source.read(65536)
        nonce = secrets.token_bytes(12)
        aad = context.encode() + stream + struct.pack('>q', sequence)
        data = cipher.encrypt(nonce, block, aad)
        target.write(struct.pack('>i', len(data)) + nonce + data)
        sequence += 1
        if not block:
            break


def decrypt(source, target, secret, context='nexus-sync-request-v1', maximum=0):
    cipher = AESGCM(unbase(secret))
    stream = exact(source, 16)
    sequence = total = 0
    while True:
        size = struct.unpack('>i', exact(source, 4))[0]
        if not 16 <= size <= 65552:
            raise ValueError('Invalid encrypted frame')
        nonce = exact(source, 12)
        block = cipher.decrypt(nonce, exact(source, size), context.encode() + stream + struct.pack('>q', sequence))
        sequence += 1
        total += len(block)
        if maximum and total > maximum:
            raise ValueError('Configured file size exceeded')
        if not block:
            if source.read(1):
                raise ValueError('Trailing encrypted data')
            break
        target.write(block)


def request_token(path, secret):
    target = io.BytesIO()
    encrypt(io.BytesIO(('nexus-sync-v1:' + path).encode()), target, secret)
    return base64.urlsafe_b64encode(target.getvalue()).decode().rstrip('=')


def authorized(token, path, secret):
    try:
        target = io.BytesIO()
        decrypt(io.BytesIO(unbase(token)), target, secret)
        return secrets.compare_digest(target.getvalue(), ('nexus-sync-v1:' + path).encode())
    except Exception:
        return False


def endpoint(value):
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme not in {'http', 'https'} or not parsed.hostname or parsed.username or parsed.password or parsed.path not in {'', '/'} or parsed.query or parsed.fragment:
        raise ValueError('Usa una direccion http(s) sin ruta ni credenciales.')
    return value.rstrip('/')


def skill_path(root, name):
    if not name.startswith(('private/', 'shared/')) or '\\' in name:
        raise ValueError('Invalid skill path')
    relative = name.split('/', 1)[1]
    parts = relative.split('/')
    if not relative or any(p in {'', '.', '..'} or ':' in p for p in parts):
        raise ValueError('Invalid skill path')
    destination = (root / ('synced-shared' if name.startswith('shared/') else '') / relative).resolve()
    if not destination.is_relative_to(root.resolve()) or destination == root.resolve():
        raise ValueError('Skill escaped its directory')
    return destination


def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        temporary.write_bytes(data)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def merge(local, remote, base=None, own_base=None, deletions=False):
    """Dual-base field merge; initial differences never authorize overwriting local edits."""
    missing = object()
    if local == remote or remote == base:
        return local
    if local == own_base or local is None:
        return remote
    if isinstance(local, dict) and isinstance(remote, dict):
        result = dict(local)
        for key in local.keys() | remote.keys():
            if key not in remote:
                if deletions and isinstance(base, dict) and key in base and result.get(key, missing) == (own_base or {}).get(key, missing):
                    result.pop(key, None)
            elif key not in local:
                if not isinstance(base, dict) or key not in base or not deletions:
                    result[key] = remote[key]
            else:
                result[key] = merge(local[key], remote[key], (base or {}).get(key), (own_base or {}).get(key), deletions)
        return result
    return local


def auth_rank(auth):
    token = (auth or {}).get('antigravity-oauth-token', '')
    try:
        jwt = json.loads(token)['id_token']
        expiry = int(json.loads(unbase(jwt.split('.')[1]))['exp'])
    except Exception:
        expiry = 0
    return expiry, digest(token.encode())


class DeviceContinuity:
    def __init__(self, host, start=True):
        self.host = host
        self.root = host.data.parent / 'device-continuity'
        self.root.mkdir(parents=True, exist_ok=True)
        self.config_file = self.root / 'options.json'
        self.secret_file = self.root / 'private.dpapi'
        self.skills = host.data / 'skills'
        self.skills.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self.publish_lock = threading.RLock()
        self.wake = threading.Event()
        self.server = None
        self.manifest = None
        self.sources = {}
        self.private_sources = {}
        self.status = 'Sin vincular'
        self.dirty = False
        defaults = dict(enabled=False, peers=[], address='', port=8797, interval=30,
                        timeout=120, maxFileMb=0, skills=True, credentials=True,
                        settings=True, agy=True, deletions=False, device=str(uuid.uuid4()),
                        agyDirectory=str(Path.home() / '.gemini' / 'antigravity-cli'))
        if self.config_file.exists():
            defaults.update(json.loads(self.config_file.read_text('utf-8')))
        self.config = defaults
        atomic(self.config_file, encoded(self.config))
        if start:
            threading.Thread(target=self.loop, daemon=True, name='NexusContinuity').start()

    def private(self):
        return self.host.read_secret(self.secret_file)

    def public(self):
        return {**self.config, 'configured': bool(self.private().get('key')), 'status': self.status}

    def command(self, args):
        op = args.get('op', 'status')
        if op == 'configure':
            if not self.lock.acquire(False):
                raise ValueError('Espera a que termine el intercambio en curso.')
            try:
                next_config = {**self.config, **{k: v for k, v in args.items() if k in self.config and k != 'device'}}
                next_config['peers'] = [endpoint(str(v)) for v in next_config['peers']]
                next_config['address'] = endpoint(next_config['address']) if next_config['address'] else ''
                for name in ('port', 'interval', 'timeout', 'maxFileMb'):
                    next_config[name] = int(next_config[name])
                for name in ('enabled', 'skills', 'credentials', 'settings', 'agy', 'deletions'):
                    if not isinstance(next_config[name], bool):
                        raise ValueError('Opcion de sincronizacion no valida.')
                next_config['agyDirectory'] = str(Path(next_config['agyDirectory']).expanduser().resolve())
                if not 1024 <= next_config['port'] <= 65535 or next_config['interval'] < 1 or min(next_config['timeout'], next_config['maxFileMb']) < 0:
                    raise ValueError('Puerto o intervalo no valido.')
                private = self.private()
                key = args.get('key') or private.get('key', '')
                if key and len(unbase(key)) != 32:
                    raise ValueError('Clave de vinculacion no valida.')
                if next_config['enabled'] and (not key or not next_config['peers'] or not next_config['address']):
                    raise ValueError('Faltan las direcciones o la clave de vinculacion.')
                private['key'] = key
                self.host.write_secret(self.secret_file, private)
                self.config = next_config
                atomic(self.config_file, encoded(self.config))
                if self.server:
                    self.server.shutdown()
                    self.server.server_close()
                    self.server = None
                self.manifest = None
                if not self.config['enabled']:
                    self.status = 'Sincronizacion desactivada'
            finally:
                self.lock.release()
            self.wake.set()
        elif op == 'run':
            self.wake.set()
        elif op != 'status':
            raise ValueError('Operacion desconocida.')
        return self.public()

    def fetch(self, address, path, body=None):
        key = self.private()['key']
        token = request_token(path, key)
        packet = None
        if body is not None:
            output = io.BytesIO()
            encrypt(io.BytesIO(encoded(body)), output, key, token)
            packet = output.getvalue()
        request = urllib.request.Request(endpoint(address) + path, data=packet,
                                         headers={'X-Nexus-Sync': token})
        # Ignore browser/system HTTP proxies for this explicit private peer transport.
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=self.config['timeout'] or None) as response:
            output = io.BytesIO()
            decrypt(response, output, key, token, self.config['maxFileMb'] * 1048576)
            return output.getvalue()

    def credential_snapshot(self):
        private = self.private()
        shared = private.get('shared', {'keys': {}, 'settings': {}, 'agy': None})
        result = json.loads(json.dumps(shared))
        provider = self.host.read_secret(self.host.data.parent / 'credentials.dpapi')
        agent = self.host.read_secret(self.host.data / 'credentials.dpapi')
        if self.config['credentials']:
            for name, value in provider.items():
                if isinstance(value, str) and re.fullmatch(r'[a-z0-9_-]+', name):
                    result['keys']['atlas_cloud_api_key' if name == 'atlas_cloud' else 'provider_' + name + '_api_key'] = value
            for name, value in agent.items():
                if name.startswith('brain:'):
                    result['keys']['provider_nexus_brain_key_' + name[6:] + '_api_key'] = value
                elif name in AGENT_SLOTS:
                    result['keys']['provider_' + AGENT_SLOTS[name] + '_api_key'] = value
            media = self.host.read_secret(self.host.data.parent / 'custom-media-credentials.dpapi')
            for name, value in media.items():
                result['keys']['provider_nexus_media_key_' + name + '_api_key'] = value
        else:
            result['keys'] = {}
        if not self.config['settings']:
            result['settings'] = {}
        else:
            settings = self.host.settings()
            fields = result.setdefault('settings', {}).setdefault('assistant_config_v1', {})
            for desktop, android in {'mode': 'mode', 'maxTokens': 'max_tokens', 'modelId': 'model_id', 'region': 'region', 'sendImages': 'send_images', 'excludedFamilies': 'excluded_families'}.items():
                if desktop in settings:
                    value = settings[desktop]
                    fields[android] = {'type': 'boolean' if isinstance(value, bool) else 'int' if isinstance(value, int) else 'string', 'value': value}
            if settings.get('brainProfiles'):
                result['settings'].setdefault('brain_profiles_v1', {})['profiles'] = {'type': 'string', 'value': list(settings['brainProfiles'].values())}
            limits = settings.get('limits', {})
            for name, slot in LIMIT_SLOTS.items():
                if name in limits:
                    fields[slot] = {'type': 'int', 'value': limits[name]}
            if limits:
                fields['lim_extra_json'] = {'type': 'string', 'value': {k: v for k, v in limits.items() if k not in LIMIT_SLOTS}}
            backend = settings.get('backend')
            if backend:
                fields['backend'] = {'type': 'string', 'value': 'custom:' + settings['brainProfile']['id'] if backend == 'profile' and settings.get('brainProfile') else backend}
        if not self.config['agy']:
            result['agy'] = None
        else:
            directory = Path(self.config['agyDirectory'])
            names = ('antigravity-oauth-token', 'installation_id')
            if all((directory / name).is_file() for name in names):
                result['agy'] = {name: (directory / name).read_text('utf-8') for name in names}
        return result

    def publish(self):
        documents = []
        sources = {}
        private_sources = {}
        private = self.private()
        if self.config['skills']:
            for file in self.skills.rglob('*'):
                if not file.is_file() or file.is_symlink() or not file.resolve().is_relative_to(self.skills.resolve()):
                    continue
                data = file.read_bytes()
                if self.config['maxFileMb'] and len(data) > self.config['maxFileMb'] * 1048576:
                    continue
                name = 'private/' + file.relative_to(self.skills).as_posix()
                sha = digest(data)
                sources[sha] = data
                documents.append(dict(kind='skill', name=name, hash=sha, bytes=len(data), baseHash=private.get('replicas', {}).get(name)))
        scopes = [name for name in ('credentials', 'settings', 'agy') if self.config[name]]
        if scopes:
            data = encoded(self.credential_snapshot())
            sha = digest(data)
            private_sources[sha] = data
            documents.append(dict(kind='credentials', name='credentials.json', hash=sha, bytes=len(data)))
        if self.config['skills']:
            scopes.append('skill')
        with self.publish_lock:
            self.sources, self.private_sources = sources, private_sources
            self.manifest = dict(protocol=1, device=self.config['device'], documents=documents, scopes=scopes)

    def start_server(self):
        owner = self
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                self.connection.settimeout(owner.config['timeout'] or None)
                token = self.headers.get('X-Nexus-Sync', '')
                key = owner.private().get('key', '')
                if not owner.config['enabled'] or not authorized(token, self.path, key):
                    self.send_error(403)
                    return
                with owner.publish_lock:
                    if self.path == '/manifest' and owner.manifest:
                        source = encoded(owner.manifest)
                    elif re.fullmatch(r'/blob/[a-f0-9]{64}', self.path):
                        sha = self.path[6:]
                        source = owner.private_sources.get(sha, owner.sources.get(sha))
                    else:
                        source = None
                if source is None:
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header('Content-Type', 'application/octet-stream')
                self.send_header('Cache-Control', 'no-store')
                self.send_header('Connection', 'close')
                self.end_headers()
                encrypt(io.BytesIO(source), self.wfile, key, token)
        self.server = ThreadingHTTPServer(('0.0.0.0', self.config['port']), Handler)
        self.server.daemon_threads = True
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def receive_credentials(self, received, state, scopes):
        private = self.private()
        local = self.credential_snapshot()
        scoped = {'settings': received.get('settings', {}) if self.config['settings'] and 'settings' in scopes else local['settings']}
        # Wire scope name is credentials, payload field name is keys.
        scoped['keys'] = received.get('keys', {}) if self.config['credentials'] and 'credentials' in scopes else local['keys']
        merged = merge({k: local[k] for k in ('keys', 'settings')}, scoped,
                       state.get('base'), state.get('localBase'), self.config['deletions'])
        own_auth, incoming_auth = local.get('agy'), received.get('agy')
        auth = own_auth
        if self.config['agy'] and 'agy' in scopes and incoming_auth and (not own_auth or auth_rank(incoming_auth) > auth_rank(own_auth)):
            auth = incoming_auth
        if merged != scoped:
            private.setdefault('conflicts', {})[digest(encoded(received))] = received
        prior_keys = private.get('shared', {}).get('keys', {})
        self.dirty = self.dirty or merged != {k: local[k] for k in ('keys', 'settings')} or auth != own_auth
        private['shared'] = {**merged, 'agy': auth}
        self.host.write_secret(self.secret_file, private)
        if self.config['credentials']:
            provider_file = self.host.data.parent / 'credentials.dpapi'
            provider = self.host.read_secret(provider_file)
            agent_file = self.host.data / 'credentials.dpapi'
            agent = self.host.read_secret(agent_file)
            media_file = self.host.data.parent / 'custom-media-credentials.dpapi'
            media = self.host.read_secret(media_file)
            reverse_slots = {'provider_' + slot + '_api_key': name for name, slot in AGENT_SLOTS.items()}
            if self.config['deletions']:
                for name, value in prior_keys.items():
                    if name in merged['keys']:
                        continue
                    slot = 'atlas_cloud' if name == 'atlas_cloud_api_key' else name[len('provider_'):-len('_api_key')]
                    if name.startswith('provider_nexus_brain_key_'):
                        brain = 'brain:' + name[len('provider_nexus_brain_key_'):-len('_api_key')]
                        if agent.get(brain) == value:
                            agent.pop(brain, None)
                    elif provider.get(slot) == value:
                        provider.pop(slot, None)
                    if name in reverse_slots and agent.get(reverse_slots[name]) == value:
                        agent.pop(reverse_slots[name], None)
                    if name.startswith('provider_nexus_media_key_'):
                        slot = name[len('provider_nexus_media_key_'):-len('_api_key')]
                        if media.get(slot) == value:
                            media.pop(slot, None)
            for name, value in merged['keys'].items():
                if not isinstance(value, str) or not name.endswith('_api_key') or name == 'provider_nexus_device_sync_pair_api_key':
                    raise ValueError('Invalid credential slot')
                if name == 'atlas_cloud_api_key':
                    provider['atlas_cloud'] = value
                elif name.startswith('provider_nexus_brain_key_'):
                    agent['brain:' + name[len('provider_nexus_brain_key_'):-len('_api_key')]] = value
                elif name.startswith('provider_nexus_media_key_'):
                    media[name[len('provider_nexus_media_key_'):-len('_api_key')]] = value
                elif name in reverse_slots:
                    agent[reverse_slots[name]] = value
                elif name.startswith('provider_'):
                    provider[name[len('provider_'):-len('_api_key')]] = value
            self.host.write_secret(provider_file, provider)
            self.host.write_secret(agent_file, agent)
            self.host.write_secret(media_file, media)
        if self.config['settings']:
            settings_file = self.host.data / 'settings.json'
            settings = self.host.settings()
            prefs = merged['settings'].get('assistant_config_v1', {})
            for desktop, android in {'mode': 'mode', 'maxTokens': 'max_tokens', 'modelId': 'model_id', 'region': 'region', 'sendImages': 'send_images', 'excludedFamilies': 'excluded_families'}.items():
                if android in prefs:
                    settings[desktop] = prefs[android]['value']
            profiles = merged['settings'].get('brain_profiles_v1', {}).get('profiles', {}).get('value', [])
            if isinstance(profiles, str):
                profiles = json.loads(profiles)
            if isinstance(profiles, list):
                settings['brainProfiles'] = {**settings.get('brainProfiles', {}), **{p['id']: p for p in profiles if isinstance(p, dict) and p.get('id')}}
            limits = dict(settings.get('limits', {}))
            for name, slot in LIMIT_SLOTS.items():
                if slot in prefs:
                    limits[name] = prefs[slot]['value']
            extra = prefs.get('lim_extra_json', {}).get('value', {})
            if isinstance(extra, str):
                extra = json.loads(extra)
            if isinstance(extra, dict):
                limits.update(extra)
            if limits:
                settings['limits'] = limits
            backend = prefs.get('backend', {}).get('value')
            if isinstance(backend, str) and backend.startswith('custom:'):
                selected = settings.get('brainProfiles', {}).get(backend[7:])
                if selected:
                    settings.update(backend='profile', brainProfile=selected)
            elif backend in {'direct', 'proxy', 'anthropic', 'openai', 'antigravity'}:
                settings['backend'] = backend
            atomic(settings_file, encoded(settings))
        if auth and auth != own_auth:
            directory = Path(self.config['agyDirectory'])
            # CLI session files are the CLI's normal store; revisions/backups stay DPAPI.
            for name in ('antigravity-oauth-token', 'installation_id'):
                if name not in auth or not isinstance(auth[name], str):
                    raise ValueError('Incomplete AGY auth revision')
            for name in ('antigravity-oauth-token', 'installation_id'):
                atomic(directory / name, auth[name].encode('utf-8'))
        return json.loads(encoded(dict(base=scoped, localBase=merged)))

    def cycle(self, peer):
        manifest = json.loads(self.fetch(peer, '/manifest'))
        device = str(uuid.UUID(manifest['device']))
        if manifest['protocol'] != 1 or device == self.config['device']:
            raise ValueError('Invalid peer manifest')
        for doc in manifest['documents']:
            if doc['kind'] not in {'skill', 'credentials'}:
                continue
            if doc['kind'] == 'skill' and not self.config['skills']:
                continue
            private = self.private()
            state_key = device + ':' + doc['kind'] + ':' + doc['name']
            state = private.get('peers', {}).get(state_key, {})
            policy = [self.config[name] for name in ('skills', 'credentials', 'settings', 'agy', 'deletions')]
            if state.get('hash') == doc['hash'] and state.get('policy') == policy:
                continue
            if not re.fullmatch(r'[a-f0-9]{64}', doc['hash']):
                raise ValueError('Invalid document hash')
            data = self.fetch(peer, '/blob/' + doc['hash'])
            if digest(data) != doc['hash'] or len(data) != doc['bytes']:
                raise ValueError('Invalid document integrity')
            if not self.host.lock.acquire(False):
                raise RuntimeError('Agent busy; retry next interval')
            try:
                if doc['kind'] == 'credentials':
                    state = self.receive_credentials(json.loads(data), state, manifest.get('scopes', []))
                else:
                    destination = skill_path(self.skills, doc['name'])
                    local_hash = digest(destination.read_bytes()) if destination.exists() else None
                    if doc.get('baseHash') == doc['hash'] and (local_hash is not None or state):
                        pass
                    elif local_hash not in {None, doc['hash'], doc.get('baseHash'), state.get('localHash')}:
                        atomic(self.root / 'conflicts' / (doc['hash'] + '.skill'), data)
                    else:
                        atomic(destination, data)
                        self.dirty = True
                        private.setdefault('replicas', {})['private/' + destination.relative_to(self.skills).as_posix()] = doc['hash']
                        self.host.write_secret(self.secret_file, private)
                    state = {'localHash': digest(destination.read_bytes()) if destination.exists() else None}
                private = self.private()
                private.setdefault('peers', {})[state_key] = {**state, 'hash': doc['hash'], 'policy': policy}
                self.host.write_secret(self.secret_file, private)
            finally:
                self.host.lock.release()
        if self.config['deletions'] and self.config['skills'] and 'skill' in manifest.get('scopes', []):
            private = self.private()
            present = {doc['name'] for doc in manifest['documents'] if doc['kind'] == 'skill'}
            prefix = device + ':skill:'
            for state_key, state in list(private.get('peers', {}).items()):
                if state_key.startswith(prefix) and state_key[len(prefix):] not in present:
                    name = state_key[len(prefix):]
                    destination = skill_path(self.skills, name)
                    if destination.is_file() and digest(destination.read_bytes()) == state.get('hash'):
                        destination.unlink()
                        private['peers'].pop(state_key)
                        private.get('replicas', {}).pop('private/' + destination.relative_to(self.skills).as_posix(), None)
            self.host.write_secret(self.secret_file, private)

    def loop(self):
        while True:
            self.wake.clear()
            if self.config['enabled'] and self.lock.acquire(False):
                try:
                    self.status = 'Sincronizando skills, ajustes y claves...'
                    self.publish()
                    if self.server is None:
                        self.start_server()
                    failures = []
                    for peer in self.config['peers']:
                        try:
                            self.cycle(peer)
                        except Exception as error:
                            failures.append(type(error).__name__)
                    self.publish()
                    # Network requests never hold the agent turn lock.
                    for peer in self.config['peers']:
                        try:
                            self.fetch(peer, '/sync', {'endpoint': self.config['address']})
                        except Exception as error:
                            failures.append(type(error).__name__)
                    if self.dirty and self.host.lock.acquire(False):
                        try:
                            if self.host.process and self.host.process.poll() is None:
                                self.host.exchange('reload', {})
                                self.host.exchange('settings', {'settings': self.host.settings(), 'secrets': self.host.runtime_secrets()})
                            self.dirty = False
                        finally:
                            self.host.lock.release()
                    self.status = ('Pendiente de conexion (' + ', '.join(sorted(set(failures))) + ')') if failures else 'Sincronizado - ' + time.strftime('%H:%M:%S')
                except Exception as error:
                    self.status = 'Pendiente de conexion (' + type(error).__name__ + ')'
                finally:
                    self.lock.release()
            self.wake.wait(self.config['interval'])
