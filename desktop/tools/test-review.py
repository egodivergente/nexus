"""Isolated UI, project memory and real vision test; no media generation."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
data=root.parent/'outputs/review-agent-20261006'
(data/'assistant').mkdir(parents=True,exist_ok=True)
original=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop/assistant'
for name in ('credentials.dpapi','settings.json'):
    shutil.copy2(original/name,data/'assistant'/name)
env=dict(os.environ)
env['PATH']=r'C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\bin;'+env['PATH']
process=subprocess.Popen([sys.executable,str(root/'local_host.py'),'--headless','--data-dir',str(data)],
    env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
try:
    for _ in range(100):
        path=data/'local-session.json'
        if path.exists():
            session=json.loads(path.read_text())
            if session['pid']==process.pid:break
        time.sleep(.2)
    else:raise RuntimeError('Host unavailable')
    with sync_playwright() as p:
        browser=p.chromium.launch(channel='msedge',headless=True)
        page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
        errors=[]
        page.on('pageerror',lambda e:errors.append(str(e)))
        page.goto(f"http://127.0.0.1:{session['port']}/")
        page.wait_for_function("typeof reviewImages==='function'")
        page.locator('[data-nav=assistant]').click()
        page.wait_for_function('Agent.snapshot && !Agent.busy',timeout=45000)
        checks=page.evaluate("""async()=>{
          await agentCall('createProject',{name:'Review QA'});
          const id=Agent.snapshot.projectId;
          await agentCall('memory',{id,description:'Preserve description',synopsis:'Preserve synopsis',tone:'Sobrio',visualStyle:'Fotografico',rules:'Regla original',preferredModels:'flux'});
          await saveReviewPreference(id,'Evitar fondos amarillos.');
          await saveReviewPreference(id,'Evitar fondos amarillos.');
          await agentCall('new');
          const m=Agent.snapshot.index.projects.find(p=>p.id===id);
          let blocked=false;
          try{await reviewImages();}catch{blocked=true;}
          return {blocked,rules:m.memory.rules,description:m.description,synopsis:m.memory.synopsis};
        }""")
        assert checks['blocked'] and checks['rules']==['Regla original','Evitar fondos amarillos.']
        assert checks['description']=='Preserve description' and checks['synopsis']=='Preserve synopsis'
        # Synthetic visual fixture: actual pixels, no private image or media provider.
        page.evaluate("""async()=>{
          const c=document.createElement('canvas');c.width=640;c.height=400;
          const x=c.getContext('2d');x.fillStyle='white';x.fillRect(0,0,640,400);
          x.fillStyle='red';x.fillRect(50,100,150,150);x.fillStyle='green';x.beginPath();x.arc(450,175,75,0,Math.PI*2);x.fill();
          const f=await rpc('importMedia',{name:'formas.png',mime:'image/png',content:c.toDataURL('image/png').split(',')[1]});
          Agent.attachments=[f];Agent.draft='Identifica las formas y colores visibles. Recuerda tambien mi preferencia sobre fondos.';
          renderAssistant();
        }""")
        page.locator('[data-review=image]').click()
        page.wait_for_function('!Review.busy && !Agent.busy && Agent.items.some(i=>i.role==="assistant")',timeout=120000)
        result=page.evaluate('Agent.items.filter(i=>i.role==="assistant").at(-1).text')
        assert 'menos de 180 palabras' not in page.locator('.agent-message.user').last.inner_text()
        print(json.dumps({'checks':checks,'vision':result,'errors':errors},ensure_ascii=True))
        assert 'rojo' in result.lower() and 'verde' in result.lower() and 'amarill' in result.lower()
        page.locator('[data-remember]').last.click()
        page.locator('#review-memory textarea').fill('Conservar la geometria original.')
        page.locator('#review-memory button[type=submit]').click()
        page.wait_for_function('!document.querySelector("#dialog").open')
        assert page.evaluate('Agent.snapshot.index.projects.find(p=>p.id===Agent.snapshot.projectId).memory.rules.includes("Conservar la geometria original.")')
        for width in (1360,760):
            page.set_viewport_size({'width':width,'height':900})
            page.screenshot(path=str(data/f'review-{width}.png'))
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        assert not errors
        # The real CSP remains enabled in a second window; no eval-based waits.
        csp=browser.new_page()
        csp.on('pageerror',lambda e:errors.append(str(e)))
        csp.goto(f"http://127.0.0.1:{session['port']}/")
        csp.locator('[data-nav=assistant]').click()
        csp.locator('[data-review=image]').wait_for()
        assert not errors
        browser.close()
finally:
    subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
