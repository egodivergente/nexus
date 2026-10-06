"""Exercise the real local host and generic Agent callbacks without generating."""
import json
from pathlib import Path
import sys
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
folder=root/'tools/flow-bridge/evidence-private'
session=json.loads((folder/'nexus-qa/local-session.json').read_text())
errors=[]
with sync_playwright() as pw:
    browser=pw.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    result=page.evaluate("""async()=>{
      const list=await agentCallback('models',{}),ref={providerId:'google_flow',modelId:'google-flow/nano2'};
      const schema=await agentCallback('schema',ref);
      const model=await configuration(ref.modelId);
      const video=await configuration('google-flow/veo-fast');
      return {flow:list.filter(m=>m.ref.providerId==='google_flow'),schema,defaults:E.defaults(model),
       imageError:E.validate(model,'Prueba',{aspect:'1:1'},[]),videoError:E.validate(video,'Prueba',{aspect:'16:9'},[]),
       malicious:isMediaUrl('/media/../../credentials.dpapi'),local:isMediaUrl('/media/'+ 'a'.repeat(32)+'.mp4')};
    }""")
    assert len(result['flow'])==2 and all('lite' not in m['name'].lower() for m in result['flow'])
    assert result['schema']['parameters'][0]['key']=='aspect'
    assert result['imageError'] is None and result['videoError'] is None
    assert result['malicious'] is False and result['local'] is True
    native=page.evaluate("async()=>{await agentCall('reload');return {ready:!!Agent.snapshot,skills:Agent.snapshot.skills.length,price:await agentCallback('quote',{ref:{providerId:'google_flow',modelId:'google-flow/nano2'},params:{aspect:'1:1'}})}}")
    assert native['ready'] and native['skills']>0
    assert native['price']['amountUsd'] is None and 'Flow' in native['price']['basis']
    page.evaluate("navigate('canvas')")
    page.evaluate("openModel('google-flow/nano2')")
    page.wait_for_function("loaded?.id==='google-flow/nano2'&&!configuring")
    page.screenshot(path=str(folder/'nexus-flow-model.png'))
    assert not errors, errors
    print(json.dumps({'agent_models':len(result['flow']),'generic_agent_schema':True,'form_validation':True,
      'local_media_allowlist':True,'javascript_errors':errors,'generations_sent':0},indent=2))
    browser.close()
