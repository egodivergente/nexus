"""Complete the LLM-created Flow plan in the isolated live test workspace.

Only a freshly read zero-credit image 2K quote is allowed. Reuses the plan's
existing Nexus job after interruption; never creates another plan or prompt.
Run verify-flow-agent-conversation.py first with an authorized live LLM account.
"""
import hashlib
import json
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
folder=root/'tools/flow-bridge/evidence-private'
session=json.loads((folder/'nexus-llm-qa/local-session.json').read_text())
conversation=json.loads((folder/'flow-agent-conversation.json').read_text())
entry=next(p for p in conversation['plans'] if p['plan']['id']==conversation['prepared_plan_id'])
plan=entry['plan']
assert plan['params']=={'source_request_id':'5c054626-692e-4973-bfed-620f0b8c9f43','resolution':'2k'}
errors=[]
with sync_playwright() as pw:
    browser=pw.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(bypass_csp=True,viewport={'width':1360,'height':900})
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    source=page.evaluate('(id)=>state.jobs.find(j=>j.id===id)',plan['params']['source_request_id'])
    original=page.request.get(f"http://127.0.0.1:{session['port']}"+source['outputs'][0]).body()
    original_sha=hashlib.sha256(original).hexdigest()
    assert original_sha==source['verifiedSha256'],'Original does not match the verified source job.'
    existing=page.evaluate('(id)=>state.jobs.find(j=>j.agentPlanId===id)?.id',plan['id'])
    if not existing:
        page.evaluate("agentCall('snapshot')")
        result=page.evaluate("""async expected=>{
          const entry=Agent.snapshot.plans.find(p=>p.plan.id===expected.id);
          if(!entry||entry.fingerprint!==expected.fingerprint)throw Error('The live LLM plan is missing or changed; do not recreate it.');
          const p=entry.plan,m=await agentModel(p.ref);
          const cost=await quotePlan({model:m,prompt:p.prompt,values:p.params,media:[]});
          if(cost.amount!==0||cost.currency!=='flow_credits'||cost.upperBound)throw Error('Only a confirmed zero-credit image upscale is authorized.');
          Agent.approved={id:p.id,fingerprint:entry.fingerprint,cost};
          return await agentCall('confirm',{id:p.id,fingerprint:entry.fingerprint});
        }""",{'id':plan['id'],'fingerprint':entry['fingerprint']})
        existing=result['job']['jobId']
    deadline=time.monotonic()+500
    while time.monotonic()<deadline:
        page.evaluate('poll(true)')
        job=page.evaluate('(id)=>state.jobs.find(j=>j.id===id)',existing)
        if job['status'] in ('COMPLETED','FAILED'):break
        page.wait_for_timeout(3000)
    assert job['status']=='COMPLETED',{'status':job['status'],'error':job.get('error'),'job_id':existing}
    dimensions=page.evaluate("async url=>{const i=new Image();i.src=url;await i.decode();return [i.naturalWidth,i.naturalHeight]}",job['outputs'][0])
    assert dimensions==[2048,2048],dimensions
    output=page.request.get(f"http://127.0.0.1:{session['port']}"+job['outputs'][0]).body()
    assert hashlib.sha256(page.request.get(f"http://127.0.0.1:{session['port']}"+source['outputs'][0]).body()).hexdigest()==original_sha
    page.evaluate("(id)=>agentCall('sync',{id})",existing)
    assert page.evaluate('(id)=>Agent.snapshot.index.generations.some(g=>g.nexusJobId===id&&g.status==="COMPLETED")',existing)
    page.evaluate("navigate('library')")
    page.screenshot(path=str(folder/'flow-agent-result.png'))
    assert not errors,errors
    report={'status':'PASS','plan_id':plan['id'],'job_id':existing,'model_id':plan['ref']['modelId'],
        'source_request_id':plan['params']['source_request_id'],'dimensions':dimensions,
        'original_sha256':original_sha,'result_sha256':hashlib.sha256(output).hexdigest(),
        'bytes':len(output),'credits_quoted':0,'original_preserved':True,
        'gateway':'real LLM plan -> native confirmation -> MCP -> Flow -> Nexus library',
        'video_4k_submitted':False,'javascript_errors':errors}
    (folder/'flow-agent-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    browser.close()
