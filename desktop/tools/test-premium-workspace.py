"""Exercise desktop workspace journeys in Edge with an isolated offline profile.

This deliberately uses the real application and host. Provider operations and all
non-loopback traffic are blocked; no user's profile, projects or credentials load.
Set NEXUS_TEST_DESKTOP to validate an installed package instead of the checkout.
NEXUS_TEST_FOCUSED=1 skips the layout matrix; NEXUS_TEST_VIEWS limits that matrix
to a comma-separated subset when verifying a specific screen after a change.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[2]
DESKTOP = Path(os.environ.get('NEXUS_TEST_DESKTOP', str(ROOT / 'desktop')))
OUT = Path(os.environ.get('NEXUS_TEST_OUTPUT', str(ROOT / 'outputs/premium-workspace')))
VIEWS = ('assistant', 'canvas', 'catalog', 'history', 'library', 'settings', 'editor')
MATRIX_VIEWS = tuple(v for v in os.environ.get('NEXUS_TEST_VIEWS', ','.join(VIEWS)).split(',') if v in VIEWS)
checks, errors, remote, blocked, layouts, activity = [], [], [], [], [], []


def check(name, passed, detail=None):
    result = {'name': name, 'passed': bool(passed)}
    if detail is not None:
        result['detail'] = detail
    checks.append(result)
    print(('PASS ' if passed else 'FAIL ') + name, flush=True)


LAYOUT = """()=>{
 const visible=n=>{const r=n.getBoundingClientRect(),s=getComputedStyle(n),closed=n.closest('details:not([open])');return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&(!closed||closed.querySelector(':scope>summary')?.contains(n))};
 const outside=[...document.querySelectorAll('main button,main input,main textarea,main select,main summary,main h1,main h2,main h3')].filter(visible).map(n=>{const r=n.getBoundingClientRect();return {tag:n.tagName,id:n.id,text:(n.getAttribute('aria-label')||n.textContent||'').trim().slice(0,65),left:Math.round(r.left),right:Math.round(r.right),width:Math.round(r.width)}}).filter(r=>r.right>innerWidth+2||r.left< -2);
 return {width:innerWidth,scrollWidth:document.documentElement.scrollWidth,outside,
 title:document.querySelector('#title')?.textContent,
 titleSize:getComputedStyle(document.querySelector('#title')).fontSize,
 content:document.querySelector('#content')?.innerText.slice(0,180),
 background:getComputedStyle(document.body).backgroundColor,
 fontScale:getComputedStyle(document.body).getPropertyValue('--ui-font-scale'),
 focus:document.activeElement?.tagName,
 canvasNav:document.querySelector('#navigation [data-nav=canvas]')?.outerHTML};
}"""


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='nexus-premium-') as temp:
        # Small local fixture for contrast/focus journeys only. Real image/video
        # provenance and playback are covered by test-gallery-previews.py.
        fixture_media = Path(temp) / 'media' / ('f' * 32 + '.png')
        fixture_media.parent.mkdir(parents=True, exist_ok=True)
        fixture_media.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j+XkAAAAASUVORK5CYII='))
        env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
        with (OUT / 'host.log').open('w', encoding='utf-8') as log:
            host = subprocess.Popen(
                [sys.executable, '-u', str(DESKTOP / 'local_host.py'), '--headless',
                 '--offline-qa', '--data-dir', temp], stdout=subprocess.PIPE,
                stderr=log, text=True, env=env,
                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            try:
                url = json.loads(host.stdout.readline())['url']
                with sync_playwright() as pw:
                    browser = pw.chromium.launch(channel='msedge', headless=True)
                    page = browser.new_page(viewport={'width': 1280, 'height': 720})
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    requests = []
                    faults = {'save': False}

                    def guard(route):
                        parsed = urlparse(route.request.url)
                        if parsed.netloc != urlparse(url).netloc:
                            remote.append(route.request.url)
                            return route.abort()
                        if parsed.path == '/media/' + 'f' * 32 + '.png':
                            return route.fulfill(content_type='image/png', body=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j+XkAAAAASUVORK5CYII='))
                        if parsed.path == '/rpc':
                            message = route.request.post_data_json
                            op, args = message.get('op'), message.get('args') or {}
                            requests.append(op + ':' + str(args.get('op', '')))
                            if op == 'save' and faults['save']:
                                faults['save'] = False
                                return route.fulfill(json={'id': message['id'], 'ok': False,
                                                           'error': 'Guardado rechazado para probar la recuperación.'})
                            denied = op in {'request', 'generate', 'upload', 'flowSubmit',
                                            'flowConnect', 'flowQuote', 'nativeOperation'}
                            denied |= op == 'agent' and args.get('op') in {'send', 'confirm', 'apply'}
                            denied |= op in {'driveSync', 'deviceContinuity'} and args.get('op') not in {'status', 'diagnostics'}
                            if denied:
                                blocked.append(op + ':' + str(args.get('op', '')))
                                return route.fulfill(json={'id': message['id'], 'ok': False,
                                                           'error': 'Acción externa bloqueada por la prueba.'})
                        route.continue_()

                    page.route('**/*', guard)
                    started = time.monotonic()
                    page.goto(url)
                    page.wait_for_function("()=>!!window.NexusTen&&!!initial&&document.body.classList.contains('nx-ten')", timeout=60000)
                    activity.append({'startupSeconds': round(time.monotonic() - started, 3)})
                    page.evaluate("()=>document.querySelector('#nx-startup-onboarding')?.remove()")

                    matrix = [] if os.environ.get('NEXUS_TEST_FOCUSED') else [(1280, 720, 1), (1920, 1080, 1), (768, 900, 1), (1280, 720, 2)]
                    for width, height, scale in matrix:
                        page.set_viewport_size({'width': width, 'height': height})
                        for theme in ('aurora', 'light'):
                            page.evaluate("({theme,scale})=>{state.interfaceTheme=theme;state.fontScale=scale;document.body.style.setProperty('--ui-font-scale',String(scale));window.NexusThemes?.apply();}", {'theme': theme, 'scale': scale})
                            scheme = page.evaluate("getComputedStyle(document.body).colorScheme")
                            check(f'{theme} uses matching native-control color scheme', ('light' if theme == 'light' else 'dark') in scheme, scheme)
                            for view in MATRIX_VIEWS:
                                page.evaluate('(v)=>navigate(v)', view)
                                page.wait_for_timeout(160)
                                data = page.evaluate(LAYOUT)
                                data.update(view=view, theme=theme, scale=scale)
                                layouts.append(data)
                                label = f'{view} {theme} {width}px text {scale*100:.0f}%'
                                check(label + ': viewport fits', data['scrollWidth'] <= width + 1, data['outside'][:6])
                                check(label + ': controls stay reachable', not data['outside'], data['outside'][:6])
                                check(label + ': content renders', bool(data['content']))
                                if width == 1280 and ((scale == 1 and view in ('assistant', 'canvas', 'catalog', 'settings', 'editor')) or (scale == 2 and view in ('settings', 'editor'))):
                                    page.screenshot(path=str(OUT / f'{view}-{theme}-{width}-{scale}.png'))

                    # Real mouse/keyboard interactions, without provider requests.
                    page.set_viewport_size({'width': 1280, 'height': 720})
                    page.evaluate("()=>{state.fontScale=1;document.body.style.setProperty('--ui-font-scale','1');navigate('catalog');}")
                    page.wait_for_selector('#nx-tools > summary')
                    page.locator('#nx-tools > summary').click()
                    check('Tools opens', page.locator('#nx-tools').get_attribute('open') is not None)
                    page.keyboard.press('Escape')
                    check('Escape closes Tools', page.locator('#nx-tools').get_attribute('open') is None)
                    search = page.locator('#search')
                    search.fill('zzzz-no-result-premium-qa')
                    page.wait_for_selector('.nx-empty')
                    check('Search has useful empty-state recovery', page.locator('.nx-empty [data-nx-clear=all]').is_visible())
                    page.locator('.nx-empty [data-nx-clear=all]').click()
                    check('Clear search restores models', page.locator('article.model').count() > 0)
                    page.keyboard.press('Tab')
                    focus = page.evaluate("()=>{const n=document.activeElement,s=getComputedStyle(n),r=n.getBoundingClientRect();return {tag:n.tagName,width:r.width,outline:s.outlineStyle,outlineWidth:s.outlineWidth,shadow:s.boxShadow}}")
                    check('Keyboard focus reaches a visible control', focus['tag'] not in ('BODY', 'HTML') and focus['width'] > 0, focus)

                    page.evaluate("navigate('canvas')")
                    page.wait_for_selector('#prompt')
                    draft = 'Borrador conservado al cambiar de pantalla — prueba local'
                    page.locator('#prompt').fill(draft)
                    page.locator('#navigation [data-nav=catalog]').click()
                    page.locator('#navigation [data-nav=canvas]').click()
                    check('Canvas draft survives navigation', page.locator('#prompt').input_value() == draft)
                    page.wait_for_function("()=>SaveStatus.status==='saved'")
                    faults['save'] = True
                    page.locator('#prompt').fill(draft + ' modificado')
                    page.wait_for_function("()=>SaveStatus.status==='error'")
                    check('Save failure is shown and retains the draft', page.locator('#studio-save-retry').is_visible() and page.locator('#prompt').input_value() == draft + ' modificado')
                    page.locator('#studio-save-retry').click()
                    page.wait_for_function("()=>SaveStatus.status==='saved'")
                    check('Save retry recovers without changing the draft', page.locator('#prompt').input_value() == draft + ' modificado')

                    page.evaluate("navigate('catalog')")
                    late = page.evaluate("()=>{const before=document.querySelector('#content').innerHTML;renderCorte();return {same:document.querySelector('#content').innerHTML===before,view};}")
                    check('Late editor render leaves the current section intact', late['same'] and late['view'] == 'catalog', late)
                    deferred = page.evaluate("""async()=>{
                     const ready=ensureAssistantReady,prior=Agent.snapshot,loadedBefore=Corte.loaded;
                     let release;ensureAssistantReady=()=>new Promise(resolve=>release=resolve);
                     Agent.snapshot={projectId:null,mode:'ASSISTANT',index:{projects:[],assets:[]},skills:[],plans:[],proposals:[]};
                     Corte.loaded=false;Corte.loading=false;navigate('editor');navigate('canvas');
                     release();await Promise.resolve();await Promise.resolve();
                     const result={view,prompt:document.querySelector('#prompt')?.value,editor:!!document.querySelector('.corte-layout')};
                     ensureAssistantReady=ready;Agent.snapshot=prior;Corte.loaded=loadedBefore;Corte.loading=false;return result;
                    }""")
                    check('Delayed editor initialization preserves the new view and draft', deferred['view'] == 'canvas' and deferred['prompt'] == draft + ' modificado' and not deferred['editor'], deferred)

                    # A normal local modal must trap keyboard focus, close with Escape,
                    # and return focus to its trigger. Opening does not refresh balances.
                    page.locator('#parity-balance-chip').click()
                    page.wait_for_selector('#dialog[open]')
                    check('Local dialog has an accessible name', bool(page.locator('#dialog').get_attribute('aria-labelledby') or page.locator('#dialog').get_attribute('aria-label')))
                    page.keyboard.press('Tab')
                    check('Modal keeps keyboard focus inside', page.evaluate("document.querySelector('#dialog').contains(document.activeElement)"))
                    page.keyboard.press('Escape')
                    check('Escape closes the dialog', not page.locator('#dialog').is_visible())
                    check('Closing dialog restores trigger focus', page.evaluate("document.activeElement?.id==='parity-balance-chip'"))

                    page.evaluate("navigate('assistant')")
                    page.locator('#agent-input').focus()
                    page.keyboard.press('ArrowRight')
                    focus_style = page.locator('#agent-input').evaluate('(n)=>({style:getComputedStyle(n).outlineStyle,width:parseFloat(getComputedStyle(n).outlineWidth)})')
                    check('Assistant text field has a visible keyboard focus ring', focus_style['style'] == 'solid' and focus_style['width'] >= 2, focus_style)
                    page.emulate_media(reduced_motion='reduce')
                    motion = page.locator('#agent-compose').evaluate('(n)=>getComputedStyle(n).transitionDuration')
                    check('Reduced motion removes composer transitions', all(float(v.strip().rstrip('s')) <= .01 for v in motion.split(',')), motion)

                    page.evaluate("()=>{for(const v of ['canvas','catalog','settings','editor','assistant','canvas'])navigate(v);}")
                    page.wait_for_timeout(150)
                    # The presentation enhancer runs on the next frame. Recheck
                    # every stable navigation after giving that frame a chance.
                    nav = []
                    for destination in ('catalog', 'settings', 'editor', 'assistant', 'canvas'):
                        page.evaluate('(v)=>navigate(v)', destination)
                        page.wait_for_timeout(100)
                        nav.append(page.evaluate("()=>{const n=document.querySelector('#navigation [data-nav=canvas]');return {view,label:!!n?.querySelector('.creative-nav-label'),raw:n?[...n.childNodes].some(c=>c.nodeType===3&&c.textContent.trim()):true};}"))
                    check('Compact navigation keeps structured labels after repeated navigation', all(n['label'] and not n['raw'] for n in nav), nav)

                    contrast = """n=>{const c=getComputedStyle(n),ctx=document.createElement('canvas').getContext('2d');const pixel=v=>{ctx.clearRect(0,0,1,1);ctx.fillStyle=v;ctx.fillRect(0,0,1,1);return [...ctx.getImageData(0,0,1,1).data]};const lum=v=>{const rgb=pixel(v).slice(0,3).map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4});return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722};let p=n,bg=c.backgroundColor;while(p&&pixel(bg)[3]===0){p=p.parentElement;if(p)bg=getComputedStyle(p).backgroundColor;}const a=lum(c.color),b=lum(bg);return {color:c.color,background:bg,ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)}}"""
                    for theme in ('aurora', 'light'):
                        entry_id = page.evaluate("async theme=>{state.interfaceTheme=theme;window.NexusThemes.apply();state.jobs=[{id:'premium-gallery-fixture',name:'Referencia local de prueba',modelId:'local/source',providerId:'local_media',prompt:'Prueba aislada de galería',modality:'IMAGE',status:'COMPLETED',startedAt:1,outputs:['/media/'+('f'.repeat(32))+'.png'],parameters:{}}];PC.search='';PC.kind='';PC.provider='';PC.status='';Studio.tab='results';await flush();navigate('library');await NexusGallery.refresh();return NexusGallery.items.find(entry=>entry.identityKeys.includes('job:premium-gallery-fixture:0'))?.id;}", theme)
                        check(f'{theme} gallery exposes the canonical file identity', bool(entry_id))
                        page.wait_for_selector('.gallery-piece')
                        page.locator('[data-gallery-select="' + entry_id + '"]').click()
                        check(f'{theme} canonical file selection is active', page.locator('[data-gallery-select="' + entry_id + '"]').get_attribute('aria-pressed') == 'true')
                        selection = page.locator('.gallery-selection').evaluate(contrast)
                        check(f'{theme} gallery selection remains readable', selection['ratio'] >= 4.5, selection)
                        page.locator('.gallery-filters > summary').click()
                        check(f'{theme} gallery filters open and fit', page.locator('.gallery-filters > div').is_visible() and page.locator('.gallery-filters > div').bounding_box()['x'] >= 0)
                        page.screenshot(path=str(OUT / f'gallery-{theme}-selection-filters.png'))
                        page.locator('.gallery-filters > summary').click()
                        page.locator('[data-gallery=clear]').click()
                        check(f'{theme} clearing selection removes its action bar', page.locator('.gallery-selection').count() == 0 and page.locator('[data-gallery-select="' + entry_id + '"]').get_attribute('aria-pressed') == 'false')
                        page.locator('.gallery-media').click()
                        page.wait_for_selector('.gallery-lightbox')
                        viewer = page.locator('.gallery-viewer-top').evaluate(contrast)
                        check(f'{theme} gallery viewer controls remain readable', viewer['ratio'] >= 4.5, viewer)
                        page.screenshot(path=str(OUT / f'gallery-{theme}-viewer.png'))
                        page.keyboard.press('Escape')
                        check(f'{theme} gallery viewer closes with Escape', page.locator('.gallery-lightbox').count() == 0)

                    page.evaluate("()=>{state.jobs=[];navigate('assistant');}")
                    text_color = page.locator('#content .agent-empty h2').evaluate('(n)=>({heading:getComputedStyle(n).color,text:getComputedStyle(document.body).color})')
                    check('Light assistant welcome uses the theme text color', text_color['heading'] == text_color['text'], text_color)

                    # Measure actual settled idle activity, not a source-code estimate.
                    page.emulate_media(reduced_motion='no-preference')
                    page.wait_for_function('()=>!Agent.busy&&!Agent.callbacks.size')
                    page.evaluate('async()=>{await flush();}')
                    page.wait_for_timeout(600)
                    page.evaluate("()=>{window.__premiumMutations=0;window.__premiumTargets={};window.__premiumObserver=new MutationObserver(rs=>{window.__premiumMutations+=rs.length;for(const r of rs){const n=r.target.nodeType===3?r.target.parentElement:r.target;const key=(n.id?'#'+n.id:n.tagName+'.'+String(n.className).replaceAll(' ','.'))+':'+r.type+(r.attributeName?':'+r.attributeName:'');window.__premiumTargets[key]=(window.__premiumTargets[key]||0)+1;}});window.__premiumObserver.observe(document.body,{subtree:true,childList:true,attributes:true,characterData:true});}")
                    session = page.context.new_cdp_session(page)
                    session.send('DOM.enable')
                    session.send('CSS.enable')
                    document = session.send('DOM.getDocument')
                    heading_id = session.send('DOM.querySelector', {'nodeId': document['root']['nodeId'], 'selector': '#content .agent-empty h2'})['nodeId']
                    rendered_fonts = session.send('CSS.getPlatformFontsForNode', {'nodeId': heading_id})['fonts']
                    families = page.evaluate("()=>Object.fromEntries(['body','button','#agent-input','#title','#content .agent-empty h2'].map(s=>[s,getComputedStyle(document.querySelector(s)).fontFamily]))")
                    activity.append({'computedFonts': families, 'renderedHeadingFonts': rendered_fonts})
                    check('Assistant heading renders a Windows UI font', bool(rendered_fonts) and all('Times' not in f['familyName'] for f in rendered_fonts), rendered_fonts)
                    session.send('Performance.enable')
                    before = {m['name']: m['value'] for m in session.send('Performance.getMetrics')['metrics']}
                    request_count = len(requests)
                    page.wait_for_timeout(2500)
                    after = {m['name']: m['value'] for m in session.send('Performance.getMetrics')['metrics']}
                    idle = {name: round(after.get(name, 0) - before.get(name, 0), 4)
                            for name in ('TaskDuration', 'ScriptDuration', 'LayoutDuration', 'RecalcStyleDuration', 'LayoutCount', 'RecalcStyleCount')}
                    idle['mutations'] = page.evaluate('window.__premiumMutations')
                    idle['targets'] = page.evaluate('Object.entries(window.__premiumTargets).sort((a,b)=>b[1]-a[1]).slice(0,12)')
                    idle['requests'] = requests[request_count:]
                    activity.append({'idle2500ms': idle})
                    page.evaluate('window.__premiumObserver.disconnect()')
                    check('No JavaScript errors', not errors, errors)
                    check('No remote requests', not remote, remote[:3])
                    check('No external operations attempted', not blocked, blocked)
                    browser.close()
            finally:
                host.terminate()
                host.wait(timeout=10)
    failed = [c for c in checks if not c['passed']]
    result = {'status': 'FAIL' if failed else 'PASS', 'checks': len(checks), 'failures': failed,
              'layouts': layouts, 'errors': errors, 'remoteRequests': remote,
              'blockedOperations': blocked, 'activity': activity, 'desktop': str(DESKTOP),
              'layoutMatrix': list(MATRIX_VIEWS) if not os.environ.get('NEXUS_TEST_FOCUSED') else [],
              'profile': 'disposable offline profile', 'physicalEliteBook': 'not tested'}
    (OUT / 'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), 'utf-8')
    print(json.dumps({k: v for k, v in result.items() if k not in {'layouts', 'failures'}}, ensure_ascii=False))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
