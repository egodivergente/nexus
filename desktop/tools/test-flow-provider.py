import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import flow_provider as flow

class ProviderTests(unittest.TestCase):
    def test_ambiguous_submission_never_starts_second_worker(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(flow,'quote',return_value={'amount':0}),patch.object(flow.threading,'Thread') as worker:
            args={'request_id':'same-id','model':'nano2','prompt':'sphere','aspect':'1:1','cost':0}
            try:
                flow.submit(folder,args);flow.submit(folder,args)
                self.assertEqual(worker.call_count,1)
                with self.assertRaises(ValueError):flow.submit(folder,{**args,'prompt':'cube'})
            finally:flow.WORKERS.discard('same-id')
    def test_result_cannot_escape_private_results_folder(self):
        with tempfile.TemporaryDirectory() as folder:
            connection=flow.ledger(folder)
            result={'status':'completed','path':str(Path(folder)/'outside.mp4'),'sha256':'irrelevant'}
            with connection:connection.execute('INSERT INTO jobs VALUES (?,?,?)',('fixture','{}',json.dumps(result)))
            connection.close()
            with self.assertRaises(ValueError):flow.status(folder,'fixture')
    def test_lite_and_wrong_aspect_are_rejected(self):
        for args in ({'model':'nano_lite'},{'model':'veo_fast','aspect':'1:1'}):
            with self.assertRaises(ValueError):flow.parameters(args)

if __name__=='__main__':unittest.main()
