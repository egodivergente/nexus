"""Direct Flow callback and one scrolling conversation; isolated data and intercepted transport."""
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'outputs/flow-direct-chat-20261008';OUT.mkdir(parents=True,exist_ok=True)
errors=[]
with tempfile.TemporaryDirectory(prefix='nexus-flow-direct-') as folder:
    data=Path(folder);(data/'media').mkdir()
    shutil.copyfile(ROOT/'desktop/ui/logos/flux.png',data/'media'/('a'*32+'.png'))
    host=subprocess.Popen([sys.executable,str(ROOT/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],
                          stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            try:session=json.loads((data/'local-session.json').read_text(encoding='utf-8'));break
            except (OSError,ValueError):time.sleep(.1)
        else:raise RuntimeError('QA host unavailable')
        with sync_playwright() as p:
            browser=p.chromium.launch(channel='msedge',headless=True)
            page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
            page.on('pageerror',lambda e:errors.append(str(e)))
            page.route('https://**/*',lambda r:r.abort())
            calls=[]
            def guard(route):
                m=route.request.post_data_json
                if m['op']=='flowSubmit':
                    calls.append(m['args']);route.fulfill(json={'id':m['id'],'ok':True,'value':{'id':m['args']['request_id'],'status':'queued','outputs':[]}})
                elif m['op'] in ['generate','upload','request','flowQuote','agent']:
                    raise AssertionError('Unexpected external operation: '+m['op'])
                else:route.continue_()
            page.route('**/rpc',guard)
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('!!window.NexusCreative&&!!window.NexusGallery')
            page.evaluate("""()=>{
              Agent.snapshot={mode:'AGENT',offline:false,projectId:null,index:{projects:[],assets:[]},skills:[],plans:[],proposals:[]};
              Agent.restored=true;Agent.items=[];Agent.cardAnchors.clear();
              for(let i=0;i<20;i++){
                Agent.items.push({role:'user',text:'Escena '+i},{role:'assistant',text:'Resultado '+i});
                const id='flow-plan-'+i;
                Agent.snapshot.plans.push({state:'SUBMITTED',plan:{id,ref:{providerId:'google_flow',modelId:'google-flow/nano-pro'},
                  modelName:'Nano Banana Pro',modality:'IMAGE',prompt:'Escena '+i,params:{aspect:'16:9',count:'1'},effectiveParams:{aspect:'16:9',count:'1'}}});
                Agent.cardAnchors.set('batch:image:'+id,Agent.items.at(-1));
                state.jobs.push({id:'flow-job-'+i,agentPlanId:id,providerId:'google_flow',modelId:'google-flow/nano-pro',modality:'IMAGE',
                  name:'Nano Banana Pro',prompt:'Escena '+i,status:'COMPLETED',outputs:['/media/'+'a'.repeat(32)+'.png']});
              }
              navigate('assistant');
            }""")
            page.wait_for_selector('.agent-flow-result img')
            assert page.locator('.agent-flow-result img').count()==20
            page.evaluate("""()=>{state.jobs[0].outputs.push('/media/'+'b'.repeat(32)+'.png');refreshJobViews([state.jobs[0].id]);}""" )
            assert page.locator('.agent-flow-result img').count()==21
            page.evaluate("""()=>{state.jobs[0].outputs.pop();refreshJobViews([state.jobs[0].id]);}""" )
            assert page.locator('.agent-flow-result img').count()==20
            assert page.locator('.generation-batch-card,.agent-plan').count()==0
            assert page.locator('[data-agent-generate],[data-agent-batch-generate]').count()==0
            for width,height in [(1360,900),(480,850),(1280,600)]:
                page.set_viewport_size({'width':width,'height':height})
                page.wait_for_timeout(200)
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
                box=page.locator('.agent-messages').bounding_box();compose=page.locator('#agent-compose').bounding_box()
                assert box['height']>100
                assert box['y']+box['height']<=compose['y']+1
                assert compose['y']+compose['height']<=height+1
                assert page.locator('.agent-messages').evaluate('n=>n.scrollHeight>n.clientHeight')
                page.mouse.move(box['x']+40,box['y']+min(60,box['height']/2))
                page.mouse.wheel(0,80-page.locator('.agent-messages').evaluate('n=>n.scrollTop'))
                page.wait_for_timeout(200)
                page.evaluate('renderAssistant()')
                scroll=page.locator('.agent-messages').evaluate('n=>n.scrollTop')
                assert abs(scroll-80)<2,(width,scroll,page.locator('.agent-messages').evaluate('n=>[n.scrollHeight,n.clientHeight]'))
                page.screenshot(path=str(OUT/f'conversation-{width}.png'))
            # Twenty results pass through the real callback and dispatch with no approved card.
            page.evaluate("""async()=>{
              const real=agentModel;agentModel=async()=>({id:'google-flow/nano-pro',providerId:'google_flow',providerModelId:'nano-pro',modalities:['IMAGE'],displayName:'Flow',fields:[]});
              Agent.approved=null;await agentCallback('submit',{id:'direct-twenty',ref:{providerId:'google_flow',modelId:'google-flow/nano-pro'},prompt:'Cinematic test',params:{count:'20',aspect:'16:9'},media:[]});
              agentModel=real;
            }""")
            assert len(calls)==1 and calls[0]['count']==20 and calls[0]['account_direct'] is True
            assert calls[0]['aspect']=='16:9'
            assert not page.locator('#dialog').evaluate('d=>d.open')
            assert not errors,errors
            browser.close()
        report={'pass':True,'directCount':20,'inlineResults':20,'singleScroll':True,'scrollPositionPreserved':True,'javascriptErrors':errors,'paidCalls':0}
        (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
    finally:
        host.terminate();host.wait(timeout=15)
