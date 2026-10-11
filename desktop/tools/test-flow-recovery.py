"""Recover stable existing jobs without quote, generation or duplicate workers."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import flow_provider as flow
import local_host as host


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.data = Path(self.folder.name) / 'data'
        self.runtime = Path(self.folder.name) / 'runtime'
        (self.runtime / 'results').mkdir(parents=True)
        self.runtime_patch = patch.object(flow, 'RUNTIME', self.runtime)
        self.runtime_patch.start()
        self.source = self.runtime / 'results/result.png'
        self.source.write_bytes(b'owned-result-fixture')
        self.proof = {'status': 'completed', 'path': str(self.source),
                      'sha256': hashlib.sha256(self.source.read_bytes()).hexdigest()}

    def tearDown(self):
        flow.WORKERS.discard('existing')
        self.runtime_patch.stop()
        self.folder.cleanup()

    def reserve(self, result=None):
        connection = flow.ledger(self.data)
        with connection:
            connection.execute('INSERT INTO jobs VALUES (?,?,?)',
                ('existing', json.dumps({'model': 'nano2', 'prompt': 'original'}), json.dumps(result) if result else None))
        connection.close()

    def test_double_click_starts_one_read_worker_and_preserves_identifier(self):
        self.reserve()
        with patch.object(flow.threading, 'Thread') as thread, patch.object(flow, 'quote') as quote:
            first = flow.recover(self.data, 'existing')
            second = flow.recover(self.data, 'existing')
            self.assertEqual(thread.call_count, 1)
            self.assertEqual(first['id'], 'existing')
            self.assertEqual(second['id'], 'existing')
            self.assertTrue(first['reconciling'])
            self.assertEqual(thread.call_args.kwargs['name'], 'NexusFlowQueue')
            from flow_automation import database
            with database(flow,self.data) as db:
                self.assertEqual(db.execute('SELECT mode FROM flow_queue').fetchone()['mode'],'recover')
            quote.assert_not_called()
            revision = first['recovery_started_at']
        with patch.object(flow, 'call', return_value=self.proof) as call:
            flow.work(self.data, 'existing', {}, recover=True, recovery_started_at=revision)
            self.assertEqual(call.call_args.args[0], 'flow_get_result')
            self.assertEqual(call.call_args.args[1], {'request_id': 'existing'})
        completed = flow.status(self.data, 'existing')
        self.assertEqual(completed['status'], 'completed')
        self.assertEqual(completed['recovery_started_at'], revision)
        self.assertEqual((self.data / 'media' / completed['outputs'][0].split('/')[-1]).read_bytes(), self.source.read_bytes())

    def test_completed_cached_result_never_starts_worker_or_quote(self):
        self.reserve(self.proof)
        with patch.object(flow.threading, 'Thread') as thread, patch.object(flow, 'quote') as quote:
            self.assertEqual(flow.recover(self.data, 'existing')['status'], 'completed')
            thread.assert_not_called(); quote.assert_not_called()

    def test_unknown_identifier_and_traversal_never_start_worker(self):
        with patch.object(flow.threading, 'Thread') as thread:
            for identifier in ('unknown', '../profile', None):
                with self.assertRaises(ValueError): flow.recover(self.data, identifier)
            thread.assert_not_called()

    def test_restarted_recovery_is_uncertain_and_can_be_read_again(self):
        self.reserve({'status': 'in_progress', 'recovery_started_at': 1})
        old = flow.status(self.data, 'existing')
        self.assertEqual(old['status'], 'failed')
        self.assertTrue(old['uncertain'])
        with patch.object(flow.threading, 'Thread'):
            new = flow.recover(self.data, 'existing')
        self.assertGreater(new['recovery_started_at'], 1)

    def test_worker_failure_clears_activity_and_never_generates(self):
        self.reserve()
        flow.WORKERS.add('existing')
        with patch.object(flow, 'call', side_effect=TimeoutError()) as call:
            flow.work(self.data, 'existing', {}, recover=True, recovery_started_at=3)
        self.assertNotIn('existing', flow.WORKERS)
        self.assertEqual(call.call_args.args[0], 'flow_get_result')
        self.assertTrue(flow.status(self.data, 'existing')['uncertain'])

    def test_failed_thread_start_keeps_previous_evidence(self):
        previous = {'status': 'failed', 'detail': 'old evidence'}
        self.reserve(previous)
        with patch.object(flow.threading, 'Thread') as thread:
            thread.return_value.start.side_effect = RuntimeError()
            with self.assertRaises(RuntimeError): flow.recover(self.data, 'existing')
        self.assertNotIn('existing', flow.WORKERS)
        connection = flow.ledger(self.data)
        result=json.loads(connection.execute('SELECT result FROM jobs').fetchone()['result'])
        connection.close()
        self.assertEqual(result['detail'],previous['detail'])
        self.assertEqual(result['status'],'queued')

    def test_new_recovery_survives_old_windows_and_terminal_state(self):
        previous = [{'id': 'existing', 'status': 'FAILED', 'uncertain': True, 'flowRecoveryRevision': 1}]
        recovering = [{'id': 'existing', 'status': 'PROCESSING', 'uncertain': False, 'flowRecoveryRevision': 2}]
        active = host.merge_jobs(recovering, previous)
        self.assertEqual(active[0]['status'], 'PROCESSING')
        stale = host.merge_jobs(previous, active)
        self.assertEqual(stale[0]['status'], 'PROCESSING')
        completed = host.merge_jobs([{'id': 'existing', 'status': 'COMPLETED', 'flowRecoveryRevision': 2, 'outputs': ['result']}], active)
        self.assertEqual(host.merge_jobs(previous, completed)[0]['outputs'], ['result'])

    def test_lite_is_blocked_in_paid_host_even_with_valid_price(self):
        with patch.object(host, 'OFFLINE_QA', False), patch.object(host, 'provider_request') as provider:
            for model in ('google/nano-banana-2-lite/edit', 'crun/google/nano-banana-2-lite', 'nano_lite'):
                with self.assertRaisesRegex(ValueError, 'Lite'):
                    host.dispatch({}, 'generate', {'body': {'model': model}, 'cost': 0})
            with self.assertRaises(ValueError):host.dispatch({}, 'generate', {'body': {'model': 'allowed'}, 'cost': True})
            provider.assert_not_called()

    def test_malformed_read_result_cannot_leave_an_active_worker(self):
        self.reserve()
        flow.WORKERS.add('existing')
        with patch.object(flow, 'call', return_value=[]):
            flow.work(self.data, 'existing', {}, recover=True, recovery_started_at=4)
        self.assertNotIn('existing', flow.WORKERS)
        self.assertTrue(flow.status(self.data, 'existing')['uncertain'])

    def test_submission_thread_failure_never_orphans_or_resends_reservation(self):
        args = {'request_id':'existing','model':'nano2','aspect':'1:1','prompt':'original','cost':0}
        with patch.object(flow, 'quote', return_value={'amount':0}), patch.object(flow.threading, 'Thread') as thread:
            thread.return_value.start.side_effect = RuntimeError()
            with self.assertRaises(RuntimeError):flow.submit(self.data, args)
            self.assertNotIn('existing', flow.WORKERS)
            result = flow.submit(self.data, args)
            self.assertEqual(result['status'],'queued')
            self.assertEqual(thread.call_count, 1)


if __name__ == '__main__':
    unittest.main()
