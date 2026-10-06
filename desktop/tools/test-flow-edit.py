import sys
from pathlib import Path
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import flow_provider as provider

class EditProviderTests(unittest.TestCase):
    def test_catalog_requires_original_and_prompt(self):
        with patch.object(provider,'available',return_value=True):
            model=next(m for m in provider.catalog() if m['providerModelId']=='video_edit')
        self.assertTrue(model['promptRequired'])
        self.assertEqual(model['flowOperation'],'edit_video')
        self.assertTrue(model['parameters'][0]['isRequired'])

    def test_native_availability_is_not_invented_zero_cost(self):
        with patch.object(provider,'available',return_value=True),patch.object(provider,'call',return_value={'status':'available'}) as call:
            result=provider.quote({'model':'video_edit','source_request_id':'original'})
        self.assertIsNone(result['amount']);self.assertEqual(result['currency'],'flow_account')
        self.assertEqual(call.call_args.args[0],'flow_get_edit_options')

    def test_unconfirmed_source_keeps_submit_blocked(self):
        with patch.object(provider,'available',return_value=True),patch.object(provider,'call',return_value={'status':'error'}):
            with self.assertRaises(ValueError):provider.quote({'model':'video_edit','source_request_id':'original'})

if __name__=='__main__':unittest.main()
