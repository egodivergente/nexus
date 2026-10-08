"""The host accepts unavailable estimates without pretending a generation is free."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import local_host as host

class OrderCosts(unittest.TestCase):
    def test_unknown_quote_is_informational_for_all_paid_providers(self):
        for provider in ('atlas_cloud','runway','crun'):
            args={'provider':provider,'path':'/test','body':{'model':'test'},'cost':None,'estimate_status':'unavailable'}
            with patch.object(host,'OFFLINE_QA',False), patch.object(host,'provider_request',return_value={'id':'fake'}) as request:
                self.assertEqual(host.dispatch({},'generate',args),{'id':'fake'})
                self.assertTrue(request.call_args.kwargs['paid'])
                self.assertIsNone(args['cost'])
    def test_malformed_costs_are_not_treated_as_unknown(self):
        with patch.object(host,'OFFLINE_QA',False), patch.object(host,'provider_request') as request:
            for value in (False,-1,float('nan'),float('inf'),'unknown',None):
                with self.assertRaises(ValueError):
                    host.dispatch({},'generate',{'provider':'runway','path':'/test','body':{'model':'test'},'cost':value})
            request.assert_not_called()
    def test_offline_qa_never_generates_even_without_price(self):
        with patch.object(host,'OFFLINE_QA',True), patch.object(host,'provider_request') as request:
            with self.assertRaises(ValueError):host.dispatch({},'generate',{'cost':None,'estimate_status':'unavailable'})
            request.assert_not_called()

if __name__=='__main__':unittest.main()
