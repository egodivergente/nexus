"""Real Kotlin discovery tools with a local scripted LLM; no paid calls or builds."""
import json
import shutil
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
received = []
queries = [('find-reference','search_assets',{'project':'Mateo','text':'Mateo face reference','type':'IMAGE'})]

class ScriptedProxy(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args):
        pass

    def do_POST(self):
        if self.path != '/v1/converse':
            self.send_error(404); return
        request = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        received.append(request)
        if len(received)==2:
            found=next(block['toolResult']['content'][0]['json']['assets'][0]['asset_id'] for m in request['messages'] for block in m['content'] if 'toolResult' in block)
            queries[:]=[('use-reference','prepare_generation',{'provider_id':'google_flow','model_id':'google-flow/nano-pro','prompt':'Mateo walking in Madrid','params':{'aspect':'16:9'},'media':[{'role':'reference','asset_id':found}],'execute':True})]
        content = [{'toolUse':{'toolUseId':identifier,'name':name,'input':args}} for identifier,name,args in queries] if len(received)<=2 else [{'text':'Referencia utilizada.'}]
        response = json.dumps({'output':{'message':{'role':'assistant','content':content}},
                               'stopReason':'tool_use' if len(received)<=2 else 'end_turn',
                               'usage':{'inputTokens':0,'outputTokens':0}}).encode()
        self.send_response(200)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(response)))
        self.send_header('Connection','close')
        self.end_headers(); self.wfile.write(response); self.wfile.flush()

proxy = ThreadingHTTPServer(('127.0.0.1',0), ScriptedProxy)
threading.Thread(target=proxy.serve_forever, daemon=True).start()
with tempfile.TemporaryDirectory(prefix='nexus-flow-direct-jvm-') as folder:
    data = Path(folder)
    (data/'media').mkdir();shutil.copyfile(root/'desktop/ui/logos/flux.png',data/'media'/('a'*32+'.png'))
    log = (data/'host.log').open('wb')
    process = subprocess.Popen([str(Path(sys.base_prefix)/'python.exe'),str(root/'desktop/local_host.py'),
        '--headless','--offline-qa','--data-dir',str(data)], stdout=log,stderr=log,
        creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    try:
        for _ in range(300):
            if process.poll() is not None: raise RuntimeError('Isolated host exited')
            try:
                session = json.loads((data/'local-session.json').read_text())
                if session['pid']==process.pid: break
            except (OSError,ValueError): pass
            time.sleep(.1)
        else: raise RuntimeError('Isolated host unavailable')
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge',headless=True)
            page = browser.new_page(bypass_csp=True)
            errors = []
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.route('https://**/*',lambda route:route.abort())
            submissions=[]
            def intercept(route):
                m=route.request.post_data_json
                if m['op']=='pick':
                    route.fulfill(json={'id':m['id'],'ok':True,'value':[{'id':'a'*32+'.png','url':'/media/'+'a'*32+'.png','name':'Mateo original.png','mime':'image/png','size':128}]})
                elif m['op']=='flowSubmit':
                    submissions.append(m['args'])
                    route.fulfill(json={'id':m['id'],'ok':True,'value':{'id':m['args']['request_id'],'status':'queued','outputs':[]}})
                elif m['op'] in ['generate','upload','request','flowQuote']:raise AssertionError('Unexpected provider operation '+m['op'])
                else:route.continue_()
            page.route('**/rpc',intercept)
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('Boolean(window.nexusQA)&&Boolean(window.NexusProjectReferences)')
            page.evaluate("""async endpoint=>{
                await agentCall('settings',{backend:'proxy',endpoint,token:'isolated-fixture',mode:'AGENT'});
                window.discoveryCallbacks=[];const previous=agentCallback;
                agentCallback=async(op,args)=>{discoveryCallbacks.push(op);return previous(op,args);};
            }""",f'http://127.0.0.1:{proxy.server_port}')
            page.evaluate("async()=>{await agentCall('createProject',{name:'Mateo'});await agentCall('project',{id:Agent.snapshot.projectId});await NexusProjectReferences.open();}")
            page.get_by_role('button',name='Importar archivos',exact=True).click()
            page.wait_for_selector('[data-reference-name]')
            page.locator('[data-reference-name]').fill('Mateo face reference')
            page.locator('[data-reference-name]').press('Tab')
            page.wait_for_function("Agent.snapshot.index.assets.some(a=>a.displayName==='Mateo face reference')")
            assert page.locator('.project-reference img').count()==1
            page.screenshot(path=str(root/'outputs/project-references-20261008.png'))
            page.evaluate("async()=>{await agentCall('new');await agentCall('send',{text:'Genera a Mateo caminando con su face reference.',attachments:[]});}")
            assert len(received)==3,len(received)
            assert len(submissions)==1 and submissions[0]['reference_images']==['a'*32+'.png'],submissions
            assert any('image' in block for message in received[0]['messages'] for block in message['content']), 'Reference was not loaded visually'
            saved=json.loads((data/'assistant/index.json').read_text(encoding='utf-8'))
            assert any(a['displayName']=='Mateo face reference' for a in saved['assets'])
            # A different active project must not load Mateo automatically.
            page.evaluate("async()=>{await agentCall('createProject',{name:'Other'});await agentCall('send',{text:'Describe a Mateo.',attachments:[]});}")
            assert not any('image' in block for block in received[-1]['messages'][-1]['content']), 'Cross-project automatic attachment'
            assert not errors,errors
            print(json.dumps({'status':'PASS','importUI':True,'logicalRename':True,'persistentReference':True,'nativeAssetLookup':True,'automaticVisualReference':True,'usedInFlowRequest':True,'projectIsolation':True,'paidCalls':0}))
            browser.close()
    finally:
        process.terminate()
        process.wait(timeout=10)
        log.close()
        proxy.shutdown(); proxy.server_close()
