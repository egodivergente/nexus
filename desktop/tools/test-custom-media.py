"""Real shared Kotlin codec + protected Windows credentials + local synthetic HTTP API."""
import base64
import copy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from io import BytesIO
from pathlib import Path
import socket
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from custom_media import CustomMedia
from corte_host import CorteHost
from local_host import windows_keys, write_secret
from PIL import Image


class CustomMediaAcceptance(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='nexus-custom-media-')
        self.data = Path(self.temp.name)
        self.host = CorteHost(Path(__file__).resolve().parents[1], self.data, None)
        self.requests = []
        image = BytesIO()
        Image.new('RGB', (64, 64), '#38bca6').save(image, format='PNG')
        self.image_bytes = image.getvalue()
        image_bytes = self.image_bytes
        requests = self.requests
        inference = []
        class Handler(BaseHTTPRequestHandler):
            protocol_version = 'HTTP/1.1'
            def log_message(self, *_): pass
            def do_POST(self):
                body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
                requests.append((self.path, self.headers.get('Authorization'), body))
                if self.path == '/chat/completions':
                    inference.append(json.loads(body))
                    if len(inference) == 1:
                        args = {'provider_id': 'usr_local-test', 'model_id': 'custom/local-test/image', 'prompt': 'Imagen de prueba desde el agente'}
                        message = {'role':'assistant','content':None,'tool_calls':[{'id':'prepare-one','type':'function','function':{'name':'prepare_generation','arguments':json.dumps(args)}}]}
                        finish = 'tool_calls'
                    else:
                        message = {'role':'assistant','content':'Encargo preparado. Pulsa Generar para enviarlo.'}
                        finish = 'stop'
                    self.reply({'choices':[{'message':message,'finish_reason':finish}],'usage':{'prompt_tokens':20,'completion_tokens':10}})
                    return
                if self.path == '/drop':
                    self.close_connection = True
                    self.connection.shutdown(socket.SHUT_RDWR)
                    self.connection.close()
                    return
                if self.path == '/upload':
                    self.reply({'file': {'url': 'https://example.com/input.png'}})
                    return
                self.reply({'images': [{'url': 'data:image/png;base64,' + base64.b64encode(image_bytes).decode()}]} if self.path == '/sync' else {'task': {'id': 'remote-test'}})
            def do_GET(self):
                requests.append((self.path, self.headers.get('Authorization'), b''))
                self.reply({'task': {'status': 'completed', 'progress': 100, 'outputs': ['https://example.com/result.mp4']}})
            def reply(self, value):
                raw = json.dumps(value).encode()
                self.send_response(200)
                self.send_header('Content-Length', str(len(raw)))
                self.send_header('Connection', 'close')
                self.end_headers()
                self.wfile.write(raw)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.service = CustomMedia(self.data, self.host.exchange, windows_keys, write_secret)
        self.config = {'id': 'local-test', 'name': 'Local test', 'baseUrl': f'http://127.0.0.1:{self.server.server_port}',
                       'healthPath': '/health',
                       'pollIntervalSeconds': 0,
                       'models': [{'id': 'image', 'name': 'Synthetic image', 'capability': 'TEXT_TO_IMAGE', 'createPath': '/sync',
                                   'requestTemplate': '{"model":"{{model}}","prompt":"{{prompt}}","seed":"{{seed}}"}',
                                   'params': [{'key': 'seed', 'type': 'seed', 'default': '-1'}],
                                   'mode': 'SYNC', 'resultPaths': ['images.*.url'], 'priceUsd': 0.01, 'priceUnit': 'generation'}]}
        self.service.save(self.config, 'synthetic-test-secret')
        self.model = 'custom/local-test/image'
        self.input = {'prompt': 'Prueba local', 'params': {'seed': '123'}}

    def tearDown(self):
        self.host.close()
        self.server.shutdown()
        self.server.server_close()
        self.temp.cleanup()

    def submit(self, identifier='one'):
        return self.service.submit(identifier, self.model, self.input, self.service.quote(self.model, self.input['params']))

    def test_sync_protected_key_and_durable_receipt(self):
        result = self.submit()
        self.assertEqual(result['status'], 'COMPLETED')
        self.assertTrue(result['outputs'][0].startswith('/media/'))
        self.assertEqual((self.data / result['outputs'][0].lstrip('/')).read_bytes(), self.image_bytes)
        self.assertNotIn(b'synthetic-test-secret', self.service.key_file.read_bytes())
        self.assertNotIn('synthetic-test-secret', self.service.config_file.read_text('utf-8'))
        self.assertNotIn('config', result)
        self.assertEqual(self.requests[0][1], 'Bearer synthetic-test-secret')
        self.assertEqual(json.loads(self.requests[0][2])['seed'], 123)
        restarted = CustomMedia(self.data, self.host.exchange, windows_keys, write_secret)
        self.assertEqual(restarted.submit('one', self.model, self.input, None)['outputs'], result['outputs'])
        self.assertEqual(len(self.requests), 1)
        with self.assertRaises(ValueError):
            restarted.submit('one', self.model, {'prompt': 'Other'}, None)

    def test_async_poll_uses_original_config(self):
        config = copy.deepcopy(self.config)
        m = config['models'][0]
        m.update(mode='ASYNC', createPath='/async', taskIdPath='task.id', pollPath='/jobs/{{task_id}}', statusPath='task.status', progressPath='task.progress', resultPaths=['task.outputs.*'])
        self.service.save(config)
        created = self.submit()
        self.assertEqual(created['status'], 'PROCESSING')
        self.assertEqual(created['providerTaskId'], 'remote-test')
        with self.assertRaises(ValueError): self.service.delete('local-test')
        config['models'][0]['pollPath'] = '/wrong/{{task_id}}'
        self.service.save(config)
        result = self.service.status('one')
        self.assertEqual(result['status'], 'COMPLETED')
        self.assertEqual(self.requests[-1][0], '/jobs/remote-test')
        self.assertEqual(result['outputs'], ['https://example.com/result.mp4'])
        with self.assertRaisesRegex(ValueError, 'No se ha cancelado'):
            self.service.command('cancel', {'id': 'one'})

    def test_uncertain_creation_never_resends(self):
        config = copy.deepcopy(self.config)
        config['models'][0]['createPath'] = '/drop'
        self.service.save(config)
        result = self.submit()
        self.assertEqual(result['status'], 'UNCERTAIN')
        self.assertTrue(result['uncertain'])
        restarted = CustomMedia(self.data, self.host.exchange, windows_keys, write_secret)
        self.assertEqual(restarted.submit('one', self.model, self.input, None)['status'], 'UNCERTAIN')
        self.assertEqual(restarted.status('one')['status'], 'UNCERTAIN')
        self.assertEqual(len(self.requests), 1)

    def test_validate_input_price_and_origin_before_http(self):
        self.input['params']['seed'] = 'not-a-number'
        with self.assertRaises(ValueError): self.submit()
        self.assertEqual(len(self.requests), 0)
        config = copy.deepcopy(self.config)
        config['models'][0]['createPath'] = 'https://elsewhere.example/generate'
        with self.assertRaises(ValueError): self.service.save(config)
        config = copy.deepcopy(self.config)
        config['models'][0]['priceUnit'] = 'million-pixels'
        self.service.save(config)
        with self.assertRaises(ValueError): self.service.quote(self.model, {})
        self.assertEqual(len(self.requests), 0)

    def test_local_reference_stays_out_of_rpc_and_quote_pins_config(self):
        media=self.data/'media';media.mkdir()
        identifier='c'*32+'.png';(media/identifier).write_bytes(self.image_bytes)
        config=copy.deepcopy(self.config)
        config['models'][0].update(capability='IMAGE_TO_IMAGE',requestTemplate='{"prompt":"{{prompt}}","images":"{{image_urls}}"}')
        self.service.save(config)
        self.input['imageUrls']=['/media/'+identifier]
        cost=self.service.quote(self.model,self.input['params'])
        config['models'][0]['providerModelId']='changed-model'
        self.service.save(config)
        with self.assertRaises(ValueError):self.service.submit('stale',self.model,self.input,cost)
        self.assertEqual(len(self.requests),0)
        result=self.submit()
        self.assertEqual(result['status'],'COMPLETED')
        body=json.loads(self.requests[0][2])
        self.assertTrue(body['images'][0].startswith('data:image/png;base64,'))
        self.assertEqual(base64.b64decode(body['images'][0].split(',',1)[1]),self.image_bytes)

    def test_restart_distinguishes_preparation_from_uncertain_creation(self):
        self.submit()
        path=self.service.job_file('one')
        original=json.loads(path.read_text('utf-8'))
        for stage,status in [('UPLOADING','FAILED'),('CREATING','UNCERTAIN')]:
            job={**original,'status':'SENDING','stage':stage}
            path.write_text(json.dumps(job),encoding='utf-8')
            restarted=CustomMedia(self.data,self.host.exchange,windows_keys,write_secret)
            self.assertEqual(restarted.status('one')['status'],status)
        self.assertEqual(len(self.requests),1)

    def test_upload_data_uri_and_multipart(self):
        media = self.data / 'media'
        media.mkdir()
        identifier = 'a' * 32 + '.png'
        (media / identifier).write_bytes(b'local-reference')
        result = self.service.upload('usr_local-test', identifier)
        self.assertEqual(base64.b64decode(result.split(',', 1)[1]), b'local-reference')
        config = copy.deepcopy(self.config)
        config.update(uploadMode='ENDPOINT', uploadPath='/upload', uploadUrlPath='file.url')
        self.service.save(config)
        self.assertEqual(self.service.upload('usr_local-test', identifier), 'https://example.com/input.png')
        self.assertIn(b'local-reference', self.requests[-1][2])
        self.assertEqual(self.requests[-1][1], 'Bearer synthetic-test-secret')
        self.assertEqual(self.service.command('test', {'id':'local-test'})['httpStatus'],200)

    def test_browser_manual_and_agent_confirm(self):
        """All paid routes remain disabled. Only this test's loopback API is allowed."""
        import local_host as local
        from playwright.sync_api import sync_playwright
        unsafe_name=copy.deepcopy(self.config)
        unsafe_name['name']='Local <b id="injected-provider">test</b>'
        self.service.save(unsafe_name)
        original = {k: getattr(local, k) for k in ['DATA','MEDIA','KEY_FILE','OFFLINE_QA','_agent','dispatch']}
        local.DATA = self.data
        local.MEDIA = self.data / 'media'
        local.MEDIA.mkdir(exist_ok=True)
        local.KEY_FILE = self.data / 'credentials.dpapi'
        local.OFFLINE_QA = True
        local._agent = None
        local.write_state({'jobs': [], 'draft': {'prompt':'','values':{},'media':[]}})
        before = local.dispatch
        def isolated_dispatch(keys, op, args):
            if op == 'customMedia':
                return self.service.command(args['op'], args.get('args', {}))
            return before(keys, op, args)
        local.dispatch = isolated_dispatch
        ui_server = local.LoopbackHTTPServer(('127.0.0.1', 0), local.Handler)
        ui_server.token = 'isolated-local-media-qa'
        ui_server.keys = {}
        threading.Thread(target=ui_server.serve_forever, daemon=True).start()
        try:
            with sync_playwright() as playwright:
                browser = playwright.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width':1600,'height':1000})
                errors = []
                network_errors = []
                page.on('pageerror', lambda e: errors.append(str(e)))
                page.on('requestfailed', lambda r: network_errors.append({'url':r.url,'failure':r.failure}))
                page.goto(f'http://127.0.0.1:{ui_server.server_port}/')
                page.wait_for_function('()=>typeof CustomMedia!=="undefined"&&CustomMedia.loaded&&Boolean(window.NexusAgentParity)')
                self.assertTrue(page.evaluate('Boolean(E)'), network_errors)
                page.evaluate("()=>{initial.qa=false;navigate('settings');}")
                self.assertEqual(page.locator('#injected-provider').count(),0)
                page.locator('[data-custom-edit="local-test"]').click()
                page.locator('#custom-media-form [name=config]').fill(json.dumps(self.config))
                page.get_by_role('button',name='Guardar y probar conexión').click()
                page.wait_for_function('()=>document.querySelector("#custom-media-diagnostic").textContent.includes("Conexión HTTP 200")')
                page.locator('#custom-media-form button[type=submit]').click()
                page.wait_for_function('()=>!document.querySelector("#dialog").open')
                # Manual route: current model contract -> estimate -> dispatch -> persisted library job.
                result = page.evaluate("async()=>{const model=await configuration('custom/local-test/image');const plan={model,prompt:'Manual local',values:E.defaults(model),media:[]};plan.cost=await quotePlan(plan);return await dispatch(plan);}")
                self.assertEqual(result['status'], 'COMPLETED')
                self.assertEqual(len([r for r in self.requests if r[0]=='/sync']), 1)
                page.wait_for_function('()=>!Agent.busy&&!CustomMedia.syncing&&!CustomMedia.needsIndex')
                # Agent route: actual local Chat inference -> prepare_generation -> no auto-send.
                endpoint = f'http://127.0.0.1:{self.server.server_port}'
                page.evaluate("async endpoint=>{await ensureAssistantReady();await agentCall('settings',{backend:'profile',mode:'COPILOT',brainProfile:{id:'local-brain',name:'Local brain',baseUrl:endpoint,modelId:'mock-model',capabilities:{tools:true,streaming:false}},brainKey:'synthetic-brain-key'});await agentCall('createProject',{name:'Proveedor personalizado QA'});navigate('assistant');}", endpoint)
                page.evaluate("async()=>{Agent.draft='Prepara una imagen con el generador local';await sendAgent();}")
                self.assertEqual(len([r for r in self.requests if r[0]=='/sync']), 1)
                plans = page.evaluate('Agent.snapshot.plans')
                self.assertTrue(plans, {'error':page.evaluate('Agent.error'),'paths':[r[0] for r in self.requests]})
                self.assertEqual(plans[0]['plan']['ref']['providerId'], 'usr_local-test')
                page.evaluate('async()=>{await generateAgentEntries(Agent.snapshot.plans);}')
                page.wait_for_function('()=>!Agent.busy&&!CustomMedia.syncing&&Agent.snapshot.index.assets.some(a=>a.uri===state.jobs[0].outputs[0])')
                self.assertEqual(len([r for r in self.requests if r[0]=='/sync']), 2)
                jobs = page.evaluate('state.jobs')
                self.assertEqual(jobs[0]['status'], 'COMPLETED')
                self.assertTrue(jobs[0].get('agentPlanId'))
                self.assertTrue(page.evaluate('Agent.snapshot.index.assets.some(a=>a.uri===state.jobs[0].outputs[0])'))
                # Same generated output can be selected and placed in the real Corte engine.
                page.evaluate('()=>navigate("editor")')
                page.wait_for_function('()=>Boolean(Corte.snapshot)&&!Agent.busy', timeout=45000)
                page.locator('[data-corte-place]').first.click()
                page.wait_for_selector('.corte-clip')
                out=Path(__file__).resolve().parents[2]/'outputs/windows-corte'
                out.mkdir(parents=True,exist_ok=True)
                page.screenshot(path=str(out/'custom-media-editor.png'),full_page=True)
                # The standard scheduler must route custom asynchronous IDs to this host,
                # rather than the Atlas endpoint used by older desktop providers.
                async_config=copy.deepcopy(self.config)
                async_model=copy.deepcopy(async_config['models'][0])
                async_model.update(id='async',name='Local asynchronous video',capability='TEXT_TO_VIDEO',mode='ASYNC',createPath='/async',taskIdPath='task.id',pollPath='/jobs/{{task_id}}',statusPath='task.status',resultPaths=['task.outputs.*'])
                async_config['models'].append(async_model)
                status=page.evaluate("async config=>{installCustomCatalog(await customCall('save',{config}));const model=await configuration('custom/local-test/async');const plan={model,prompt:'Vídeo local',values:E.defaults(model),media:[]};plan.cost=await quotePlan(plan);const job=await dispatch(plan);await poll(true);return job.status;}",async_config)
                self.assertEqual(status,'COMPLETED')
                self.assertTrue(any(r[0]=='/jobs/remote-test' for r in self.requests))
                self.assertFalse(errors, errors)
                browser.close()
        finally:
            ui_server.shutdown()
            ui_server.server_close()
            if local._agent:
                local._agent.close()
                local._agent.corte.close()
                local._agent.native.executor.shutdown()
            for k,v in original.items(): setattr(local,k,v)


if __name__ == '__main__': unittest.main(verbosity=2)
