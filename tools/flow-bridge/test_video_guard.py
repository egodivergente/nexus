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
    def test_each_measured_model_accepts_only_its_own_wire_identity(self):
        codes={'veo_3_1_fast':'veo_3_1_t2v_fast_ultra','veo_3_1_quality':'veo_3_1_t2v','veo_3_1_lite':'veo_3_1_t2v_lite','omni_flash':'abra_t2v_8s'}
        for requested,code in codes.items():
            self.assertIsNone(model_problem(body(code),requested))
            for other in codes:
                if other!=requested:self.assertIsNotNone(model_problem(body(code),other))
        self.assertIsNotNone(model_problem(body('abra_t2v_8s'),None))
    def test_nano_lite_explicitly_prohibited(self):
        self.assertIsNotNone(image_problem('','HARBOR_SEAL'))
    def test_both_frames_are_proved_by_slots_not_by_prompt_text(self):
        row=[[[None,None,[[['start-id end-id']]]]],'veo_3_1_i2v_s_fast_ultra_fl',2,None,[None,'wrong-start'],[None,'end-id']]
        wire=lambda r:'f.req='+json.dumps([[['nprQif',json.dumps([[r]]),None,'generic']]])
        args=dict(mode='i2v',duration=8,start_id='start-id',end_id='end-id')
        self.assertIsNotNone(model_problem(wire(row),'veo_3_1_fast',**args))
        row[4][1]='start-id'
        self.assertIsNone(model_problem(wire(row),'veo_3_1_fast',**args))
        self.assertIsNotNone(model_problem(wire(row),'veo_3_1_quality',**args))
        self.assertIsNotNone(model_problem(wire(row),'veo_3_1_fast',**{**args,'duration':4}))
    def test_references_are_exact_and_cannot_be_faked_in_the_prompt(self):
        row=[[[None,None,[[['ref-one ref-two']]]]],[[None,'ref-one']],'abra_r2v_4s',2]
        wire=lambda r:'f.req='+json.dumps([[['MZZa6b',json.dumps([[r]]),None,'generic']]])
        args=dict(mode='r2v',duration=4,resolution='720p',reference_ids=('ref-one','ref-two'))
        self.assertIsNotNone(model_problem(wire(row),'omni_flash',**args))
        row[1].append([None,'ref-two'])
        self.assertIsNone(model_problem(wire(row),'omni_flash',**args))
        self.assertIsNotNone(model_problem(wire(row),'omni_flash',**{**args,'resolution':'360p'}))
