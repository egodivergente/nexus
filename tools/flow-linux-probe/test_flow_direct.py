"""Android's explicit Flow parameters: contract-derived options and the guards that prevent unconfirmed spending."""
import asyncio
import hashlib
import tempfile
import unittest
from pathlib import Path

import flow_direct


class FlowDirectTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())

    def run_async(self, coroutine):
        return asyncio.run(coroutine)

    def test_capabilities_follow_the_verified_contract(self):
        caps = flow_direct.capabilities()
        models = {m['model']: m for m in caps['models']}
        self.assertEqual({'nano2', 'nano-pro', 'veo_fast', 'veo_quality', 'veo_lite', 'omni_flash'}, set(models))
        veo = {m['mode']: m for m in models['veo_fast']['modes']}
        self.assertEqual(['16:9', '9:16'], veo['t2v']['aspects'])
        self.assertEqual([4, 6, 8], veo['t2v']['durations'])
        self.assertTrue(veo['i2v']['end_frame'])
        self.assertEqual([8], veo['r2v']['durations'])
        self.assertEqual([8], {m['mode']: m for m in models['veo_quality']['modes']}['t2v']['durations'])
        self.assertNotIn('r2v', {m['mode'] for m in models['veo_quality']['modes']})
        self.assertEqual([1, 2, 3, 4], models['omni_flash']['counts'])
        self.assertEqual({'image': ['2k', '4k'], 'video': ['1080p', '4k']}, caps['downloads'])

    def test_submission_requires_a_confirmed_quote(self):
        with self.assertRaises(flow_direct.DirectError) as error:
            self.run_async(flow_direct.submit(self.root, {'request_id': 'android-job-0001', 'prompt': 'x', 'model': 'veo_fast'}))
        self.assertEqual('quote_confirmation_required', error.exception.code)

    def test_invalid_ids_and_unknown_fields_are_rejected_before_any_call(self):
        with self.assertRaises(flow_direct.DirectError) as error:
            self.run_async(flow_direct.submit(self.root, {'request_id': '../x', 'prompt': 'x', 'cost': 0}))
        self.assertEqual('invalid_request_id', error.exception.code)
        with self.assertRaises(flow_direct.DirectError) as error:
            self.run_async(flow_direct.quote(self.root, {'model': 'veo_fast', 'upscale': '4k'}))
        self.assertEqual('unsupported_field', error.exception.code)

    def test_references_are_staged_by_verified_hash_only(self):
        payload = b'\x89PNG\r\n\x1a\n' + b'0' * 64
        digest = hashlib.sha256(payload).hexdigest()
        store = self.root / 'flow-references'
        store.mkdir()
        (store / f'ref_{digest}.png').write_bytes(payload)
        data = flow_direct.data_dir(self.root)
        staged = flow_direct._stage_references(self.root, data, {'initial_frame': f'ref_{digest}', 'end_frame': None})
        self.assertEqual(digest[:32] + '.png', staged['initial_frame'])
        self.assertNotIn('end_frame', staged)
        self.assertEqual(payload, (data / 'media' / staged['initial_frame']).read_bytes())

    def test_media_cannot_escape_the_results_folder(self):
        for name in ('../recovery-token', 'x.png', 'a' * 32 + '.exe'):
            with self.assertRaises(flow_direct.DirectError):
                flow_direct.media_file(self.root, name)


if __name__ == '__main__':
    unittest.main()
