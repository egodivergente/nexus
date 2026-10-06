"""Selection must retain modality, model identity, format and verified credits."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import flow_provider as provider

class SelectionTests(unittest.TestCase):
    def test_pro_is_featured_and_each_allowed_model_has_the_right_modality(self):
        with patch.object(provider,'available',return_value=True):
            catalog=provider.catalog()
        self.assertEqual(catalog[0]['providerModelId'],'nano-pro')
        self.assertTrue(catalog[0]['isFlagship'])
        for model in catalog:
            if model['promptRequired'] is False:continue
            image=model['providerModelId'] in {'nano2','nano-pro'}
            self.assertEqual(model['modalities'],['IMAGE' if image else 'VIDEO'])
            self.assertEqual(provider.parameters({'model':model['providerModelId']})['aspect'],'1:1' if image else '16:9')
        self.assertNotIn('nano2-lite',[m['providerModelId'] for m in catalog])

    def test_pro_worker_uses_image_tool_with_the_exact_model(self):
        with tempfile.TemporaryDirectory() as data,patch.object(provider,'call',return_value={'status':'in_progress'}) as call:
            provider.work(data,'fixture',{'model':'nano-pro','aspect':'3:4','prompt':'fixture'})
        self.assertEqual(call.call_args.args[0],'flow_generate_image')
        self.assertEqual(call.call_args.args[1]['model'],'nano-pro')
        self.assertEqual(call.call_args.args[1]['aspect'],'3:4')

    def test_new_video_models_do_not_inherit_fast_price_on_failure(self):
        for model in ('veo_quality','veo_lite','omni_flash'):
            with tempfile.TemporaryDirectory() as root,patch.object(provider,'RUNTIME',Path(root)),patch.object(provider,'available',return_value=True),patch.object(provider,'call',return_value={'status':'error'}):
                with self.assertRaises(ValueError):provider.quote({'model':model})

if __name__=='__main__':unittest.main()
