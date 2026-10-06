"""Real LLM planning check in an isolated workspace, without submitting media.

Seed that workspace with one completed Flow test image and the user's encrypted
LLM settings. Requires the user's authorization for the live conversation.
No Bedrock secrets, private conversation history or signed URLs are printed.
"""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
folder=root/'tools/flow-bridge/evidence-private'
session=json.loads((folder/'nexus-llm-qa/local-session.json').read_text())
report_path=folder/'flow-agent-conversation.json'
errors=[]
with sync_playwright() as pw:
    browser=pw.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(bypass_csp=True,viewport={'width':1360,'height':900})
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    setup=page.evaluate("""async()=>{
      await agentCall('snapshot');await agentCall('importHistory');
      await agentCall('new');
      window.flowCallbackTrace=[];const original=agentCallback;
      agentCallback=async(op,args)=>{flowCallbackTrace.push(op);return original(op,args);};
      return {offline:Agent.snapshot.offline,mode:Agent.snapshot.mode,jobs:state.jobs.length,planIds:Agent.snapshot.plans.map(p=>p.plan.id)};
    }""")
    assert setup['offline'] is False and setup['mode']=='AGENT'
    # This is deliberately a user-level request. No model id, tool name or source
    # id is supplied: the LLM must discover them in the real catalog/history.
    response=page.evaluate("""async()=>{
      const result=await agentCall('send',{text:'Escala a 2K mi imagen más reciente de Google Flow usando mi propia cuenta Flow. Conserva el original. Prepara el trabajo con su coste en créditos Flow y déjalo listo para revisar, sin enviarlo todavía.',attachments:[]});
      return {text:result.text,inputTokens:result.inputTokens,outputTokens:result.outputTokens,
        toolRounds:result.toolRounds,elapsedMs:result.elapsedMs,stop:result.stop,
        tools:result.tools,toolNames:(result.tools||[]).map(t=>typeof t==='string'?t:t.name||t.toolName||t.call?.name),plans:Agent.snapshot.plans,
        callbacks:flowCallbackTrace,jobs:state.jobs.length};
    }""")
    # Persist the exact outcome before asserting so failures remain reviewable.
    report_path.write_text(json.dumps(response,ensure_ascii=False,indent=2),encoding='utf-8')
    plans=[p for p in response['plans'] if p['plan']['ref']['modelId']=='google-flow/image_upscale' and p['plan']['id'] not in setup['planIds']]
    assert plans,{'text':response['text'],'tools':response['toolNames'],'callbacks':response['callbacks']}
    plan=plans[-1]['plan']
    assert plan['params']['source_request_id']=='5c054626-692e-4973-bfed-620f0b8c9f43'
    assert plan['params']['resolution']=='2k'
    assert 'Flow' in plan['estimate']['basis']
    assert plan['estimate']['amountUsd'] is None and plan['estimate']['confidence']=='NEXUS_QUOTE'
    assert '0 créditos Flow' in response['text'],response['text']
    assert 'saldo exacto se muestra' not in response['text'].lower(),response['text']
    assert response['jobs']==setup['jobs'] and 'submit' not in response['callbacks']
    assert not errors,errors
    response['prepared_plan_id']=plan['id']
    report_path.write_text(json.dumps(response,ensure_ascii=False,indent=2),encoding='utf-8')
    page.evaluate("navigate('assistant')")
    page.screenshot(path=str(folder/'flow-agent-plan.png'))
    print(json.dumps({'status':'PASS','model_id':plan['ref']['modelId'],'source_request_id':plan['params']['source_request_id'],
        'resolution':plan['params']['resolution'],'cost':plan['estimate']['basis'],
        'input_tokens':response['inputTokens'],'output_tokens':response['outputTokens'],
        'tool_rounds':response['toolRounds'],'callbacks':response['callbacks'],
        'media_submissions':0,'javascript_errors':errors},ensure_ascii=False,indent=2))
    browser.close()
