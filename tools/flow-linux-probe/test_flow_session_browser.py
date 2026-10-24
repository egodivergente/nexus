from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from playwright.async_api import async_playwright
from flow_session import sign_in, repair_project_page, saved_project, remember_project, preserve_recovery_draft
import json
from auto_auth import CredentialExpiredError


class SavedLoginTests(unittest.IsolatedAsyncioTestCase):
    def test_remembers_only_valid_existing_project_and_keeps_legacy_fallback(self):
        root=Path(self.tmp.name)
        self.assertIsNone(saved_project(root))
        remember_project(root,'https://accounts.google.com/signin')
        self.assertIsNone(saved_project(root))
        project='https://flow.google.com/project/known-project'
        remember_project(root,project)
        self.assertEqual(saved_project(root),project)
        self.assertFalse((root/'keepalive-state.json').exists())

    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pw = await async_playwright().start()
        kwargs = {'channel': 'msedge'} if sys.platform == 'win32' else {'executable_path': '/usr/bin/chromium'}
        self.browser = await self.pw.chromium.launch(headless=True, **kwargs)
        self.page = await self.browser.new_page()
        self.visited = []

    async def asyncTearDown(self):
        await self.browser.close()
        await self.pw.stop()
        self.tmp.cleanup()

    async def fixture(self, route):
        url = route.request.url
        self.visited.append(url)
        if '/about' in url:
            html = '''<button onclick="location.href='https://accounts.google.com/signin/identifier'">Iniciar sesión</button>'''
        elif '/identifier' in url:
            html = '''<form action="/signin/challenge/pwd"><input type="email" value="fixture@example.invalid"><button>Siguiente</button></form>'''
        elif '/challenge/pwd' in url:
            html = '''<form action="https://flow.google.com/"><input type="password" value="fixture-only"><button>Siguiente</button></form>'''
        else:
            html = '<button>Nuevo proyecto</button>'
        await route.fulfill(content_type='text/html; charset=utf-8', body=html)

    async def test_button_entry_and_two_enters_complete_saved_login_without_reading_credential(self):
        await self.page.route('**/*', self.fixture)
        await self.page.goto('https://flow.google.com/about')
        with patch('flow_session.load_credential') as credential:
            result = await sign_in(self.page, Path(self.tmp.name))
        self.assertEqual(result, 'signed_in')
        credential.assert_not_called()
        self.assertTrue(any('/challenge/pwd' in u for u in self.visited))

    async def test_current_google_flow_entry_label_completes_saved_login(self):
        async def current(route):
            if '/about' in route.request.url:
                await route.fulfill(content_type='text/html; charset=utf-8', body='''<button onclick="location.href='https://accounts.google.com/signin/identifier'">Create with Google Flow</button>''')
            else:
                await self.fixture(route)
        await self.page.route('**/*', current)
        await self.page.goto('https://flow.google.com/about')
        with patch('flow_session.load_credential') as credential:
            result = await sign_in(self.page, Path(self.tmp.name))
        self.assertEqual(result, 'signed_in')
        credential.assert_not_called()

    async def test_late_rendered_entry_is_waited_for_before_declaring_missing_control(self):
        async def delayed(route):
            if '/about' in route.request.url:
                await route.fulfill(content_type='text/html; charset=utf-8', body='''<script>setTimeout(()=>{const b=document.createElement('button');b.textContent='Iniciar sesión';b.onclick=()=>location.href='https://accounts.google.com/signin/identifier';document.body.append(b)},700)</script>''')
            else:
                await self.fixture(route)
        await self.page.route('**/*', delayed)
        await self.page.goto('https://flow.google.com/about')
        with patch('flow_session.load_credential') as credential:
            result = await sign_in(self.page, Path(self.tmp.name))
        self.assertEqual(result, 'signed_in')
        credential.assert_not_called()

    async def test_empty_autofill_uses_existing_encrypted_login(self):
        async def empty(route):
            if '/challenge/pwd' in route.request.url:
                await route.fulfill(content_type='text/html; charset=utf-8', body='''<form action="https://flow.google.com/"><input type="password"><button>Siguiente</button></form>''')
            else:
                await self.fixture(route)
        await self.page.route('**/*', empty)
        await self.page.goto('https://accounts.google.com/signin/challenge/pwd')
        with patch('flow_session.load_credential', return_value={'email': 'fixture', 'password': 'fixture'}) as credential:
            result = await sign_in(self.page, Path(self.tmp.name))
        self.assertEqual(result, 'signed_in')
        credential.assert_called_once()

    async def test_verification_never_reads_or_submits_password(self):
        await self.page.route('**/*', self.fixture)
        await self.page.goto('https://accounts.google.com/signin/challenge/totp')
        with patch('flow_session.load_credential') as credential:
            result = await sign_in(self.page, Path(self.tmp.name))
        self.assertEqual(result, 'verification_required')
        credential.assert_not_called()
        self.assertEqual(len(self.visited), 1)

    async def test_expired_credential_stops_before_submitting_password(self):
        async def empty(route):
            await route.fulfill(content_type='text/html', body='<form><input type="password"><button>Siguiente</button></form>')
        await self.page.route('**/*', empty)
        await self.page.goto('https://accounts.google.com/signin/challenge/pwd')
        with patch('flow_session.load_credential', side_effect=CredentialExpiredError):
            self.assertEqual(await sign_in(self.page, Path(self.tmp.name)), 'credential_expired')
        self.assertFalse((Path(self.tmp.name) / 'session-login-attempt.json').exists())

    async def test_google_expired_session_page_preserves_expired_credential_boundary(self):
        await self.page.route('**/*',self.fixture)
        await self.page.goto('https://accounts.google.com/info/sessionexpired')
        with patch('flow_session.load_credential',side_effect=CredentialExpiredError):
            self.assertEqual(await sign_in(self.page,Path(self.tmp.name)),'credential_expired')
        self.assertEqual(len(self.visited),1)

    async def test_lookalike_domain_never_uses_saved_login(self):
        await self.page.route('**/*', self.fixture)
        await self.page.goto('https://accounts.google.com.evil.invalid/signin/identifier')
        with patch('flow_session.load_credential') as credential:
            result = await sign_in(self.page, Path(self.tmp.name))
        self.assertEqual(result, 'unexpected_destination')
        credential.assert_not_called()

    async def test_missing_editor_reloads_once_but_preserves_manual_draft(self):
        visits = []

        async def fixture(route):
            visits.append(route.request.url)
            await route.fulfill(content_type='text/html; charset=utf-8', body='<p>stale project shell</p>')

        await self.page.route('**/*', fixture)
        await self.page.goto('https://flow.google.com/project/fixture-project')
        self.assertEqual(await repair_project_page(self.page), 'reloaded')
        self.assertEqual(len(visits), 2)
        await self.page.set_content('<textarea>private manual draft</textarea>')
        self.assertEqual(await repair_project_page(self.page), 'flow_composer_not_empty')
        self.assertEqual(len(visits), 2)

    async def text_draft_fixture(self):
        html = '''<section><div contenteditable="true" aria-label="¿Qué quieres crear?"></div>
        <button aria-label="Añadir ingredientes a ventana para peticiones">+</button>
        <button aria-label="Iniciar generación">Generate</button></section>'''
        async def fixture(route):
            await route.fulfill(content_type='text/html; charset=utf-8', body=html)
        await self.page.route('**/*', fixture)
        await self.page.goto('https://flow.google.com/project/draft-fixture')
        return self.page.locator('[contenteditable="true"]')

    async def test_periodic_auth_refresh_preserves_text_draft_and_restores_it(self):
        composer = await self.text_draft_fixture()
        text = 'Mi borrador 😀\ntexto guardado'
        await composer.fill(text)
        root = Path(self.tmp.name)
        async with preserve_recovery_draft(self.page, root, True):
            self.assertEqual((await composer.inner_text()).strip(), '')
            self.assertEqual(await repair_project_page(self.page, refresh=True), 'reloaded')
        self.assertEqual(await composer.inner_text(), text)
        backup, = (root / 'flow-drafts').glob('*.json')
        self.assertEqual(json.loads(backup.read_text('utf-8'))['text'], text)

    async def test_new_manual_edit_after_refresh_wins_over_old_backup(self):
        composer = await self.text_draft_fixture()
        await composer.fill('previous draft')
        async with preserve_recovery_draft(self.page, Path(self.tmp.name), True):
            self.assertEqual(await repair_project_page(self.page, refresh=True), 'reloaded')
            await composer.fill('new manual edit')
        self.assertEqual(await composer.inner_text(), 'new manual edit')

    async def test_manual_reference_blocks_refresh_without_clearing_any_text(self):
        composer = await self.text_draft_fixture()
        await composer.fill('keep draft with reference')
        await self.page.evaluate('''() => {const img=document.createElement('img');img.src='data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';document.querySelector('section').append(img)}''')
        await self.page.wait_for_function('document.querySelector("img").complete && document.querySelector("img").naturalWidth>0')
        async with preserve_recovery_draft(self.page, Path(self.tmp.name), True):
            self.assertEqual(await repair_project_page(self.page, refresh=True), 'flow_composer_not_empty')
        self.assertEqual(await composer.inner_text(), 'keep draft with reference')

    async def test_unknown_reference_scope_keeps_draft_and_blocks_refresh(self):
        composer = await self.text_draft_fixture()
        await composer.fill('keep unknown controls')
        await self.page.locator('button[aria-label="Iniciar generación"]').evaluate('e=>e.remove()')
        async with preserve_recovery_draft(self.page, Path(self.tmp.name), True):
            self.assertEqual(await repair_project_page(self.page, refresh=True), 'flow_composer_not_empty')
        self.assertEqual(await composer.inner_text(), 'keep unknown controls')


if __name__ == '__main__':
    unittest.main()
