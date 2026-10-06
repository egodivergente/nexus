"""Read-only responsive smoke test of the live local edition."""
from pathlib import Path
import json
import os
from playwright.sync_api import sync_playwright

session = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop' / 'local-session.json'
port = int(json.loads(session.read_text(encoding='utf-8'))['port'])
with sync_playwright() as playwright:
    browser=playwright.chromium.launch(channel='msedge',headless=True)
    for scale in (1,1.25,1.5):
        context=browser.new_context(viewport={'width':1024,'height':768},device_scale_factor=scale)
        page=context.new_page()
        page.goto(f'http://127.0.0.1:{port}/',wait_until='domcontentloaded')
        page.get_by_role('button',name='Imagen',exact=True).wait_for(timeout=20000)
        page.get_by_text('Elige por lo que quieres hacer').wait_for(timeout=20000)
        result=page.evaluate('''() => ({overflow:document.documentElement.scrollWidth>innerWidth,
            links:document.querySelectorAll('.guide-item').length,
            catalog:document.querySelectorAll('.model').length,
            controls:[...document.querySelectorAll('.select-trigger')].length})''')
        assert not result['overflow'],f'Horizontal overflow at {scale}: {result}'
        assert result['links']>=10 and result['catalog']>=30,result
        assert result['controls']>=2,result
        print(f'PASS 1024px, escala {scale}: guía {result["links"]}, catálogo visible {result["catalog"]}, sin desbordamiento')
        context.close()
    browser.close()
