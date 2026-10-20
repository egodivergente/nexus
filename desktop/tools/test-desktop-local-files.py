"""Real local imports, scene evidence/cut, Windows actions and browser/HTTP integration."""
import base64
import hashlib
import http.client
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from media_import import CHUNK_BYTES, MAX_IMPORT_BYTES, MediaImporter, validate
from native_operations import NativeOperations
import local_host as local


class ImportAcceptance(unittest.TestCase):
    def test_boundaries_integrity_and_incomplete_copy(self):
        self.assertEqual(validate('video.mp4', MAX_IMPORT_BYTES)[1], '.mp4')
        for size in [0, -1, True, MAX_IMPORT_BYTES+1]:
            with self.assertRaises(ValueError): validate('video.mp4', size)
        with tempfile.TemporaryDirectory(prefix='nexus-import-qa-') as tmp:
            importer = MediaImporter(Path(tmp)/'media')
            payload = b'original\x00bytes' * 100
            identifier = importer.begin('video.mp4', len(payload))['import_id']
            with self.assertRaises(ValueError): importer.finish(identifier)
            self.assertFalse((importer.directory/identifier).exists())
            encoded = base64.b64encode(payload).decode()
            importer.append(identifier, 0, encoded)
            with self.assertRaises(ValueError): importer.append(identifier, 0, encoded)
            result = importer.finish(identifier)
            self.assertEqual(result['sha256'], hashlib.sha256(payload).hexdigest())
            self.assertEqual((importer.directory/identifier).read_bytes(), payload)
            partial = importer.begin('cancel.mp4', 5)['import_id']
            importer.abort(partial)
            self.assertFalse((importer.directory/(partial+'.importing')).exists())
            with patch('media_import.shutil.disk_usage', return_value=type('Disk', (), {'free': 0})()):
                with self.assertRaises(ValueError): importer.begin('full.mp4', 10)

    @unittest.skipUnless(os.environ.get('NEXUS_TEST_4GIB') == '1', 'Enable real 4 GiB disk copy explicitly')
    def test_real_exact_four_gib_native_copy(self):
        with tempfile.TemporaryDirectory(prefix='nexus-4gib-qa-') as tmp:
            root = Path(tmp)
            source = root/'exact-4gib.mp4'
            with source.open('wb') as stream:
                stream.write(b'NEXUS-FOUR-GIB-START')
                stream.seek(MAX_IMPORT_BYTES-20)
                stream.write(b'NEXUS-FOUR-GIB-END!!!')
            # Fixed exact byte length, including both markers.
            with source.open('r+b') as stream: stream.truncate(MAX_IMPORT_BYTES)
            importer = MediaImporter(root/'media')
            copied = importer.copy(source)
            target = root/'media'/copied['id']
            self.assertEqual(source.stat().st_size, MAX_IMPORT_BYTES)
            self.assertEqual(target.stat().st_size, MAX_IMPORT_BYTES)
            with target.open('rb') as stream: actual = hashlib.file_digest(stream, 'sha256').hexdigest()
            self.assertEqual(actual, copied['sha256'])
            with source.open('rb') as stream: original = hashlib.file_digest(stream, 'sha256').hexdigest()
            self.assertEqual(actual, original)
            with source.open('ab') as stream: stream.write(b'x')
            with self.assertRaises(ValueError): importer.copy(source)
            print(json.dumps({'real_import_bytes': target.stat().st_size, 'sha256_verified': True, 'over_limit_rejected': True}))


