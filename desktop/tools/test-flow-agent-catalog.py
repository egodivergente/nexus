"""Real Kotlin discovery with stale window replies; local LLM fixture, no generation."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys
import tempfile
import threading
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from agent_host import AgentHost
from flow_agent import reply

requests = []
queries = [
    ('images', 'list_models', {'provider_id': 'google_flow', 'modality': 'IMAGE', 'capability': 'TEXT_TO_IMAGE'}),
    ('pro', 'get_model_brief', {'provider_id': 'google_flow', 'query': 'nano-pro', 'focus': 'schema'}),
    ('frames', 'get_model_brief', {'provider_id': 'google_flow', 'query': 'omni_flash-frames', 'focus': 'schema'}),
    ('upscale', 'list_models', {'provider_id': 'google_flow', 'capability': 'UPSCALE'}),
]


class Proxy(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args):
        pass

    def do_POST(self):
        assert self.path == '/v1/converse'
        requests.append(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
        content = [{'toolUse': {'toolUseId': key, 'name': name, 'input': value}}
                   for key, name, value in queries] if len(requests) == 1 else [{'text': 'Catálogo comprobado.'}]
        body = json.dumps({'output': {'message': {'role': 'assistant', 'content': content}},
                           'stopReason': 'tool_use' if len(requests) == 1 else 'end_turn',
                           'usage': {'inputTokens': 0, 'outputTokens': 0}}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(body)


proxy = ThreadingHTTPServer(('127.0.0.1', 0), Proxy)
threading.Thread(target=proxy.serve_forever, daemon=True).start()
with tempfile.TemporaryDirectory(prefix='nexus-live-flow-catalog-') as folder:
    host = AgentHost(Path(__file__).resolve().parents[1], folder, lambda _: {'token': 'local-fixture'}, lambda *_: None)
    try:
        host.save_settings({'backend': 'proxy', 'endpoint': f'http://127.0.0.1:{proxy.server_port}', 'mode': 'AGENT'})
        result, errors, callbacks = [], [], []

        def run():
            try:
                result.append(host.command({'owner': 'stale-window', 'op': 'send',
                                            'args': {'text': 'Comprueba Flow.', 'attachments': []}}))
            except Exception as exc:
                errors.append(str(exc))

        worker = threading.Thread(target=run, daemon=True)
        worker.start()
        deadline = time.monotonic() + 45
        while worker.is_alive() and time.monotonic() < deadline:
            for event in host.events('stale-window'):
                callbacks.append(event['op'])
                assert event['op'] in {'models', 'schema'}, 'Only discovery is allowed'
                # This open window predates Nano Banana generation entries.
                stale = [{'ref': {'providerId': 'google_flow', 'modelId': 'google-flow/image_upscale'},
                          'name': 'Escalar imagen Flow', 'modalities': ['IMAGE'], 'capabilities': ['UPSCALE']}]
                answer = {'owner': 'stale-window', 'id': event['id'], 'ok': event['op'] == 'models',
                          'value': stale if event['op'] == 'models' else None, 'error': 'Modelo ausente en esta ventana.'}
                assert reply(host, {**answer, 'owner': 'another-window'}, {}) is False
                assert reply(host, answer, {}) is True
                assert reply(host, answer, {}) is False
            time.sleep(.02)
        worker.join(timeout=1)
        assert not worker.is_alive() and not errors, {'errors': errors, 'requests': len(requests), 'callbacks': callbacks}
        outcomes = {}
        for message in requests[-1]['messages']:
            for block in message['content']:
                if 'toolResult' in block:
                    tool = block['toolResult']
                    assert tool.get('status', 'success') == 'success', tool
                    outcomes[tool['toolUseId']] = tool['content'][0]['json']
        images = {m['model_id'] for m in outcomes['images']['items']}
        assert {'google-flow/nano-pro', 'google-flow/nano2'} <= images, outcomes
        assert outcomes['pro']['items'][0]['schema_status'] == 'available', outcomes['pro']
        frames = outcomes['frames']['items'][0]['schema']['media']
        assert frames['firstFrameField'] == 'initial_frame' and frames['lastFrameField'] == 'end_frame', frames
        assert 'google-flow/image_upscale' in {m['model_id'] for m in outcomes['upscale']['items']}
        assert len(requests) == 2 and set(callbacks) == {'models', 'schema'}
        policy = json.dumps(requests[0]['system'], ensure_ascii=False)
        assert 'Nano Banana Pro y Nano Banana 2 estan PERMITIDOS.' in policy
        assert 'esta restriccion NO incluye Pro ni Nano Banana 2' in policy
        assert 'CATALOGO ACTUAL DE GOOGLE FLOW' in policy and 'google-flow/nano-pro' in policy
        print(json.dumps({'status': 'PASS', 'realKotlinTools': True, 'staleWindowRecovered': True,
                          'nanoBananaProDiscovered': True, 'nativeFramesRetained': True,
                          'inference': 'local fixture', 'mediaSubmissions': 0}))
    finally:
        host.close()
        proxy.shutdown()
        proxy.server_close()
