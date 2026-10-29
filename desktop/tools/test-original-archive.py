"""Canonical registration must reflect persisted URI identity, including tombstones."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from original_archive import OriginalArchive


class ArchiveRegistrationTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);(self.root/'media').mkdir()
        self.image=self.root/'media'/('a'*32+'.png');self.image.write_bytes(b'original-one')
        self.uri='/media/'+self.image.name
        self.state={'jobs':[{'id':'job-one','status':'COMPLETED','outputs':[self.uri],'modality':'IMAGE','startedAt':1}]}
        self.archive=OriginalArchive(self.root,lambda:self.state,start_worker=False)
        self.record=self.archive.protect('job-one',remote=False)
        self.key=self.archive.previews.key('job-one',0,self.uri)

    def tearDown(self):self.temp.cleanup()

    def index(self,assets):
        (self.root/'assistant').mkdir(exist_ok=True)
        path=self.root/'assistant/index.json';path.write_text(json.dumps({'assets':assets}))
        return path

    def test_register_records_actual_canonical_id_instead_of_unpersisted_request(self):
        requested=self.record['assetId'];self.archive.register_assets=lambda rows:{requested:'existing-original'}
        self.archive._register();actual=self.archive.records[self.key]
        self.assertTrue(actual['registered']);self.assertEqual(actual['assetId'],'existing-original')
        self.assertEqual(json.loads(self.archive.path.read_text())['records'][self.key]['assetId'],'existing-original')

    def test_boolean_success_never_proves_registration(self):
        self.archive.register_assets=lambda rows:True;self.archive._register()
        self.assertFalse(self.archive.records[self.key]['registered'])

    def test_partial_mapping_registers_only_confirmed_rows(self):
        image=self.root/'media'/('b'*32+'.png');image.write_bytes(b'original-two')
        self.state['jobs'].append({'id':'job-two','status':'COMPLETED','outputs':['/media/'+image.name],'modality':'IMAGE','startedAt':2})
        second=self.archive.protect('job-two',remote=False)
        self.archive.register_assets=lambda rows:{self.record['assetId']:'canonical-one'};self.archive._register()
        self.assertEqual(self.archive.public()['registered'],1)
        self.assertTrue(any(r['assetId']==second['assetId'] and not r['registered'] for r in self.archive.records.values()))

    def test_save_failure_rolls_back_identity_and_all_flags(self):
        before=copy.deepcopy(self.archive.records);self.archive.register_assets=lambda rows:{self.record['assetId']:'canonical'}
        with patch.object(self.archive,'_save',side_effect=OSError('disk failure')):
            with self.assertRaises(OSError):self.archive._register()
        self.assertEqual(self.archive.records,before)

    def test_changed_record_during_registration_is_not_marked_by_a_stale_result(self):
        def register(rows):
            self.archive.records[self.key]['sha256']='0'*64
            return {self.record['assetId']:'stale-canonical'}
        self.archive.register_assets=register;self.archive._register()
        self.assertFalse(self.archive.records[self.key]['registered'])
        self.assertEqual(self.archive.records[self.key]['assetId'],self.record['assetId'])

    def test_startup_invalidates_missing_identity_without_writing_either_input(self):
        self.archive.records[self.key].update(registered=True,cloudVerifiedAt=123);self.archive._save()
        index=self.index([{'id':'real-canonical','uri':self.uri,'libraryDeleted':True}])
        manifest_before=self.archive.path.read_bytes();index_before=index.read_bytes()
        restarted=OriginalArchive(self.root,lambda:self.state,start_worker=False)
        self.assertFalse(restarted.records[self.key]['registered']);self.assertIsNone(restarted.records[self.key]['cloudVerifiedAt'])
        self.assertEqual(self.archive.path.read_bytes(),manifest_before);self.assertEqual(index.read_bytes(),index_before)

    def test_startup_accepts_real_hidden_identity_without_resurrecting_it(self):
        self.archive.records[self.key].update(registered=True);self.archive._save()
        index=self.index([{'id':self.record['assetId'],'uri':self.uri,'libraryDeleted':True}]);before=index.read_bytes()
        restarted=OriginalArchive(self.root,lambda:self.state,start_worker=False)
        self.assertTrue(restarted.records[self.key]['registered']);self.assertEqual(index.read_bytes(),before)

    def test_reprotect_preserves_verified_canonical_identity(self):
        self.archive.register_assets=lambda rows:{self.record['assetId']:'canonical-one'};self.archive._register()
        repeated=self.archive.protect('job-one',remote=False)
        self.assertEqual(repeated['assetId'],'canonical-one');self.assertTrue(repeated['registered'])

    def test_host_callback_reuses_retired_uri_without_import_or_resurrection(self):
        import local_host
        class Host:
            lock=threading.Lock()
            def __init__(self):self.imports=[];self.assets=[{'id':'retired-id','uri':self_uri,'libraryDeleted':True}]
            def start(self):pass
            def exchange(self,op,args):
                if op=='importAssets':self.imports.extend(copy.deepcopy(args['assets']));self.assets.extend(args['assets'])
                return {'index':{'assets':copy.deepcopy(self.assets)}}
        self_uri=self.uri;host=Host()
        with patch.object(local_host,'DATA',self.root),patch.object(local_host,'OFFLINE_QA',True),patch.object(local_host,'_agent',host),patch.object(local_host,'_ORIGINAL_SERVICES',{}):
            service=local_host.original_archive();mapping=service.register_assets([{'id':'requested','uri':self.uri}])
        self.assertEqual(mapping,{'requested':'retired-id'});self.assertEqual(host.imports,[])
        self.assertTrue(host.assets[0]['libraryDeleted'])


if __name__=='__main__':unittest.main()
