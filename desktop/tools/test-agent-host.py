import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from agent_host import AgentHost
from local_host import windows_keys, write_secret


class HostTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix='nexus-agent-test-')
        self.root = Path(self.folder.name)
        self.host = AgentHost(self.root, self.root, windows_keys, write_secret)

    def tearDown(self):
        self.host.close()
        self.folder.cleanup()

    def test_dpapi_roundtrip_is_not_plaintext(self):
        path = self.root / 'secret.dpapi'
        write_secret(path, {'token': 'qa-sentinel-not-a-real-secret'})
        self.assertNotIn(b'qa-sentinel', path.read_bytes())
        self.assertEqual(windows_keys(path)['token'], 'qa-sentinel-not-a-real-secret')

    def test_public_settings_do_not_return_credentials(self):
        self.host.save_settings({'accessKey': 'qa-access', 'secretKey': 'qa-secret'})
        public = self.host.public_settings()
        self.assertNotIn('qa-secret', json.dumps(public))
        self.assertEqual(set(public['configured']), {'accessKey', 'secretKey'})
        self.assertNotIn('qa-secret', (self.host.data / 'settings.json').read_text())

    def test_blank_secret_keeps_existing(self):
        self.host.save_settings({'secretKey': 'qa-existing'})
        self.host.save_settings({'secretKey': ''})
        self.assertEqual(windows_keys(self.host.data / 'credentials.dpapi')['secretKey'], 'qa-existing')

    def test_insecure_remote_proxy_rejected(self):
        with self.assertRaises(ValueError):
            self.host.save_settings({'endpoint': 'http://example.invalid'})

    def test_callback_cannot_be_claimed_by_another_window(self):
        self.host.owner = 'correct-window'
        event = threading.Event()
        self.host.pending['request'] = {'op': 'models', 'args': {}, 'event': event}
        self.assertEqual(self.host.events('different-window'), [])
        self.assertFalse(self.host.reply({'owner': 'different-window', 'id': 'request', 'ok': True, 'value': []}))
        self.assertFalse(event.is_set())
        self.assertEqual(len(self.host.events('correct-window')), 1)
        self.assertTrue(self.host.reply({'owner': 'correct-window', 'id': 'request', 'ok': True, 'value': []}))
        self.assertTrue(event.is_set())
        self.assertFalse(self.host.reply({'owner': 'correct-window', 'id': 'request', 'ok': True, 'value': []}))

    def test_invalid_mode_does_not_persist(self):
        with self.assertRaises(ValueError):
            self.host.save_settings({'mode': 'UNRESTRICTED'})
        self.assertFalse((self.host.data / 'settings.json').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
