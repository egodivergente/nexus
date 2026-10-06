import json
import unittest
from image_guard import model_problem

def body(codes, prompt="NARWHAL BELUGA"):
    rows = [[None, None, None, 1, 1, code, None, None, [[[prompt]]]] for code in codes]
    return "f.req=" + json.dumps([[["ogiZ0b", json.dumps([None, rows, 1]), None, "generic"]]]) + "&at=redacted"

class GuardTests(unittest.TestCase):
    def test_live_beluga_mapping_and_legacy(self):
        for code in ["BELUGA", "NARWHAL"]:
            self.assertIsNone(model_problem(body([code]), "NARWHAL"))

    def test_prompt_cannot_prove_model(self):
        self.assertIsNotNone(model_problem(body(["GEM_PIX_2"]), "NARWHAL"))

    def test_one_wrong_row_refuses_entire_request(self):
        self.assertIsNotNone(model_problem(body(["BELUGA", "UNKNOWN"]), "NARWHAL"))

    def test_malformed_or_empty_request_refused(self):
        for value in ["", "f.req=garbage", body([]), "f.req=[]"]:
            self.assertIsNotNone(model_problem(value, "NARWHAL"))

    def test_other_models_do_not_inherit_mapping(self):
        self.assertIsNotNone(model_problem(body(["BELUGA"]), "GEM_PIX_2"))

    def test_ampersand_in_prompt_does_not_break_json(self):
        self.assertIsNone(model_problem(body(["BELUGA"], "a & b"), "NARWHAL"))

if __name__ == "__main__":
    unittest.main()
