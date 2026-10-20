"""Private Tailscale recovery portal and explicit Flow UI bridge.

The bridge deliberately drives only the already-open, authenticated Flow page.
It never reads/export cookies, stores prompts, or uses an undocumented Google API.
"""
import asyncio
import base64
from collections import Counter
from contextlib import asynccontextmanager, suppress
import hashlib
import hmac
import json
import mimetypes
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
from urllib.parse import urlsplit

from fastapi import FastAPI, Request, WebSocket
from starlette.background import BackgroundTask
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from starlette.websockets import WebSocketDisconnect
from playwright.async_api import async_playwright
import websockets
from flow_references import receive_reference, reference_paths, frame_paths, attach_video_frames, attach_references, ReferenceError, MAX_IMAGES, COMPOSER
import flow_agent
import flow_import

ROOT = Path(os.environ.get('NEXUS_FLOW_ROOT', str(Path.home() / '.local/share/nexus-flow-probe')))
HOST = os.environ.get('NEXUS_FLOW_HOST', '100.112.57.2')
MAGICDNS_HOST = os.environ.get('NEXUS_FLOW_MAGICDNS_HOST', 'asusvidal.tail6ac6b4.ts.net')
PORT = int(os.environ.get('NEXUS_FLOW_PORT', '8899'))
# Lotes: un encargo puede enviar el mismo prompt varias veces seguidas (como hacer «x4» varias veces a mano).
FLOW_MAX_RUNS = 10
FLOW_MAX_RESULTS = 40
# Si ya llegaron resultados y Flow deja de añadir más durante este tiempo, el lote se da por terminado.
FLOW_STALL_SECONDS = {'image': 120, 'video': 480}
ORIGIN = f'http://{HOST}:{PORT}'
ALLOWED_HOSTS = frozenset({f'{HOST}:{PORT}', f'{MAGICDNS_HOST}:{PORT}'})
ALLOWED_ORIGINS = frozenset({f'http://{HOST}:{PORT}', f'http://{MAGICDNS_HOST}:{PORT}'})
TOKEN_FILE = ROOT / 'recovery-token'
SESSIONS = {}
LOCK = asyncio.Lock()
CHECK_LOCK = asyncio.Lock()
BROWSER = None
CACHE = {'status': 'not_checked', 'checked_at': 0}
FLOW_UI_LOCK = asyncio.Lock()
FLOW_JOB_LOCK = asyncio.Lock()
FLOW_JOBS = {}
FLOW_PROMPT_LIMIT = 4000
FLOW_BODY_LIMIT = 24 * 1024
FLOW_REQUEST_ID = re.compile(r'^[A-Za-z0-9][A-Za-z0-9_-]{15,95}$')
FLOW_JOB_ID = re.compile(r'^[A-Za-z0-9_-]{16,96}$')
FLOW_OUTPUTS = {'image', 'video'}
FLOW_DOWNLOAD_NAME = re.compile(r'^[A-Za-z0-9][A-Za-z0-9._ -]{0,127}$')
FLOW_SIGNATURE = re.compile(r'^[0-9a-f]{64}$')


# Longitud del texto escrito en el cuadro (div editable o input); un div vacío deja un salto de línea.
COMPOSER_LENGTH = 'el => ((el.value ?? el.innerText) || "").trim().length'


class FlowError(Exception):
    def __init__(self, code, status_code=409):
        super().__init__(code)
        self.code = code
        self.status_code = status_code


class FlowClickUncertain(Exception):
    """A submit click may have reached Flow, so it must never be retried."""


async def visible(locator):
    for item in await locator.all():
        if await item.is_visible():
            return True
    return False


async def context_status(context):
    needs_auth = False
    for page in context.pages:
        parts = urlsplit(page.url)
        if parts.hostname == 'accounts.google.com':
            needs_auth = True
            continue
        if parts.scheme != 'https' or parts.hostname != 'flow.google.com':
            continue
        if parts.path.startswith('/about'):
            needs_auth = True
            continue
        errors = await visible(page.get_by_text(re.compile(
            r'No se han podido cargar los proyectos|Failed to load projects|Unable to load projects', re.I)))
        if errors:
            continue
        ready = await visible(page.get_by_role('button', name=re.compile(
            r'^(New project|Nuevo proyecto|Crear proyecto)$', re.I)))
        if not ready and re.fullmatch(r'/project/[^/]+/?', parts.path):
            # The authenticated project itself is the reliable signal. Flow no longer
            # exposes stable workspace labels, but its composer and submit control do.
            composer = await visible(page.locator(COMPOSER))
            submit = await visible(page.locator('button[aria-label="Iniciar generación"]'))
            ready = composer and submit
        if ready:
            return 'connected'
    return 'auth_required' if needs_auth else 'unverified'


def token():
    return TOKEN_FILE.read_text().strip()


def bearer_valid(value):
    return bool(value) and hmac.compare_digest(value.encode('utf-8'), token().encode('utf-8'))


