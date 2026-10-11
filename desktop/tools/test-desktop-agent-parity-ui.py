"""Isolated browser QA: six Studio themes, three font scales and real local host."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import time
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
out=root/'outputs/desktop-agent-parity/ui-qa'
out.mkdir(parents=True,exist_ok=True)
data=out/'data'
data.mkdir(exist_ok=True)
original=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop'
state=json.loads((original/'state.json').read_text('utf-8-sig'))
# Preserve representative catalog/drafts/results while using an isolated, offline profile.
state['agentConversation']=None
(data/'state.json').write_text(json.dumps(state),encoding='utf-8')
(data/'assistant').mkdir(exist_ok=True)
(data/'assistant/settings.json').write_text(json.dumps({'backend':'direct','mode':'AGENT','modelId':'us.anthropic.claude-sonnet-4-6'}))
process=subprocess.Popen([sys.executable,str(root/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
try:
    url=json.loads(process.stdout.readline())['url']
    errors=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(channel='msedge',headless=True)
        page=browser.new_page(viewport={'width':1366,'height':768})
        page.on('pageerror',lambda error:errors.append(str(error)))
        page.goto(url)
        page.wait_for_function('Boolean(window.NexusAgentParity)&&Boolean(initial)',timeout=45000)
        page.wait_for_function("document.fonts.status==='loaded'")
        page.evaluate("async()=>{await agentCall('snapshot');await agentCall('createProject',{name:'QA Studio'});await agentCall('projectNote',{projectId:Agent.snapshot.projectId,text:'Nota de prueba'});}")
        assert page.evaluate("Agent.snapshot.tools.includes('edit_video_assets')&&Agent.snapshot.tools.includes('web_search')&&Agent.snapshot.tools.includes('transcribe_media_asset')")
        report=[]
        for theme in ['aurora','oled','quartz','ocean','forest','light']:
            for scale in [1,1.3,1.5]:
                for view in ['assistant','canvas','catalog','history','library','settings']:
                    page.evaluate('args=>{state.interfaceTheme=args.theme;state.fontScale=args.scale;NexusThemes.apply();navigate(args.view);}',{'theme':theme,'scale':scale,'view':view})
                    page.wait_for_timeout(100)
                    measurement=page.evaluate("""()=>({overflow:document.documentElement.scrollWidth>innerWidth,
                       navigation:document.querySelectorAll('#navigation button').length,
                       visible:document.querySelector('#content').getBoundingClientRect().height>0,
                       theme:document.body.dataset.interfaceTheme})""")
                    assert not measurement['overflow'],(theme,scale,view,measurement)
                    assert measurement['navigation']==5,measurement
                    assert measurement['visible'],measurement
                    if view in ['assistant','catalog','history']:
                        page.screenshot(path=str(out/f'{theme}-{scale}-{view}.png'))
                    report.append({'theme':theme,'scale':scale,'screen':view,**measurement})
        page.evaluate("NexusAgentParity.project()")
        page.wait_for_selector('#parity-project-memory')
        assert page.locator('#dialog').evaluate("node=>node.classList.contains('parity-sheet')")
        assert 'Nota de prueba' in page.locator('#dialog').inner_text()
        page.screenshot(path=str(out/'project-sheet.png'))
        page.locator('#dialog .close').click()
        page.evaluate("navigate('assistant');agentSettings()")
        assert page.locator('[data-parity-model]').count()==4
        page.screenshot(path=str(out/'agent-settings.png'))
        assert not errors,errors
        result={'checks':len(report),'errors':errors,'nativeTools':page.evaluate('Agent.snapshot.tools'),'paidGenerations':0,'matrix':report}
        (out/'report.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
        print(json.dumps({'checks':len(report),'errors':errors,'tools':len(result['nativeTools']),'paidGenerations':0}))
        browser.close()
finally:
    process.terminate()
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.kill()
