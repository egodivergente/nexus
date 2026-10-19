"""Offline document transport acceptance; --live opts into real BytePlus inference."""
import argparse
import copy
import hashlib
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
import uuid


def fixture(data):
    from PIL import Image,ImageDraw,ImageFont
    from reportlab.pdfgen.canvas import Canvas
    media=data/'media';media.mkdir(parents=True,exist_ok=True)
    code=''.join(str(2+int(c,16)%7) for c in uuid.uuid4().hex[:6]);reference='DOC-'+uuid.uuid4().hex[:10].upper()
    image=media/(uuid.uuid4().hex+'.png');im=Image.new('RGB',(960,720),'white');draw=ImageDraw.Draw(im)
    font=ImageFont.truetype('C:/Windows/Fonts/consolab.ttf',52)
    draw.text((40,25),'CODIGO: '+code,font=font,fill='black')
    for x in [100,300,500]:draw.ellipse((x,150,x+100,250),fill='#e00000')
    for x in [150,400]:draw.rectangle((x,340,x+100,440),fill='#004bdd')
    draw.polygon([(650,600),(750,440),(850,600)],fill='#008900');im.save(image);im.close()
    pdf=media/(uuid.uuid4().hex+'.pdf');canvas=Canvas(str(pdf));canvas.setFont('Helvetica',15)
    for y,text in enumerate([reference,'Lote A: 7 unidades a 13 euros.','Lote B: 3 unidades a 17 euros.','Descuento fijo: 11 euros.']):canvas.drawString(50,780-y*32,text)
    canvas.save()
    scan=media/(uuid.uuid4().hex+'.pdf');canvas=Canvas(str(scan),pagesize=(960,720));canvas.drawImage(str(image),0,0,width=960,height=720);canvas.save()
    return {'image':image,'pdf':pdf,'scan':scan,'code':code,'reference':reference,'total':131}


def parse_answer(text):
    matches=re.findall(r'\{[^{}]*\}',text)
    if not matches:raise AssertionError('No llegó una respuesta JSON verificable: '+text[:300])
    return json.loads(matches[-1])


