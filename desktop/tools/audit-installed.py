"""Read-only desktop UI audit. Blocks provider, upload, submission and inference RPCs."""
import hashlib
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
out = root / 'outputs/audit-nexus-desktop-20261006/installed'
out.mkdir(parents=True, exist_ok=True)
data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
session = json.loads((data / 'local-session.json').read_text(encoding='utf-8'))
protected = [data / 'state.json', data / 'credentials.dpapi',
             data / 'assistant/settings.json', data / 'assistant/credentials.dpapi']
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None
before = {str(path): digest(path) for path in protected}
errors, blocked = [], []

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel='msedge', headless=True)
    # Allow the test runner's evaluate/wait predicates; product CSP stays unchanged.
    page = browser.new_page(viewport={'width': 1360, 'height': 900}, bypass_csp=True)
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('https://**/*', lambda route: route.abort())

    def guard(route):
        message = route.request.post_data_json
        allowed = (message.get('op') in ('init', 'agentEvents')
                   or message.get('op') == 'agent' and message.get('args', {}).get('op') == 'snapshot')
        if allowed:
            route.continue_()
        else:
            blocked.append(message.get('op'))
            route.fulfill(json={'id': message.get('id'), 'ok': False, 'error': 'Auditoría: operación bloqueada.'})

    page.route('**/rpc', guard)
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    page.wait_for_function("document.fonts.status==='loaded'")
    report = page.evaluate("""()=>({version:initial.version,models:models.length,
        flowModels:models.filter(m=>m.providerId==='google_flow').length,
        flowLite:models.some(m=>m.providerId==='google_flow'&&/lite/i.test(m.id+' '+m.displayName)),
        jobs:state.jobs.length,outputs:state.jobs.reduce((n,j)=>n+(j.outputs?.length||0),0),
        visibleCards:document.querySelectorAll('#content .result').length})""")
    assert report['flowModels'] == 5 and not report['flowLite'], report
    report['screens'] = []
    for view in ('library', 'catalog', 'canvas', 'assistant', 'settings'):
        if view == 'canvas':
            page.evaluate("""()=>{loaded=E.normalize(models.find(m=>m.id==='google-flow/nano2'));
                state.draft={modelId:loaded.id,prompt:'',values:E.defaults(loaded),media:[]};}""")
        page.evaluate('(target)=>navigate(target)', view)
        if view == 'assistant':
            page.evaluate("async()=>{await agentCall('snapshot');}")
            page.wait_for_function('Agent.snapshot!==null&&!Agent.busy')
            page.locator('#agent-input:not([disabled])').wait_for(timeout=30000)
            report['agentSkills'] = page.evaluate('Agent.snapshot.skills.length')
        for width in (1360, 760, 480):
            page.set_viewport_size({'width': width, 'height': 900})
            page.wait_for_timeout(150)
            overflow = page.evaluate('document.documentElement.scrollWidth>innerWidth')
            page.screenshot(path=str(out / f'{view}-{width}.png'))
            report['screens'].append({'view': view, 'width': width, 'overflow': overflow})
            assert not overflow, (view, width)
    browser.close()

after = {str(path): digest(path) for path in protected}
assert before == after, 'The audit changed protected data.'
assert not errors, errors
report.update(errors=errors, blockedOperations=blocked, protectedDataUnchanged=True,
              paidCalls=0, inferenceCalls=0)
(out / 'verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False))
