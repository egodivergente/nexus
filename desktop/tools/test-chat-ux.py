"""Exercise the chat with real keyboard/scroll events and intercepted, unpaid replies."""
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'outputs/chat-ux-20261008'
OUT.mkdir(parents=True, exist_ok=True)
errors, requests, pending, delayed_media = [], [], [], []
estimate = {'code': 200, 'data': {'discount': '1', 'origin_price': '0.072', 'price': '0.072'}}
snapshot = {'mode': 'AGENT', 'offline': False, 'projectId': None,
            'index': {'projects': [], 'assets': []}, 'skills': [], 'plans': [], 'proposals': []}

with tempfile.TemporaryDirectory(prefix='nexus-chat-ux-') as folder:
    data = Path(folder)
    (data / 'media').mkdir()
    shutil.copyfile(ROOT / 'desktop/ui/logos/flux.png', data / 'media' / ('a' * 32 + '.png'))
    host = subprocess.Popen([sys.executable, str(ROOT / 'desktop/local_host.py'), '--headless',
                             '--offline-qa', '--data-dir', str(data)], stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            try:
                session = json.loads((data / 'local-session.json').read_text(encoding='utf-8'))
                break
            except (OSError, ValueError):
                time.sleep(.1)
        else:
            raise RuntimeError('QA host unavailable')
        with sync_playwright() as p:
            browser = p.chromium.launch(channel='msedge', headless=True)
            page = browser.new_page(viewport={'width': 1920, 'height': 1080}, bypass_csp=True)
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.route('https://**/*', lambda route: route.abort())

            def rpc_guard(route):
                m = route.request.post_data_json
                if m['op'] == 'agent':
                    requests.append(m)
                    if m['args']['op'] == 'send':
                        pending.append((route, m))
                    else:
                        route.fulfill(json={'id': m['id'], 'ok': True,
                                            'value': {'settings': {}, 'result': snapshot}})
                elif m['op'] == 'agentEvents':
                    route.fulfill(json={'id': m['id'], 'ok': True, 'value': []})
                elif m['op'] == 'request' and m['args'].get('path') == '/api/v1/model/calculate':
                    route.fulfill(json={'id': m['id'], 'ok': True, 'value': estimate})
                elif m['op'] in ['generate', 'upload', 'request', 'flowSubmit', 'flowQuote']:
                    raise AssertionError('External operation blocked: ' + m['op'])
                else:
                    route.continue_()

            page.route('**/rpc', rpc_guard)
            page.route('**/media/' + 'b' * 32 + '.png', lambda route: delayed_media.append(route))
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function('!!window.NexusCreative && !!window.NexusProjectReferences')
            # Use the actual Atlas response shape; quotes must accept numeric JSON strings.
            quote_script = """async()=>{
              const qa=initial.qa;initial.qa=false;
              try{const m=E.normalize(models.find(m=>m.id==='bytedance/seedream-v5.0-pro/edit'));
                return await quotePlan({model:m,prompt:'Local QA',values:E.defaults(m),media:[]});
              }finally{initial.qa=qa;}
            }"""
            assert page.evaluate(quote_script)['amount'] == .072
            for value in [0, '0', '0.036', .036]:
                estimate['data']['price'] = value
                assert page.evaluate(quote_script)['amount'] == float(value)
            for value in ['', None, False, '-1', 'unknown']:
                estimate['data']['price'] = value
                try:
                    page.evaluate(quote_script)
                except Exception as error:
                    assert 'El proveedor no devolvió una estimación' in str(error)
                else:
                    raise AssertionError('Invalid quote accepted: ' + repr(value))
            page.evaluate(r"""s=>{
              Agent.snapshot=s;Agent.restored=true;Agent.items=[];Agent.cardAnchors.clear();
              for(let i=0;i<24;i++)Agent.items.push({role:'user',text:'Escena '+i},
                {role:'assistant',text:'Respuesta '+i+'\n'+('Continuidad visual y referencias del proyecto. '.repeat(8))});
              navigate('assistant');
            }""", snapshot)
            bottom = "(()=>{const n=document.querySelector('.agent-messages');return n.scrollHeight-n.clientHeight-n.scrollTop<3})()"
            page.wait_for_function(bottom)
            def read_at(top):
                box = page.locator('.agent-messages').bounding_box()
                current = page.locator('.agent-messages').evaluate('n=>n.scrollTop')
                page.mouse.move(box['x'] + 40, box['y'] + min(60, box['height'] / 2))
                page.mouse.wheel(0, top - current)
                page.wait_for_timeout(150)
            page.evaluate("window.qaInput=document.querySelector('#agent-input');window.qaMessage=document.querySelector('.agent-message')")
            page.locator('#agent-input').fill('Una escena nueva con Mateo')
            page.locator('#agent-input').evaluate('n=>n.setSelectionRange(4,11)')
            for _ in range(8):
                page.evaluate('renderAssistant()')
            assert page.evaluate("qaInput===document.querySelector('#agent-input') && qaMessage===document.querySelector('.agent-message')")
            assert page.locator('#agent-input').evaluate("n=>document.activeElement===n && n.selectionStart===4 && n.selectionEnd===11 && n.value==='Una escena nueva con Mateo'")
            page.wait_for_function(bottom)
            assert page.locator('.agent-message.user [data-remember]').count() == 24
            assert page.locator('.agent-author .nx-avatar').count() == 48

            # Keyboard send always brings the new turn into view, even from older history.
            read_at(200)
            page.locator('#agent-input').press('Control+Enter')
            page.wait_for_function('Agent.busy')
            page.wait_for_function(bottom)
            assert len(pending) == 1 and len([m for m in requests if m['args']['op'] == 'send']) == 1
            assert page.locator('#content').get_attribute('class').find('agent-view') >= 0
            assert page.locator('#agent-input').is_enabled()
            page.locator('#agent-input').fill('El siguiente encargo, todavía sin enviar')
            page.evaluate('renderAssistant()')
            assert page.locator('#agent-input').evaluate('n=>document.activeElement===n')
            page.locator('#agent-input').press('Enter')
            assert len(pending) == 1  # No duplicate send while busy.
            route, m = pending.pop()
            route.fulfill(json={'id': m['id'], 'ok': True, 'value': {'settings': {},
                                'result': {'snapshot': snapshot, 'text': 'Respuesta larga.\n' * 100}}})
            page.wait_for_function('!Agent.busy && Agent.items.at(-1).role==="assistant"')
            page.wait_for_function(bottom)
            assert page.locator('#agent-input').input_value() == 'El siguiente encargo, todavía sin enviar'

            # Read history: a growing response must not pull the reader to the bottom.
            read_at(700)
            before = page.locator('.agent-messages').evaluate('n=>n.scrollTop')
            for _ in range(8):
                page.evaluate(r"Agent.items.at(-1).text+='Otro párrafo.\n';renderAssistant()")
            page.wait_for_timeout(150)
            assert abs(page.locator('.agent-messages').evaluate('n=>n.scrollTop') - before) < 2
            assert page.locator('[data-agent-latest]').is_visible()

            # Media arriving above the reader should preserve the visible message anchor.
            page.evaluate("""()=>{
              const plan={id:'delayed-result',ref:{providerId:'google_flow',modelId:'google-flow/nano-pro'},
                modelName:'Flow',modality:'IMAGE',prompt:'Referencia',params:{count:'1',aspect:'16:9'},effectiveParams:{count:'1',aspect:'16:9'}};
              Agent.snapshot.plans=[{state:'SUBMITTED',plan}];
              state.jobs.push({id:'delayed-job',agentPlanId:plan.id,providerId:'google_flow',modality:'IMAGE',status:'PROCESSING',outputs:[]});
              Agent.cardAnchors.set('batch:native:'+plan.id,Agent.items[0]);renderAssistant();
            }""")
            page.wait_for_timeout(150)
            page.evaluate("""()=>{
              const n=document.querySelector('.agent-messages'),edge=n.getBoundingClientRect().top;
              window.qaAnchor=[...n.children].find(c=>c.getBoundingClientRect().bottom>edge+1);
              window.qaOffset=qaAnchor.getBoundingClientRect().top-edge;
              state.jobs.at(-1).outputs=['/media/'+'b'.repeat(32)+'.png'];refreshJobViews(['delayed-job']);
              document.querySelector('.agent-flow-media img').loading='eager';
            }""")
            page.wait_for_timeout(250)
            assert delayed_media
            delayed_media.pop().fulfill(content_type='image/svg+xml', body='<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="900"><rect width="1600" height="900" fill="#233842"/></svg>')
            page.wait_for_function("document.querySelector('.agent-flow-media img').naturalWidth>0")
            page.wait_for_timeout(250)
            assert page.evaluate("Math.abs(qaAnchor.getBoundingClientRect().top-document.querySelector('.agent-messages').getBoundingClientRect().top-qaOffset)<3")

            # Resume following explicitly and keep decoded media during further status changes.
            page.locator('[data-agent-latest]').click()
            page.wait_for_function(bottom)
            page.evaluate("window.qaMedia=document.querySelector('.agent-flow-media img');state.jobs.at(-1).status='COMPLETED';refreshJobViews(['delayed-job'])")
            assert page.evaluate("qaMedia===document.querySelector('.agent-flow-media img')")
            page.wait_for_function(bottom)
            page.locator('#agent-input').fill('Primera línea')
            page.locator('#agent-input').press('Shift+Enter')
            page.locator('#agent-input').press_sequentially('Segunda línea')
            assert '\n' in page.locator('#agent-input').input_value()

            # A response must respect a reader who scrolls up while it is pending.
            page.locator('#agent-input').press('Enter')
            page.wait_for_function('Agent.busy')
            page.evaluate("state.jobs.at(-1).status='PROCESSING';refreshJobViews(['delayed-job'])")
            assert page.locator('.agent-flow-status').inner_text() != 'Completado'
            read_at(800)
            route, m = pending.pop()
            route.fulfill(json={'id': m['id'], 'ok': True, 'value': {'settings': {},
                                'result': {'snapshot': snapshot, 'text': 'Terminado.\n' * 70}}})
            page.wait_for_function('!Agent.busy && Agent.items.at(-1).text.startsWith("Terminado")')
            page.wait_for_timeout(200)
            assert abs(page.locator('.agent-messages').evaluate('n=>n.scrollTop') - 800) < 3

            # A failed transport conserves both the sent request and a newer draft.
            page.locator('#agent-input').fill('Mensaje cuyo envío falla')
            page.locator('#agent-input').press('Enter')
            page.wait_for_function('Agent.busy')
            page.locator('#agent-input').fill('Borrador posterior intacto')
            route, m = pending.pop()
            route.fulfill(json={'id': m['id'], 'ok': False, 'error': 'Fallo de transporte simulado'})
            page.wait_for_function('!!Agent.error && !Agent.busy')
            assert page.locator('#agent-input').input_value() == 'Borrador posterior intacto'
            assert page.locator('.agent-message').last.inner_text().find('Mensaje cuyo envío falla') >= 0

            for width, height in [(1920, 1080), (1280, 600), (480, 850)]:
                page.set_viewport_size({'width': width, 'height': height})
                page.locator('#agent-input').fill('Borrador de prueba\n' * 6)
                page.wait_for_timeout(250)
                page.wait_for_function(bottom)
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
                box = page.locator('.agent-messages').bounding_box()
                compose = page.locator('#agent-compose').bounding_box()
                assert box['height'] > 90 and box['y'] + box['height'] <= compose['y'] + 1, (width, height, box, compose)
                assert compose['y'] + compose['height'] <= height + 1
                page.screenshot(path=str(OUT / f'chat-{width}.png'))
            page.locator('[data-agent="new"]').click()
            page.wait_for_function('Agent.items.length===0 && !Agent.busy')
            assert page.locator('#agent-input').input_value() == ''
            assert page.locator('.agent-flow-result').count() == 0
            assert not errors, errors
            browser.close()
        report = {'pass': True, 'stableEditorAndCaret': True, 'followNewReplies': True,
                  'readingPositionPreserved': True, 'lateMediaAnchorPreserved': True,
                  'draftWhileBusy': True, 'keyboardAndFailureRecovery': True,
                  'numericStringQuotes': True, 'viewports': [1920, 1280, 480], 'javascriptErrors': errors, 'paidCalls': 0}
        (OUT / 'verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        print(json.dumps(report))
    finally:
        host.terminate()
        host.wait(timeout=15)
