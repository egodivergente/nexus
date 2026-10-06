import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from PIL import Image
import bridge_tools
from video_guard import model_problem

class NativeBlocksTests(unittest.TestCase):
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
