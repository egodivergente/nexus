"""Keep the ASUS Flow browser usable without a second Chromium or any login automation.

Run every few minutes from nexus-flow-keepalive.timer. It only attaches to the
already-running browser over loopback CDP and, after a grace period:
- reopens the last known Flow project when Flow drops back to its gallery;
- when Flow is signed out, makes at most one sign-in attempt every RELOGIN_COOLDOWN with
  the password the user saved in this Chromium (autofill): it only clicks the saved
  account and Next, and stops on any verification, consent or rejection;
- otherwise parks the signed-out /about page on about:blank, because that public page
  alone saturates the ASUS CPU, and records that a human sign-in is needed.
It never types or reads credentials, creates projects, reloads an open project or submits work.
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
REOPEN_AFTER = 10 * 60
PARK_AFTER = 20 * 60
RELOGIN_AFTER = 2 * 60
RELOGIN_COOLDOWN = 6 * 3600
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
    # A sign-in in progress is never interrupted.
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
        return None, state
    if kind in ('login', 'gallery'):
        state['parked'] = False
    if (kind == 'signed_out' and waited >= RELOGIN_AFTER and not state.get('parked')
            and now - state.get('last_relogin', float('-inf')) >= RELOGIN_COOLDOWN):
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
    """One autofill sign-in: click the saved account and Next; never type anything."""
    if urlsplit(page.url).hostname == 'flow.google.com':
        link = page.locator('a').filter(has_text=re.compile(r'^\s*(Sign in|Iniciar sesi[oó]n)\s*$', re.I)).first
        href = await link.get_attribute('href', timeout=5000)
        if not google_login(href or ''):
            return 'sign_in_link_not_found'
        await page.goto(href, wait_until='domcontentloaded', timeout=30000)
    clicked = set()
    for _ in range(20):
        outcome = login_outcome(page.url)
        if outcome:
            return outcome
        chooser = page.locator('[data-identifier]')
        password = page.locator('input[type="password"]')
        email = page.locator('input[type="email"]')
        if 'account' not in clicked and await chooser.count() == 1:
            await chooser.first.click(timeout=5000)
            clicked.add('account')
        elif 'password' not in clicked and await password.count() and await password.first.is_visible():
            await page.get_by_role('button', name=NEXT).click(timeout=5000)
            clicked.add('password')
        elif 'email' not in clicked and await email.count() and await email.first.is_visible():
            await page.get_by_role('button', name=NEXT).click(timeout=5000)
            clicked.add('email')
        await page.wait_for_timeout(1500)
    return 'needs_user'


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
        open_pages = await pages(browser)
        kind, project_url = classify(page.url for page in open_pages)
        action, state = decide(kind, project_url, load_state(), time.time())
        if action == 'reopen':
            target = next((page for page in open_pages
                           if urlsplit(page.url).hostname == 'flow.google.com' or page.url == 'about:blank'), None)
            if target:
                await target.goto(state['last_project'], wait_until='domcontentloaded', timeout=45000)
        elif action == 'relogin':
            target = next((page for page in open_pages if urlsplit(page.url).hostname == 'flow.google.com'), None)
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
        state['status'] = 'needs_login' if state.get('parked') else kind
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
