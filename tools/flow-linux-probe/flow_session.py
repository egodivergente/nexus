"""Bounded recovery of the existing private Flow browser; no generation actions."""
import asyncio
from contextlib import asynccontextmanager
import json
from pathlib import Path
import re
import time
from urllib.parse import urlsplit

from auto_auth import load_credential
from flow_references import COMPOSER

CDP = 'http://127.0.0.1:9229'
SIGN_IN = re.compile(r'(Sign in|Log in|Iniciar sesi[oó]n|Acceder|Create with Flow|Crear con Flow)', re.I)
HUMAN = {'verification_required', 'consent_required', 'browser_rejected', 'invalid_credentials'}


def project_url(url):
    p = urlsplit(url)
    return p.scheme == 'https' and p.hostname == 'flow.google.com' and bool(re.fullmatch(r'/project/[A-Za-z0-9-]+/?', p.path))


def login_block(url):
    p = urlsplit(url)
    if p.scheme != 'https' or p.hostname != 'accounts.google.com':
        return None
    if any(s in p.path for s in ('/rejected', '/denied')):
        return 'browser_rejected'
    if '/consent' in p.path:
        return 'consent_required'
    if '/challenge/' in p.path and '/challenge/pwd' not in p.path:
        return 'verification_required'
    return None


async def first_visible(locator):
    for item in await locator.all():
        if await item.is_visible():
            return item
    return None


async def sign_in(page, root):
    """Use autofill first, then the user's existing encrypted credential if needed."""
    credential = None
    sent = set()
    started = time.monotonic()
    while time.monotonic() - started < 45:
        p = urlsplit(page.url)
        if p.scheme == 'https' and p.hostname == 'flow.google.com':
            if not p.path.startswith('/about'):
                return 'signed_in'
            control = await first_visible(page.get_by_role('link', name=SIGN_IN))
            control = control or await first_visible(page.get_by_role('button', name=SIGN_IN))
            if control is None:
                return 'sign_in_control_missing'
            if 'entry' in sent:
                await page.wait_for_timeout(500)
                continue
            sent.add('entry')
            await control.click(timeout=6000)
        elif p.scheme == 'https' and p.hostname == 'accounts.google.com':
            blocked = login_block(page.url)
            if blocked:
                return blocked
            # Only a boolean leaves the DOM. Neither autofilled value is read.
            field = await first_visible(page.locator('input[type="password"]'))
            kind = 'password'
            if field is None:
                field = await first_visible(page.locator('input[type="email"]'))
                kind = 'email'
            if field is not None and kind not in sent:
                if not await field.evaluate('e => e.value.length > 0'):
                    # Autofill can arrive after paint; focus also triggers the saved login.
                    await field.focus()
                    await page.wait_for_timeout(700)
                if not await field.evaluate('e => e.value.length > 0'):
                    try:
                        credential = credential or await asyncio.to_thread(load_credential)
                    except Exception:
                        return 'credential_unavailable'
                    if not credential:
                        return 'credential_required'
                    await field.fill(credential[kind])
                # Journal before sending, including a failed login, to avoid retry storms.
                attempt = root / 'session-login-attempt.json'
                if not sent.intersection({'email', 'password'}):
                    try:
                        previous = json.loads(attempt.read_text())
                    except (OSError, ValueError):
                        previous = {}
                    if time.time() - previous.get('time', 0) < 300:
                        return 'login_cooldown'
                    attempt.write_text(json.dumps({'time': time.time()}))
                    attempt.chmod(0o600)
                sent.add(kind)
                await field.press('Enter', timeout=6000)
            elif field is not None and kind in sent:
                invalid = await field.get_attribute('aria-invalid')
                if invalid == 'true':
                    return 'invalid_credentials'
            elif 'account' not in sent:
                accounts = page.locator('[data-identifier]')
                if await accounts.count() == 1:
                    await accounts.first.click(timeout=6000)
                    sent.add('account')
                elif await accounts.count() > 1:
                    try:
                        credential = credential or await asyncio.to_thread(load_credential)
                    except Exception:
                        return 'account_selection_required'
                    match = page.get_by_text(credential['email'], exact=True) if credential else None
                    if match is None or await match.count() != 1:
                        return 'account_selection_required'
                    await match.click(timeout=6000)
                    sent.add('account')
        else:
            return 'unexpected_destination'
        await page.wait_for_timeout(400)
    return 'login_timeout'