def session_valid(value):
    return bool(value) and SESSIONS.get(value, 0) > time.time()


def flow_jobs_file():
    return ROOT / 'flow-jobs.json'


def flow_download_directory():
    return ROOT / 'flow-downloads'


def public_flow_job(job, replayed=False):
    """Return state only. Prompts and browser/session data never leave memory."""
    result = {
        'job_id': job['job_id'],
        'state': job['state'],
        'expected_output': job['expected_output'],
        'created_at': job['created_at'],
        'updated_at': job['updated_at'],
        'result_count': len(job.get('new_downloads', [])),
        'download_available': job['state'] == 'completed' and bool(job.get('new_downloads')),
        'cost_source': 'flow_current_project',
        'runs': job.get('runs', 1),
        'runs_submitted': job.get('runs_submitted', job.get('runs', 1)),
        'expected_results': job.get('expected_results', 0),
    }
    if job.get('error'):
        result['error'] = job['error']
    if replayed:
        result['idempotent_replay'] = True
    return result


def persisted_flow_job(job):
    """The on-disk job journal is deliberately prompt- and token-free."""
    return {
        'job_id': job['job_id'],
        'client_request_id': job['client_request_id'],
        'state': job['state'],
        'expected_output': job['expected_output'],
        'created_at': job['created_at'],
        'updated_at': job['updated_at'],
        'baseline_downloads': list(job.get('baseline_downloads', [])),
        'new_downloads': list(job.get('new_downloads', [])),
        'error': job.get('error'),
        'intent_hash': job.get('intent_hash'),
        'runs': int(job.get('runs', 1)),
        'runs_submitted': int(job.get('runs_submitted', job.get('runs', 1))),
        'expected_results': int(job.get('expected_results', 0)),
        'last_growth_at': float(job.get('last_growth_at', job.get('updated_at', 0))),
    }


def valid_persisted_flow_job(job):
    if not isinstance(job, dict):
        return False
    if not FLOW_JOB_ID.fullmatch(str(job.get('job_id', ''))):
        return False
    if not FLOW_REQUEST_ID.fullmatch(str(job.get('client_request_id', ''))):
        return False
    if job.get('expected_output') not in FLOW_OUTPUTS:
        return False
    if job.get('state') not in {'submitting', 'submitted', 'generating', 'completed', 'failed', 'uncertain'}:
        return False
    if not isinstance(job.get('created_at'), (int, float)) or not isinstance(job.get('updated_at'), (int, float)):
        return False
    baseline = job.get('baseline_downloads', [])
    outputs = job.get('new_downloads', [])
    if not isinstance(baseline, list) or not isinstance(outputs, list):
        return False
    if not all(isinstance(signature, str) and FLOW_SIGNATURE.fullmatch(signature) for signature in baseline):
        return False
    if not all(isinstance(output, dict) and isinstance(output.get('index'), int) and output['index'] >= 0
               and isinstance(output.get('signature'), str) and FLOW_SIGNATURE.fullmatch(output['signature'])
               for output in outputs):
        return False
    return True


def save_flow_jobs():
    """Atomically persist only opaque job metadata, under the portal's private root."""
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    path = flow_jobs_file()
    temporary = path.with_suffix('.json.tmp')
    payload = [persisted_flow_job(job) for job in FLOW_JOBS.values()]
    temporary.write_text(json.dumps(payload, separators=(',', ':')))
    temporary.chmod(0o600)
    os.replace(temporary, path)
    path.chmod(0o600)


def load_flow_jobs():
    FLOW_JOBS.clear()
    path = flow_jobs_file()
    if not path.exists():
        return
    try:
        jobs = json.loads(path.read_text())
    except (OSError, UnicodeError, ValueError):
        return
    if not isinstance(jobs, list):
        return
    for job in jobs:
        if valid_persisted_flow_job(job):
            # Drop unexpected fields even if the file was edited manually.
            clean = persisted_flow_job(job)
            FLOW_JOBS[clean['job_id']] = clean


async def update_flow_job(job_id, **changes):
    async with FLOW_JOB_LOCK:
        job = FLOW_JOBS.get(job_id)
        if not job:
            raise FlowError('job_not_found', 404)
        job.update(changes)
        job['updated_at'] = time.time()
        save_flow_jobs()
        return dict(job)


async def flow_job(job_id):
    if not FLOW_JOB_ID.fullmatch(job_id):
        raise FlowError('job_not_found', 404)
    async with FLOW_JOB_LOCK:
        existing = FLOW_JOBS.get(job_id)
        if not existing:
            raise FlowError('job_not_found', 404)
        return dict(existing)


async def read_limited_json(request, limit=FLOW_BODY_LIMIT):
    content_length = request.headers.get('content-length')
    if content_length and content_length.isdigit() and int(content_length) > limit:
        raise FlowError('too_large', 413)
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > limit:
            raise FlowError('too_large', 413)
    try:
        data = json.loads(body)
    except (ValueError, UnicodeError):
        raise FlowError('invalid_json', 400)
    if not isinstance(data, dict):
        raise FlowError('invalid_json', 400)
    return data


