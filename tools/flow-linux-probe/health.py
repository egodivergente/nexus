"""Read-only Linux Flow session check. Never exports cookies or user identities."""
import asyncio
import fcntl
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit

from gflow_cli.auth.verification import has_migrated_app_session
from playwright.async_api import async_playwright

ROOT = Path.home() / '.local/share/nexus-flow-probe'


async def check(reauthenticate=False):
    profile = ROOT / 'profile'
    if (profile / 'SingletonLock').is_symlink():
        return {'status': 'busy'}
    async with async_playwright() as playwright:
        context = await playwright.chromium.launch_persistent_context(
            str(profile), executable_path='/usr/bin/chromium',
            headless=False, chromium_sandbox=True,
            args=['--disable-dev-shm-usage'],
        )
        try:
            page = context.pages[0] if context.pages else await context.new_page()
            await page.goto('https://flow.google.com/?hl=en', wait_until='domcontentloaded', timeout=45000)
            button = page.get_by_role('button', name=re.compile(r'^(New project|Nuevo proyecto|Crear proyecto)$', re.I))
            try:
                await button.first.wait_for(state='visible', timeout=15000)
            except Exception:
                if reauthenticate:
                    from auto_auth import authenticate
                    result = await authenticate(page, ROOT)
                    if result['status'] != 'login_submitted':
                        return result
                    await button.first.wait_for(state='visible', timeout=15000)
                else:
                    return {'status': 'auth_required' if urlsplit(page.url).hostname == 'accounts.google.com'
                            or urlsplit(page.url).path.startswith('/about') else 'unverified'}
            if not has_migrated_app_session(await context.cookies()):
                return {'status': 'auth_required'}
            failed = page.get_by_text(re.compile(r'No se han podido cargar los proyectos|Failed to load projects|Unable to load projects', re.I))
            if await failed.count():
                return {'status': 'unverified', 'reason': 'projects_unavailable'}
            response = await context.request.get('https://myaccount.google.com/?hl=en', timeout=15000)
            connected = response.status == 200 and urlsplit(str(response.url)).hostname == 'myaccount.google.com'
            return {'status': 'connected' if connected else 'auth_required'}
        finally:
            await context.close()


def main():
    os.umask(0o077)
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (ROOT / 'health.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print(json.dumps({'status': 'busy'}))
            return
        try:
            result = asyncio.run(check(reauthenticate='--reauthenticate' in sys.argv))
        except Exception as error:
            result = {'status': 'error', 'error_type': type(error).__name__}
        print(json.dumps(result))


if __name__ == '__main__':
    main()
