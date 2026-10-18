"""Current Android library operations adapted to Windows files and the shared index."""
import json
import math
from pathlib import Path
import subprocess
import time
import uuid
from xml.sax.saxutils import escape

from native_operations import FLAGS

OPERATIONS={'list_library_assets','view_library_asset','inspect_image_quality','create_document',
            'read_library_text','analyze_audio_asset','archive_failed_generations','delete_library_assets'}


def run(native,operation,args,assets,payload):
    if operation=='list_library_assets':
        query=str(args.get('query','')).casefold();offset=max(0,int(args.get('offset',0)));limit=max(1,min(100,int(args.get('limit',50))))
        selected=sorted([a for a in assets if not a.get('libraryDeleted') and query in a.get('displayName','').casefold()],key=lambda a:a.get('createdAt',0),reverse=True)
        return {'assets':selected[offset:offset+limit],'total':len(selected),'next_offset':offset+limit if offset+limit<len(selected) else None}
    if operation=='archive_failed_generations':
        state=native.read_state() or {};count=0
        for job in state.get('jobs',[]):
            if job.get('status') in {'FAILED','CANCELLED'} and not job.get('archived'):
                job['archived']=True;count+=1
        native.write_state(state);return {'archived':count,'history_preserved':True}
    if operation=='delete_library_assets':
        ids=set(args.get('asset_ids',[]));found=[a for a in assets if a['id'] in ids]
        if not ids or len(found)!=len(ids):raise ValueError('Selecciona identificadores existentes de Biblioteca.')
        return {'deleted':list(ids),'originals_preserved':True,'_assets':[{**a,'libraryDeleted':True} for a in found]}
    if operation=='create_document':
        fmt=args['format']
        if fmt not in {'pdf','txt','md','html','csv','json','py','js','sh','ps1','srt'}:raise ValueError('Formato no compatible.')
        target=native.new_path('.'+fmt);content=str(args.get('content',''))
        if fmt=='pdf':
            from reportlab.platypus import SimpleDocTemplate,Paragraph,Spacer,Image as PdfImage
            from reportlab.lib.styles import getSampleStyleSheet
            styles=getSampleStyleSheet();story=[]
            blocks=json.loads(args['blocks_json']) if args.get('blocks_json') else [{'text':content}]
            for block in blocks:
                if block.get('text'):
                    for paragraph in block['text'].split('\n'):
                        story.append(Paragraph(escape(paragraph) or ' ',styles['BodyText']));story.append(Spacer(1,6))
                if block.get('image_asset_id'):
                    _,path=native.asset(block['image_asset_id'],assets)
                    picture=PdfImage(str(path));ratio=min(460/picture.imageWidth,600/picture.imageHeight,1)
                    picture.drawWidth=picture.imageWidth*ratio;picture.drawHeight=picture.imageHeight*ratio;story.append(picture)
                    if block.get('caption'):story.append(Paragraph(escape(block['caption']),styles['Caption'] if 'Caption' in styles else styles['BodyText']))
            if not story:raise ValueError('El documento está vacío.')
            SimpleDocTemplate(str(target)).build(story)
        else:
            if fmt=='json':json.loads(content)
            target.write_text(content,encoding='utf-8')
        result={'id':'asset_'+uuid.uuid4().hex,'uri':'/media/'+target.name,'type':'OTHER','role':'OUTPUT',
                'source':'IMPORTED','displayName':str(args['title'])[:160]+'.'+fmt,
                'mimeType':'application/pdf' if fmt=='pdf' else 'text/plain','createdAt':int(time.time()*1000),
                'projectId':payload.get('projectId'),'sizeBytes':target.stat().st_size}
        return {'asset':result,'_assets':[result]}
    asset,path=native.asset(args['asset_id'],assets)
    if operation=='read_library_text':
        if path.suffix.lower()=='.pdf':
            from pypdf import PdfReader
            text='\n'.join(page.extract_text() or '' for page in PdfReader(path).pages)
        else:
            if path.stat().st_size>8*1024*1024:raise ValueError('Documento demasiado grande para una lectura completa.')
            text=path.read_text(encoding='utf-8-sig')
        return {'asset_id':asset['id'],'text':text[:200000],'truncated':len(text)>200000}
    if operation in {'view_library_asset','inspect_image_quality'}:
        result={'asset_id':asset['id'],'requested_context_load':True,
                '_attachments':[{'id':asset['id'],'uri':asset['uri'],'name':asset['displayName'],'mimeType':asset.get('mimeType','application/octet-stream'),'assetId':asset['id']}]}
        if operation=='inspect_image_quality':
            import numpy as np
            from PIL import Image,ImageOps,ImageFilter
            with Image.open(path) as source:
                im=ImageOps.exif_transpose(source).convert('L');width,height=im.size;im.thumbnail((512,512));a=np.asarray(im,dtype=np.float64)
            dx=np.diff(a,axis=1);dy=np.diff(a,axis=0)
            result.update(width=width,height=height,luminance=float(a.mean()/255),edge_energy=float((np.mean(dx*dx)+np.mean(dy*dy))/2),sample_width=a.shape[1],sample_height=a.shape[0],limitations='Métricas de muestra; la evaluación perceptiva requiere examinar la imagen.')
        return result
    if operation=='analyze_audio_asset':
        # Reuse the persistent serial local-media queue and its recovery contract.
        return native.queue(operation,args,assets)
    raise ValueError('Operación de biblioteca no implementada.')


def analyze_audio(native,args,assets):
    import numpy as np
    parent,path=native.asset(args['asset_id'],assets);info=native.inspect(path)
    start=int(args.get('start_ms',0));end=int(args.get('end_ms',info['duration_ms']))
    if start<0 or end<=start or end>info['duration_ms']+100:raise ValueError('Intervalo de audio inválido.')
    wav=native.new_path('.wav')
    native.encode(['-ss',str(start/1000),'-i',path,'-t',str((end-start)/1000),'-vn','-ac','1','-ar','16000','-c:a','pcm_s16le',wav])
    import wave
    with wave.open(str(wav),'rb') as stream:
        total=0;squares=0.0;peak=0.0;clipped=0;silent=0;energy=np.zeros(4)
        while block:=stream.readframes(16384):
            samples=np.frombuffer(block,dtype='<i2').astype(np.float64)/32768
            total+=len(samples);squares+=float(samples@samples);peak=max(peak,float(np.abs(samples).max(initial=0)));clipped+=int((np.abs(samples)>=.999).sum());silent+=int((np.abs(samples)<.01).sum())
            spectrum=np.abs(np.fft.rfft(samples))**2;frequencies=np.fft.rfftfreq(len(samples),1/16000)
            for i,(lo,hi) in enumerate([(0,250),(250,1000),(1000,4000),(4000,8001)]):energy[i]+=spectrum[(frequencies>=lo)&(frequencies<hi)].sum()
    report={'asset_id':parent['id'],'sample_rate':16000,'samples':total,'rms_dbfs':20*math.log10(max(1e-12,math.sqrt(squares/max(1,total)))),
            'peak_dbfs':20*math.log10(max(1e-12,peak)),'clipped_samples':clipped,'silence_fraction':silent/max(1,total),
            'band_energy_fraction':(energy/max(float(energy.sum()),1e-20)).tolist(),'bands_hz':[[0,250],[250,1000],[1000,4000],[4000,8000]],
            'limitations':'Medición local; no identifica instrumentos ni certifica calidad perceptiva.'}
    target=native.new_path('.txt');target.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return [native.output(target,parent['displayName']+' · análisis de audio',parent,'OTHER')]
