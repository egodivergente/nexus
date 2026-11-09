"""Exercise the real packaged JVM and editor in a disposable offline profile."""
import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid
from urllib.parse import urlsplit

from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--desktop', type=Path, default=Path(__file__).resolve().parents[1])
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
checks, errors, remote = [], [], []


def start(profile, log):
    host = subprocess.Popen([sys.executable, '-u', str(args.desktop/'local_host.py'),
                             '--headless', '--offline-qa', '--data-dir', str(profile)],
                            stdout=subprocess.PIPE, stderr=log, text=True, encoding='utf-8',
                            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0),
                            env={**os.environ, 'PYTHONDONTWRITEBYTECODE': '1'})
    try:
        return host, json.loads(host.stdout.readline())['url']
    except Exception:
        host.terminate()
        raise


def stop(host):
    if os.name == 'nt' and host.poll() is None:
        # Terminate only this disposable host and its JVM, never the installed app.
        subprocess.run(['taskkill', '/PID', str(host.pid), '/T', '/F'],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        host.wait(timeout=10)
        return
    host.terminate()
    try:
        host.wait(timeout=10)
    except subprocess.TimeoutExpired:
        host.kill()


with tempfile.TemporaryDirectory(prefix='nexus-story-desktop-') as temp:
    profile = Path(temp)
    (profile/'assistant').mkdir()
    (profile/'media').mkdir()
    uri = '/media/'+('a'*32)+'.png'
    (profile/uri.lstrip('/')).write_bytes(base64.b64decode(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3ioAAAAASUVORK5CYII='))
    (profile/'assistant/index.json').write_text(json.dumps({
        'projects': [{'id': 'film', 'name': 'Película existente', 'createdAt': 1}],
        'assets': [{'id': 'reference', 'uri': uri, 'type': 'IMAGE', 'role': 'REFERENCE',
                    'source': 'IMPORTED', 'createdAt': 1, 'displayName': 'Referencia local'}]}), 'utf-8')
    (profile/'state.json').write_text(json.dumps({'jobs': [], 'draft': {
        'modelId': None, 'prompt': 'Conservar el borrador de ArtBox', 'values': {}, 'media': []}}), 'utf-8')
    with (args.output/'host.log').open('w', encoding='utf-8') as log, sync_playwright() as pw:
        browser = pw.chromium.launch(channel='msedge', headless=True)
        host, url = start(profile, log)
        try:
            page = browser.new_page(viewport={'width': 1440, 'height': 1000})
            page.on('pageerror', lambda error: errors.append(str(error)))
            def guard(route):
                if urlsplit(route.request.url).netloc != urlsplit(url).netloc:
                    remote.append(route.request.url)
                    route.abort()
                else:
                    route.continue_()
            page.route('**/*', guard)
            page.goto(url)
            page.wait_for_function('()=>!!initial && typeof ensureAssistantReady==="function"')
            page.evaluate('async()=>{document.getElementById("nx-startup-onboarding")?.remove();await ensureAssistantReady();}')

            def agent(op, body):
                return page.evaluate('async ([op,args])=>(await rpc("agent",{op,args,owner:Agent.owner})).result', [op, body])

            snapshot = agent('snapshot', {})
            assert {'read_story_project', 'edit_story_project', 'propose_story_changes', 'prepare_story_generation'} <= set(snapshot['tools'])
            checks.append('real-story-agent-tools-registered')
            agent('project', {'id': 'film'})
            def command(action, **values):
                result = agent('storyCommand', {'projectId': 'film', 'input': {
                    'action': action, 'operation_id': uuid.uuid4().hex, **values}})
                assert result.get('ok') is True, result
                return result

            scene = command('create_scene', title='El encuentro')['id']
            block = command('create_block', scene_id=scene, kind='note', text='Su mano tiembla')['id']
            one = command('create_shot', scene_id=scene, title='La mano', origin_block_id=block)['id']
            two = command('create_shot', scene_id=scene, title='La mirada')['id']
            for shot in [one, two]:
                command('link_material', asset_id='reference', scope='shot', target_id=shot, role='candidate')
            command('move_shot', shot_id=two, scene_id=scene, expected_revision=0, order=0)
            view = agent('storyView', {'projectId': 'film', 'sceneId': scene})
            index = agent('snapshot', {})['index']
            assert len(index['assets']) == 1
            assert len(index['story']['links']) == 2
            assert sorted(index['shots'], key=lambda s: s['order'])[0]['id'] == two
            assert next(b for b in index['story']['blocks'] if b['id'] == block)['shotId'] == one
            checks += ['many-to-many-with-one-physical-file', 'reorder-keeps-links-and-note-origin']

            proposal = command('propose', title='Variante', changes=[{
                'action': 'update_shot', 'shot_id': one, 'expected_revision': 0,
                'description': 'Cerrar la mano'}])['id']
            command('update_shot', shot_id=one, expected_revision=0, description='Abrir la mano')
            conflict = agent('storyCommand', {'projectId': 'film', 'input': {
                'action': 'apply_proposal', 'proposal_id': proposal, 'expected_revision': 0,
                'operation_id': uuid.uuid4().hex}})
            assert conflict['code'] == 'revision_conflict', conflict
            checks.append('proposal-conflict-keeps-new-user-edit')

            task = command('enqueue_task', title='Preparar', scene_id=scene, shot_id=one)['id']
            command('update_task', task_id=task, expected_revision=0, status='running', executor='local')
            command('recover_tasks')
            command('recover_tasks')
            index = agent('snapshot', {})['index']
            assert len(index['story']['tasks']) == 1
            assert index['story']['tasks'][0]['status'] == 'pending_resume'
            checks.append('task-recovery-does-not-resubmit-or-duplicate')

            agent('storySelection', {'projectId': 'film', 'sceneId': scene, 'shotId': one, 'mode': 'proposal'})
            page.evaluate('()=>navigate("story")')
            page.wait_for_function('()=>document.querySelector("#navigation [data-nav=story]") && document.getElementById("title").textContent.includes("Guion")')
            page.get_by_text('El encuentro', exact=True).first.wait_for()
            note = page.locator(f'[data-story-block="{block}"] textarea[data-story-block-field="text"]')
            note.wait_for()
            edited_text = 'Su mano tiembla · edición desde Guion'
            note.fill(edited_text)
            page.locator(f'[data-story-block="{block}"] [data-story="save-block"]').click()
            page.wait_for_function('(id)=>!NexusStory.state.busy && NexusStory.state.data.blocks.find(b=>b.id===id)?.text.includes("edición desde Guion")', arg=block)
            index = agent('snapshot', {})['index']
            edited_block = next(b for b in index['story']['blocks'] if b['id'] == block)
            assert edited_block['text'] == edited_text
            assert edited_block['shotId'] == one
            checks.append('direct-ui-script-edit-keeps-note-and-shot-link')

            pending_text = 'Borrador propio · conservar durante cambios del agente'
            note.fill(pending_text)
            command('update_block', block_id=block, expected_revision=edited_block['revision'], text='Cambio reciente del agente')
            page.evaluate('async()=>await NexusStory.load()')
            assert note.input_value() == pending_text
            assert page.locator(f'[data-story-block="{block}"] [data-story="compare-block"]').count() == 1
            assert next(b for b in agent('snapshot', {})['index']['story']['blocks'] if b['id'] == block)['text'] == 'Cambio reciente del agente'
            page.locator(f'[data-story-block="{block}"] [data-story="compare-block"]').click()
            assert page.locator('#story-dialog').get_by_text('Cambio reciente del agente', exact=True).count() == 1
            assert page.locator('#story-dialog').get_by_text(pending_text, exact=True).count() == 1
            page.locator('#story-dialog [data-story="rebase-conflict"]').click()
            page.locator(f'[data-story-block="{block}"] [data-story="save-block"]').click()
            page.wait_for_function('(id)=>!NexusStory.state.busy && NexusStory.state.data.blocks.find(b=>b.id===id)?.text.startsWith("Borrador propio")', arg=block)
            checks.append('refresh-retains-draft-and-explicit-conflict-review')

            page.locator('[data-story-tab="board"]').click()
            assert page.locator('.story-board [data-story-shot-card]').count() == 2
            assert page.locator('.story-board img[src="'+uri+'"]').count() == 2
            cover_bounds = page.locator('.story-board .story-shot-visual').first.bounding_box()
            assert cover_bounds['width'] >= 180 and cover_bounds['height'] >= 100, cover_bounds
            page.screenshot(path=str(args.output/'storyboard-desktop.png'))
            checks.append('storyboard-renders-same-shots-and-shared-file')
            page.locator('[data-story-tab="material"]').click()
            assert page.locator('.story-material-grid [data-story-asset]').count() == 1
            page.locator('.story-material-grid [data-story-asset="reference"]').click()
            assert page.locator('#story-dialog .story-asset-use').count() == 2
            assert page.locator('#story-dialog').get_by_text('2 usos · un único archivo', exact=True).count() == 1
            assert page.locator('#story-dialog img').get_attribute('src') == uri
            page.screenshot(path=str(args.output/'material-desktop.png'))
            page.locator('#story-dialog [data-story="close-dialog"]').click()
            checks.append('material-preview-shows-two-contextual-uses')
            page.locator('[data-story-tab="script"]').click()
            page.screenshot(path=str(args.output/'guion-desktop.png'))
            page.set_viewport_size({'width': 800, 'height': 1000})
            page.wait_for_timeout(150)
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            page.screenshot(path=str(args.output/'guion-desktop-800.png'))
            checks.append('800-width-without-document-overflow')
            page.set_viewport_size({'width': 1440, 'height': 1000})
            restart_draft = 'Borrador duradero después de reiniciar el host'
            note.fill(restart_draft)
            page.evaluate('async()=>await flush()')
            checks.append('primary-guion-entry-renders-real-project')
            page.reload()
            page.wait_for_function('()=>!!initial')
            index = agent('snapshot', {})['index']
            assert next(s for s in index['shots'] if s['id'] == one)['description'] == 'Abrir la mano'
            assert len(index['story']['links']) == 2
            checks.append('page-reload-keeps-script-and-links')
            page.close()
            stop(host)
            host, url = start(profile, log)
            page = browser.new_page()
            page.goto(url)
            page.wait_for_function('()=>!!initial')
            index = agent('snapshot', {})['index']
            assert len(index['assets']) == 1 and len(index['story']['links']) == 2
            assert next(s for s in index['shots'] if s['id'] == one)['description'] == 'Abrir la mano'
            assert json.loads((profile/'state.json').read_text('utf-8'))['draft']['prompt'] == 'Conservar el borrador de ArtBox'
            assert (profile/uri.lstrip('/')).exists()
            page.evaluate('async()=>{await ensureAssistantReady();navigate("story");}')
            page.wait_for_function('()=>!!NexusStory.state.data')
            restored_note = page.locator(f'[data-story-block="{block}"] textarea[data-story-block-field="text"]')
            assert restored_note.input_value() == restart_draft
            checks.append('draft-restores-after-host-origin-changes')
            checks += ['host-restart-preserves-project-and-original', 'artbox-draft-preserved']
            assert not errors, errors
            assert not remote, remote
            checks += ['no-javascript-errors', 'no-external-api-or-spend']
        finally:
            stop(host)
            browser.close()
report = {'status': 'PASS', 'checks': checks, 'errors': errors, 'remoteRequests': remote}
(args.output/'report.json').write_text(json.dumps(report, indent=2), 'utf-8')
print(json.dumps(report))
