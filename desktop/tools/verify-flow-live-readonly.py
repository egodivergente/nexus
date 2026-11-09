"""Explicit live, READ-ONLY Flow acceptance. No input event, order, import, LLM or generation.

Starts an isolated host with no credentials and opens the real same-origin Flow proxy in
headless Edge. Records counts only: no frame, private title, URL, asset name or token is saved.
"""
import json
from pathlib import Path
import secrets
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from playwright.sync_api import sync_playwright

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import local_host as local


def run():
    evidence = {'provider_calls': 0, 'llm_calls': 0, 'input_events': 0, 'binary_frames': 0, 'frame_bytes': 0}
    errors = []; forbidden = []; sent = []
    with tempfile.TemporaryDirectory(prefix='nexus-flow-readonly-') as directory:
        local.DATA = Path(directory); local.MEDIA = local.DATA / 'media'; local.MEDIA.mkdir()
        local.KEY_FILE = local.DATA / 'credentials.dpapi'; local.OFFLINE_QA = False
        server = local.LoopbackHTTPServer(('127.0.0.1', 0), local.Handler)
        server.token = secrets.token_urlsafe(32); server.keys = {}
        threading.Thread(target=server.serve_forever, daemon=True).start()
        origin = f'http://127.0.0.1:{server.server_port}'
        def status(path, headers=None):
            try:
                with urllib.request.urlopen(urllib.request.Request(origin + path, headers=headers or {}), timeout=15) as response:
                    return response.status
            except urllib.error.HTTPError as error:
                return error.code
        try:
            assert status('/flow-live/health') == 403
            assert status('/flow-live/health?token=' + server.token, {'Sec-Fetch-Site': 'cross-site'}) == 403
            evidence['proxy_guard'] = 'PASS'
            with sync_playwright() as playwright:
                browser = playwright.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width': 1440, 'height': 1000})
                page.on('pageerror', lambda error: errors.append(str(error)))
                def socket_opened(socket):
                    if '/flow-live/live' not in socket.url:
                        return
                    def frame(data):
                        if isinstance(data, bytes):
                            assert data.startswith(b'\xff\xd8'), 'Expected the real JPEG protocol frame'
                            evidence['binary_frames'] += 1; evidence['frame_bytes'] += len(data)
                    socket.on('framereceived', frame)
                    socket.on('framesent', lambda data: sent.append(data))
                page.on('websocket', socket_opened)
                def guard(route):
                    request = route.request.post_data_json
                    operation = request.get('op'); args = request.get('args') or {}
                    dangerous = operation in ('generate', 'flowSubmit', 'flowRecover', 'upload', 'flowAutomation', 'flowConnect') or (
                        operation == 'agent' and args.get('op') in ('send', 'confirm', 'rewritePrompt'))
                    if dangerous:
                        forbidden.append(operation)
                        route.fulfill(json={'id': request.get('id'), 'ok': False, 'error': 'Read-only Flow acceptance blocks this operation.'})
                    else:
                        route.continue_()
                page.route('**/rpc', guard)
                page.goto(origin)
                page.wait_for_function('()=>Boolean(window.nexusQA)&&Boolean(window.NexusFlowLive)', timeout=30000)
                page.evaluate("navigate('flow')")
                page.wait_for_selector('#flow-live-connect')
                assert evidence['binary_frames'] == 0, 'Opening Flow must not connect automatically'
                page.locator('#flow-live-connect').click()
                page.wait_for_selector('#flow-live-placeholder', state='detached', timeout=30000)
                assert evidence['binary_frames'] > 0
                evidence['canvas_dimensions'] = page.locator('#flow-live-canvas').evaluate('(canvas)=>[canvas.width,canvas.height]')
                evidence['listed_assets'] = page.locator('.flow-result').count()
                evidence['live_status'] = 'PASS'
                page.evaluate("navigate('catalog')")
                page.wait_for_timeout(250)
                assert not sent, 'Read-only acceptance sent an input or viewport event'
                evidence['closed_on_navigation'] = True
                assert not forbidden, f'Unexpected operations were requested: {forbidden}'
                assert not errors, f'Page errors: {errors}'
                browser.close()
            evidence['page_errors'] = 0
            return evidence
        finally:
            if local._agent is not None:
                local._agent.close()
            server.shutdown(); server.server_close()


if __name__ == '__main__':
    print(json.dumps(run(), ensure_ascii=False))
