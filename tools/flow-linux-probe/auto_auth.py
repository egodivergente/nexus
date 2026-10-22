"""One bounded Google login using a user-scoped systemd encrypted credential."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
from urllib.parse import urlsplit


class CredentialExpiredError(ValueError):
    """The existing encrypted login is outside its authorized validity window."""


def google_login(url):
    parts = urlsplit(url)
    return parts.scheme == 'https' and parts.hostname == 'accounts.google.com'


def load_credential():
    directory = os.environ.get('CREDENTIALS_DIRECTORY')
    if directory:
        raw = (Path(directory) / 'flow-login').read_text(encoding='utf-8-sig')
    else:
        encrypted = Path.home() / '.local/share/nexus-flow-probe/flow-login.cred'
        if not encrypted.exists():
            return None
        if encrypted.stat().st_mode & 0o077:
            raise ValueError('Credential permissions must be private')
        # User manager cannot load host-key credentials on this Debian setup;
        # systemd-creds uses the supported user-scoped decrypt service instead.
        try:
            result = subprocess.run(['systemd-creds', 'decrypt', '--user', '--name=flow-login',
                                     str(encrypted), '-'], capture_output=True, check=True, timeout=10)
        except subprocess.CalledProcessError as error:
            if b'Outside of credential validity time window' in (error.stderr or b''):
                raise CredentialExpiredError('Stored login has expired') from None
            raise
        raw = result.stdout.decode('utf-8-sig')
    value = json.loads(raw)
    if not isinstance(value, dict) or not all(isinstance(value.get(k), str) and value[k] for k in ('email', 'password')):
        raise ValueError('Invalid credential format')
    return value


async def authenticate(page, root):
    credential = load_credential()
    if credential is None:
        return {'status': 'credential_required'}
    attempt = root / 'last-auth-attempt.json'
    if attempt.exists():
        elapsed = time.time() - json.loads(attempt.read_text())['time']
        if elapsed < 900:
            return {'status': 'cooldown'}
    # Record before sending anything, so interruption cannot cause a retry loop.
    attempt.write_text(json.dumps({'time': time.time()}), encoding='utf-8')
    if urlsplit(page.url).hostname == 'flow.google.com':
        link = page.locator('a').filter(has_text=re.compile(r'^\s*(Sign in|Iniciar sesi[oó]n)\s*$', re.I)).first
        href = await link.get_attribute('href', timeout=5000)
        if not google_login(href or ''):
            return {'status': 'needs_user'}
        await page.goto(href, wait_until='domcontentloaded', timeout=30000)
    if not google_login(page.url):
        return {'status': 'needs_user'}

    password_sent = False
    email_sent = False
    account_selected = False
    for _ in range(15):
        parts = urlsplit(page.url)
        if parts.scheme == 'https' and parts.hostname == 'flow.google.com' and not parts.path.startswith('/about'):
            return {'status': 'login_submitted'}  # Caller must independently verify the workspace.
        if not google_login(page.url):
            return {'status': 'needs_user', 'reason': 'unexpected_destination'}
        if any(s in parts.path for s in ('/rejected', '/denied')):
            return {'status': 'needs_user', 'reason': 'browser_rejected'}
        if '/consent' in parts.path:
            return {'status': 'needs_user', 'reason': 'consent_required'}
        if '/challenge/' in parts.path and '/challenge/pwd' not in parts.path:
            return {'status': 'needs_user', 'reason': 'verification_required'}
        password = page.locator('input[type="password"]')
        email = page.locator('input[type="email"]')
        if not password_sent and await password.count() and await password.first.is_visible():
            await password.first.fill(credential['password'])
            password_sent = True
            await page.get_by_role('button', name=re.compile(r'^(Next|Siguiente)$')).click(timeout=5000)
        elif not email_sent and await email.count() and await email.first.is_visible():
            await email.first.fill(credential['email'])
            email_sent = True
            await page.get_by_role('button', name=re.compile(r'^(Next|Siguiente)$')).click(timeout=5000)
        elif '/accountchooser' in parts.path and not account_selected:
            await page.get_by_text(credential['email'], exact=True).first.click(timeout=5000)
            account_selected = True
        await page.wait_for_timeout(1000)
    return {'status': 'needs_user'}
