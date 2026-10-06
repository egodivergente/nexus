"""Desktop workflow and bounded-rendering checks, using only isolated fixtures."""
import json
from pathlib import Path
import sys
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
out = root / 'outputs/pc-optimization-20261006'
port = json.loads((out / 'qa-data/local-session.json').read_text())['port']
baseline = '--baseline' in sys.argv
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width':1360,'height':900}, bypass_csp=True)
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(f'http://127.0.0.1:{port}/')
    page.wait_for_function('Boolean(window.nexusQA)')
    assert page.evaluate("view==='library' && Agent.snapshot===null")
    page.evaluate('''() => {
      state.jobs=Array.from({length:1000},(_,i)=>({id:'pc-'+i,providerId:i%2?'runway':'atlas_cloud',
        modelId:'test-model',name:'Modelo '+i,prompt:i%2?'Retrato de estudio':'Ciudad nocturna',
        modality:'IMAGE',status:'COMPLETED',startedAt:1700000000000+i,
        outputs:['https://example.invalid/a.png','https://example.invalid/b.png']}));
    }''')
    page.route('https://example.invalid/**', lambda route: route.fulfill(path=str(root/'desktop/ui/logos/flux.png'),content_type='image/png'))
    metrics = page.evaluate('''() => {
      const times=[];for(let i=0;i<5;i++){const t=performance.now();navigate('library');times.push(performance.now()-t);}
      return {renderMs:times,renderedCards:document.querySelectorAll('.result').length,totalJobs:state.jobs.length};
    }''')
    if baseline:
        (out/'baseline.json').write_text(json.dumps(metrics,indent=2))
        browser.close()
        print(json.dumps(metrics))
        sys.exit(0)
    assert metrics['renderedCards'] <= 36
    page.locator('#pc-search').fill('Retrato')
    page.wait_for_timeout(300)
    assert '500' in page.locator('.pc-results-count').inner_text()
    page.locator('[data-pc-compare]').nth(0).click()
    page.locator('[data-pc-compare]').nth(1).click()
    page.locator('[data-pc=open-compare]').click()
    assert page.locator('.pc-comparison-item').count() == 2
    page.screenshot(path=str(out/'comparison.png'))
    page.locator('[data-action=close]').click()
    page.locator('#pc-search').fill('')
    page.wait_for_timeout(300)
    page.locator('[data-pc=more]').click()
    assert page.locator('#content .result').count() <= 72
    page.screenshot(path=str(out/'library.png'))
    page.evaluate('''async() => {
      const before=state.jobs;state.jobs=[{id:'active',providerId:'atlas_cloud',modelId:'x',name:'Active',modality:'VIDEO',
        prompt:'Fixture',status:'PROCESSING',remoteId:'fixture',outputs:[]}];
      navigate('history');const node=document.querySelector('.result');
      const oldRpc=rpc,oldQa=initial.qa;let saves=0;
      rpc=async(op)=>{if(op==='save'){saves++;return true;}if(op==='request')return {data:{id:'fixture',status:'processing'}};throw Error(op);};
      initial.qa=false;await poll(true);window.pcPollingCheck={sameNode:node===document.querySelector('.result'),saves};
      rpc=oldRpc;initial.qa=oldQa;state.jobs=before;navigate('library');
    }''')
    check=page.evaluate('window.pcPollingCheck')
    assert check['sameNode'] and check['saves']==0, check
    batch=page.evaluate('''async()=>{
      const oldDispatch=dispatch,oldNavigate=navigate;let release,submitted=0;
      dispatch=async()=>{submitted++;if(submitted===1)await new Promise(r=>release=r);return {};};navigate=()=>{};
      comparePlans=[{model:{id:'fixture'},cost:{time:Date.now(),amount:1,currency:'USD'}}];
      state.batchCounts={fixture:3};const run=sendComparison();
      Batch.paused=true;release();await new Promise(r=>setTimeout(r,120));
      const pausedAt=submitted;Batch.stopped=true;Batch.paused=false;await run;
      dispatch=oldDispatch;navigate=oldNavigate;navigate('library');
      return {pausedAt,submitted};
    }''')
    assert batch=={'pausedAt':1,'submitted':1}, batch
    page.set_viewport_size({'width':760,'height':900})
    page.evaluate("window.scrollTo(0,0)")
    page.screenshot(path=str(out/'library-narrow.png'))
    assert not page.evaluate('document.documentElement.scrollWidth>innerWidth')
    assert not errors, errors
    report={'metrics':metrics,'polling':check,'batch':batch,'errors':errors,'paidCalls':0}
    (out/'verification.json').write_text(json.dumps(report,indent=2))
    browser.close()
    print(json.dumps(report))
