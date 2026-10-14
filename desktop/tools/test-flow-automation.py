"""Real queue/SQLite/thread lifecycle with intercepted transport; zero provider calls."""
import asyncio
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
import subprocess
from unittest.mock import patch, AsyncMock

sys.path[:0]=[str(Path(__file__).resolve().parents[1]),str(Path(__file__).resolve().parents[2]/'tools/flow-bridge')]
import flow_provider as flow
import flow_automation as automation
import flow_health


class QueueTests(unittest.TestCase):
    def test_account_direct_has_no_quote_or_confirmation_gate(self):
        with patch.object(flow,'call',side_effect=self.proof):
            flow.submit(self.data,self.args(account_direct=True,cost=None))
            self.settled()
            self.assertEqual(flow.status(self.data,'one')['status'],'completed')
            self.quote.assert_not_called()
        with self.assertRaisesRegex(ValueError,'20'):
            flow.submit(self.data,self.args('over',account_direct=True,count=21,cost=None))
        with patch.object(flow,'call',side_effect=self.proof) as call:
            flow.submit(self.data,{'request_id':'upscale1','model':'image_upscale','source_request_id':'orig1','resolution':'2k','account_direct':True,'cost':0})
            self.settled()
            self.assertEqual(call.call_args.args[0],'flow_process_asset')
            self.assertIs(type(call.call_args.args[1]['expected_credits']),int)
    def test_direct_uncertain_does_not_freeze_other_requested_jobs(self):
        flow.automation_configure(self.data,{'paused':True})
        flow.submit(self.data,self.args(account_direct=True,cost=None))
        flow.submit(self.data,self.args('two',account_direct=True,cost=None));self.settled()
        def send(op,args,key):
            return {'status':'needs_reconciliation'} if key=='one' else self.proof(op,args,key)
        with patch.object(flow,'call',side_effect=send) as call:
            flow.automation_configure(self.data,{'paused':False});self.settled()
            self.assertEqual([x.args[2] for x in call.call_args_list],['one','two'])
        self.assertEqual(flow.status(self.data,'two')['status'],'completed')
        self.assertFalse(flow.automation_overview(self.data)['policy']['paused'])
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.data=self.root/'data';self.runtime=self.root/'runtime';(self.runtime/'results').mkdir(parents=True)
        self.runtime_patch=patch.object(flow,'RUNTIME',self.runtime);self.runtime_patch.start()
        self.quote_patch=patch.object(flow,'quote',return_value={'amount':0});self.quote=self.quote_patch.start()
        self.release=threading.Event()
    def tearDown(self):
        self.release.set()
        self.wait(lambda:not automation.RUNNERS)
        self.quote_patch.stop();self.runtime_patch.stop();self.temp.cleanup()
    def wait(self,condition):
        end=time.monotonic()+5
        while not condition():
            if time.monotonic()>end:self.fail('Queue did not settle')
            time.sleep(.01)
    def args(self,id='one',**extra):
        return {'request_id':id,'model':'nano-pro','aspect':'1:1','prompt':'offline fixture','cost':0,**extra}
    def proof(self,op,args,key):
        file=self.runtime/'results'/f'{key}.png';file.write_bytes(b'fixture-'+key.encode())
        return {'status':'completed','path':str(file),'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),
                'requested_model':'nano-pro','model_confirmed':False}
    def settled(self):self.wait(lambda:not automation.RUNNERS)
    def test_single_consumer_covers_full_execution_and_multiple_windows(self):
        started=threading.Event();calls=[]
        def send(op,args,key):
            calls.append(key)
            if len(calls)==1:started.set();self.release.wait(5)
            return self.proof(op,args,key)
        with patch.object(flow,'call',side_effect=send):
            flow.submit(self.data,self.args());self.assertTrue(started.wait(2))
            flow.submit(self.data,self.args('two'));flow.submit(self.data,self.args('two'))
            self.assertEqual(calls,['one']);self.assertEqual(flow.status(self.data,'two')['status'],'queued')
            self.release.set();self.settled()
        self.assertEqual(calls,['one','two'])
        self.assertFalse(flow.status(self.data,'one')['model_confirmed'])
        self.assertEqual(flow.status(self.data,'one')['requested_model'],'nano-pro')
        self.assertIn('execution_ms',flow.status(self.data,'one')['timings'])
    def test_profile_gate_serializes_different_data_directories(self):
        started=threading.Event();calls=[]
        def send(op,args,key):
            calls.append(key)
            if key=='one':started.set();self.release.wait(5)
            return self.proof(op,args,key)
        with patch.object(flow,'call',side_effect=send):
            flow.submit(self.data,self.args());self.assertTrue(started.wait(2))
            flow.submit(self.root/'other',self.args('two'));time.sleep(.1)
            self.assertEqual(calls,['one']);self.release.set();self.settled()
        self.assertEqual(calls,['one','two'])
    def test_pause_cancel_and_same_id_cannot_send_again(self):
        flow.automation_configure(self.data,{'paused':True})
        with patch.object(flow,'call') as call:
            flow.submit(self.data,self.args());self.settled()
            self.assertEqual(flow.automation_cancel(self.data,'one')['status'],'not_sent')
            flow.automation_configure(self.data,{'paused':False});self.settled()
            self.assertEqual(flow.submit(self.data,self.args())['status'],'not_sent');call.assert_not_called()
    def test_restart_only_recovers_running_and_leaves_unsubmitted_not_sent(self):
        flow.automation_configure(self.data,{'paused':True})
        flow.submit(self.data,self.args('queued'));self.settled()
        with flow.LOCK,automation.database(flow,self.data) as db:
            db.execute('INSERT INTO jobs VALUES (?,?,?)',('running',json.dumps({'model':'nano-pro','aspect':'1:1','prompt':'original'}),json.dumps({'status':'in_progress'})))
            db.execute("INSERT INTO flow_queue(id,mode,phase,created) VALUES ('running','submit','running',?)",(time.time(),))
        with patch.object(flow,'call',side_effect=self.proof) as call:
            flow.automation_initialize(self.data);self.settled()
            self.assertEqual([x.args[0] for x in call.call_args_list],['flow_get_result'])
            self.assertEqual(call.call_args.args[1],{'request_id':'running'})
        self.assertEqual(flow.status(self.data,'queued')['status'],'not_sent')
        self.assertEqual(flow.status(self.data,'running')['status'],'completed')
    def test_uncertain_result_pauses_future_spending(self):
        flow.automation_configure(self.data,{'paused':True})
        flow.submit(self.data,self.args());flow.submit(self.data,self.args('two'));self.settled()
        with patch.object(flow,'call',return_value={'status':'needs_reconciliation'}) as call:
            flow.automation_configure(self.data,{'paused':False});self.settled()
            self.assertEqual(call.call_count,1)
        self.assertTrue(flow.automation_overview(self.data)['policy']['paused'])
        self.assertEqual(flow.status(self.data,'two')['status'],'queued')
    def test_budget_pending_count_and_unknown_cost_are_server_enforced(self):
        flow.automation_configure(self.data,{'paused':True,'max_credits':5,'max_pending':2,'max_results':4})
        self.quote.return_value={'amount':3}
        flow.submit(self.data,self.args(cost=3));self.settled()
        with self.assertRaisesRegex(ValueError,'presupuesto'):flow.submit(self.data,self.args('two',cost=3))
        with self.assertRaisesRegex(ValueError,'resultados'):flow.submit(self.data,self.args('many',count=5,cost=3))
        self.quote.return_value={'amount':None}
        with self.assertRaisesRegex(ValueError,'sin importe'):flow.submit(self.data,self.args('unknown',cost=None))
        flow.automation_configure(self.data,{'allow_unknown_cost':True})
        flow.submit(self.data,self.args('unknown',cost=None));self.settled()
        self.quote.return_value={'amount':0}
        with self.assertRaisesRegex(ValueError,'llena'):flow.submit(self.data,self.args('third'))
    def test_queued_price_change_never_calls_generation(self):
        flow.automation_configure(self.data,{'paused':True});flow.submit(self.data,self.args());self.settled()
        self.quote.return_value={'amount':10}
        with patch.object(flow,'call') as call:
            flow.automation_configure(self.data,{'paused':False});self.settled();call.assert_not_called()
        self.assertEqual(flow.status(self.data,'one')['status'],'not_sent')
    def test_policy_rejects_invalid_and_never_exposes_private_data(self):
        for changes in [{'max_results':121},{'max_credits':float('nan')},{'paused':'yes'},{'private':'secret'}]:
            with self.assertRaises(ValueError):flow.automation_configure(self.data,changes)
        flow.automation_configure(self.data,{'paused':True});flow.submit(self.data,self.args());self.settled()
        out=json.dumps(flow.automation_overview(self.data));self.assertNotIn('offline fixture',out);self.assertNotIn(str(self.root),out)
    def test_total_results_are_limited_across_independent_requests(self):
        flow.automation_configure(self.data,{'paused':True,'max_total_results':4})
        flow.submit(self.data,self.args(count=3));self.settled()
        with self.assertRaisesRegex(ValueError,'total de resultados'):flow.submit(self.data,self.args('two',count=2))
    def test_os_profile_lock_blocks_another_process(self):
        code="import sys\nfrom flow_automation import profile_gate\ntry:\n with profile_gate(sys.argv[1],timeout=.1): pass\nexcept RuntimeError: sys.exit(7)\n"
        with automation.profile_gate(self.runtime):
            p=subprocess.run([sys.executable,'-c',code,str(self.runtime)],cwd=Path(__file__).resolve().parents[1],capture_output=True,timeout=10)
        self.assertEqual(p.returncode,7,p.stderr.decode(errors='replace'))
    def test_exception_after_transport_is_uncertain_never_not_sent(self):
        with patch.object(flow,'work',side_effect=RuntimeError('after acceptance')):
            flow.submit(self.data,self.args());self.settled()
        result=flow.status(self.data,'one')
        self.assertTrue(result['uncertain']);self.assertEqual(result['queue_phase'],'uncertain')
    def test_damaged_completed_file_is_recovered_using_the_same_id(self):
        proof=self.proof('unused',{},'damaged');Path(proof['path']).unlink()
        with flow.LOCK,automation.database(flow,self.data) as db:
            db.execute('INSERT INTO jobs VALUES (?,?,?)',('damaged',json.dumps({'model':'nano-pro','prompt':'original'}),json.dumps(proof)))
        with patch.object(flow,'call',side_effect=self.proof) as call:
            flow.recover(self.data,'damaged');self.settled()
            self.assertEqual(call.call_args.args[0],'flow_get_result');self.assertEqual(call.call_args.args[1],{'request_id':'damaged'})
        self.assertEqual(flow.status(self.data,'damaged')['status'],'completed')
    def test_corrupted_local_copy_of_same_size_is_recovered_from_valid_original(self):
        with patch.object(flow,'call',side_effect=self.proof):
            flow.submit(self.data,self.args('same_size_corrupt'));self.settled()
        first=flow.status(self.data,'same_size_corrupt')
        self.assertEqual(first['status'],'completed')
        local_copy=self.data/'media'/Path(first['outputs'][0]).name
        original_bytes=local_copy.read_bytes()
        expected_sha=hashlib.sha256(original_bytes).hexdigest()
        corrupted_bytes=(b'\x00' if original_bytes[:1]!=b'\x00' else b'\xff')+original_bytes[1:]
        self.assertEqual(len(corrupted_bytes),len(original_bytes))
        self.assertNotEqual(hashlib.sha256(corrupted_bytes).hexdigest(),expected_sha)
        local_copy.write_bytes(corrupted_bytes)
        second=flow.status(self.data,'same_size_corrupt')
        self.assertEqual(second['status'],'completed')
        self.assertEqual(local_copy.read_bytes(),original_bytes)
        self.assertEqual(hashlib.sha256(local_copy.read_bytes()).hexdigest(),expected_sha)
    def test_quality_and_count_constraints_reach_catalog_and_contract(self):
        with patch.object(flow,'available',return_value=True):models=flow.catalog()
        for m in models:
            for p in m['parameters']:
                if p['key']=='count':self.assertEqual(p['maxValue'],120)
                if p['key']=='duration' and m['providerModelId']=='veo_quality':self.assertEqual(p['options'],['8'])
        for args in [{'model':'veo_quality','duration':4},{'model':'nano-pro','count':121}]:
            with self.assertRaises(ValueError):flow.parameters(args)
    def test_incomplete_blocks_are_recovered_without_generating_future_blocks(self):
        params={'model':'nano-pro','aspect':'1:1','prompt':'original','count':8}
        with flow.LOCK,automation.database(flow,self.data) as db:
            db.execute('INSERT INTO jobs VALUES (?,?,?)',('partial',json.dumps(params),json.dumps({'status':'needs_reconciliation','count':8,'blocks':[{'request_id':'b1','count':4,'status':'needs_reconciliation'},{'request_id':'b2','count':4,'status':'queued'}]})))
        with patch.object(flow,'call',return_value={'status':'needs_reconciliation'}) as call:
            flow.automation_initialize(self.data);self.settled()
            self.assertEqual(call.call_count,1);self.assertEqual(call.call_args.args[0],'flow_get_result')
            self.assertEqual(call.call_args.args[1],{'request_id':'b1'})


