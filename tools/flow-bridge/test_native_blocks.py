import asyncio
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock, patch
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'desktop'))
import bridge_tools
import flow_quote
from video_guard import model_problem

class NativeBlocksTests(unittest.TestCase):
    def test_quote_accepts_default_and_native_block_counts(self):
        for count in (1, 2, 3, 4):
            with self.subTest(count=count):
                quote = AsyncMock(return_value={'credits': count * 5})
                with patch.object(flow_quote, 'quote', quote):
                    options = {} if count == 1 else {'count': count}
                    result = asyncio.run(bridge_tools.flow_get_quote(model='nano-pro', **options))
                self.assertEqual(result['status'], 'quoted')
                self.assertEqual(result['credits'], count * 5)
                quote.assert_awaited_once()
                self.assertEqual(quote.call_args.kwargs.get('count', 1), count)

    def test_invalid_quote_counts_do_not_reach_account(self):
        for count in (0, 5, True, 2.5, '4', None):
            with self.subTest(count=count):
                quote = AsyncMock()
                with patch.object(flow_quote, 'quote', quote):
                    result = asyncio.run(bridge_tools.flow_get_quote(count=count))
                self.assertEqual(result['status'], 'error')
                self.assertEqual(result['error_class'], 'ValueError')
                quote.assert_not_awaited()

    def test_every_file_is_verified_and_completion_status_survives_recovery(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(bridge_tools,'ROOT',Path(folder)):
            root=Path(folder)/'results';root.mkdir()
            paths=[]
            for index in range(4):
                path=root/f'{index}.png';Image.new('RGB',(4,4),(index,0,0)).save(path);paths.append(str(path))
            proof=bridge_tools.complete_block({'status':'completed','path':paths[0],'files':paths},4)
            self.assertEqual(len(proof['artifacts']),4)
            self.assertEqual(proof['status'],'completed')
            self.assertEqual(bridge_tools.complete_block(proof,4)['files'],paths)
            Image.new('RGB',(4,4),'white').save(paths[-1])
            with self.assertRaises(ValueError):bridge_tools.complete_block(proof,4)

    def test_video_guard_checks_all_four_models_and_exact_count(self):
        rows=[[[[None,None,[[['test']]]]],'veo_3_1_t2v_fast_ultra',2] for _ in range(4)]
        wire=lambda:'f.req='+json.dumps([[['YhhmEf',json.dumps([rows,[],[]])]]])
        self.assertIsNone(model_problem(wire(),count=4))
        self.assertIsNotNone(model_problem(wire(),count=1))
        rows[-1][1]='veo_3_1_t2v'
        self.assertIsNotNone(model_problem(wire(),count=4))

if __name__=='__main__':unittest.main()
