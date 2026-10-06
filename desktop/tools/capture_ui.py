"""Capture the installed local edition without sending generations or editing user state."""
from pathlib import Path
import json
import os
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
session = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop' / 'local-session.json'
port = int(json.loads(session.read_text(encoding='utf-8'))['port'])
destination = root / 'outputs' / 'desktop-audit-20261002' / 'after'
destination.mkdir(parents=True, exist_ok=True)

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width': 1440, 'height': 900}, device_scale_factor=1)
    page.goto(f'http://127.0.0.1:{port}/', wait_until='domcontentloaded')
    page.get_by_role('button', name='Imagen', exact=True).wait_for(timeout=20000)
    for label, filename in [('Modelos','modelos-imagen.png'),('Vídeo','modelos-video.png'),
                            ('Lienzo','lienzo.png'),('Historial','historial.png'),
                            ('Biblioteca','biblioteca.png'),('Ajustes','ajustes.png')]:
        if label == 'Vídeo': page.get_by_role('button',name='Vídeo',exact=True).click()
        else: page.locator(f'#navigation [data-nav="{dict(Modelos="catalog",Lienzo="canvas",Historial="history",Biblioteca="library",Ajustes="settings")[label]}"]').click()
        page.screenshot(path=str(destination / filename), full_page=False, animations='disabled')
    browser.close()
print(destination)
