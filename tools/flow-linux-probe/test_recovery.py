import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
import recovery


class Locator:
    def __init__(self, shown):
        self.shown = shown

    async def all(self):
        return [self]

    async def is_visible(self):
        return self.shown


class Page:
    def __init__(self, url, gallery=False, composer=False, workspace=False, errors=False):
        self.url, self.gallery = url, gallery
        self.composer, self.workspace, self.errors = composer, workspace, errors

    def get_by_role(self, *args, **kwargs):
        return Locator(self.gallery)

    def get_by_text(self, pattern):
        return Locator(self.errors if 'Failed to load' in pattern.pattern else self.workspace)

    def locator(self, selector):
        return Locator(self.composer)


class SessionReuseTests(unittest.IsolatedAsyncioTestCase):
    def context(self, pages, account_url='https://myaccount.google.com/', code=200):
        return SimpleNamespace(pages=pages, request=SimpleNamespace(get=AsyncMock(
            return_value=SimpleNamespace(status=code, url=account_url))))

    async def test_gallery_and_open_project_are_recognized(self):
        for page in [Page('https://flow.google.com/', gallery=True),
                     Page('https://flow.google.com/project/example', composer=True, workspace=True)]:
            context = self.context([page])
            self.assertEqual(await recovery.context_status(context), 'connected')
            context.request.get.assert_awaited_once()

    async def test_project_url_alone_or_hidden_controls_are_not_success(self):
        for page in [Page('https://flow.google.com/project/example'),
                     Page('https://flow.google.com/project/example', composer=True),
                     Page('https://flow.google.com/', gallery=True, errors=True)]:
            context = self.context([page])
            self.assertEqual(await recovery.context_status(context), 'unverified')
            context.request.get.assert_not_awaited()

    async def test_google_server_must_confirm_authentication(self):
        page = Page('https://flow.google.com/project/example', composer=True, workspace=True)
        for url, code, expected in [('https://accounts.google.com/signin', 200, 'auth_required'),
                                    ('https://myaccount.google.com/', 503, 'unverified'),
                                    ('https://other.test/', 200, 'unverified')]:
            self.assertEqual(await recovery.context_status(self.context([page], url, code)), expected)

    async def test_no_navigation_or_account_request_for_unrelated_or_blank_pages(self):
        for url in ['about:blank', 'https://other.test/project/example', 'http://flow.google.com/']:
            context = self.context([Page(url, gallery=True)])
            self.assertEqual(await recovery.context_status(context), 'unverified')
            context.request.get.assert_not_awaited()

    async def test_stale_signin_tab_does_not_override_connected_project(self):
        context = self.context([Page('https://accounts.google.com/signin'),
                                Page('https://flow.google.com/project/example', composer=True, workspace=True)])
        self.assertEqual(await recovery.context_status(context), 'connected')


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'recovery-token').write_text('private-test-token')
        (self.root / 'login-access').write_text('testpass')
        self.patches = [patch.object(recovery, 'ROOT', self.root),
                        patch.object(recovery, 'TOKEN_FILE', self.root / 'recovery-token'),
                        patch.object(recovery, 'status', AsyncMock(return_value={'status': 'unverified'})),
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

    def test_websocket_does_not_force_unrequested_protocol(self):
        self.unlock()
        with patch.object(recovery.websockets, 'connect', side_effect=OSError):
            with self.client.websocket_connect('/websockify', headers={'Origin': recovery.ORIGIN, 'Host': f'{recovery.HOST}:{recovery.PORT}', 'Cookie': f'nexus_flow={self.client.cookies.get("nexus_flow")}'}) as ws:
                self.assertIsNone(ws.accepted_subprotocol)

    def test_websocket_negotiates_requested_binary(self):
        self.unlock()
        with patch.object(recovery.websockets, 'connect', side_effect=OSError):
            with self.client.websocket_connect('/websockify', headers={'Origin': recovery.ORIGIN, 'Host': f'{recovery.HOST}:{recovery.PORT}', 'Cookie': f'nexus_flow={self.client.cookies.get("nexus_flow")}'}, subprotocols=['binary']) as ws:
                self.assertEqual(ws.accepted_subprotocol, 'binary')

    def test_static_traversal_denied(self):
        self.unlock()
        self.assertEqual(self.client.get('/%2e%2e/%2e%2e/etc/passwd').status_code, 404)


if __name__ == '__main__':
    unittest.main()
