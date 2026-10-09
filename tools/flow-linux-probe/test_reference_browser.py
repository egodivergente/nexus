"""Actual Chromium DOM transport test; not a Google account/generation test."""
import asyncio
from pathlib import Path
import struct
import tempfile
import unittest
import zlib
from playwright.async_api import async_playwright
from flow_references import attach_references, composer_reference_count

def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 4, 4, 8, 2, 0, 0, 0)) + chunk(
        b'IDAT', zlib.compress((b'\0' + b'\x40\x90\xb0'*4)*4)) + chunk(b'IEND', b'')

class BrowserReferenceTests(unittest.IsolatedAsyncioTestCase):
    async def test_files_drop_into_composer_and_gallery_cannot_confirm_them(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / ('reference' + str(i) + '.png') for i in range(2)]
            for path in paths:
                path.write_bytes(png())
            async with async_playwright() as playwright:
                browser = await playwright.chromium.launch(executable_path='/usr/bin/chromium', headless=True,
                    args=['--disable-dev-shm-usage'])
                try:
                    page = await browser.new_page()
                    await page.set_content("""<aside><img id="gallery"></aside>
                        <section><div id="ingredients"></div><input aria-label="Texto editable">
                        <button aria-label="Iniciar generación">Generate</button>
                        <button aria-label="Añadir ingredientes a ventana para peticiones">Add</button></section>
                        <script>
                        document.querySelector('input').addEventListener('drop', event => {
                            event.preventDefault();
                            for (const file of event.dataTransfer.files) {
                                const img = document.createElement('img');
                                img.src = URL.createObjectURL(file);
                                document.querySelector('#ingredients').appendChild(img);
                            }
                        });
                        </script>""")
                    await page.locator('#gallery').evaluate("(img, src) => img.src=src",
                        'data:image/png;base64,' + __import__('base64').b64encode(png()).decode())
                    self.assertEqual(await composer_reference_count(page), 0)
                    await attach_references(page, paths)
                    self.assertEqual(await composer_reference_count(page), 2)
                    self.assertEqual(await page.locator('#ingredients img').count(), 2)
                finally:
                    await browser.close()

if __name__ == '__main__':
    unittest.main()
