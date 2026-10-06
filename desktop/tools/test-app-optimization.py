"""Isolated UI regression checks; network and paid calls are not used."""
import json
from pathlib import Path
import subprocess
import sys
import time
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
data=root.parent/'outputs/app-optimization-20261006/qa'
data.mkdir(parents=True,exist_ok=True)
process=subprocess.Popen([sys.executable,str(root/'local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],
    stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
try:
    for _ in range(100):
        f=data/'local-session.json'
        if f.exists():
            s=json.loads(f.read_text())
            if s['pid']==process.pid:break
        time.sleep(.1)
    else:raise RuntimeError('Host failed')
    with sync_playwright() as p:
        b=p.chromium.launch(channel='msedge',headless=True)
        page=b.new_page(bypass_csp=True)
        errors=[]
        page.on('pageerror',lambda e:errors.append(str(e)))
        page.route('https://**/*',lambda route:route.abort())
        page.goto(f"http://127.0.0.1:{s['port']}/")
        page.wait_for_function("typeof reviewImages==='function'")
        result=page.evaluate("""async()=>{
          Agent.snapshot={offline:false,index:{projects:[]},plans:[],proposals:[],skills:[]};
          Agent.draft='Conservar mi borrador';Agent.attachments=[{id:'test',url:'/media/test',mime:'image/png'}];
          const original=agentCall;agentCall=async()=>{throw Error('Simulated timeout');};
          try{await sendAgent();await sendAgent();}
          finally{agentCall=original;}
          return {draft:Agent.draft,attachments:Agent.attachments.length,messages:Agent.items.length};
        }""")
        assert result=={'draft':'Conservar mi borrador','attachments':1,'messages':0}
        page.evaluate("""()=>{
          view='canvas';loaded=E.normalize({id:'qa',providerId:'atlas_cloud',displayName:'QA',modalities:['IMAGE'],parameters:[]},{components:{schemas:{Input:{properties:{prompt:{type:'string',maxLength:3}}}}}});
          state.draft={modelId:'qa',prompt:'abcd',values:{},media:[]};render();
        }""")
        assert '4 / 3' in page.locator('#prompt-limits').inner_text()
        assert page.locator('#generate').is_disabled()
        for width in (1360,760):
            page.set_viewport_size({'width':width,'height':900})
            page.screenshot(path=str(data/f'canvas-{width}.png'))
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        assert not errors,errors
        print('PASS: retry preserves draft/attachments, no duplicate bubbles, length guard, 1360/760 layout')
        b.close()
finally:
    subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
