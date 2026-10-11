import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'desktop'))
from flow_contract import normalize,quote_key
from flow_voices import install,pending_voice

class VoiceTests(unittest.TestCase):
    def test_preset_is_bound_to_the_exact_queue_payload_and_context_resets(self):
        from gflow_cli.mcp import tools
        install()
        args=dict(prompt='fixture',mode='r2v',aspect='16:9',count=1,model='omni_flash',duration=8,resolution='720p',
            tool_specs=[],project=None,project_name='fixture',output=None,ui_mode='classic',reference_entities=None,reference_entity_names=None)
        token=pending_voice.set('charon')
        try:self.assertEqual(tools._build_video_payload(**args)['reference_audio'],'charon')
        finally:pending_voice.reset(token)
        self.assertNotIn('reference_audio',tools._build_video_payload(**args))

    def test_voice_is_only_available_on_verified_omni_reference_mode(self):
        base=dict(model='omni_flash',mode='r2v',reference_images=['fixture'],voice='Charon')
        self.assertEqual(normalize(base)['voice'],'Charon')
        self.assertNotEqual(quote_key(base),quote_key({**base,'voice':'Aoede'}))
        for change in ({'model':'veo_fast'},{'voice':'invented'},{'mode':'t2v'}):
            with self.assertRaises(ValueError):normalize({**base,**change})

if __name__=='__main__':unittest.main()
