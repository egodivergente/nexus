"""Batch transport, partial recovery and quote binding; no provider requests."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import flow_provider as flow
from flow_contract import normalize, quote_key, block_counts


class BlocksTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.data = Path(self.folder.name) / 'data'
        self.runtime = Path(self.folder.name) / 'runtime'
        (self.runtime / 'results').mkdir(parents=True)
        self.runtime_patch = patch.object(flow, 'RUNTIME', self.runtime)
        self.runtime_patch.start()

    def tearDown(self):
        self.runtime_patch.stop()
        self.folder.cleanup()

    def reserve(self, count):
        params = {'model':'nano-pro', 'aspect':'1:1', 'prompt':'A sphere', 'count':count}
        db = flow.ledger(self.data)
        with db: db.execute('INSERT INTO jobs VALUES (?,?,NULL)', ('batch', json.dumps(params)))
        db.close()
        return params

    def native_result(self, operation, args, request_id):
        artifacts=[]
        for index in range(args['count']):
            path=self.runtime/'results'/f'{request_id}-{index}.png'
            path.write_bytes(f'{request_id}-{index}'.encode())
            artifacts.append({'path':str(path), 'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        return {'status':'completed', 'artifacts':artifacts, **artifacts[0]}

    def test_quantity_and_price_identity(self):
        self.assertEqual(list(block_counts(28)), [4]*7)
        self.assertEqual(list(block_counts(10)), [4,4,2])
        self.assertEqual(normalize({'model':'nano-pro','count':28})['count'],28)
        self.assertNotEqual(quote_key({'model':'nano-pro'}),quote_key({'model':'nano-pro','count':4}))
        for count in (0,-1,2.5,True,'4'):
            with self.assertRaises(ValueError):normalize({'model':'nano-pro','count':count})

    def test_28_outputs_are_seven_native_blocks_with_complete_local_results(self):
        params=self.reserve(28)
        with patch.object(flow,'call',side_effect=self.native_result) as call:
            flow.work(self.data,'batch',params)
        self.assertEqual([c.args[1]['count'] for c in call.call_args_list],[4]*7)
        self.assertEqual(len(set(c.args[1]['request_id'] for c in call.call_args_list)),7)
        status=flow.status(self.data,'batch')
        self.assertEqual(status['status'],'completed')
        self.assertEqual(len(status['outputs']),28)
        self.assertTrue(all((self.data/url.lstrip('/')).exists() for url in status['outputs']))

    def test_ambiguous_block_stops_and_recovery_never_generates_future_blocks(self):
        params=self.reserve(10)
        def submit(operation,args,request_id):
            if request_id.endswith('b2'):return {'status':'needs_reconciliation'}
            return self.native_result(operation,args,request_id)
        with patch.object(flow,'call',side_effect=submit) as call:
            flow.work(self.data,'batch',params)
        self.assertEqual(call.call_count,2)
        self.assertEqual(len(flow.status(self.data,'batch')['outputs']),4)
        def recover(operation,args,request_id):
            self.assertEqual(operation,'flow_get_result')
            self.assertTrue(args['request_id'].endswith('b2'))
            return self.native_result(operation,{'count':4},args['request_id'])
        with patch.object(flow,'call',side_effect=recover) as call:
            flow.work(self.data,'batch',params,recover=True,recovery_started_at=123)
        self.assertEqual(call.call_count,1)
        status=flow.status(self.data,'batch')
        self.assertEqual(len(status['outputs']),8)
        self.assertEqual(status['blocks'][2]['status'],'queued')
        self.assertTrue(status['uncertain'])

    def test_total_price_reads_full_and_remainder_blocks(self):
        def quote(operation,args,request_id):
            return {'status':'quoted','credits':args.get('count',1)*5,'source':'test account settings'}
        with patch.object(flow,'available',return_value=True),patch.object(flow,'call',side_effect=quote) as call:
            result=flow.quote({'model':'nano-pro','count':10})
        self.assertEqual(result['amount'],50)
        self.assertEqual(sorted(c.args[1]['count'] for c in call.call_args_list),[2,4])

    def test_incomplete_native_block_is_not_reported_as_complete(self):
        params=self.reserve(8)
        with patch.object(flow,'call',side_effect=lambda op,args,key:self.native_result(op,{'count':1},key)) as call:
            flow.work(self.data,'batch',params)
        self.assertEqual(call.call_count,1)
        self.assertEqual(flow.status(self.data,'batch')['status'],'failed')

if __name__=='__main__':unittest.main()
