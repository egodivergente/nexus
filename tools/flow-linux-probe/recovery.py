"""Private Tailscale recovery portal; no generation or Google password handling."""
import asyncio
from contextlib import asynccontextmanager
import hmac
import json
import os
from pathlib import Path
import secrets
import subprocess
import time

from fastapi import FastAPI, Request, WebSocket
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from playwright.async_api import async_playwright
import websockets

ROOT = Path.home() / '.local/share/nexus-flow-probe'
HOST = os.environ.get('NEXUS_FLOW_HOST', '100.112.57.2')
PORT = 8899
ORIGIN = f'http://{HOST}:{PORT}'
TOKEN_FILE = ROOT / 'recovery-token'
SESSIONS = {}
LOCK = asyncio.Lock()
CHECK_LOCK = asyncio.Lock()
BROWSER = None
CACHE = {'status': 'not_checked', 'checked_at': 0}


def token():
    return TOKEN_FILE.read_text().strip()


def bearer_valid(value):
    return bool(value) and hmac.compare_digest(value.encode('utf-8'), token().encode('utf-8'))


def session_valid(value):
    return bool(value) and SESSIONS.get(value, 0) > time.time()


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
        for _ in range(30):
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
                        for page in context.pages:
                            if page.url in ('', 'about:blank'):
                                try:
                                    await page.goto('https://flow.google.com/', timeout=8000)
                                except Exception:
                                    pass
                            from urllib.parse import urlsplit
                            parts = urlsplit(page.url)
                            if parts.hostname == 'accounts.google.com':
                                state = 'auth_required'
                            elif parts.hostname == 'flow.google.com':
                                if parts.path.startswith('/about'):
                                    state = 'auth_required'
                                    continue
                                import re
                                ready = await page.get_by_role('button', name=re.compile(r'^(New project|Nuevo proyecto|Crear proyecto)$', re.I)).count()
                                errors = await page.get_by_text(re.compile(r'No se han podido cargar los proyectos|Failed to load projects|Unable to load projects', re.I)).count()
                                if ready and not errors:
                                    response = await context.request.get('https://myaccount.google.com/?hl=en', timeout=8000)
                                    if response.status == 200 and urlsplit(str(response.url)).hostname == 'myaccount.google.com':
                                        state = 'connected'
                                        break
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
    yield


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


@app.middleware('http')
async def guard(request: Request, call_next):
    if request.headers.get('host') != f'{HOST}:{PORT}':
        return JSONResponse({'error': 'host'}, status_code=403)
    if request.headers.get('origin') not in (None, ORIGIN):
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


@app.post('/lock')
async def lock(request: Request):
    SESSIONS.pop(request.cookies.get('nexus_flow'), None)
    response = JSONResponse({'remote_access': 'closed'})
    response.delete_cookie('nexus_flow')
    return response


@app.websocket('/websockify')
async def socket(ws: WebSocket):
    sid = ws.cookies.get('nexus_flow')
    if ws.headers.get('host') != f'{HOST}:{PORT}' or ws.headers.get('origin') != ORIGIN or not session_valid(sid):
        await ws.close(code=1008)
        return
    await ws.accept(subprotocol='binary')
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
        await ws.close()


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
