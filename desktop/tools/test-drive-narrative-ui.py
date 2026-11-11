"""Check narrative status and resolution controls with isolated presentation fixtures, without Google or a real profile."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--desktop', type=Path, default=Path(__file__).resolve().parents[1])
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
checks, errors, remote = [], [], []

with tempfile.TemporaryDirectory(prefix='nexus-story-sync-ui-') as profile:
    with (args.output/'host.log').open('w', encoding='utf-8') as log:
        host = subprocess.Popen([sys.executable, '-u', str(args.desktop/'local_host.py'), '--headless', '--offline-qa', '--data-dir', profile],
            stdout=subprocess.PIPE, stderr=log, text=True, encoding='utf-8',
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0), env={**os.environ, 'PYTHONDONTWRITEBYTECODE': '1'})
        browser = None
        try:
            url = json.loads(host.stdout.readline())['url']
            with sync_playwright() as pw:
                browser = pw.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width': 1280, 'height': 1000})
                page.on('pageerror', lambda error: errors.append(str(error)))
                def guard(route):
                    if urlsplit(route.request.url).netloc != urlsplit(url).netloc:
                        remote.append(route.request.url)
                        route.abort()
                    else:
                        route.continue_()
                page.route('**/*', guard)
                page.goto(url)
                page.wait_for_function('()=>!!initial && !!window.NexusContinuity && !!window.NexusDriveSync && !!window.NexusTen && !!window.NexusAgentParity')
                page.evaluate('''()=>{
                  document.getElementById('nx-startup-onboarding')?.remove();
                  window.storyFixture={state:'synced',connected:true,configured:true,enabled:true,storyEnabled:true,busy:false,conflicts:[]};
                  window.storyCalls=[];const original=rpc;
                  rpc=async function(op,args={}) {
                    if(op==='driveSync') {storyCalls.push(structuredClone(args));if(args.op==='story')storyFixture.storyEnabled=args.enabled;return structuredClone(storyFixture);}
                    if(op==='deviceContinuity')return {configured:false,enabled:false,status:'Sin vincular'};
                    return original(op,args);
                  };
                  navigate('settings');
                }''')
                page.wait_for_function('()=>document.querySelector("[data-sync-narrative-status]")?.textContent.includes("pendiente")')
                assert page.locator('[data-channel-narrative]').inner_text() == 'Guion pendiente de sincronizar'
                checks.append('legacy-completion-does-not-confirm-screenplay')
                page.locator('[data-channel=drive] > details.continuity-advanced > summary').click()
                page.locator('[data-sync-narrative-status]').wait_for(state='visible')
                page.evaluate('async()=>{storyFixture.narrativeSync={supported:true,enabled:true,state:"synced",conflicts:0,pendingDependencies:0};await NexusContinuity.refresh(true);}')
                page.wait_for_function('()=>document.querySelector("[data-sync-narrative-status]")?.textContent==="Guion compartido en Drive"')
                assert page.locator('[data-sync-narrative-status]').inner_text() == 'Guion compartido en Drive', page.evaluate('()=>({fixture:storyFixture,drive:NexusContinuity.state.drive,calls:storyCalls})')
                assert 'completar su sincronización' in page.locator('[data-sync-narrative-detail]').inner_text()
                checks.append('drive-upload-does-not-claim-phone-acknowledgement')
                page.evaluate('''async()=>{
                  storyFixture.narrativeSync={supported:true,enabled:true,state:'conflicts',conflicts:1,pendingDependencies:0};
                  storyFixture.conflicts=[{key:'story:blocks:private-block',versions:[
                    {id:'revision-one',device:'Windows',at:1800000000000,value:{id:'private-block',projectId:'private-project',payload:{text:'Mateo baja la mirada',narrativeState:'idea'}}},
                    {id:'revision-two',device:'S25',at:1800000001000,value:{id:'private-block',projectId:'private-project',payload:{text:'Mateo mira a Julián',narrativeState:'idea'}}}
                  ]}];await NexusContinuity.refresh(true);
                }''')
                content = page.locator('#nexus-drive-sync [data-sync-conflicts]').inner_text()
                assert 'Mateo baja la mirada' in content and 'Mateo mira a Julián' in content
                assert 'private-block' not in content and 'revision-one' not in content and 'private-project' not in content
                assert page.locator('#nexus-drive-sync [data-sync=resolve]').count() == 2
                checks.append('wrapped-script-conflicts-show-editorial-text-not-ids')
                page.locator('[data-sync=story-enabled]').uncheck()
                page.wait_for_function('()=>storyCalls.some(c=>c.op==="story"&&c.enabled===false)')
                assert page.locator('[data-sync-narrative-status]').inner_text() == 'Guion y storyboard pausados'
                assert page.locator('[data-sync=resolve]').first.is_disabled()
                page.locator('[data-sync=story-enabled]').check()
                page.wait_for_function('()=>storyCalls.some(c=>c.op==="story"&&c.enabled===true)')
                assert page.locator('[data-sync=resolve]').first.is_enabled()
                page.locator('[data-sync=resolve]').last.click()
                page.wait_for_function('()=>storyCalls.some(c=>c.op==="resolve"&&c.key==="story:blocks:private-block"&&c.revision==="revision-two")')
                checks += ['story-toggle-routes-without-extra-section', 'paused-story-cannot-resolve-but-enabled-can']
                page.screenshot(path=str(args.output/'drive-story-conflicts.png'))
                assert not errors, errors
                assert not remote, remote
                checks += ['no-javascript-errors', 'no-external-requests-or-spend']
                browser.close()
        finally:
            if os.name == 'nt' and host.poll() is None:
                subprocess.run(['taskkill', '/PID', str(host.pid), '/T', '/F'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                    creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            else:
                host.terminate()
            host.wait(timeout=10)

report = {'status': 'PASS', 'mode': 'isolated-presentation-fixtures', 'checks': checks, 'errors': errors, 'remoteRequests': remote}
(args.output/'report.json').write_text(json.dumps(report, indent=2), 'utf-8')
print(json.dumps(report))
