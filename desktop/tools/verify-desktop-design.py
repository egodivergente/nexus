"""Read-only installed UI validation. Blocks provider requests and inference."""
import hashlib
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
out=root/'outputs/desktop-design-20261006/installed'
out.mkdir(parents=True,exist_ok=True)
data=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop'
session=json.loads((data/'local-session.json').read_text(encoding='utf-8'))
paths=[data/'state.json',data/'credentials.dpapi',data/'assistant/settings.json',data/'assistant/credentials.dpapi']
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None
before={str(p):digest(p) for p in paths}
errors,blocked=[],[]
with sync_playwright() as pw:
    options={'headless':True}
    if os.environ.get('NEXUS_QA_BROWSER'):options['executable_path']=os.environ['NEXUS_QA_BROWSER']
    else:options['channel']='msedge'
    browser=pw.chromium.launch(**options)
    page=browser.new_page(viewport={'width':1360,'height':900},bypass_csp=True)
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.route('https://**/*',lambda route:route.abort())
    def guard(route):
        message=route.request.post_data_json
        allowed=message['op'] in ('init','agentEvents') or message['op']=='agent' and message.get('args',{}).get('op')=='snapshot'
        if allowed:route.continue_()
        else:
            blocked.append(message['op'])
            route.fulfill(json={'id':message['id'],'ok':False,'error':'Read-only visual verification.'})
    page.route('**/rpc',guard)
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)&&DesktopDesign.ready')
    page.wait_for_function("typeof renderBalances==='function'")
    report=page.evaluate("""()=>({models:models.length,jobs:state.jobs.length,outputs:state.jobs.reduce((n,j)=>n+(j.outputs?.length||0),0),design:DesktopDesign.version,liteBlocked:!models.some(forbiddenModel)})""")
    assert report['liteBlocked'],report
    report['screens']=[]
    for view in ('library','catalog','canvas','assistant','settings'):
        if view=='canvas':
            page.evaluate("()=>{loaded=E.normalize(models.find(m=>m.id==='google-flow/nano2'));state.draft={modelId:loaded.id,prompt:'',values:E.defaults(loaded),media:[]};}")
        page.evaluate('(view)=>navigate(view)',view)
        if view=='assistant':page.evaluate("async()=>{await agentCall('snapshot');}")
        for width,height in [(1360,900),(760,900),(480,850)]:
            page.set_viewport_size({'width':width,'height':height})
            page.wait_for_timeout(150)
            assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'),(view,width)
            if view=='assistant':
                compose=page.locator('#agent-compose').bounding_box()
                assert compose['y']+compose['height']<=height+1,(width,compose)
            page.screenshot(path=str(out/f'{view}-{width}.png'))
            report['screens'].append({'view':view,'width':width})
    browser.close()
assert before=={str(p):digest(p) for p in paths},'Protected main data changed'
assert not errors,errors
report.update(status='PASS',protectedDataUnchanged=True,errors=errors,blockedOperations=blocked,generations=0,inferenceCalls=0)
(out/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
