"""Smoke-test the packaged Windows edition in Edge with a disposable offline profile."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--desktop', type=Path, default=Path(__file__).resolve().parents[1])
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
errors, remote, checks = [], [], []
with tempfile.TemporaryDirectory(prefix='nexus-modern-package-') as folder:
    with (args.output/'host.log').open('w', encoding='utf-8') as log:
        host = subprocess.Popen([sys.executable, '-u', str(args.desktop/'local_host.py'), '--headless', '--offline-qa', '--data-dir', folder],
            stdout=subprocess.PIPE, stderr=log, text=True, encoding='utf-8',
            env={**os.environ, 'PYTHONDONTWRITEBYTECODE':'1'}, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            url = json.loads(host.stdout.readline())['url']
            with sync_playwright() as pw:
                browser = pw.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width':1440, 'height':1000})
                page.on('pageerror', lambda error: errors.append(str(error)))
                def guard(route):
                    if urlparse(route.request.url).netloc != urlparse(url).netloc:
                        remote.append(route.request.url)
                        route.abort()
                    else:
                        route.continue_()
                page.route('**/*', guard)
                page.goto(url)
                page.wait_for_function('()=>!!initial&&!!window.NexusTen&&!!window.NexusChatContinuity&&!!window.CatalogParity&&!!window.NexusAgentParity')
                page.evaluate("()=>document.getElementById('nx-startup-onboarding')?.remove()")
                assert page.evaluate('initial.version') == '2026.10.26-desktop-modern.2'
                assert page.evaluate('initial.maxImportBytes') == 4*1024**3
                page.evaluate("async()=>{await ensureAssistantReady();await agentCall('createProject',{name:'Paquete moderno · prueba aislada'});}")
                assert page.evaluate('Agent.snapshot.tools.length') == 81
                checks += ['packaged-version', '4-GiB-contract', '81-runtime-tools']
                for view in ['assistant', 'canvas', 'catalog', 'history', 'library', 'settings', 'editor']:
                    page.evaluate('(view)=>navigate(view)', view)
                    page.wait_for_timeout(180)
                    assert page.locator('#navigation [data-nav="editor"]').count() == 1
                    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
                    checks.append('navigation:'+view)
                page.wait_for_function('()=>Corte.loaded&&!Agent.busy')
                assert page.locator('#corte-chat #agent-compose').count() == 1
                assert page.locator('#title').inner_text() == 'Corte · Montaje'
                page.evaluate("()=>{Agent.items=[{id:'synced-proof',role:'assistant',text:'Adjunto recuperable',attachments:[{name:'local.png',mime:'image/png',url:'/media/'+('a'.repeat(32))+'.png'}]}];renderAssistant();}")
                assert page.locator('#corte-chat [data-agent-message="synced-proof"] a[download]').count() == 1
                page.evaluate('()=>{renderAssistant();renderAssistant();}')
                assert page.locator('#corte-chat #agent-compose').count() == 1
                checks += ['corte-embedded-chat', 'synced-attachment-link', 'single-composer-after-refresh']
                page.evaluate("()=>{Agent.items=[];navigate('catalog');}")
                page.wait_for_timeout(300)
                page.screenshot(path=str(args.output/'installed-catalog.png'))
                assert not errors, errors
                assert not remote, remote
                checks += ['no-javascript-errors', 'no-remote-requests']
                browser.close()
        finally:
            host.terminate()
            try:
                host.wait(timeout=10)
            except subprocess.TimeoutExpired:
                host.kill()
report = {'status':'PASS','desktop':str(args.desktop.resolve()),'checks':checks,'errors':errors,'remoteRequests':remote}
(args.output/'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report))
