"""Actual UI + Kotlin rewrite protocol; scripted local LLM and simulated submissions only."""
import json
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'outputs/order-resilience-20261008'
OUT.mkdir(parents=True, exist_ok=True)
llm_requests, errors, forbidden = [], [], []
rewrite_gate = threading.Event()
rewrite_gate.set()
rewrite = 'Mateo walking in Madrid, wide cinema frame, natural light. Keep his reference identity. No visible text.'
original_prompt = 'Mateo walking in Madrid. Keep his reference identity. No visible text. ' + ('Wide cinema frame with natural light and realistic skin. ' * 30)
fixture_schema = {'components':{'schemas':{'Input':{'required':['prompt'],'properties':{'prompt':{'type':'string','maxLength':120},'duration':{'type':'integer','minimum':1,'maximum':10,'default':3}}}}}}

class Proxy(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args): pass
    def do_POST(self):
        assert self.path == '/v1/converse'
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        llm_requests.append(body)
        tools = body.get('toolConfig')
        if not tools: rewrite_gate.wait(timeout=10)
        has_results = any('toolResult' in block for message in body['messages'] for block in message['content'])
        content = ([{'text':'Prepared.'}] if has_results else [{'toolUse':{'toolUseId':'native-plan','name':'prepare_generation','input':{'provider_id':'atlas_cloud','model_id':'short','prompt':original_prompt,'params':{'duration':'3'}}}}]) if tools else [{'text':rewrite}]
        response = json.dumps({'output': {'message': {'role': 'assistant', 'content': content}},
                              'stopReason': 'tool_use' if tools and not has_results else 'end_turn', 'usage': {'inputTokens': 0, 'outputTokens': 0}}).encode()
        self.send_response(200); self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response))); self.send_header('Connection','close')
        self.end_headers(); self.wfile.write(response); self.wfile.flush()

