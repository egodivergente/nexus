"""Validate the account-specific contracts and private imported media boundary."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import flow_provider as provider
from flow_contract import normalize,quote_key

class ModalityTests(unittest.TestCase):
    def test_each_form_has_required_inputs_and_its_own_options(self):
        with patch.object(provider,'available',return_value=True):catalog=provider.catalog()
        ids=[m['id'] for m in catalog]
        self.assertEqual(len(ids),len(set(ids)))
        for model in catalog:
            mode=model.get('flowMode')
            if not mode:continue
            args={'model':model['providerModelId'],'mode':mode,'duration':8,'resolution':'720p'}
            if mode=='i2v':args.update(initial_frame='start',end_frame='end')
            if mode=='r2v':args['reference_images']=['reference']
            actual=normalize(args)
            self.assertEqual(actual.get('mode','t2v'),mode)
            if mode!='t2v':self.assertTrue(any(f['required'] for f in model['contractMedia']))
        self.assertFalse(any(m.get('flowMode')=='r2v' and m['providerModelId']=='veo_quality' for m in catalog))

    def test_invalid_combinations_are_rejected_before_a_browser_call(self):
        invalid=[{'model':'veo_quality','mode':'r2v','reference_images':['r']},
            {'model':'veo_fast','duration':10},{'model':'veo_fast','resolution':'4k'},
            {'model':'veo_fast','mode':'i2v','end_frame':'end'},
            {'model':'veo_fast','mode':'r2v','reference_images':['r'],'duration':4},
            {'model':'omni_flash','mode':'i2v','initial_frame':'start','reference_images':['r']},
            {'model':'omni_flash','mode':'r2v','reference_images':['r']*2},
            {'model':'nano-pro','initial_frame':'start'}, {'model':'nano2-lite'}]
        with patch.object(provider,'call') as call:
            for args in invalid:
                with self.subTest(args=args),self.assertRaises(ValueError):provider.parameters(args)
            call.assert_not_called()

    def test_quote_cache_separates_every_billable_configuration(self):
        base={'model':'omni_flash','aspect':'16:9','duration':8,'resolution':'720p'}
        changes=[{}, {'duration':4}, {'resolution':'360p'}, {'aspect':'9:16'},
            {'mode':'i2v','initial_frame':'first'},
            {'mode':'i2v','initial_frame':'first','end_frame':'last'},
            {'mode':'r2v','reference_images':['r']}]
        keys=[quote_key({**base,**change}) for change in changes]
        self.assertEqual(len(keys),len(set(keys)))

    def test_imported_ids_cannot_escape_private_media(self):
        with tempfile.TemporaryDirectory() as data:
            root=Path(data)/'media';root.mkdir()
            name='a'*32+'.png';(root/name).write_bytes(b'owned placeholder')
            actual=provider.media_parameters(data,{'model':'veo_fast','initial_frame':name})
            self.assertEqual(actual['initial_frame'],str((root/name).resolve()))
            for value in ('../secret.png','C:/private/photo.png','https://host/photo.png','a'*32+'.mp4'):
                with self.assertRaises(ValueError):provider.media_parameters(data,{'initial_frame':value})

    def test_existing_request_cannot_change_frames_duration_or_mode(self):
        params={'model':'omni_flash','aspect':'16:9','mode':'i2v','duration':8,'resolution':'720p',
            'initial_frame':'a'*32+'.png','prompt':'fixture'}
        with tempfile.TemporaryDirectory() as data:
            db=provider.ledger(data)
            db.execute('INSERT INTO jobs VALUES (?,?,?)',('owned',json.dumps(params),json.dumps({'status':'needs_reconciliation'})))
            db.commit();db.close()
            with patch.object(provider,'quote') as quote:
                self.assertTrue(provider.submit(data,{'request_id':'owned',**params})['uncertain'])
                for change in ({'duration':4},{'end_frame':'b'*32+'.png'},{'initial_frame':'b'*32+'.png'}):
                    with self.assertRaises(ValueError):provider.submit(data,{'request_id':'owned',**params,**change})
                quote.assert_not_called()

if __name__=='__main__':unittest.main()
