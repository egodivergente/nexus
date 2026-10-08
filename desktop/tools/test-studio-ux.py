"""Workspace UX regression with isolated data; provider operations are blocked."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'outputs/studio-ux-20261008'
OUT.mkdir(parents=True, exist_ok=True)
errors, blocked = [], []
with tempfile.TemporaryDirectory(prefix='nexus-studio-ux-') as folder:
    data = Path(folder)
    (data / 'media').mkdir()
    shutil.copyfile(ROOT / 'desktop/ui/logos/flux.png', data / 'media' / ('a' * 32 + '.png'))
    host = subprocess.Popen([sys.executable, str(ROOT / 'desktop/local_host.py'),
        '--headless', '--offline-qa', '--data-dir', str(data)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    try:
        for _ in range(150):
            if host.poll() is not None:
                raise RuntimeError('Isolated host exited')
            try:
                session = json.loads((data / 'local-session.json').read_text())
                break
            except (OSError, ValueError):
                time.sleep(.1)
        else:
            raise RuntimeError('Isolated host unavailable')
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge', headless=True)
            page = browser.new_page(viewport={'width':1360,'height':900}, bypass_csp=True)
            page.set_default_timeout(10000)
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.route('https://**/*', lambda route: route.abort())
            def guard(route):
                message = route.request.post_data_json
                if message['op'] not in ['init', 'save']:
                    blocked.append(message['op'])
                    route.fulfill(json={'id':message['id'], 'ok':False, 'error':'QA operation blocked'})
                else:
                    route.continue_()
            page.route('**/rpc', guard)
            page.goto(f"http://127.0.0.1:{session['port']}/", wait_until='domcontentloaded')
            page.wait_for_function('window.NexusGallery && window.NexusStudioUX && window.nexusQA')
            assert not errors, errors
            page.evaluate("navigate('canvas')")
            page.wait_for_selector('#studio-start-guide')
            assert page.locator('#studio-start-guide li').count() == 3
            page.locator('[data-studio-help=dismiss]').click()
            page.wait_for_function("SaveStatus.status==='saved'")
            page.reload(wait_until='domcontentloaded')
            page.wait_for_function('window.NexusStudioUX && window.NexusGallery && window.nexusQA')
            page.evaluate("navigate('canvas')")
            assert page.locator('#studio-start-guide').count() == 0
            assert page.evaluate('state.studioHelp.dismissed')
            page.locator('[data-studio-help=toggle]').click()
            page.wait_for_selector('#studio-start-guide')
            page.locator('[data-studio-help=dismiss]').click()
            page.wait_for_function("SaveStatus.status==='saved'")

            # A completed old snapshot must not label a newer draft as saved.
            page.evaluate("""()=>{
                window.originalUXRPC=rpc;window.saveCalls=[];
                rpc=(op,args)=>op==='save'?new Promise((resolve,reject)=>saveCalls.push({args,resolve,reject})):originalUXRPC(op,args);
                state.draft.prompt='Primera versión';persist();
            }""")
            page.wait_for_function('saveCalls.length===1')
            assert page.locator('#studio-save-state').inner_text() == 'Guardando…'
            page.evaluate("state.draft.prompt='Segunda versión';persist();saveCalls[0].resolve(true)")
            assert page.evaluate("SaveStatus.status!=='saved'")
            assert page.evaluate("(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented;})()")
            page.wait_for_function('saveCalls.length===2')
            assert page.evaluate('saveCalls.map(x=>x.args.draft.prompt)') == ['Primera versión','Segunda versión']
            page.evaluate('saveCalls[1].resolve(true)')
            page.wait_for_function("SaveStatus.status==='saved'")
            assert page.locator('#studio-save-state').inner_text() == 'Guardado'

            page.evaluate("state.draft.prompt='Borrador recuperable';persist()")
            page.wait_for_function('saveCalls.length===3')
            page.evaluate("saveCalls[2].reject(Error('Disco de prueba no disponible'))")
            page.wait_for_function("SaveStatus.status==='error'")
            assert page.locator('#studio-save-state').inner_text() == 'Sin guardar'
            assert page.locator('#studio-save-retry').is_visible()
            assert 'Disco de prueba' in page.locator('#studio-save-state').get_attribute('title')
            page.evaluate('()=>{rpc=originalUXRPC;}')
            page.locator('#studio-save-retry').click()
            page.wait_for_function("SaveStatus.status==='saved'")
            assert json.loads((data / 'state.json').read_text(encoding='utf-8'))['draft']['prompt'] == 'Borrador recuperable'
            page.evaluate("state.draft.prompt='Guardado con teclado'")
            page.keyboard.press('Control+s')
            page.wait_for_function("SaveStatus.status==='saved'")
            assert json.loads((data / 'state.json').read_text(encoding='utf-8'))['draft']['prompt'] == 'Guardado con teclado'

            page.evaluate("""()=>{
                loaded=E.normalize({id:'ux-fixture',providerId:'atlas_cloud',displayName:'Modelo de prueba',modalities:['IMAGE'],parameters:[],capabilities:[]});
                state.draft={modelId:loaded.id,prompt:'Prueba de teclado',values:{},media:[]};
                quote={amount:1,currency:'USD',time:Date.now()};render();
                window.keyboardGenerations=0;generate=async()=>{keyboardGenerations++;};
                document.querySelector('#generate').disabled=false;
            }""")
            page.locator('#prompt').focus()
            page.keyboard.press('Control+Enter')
            assert page.evaluate('keyboardGenerations') == 1
            page.evaluate("modal('<h2>Confirmación de prueba</h2>')")
            page.keyboard.press('Control+Enter')
            assert page.evaluate('keyboardGenerations') == 1
            page.keyboard.press('Escape')
            page.locator('#generate').evaluate('(b)=>b.disabled=true')
            page.keyboard.press('Control+Enter')
            assert page.evaluate('keyboardGenerations') == 1

            page.evaluate("""()=>{
                state.jobs=Array.from({length:1000},(_,i)=>({id:'ux-job-'+i,name:'Trabajo '+i,modelId:'ux-fixture',providerId:'atlas_cloud',
                  prompt:'Prompt '+i,modality:'IMAGE',status:'COMPLETED',startedAt:1700000000000+i,
                  parameters:{},outputs:['/media/'+'a'.repeat(32)+'.png']}));
                Studio.tab='results';navigate('library');
                window.firstGalleryNode=document.querySelector('.gallery-piece');
            }""")
            initial_count=page.locator('.gallery-piece').count()
            assert initial_count <= 72
            page.locator('.gallery-sentinel').scroll_into_view_if_needed()
            page.wait_for_function(f"document.querySelectorAll('.gallery-piece').length>{initial_count}")
            assert page.evaluate("firstGalleryNode===document.querySelector('.gallery-piece')")
            page.evaluate("""()=>{
                window.unchangedNode=document.querySelector('[data-job=ux-job-999]');
                window.changedNode=document.querySelector('[data-job=ux-job-998]');
                state.jobs.find(j=>j.id==='ux-job-998').pollError=true;
                refreshJobViews(['ux-job-998']);
            }""")
            assert page.evaluate("unchangedNode===document.querySelector('[data-job=ux-job-999]')")
            assert page.evaluate("changedNode!==document.querySelector('[data-job=ux-job-998]')")
            page.locator('#pc-search').fill('Prompt 987')
            page.wait_for_function("document.querySelectorAll('.gallery-piece').length===1")
            assert page.locator('.gallery-piece').get_attribute('data-job') == 'ux-job-987'
            for width in [1360,760,480]:
                page.set_viewport_size({'width':width,'height':900})
                page.evaluate("navigate('canvas')")
                page.locator('[data-studio-help=toggle]').click()
                page.wait_for_selector('#studio-start-guide')
                assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'), width
                assert page.locator('#studio-save-state').is_visible()
                page.screenshot(path=str(OUT / f'workspace-{width}.png'))
                page.locator('[data-studio-help=dismiss]').click()
            assert not errors, errors
            assert not blocked, blocked
            report={'status':'PASS','saveRace':True,'failedSaveRetry':True,'saveShortcut':True,
                'generationShortcutGuarded':True,'guideDismissalPersists':True,
                'galleryJobs':1000,'appendPreservesNodes':True,'pollPreservesUnchangedNodes':True,
                'searchAfterAppend':True,'widths':[1360,760,480],'pageErrors':errors,'paidCalls':0}
            (OUT / 'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
            print(json.dumps(report))
            browser.close()
    finally:
        host.terminate()
        host.wait(timeout=10)