def session(desktop,production=None):
    sys.path.insert(0,str(desktop))
    from agent_host import AgentHost
    from local_host import windows_keys,write_secret
    from native_operations import NativeOperations
    live=production is not None;profile=None;protected={};key='synthetic-documents-key';requests=[];current={};events=[];attachments=[]
    if live:
        for name in ['settings.json','credentials.dpapi']:
            path=production/'assistant'/name;protected[path]=hashlib.sha256(path.read_bytes()).hexdigest()
        settings=json.loads((production/'assistant/settings.json').read_text('utf-8'));profile=copy.deepcopy(settings['brainProfiles']['byteplus'])
        key=windows_keys(production/'assistant/credentials.dpapi').get('brain:byteplus')
        if not key:raise ValueError('Falta la clave BytePlus en DPAPI.')
        upstream=profile['baseUrl'].rstrip('/')+'/'+(profile.get('endpointPath') or 'responses').lstrip('/')
        if not upstream.startswith('https://ark.ap-southeast.bytepluses.com/'):raise ValueError('Destino BytePlus inesperado.')
    class Relay(BaseHTTPRequestHandler):
        def log_message(self,*_):pass
        def do_POST(self):
            raw=self.rfile.read(int(self.headers['Content-Length']));body=json.loads(raw)
            parts=[p for m in body.get('input',[]) if isinstance(m,dict) and isinstance(m.get('content'),list) for p in m['content']]
            entry={'case':current['name'],'images':sum(p.get('type')=='input_image' for p in parts),
                   'thinking':body.get('thinking'),'reasoning':body.get('reasoning'),'tools':len(body.get('tools',[]))}
            requests.append(entry)
            if live:
                request=urllib.request.Request(upstream,data=raw,headers={'Content-Type':'application/json','Authorization':self.headers['Authorization']},method='POST')
                try:
                    with urllib.request.urlopen(request,timeout=100) as response:status=response.status;output=response.read()
                except urllib.error.HTTPError as error:status=error.code;output=error.read()
                parsed=json.loads(output);entry.update(status=status,usage=parsed.get('usage'),effectiveReasoning=parsed.get('reasoning'),effectiveThinking=parsed.get('thinking'))
            else:
                status=200;output=json.dumps({'status':'completed','output':[{'type':'message','role':'assistant','content':[{'type':'output_text','text':json.dumps(current['expected'])}]}],'usage':{'input_tokens':1,'output_tokens':1}}).encode();entry['status']=status
            self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(output)));self.send_header('Connection','close');self.end_headers();self.wfile.write(output)
    server=ThreadingHTTPServer(('127.0.0.1',0),Relay);threading.Thread(target=server.serve_forever,daemon=True).start();stop=threading.Event()
    try:
        with tempfile.TemporaryDirectory(prefix='nexus-documents-') as temporary:
            data=Path(temporary);files=fixture(data);host=AgentHost(desktop,data,windows_keys,write_secret)
            native=NativeOperations(data,host,lambda:{},lambda _:None,None,lambda:{})
            class AuditedNative:
                def __getattr__(self,name):return getattr(native,name)
                def run(self,payload):raise ValueError('Esta prueba solo lee adjuntos; no ejecuta herramientas.')
                def attachment(self,op,args):
                    value=native.attachment(op,args)
                    attachments.append({'operation':op,'format':value.get('format'),'pages':len(value.get('pages',[])),
                                        'complete':value.get('complete'),'coverage':value.get('coverage')})
                    return value
            host.native=AuditedNative()
            def pump():
                while not stop.wait(.02):
                    for event in host.events('documents-qa'):
                        events.append(event['op']);host.reply({'owner':'documents-qa','id':event['id'],'ok':event['op'] in {'progress','stream','uiEvent'},'value':None,'error':'Sin generaciones ni otras operaciones.'})
            thread=threading.Thread(target=pump,daemon=True)
            try:
                profile=profile or {'id':'local-documents','name':'Local','protocol':'OPENAI_RESPONSES','modelId':'mock'}
                profile.update(storeOnServer=False,baseUrl=f'http://127.0.0.1:{server.server_port}',endpointPath='',thinking='enabled',reasoningEffort='low',
                               capabilities={**profile.get('capabilities',{}),'vision':True,'streaming':False,'tools':False},
                               limits={'maxCallsPerTurn':3,'maxToolRounds':2,'maxTokens':2048,'turnTimeoutSeconds':150,'httpTimeoutSeconds':110})
                host.command({'op':'settings','owner':'documents-qa','args':{'backend':'profile','mode':'ASSISTANT','brainProfile':profile,'brainKey':key}})
                thread.start();results=[]
                for name,mime,prompt,expected in [
                    ('image','image/png','Lee el código superior y cuenta las figuras por forma y color. Responde solo JSON con code, red_circles, blue_squares, green_triangles.',{'code':files['code'],'red_circles':3,'blue_squares':2,'green_triangles':1}),
                    ('pdf','application/pdf','Lee el documento adjunto y calcula el importe conjunto de los lotes menos el descuento. Responde solo JSON con reference y total.',{'reference':files['reference'],'total':files['total']}),
                    ('scan','application/pdf','Examina las figuras del PDF adjunto. Responde solo JSON con code, red_circles, blue_squares, green_triangles.',{'code':files['code'],'red_circles':3,'blue_squares':2,'green_triangles':1})]:
                    current.update(name=name,expected=expected);host.command({'op':'new','owner':'documents-qa'})
                    path=files[name];original=hashlib.sha256(path.read_bytes()).hexdigest()
                    result=host.command({'op':'send','owner':'documents-qa','args':{'text':prompt,'attachments':[{'id':uuid.uuid4().hex,'uri':'/media/'+path.name,'mimeType':mime,'name':'Documento de prueba' if mime=='application/pdf' else 'Imagen de prueba'}]}})['result']
                    assert parse_answer(result['text'])==expected,(name,result['text'],expected)
                    assert original==hashlib.sha256(path.read_bytes()).hexdigest()
                    results.append({'case':name,'status':'PASS','answer':parse_answer(result['text']),'inputTokens':result.get('inputTokens'),'outputTokens':result.get('outputTokens')})
                assert any(r['case']=='image' and r['images'] for r in requests),requests
                assert any(r['case']=='scan' and r['images'] for r in requests),requests
                assert any(a['operation']=='document' and a['format']=='txt' for a in attachments),attachments
                assert any(a['operation']=='document' and a['pages']==1 and a['complete'] for a in attachments),attachments
                assert all(r['thinking']=={'type':'enabled'} and r['reasoning']=={'effort':'low'} and r['tools']==0 and r['status']==200 for r in requests),requests
                reasoning_tokens=sum(((r.get('usage') or {}).get('output_tokens_details') or {}).get('reasoning_tokens',0) for r in requests)
                return {'status':'PASS','reasoningTokens':reasoning_tokens,'reasoningObserved':reasoning_tokens>0,'live':live,'desktop':str(desktop),'model':profile['modelId'],'cases':results,'transport':requests,'attachments':attachments,
                        'originalsPreserved':True,'mediaGenerationSubmitted':False,'productionPreferencesUnchanged':True}
            finally:
                stop.set()
                if thread.ident:thread.join(3)
                host.close();native.executor.shutdown()
    finally:
        server.shutdown();server.server_close()
        for path,expected in protected.items():assert hashlib.sha256(path.read_bytes()).hexdigest()==expected