class LocalActionsAcceptance(unittest.TestCase):
    def test_scene_evidence_and_precise_cut_preserve_original(self):
        with tempfile.TemporaryDirectory(prefix='nexus-scenes-qa-') as tmp:
            native = NativeOperations(Path(tmp), None, lambda: {}, lambda _: None, None, lambda: {})
            try:
                source = native.new_path('.mp4')
                native.encode(['-f', 'lavfi', '-i', 'color=c=red:s=320x240:r=25:d=2',
                               '-f', 'lavfi', '-i', 'color=c=blue:s=320x240:r=25:d=2',
                               '-f', 'lavfi', '-i', 'color=c=green:s=320x240:r=25:d=2',
                               '-filter_complex', '[0:v][1:v][2:v]concat=n=3:v=1:a=0[v]', '-map', '[v]', '-c:v', 'libx264', source])
                original = hashlib.sha256(source.read_bytes()).hexdigest()
                assets = [{'id': 'source', 'uri': '/media/'+source.name, 'type': 'VIDEO', 'role': 'REFERENCE',
                           'source': 'IMPORTED', 'displayName': 'Tres escenas', 'mimeType': 'video/mp4', 'projectId': 'qa', 'createdAt': 1}]
                queued = native.run({'operation': 'detect_video_scenes', 'input': {'asset_id': 'source'}, 'assets': assets})
                job = native.get_job({'edit_job_id': queued['edit_job_id'], 'wait_seconds': 20})
                self.assertEqual(job['state'], 'COMPLETED', job.get('error'))
                report = job['scene_analysis']
                self.assertEqual(report['detected_cuts_ms'], [2000, 4000])
                self.assertEqual(len(report['samples']), 3)
                self.assertTrue(job['_attachments'])
                self.assertIsNone(report['next_start_ms'])
                # Use the actual detected blue-shot boundaries, not invented times.
                selected = report['samples'][1]
                cut = native.run({'operation': 'edit_video_assets', 'input': {'action': 'trim', 'asset_ids': ['source'],
                                 'start_ms': selected['shot_start_ms'], 'end_ms': selected['shot_end_ms']}, 'assets': assets})
                result = native.get_job({'edit_job_id': cut['edit_job_id'], 'wait_seconds': 20})
                self.assertEqual(result['state'], 'COMPLETED', result.get('error'))
                self.assertAlmostEqual(result['results'][0]['durationSec'], 2, delta=.08)
                self.assertEqual(result['results'][0]['parentAssetId'], 'source')
                self.assertEqual(result['results'][0]['projectId'], 'qa')
                self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), original)
                from PIL import Image
                import io
                path = native.media/Path(result['results'][0]['uri']).name
                frame = native.encode(['-i', path, '-frames:v', '1', '-f', 'image2pipe', '-vcodec', 'mjpeg', 'pipe:1'])
                with Image.open(io.BytesIO(frame)) as image:
                    r, g, b = image.getpixel((160, 120))
                    self.assertGreater(b, max(r, g)+100)
            finally: native.executor.shutdown()

    def test_real_windows_file_import_and_command(self):
        with tempfile.TemporaryDirectory(prefix='nexus-windows-qa-') as tmp:
            data = Path(tmp)
            native = NativeOperations(data, None, lambda: {}, lambda _: None, None, lambda: {})
            try:
                source = data/'local test.txt';source.write_text('original de Windows', encoding='utf-8')
                def run(op, args): return native.run({'operation': op, 'input': args, 'assets': []})
                listing = run('list_windows_files', {'directory': tmp, 'query': 'local test'})
                self.assertEqual(listing['entries'][0]['path'], str(source))
                imported = run('import_windows_files', {'paths': [str(source)]})
                self.assertEqual(len(imported['_assets']), 1)
                self.assertEqual(source.read_text('utf-8'), 'original de Windows')
                result = run('run_windows_command', {'command': "Write-Output 'NEXUS local verificado'; exit 7", 'directory': tmp})
                self.assertEqual(result['exit_code'], 7)
                self.assertIn('NEXUS local verificado', result['stdout'])
                self.assertFalse(result['timed_out'])
                with patch('windows_operations.os.startfile') as launch:
                    self.assertTrue(run('open_windows_item', {'target': str(source)})['requested'])
                    launch.assert_called_once_with(str(source))
            finally: native.executor.shutdown()


