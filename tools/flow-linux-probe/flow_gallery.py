"""Stable identities and original downloads for Flow's current tile gallery."""
import hashlib
import re
from playwright.async_api import TimeoutError as BrowserTimeout
from urllib.parse import urlsplit

MEDIA_ID = re.compile(r'^[A-Za-z0-9_-]{8,96}$')
READ_TILES = """() => [...document.querySelectorAll('flow-grid-tile-container')].flatMap(tile =>
    [...tile.querySelectorAll('img[data-media-id], video[data-media-id]')].map(m => ({
        media_id: m.getAttribute('data-media-id'),
        kind: m.tagName === 'VIDEO' || m.closest('flow-video-tile') ? 'video' : 'image'
    })))"""


def controls(project_url, media):
    parts = urlsplit(project_url)
    if parts.hostname != 'flow.google.com' or not re.fullmatch(r'/project/[A-Za-z0-9-]+/?', parts.path):
        return []
    scope = parts.path.rstrip('/')
    signature = lambda value: hashlib.sha256((scope + ':' + value).encode()).hexdigest()
    # Include the project even for an empty gallery. A project switch must not
    # make pre-existing files in another project look like new results.
    result = [{'index': -1, 'signature': signature('project'), 'kind': 'project'}]
    seen = set()
    for item in media:
        mid = item.get('media_id', '')
        if not isinstance(mid, str) or not MEDIA_ID.fullmatch(mid) or mid in seen:
            continue
        seen.add(mid)
        result.append({'index': len(result) - 1, 'signature': signature(mid),
                       'media_id': mid, 'kind': item.get('kind', 'image')})
    return result


async def read_controls(page):
    return controls(page.url, await page.evaluate(READ_TILES))


async def capture_baseline(page):
    """Inventory the virtual gallery before Generate, including off-screen assets."""
    viewport = await page.evaluate('({width:innerWidth,height:innerHeight})')
    if viewport['width'] < 1100 or viewport['height'] < 650:
        await page.set_viewport_size({'width':1280,'height':900})
    scroll = page.locator('.cdk-virtual-scrollable.page-container')
    if await scroll.count() != 1:
        return await read_controls(page)
    media = {}
    await scroll.evaluate('e=>e.scrollTop=0')
    try:
        for _ in range(160):
            await page.wait_for_timeout(150)
            for item in await page.evaluate(READ_TILES):
                media[item['media_id']] = item
            ended = await scroll.evaluate('e=>{if(e.scrollTop+e.clientHeight>=e.scrollHeight-2)return true;e.scrollTop+=Math.max(100,e.clientHeight*.7);return false}')
            if ended:
                await page.wait_for_timeout(300)
                for item in await page.evaluate(READ_TILES):
                    media[item['media_id']] = item
                return controls(page.url, list(media.values()))
        raise ValueError('gallery_inventory_incomplete')
    finally:
        await scroll.evaluate('e=>e.scrollTop=0')
        await page.wait_for_timeout(200)


async def close_menus(page):
    backdrop = page.locator('.cdk-overlay-backdrop')
    for _ in range(3):
        if not await backdrop.count():
            return
        await page.keyboard.press('Escape')
        await page.wait_for_timeout(200)
    if await backdrop.count():
        await backdrop.first.click(position={'x': 2, 'y': 2}, timeout=5000)


async def download_original(page, expected):
    mid = expected.get('media_id', '')
    if not isinstance(mid, str) or not MEDIA_ID.fullmatch(mid):
        raise ValueError('invalid_media_id')
    tile = page.locator('flow-grid-tile-container').filter(has=page.locator(f'[data-media-id="{mid}"]'))
    if await tile.count() != 1:
        raise ValueError('download_ambiguous')
    try:
        await close_menus(page)
        viewport = await page.evaluate('({width:innerWidth,height:innerHeight})')
        if viewport['width'] < 1100 or viewport['height'] < 650:
            await page.set_viewport_size({'width':1280,'height':900})
        await tile.hover(timeout=10000)
        await tile.get_by_role('button', name=re.compile(r'^(Más opciones|More options)$')).click(timeout=10000)
        download = page.get_by_role('menuitem', name=re.compile(r'(Descargar|Download)'))
        await download.hover(timeout=10000)
        # Select only the original. Enhanced resolutions may start a paid upscale.
        original = page.get_by_role('menuitem', name=re.compile(r'(Tamaño original|Original size|Original)', re.I))
        try:
            await original.first.wait_for(state='visible', timeout=1200)
        except BrowserTimeout:
            # Some tiles (including uploaded images) expose a direct original download,
            # while generated media offer the resolution submenu. Never select an upscale.
            if await download.get_attribute('aria-haspopup') == 'menu':
                await download.click(timeout=10000)
                await original.first.wait_for(state='visible', timeout=5000)
            else:
                async with page.expect_download(timeout=30000) as pending:
                    await download.click(timeout=10000)
                return await pending.value
        if await original.count() != 1:
            raise ValueError('original_download_unavailable')
        async with page.expect_download(timeout=30000) as pending:
            await original.click(timeout=10000)
        return await pending.value
    finally:
        await close_menus(page)
