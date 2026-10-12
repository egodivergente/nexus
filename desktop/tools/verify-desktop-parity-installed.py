"""Verify the actual installed host/profile without changing settings or submitting work."""
from pathlib import Path
import json,os
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[2];out=root/'outputs/desktop-agent-parity';out.mkdir(parents=True,exist_ok=True)
session=json.loads((Path(os.environ['LOCALAPPDATA'])/'NexusDesktop/local-session.json').read_text('utf-8'))
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True);page=browser.new_page(viewport={'width':1366,'height':768});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto('http://127.0.0.1:'+str(session['port']))
    page.wait_for_function('()=>Boolean(window.NexusAgentParity)&&Boolean(initial)',timeout=45000)
    page.wait_for_function('()=>!Agent.busy',timeout=30000)
    result=page.evaluate("async()=>{await agentCall('snapshot');const whisper=await NexusAgentParity.native('get_whisper_status');return {version:initial.version,androidSource:initial.androidSource,providers:initial.configured,model:Agent.settings.modelId,endpoint:Agent.settings.endpoint,tools:Agent.snapshot.tools.length,offline:Agent.snapshot.offline,projects:Agent.snapshot.index.projects.length,assets:Agent.snapshot.index.assets.length,jobs:state.jobs.length,whisperReady:whisper.ready};}")
    assert result['version']=='1.9.3-agent-desktop' and result['tools']==51 and result['whisperReady'],result
    assert result['model']=='openai.gpt-5.6-sol' and result['endpoint']=='http://127.0.0.1:18787',result
    page.evaluate("navigate('assistant')");page.screenshot(path=str(out/'installed-assistant-private.png'))
    page.evaluate("navigate('library')");page.wait_for_timeout(500);page.screenshot(path=str(out/'installed-library-private.png'))
    assert page.locator('[data-parity=editor]').count()==1
    assert not errors,errors
    result.update(errors=errors,paidGenerations=0,pid=session['pid'])
    (out/'installed-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
    browser.close()