async def repair_project_page(page, refresh=False):
    """A stale SPA can retain a project URL without rendering its composer."""
    if not refresh and await first_visible(page.locator(COMPOSER)):
        return 'ready'
    draft = await page.locator('textarea,[contenteditable="true"]').evaluate_all(
        'es => es.some(e => ((e.value ?? e.innerText) || "").trim().length > 0)')
    if draft:
        return 'flow_composer_not_empty'
    # Called only during preparation, never when following a submitted generation.
    await page.reload(wait_until='domcontentloaded', timeout=20000)
    return 'reloaded'


@asynccontextmanager
async def session_lock(root):
    """Coordinate the timer and bridge without blocking the server event loop."""
    root.mkdir(parents=True, exist_ok=True)
    lock = (root / 'session-recovery.lock').open('a')
    try:
        try:
            import fcntl
        except ImportError:
            fcntl = None
        if fcntl:
            acquired = False
            for _ in range(100):
                try:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    acquired = True
                    break
                except BlockingIOError:
                    await asyncio.sleep(0.5)
            if not acquired:
                raise TimeoutError('session_recovery_busy')
        yield
    finally:
        lock.close()


async def ensure_ready(root):
    from playwright.async_api import async_playwright
    async with session_lock(root), async_playwright() as pw:
        browser = await pw.chromium.connect_over_cdp(CDP, timeout=7000)
        pages = [p for c in browser.contexts for p in c.pages if not p.is_closed()]
        projects = [p for p in pages if project_url(p.url)]
        if len(projects) > 1:
            return 'flow_project_ambiguous'
        health = root / 'session-checked-at.json'
        try:
            checked = json.loads(health.read_text()).get('time', 0)
        except (OSError, ValueError):
            checked = 0
        if projects:
            # A stale SPA can still show its old editor after Google expires the login.
            # Refresh at most once per five minutes, only before a new submission and
            # only when there is no draft. Never reload while following a generation.
            repaired = await repair_project_page(projects[0], refresh=time.time() - checked > 300)
            if repaired != 'reloaded':
                return repaired
        target = projects[0] if projects else next((p for p in pages if urlsplit(p.url).hostname == 'accounts.google.com'), None)
        target = target or next((p for p in pages if urlsplit(p.url).hostname == 'flow.google.com'), None)
        target = target or next((p for p in pages if p.url == 'about:blank'), None)
        if target is None:
            target = await browser.contexts[0].new_page()
        try:
            state = json.loads((root / 'keepalive-state.json').read_text())
        except (OSError, ValueError):
            state = {}
        last_project = state.get('last_project', '')
        if target.url == 'about:blank':
            await target.goto(last_project if project_url(last_project) else 'https://flow.google.com/',
                              wait_until='domcontentloaded', timeout=20000)
        if urlsplit(target.url).hostname == 'accounts.google.com' or urlsplit(target.url).path.startswith('/about'):
            outcome = await sign_in(target, root)
            if outcome != 'signed_in':
                return outcome
        if not project_url(target.url):
            if not project_url(last_project):
                return 'flow_project_not_open'
            await target.goto(last_project, wait_until='domcontentloaded', timeout=20000)
        if not project_url(target.url):
            return 'auth_required'
        await target.locator(COMPOSER).first.wait_for(state='visible', timeout=35000)
        health.write_text(json.dumps({'time': time.time()}))
        health.chmod(0o600)
        return 'ready'
