"""Authorized one-image Flow validation through the generic Nexus Agent gateway."""
import json
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
folder=root/'tools/flow-bridge/evidence-private'
session=json.loads((folder/'nexus-qa/local-session.json').read_text())
evidence=folder/'nexus-live-validation.json'
with sync_playwright() as pw:
    browser=pw.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    # The current host persists a SENDING reservation before sending to Flow.
    # An interrupted validation is read from that exact job; never send twice.
    existing=page.evaluate("state.jobs.find(j=>j.agentPlanId==='flow-integration-validation-20261006'||j.prompt==='A polished turquoise glass sphere floating above a matte black pedestal, soft studio lighting, minimalist product photography, no text.')?.id")
    if not existing:
        result=page.evaluate("""async()=>{
          const ref={providerId:'google_flow',modelId:'google-flow/nano2'};
          const model=await agentModel(ref),cost=await quotePlan({model,prompt:'',values:{aspect:'1:1'},media:[]});
          if(cost.amount!==0||cost.currency!=='flow_credits')throw Error('Only the verified zero-credit image is authorized for this test.');
          Agent.approved={id:'flow-integration-validation-20261006',cost};
          return await agentCallback('submit',{id:'flow-integration-validation-20261006',ref,
            prompt:'A polished turquoise glass sphere floating above a matte black pedestal, soft studio lighting, minimalist product photography, no text.',
            params:{aspect:'1:1'},media:[]});
        }""")
        existing=result['jobId']
        evidence.write_text(json.dumps({'job_id':existing,'state':'submitted'}),encoding='utf-8')
    deadline=time.monotonic()+420
    while time.monotonic()<deadline:
        page.evaluate('poll(true)')
        job=page.evaluate('(id)=>state.jobs.find(j=>j.id===id)',existing)
        if job['status'] in ('COMPLETED','FAILED'):break
        page.wait_for_timeout(5000)
    assert job['status']=='COMPLETED', {'status':job['status'],'error':job.get('error')}
    assert len(job['outputs'])==1 and job['outputs'][0].startswith('/media/')
    actual=page.evaluate("async url=>{const r=await fetch(url);return {status:r.status,bytes:(await r.arrayBuffer()).byteLength}}",job['outputs'][0])
    assert actual['status']==200 and actual['bytes']>1000
    page.evaluate("navigate('library')")
    page.wait_for_function('document.querySelector(".result img")?.naturalWidth>0')
    page.screenshot(path=str(folder/'nexus-flow-real-result.png'))
    evidence.write_text(json.dumps({'job_id':existing,'status':job['status'],'outputs':job['outputs'],
        'bytes':actual['bytes'],'gateway':'agentCallback submit → desktop host → real Flow MCP → library',
        'credit_quote':job['estimated'],'llm_conversation_tested':False},indent=2),encoding='utf-8')
    print(evidence.read_text(encoding='utf-8'))
    browser.close()
