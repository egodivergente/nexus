"""Isolated desktop UI with local queue fixtures and intercepted Flow RPCs."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'outputs/flow-automation-20261008';OUT.mkdir(parents=True,exist_ok=True)
errors=[];forbidden=[]
with tempfile.TemporaryDirectory(prefix='nexus-flow-ui-') as folder:
    data=Path(folder)
    host=subprocess.Popen([sys.executable,str(ROOT/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],
                          stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            try:session=json.loads((data/'local-session.json').read_text());break
            except (OSError,ValueError):time.sleep(.1)
        else:raise RuntimeError('QA host did not start')
        fixture={'policy':{'max_results':120,'max_total_results':120,'max_pending':120,'max_credits':1000,'allow_unknown_cost':False,'paused':False},
                 'pending':1,'reserved_credits':10,'requests':[{'id':'fixture','mode':'submit','phase':'queued','created_at':0,'started_at':None,'finished_at':None,'metrics':{'quote_ms':123}}]}
        with sync_playwright() as pw:
            browser=pw.chromium.launch(channel='msedge',headless=True)
            page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
            page.on('pageerror',lambda e:errors.append(str(e)));page.route('https://**/*',lambda r:r.abort())
            def guard(route):
                m=route.request.post_data_json;op=m['op'];value=None
                if op in ['generate','flowSubmit','flowRecover','flowQuote','upload','agent']:
                    forbidden.append(op);route.fulfill(json={'id':m['id'],'ok':False,'error':'paid/remote operation blocked'});return
                if op=='flowAutomation':value=fixture
                elif op=='flowPolicy':fixture['policy'].update(m['args']);value=fixture['policy']
                elif op=='flowCancel':
                    fixture['requests'][0]['phase']='not_sent';fixture['pending']=0;fixture['reserved_credits']=0
                    value={'id':'fixture','status':'not_sent','outputs':[],'requested_model':'nano-pro','model_confirmed':False,'queue_phase':'not_sent','timings':{}}
                elif op in ['flowConnection','flowConnect']:value={'status':'connected','checked_at':time.time(),'source':'flow_editor','cached':True}
                if value is not None:route.fulfill(json={'id':m['id'],'ok':True,'value':value})
                else:route.continue_()
            page.route('**/rpc',guard);page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('!!window.NexusFlowControl&&!!window.NexusGallery&&!!window.nexusQA')
            page.evaluate("""()=>{
              initial.qa=false;initial.configured=['google_flow'];state.jobs=[{id:'fixture',remoteId:'fixture',providerId:'google_flow',modelId:'google-flow/nano-pro',name:'Nano Banana Pro',modality:'IMAGE',status:'QUEUED',prompt:'PRIVATE PROMPT',parameters:{},outputs:[]}];
              parseResponse(state.jobs[0],{id:'fixture',status:'queued',outputs:[],requested_model:'nano-pro',model_confirmed:false,queue_phase:'queued',timings:{quote_ms:123}});
              render();
            }""")
            page.wait_for_selector('#flow-control-button');page.click('#flow-control-button')
            page.wait_for_selector('#flow-control-rows [data-flow-cancel]');assert '123 ms' in page.locator('#flow-control-rows').inner_text()
            page.fill('[name=max_credits]','25');page.click('#flow-control-policy button[type=submit]')
            page.wait_for_function("document.querySelector('#flow-control-status').textContent.includes('10 créditos')")
            assert fixture['policy']['max_credits']==25
            page.click('#flow-control-pause');page.wait_for_function("document.querySelector('#flow-control-pause').textContent==='Reanudar cola'")
            for width in [1360,480]:
                page.set_viewport_size({'width':width,'height':900});page.screenshot(path=str(OUT/f'flow-control-{width}.png'))
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),width
            page.click('[data-flow-cancel]');page.wait_for_function("state.jobs[0].status==='NOT_SENT'")
            page.wait_for_selector('[data-flow-cancel]',state='detached')
            assert page.evaluate('state.jobs[0].modelConfirmed') is False
            assert not page.locator('[data-flow-cancel]').count()
            page.click('#flow-control-close');page.wait_for_function('!document.querySelector("#flow-control")')
            # The visor must distinguish a requested model from provider attribution.
            page.evaluate("""()=>{const j=state.jobs[0];j.status='COMPLETED';j.outputs=['/media/'+'a'.repeat(32)+'.png'];Studio.tab='results';navigate('library');studioDetails('fixture');}""")
            assert 'atribución no confirmada' in page.locator('.flow-model-evidence').inner_text()
            page.keyboard.press('Escape');assert not page.locator('.gallery-lightbox').count()
            assert not errors,errors;assert not forbidden,forbidden
            report={'status':'PASS','responsive':[1360,480],'policyPauseCancel':True,'modelAttribution':True,'javascriptErrors':errors,'remoteOperations':forbidden,'paidCalls':0}
            (OUT/'ui-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
            browser.close()
    finally:
        host.terminate();host.wait(timeout=10)
