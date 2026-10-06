import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
import recovery


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'recovery-token').write_text('private-test-token')
        (self.root / 'login-access').write_text('testpass')
        self.patches = [patch.object(recovery, 'ROOT', self.root),
                        patch.object(recovery, 'TOKEN_FILE', self.root / 'recovery-token'),
                        patch.object(recovery, 'start_browser', AsyncMock(return_value=True))]
        for p in self.patches:
            p.start()
        recovery.SESSIONS.clear()
        self.client = TestClient(recovery.app, base_url=recovery.ORIGIN)

    def tearDown(self):
        self.client.close()
        for p in reversed(self.patches):
            p.stop()
        self.tmp.cleanup()

    def unlock(self):
        return self.client.post('/unlock', json={'token': 'private-test-token'})

    def test_private_routes_require_auth(self):
        for path in ['/status', '/vnc.html', '/app/ui.js']:
            self.assertEqual(self.client.get(path).status_code, 401)

    def test_origin_and_host_rejected(self):
        self.assertEqual(self.client.get('/', headers={'Origin': 'https://other.test'}).status_code, 403)
        self.assertEqual(self.client.get('/', headers={'Host': 'other.test'}).status_code, 403)

    def test_bad_and_unicode_tokens(self):
        for value in ['', 'invalid', '\u00e1']:
            self.assertEqual(self.client.post('/unlock', json={'token': value}).status_code, 401)
        recovery.start_browser.assert_not_awaited()

    def test_invalid_or_large_body(self):
        self.assertEqual(self.client.post('/unlock', content='{').status_code, 400)
        self.assertEqual(self.client.post('/unlock', content='a'*1025).status_code, 413)

    def test_unlock_lock_keeps_browser(self):
        result = self.unlock()
        self.assertEqual(result.status_code, 200)
        self.assertIn('HttpOnly', result.headers['set-cookie'])
        self.assertIn('SameSite=strict', result.headers['set-cookie'])
        self.assertEqual(self.client.get('/status').status_code, 200)
        with patch.object(recovery, 'BROWSER') as browser:
            self.assertEqual(self.client.post('/lock').status_code, 200)
            browser.terminate.assert_not_called()
        self.assertEqual(self.client.get('/status').status_code, 401)

    def test_expired_session(self):
        self.unlock()
        for sid in recovery.SESSIONS:
            recovery.SESSIONS[sid] = 0
        self.assertEqual(self.client.get('/status').status_code, 401)

    def test_bearer_requires_prefix(self):
        self.assertEqual(self.client.get('/status', headers={'Authorization': 'private-test-token'}).status_code, 401)
        self.assertEqual(self.client.get('/status', headers={'Authorization': 'Bearer private-test-token'}).status_code, 200)

    def test_busy_browser(self):
        recovery.start_browser.return_value = False
        self.assertEqual(self.unlock().status_code, 409)
        self.assertFalse(recovery.SESSIONS)

    def test_websocket_denies_unauthenticated(self):
        with self.assertRaises(WebSocketDisconnect):
            with self.client.websocket_connect('/websockify', headers={'Origin': recovery.ORIGIN}):
                pass

    def test_static_traversal_denied(self):
        self.unlock()
        self.assertEqual(self.client.get('/%2e%2e/%2e%2e/etc/passwd').status_code, 404)


if __name__ == '__main__':
    unittest.main()
