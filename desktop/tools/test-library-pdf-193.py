"""Offline regression: real PDF extraction and gallery navigation, isolated from user data."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,tempfile,time
from playwright.sync_api import sync_playwright
from reportlab.pdfgen.canvas import Canvas
from pypdf import PdfWriter
root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'desktop'))
from native_operations import NativeOperations
with tempfile.TemporaryDirectory(prefix='nexus193-') as tmp:
 data=Path(tmp);(data/'assistant').mkdir();(data/'media').mkdir()
 native=NativeOperations(data,None,lambda:{},lambda _:None,None,lambda:{})
 pdf=native.new_path('.pdf');canvas=Canvas(str(pdf))
 for i in range(21):canvas.drawString(50,700,f'Mateo dossier page {i+1}');canvas.showPage()
 canvas.save();before=hashlib.sha256(pdf.read_bytes()).hexdigest()
 result=native.attachment('document',{'uri':'/media/'+pdf.name,'name':'Dossier'})
 import base64
 assert result['format']=='txt' and 'Mateo dossier page 21' in base64.b64decode(result['bytes']).decode()
 assert hashlib.sha256(pdf.read_bytes()).hexdigest()==before
 blank=native.new_path('.pdf');writer=PdfWriter();writer.add_blank_page(200,200)
 with blank.open('wb') as f:writer.write(f)
 assert native.attachment('document',{'uri':'/media/'+blank.name,'name':'Scanned'})['format']=='pdf'
 native.executor.shutdown()
 (data/'state.json').write_text(json.dumps({'jobs':[],'draft':{'prompt':'','values':{},'media':[]}}))
 process=subprocess.Popen([sys.executable,str(root/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 try:
  url=json.loads(process.stdout.readline())['url']
  with sync_playwright() as p:
   browser=p.chromium.launch(channel='msedge',headless=True);page=browser.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
   page.route('https://example.com/**', lambda route: route.fulfill(status=200, content_type='image/png', body=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=')))
   page.goto(url);page.wait_for_function('()=>Boolean(window.NexusGallery)&&Boolean(initial)',timeout=45000)
   page.evaluate("""()=>{const now=Date.now();state.jobs=[{id:'pending',status:'PROCESSING',providerId:'atlas',modelId:'qa',name:'En curso',modality:'IMAGE',prompt:'Test',startedAt:now-10000,progress:.25,outputs:[]},...[1,2,3].map(i=>({id:'done'+i,status:'COMPLETED',providerId:'atlas',modelId:'qa',name:'Foto '+i,modality:'IMAGE',prompt:'Test',startedAt:now-i*500000,completedAt:now-i*500000+120000,outputs:i===1?['https://example.com/a.png','https://example.com/b.png']:['https://example.com/c.png']}))];PC.search='';PC.status='';PC.kind='';PC.provider='';Studio.tab='results';navigate('library');}""")
   assert page.locator('[data-job="pending"] progress').get_attribute('value')=='0.25'
   assert 'Restante' in page.locator('[data-job="pending"]').inner_text()
   page.locator('[data-job="done1"] .gallery-media').click()
   page.locator('[data-gallery="next"]').click()
   assert page.locator('.gallery-stage img').get_attribute('src').endswith('/b.png')
   page.keyboard.press('ArrowRight')
   assert page.locator('.gallery-stage img').get_attribute('src').endswith('/c.png')
   page.locator('[data-gallery="prev"]').click()
   assert page.locator('.gallery-stage img').get_attribute('src').endswith('/b.png')
   page.locator('[data-gallery="close-viewer"]').click()
   page.evaluate("()=>{state.jobs[0].status='COMPLETED';state.jobs[0].outputs=['https://example.com/result.png'];refreshJobViews(['pending']);}")
   assert page.locator('[data-job="pending"] progress').count()==0
   page.wait_for_selector('[data-job="pending"] img')
   assert page.locator('[data-job="pending"] img').count()==1, page.locator('[data-job="pending"]').inner_html()
   assert not errors,errors
   browser.close()
  print('PASS: 21-page PDF extracted, original preserved, scanned fallback, pending progress/ETA, multi-output navigation, completion replacement')
 finally:process.terminate();process.wait(timeout=10)
