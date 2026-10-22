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
    def __init__(self, url, gallery=False, composer=False, submit=None, errors=False):
        self.url, self.gallery = url, gallery
        self.composer = composer
        self.submit = composer if submit is None else submit
        self.errors = errors
        self.selectors = []

    def get_by_role(self, *args, **kwargs):
        return Locator(self.gallery)

    def get_by_text(self, pattern):
        return Locator(self.errors if 'Failed to load' in pattern.pattern else False)

    def locator(self, selector):
        self.selectors.append(selector)
        return Locator(self.submit if 'Iniciar generación' in selector else self.composer)


class SessionReuseTests(unittest.IsolatedAsyncioTestCase):
    def context(self, pages, account_url='https://myaccount.google.com/', code=200):
        return SimpleNamespace(pages=pages, request=SimpleNamespace(get=AsyncMock(
            return_value=SimpleNamespace(status=code, url=account_url))))

    async def test_gallery_and_open_project_are_recognized(self):
        for page in [Page('https://flow.google.com/', gallery=True),
                     Page('https://flow.google.com/project/example', composer=True, submit=True)]:
            context = self.context([page])
            self.assertEqual(await recovery.context_status(context), 'connected')
            context.request.get.assert_not_awaited()

    async def test_project_url_alone_or_hidden_controls_are_not_success(self):
        for page in [Page('https://flow.google.com/project/example'),
                     Page('https://flow.google.com/project/example', composer=True, submit=False),
                     Page('https://flow.google.com/', gallery=True, errors=True)]:
            context = self.context([page])
            self.assertEqual(await recovery.context_status(context), 'unverified')
            context.request.get.assert_not_awaited()

    async def test_open_project_checks_the_native_flow_composer(self):
        page = Page('https://flow.google.com/project/example', composer=True, submit=True)
        self.assertEqual(await recovery.context_status(self.context([page])), 'connected')
        self.assertTrue(any(recovery.COMPOSER in selector for selector in page.selectors))
        self.assertTrue(any('button[aria-label="Iniciar generación"]' in selector for selector in page.selectors))

    async def test_active_project_is_authoritative_without_an_account_probe(self):
        page = Page('https://flow.google.com/project/example', composer=True, submit=True)
        context = self.context([page], 'https://accounts.google.com/signin', 200)
        self.assertEqual(await recovery.context_status(context), 'connected')
        context.request.get.assert_not_awaited()

    async def test_no_navigation_or_account_request_for_unrelated_or_blank_pages(self):
        for url in ['about:blank', 'https://other.test/project/example', 'http://flow.google.com/']:
            context = self.context([Page(url, gallery=True)])
            self.assertEqual(await recovery.context_status(context), 'unverified')
            context.request.get.assert_not_awaited()

    async def test_stale_signin_tab_does_not_override_connected_project(self):
        context = self.context([Page('https://accounts.google.com/signin'),
                                Page('https://flow.google.com/project/example', composer=True, submit=True)])
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
        recovery.FLOW_JOBS.clear()
        self.client = TestClient(recovery.app, base_url=recovery.ORIGIN)

    def tearDown(self):
        self.client.close()
        for p in reversed(self.patches):
            p.stop()
        self.tmp.cleanup()

    def unlock(self):
        return self.client.post('/unlock', json={'token': 'private-test-token'})

    def test_private_routes_require_auth(self):
        for path in ['/status', '/vnc.html', '/app/ui.js', '/api/flow/status', '/api/flow/jobs', '/api/byteplus/usage']:
            self.assertEqual(self.client.get(path).status_code, 401)

    def test_byteplus_reuses_private_auth_and_validates_period(self):
        import byteplus_admin
        headers = {'Authorization': 'Bearer private-test-token'}
        with patch.object(byteplus_admin, 'snapshot', return_value={'schema': 1, 'period': 'day'}):
            self.assertEqual(self.client.get('/api/byteplus/usage', headers=headers).json()['schema'], 1)
            self.assertEqual(self.client.get('/api/byteplus/usage?period=invalid', headers=headers).status_code, 400)
            self.assertEqual(self.client.get('/api/byteplus/usage', headers=dict(headers, Origin='https://other.test')).status_code, 403)

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

    def test_magicdns_host_is_allowed_but_other_hosts_are_not(self):
        headers = {'Host': f'{recovery.MAGICDNS_HOST}:{recovery.PORT}',
                   'Origin': f'http://{recovery.MAGICDNS_HOST}:{recovery.PORT}',
                   'Authorization': 'Bearer private-test-token'}
        self.assertEqual(self.client.get('/status', headers=headers).status_code, 200)
        self.assertEqual(self.client.get('/status', headers={**headers, 'Host': 'not-asus.example:8899'}).status_code, 403)

    def test_flow_status_is_private_and_uses_the_capability_adapter(self):
        response_body = {'bridge_status': 'connected', 'generation_ready': True,
                         'prompt_limit': 4000, 'expected_outputs': ['image', 'video'],
                         'cost_source': 'flow_current_project'}
        with patch.object(recovery, 'flow_api_status', AsyncMock(return_value=response_body)) as status:
            response = self.client.get('/api/flow/status', headers={'Authorization': 'Bearer private-test-token'})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), response_body)
        status.assert_awaited_once()

    def test_flow_submit_validates_before_any_ui_automation(self):
        valid_id = 'client-request-123456'
        job = {'job_id': 'server-job-1234567', 'client_request_id': valid_id,
               'state': 'submitted', 'expected_output': 'image',
               'created_at': 1, 'updated_at': 1, 'baseline_downloads': [],
               'new_downloads': [], 'error': None}
        with patch.object(recovery, 'submit_flow_generation', AsyncMock(return_value=(job, False))) as submit:
            rejected = self.client.post('/api/flow/jobs', headers={'Authorization': 'Bearer private-test-token'},
                                        json={'prompt': 'hola', 'expected_output': 'image',
                                              'client_request_id': valid_id})
            accepted = self.client.post('/api/flow/jobs', headers={'Authorization': 'Bearer private-test-token'},
                                        json={'prompt': 'hola', 'expected_output': 'image',
                                              'client_request_id': valid_id, 'confirmed': True})
        self.assertEqual(rejected.status_code, 400)
        self.assertEqual(rejected.json(), {'error': 'confirmation_required'})
        self.assertEqual(accepted.status_code, 201)
        self.assertEqual(accepted.json()['job_id'], job['job_id'])
        submit.assert_awaited_once_with('hola', 'image', valid_id, None, 1, 0, defer=True)

    def test_flow_job_poll_uses_mocked_bridge_without_a_browser(self):
        job = {'job_id': 'server-job-1234567', 'client_request_id': 'client-request-123456',
               'state': 'completed', 'expected_output': 'video',
               'created_at': 1, 'updated_at': 2, 'baseline_downloads': ['before'],
               'new_downloads': [{'index': 1, 'signature': 'after'}], 'error': None}
        with patch.object(recovery, 'get_flow_job_status', AsyncMock(return_value=job)) as poll:
            response = self.client.get('/api/flow/jobs/server-job-1234567',
                                       headers={'Authorization': 'Bearer private-test-token'})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['state'], 'completed')
        self.assertTrue(response.json()['download_available'])
        poll.assert_awaited_once_with('server-job-1234567')

    def test_flow_download_is_authenticated_and_delegates_without_a_browser(self):
        with patch.object(recovery, 'download_flow_result', AsyncMock(
                return_value=recovery.JSONResponse({'download': 'mocked'}))) as download:
            response = self.client.get('/api/flow/jobs/server-job-1234567/downloads/0',
                                       headers={'Authorization': 'Bearer private-test-token'})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {'download': 'mocked'})
        download.assert_awaited_once_with('server-job-1234567', 0)

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


class FlowJobTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.patch = patch.object(recovery, 'ROOT', self.root)
        self.patch.start()
        recovery.FLOW_JOBS.clear()

    def tearDown(self):
        recovery.FLOW_JOBS.clear()
        self.patch.stop()
        self.tmp.cleanup()

    async def test_idempotency_journal_never_contains_prompt(self):
        request_id = 'client-request-123456'
        prompt = 'texto privado que no debe persistirse'
        with patch.object(recovery, 'drive_flow_submission', AsyncMock(return_value=['a' * 64])) as drive:
            first, replayed = await recovery.submit_flow_generation(prompt, 'image', request_id)
            second, replayed_second = await recovery.submit_flow_generation(prompt, 'image', request_id)
        self.assertFalse(replayed)
        self.assertTrue(replayed_second)
        self.assertEqual(first['job_id'], second['job_id'])
        self.assertEqual(second['state'], 'submitted')
        drive.assert_awaited_once()
        self.assertEqual(drive.await_args.args[:3], (prompt, None, 1))
        journal = (self.root / 'flow-jobs.json').read_text()
        self.assertNotIn(prompt, journal)
        self.assertNotIn('token', journal.lower())
        recovery.FLOW_JOBS.clear()
        recovery.load_flow_jobs()
        self.assertEqual((await recovery.flow_job(first['job_id']))['state'], 'submitted')

    async def test_uncertain_submit_is_not_automatically_retried(self):
        request_id = 'client-request-123456'
        with patch.object(recovery, 'drive_flow_submission', AsyncMock(side_effect=recovery.FlowClickUncertain())) as drive:
            first, replayed = await recovery.submit_flow_generation('texto', 'video', request_id)
            second, replayed_second = await recovery.submit_flow_generation('texto', 'video', request_id)
        self.assertFalse(replayed)
        self.assertEqual(first['state'], 'uncertain')
        self.assertTrue(replayed_second)
        self.assertEqual(second['state'], 'uncertain')
        drive.assert_awaited_once()

    async def test_changed_or_duplicate_download_controls_are_ambiguous(self):
        job = {'baseline_downloads': ['old-control']}
        additions, error = recovery.new_downloads(job, [
            {'index': 0, 'signature': 'old-control'},
            {'index': 1, 'signature': 'old-control'},
        ])
        self.assertIsNone(additions)
        self.assertEqual(error, 'download_ambiguous')
        additions, error = recovery.new_downloads(job, [{'index': 0, 'signature': 'other-control'}])
        self.assertIsNone(additions)
        self.assertEqual(error, 'download_controls_changed')

    async def test_visible_download_keeps_its_raw_locator_index(self):
        expected = {'index': 3, 'signature': 'visible-output'}
        # Index 1 represents a hidden matching button omitted by download_controls.
        controls = [{'index': 0, 'signature': 'old-output'},
                    {'index': 3, 'signature': 'visible-output'}]
        self.assertTrue(recovery.download_control_matches(expected, controls))
        self.assertFalse(recovery.download_control_matches(
            {'index': 1, 'signature': 'visible-output'}, controls))

    async def test_gallery_video_job_does_not_return_an_image(self):
        from flow_gallery import controls
        url = 'https://flow.google.com/project/test-project'
        baseline = controls(url, [])
        with patch.object(recovery, 'drive_flow_submission', AsyncMock(return_value=[c['signature'] for c in baseline])):
            job, _ = await recovery.submit_flow_generation('video', 'video', 'video-request-123456')
        current = controls(url, [{'media_id': 'image-output-1234', 'kind': 'image'},
                                 {'media_id': 'video-output-5678', 'kind': 'video'}])
        with patch.object(recovery, 'with_flow_project_page', AsyncMock(return_value=current)):
            result = await recovery.refresh_flow_job(job)
        self.assertEqual('completed', result['state'])
        self.assertEqual(['video-output-5678'], [item['media_id'] for item in result['new_downloads']])


if __name__ == '__main__':
    unittest.main()
