#!/usr/bin/env python3
"""Read-only Android library classification and project projection regressions."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from library_snapshot import LibrarySnapshot


class LibraryProjectSnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
        (self.root/'assistant').mkdir()
        self.index={
            'projects':[{'id':'a','name':'Alba / literal','createdAt':1},
                        {'id':'b','name':'Bosque','createdAt':2},
                        {'id':'archived','name':'Archivado','archived':True},
                        {'id':'archived-empty','name':'Vacío archivado','archived':True}],
            'scenes':[{'id':'scene-a','projectId':'a','code':'A01','title':'Escena real'}],
            'shots':[{'id':'shot-a','projectId':'a','sceneId':'scene-a','code':'A01-01','description':'Plano real'}],
            'assets':[
                {'id':'a-output','uri':'https://fixture.invalid/shared.png','type':'IMAGE','projectId':'a',
                 'source':'GENERATED','role':'OUTPUT','sceneId':'scene-a','shotId':'shot-a',
                 'characterIds':['person-a'],'tags':['azul'],'mimeType':'image/png'},
                {'id':'b-reference','uri':'https://fixture.invalid/shared.png','type':'IMAGE','projectId':'b',
                 'source':'SHARED','role':'REFERENCE','parentAssetId':'a-output','tags':['verde']},
                {'id':'old-hidden','uri':'https://fixture.invalid/shared.png','type':'IMAGE','projectId':'archived-empty','libraryDeleted':True},
                {'id':'unassigned','uri':'https://fixture.invalid/unassigned.png','type':'IMAGE','projectId':None,'source':'IMPORTED'},
                {'id':'edited','uri':'https://fixture.invalid/edited.png','type':'IMAGE','projectId':'archived',
                 'source':'IMPORTED','parentAssetId':'','role':'SOURCE','generationId':'exact-generation'},
                {'id':'unknown','uri':'https://fixture.invalid/unknown.png','type':'IMAGE','projectId':'absent'},
            ],
            'generations':[
                {'id':'old-generation','nexusJobId':'job','projectId':'a','outputAssetIds':[]},
                {'id':'current-generation','nexusJobId':'job','projectId':'b','outputAssetIds':[]},
                {'id':'first-output-generation','providerId':'wrong-provider','outputAssetIds':['edited']},
                {'id':'exact-generation','providerId':'exact-provider','outputAssetIds':[]},
            ],
        }
        self.state={'jobs':[{'id':'job','projectId':'a','parameters':{'__project_id':'a'},'status':'COMPLETED',
            'outputs':['https://fixture.invalid/shared.png','https://fixture.invalid/unassigned.png',
                       'https://fixture.invalid/legacy.png']}], 'draft':{'prompt':'Sin cambios'}}
        self.index_path=self.root/'assistant/index.json'
        self.index_path.write_text(json.dumps(self.index),'utf-8')
        self.service=LibrarySnapshot(self.root,lambda:self.state)

    def tearDown(self):
        self.temp.cleanup()

    def entries(self):
        return {row['id']:row for row in self.service.snapshot()['entries']}

    def test_exact_file_preserves_multiple_project_assignments(self):
        row=self.entries()['job:job:0']
        self.assertEqual(row['projectIds'],['a','b'])
        self.assertEqual(row['knownProjectIds'],['a','b'])
        self.assertTrue(row['projectAssignmentExplicit'])
        self.assertEqual([(m['projectId'],m['role'],m['originCategory']) for m in row['memberships']],
                         [('a','OUTPUT','GENERATED'),('b','REFERENCE','SHARED')])
        self.assertNotIn('archived-empty',row['projectIds'])

    def test_identical_bytes_at_distinct_paths_share_verified_identity(self):
        media=self.root/'media';media.mkdir()
        first='a'*32+'.png';second='b'*32+'.jpg'
        (media/first).write_bytes(b'same bytes')
        (media/second).write_bytes(b'same bytes')
        self.index['assets']=[{'id':'first','uri':'/media/'+first,'projectId':'a','type':'IMAGE'},
                              {'id':'second','uri':'/media/'+second,'projectId':'b','type':'IMAGE'}]
        self.index_path.write_text(json.dumps(self.index),'utf-8')
        rows=self.entries()
        keys=lambda row:{key for key in row['identityKeys'] if key.startswith('content:')}
        self.assertTrue(keys(rows['asset:first']))
        self.assertEqual(keys(rows['asset:first']),keys(rows['asset:second']))
        self.assertEqual(rows['asset:first']['projectIds'],['a'])
        self.assertEqual(rows['asset:second']['projectIds'],['b'])
        # Content edits invalidate the projection even when index metadata stays.
        (media/second).write_bytes(b'other data')
        changed=self.entries()
        self.assertNotEqual(keys(changed['asset:first']),keys(changed['asset:second']))

    def test_explicit_unassignment_overrides_original_job_project(self):
        row=self.entries()['job:job:1']
        self.assertEqual(row['projectIds'],[])
        self.assertEqual(row['knownProjectIds'],[])
        self.assertIsNone(row['memberships'][0]['projectId'])
        self.assertTrue(row['projectAssignmentExplicit'])

    def test_conflicting_cached_original_does_not_supply_content_merge_proof(self):
        media=self.root/'media';media.mkdir()
        first='a'*32+'.png';second='b'*32+'.png';cached='c'*32+'.png'
        for name,content in ((first,b'same'),(second,b'same'),(cached,b'else')):
            (media/name).write_bytes(content)
        self.index['assets']=[{'id':'first','uri':'/media/'+first,'projectId':'a','type':'IMAGE'},
                              {'id':'second','uri':'/media/'+second,'projectId':'b','type':'IMAGE'}]
        self.index_path.write_text(json.dumps(self.index),'utf-8')
        (self.root/'remote-media-cache.json').write_text(json.dumps({'first':'/media/'+cached}),'utf-8')
        rows=self.entries()
        self.assertFalse(any(key.startswith('content:') for key in rows['asset:first']['identityKeys']))

    def test_legacy_assignment_uses_parameter_then_latest_generation(self):
        row=self.entries()['job:job:2']
        self.assertEqual(row['projectIds'],['a'])
        self.assertFalse(row['projectAssignmentExplicit'])
        self.state['jobs'][0]['parameters'].clear()
        row=self.entries()['job:job:2']
        self.assertEqual(row['projectIds'],['b'])
        self.assertEqual(row['memberships'][0]['generationId'],'current-generation')

    def test_cache_refreshes_when_job_project_parameter_changes(self):
        first=self.service.snapshot()
        self.state['jobs'][0]['parameters']['__project_id']='b'
        second=self.service.snapshot()
        self.assertNotEqual(first['revision'],second['revision'])
        self.assertEqual({r['id']:r for r in second['entries']}['job:job:2']['projectIds'],['b'])

    def test_unknown_project_is_preserved_but_unfiled_in_known_context(self):
        row=self.entries()['asset:unknown']
        self.assertEqual(row['projectIds'],['absent'])
        self.assertEqual(row['knownProjectIds'],[])
        self.assertTrue(row['projectAssignmentExplicit'])

    def test_asset_classification_is_complete_and_keeps_native_origin_precedence(self):
        rows=self.entries()
        row=rows['asset:a-output'];member=row['memberships'][0]
        self.assertEqual(member['assetId'],'a-output')
        self.assertEqual(member['assignmentSource'],'asset')
        for key in ('sceneId','shotId','characterIds','tags','mimeType'):
            self.assertEqual(member[key],self.index['assets'][0][key])
        self.assertEqual(rows['asset:b-reference']['originCategory'],'SHARED')
        self.assertEqual(rows['asset:edited']['originCategory'],'EDITED')
        self.assertEqual(rows['asset:unknown']['originCategory'],'IMPORTED')

    def test_explicit_generation_id_takes_precedence_over_first_output_revision(self):
        row=self.entries()['asset:edited']
        self.assertEqual(row['generationId'],'exact-generation')
        self.assertEqual(row['providerId'],'exact-provider')

    def test_projects_remain_flat_and_archived_metadata_is_available_for_ui_counts(self):
        snapshot=self.service.snapshot()
        self.assertEqual(snapshot['projects'],self.index['projects'])
        self.assertEqual([r['id'] for r in snapshot['folders']],['a','b','archived','archived-empty'])
        self.assertTrue(all(r['parentId'] is None and r['projectId']==r['id'] and r['kind']=='project' for r in snapshot['folders']))
        self.assertEqual(snapshot['folders'][0]['name'],'Alba / literal')
        self.assertEqual(snapshot['scenes'],self.index['scenes'])
        self.assertEqual(snapshot['shots'],self.index['shots'])

    def test_snapshot_never_changes_index_history_or_active_draft(self):
        state_before=copy.deepcopy(self.state)
        digest_before=hashlib.sha256(self.index_path.read_bytes()).digest()
        files_before=sorted(p.relative_to(self.root).as_posix() for p in self.root.rglob('*') if p.is_file())
        self.service.snapshot()
        self.assertEqual(self.state,state_before)
        self.assertEqual(hashlib.sha256(self.index_path.read_bytes()).digest(),digest_before)
        self.assertEqual(sorted(p.relative_to(self.root).as_posix() for p in self.root.rglob('*') if p.is_file()),files_before)


if __name__=='__main__':
    unittest.main(verbosity=2)
