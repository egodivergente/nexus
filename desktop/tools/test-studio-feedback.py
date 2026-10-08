"""Persistent canvas feedback, async races and recovery; no real provider calls."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'outputs/studio-feedback-20261008'
OUT.mkdir(parents=True, exist_ok=True)
errors, blocked = [], []
with tempfile.TemporaryDirectory(prefix='nexus-feedback-') as folder:
    data = Path(folder)
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
                    route.fulfill(json={'id':message['id'],'ok':False,'error':'External operation blocked'})
                else:
                    route.continue_()
            page.route('**/rpc', guard)
            page.goto(f"http://127.0.0.1:{session['port']}/", wait_until='domcontentloaded')
            page.wait_for_function('window.NexusStudioUX && window.NexusGallery && window.nexusQA')
            assert not errors, errors
            page.evaluate("""async()=>{
                state.draft.prompt='Mi idea conservada';state.studioHelp={dismissed:true};
                window.fixture=id=>E.normalize({id,providerId:id==='feedback-flow'?'google_flow':'atlas_cloud',
                    displayName:id,modalities:['IMAGE'],parameters:[],capabilities:[]});
                window.modelAttempts=0;
                configuration=async id=>{if(++modelAttempts===1)throw Error('No se pudo descargar el esquema');return fixture(id);};
                await openModel('feedback-image');
            }""")
            page.wait_for_selector('[data-canvas-recovery=model]')
            assert 'No se pudo descargar el esquema' in page.locator('#studio-canvas-feedback').inner_text()
            assert page.locator('#prompt').input_value() == 'Mi idea conservada'
            page.locator('[data-canvas-recovery=model]').click()
            page.wait_for_function("loaded?.id==='feedback-image' && !configuring")
            assert page.locator('#studio-canvas-feedback').count() == 0
            assert page.evaluate('modelAttempts') == 2

            # An old model failure must not replace a newer successful choice.
            page.evaluate("""()=>{
                configuration=id=>id==='feedback-old'?new Promise((_,reject)=>window.rejectOldModel=reject):Promise.resolve(fixture(id));
                window.oldModelTask=openModel('feedback-old');
            }""")
            page.wait_for_function("document.querySelector('#content').getAttribute('aria-busy')==='true'")
            page.evaluate("async()=>{await openModel('feedback-new');rejectOldModel(Error('Antiguo fallo'));await oldModelTask;}")
            assert page.evaluate("loaded.id==='feedback-new' && CanvasFeedback.error===null")

            # Re-rendering while quoting preserves the pending state and disabled button.
            page.evaluate("""()=>{
                quotePlan=()=>new Promise(resolve=>window.resolveQuote=resolve);
                window.pendingQuote=getQuote();
            }""")
            page.wait_for_function('CanvasFeedback.operation===\'quote\' && typeof resolveQuote===\'function\'')
            page.evaluate('renderCanvas()')
            page.wait_for_selector('.studio-operation')
            assert page.locator('#quote-button').is_disabled()
            assert page.locator('#content').get_attribute('aria-busy') == 'true'
            page.evaluate("async()=>{resolveQuote(unavailableCost(Error('HTTP 403: consulta rechazada')));await pendingQuote;}")
            page.wait_for_selector('[data-canvas-recovery=quote]')
            assert page.locator('#cost').inner_text() == 'Coste no disponible'
            assert 'HTTP 403' in page.locator('#studio-canvas-feedback').inner_text()
            assert not page.locator('#generate').is_disabled(), 'Existing unknown-price behavior must remain available'
            for width in [1360,760,480]:
                page.set_viewport_size({'width':width,'height':900})
                assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'), width
                page.screenshot(path=str(OUT / f'quote-error-{width}.png'))
            page.evaluate("()=>{quotePlan=async()=>({amount:.05,currency:'USD',time:Date.now()});}")
            page.locator('[data-canvas-recovery=quote]').click()
            page.wait_for_function('quote?.amount===.05 && CanvasFeedback.operation===null')
            assert page.locator('#studio-canvas-feedback').count() == 0

            # A stale failed quote cannot attach an error to a new model.
            page.evaluate("""()=>{
                quotePlan=()=>new Promise((_,reject)=>window.rejectOldQuote=reject);
                window.oldQuoteTask=getQuote();
            }""")
            page.wait_for_function('typeof rejectOldQuote===\'function\'')
            page.evaluate("async()=>{await openModel('feedback-next');rejectOldQuote(Error('Cotización antigua'));await oldQuoteTask;}")
            assert page.evaluate("loaded.id==='feedback-next' && quote===null && CanvasFeedback.error===null")

            # A late prompt adaptation belongs to the original model, not the new choice.
            page.evaluate("""()=>{
                window.originalAdapt=adaptPlanPrompt;
                adaptPlanPrompt=plan=>new Promise(resolve=>window.resolveAdapt=()=>{plan.prompt='Resumen antiguo';resolve(true);});
                window.oldAdaptTask=getQuote();
            }""")
            page.wait_for_function('typeof resolveAdapt===\'function\'')
            page.evaluate("async()=>{await openModel('feedback-after-adapt');resolveAdapt();await oldAdaptTask;adaptPlanPrompt=originalAdapt;}")
            assert page.evaluate("loaded.id==='feedback-after-adapt' && state.draft.prompt==='Mi idea conservada'")

            # The real Generate handler is exercised; dispatch is an isolated simulator.
            page.evaluate("""async()=>{
                await openModel('feedback-flow');quote={amount:null,currency:'flow_account',time:Date.now()};render();
                window.sendCount=0;
                dispatch=async plan=>{
                    sendCount++;const job={id:'feedback-send',remoteId:'existing-flow-request',providerId:'google_flow',
                        modelId:plan.model.id,name:plan.model.displayName,prompt:plan.prompt,parameters:{},modality:'IMAGE',
                        status:'SENDING',startedAt:Date.now(),outputs:[]};state.jobs.unshift(job);
                    try{await new Promise((_,reject)=>window.rejectSend=reject);}
                    catch(error){job.status='FAILED';job.uncertain=true;job.error=error.message;throw error;}
                };
            }""")
            page.locator('#generate').click()
            page.wait_for_function("CanvasFeedback.operation==='send'")
            assert page.locator('#generate').is_disabled()
            assert 'Preparando y enviando' in page.locator('.studio-operation').inner_text()
            page.evaluate('generate()')
            assert page.evaluate('sendCount') == 1
            page.evaluate("rejectSend(Error('No se recibió confirmación del proveedor'))")
            page.wait_for_selector('[data-canvas-recovery=recover]')
            assert 'Envío por comprobar' in page.locator('#studio-canvas-feedback').inner_text()
            assert page.locator('[data-canvas-recovery=model],[data-canvas-recovery=quote]').count() == 0
            page.evaluate("""()=>{
                window.recoveryCalls=[];const original=rpc;
                rpc=async(op,args)=>{
                    if(op==='flowRecover'){recoveryCalls.push(args.request_id);return {id:args.request_id,status:'completed',outputs:['/media/'+'a'.repeat(32)+'.png']};}
                    return original(op,args);
                };
            }""")
            page.locator('[data-canvas-recovery=recover]').click()
            page.wait_for_function("state.jobs.find(j=>j.id==='feedback-send').status==='COMPLETED' && CanvasFeedback.operation===null")
            assert page.evaluate('recoveryCalls') == ['existing-flow-request']
            assert page.evaluate('sendCount') == 1
            assert page.locator('#studio-canvas-feedback').count() == 0
            assert not errors, errors
            assert not blocked, blocked
            report={'status':'PASS','modelRetryPreservesPrompt':True,'staleModelFailureIgnored':True,
                'quoteLoadingSurvivesRender':True,'quoteErrorAndRetry':True,'unknownPriceUnchanged':True,
                'staleQuoteErrorIgnored':True,'stalePromptAdaptationIgnored':True,
                'sendLoadingAndBusyGuard':True,'recoveryUsesExistingRequest':True,
                'widths':[1360,760,480],'pageErrors':errors,'providerCalls':0,'paidCalls':0}
            (OUT / 'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
            print(json.dumps(report))
            browser.close()
    finally:
        host.terminate()
        host.wait(timeout=10)
