#!/usr/bin/env python3
"""Native index adapter tests: scoped moves, content dedup and trusted imports."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from library_operations import LibraryOperations


class NativeIndex:
    """Mirror importAssets/updateAsset's immutable identity and URI dedup contract."""
    def __init__(self,root,index):
        self.data=root/'assistant';self.data.mkdir()
        self.index=copy.deepcopy(index);self.lock=threading.RLock()
        self.calls=[];self.fail_after=None;self.lose_ack=False
        self.persist()

    def start(self):pass

    def persist(self):
        temporary=self.data/'index.tmp'
        temporary.write_text(json.dumps(self.index),'utf-8')
        temporary.replace(self.data/'index.json')

    def exchange(self,op,args):
        if op=='snapshot':return {'index':copy.deepcopy(self.index)}
        if op!='importAssets':raise AssertionError(op)
        self.calls.append(copy.deepcopy(args))
        for ordinal,value in enumerate(args['assets']):
            if self.fail_after is not None and ordinal>=self.fail_after:raise RuntimeError('isolated native interruption')
            current=next((a for a in self.index['assets'] if a['id']==value['id']),None)
            if current:
                replacement={**value,**{key:current[key] for key in ('id','uri','createdAt')}}
                self.index['assets']=[replacement if a['id']==current['id'] else a for a in self.index['assets']]
            elif not any(a['uri']==value['uri'] for a in self.index['assets']):
                self.index['assets'].append(copy.deepcopy(value))
            self.persist()
        if self.lose_ack:raise RuntimeError('isolated lost acknowledgement')
        return {'index':copy.deepcopy(self.index)}


class LibraryOperationsTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        (self.root/'media').mkdir()
        for name,payload in [('11111111111111111111111111111111.png',b'original-A'),('22222222222222222222222222222222.png',b'original-A'),('33333333333333333333333333333333.png',b'original-B')]:
            (self.root/'media'/name).write_bytes(payload)
        self.index={'projects':[{'id':'a','name':'A'},{'id':'b','name':'B'},{'id':'c','name':'C'},{'id':'archived','name':'Archivado','archived':True}],
                    'scenes':[{'id':'scene-a','projectId':'a','code':'A01'}],
                    'shots':[{'id':'shot-a','projectId':'a','sceneId':'scene-a','code':'A01-01'}],
                    'assets':[self.asset('first','/media/11111111111111111111111111111111.png','a',sceneId='scene-a',shotId='shot-a',tags=['source'],generationId='gen'),
                              self.asset('other','/media/33333333333333333333333333333333.png','a')],
                    'generations':[{'id':'gen','nexusJobId':'old-job','projectId':'a','outputAssetIds':['first']}],
                    'projectSessions':[{'projectId':'a','from':1}]}
        self.state={'draft':{'prompt':'Preservar'},'jobs':[{'id':'old-job','status':'COMPLETED','modality':'IMAGE',
                    'outputs':['/media/11111111111111111111111111111111.png'],'parameters':{'__project_id':'a'},'startedAt':1}]}
        self.host=NativeIndex(self.root,self.index)
        self.service=LibraryOperations(self.host,lambda:self.state)

    def tearDown(self):self.temp.cleanup()

    @staticmethod
    def asset(identifier,uri,project_id,**extra):
        return {'id':identifier,'uri':uri,'projectId':project_id,'type':'IMAGE','source':'IMPORTED',
                'role':'REFERENCE','displayName':'Same name','createdAt':1,**extra}

    def move(self,entry_ids=None,**args):
        return self.service.command({'op':'move','entryIds':entry_ids or ['asset:first'],'projectId':'b',**args})

    def by_id(self,identifier):return next(a for a in self.host.index['assets'] if a['id']==identifier)

    def receipt(self,name='22222222222222222222222222222222.png'):
        payload=(self.root/'media'/name).read_bytes()
        return {'id':name,'name':'Imported.png','url':'/media/'+name,'size':len(payload),
                'mime':'image/png','sha256':hashlib.sha256(payload).hexdigest()}

    def test_move_preserves_identity_bytes_history_and_other_metadata(self):
        before=copy.deepcopy(self.state);history=copy.deepcopy(self.host.index['generations'])
        original=(self.root/'media/11111111111111111111111111111111.png').read_bytes()
        result=self.move(sourceProjectId='a')
        self.assertEqual(result['status'],'completed');self.assertEqual(result['movedEntries'],1)
        moved=self.by_id('first')
        self.assertEqual({key:moved[key] for key in ('id','uri','createdAt','tags','generationId')},
                         {key:self.index['assets'][0][key] for key in ('id','uri','createdAt','tags','generationId')})
        self.assertEqual(moved['projectId'],'b');self.assertIsNone(moved['sceneId']);self.assertIsNone(moved['shotId'])
        self.assertEqual(self.host.index['generations'],history);self.assertEqual(self.state,before)
        self.assertEqual((self.root/'media/11111111111111111111111111111111.png').read_bytes(),original)
        self.assertEqual(self.by_id('other')['projectId'],'a')

    def test_selected_origin_preserves_other_memberships_and_merges_same_bytes_destination(self):
        self.host.index['assets'] += [self.asset('copy','/media/22222222222222222222222222222222.png','a'),
            self.asset('destination','/media/11111111111111111111111111111111.png','b',tags=['destination'],role='FINAL'),
            self.asset('third','/media/22222222222222222222222222222222.png','c',tags=['third'])]
        self.host.persist();destination=copy.deepcopy(self.by_id('destination'));third=copy.deepcopy(self.by_id('third'))
        result=self.move(sourceProjectId='a')
        self.assertEqual(result['updatedAssets'],2);self.assertEqual(result['mergedAssets'],2)
        for identifier in ('first','copy'):
            self.assertEqual(self.by_id(identifier)['projectId'],'b');self.assertTrue(self.by_id(identifier)['libraryDeleted'])
        self.assertEqual(self.by_id('destination'),destination);self.assertEqual(self.by_id('third'),third)
        self.assertEqual(len(self.host.index['assets']),5)
        self.assertTrue(all(path.exists() for path in (self.root/'media').iterdir()))

    def test_identical_names_and_sizes_with_different_sha_are_never_merged(self):
        result=self.move()
        self.assertEqual(result['updatedAssets'],1);self.assertEqual(result['mergedAssets'],0)
        self.assertEqual(self.by_id('other')['projectId'],'a')

    def test_unavailable_same_uri_memberships_move_without_unverified_retirement(self):
        uri='https://fixture.invalid/unavailable.png'
        self.host.index['assets'] += [self.asset('offline-a',uri,'a'),self.asset('offline-b',uri,'b')]
        self.host.persist()
        result=self.move(['asset:offline-a'],sourceProjectId='a')
        self.assertEqual(result['movedEntries'],1);self.assertEqual(result['mergedAssets'],0)
        self.assertEqual(self.by_id('offline-a')['projectId'],'b')
        self.assertFalse(self.by_id('offline-a').get('libraryDeleted',False))

    def test_invalidated_content_proof_prevents_retirement_before_any_write(self):
        self.host.index['assets'].append(self.asset('destination','/media/'+'2'*32+'.png','b'));self.host.persist()
        original=self.service._content
        verified=original(self.host.index['assets'][0])
        calls={'first':0}
        def content(asset,size=None):
            if asset['id']=='first':
                calls['first']+=1
                if calls['first']>=3:return (verified[0],'0'*64)
            return original(asset,size)
        with patch.object(self.service,'_content',side_effect=content):
            with self.assertRaises(ValueError):self.move(sourceProjectId='a')
        self.assertEqual(self.host.calls,[]);self.assertEqual(self.by_id('first')['projectId'],'a')

    def test_ambiguous_cached_originals_cannot_prove_content_equivalence(self):
        self.host.index['assets'].append(self.asset('ambiguous','/media/'+'2'*32+'.png','c'))
        self.host.persist()
        (self.root/'remote-media-cache.json').write_text(json.dumps({'ambiguous':'/media/'+'3'*32+'.png'}),'utf-8')
        result=self.move(sourceProjectId='a')
        self.assertEqual(result['updatedAssets'],1);self.assertEqual(result['mergedAssets'],0)
        self.assertEqual(self.by_id('ambiguous')['projectId'],'c')

    def test_all_scope_moves_memberships_without_copying(self):
        self.host.index['assets'].append(self.asset('third','/media/22222222222222222222222222222222.png','c'));self.host.persist()
        result=self.move()
        self.assertEqual(result['updatedAssets'],2)
        self.assertEqual({self.by_id(value)['projectId'] for value in ('first','third')},{'b'})
        self.assertEqual(len(self.host.index['assets']),3)

    def test_explicit_unassigned_scope_includes_unknown_project_references(self):
        self.host.index['assets'][0]['projectId']='missing';self.host.persist()
        result=self.move(sourceProjectId=None)
        self.assertEqual(result['movedEntries'],1);self.assertEqual(self.by_id('first')['projectId'],'b')

    def test_invalid_destination_or_stale_origin_does_not_write_any_selected_row(self):
        before=copy.deepcopy(self.host.index)
        for args in ({'projectId':'missing'},{'sourceProjectId':'c'},{'projectId':'archived'}):
            with self.assertRaises(ValueError):self.move(**args)
        with self.assertRaises(ValueError):self.move(['asset:first','asset:absent'])
        self.assertEqual(self.host.index,before);self.assertEqual(self.host.calls,[])

    def test_explicit_archived_and_unassigned_destinations(self):
        self.assertEqual(self.move(projectId='archived',allowArchivedProject=True)['movedEntries'],1)
        self.assertEqual(self.move(projectId=None,sourceProjectId='archived')['movedEntries'],1)
        self.assertIsNone(self.by_id('first')['projectId'])

    def test_repeated_move_is_a_verified_noop(self):
        self.move(sourceProjectId='a')
        calls=len(self.host.calls)
        result=self.move(sourceProjectId='a')
        self.assertEqual(result['movedEntries'],0);self.assertEqual(result['alreadyAtDestination'],1)
        self.assertEqual(result['updatedAssets'],0);self.assertEqual(len(self.host.calls),calls)

    def test_legacy_output_materializes_one_asset_without_fabricating_history(self):
        self.state['jobs'].append({'id':'legacy','modality':'IMAGE','status':'COMPLETED','outputs':['https://fixture.invalid/original.png'],
                                  'projectId':'a','startedAt':2})
        before=copy.deepcopy(self.state);generations=copy.deepcopy(self.host.index['generations'])
        result=self.move(['job:legacy:0'],sourceProjectId='a')
        self.assertEqual(result['movedEntries'],1);self.assertEqual(result['updatedAssets'],1)
        self.assertEqual(self.state,before);self.assertEqual(self.host.index['generations'],generations)
        created=[a for a in self.host.index['assets'] if a['id'].startswith('ast-library-')]
        self.assertEqual(len(created),1);self.assertEqual(created[0]['uri'],'https://fixture.invalid/original.png')
        self.assertTrue(any('job:legacy:0' in entry.get('identityKeys',[]) for entry in result['snapshot']['entries'] if entry.get('assetId')==created[0]['id']))
        self.assertEqual(self.move(['job:legacy:0'],sourceProjectId='a')['alreadyAtDestination'],1)

    def test_retired_output_cannot_be_resurrected_by_move(self):
        self.host.index['assets'][0]['libraryDeleted']=True;self.host.persist()
        before=copy.deepcopy(self.host.index)
        with self.assertRaises(ValueError):self.move()
        self.assertEqual(self.host.index,before)

    def test_partial_native_failure_is_reported_from_readback_without_rollback(self):
        self.host.fail_after=1
        result=self.move(['asset:first','asset:other'])
        self.assertEqual(result['status'],'partial');self.assertEqual(result['updatedAssets'],1)
        self.assertEqual(result['movedEntries'],1);self.assertEqual(len(result['failures']),1)
        self.assertEqual(self.by_id('first')['projectId'],'b');self.assertEqual(self.by_id('other')['projectId'],'a')
        self.assertEqual(len(self.host.calls),1)

    def test_lost_acknowledgement_is_success_only_after_exact_readback(self):
        self.host.lose_ack=True
        result=self.move()
        self.assertEqual(result['status'],'completed');self.assertEqual(result['updatedAssets'],1)
        self.assertEqual(len(self.host.calls),1)

    def test_import_dedup_sha_does_not_move_existing_association(self):
        before=copy.deepcopy(self.host.index)
        result=self.service.register_import(self.receipt(),'b')
        self.assertEqual(result,{'status':'exists','assetId':'first','projectId':'a'})
        self.assertEqual(self.host.index,before);self.assertEqual(self.host.calls,[])

    def test_import_preserves_retirement_and_imports_distinct_content_once(self):
        self.host.index['assets'][0]['libraryDeleted']=True;self.host.persist()
        result=self.service.register_import(self.receipt(),'b')
        self.assertEqual(result['status'],'retired');self.assertTrue(self.by_id('first')['libraryDeleted'])
        (self.root/'media/44444444444444444444444444444444.png').write_bytes(b'new-original')
        receipt=self.receipt('44444444444444444444444444444444.png')
        created=self.service.register_import(receipt,'b')
        self.assertEqual(created['status'],'created');self.assertEqual(created['projectId'],'b')
        again=self.service.register_import(receipt,'a')
        self.assertEqual(again['status'],'exists');self.assertEqual(again['assetId'],created['assetId'])
        self.assertEqual(again['projectId'],'b')

    def test_import_rejects_changed_bytes_and_unknown_destination(self):
        receipt=self.receipt();(self.root/'media/22222222222222222222222222222222.png').write_bytes(b'changed')
        with self.assertRaises(ValueError):self.service.register_import(receipt,'b')
        with self.assertRaises(ValueError):self.service.register_import(self.receipt('33333333333333333333333333333333.png'),'missing')
        self.assertEqual(self.host.calls,[])

    def test_dispatch_requires_server_receipt_and_keeps_client_metadata_out(self):
        import local_host
        importer=Mock();importer.completed_item.return_value=self.receipt()
        operation=Mock();operation.register_import.return_value={'status':'exists'}
        with patch.object(local_host,'media_importer',return_value=importer),patch.object(local_host,'library_operations',return_value=operation):
            local_host.dispatch({},'libraryImport',{'id':'22222222222222222222222222222222.png','projectId':'b','item':{'url':'/media/forged.png'}})
        importer.completed_item.assert_called_once_with('22222222222222222222222222222222.png')
        operation.register_import.assert_called_once_with(importer.completed_item.return_value,'b',allow_archived=False)


if __name__=='__main__':unittest.main(verbosity=2)
