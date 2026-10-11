"""Real local media roundtrips and MCP error/transport contracts. No provider generation."""
from pathlib import Path
import hashlib
import json
import math
import struct
import sys
import tempfile
import unittest
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from native_operations import NativeOperations, resolve_media
from native_internet import MCP


class Host:
    def __init__(self):
        self.secrets = {}
    def read_secret(self, path): return self.secrets.get(str(path), {})
    def write_secret(self, path, value): self.secrets[str(path)] = value
    def public_settings(self): return {}


class MediaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='nexus-parity-test-')
        cls.data = Path(cls.temp.name)
        cls.native = NativeOperations(cls.data, Host(), lambda: {}, lambda state: None, None, lambda: {})
        cls.video = cls.native.new_path('.mp4')
        cls.silent = cls.native.new_path('.mp4')
        cls.audio = cls.native.new_path('.wav')
        cls.native.encode(['-f', 'lavfi', '-i', 'color=c=blue:s=320x240:r=30:d=2', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=2', '-c:v', 'libx264', '-c:a', 'aac', '-shortest', cls.video])
        cls.native.encode(['-f', 'lavfi', '-i', 'color=c=red:s=160x240:r=24:d=1', '-c:v', 'libx264', cls.silent])
        with wave.open(str(cls.audio), 'wb') as output:
            output.setnchannels(1); output.setsampwidth(2); output.setframerate(16000)
            output.writeframes(b''.join(struct.pack('<h', int(math.sin(i*2*math.pi*880/16000)*12000)) for i in range(16000)))
        cls.assets = [{'id': id, 'uri': '/media/'+path.name, 'type': kind, 'displayName': id, 'projectId': 'project1'} for id,path,kind in [('video', cls.video,'VIDEO'),('silent',cls.silent,'VIDEO'),('audio',cls.audio,'AUDIO')]]
        cls.original = hashlib.sha256(cls.video.read_bytes()).hexdigest()

    @classmethod
    def tearDownClass(cls):
        cls.native.executor.shutdown()
        cls.temp.cleanup()

    def completed(self, operation, arguments):
        queued = self.native.run({'operation': operation, 'input': arguments, 'assets': self.assets})
        result = self.native.get_job({'edit_job_id': queued['edit_job_id'], 'wait_seconds': 20})
        self.assertEqual('COMPLETED', result['state'], result.get('error'))
        self.assertEqual(self.original, hashlib.sha256(self.video.read_bytes()).hexdigest())
        return result['results']

    def test_trim_and_original(self):
        asset = self.completed('edit_video_assets', {'action': 'trim', 'asset_ids': ['video'], 'start_ms': 500, 'end_ms': 1500})[0]
        metadata = self.native.inspect(resolve_media(self.data, asset['uri']))
        self.assertAlmostEqual(1, metadata['durationSec'], delta=.15)
        self.assertTrue(metadata['has_audio'])
        self.assertEqual('video', asset['parentAssetId'])

    def test_concat_normalizes_dimensions_and_missing_audio(self):
        asset = self.completed('edit_video_assets', {'action': 'concat', 'asset_ids': ['video', 'silent']})[0]
        metadata = self.native.inspect(resolve_media(self.data, asset['uri']))
        self.assertAlmostEqual(3, metadata['durationSec'], delta=.2)
        self.assertEqual(320, metadata['width'])
        self.assertTrue(metadata['has_audio'])

    def test_remove_audio_and_extract_audio(self):
        silent = self.completed('edit_video_assets', {'action': 'remove_audio', 'asset_ids': ['video']})[0]
        self.assertFalse(self.native.inspect(resolve_media(self.data, silent['uri']))['has_audio'])
        audio = self.completed('edit_video_assets', {'action': 'extract_audio', 'asset_ids': ['video']})[0]
        with wave.open(str(resolve_media(self.data, audio['uri']))) as stream:
            self.assertEqual(16000, stream.getframerate())
            self.assertEqual(1, stream.getnchannels())

    def test_audio_replace_and_mix(self):
        for action in ['replace_audio', 'mix_audio']:
            asset = self.completed('edit_video_assets', {'action': action, 'asset_ids': ['video'], 'audio_asset_id': 'audio'})[0]
            metadata = self.native.inspect(resolve_media(self.data, asset['uri']))
            self.assertTrue(metadata['has_audio'])
            self.assertAlmostEqual(2, metadata['durationSec'], delta=.2)

    def test_frames_and_failed_job_does_not_stall_queue(self):
        broken = self.native.queue('edit_video_assets', {'action': 'trim', 'asset_ids': ['video'], 'start_ms': 3000, 'end_ms': 5000}, self.assets)
        self.assertEqual('FAILED', self.native.get_job({'edit_job_id': broken['edit_job_id'], 'wait_seconds': 20})['state'])
        results = self.completed('extract_video_frames', {'asset_id': 'video', 'timestamps_ms': [0, 1000]})
        from PIL import Image
        self.assertEqual(2, len(results))
        with Image.open(resolve_media(self.data, results[0]['uri'])) as image:
            self.assertGreater(image.getpixel((100,100))[2], 150)

    def test_rename_keeps_uris_and_conversion_preserves_original(self):
        from PIL import Image
        original = self.native.new_path('.png')
        Image.new('RGB', (400,200), 'green').save(original)
        asset = {'id':'picture','uri':'/media/'+original.name,'type':'IMAGE','displayName':'picture.png'}
        renamed = self.native.run({'operation':'rename_asset_file','input':{'asset_id':'picture','file_name':'Referencia.png'},'assets':[asset]})
        self.assertEqual('Referencia.png', resolve_media(self.data, asset['uri']).name)
        result = self.native.run({'operation':'convert_image_asset','input':{'asset_id':'picture','format':'jpeg','aspect_ratio':'1:1','max_dimension':128},'assets':[asset]})
        with Image.open(resolve_media(self.data,result['asset']['uri'])) as image:
            self.assertEqual((128,128), image.size)
        self.assertTrue(resolve_media(self.data, renamed['uri']).exists())

    def test_interrupted_job_survives_restart(self):
        from native_operations import atomic_json
        record=self.native.directory/'11111111111111111111111111111111.json'
        atomic_json(record,{'state':'RUNNING','results':[]})
        restarted=NativeOperations(self.data,Host(),lambda:{},lambda state:None,None,lambda:{})
        self.assertEqual('INTERRUPTED', json.loads(record.read_text())['state'])
        restarted.executor.shutdown()


class MCPTests(unittest.TestCase):
    def test_session_pagination_sse_and_is_error(self):
        calls=[]
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def do_POST(self):
                body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                calls.append((body,self.headers.get('Mcp-Session-Id')))
                method=body['method']
                if method=='notifications/initialized':
                    self.send_response(202);self.end_headers();return
                if method=='initialize':result={'protocolVersion':'2025-03-26'}
                elif method=='tools/list':
                    result={'tools':[], 'nextCursor':'page2'} if not body['params'].get('cursor') else {'tools':[{'name':'balance','annotations':{'readOnlyHint':True},'inputSchema':{'required':['account']}}]}
                else: result={'isError':True,'content':[{'type':'text','text':'account unavailable'}]}
                raw=('data: '+json.dumps({'jsonrpc':'2.0','id':body['id'],'result':result})+'\n\n').encode()
                self.send_response(200);self.send_header('Mcp-Session-Id','real-session');self.send_header('Content-Type','text/event-stream');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            client=MCP(f'http://127.0.0.1:{server.server_port}/mcp')
            with self.assertRaisesRegex(ValueError,'account unavailable'):client.call('balance',{'account':'test'})
            self.assertTrue(all(session=='real-session' for body,session in calls if body['method']!='initialize'))
            self.assertEqual(1,len([body for body,_ in calls if body['method']=='tools/call']))
        finally:server.shutdown();server.server_close()


if __name__=='__main__':unittest.main()
