"""Local fixtures for library integrity, atomic downloads and RPC input guards."""
import copy
import http.client
import io
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import local_host as host


class Response(io.BytesIO):
    headers = {}


class IntegrityTests(unittest.TestCase):
    def test_stale_window_keeps_jobs_from_every_provider(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(host, 'DATA', Path(folder)):
            host.write_state({'jobs': [{'id': 'a', 'providerId': 'atlas_cloud', 'status': 'PROCESSING'}]})
            stale = host.read_state()
            fresh = copy.deepcopy(stale)
            fresh['jobs'] += [{'id': p, 'providerId': p, 'status': 'COMPLETED', 'outputs': [p]}
                             for p in ('runway', 'crun', 'google_flow')]
            host.write_state(fresh)
            host.write_state(stale)
            self.assertEqual(len(host.read_state()['jobs']), 4)
            self.assertEqual(len(stale['jobs']), 1)

    def test_terminal_result_cannot_regress(self):
        for status in ('SENDING', 'PROCESSING', 'FAILED'):
            result = host.merge_jobs([{'id': 'a', 'status': status, 'outputs': [], 'uncertain': True}],
                                     [{'id': 'a', 'status': 'COMPLETED', 'outputs': ['original'], 'remoteId': 'remote'}])[0]
            self.assertEqual(result['status'], 'COMPLETED')
            self.assertEqual(result['outputs'], ['original'])
            self.assertEqual(result['remoteId'], 'remote')
            self.assertFalse(result['uncertain'])

    def test_reconciliation_can_complete_uncertain_failure(self):
        result = host.merge_jobs([{'id': 'a', 'status': 'COMPLETED', 'outputs': ['recovered']}],
                                 [{'id': 'a', 'status': 'FAILED', 'error': 'uncertain', 'uncertain': True}])[0]
        self.assertEqual(result['outputs'], ['recovered'])
        self.assertNotIn('error', result)
        self.assertFalse(result['uncertain'])

    def test_progress_and_partial_outputs_are_preserved(self):
        result = host.merge_jobs([{'id': 'a', 'status': 'PROCESSING', 'outputs': []}],
                                 [{'id': 'a', 'status': 'PROCESSING', 'outputs': ['partial']}])[0]
        self.assertEqual(result['status'], 'PROCESSING')
        self.assertEqual(result['outputs'], ['partial'])
        failed = host.merge_jobs([{'id': 'a', 'status': 'PROCESSING'}],
                                 [{'id': 'a', 'status': 'FAILED', 'error': 'confirmed'}])[0]
        self.assertEqual(failed['status'], 'FAILED')

    def test_completed_snapshot_cannot_remove_other_outputs(self):
        result = host.merge_jobs([{'id': 'a', 'status': 'COMPLETED', 'outputs': ['first', 'new']}],
                                 [{'id': 'a', 'status': 'COMPLETED', 'outputs': ['first', 'second']}])[0]
        self.assertEqual(result['outputs'], ['first', 'second', 'new'])

    def download(self, destination, response, copy_error=None, replace_error=None):
        with patch.object(host.tk, 'Tk', return_value=Mock()), \
             patch.object(host.filedialog, 'asksaveasfilename', return_value=str(destination)), \
             patch.object(host.OPENER, 'open', return_value=response):
            if copy_error:
                with patch.object(host.shutil, 'copyfileobj', side_effect=copy_error):
                    return host.save_remote('https://example.invalid/result.mp4')
            if replace_error:
                with patch.object(host.os, 'replace', side_effect=replace_error):
                    return host.save_remote('https://example.invalid/result.mp4')
            return host.save_remote('https://example.invalid/result.mp4')

    def test_failed_download_preserves_existing_file(self):
        with tempfile.TemporaryDirectory() as folder:
            destination = Path(folder) / 'result.mp4'
            destination.write_bytes(b'ORIGINAL')
            with self.assertRaises(TimeoutError):
                self.download(destination, Response(b'new'), copy_error=TimeoutError())
            self.assertEqual(destination.read_bytes(), b'ORIGINAL')
            self.assertEqual(list(Path(folder).iterdir()), [destination])

    def test_complete_download_replaces_atomically(self):
        with tempfile.TemporaryDirectory() as folder:
            destination = Path(folder) / 'result.mp4'
            destination.write_bytes(b'ORIGINAL')
            self.assertTrue(self.download(destination, Response(b'NEW')))
            self.assertEqual(destination.read_bytes(), b'NEW')
            self.assertEqual(list(Path(folder).iterdir()), [destination])

    def test_short_response_and_replace_failure_preserve_original(self):
        with tempfile.TemporaryDirectory() as folder:
            destination = Path(folder) / 'result.mp4'
            destination.write_bytes(b'ORIGINAL')
            response = Response(b'short')
            response.headers = {'Content-Length': '20'}
            with self.assertRaises(ValueError):
                self.download(destination, response)
            with self.assertRaises(PermissionError):
                self.download(destination, Response(b'new'), replace_error=PermissionError())
            self.assertEqual(destination.read_bytes(), b'ORIGINAL')
            self.assertEqual(list(Path(folder).iterdir()), [destination])

    def test_http_guards_reject_malformed_messages_without_dispatch(self):
        server = host.ThreadingHTTPServer(('127.0.0.1', 0), host.Handler)
        server.token, server.keys = 'fixture-token', {}
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            origin = f'http://127.0.0.1:{server.server_port}'
            headers = {'Origin': origin, 'X-Nexus-Session': server.token}
            with patch.object(host, 'dispatch', return_value=True) as dispatch:
                for payload in ('[]', '{"op":"save","args":[]}', '{"op":42}'):
                    connection = http.client.HTTPConnection('127.0.0.1', server.server_port)
                    connection.request('POST', '/rpc', payload, headers)
                    response = connection.getresponse()
                    self.assertEqual(response.status, 400)
                    response.read(); connection.close()
                for changed, expected in (({'Origin': 'https://example.invalid'}, 403),
                                          ({'X-Nexus-Session': 'wrong'}, 403),
                                          ({'Content-Length': 'invalid'}, 400)):
                    connection = http.client.HTTPConnection('127.0.0.1', server.server_port)
                    connection.request('POST', '/rpc', '{}', {**headers, **changed})
                    response = connection.getresponse()
                    self.assertEqual(response.status, expected)
                    response.read(); connection.close()
                dispatch.assert_not_called()
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=2)

    def test_deleted_job_ids_prevent_stale_window_resurrection(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(host, 'DATA', Path(folder)):
            host.write_state({'jobs': [{'id': 'keep', 'status': 'COMPLETED'}, {'id': 'remove', 'status': 'FAILED'}]})
            stale = host.read_state()
            host.write_state({'jobs': [{'id': 'keep', 'status': 'COMPLETED'}], 'deletedJobIds': ['remove']})
            host.write_state(stale)
            saved = host.read_state()
            self.assertEqual([j['id'] for j in saved['jobs']], ['keep'])
            self.assertIn('remove', saved.get('deletedJobIds', []))

    def test_generate_persists_remote_id_before_http_response_returns(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(host, 'DATA', Path(folder)), patch.object(host, 'OFFLINE_QA', False):
            host.write_state({'jobs': [{'id': 'job-1', 'providerId': 'atlas_cloud', 'status': 'SENDING'}]})
            with patch.object(host, 'provider_request', return_value={'data': {'id': 'pred-999'}}):
                with self.assertRaises(ValueError):
                    host.dispatch({'atlas_cloud': 'key'}, 'generate', {
                        'provider': 'atlas_cloud',
                        'path': '/api/v1/model/generateImage',
                        'body': {'model': 'flux', 'prompt': 'test'},
                        'cost': 0.05,
                    })
                res = host.dispatch({'atlas_cloud': 'key'}, 'generate', {
                    'job_id': 'job-1',
                    'provider': 'atlas_cloud',
                    'path': '/api/v1/model/generateImage',
                    'body': {'model': 'flux', 'prompt': 'test'},
                    'cost': 0.05,
                })
            self.assertEqual(res['data']['id'], 'pred-999')
            persisted = host.read_state()['jobs'][0]
            self.assertEqual(persisted['remoteId'], 'pred-999')
            self.assertEqual(persisted['status'], 'PROCESSING')

    def test_concurrent_generate_responses_in_reverse_order_assign_exact_job_ids(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(host, 'DATA', Path(folder)), patch.object(host, 'OFFLINE_QA', False):
            host.write_state({'jobs': [
                {'id': 'job-1', 'providerId': 'atlas_cloud', 'status': 'SENDING'},
                {'id': 'job-2', 'providerId': 'atlas_cloud', 'status': 'SENDING'},
            ]})
            both_entered = threading.Barrier(2, timeout=5)
            second_persisted = threading.Event()
            results = {}
            errors = []

            def fake_provider_request(keys, provider, path, method, body, paid=False):
                both_entered.wait()
                if body['prompt'] == 'first':
                    self.assertTrue(second_persisted.wait(timeout=5))
                    return {'data': {'id': 'remote-first'}}
                return {'data': {'id': 'remote-second'}}

            def worker(job_id, prompt):
                try:
                    results[job_id] = host.dispatch({'atlas_cloud': 'key'}, 'generate', {
                        'job_id': job_id,
                        'provider': 'atlas_cloud',
                        'path': '/api/v1/model/generateImage',
                        'body': {'model': 'flux', 'prompt': prompt},
                        'cost': 0.05,
                    })
                    if job_id == 'job-2':
                        second_persisted.set()
                except Exception as exc:
                    errors.append(exc)
                    second_persisted.set()

            t1 = threading.Thread(target=worker, args=('job-1', 'first'))
            t2 = threading.Thread(target=worker, args=('job-2', 'second'))
            with patch.object(host, 'provider_request', side_effect=fake_provider_request):
                t1.start()
                t2.start()
                t1.join(timeout=5)
                t2.join(timeout=5)
            self.assertEqual(errors, [])
            jobs_by_id = {j['id']: j for j in host.read_state()['jobs']}
            self.assertEqual(jobs_by_id['job-1'].get('remoteId'), 'remote-first')
            self.assertEqual(jobs_by_id['job-2'].get('remoteId'), 'remote-second')
            self.assertEqual(jobs_by_id['job-1'].get('status'), 'PROCESSING')
            self.assertEqual(jobs_by_id['job-2'].get('status'), 'PROCESSING')

    def test_installation_root_check_rejects_documents_chatgpt_nexus_and_reports_194(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(host, 'DATA', Path(folder)), patch.object(host, 'OFFLINE_QA', True):
            self.assertFalse(host.is_same_installation_root({}, host.ROOT))
            self.assertFalse(host.is_same_installation_root({'root': r'C:\Users\User\Documents\ChatGPT\NEXUS\desktop'}, host.ROOT))
            self.assertTrue(host.is_same_installation_root({'root': str(host.ROOT)}, host.ROOT))
            info = host.dispatch({}, 'init', {})
            self.assertEqual(info.get('version'), '2026.10.18-corte-preview.1')

    def test_startup_onboarding_includes_official_logo_and_version_195(self):
        ui_root = host.ROOT / 'ui'
        logo_path = ui_root / 'branding' / 'nexus-logo.png'
        self.assertTrue(logo_path.is_file())
        self.assertGreater(logo_path.stat().st_size, 100000)
        design_js = (ui_root / 'desktop-design.js').read_text(encoding='utf-8')
        self.assertIn('nx-startup-onboarding', design_js)
        self.assertIn('branding/nexus-logo.png', design_js)
        self.assertIn('2026.10.18-corte-preview.1', design_js)
        self.assertIn('data-nx-version', design_js)


if __name__ == '__main__':
    unittest.main()