proxy = ThreadingHTTPServer(('127.0.0.1', 0), Proxy)
threading.Thread(target=proxy.serve_forever, daemon=True).start()
with tempfile.TemporaryDirectory(prefix='nexus-order-resilience-') as folder:
    data = Path(folder)
    runtime = Path(sys.argv[1]).resolve() if len(sys.argv)>1 else ROOT/'desktop/agent/runtime'
    launcher = data/'qa-host.py'
    launcher.write_text(f'''import runpy, sys
sys.path.insert(0,{str(ROOT/'desktop')!r})
import agent_host
real_popen=agent_host.subprocess.Popen
def fixture_popen(command,*args,**kwargs):
    if isinstance(command,list) and 'DesktopAgentKt' in command:
        command=list(command);command[command.index('-cp')+1]={str(runtime/'*')!r}
    return real_popen(command,*args,**kwargs)
agent_host.subprocess.Popen=fixture_popen
sys.argv=[{str(ROOT/'desktop/local_host.py')!r}]+sys.argv[1:]
runpy.run_path(sys.argv[0],run_name='__main__')
''',encoding='utf-8')
    host = subprocess.Popen([sys.executable, str(launcher), '--headless', '--offline-qa', '--data-dir', str(data)],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            try: session = json.loads((data/'local-session.json').read_text()); break
            except (OSError, ValueError): time.sleep(.1)
        else: raise RuntimeError('QA host unavailable')
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge', headless=True)
            page = browser.new_page(viewport={'width': 1600, 'height': 950}, bypass_csp=True)
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.route('https://**/*', lambda route: route.abort())
            def guard(route):
                message = route.request.post_data_json
                if message['op'] in {'generate', 'upload', 'request', 'flowSubmit', 'flowQuote'}:
                    forbidden.append(message['op']); route.abort()
                elif message['op']=='schema' and message['args']['url']=='https://example.invalid/schema':
                    route.fulfill(json={'id':message['id'],'ok':True,'value':fixture_schema})
                else: route.continue_()
            page.route('**/rpc', guard)
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('Boolean(window.NexusOrderEditor)&&Boolean(window.NexusProjectReferences)')
            page.evaluate("endpoint=>agentCall('settings',{backend:'proxy',endpoint,token:'offline-test',mode:'AGENT'})", f'http://127.0.0.1:{proxy.server_port}')
            page.evaluate("""async()=>{
              const schema=(id,max,required=false)=>E.normalize({id,providerId:'atlas_cloud',displayName:id,modalities:['IMAGE'],schemaUrl:'https://example.invalid/schema'},
               {components:{schemas:{Input:{required:['prompt',...(required?['images']:[])],properties:{
                prompt:{type:'string',...(max?{maxLength:max}:{})},duration:{type:'integer',minimum:1,maximum:10,default:3},
                aspect_ratio:{type:'string',enum:['1:1','16:9'],default:'1:1'},images:{type:'array',maxItems:2}}}}}});
              window.qaModels=[schema('short',120),schema('fail'),schema('needs-reference',null,true),schema('no-price')];
              models.push(...qaModels);configuration=async id=>qaModels.find(m=>m.id===id);
              selection=new Set(qaModels.map(m=>m.id));
              window.originalPrompt='Mateo walking in Madrid. Keep his reference identity. No visible text. '+('Wide cinema frame with natural light and realistic skin. '.repeat(30));
              state.draft={modelId:'source',prompt:originalPrompt,values:{duration:'99',aspect:'16:9'},media:[]};
              window.quoteCalls=[];quotePlan=async p=>{quoteCalls.push(p.model.id);if(p.model.id==='no-price')throw Error('Provider estimate unavailable');return {amount:.1,currency:'USD',time:Date.now()};};
              window.attempts=[];window.qaTick=0;window.realNow=Date.now;Date.now=()=>realNow()+qaTick;
              dispatch=async(p,batchId,meta)=>{
               attempts.push({id:p.model.id,prompt:p.prompt,original:p.originalPrompt,values:p.values,cost:p.cost,meta,batchId});
               if(p.model.id==='short')qaTick=301000;
               if(p.model.id==='fail')throw Error('Simulated provider rejection');
               await new Promise(r=>setTimeout(r,5));return {id:'job-'+attempts.length};
              };
              await prepareComparison();
            }""")
            assert len(llm_requests) == 1
            assert llm_requests[0].get('toolConfig') is None
            assert 'Mateo walking in Madrid' in llm_requests[0]['messages'][0]['content'][0]['text']
            assert page.evaluate('Agent.items.length') == 0, 'Rewrite polluted chat'
            assert page.evaluate('state.draft.prompt===originalPrompt')
            assert page.evaluate("comparePlans[0].prompt") == rewrite
            assert page.evaluate('comparePlans[0].originalPrompt===originalPrompt')
            assert page.evaluate('comparePlans.every(p=>p.values.duration===\'3\')'), 'Incompatible source value leaked'
            assert page.evaluate("comparePlans.every(p=>p.values.aspect_ratio==='16:9')")
            assert page.evaluate('comparePlans[1].prompt===originalPrompt'), 'Unknown text limit invented'
            assert page.evaluate("comparePlans[3].cost.currency==='unknown'&&comparePlans[3].cost.amount===null")
            assert not page.locator('#order-send').is_disabled(), 'One invalid model blocked all valid models'
            assert '1 modelo(s) pendientes' in page.locator('#order-summary').inner_text()
            page.locator('[data-order-index="0"] .prompt-original summary').click()
            original = page.locator('[data-order-index="0"] .prompt-original').inner_text()
            assert original
            assert 'Keep his reference identity' in original
            # Changes refresh quotes automatically, without an extra click.
            page.locator('[data-order-index="0"] [data-order-param=duration]').fill('7')
            page.wait_for_function("comparePlans[0].cost&&comparePlans[0].values.duration==='7'&&!document.querySelector('#order-send').disabled")
            page.locator('[data-order-index="1"] [data-order-quantity]').fill('20')
            page.screenshot(path=str(OUT/'partial-order.png'))
            page.locator('#order-send').click()
            page.wait_for_function('!Batch.active&&state.lastOrderRun.requests.length===23')
            assert page.evaluate('attempts.length') == 22
            assert page.evaluate("state.lastOrderRun.requests.filter(t=>t.status==='SUBMITTED').length") == 2
            assert page.evaluate("state.lastOrderRun.requests.filter(t=>t.status==='FAILED').length") == 20
            assert page.evaluate("state.lastOrderRun.requests.filter(t=>t.status==='NOT_SENT').length") == 1
            assert page.evaluate("attempts.some(t=>t.id==='no-price'&&t.cost.amount===null)")
            assert page.evaluate("attempts.filter(t=>t.id==='short')[0].original===originalPrompt")
            assert page.evaluate("attempts.every(t=>t.cost.time>0)")
            assert page.evaluate("quoteCalls.filter(id=>id==='fail').length>=2"), 'Stale batch quote not refreshed'
            assert page.evaluate('new Set(attempts.map(t=>t.meta.orderRequestId)).size===attempts.length'), 'Duplicate request'
            assert page.evaluate('state.draft.prompt===originalPrompt')
            page.evaluate('Date.now=realNow')
            # Reopening keeps both rewritten and original versions.
            page.evaluate('prepareComparison()')
            page.wait_for_function("!document.querySelector('#order-send').disabled")
            assert page.evaluate('comparePlans[0].originalPrompt===originalPrompt')
            assert len(llm_requests) == 1, 'Identical draft summarized again'
            assert page.locator('.prompt-original').count() == 1
            # Editing while a rewrite is in flight must preserve the newer user text.
            rewrite_gate.clear()
            page.locator('[data-order-index="0"] [data-order-prompt]').fill(original_prompt+' A new draft.')
            page.wait_for_function('Agent.busy&&comparePlans[0].preparing')
            page.locator('[data-order-index="0"] [data-order-prompt]').fill('Newer short user prompt')
            rewrite_gate.set()
            page.wait_for_function("comparePlans[0].cost&&comparePlans[0].prompt==='Newer short user prompt'")
            assert page.locator('[data-order-index="0"] [data-order-prompt]').input_value()=='Newer short user prompt'
            assert page.evaluate('!comparePlans[0].originalPrompt')
            # The chat's prepare_generation route also fits text before registering a plan.
            result = page.evaluate("()=>agentCall('send',{text:'Native plan test: prepara una imagen de Mateo.',attachments:[]})")
            (OUT/'native-debug.json').write_text(json.dumps({'result':result,'requests':llm_requests},indent=2),encoding='utf-8')
            assert result['snapshot']['plans'], {k:v for k,v in result.items() if k not in {'snapshot','html'}}
            native = result['snapshot']['plans'][0]['plan']
            assert native['prompt']==rewrite and native['originalPrompt']==original_prompt.strip(), native
            assert result['snapshot']['plans'][0]['state']=='PREPARED'
            assert len(llm_requests)==5, len(llm_requests)
            # The inline action crosses the real Kotlin ticket/callback protocol once,
            # including an unavailable price; dispatch remains a local simulation.
            page.evaluate("""()=>{
              document.querySelector('#dialog').close();
              Agent.items=[{role:'user',text:'Una imagen de Mateo'},{role:'assistant',text:'Encargo preparado'}];
              quotePlan=async()=>({amount:null,currency:'unknown',status:'unavailable',time:Date.now()});
              dispatch=async(p,batchId,meta)=>{
               attempts.push({id:p.model.id,prompt:p.prompt,original:p.originalPrompt,cost:p.cost,meta});
               const job={id:'native-chat-result',name:p.model.displayName,modelId:p.model.id,providerId:p.model.providerId,
                modality:'IMAGE',status:'PROCESSING',outputs:[],prompt:p.prompt,originalPrompt:p.originalPrompt,...meta};
               state.jobs.push(job);return job;
              };
              navigate('assistant');
            }""")
            page.locator('[data-agent=generate-ready]').click()
            page.wait_for_function("!Agent.generating&&!Agent.busy&&Agent.snapshot.plans[0].state==='SUBMITTED'")
            assert page.evaluate('attempts.length')==23
            assert page.evaluate("attempts.at(-1).original===originalPrompt.trim() && attempts.at(-1).cost.currency==='unknown'"),page.evaluate('attempts.at(-1)')
            assert page.evaluate('!document.querySelector("#dialog").open')
            assert page.locator('[data-agent=generate-ready]').count()==0
            assert not errors, errors
            assert not forbidden, forbidden
            report = {'status':'PASS','nativeSemanticRewrite':True,'chatPlanRewrite':True,'originalPreserved':True,'unknownLimitsUntouched':True,
                      'parameterAdaptation':True,'partialReadiness':True,'unknownPriceDoesNotBlock':True,
                      'automaticQuotes':True,'staleQuotesRefreshed':True,'rewriteEditRace':True,'failureIsolation':True,'max20PerModel':True,
                      'nativeSingleClickGeneration':True,'paidCalls':0,'simulatedSubmissions':23}
            (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
            print(json.dumps(report)); browser.close()
    finally:
        host.terminate(); host.wait(timeout=10); proxy.shutdown(); proxy.server_close()
