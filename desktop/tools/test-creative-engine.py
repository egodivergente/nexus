"""Creative command layout regression in isolated state, with all provider calls blocked."""
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'outputs/creative-engine-20261008'
OUT.mkdir(parents=True, exist_ok=True)
errors, blocked = [], []
with tempfile.TemporaryDirectory(prefix='nexus-creative-') as folder:
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
                session = json.loads((data/'local-session.json').read_text(encoding='utf-8'))
                break
            except (OSError, ValueError): time.sleep(.1)
        else: raise RuntimeError('QA host unavailable')
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge', headless=True)
            page = browser.new_page(viewport={'width':1360,'height':900}, bypass_csp=True)
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.set_default_timeout(10000)
            page.route('https://**/*', lambda r: r.abort())
            def guard(route):
                m = route.request.post_data_json
                if m['op'] in ['request','agent','generate','flowSubmit','upload','flowConnect','flowConnection']:
                    blocked.append(m['op'])
                    route.fulfill(json={'id':m['id'],'ok':False,'error':'QA external operation blocked'})
                else: route.continue_()
            page.route('**/rpc', guard)
            page.goto(f"http://127.0.0.1:{session['port']}/", wait_until='domcontentloaded')
            page.wait_for_function('Boolean(window.NexusCreative)&&Boolean(window.NexusBatch)&&Boolean(window.NexusOrderEditor)&&typeof renderBalances==="function"')
            page.evaluate("""()=>{
              state.draft={modelId:null,prompt:'Mi escena',values:{},media:[]};navigate('canvas');
            }""")
            page.wait_for_selector('#creative-command')
            assert page.locator('#prompt').input_value() == 'Mi escena'
            page.wait_for_function('Math.abs(document.querySelector(".sidebar").getBoundingClientRect().width-64)<1')
            page.locator('#creative-dock-toggle').click()
            page.wait_for_timeout(250)
            assert page.locator('#creative-dock-toggle').get_attribute('aria-expanded') == 'true'
            page.locator('#creative-dock-toggle').click()
            # Controlled real-schema-shaped fixture; never submit it to a provider.
            page.evaluate("""()=>{
              loaded={id:'qa-creative',displayName:'Motor de prueba',providerId:'runway',modalities:['IMAGE'],
                parameters:[{key:'aspect_ratio',displayName:'Formato',options:['16:9','9:16'],defaultValue:'16:9'},
                  {key:'resolution',displayName:'Resolución',options:['720p','1080p'],defaultValue:'1080p'},
                  {key:'seed',displayName:'Semilla',nativeType:'integer',isAdvanced:true}],
                fields:[{key:'image',label:'Referencia',mime:'image/*',maximum:1}],capabilities:[]};
              state.draft={modelId:loaded.id,prompt:'Mi escena',values:{aspect_ratio:'16:9',resolution:'1080p',seed:42},media:[]};
              state.jobs=[{id:'creative-result',name:'Render de prueba',providerId:'runway',modelId:loaded.id,
                modality:'IMAGE',status:'COMPLETED',prompt:'Mi escena',outputs:['/media/'+'a'.repeat(32)+'.png']}];
              quote=null;renderCanvas();
            }""")
            page.wait_for_selector('.creative-chip select')
            assert page.locator('.creative-chip select').count() == 2
            assert page.locator('[data-param=resolution] option').all_text_contents() == ['Automático','720p','1080p']
            assert page.locator('#header-actions [data-studio=picker]').is_hidden()
            page.locator('[data-param=aspect_ratio]').select_option('9:16')
            assert page.evaluate("state.draft.values.aspect_ratio") == '9:16'
            assert page.locator('#generate').is_disabled()
            # Keyboard only activates the existing guarded button. Shift Enter remains multiline.
            page.evaluate("window.creativeClicks=0;document.querySelector('#generate').addEventListener('click',e=>{creativeClicks++;e.stopImmediatePropagation();});")
            page.locator('#prompt').focus()
            page.keyboard.press('Enter')
            assert page.evaluate('creativeClicks') == 0
            page.keyboard.press('Shift+Enter')
            assert '\n' in page.locator('#prompt').input_value()
            page.evaluate("document.querySelector('#generate').disabled=false")
            page.keyboard.press('Enter')
            assert page.evaluate('creativeClicks') == 1
            page.locator('.creative-parameters > summary').click()
            page.locator('[data-param=seed]').fill('777')
            assert page.evaluate('state.draft.values.seed') == '777'
            # Internal drag uses a known completed result, and invalidates its prior quote.
            page.evaluate("""()=>{const dt=new DataTransfer();dt.setData('application/x-nexus-job','creative-result');
              document.querySelector('#creative-command').dispatchEvent(new DragEvent('drop',{bubbles:true,dataTransfer:dt}));}""")
            page.wait_for_function('state.draft.media.length===1')
            assert page.evaluate('state.draft.media[0].field') == 'image'
            page.evaluate("NexusCreative.attach('creative-result')")
            assert page.evaluate('state.draft.media.length') == 1
            assert page.evaluate('quote') is None
            # An incoming job state updates the tray without replacing the draft input.
            page.evaluate("""()=>{window.creativePromptNode=document.querySelector('#prompt');
              state.jobs[0].status='PROCESSING';parseResponse(state.jobs[0],{status:'COMPLETED',outputs:['/media/'+'a'.repeat(32)+'.png']});}""")
            page.wait_for_function('document.querySelector(".creative-piece").draggable')
            assert page.evaluate('creativePromptNode===document.querySelector("#prompt")')
            page.locator('#creative-control > summary').click()
            assert page.locator('#telemetry-button').is_visible()
            assert page.locator('#account-balances').is_visible()
            page.locator('#telemetry-button').click()
            assert page.locator('#telemetry-panel').evaluate('d=>d.open')
            page.locator('#telemetry-close').click()
            page.keyboard.press('Escape')
            page.evaluate("document.querySelector('#toast').classList.remove('show')")
            for width,height in [(1360,900),(760,900),(480,850),(1280,600)]:
                page.set_viewport_size({'width':width,'height':height})
                page.wait_for_timeout(250)
                assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'), width
                assert page.locator('#creative-command').bounding_box()['width'] <= width-64
                if width == 1360:
                    command=page.locator('#creative-command').bounding_box()
                    assert abs(command['x']+command['width']/2-(width+64)/2)<15
                page.screenshot(path=str(OUT/f'creative-{width}.png'))
            page.locator('.creative-multi > summary').click()
            page.locator('.creative-multi [data-action=chooseCompare]').click()
            page.wait_for_selector('.catalog')
            assert page.evaluate('selection.has("qa-creative")')
            page.locator('.creative-shortcut').click()
            assert page.locator('#gallery-command-search').is_visible()
            page.keyboard.press('Escape')
            assert not errors, errors
            assert not [op for op in blocked if op in ['generate','flowSubmit','upload']], blocked
            browser.close()
        report={'pass':True,'viewports':[1360,760,480,1280],'javascriptErrors':errors,
                'paidCalls':0,'blockedExternalOperations':blocked}
        (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps(report))
    finally:
        host.terminate()
        host.wait(timeout=15)
