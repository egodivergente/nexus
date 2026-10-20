import base64
import copy
import io
import http.client
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from device_continuity import (DeviceContinuity, encoded, digest, encrypt, decrypt,
                               authorized, request_token, merge, skill_path, auth_rank)
from chat_continuity import history_id, visible_history, plain_items


class FakeHost:
    def __init__(self, root):
        self.data = root / 'assistant'
        self.data.mkdir(parents=True)
        self.store = {}
        self.lock = threading.Lock()
        self.process = None

    def read_secret(self, path):
        return copy.deepcopy(self.store.get(str(path), {}))

    def write_secret(self, path, value):
        self.store[str(path)] = copy.deepcopy(value)

    def settings(self):
        path = self.data / 'settings.json'
        return json.loads(path.read_text('utf-8')) if path.exists() else {}


class Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.key = base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip('=')

    def tearDown(self):
        self.temp.cleanup()

    def peer(self, name):
        root = self.root / name
        root.mkdir()
        host = FakeHost(root)
        peer = DeviceContinuity(host, start=False)
        peer.config.update(enabled=True, port=0, agyDirectory=str(root / 'agy'))
        host.write_secret(peer.secret_file, {'key': self.key})
        return peer

    def test_multiple_return_routes_are_validated_and_deduplicated(self):
        peer = self.peer('routes')
        state = peer.command({'op': 'configure', 'enabled': False, 'port': 8797, 'address': 'http://100.72.111.97:8797/\nhttp://192.168.1.147:8797\nhttp://100.72.111.97:8797'})
        self.assertEqual(state['address'].splitlines(), ['http://100.72.111.97:8797', 'http://192.168.1.147:8797'])
        with self.assertRaises(ValueError):
            peer.command({'op': 'configure', 'address': 'http://100.72.111.97:8797\nhttps://user:password@example.invalid'})

    def test_return_route_rejection_falls_back_without_failing_the_peer(self):
        peer = self.peer('route-fallback')
        peer.config['address'] = 'http://100.72.111.97:8797\nhttp://192.168.1.147:8797'
        with patch.object(peer, 'fetch', side_effect=[ConnectionResetError(), b'{"queued":true}']) as fetch:
            peer.notify('http://192.168.1.134:8795')
        self.assertEqual(fetch.call_count, 2)
        self.assertEqual(fetch.call_args.args[2]['endpoint'], 'http://192.168.1.147:8797')
        with patch.object(peer, 'fetch', side_effect=ConnectionResetError()):
            with self.assertRaises(ConnectionResetError):
                peer.notify('http://192.168.1.134:8795')

    def test_frames_context_tamper_truncation_and_size(self):
        source = bytes(range(256)) * 800
        packet = io.BytesIO()
        encrypt(io.BytesIO(source), packet, self.key, 'context')
        output = io.BytesIO()
        decrypt(io.BytesIO(packet.getvalue()), output, self.key, 'context')
        self.assertEqual(source, output.getvalue())
        changed = bytearray(packet.getvalue())
        changed[50] ^= 1
        for damaged in (packet.getvalue()[:-1], packet.getvalue() + b'x', bytes(changed)):
            with self.assertRaises(Exception):
                decrypt(io.BytesIO(damaged), io.BytesIO(), self.key, 'context')
        with self.assertRaises(Exception):
            decrypt(io.BytesIO(packet.getvalue()), io.BytesIO(), self.key, 'wrong')
        with self.assertRaises(ValueError):
            decrypt(io.BytesIO(packet.getvalue()), io.BytesIO(), self.key, 'context', 100)

    def test_authorization_bound_to_route(self):
        token = request_token('/manifest', self.key)
        self.assertTrue(authorized(token, '/manifest', self.key))
        self.assertFalse(authorized(token, '/blob/x', self.key))
        self.assertFalse(authorized('invalid', '/manifest', self.key))

    def test_node_android_codec_interoperability(self):
        adapter = Path(__file__).resolve().parents[2] / 'tools/nexus-continuity-termux.mjs'
        script = "import {encode,decode} from " + json.dumps(adapter.as_uri()) + ";let b='';for await(const c of process.stdin)b+=c;const d=JSON.parse(b);const k=Buffer.from(d.key,'base64url');const plain=decode(Buffer.from(d.packet,'base64'),k,'wire');process.stdout.write(encode(plain,k,'back').toString('base64'));"
        packet = io.BytesIO()
        encrypt(io.BytesIO(b'cross runtime' * 15000), packet, self.key, 'wire')
        result = subprocess.run(['node', '--input-type=module', '-e', script], input=encoded({'key': self.key, 'packet': base64.b64encode(packet.getvalue()).decode()}), capture_output=True, check=True)
        output = io.BytesIO()
        decrypt(io.BytesIO(base64.b64decode(result.stdout)), output, self.key, 'back')
        self.assertEqual(b'cross runtime' * 15000, output.getvalue())

    def test_merge_preserves_concurrent_local_and_imports_remote_fields(self):
        self.assertEqual({'a': 2, 'b': 3}, merge({'a': 2, 'b': 1}, {'a': 1, 'b': 3}, {'a': 1, 'b': 1}, {'a': 1, 'b': 1}))
        # Initially different values still accept a subsequent one-sided edit.
        self.assertEqual({'a': 4}, merge({'a': 2}, {'a': 4}, {'a': 1}, {'a': 2}))
        self.assertEqual({'a': 2}, merge({'a': 2}, {}, {'a': 1}, {'a': 1}, True))
        self.assertEqual({}, merge({'a': 1}, {}, {'a': 1}, {'a': 1}, True))
        self.assertEqual({'a': 1}, merge({'a': 1}, {}, {'a': 1}, {'a': 1}, False))

    def test_profile_edits_merge_per_field_and_removals_are_optional(self):
        base = [{'id': 'brain', 'modelId': 'first', 'name': 'Original'}]
        local = [{'id': 'brain', 'modelId': 'local', 'name': 'Original'}]
        remote = [{'id': 'brain', 'modelId': 'first', 'name': 'Phone'}]
        self.assertEqual([{'id': 'brain', 'modelId': 'local', 'name': 'Phone'}], merge(local, remote, base, base))
        self.assertEqual(base, merge(base, [], base, base))
        self.assertEqual([], merge(base, [], base, base, True))

    def test_skill_cannot_escape_or_use_windows_stream(self):
        for name in ('private/../outside', 'private/C:/x', 'private/a:stream', 'private/\\x', 'private//x'):
            with self.assertRaises(ValueError):
                skill_path(self.root, name)

    def test_skills_return_edit_and_do_not_overwrite_concurrent_edits(self):
        a, b = self.peer('a'), self.peer('b')
        path = a.skills / 'personal/SKILL.md'
        path.parent.mkdir()
        path.write_bytes(b'first')
        a.publish()
        def link(receiver, sender):
            def fetch(_, route, body=None):
                return encoded(sender.manifest) if route == '/manifest' else sender.sources.get(route[6:], sender.private_sources.get(route[6:]))
            receiver.fetch = fetch
        link(b, a)
        b.cycle('a')
        received = b.skills / 'personal/SKILL.md'
        self.assertEqual(b'first', received.read_bytes())
        received.write_bytes(b'edited on b')
        b.publish()
        link(a, b)
        a.cycle('b')
        self.assertEqual(b'edited on b', path.read_bytes())
        path.write_bytes(b'concurrent a')
        received.write_bytes(b'concurrent b')
        b.publish()
        a.cycle('b')
        self.assertEqual(b'concurrent a', path.read_bytes())
        self.assertTrue(list((a.root / 'conflicts').glob('*.skill')))

    def test_encrypted_http_round_trip(self):
        a, b = self.peer('a'), self.peer('b')
        a.publish()
        a.start_server()
        try:
            result = json.loads(b.fetch('http://127.0.0.1:' + str(a.server.server_port), '/manifest'))
            self.assertEqual(a.config['device'], result['device'])
            connection = http.client.HTTPConnection('127.0.0.1', a.server.server_port)
            token = request_token('/manifest', self.key)
            connection.request('GET', '/manifest', headers={'X-Nexus-Sync': token})
            response = connection.getresponse()
            self.assertEqual(11, response.version)  # Android requires HTTP/1.1 framing.
            output = io.BytesIO()
            decrypt(response, output, self.key, token)
            self.assertEqual(a.config['device'], json.loads(output.getvalue())['device'])
            connection.close()
        finally:
            a.server.shutdown()
            a.server.server_close()

    def test_real_encrypted_chat_blobs_preserve_distinct_same_text_contexts(self):
        a, b = self.peer('a'), self.peer('b')
        b.publish()
        def publish_chat(name, data):
            sha = digest(data)
            raw = {'items': [{'type': 'com.vidadomu.nexus.assistant.ChatItem.User', 'id': name, 'text': 'Describe',
                              'html': '<script>ignored</script>', 'attachments': [{'id': name, 'uri': 'nexus-sync-blob:' + sha,
                               'name': 'photo.png', 'mimeType': 'image/png'}]},
                             {'type': 'com.vidadomu.nexus.assistant.ChatItem.PlanCard', 'planId': 'never-execute'}],
                   'plans': {'never-execute': {'authorized': True}}}
            payload = encoded(raw)
            b.sources[sha] = data
            b.sources[digest(payload)] = payload
            b.manifest['documents'].append({'kind': 'chat', 'name': name + '.json', 'hash': digest(payload), 'bytes': len(payload)})
            return raw
        first = publish_chat('context-one', b'\x89PNG\r\n\x1a\n' + bytes(range(256)) * 1500)
        publish_chat('context-two', b'\x89PNG\r\n\x1a\nother-image')
        b.start_server()
        try:
            address = 'http://127.0.0.1:' + str(b.server.server_port)
            a.cycle(address)
            a.cycle(address)
        finally:
            b.server.shutdown()
            b.server.server_close()
        files = [(p.stem, json.loads(p.read_text('utf-8'))) for p in (a.host.data / 'conversations').glob('*.json')]
        self.assertEqual(2, len(files))
        self.assertEqual(2, len(list((a.host.data.parent / 'media').glob('*'))))
        for _, chat in files:
            self.assertNotIn('plans', chat)
            self.assertEqual([], chat['resultPlans'])
            self.assertEqual(1, len(chat['items']))
            self.assertNotIn('html', chat['items'][0])
            url = chat['items'][0]['attachments'][0]['url']
            path = a.host.data.parent / url.removeprefix('/')
            self.assertEqual(chat['continuityRaw']['items'][0]['attachments'][0]['uri'].split(':')[1], digest(path.read_bytes()))
        mirror = ('sync_' + history_id(first['items']), {'desktop': True, 'items': plain_items(first['items'])})
        visible = visible_history([mirror] + files)
        self.assertEqual(2, len(visible))
        self.assertEqual({'context-one.json', 'context-two.json'}, {chat['continuityOrigin']['document'] for _, chat in visible})
        self.assertFalse((a.host.data / 'index.json').exists())  # Attachments do not create duplicate gallery assets.

    def test_chat_attachment_opt_out_never_downloads_chat_or_media(self):
        a, b = self.peer('a'), self.peer('b')
        a.config['chatAttachments'] = False
        b.publish()
        b.manifest['documents'].append({'kind': 'chat', 'name': 'private.json', 'hash': '0' * 64, 'bytes': 20})
        def fetch(_, route):
            self.assertNotIn('0' * 64, route)
            return encoded(b.manifest) if route == '/manifest' else b.private_sources[route[6:]]
        a.fetch = fetch
        a.cycle('b')
        self.assertFalse((a.host.data / 'conversations').exists())

    def test_html_attachment_is_preserved_as_inert_bytes(self):
        a = self.peer('a')
        data = b'<script>never execute this user document</script>'
        def fetch(_, route, target=None):
            self.assertEqual('/blob/' + digest(data), route)
            target.write(data)
        a.fetch = fetch
        uri = a.chat_media('phone', {'uri': 'nexus-sync-blob:' + digest(data), 'name': 'page.html', 'mimeType': 'text/html'})
        self.assertTrue(uri.endswith('.txt'))
        self.assertEqual(data, (a.host.data.parent / uri.removeprefix('/')).read_bytes())

    def test_keys_are_dpapi_only_and_mapping_updates_existing_keys(self):
        a = self.peer('a')
        received = {'keys': {'atlas_cloud_api_key': 'synthetic-1', 'provider_nexus_bedrock_access_key_api_key': 'synthetic-aws'}, 'settings': {}, 'agy': None}
        state = a.receive_credentials(received, {}, ['credentials'])
        self.assertEqual('synthetic-1', a.host.read_secret(a.host.data.parent / 'credentials.dpapi')['atlas_cloud'])
        self.assertEqual('synthetic-aws', a.host.read_secret(a.host.data / 'credentials.dpapi')['accessKey'])
        received['keys']['atlas_cloud_api_key'] = 'synthetic-2'
        a.receive_credentials(received, state, ['credentials'])
        self.assertEqual('synthetic-2', a.host.read_secret(a.host.data.parent / 'credentials.dpapi')['atlas_cloud'])
        provider_file = a.host.data.parent / 'credentials.dpapi'
        a.host.write_secret(provider_file, {**a.host.read_secret(provider_file), 'nexus_device_sync_pair': 'synthetic-pair-do-not-export'})
        a.publish()
        self.assertFalse(a.sources)
        self.assertTrue(a.private_sources)
        self.assertNotIn('provider_nexus_device_sync_pair_api_key', json.loads(next(iter(a.private_sources.values())))['keys'])
        self.assertFalse(any(b'synthetic-' in p.read_bytes() for p in a.root.rglob('*') if p.is_file()))

    def test_settings_limits_profiles_and_key_slots_are_usable(self):
        a = self.peer('a')
        received = {'keys': {'provider_nexus_brain_key_custom_api_key': 'synthetic'}, 'settings': {
            'assistant_config_v1': {'mode': {'type': 'string', 'value': 'AGENT'}, 'lim_rounds': {'type': 'int', 'value': 17}},
            'brain_profiles_v1': {'profiles': {'type': 'string', 'value': [{'id': 'custom', 'name': 'Custom'}]}}}, 'agy': None}
        a.receive_credentials(received, {}, ['settings', 'credentials'])
        self.assertEqual(17, a.host.settings()['limits']['maxToolRounds'])
        self.assertIn('custom', a.host.settings()['brainProfiles'])
        self.assertEqual('synthetic', a.host.read_secret(a.host.data / 'credentials.dpapi')['brain:custom'])

    def test_custom_media_provider_config_and_key_use_native_windows_stores(self):
        a = self.peer('a')
        config = {'id': 'custom-media', 'name': 'Private media', 'baseUrl': 'https://example.com'}
        incoming = {'keys': {'provider_nexus_media_key_custom-media_api_key': 'synthetic'},
                    'settings': {'media_providers_v1': {'configs': {'type': 'string', 'value': [config]}}}, 'agy': None}
        a.receive_credentials(incoming, {}, ['credentials', 'settings'])
        self.assertEqual(config, json.loads((a.host.data.parent / 'custom-media-providers.json').read_text('utf-8'))['custom-media'])
        self.assertEqual('synthetic', a.host.read_secret(a.host.data.parent / 'custom-media-credentials.dpapi')['custom-media'])

    def test_category_disabled_does_not_write_peer_keys(self):
        a = self.peer('a')
        a.config['credentials'] = False
        a.receive_credentials({'keys': {'atlas_cloud_api_key': 'synthetic'}, 'settings': {}, 'agy': None}, {}, ['credentials'])
        self.assertFalse(a.host.read_secret(a.host.data.parent / 'credentials.dpapi'))

    def test_all_sensitive_categories_disabled_never_downloads_secrets(self):
        a, b = self.peer('a'), self.peer('b')
        a.config.update(credentials=False, settings=False, agy=False)
        b.publish()
        calls = []
        def fetch(_, route):
            calls.append(route)
            self.assertEqual('/manifest', route)
            return encoded(b.manifest)
        a.fetch = fetch
        a.cycle('b')
        self.assertEqual(['/manifest'], calls)

    def test_optional_deletion_preserves_local_key_edits(self):
        a = self.peer('a')
        incoming = {'keys': {'atlas_cloud_api_key': 'first'}, 'settings': {}, 'agy': None}
        state = a.receive_credentials(incoming, {}, ['credentials'])
        state = a.receive_credentials({'keys': {}, 'settings': {}, 'agy': None}, state, ['credentials'])
        self.assertEqual('first', a.host.read_secret(a.host.data.parent / 'credentials.dpapi')['atlas_cloud'])
        a.config['deletions'] = True
        state = a.receive_credentials(incoming, state, ['credentials'])
        a.host.write_secret(a.host.data.parent / 'credentials.dpapi', {'atlas_cloud': 'local edit'})
        a.receive_credentials({'keys': {}, 'settings': {}, 'agy': None}, state, ['credentials'])
        self.assertEqual('local edit', a.host.read_secret(a.host.data.parent / 'credentials.dpapi')['atlas_cloud'])

    def test_local_key_removal_exports_only_when_deletions_enabled(self):
        a = self.peer('a')
        a.receive_credentials({'keys': {'atlas_cloud_api_key': 'first'}, 'settings': {}, 'agy': None}, {}, ['credentials'])
        a.host.write_secret(a.host.data.parent / 'credentials.dpapi', {})
        self.assertIn('atlas_cloud_api_key', a.credential_snapshot()['keys'])
        a.config['deletions'] = True
        self.assertNotIn('atlas_cloud_api_key', a.credential_snapshot()['keys'])

    def test_agy_revision_is_complete_and_previous_auth_is_dpapi(self):
        a = self.peer('a')
        auth = {'antigravity-oauth-token': '{"id_token":"sample"}', 'installation_id': 'synthetic-installation'}
        a.receive_credentials({'keys': {}, 'settings': {}, 'agy': auth}, {}, ['agy'])
        self.assertEqual(auth, a.private()['shared']['agy'])
        for name, value in auth.items():
            self.assertEqual(value, (Path(a.config['agyDirectory']) / name).read_text('utf-8'))
        self.assertFalse(list(a.root.glob('*.json.enc')))

    def test_opted_in_agy_logout_is_not_resurrected_by_an_unchanged_peer(self):
        a = self.peer('a')
        auth = {'antigravity-oauth-token': '{"id_token":"sample"}', 'installation_id': 'synthetic-installation'}
        state = a.receive_credentials({'keys': {}, 'settings': {}, 'agy': auth}, {}, ['agy'])
        a.config['deletions'] = True
        (Path(a.config['agyDirectory']) / 'antigravity-oauth-token').unlink()
        self.assertEqual({'installation_id': 'synthetic-installation'}, a.credential_snapshot()['agy'])
        a.receive_credentials({'keys': {}, 'settings': {}, 'agy': auth}, state, ['agy'])
        self.assertFalse((Path(a.config['agyDirectory']) / 'antigravity-oauth-token').exists())
        b = self.peer('b')
        state = b.receive_credentials({'keys': {}, 'settings': {}, 'agy': auth}, {}, ['agy'])
        b.config['deletions'] = True
        b.receive_credentials({'keys': {}, 'settings': {}, 'agy': {}}, state, ['agy'])
        self.assertFalse((Path(b.config['agyDirectory']) / 'antigravity-oauth-token').exists())

    def test_busy_agent_never_receives_writes_during_turn(self):
        a, b = self.peer('a'), self.peer('b')
        (b.skills / 'SKILL.md').write_bytes(b'synthetic')
        b.publish()
        a.fetch = lambda _, route: encoded(b.manifest) if route == '/manifest' else b.sources.get(route[6:], b.private_sources.get(route[6:]))
        a.host.lock.acquire()
        try:
            with self.assertRaises(RuntimeError):
                a.cycle('b')
            self.assertFalse((a.skills / 'SKILL.md').exists())
        finally:
            a.host.lock.release()

    def test_agy_partial_write_rolls_back_the_whole_revision(self):
        a = self.peer('a')
        def auth(expiry, installation):
            payload = base64.urlsafe_b64encode(encoded({'exp': expiry})).decode().rstrip('=')
            return {'antigravity-oauth-token': json.dumps({'id_token': 'e30.' + payload + '.sample'}), 'installation_id': installation}
        old, new = auth(1, 'old-install'), auth(2, 'new-install')
        state = a.receive_credentials({'keys': {}, 'settings': {}, 'agy': old}, {}, ['agy'])
        from device_continuity import atomic
        failed = []
        def write(path, data):
            if path.name == 'installation_id' and data == b'new-install' and not failed:
                failed.append(True)
                raise OSError('simulated interruption')
            atomic(path, data)
        with patch('device_continuity.atomic', write):
            with self.assertRaises(OSError):
                a.receive_credentials({'keys': {}, 'settings': {}, 'agy': new}, state, ['agy'])
        self.assertEqual(old, a.private()['shared']['agy'])
        for name, value in old.items():
            self.assertEqual(value, (Path(a.config['agyDirectory']) / name).read_text('utf-8'))

    def test_unchanged_replica_does_not_overwrite_author_edit(self):
        a, b = self.peer('a'), self.peer('b')
        path = a.skills / 'SKILL.md'
        path.write_bytes(b'local edit')
        bpath = b.skills / 'SKILL.md'
        bpath.write_bytes(b'old replica')
        private = b.private()
        private['replicas'] = {'private/SKILL.md': digest(b'old replica')}
        b.host.write_secret(b.secret_file, private)
        b.publish()
        a.fetch = lambda _, route: encoded(b.manifest) if route == '/manifest' else b.sources.get(route[6:], b.private_sources.get(route[6:]))
        a.cycle('b')
        self.assertEqual(b'local edit', path.read_bytes())

    def test_received_skill_update_relays_to_a_third_device(self):
        a, b, c = self.peer('a'), self.peer('b'), self.peer('c')
        def link(receiver, sender):
            receiver.fetch = lambda _, route: encoded(sender.manifest) if route == '/manifest' else sender.sources.get(route[6:], sender.private_sources.get(route[6:]))
        path = b.skills / 'SKILL.md'
        path.write_bytes(b'first')
        b.publish()
        link(a, b)
        a.cycle('b')
        a.publish()
        link(c, a)
        c.cycle('a')
        path.write_bytes(b'new revision')
        b.publish()
        a.cycle('b')
        a.publish()
        c.cycle('a')
        self.assertEqual(b'new revision', (c.skills / 'SKILL.md').read_bytes())


if __name__ == '__main__':
    unittest.main()
