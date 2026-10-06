"""Source binding and at-most-once edits; no Google generation is submitted."""
import asyncio
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock,patch
from urllib.parse import urlencode
sys.path.insert(0,str(Path(__file__).parent))
import bridge_tools as bridge
import video_edit

class EditTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory()
        self.patch=patch.object(bridge,'ROOT',Path(self.folder.name));self.patch.start()
        self.source={'source_request_id':'original','source_sha256':'a'*64,'kind':'VIDEO',
            'media_id':'source-id','workflow_id':'source-workflow','project_id':'project-id','original_dimensions':[1280,720],
            'edit_frames':192,'aspect_code':2}
    def tearDown(self):self.patch.stop();self.folder.cleanup()

    def body(self,row):return urlencode({'f.req':json.dumps([[['jIps6',json.dumps([[row],None,None]),None,'generic']]])})

    async def test_structural_original_model_prompt_and_whole_clip_are_required(self):
        prompt='Change the light'
        row=[[None,'source-workflow',0,192],[None,None,[[[prompt]]]],'abra_edit',2,[None,'source-id']]
        self.assertIsNone(video_edit.submission_problem(self.body(row),self.source,prompt))
        for slot,value in [(0,[None,'other-id',0,192]),(0,[None,'source-workflow',24,192]),
            (0,[None,'source-workflow',0,240]),(4,[None,'other-source']),
            (2,'abra_t2v_8s'),(3,1),(1,[None,None,[[['source-id']]]])]:
            changed=list(row);changed[slot]=value
            self.assertIsNotNone(video_edit.submission_problem(self.body(changed),self.source,prompt))
        self.assertIsNotNone(video_edit.submission_problem(self.body(row),{**self.source,'edit_frames':300},prompt))

    async def test_same_request_submits_once_and_cannot_change_original_or_prompt(self):
        action=AsyncMock(return_value={'status':'needs_reconciliation'})
        with patch('video_edit.source_asset',return_value=self.source),patch('flow_session.ensure',new=AsyncMock()),patch('video_edit.operate',new=action):
            await bridge.flow_edit_video('edit','original','Change the light')
            await bridge.flow_edit_video('edit','original','Change the light')
            changed=await bridge.flow_edit_video('edit','original','Change the background')
        self.assertEqual(action.await_count,1)
        self.assertEqual(changed['status'],'error')

    async def test_ambiguous_submit_never_retries(self):
        action=AsyncMock(side_effect=TimeoutError())
        with patch('video_edit.source_asset',return_value=self.source),patch('flow_session.ensure',new=AsyncMock()),patch('video_edit.operate',new=action):
            first=await bridge.flow_edit_video('timeout','original','fixture')
            second=await bridge.flow_edit_video('timeout','original','fixture')
        self.assertEqual(first['status'],'needs_reconciliation')
        self.assertEqual(second['status'],'needs_reconciliation')
        self.assertEqual(action.await_count,1)

    async def test_images_cannot_be_sent_to_video_editor(self):
        with patch('video_edit.source_asset',return_value={**self.source,'kind':'IMAGE'}),patch('video_edit.operate',new=AsyncMock()) as action:
            result=await bridge.flow_edit_video('image','original','fixture')
        self.assertEqual(result['status'],'error');action.assert_not_awaited()

if __name__=='__main__':unittest.main()
