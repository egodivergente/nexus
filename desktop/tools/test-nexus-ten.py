"""NEXUS 10 presentation layer: hierarchy, navigation, Crear, Modelos, Asistente, states and Ajustes.
Real Edge + offline host + disposable profile. No generation, no paid call and no network beyond the local host."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[2]
DESKTOP = Path(os.environ.get('NEXUS_TEST_DESKTOP', str(ROOT / 'desktop')))
OUT = Path(os.environ.get('NEXUS_TEST_OUTPUT', str(ROOT / 'outputs/nexus-ten-20261025')))
OUT.mkdir(parents=True, exist_ok=True)
VIEWS = ['assistant', 'canvas', 'catalog', 'history', 'library', 'settings']
checks, errors, blocked, allowed, remote = [], [], [], [], []
state = {'allowAgent': False}


def check(name, value, detail=''):
    if not value:
        raise AssertionError(f'{name} {detail}')
    checks.append(name)
    print('PASS', name, flush=True)


FIXTURE = """()=>{
 AccountBalances.values={atlas_cloud:{value:4.0791,currency:'USD',time:Date.now()}};
 Agent.draft='';Agent.attachments=[];
 Agent.items=[{id:'u1',role:'user',text:'hey',failed:true},{id:'u2',role:'user',text:'hola'},
  {id:'a1',role:'assistant',text:'Hola. ¿Qué quieres crear?',usage:'18128 entrada · 33 salida'}];
}"""

with tempfile.TemporaryDirectory(prefix='nexus-ten-', dir=Path.home()/'.aki/tmp') as temp:
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    with (OUT/'host.log').open('w', encoding='utf-8') as log:
        host = subprocess.Popen([sys.executable, '-u', str(DESKTOP/'local_host.py'),
            '--headless', '--offline-qa', '--data-dir', temp],
            stdout=subprocess.PIPE, stderr=log, text=True, env=env,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            url = json.loads(host.stdout.readline())['url']
            with sync_playwright() as pw:
                browser = pw.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width':1920, 'height':1080})
                page.on('pageerror', lambda error: errors.append(str(error)))

                def guard(route):
                    if urlparse(route.request.url).netloc != urlparse(url).netloc:
                        remote.append(route.request.url)
                        route.abort()  # offline QA must never reach a CDN
                    elif urlparse(route.request.url).path == '/rpc':
                        message = route.request.post_data_json
                        op = message.get('op')
                        sub = (message.get('args') or {}).get('op')
                        external = op in {'request', 'generate', 'upload', 'flowSubmit', 'flowConnect', 'flowQuote', 'nativeOperation'}
                        sending = op == 'agent' and sub in {'send', 'confirm', 'rewritePrompt', 'apply'}
                        if external or sending:
                            (allowed if sending and state['allowAgent'] else blocked).append(f'{op}:{sub}')
                            route.fulfill(json={'id':message['id'], 'ok':False, 'error':'Sin red en la prueba de NEXUS 10'})
                        else:
                            route.continue_()
                    else:
                        route.continue_()

                page.route('**/*', guard)
                page.goto(url)
                page.wait_for_function("()=>!!window.NexusTen&&!!window.NexusChatContinuity&&!!window.CatalogParity&&!!initial&&document.body.classList.contains('nx-ten')", timeout=60000)
                page.wait_for_function("()=>[...document.styleSheets].some(s=>(s.href||'').endsWith('nexus-ten.css')&&s.cssRules.length>50)")
                page.evaluate("()=>document.getElementById('nx-startup-onboarding')?.remove()")
                page.evaluate(FIXTURE)
                check('layer loaded last', page.evaluate("[...document.styleSheets].map(s=>(s.href||'').split('/').pop()).indexOf('nexus-ten.css')>[...document.styleSheets].map(s=>(s.href||'').split('/').pop()).indexOf('agent-parity.css')"))

                # 2 · Type scale and 8 · stable navigation
                positions = {}
                for v in VIEWS:
                    page.evaluate('(v)=>navigate(v)', v)
                    page.wait_for_timeout(150)
                    m = page.evaluate("""()=>{const t=getComputedStyle(document.querySelector('#title'));const s=document.querySelector('#studio-settings').getBoundingClientRect();
                      const side=document.querySelector('.sidebar').getBoundingClientRect();const tools=document.querySelectorAll('#header-actions #nx-tools');
                      return {title:parseFloat(t.fontSize),weight:+t.fontWeight,body:parseFloat(getComputedStyle(document.body).fontSize),tools:tools.length,
                        toolsVisible:!!tools[0]&&tools[0].getBoundingClientRect().width>0,settings:[Math.round(s.x),Math.round(s.y)],gap:Math.round(side.bottom-s.bottom),
                        oldTools:!!document.querySelector('#parity-library-tools')&&document.querySelector('#parity-library-tools').getBoundingClientRect().width>0,
                        subtitle:document.querySelector('#eyebrow')?.getBoundingClientRect().height>0}}""")
                    check(f'{v}: title 24-28px and heavier than controls', 24 <= m['title'] <= 28 and m['weight'] >= 600, m)
                    check(f'{v}: body text 15px', abs(m['body']-15) < .5, m)
                    check(f'{v}: one Herramientas menu in the header', m['tools'] == 1 and m['toolsVisible'], m)
                    check(f'{v}: no separate library tool row', not m['oldTools'], m)
                    check(f'{v}: readable subtitle', m['subtitle'], m)
                    check(f'{v}: Ajustes pinned to the bottom of the sidebar', 0 <= m['gap'] <= 80, m)
                    positions[v] = m['settings']
                check('Ajustes keeps the same position in every section', len({tuple(p) for p in positions.values()}) == 1, positions)
                check('nav says Crear', page.locator('#navigation [data-nav=canvas]').get_attribute('aria-label') == 'Crear')

                # 19 · Vocabulary: no leftovers across views
                version = page.evaluate('desktopVersionLabel()')
                for v in VIEWS:
                    page.evaluate('(v)=>navigate(v)', v)
                    page.wait_for_timeout(120)
                    text = page.evaluate('document.body.innerText')
                    for stale in ['Lienzo', 'motores configurados', 'Elegir motor', 'Multi-Run']:
                        check(f'{v}: no «{stale}»', stale not in text)
                    if v != 'settings':
                        check(f'{v}: build number only in Acerca de', version not in text)

                # Menus: one at a time, Escape and outside click close them
                page.evaluate("navigate('catalog')")
                page.locator('#nx-tools > summary').click()
                expect(page.locator('#nx-tools .nx-menu-panel')).to_be_visible()
                check('tools menu lists local tools', page.locator('#nx-tools [data-parity]').count() == 4)
                page.locator('.nx-filter-menu > summary').click()
                check('only one menu open', page.evaluate("document.querySelectorAll('.nx-menu[open]').length") == 1)
                page.keyboard.press('Escape')
                check('Escape closes the menu', page.evaluate("document.querySelectorAll('.nx-menu[open]').length") == 0)
                page.locator('#nx-tools > summary').click()
                page.locator('#title').click()
                check('outside click closes the menu', page.evaluate("document.querySelectorAll('.nx-menu[open]').length") == 0)

                # 3, 14, 15 · Crear
                page.evaluate("()=>{loaded=null;state.draft.modelId=null;navigate('canvas');}")
                page.wait_for_selector('.nx-create-grid .nx-stage-frame.is-empty')
                c = page.evaluate("""()=>{const r=s=>document.querySelector(s)?.getBoundingClientRect();const g=document.querySelector('#generate');
                  return {cmd:r('#creative-command'),stage:r('.nx-stage'),label:document.querySelector('.nx-field-label')?.textContent,
                  reason:document.querySelector('#nx-generate-reason')?.textContent,described:g.getAttribute('aria-describedby'),disabled:g.disabled,
                  guideW:r('#studio-start-guide')?.width,badge:[...document.querySelectorAll('#studio-start-guide .nx-version-badge')].length,
                  motor:document.querySelector('.creative-motor')?.textContent,hint:!!document.querySelector('.nx-ref-hint')}}""")
                check('Crear: preview sits beside the composer at 1920', c['stage']['x'] > c['cmd']['x'] + c['cmd']['width'] - 1 and c['stage']['width'] > c['cmd']['width'], c)
                check('Crear: guide is never wider than the composer', not c['guideW'] or c['guideW'] <= c['cmd']['width'] + 1, c)
                check('Crear: no build number in the guide', c['badge'] == 0)
                check('Crear: persistent prompt label', c['label'] == 'Describe tu creación')
                check('Crear: model choice named «Elegir modelo»', (c['motor'] or '').strip() == 'Elegir modelo', c)
                check('Crear: references explained before choosing', c['hint'])
                check('Crear: disabled Generar explains why', c['disabled'] and c['reason'] == 'Elige un modelo para poder generar.' and c['described'] == 'nx-generate-reason', c)
                page.set_viewport_size({'width':1360, 'height':900})
                page.wait_for_timeout(150)
                box = page.locator('#creative-command').bounding_box()
                check('Crear: single centred column at 1360', abs(box['x'] + box['width']/2 - (1360+64)/2) < 15, box)
                page.set_viewport_size({'width':1920, 'height':1080})
                page.evaluate("""()=>{loaded=E.normalize(models.find(m=>m.id==='runway/gen4_image_turbo/image'));state.draft={modelId:loaded.id,prompt:'Una esfera con luz cálida',values:E.defaults(loaded),media:[]};navigate('canvas');}""")
                page.wait_for_timeout(250)
                l = page.evaluate("""()=>{const r=document.querySelector('#nx-generate-reason');const chip=document.querySelector('.creative-chips > .creative-motor');
                  return {reasonHidden:r.hidden,validation:document.querySelector('#validation')?.textContent.trim(),hint:!!document.querySelector('.nx-ref-hint'),
                  chipLeft:chip.getBoundingClientRect().x-chip.parentElement.getBoundingClientRect().x,chipText:getComputedStyle(chip).textOverflow,
                  grid:document.querySelector('.nx-create-grid').getBoundingClientRect().width}}""")
                check('Crear: validation is not repeated by the reason line', l['reasonHidden'] and bool(l['validation']), l)
                check('Crear: reference hint gone once a model is chosen', not l['hint'], l)
                check('Crear: long model names truncate with an ellipsis', l['chipText'] == 'ellipsis' and l['chipLeft'] >= -1, l)
                check('Crear: loaded model keeps the wide composition', l['grid'] > 1200, l)

                # 4, 5, 10, 12, 13, 18 · Modelos
                page.evaluate("()=>{loaded=null;navigate('catalog');}")
                page.wait_for_selector('.nx-intent')
                total = page.locator('#content article.model').count()
                cols = page.evaluate("()=>{const cards=[...document.querySelectorAll('#content article.model')];const y=cards[0].getBoundingClientRect().y;return cards.filter(c=>Math.abs(c.getBoundingClientRect().y-y)<2).length}")
                check('Modelos: three or four columns when comfortable at 1920', 3 <= cols <= 4, cols)
                check('Modelos: intents first', page.locator('.nx-intent').count() >= 4 and page.locator('#nx-intents-title').inner_text() == '¿Qué quieres hacer?')
                check('Modelos: original filter controls hidden', page.evaluate("[...document.querySelectorAll('.nx-orig')].every(n=>n.getBoundingClientRect().width===0)") and page.locator('#sort').count() == 1)
                check('Modelos: no «Precio por confirmar» on cards', page.locator('#content article.model .price', has_text='Precio por confirmar').count() == 0)
                check('Modelos: pending prices offer an estimate', page.locator('#content article.model .nx-price-link').count() > 0)
                sizes = page.evaluate("[...document.querySelectorAll('#content article.model .nx-icon-btn')].slice(0,9).map(b=>{const r=b.getBoundingClientRect();return Math.min(r.width,r.height)})")
                check('Modelos: icon buttons have a 36px hit area', min(sizes) >= 36, sizes)
                marks = page.evaluate("[...document.querySelectorAll('#content article.model')].map(a=>{const m=a.querySelector('.top .nx-mark');return m?(m.querySelector('img.logo')?'logo':(m.classList.contains('is-neutral')&&m.querySelector('svg')&&!m.querySelector('.generic-mark')?'neutral':'bad')):'none'})")
                check('Modelos: every card has a brand logo or the neutral icon, never the generic «N»', marks and all(m in ('logo','neutral') for m in marks) and page.locator('#content article.model .generic-mark').count() == 0, marks)
                check('Modelos: traits on cards', page.locator('#content article.model .nx-traits li').count() >= total)
                page.locator('#content .catalog-header-bar [data-kind=VIDEO]').click()
                page.wait_for_function("()=>document.querySelector('#content article.model .nx-traits')")
                video = page.evaluate("""()=>[...document.querySelectorAll('#content article.model')].map(a=>({name:a.querySelector('h3')?.textContent,logo:a.querySelector('.nx-mark img.logo')?.getAttribute('src')||'',desc:a.querySelector('.description:not([hidden])')?.textContent||'',meta:Math.round(a.getBoundingClientRect().bottom-a.querySelector('.card-meta').getBoundingClientRect().bottom)}))""")
                veo = [v for v in video if v['name'] == 'Google Veo']
                check('Modelos: shipped brand logos are used (Google Veo)', veo and veo[0]['logo'].endswith('logos/google.png'), veo)
                check('Modelos: modality-only sentences are not repeated as descriptions', not any(v['desc'].startswith(('Crear una escena de vídeo desde la descripción', 'Crear un clip desde la descripción')) for v in video), [v['desc'] for v in video if v['desc']])
                rows = {}
                for v in video[:8]:
                    rows.setdefault(v['meta'], []).append(v['name'])
                check('Modelos: price line at the same height on every card', len(rows) == 1, rows)
                page.locator('#content .catalog-header-bar [data-kind=IMAGE]').click()
                page.wait_for_function("()=>document.querySelector('#content .catalog-header-bar [data-kind=IMAGE].active')")
                page.locator('[data-nx-intent=background]').click()
                page.wait_for_selector('[data-nx-clear=intent]')
                filtered = page.locator('#content article.model').count()
                check('Modelos: intent narrows the catalogue', 0 < filtered < total, (filtered, total))
                check('Modelos: intent shows as a removable chip', page.locator('[data-nx-clear=intent]').inner_text().startswith('Objetivo: Eliminar fondo'))
                check('Modelos: intent pressed', page.locator('[data-nx-intent=background]').get_attribute('aria-pressed') == 'true')
                page.locator('[data-nx-clear=intent]').click()
                page.wait_for_timeout(120)
                check('Modelos: removing the chip restores the catalogue', page.locator('#content article.model').count() == total and page.locator('.nx-chip').count() == 0)
                page.locator('.nx-filter-menu > summary').click()
                page.locator('[data-nx-provider=runway]').click()
                page.wait_for_selector('[data-nx-clear=provider]')
                check('Modelos: filter panel stays open while choosing', page.evaluate("document.querySelector('.nx-filter-menu').open"))
                check('Modelos: active filter counter', page.locator('.nx-filter-menu .nx-count').inner_text() == '1')
                page.locator('#title').click()
                page.locator('[data-nx-clear=provider]').click()
                page.wait_for_timeout(120)
                check('Modelos: provider chip clears the filter', page.evaluate('provider') == '' and page.locator('#content article.model').count() == total)
                page.locator('#search').fill('zzzz-no-existe')
                page.wait_for_selector('.nx-empty')
                check('Modelos: empty search offers «Limpiar filtros»', page.locator('.nx-empty [data-nx-clear=all]').is_visible())
                page.locator('.nx-empty [data-nx-clear=all]').click()
                page.wait_for_timeout(150)
                check('Modelos: clearing filters brings the catalogue back', page.locator('#content article.model').count() == total)

                # 6, 7, 19, 20 · Asistente
                page.evaluate(FIXTURE)
                page.evaluate("navigate('assistant')")
                page.wait_for_selector('.cc-retry')
                a = page.evaluate("""()=>{const r=s=>document.querySelector(s).getBoundingClientRect();const hola=document.querySelector('[data-agent-message=u2]').getBoundingClientRect();
                  const msgs=document.querySelector('.agent-messages');const cs=getComputedStyle(msgs);const inner=msgs.getBoundingClientRect().width-parseFloat(cs.paddingLeft)-parseFloat(cs.paddingRight);
                  return {hola:[hola.width,hola.height],compose:r('#agent-compose'),inner,left:msgs.getBoundingClientRect().x+parseFloat(cs.paddingLeft),
                  hint:document.querySelector('.nx-mode-hint')?.textContent,primary:document.querySelector('.agent-toolbar .nx-primary-action')?.textContent,
                  more:document.querySelectorAll('.agent-toolbar .nx-more .nx-menu-item').length,usage:document.querySelector('.cc-usage small')?.textContent}}""")
                check('Asistente: short bubbles fit their text', a['hola'][0] < 140 and a['hola'][1] < 52, a['hola'])
                check('Asistente: reading column about 760px', 700 <= a['inner'] <= 800, a['inner'])
                check('Asistente: composer aligned with the reading column', abs(a['compose']['x'] - a['left']) < 3 and abs(a['compose']['width'] - a['inner']) < 3, a)
                check('Asistente: modes explained', bool(a['hint']))
                check('Asistente: one primary action', a['primary'] == 'Nueva charla' and page.locator('.agent-toolbar button.primary:visible').count() == 1)
                check('Asistente: secondary project tools grouped under «Más»', a['more'] >= 3)
                check('Asistente: token usage folded and readable', a['usage'] == 'Tokens: 18.128 de entrada · 33 de salida', a['usage'])
                check('Asistente: usage hidden until asked', not page.locator('.cc-usage small').first.is_visible())
                check('Asistente: failed send shows Reintentar beside the notice', page.locator('[data-agent-message=u1] .cc-failed .cc-retry').inner_text() == 'Reintentar')
                page.evaluate("()=>{Agent.draft='Otro borrador';renderAssistant();}")
                page.locator('[data-agent-message=u1] .cc-retry').click()
                page.wait_for_timeout(200)
                check('Asistente: retry never overwrites a different draft', page.evaluate("Agent.draft") == 'Otro borrador' and page.evaluate("Agent.items.some(i=>i.id==='u1'&&i.failed)"))
                # Offline: sendAgent returns before sending, so the failed message must stay with its Reintentar.
                page.evaluate("()=>{window.__ready=ensureAssistantReady;ensureAssistantReady=async()=>{throw Error('Sin conexión de prueba');};Agent.snapshot={offline:true,mode:'ASSISTANT',index:{projects:[],assets:[]},skills:[],plans:[],proposals:[]};Agent.draft='';renderAssistant();}")
                page.locator('[data-agent-message=u1] .cc-retry').click()
                page.wait_for_timeout(300)
                offline = page.evaluate("()=>({kept:Agent.items.filter(i=>i.id==='u1'&&i.failed).length,draft:Agent.draft,retry:[...document.querySelectorAll('[data-agent-message=u1] .cc-retry')].map(b=>b.disabled)})")
                page.evaluate("()=>{ensureAssistantReady=window.__ready;}")
                check('Asistente: offline retry keeps the failed message', offline['kept'] == 1 and offline['draft'] == '', offline)
                check('Asistente: offline retry leaves Reintentar usable', offline['retry'] == [False], offline)
                # An online snapshot so the retry walks the real send path; the guard answers the send with an error.
                page.evaluate("()=>{Agent.snapshot={offline:false,mode:'ASSISTANT',index:{projects:[],assets:[]},skills:[],plans:[],proposals:[]};Agent.draft='';renderAssistant();}")
                state['allowAgent'] = True
                page.locator('[data-agent-message=u1] .cc-retry').click()
                page.wait_for_timeout(600)
                state['allowAgent'] = False
                kept = page.evaluate("Agent.items.filter(i=>i.role==='user'&&i.text==='hey').map(i=>!!i.failed)")
                check('Asistente: retry resends through the normal path', any(op.startswith('agent:send') for op in allowed), allowed)
                # sendAgent marks a failed send and restores the draft: one entry, flagged again, text still in the composer.
                check('Asistente: a failed retry keeps a single entry', kept == [True], kept)
                check('Asistente: a failed retry offers Reintentar again', page.locator('.cc-failed .cc-retry').count() == 1)
                check('Asistente: the text stays in the composer', page.evaluate("String(Agent.draft).trim()") == 'hey')

                # 9 · Empty states
                page.evaluate("()=>{state.jobs=[];navigate('history');}")
                page.wait_for_selector('.nx-empty[data-empty=first]')
                check('Historial: first-run start', page.locator('.nx-empty [data-nav=canvas]').inner_text() == 'Crear primera imagen')
                page.evaluate("()=>{state.jobs=[{id:'j1',name:'Retrato',modelId:'sample',providerId:'atlas_cloud',prompt:'Retrato con luz suave',modality:'IMAGE',status:'COMPLETED',outputs:[],parameters:{}}];navigate('history');}")
                page.locator('#parity-history-search').fill('zzzz')
                page.wait_for_selector('.nx-empty[data-empty=filtered]')
                check('Historial: filtered empty is distinct', page.locator('.nx-empty h2').inner_text() == 'No hay resultados para esta búsqueda')
                page.locator('[data-nx-clear-history]').click()
                page.wait_for_function("()=>!document.querySelector('.nx-empty')&&document.querySelector('#parity-history-search').value===''")
                checks.append('Historial: «Limpiar filtros» restores the list')
                page.locator('#parity-history-search').fill('retrato')
                page.evaluate("navigate('settings')")
                page.wait_for_timeout(400)
                check('Historial: a pending search never paints over another section', page.locator('#settings-container').count() == 1 and page.locator('.history-header-bar').count() == 0)
                page.evaluate("()=>{state.jobs=[];navigate('library');}")
                page.wait_for_selector('.nx-empty[data-empty=first]')
                check('Biblioteca: first-run start', page.locator('.nx-empty [data-nav=canvas]').is_visible())

                # 16, 17 · Ajustes
                page.evaluate("navigate('settings')")
                page.wait_for_selector('.nx-settings')
                check('Ajustes: five categories', page.locator('.nx-settings-section').count() == 5 and page.locator('[data-nx-settings-jump]').count() == 5)
                themes = page.evaluate("[...document.querySelectorAll('.interface-themes button')].map(b=>{const r=b.getBoundingClientRect(),p=b.querySelector('.theme-preview').getBoundingClientRect(),n=[...b.children].find(c=>c.tagName==='SPAN'&&!c.className);return [r.height,p.height,n?n.getBoundingClientRect().width:0]})")
                check('Ajustes: complete theme thumbnails with names', all(h >= 100 and p >= 70 and n > 20 for h, p, n in themes), themes)
                page.locator('.interface-themes [data-interface-theme=ocean]').click()
                check('Ajustes: unmistakable selection', page.locator('.interface-themes [data-interface-theme=ocean]').get_attribute('aria-pressed') == 'true' and page.locator('.interface-themes [data-interface-theme=ocean] .theme-selected').is_visible())
                page.wait_for_function("()=>!/Guardando|pendientes/.test(document.querySelector('#studio-save-state')?.textContent||'')", timeout=8000)
                page.locator('.interface-themes [data-interface-theme=aurora]').click()
                edges = page.evaluate("[...document.querySelectorAll('#nx-settings-agent .nx-row-controls')].map(c=>Math.round(c.getBoundingClientRect().right))")
                check('Ajustes: actions share one column', len(edges) >= 3 and max(edges) - min(edges) <= 2, edges)
                check('Privacidad: offline QA made no remote request', not remote, remote[:3])
                toggle = page.locator('#nx-settings-data [data-nx-examples]')
                check('Ajustes: provider examples off in offline QA', toggle.get_attribute('aria-pressed') == 'false' and toggle.inner_text() == 'Desactivados')
                toggle.click()
                check('Ajustes: provider examples can be enabled', page.locator('#nx-settings-data [data-nx-examples]').get_attribute('aria-pressed') == 'true' and page.evaluate('state.catalogExamples===true'))
                page.locator('#nx-settings-data [data-nx-examples]').click()
                check('Ajustes: and disabled again', page.evaluate('state.catalogExamples===false'))
                check('Ajustes: version lives in Acerca de', bool(page.locator('#nx-settings-about [data-nx-version]').inner_text().strip()))

                # 1, 11 · No overflow and reduced motion
                for width, height in [(1920,1080), (1360,900), (1024,768), (760,900), (480,850)]:
                    page.set_viewport_size({'width':width, 'height':height})
                    for v in VIEWS:
                        page.evaluate('(v)=>navigate(v)', v)
                        page.wait_for_timeout(90)
                        check(f'{v} fits {width}px', page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
                        if width in (1920, 480):
                            page.screenshot(path=str(OUT/f'{v}-{width}.png'))
                page.set_viewport_size({'width':1360, 'height':900})
                page.evaluate("navigate('assistant')")
                page.emulate_media(reduced_motion='reduce')
                check('reduced motion respected', page.locator('#agent-compose').evaluate('(n)=>getComputedStyle(n).transitionDuration') == '0s')
                check('no JavaScript errors', not errors, errors)
                check('no remote request during the whole run', not remote, remote[:3])
                check('no external operations attempted', not blocked, blocked)
                browser.close()
        finally:
            host.terminate()
            host.wait(timeout=10)
result = {'status':'PASS', 'count':len(checks), 'checks':checks, 'errors':errors, 'blockedOperations':blocked,
          'allowedAgentRetry':allowed, 'profile':'isolated', 'desktop':str(DESKTOP)}
(OUT/'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), 'utf-8')
print(json.dumps({k:v for k, v in result.items() if k != 'checks'}, ensure_ascii=False))
