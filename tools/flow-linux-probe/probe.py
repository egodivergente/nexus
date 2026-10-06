"""Read-only Flow browser smoke test. No login, project creation or generation."""
import asyncio
import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

from playwright.async_api import async_playwright


async def main():
    root = Path.home() / '.local/share/nexus-flow-probe'
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.umask(0o077)
    async with async_playwright() as playwright:
        context = await playwright.chromium.launch_persistent_context(
            str(root / 'profile'), executable_path='/usr/bin/chromium',
            headless=False, chromium_sandbox=True,
            viewport={'width': 1280, 'height': 800},
            args=['--disable-dev-shm-usage'],
        )
        try:
            await context.route('**/*', lambda route: route.abort() if route.request.resource_type == 'media' else route.continue_())
            page = context.pages[0] if context.pages else await context.new_page()
            response = await page.goto('https://flow.google.com/', wait_until='domcontentloaded', timeout=60000)
            await page.wait_for_timeout(5000)
            if '--signin' in sys.argv:
                control = page.locator('a,button').filter(has_text=re.compile(r'^\s*Sign in\s*$', re.I)).first
                href = await control.get_attribute('href', timeout=10000)
                target = urlsplit(href or '')
                if target.scheme != 'https' or target.hostname != 'accounts.google.com':
                    raise RuntimeError('Unexpected sign-in destination')
                await page.goto(href, wait_until='domcontentloaded', timeout=30000)
                await page.wait_for_timeout(3000)
            controls = await page.locator('a,button').all_text_contents()
            result = {
                'browser': context.browser.version,
                'http_status': response.status if response else None,
                'host': urlsplit(page.url).hostname,
                'path': urlsplit(page.url).path,
                'title': await page.title(),
                'controls': [s.strip()[:100] for s in controls if s.strip()][:35],
                'generation_attempted': False,
                'authenticated_flow_verified': False,
            }
            (root / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
            print(json.dumps(result), flush=True)
            await page.screenshot(path=str(root / 'flow.png'), animations='disabled', timeout=20000)
        finally:
            await context.close()


if __name__ == '__main__':
    asyncio.run(main())
