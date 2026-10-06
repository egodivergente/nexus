import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import AsyncMock,patch
from PIL import Image
import asset_process as asset
import bridge_tools as bridge

class ProcessTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.patches=[patch.object(bridge,'ROOT',self.root),patch.object(asset,'ROOT',self.root)]
        for p in self.patches:p.start()
        folder=self.root/'results';folder.mkdir();self.image=folder/'source.jpg'
        Image.new('RGB',(1024,1024),'blue').save(self.image)
        with bridge.database() as db:
            result=bridge.verified_file({'status':'completed','path':str(self.image),
                'flow_project_id':'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','flow_media_id':'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'})
            db.execute('INSERT INTO jobs VALUES (?,?,?,?,?)',('source','{}',None,json.dumps(result),'completed'))
    async def asyncTearDown(self):
        for p in reversed(self.patches):p.stop()
        self.temp.cleanup()
    async def test_unknown_source_never_opens_browser(self):
        with patch.object(asset,'operate',AsyncMock()) as operate:
            result=await bridge.flow_process_asset('action','missing','4k',50)
            self.assertEqual(result['status'],'error');operate.assert_not_awaited()
    async def test_reserved_action_never_repeats_after_timeout(self):
        with patch.object(asset,'operate',AsyncMock(side_effect=TimeoutError())) as operate:
            first=await bridge.flow_process_asset('action','source','2k',0)
            second=await bridge.flow_process_asset('action','source','2k',0)
            changed=await bridge.flow_process_asset('action','source','4k',0)
            self.assertEqual(operate.await_count,1)
            self.assertEqual(first['status'],'needs_reconciliation')
            self.assertEqual(second['status'],'needs_reconciliation')
            self.assertEqual(changed['status'],'error')
    async def test_completed_download_recovers_without_browser(self):
        path=self.root/'results/upscale.jpg';Image.new('RGB',(2048,2048),'blue').save(path)
        params={'operation':'process_asset','source_request_id':'source','resolution':'2k','expected_credits':0}
        result={**asset.source_asset('source'),'status':'downloaded','path':str(path),'resolution':'2k','request_id':'action'}
        with bridge.database() as db:db.execute('INSERT INTO jobs VALUES (?,?,?,?,?)',('action',json.dumps(params),None,json.dumps(result),'downloaded'))
        with patch.object(asset,'operate',AsyncMock()) as operate:
            result=await bridge.flow_process_asset('action','source','2k',0)
            self.assertEqual(result['status'],'completed');self.assertEqual(result['dimensions'],(2048,2048));operate.assert_not_awaited()
    async def test_source_hash_is_separate_from_output(self):
        source=asset.source_asset('source');self.assertIn('source_sha256',source);self.assertNotIn('sha256',source)
    async def test_image_resolution_checks_aspect_and_original(self):
        asset.check_dimensions('IMAGE','2k',{'dimensions':[2752,1536],'original_dimensions':[1376,768]})
        with self.assertRaises(ValueError):asset.check_dimensions('IMAGE','2k',{'dimensions':[1024,1024],'original_dimensions':[1024,1024]})
    async def test_menu_cost_and_disabled_options(self):
        self.assertEqual(asset.menu_option('VIDEO','4k','4K Resolución mejorada · 50 puntos')['credits'],50)
        self.assertEqual(asset.menu_option('IMAGE','2k','2K Resolución mejorada')['credits'],0)
        with self.assertRaises(ValueError):asset.menu_option('VIDEO','4k','4K Upgrade · unknown credits')
        with self.assertRaises(ValueError):asset.menu_option('VIDEO','4k','4K Upgrade',True)
        with self.assertRaises(ValueError):asset.menu_option('VIDEO','gif','270p Video')

if __name__=='__main__':unittest.main()
