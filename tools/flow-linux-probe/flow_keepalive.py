"""Keep the ASUS Flow browser usable using the existing Chromium and a bounded saved sign-in.

Run every few minutes from nexus-flow-keepalive.timer. It attaches to the
already-running browser over loopback CDP and, after a grace period:
- reopens the last known Flow project when Flow drops back to its gallery;
- when Flow is signed out, makes at most one sign-in attempt every RELOGIN_COOLDOWN with
  the password the user saved in this Chromium (autofill): it completes the saved
  account and password steps, and stops on any verification, consent or rejection;
- otherwise parks the signed-out /about page on about:blank, because that public page
  alone saturates the ASUS CPU, and records that a human sign-in is needed.
It uses existing autofill or the private encrypted login, never creates projects or submits work.
`--close-browser` closes Chromium cleanly (cookies flushed) for ExecStop.
"""
import asyncio
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.parse import urlsplit

ROOT = Path.home() / '.local/share/nexus-flow-probe'
STATE_FILE = ROOT / 'keepalive-state.json'
CDP = 'http://127.0.0.1:9229'
REOPEN_AFTER = 30
PARK_AFTER = 5 * 60
RELOGIN_AFTER = 2 * 60
RELOGIN_COOLDOWN = 15 * 60
NEXT = re.compile(r'^(Next|Siguiente)$')


def classify(urls):
    """Return (kind, project_url) for the browser's page URLs."""
    kinds = set()
    for url in urls:
        parts = urlsplit(url)
        if parts.scheme == 'https' and parts.hostname == 'flow.google.com':
            if re.fullmatch(r'/project/[^/]+/?', parts.path):
                return 'project', url
            kinds.add('signed_out' if parts.path.startswith('/about') else 'gallery')
        elif parts.hostname == 'accounts.google.com':
            kinds.add('login')
    # A sign-in page has priority over a stale signed-out tab.
    for kind in ('login', 'signed_out', 'gallery'):
        if kind in kinds:
            return kind, None
    return 'blank', None


def decide(kind, project_url, state, now):
    """Return (action, new_state). action is None, 'reopen', 'relogin', 'wake' or 'park'."""
    state = dict(state)
    if kind != state.get('kind'):
        state['kind'], state['since'] = kind, now
    waited = now - state['since']
    if kind == 'project':
        state['last_project'], state['parked'] = project_url, False
        state.pop('relogin_result', None)
        return None, state
    if kind in ('login', 'gallery'):
        state['parked'] = False
    if (kind in ('signed_out', 'login') and waited >= RELOGIN_AFTER and not state.get('parked')
            and now - state.get('last_relogin', float('-inf')) >= RELOGIN_COOLDOWN
            and state.get('relogin_result') not in {'verification_required', 'invalid_credentials', 'consent_required', 'browser_rejected'}):
        # Recorded before acting, so a crash mid-attempt cannot cause a retry loop.
        state['last_relogin'] = now
        return 'relogin', state
    if kind == 'blank' and state.get('parked') and now - state.get('last_relogin', float('-inf')) >= RELOGIN_COOLDOWN:
        # Bring the signed-out page back so the next run can make the next attempt.
        state['parked'] = False
        return 'wake', state
    if kind == 'signed_out' and waited >= PARK_AFTER and not state.get('parked'):
        state['parked'] = True
        return 'park', state
    if (kind in ('gallery', 'blank') and state.get('last_project') and not state.get('parked')
            and waited >= REOPEN_AFTER):
        state['since'] = now
        return 'reopen', state
    return None, state


def load_state():
    try:
        return json.loads(STATE_FILE.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        return {}


def save_state(state):
    os.umask(0o077)
    STATE_FILE.write_text(json.dumps(state), encoding='utf-8')


def google_login(url):
    parts = urlsplit(url)
    return parts.scheme == 'https' and parts.hostname == 'accounts.google.com'


def login_outcome(url):
    """Classify a URL reached during sign-in: None means keep going."""
    parts = urlsplit(url)
    if parts.scheme == 'https' and parts.hostname == 'flow.google.com' and not parts.path.startswith('/about'):
        return 'signed_in'
    if not google_login(url):
        return 'unexpected_destination'
    if any(s in parts.path for s in ('/rejected', '/denied')):
        return 'browser_rejected'
    if '/consent' in parts.path:
        return 'consent_required'
    if '/challenge/' in parts.path and '/challenge/pwd' not in parts.path:
        return 'verification_required'
    return None


async def relogin(page):
    """Complete the saved sign-in, serialized with on-demand bridge recovery."""
    from flow_session import session_lock, sign_in
    async with session_lock(ROOT):
        return await sign_in(page, ROOT)


async def pages(browser):
    return [page for context in browser.contexts for page in context.pages]


async def check():
    from playwright.async_api import async_playwright
    async with async_playwright() as pw:
        try:
            browser = await pw.chromium.connect_over_cdp(CDP, timeout=10000)
        except Exception:
            print('browser_closed')
            return
        # Leaving this block only drops Playwright's connection, never browser.close().
        import flow_import
        all_pages = await pages(browser)
        # La pestaña de importación es temporal: se cierra si lleva un rato sin usarse y no cuenta como proyecto.
        await flow_import.close_idle_import_tabs(all_pages)
        open_pages = [page for page in all_pages if not page.is_closed() and not await flow_import.is_import_page(page)]
        kind, project_url = classify(page.url for page in open_pages)
        action, state = decide(kind, project_url, load_state(), time.time())
        if action == 'reopen':
            target = next((page for page in open_pages
                           if urlsplit(page.url).hostname == 'flow.google.com' or page.url == 'about:blank'), None)
            if target:
                await target.goto(state['last_project'], wait_until='domcontentloaded', timeout=45000)
        elif action == 'relogin':
            target = next((page for page in open_pages if urlsplit(page.url).hostname == 'accounts.google.com'), None)
            target = target or next((page for page in open_pages if urlsplit(page.url).hostname == 'flow.google.com'), None)
            try:
                result = await relogin(target) if target else 'no_flow_page'
            except Exception as error:
                result = 'error_' + type(error).__name__
            state['relogin_result'] = result
            print('relogin', result)
            if result == 'signed_in' and state.get('last_project'):
                await target.goto(state['last_project'], wait_until='domcontentloaded', timeout=45000)
        elif action == 'wake':
            target = next((page for page in open_pages if page.url == 'about:blank'), None)
            if target:
                await target.goto('https://flow.google.com/', wait_until='domcontentloaded', timeout=45000)
        elif action == 'park':
            for page in open_pages:
                if urlsplit(page.url).hostname == 'flow.google.com':
                    await page.goto('about:blank')
        state['status'] = 'needs_login' if state.get('parked') else classify(page.url for page in open_pages if not page.is_closed())[0]
        state['checked_at'] = time.time()
        save_state(state)
        print(kind, action or 'none')


async def close_browser():
    from playwright.async_api import async_playwright
    async with async_playwright() as pw:
        try:
            browser = await pw.chromium.connect_over_cdp(CDP, timeout=10000)
            session = await browser.new_browser_cdp_session()
            await session.send('Browser.close')
        except Exception:
            print('browser_not_reachable')
            return
    for _ in range(40):
        try:
            _, writer = await asyncio.open_connection('127.0.0.1', 9229)
            writer.close()
            await asyncio.sleep(0.5)
        except OSError:
            print('browser_closed_cleanly')
            return
    print('browser_close_timeout')


if __name__ == '__main__':
    asyncio.run(close_browser() if '--close-browser' in sys.argv else check())
