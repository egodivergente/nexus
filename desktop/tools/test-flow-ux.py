"""Headless UI regressions with isolated data and simulated provider replies."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
out = root / 'outputs/flow-ux-20261006'
data = out / 'qa'
(data / 'media').mkdir(parents=True, exist_ok=True)
for char in ('a', 'b'):
    shutil.copyfile(root / 'desktop/ui/logos/flux.png', data / 'media' / (char*32+'.png'))
python = Path(sys.base_prefix) / 'python.exe'
log = (out / 'host.log').open('wb')
process = subprocess.Popen([str(python), str(root / 'desktop/local_host.py'), '--headless',
    '--offline-qa', '--data-dir', str(data)], stdout=log, stderr=log,
    creationflags=subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS)
try:
    for _ in range(300):
        if process.poll() is not None:
            raise RuntimeError('Isolated host exited; see host.log')
        try:
            session = json.loads((data / 'local-session.json').read_text(encoding='utf-8'))
            if session['pid'] == process.pid: break
        except (OSError, ValueError): pass
        time.sleep(.1)
    else: raise RuntimeError('Isolated host failed; see host.log')
    errors, calls = [], []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel='msedge', headless=True)
        page = browser.new_page(viewport={'width':1360,'height':900}, bypass_csp=True)
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('https://**/*', lambda route: route.abort())

        def fixture(route):
            message = route.request.post_data_json
            op, args = message['op'], message.get('args', {})
            if op not in ('flowRecover', 'flowStatus', 'flowSubmit', 'generate', 'upload', 'agent'):
                route.continue_(); return
            calls.append({'op':op, 'id':args.get('request_id')})
            if op == 'flowRecover':
                time.sleep(.15)
                result = {'id':args['request_id'],'status':'in_progress','recovery_started_at':100,'reconciling':True}
            elif op == 'flowStatus':
                result = {'id':args['request_id'],'status':'completed','recovery_started_at':100,'outputs':['/media/'+'a'*32+'.png']}
            else:
                route.fulfill(json={'id':message['id'],'ok':False,'error':'Fixture: envío e inferencia bloqueados.'}); return
            route.fulfill(json={'id':message['id'],'ok':True,'value':result})

        page.route('**/rpc', fixture)
        page.goto(f"http://127.0.0.1:{session['port']}/")
        page.wait_for_function('Boolean(window.nexusQA)&&typeof recoverFlowJob==="function"')
        assert page.evaluate('models.filter(forbiddenModel).length') == 0
        page.evaluate("""()=>{
         state.jobs=[{id:'recover-fixture',remoteId:'stable-existing',providerId:'google_flow',modelId:'google-flow/nano2',name:'Flow pendiente',modality:'IMAGE',prompt:'Esfera',status:'FAILED',uncertain:true,error:'Envío incierto',startedAt:Date.now()-60000,outputs:[]},
          {id:'source-image',remoteId:'source-owned',providerId:'google_flow',modelId:'google-flow/nano2',name:'Original',modality:'IMAGE',prompt:'Esfera',status:'COMPLETED',startedAt:Date.now(),outputs:['/media/'+'a'.repeat(32)+'.png','/media/'+'b'.repeat(32)+'.png']}];
         Studio.tab='activity';navigate('library');
        }""")
        page.locator('[data-studio-detail=recover-fixture]').click()
        assert 'Envío por comprobar' in page.locator('.studio-details').inner_text()
        page.locator('[data-flow-recover=recover-fixture]').click()
        page.evaluate("()=>{recoverFlowJob('recover-fixture');}")
        page.wait_for_function("!FlowRecovery.busy.size&&state.jobs[0].status==='PROCESSING'")
        assert len([call for call in calls if call['op']=='flowRecover']) == 1, calls
        assert page.locator('[data-flow-recover=recover-fixture]').is_disabled()
        page.evaluate("async()=>{initial.qa=false;await poll(true);await flush();initial.qa=true;}")
        assert page.evaluate("state.jobs[0].status==='COMPLETED'&&!state.jobs[0].uncertain")
        assert 'Completado' in page.locator('.studio-details').inner_text()
        page.keyboard.press('Escape')
        assert page.locator('.studio-details').count() == 0
        assert page.evaluate("document.activeElement.dataset.studioDetail==='recover-fixture'")
        page.locator('[data-studio-detail=source-image]').click()
        page.locator('.studio-details [data-output-next]').click()
        assert 'Resultado 2 de 2' in page.locator('.studio-details').inner_text()
        page.locator('[data-studio=close-detail]').click()
        assert page.evaluate("document.activeElement.dataset.studioDetail==='source-image'")
        page.locator('#pc-search').fill('sin-coincidencias-de-prueba')
        page.wait_for_timeout(300)
        assert 'No hay coincidencias' in page.locator('.empty').inner_text()
        page.locator('[data-ux=clear-filters]').first.click()
        assert page.locator('#pc-search').input_value() == ''
        page.evaluate("()=>{navigate('canvas');studioPicker();}")
        assert page.locator('.studio-model-row').count() <= 50
        page.locator('[data-ux=more-models]').click()
        assert page.locator('.studio-model-row').count() == 100
        def choose(selector, label):
            page.locator(selector+' + .select-trigger').click()
            popup = page.locator('.select-popup')
            assert popup.evaluate('(list)=>Boolean(list.closest("dialog[open]"))')
            popup.get_by_role('option', name=label, exact=True).click()
        choose('#ux-model-provider', 'Google Flow')
        expected_images = page.evaluate("models.filter(m=>m.providerId==='google_flow'&&m.modalities.includes('IMAGE')).length")
        assert page.locator('.studio-model-row').count() == expected_images
        choose('#studio-model-kind', 'Video')
        choose('#ux-model-family', 'Veo')
        expected_video = page.evaluate("models.filter(m=>m.providerId==='google_flow'&&m.modalities.includes('VIDEO')&&m.modelFamily==='Veo').length")
        assert page.locator('.studio-model-row').count() == expected_video
        page.wait_for_function("document.querySelector('#studio-model-kind').nextElementSibling.textContent==='Video'&&document.querySelector('#ux-model-family').nextElementSibling.textContent==='Veo'")
        assert page.locator('#ux-model-provider').evaluate('(select)=>select.nextElementSibling.textContent') == 'Google Flow'
        page.locator('#studio-model-kind').evaluate('(select)=>{select.disabled=true;}')
        page.wait_for_function("document.querySelector('#studio-model-kind').nextElementSibling.disabled")
        page.locator('#studio-model-kind').evaluate('(select)=>{select.disabled=false;}')
        page.wait_for_function("!document.querySelector('#studio-model-kind').nextElementSibling.disabled")
        page.locator('#ux-model-family + .select-trigger').click()
        page.locator('.select-popup [aria-selected=true]').press('Escape')
        assert page.locator('.select-popup').count() == 0
        assert page.locator('#dialog').evaluate('(dialog)=>dialog.open')
        for width in (1360,760,480):
            page.set_viewport_size({'width':width,'height':900})
            page.screenshot(path=str(out / f'picker-{width}.png'))
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.locator('#studio-model-search').fill('sin-modelos-que-coincidan')
        page.locator('[data-ux=reset-models]').click()
        page.wait_for_function("document.querySelector('#ux-model-provider').value===''&&document.querySelector('#ux-model-provider').nextElementSibling.textContent==='Todos los proveedores'")
        assert page.locator('#studio-model-search').input_value() == ''
        assert page.locator('.studio-model-row').count() > 1
        page.locator('#dialog [data-action=close]').click()
        page.evaluate("""()=>{
         window.fixtureRpc=rpc;rpc=async(op,args)=>{if(op==='flowAssetOptions')return new Promise(resolve=>window.optionsRelease=resolve);return fixtureRpc(op,args);};
         window.pendingOptions=flowAssetDialog('source-image');
        }""")
        page.locator('#dialog [data-action=close]').click()
        page.evaluate("async()=>{optionsRelease({kind:'IMAGE',options:[{resolution:'2k',credits:0}]});await pendingOptions;}")
        assert not page.locator('#dialog').evaluate('(dialog)=>dialog.open')
        page.evaluate("()=>{window.pendingOptions=flowAssetDialog('source-image');modal('<h2>Otra ventana</h2>');}")
        page.evaluate("async()=>{optionsRelease({kind:'IMAGE',options:[{resolution:'2k',credits:0}]});await pendingOptions;}")
        assert page.locator('#dialog h2').inner_text() == 'Otra ventana'
        page.evaluate("""async()=>{rpc=async(op,args)=>{if(op==='flowAssetOptions')throw Error('Consulta interrumpida');return fixtureRpc(op,args);};await flowAssetDialog('source-image');}""")
        assert page.locator('#dialog .loading').count() == 0
        assert page.locator('#dialog [role=alert]').inner_text() == 'Consulta interrumpida'
        page.evaluate("""async()=>{rpc=async(op,args)=>op==='flowAssetOptions'?{kind:'IMAGE',options:[{resolution:'2k',credits:0,label:'2K'}]}:fixtureRpc(op,args);await flowAssetDialog('source-image');flowAssetPlan.time-=300001;flowAssetPrice();}""")
        assert page.locator('[data-flow-process]').is_disabled()
        assert 'caducado' in page.locator('#flow-asset-cost').inner_text()
        page.evaluate("""async()=>{rpc=async(op,args)=>op==='flowAssetOptions'?{kind:'IMAGE',options:[{resolution:'2k',credits:'gratis'}]}:fixtureRpc(op,args);await flowAssetDialog('source-image');}""")
        assert page.locator('[data-flow-process]').count() == 0
        page.evaluate("""async()=>{rpc=async(op,args)=>op==='flowAssetOptions'?{kind:'IMAGE',options:[{resolution:'2k',credits:0,label:'2K'},{resolution:'4k',credits:0,label:'4K'}]}:fixtureRpc(op,args);await flowAssetDialog('source-image');}""")
        for width in (1360,760,480):
            page.set_viewport_size({'width':width,'height':900})
            page.screenshot(path=str(out / f'upscale-{width}.png'))
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        page.locator('#dialog [data-action=close]').click()
        page.evaluate("()=>{rpc=fixtureRpc;Studio.tab='results';navigate('library');studioDetails('source-image');}")
        page.screenshot(path=str(out / 'details-480.png'))
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        assert not errors, errors
        assert not [call for call in calls if call['op'] in ('flowSubmit','generate','upload','agent')], calls
        report = {'errors':errors,'calls':calls,'models':page.evaluate('models.length'),
                  'viewports':[1360,760,480],'paidCalls':0,'inferenceCalls':0,
                  'recoverySingleRequest':True,'cancelledDialogStayedClosed':True,'expiredQuoteBlocked':True,
                  'familyAndProviderFilters':True,'keyboardFocusReturned':True}
        (out / 'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps(report))
        browser.close()
finally:
    if process.poll() is None:
        process.terminate(); process.wait(timeout=10)
    log.close()
