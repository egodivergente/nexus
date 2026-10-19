"""Importar a Nexus lo que ya hay generado en los proyectos de Google Flow.

Trabaja en una pestaña propia del navegador ya autenticado (marcada con window.name), de modo que el
proyecto que usa el puente para generar no se toca. Solo lee la galería y los medios ya cargados y los
descarga con fetch() desde la propia página, como si el usuario los abriera: no pulsa Generar, Mejorar ni
Descargar, no gasta créditos y nunca exporta cookies.
"""
import asyncio
import base64
import hashlib
import re
import tempfile
from pathlib import Path
from urllib.parse import urlsplit

from flow_agent import FETCH_MEDIA

IMPORT_TAB = 'nexus-import'
FLOW_HOME = 'https://flow.google.com/'
PROJECT_ID = re.compile(r'^[A-Za-z0-9-]{8,64}$')
MEDIA_KEY = re.compile(r'^[0-9a-f]{24}$')
IMPORT_LOCK = asyncio.Lock()
MAX_SCROLLS = 25
SETTLE_MS = 1500

# Proyectos de la galería: id y el título que muestra su tarjeta (lo que el usuario ya ve en Flow).
READ_PROJECTS = """() => {
  const seen = new Map();
  for (const a of document.querySelectorAll('a[href*="/project/"]')) {
    const id = (a.getAttribute('href').match(/\\/project\\/([A-Za-z0-9-]+)/) || [])[1];
    if (!id || seen.has(id)) continue;
    const title = (a.getAttribute('aria-label') || a.innerText || '').trim().split('\\n')[0].slice(0, 80);
    seen.set(id, title);
  }
  return [...seen].map(([id, title]) => ({id, title}));
}"""

# Medios generados del proyecto: solo los servidos por flow.google.com y de tamaño de resultado
# (se descartan avatares e iconos). La clave es un hash de la ruta, estable entre visitas.
READ_MEDIA = """() => {
  const out = [];
  for (const m of document.querySelectorAll('img, video')) {
    const src = m.tagName === 'VIDEO' ? (m.currentSrc || m.src || (m.querySelector('source') || {}).src || '') : (m.currentSrc || m.src || '');
    if (!src.startsWith('https://flow.google.com/')) continue;
    const width = m.naturalWidth || m.videoWidth || 0, height = m.naturalHeight || m.videoHeight || 0;
    if (m.tagName === 'IMG' && Math.max(width, height) < 256) continue;
    out.push({kind: m.tagName === 'VIDEO' ? 'video' : 'image', src, width, height});
  }
  return out;
}"""


def media_signature(src):
    """Identidad completa de un archivo de Flow (sin la consulta, que cambia entre visitas)."""
    parts = urlsplit(src)
    return hashlib.sha256((parts.netloc + parts.path).encode()).hexdigest()


def media_key(src):
    return media_signature(src)[:24]


def unique_media(raw):
    """Una entrada por archivo: los vídeos ganan a su póster y se conserva el orden de la página."""
    items = {}
    for media in raw:
        key = media_key(media['src'])
        if key not in items or (media['kind'] == 'video' and items[key]['kind'] != 'video'):
            items[key] = {'key': key, 'kind': media['kind'], 'width': media['width'], 'height': media['height'],
                          'src': media['src']}
    return list(items.values())


def public_media(items):
    return [{k: v for k, v in item.items() if k != 'src'} for item in items]


def project_url(project_id):
    return f'{FLOW_HOME}project/{project_id}'


async def is_import_page(page):
    try:
        return await page.evaluate('() => window.name') == IMPORT_TAB
    except Exception:
        return False


async def import_page(browser):
    for context in browser.contexts:
        for page in context.pages:
            if await is_import_page(page):
                return page
    # La marca se pone tras cada navegación (touch), cuando la página ya es de flow.google.com.
    return await browser.contexts[0].new_page()


async def touch(page):
    """Marca de uso; el vigilante cierra la pestaña cuando lleva un rato sin usarse."""
    await page.evaluate('() => { window.name = "%s"; window.__nexusImportAt = Date.now(); }' % IMPORT_TAB)


async def open_url(page, url):
    if page.url.rstrip('/') != url.rstrip('/'):
        await page.goto(url, wait_until='domcontentloaded', timeout=45000)
        await page.wait_for_timeout(SETTLE_MS * 3)
    await touch(page)


async def scan(page, reader):
    """Recorre la página de arriba abajo uniendo lo leído en cada paso: Flow carga (y puede descargar
    de memoria) los resultados según se desplaza, así que una sola lectura al final no basta."""
    found = []
    await page.evaluate('() => document.scrollingElement.scrollTo(0, 0)')
    for _ in range(MAX_SCROLLS):
        await page.wait_for_timeout(SETTLE_MS)
        found.extend(await page.evaluate(reader))
        at_end = await page.evaluate("""() => { const s = document.scrollingElement;
            const end = s.scrollTop + window.innerHeight >= s.scrollHeight - 4;
            s.scrollBy(0, Math.max(200, window.innerHeight * 0.8)); return end; }""")
        if at_end:
            break
    return found


