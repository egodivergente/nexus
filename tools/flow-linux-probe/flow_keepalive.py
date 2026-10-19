"""Keep the ASUS Flow browser usable without a second Chromium or any login automation.

Run every few minutes from nexus-flow-keepalive.timer. It only attaches to the
already-running browser over loopback CDP and, after a grace period:
- reopens the last known Flow project when Flow drops back to its gallery;
- parks a signed-out /about page on about:blank, because that public page alone
  saturates the ASUS CPU, and records that a human sign-in is needed.
It never types credentials, creates projects, reloads an open project or submits work.
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
    """Return (action, new_state). action is None, 'reopen' or 'park'."""
    state = dict(state)
    if kind != state.get('kind'):
        state['kind'], state['since'] = kind, now
    waited = now - state['since']
    if kind == 'project':
        state['last_project'], state['parked'] = project_url, False
        return None, state
    if kind in ('login', 'gallery'):
        state['parked'] = False
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
