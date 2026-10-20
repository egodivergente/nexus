import json
import io
from pathlib import Path
import sys
import tempfile
import threading
from types import SimpleNamespace
import unittest
import urllib.error
import urllib.parse
import urllib.request
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from drive_sync import DriveSync, SCOPE


class Host:
    def __init__(self, root):
        self.data = root / 'assistant'
        self.data.mkdir()
        self.secrets = {}

    def read_secret(self, path):
        return self.secrets.get(str(path), {}).copy()

    def write_secret(self, path, value):
        self.secrets[str(path)] = value.copy()


class DriveSyncTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.host = Host(self.root)
        self.service = DriveSync(self.host)
        self.service.config['client_id'] = 'nexus-test.apps.googleusercontent.com'
        self.host.write_secret(self.service.secret_file, {'client_secret': 'test-only'})

    def tearDown(self):
        self.service.closed.set()
        if getattr(self.service, 'auth_server', None):
            self.service.auth_done.set()
        self.directory.cleanup()

    def callback(self, url, **values):
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)
        endpoint = query['redirect_uri'][0] + '?' + urllib.parse.urlencode(values)
        with urllib.request.urlopen(endpoint, timeout=5) as response:
            return response.read()

    def test_authorization_uses_pkce_loopback_and_minimum_scope(self):
        url = self.service.begin()
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)
        self.assertEqual(query['scope'], [SCOPE])
        self.assertEqual(query['code_challenge_method'], ['S256'])
        self.assertTrue(query['redirect_uri'][0].startswith('http://127.0.0.1:'))
        self.assertNotIn('test-only', url)
        self.assertGreaterEqual(len(query['state'][0]), 40)

    def test_invalid_state_cannot_capture_an_authorization(self):
        url = self.service.begin()
        with self.assertRaises(urllib.error.HTTPError):
            self.callback(url, state='wrong', code='fake')
        self.assertNotIn('refresh_token', self.host.read_secret(self.service.secret_file))

    def test_success_stores_tokens_privately_and_never_exposes_them_in_status(self):
        url = self.service.begin()
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)
        with patch.object(self.service, '_token_request', return_value={'access_token': 'test-access', 'refresh_token': 'test-refresh', 'scope': SCOPE, 'expires_in': 3600}):
            self.callback(url, state=query['state'][0], code='fake-code')
        self.assertTrue(self.service.public()['connected'])
        self.assertTrue(self.service.public()['enabled'])
        public = json.dumps(self.service.public())
        self.assertNotIn('test-access', public)
        self.assertNotIn('test-refresh', public)
        self.assertNotIn('test-only', public)
        self.assertNotIn('test-refresh', json.dumps(self.service.command({'op': 'diagnostics'})))

    def test_denied_scope_never_enables_sync(self):
        url = self.service.begin()
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)
        with patch.object(self.service, '_token_request', return_value={'access_token': 'test-access', 'refresh_token': 'test-refresh', 'scope': 'openid'}):
            self.callback(url, state=query['state'][0], code='fake-code')
        self.assertFalse(self.service.public()['connected'])
        self.assertFalse(self.service.public()['enabled'])

    def test_disconnect_keeps_originals_and_client_identity(self):
        media = self.root / 'media'
        media.mkdir()
        original = media / 'original.mp4'
        original.write_bytes(b'original-test-video')
        self.host.write_secret(self.service.secret_file, {'client_secret': 'test-only', 'refresh_token': 'test-refresh'})
        self.service.command({'op': 'disconnect'})
        self.assertEqual(original.read_bytes(), b'original-test-video')
        self.assertEqual(self.host.read_secret(self.service.secret_file), {'client_secret': 'test-only'})

    def test_effective_connection_checks_account_folder_and_commits_without_exposing_tokens(self):
        self.service.config['folderId'] = 'folder-one'
        replies = [io.BytesIO(json.dumps(value).encode()) for value in [
            {'user': {'emailAddress': 'owner@example.com'}}, {'id': 'folder-one'},
            {'files': [{'name': 'blob-example.png'}, {'name': 'commit-example.json'}]}]]
        with patch.object(self.service, 'token', return_value='private-test-token'), patch('drive_sync.urllib.request.urlopen', side_effect=replies) as network:
            result = self.service.command({'op': 'verifyConnection'})
        self.assertEqual(result['account'], 'owner@example.com')
        self.assertTrue(result['folderAccessible']); self.assertEqual(result['commits'], 1)
        self.assertNotIn('private-test-token', json.dumps(result))
        self.assertEqual(network.call_count, 3)

    def test_effective_connection_distinguishes_an_inaccessible_folder(self):
        self.service.config['folderId'] = 'other-account-folder'
        with patch.object(self.service, 'token', return_value='private-test-token'), patch('drive_sync.urllib.request.urlopen', side_effect=[
            io.BytesIO(b'{"user":{"emailAddress":"owner@example.com"}}'), urllib.error.HTTPError('https://www.googleapis.com/drive/v3/files/other-account-folder', 404, 'Not Found', {}, None)]):
            result = self.service.command({'op': 'verifyConnection'})
        self.assertFalse(result['folderAccessible'])

    def test_client_configuration_rejects_traversal(self):
        with self.assertRaises(ValueError):
            self.service.command({'op': 'configure', 'id': '../../credentials.json'})

    def test_long_original_preparation_renews_token_before_the_engine_transfer(self):
        calls = []
        exchanged = threading.Event()
        self.host.lock = threading.Lock()
        self.host.start = lambda: None
        def original(*_):
            self.assertFalse(self.host.lock.locked(), 'Network preparation must leave the agent available')
            calls.append('original')
            return ('image/png', self.root / 'original.png')
        self.host.native = SimpleNamespace(asset=original)
        self.host.corte = SimpleNamespace(lock=threading.Lock(), projects=self.root / 'projects')
        def exchange(operation, args):
            if operation == 'snapshot':
                return {'index': {'assets': [{'id': 'one', 'uri': 'https://example.invalid/old.png'}]}}
            self.assertTrue(self.host.lock.locked())
            calls.append(args['accessToken'])
            exchanged.set()
            return {'rootId': 'folder-one', 'state': 'synced'}
        self.host.exchange = exchange
        with patch.object(self.service, 'token', side_effect=['before-preparation', 'renewed-after-preparation']) as tokens:
            self.service.run()
            self.assertTrue(exchanged.wait(5), 'The background transfer did not reach the engine')
            with self.service.lock:
                pass
            self.assertEqual(tokens.call_count, 2)
        self.assertEqual(calls, ['original', 'renewed-after-preparation'])
        self.assertNotIn('renewed-after-preparation', json.dumps(self.service.public()))

    def test_configuration_removes_the_disposable_plaintext_copy(self):
        media = self.root / 'media'
        media.mkdir()
        selected = media / 'oauth.json'
        selected.write_text(json.dumps({'installed': {'client_id': 'nexus-test.apps.googleusercontent.com', 'client_secret': 'test-only'}}))
        self.service.command({'op': 'configure', 'id': 'oauth.json'})
        self.assertFalse(selected.exists())
        self.assertEqual(self.host.read_secret(self.service.secret_file)['client_secret'], 'test-only')

    def test_corte_receives_filesystem_uris_while_library_links_stay_portable(self):
        media = self.root / 'media'
        media.mkdir()
        image = media / ('a' * 32 + '.png')
        image.write_bytes(b'test-only')
        uri = '/media/' + image.name
        document = {'project': {'media': [{'id': 'm', 'uri': uri}]}, 'links': {'m': {'original_uri': uri}}}
        self.host.corte = SimpleNamespace(lock=threading.RLock(), projects=self.root / 'projects', exchange=lambda *_: None)
        self.service.captured_montages = {}
        self.service.apply_documents({'montages:p': {'document': document}})
        installed = json.loads((self.root / 'projects/p/edit.json').read_text('utf-8'))
        self.assertEqual(installed['project']['media'][0]['uri'], image.resolve().as_uri())
        self.assertEqual(installed['links']['m']['original_uri'], uri)
        self.assertEqual(document['project']['media'][0]['uri'], uri)


if __name__ == '__main__':
    unittest.main()
