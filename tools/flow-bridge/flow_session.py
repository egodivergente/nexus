"""Renew the owned Flow session through Google's existing browser sign-in.

Never enters credentials, accepts agreements or sends a generation. A required
password, verification or consent stays with the user.
"""
import argparse
import asyncio
import json
import os
from pathlib import Path
import re
import time
import uuid
from urllib.parse import urlsplit
from launcher import ROOT, environment

PROFILE = ROOT / 'state/profile_nexus'
CACHE = ROOT / 'jobs/session-health.json'
TTL = 300
SIGNIN_LABEL = re.compile(r'^(Sign in|Iniciar sesión|Create with(?: Google)? Flow|Crear con(?: Google)? Flow|Get started|Empezar)$', re.I)

class SessionUnavailable(RuntimeError):
    def __init__(self, status):
        self.status = status
        super().__init__('Flow session is not ready')

def cached():
    try:
        value = json.loads(CACHE.read_text(encoding='utf-8'))
        if value.get('status') == 'connected' and 0 <= time.time()-value['checked_at'] < TTL:
            return value
    except (OSError, ValueError, KeyError, TypeError):
        pass
    return None

def remember(status, refreshed=False, reason=None):
    value = {'status': status, 'checked_at': time.time(), 'refreshed': refreshed}
    if reason:
        value['reason'] = reason
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    temporary = CACHE.with_name(CACHE.name + '.' + uuid.uuid4().hex + '.tmp')
    temporary.write_text(json.dumps(value), encoding='utf-8')
    temporary.replace(CACHE)
    return value

def allowed_signin(url):
    parts = urlsplit(url)
    return parts.scheme == 'https' and parts.hostname == 'accounts.google.com'

async def app_ready(page, ctx):
    from gflow_cli.auth.verification import has_migrated_app_session
    from gflow_cli.api.transports.migrated_composer import READY_ANCHOR
    parts = urlsplit(page.url)
    if parts.hostname != 'flow.google.com' or parts.path.startswith('/about'):
        return False
    if not has_migrated_app_session(await ctx.cookies()):
        return False
    # Cookie presence alone is never considered a usable session.
    ready = False
    try:
        await page.locator(READY_ANCHOR).first.wait_for(state='visible', timeout=8000)
        ready = True
    except Exception:
        # The authenticated workspace home need not contain an open composer.
        ready = await page.get_by_role('button', name=re.compile(r'^(New project|Nuevo proyecto|Crear proyecto)$', re.I)).count() > 0
    if not ready:
        return False
    answer = await ctx.request.get('https://myaccount.google.com/?hl=en', timeout=20000)
    return answer.status == 200 and urlsplit(str(answer.url)).hostname == 'myaccount.google.com'

async def ensure(force=False):
    value = await renew(force)
    if value['status'] != 'connected':
        raise SessionUnavailable(value['status'])
    return value

async def click_signin(page):
    for label in (re.compile(r'^(Sign in|Iniciar sesión)$', re.I), SIGNIN_LABEL):
        for role in ('link', 'button'):
            controls = page.get_by_role(role, name=label)
            for index in range(await controls.count()):
                control = controls.nth(index)
                if not await control.is_visible():
                    continue
                if role == 'link':
                    href = await control.get_attribute('href') or ''
                    target = urlsplit(href)
                    if not (allowed_signin(href) or target.scheme == 'https' and target.hostname in {'flow.google.com','labs.google'} or href.startswith('/') and not href.startswith('//')):
                        continue
                await control.click(timeout=5000)
                return True
    return False

async def existing_account(page, ctx):
    """Resolve only an identity already signed in inside this owned profile."""
    marker = PROFILE / '.gflow_account'
    expected = marker.read_text(encoding='utf-8').strip() if marker.exists() else ''
    if expected:
        return expected
    # Migrated Flow logins can leave the browser marker without an account
    # marker. Ask Google's authenticated account page, using this same context.
    from collections import Counter
    from gflow_cli.auth.verification import find_emails
    try:
        answer = await ctx.request.get('https://myaccount.google.com/?hl=en', timeout=15000)
        if answer.status == 200 and urlsplit(str(answer.url)).hostname == 'myaccount.google.com':
            counts = Counter(find_emails(await answer.text()))
            ranked = counts.most_common(2)
            if ranked and (len(ranked) == 1 or ranked[0][1] > ranked[1][1]):
                return ranked[0][0]
    except Exception:
        pass
    # A chooser with exactly one already signed-in identity is unambiguous.
    if allowed_signin(page.url) and '/accountchooser' in page.url.lower():
        accounts = set(value for value, _ in await account_choices(page))
        if len(accounts) == 1:
            return accounts.pop()
    return ''

async def account_choices(page):
    from gflow_cli.auth.verification import find_emails
    if not allowed_signin(page.url) or '/accountchooser' not in page.url.lower():
        return []
    choices = []
    cards = page.locator('[data-identifier], [data-email], li [role="link"]')
    for index in range(await cards.count()):
        card = cards.nth(index)
        if not await card.is_visible():
            continue
        value = await card.get_attribute('data-identifier') or await card.get_attribute('data-email')
        if not value:
            emails = set(find_emails(await card.inner_text()))
            value = emails.pop() if len(emails) == 1 else ''
        if value and re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', value):
            choices.append((value, card))
    return choices

