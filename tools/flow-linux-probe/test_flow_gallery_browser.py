import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.async_api import async_playwright
import flow_gallery
import recovery


class GalleryBrowserTests(unittest.IsolatedAsyncioTestCase):
    async def test_baseline_inventories_virtual_tiles_before_generation(self):
        async with async_playwright() as p:
            kwargs={'channel':'msedge'} if sys.platform=='win32' else {'executable_path':'/usr/bin/chromium'}
            browser=await p.chromium.launch(headless=True,**kwargs)
            try:
                page=await browser.new_page()
                html='''<div class="cdk-virtual-scrollable page-container" style="height:200px;overflow:auto">
                <div style="height:1600px"><flow-grid-tile-container><img data-media-id="first-old-1234"></flow-grid-tile-container></div></div>
                <script>document.querySelector('.page-container').onscroll=e=>document.querySelector('img').dataset.mediaId=e.target.scrollTop>400?'last-old-5678':'first-old-1234';</script>'''
                await page.route('**/*',lambda r:r.fulfill(content_type='text/html',body=html))
                await page.goto('https://flow.google.com/project/virtual-project')
                baseline=await flow_gallery.capture_baseline(page)
                self.assertEqual({'first-old-1234','last-old-5678'},{c['media_id'] for c in baseline if c.get('media_id')})
                self.assertEqual(await page.locator('.page-container').evaluate('e=>e.scrollTop'),0)
            finally:
                await browser.close()

    async def test_direct_download_without_resolution_menu(self):
        async with async_playwright() as p:
            kwargs = {'channel': 'msedge'} if sys.platform == 'win32' else {'executable_path': '/usr/bin/chromium'}
            browser = await p.chromium.launch(headless=True, **kwargs)
            try:
                page = await browser.new_page(accept_downloads=True)
                await page.set_content('''<flow-grid-tile-container>
                    <img data-media-id="fixture-image-1234">
                    <button aria-label="More options" onclick="document.querySelector('#menu').hidden=false">Menu</button>
                    </flow-grid-tile-container>
                    <div id="menu" hidden><a role="menuitem" download="original.txt"
                    href="data:text/plain,original-fixture">Download</a></div>''')
                download = await flow_gallery.download_original(page, {'media_id': 'fixture-image-1234'})
                with tempfile.TemporaryDirectory() as folder:
                    target = Path(folder) / 'original'
                    await download.save_as(str(target))
                    self.assertEqual(target.read_text(), 'original-fixture')
            finally:
                await browser.close()

    async def test_current_gallery_downloads_original_and_never_selects_upscale(self):
        payload = b'original-file-from-test-fixture'
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                self.send_response(200)
                self.send_header('Content-Type', 'image/png')
                self.send_header('Content-Disposition', 'attachment; filename=original.png')
                self.send_header('Content-Length', str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        html = '''<meta charset="utf-8"><flow-base-prompt-box></flow-base-prompt-box>
          <flow-grid-tile-container>
            <img data-media-id="test-image-1234" alt="result">
            <button aria-label="Más opciones" onclick="document.querySelector('#menu').hidden=false">Menu</button>
          </flow-grid-tile-container>
          <div id="menu" hidden>
            <button role="menuitem" onmouseover="document.querySelector('#sizes').hidden=false">Descargar</button>
            <div id="sizes" hidden>
              <a role="menuitem" href="DOWNLOAD_URL" download="original.png">1K Tamaño original</a>
              <button role="menuitem" onclick="window.upscaled=true">2K Resolución mejorada</button>
            </div>
          </div>'''.replace('DOWNLOAD_URL', f'http://127.0.0.1:{server.server_port}/original.png')
        async with async_playwright() as p:
            kwargs = {'channel': 'msedge'} if sys.platform == 'win32' else {'executable_path': '/usr/bin/chromium'}
            browser = await p.chromium.launch(headless=True, **kwargs)
            try:
                page = await browser.new_page(accept_downloads=True)

                async def route(request):
                    requests.append(request.request.url)
                    if request.request.url.endswith('/original.png'):
                        await request.fulfill(body=payload, content_type='image/png',
                                              headers={'Content-Disposition': 'attachment; filename=original.png'})
                    else:
                        await request.fulfill(body=html, content_type='text/html')

                await page.route('**/*', route)
                await page.goto('https://flow.google.com/project/test-project')
                controls = await flow_gallery.read_controls(page)
                self.assertEqual(await recovery.download_controls(page), controls)
                self.assertEqual(len(controls), 2)
                result = await flow_gallery.download_original(page, controls[1])
                with tempfile.TemporaryDirectory() as folder:
                    path = Path(folder) / 'result.png'
                    await result.save_as(str(path))
                    self.assertEqual(payload, path.read_bytes())
                self.assertFalse(await page.evaluate('() => !!window.upscaled'))
                self.assertTrue(any(url.endswith('/original.png') for url in requests))
            finally:
                await browser.close()
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


if __name__ == '__main__':
    unittest.main()
