"""Offline Edge acceptance for the Windows ArtBox to Montaje workflow."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlsplit

from PIL import Image, ImageDraw
from playwright.sync_api import expect, sync_playwright

PROJECT = 'aurora-example'
MODEL = 'runway/gen4_image_turbo/image'
SEEDREAM_FLASH = 'bytedance/seedream-v5.0-flash/edit'
SEEDREAM_PRO = 'bytedance/seedream-v5.0-pro/edit'
RESULT = '/media/' + 'f' * 32 + '.png'
PROMPT = 'Plano amplio de una costa al amanecer, luz cálida y niebla baja.'


def make_fixture(root):
    media = root / 'media'
    media.mkdir()
    (root / 'assistant').mkdir()
    palettes = [
        ((37, 57, 77), (233, 161, 109), (42, 80, 90)),
        ((77, 62, 82), (208, 158, 140), (57, 68, 86)),
        ((37, 67, 81), (163, 187, 148), (44, 88, 77)),
        ((21, 47, 75), (202, 156, 122), (36, 69, 98)),
    ]
    jobs, assets = [], []
    for i, (sky, glow, water) in enumerate(palettes):
        name = ['Costa al amanecer', 'Contraluz', 'Reflejo en el agua', 'Plano de apertura'][i]
        uri = RESULT if i == 3 else '/media/' + str(i + 1) * 32 + '.png'
        image = Image.new('RGB', (960, 600))
        draw = ImageDraw.Draw(image)
        for y in range(image.height):
            ratio = min(1, y / 380)
            color = tuple(round(a + (b - a) * ratio) for a, b in zip(sky, glow))
            draw.line((0, y, 960, y), fill=color if y < 390 else water)
        draw.ellipse((615 + i * 23, 215, 670 + i * 23, 270), fill=(250, 221, 167))
        draw.polygon([(0, 445), (0, 310), (130, 302), (252, 345),
                      (407, 364), (515, 410), (960, 470), (960, 600), (0, 600)],
                     fill=tuple(int(c * 0.66) for c in water))
        image.save(media / uri.rsplit('/', 1)[-1])
        if i == 3:
            continue
        asset_id = f'aurora-image-{i}'
        job_id = f'aurora-job-{i}'
        assets.append({'id': asset_id, 'uri': uri, 'type': 'IMAGE', 'mimeType': 'image/png',
                       'role': 'OUTPUT', 'source': 'GENERATED', 'displayName': name,
                       'createdAt': 10 - i, 'projectId': PROJECT, 'jobId': job_id})
        jobs.append({'id': job_id, 'providerId': 'local_media', 'modelId': 'local/reference',
                     'name': name, 'modality': 'IMAGE', 'status': 'COMPLETED',
                     'prompt': name, 'parameters': {}, 'outputs': [uri],
                     'startedAt': 10 - i, 'projectId': PROJECT})
    state = {'jobs': jobs, 'draft': {'modelId': None, 'prompt': '', 'values': {}, 'media': []},
             'interfaceTheme': 'aurora'}
    index = {'projects': [{'id': PROJECT, 'name': 'Costa de prueba', 'createdAt': 1}],
             'assets': assets, 'generations': []}
    (root / 'state.json').write_text(json.dumps(state), encoding='utf-8')
    (root / 'assistant/index.json').write_text(json.dumps(index), encoding='utf-8')


@contextmanager
def offline_host(desktop, data, log_path):
    with log_path.open('w', encoding='utf-8') as log:
        host = subprocess.Popen(
            [sys.executable, '-u', str(desktop / 'local_host.py'), '--headless',
             '--offline-qa', '--data-dir', str(data)],
            stdout=log, stderr=subprocess.STDOUT, encoding='utf-8',
            env=dict(os.environ, PYTHONDONTWRITEBYTECODE='1'),
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0),
        )
        try:
            for _ in range(200):
                if host.poll() is not None:
                    raise RuntimeError(f'El host salió. Consulta {log_path.name}')
                lines = log_path.read_text(encoding='utf-8', errors='replace').splitlines()
                for line in lines:
                    if line.lstrip().startswith('{"url"'):
                        result = json.loads(line)
                        yield result['url']
                        return
                time.sleep(0.1)
            raise RuntimeError(f'El host no publicó una URL. Consulta {log_path.name}')
        finally:
            host.terminate()
            try:
                host.wait(timeout=10)
            except subprocess.TimeoutExpired:
                host.kill()
                host.wait()


def open_view(page, view, selector):
    page.locator(f'button[data-nav="{view}"]').first.click()
    page.locator(selector).first.wait_for(timeout=45_000)


def screenshot(page, out, name):
    page.screenshot(path=str(out / f'{name}.png'), animations='disabled', timeout=20_000)


def attach_reference(page):
    page.evaluate("""async uri => {
        const image = await fetch(uri).then(response => response.blob());
        await studioAttach([new File([image], 'Costa de referencia.png', {type: 'image/png'})]);
    }""", '/media/' + '1' * 32 + '.png')
    page.wait_for_function("() => state.draft.media.length === 1")


def verify(page, out, baseline):
    page.wait_for_function("() => window.NexusTen && window.NexusGallery && !!initial && initial.qa",
                           timeout=60_000)
    page.evaluate("async id => { await ensureAssistantReady(); await agentCall('project', {id}); }", PROJECT)
    open_view(page, 'canvas', '#creative-command')
    aurora2 = page.evaluate("typeof AgentQueue !== 'undefined'")
    if aurora2:
        expect(page.locator('#nexus-project-context')).to_have_text('Costa de prueba')
        page.locator('#gallery-activity').click()
        expect(page.locator('#nexus-activity-panel')).to_be_visible()
        expect(page.locator('#creative-command')).to_be_visible()
        page.locator('#gallery-activity').click()
        expect(page.locator('#nexus-activity-panel')).to_be_hidden()
    if baseline and page.locator('#creative-dock-toggle').get_attribute('aria-expanded') == 'false':
        page.locator('#creative-dock-toggle').click()
    if not baseline:
        page.evaluate('id => openModel(id)', SEEDREAM_FLASH)
        page.locator('#prompt').fill(PROMPT)
        attach_reference(page)
        photo_id = page.evaluate('state.draft.media[0].id')
        page.locator('#creative-variant').select_option(SEEDREAM_PRO)
        page.wait_for_function('id => loaded?.id === id', arg=SEEDREAM_PRO)
        expect(page.locator('#prompt')).to_have_value(PROMPT)
        assert page.evaluate('state.draft.media[0].id') == photo_id
        expect(page.locator('#generate')).to_be_disabled()
        page.wait_for_function("() => document.querySelector('.nx-stage img')?.naturalWidth > 0")
        screenshot(page, out, '01-variantes')
    page.evaluate('id => openModel(id)', MODEL)
    page.locator('#prompt').fill(PROMPT)
    attach_reference(page)
    page.locator('#quote-button').click()
    expect(page.locator('#generate')).to_be_enabled(timeout=30_000)
    page.wait_for_function("() => document.querySelector('.nx-stage img')?.naturalWidth > 0", timeout=25_000)
    screenshot(page, out, '01-artbox')
    metrics = {
        'artboxSidebarPx': round(page.locator('.sidebar').bounding_box()['width']),
        'artboxPreviewPx': round(page.locator('.nx-stage-frame').bounding_box()['width']),
        'artboxPromptPx': round(page.locator('#prompt').bounding_box()['width']),
        'edgeHaptics': page.evaluate("() => Object.getOwnPropertyNames(Navigator.prototype)"
                                     ".filter(key => /haptic|vibrat/i.test(key))"),
    }
    if not baseline:
        row = page.locator('.creative-model-row')
        expect(row.locator('.creative-motor')).to_be_visible()
        expect(row.locator('.creative-variant-field')).to_be_visible()
        expect(page.locator('#creative-command .creative-parameters > summary')).to_have_text('Avanzado')
        expect(page.locator('#creative-command .creative-references [data-pick]').first).to_be_visible()

    # The host stays in offline mode. Only this response is simulated by Edge.
    page.evaluate("initial.qa = false")
    try:
        page.locator('#generate').click()
        page.wait_for_function("prompt => state.jobs.some(j => j.prompt === prompt && j.status === 'COMPLETED')",
                               arg=PROMPT, timeout=25_000)
    finally:
        page.evaluate("initial.qa = true")

    open_view(page, 'library', '.gallery-grid')
    if aurora2:
        expect(page.locator('#library-source')).to_be_hidden()
        page.locator('.gallery-filters summary').click()
        expect(page.locator('#library-source')).to_be_visible()
        page.locator('.gallery-filters summary').click()
    page.locator('#pc-search').fill('Costa al amanecer')
    asset = page.locator('.gallery-piece').filter(has_text='Costa al amanecer')
    expect(asset).to_have_count(1)
    expect(asset.locator('img.gallery-thumbnail')).to_have_attribute('data-preview-state', 'ready', timeout=25_000)
    metrics['libraryTitleOpacity'] = float(asset.locator('.gallery-overlay').evaluate(
        'node => getComputedStyle(node).opacity'))
    page.locator('#pc-search').fill('')
    screenshot(page, out, '02-biblioteca')
    asset.locator('.gallery-media').click()
    expect(page.locator('.gallery-lightbox')).to_be_visible()
    screenshot(page, out, '03-visor-biblioteca')

    if baseline:
        page.keyboard.press('Escape')
        open_view(page, 'editor', '#corte-timeline')
        page.wait_for_function('() => Boolean(Corte.snapshot)&&!Corte.busy', timeout=45_000)
        page.locator('.corte-asset').filter(has_text='Costa al amanecer').locator('[data-corte-place]').click()
    elif aurora2:
        expect(page.get_by_role('button', name='Usar como referencia')).to_be_visible()
        expect(page.get_by_role('button', name='Mover a proyecto…')).to_be_hidden()
        page.get_by_role('button', name='Añadir a Costa de prueba').click()
    else:
        page.get_by_role('button', name='Añadir a Montaje…').click()
        expect(page.locator('#library-montage-project')).to_have_value(PROJECT)
        page.get_by_role('button', name='Añadir al cabezal', exact=True).click()
        expect(page.locator('#library-montage-form')).to_be_hidden(timeout=45_000)
    clip = page.locator('.corte-clip').first
    expect(clip).to_contain_text('Costa al amanecer', timeout=45_000)
    expect(page.locator('#corte-project')).to_have_value(PROJECT)
    if page.evaluate("initial.version.endsWith('aurora.3')"):
        control=page.get_by_role('button', name='Continuar en otro dispositivo')
        expect(control).to_be_visible()
        control.click()
        expect(page.locator('#toast')).to_contain_text('Montaje guardado en este PC')
    if aurora2:
        page.wait_for_function("() => document.querySelector('#corte-image')?.naturalWidth > 0")
        assert page.locator('#corte-video').get_attribute('src') is None
        assert not page.evaluate("async () => (await corteRpc('jobs')).jobs.length")
    screenshot(page, out, '04-montaje')
    if not baseline:
        page.wait_for_function('() => Corte.selected.size === 1 && !Corte.busy')
        page.locator('[data-corte="inspector"]').click()
        expect(page.locator('#corte-inspector')).to_be_visible()
        expect(page.locator('#corte-chat')).to_be_hidden()
        screenshot(page, out, '05-inspector')
        page.get_by_role('button', name='Cerrar inspector').click()
        expect(page.locator('#corte-inspector')).to_be_hidden()
        page.locator('[data-corte-time="5000000"]').click()
        page.wait_for_function('() => Corte.playhead === 5000000 && !Corte.busy')
        open_view(page, 'library', '.gallery-grid')
        page.locator('#pc-search').fill(PROMPT)
        expect(page.locator('.gallery-piece')).to_have_count(1)
        page.locator('.gallery-piece .gallery-media').click()
        if aurora2:
            page.get_by_role('button', name='Añadir a Costa de prueba · 5.00 s').click()
        else:
            page.get_by_role('button', name='Añadir a Montaje…').click()
            page.get_by_role('button', name='Añadir al cabezal', exact=True).click()
        page.wait_for_function('() => view === "editor" && corteClips().length === 2 && !Corte.busy')
        assert page.evaluate('uri => Object.values(Corte.snapshot.links).some(link => link.original_uri === uri)', RESULT)
        first = page.locator('.corte-clip').filter(has_text='Costa al amanecer')
        first_id = first.get_attribute('data-corte-clip')
        drag = page.evaluate_handle('new DataTransfer()')
        x = round(first.bounding_box()['x'] + 12)
        first.dispatch_event('dragstart', {'dataTransfer': drag, 'clientX': x})
        delta = page.evaluate('() => 10 * Corte.zoom') + 2
        timeline = page.locator('#corte-timeline')
        timeline.dispatch_event('dragover', {'dataTransfer': drag, 'clientX': x + delta})
        expect(page.locator('#corte-snap-guide')).to_be_visible()
        screenshot(page, out, '06-encaje')
        timeline.dispatch_event('drop', {'dataTransfer': drag, 'clientX': x + delta})
        expect(page.locator('#corte-snap-guide')).to_have_count(0)
        page.wait_for_function('id => corteClips().find(clip => clip.id === id)?.timelineStartUs === 5000000 && !Corte.busy', arg=first_id)
        if aurora2:
            page.locator('[data-corte-time="5000000"]').click()
            page.wait_for_function("() => document.querySelector('#corte-image')?.naturalWidth > 0")
            assert not page.evaluate("async () => (await corteRpc('jobs')).jobs.length")
            page.locator('[data-corte="preview"]').click()
            page.wait_for_function("() => document.querySelector('#corte-video')?.readyState >= 2", timeout=45_000)
            assert page.evaluate("async () => (await corteRpc('jobs')).jobs.some(j => j.kind === 'preview' && j.state === 'COMPLETED')")
            print('PASS fotograma inmediato sin trabajo de render y reproducción explícita', flush=True)
        print('PASS ArtBox, variante, generación simulada, biblioteca, visor, montaje e inspector', flush=True)
    return metrics


def verify_message_queue(page, held):
    """Keep using the real workspace, but hold the assistant transport at the localhost boundary."""
    open_view(page, 'assistant', '#agent-compose')
    page.evaluate("() => {Agent.snapshot.offline=false;renderAssistant();}")
    composer = page.locator('#agent-input')
    respond = lambda route, payload: route.fulfill(json={
        'id': route.request.post_data_json['id'], **payload
    })

    def next_turn(text):
        assert held and held[0].request.post_data_json['args']['args']['text'] == text
        return held.pop(0)

    def success(text):
        route = next_turn(text)
        respond(route, {'ok': True, 'value': {'result': {'text': 'Respuesta ' + text}, 'settings': {}}})
        return route.request.post_data_json['args']['args']['attachments']

    def enter(text):
        composer.fill(text)
        composer.press('Enter')
        expect(composer).to_have_value('')

    enter('Inicio')
    expect(page.locator('.agent-thinking')).to_be_visible()
    expect(page.locator('[data-agent="attach"]')).to_be_enabled()
    page.locator('[data-agent="attach"]').click()
    expect(page.locator('.agent-attachments')).to_contain_text('Referencia de prueba.png')
    enter('Segundo')
    enter('Tercero')
    expect(page.locator('.agent-queue summary')).to_have_text('2 en cola')
    assert len(held) == 1
    success('Inicio')
    page.wait_for_function("() => Agent.busy && Agent.items.at(-1).text === 'Segundo'")
    attachments = success('Segundo')
    assert len(attachments) == 1 and attachments[0]['uri'] == RESULT
    page.wait_for_function("() => Agent.busy && Agent.items.at(-1).text === 'Tercero'")
    enter('Después de parar')
    page.get_by_role('button', name='Detener agente', exact=True).click()
    expect(page.locator('.agent-queue')).to_contain_text('Cola pausada')
    respond(next_turn('Tercero'), {'ok': False, 'error': 'Turno detenido por el usuario.'})
    expect(page.locator('.agent-queue summary')).to_have_text('1 en cola')
    assert not held
    page.locator('.agent-queue summary').click()
    page.locator('.agent-queue li').filter(has_text='Después de parar').get_by_role('button', name='Quitar').click()
    expect(page.locator('.agent-queue')).to_be_hidden()
    enter('Fallo')
    enter('Pendiente')
    enter('Eliminar')
    respond(next_turn('Fallo'), {'ok': False, 'error': 'Fallo de prueba'})
    expect(page.locator('.agent-queue')).to_contain_text('Cola pausada')
    assert not held
    page.locator('.agent-queue summary').click()
    pending = page.locator('.agent-queue li').filter(has_text='Pendiente')
    pending.get_by_role('button', name='Editar').click()
    page.locator('#agent-queue-edit textarea').fill('Pendiente editado')
    page.get_by_role('button', name='Guardar cambios', exact=True).click()
    page.locator('.agent-queue li').filter(has_text='Eliminar').get_by_role('button', name='Quitar').click()
    expect(page.locator('.agent-queue summary')).to_have_text('1 en cola')
    page.evaluate('flush()')
    page.reload()
    page.wait_for_function("() => !!initial && typeof AgentQueue !== 'undefined'")
    open_view(page, 'assistant', '#agent-compose')
    page.evaluate("async () => {await ensureAssistantReady(); Agent.snapshot.offline=false;renderAssistant();}")
    expect(page.locator('.agent-queue summary')).to_have_text('1 en cola')
    assert not held
    page.get_by_role('button', name='Reanudar', exact=True).click()
    page.wait_for_function("() => Agent.busy && Agent.items.at(-1).text === 'Pendiente editado'")
    success('Pendiente editado')
    expect(page.locator('.agent-queue')).to_be_hidden()
    print('PASS cola FIFO con adjuntos, detención, edición, retirada, fallo, reapertura y reanudación', flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--desktop', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--baseline', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    errors, simulated, held = [], [], []
    with tempfile.TemporaryDirectory(prefix='nexus-aurora-') as temp:
        data = Path(temp)
        make_fixture(data)
        with offline_host(args.desktop.resolve(), data, output / 'host.log') as url, sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge', headless=True)
            page = browser.new_page(viewport={'width': 1440, 'height': 900})
            page.on('pageerror', lambda error: errors.append(str(error)))

            def route_request(route):
                if urlsplit(route.request.url).netloc != urlsplit(url).netloc:
                    return route.abort()
                if urlsplit(route.request.url).path == '/rpc':
                    message = route.request.post_data_json
                    operation = message.get('op')
                    if operation == 'agent' and message['args'].get('op') == 'send':
                        held.append(route)
                        return
                    if operation == 'agentCancel':
                        return route.fulfill(json={'id': message['id'], 'ok': True, 'value': True})
                    if operation == 'pick':
                        return route.fulfill(json={'id': message['id'], 'ok': True, 'value': [
                            {'id': 'aurora-attachment', 'url': RESULT, 'mime': 'image/png', 'name': 'Referencia de prueba.png'}
                        ]})
                    if operation == 'generate':
                        simulated.append(message['args']['provider'])
                        return route.fulfill(json={'id': message['id'], 'ok': True, 'value': {
                            'id': 'aurora-simulated', 'status': 'SUCCEEDED', 'output': [RESULT]
                        }})
                    if operation == 'upload':
                        return route.fulfill(json={'id': message['id'], 'ok': True, 'value': 'https://fixture.invalid/reference.png'})
                    if operation == 'schema':
                        return route.fulfill(json={'id': message['id'], 'ok': True, 'value': {
                            'components': {'schemas': {'Input': {
                                'required': ['prompt'],
                                'properties': {'prompt': {'type': 'string'},
                                               'images': {'type': 'array', 'items': {'type': 'string'}}},
                            }}},
                        }})
                    if operation in {'request', 'flowSubmit', 'flowQuote', 'flowConnect', 'download'}:
                        return route.fulfill(json={'id': message['id'], 'ok': False, 'error': 'Red bloqueada en la prueba'})
                return route.continue_()

            page.route('**/*', route_request)
            try:
                page.goto(url)
                result = verify(page, output, args.baseline)
                if not args.baseline:
                    views = {'canvas': '#creative-command', 'library': '.gallery-grid', 'editor': '#corte-timeline',
                             'assistant': '#agent-compose', 'catalog': '.nx-intent-row', 'story': '#content',
                             'flow': '#flow-live-workspace', 'history': '.history-list', 'settings': '.nx-settings'}
                    for width in (1440, 1024, 760):
                        page.set_viewport_size({'width': width, 'height': 900})
                        for view, selector in views.items():
                            open_view(page, view, selector)
                            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), (view, width)
                            expect(page.locator(f'#navigation [data-nav="{view}"], #studio-settings[data-nav="{view}"]').first).to_have_attribute('aria-current', 'page')
                            if view == 'editor':
                                expect(page.locator('#title')).to_have_text('Montaje')
                            if view in ('canvas', 'library', 'editor', 'assistant') and width in (1024, 760):
                                screenshot(page, output, f'{view}-{width}')
                    open_view(page, 'catalog', '.nx-intent-row')
                    page.keyboard.press('Control+k')
                    expect(page.locator('#gallery-command-search')).to_be_focused()
                    page.keyboard.press('Escape')
                    expect(page.locator('#dialog')).to_be_hidden()
                    open_view(page, 'library', '.gallery-grid')
                    expect(page.locator('#library-projects')).to_be_hidden()
                    page.get_by_role('button', name='Carpetas', exact=True).click()
                    expect(page.locator('#library-projects')).to_be_visible()
                    page.emulate_media(reduced_motion='reduce')
                    assert page.evaluate("getComputedStyle(document.querySelector('#studio-settings')).transitionDuration === '0s'")
                    print('PASS 9 pantallas a 1440, 1024 y 760 px, comandos y movimiento reducido', flush=True)
                    if page.evaluate("typeof AgentQueue !== 'undefined'"):
                        verify_message_queue(page, held)
                assert simulated == ['runway'], simulated
                assert not errors, errors
                (output / 'resultado.json').write_text(json.dumps({
                    **result, 'simulatedGenerations': len(simulated), 'errors': errors, 'qa': 'PASS'
                }, indent=2), encoding='utf-8')
                print('PASS', json.dumps(result, ensure_ascii=True), flush=True)
            except Exception:
                screenshot(page, output, 'error')
                print(page.evaluate("() => ({view, version:initial?.version, status:document.querySelector('#toast')?.textContent, screen:document.querySelector('#content')?.innerText.slice(0,1300)})"), flush=True)
                raise
            finally:
                browser.close()


if __name__ == '__main__':
    main()