async def advance_existing_signin(page, expected):
    if not allowed_signin(page.url) or not expected:
        return False
    if '/accountchooser' in page.url.lower():
        for value, card in await account_choices(page):
            if value == expected:
                await card.click(timeout=5000)
                return True
        account = page.get_by_text(expected, exact=True)
        if await account.count() == 1 and await account.is_visible():
            await account.click(timeout=5000)
            return True
    # This is the known account identifier, never a password or verification.
    email = page.locator('input[type="email"]')
    if await email.count() == 1 and await email.is_visible():
        next_button = page.get_by_role('button', name=re.compile(r'^(Next|Siguiente)$', re.I))
        if await next_button.count() == 1 and await next_button.is_visible():
            await email.fill(expected)
            await next_button.click(timeout=5000)
            return True
    return False

async def signin_requirement(page):
    if not allowed_signin(page.url):
        return 'page_not_ready'
    path = urlsplit(page.url).path.lower()
    if '/rejected' in path or '/denied' in path:
        return 'browser_rejected'
    password = page.locator('input[type="password"]')
    if await password.count() and await password.first.is_visible():
        return 'password_required'
    if '/challenge/' in path:
        return 'verification_required'
    if '/accountchooser' in path:
        return 'account_selection_required'
    return 'google_signin'

async def renew(force=False):
    if not force and (value := cached()):
        return value
    result = await _renew()
    if result.get('reason') == 'browser_rejected':
        # Some Google sign-in flows reject headless Chromium. Retry once with
        # the existing browser in its normal mode, still off the user's screen.
        # The first context and profile lease have both ended before this call.
        return await _renew(headed=True)
    return result

async def _renew(headed=False):
    os.environ.update(environment())
    os.environ['GFLOW_CLI_LEASE_WAIT_SECONDS'] = '15'
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.auth.internal_chromium import login_launch_kwargs
    renewed = False
    stage = 'browser'
    try:
        async with ProfileLease(PROFILE), async_playwright() as pw:
            launch = login_launch_kwargs(PROFILE, not headed)
            if headed:
                launch['args'] = [*launch['args'], '--window-position=-10000,-10000']
            ctx = await pw.chromium.launch_persistent_context(**launch)
            try:
                page = await ctx.new_page()
                stage = 'navigate'
                await page.goto('https://flow.google.com/?hl=en', wait_until='domcontentloaded', timeout=45000)
                # Wait for the SPA to make its redirect/readiness decision.
                await page.wait_for_timeout(3000)
                stage = 'verify'
                if await app_ready(page, ctx):
                    return remember('connected')
                # Only follow an explicit Google sign-in control from Flow.
                if urlsplit(page.url).hostname in {'flow.google.com', 'labs.google'}:
                    stage = 'signin'
                    cookie = page.locator('[id^="glue-cookie-notification-bar"]').get_by_role('button', name=re.compile(r'^(Entendido|Got it|OK|OK, got it)$', re.I))
                    if await cookie.count() == 1 and await cookie.is_visible():
                        await cookie.click(timeout=5000)
                    renewed = await click_signin(page)
                await page.wait_for_timeout(3000)
                # Selecting this already signed-in identity is permitted; passwords,
                # consent, account creation and verification are never automated.
                stage = 'account'
                expected = await existing_account(page, ctx)
                for _ in range(4):
                    stage = 'verify'
                    if await app_ready(page, ctx):
                        if expected:
                            (PROFILE / '.gflow_account').write_text(expected, encoding='utf-8')
                        return remember('connected', renewed)
                    if allowed_signin(page.url):
                        if not await advance_existing_signin(page, expected):
                            break
                        renewed = True
                    await page.wait_for_timeout(1500)
                parts = urlsplit(page.url)
                needs_auth = allowed_signin(page.url) or parts.hostname == 'flow.google.com' and parts.path == '/about'
                result = remember('auth_required' if needs_auth else 'error', renewed, await signin_requirement(page))
                if result['reason'] == 'account_selection_required':
                    result['saved_accounts'] = len(set(value for value, _ in await account_choices(page)))
                return result
            finally:
                await ctx.close()
    except Exception as error:
        name = type(error).__name__
        # No exception text, cookies, URLs with auth parameters or identity leave
        # this process. A profile owned by a generation is retried later.
        value = remember('busy' if name == 'ProfileLockedError' else 'error', renewed, name + ':' + stage)
        return value

async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--force', action='store_true')
    parser.add_argument('--interactive', action='store_true')
    args = parser.parse_args()
    if args.interactive:
        os.environ.update(environment())
        from runtime_compat import install
        install()
        from gflow_cli.auth import login
        try:
            await login('nexus', browser='internal', headless=False)
        except Exception:
            # Closing after a successful migrated login may still make the CLI
            # return an error. Only a fresh verified probe decides the outcome.
            pass
        result = await renew(force=True)
    else:
        result = await renew(args.force)
    Path(args.output).write_text(json.dumps(result), encoding='utf-8')

if __name__ == '__main__':
    asyncio.run(main())
