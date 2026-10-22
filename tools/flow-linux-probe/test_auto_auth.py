import json
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
from types import SimpleNamespace

from auto_auth import authenticate, google_login, load_credential


class AuthTests(unittest.IsolatedAsyncioTestCase):
    def test_origin_is_exact(self):
        self.assertTrue(google_login('https://accounts.google.com/v3/signin/identifier'))
        for url in ['http://accounts.google.com/', 'https://accounts.google.com.evil.invalid/',
                    'https://accounts.google.com@evil.invalid/', 'https://flow.google.com/', '']:
            self.assertFalse(google_login(url))

    def test_credential_validation(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {'CREDENTIALS_DIRECTORY': folder}):
            path = Path(folder) / 'flow-login'
            path.write_text(json.dumps({'email': 'test@example.invalid', 'password': 'fixture'}))
            self.assertEqual(load_credential()['email'], 'test@example.invalid')
            path.write_text(json.dumps({'email': 'test@example.invalid', 'password': ''}))
            with self.assertRaises(ValueError):
                load_credential()

    async def test_no_credential_never_touches_browser(self):
        with patch.dict(os.environ, {}, clear=True), patch('auto_auth.Path.home', return_value=Path('fixture-home')), patch('auto_auth.Path.exists', return_value=False):
            self.assertEqual(await authenticate(None, None), {'status': 'credential_required'})

    async def test_cooldown_never_touches_browser(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'last-auth-attempt.json').write_text(json.dumps({'time': time.time()}))
            with patch('auto_auth.load_credential', return_value={'email': 'fixture', 'password': 'fixture'}):
                self.assertEqual(await authenticate(None, root), {'status': 'cooldown'})

    async def test_rejection_does_not_type_credentials(self):
        with tempfile.TemporaryDirectory() as folder, patch('auto_auth.load_credential', return_value={'email': 'fixture', 'password': 'fixture'}):
            page = SimpleNamespace(url='https://accounts.google.com/v3/signin/rejected')
            result = await authenticate(page, Path(folder))
            self.assertEqual(result, {'status': 'needs_user', 'reason': 'browser_rejected'})

    async def test_second_factor_is_not_automated(self):
        with tempfile.TemporaryDirectory() as folder, patch('auto_auth.load_credential', return_value={'email': 'fixture', 'password': 'fixture'}):
            page = SimpleNamespace(url='https://accounts.google.com/v3/signin/challenge/totp')
            result = await authenticate(page, Path(folder))
            self.assertEqual(result, {'status': 'needs_user', 'reason': 'verification_required'})


if __name__ == '__main__':
    unittest.main()
