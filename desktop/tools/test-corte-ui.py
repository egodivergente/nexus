"""Browser/host/JVM integration with a private test profile and synthetic media."""
from pathlib import Path
import json
import hashlib
import os
import subprocess
import sys
import time
import uuid
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
out=root/'outputs/windows-corte/ui';out.mkdir(parents=True,exist_ok=True)
data=out/('profile-'+uuid.uuid4().hex[:8]);(data/'assistant').mkdir(parents=True)
(data/'media').mkdir()
(data/'state.json').write_text(json.dumps({'jobs':[],'draft':{'prompt':'','values':{},'media':[]}}),encoding='utf-8')
sys.path.insert(0,str(root/'desktop'))
from native_operations import NativeOperations
native=NativeOperations(data,None,lambda:{},lambda _:None,None,lambda:{})
video=data/'media'/('b'*32+'.mp4')
native.encode(['-f','lavfi','-i','testsrc2=s=320x240:r=25:d=4','-f','lavfi','-i','sine=frequency=440:duration=4','-c:v','libx264','-c:a','aac',video])
native.executor.shutdown()
host=subprocess.Popen([sys.executable,str(root/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf-8',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
try:
 url=json.loads(host.stdout.readline())['url']
 with sync_playwright() as p:
  browser=p.chromium.launch(channel='msedge',headless=True)
  page=browser.new_page(viewport={'width':1600,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(url)
  page.wait_for_function('()=>typeof Corte!=="undefined"&&Boolean(initial)&&Boolean(window.NexusAgentParity)',timeout=45000)
  page.evaluate("async()=>{await ensureAssistantReady();await agentCall('createProject',{name:'Montaje Windows · prueba local'});navigate('editor');}")
  page.wait_for_function('()=>Boolean(Corte.snapshot)&&!Agent.busy',timeout=45000)
  page.evaluate("async()=>{await agentCall('importReferences',{projectId:Corte.projectId,files:[{uri:'/media/"+video.name+"',mime:'video/mp4',name:'Plano de prueba'}]});renderCorteAssets();}")
  page.locator('[data-corte-place]').first.click()
  page.wait_for_selector('.corte-clip')
  page.locator('.corte-clip').first.click()
  page.wait_for_function('()=>Corte.selected.size>0&&!Corte.busy')
  page.locator('[data-corte=duplicate]').click()
  page.wait_for_function('()=>corteClips().filter(c=>c.kind==="VIDEO").length===2&&!Corte.busy')
  page.locator('[data-corte=preview]').click()
  page.wait_for_function('()=>Corte.jobs.some(j=>j.kind==="preview"&&["COMPLETED","FAILED"].includes(j.state))',timeout=180000)
  jobs=page.evaluate('Corte.jobs');assert jobs[0]['state']=='COMPLETED',jobs
  page.wait_for_function('()=>document.querySelector("#corte-video").readyState>=2',timeout=30000)
  page.evaluate("()=>{document.querySelector('#corte-video').currentTime=1;}")
  page.wait_for_timeout(800)
  page.screenshot(path=str(out/'editor.png'),full_page=True)
  page.locator('[data-corte=chat]').click();assert not page.locator('#corte-chat').is_visible()
  page.locator('[data-corte=chat]').click();assert page.locator('#corte-chat').is_visible()
  page.evaluate('agentSettings()');page.wait_for_selector('#brain-settings-form')
  page.select_option('#brain-template','byteplus')
  assert page.locator('[name=backend]').input_value()=='profile'
  page.screenshot(path=str(out/'brain-settings.png'),full_page=True)
  page.locator('[data-brain=close]').click()
  page.evaluate("navigate('library')");page.wait_for_timeout(500)
  page.evaluate("navigate('editor')");page.wait_for_selector('.corte-clip')
  assert not errors,errors
  (out/'result.json').write_text(json.dumps({'result':'PASS','installedRoot':str(root),'hostSession':json.loads((data/'local-session.json').read_text('utf-8')),'runtimeHashes':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'desktop/agent/runtime').glob('*.jar')},'browserErrors':errors,'checks':['same-window-editor-chat','shared-project','library-placement','manual-engine-edit','preview-output-playback','collapsible-chat','brain-profile-form','navigation-preserves-montage'],'screenshots':['editor.png','brain-settings.png']},indent=2),encoding='utf-8')
  print((out/'result.json').read_text('utf-8'));browser.close()
finally:
 host.terminate()
 try:host.wait(timeout=10)
 except subprocess.TimeoutExpired:host.kill()