def validate_flow_submission(data):
    prompt = data.get('prompt')
    expected_output = data.get('expected_output')
    client_request_id = data.get('client_request_id')
    if data.get('confirmed') is not True:
        raise FlowError('confirmation_required', 400)
    if not isinstance(prompt, str):
        raise FlowError('invalid_prompt', 400)
    prompt = prompt.strip()
    if not prompt:
        raise FlowError('empty_prompt', 400)
    if len(prompt) > FLOW_PROMPT_LIMIT:
        raise FlowError('prompt_too_long', 413)
    if expected_output not in FLOW_OUTPUTS:
        raise FlowError('invalid_expected_output', 400)
    if not isinstance(client_request_id, str) or not FLOW_REQUEST_ID.fullmatch(client_request_id):
        raise FlowError('invalid_client_request_id', 400)
    return prompt, expected_output, client_request_id


def validate_flow_batch(data):
    """`runs`: veces que se pulsa Generar con el mismo prompt; `expected_results`: total que se espera (p. ej. 8 × 4)."""
    runs = data.get('runs', 1)
    expected = data.get('expected_results', 0)
    if not isinstance(runs, int) or isinstance(runs, bool) or not 1 <= runs <= FLOW_MAX_RUNS:
        raise FlowError('invalid_runs', 400)
    if not isinstance(expected, int) or isinstance(expected, bool) or not 0 <= expected <= FLOW_MAX_RESULTS:
        raise FlowError('invalid_expected_results', 400)
    return runs, expected


def is_flow_project(url):
    parts = urlsplit(url)
    return (parts.scheme == 'https' and parts.hostname == 'flow.google.com'
            and bool(re.fullmatch(r'/project/[^/]+/?', parts.path)))


async def with_flow_project_page(action):
    """Attach to exactly one already-open project; never navigate or create one."""
    try:
        async with async_playwright() as playwright:
            browser = await playwright.chromium.connect_over_cdp('http://127.0.0.1:9229', timeout=10000)
            pages = [page for context in browser.contexts for page in context.pages if is_flow_project(page.url)]
            # La pestaña de importación muestra otros proyectos; nunca es la que usa el puente para generar.
            pages = [page for page in pages if not await flow_import.is_import_page(page)]
            if not pages:
                raise FlowError('flow_project_not_open', 409)
            if len(pages) != 1:
                raise FlowError('flow_project_ambiguous', 409)
            return await action(pages[0])
    except FlowError:
        raise
    except Exception as error:
        raise FlowError('flow_browser_unavailable', 503) from error


async def flow_controls(page):
    """Only use the explicitly observed Flow composer and submit controls."""
    composer = page.locator(COMPOSER)
    submit = page.locator('button[aria-label="Iniciar generación"]')
    if await composer.count() != 1 or await submit.count() != 1:
        raise FlowError('flow_controls_unavailable', 409)
    if not await composer.is_visible() or not await submit.is_visible():
        raise FlowError('flow_controls_unavailable', 409)
    return composer, submit


async def flow_capability():
    async def inspect(page):
        composer, submit = await flow_controls(page)
        return {
            'project_open': True,
            'composer_editable': await composer.is_editable(),
            # It is normally disabled until a prompt is supplied. That is not a failure.
            'submit_enabled_without_prompt': await submit.is_enabled(),
        }
    return await with_flow_project_page(inspect)


async def flow_api_status():
    current = await status()
    response = {
        'bridge_status': current.get('status', 'unverified'),
        'generation_ready': False,
        'prompt_limit': FLOW_PROMPT_LIMIT,
        'expected_outputs': sorted(FLOW_OUTPUTS),
        'cost_source': 'flow_current_project',
        'reference_images': True,
        'reference_limit': MAX_IMAGES,
        'batch_runs_max': FLOW_MAX_RUNS,
        'batch_results_max': FLOW_MAX_RESULTS,
    }
    if current.get('status') != 'connected':
        response['reason'] = 'flow_not_connected'
        return response
    try:
        capability = await flow_capability()
    except FlowError as error:
        response['reason'] = error.code
        return response
    response.update(capability)
    response['generation_ready'] = capability['composer_editable']
    return response


async def download_controls(page):
    """Resultados visibles del proyecto: imágenes y vídeos servidos por Flow, identificados por la ruta de su archivo.
    La Flow actual ya no pone botones «Descargar» en las tarjetas (están en su menú ⋮), así que se usan los medios,
    que además identifican cada resultado de forma única sin leer el texto de las tarjetas."""
    items = flow_import.unique_media(await page.evaluate(flow_import.READ_MEDIA))
    return [{'index': index, 'signature': flow_import.media_signature(item['src']), 'src': item['src'], 'kind': item['kind']}
            for index, item in enumerate(items)]


