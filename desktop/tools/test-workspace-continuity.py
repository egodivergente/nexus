"""Immutable work snapshots, stable media, explicit resume and encrypted peer handoff."""
import copy
import base64
import io
import json
from pathlib import Path
import shutil
import sys
import tempfile
import threading
import unittest
import uuid

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from workspace_continuity import WorkspaceContinuity, CAPABILITY
from device_continuity import DeviceContinuity, encoded


class WorkTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.state={'draft':{'modelId':'old','prompt':'Borrador local sin perder','values':{'seed':7},'media':[]},'jobs':[]}
        self.writes=[]
        self.source=WorkspaceContinuity(self.root/'a',lambda:copy.deepcopy(self.state),lambda s:self.writes.append(s))
        (self.source.data/'media').mkdir()
        (self.source.data/'assistant').mkdir()
        (self.source.data/'assistant/index.json').write_text(json.dumps({'projects':[{'id':'project_one'}],'assets':[]}))
        self.media=self.source.data/'media'/('a'*32+'.png');self.media.write_bytes(b'original-reference-bytes')
        self.args={'projectId':'project_one','title':'Secuencia 1','view':'canvas','shared':True,
                   'canvas':{'modelId':'model/one','providerId':'provider','prompt':'Una escena','values':{'seed':42,'guidance':2.5},
                             'references':[{'url':'/media/'+self.media.name,'name':'Referencia.png','mime':'image/png','field':'image'}]},
                   'editor':{'projectId':'project_one','playheadUs':5000,'zoom':70},'library':{'collection':'project_one','sort':'recent'}}

    def tearDown(self):self.temp.cleanup()

    def snapshot(self):
        result=self.source.capture(self.args)
        return self.source._snapshot(result['snapshot']['id'])

    def target(self):
        service=WorkspaceContinuity(self.root/'b',lambda:copy.deepcopy(self.state))
        (service.data/'assistant').mkdir();(service.data/'assistant/index.json').write_text(json.dumps({'projects':[{'id':'project_one'}]}))
        return service

    def test_snapshot_is_durable_and_wire_has_no_device_paths_or_upload_urls(self):
        snapshot=self.snapshot();self.assertEqual(snapshot['canvas']['references'][0]['field'],'image')
        self.assertNotIn('/media/',json.dumps(snapshot));self.assertNotIn(str(self.root),json.dumps(snapshot))
        self.media.unlink()
        restarted=WorkspaceContinuity(self.source.data,lambda:self.state)
        self.assertEqual(restarted.list()[0]['missingReferences'],0)
        self.assertEqual(restarted.open(snapshot['id'])['draft']['media'][0]['field'],'image')
        self.assertEqual(len(self.writes),0)

    def test_receive_never_applies_and_explicit_open_preserves_conflicting_local_draft(self):
        snapshot=self.snapshot();target=self.target();before=copy.deepcopy(self.state)
        target.receive(snapshot,lambda sha,path:shutil.copyfile(self.source.blob(sha),path))
        self.assertEqual(self.state,before)
        patch=target.open(snapshot['id'],{'projectId':'project_one','view':'canvas'});self.assertTrue(patch['requiresReview']);self.assertFalse(patch['paidExecutionEnabled'])
        self.assertEqual(patch['draft']['values'],{'seed':42,'guidance':2.5});self.assertEqual(self.state,before)
        backup=target.command({'op':'restoreBackup','id':patch['preservedLocalDraft']})
        self.assertEqual(backup['draft'],before['draft'])
        self.assertEqual(backup['projectId'],'project_one');self.assertTrue(backup['projectAvailable'])
        local=target.data/patch['draft']['media'][0]['url'].lstrip('/')
        self.assertEqual(local.read_bytes(),b'original-reference-bytes')

    def test_each_capture_preserves_its_own_version(self):
        first=self.snapshot();self.args['canvas']['prompt']='Otra escena';second=self.snapshot()
        self.assertNotEqual(first['id'],second['id']);self.assertEqual(len(self.source.list()),2)
        self.assertEqual(self.source._snapshot(first['id'])['canvas']['prompt'],'Una escena')

    def test_unshared_work_is_not_published(self):
        self.args['shared']=False;self.snapshot();self.assertEqual(self.source.export(),[])

    def test_unknown_root_fields_and_credentials_are_rejected(self):
        snapshot=self.snapshot();snapshot['toolCalls']=[{'op':'generate'}]
        with self.assertRaises(ValueError):self.source.validate(snapshot)
        self.args['canvas']['values']['api_key']='must-never-travel'
        with self.assertRaisesRegex(ValueError,'credenciales'):self.source.capture(self.args)

    def test_parameter_url_credentials_are_rejected_before_export(self):
        for url in ['https://user:password@example.invalid/api','https://example.invalid/api?access_token=private']:
            self.args['canvas']['values']={'endpoint':url}
            with self.assertRaisesRegex(ValueError,'credenciales'):self.source.capture(self.args)

    def test_malformed_schema_has_actionable_errors(self):
        snapshot=self.snapshot()
        for key,value in [('schemaVersion',2),('projectId',{}),('view',[]),('id','../../bad')]:
            malformed=copy.deepcopy(snapshot);malformed[key]=value
            with self.assertRaises(ValueError):self.source.validate(malformed)

    def test_peer_cannot_mutate_an_existing_snapshot_revision(self):
        snapshot=self.snapshot();target=self.target();target.receive(snapshot)
        changed=copy.deepcopy(snapshot);changed['canvas']['prompt']='Changed remotely'
        with self.assertRaisesRegex(ValueError,'cambiado'):target.receive(changed)
        self.assertEqual(target._snapshot(snapshot['id'])['canvas']['prompt'],'Una escena')

    def test_corrupt_media_remains_missing_then_can_be_retried(self):
        snapshot=self.snapshot();target=self.target()
        with self.assertRaisesRegex(ValueError,'integridad'):
            target.receive(snapshot,lambda sha,path:path.write_bytes(b'wrong'))
        self.assertEqual(target.list()[0]['missingReferences'],1);self.assertFalse(list(target.blobs.glob('*.part')))
        target.receive(snapshot,lambda sha,path:shutil.copyfile(self.source.blob(sha),path))
        self.assertEqual(target.list()[0]['missingReferences'],0)

    def test_missing_reference_is_explicit_and_does_not_include_a_signed_url(self):
        self.args['canvas']['references']=[{'url':'https://example.invalid/original?token=private','name':'Ausente','mime':'image/png','field':'image'}]
        snapshot=self.snapshot();self.assertNotIn('private',json.dumps(snapshot));patch=self.source.open(snapshot['id'])
        self.assertEqual(len(patch['missingReferences']),1);self.assertEqual(patch['draft']['media'],[])

    def test_unknown_project_is_reported_before_ui_applies_it(self):
        snapshot=self.snapshot();target=WorkspaceContinuity(self.root/'b',lambda:self.state);target.receive(snapshot)
        self.assertFalse(target.open(snapshot['id'])['projectAvailable'])

    def test_peer_capability_is_truthful_for_current_android(self):
        peer=str(uuid.uuid4());self.source.observe_peer(peer,['skill','settings'])
        self.assertFalse(self.source.peers[peer]['workspaceCompatible'])
        self.source.observe_peer(peer,[CAPABILITY]);self.assertTrue(self.source.peers[peer]['workspaceCompatible'])

    def test_encrypted_protocol_advertises_and_receives_inert_work_with_verified_blobs(self):
        snapshot=self.snapshot();target=self.target()
        class Host:
            def __init__(self,service):
                self.data=service.data/'assistant';self.data.mkdir(exist_ok=True);self.workspace_continuity=service
                self.store={};self.lock=threading.Lock();self.process=None
            def read_secret(self,p):return copy.deepcopy(self.store.get(str(p),{}))
            def write_secret(self,p,v):self.store[str(p)]=copy.deepcopy(v)
            def settings(self):return {}
        a=DeviceContinuity(Host(self.source),start=False);b=DeviceContinuity(Host(target),start=False)
        for peer in (a,b):
            peer.config.update(skills=False,settings=False,credentials=False,agy=False,enabled=True,port=0,timeout=5)
            peer.host.write_secret(peer.secret_file,{'key':base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip('=')})
        a.publish();self.assertIn(CAPABILITY,a.manifest['scopes'])
        a.start_server()
        try:
            b.cycle('http://127.0.0.1:'+str(a.server.server_port))
        finally:
            a.server.shutdown();a.server.server_close()
        self.assertEqual(target.list()[0]['id'],snapshot['id']);self.assertEqual(target.list()[0]['missingReferences'],0)
        self.assertEqual(self.state['draft']['modelId'],'old');self.assertEqual(self.writes,[])


if __name__=='__main__':unittest.main()
