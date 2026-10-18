from pathlib import Path
import json
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from native_operations import NativeOperations

class LibraryAcceptance(unittest.TestCase):
    def test_real_documents_audio_and_images(self):
        with tempfile.TemporaryDirectory(prefix='nexus-library-qa-') as tmp:
            data=Path(tmp);state={'jobs':[{'id':'failed','status':'FAILED'},{'id':'ok','status':'COMPLETED'}]}
            def save(value):state.update(value)
            native=NativeOperations(data,None,lambda:state,save,None,lambda:{})
            try:
                from PIL import Image
                image=native.new_path('.png');Image.new('RGB',(640,480),'blue').save(image)
                assets=[{'id':'image','uri':'/media/'+image.name,'type':'IMAGE','displayName':'Imagen QA','mimeType':'image/png','createdAt':1}]
                def run(op,args={}):return native.run({'operation':op,'input':args,'assets':assets,'projectId':'qa'})
                listing=run('list_library_assets');self.assertEqual(listing['total'],1)
                measured=run('inspect_image_quality',{'asset_id':'image'});self.assertEqual(measured['width'],640);self.assertTrue(measured['_attachments'])
                doc=run('create_document',{'format':'pdf','title':'Documento QA','blocks_json':json.dumps([{'text':'Documento Windows verificable'},{'image_asset_id':'image','caption':'Imagen incluida'}])})['asset'];assets.append(doc)
                text=run('read_library_text',{'asset_id':doc['id']});self.assertIn('Windows verificable',text['text'])
                audio=native.new_path('.wav');native.encode(['-f','lavfi','-i','sine=frequency=440:duration=1','-ar','16000',audio]);assets.append({'id':'audio','uri':'/media/'+audio.name,'type':'AUDIO','displayName':'Audio QA','mimeType':'audio/wav'})
                queued=run('analyze_audio_asset',{'asset_id':'audio'});job=native.get_job({'edit_job_id':queued['edit_job_id'],'wait_seconds':20});self.assertEqual(job['state'],'COMPLETED')
                self.assertEqual(run('archive_failed_generations')['archived'],1);self.assertEqual(len(state['jobs']),2)
                result=run('delete_library_assets',{'asset_ids':['image']});self.assertTrue(result['_assets'][0]['libraryDeleted']);self.assertTrue(image.exists())
            finally:native.executor.shutdown()

if __name__=='__main__':unittest.main(verbosity=2)
