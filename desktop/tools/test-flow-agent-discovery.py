"""Real Kotlin discovery tools with a local scripted LLM; no paid calls or builds."""
import json
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
queries = [
    ('image-list', 'list_models', {'provider_id':'google_flow','modality':'IMAGE','capability':'TEXT_TO_IMAGE'}),
    ('image-brief', 'get_model_brief', {'provider_id':'google_flow','modality':'IMAGE','capability':'TEXT_TO_IMAGE','focus':'schema'}),
    ('video-list', 'list_models', {'provider_id':'google_flow','modality':'VIDEO','capability':'TEXT_TO_VIDEO'}),
    ('upscale-brief', 'get_model_brief', {'provider_id':'google_flow','modality':'IMAGE','capability':'UPSCALE','focus':'schema'}),
]

class ScriptedProxy(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args):
        pass

    def do_POST(self):
        if self.path != '/v1/converse':
            self.send_error(404); return
        request = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        received.append(request)
        content = [{'toolUse':{'toolUseId':identifier,'name':name,'input':args}} for identifier,name,args in queries] if len(received)==1 else [{'text':'Catálogo comprobado.'}]
        response = json.dumps({'output':{'message':{'role':'assistant','content':content}},
                               'stopReason':'tool_use' if len(received)==1 else 'end_turn',
                               'usage':{'inputTokens':0,'outputTokens':0}}).encode()
        self.send_response(200)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(response)))
        self.send_header('Connection','close')
        self.end_headers(); self.wfile.write(response); self.wfile.flush()

proxy = ThreadingHTTPServer(('127.0.0.1',0), ScriptedProxy)
threading.Thread(target=proxy.serve_forever, daemon=True).start()
with tempfile.TemporaryDirectory(prefix='nexus-flow-discovery-') as folder:
    data = Path(folder)
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
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('Boolean(window.nexusQA)')
            page.evaluate("""async endpoint=>{
                await agentCall('settings',{backend:'proxy',endpoint,token:'isolated-fixture',mode:'AGENT'});
                window.discoveryCallbacks=[];const previous=agentCallback;
                agentCallback=async(op,args)=>{discoveryCallbacks.push(op);if(!['models','schema'].includes(op))throw Error('Read-only discovery fixture');return previous(op,args);};
            }""",f'http://127.0.0.1:{proxy.server_port}')
            try:
                result = page.evaluate("""async()=>{const response=await agentCall('send',{text:'Comprueba los modelos de Flow disponibles.',attachments:[]});return {callbacks:discoveryCallbacks,jobs:state.jobs.length,rounds:response.toolRounds};}""")
            except Exception as exc:
                callbacks = page.evaluate('discoveryCallbacks')
                raise RuntimeError(f'Native discovery failed; local proxy requests={len(received)}, callbacks={callbacks}') from exc
            assert len(received)==2, len(received)
            outcomes = {}
            for message in received[-1]['messages']:
                for block in message['content']:
                    if 'toolResult' in block:
                        item = block['toolResult']
                        assert item.get('status','success')=='success', item
                        outcomes[item['toolUseId']] = item['content'][0]['json']
            for key, model in [('image-list','google-flow/nano2'),('image-brief','google-flow/nano2'),
                               ('video-list','google-flow/veo-fast'),('upscale-brief','google-flow/image_upscale')]:
                assert model in [item['model_id'] for item in outcomes[key]['items']], outcomes[key]
            assert all(item['schema_status']=='available' for item in outcomes['image-brief']['items'])
            assert result['jobs']==0 and set(result['callbacks'])=={'models','schema'}, result
            assert not errors, errors
            print(json.dumps({'status':'PASS','realKotlinTools':True,'scriptedLocalLlm':True,
                              'imageGenerationDiscovered':True,'videoGenerationDiscovered':True,
                              'upscalePreserved':True,'externalInferenceCalls':0,'generations':0}))
            browser.close()
    finally:
        process.terminate()
        process.wait(timeout=10)
        log.close()
        proxy.shutdown(); proxy.server_close()
