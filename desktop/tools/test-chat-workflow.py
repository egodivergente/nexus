"""Real keyboard/scroll/theme interactions; local-only simulated generation confirmations."""
import json
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'outputs/chat-workflow-20261008'
OUT.mkdir(parents=True, exist_ok=True)
errors, operations, confirmations = [], [], []
snapshot = {'mode':'AGENT','offline':False,'projectId':None,'index':{'projects':[], 'assets':[]},'skills':[], 'plans':[], 'proposals':[]}

with tempfile.TemporaryDirectory(prefix='nexus-chat-workflow-') as folder:
    data=Path(folder)
    host=subprocess.Popen([sys.executable,str(ROOT/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            try:
                session=json.loads((data/'local-session.json').read_text(encoding='utf-8'));break
            except (OSError, ValueError): time.sleep(.1)
        else: raise RuntimeError('QA host unavailable')
        with sync_playwright() as p:
            browser=p.chromium.launch(channel='msedge',headless=True)
            page=browser.new_page(viewport={'width':1920,'height':1080},bypass_csp=True)
            page.on('pageerror',lambda e:errors.append(str(e)))
            page.route('https://**/*',lambda route:route.abort())
            def guard(route):
                m=route.request.post_data_json
                if m['op']=='agent':
                    op=m['args']['op'];operations.append(op)
                    if op=='confirm':
                        args=m['args']['args'];confirmations.append(args)
                        if args['id']=='fail':
                            route.fulfill(json={'id':m['id'],'ok':False,'error':'Rechazo simulado del proveedor'});return
                        for entry in snapshot['plans']:
                            if entry['plan']['id']==args['id']:entry['state']='SUBMITTED'
                    route.fulfill(json={'id':m['id'],'ok':True,'value':{'settings':{},'result':snapshot}})
                elif m['op']=='agentEvents':route.fulfill(json={'id':m['id'],'ok':True,'value':[]})
                elif m['op'] in ['generate','flowSubmit','flowQuote','request','upload']:
                    raise AssertionError('External operation forbidden: '+m['op'])
                else:route.continue_()
            page.route('**/rpc',guard)
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('!!window.NexusThemes && !!window.NexusModelSuggestions && !!window.NexusCreative')
            page.evaluate('s=>{Agent.snapshot=s;Agent.restored=true;Agent.items=[];navigate("assistant");}',snapshot)
            editor=page.locator('#agent-input')
            for prefix in ['seed','seed 5 pro','kling','flux','wan','veo','gen4','qwen']:
                editor.fill('Crear con '+prefix)
                page.wait_for_selector('#chat-model-suggestions:not([hidden])')
                names=page.locator('#chat-model-suggestions [role=option]>span').all_text_contents()
                assert names and page.evaluate('names=>names.every(n=>models.some(m=>m.displayName===n))',names)
                if prefix=='seed':assert 'Seedream v5.0 Pro' in names
                editor.press('Escape')
                assert page.locator('#chat-model-suggestions').is_hidden()
            # Replace only the model fragment at the caret, preserving the rest.
            editor.fill('Escena con seed en Madrid')
            editor.evaluate('n=>{n.setSelectionRange(15,15);NexusModelSuggestions.update(n);}');
            expected=page.locator('#model-suggestion-1>span').inner_text()
            before=len(operations)
            editor.press('ArrowDown');editor.press('Enter')
            assert editor.input_value()=='Escena con '+expected+' en Madrid'
            assert len(operations)==before and page.evaluate('Agent.items.length')==0
            assert editor.evaluate('n=>document.activeElement===n && n.selectionStart===n.selectionEnd')
            # Pointer completion and Tab both keep focus; Escape leaves text untouched.
            editor.fill('Usar seed')
            page.locator('#chat-model-suggestions [role=option]').filter(has=page.locator('span',has_text='Seedream v5.0 Pro')).first.click()
            assert 'Seedream v5.0 Pro' in editor.input_value()
            assert editor.evaluate('n=>document.activeElement===n')
            editor.fill('Usar flux');expected=page.locator('#model-suggestion-0>span').inner_text();editor.press('Tab')
            assert editor.input_value()=='Usar '+expected+' '
            editor.fill('Usar seed');before=len(operations);editor.press('Control+Enter')
            page.wait_for_function('!Agent.busy && Agent.items.length===2')
            assert len(operations)==before+1 and operations[-1]=='send'
            assert page.locator('#chat-model-suggestions').is_hidden()
            editor.fill('Una escena sin referencias a modelos.')
            assert page.locator('#chat-model-suggestions').is_hidden()
            # Suggestions must not move someone who is reading older messages.
            page.evaluate("for(let i=0;i<35;i++)Agent.items.push({role:'assistant',text:'Respuesta '+i+' '+('Continuidad visual. '.repeat(25))});renderAssistant();")
            page.wait_for_timeout(150)
            box=page.locator('.agent-messages').bounding_box();page.mouse.move(box['x']+40,box['y']+40);page.mouse.wheel(0,-1200);page.wait_for_timeout(150)
            top=page.locator('.agent-messages').evaluate('n=>n.scrollTop')
            editor.fill('Crear con seed');editor.press('ArrowDown');editor.press('Escape');page.wait_for_timeout(150)
            assert abs(page.locator('.agent-messages').evaluate('n=>n.scrollTop')-top)<3
            # All theme surfaces and accents change immediately and remain after render/reload.
            page.evaluate('navigate("settings")')
            assert page.locator('button[data-interface-theme]').count()==6
            colors=[]
            for theme in ['aurora','ocean','quartz','forest','light','oled']:  # ids actuales de interface-themes.js
                page.locator(f'button[data-interface-theme={theme}]').click()
                assert page.locator(f'button[data-interface-theme={theme}]').get_attribute('aria-pressed')=='true'
                page.wait_for_function('document.body.classList.contains("creative-engine")')
                colors.append(page.evaluate('getComputedStyle(document.body).getPropertyValue("--bg").trim()'))
                assert page.evaluate('document.body.classList.contains("nx-desktop")')
            assert len(set(colors))==6
            page.locator('button[data-interface-theme=ocean]').click();page.evaluate('flush()')
            page.screenshot(path=str(OUT/'themes.png'))
            page.reload();page.wait_for_function('!!window.NexusThemes && document.body.dataset.interfaceTheme==="ocean"')
            # Keep each generation after its own response, even while later turns arrive.
            model=page.evaluate("models.find(m=>m.id==='bytedance/seedream-v5.0-pro/text-to-image')")
            def entry(id, state='PREPARED'):
                return {'state':state,'fingerprint':'fp-'+id,'plan':{'id':id,'ref':{'providerId':model['providerId'],'modelId':model['id']},'modelName':model['displayName'],'modality':'IMAGE','prompt':'Una escena de Mateo','params':{'aspect_ratio':'16:9'},'effectiveParams':{'aspect_ratio':'16:9'},'estimate':{'basis':'Coste no disponible'}}}
            snapshot['plans']=[entry('fail'),entry('ok'),entry('uncertain','UNCERTAIN')]
            page.evaluate("s=>{Agent.snapshot=s;Agent.restored=true;Agent.items=[{role:'user',text:'Genera dos'},{role:'assistant',text:'Encargos preparados'}];Agent.cardAnchors.clear();state.jobs=[];models.find(m=>m.id===s.plans[0].plan.ref.modelId).schemaUrl=null;quotePlan=async()=>{await new Promise(r=>setTimeout(r,150));return {amount:null,currency:'unknown',status:'unavailable',time:Date.now()};};navigate('assistant');}",snapshot)
            assert page.evaluate("document.querySelector('.agent-message.assistant').nextElementSibling.hasAttribute('data-agent-card')")
            page.evaluate("Agent.items.push({role:'user',text:'Siguiente mensaje'},{role:'assistant',text:'Respuesta posterior'});renderAssistant();")
            assert page.evaluate("document.querySelector('.agent-message.assistant').nextElementSibling.hasAttribute('data-agent-card')")
            # One click dispatches both plans, continues after failure, never opens a dialog.
            page.locator('[data-agent=generate-ready]').click()
            page.wait_for_function('Agent.generating')
            page.evaluate("document.querySelector('[data-agent=generate-ready]')?.click()")
            page.wait_for_function('!Agent.generating && !Agent.busy')
            assert [c['id'] for c in confirmations]==['fail','ok'],confirmations
            assert all(c['fingerprint']=='fp-'+c['id'] for c in confirmations)
            assert page.evaluate('!document.querySelector("#dialog").open')
            assert 'edit' not in operations and 'discard' not in operations
            assert page.evaluate('Agent.snapshot.plans[0].plan.params.count===undefined')
            # A repeated prompt creates a new actionable order instead of merging with a sent one.
            snapshot['plans'].append(entry('next'))
            page.evaluate('s=>{Agent.snapshot=s;renderAssistant();}',snapshot)
            assert page.evaluate("findGenerationBatch('image:ok').entries.length===1")
            assert page.evaluate("generationBatches(Agent.snapshot.plans).byPlanId.get('next').jobs.length===0")
            different=entry('different-seed');different['plan']['params']['seed']='42';different['plan']['effectiveParams']['seed']='42'
            snapshot['plans'].append(different);page.evaluate('s=>{Agent.snapshot=s;renderAssistant();}',snapshot)
            assert page.evaluate("generationBatches(Agent.snapshot.plans).byPlanId.get('next').id!==generationBatches(Agent.snapshot.plans).byPlanId.get('different-seed').id")
            for width,height in [(1920,1080),(480,850)]:
                page.set_viewport_size({'width':width,'height':height});editor.fill('Crear con seed');page.wait_for_timeout(120)
                bounds=page.locator('#chat-model-suggestions').bounding_box()
                assert bounds['x']>=0 and bounds['x']+bounds['width']<=width+1 and bounds['y']>=0,bounds
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
                page.screenshot(path=str(OUT/f'suggestions-{width}.png'));editor.press('Escape')
            # Completed paid results also survive refresh/host snapshot reset.
            page.evaluate("state.jobs.push({id:'saved-paid-result',agentPlanId:'ok',providerId:'atlas_cloud',modality:'IMAGE',status:'COMPLETED',outputs:[]});renderAssistant();")
            assert page.evaluate("state.agentConversation.resultPlans.some(e=>e.plan.id==='ok')")
            page.evaluate('flush()');snapshot['plans']=[]
            page.reload();page.wait_for_function('!!window.NexusModelSuggestions && !!window.NexusThemes')
            page.evaluate('s=>{Agent.snapshot=s;navigate("assistant");}',snapshot)
            assert page.locator('[data-agent-card="batch:image:ok"]').count()==1
            assert page.locator('[data-agent=generate-ready]').count()==0
            # A clean visual deliverable, still inside the isolated QA session.
            page.set_viewport_size({'width':1920,'height':1080})
            page.evaluate("Agent.items=[];Agent.restored=true;Agent.cardAnchors.clear();state.agentConversation=null;state.jobs=[];document.querySelector('#toast').classList.remove('show');renderAssistant();")
            editor.fill('Genera 8 imágenes de Mateo con seed')
            page.screenshot(path=str(OUT/'autocomplete-preview.png'))
            assert not errors,errors
            browser.close()
        report={'pass':True,'catalogAutocomplete':True,'keyboardAndCaret':True,'readingPositionPreserved':True,'sixThemesPersisted':True,'singleClickGeneration':True,'failedPlanIsolated':True,'noUnsupportedCount':True,'chronologicalResults':True,'noHistoricalMerge':True,'differentParametersKeptSeparate':True,'paidResultsPersisted':True,'paidCalls':0,'simulatedConfirmations':len(confirmations),'javascriptErrors':errors}
        (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    finally:
        host.terminate();host.wait(timeout=15)
