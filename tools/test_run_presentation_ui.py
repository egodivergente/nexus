import unittest
from run_presentation_ui import METHODS, SUITE, parse_instrumentation

def transcript(code=0):
    return "\n".join(f"INSTRUMENTATION_STATUS: class={SUITE}\nINSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS: class={SUITE}\nINSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: {code}" for name in sorted(METHODS)) + "\nINSTRUMENTATION_CODE: -1\n"

class ParserTest(unittest.TestCase):
    def test_all_real_success_events(self):
        root = parse_instrumentation(transcript())
        self.assertEqual(root.get("tests"), "6")
        self.assertEqual(root.get("failures"), "0")
        self.assertEqual({c.get("name") for c in root.findall("testcase")}, METHODS)
    def test_failures_remain_failures(self):
        self.assertEqual(parse_instrumentation(transcript(-2)).get("failures"), "6")
    def test_ignored_and_assumption_failures_not_success(self):
        for code in [-3, -4]: self.assertEqual(parse_instrumentation(transcript(code)).get("failures"), "6")
    def test_missing_test_is_rejected(self):
        with self.assertRaises(ValueError): parse_instrumentation("INSTRUMENTATION_CODE: -1")
    def test_process_crash_is_rejected(self):
        with self.assertRaises(ValueError): parse_instrumentation(transcript().replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"))
    def test_duplicate_results_are_rejected(self):
        with self.assertRaises(ValueError): parse_instrumentation(transcript()+transcript())
    def test_wrong_suite_rejected(self):
        with self.assertRaises(ValueError): parse_instrumentation(transcript().replace(SUITE,"unrelated"))
    def test_missing_start_rejected(self):
        with self.assertRaises(ValueError): parse_instrumentation(transcript().replace("INSTRUMENTATION_STATUS_CODE: 1", "INSTRUMENTATION_STATUS_CODE: 0",1))
    def test_failure_marker_rejected(self):
        with self.assertRaises(ValueError): parse_instrumentation(transcript()+"INSTRUMENTATION_FAILED: crash")

if __name__ == "__main__": unittest.main(verbosity=2)
