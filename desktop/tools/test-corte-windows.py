"""Real JVM/domain, Windows media and export acceptance; isolated data, no account calls."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
import uuid

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from corte_host import CorteHost
from corte_render import render_project
from native_operations import NativeOperations


class CorteAcceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(prefix='nexus-corte-acceptance-')
        cls.data=Path(cls.temp.name);cls.root=Path(__file__).resolve().parents[1]
        cls.native=NativeOperations(cls.data,None,lambda:{},lambda s:None,None,lambda:{})
        cls.host=CorteHost(cls.root,cls.data,cls.native)
        cls.source=cls.data/'media'/('a'*32+'.mp4')
        cls.native.encode(['-f','lavfi','-i','testsrc2=s=320x240:r=25:d=4','-f','lavfi','-i','sine=frequency=440:sample_rate=48000:duration=4','-c:v','libx264','-c:a','aac',cls.source])
        cls.original_hash=CorteHost.fingerprint(cls.source)
        cls.asset={'id':'asset-test','uri':'/media/'+cls.source.name,'type':'VIDEO','displayName':'Clip local de prueba','mimeType':'video/mp4','projectId':'qa'}
        cls.snap=cls.host.run({'method':'create_project','project_id':'qa','name':'Prueba Corte Windows','settings':{'width':320,'height':240}})
    @classmethod
    def tearDownClass(cls):
        cls.host.close();cls.native.executor.shutdown();cls.temp.cleanup()
    def mutate(self,method,**args):
        result=self.host.run({'method':method,'project_id':'qa','expected_revision':self.snap['revision'],'request_id':uuid.uuid4().hex,**args},[self.asset])
        if result.get('project'):type(self).snap=result
        return result
    def clips(self,kind='videoTracks'):
        return [c for t in self.snap['project']['timeline'][kind] for c in t['clips']]
    def test_01_full_edit_flow(self):
        self.mutate('import_media',asset_ids=['asset-test'])
        first=self.clips()[0];self.assertEqual(first['durationUs'],4_000_000)
        self.mutate('apply_ops',ops=[{'op':'duplicate','clip_ids':[first['id']]}])
        self.assertEqual(len(self.clips()),2)
        self.mutate('apply_ops',ops=[{'op':'split','at_us':t} for t in [5_000_000,6_000_000,7_000_000]])
        pieces=sorted(self.clips(),key=lambda c:c['timelineStartUs'])[1:]
        self.assertEqual([c['durationUs'] for c in pieces],[1_000_000]*4)
        self.mutate('apply_ops',ops=[
            {'op':'set_color','clip_ids':[pieces[0]['id']],'saturation':-1},
            {'op':'set_color','clip_ids':[pieces[1]['id']],'temperature':-1},
            {'op':'set_color','clip_ids':[pieces[2]['id']],'tint':-1}])
        colored=self.snap;transaction=self.snap['undo_id']
        self.assertEqual(self.snap['playhead_us'],4_000_000)
        self.assertEqual(set(self.snap['selection']),{c['id'] for c in pieces[:3]})
        self.mutate('undo',transaction_id=transaction)
        self.assertEqual(self.clips()[1]['video']['color']['saturation'],0)
        self.mutate('redo',transaction_id=transaction)
        self.assertEqual(self.clips()[1]['video']['color']['saturation'],-1)
        self.host.close()
        reopened=self.host.run({'method':'read_project','project_id':'qa'})
        self.assertEqual(reopened['project'],self.snap['project'])
        self.assertEqual(reopened['undo_id'],self.snap['undo_id'])
    def test_02_transaction_safety(self):
        before=self.host.run({'method':'read_project','project_id':'qa'})
        with self.assertRaises(ValueError):
            self.mutate('apply_ops',ops=[{'op':'add_marker','at_us':1,'label':'Never committed'},{'op':'delete','clip_ids':['missing']}])
        self.assertEqual(before,self.host.run({'method':'read_project','project_id':'qa'}))
        args={'method':'apply_ops','project_id':'qa','expected_revision':self.snap['revision'],'request_id':uuid.uuid4().hex,'ops':[{'op':'add_marker','at_us':100000,'label':'Receipt'}]}
        once=self.host.run(args);self.host.close();twice=self.host.run(args)
        self.assertEqual(once['project'],twice['project']);self.assertEqual(once['revision'],twice['revision']);self.assertTrue(twice['replayed']);type(self).snap=once
        with self.assertRaises(ValueError):self.host.run({**args,'ops':[]})
        with self.assertRaises(ValueError):self.host.run({**args,'request_id':uuid.uuid4().hex})
        agent_transaction=self.snap['undo_id']
        self.mutate('apply_ops',ops=[{'op':'add_marker','at_us':200000,'label':'Manual posterior'}])
        with self.assertRaises(ValueError):self.mutate('undo',transaction_id=agent_transaction)
        self.assertEqual(CorteHost.fingerprint(self.source),self.original_hash)
    def test_03_render_preview_and_export(self):
        tracks=self.snap['project']['timeline']['videoTracks'];clips=tracks[0]['clips']
        self.mutate('apply_ops',ops=[{'op':'add_title','at_us':500000,'duration_us':2000000,'text':'CORTE LOCAL'},
            {'op':'add_marker','at_us':1_000_000,'label':'Marcador'},
            {'op':'set_video','clip_ids':[clips[0]['id']],'fade_in_us':200000,'fade_out_us':200000},
            {'op':'set_audio','clip_ids':[self.clips('audioTracks')[0]['id']],'fade_in_us':200000,'fade_out_us':200000},
            {'op':'set_transition','track_id':tracks[0]['id'],'left_clip_id':clips[0]['id'],'right_clip_id':clips[1]['id'],'kind':'DIP_TO_BLACK','duration_us':500000}])
        target=self.data/'export.mp4';preview=self.data/'preview.mp4'
        immutable=self.snap
        self.mutate('apply_ops',ops=[{'op':'add_marker','at_us':300000,'label':'Edición mientras exporta'}])
        render_project(self.host,immutable,target,lambda *_:None)
        render_project(self.host,immutable,preview,lambda *_:None,preview=True)
        info=self.native.inspect(target);self.assertTrue(info['has_audio']);self.assertAlmostEqual(info['durationSec'],8,delta=.1)
        self.assertEqual((info['width'],info['height']),(320,240))
        # Same compositor and same dimensions: decoded RGB must match exactly.
        def frame(path,t):
            return self.native.process([self.native.ffmpeg(),'-v','error','-ss',str(t),'-i',str(path),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],30)
        for t in [1,4.5,5.5,6.5]:self.assertEqual(frame(target,t),frame(preview,t))
        type(self).rendered=target
    def test_04_missing_media_and_cancellation(self):
        renamed=self.source.with_suffix('.unavailable');self.source.rename(renamed)
        try:
            with self.assertRaises(ValueError):render_project(self.host,self.snap,self.data/'missing.mp4',lambda *_:None)
        finally:renamed.rename(self.source)
        def cancel(*_):raise InterruptedError('Cancelación de prueba')
        with self.assertRaises(InterruptedError):render_project(self.host,self.snap,self.data/'cancel.mp4',cancel)
        self.assertFalse((self.data/'cancel.mp4').exists())
        self.assertEqual(CorteHost.fingerprint(self.source),self.original_hash)
    def test_05_waveform_thumbnail_and_silence_adapter(self):
        mid=self.snap['project']['media'][0]['id']
        result=self.host.run({'method':'media_analysis','project_id':'qa','media_id':mid,'pixels':100})
        self.assertEqual(len(result['max']),100);self.assertTrue(result['thumbnail'].startswith('/media/'))
        self.assertGreater(max(result['max']),0)
        silence=self.host.run({'method':'find_silences','project_id':'qa'})
        self.assertEqual(silence['suggested_op']['op'],'delete_ranges')
    def test_06_project_scope_for_late_results(self):
        self.host.run({'method':'create_project','project_id':'other','name':'Otro proyecto'})
        before=self.host.run({'method':'read_project','project_id':'other'})
        with self.assertRaises(ValueError):
            self.host.run({'method':'import_media','project_id':'other','expected_revision':0,'request_id':uuid.uuid4().hex,'asset_ids':['asset-test']},[self.asset])
        self.assertEqual(before,self.host.run({'method':'read_project','project_id':'other'}))

    def test_07_multitrack_audio_and_real_mix(self):
        import numpy as np
        project='multiaudio'
        snap=self.host.run({'method':'create_project','project_id':project,'name':'Mezcla multipista','settings':{'width':320,'height':240}})
        def edit(method,assets=None,**args):
            nonlocal snap
            result=self.host.run({'method':method,'project_id':project,'expected_revision':snap['revision'],'request_id':uuid.uuid4().hex,**args},assets)
            if result.get('project'):snap=result
            return result
        edit('apply_ops',ops=[{'op':'add_audio_track','name':f'Pista {i}'} for i in range(24)])
        self.assertEqual(25,len(snap['project']['timeline']['audioTracks']))
        transaction=snap['undo_id'];edit('undo',transaction_id=transaction)
        self.assertEqual(1,len(snap['project']['timeline']['audioTracks']))
        edit('redo',transaction_id=transaction);self.assertEqual(25,len(snap['project']['timeline']['audioTracks']))
        edit('undo',transaction_id=transaction)
        args={'method':'apply_ops','project_id':project,'expected_revision':snap['revision'],'request_id':uuid.uuid4().hex,'ops':[{'op':'add_audio_track','name':'Música','role':'MUSIC'}]}
        snap=self.host.run(args);replay=self.host.run(args)
        self.assertTrue(replay['replayed']);self.assertEqual(2,len(replay['project']['timeline']['audioTracks']))
        track=snap['project']['timeline']['audioTracks'][-1]['id']
        before=snap
        with self.assertRaises(ValueError):edit('apply_ops',ops=[{'op':'add_audio_track','name':'No guardar'},{'op':'set_audio_track','track_id':'missing','muted':True}])
        after=self.host.run({'method':'read_project','project_id':project});self.assertEqual(before['project'],after['project']);self.assertEqual(before['revision'],after['revision'])
        with self.assertRaises(ValueError):edit('apply_ops',ops=[{'op':'set_audio_track','track_id':track,'gain_db':90}])
        edit('import_media',assets=[{**self.asset,'projectId':project}],asset_ids=[self.asset['id']],at_us=0)
        original_video=snap['project']['timeline']['videoTracks']
        sound=self.data/'media'/('b'*32+'.wav')
        self.native.encode(['-f','lavfi','-i','sine=frequency=880:sample_rate=48000:duration=4','-c:a','pcm_s16le',sound])
        asset={'id':'music-test','uri':'/media/'+sound.name,'type':'AUDIO','displayName':'Música 880 Hz','projectId':project}
        edit('import_media',assets=[asset],asset_ids=[asset['id']],audio_track_id=track,at_us=0,mode='mix')
        self.assertEqual(original_video,snap['project']['timeline']['videoTracks'])
        self.assertEqual([0,0],[t['clips'][0]['timelineStartUs'] for t in snap['project']['timeline']['audioTracks']])
        before=snap
        with self.assertRaises(ValueError):edit('import_media',assets=[asset],asset_ids=[asset['id']],audio_track_id=track,at_us=0,mode='mix')
        after=self.host.run({'method':'read_project','project_id':project});self.assertEqual(before['project'],after['project']);self.assertEqual(before['revision'],after['revision'])
        edit('apply_ops',ops=[{'op':'set_audio_track','track_id':track,'name':'Banda sonora','gain_db':-6}])
        target=self.data/'multitrack.mp4';render_project(self.host,snap,target,lambda *_:None)
        pcm=self.native.process([self.native.ffmpeg(),'-v','error','-i',str(target),'-f','f32le','-ac','1','-ar','48000','pipe:1'],30)
        signal=np.frombuffer(pcm,dtype='<f4')[24000:144000];time=np.arange(len(signal))/48000
        for hz in [440,880]:self.assertGreater(abs(np.dot(signal,np.exp(-2j*np.pi*hz*time)))*2/len(signal),.02)
        def gains():return [layer['gain'] for layer in self.host.exchange('render_samples',{'project_id':project,'expected_revision':snap['revision'],'times':[1000000]})['samples'][0]['audio']]
        edit('apply_ops',ops=[{'op':'set_audio_track','track_id':track,'muted':True}]);self.assertEqual(0,gains()[1])
        edit('apply_ops',ops=[{'op':'set_audio_track','track_id':track,'muted':False,'solo':True}]);self.assertEqual(0,gains()[0]);self.assertAlmostEqual(10**(-6/20),gains()[1],places=5)
        transaction=snap['undo_id'];edit('undo',transaction_id=transaction);self.assertGreater(gains()[0],0);self.assertEqual(0,gains()[1]);edit('redo',transaction_id=transaction)
        self.host.close();reopened=self.host.run({'method':'read_project','project_id':project})
        self.assertEqual(snap['project'],reopened['project']);self.assertEqual(self.original_hash,CorteHost.fingerprint(self.source))

if __name__=='__main__':unittest.main(verbosity=2)
