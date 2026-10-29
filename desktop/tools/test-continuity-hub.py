"""Offline browser regression for the unified Windows/Android continuity workflow."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit

from playwright.sync_api import sync_playwright, expect

DESKTOP = Path(__file__).resolve().parents[1]
checks = []


def check(label, value):
    assert value, label
    checks.append(label)


with tempfile.TemporaryDirectory(prefix='nexus-continuity-') as temporary:
    with (Path(temporary) / 'host.log').open('w', encoding='utf-8') as log:
        host = subprocess.Popen([sys.executable, '-u', str(DESKTOP / 'local_host.py'), '--headless', '--offline-qa', '--data-dir', temporary],
                                stdout=subprocess.PIPE, stderr=log, text=True,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            url = json.loads(host.stdout.readline())['url']
            drive = dict(state='synced', connected=True, configured=True, enabled=True, conflicts=[], lastSuccessAt=1793203200000)
            device = dict(enabled=True, configured=True, peers=['http://127.0.0.1:8795'], address='http://127.0.0.1:8797',
                          port=8797, interval=30, timeout=120, maxFileMb=0, agyDirectory='', skills=True,
                          credentials=False, settings=True, agy=False, deletions=False, chatAttachments=True,
                          status='Sincronizado - 12:00:00', lastSuccessAt=1793203200000)
            runs, errors, remote = [], [], []
            with sync_playwright() as pw:
                browser = pw.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width':1280,'height':840}, reduced_motion='reduce')
                page.on('pageerror', lambda error: errors.append(str(error)))
                def guard(route):
                    request = route.request
                    if urlsplit(request.url).netloc != urlsplit(url).netloc:
                        remote.append(request.url);route.abort();return
                    if urlsplit(request.url).path == '/rpc':
                        message = request.post_data_json
                        if message['op'] in {'driveSync','deviceContinuity'}:
                            value = drive if message['op'] == 'driveSync' else device
                            if message['args']['op'] == 'run':
                                runs.append(message['op'])
                                value['busy'] = True
                            route.fulfill(json={'id':message['id'],'ok':True,'value':value});return
                    route.continue_()
                page.route('**/*', guard)
                page.goto(url)
                page.wait_for_function('()=>!!window.NexusTen&&!!window.NexusContinuity&&!!initial')
                page.evaluate("()=>document.getElementById('nx-startup-onboarding')?.remove()")
                for view in ['library','assistant']:
                    page.evaluate('(view)=>navigate(view)', view)
                    expect(page.locator('#continuity-shortcut')).to_have_count(1)
                page.click('#continuity-shortcut')
                hub = page.locator('#nx-settings-continuity #continuity-hub')
                expect(hub).to_be_visible()
                expect(hub.locator('[data-channel=drive] [data-channel-status]')).to_have_text('Al día')
                expect(hub.locator('[data-channel=device] [data-channel-status]')).to_contain_text('Sincronizado')
                check('timestamps visible and key never populated', 'Último intercambio:' in hub.inner_text() and page.input_value('#device-continuity [name=key]') == '')
                check('no synchronization on navigation or refresh', runs == [])
                page.locator('#device-continuity').evaluate("node=>node.closest('details').open=true")
                page.fill('#device-continuity [name=peers]', 'http://127.0.0.1:1234')
                page.evaluate('()=>NexusContinuity.refresh()')
                check('refresh preserves unsaved pairing edits', page.input_value('#device-continuity [name=peers]') == 'http://127.0.0.1:1234')
                page.evaluate("()=>{const b=document.querySelector('[data-continuity=all]');b.click();b.click();}")
                page.wait_for_function("()=>document.querySelector('[data-continuity=all]').disabled")
                page.evaluate('()=>NexusContinuity.refresh()')
                check('one request per channel on rapid repeated activation', sorted(runs) == ['deviceContinuity','driveSync'])
                drive.update(busy=False, state='conflicts', conflicts=[{'key':'project:one','versions':[{'id':'r1','device':'Pixel','value':{'name':'Version one'}}]}])
                device.update(busy=False)
                page.evaluate('()=>NexusContinuity.refresh()')
                page.locator('#nexus-drive-sync').evaluate("node=>node.closest('details').open=true")
                conflict = page.locator('[data-sync=resolve]')
                conflict.focus()
                page.evaluate('()=>NexusContinuity.refresh()')
                expect(conflict).to_be_focused()
                check('conflict polling preserves keyboard focus', True)
                page.evaluate("()=>{window.retainedConflictButton=document.querySelector('[data-sync=resolve]')}")
                drive['busy'] = True
                page.evaluate('()=>NexusContinuity.refresh()')
                expect(conflict).to_be_disabled()
                drive['busy'] = False
                page.evaluate('()=>NexusContinuity.refresh()')
                expect(conflict).to_be_enabled()
                check('busy transitions update the same conflict control', page.evaluate("()=>window.retainedConflictButton===document.querySelector('[data-sync=resolve]')"))
                drive['conflicts'] = [{'key':f'assets:private-id-{i}','versions':[{'id':f'r{i}','device':'Pixel','value':{'displayName':f'Archivo {i}'}}]} for i in range(312)]
                page.evaluate('()=>NexusContinuity.refresh()')
                expect(page.locator('[data-sync-conflict]')).to_have_count(20)
                expect(page.locator('[data-sync-conflict-summary]')).to_have_text('20 de 312 elementos con versiones para revisar')
                check('conflict titles use names rather than internal identifiers', 'private-id-' not in page.locator('[data-sync-conflicts]').inner_text())
                before = list(runs)
                page.click('[data-sync=more]')
                expect(page.locator('[data-sync-conflict]')).to_have_count(40)
                check('large conflict lists expand locally in bounded pages', runs == before)
                drive.update(connected=False, state='idle', conflicts=[])
                device.update(enabled=False)
                page.evaluate('()=>NexusContinuity.refresh()')
                expect(page.locator('[data-continuity=all]')).to_be_disabled()
                check('paused and disconnected channels cannot claim sync', True)
                page.evaluate("()=>navigate('library')")
                page.evaluate('()=>NexusContinuity.refresh()')
                check('background refresh preserves current workspace', page.evaluate('view') == 'library')
                check('no browser errors', not errors)
                check('no external request', not remote)
                browser.close()
        finally:
            host.terminate();host.wait(timeout=10)
print(json.dumps({'status':'PASS','checks':checks,'paidCalls':0,'productionDataAccess':False}, ensure_ascii=False))
