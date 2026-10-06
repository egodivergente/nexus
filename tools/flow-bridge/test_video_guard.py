import json
import unittest
from video_guard import model_problem
from image_guard import model_problem as image_problem

def body(model,prompt='fixture'):
    row=[[[None,None,[[[prompt]]]]],model,2]
    return 'f.req='+json.dumps([[['YhhmEf',json.dumps([[row],[],[]]),None,'generic']]])

class VideoGuardTests(unittest.TestCase):
    def test_measured_fast_models(self):
        for model in ('veo_3_1_t2v_fast_ultra','veo_3_1_t2v_fast'):
            self.assertIsNone(model_problem(body(model)))
    def test_prompt_cannot_prove_model(self):
        self.assertIsNotNone(model_problem(body('veo_3_1_t2v_quality','veo_3_1_t2v_fast_ultra')))
    def test_unknown_response_fails_closed(self):
        self.assertIsNotNone(model_problem('invalid'))
    def test_nano_lite_explicitly_prohibited(self):
        self.assertIsNotNone(image_problem('','HARBOR_SEAL'))
