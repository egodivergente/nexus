"""Exercise status-panel dismissal in real Edge + offline host, with a disposable profile."""
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
OUT = Path(os.environ.get('NEXUS_TEST_OUTPUT', str(ROOT / 'outputs/status-panel-20261025')))
OUT.mkdir(parents=True, exist_ok=True)
checks, errors, blocked = [], [], []


def check(name, value):
    if not value:
        raise AssertionError(name)
    checks.append(name)
    print('PASS', name, flush=True)


with tempfile.TemporaryDirectory(prefix='nexus-status-', dir=Path.home()/'.aki/tmp') as temp:
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
                page = browser.new_page(viewport={'width':1360, 'height':900})
                page.on('pageerror', lambda error: errors.append(str(error)))

                def guard(route):
                    if urlparse(route.request.url).netloc != urlparse(url).netloc:
                        blocked.append('non-local request')
                        route.abort()
                    elif urlparse(route.request.url).path == '/rpc':
                        message = route.request.post_data_json
                        op = message.get('op')
                        if op in {'request', 'generate', 'upload', 'flowSubmit', 'flowConnect',
                                  'flowQuote', 'nativeOperation', 'agent'}:
                            blocked.append(op)
                            route.fulfill(json={'id':message['id'], 'ok':False,
                                'error':'No external operations allowed in status-panel QA'})
                        else:
                            route.continue_()
                    else:
                        route.continue_()

                page.route('**/*', guard)
                page.goto(url)
                page.wait_for_function("()=>!!window.NexusCreative&&!!window.NexusTelemetry&&!!initial")
                page.evaluate("navigate('library')")
                summary = page.locator('#creative-control > summary')
                panel = page.locator('#creative-control-panel')
                close = page.get_by_role('button', name='Cerrar estado y consumo', exact=True)
                check('closed initially', not page.locator('#creative-control').evaluate('(e)=>e.open'))
                summary.click()
                expect(panel).to_be_visible()
                expect(close).to_be_visible()
                check('summary controls named status region', summary.get_attribute('aria-controls')=='creative-control-panel'
                      and panel.get_attribute('role')=='region'
                      and panel.get_attribute('aria-labelledby')=='creative-control-title')
                page.locator('#creative-control-title').click()
                check('inside clicks keep panel open', page.locator('#creative-control').evaluate('(e)=>e.open'))
                close.click()
                expect(panel).to_be_hidden()
                expect(summary).to_be_focused()
                checks.append('visible Close button closes and returns focus')
                summary.press('Enter')
                expect(panel).to_be_visible()
                close.focus()
                page.keyboard.press('Escape')
                expect(panel).to_be_hidden()
                expect(summary).to_be_focused()
                checks.append('keyboard open and Escape close with focus restored')
                summary.click()
                summary.click()
                expect(panel).to_be_hidden()
                checks.append('summary still toggles closed')
                summary.click()
                page.locator('#title').click()
                expect(panel).to_be_hidden()
                checks.append('outside click closes panel')
                summary.click()
                page.locator('#navigation [data-nav=catalog]').click()
                expect(panel).to_be_hidden()
                # Navigation rebuilds its buttons; closing must not focus the status trigger.
                expect(summary).not_to_be_focused()
                checks.append('navigation closes panel without stealing focus')
                page.evaluate("navigate('canvas')")
                page.locator('#prompt').fill('Borrador intacto')
                summary.click()
                page.locator('#prompt').click()
                expect(panel).to_be_hidden()
                expect(page.locator('#prompt')).to_be_focused()
                check('outside click preserves draft', page.locator('#prompt').input_value()=='Borrador intacto')
                page.evaluate("navigate('library')")
                for mode in ['escape', 'button']:
                    summary.click()
                    page.locator('#telemetry-button').click()
                    expect(page.locator('#telemetry-panel')).to_be_visible()
                    expect(panel).to_be_visible()
                    page.locator('#telemetry-title').click()
                    check('telemetry interactions preserve parent '+mode,
                          page.locator('#creative-control').evaluate('(e)=>e.open'))
                    if mode == 'escape':
                        page.keyboard.press('Escape')
                    else:
                        page.locator('#telemetry-close').click()
                    expect(page.locator('#telemetry-panel')).to_have_count(0)
                    expect(panel).to_be_visible()
                    expect(page.locator('#telemetry-button')).to_be_focused()
                    checks.append('telemetry '+mode+' closes only dialog and restores visible focus')
                    page.keyboard.press('Escape')
                    expect(panel).to_be_hidden()
                    expect(summary).to_be_focused()
                for width, height in [(1360,900), (1024,768), (480,850), (1280,600)]:
                    page.set_viewport_size({'width':width, 'height':height})
                    for expanded in [False, True]:
                        toggle = page.locator('#creative-dock-toggle')
                        if (toggle.get_attribute('aria-expanded')=='true') != expanded:
                            toggle.click()
                        summary.click()
                        expect(close).to_be_visible()
                        page.wait_for_function("()=>{const p=document.querySelector('#creative-control-panel').getBoundingClientRect();return p.x>=0&&p.right<=innerWidth+1&&p.y>=0&&p.bottom<=innerHeight}")
                        box = close.bounding_box()
                        check(f'close stays inside viewport {width} dock={expanded}',
                              box['x']>=0 and box['x']+box['width']<=width+1)
                        if width == 1360 and expanded:
                            page.screenshot(path=str(OUT/'status-panel.png'))
                        close.click()
                        expect(panel).to_be_hidden()
                check('no JavaScript errors', not errors)
                check('no external operations attempted', not blocked)
                browser.close()
        finally:
            host.terminate()
            host.wait(timeout=10)
result = {'status':'PASS', 'count':len(checks), 'checks':checks, 'errors':errors,
          'blockedOperations':blocked, 'profile':'isolated', 'desktop':str(DESKTOP)}
(OUT/'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), 'utf-8')
print(json.dumps(result, ensure_ascii=False))
