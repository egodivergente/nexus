"""Actual Edge controls: stop a turn, continue, export and reimport a montage."""
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from PIL import Image
from playwright.sync_api import sync_playwright

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import local_host as local


class ControlsAcceptance(unittest.TestCase):
    def test_controls(self):
        received=threading.Event();release=threading.Event();calls=[]
        class Provider(BaseHTTPRequestHandler):
            def log_message(self,*_):pass
            def do_POST(self):
                self.rfile.read(int(self.headers['Content-Length']));calls.append(self.path)
                if len(calls)==1:received.set();release.wait(20)
                raw=json.dumps({'choices':[{'message':{'role':'assistant','content':'Seguimos.'},'finish_reason':'stop'}],'usage':{'prompt_tokens':10,'completion_tokens':3}}).encode()
                try:self.send_response(200);self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
                except ConnectionError:pass
        provider=ThreadingHTTPServer(('127.0.0.1',0),Provider);threading.Thread(target=provider.serve_forever,daemon=True).start()
        original={k:getattr(local,k) for k in ['DATA','MEDIA','KEY_FILE','OFFLINE_QA','_agent']}
        with tempfile.TemporaryDirectory(prefix='nexus-controls-ui-') as tmp:
            data=Path(tmp);local.DATA=data;local.MEDIA=data/'media';local.MEDIA.mkdir();local.KEY_FILE=data/'credentials.dpapi';local.OFFLINE_QA=True;local._agent=None
            local.write_state({'jobs':[],'draft':{'prompt':'','values':{},'media':[]}})
            image=local.MEDIA/('d'*32+'.png');Image.new('RGB',(64,64),'#57bdb4').save(image)
            server=local.LoopbackHTTPServer(('127.0.0.1',0),local.Handler);server.token='qa-project-controls';server.keys={};threading.Thread(target=server.serve_forever,daemon=True).start()
            try:
                with sync_playwright() as p:
                    browser=p.chromium.launch(channel='msedge',headless=True);page=browser.new_page(viewport={'width':1600,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
                    page.goto(f'http://127.0.0.1:{server.server_port}/');page.wait_for_function('()=>Boolean(initial)&&typeof CustomMedia!=="undefined"&&CustomMedia.loaded')
                    page.evaluate("async endpoint=>{await ensureAssistantReady();await agentCall('settings',{backend:'profile',brainKey:'synthetic-key',brainProfile:{id:'local-brain',name:'Local',baseUrl:endpoint,modelId:'mock',capabilities:{tools:false,streaming:false}}});navigate('assistant');Agent.draft='Detén esta espera';window.pendingSend=sendAgent();}",f'http://127.0.0.1:{provider.server_port}')
                    self.assertTrue(received.wait(10));page.locator('[data-stop-turn]').click()
                    page.wait_for_function('()=>!Agent.busy&&Agent.error.includes("Turno detenido")',timeout=10000)
                    self.assertEqual(page.evaluate('Agent.draft'),'');self.assertTrue(page.evaluate('Agent.items.some(i=>i.cancelled&&!i.failed)'))
                    page.evaluate("async()=>{Agent.draft='Continúa';await sendAgent();}")
                    self.assertTrue(page.evaluate('Agent.items.some(i=>i.text==="Seguimos.")'))
                    page.evaluate("async()=>{await agentCall('createProject',{name:'Portable UI'});await agentCall('importReferences',{projectId:Agent.snapshot.projectId,files:[{uri:'/media/"+image.name+"',mime:'image/png',name:'Imagen'}]});navigate('editor');}")
                    page.wait_for_function('()=>Boolean(Corte.snapshot)&&!Agent.busy');page.locator('[data-corte-place]').first.click();page.wait_for_selector('.corte-clip')
                    source_id=page.evaluate('Corte.projectId')
                    page.evaluate("()=>navigate('settings')")
                    with page.expect_download():page.locator('[data-transport=export]').click()
                    archive=next(local.MEDIA.glob('*.zip'))
                    page.evaluate("file=>{const original=rpc;rpc=(op,args={})=>op==='pick'?Promise.resolve([file]):original(op,args);}",{'id':archive.name,'name':'Portable.nexus.zip','url':'/media/'+archive.name,'mime':'application/zip'})
                    page.locator('[data-transport=import]').click()
                    page.wait_for_function('source=>view==="editor"&&Corte.projectId!==source&&Boolean(Corte.snapshot)&&!Agent.busy',arg=source_id,timeout=45000)
                    self.assertEqual(page.locator('.corte-clip').count(),1)
                    self.assertEqual(len(local.agent_host().corte.run({'method':'read_project','project_id':source_id})['project']['timeline']['videoTracks'][0]['clips']),1)
                    self.assertFalse(errors,errors)
                    out=Path(__file__).resolve().parents[2]/'outputs/windows-corte';out.mkdir(parents=True,exist_ok=True);page.screenshot(path=str(out/'portable-imported.png'),full_page=True)
                    browser.close()
            finally:
                release.set();server.shutdown();server.server_close();provider.shutdown();provider.server_close()
                if local._agent:local._agent.close();local._agent.corte.close();local._agent.native.executor.shutdown()
                for k,v in original.items():setattr(local,k,v)


if __name__=='__main__':unittest.main(verbosity=2)
