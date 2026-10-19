"""Real Kotlin index, edit history, portable files and interrupted-import recovery."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid
from unittest.mock import patch
import zipfile
from PIL import Image

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from agent_host import AgentHost
from corte_host import CorteHost
from local_host import windows_keys,write_secret
from native_operations import NativeOperations
from portable_project import PortableProject,digest


class PortableAcceptance(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='nexus-portable-');self.data=Path(self.temp.name)
        self.root=Path(__file__).resolve().parents[1]
        self.host=AgentHost(self.root,self.data,windows_keys,write_secret)
        self.host.native=NativeOperations(self.data,self.host,lambda:{},lambda _:None,None,lambda:{})
        self.host.corte=CorteHost(self.root,self.data,self.host.native)
        self.service=PortableProject(self.host,allow_network=False)
        result=self.host.command({'op':'createProject','owner':'qa','args':{'name':'Original portable'}})['result']
        self.project=result['projectId']
        self.source=self.data/'media'/('a'*32+'.png');self.source.parent.mkdir(exist_ok=True);Image.new('RGB',(64,64),'#52cba6').save(self.source)
        snapshot=self.host.command({'op':'importReferences','owner':'qa','args':{'projectId':self.project,'files':[{'uri':'/media/'+self.source.name,'mime':'image/png','name':'Reference'}]}})['result']
        self.asset=snapshot['index']['assets'][0]
        self.host.corte.run({'method':'create_project','project_id':self.project,'settings':{'width':320,'height':240}})
        self.edit=self.host.corte.run({'method':'import_media','project_id':self.project,'expected_revision':0,'request_id':'place','asset_ids':[self.asset['id']]},[self.asset])
        self.original_hash=digest(self.source)

    def tearDown(self):
        self.host.close();self.host.corte.close();self.host.native.executor.shutdown();self.temp.cleanup()

    def archive(self):return self.service.command('export',{'projectId':self.project})
    def import_(self,archive,request='import-one'):return self.service.command('import',{'id':archive['url'],'request_id':request})

    def test_roundtrip_history_remapping_and_receipts(self):
        archive=self.archive()
        result=self.import_(archive)
        self.assertNotEqual(result['projectId'],self.project)
        snapshot=self.host.command({'op':'snapshot','owner':'qa'})['result']
        imported=[a for a in snapshot['index']['assets'] if a['projectId']==result['projectId']]
        self.assertEqual(len(imported),1);self.assertNotEqual(imported[0]['id'],self.asset['id'])
        self.assertEqual(digest(self.data/imported[0]['uri'].lstrip('/')),self.original_hash)
        edit=self.host.corte.run({'method':'read_project','project_id':result['projectId']})
        self.assertEqual(edit['project']['timeline'],self.edit['project']['timeline'])
        self.assertEqual(edit['links'][edit['project']['media'][0]['id']]['asset_id'],imported[0]['id'])
        self.host.corte.run({'method':'undo','project_id':result['projectId'],'expected_revision':edit['revision'],'request_id':'undo-copy','transaction_id':edit['undo_id']})
        self.assertEqual(self.host.corte.run({'method':'read_project','project_id':self.project})['project'],self.edit['project'])
        self.assertEqual(self.import_(archive),result)
        self.assertEqual(len(self.host.command({'op':'snapshot','owner':'qa'})['result']['index']['projects']),2)
        self.assertEqual(digest(self.source),self.original_hash)

    def test_interrupted_import_resumes_without_duplicate_project(self):
        archive=self.archive();exchange=self.host.exchange
        def interrupted(op,args):
            if op=='importProjectBundle':raise RuntimeError('synthetic power loss')
            return exchange(op,args)
        with patch.object(self.host,'exchange',side_effect=interrupted):
            with self.assertRaisesRegex(RuntimeError,'power loss'):self.import_(archive)
        self.assertEqual(len(self.host.command({'op':'snapshot','owner':'qa'})['result']['index']['projects']),1)
        result=self.import_(archive)
        self.assertEqual(self.import_(archive),result)
        self.assertEqual(len(self.host.command({'op':'snapshot','owner':'qa'})['result']['index']['projects']),2)

    def test_corrupted_file_and_zip_paths_leave_original_project_unchanged(self):
        archive=self.archive();source=self.data/archive['url'].lstrip('/')
        with zipfile.ZipFile(source) as z:contents={i.filename:z.read(i) for i in z.infolist()}
        for kind in ['hash','path','relation','montage']:
            damaged=dict(contents)
            if kind=='hash':damaged[next(n for n in damaged if n.startswith('media/'))]=b'corrupt'
            elif kind=='path':damaged['../escape.txt']=b'no'
            else:
                name='metadata/index.json' if kind=='relation' else 'metadata/corte.json'
                payload=json.loads(damaged[name])
                if kind=='relation':payload['assets'][0]['sceneId']=payload['assets'][0]['id']
                else:payload['project']['timeline']['videoTracks'][0]['clips'][0]['mediaId']='missing-media'
                damaged[name]=json.dumps(payload).encode()
                manifest=json.loads(damaged['manifest.json']);manifest['files'][name]={'size':len(damaged[name]),'sha256':hashlib.sha256(damaged[name]).hexdigest()}
                damaged['manifest.json']=json.dumps(manifest).encode()
            target=self.data/'media'/(uuid.uuid4().hex+'.zip')
            with zipfile.ZipFile(target,'w') as z:
                for name,value in damaged.items():z.writestr(name,value)
            with self.assertRaises((ValueError,RuntimeError)):self.import_({'url':'/media/'+target.name},kind)
        self.assertEqual(len(self.host.command({'op':'snapshot','owner':'qa'})['result']['index']['projects']),1)
        self.assertFalse((self.data/'escape.txt').exists());self.assertEqual(digest(self.source),self.original_hash)


if __name__=='__main__':unittest.main(verbosity=2)
