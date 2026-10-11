"""Live zero-credit image 4K validation via the generic Nexus Agent gateway.

Uses a copied private test workspace. Never submits a paid video 4K operation.
Persists the Agent plan id and reuses its job after interruptions.
"""
import json,time
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
folder=root/'tools/flow-bridge/evidence-private'
session=json.loads((folder/'nexus-upscale-qa/local-session.json').read_text())
errors=[]
with sync_playwright() as pw:
    browser=pw.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    schema=page.evaluate("async()=>({models:(await agentCallback('models',{})).filter(m=>m.ref.providerId==='google_flow'),schema:await agentCallback('schema',{providerId:'google_flow',modelId:'google-flow/image_upscale'})})")
    assert len(schema['models'])==5
    assert schema['schema']['promptRequired'] is False
    assert any(p['key']=='source_request_id' and p['required'] for p in schema['schema']['parameters'])
    assert page.evaluate("isMediaUrl('/media/'+ 'a'.repeat(32)+'.gif')")
    existing=page.evaluate("state.jobs.find(j=>j.agentPlanId==='flow-upscale-4k-validation-20261006')?.id")
    if not existing:
        result=page.evaluate("""async()=>{
          const ref={providerId:'google_flow',modelId:'google-flow/image_upscale'};
          const params={source_request_id:'5c054626-692e-4973-bfed-620f0b8c9f43',resolution:'4k'};
          const model=await agentModel(ref),cost=await quotePlan({model,prompt:'',values:params,media:[]});
          if(cost.amount!==0||cost.currency!=='flow_credits')throw Error('Only the zero-credit image upscale is authorized for this test.');
          Agent.approved={id:'flow-upscale-4k-validation-20261006',cost};
          return await agentCallback('submit',{id:'flow-upscale-4k-validation-20261006',ref,prompt:'',params,media:[]});
        }""")
        existing=result['jobId']
    deadline=time.monotonic()+500
    while time.monotonic()<deadline:
        page.evaluate('poll(true)')
        job=page.evaluate('(id)=>state.jobs.find(j=>j.id===id)',existing)
        if job['status'] in ('COMPLETED','FAILED'):break
        page.wait_for_timeout(3000)
    assert job['status']=='COMPLETED',{'status':job['status'],'error':job.get('error')}
    size=page.evaluate("async url=>{const image=new Image();image.src=url;await image.decode();return [image.naturalWidth,image.naturalHeight]}",job['outputs'][0])
    assert size==[4096,4096],size
    page.evaluate("navigate('library')")
    page.evaluate("studioDetails('flow-video-validacion-20261006-001')")
    assert page.locator('[data-flow-improve]').count()==1
    page.locator('[data-flow-improve]').click()
    page.wait_for_selector('#flow-asset-resolution',timeout=90000)
    choices=page.locator('#flow-asset-resolution').inner_text()
    assert '1080P' in choices and '50 créditos Flow' in choices and 'GIF' in choices
    page.screenshot(path=str(folder/'nexus-upscale-options.png'))
    assert not errors,errors
    evidence={'job_id':existing,'status':job['status'],'dimensions':size,'options':choices,
        'gateway':'Agent callback → host → real Flow upscale → local library',
        'new_generations':0,'video_4k_submitted':False,'javascript_errors':errors,'llm_conversation_tested':False}
    (folder/'nexus-upscale-live-validation.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
    print(json.dumps(evidence,ensure_ascii=False,indent=2))
    browser.close()
