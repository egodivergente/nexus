import asyncio
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import AsyncMock, patch

import recovery


class AsyncSubmissionTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.patch = patch.object(recovery, 'ROOT', self.root)
        self.patch.start()
        recovery.FLOW_JOBS.clear()
        recovery.FLOW_TASKS.clear()
        recovery.FLOW_JOB_LOCK = asyncio.Lock()
        recovery.FLOW_UI_LOCK = asyncio.Lock()

    async def asyncTearDown(self):
        tasks = list(recovery.FLOW_TASKS.values())
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        self.patch.stop()
        self.tmp.cleanup()

    async def test_accepts_and_polls_while_login_is_still_running_and_replay_never_duplicates(self):
        waiting = asyncio.Event()

        async def recover():
            await waiting.wait()
            return 'ready'

        with patch.object(recovery, 'recover_flow_session', side_effect=recover), \
                patch.object(recovery, 'drive_flow_submission', AsyncMock(return_value=[])) as driver:
            job, replay = await asyncio.wait_for(recovery.submit_flow_generation(
                'private prompt', 'image', 'client-request-123456', defer=True), 0.2)
            await asyncio.sleep(0)
            snapshot = await asyncio.wait_for(recovery.get_flow_job_status(job['job_id']), 0.2)
            self.assertEqual(snapshot['state'], 'submitting')
            second, replay = await recovery.submit_flow_generation(
                'private prompt', 'image', 'client-request-123456', defer=True)
            self.assertTrue(replay)
            self.assertEqual(second['job_id'], job['job_id'])
            self.assertEqual(len(recovery.FLOW_TASKS), 1)
            driver.assert_not_awaited()
            with self.assertRaisesRegex(recovery.FlowError, 'flow_busy'):
                await recovery.submit_flow_generation('different', 'image', 'other-request-123456', defer=True)
            waiting.set()
            await asyncio.gather(*list(recovery.FLOW_TASKS.values()))
            driver.assert_awaited_once()
            self.assertEqual((await recovery.flow_job(job['job_id']))['state'], 'submitted')
            journal = (self.root / 'flow-jobs.json').read_text()
            self.assertNotIn('private prompt', journal)

    async def test_verification_is_visible_and_never_calls_generate(self):
        with patch.object(recovery, 'recover_flow_session', AsyncMock(return_value='verification_required')), \
                patch.object(recovery, 'drive_flow_submission', AsyncMock()) as driver:
            job, _ = await recovery.submit_flow_generation('p', 'image', 'client-request-123456', defer=True)
            await asyncio.gather(*list(recovery.FLOW_TASKS.values()))
            result = await recovery.get_flow_job_status(job['job_id'])
            self.assertEqual(result['state'], 'failed')
            self.assertEqual(result['error'], 'verification_required')
            driver.assert_not_awaited()

    async def test_timeout_after_click_is_uncertain_and_replayed_without_another_click(self):
        async def drive(prompt, paths, runs, on_run, on_phase):
            await on_phase('clicking')
            await asyncio.sleep(60)

        with patch.object(recovery, 'recover_flow_session', AsyncMock(return_value='ready')), \
                patch.object(recovery, 'FLOW_PREPARE_SECONDS', 0.04), \
                patch.object(recovery, 'drive_flow_submission', side_effect=drive) as driver:
            job, _ = await recovery.submit_flow_generation('p', 'image', 'client-request-123456', defer=True)
            await asyncio.gather(*list(recovery.FLOW_TASKS.values()))
            result, replay = await recovery.submit_flow_generation('p', 'image', 'client-request-123456', defer=True)
            self.assertTrue(replay)
            self.assertEqual(result['state'], 'uncertain')
            self.assertEqual(result['error'], 'submit_uncertain')
            driver.assert_awaited_once()

    async def test_restart_distinguishes_not_sent_from_click_started(self):
        with patch.object(recovery, 'drive_flow_submission', AsyncMock(return_value=[])):
            job, _ = await recovery.submit_flow_generation('p', 'image', 'client-request-123456')
        for clicked in (False, True):
            await recovery.update_flow_job(job['job_id'], state='submitting', click_started=clicked)
            recovery.load_flow_jobs()
            result = await recovery.flow_job(job['job_id'])
            self.assertEqual(result['state'], 'uncertain' if clicked else 'failed')
            self.assertEqual(result['error'], 'submit_uncertain' if clicked else 'bridge_restarted_before_submit')

    async def test_delayed_result_remains_queryable_without_resubmission(self):
        with patch.object(recovery, 'drive_flow_submission', AsyncMock(return_value=[])):
            job, _ = await recovery.submit_flow_generation('p', 'image', 'client-request-123456')
        job = await recovery.update_flow_job(job['job_id'], created_at=time.time() - 301)
        with patch.object(recovery, 'with_flow_project_page', AsyncMock(return_value=[])):
            result = await recovery.refresh_flow_job(job)
        self.assertEqual(result['state'], 'generating')
        self.assertEqual(result['error'], 'generation_delayed')
        payload = recovery.public_flow_job(result)
        self.assertGreaterEqual(payload['elapsed_seconds'], 301)
        self.assertEqual(payload['phase'], 'waiting_results')
        job = await recovery.update_flow_job(job['job_id'], runs=2, expected_results=4)
        with patch.object(recovery, 'with_flow_project_page', AsyncMock(return_value=[])):
            batch = await recovery.refresh_flow_job(job)
        self.assertEqual(batch['error'], 'generation_delayed')

    async def test_legacy_preparation_journal_cannot_claim_no_click_occurred(self):
        with patch.object(recovery, 'drive_flow_submission', AsyncMock(return_value=[])):
            job, _ = await recovery.submit_flow_generation('p', 'image', 'client-request-123456')
        job['state'] = 'submitting'
        job.pop('click_started')
        (self.root / 'flow-jobs.json').write_text(json.dumps([job]))
        recovery.load_flow_jobs()
        restored = await recovery.flow_job(job['job_id'])
        self.assertEqual(restored['state'], 'uncertain')
        self.assertEqual(restored['error'], 'submit_uncertain')


if __name__ == '__main__':
    unittest.main()