class HealthTests(unittest.IsolatedAsyncioTestCase):
    async def test_actual_mcp_registration_replaces_upstream_health_and_serializes(self):
        from bridge_tools import install
        from gflow_cli.mcp.server import server
        install()
        with patch('flow_session.cached',return_value={'status':'connected','checked_at':time.time()}):
            result=await server.call_tool('gflow_auth_status',{'profile':'nexus'})
        self.assertIn('flow_editor',str(result))
    async def test_cached_health_uses_editor_and_not_balance_auth(self):
        value={'status':'connected','checked_at':time.time(),'refreshed':False}
        with patch('flow_session.cached',return_value=value),patch('flow_session.renew',new_callable=AsyncMock) as renew:
            result=await flow_health.gflow_auth_status();renew.assert_not_called()
        self.assertEqual(result['source'],'flow_editor');self.assertTrue(result['cached']);self.assertEqual(result['balance_status'],'not_checked')
    async def test_failure_is_sanitized_and_never_claims_connected(self):
        with patch('flow_session.cached',return_value=None),patch('flow_session.renew',new_callable=AsyncMock,side_effect=RuntimeError('SECRET')):
            result=await flow_health.gflow_auth_status()
        self.assertEqual(result['status'],'error');self.assertNotIn('SECRET',json.dumps(result))


if __name__=='__main__':unittest.main()
