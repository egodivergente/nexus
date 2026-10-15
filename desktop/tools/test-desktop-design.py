"""Desktop visual and interaction checks with isolated data and no paid calls."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
out = root/'outputs/desktop-design-20261006'
out.mkdir(parents=True,exist_ok=True)
screens, errors, blocked = [], [], []
with tempfile.TemporaryDirectory(prefix='nexus-desktop-design-') as folder:
    data = Path(folder)
    (data/'media').mkdir()
    for char,logo in [('a','flux'),('b','google'),('c','bytedance')]:
        shutil.copyfile(root/f'desktop/ui/logos/{logo}.png',data/'media'/f'{char*32}.png')
    log = (out/'host.log').open('wb')
    host = subprocess.Popen([str(Path(sys.base_prefix)/'python.exe'),str(root/'desktop/local_host.py'),
        '--headless','--offline-qa','--data-dir',str(data)],stdout=log,stderr=log,
        creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    try:
        for _ in range(300):
            if host.poll() is not None: raise RuntimeError('Isolated host exited')
            try:
                session=json.loads((data/'local-session.json').read_text())
                if session['pid']==host.pid: break
            except (OSError,ValueError): pass
            time.sleep(.1)
        else: raise RuntimeError('Isolated host unavailable')
        with sync_playwright() as pw:
            options={'headless':True}
            if os.environ.get('NEXUS_QA_BROWSER'):options['executable_path']=os.environ['NEXUS_QA_BROWSER']
            else:options['channel']='msedge'
            browser=pw.chromium.launch(**options)
            page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.route('https://**/*',lambda route:route.abort())
            def guard(route):
                message=route.request.post_data_json
                if message['op'] in ('agent','request','generate','flowSubmit','upload','flowConnect','flowConnection'):
                    blocked.append(message['op'])
                    route.fulfill(json={'id':message['id'],'ok':False,'error':'Visual fixture: external operations blocked.'})
                else: route.continue_()
            page.route('**/rpc',guard)
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('Boolean(window.nexusQA)&&DesktopDesign.ready')
            page.wait_for_function("typeof renderBalances==='function'")
            page.evaluate("""()=>{
                state.accent='violet';state.draft={modelId:null,prompt:'',values:{},media:[]};loaded=null;
                Agent.settings={modelId:'us.anthropic.claude-sonnet-4-6'};
                Agent.snapshot={offline:false,mode:'ASSISTANT',index:{projects:[],assets:[]},skills:[],plans:[],proposals:[]};
                AccountBalances.values={atlas_cloud:{value:24.9333,currency:'USD',time:Date.now()},runway:{value:364,currency:'credits',time:Date.now()},crun:{value:100.78,currency:'credits',time:Date.now()}};
                if(typeof FlowConnection!=='undefined')FlowConnection.value={status:'connected'};
                state.jobs=[{id:'sample-a',name:'Una nueva idea',modelId:'google-flow/nano2',providerId:'google_flow',prompt:'Prueba de composición',modality:'IMAGE',status:'COMPLETED',outputs:['/media/'+'a'.repeat(32)+'.png'],parameters:{aspect:'1:1'},startedAt:Date.now()},
                    {id:'sample-b',name:'Otro punto de vista',modelId:'sample',providerId:'atlas_cloud',prompt:'Prueba de biblioteca',modality:'IMAGE',status:'COMPLETED',outputs:['/media/'+'b'.repeat(32)+'.png'],parameters:{}},
                    {id:'sample-c',name:'En preparación',modelId:'sample',providerId:'runway',prompt:'Prueba de actividad',modality:'VIDEO',status:'PROCESSING',outputs:[],parameters:{}}];
            }""")
            def capture(name,width,height=900):
                page.set_viewport_size({'width':width,'height':height})
                page.wait_for_timeout(130)
                assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'),(name,width)
                page.screenshot(path=str(out/f'{name}-{width}.png'))
                screens.append({'view':name,'width':width,'height':height})
            for width,height in [(1920,1080),(1360,900),(1024,768),(760,900),(480,850),(1280,600)]:
                page.evaluate("()=>{Agent.items=[];navigate('assistant');}")
                capture('assistant',width,height)
                composer=page.locator('#agent-compose').bounding_box()
                assert composer['y']+composer['height']<=height+1,(width,composer)
                assert page.locator('[data-desktop-idea]').count()==3
                assert page.locator('#navigation [aria-current=page]').get_attribute('data-nav')=='assistant'
                page.locator('[data-desktop-idea=image]').click()
                assert page.locator('#agent-input').input_value().startswith('Quiero crear una imagen.')
                assert page.locator('#agent-input').evaluate('(input)=>input===document.activeElement')
                assert page.evaluate('Agent.items.length')==0
                page.evaluate("()=>{Agent.draft='';loaded=null;state.draft.modelId=null;navigate('canvas');}")
                capture('create',width,height)
                assert page.locator('.nx-create-start').count()==1
                page.locator('#prompt').fill('Borrador que debe conservarse')
                page.locator('.studio-start [data-studio=picker]').click()
                page.wait_for_selector('#studio-model-search')
                assert page.locator('#dialog').evaluate('(dialog)=>dialog.open')
                capture('models',width,height)
                page.locator('#studio-model-kind + .select-trigger').click()
                assert page.locator('.select-popup').evaluate('(list)=>Boolean(list.closest("dialog[open]"))')
                page.locator('.select-popup [aria-selected=true]').press('Escape')
                assert page.locator('#dialog').evaluate('(dialog)=>dialog.open')
                page.locator('#dialog [data-action=close]').click()
                assert page.evaluate("state.draft.prompt==='Borrador que debe conservarse'")
                page.evaluate("()=>{Studio.tab='results';navigate('library');}")
                capture('library',width,height)
                assert page.locator('#content .result').count()==3  # la galería muestra también el encargo en curso
                assert page.locator('#content .result[data-job=sample-c] .gallery-progress').count()==1
                # La galería abre el visor (antes, un panel lateral de detalles).
                page.wait_for_selector('#content .result[data-job=sample-a][data-gallery-wired]')
                # Abre el visor con teclado. Si la galería se vuelve a pintar justo entre el foco y la
                # tecla (refresco de saldos), el foco se pierde: se reintenta sobre la tarjeta nueva.
                for attempt in range(3):
                    page.locator('#content .result[data-job=sample-a] .gallery-media').focus()
                    page.keyboard.press('Enter')
                    try:
                        page.wait_for_selector('.gallery-lightbox[open]',timeout=4000);break
                    except Exception:
                        if attempt==2: raise
                capture('details',width,height)
                panel=page.locator('.gallery-lightbox').bounding_box()
                assert panel['x']>=0 and panel['x']+panel['width']<=width+1
                page.keyboard.press('Escape')
                assert page.locator('.gallery-lightbox').count()==0
                page.evaluate("navigate('settings')")
                capture('settings',width,height)
                assert page.locator('#studio-settings').get_attribute('aria-current')=='page'
            page.set_viewport_size({'width':1360,'height':900})
            page.evaluate("""()=>{loaded=E.normalize(models.find(m=>m.id==='google-flow/nano2'));state.draft={modelId:loaded.id,prompt:'Una esfera con luz cálida',values:E.defaults(loaded),media:[]};navigate('canvas');}""")
            for width,height in [(1360,900),(480,850),(1280,600)]:
                capture('canvas-loaded',width,height)
                button=page.locator('#generate').bounding_box()
                assert button['y']+button['height']<=height+1,(width,button)
                assert page.locator('.nx-selected-label').count()==1
            page.set_viewport_size({'width':1360,'height':900})
            page.evaluate("""()=>{Agent.items=[{role:'user',text:'Quiero preparar una imagen con luz cálida y una composición sencilla.'},{role:'assistant',text:'Podemos empezar por el encuadre y la luz. Después revisamos las opciones del modelo y su coste.',html:'<p>Podemos empezar por el <strong>encuadre y la luz</strong>. Después revisamos las opciones del modelo y su coste.</p>'}];navigate('assistant');}""")
            capture('conversation',1360)
            assert page.locator('.nx-avatar').count()==2
            for accent in ('turquoise','amber','violet'):
                page.evaluate('(accent)=>{state.accent=accent;render();}',accent)
                assert page.evaluate('document.body.classList.contains("nx-desktop")')
                assert page.evaluate('(accent)=>document.body.classList.contains(accent)',accent)
            page.evaluate("()=>{state.theme='oled';render();}")
            assert page.evaluate('getComputedStyle(document.body).getPropertyValue("--nx-bg").trim()').lower()=='#000000'  # tema OLED de interface-themes.js
            page.emulate_media(reduced_motion='reduce')
            assert page.locator('#agent-compose').evaluate('(node)=>getComputedStyle(node).transitionDuration')=='0s'
            assert not errors,errors
            assert not blocked,blocked
            report={'status':'PASS','screens':screens,'javascriptErrors':errors,'blockedOperations':blocked,
                'externalInference':0,'generations':0,'shortcutOnlyFillsDraft':True,'modalKeyboardPreserved':True,
                'draftPreserved':True,'themeAndAccentsPreserved':True,'reducedMotion':True}
            (out/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
            print(json.dumps(report))
            browser.close()
    finally:
        host.terminate();host.wait(timeout=10);log.close()