def new_downloads(job, current_controls):
    """Identify only additions whose identity remains unambiguous across polls."""
    baseline = list(job.get('baseline_downloads', []))
    baseline_counts = Counter(baseline)
    additions = []
    seen = Counter()
    for control in current_controls:
        signature = control['signature']
        seen[signature] += 1
        if seen[signature] > baseline_counts[signature]:
            additions.append(control)
    if not additions:
        return [], None
    # Identical controls cannot tell us whether an old or a new output will be
    # clicked, so make the user open Flow rather than guess.
    if any(control['signature'] in baseline_counts for control in additions):
        return None, 'download_ambiguous'
    return additions, None


def download_control_matches(expected, current_controls):
    """Devuelve el resultado actual con la misma identidad; la posición cambia cuando Flow añade otros."""
    return next((control for control in current_controls if control['signature'] == expected['signature']), None)


async def wait_composer_cleared(page, composer, timeout_ms=15000):
    """Flow vacía el cuadro al aceptar un envío; así sabemos que podemos lanzar el siguiente del lote."""
    waited = 0
    while waited < timeout_ms:
        if not await composer.evaluate(COMPOSER_LENGTH):
            return True
        await page.wait_for_timeout(250)
        waited += 250
    return False


async def drive_flow_submission(prompt, references=None, runs=1, on_run=None, mode='Texto', frames=None):
    """Fill and click `runs` times. Any failure after the first click start becomes uncertain/partial."""
    async with FLOW_UI_LOCK:
        async def submit(page):
            composer, submit_button = await flow_controls(page)
            if not await composer.is_editable():
                raise FlowError('flow_composer_not_editable', 409)
            current_length = await composer.evaluate(COMPOSER_LENGTH)
            if current_length:
                # Do not overwrite a manual draft in the existing Flow tab.
                raise FlowError('flow_composer_not_empty', 409)
            baseline = await download_controls(page)
            if on_run:
                # Guardar la referencia antes del primer clic: si el lote se corta, los resultados siguen siendo atribuibles.
                await on_run(0, [item['signature'] for item in baseline])
            filled = False
            click_started = False
            try:
                for run in range(runs):
                    if mode == 'Fotogramas' and frames:
                        if run == 0:
                            await attach_video_frames(page, frames[0], frames[1] if len(frames) > 1 else None)
                        else:
                            with suppress(ReferenceError):
                                await attach_video_frames(page, frames[0], frames[1] if len(frames) > 1 else None)
                    elif run == 0:
                        await attach_references(page, references or [])
                    elif references:
                        # Flow suele conservar las referencias tras enviar; si las quitó, se vuelven a poner.
                        with suppress(ReferenceError):
                            await attach_references(page, references)
                    await composer.fill(prompt, timeout=10000)
                    filled = True
                    if not await submit_button.is_enabled():
                        if run == 0:
                            raise FlowError('flow_submit_unavailable', 409)
                        break
                    click_started = True
                    await submit_button.click(timeout=10000)
                    if on_run:
                        await on_run(run + 1, None)
                    if run + 1 < runs and not await wait_composer_cleared(page, composer):
                        break
                    filled = False
                await page.wait_for_timeout(250)
                return [item['signature'] for item in baseline]
            except ReferenceError as error:
                raise FlowError(error.code, error.status_code) from error
            except FlowError:
                if filled and not click_started:
                    with suppress(Exception):
                        await composer.fill('', timeout=5000)
                raise
            except Exception as error:
                if click_started:
                    raise FlowClickUncertain() from error
                if filled:
                    with suppress(Exception):
                        await composer.fill('', timeout=5000)
                raise FlowError('flow_submit_failed', 503) from error
        return await with_flow_project_page(submit)


