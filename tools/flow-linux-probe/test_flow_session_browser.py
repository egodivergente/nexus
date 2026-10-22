from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from playwright.async_api import async_playwright
from flow_session import sign_in, repair_project_page


class SavedLoginTests(unittest.IsolatedAsyncioTestCase):
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


if __name__ == '__main__':
    unittest.main()