class BrowserImportAcceptance(unittest.TestCase):
    def test_authenticated_chunks_gallery_index_and_range(self):
        from playwright.sync_api import sync_playwright
        saved = {key: getattr(local, key) for key in ['DATA', 'MEDIA', 'KEY_FILE', 'OFFLINE_QA', '_agent']}
        with tempfile.TemporaryDirectory(prefix='nexus-import-ui-') as tmp:
            data = Path(tmp);local.DATA = data;local.MEDIA = data/'media';local.MEDIA.mkdir()
            local.KEY_FILE = data/'credentials.dpapi';local.OFFLINE_QA = True;local._agent = None
            local.write_state({'jobs': [], 'draft': {'prompt': '', 'values': {}, 'media': []}})
            server = local.LoopbackHTTPServer(('127.0.0.1', 0), local.Handler)
            server.token = 'synthetic-import-session';server.keys = {}
            threading.Thread(target=server.serve_forever, daemon=True).start()
            try:
                origin = f'http://127.0.0.1:{server.server_port}'
                connection = http.client.HTTPConnection('127.0.0.1', server.server_port)
                connection.request('POST', '/rpc', json.dumps({'op': 'importBegin', 'args': {'name': 'bad.mp4', 'size': MAX_IMPORT_BYTES}}), {'Origin': origin})
                response = connection.getresponse();self.assertEqual(response.status, 403);response.read();connection.close()
                with sync_playwright() as p:
                    browser = p.chromium.launch(channel='msedge', headless=True)
                    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
                    errors = [];page.on('pageerror', lambda error: errors.append(str(error)))
                    page.goto(origin+'/');page.wait_for_function('()=>Boolean(initial)&&Boolean(window.NexusImports)')
                    page.evaluate("async()=>{await ensureAssistantReady();navigate('library');}")
                    page.wait_for_selector('[data-import-gallery]')
                    payload = b'Windows gallery import\n'*(CHUNK_BYTES//10)
                    source = data/'Browser multi block.txt';source.write_bytes(payload)
                    with page.expect_file_chooser() as chooser:
                        page.locator('[data-import-gallery]').click()
                    chooser.value.set_files(str(source))
                    page.wait_for_function("()=>Agent.snapshot?.index.assets.some(a=>a.displayName==='Browser multi block.txt')", timeout=60000)
                    asset = page.evaluate("()=>Agent.snapshot.index.assets.find(a=>a.displayName==='Browser multi block.txt')")
                    page.wait_for_selector('.gallery-piece')
                    self.assertFalse(page.locator('.gallery-empty').count())
                    self.assertEqual(page.locator('.gallery-piece').count(), 1)
                    copied = local.MEDIA/Path(asset['uri']).name
                    self.assertEqual(hashlib.sha256(copied.read_bytes()).digest(), hashlib.sha256(payload).digest())
                    self.assertEqual(copied.stat().st_size, len(payload))
                    # Range playback/download uses streaming and exact byte counts.
                    reply = page.request.get(origin+asset['uri'], headers={'Range': 'bytes=1-32'})
                    self.assertEqual(reply.status, 206);self.assertEqual(reply.body(), payload[1:33])
                    oversize = page.evaluate("async()=>{try{await rpc('importBegin',{name:'large.mp4',size:4*1024**3+1});return false;}catch{return true;}}")
                    self.assertTrue(oversize)
                    self.assertEqual(errors, [])
                    output = Path(__file__).resolve().parents[2]/'outputs/desktop-local-4gb'
                    output.mkdir(parents=True, exist_ok=True);page.screenshot(path=str(output/'gallery-import.png'))
                    browser.close()
            finally:
                server.shutdown();server.server_close()
                if local._agent:
                    local._agent.native.executor.shutdown();local._agent.close()
                for key, value in saved.items(): setattr(local, key, value)


if __name__ == '__main__': unittest.main(verbosity=2)