class DocumentsAcceptance(unittest.TestCase):
    def test_visual_document_host_transport(self):session(Path(__file__).resolve().parents[1])
    def test_text_with_vector_graphics_uses_visual_route(self):
        sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
        from native_operations import NativeOperations
        from reportlab.pdfgen.canvas import Canvas
        with tempfile.TemporaryDirectory(prefix='nexus-pdf-vector-') as temporary:
            data=Path(temporary);(data/'media').mkdir();pdf=data/'media'/(uuid.uuid4().hex+'.pdf')
            canvas=Canvas(str(pdf));canvas.drawString(40,700,'Texto con gráfico vectorial');canvas.setFillColorRGB(1,0,0);canvas.rect(40,500,100,100,fill=1);canvas.save()
            native=NativeOperations(data,None,lambda:{},lambda _:None,None,lambda:{})
            try:
                result=native.attachment('document',{'uri':'/media/'+pdf.name,'name':'Vector','render_pdf':True})
                self.assertEqual(result['format'],'pdf');self.assertEqual(len(result['pages']),1);self.assertTrue(result['complete'])
            finally:native.executor.shutdown()

    def test_scanned_pdf_without_vision_fails_before_inference(self):
        sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
        from agent_host import AgentHost
        from local_host import windows_keys,write_secret
        from native_operations import NativeOperations
        with tempfile.TemporaryDirectory(prefix='nexus-pdf-no-vision-') as temporary:
            data=Path(temporary);files=fixture(data);host=AgentHost(Path(__file__).resolve().parents[1],data,windows_keys,write_secret)
            native=NativeOperations(data,host,lambda:{},lambda _:None,None,lambda:{ });host.native=native
            stop=threading.Event()
            def pump():
                while not stop.wait(.02):
                    for event in host.events('qa'):host.reply({'owner':'qa','id':event['id'],'ok':event['op']=='progress','value':None})
            thread=threading.Thread(target=pump,daemon=True);thread.start()
            try:
                host.command({'op':'settings','owner':'qa','args':{'backend':'profile','brainKey':'synthetic-key','brainProfile':{'id':'no-vision','name':'No vision','protocol':'OPENAI_RESPONSES','baseUrl':'http://127.0.0.1:1','modelId':'mock','capabilities':{'vision':False,'streaming':False,'tools':False}}}})
                with self.assertRaisesRegex(RuntimeError,'requiere un cerebro Responses con visión'):
                    host.command({'op':'send','owner':'qa','args':{'text':'Lee el PDF','attachments':[{'id':'scan','uri':'/media/'+files['scan'].name,'mimeType':'application/pdf','name':'Escaneo'}]}})
            finally:stop.set();thread.join(3);host.close();native.executor.shutdown()

    def test_partial_pdf_coverage_and_original_preservation(self):
        sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
        from native_operations import NativeOperations
        from reportlab.pdfgen.canvas import Canvas
        with tempfile.TemporaryDirectory(prefix='nexus-pdf-pages-') as temporary:
            data=Path(temporary);files=fixture(data);pdf=data/'media'/(uuid.uuid4().hex+'.pdf');canvas=Canvas(str(pdf))
            for _ in range(9):canvas.drawImage(str(files['image']),20,30,width=500,height=375);canvas.showPage()
            canvas.save();before=hashlib.sha256(pdf.read_bytes()).hexdigest();native=NativeOperations(data,None,lambda:{},lambda _:None,None,lambda:{})
            try:
                result=native.attachment('document',{'uri':'/media/'+pdf.name,'name':'Largo','render_pdf':True})
                self.assertEqual(result['total_pages'],9);self.assertEqual(len(result['pages']),8);self.assertFalse(result['complete']);self.assertIn('LECTURA PARCIAL',result['coverage'])
                self.assertEqual(before,hashlib.sha256(pdf.read_bytes()).hexdigest())
            finally:native.executor.shutdown()


if __name__=='__main__':
    if '--live' in sys.argv:
        parser=argparse.ArgumentParser();parser.add_argument('--live',action='store_true');parser.add_argument('--desktop-root',type=Path,default=Path(__file__).resolve().parents[1]);parser.add_argument('--report',type=Path,required=True);args=parser.parse_args()
        report=session(args.desktop_root.resolve(),Path(os.environ['LOCALAPPDATA'])/'NexusDesktop');args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False,indent=2))
    else:unittest.main(verbosity=2)