async def submit_flow_generation(prompt, expected_output, client_request_id, reference_ids=None, runs=1, expected_results=0,
                                 initial_frame_id=None, end_frame_id=None, mode='Texto'):
    """Reserve an idempotent job before the UI clicks, never storing prompt."""
    intent_hash = hashlib.sha256(json.dumps([prompt, expected_output, mode, reference_ids or [], initial_frame_id, end_frame_id] + ([runs] if runs > 1 else []),
        ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
    async with FLOW_JOB_LOCK:
        existing = next((job for job in FLOW_JOBS.values()
                         if job['client_request_id'] == client_request_id), None)
        if existing:
            if existing.get('intent_hash') and existing['intent_hash'] != intent_hash:
                raise FlowError('client_request_conflict', 409)
            return dict(existing), True
        now = time.time()
        job_id = secrets.token_urlsafe(18)
        job = {
            'job_id': job_id,
            'client_request_id': client_request_id,
            'state': 'submitting',
            'expected_output': expected_output,
            'created_at': now,
            'updated_at': now,
            'baseline_downloads': [],
            'intent_hash': intent_hash,
            'new_downloads': [],
            'error': None,
            'runs': runs,
            'runs_submitted': 0,
            'expected_results': expected_results,
            'last_growth_at': now,
        }
        FLOW_JOBS[job_id] = job
        save_flow_jobs()

    async def on_run(done, baseline):
        if baseline is not None:
            await update_flow_job(job_id, baseline_downloads=baseline)
        else:
            await update_flow_job(job_id, runs_submitted=done)
    try:
        frames = frame_paths(ROOT, initial_frame_id, end_frame_id)
        references = reference_paths(ROOT, reference_ids or [])
        baseline = await drive_flow_submission(prompt, references or None, runs, on_run, mode, frames or None)
    except ReferenceError as error:
        await update_flow_job(job_id, state='failed', error=error.code)
        raise FlowError(error.code, error.status_code) from error
    except FlowClickUncertain:
        current = await flow_job(job_id)
        if runs > 1 and current.get('runs_submitted', 0) >= 1:
            # Al menos un envío del lote entró: se siguen recogiendo los resultados que vayan saliendo.
            return await update_flow_job(job_id, state='submitted', error='batch_partial',
                                         last_growth_at=time.time()), False
        return await update_flow_job(job_id, state='uncertain', error='submit_uncertain'), False
    except FlowError as error:
        await update_flow_job(job_id, state='failed', error=error.code)
        raise
    except Exception as error:
        await update_flow_job(job_id, state='failed', error='flow_submit_failed')
        raise FlowError('flow_submit_failed', 503) from error
    return await update_flow_job(job_id, state='submitted', baseline_downloads=baseline, error=None,
                                 last_growth_at=time.time()), False


async def refresh_flow_job(job):
    if job['state'] in {'completed', 'failed'}:
        return job
    try:
        async with FLOW_UI_LOCK:
            controls = await with_flow_project_page(download_controls)
    except FlowError as error:
        # The user can reconnect/open the original project and poll again.
        return await update_flow_job(job['job_id'], error=error.code)
    additions, error = new_downloads(job, controls)
    if error:
        return await update_flow_job(job['job_id'], state='uncertain', error=error)
    if job['state'] == 'uncertain':
        # A click with an indeterminate outcome is never promoted automatically.
        return await update_flow_job(job['job_id'], new_downloads=additions,
                                     error='submit_uncertain')
    expected = int(job.get('expected_results', 0))
    if job.get('runs', 1) > 1 or expected > 1:
        # Lote: se espera a que salgan todos; si Flow deja de añadir resultados un buen rato, se entrega lo que haya.
        now = time.time()
        grew = len(additions) > len(job.get('new_downloads', []))
        last = now if grew else float(job.get('last_growth_at', now))
        stalled = additions and now - last > FLOW_STALL_SECONDS.get(job['expected_output'], 180)
        if (expected and len(additions) >= expected) or stalled:
            return await update_flow_job(job['job_id'], state='completed', new_downloads=additions, error=None)
        return await update_flow_job(job['job_id'], state='generating', new_downloads=additions,
                                     last_growth_at=last, error=job.get('error') if job.get('error') == 'batch_partial' else None)
    if additions:
        return await update_flow_job(job['job_id'], state='completed', new_downloads=additions, error=None)
    return await update_flow_job(job['job_id'], state='generating', error=None)


async def get_flow_job_status(job_id):
    job = await flow_job(job_id)
    return await refresh_flow_job(job)


def private_download_path(job_id, output_index, filename):
    suffix = Path(filename).suffix.lower()
    if not re.fullmatch(r'\.[a-z0-9]{1,10}', suffix):
        suffix = '.bin'
    directory = flow_download_directory()
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    return directory / f'{job_id}-{output_index}-{secrets.token_hex(8)}{suffix}'


def safe_download_filename(filename, output_index):
    if isinstance(filename, str) and FLOW_DOWNLOAD_NAME.fullmatch(filename):
        return filename
    suffix = Path(str(filename)).suffix.lower()
    if not re.fullmatch(r'\.[a-z0-9]{1,10}', suffix):
        suffix = ''
    return f'flow-result-{output_index}{suffix}'


def delete_private_download(path):
    with suppress(OSError):
        path.unlink(missing_ok=True)


async def download_flow_result(job_id, output_index):
    job = await get_flow_job_status(job_id)
    if job['state'] != 'completed':
        raise FlowError('result_not_ready', 409)
    downloads = job.get('new_downloads', [])
    if output_index < 0 or output_index >= len(downloads):
        raise FlowError('result_not_found', 404)
    expected = downloads[output_index]
    async with FLOW_UI_LOCK:
        async def download(page):
            match = download_control_matches(expected, await download_controls(page))
            if not match:
                raise FlowError('download_ambiguous', 409)
            try:
                result = await page.evaluate(flow_agent.FETCH_MEDIA, match['src'])
            except Exception as error:
                raise FlowError('download_failed', 503) from error
            if not result or 'error' in result:
                raise FlowError('download_unavailable', 409)
            media_type = result.get('type') or 'application/octet-stream'
            extension = mimetypes.guess_extension(media_type) or ('.mp4' if match['kind'] == 'video' else '.jpg')
            filename = safe_download_filename(f'flow-result-{output_index}{extension}', output_index)
            path = private_download_path(job_id, output_index, filename)
            path.write_bytes(base64.b64decode(result['data']))
            path.chmod(0o600)
            return FileResponse(path, media_type=media_type, filename=filename,
                                background=BackgroundTask(delete_private_download, path))
        return await with_flow_project_page(download)


async def start_browser():
    global BROWSER
    async with LOCK:
        if BROWSER and BROWSER.poll() is None:
            return True
        lock_file = ROOT / 'profile/SingletonLock'
        if lock_file.is_symlink():
            try:
                target = os.readlink(lock_file)
                pid = int(target.rsplit('-', 1)[-1])
                os.kill(pid, 0)
                try:
                    reader, writer = await asyncio.open_connection('127.0.0.1', 6099)
                    writer.close()
                    await writer.wait_closed()
                    return True
                except OSError:
                    return False
            except (ProcessLookupError, ValueError):
                lock_file.unlink(missing_ok=True)
            except PermissionError:
                return False
        BROWSER = subprocess.Popen(['xvfb-run', '-a', '-s', '-screen 0 1280x800x24',
                                    'python3', str(ROOT / 'login.py'), '--persistent'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(200):
            if BROWSER.poll() is not None:
                return False
            try:
                reader, writer = await asyncio.open_connection('127.0.0.1', 6099)
                writer.close()
                await writer.wait_closed()
                return True
            except OSError:
                await asyncio.sleep(0.3)
        return False


async def status():
    global CACHE
    async with CHECK_LOCK:
        if time.time() - CACHE['checked_at'] < 5:
            return CACHE
        state = 'browser_closed'
        alive = bool(BROWSER and BROWSER.poll() is None)
        if not alive:
            try:
                reader, writer = await asyncio.open_connection('127.0.0.1', 9229)
                writer.close()
                await writer.wait_closed()
                alive = True
            except OSError:
                pass
        if alive:
            state = 'unverified'
            try:
                async with async_playwright() as pw:
                    browser = await pw.chromium.connect_over_cdp('http://127.0.0.1:9229', timeout=10000)
                    for context in browser.contexts:
                        current = await context_status(context)
                        if current != 'unverified':
                            state = current
                        if state == 'connected':
                            break
                    # Stop only Playwright's connection, never browser.close().
            except Exception:
                state = 'unverified'
        CACHE = {'status': state, 'checked_at': time.time(), 'generation_enabled': False}
        return CACHE


@asynccontextmanager
async def lifespan(app):
    os.umask(0o077)
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    if not TOKEN_FILE.exists():
        TOKEN_FILE.write_text(secrets.token_urlsafe(32))
    TOKEN_FILE.chmod(0o600)
    load_flow_jobs()
    # En el ASUS el navegador de Flow arranca con el servicio: tras un reinicio (o del propio equipo) el puente
    # vuelve a estar listo sin esperar a que alguien abra el portal. En Windows el navegador lo abre su lanzador.
    if shutil.which('xvfb-run'):
        asyncio.create_task(start_browser())
    yield


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


@app.middleware('http')
async def guard(request: Request, call_next):
    if request.headers.get('host') not in ALLOWED_HOSTS:
        return JSONResponse({'error': 'host'}, status_code=403)
    if request.headers.get('origin') not in (None, *ALLOWED_ORIGINS):
        return JSONResponse({'error': 'origin'}, status_code=403)
    public = request.url.path in ('/', '/unlock')
    auth = request.headers.get('authorization', '')
    if not public and not ((auth.startswith('Bearer ') and bearer_valid(auth[7:]))
                           or session_valid(request.cookies.get('nexus_flow'))):
        return JSONResponse({'error': 'authentication_required'}, status_code=401)
    response = await call_next(request)
    response.headers['Cache-Control'] = 'no-store'
    response.headers['Referrer-Policy'] = 'no-referrer'
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['X-Frame-Options'] = 'SAMEORIGIN'
    return response


@app.get('/')
async def index():
    return FileResponse(Path(__file__).with_name('recovery.html'))


@app.post('/unlock')
async def unlock(request: Request):
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > 1024:
            return JSONResponse({'error': 'too_large'}, status_code=413)
    try:
        data = json.loads(body)
    except (ValueError, UnicodeError):
        return JSONResponse({'error': 'invalid_json'}, status_code=400)
    if not isinstance(data, dict) or not isinstance(data.get('token'), str) or not bearer_valid(data['token']):
        return JSONResponse({'error': 'invalid_token'}, status_code=401)
    if not await start_browser():
        return JSONResponse({'error': 'browser_busy'}, status_code=409)
    for key, expires in list(SESSIONS.items()):
        if expires <= time.time():
            SESSIONS.pop(key, None)
    sid = secrets.token_urlsafe(32)
    SESSIONS[sid] = time.time() + 600
    response = JSONResponse({'vnc_password': (ROOT / 'login-access').read_text().strip()})
    response.set_cookie('nexus_flow', sid, httponly=True, samesite='strict', max_age=600)
    return response


@app.get('/status')
async def get_status():
    return await status()


def flow_error_response(error):
    return JSONResponse({'error': error.code}, status_code=error.status_code)


@app.get('/api/flow/status')
async def get_flow_status():
    try:
        return await flow_api_status()
    except FlowError as error:
        return flow_error_response(error)


@app.post('/api/flow/jobs')
async def create_flow_job(request: Request):
    try:
        data = await read_limited_json(request)
        prompt, expected_output, client_request_id = validate_flow_submission(data)
        runs, expected_results = validate_flow_batch(data)
        references = data.get('reference_ids', [])
        reference_paths(ROOT, references)
        initial_frame_id = data.get('initial_frame_id')
        end_frame_id = data.get('end_frame_id')
        resolved_frames = frame_paths(ROOT, initial_frame_id, end_frame_id)
        mode = data.get('mode')
        if expected_output == 'video':
            mode = mode or ('Fotogramas' if resolved_frames else 'Referencias' if references else 'Texto')
            if mode not in {'Texto', 'Fotogramas', 'Referencias'}:
                raise ReferenceError('invalid_video_mode', 400)
            if mode == 'Fotogramas' and not resolved_frames:
                raise ReferenceError('initial_frame_required', 400)
            if mode == 'Fotogramas' and references:
                raise ReferenceError('frame_mode_has_references', 400)
            if mode == 'Texto' and (resolved_frames or references):
                raise ReferenceError('text_mode_has_media', 400)
            if mode == 'Referencias' and (resolved_frames or not references):
                raise ReferenceError('references_mode_requires_images', 400)
        elif resolved_frames:
            raise ReferenceError('frames_require_video', 400)
        else:
            mode = 'Referencias'
        job, replayed = await submit_flow_generation(prompt, expected_output, client_request_id, references or None,
                                                     runs, expected_results, initial_frame_id, end_frame_id, mode)
        return JSONResponse(public_flow_job(job, replayed=replayed), status_code=200 if replayed else 201)
    except ReferenceError as error:
        return JSONResponse({'error': error.code}, status_code=error.status_code)
    except FlowError as error:
        return flow_error_response(error)


@app.post('/api/flow/references')
async def upload_flow_reference(request: Request):
    try:
        return await receive_reference(request, ROOT)
    except ReferenceError as error:
        return JSONResponse({'error': error.code}, status_code=error.status_code)


@app.get('/api/flow/jobs/{job_id}')
async def get_flow_job(job_id: str):
    try:
        return public_flow_job(await get_flow_job_status(job_id))
    except FlowError as error:
        return flow_error_response(error)


@app.get('/api/flow/jobs/{job_id}/downloads/{output_index}')
async def get_flow_download(job_id: str, output_index: int):
    try:
        return await download_flow_result(job_id, output_index)
    except FlowError as error:
        return flow_error_response(error)


@app.post('/api/flow/agent')
async def flow_agent_send(request: Request):
    """Envía un mensaje al agente de Flow del proyecto abierto y devuelve un trabajo para seguir su respuesta."""
    try:
        data = await read_limited_json(request)
        message = data.get('message')
        if not isinstance(message, str) or not message.strip():
            raise FlowError('empty_prompt', 400)
        if len(message) > FLOW_PROMPT_LIMIT:
            raise FlowError('prompt_too_long', 413)
        before = await with_flow_project_page(lambda page: flow_agent.send_agent_message(page, message.strip(), FLOW_UI_LOCK, FlowError))
        return JSONResponse(flow_agent.public_agent_job(flow_agent.new_agent_job(before)), status_code=201)
    except FlowError as error:
        return flow_error_response(error)


@app.get('/api/flow/agent/{job_id}')
async def flow_agent_status(job_id: str):
    job = flow_agent.AGENT_JOBS.get(job_id)
    if not job:
        return JSONResponse({'error': 'job_not_found'}, status_code=404)
    try:
        return await with_flow_project_page(lambda page: flow_agent.refresh_agent_job(page, job))
    except FlowError as error:
        return flow_error_response(error)


@app.get('/api/flow/agent/{job_id}/media/{row}/{index}')
async def flow_agent_media(job_id: str, row: int, index: int):
    if job_id not in flow_agent.AGENT_JOBS:
        return JSONResponse({'error': 'job_not_found'}, status_code=404)
    try:
        found = await with_flow_project_page(lambda page: flow_agent.fetch_agent_media(page, row, index))
    except FlowError as error:
        return flow_error_response(error)
    if not found:
        return JSONResponse({'error': 'download_unavailable'}, status_code=409)
    media_type, raw = found
    from fastapi.responses import Response
    return Response(raw, media_type=media_type)


async def with_flow_browser(action):
    """Conecta al navegador autenticado para la importación, sin tocar el proyecto del puente."""
    try:
        async with flow_import.IMPORT_LOCK:
            async with async_playwright() as playwright:
                browser = await playwright.chromium.connect_over_cdp('http://127.0.0.1:9229', timeout=10000)
                return await action(browser)
    except FlowError:
        raise
    except Exception as error:
        raise FlowError('flow_browser_unavailable', 503) from error


@app.get('/api/flow/projects')
async def flow_projects():
    """Proyectos de la galería de Flow (id y título) para importar lo ya generado."""
    try:
        projects = await with_flow_browser(flow_import.list_projects)
    except FlowError as error:
        return flow_error_response(error)
    if projects is None:
        return JSONResponse({'error': 'auth_required'}, status_code=409)
    return {'projects': projects}


@app.get('/api/flow/projects/{project_id}/media')
async def flow_project_media(project_id: str):
    if not flow_import.PROJECT_ID.fullmatch(project_id):
        return JSONResponse({'error': 'project_not_found'}, status_code=404)
    try:
        _, items = await with_flow_browser(lambda browser: flow_import.project_media(browser, project_id))
    except FlowError as error:
        return flow_error_response(error)
    if items is None:
        return JSONResponse({'error': 'auth_required'}, status_code=409)
    return {'media': flow_import.public_media(items)}


@app.get('/api/flow/projects/{project_id}/media/{key}')
async def flow_project_media_file(project_id: str, key: str, size: str | None = None):
    """Sin size: la versión que muestra Flow. Con size (1K, 2K, 4K, 720p, 1080p, GIF): el menú «Descargar» de Flow."""
    from fastapi.responses import Response
    if not flow_import.PROJECT_ID.fullmatch(project_id) or not flow_import.MEDIA_KEY.fullmatch(key):
        return JSONResponse({'error': 'result_not_found'}, status_code=404)
    if size is not None:
        if size not in flow_import.SIZES:
            return JSONResponse({'error': 'option_unavailable'}, status_code=400)
        try:
            found = await with_flow_browser(lambda browser: flow_import.download_size(browser, project_id, key, size))
        except FlowError as error:
            return flow_error_response(error)
        if isinstance(found, str):
            return JSONResponse({'error': found}, status_code=404 if found == 'result_not_found' else 409)
        filename, raw = found
        return Response(raw, media_type=mimetypes.guess_type(filename)[0] or 'application/octet-stream',
                        headers={'Content-Disposition': f'attachment; filename="{safe_download_filename(filename, 0)}"'})
    try:
        found = await with_flow_browser(lambda browser: flow_import.fetch_media(browser, project_id, key))
    except FlowError as error:
        return flow_error_response(error)
    if not found:
        return JSONResponse({'error': 'download_unavailable'}, status_code=409)
    media_type, raw = found
    return Response(raw, media_type=media_type)


@app.post('/lock')
async def lock(request: Request):
    SESSIONS.pop(request.cookies.get('nexus_flow'), None)
    response = JSONResponse({'remote_access': 'closed'})
    response.delete_cookie('nexus_flow')
    return response


@app.websocket('/websockify')
async def socket(ws: WebSocket):
    sid = ws.cookies.get('nexus_flow')
    if (ws.headers.get('host') not in ALLOWED_HOSTS or ws.headers.get('origin') not in ALLOWED_ORIGINS
            or not session_valid(sid)):
        await ws.close(code=1008)
        return
    # Current noVNC sends no subprotocol; browsers reject an unsolicited one.
    requested = ws.scope.get('subprotocols', [])
    await ws.accept(subprotocol='binary' if 'binary' in requested else None)
    async def upstream(remote):
        while True:
            if not session_valid(sid):
                return
            await remote.send(await ws.receive_bytes())
    async def downstream(remote):
        async for payload in remote:
            if not session_valid(sid):
                return
            await ws.send_bytes(payload)
    try:
        async with websockets.connect('ws://127.0.0.1:6099/websockify', subprotocols=['binary'], max_size=8*1024*1024) as remote:
            tasks = [asyncio.create_task(upstream(remote)), asyncio.create_task(downstream(remote))]
            async def revoked():
                while session_valid(sid):
                    await asyncio.sleep(1)
            tasks.append(asyncio.create_task(revoked()))
            try:
                await asyncio.wait(tasks, timeout=max(0, SESSIONS.get(sid, 0)-time.time()), return_when=asyncio.FIRST_COMPLETED)
            finally:
                for task in tasks:
                    task.cancel()
                await asyncio.gather(*tasks, return_exceptions=True)
    except Exception:
        pass
    finally:
        with suppress(WebSocketDisconnect, RuntimeError):
            await ws.close()


# BytePlus administration shares this bridge's existing authentication guard.
from byteplus_admin import router as byteplus_usage_router
app.include_router(byteplus_usage_router)


@app.get('/{asset:path}')
async def assets(asset: str):
    base = Path('/usr/share/novnc').resolve()
    path = (base / asset).resolve()
    if not path.is_relative_to(base) or not path.is_file():
        return JSONResponse({'error': 'not_found'}, status_code=404)
    return FileResponse(path)


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host=HOST, port=PORT, access_log=False, log_level='warning')
