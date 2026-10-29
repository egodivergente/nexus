import base64
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import uuid
from types import SimpleNamespace
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from android_handoff import AndroidHandoff


class FakeADB:
    def __init__(self):self.calls=[];self.drafts=[];self.inputs=[];self.paid=False;self.timeout=False
    def __call__(self,args,**kwargs):
        self.calls.append(args);a=args[1:]
        if a==['devices','-l']:return SimpleNamespace(returncode=0,stdout='List of devices attached\nPIXEL device model:Pixel_8_Pro\n100.1.1.1:5555 device model:Pixel_8_Pro\n')
        if 'getprop' in a:return SimpleNamespace(returncode=0,stdout='Pixel 8 Pro\n')
        if 'dumpsys' in a:return SimpleNamespace(returncode=0,stdout='versionCode=158 versionName=1.9.122-agent-preview')
        if 'am' in a:return SimpleNamespace(returncode=0,stdout='Starting: Intent')
        if 'exec-out' in a:return SimpleNamespace(returncode=0,stdout='<map><string name="draft">{&quot;modelId&quot;:&quot;old-model&quot;,&quot;prompt&quot;:&quot;Borrador anterior&quot;}</string></map>')
        method=a[a.index('--method')+1]
        value=json.loads(base64.urlsafe_b64decode(a[a.index('--arg')+1])) if '--arg' in a else {}
        if method=='status':result={'ok':True,'protocol':1,'paidExecutionEnabled':self.paid}
        elif method=='list_drafts':result={'ok':True,'drafts':self.drafts.copy()}
        elif method=='prepare_draft':
            self.inputs.append(value)
            if self.timeout:raise subprocess.TimeoutExpired(args,20)
            identifier=str(uuid.uuid4());self.drafts.append({'draftId':identifier,'modelId':value['modelId'],'providerId':value['providerId']})
            result={'ok':True,'draftId':identifier,'status':'prepared_not_submitted','paidExecutionEnabled':False}
        elif method=='discard_draft':self.drafts=[d for d in self.drafts if d['draftId']!=value['draftId']];result={'ok':True}
        else:raise AssertionError(method)
        return SimpleNamespace(returncode=0,stdout='Result: Bundle[{nexus_result='+base64.urlsafe_b64encode(json.dumps(result).encode()).decode()+'}]')


class HandoffTest(unittest.TestCase):
    def setUp(self):
        self.runner=FakeADB();self.service=AndroidHandoff(adb='adb',runner=self.runner)
        self.snapshot={'id':str(uuid.uuid4()),'projectId':'project','canvas':{'modelId':'model','providerId':'provider','prompt':'Una escena',
            'values':{'seed':7,'audio':True},'references':[{'assetId':'a'}]}}

    def test_prepares_exact_native_draft_without_launch_or_generation(self):
        result=self.service.prepare(self.snapshot,'PIXEL')
        self.assertTrue(result['prepared']);self.assertFalse(result['launched']);self.assertFalse(result['paidExecutionEnabled'])
        self.assertFalse(result['projectTransferred']);self.assertEqual(result['missingReferences'],1)
        self.assertEqual(self.runner.inputs,[{'modelId':'model','providerId':'provider','prompt':'Una escena','parameters':{'seed':'7','audio':'true'}}])
        self.assertFalse(any('am' in c for c in self.runner.calls))

    def test_repeated_verified_handoff_reuses_owned_native_draft(self):
        one=self.service.prepare(self.snapshot,'PIXEL');two=self.service.prepare(self.snapshot,'PIXEL')
        self.assertEqual(one['draftId'],two['draftId']);self.assertEqual(len(self.runner.inputs),1)

    def test_only_explicit_launch_opens_activity(self):
        with tempfile.TemporaryDirectory() as folder:
            service=AndroidHandoff(data=folder,adb='adb',runner=self.runner)
            result=service.prepare(self.snapshot,'PIXEL',launch=True)
            launches=[c for c in self.runner.calls if 'am' in c];self.assertEqual(len(launches),1)
            self.assertIn('nexus_agent_draft_id',launches[0])
            backup=Path(folder)/'workspace-continuity/android-draft-backups'/(result['preservedAndroidDraft']+'.json')
            self.assertEqual(json.loads(backup.read_text())['draft']['prompt'],'Borrador anterior')

    def test_stale_serial_is_rejected_before_any_draft_creation(self):
        with self.assertRaises(ValueError):self.service.prepare(self.snapshot,'STALE')
        self.assertEqual(self.runner.inputs,[])

    def test_bridge_must_advertise_non_paid_contract(self):
        self.runner.paid=True
        with self.assertRaises(ValueError):self.service.prepare(self.snapshot,'PIXEL')
        self.assertEqual(self.runner.inputs,[])

    def test_nested_or_secret_parameters_are_rejected(self):
        for values in [{'apiKey':'secret'},{'nested':{'a':1}}]:
            self.snapshot['canvas']['values']=values
            with self.assertRaises(ValueError):self.service.prepare(self.snapshot,'PIXEL')
        self.assertEqual(self.runner.inputs,[])

    def test_uncertain_creation_is_not_repeated(self):
        self.runner.timeout=True
        with self.assertRaises(ValueError):self.service.prepare(self.snapshot,'PIXEL')
        with self.assertRaises(ValueError):self.service.prepare(self.snapshot,'PIXEL')
        self.assertEqual(len(self.runner.inputs),1)

    def test_changed_snapshot_body_cannot_reuse_an_existing_version(self):
        self.service.prepare(self.snapshot,'PIXEL');self.snapshot['canvas']['prompt']='Changed'
        with self.assertRaises(ValueError):self.service.prepare(self.snapshot,'PIXEL')
        self.assertEqual(len(self.runner.inputs),1)

    def test_device_inventory_deduplicates_same_phone_transport(self):
        devices=self.service.devices()['devices'];self.assertEqual(len(devices),1);self.assertEqual(devices[0]['serial'],'PIXEL')

    def test_only_owned_isolated_test_drafts_can_be_discarded(self):
        with self.assertRaises(ValueError):self.service.discard_owned_test('PIXEL',str(uuid.uuid4()))
        result=self.service.prepare(self.snapshot,'PIXEL');self.service.discard_owned_test('PIXEL',result['draftId'])
        self.assertEqual(self.runner.drafts,[])


if __name__=='__main__':unittest.main()
