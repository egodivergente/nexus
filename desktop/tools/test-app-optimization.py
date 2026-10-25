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
        page.wait_for_function("typeof reviewImages==='function'&&typeof refreshBalances==='function'&&window.nexusQA")
        balances=page.evaluate("""async()=>{
          const original=rpc,configured=initial.configured;let calls=0;
          initial.configured=['atlas_cloud','runway','crun','google_flow'];
          rpc=async(op,args)=>{calls++;if(args.provider==='atlas_cloud')return {data:{available:{value:'25.019026',currency:'usd'}}};if(args.provider==='runway')return {creditBalance:0};throw Error('QA unavailable');};
          try{
            await Promise.all([refreshBalances(),refreshBalances()]);render();render();
            return {calls,bars:document.querySelectorAll('#account-balances').length,text:document.querySelector('#account-balances').textContent};
          }finally{rpc=original;initial.configured=configured;renderBalances();}
        }""")
        assert balances['calls']==3 and balances['bars']==1,balances
        assert '25,02 US$' in balances['text'] and '0 créditos' in balances['text'],balances
        assert balances['text'].count('No disponible')==2,balances
        page.evaluate("initial.configured=['atlas_cloud','runway','crun','google_flow'];renderBalances()")
        for width in (1360,760,480):
            page.set_viewport_size({'width':width,'height':900})
            assert page.locator('#account-balances').is_visible()
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            page.screenshot(path=str(data/f'balances-{width}.png'))
        page.evaluate('initial.configured=[];renderBalances()')
        result=page.evaluate("""async()=>{
          Agent.snapshot={offline:false,index:{projects:[]},plans:[],proposals:[],skills:[]};
          Agent.draft='Conservar mi borrador';Agent.attachments=[{id:'test',url:'/media/test',mime:'image/png'}];
          const original=agentCall;agentCall=async()=>{throw Error('Simulated timeout');};
          try{await sendAgent();await sendAgent();}
          finally{agentCall=original;}
          return {draft:Agent.draft,attachments:Agent.attachments.length,messages:Agent.items.length};
        }""")
        assert result=={'draft':'Conservar mi borrador','attachments':1,'messages':0}
        saves=page.evaluate("""async()=>{
          const original=rpc,calls=[];let pending=0,max=0;
          rpc=async(op,args)=>{if(op!=='save')return original(op,args);pending++;max=Math.max(max,pending);await new Promise(r=>setTimeout(r,30));calls.push(args.draft.prompt);pending--;};
          try{state.draft.prompt='primero';const first=flush();state.draft.prompt='segundo';const second=flush();await Promise.all([first,second]);return {calls,max,clean:savedRevision===saveRevision};}
          finally{rpc=original;}
        }""")
        assert saves=={'calls':['primero','segundo'],'max':1,'clean':True}
        page.evaluate("""()=>{
          view='canvas';loaded=E.normalize({id:'qa',providerId:'atlas_cloud',displayName:'QA',modalities:['IMAGE'],parameters:[]},{components:{schemas:{Input:{properties:{prompt:{type:'string',maxLength:3}}}}}});
          state.draft={modelId:'qa',prompt:'abcd',values:{},media:[]};render();
        }""")
        assert '4 / 3' in page.locator('#prompt-limits').inner_text()
        assert page.locator('#prompt').get_attribute('aria-invalid')=='true'
        page.evaluate("modal('<h2>Confirmacion QA</h2>')")
        assert page.get_by_role('dialog',name='Confirmacion QA').count()==1
        page.locator('[data-action=close]').click()
        assert page.locator('#generate').is_disabled()
        quotes=page.evaluate("""async()=>{
          const original=quotePlan;let resolveQuote,seen;
          quotePlan=plan=>{seen=plan;return new Promise(resolve=>resolveQuote=resolve);};
          try{
            state.draft.prompt='ab';render();const pending=getQuote();
            state.draft.prompt='cd';state.draft.values.changed='yes';
            resolveQuote({amount:1,currency:'USD',time:Date.now()});await pending;
            const staleBlocked=quote===null&&document.querySelector('#generate').disabled;
            const snapshot=seen.prompt==='ab'&&!seen.values.changed;
            const next=getQuote();view='settings';render();
            resolveQuote({amount:2,currency:'USD',time:Date.now()});await next;
            const navigationPreserved=!document.querySelector('#generate');
            view='canvas';state.draft.prompt='abcd';quote=null;render();
            return {staleBlocked,snapshot,navigationPreserved};
          }finally{quotePlan=original;}
        }""")
        assert quotes=={'staleBlocked':True,'snapshot':True,'navigationPreserved':True},quotes
        lifecycle=page.evaluate("""async()=>{
          const original=quotePlan,resolvers=[];state.draft.prompt='ab';render();
          quotePlan=()=>new Promise(resolve=>resolvers.push(resolve));
          try{
            const first=getQuote(),second=getQuote();
            resolvers[1]({amount:2,currency:'USD',time:Date.now()});await second;
            resolvers[0]({amount:1,currency:'USD',time:Date.now()});await first;
            const latestWins=quote.amount===2;
            const expired=getQuote();resolvers[2]({amount:3,currency:'USD',time:Date.now()-300001});await expired;
            await new Promise(resolve=>setTimeout(resolve,30));
            const expiredBlocked=quote===null&&document.querySelector('#generate').disabled;
            state.draft.prompt='abcd';render();await getQuote();
            return {latestWins,expiredBlocked,invalidNeverRequested:resolvers.length===3};
          }finally{quotePlan=original;}
        }""")
        assert lifecycle=={'latestWins':True,'expiredBlocked':True,'invalidNeverRequested':True},lifecycle
        for width in (1360,760):
            page.set_viewport_size({'width':width,'height':900})
            page.screenshot(path=str(data/f'canvas-{width}.png'))
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
        assert not errors,errors
        print('PASS: save order, draft recovery, stale quote blocked, snapshot isolated, navigation preserved, length guard, 1360/760 layout')
        b.close()
finally:
    subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
