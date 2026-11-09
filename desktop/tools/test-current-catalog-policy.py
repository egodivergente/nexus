"""Isolated policy checks: never starts the host or calls a provider."""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import catalog_policy as policy


class CurrentCatalogPolicyTest(unittest.TestCase):
    def test_exact_selection_keeps_history_catalog_and_public_additions(self):
        self.assertEqual(71, len(policy.policy()['generators']))
        self.assertEqual(33, len(policy.policy()['tools']))
        models = policy.catalog()
        self.assertTrue(any(m['id'] == 'runway/gen4_turbo/i2v' for m in models))
        self.assertFalse(policy.is_allowed(next(m for m in models if m['id'] == 'runway/gen4_turbo/i2v')))
        self.assertTrue(policy.is_allowed(next(m for m in models if m['id'] == 'kwaivgi/kling-v4.0/image-to-video')))
        self.assertTrue(policy.is_allowed({'providerId': 'atlas_cloud', 'id': 'new-chat', 'modalities': ['CHAT', 'VIDEO']}))

    def test_runway_native_id_cannot_hide_text_to_video_endpoint(self):
        allowed = {'provider': 'runway', 'path': '/v1/image_to_video',
            'catalog_model_id': 'runway/gen4.5/i2v', 'body': {'model': 'gen4.5', 'promptImage': 'https://example.invalid/frame.png'}}
        self.assertEqual('runway/gen4.5/i2v', policy.validate_generation(allowed)['id'])
        for changed in ({'path': '/v1/text_to_video'}, {'body': {'model': 'gen4.5'}}):
            with self.assertRaises(ValueError):
                policy.validate_generation({**allowed, **changed})

    def test_shared_crun_mode_does_not_reintroduce_standard_kling(self):
        args = {'provider': 'crun', 'path': '/api/v1/client/job/CreateTask',
                'body': {'model': 'kling/v3', 'input': {'mode': 'pro', 'img_urls': ['https://example.invalid/image.png']}}}
        self.assertEqual('crun/kling-v3-pro', policy.validate_generation(args)['id'])
        with self.assertRaises(ValueError):
            policy.validate_generation({**args, 'body': {'model': 'kling/v3', 'input': {'mode': 'std', 'img_urls': ['https://example.invalid/image.png']}}})

    def test_generator_needs_visual_media_not_audio_or_empty_references(self):
        model = {'providerId': 'atlas_cloud', 'id': 'bytedance/seedance-2.0/reference-to-video', 'modalities': ['VIDEO']}
        base = {'provider': 'atlas_cloud', 'path': '/api/v1/model/generateVideo', 'catalog_model_id': model['id']}
        for fields in ({'prompt': 'A scene'}, {'images': []}, {'refers': [{'type': 'audio', 'url': 'https://example.invalid/audio.wav'}]}):
            with self.assertRaises(ValueError):
                policy.validate_generation({**base, 'body': {'model': model['id'], **fields}}, [model])
        self.assertEqual(model, policy.validate_generation({**base, 'body': {'model': model['id'],
            'refers': [{'type': 'image', 'url': 'https://example.invalid/reference.png'}]}}, [model]))

    def test_route_identity_is_bound_to_provider_body(self):
        with self.assertRaises(ValueError):
            policy.validate_generation({'provider': 'runway', 'path': '/v1/image_to_video',
                'catalog_model_id': 'runway/gen4.5/i2v', 'body': {'model': 'gen4_turbo', 'promptImage': 'x'}})

    def test_existing_image_routes_are_accepted_without_applying_video_pruning(self):
        checked = 0
        for model in policy.catalog():
            if model.get('modalities') != ['IMAGE'] or model.get('providerId') not in ('atlas_cloud', 'crun', 'runway'):
                continue
            provider = model['providerId']
            values = {parameter['key']: parameter.get('defaultValue') for parameter in model.get('parameters', [])}
            body = {'model': policy.native_model(model), **({'input': values} if provider == 'crun' else values)}
            path = '/api/v1/model/generateImage' if provider == 'atlas_cloud' else '/api/v1/client/job/CreateTask' if provider == 'crun' else '/v1/' + model.get('contractEndpoint', '')
            self.assertEqual(model, policy.validate_generation({'provider': provider, 'path': path,
                'body': body, 'catalog_model_id': model['id']}), model['id'])
            checked += 1
        self.assertGreater(checked, 100)

    def test_seedance_byteplus_last_for_every_version_and_manual_unchanged(self):
        for version in ('2.0-mini', '2.0', '2.0-fast', '2.5'):
            direct = {'model': {'providerId': 'usr_byteplus-media', 'id': 'custom/byteplus-media/seedance-' + version}, 'total_usd': .01}
            indirect = {'model': {'providerId': 'crun', 'id': 'seedance-' + version}, 'parameters': {'byteplus_fallback': 'true'}, 'total_usd': .02}
            ordinary = {'model': {'providerId': 'atlas_cloud', 'id': 'seedance-' + version}, 'total_usd': .5}
            self.assertEqual([ordinary, direct, indirect], policy.route_order([direct, indirect, ordinary]))
            self.assertEqual([direct, indirect, ordinary], policy.route_order([direct, indirect, ordinary], manual=True))

    def test_custom_byteplus_uses_its_real_input_contract_and_keeps_other_custom_providers(self):
        model = {'providerId': 'usr_byteplus-media', 'id': 'custom/byteplus-media/seedance-2-0-i2v', 'modalities': ['VIDEO']}
        policy.validate_custom_generation(model, {'imageUrls': ['/media/reference.png'], 'params': {}})
        with self.assertRaises(ValueError):
            policy.validate_custom_generation(model, {'imageUrls': [], 'params': {}})
        with self.assertRaises(ValueError):
            policy.validate_custom_generation({**model, 'id': 'custom/byteplus-media/seedance-2-0-t2v'}, {'prompt': 'text'})
        policy.validate_custom_generation({'providerId': 'usr_other', 'id': 'custom/other/model', 'modalities': ['VIDEO']}, {})


if __name__ == '__main__':
    unittest.main()
