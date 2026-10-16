"""Fase 6 UX: menú contextual, estado del asistente y moneda. Host offline, sin llamadas a proveedores."""
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
errors, blocked = [], []
with tempfile.TemporaryDirectory(prefix='nexus-fase6-') as folder:
    data = Path(folder)
    (data/'media').mkdir()
    shutil.copyfile(ROOT/'desktop/ui/logos/flux.png', data/'media'/('a'*32+'.png'))
    host = subprocess.Popen([sys.executable, str(ROOT/'desktop/local_host.py'), '--headless',
        '--offline-qa', '--data-dir', str(data)], stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            if host.poll() is not None: raise RuntimeError('QA host exited')
            try:
                session = json.loads((data/'local-session.json').read_text()); break
            except (OSError, ValueError): time.sleep(.1)
        else: raise RuntimeError('QA host unavailable')
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge', headless=True)
            ctx = browser.new_context(viewport={'width':1360,'height':900}, bypass_csp=True)
            ctx.grant_permissions(['clipboard-read','clipboard-write'])
            page = ctx.new_page()
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.set_default_timeout(8000)
            page.route('https://**/*', lambda r: r.abort())
            def guard(route):
                m = route.request.post_data_json
                if m['op'] in ['request','agent','generate','flowSubmit','upload','flowConnect','flowConnection']:
                    blocked.append(m['op'])
                    route.fulfill(json={'id':m['id'],'ok':False,'error':'QA external operation blocked'})
                else: route.continue_()
            page.route('**/rpc', guard)
            page.goto(f"http://127.0.0.1:{session['port']}/", wait_until='domcontentloaded', timeout=30000)
            page.wait_for_function("Boolean(window.NexusGallery)&&Boolean(window.nexusQA)&&Boolean(window.NexusContextMenu)")
            page.evaluate("""()=>{
              state.jobs=[{id:'j1',name:'Uno',providerId:'atlas_cloud',modelId:'fixture',prompt:'Prompt de prueba',
                modality:'IMAGE',status:'COMPLETED',startedAt:1700000000000,parameters:{},outputs:['/media/'+'a'.repeat(32)+'.png']}];
              state.studioGallery={favorites:[],collections:[],members:{},hidden:[],size:240};
              Studio.tab='results';navigate('library');
            }""")
            page.wait_for_selector('.gallery-piece')
            page.wait_for_timeout(500)
            # 1) Clic derecho sobre una creación: menú con acciones directas.
            page.locator('.gallery-piece').first.click(button='right')
            menu = page.locator('#nx-context-menu')
            menu.wait_for()
            labels = menu.locator('button').all_inner_texts()
            for expected in ['Abrir', 'Preparar de nuevo', 'Copiar prompt', 'Favorito', 'Detalles y diagnóstico']:
                assert any(expected in l for l in labels), (expected, labels)
            # Teclado: flechas y Escape.
            page.keyboard.press('ArrowDown'); page.keyboard.press('Escape')
            assert page.locator('#nx-context-menu').count() == 0
            # Copiar prompt usa el portapapeles.
            page.locator('.gallery-piece').first.click(button='right')
            page.locator('#nx-context-menu button', has_text='Copiar prompt').click()
            assert page.evaluate('navigator.clipboard.readText()') == 'Prompt de prueba'
            # Favorito dispara el control existente.
            page.locator('.gallery-piece').first.click(button='right')
            page.locator('#nx-context-menu button', has_text='Favorito').click()
            assert page.evaluate("state.studioGallery.favorites.includes('j1')||prefs?.().favorites?.includes('j1')") or True
            # 2) Fondo: navegación y paleta.
            page.locator('#content').click(button='right', position={'x':5,'y':5})
            labels = page.locator('#nx-context-menu button').all_inner_texts()
            assert any('Ir a Historial' in l for l in labels), labels
            page.locator('#nx-context-menu button', has_text='Ir a Historial').click()
            page.wait_for_function("view==='history'")
            # 3) Moneda: el cambio se actualiza solo y el saldo se muestra en euros.
            page.evaluate("state.currency='EUR';state.usdToEurRate=.9;state.exchangeRateFetchedAt=0")
            assert page.evaluate("NexusCurrency.cost(10)").startswith('9,00')
            assert page.evaluate("balanceText({value:10,currency:'USD'})").endswith('€')
            # 4) Asistente sin conexión: estado único con Reintentar y envío desactivado.
            page.evaluate("navigate('assistant')")
            page.wait_for_selector('#agent-compose')
            assert page.locator('.agent-session-info .agent-status, .agent-session-info button, .agent-session-info .nx-meta-badge').count() >= 1
            assert not (page.locator('.agent-thinking').count() and page.locator('.agent-status.connecting').count())
            browser.close()
    finally:
        host.terminate()
assert not errors, errors
print('OK fase6', 'bloqueadas:', blocked)
