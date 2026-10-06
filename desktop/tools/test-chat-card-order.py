"""Real DOM regression for pinned plan/proposal cards; isolated data, no LLM."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as folder:
    data=Path(folder)
    process=subprocess.Popen([sys.executable,str(root/'local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],
        cwd=root,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(150):
            if (data/'local-session.json').exists():break
            time.sleep(.1)
        session=json.loads((data/'local-session.json').read_text())
        with sync_playwright() as pw:
            browser=pw.chromium.launch(channel='msedge',headless=True)
            page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
            errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
            page.route('https://**/*',lambda route:route.abort())
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function("Boolean(window.nexusQA)&&!Agent.busy")
            page.evaluate("""()=>{
              Agent.busy=false;Agent.cardAnchors.clear();Agent.items=[{role:'user',text:'Primera petición'}];
              Agent.snapshot={offline:false,mode:'ASSISTANT',skills:[],index:{projects:[]},
                plans:[{state:'PREPARED',plan:{id:'old-plan',modelName:'Plan anterior',prompt:'Comentario anterior',effectiveParams:{count:28},warnings:[]}}],
                proposals:[{id:'old-proposal',description:'Propuesta anterior'}]};
              view='assistant';render();
              Agent.items.push({role:'assistant',text:'Primera respuesta'},{role:'user',text:'Segunda petición'},{role:'assistant',text:'Segunda respuesta'});
              renderAssistant();
            }""")
            order=page.locator('.agent-messages > article').all_text_contents()
            assert 'Primera petición' in order[0]
            assert 'Comentario anterior' in order[1]
            assert 'Propuesta anterior' in order[2]
            assert 'Primera respuesta' in order[3]
            assert 'Segunda respuesta' in order[-1],order
            page.evaluate("Agent.snapshot.plans[0].state='SUBMITTED';renderAssistant()")
            assert page.locator('.agent-messages > article').all_text_contents()[-1].endswith('Segunda respuesta')
            page.evaluate("Agent.snapshot.plans[0].state='DISCARDED';Agent.snapshot.proposals=[];renderAssistant()")
            assert page.locator('.agent-plan').count()==0
            assert page.locator('.agent-message').count()==4
            assert not errors,errors
            browser.close()
            print('PASS: plan/proposal stay before later messages; state updates do not move them; discarded cards disappear. No inference.')
    finally:
        process.terminate();process.wait(timeout=10)
