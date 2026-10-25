"""Browser + real Python RPC + compiled JVM, isolated profile, no inference or paid operations."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
DESKTOP = Path(os.environ.get('NEXUS_TEST_DESKTOP', str(ROOT / 'desktop')))
OUT = Path(os.environ.get('NEXUS_TEST_OUTPUT', str(ROOT / 'outputs/desktop-update-20261025/e2e')))
OUT.mkdir(parents=True, exist_ok=True)
errors, blocked, checks = [], [], []

def check(name, value):
    assert value, name
    checks.append(name)
    print("PASS", name, flush=True)

with tempfile.TemporaryDirectory(prefix='nexus-update-', dir=Path.home()/'.aki/tmp') as temp:
    data = Path(temp)
    chats = data / 'assistant/conversations'
    chats.mkdir(parents=True)
    original = {'desktop': True, 'id': 'fixture_a', 'items': [{'role': 'user', 'text': 'hola, crea un cartel azul'}, {'role': 'assistant', 'text': 'Propuesta de cartel'}], 'draft': 'Borrador A', 'draftAttachments': [{'id':'qa_ref','name':'Referencia.png','url':'/media/'+'a'*32+'.png','mime':'image/png'}]}
    second = {'desktop': True, 'id': 'fixture_b', 'items': [{'role': 'user', 'text': 'Vídeo del faro con niebla'}], 'draft': 'Borrador B'}
    for name, value in [('fixture_a', original), ('fixture_b', second)]:
        (chats / (name+'.json')).write_text(json.dumps(value), 'utf-8')
    digest = hashlib.sha256((chats/'fixture_a.json').read_bytes()).hexdigest()
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    with (OUT/'host.log').open('w', encoding='utf-8') as hostlog:
        host = subprocess.Popen([sys.executable, '-u', str(DESKTOP/'local_host.py'), '--headless', '--offline-qa', '--data-dir', str(data)], stdout=subprocess.PIPE, stderr=hostlog, text=True, env=env, creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:
            start = json.loads(host.stdout.readline())
            url = start['url']
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            try:
                opener.open(urllib.request.Request(url+'rpc', data=b'{"id":"x","op":"init","args":{}}', headers={'Content-Type':'application/json'}))
                raise AssertionError('Unauthenticated RPC accepted')
            except urllib.error.HTTPError as exc:
                check('unauthenticated RPC rejected', exc.code == 403)
            with sync_playwright() as pw:
                browser = pw.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width':1360,'height':900})
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.route('https://**/*', lambda route: route.abort())
                def guard(route):
                    message = route.request.post_data_json
                    op = message.get('op')
                    if op in {'request','generate','upload','flowSubmit','flowConnect','flowQuote','nativeOperation'} or op == 'agent' and message.get('args',{}).get('op') in {'send','confirm','rewritePrompt','apply'}:
                        blocked.append(op)
                        route.fulfill(json={'id':message['id'],'ok':False,'error':'E2E forbids external operations'})
                    else:
                        route.continue_()
                page.route('**/rpc', guard)
                page.goto(url)
                page.wait_for_function("()=>!!window.NexusChatContinuity&&!!window.NexusAgentParity&&!!window.CatalogParity&&!!initial", timeout=45000)
                page.evaluate("async()=>{if(Agent.initPromise)await Agent.initPromise;await agentCall('snapshot');navigate('assistant');}")
                check('compiled JVM responds offline', page.evaluate('Agent.snapshot.offline===true&&Agent.snapshot.tools.length>30&&Agent.snapshot.skills.length>30'))
                check('new desktop version', page.evaluate("initial.version==='1.9.6-agent-desktop'"))
                check('CSP unchanged', 'unsafe-eval' not in page.locator('meta[http-equiv="Content-Security-Policy"]').get_attribute('content'))
                page.locator('[data-parity="conversations"]').click()
                page.locator('[data-cc-open="fixture_a"]').wait_for()
                page.locator('[data-cc-row="fixture_a"]').hover()
                page.locator('[data-cc-rename="fixture_a"]').click()
                page.locator('#cc-rename-input').fill('Cartel del festival')
                page.locator('#cc-rename-form [type="submit"]').click()
                page.wait_for_function("()=>document.querySelector('[data-cc-row=fixture_a] .cc-row-title')?.textContent==='Cartel del festival'")
                print('PANEL DEBUG',page.evaluate("()=>({open:NexusChatContinuity._panel.open,view,hidden:document.getElementById('cc-panel').hidden,box:document.getElementById('cc-panel').getBoundingClientRect().toJSON(),content:document.getElementById('content').className})"),flush=True)
                page.screenshot(path=str(OUT/'rename-debug.png'))
                page.get_by_text('Cartel del festival', exact=True).wait_for()
                check('renaming keeps original chat bytes', hashlib.sha256((chats/'fixture_a.json').read_bytes()).hexdigest()==digest)
                page.locator('#cc-search').fill('niebla')
                page.wait_for_function("()=>document.querySelectorAll('[data-cc-open]').length===1&&!!document.querySelector('[data-cc-open=fixture_b]')")
                checks.append('searches archived message content')
                page.locator('#cc-search').fill('')
                page.locator('[data-cc-open="fixture_a"]').wait_for()
                page.locator('[data-cc-open="fixture_a"]').click()
                page.wait_for_function("()=>state.agentConversation?.id==='fixture_a'&&!Agent.busy")
                check('opens history through real JVM and restores draft', page.locator('#agent-input').input_value()=='Borrador A')
                check('archived draft attachment restored', page.evaluate("Agent.attachments[0]?.id==='qa_ref'"))
                page.locator('#agent-input').fill('Borrador A editado')
                page.locator('[data-cc-open="fixture_b"]').click()
                page.wait_for_function("()=>state.agentConversation?.id==='fixture_b'&&!Agent.busy")
                page.locator('[data-cc-open="fixture_a"]').click()
                page.wait_for_function("()=>state.agentConversation?.id==='fixture_a'&&!Agent.busy")
                check('switch back preserves draft and stable identity', page.locator('#agent-input').input_value()=='Borrador A editado')
                check('no duplicate archives', sorted(p.stem for p in chats.glob('*.json'))==['fixture_a','fixture_b'])
                guarded = page.evaluate("""async()=>{
                  const original=rpc;let release;const gate=new Promise(resolve=>release=resolve);
                  rpc=async function(op,args){if(op==='loadChat')await gate;return original(op,args);};
                  const changing=NexusChatContinuity.open('fixture_b');
                  try{
                    const before=Agent.items.length,draft=Agent.draft;
                    await sendAgent();
                    const held=NexusChatContinuity.isSwitching()&&Agent.items.length===before&&Agent.draft===draft;
                    release();await changing;await NexusChatContinuity.open('fixture_a');return held;
                  }finally{release();rpc=original;await changing.catch(()=>{});}
                }""")
                check('sending is blocked while history is switching', guarded)
                page.evaluate("()=>{Agent.items.push({id:'long_user',role:'user',text:('Línea extensa de pruebas.\\n').repeat(25)},{id:'long_assistant',role:'assistant',text:('Respuesta que debe permanecer completa.\\n').repeat(25)});renderAssistant();}")
                toggle = page.locator('[data-cc-fold="long_user"]')
                check('long user prompt folds', toggle.is_visible() and toggle.get_attribute('aria-expanded')=='false')
                toggle.click()
                check('long prompt expands', toggle.get_attribute('aria-expanded')=='true')
                check('assistant response never folds', page.locator('[data-cc-fold="long_assistant"]').count()==0)
                page.screenshot(path=str(OUT/'chat-and-conversations.png'))
                page.evaluate("()=>{NexusChatContinuity.toggle(false);navigate('catalog');}")
                for density in ['compact','comfortable','spacious']:
                    page.locator('[data-catalog-density="'+density+'"]').click()
                    check('density '+density, page.locator('.catalog').get_attribute('data-density')==density)
                page.screenshot(path=str(OUT/'catalog.png'))
                page.evaluate('async()=>{await flush();}')
                page.reload()
                page.wait_for_function("()=>!!window.NexusChatContinuity&&!!window.NexusAgentParity&&!!initial", timeout=45000)
                check('density persists after reload', page.evaluate("state.catalogDensity==='spacious'"))
                page.evaluate("async()=>{if(Agent.initPromise)await Agent.initPromise;await agentCall('snapshot');navigate('assistant');NexusChatContinuity.toggle(true);}")
                page.get_by_text('Cartel del festival', exact=True).wait_for()
                checks.append('custom title survives reload')
                check('no JavaScript errors', not errors)
                check('no external operation attempted', not blocked)
                browser.close()
            cache = data/'assistant/cache/skill-parse-cache.json'
            check('compiled JVM populated derived skill cache', cache.is_file() and cache.stat().st_size>0)
        finally:
            host.terminate()
            host.wait(timeout=10)
result={'checks':checks,'count':len(checks),'errors':errors,'blockedOperations':blocked,'inferenceCalls':0,'profile':'isolated','desktop':str(DESKTOP)}
(OUT/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf-8')
print(json.dumps(result,ensure_ascii=False))
