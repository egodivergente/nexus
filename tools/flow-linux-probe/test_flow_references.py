import hashlib
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
import flow_references as refs
import recovery

PNG = b'\x89PNG\r\n\x1a\n' + b'test-fixture'
class Request:
    headers = {'content-type': 'image/png'}
    async def stream(self):
        yield PNG[:3]
        yield PNG[3:]

class ReferenceTests(unittest.IsolatedAsyncioTestCase):
    async def test_stream_upload_hash_and_private_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            value = await refs.receive_reference(Request(), root)
            self.assertEqual(value['reference_id'], 'ref_' + hashlib.sha256(PNG).hexdigest())
            path, = refs.reference_paths(root, [value['reference_id']])
            self.assertEqual(path.read_bytes(), PNG)
            self.assertFalse(list(path.parent.glob('.upload-*')))
            path.write_bytes(b'corrupt')
            with self.assertRaisesRegex(refs.ReferenceError, 'integrity'):
                refs.reference_paths(root, [value['reference_id']])
    async def test_bad_mime_and_size_are_rejected_without_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            request = Request()
            request.headers = {'content-type': 'image/jpeg'}
            with self.assertRaises(refs.ReferenceError):
                await refs.receive_reference(request, root)
            self.assertFalse(list((root / 'flow-references').iterdir()))
            request.headers = {'content-type': 'image/png', 'content-length': '33'}
            with patch.object(refs, 'MAX_BYTES', 32), self.assertRaisesRegex(refs.ReferenceError, 'too_large'):
                await refs.receive_reference(request, root)
    async def test_invalid_ids_paths_urls_duplicates_and_count_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            for ids in ['bad', ['../prefs'], ['https://other.test/img'], ['ref_' + 'a'*64]*2,
                        ['ref_' + 'a'*64]*13]:
                with self.assertRaises(refs.ReferenceError):
                    refs.reference_paths(Path(directory), ids)
    async def test_no_reference_upload_error_can_click_generate(self):
        async def invoke(action):
            return await action(object())
        with patch.object(recovery, 'FLOW_UI_LOCK', __import__('asyncio').Lock()), \
             patch.object(recovery, 'flow_controls', AsyncMock(return_value=(
                SimpleNamespace(is_editable=AsyncMock(return_value=True), evaluate=AsyncMock(return_value=0)),
                SimpleNamespace(click=AsyncMock())))) as controls, \
             patch.object(recovery, 'download_controls', AsyncMock(return_value=[])), \
             patch.object(recovery, 'attach_references', AsyncMock(side_effect=refs.ReferenceError('flow_references_unconfirmed'))), \
             patch.object(recovery, 'with_flow_project_page', side_effect=invoke):
            with self.assertRaises(recovery.FlowError):
                await recovery.drive_flow_submission('prompt', [Path('fixture.png')])
            controls.return_value[1].click.assert_not_awaited()
    async def test_reference_paths_reach_driver_once_and_replay_does_not_resubmit(self):
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(recovery, 'ROOT', Path(directory)), \
             patch.object(recovery, 'FLOW_JOB_LOCK', __import__('asyncio').Lock()), \
             patch.object(recovery, 'save_flow_jobs'), \
             patch.object(recovery, 'drive_flow_submission', AsyncMock(return_value=[])) as driver:
            recovery.FLOW_JOBS.clear()
            value = await refs.receive_reference(Request(), Path(directory))
            first, replay = await recovery.submit_flow_generation('prompt', 'image', 'client-request-123456', [value['reference_id']])
            second, replay = await recovery.submit_flow_generation('prompt', 'image', 'client-request-123456', [value['reference_id']])
            self.assertTrue(replay)
            self.assertEqual(first['job_id'], second['job_id'])
            driver.assert_awaited_once()
            self.assertEqual(driver.call_args.args[1][0].read_bytes(), PNG)
            self.assertNotIn('prompt', recovery.persisted_flow_job(first))
            recovery.FLOW_JOBS.clear()

if __name__ == '__main__':
    unittest.main()