async def list_projects(browser):
    page = await import_page(browser)
    await open_url(page, FLOW_HOME)
    if urlsplit(page.url).path.startswith('/about'):
        return None
    projects = {}
    for project in await scan(page, READ_PROJECTS):
        projects.setdefault(project['id'], project['title'])
    return [{'id': pid, 'title': title} for pid, title in projects.items()]


# Fuentes ya vistas por proyecto, para descargar sin volver a recorrer la página.
SOURCES = {}


async def project_media(browser, project_id):
    page = await import_page(browser)
    await open_url(page, project_url(project_id))
    if urlsplit(page.url).path.startswith('/about'):
        return page, None
    items = unique_media(await scan(page, READ_MEDIA))
    SOURCES[project_id] = {item['key']: item['src'] for item in items}
    return page, items


async def fetch_media(browser, project_id, key):
    """Devuelve (mime, bytes) o None si el archivo ya no está en el proyecto."""
    src = SOURCES.get(project_id, {}).get(key)
    page = await import_page(browser)
    if src:
        await open_url(page, project_url(project_id))
    else:
        page, items = await project_media(browser, project_id)
        src = next((item['src'] for item in items or [] if item['key'] == key), None)
        if not src:
            return None
    result = await page.evaluate(FETCH_MEDIA, src)
    if not result or 'error' in result:
        return None
    return result.get('type') or 'application/octet-stream', base64.b64decode(result['data'])


async def close_menus(page):
    """Cierra menús de Flow que hayan quedado abiertos: su fondo transparente bloquea el resto de la página."""
    backdrop = page.locator('.cdk-overlay-backdrop')
    for _ in range(3):
        if not await backdrop.count():
            return
        await page.keyboard.press('Escape')
        await page.wait_for_timeout(300)
    if await backdrop.count():
        await backdrop.first.click(position={'x': 2, 'y': 2})


# Opciones del submenú «Descargar» de Flow: 1K es el original; 2K/4K piden a Flow mejorar la resolución
# (para vídeo, 720p/1080p/4K). El texto de la opción empieza por esta etiqueta.
SIZES = ('1K', '2K', '4K', '720p', '1080p', 'GIF')
UPSCALE_FAILED = re.compile(r'No se ha podido mejorar|Could not upscale|couldn.t upscale', re.I)


async def download_size(browser, project_id, key, size):
    """Descarga un resultado con el menú «Descargar» de Flow en el tamaño pedido.
    Devuelve (nombre, bytes), o un código de error: option_unavailable, upscale_failed, result_not_found."""
    page, items = await project_media(browser, project_id)
    match = next((item for item in items or [] if item['key'] == key), None)
    if not match:
        return 'result_not_found'
    # Marca la miniatura exacta para localizarla aunque haya otras con el mismo prompt.
    found = await page.evaluate("""(src) => { const m = [...document.querySelectorAll('img, video')]
        .find(n => (n.currentSrc || n.src || '').split('?')[0] === src.split('?')[0]);
        if (!m) return false; m.setAttribute('data-nexus-key', 'target'); m.scrollIntoView({block: 'center'}); return true; }""", match['src'])
    if not found:
        return 'result_not_found'
    await close_menus(page)
    tile = page.locator('[data-nexus-key="target"]').first
    await tile.hover(timeout=10000)
    await page.wait_for_timeout(600)
    tile_box = await tile.bounding_box()
    more = page.get_by_role('button', name=re.compile(r'^(Más opciones|More options)$'))
    nearest = None
    for index in range(await more.count()):
        box = await more.nth(index).bounding_box()
        if box and tile_box:
            distance = abs(box['x'] - (tile_box['x'] + tile_box['width'])) + abs(box['y'] - tile_box['y'])
            if distance < 120 and (nearest is None or distance < nearest[0]):
                nearest = (distance, index)
    await page.evaluate("() => document.querySelector('[data-nexus-key]')?.removeAttribute('data-nexus-key')")
    if nearest is None:
        return 'option_unavailable'
    try:
        await more.nth(nearest[1]).click(timeout=5000)
        await page.get_by_role('menuitem', name=re.compile(r'(Descargar|Download)')).first.hover(timeout=5000)
        option = page.get_by_role('menuitem', name=re.compile(rf'^\W*{re.escape(size)}\b', re.I))
        if not await option.count():
            option = page.get_by_role('menuitem').filter(has_text=re.compile(rf'^\s*{re.escape(size)}\b', re.I))
        if not await option.count():
            return 'option_unavailable'
        # La mejora a 2K/4K puede tardar: Flow la calcula antes de entregar el archivo.
        async with page.expect_download(timeout=240_000) as pending:
            await option.first.click(timeout=5000)
        download = await pending.value
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / 'flow-download'
            await download.save_as(str(target))
            return download.suggested_filename, target.read_bytes()
    except Exception:
        if await page.get_by_text(UPSCALE_FAILED).count():
            return 'upscale_failed'
        raise
    finally:
        await close_menus(page)
        await touch(page)


async def close_idle_import_tabs(pages, idle_ms=180_000):
    """Cierra la pestaña de importación si lleva más de idle_ms sin usarse (la llama el vigilante)."""
    for page in pages:
        if await is_import_page(page):
            idle = await page.evaluate('() => Date.now() - (window.__nexusImportAt || 0)')
            if idle > idle_ms:
                await page.close()
