"""Gallery provenance tests use explicit IDs and byte equality, never visual guesses."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gallery_sources import GallerySources


class GallerySourcesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.data = Path(self.temp.name)
        (self.data/'media').mkdir();(self.data/'assistant').mkdir()
        self.state = {'jobs': [{'id':'job','providerId':'provider','modelId':'model','remoteId':'remote','outputs':['https://example.invalid/one.png']}]}
        self.service = GallerySources(self.data, lambda:self.state)

    def tearDown(self):
        self.temp.cleanup()

    def media(self, character, content=b'original'):
        path = self.data/'media'/(character*32+'.png');path.write_bytes(content)
        return '/media/'+path.name, path

    def index(self, assets, generations, cache=None):
        (self.data/'assistant/index.json').write_text(json.dumps({'assets':assets,'generations':generations}))
        if cache is not None:(self.data/'remote-media-cache.json').write_text(json.dumps(cache))

    def test_local_output_is_resolved_without_changing_history(self):
        uri,path = self.media('a');self.state['jobs'][0]['outputs'] = [{'type':'IMAGE','url':uri}]
        original = json.dumps(self.state)
        job,url,resolved = self.service.resolve('job')
        self.assertEqual((url,resolved),(uri,path));self.assertEqual(json.dumps(self.state),original)

    def test_exact_remote_asset_cache_is_used(self):
        uri,path = self.media('a')
        original = self.state['jobs'][0]['outputs'][0]
        self.index([{'id':'asset','uri':original}],[],{'asset':uri})
        self.assertEqual(self.service.resolve('job')[1:],(original,path))

    def test_single_output_merged_duplicates_require_identical_bytes(self):
        a,pa = self.media('a');b,pb = self.media('b')
        self.index([{'id':'a','uri':a},{'id':'b','uri':b}], [{'id':'g','nexusJobId':'job','providerId':'provider','modelId':'model','outputAssetIds':['a','b']}])
        self.assertEqual(self.service.resolve('job')[2],pa)
        pb.write_bytes(b'other')
        self.assertIsNone(self.service.resolve('job')[2])

    def test_multiple_outputs_keep_explicit_generation_order(self):
        a,pa = self.media('a',b'one');b,pb = self.media('b',b'two')
        self.state['jobs'][0]['outputs'].append('https://example.invalid/two.png')
        self.index([{'id':'a','uri':a},{'id':'b','uri':b}], [{'id':'g','nexusJobId':'job','outputAssetIds':['b','a']}])
        self.assertEqual(self.service.resolve('job',0)[2],pb)
        self.assertEqual(self.service.resolve('job',1)[2],pa)

    def test_multi_output_cardinality_mismatch_is_not_guessed(self):
        a,_ = self.media('a');b,_ = self.media('b')
        self.state['jobs'][0]['outputs'].append('https://example.invalid/two.png')
        self.index([{'id':'a','uri':a},{'id':'b','uri':b}], [{'id':'g','nexusJobId':'job','outputAssetIds':['a','b','a']}])
        self.assertIsNone(self.service.resolve('job')[2])

    def test_provider_job_id_is_scoped_to_provider_and_model(self):
        uri,path = self.media('a')
        generation = {'id':'g','providerJobId':'remote','providerId':'provider','modelId':'model','outputAssetIds':['a']}
        self.index([{'id':'a','uri':uri}],[generation])
        self.assertEqual(self.service.resolve('job')[2],path)
        generation['providerId'] = 'other';self.index([{'id':'a','uri':uri}],[generation])
        self.assertIsNone(self.service.resolve('job')[2])

    def test_recovered_source_must_retain_the_exact_output(self):
        uri,path = self.media('a');self.state['jobs'][0].update(sourceJobId='source',remoteId=None)
        self.state['jobs'].append({'id':'source','outputs':self.state['jobs'][0]['outputs'].copy()})
        self.index([{'id':'a','uri':uri}],[{'id':'g','nexusJobId':'source','outputAssetIds':['a']}])
        self.assertEqual(self.service.resolve('job')[2],path)
        self.state['jobs'][0]['outputs'] = ['https://example.invalid/edited.png']
        self.assertIsNone(self.service.resolve('job')[2])

    def test_names_prompts_and_creation_time_never_attribute_a_file(self):
        uri,_ = self.media('a')
        self.index([{'id':'a','uri':uri}],[{'id':'other','nexusJobId':'other','outputAssetIds':['a'],'prompt':'same','createdAt':123}])
        self.state['jobs'][0].update(prompt='same',startedAt=123)
        self.assertIsNone(self.service.resolve('job')[2])

    def test_media_alias_cannot_escape_the_profile(self):
        uri,_ = self.media('a');self.state['jobs'][0]['outputs'] = [uri]
        (self.data/'media-aliases.json').write_text(json.dumps({uri.removeprefix('/media/'):'../../outside.png'}))
        self.assertIsNone(self.service.resolve('job')[2])

    def test_unknown_job_or_output_is_actionable(self):
        for job,index in [('missing',0),('job',1),('job',-1),('job',True),('job','0')]:
            with self.assertRaises(ValueError):self.service.resolve(job,index)

    def test_missing_cache_original_falls_back_without_network(self):
        original = self.state['jobs'][0]['outputs'][0]
        self.index([{'id':'a','uri':original}],[],{'a':'/media/'+'a'*32+'.png'})
        self.assertEqual(self.service.resolve('job')[1:],(original,None))

    def test_malformed_optional_index_rows_fall_back_to_the_original_url(self):
        original = self.state['jobs'][0]['outputs'][0]
        self.index([{'id':[]}],[{'id':{},'nexusJobId':{},'outputAssetIds':[[]]}])
        self.assertEqual(self.service.resolve('job')[1:],(original,None))

    def journal(self, generations):
        sync = self.data/'assistant/sync';workspace = sync/'workspaces/workspace-one'
        workspace.mkdir(parents=True, exist_ok=True)
        (sync/'connection.json').write_text(json.dumps({'folderId':'workspace-one'}))
        (workspace/'journal.json').write_text(json.dumps({'revisions':{
            str(position):{'key':'generations:'+generation['id'],'value':generation}
            for position,generation in enumerate(generations)}}))

    def test_merged_output_union_uses_same_generation_historical_ordinal(self):
        a,pa = self.media('a',b'first');b,pb = self.media('b',b'second')
        self.state['jobs'][0]['outputs'].append('https://example.invalid/two.png')
        generation = {'id':'g','nexusJobId':'job','providerId':'provider','modelId':'model'}
        self.index([{'id':'a','uri':a},{'id':'b','uri':b},{'id':'remote-a','uri':self.state['jobs'][0]['outputs'][0]},{'id':'remote-b','uri':self.state['jobs'][0]['outputs'][1]}],
                   [{**generation,'outputAssetIds':['a','b','remote-a','remote-b']}])
        self.journal([{**generation,'outputAssetIds':['a','b']}])
        self.assertEqual(self.service.resolve('job',0)[2],pa)
        self.assertEqual(self.service.resolve('job',1)[2],pb)

    def test_different_historical_bytes_at_same_ordinal_remain_ambiguous(self):
        a,_ = self.media('a',b'first');b,_ = self.media('b',b'different')
        self.state['jobs'][0]['outputs'].append('https://example.invalid/two.png')
        generation = {'id':'g','nexusJobId':'job','providerId':'provider','modelId':'model'}
        self.index([{'id':'a','uri':a},{'id':'b','uri':b}], [{**generation,'outputAssetIds':['a','b','a','b']}])
        self.journal([{**generation,'outputAssetIds':['a','b']},{**generation,'outputAssetIds':['b','a']}])
        self.assertIsNone(self.service.resolve('job',0)[2])

    def test_journal_with_other_identity_or_malformed_revisions_cannot_attribute_media(self):
        a,_ = self.media('a');b,_ = self.media('b')
        self.state['jobs'][0]['outputs'].append('https://example.invalid/two.png')
        self.index([{'id':'a','uri':a},{'id':'b','uri':b}], [{'id':'g','nexusJobId':'job','outputAssetIds':['a','b','a']}])
        self.journal([{'id':'other','nexusJobId':'job','outputAssetIds':['a','b']},
                      {'id':'g','nexusJobId':'other','outputAssetIds':['a','b']}])
        self.assertIsNone(self.service.resolve('job')[2])
        journal = self.data/'assistant/sync/workspaces/workspace-one/journal.json'
        journal.write_text(json.dumps({'revisions':{'bad':None,'also-bad':{'key':[],'value':[]}}}))
        self.assertIsNone(self.service.resolve('job')[2])


if __name__ == '__main__':unittest.main()
