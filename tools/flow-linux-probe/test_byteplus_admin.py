import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('byteplus_admin', Path(__file__).with_name('byteplus_admin.py'))
bp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bp)


class BytePlusAdminTest(unittest.TestCase):
    def test_stats_only_exports_whitelisted_dimensions(self):
        result = bp.stats({'data_count': 1, 'records': [{'ModelName': 'fixture', 'InputTokens': '123', 'OutputTokens': '45', 'ReqCnt': '2', 'AuthToken': 'NEVER_EXPORT_THIS'}]})
        self.assertEqual(result['rows'][0]['input'], 123)
        self.assertNotIn('NEVER_EXPORT', json.dumps(result))

    def test_stats_incomplete_and_missing_are_not_zero(self):
        for value in [{'data_count': 2, 'records': []}, {'data_count': 1, 'records': [{'InputTokens': None}]}]:
            with self.assertRaises(bp.Unavailable): bp.stats(value)

    def test_quota_never_double_counts_packs_and_preserves_negative(self):
        data = {'items': [{'model': 'fixture', 'free_usage': {'remaining': 10}, 'resource_packs': [{'remaining': -3, 'type': 'FreeInference'}]}]}
        rows = bp.quota(data)['rows']
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['remaining'], '-3')
        self.assertNotIn('amount', rows[0])

    def test_quota_reads_all_pages(self):
        with patch.object(bp, 'run', side_effect=[{'total_count': 2, 'items': [{'model': 'one'}]}, {'total_count': 2, 'items': [{'model': 'two'}]}]) as run:
            self.assertEqual(len(bp.all_quotas()['items']), 2)
            self.assertEqual(run.call_count, 2)

    def test_sdk_invoice_preserves_tax_without_double_counting(self):
        data = {"total": 1, "list": [{"pretax_amount": "1.23", "tax": "0.26", "posttax_amount": "1.49", "payable_amount": "1.49", "currency": "USD"}]}
        result = bp.regular_billing(data)
        self.assertEqual(result["amount"], "1.23")
        self.assertEqual(result["tax"], "0.26")
        self.assertEqual(result["total"], "1.49")
        with self.assertRaises(bp.Unavailable): bp.regular_billing(dict(data, total=2))

    def test_official_rates_convert_thousand_to_million_and_reject_tiers(self):
        item = {"FoundationModelName": "fixture", "ChargeItems": [{"Type": "InferencePrompt", "Price": "0.001", "UnitCode": "K tokens"}, {"Type": "InferenceCompletion", "Price": "0.003", "UnitCode": "K tokens"}]}
        data = {"items": [item]}
        result = bp.model_rates(data, "USD")
        self.assertEqual(result["input_usd_per_million"], "1.000")
        with self.assertRaises(bp.Unavailable): bp.model_rates(data, "CNY")
        item["MultiChargeItems"] = [{"ChargeItems": [{"Type": "InferencePrompt", "Price": "0.002"}]}]
        with self.assertRaises(bp.Unavailable): bp.model_rates(data, "USD")

    def test_real_monetary_balance_is_separate_from_free_quota(self):
        result = bp.monetary_balance({"Result": {"AvailableBalance": "12.34", "Currency": "USD", "CashBalance": "20"}})
        self.assertEqual(result["amount"], "12.34")
        with self.assertRaises(bp.Unavailable): bp.monetary_balance({"items": []})

    def test_billing_decimal_and_truncation(self):
        items = [{'PayableAmount': '.10', 'Currency': 'USD'}, {'PayableAmount': '.20', 'Currency': 'USD'}]
        self.assertEqual(bp.billing({'items': items})['amount'], '0.30')
        self.assertIsNone(bp.billing({'items': items})['tax'])
        for data in [{'items': items, 'is_truncated': True}, {'items': items, 'partial_failures': [{}]}, {'items': [{'PayableAmount': 1}]}]:
            with self.assertRaises(bp.Unavailable): bp.billing(data)

    def test_missing_auth_returns_unavailable_and_no_inference(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(bp, 'ROOT', Path(folder)), patch.object(bp, 'run', return_value={'logged_in': False}) as run:
            result = bp.snapshot('day')
            self.assertEqual(result['stats']['status'], 'unavailable')
            self.assertNotIn('rows', result['stats'])
            self.assertEqual(run.call_args_list[0].args, ('auth', 'status'))
            self.assertEqual(run.call_count, 1)

    def test_stale_cache_survives_upstream_failure(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(bp, 'ROOT', Path(folder)), patch.object(bp, 'TTL', -1):
            def fixture(*args):
                if args[0] == 'auth': return {'logged_in': True}
                if args[0] == 'api': return {'Result': {'AvailableBalance': '12.34', 'Currency': 'USD'}}
                if args[0] == 'pricing': return {'items': [{'FoundationModelName': 'm', 'ChargeItems': [{'Type': 'InferencePrompt', 'Price': '.001', 'UnitCode': 'K tokens'}, {'Type': 'InferenceCompletion', 'Price': '.002', 'UnitCode': 'K tokens'}]}]}
                if args[1] == 'stats': return {'data_count': 1, 'records': [{'ModelName': 'm', 'InputTokens': 1, 'OutputTokens': 2, 'ReqCnt': 1}]}
                if args[1] == 'balance': return {'items': [], 'total_count': 0}
                return {'items': []}
            with patch.object(bp, 'run', side_effect=fixture): previous = bp.snapshot('day')
            with patch.object(bp, 'run', side_effect=bp.Unavailable('offline')): stale = bp.snapshot('day')
            self.assertEqual(stale['stats']['rows'], previous['stats']['rows'])
            self.assertEqual(stale['stats']['status'], 'stale')


if __name__ == '__main__': unittest.main()
