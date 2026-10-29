import json
import io
import hashlib
from pathlib import Path
import sys
import tempfile
import threading
from types import SimpleNamespace
import unittest
import urllib.error
import urllib.parse
import urllib.request
from unittest.mock import patch, Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from drive_sync import DriveSync, DriveAuthorizationRequired, SCOPE


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

    def test_reopening_pending_authorization_reuses_the_same_live_callback(self):
        first = self.service.begin()
        server = self.service.auth_server
        self.assertEqual(self.service.begin(),first)
        self.assertIs(self.service.auth_server,server)
        self.assertFalse(self.service.auth_done.is_set())

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

    def test_temporary_offline_does_not_invalidate_saved_authorization(self):
        self.host.write_secret(self.service.secret_file, {'client_secret': 'test-only', 'refresh_token': 'test-refresh'})
        with patch('drive_sync.urllib.request.urlopen', side_effect=urllib.error.URLError('offline')):
            self.service.run()
            with self.service.lock:
                pass
        self.assertEqual(self.service.public()['state'], 'retry')
        self.assertTrue(self.service.public()['connected'])
        self.assertEqual(self.host.read_secret(self.service.secret_file)['refresh_token'], 'test-refresh')

    def test_revoked_token_requests_reauthorization(self):
        rejected = urllib.error.HTTPError('https://oauth2.googleapis.com/token', 400, 'Bad request', {}, io.BytesIO(b'{"error":"invalid_grant"}'))
        with patch('drive_sync.urllib.request.urlopen', side_effect=rejected):
            with self.assertRaises(DriveAuthorizationRequired):
                self.service._token_request({'grant_type': 'refresh_token'})

    def test_last_success_survives_restart_but_is_cleared_on_disconnect(self):
        self.service.activity = {'lastSuccessAt': 1234567890000, 'lastCompletedAt': 1234567890000}
        self.service.activity_file.write_text(json.dumps(self.service.activity))
        restarted = DriveSync(self.host, start_scheduler=False)
        self.assertEqual(restarted.public()['lastSuccessAt'], 1234567890000)
        restarted.command({'op': 'disconnect'})
        self.assertNotIn('lastSuccessAt', restarted.public())
        self.assertFalse(restarted.activity_file.exists())

    def test_activity_file_never_reexports_unrecognized_fields(self):
        self.service.activity_file.write_text(json.dumps({'lastSuccessAt': 123, 'access_token': 'do-not-export'}))
        restarted = DriveSync(self.host, start_scheduler=False)
        self.assertNotIn('do-not-export', json.dumps(restarted.public()))

    def test_restart_exposes_saved_conflicts_without_a_network_request(self):
        self.service.config_file.write_text(json.dumps({'folderId': 'workspace-one'}))
        folder = self.service.root / 'workspaces' / 'workspace-one'
        folder.mkdir(parents=True)
        conflicts = [{'key': 'projects:one', 'versions': [{'id': 'r1', 'value': {'name': 'One'}}]}]
        (folder / 'conflicts.json').write_text(json.dumps(conflicts))
        with patch('drive_sync.urllib.request.urlopen', side_effect=AssertionError('No network on hydration')):
            restarted = DriveSync(self.host, start_scheduler=False)
        self.assertEqual(restarted.public()['state'], 'conflicts')
        self.assertTrue(restarted.public()['historical'])
        self.assertEqual(restarted.public()['conflicts'], conflicts)
        restarted.status = {'state': 'syncing'}
        self.assertEqual(restarted.public()['conflicts'], conflicts)

    def test_restart_labels_previous_missing_originals_as_historical(self):
        self.service.activity_file.write_text(json.dumps({'lastCompletedAt': 1234567890000, 'lastMissingMedia': 104}))
        restarted = DriveSync(self.host, start_scheduler=False)
        self.assertEqual(restarted.public()['state'], 'partial')
        self.assertTrue(restarted.public()['historical'])
        self.assertEqual(restarted.public()['missingMedia'], 104)

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

    def archive_fixture(self):
        self.host.lock = threading.Lock()
        self.host.start = lambda: None
        (self.root/'media').mkdir(exist_ok=True)
        path = self.root/'media'/('a'*32+'.png');path.write_bytes(b'original-byte-proof')
        sha = hashlib.sha256(path.read_bytes()).hexdigest();uri = '/media/' + path.name
        record = {'assetId':'original-one','sha256':sha,'sizeBytes':path.stat().st_size,'uri':uri,'state':'archived','registered':True}
        archive = SimpleNamespace(lock=threading.RLock(), records={'one':record}, cloud_verified=Mock())
        self.host.original_archive = archive
        self.host.native = SimpleNamespace(asset=lambda *_:({},path))
        self.host.corte = SimpleNamespace(lock=threading.Lock(),projects=self.root/'projects')
        self.host.exchange = lambda op,args: {'index':{'assets':[{'id':'original-one','uri':uri}]}} if op=='snapshot' else {'rootId':'folder-one','state':'synced','missingMedia':0}
        return path,sha,uri,archive

    def test_synced_transfer_never_claims_cloud_copy_without_server_checksum(self):
        path,sha,uri,archive = self.archive_fixture()
        reply = io.BytesIO(json.dumps({'files':[{'name':'blob-'+sha+'.png','size':str(path.stat().st_size)}]}).encode())
        with patch.object(self.service,'token',return_value='private-test-token'), patch('drive_sync.urllib.request.urlopen',return_value=reply):
            self.service.run()
            with self.service.lock:pass
        archive.cloud_verified.assert_not_called()

    def test_drive_blob_verification_requires_local_and_server_sha256_and_size(self):
        path,sha,uri,archive = self.archive_fixture()
        originals={'original-one':{'sha256':sha,'uri':uri,'sizeBytes':path.stat().st_size}}
        valid={'name':'blob-'+sha+'.png','size':str(path.stat().st_size),'sha256Checksum':sha}
        for changed,expected in [({}, {'original-one':sha}),({'sha256Checksum':'0'*64},{}),({'size':'1'},{}),({'sha256Checksum':None},{})]:
            reply=io.BytesIO(json.dumps({'files':[{**valid,**changed}]}).encode())
            with patch.object(self.service,'token',return_value='private-test-token'), patch('drive_sync.urllib.request.urlopen',return_value=reply):
                self.assertEqual(self.service.verify_original_blobs('folder-one',originals,{uri:str(path)}),expected)
        path.write_bytes(b'changed-original')
        with patch('drive_sync.urllib.request.urlopen') as network:
            self.assertEqual(self.service.verify_original_blobs('folder-one',originals,{uri:str(path)}),{})
        network.assert_not_called()

    def test_checksums_are_paginated_and_no_media_payload_is_downloaded(self):
        path,sha,uri,archive=self.archive_fixture();originals={'original-one':{'sha256':sha,'uri':uri,'sizeBytes':path.stat().st_size}}
        replies=[io.BytesIO(json.dumps(v).encode()) for v in [{'files':[],'nextPageToken':'page-two'},
            {'files':[{'name':'blob-'+sha+'.png','size':str(path.stat().st_size),'sha256Checksum':sha}]}]]
        with patch.object(self.service,'token',return_value='private-test-token'),patch('drive_sync.urllib.request.urlopen',side_effect=replies) as network:
            result=self.service.verify_original_blobs('folder-one',originals,{uri:str(path)})
        self.assertEqual(result,{'original-one':sha});self.assertEqual(network.call_count,2)
        for call in network.call_args_list:
            query=urllib.parse.parse_qs(urllib.parse.urlsplit(call.args[0].full_url).query)
            self.assertIn('sha256Checksum',query['fields'][0]);self.assertNotIn('alt',query)

    def test_mismatched_duplicate_remote_blob_names_are_not_claimed_verified(self):
        path,sha,uri,archive=self.archive_fixture();originals={'original-one':{'sha256':sha,'uri':uri,'sizeBytes':path.stat().st_size}}
        rows=[{'name':'blob-'+sha+'.png','size':str(path.stat().st_size),'sha256Checksum':digest} for digest in [sha,'0'*64]]
        with patch.object(self.service,'token',return_value='private-test-token'),patch('drive_sync.urllib.request.urlopen',return_value=io.BytesIO(json.dumps({'files':rows}).encode())):
            self.assertEqual(self.service.verify_original_blobs('folder-one',originals,{uri:str(path)}),{})

    def test_partial_and_conflicting_sync_can_confirm_only_verified_originals(self):
        path,sha,uri,archive=self.archive_fixture()
        for state in ['partial','conflicts','synced']:
            archive.cloud_verified.reset_mock()
            self.host.exchange=lambda op,args: {'index':{'assets':[{'id':'original-one','uri':uri}]}} if op=='snapshot' else {'rootId':'folder-one','state':state,'missingMedia':1}
            def verify(folder,originals,paths):
                self.assertFalse(self.host.lock.locked(),'Checksum HTTP must leave the agent available')
                self.assertEqual(originals['original-one']['uri'],uri)
                return {'original-one':sha}
            with patch.object(self.service,'token',return_value='private-test-token'),patch.object(self.service,'verify_original_blobs',side_effect=verify):
                self.service.run()
                with self.service.lock:pass
            archive.cloud_verified.assert_called_once_with({'original-one':sha})

    def test_checksum_service_failure_keeps_copy_pending_without_losing_sync_result(self):
        path,sha,uri,archive=self.archive_fixture()
        with patch.object(self.service,'token',return_value='private-test-token'),patch.object(self.service,'verify_original_blobs',side_effect=urllib.error.URLError('offline')):
            self.service.run()
            with self.service.lock:pass
        archive.cloud_verified.assert_not_called();self.assertEqual(self.service.public()['state'],'synced')
        self.assertTrue(self.service.public()['cloudVerificationPending'])

    def test_same_asset_id_with_changed_snapshot_uri_is_not_cloud_proof(self):
        path,sha,uri,archive=self.archive_fixture()
        self.host.exchange=lambda op,args:{'index':{'assets':[{'id':'original-one','uri':uri+'-changed'}]}} if op=='snapshot' else {'rootId':'folder-one','state':'synced'}
        with patch.object(self.service,'token',return_value='private-test-token'),patch.object(self.service,'verify_original_blobs') as verify:
            self.service.run()
            with self.service.lock:pass
        verify.assert_not_called();archive.cloud_verified.assert_not_called()

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
