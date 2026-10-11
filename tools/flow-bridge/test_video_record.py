import unittest
from runtime_compat import install
install()
from gflow_cli.api.transports.batchexecute import generation_record, _is_record

class RecordTests(unittest.TestCase):
    def fixture(self):
        details=[None,'fixture']+[None]*6+[[3]]+[None]*5
        media=[None]*12+['veo_3_1_t2v_fast_ultra']
        return ['00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',
            '00000000-0000-0000-0000-000000000003',None,None,details,None,[media]]
    def test_measured_null_tag_shape_is_accepted(self):
        record=generation_record('Zzl0ze',self.fixture())
        self.assertTrue(record.is_done)
    def test_unknown_model_or_state_is_not_guessed(self):
        row=self.fixture();row[7][0][12]='unknown'
        self.assertFalse(_is_record(row))
        row=self.fixture();row[5][8][0]=12345
        self.assertFalse(_is_record(row))
