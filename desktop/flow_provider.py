"""Own-account Google Flow adapter for the existing authenticated local host."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import threading
import time
import uuid

RUNTIME = Path.home() / '.codex/integrations/google-flow'
PYTHON = RUNTIME / 'venv/Scripts/python.exe'
LOCK = threading.Lock()
WORKERS = set()
IDENTIFIER = re.compile(r'[a-zA-Z0-9_-]{1,80}\Z')
SOURCE = 'https://support.google.com/flow/answer/16526234?hl=en'

def available():
    return PYTHON.is_file() and (RUNTIME / 'client.py').is_file() and (RUNTIME / 'state/profile_nexus/.gflow_browser_strategy').is_file()

def catalog():
    if not available():
        return []
    common = {'providerId':'google_flow', 'organization':'Google', 'supportsImageInput':False,
        'inputMode':'TEXT_ONLY', 'capabilities':[], 'basePriceUsd':None, 'priceUnitVerified':False,
        'contractEndpoint':'flow', 'contractMedia':[], 'promptRequired':True, 'isFlagship':False}
    aspect = lambda options: {'key':'aspect', 'displayName':'Formato', 'type':'ENUM', 'nativeType':'string',
        'options':options, 'defaultValue':options[0], 'isRequired':True}
    result = [dict(common, id='google-flow/nano2', providerModelId='nano2', displayName='Nano Banana 2 · tu cuenta Flow',
        modelFamily='Nano Banana', modalities=['IMAGE'], parameters=[aspect(['1:1','16:9','9:16','4:3','3:4'])],
        shortDescription='Crea una imagen con Nano Banana 2 usando tu cuenta de Google Flow.'),
        dict(common, id='google-flow/veo-fast', providerModelId='veo_fast', displayName='Veo 3.1 Fast · tu cuenta Flow',
        modelFamily='Veo', modalities=['VIDEO'], parameters=[aspect(['16:9','9:16'])],
        shortDescription='Genera un vídeo con Veo Fast usando tus créditos de Google Flow. Duración automática.')]
    for identifier,name,modality,options in [
        ('image_upscale','Escalar imagen Flow','IMAGE',['2k','4k']),
        ('video_upscale','Escalar vídeo Flow','VIDEO',['1080p','4k']),
        ('video_gif','Exportar GIF de Flow','IMAGE',['gif'])]:
        result.append(dict(common,id='google-flow/'+identifier,providerModelId=identifier,displayName=name,
            modelFamily='Flow',modalities=[modality],promptRequired=False,capabilities=['UPSCALE'] if 'upscale' in identifier else ['EXPORT'],
            parameters=[{'key':'source_request_id','displayName':'Trabajo original de Flow','type':'TEXT','nativeType':'string','isRequired':True},
                {'key':'resolution','displayName':'Resolución','type':'ENUM','nativeType':'string','options':options,'defaultValue':options[0],'isRequired':True}],
            shortDescription='Mejora/exporta un resultado existente de tu cuenta Flow. Usa el jobId de su historial; conserva el original.'))
    return result

def parameters(args):
    model = args.get('model')
    if model in {'image_upscale','video_upscale','video_gif'}:
        source=args.get('source_request_id','')
        options={'image_upscale':{'2k','4k'},'video_upscale':{'1080p','4k'},'video_gif':{'gif'}}
        if not isinstance(source,str) or not IDENTIFIER.fullmatch(source) or args.get('resolution') not in options[model]:
            raise ValueError('Elige un trabajo original de Flow y una resolución compatible.')
        return {'model':model,'source_request_id':source,'resolution':args['resolution']}
    aspect = args.get('aspect', '16:9' if model == 'veo_fast' else '1:1')
    allowed = {'nano2':{'1:1','16:9','9:16','4:3','3:4'}, 'veo_fast':{'16:9','9:16'}}
    if model not in allowed or aspect not in allowed[model]:
        raise ValueError('Modelo o formato Flow no admitido. Nano Banana Lite está excluido.')
    return {'model':model, 'aspect':aspect}

def quote(args):
    params = parameters(args)
    if not available():
        raise ValueError('El puente local de Flow no está instalado.')
    if 'source_request_id' in params:
        result=call('flow_get_asset_options',{'source_request_id':params['source_request_id']},'options-'+uuid.uuid4().hex)
        required='IMAGE' if params['model']=='image_upscale' else 'VIDEO'
        option=next((o for o in result.get('options',[]) if o['resolution']==params['resolution']),None)
        if result.get('status')!='quoted' or result.get('kind')!=required or not option:
            raise ValueError('Flow no confirma esta mejora para el archivo original.')
        return {'amount':option['credits'],'currency':'flow_credits','time':int(time.time()*1000),
            'detail':option['label']+' · Importe indicado por el menú de tu cuenta. Saldo no verificado.',
            'source':'Flow download menu'}
    # Never invent a free tier or turn an unknown image price into zero USD.
    prices = RUNTIME / 'jobs/verified-prices.json'
    if prices.is_file():
        value = json.loads(prices.read_text(encoding='utf-8')).get(params['model'])
        if value and value.get('aspect')==params['aspect'] and time.time() - value['checked_at'] < 300:
            return {'amount':value['credits'], 'currency':'flow_credits', 'time':int(time.time()*1000),
                'detail':'Créditos mostrados por tu cuenta Flow para una generación.', 'source':'Flow settings'}
    verified=call('flow_get_quote',params,'quote-'+uuid.uuid4().hex)
    if verified.get('status') == 'quoted':
        return {'amount':verified['credits'],'currency':'flow_credits','time':int(time.time()*1000),
            'detail':'Créditos mostrados por tu cuenta Flow para una generación. Saldo no verificado.', 'source':'Flow settings'}
    if params['model'] == 'nano2':
        raise ValueError('Flow no ha publicado un coste verificable para Nano Banana 2 en esta cuenta. Envío bloqueado; Lite no se usará.')
    return {'amount':20, 'currency':'flow_credits', 'time':int(time.time()*1000), 'upperBound':True,
        'detail':'Estimación conservadora: hasta 20 créditos por vídeo; Google publica 10 para Ultra. Saldo no verificado.',
        'source':SOURCE}

def asset_options(source_request_id):
    if not isinstance(source_request_id,str) or not IDENTIFIER.fullmatch(source_request_id):
        raise ValueError('Identificador original de Flow no válido.')
    result=call('flow_get_asset_options',{'source_request_id':source_request_id},'options-'+uuid.uuid4().hex)
    if result.get('status')!='quoted':raise ValueError('No se pudieron consultar las mejoras de este resultado Flow.')
    return result

def ledger(data):
    path = Path(data) / 'flow-jobs.sqlite'
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path, timeout=10)
    connection.row_factory = sqlite3.Row
    connection.execute('CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, params TEXT NOT NULL, result TEXT)')
    connection.commit()
    return connection

def call(tool, args, request_id):
    folder = RUNTIME / 'jobs'
    folder.mkdir(parents=True, exist_ok=True)
    arguments = folder / f'nexus-{request_id}-args.json'
    output = folder / f'nexus-{request_id}-result.json'
    arguments.write_text(json.dumps(args, ensure_ascii=False), encoding='utf-8')
    creation = (subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS) if os.name == 'nt' else 0
    with (folder / 'nexus-client-errors.log').open('ab') as errors:
        run = subprocess.run([str(PYTHON), str(RUNTIME / 'client.py'), '--output', str(output),
            'call', tool, '--args-file', str(arguments)], cwd=RUNTIME, stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL, stderr=errors, timeout=920, creationflags=creation)
    if run.returncode or not output.exists():
        return {'status':'needs_reconciliation', 'detail':'Resultado de envío incierto. No se reenvía automáticamente.'}
    envelope = json.loads(output.read_text(encoding='utf-8'))
    result = envelope.get('structured_content') or envelope.get('structuredContent')
    if not result:
        for block in envelope.get('content', []):
            if block.get('type') == 'text':
                try:
                    result = json.loads(block['text'])
                    break
                except ValueError:
                    pass
    return result or {'status':'needs_reconciliation'}

def work(data, request_id, params):
    try:
        if 'source_request_id' in params:
            tool='flow_process_asset'
            arguments={k:v for k,v in params.items() if k!='model'}
        else:
            tool = 'flow_generate_image' if params['model'] == 'nano2' else 'flow_generate_video'
            arguments=params
        result = call(tool, {'request_id':request_id, **arguments}, request_id)
    except Exception as exc:
        result = {'status':'needs_reconciliation', 'error_class':type(exc).__name__}
    try:
        with LOCK:
            connection = ledger(data)
            try:
                with connection:
                    connection.execute('UPDATE jobs SET result=? WHERE id=?', (json.dumps(result), request_id))
            finally:
                connection.close()
    finally:
        with LOCK:
            WORKERS.discard(request_id)

def submit(data, args):
    params = parameters(args)
    request_id, prompt = args.get('request_id', ''), args.get('prompt', '')
    if not IDENTIFIER.fullmatch(request_id) or not isinstance(prompt, str) or ('source_request_id' not in params and not prompt.strip()):
        raise ValueError('Identificador o prompt Flow no válido.')
    if 'source_request_id' not in params:params['prompt'] = prompt.strip()
    # Return an existing reservation before another browser quote/profile lease.
    with LOCK:
        connection=ledger(data)
        try:prior=connection.execute('SELECT params FROM jobs WHERE id=?',(request_id,)).fetchone()
        finally:connection.close()
    if prior:
        comparable=json.loads(prior['params']);comparable.pop('expected_credits',None)
        if comparable!=params:raise ValueError('El identificador pertenece a otro trabajo. No se envía de nuevo.')
        return status(data,request_id)
    estimate = quote(params)
    if args.get('cost') != estimate['amount']:
        raise ValueError('Actualiza la estimación Flow antes del envío.')
    if 'source_request_id' in params:params['expected_credits']=estimate['amount']
    with LOCK:
        connection = ledger(data)
        try:
            row = connection.execute('SELECT * FROM jobs WHERE id=?', (request_id,)).fetchone()
            if row:
                if json.loads(row['params']) != params:
                    raise ValueError('El identificador pertenece a otro trabajo. No se envía de nuevo.')
            else:
                with connection:
                    connection.execute('INSERT INTO jobs VALUES (?,?,NULL)', (request_id, json.dumps(params)))
                WORKERS.add(request_id)
                threading.Thread(target=work, args=(data, request_id, params), daemon=True).start()
        finally:
            connection.close()
    return status(data, request_id)

def status(data, request_id):
    if not IDENTIFIER.fullmatch(request_id):
        raise ValueError('Identificador Flow no válido.')
    with LOCK:
        connection = ledger(data)
        try:
            row = connection.execute('SELECT result FROM jobs WHERE id=?', (request_id,)).fetchone()
        finally:
            connection.close()
        active = request_id in WORKERS
    if not row:
        raise ValueError('Trabajo Flow no encontrado.')
    result = json.loads(row['result']) if row['result'] else {'status':'in_progress' if active else 'needs_reconciliation'}
    response = {'id':request_id, 'status':result.get('status'), 'outputs':[]}
    if result.get('status') == 'completed' and result.get('path'):
        source = Path(result['path']).resolve()
        if not source.is_relative_to((RUNTIME / 'results').resolve()) or source.suffix.lower() not in {'.png','.jpg','.jpeg','.webp','.mp4','.gif'}:
            raise ValueError('Resultado Flow fuera del almacén permitido.')
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if digest != result.get('sha256'):
            raise ValueError('La integridad del resultado Flow no coincide.')
        destination = Path(data) / 'media' / (digest[:32] + source.suffix.lower())
        destination.parent.mkdir(parents=True, exist_ok=True)
        if not destination.exists():
            temporary = destination.with_suffix(destination.suffix + '.tmp-' + threading.current_thread().name)
            shutil.copyfile(source, temporary)
            temporary.replace(destination)
        if hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
            raise ValueError('La copia local de Flow no coincide.')
        response.update(outputs=['/media/' + destination.name], sha256=digest,
            dimensions=result.get('dimensions'), duration_seconds=result.get('duration_seconds'),
            source_request_id=result.get('source_request_id'),resolution=result.get('resolution'),operation=result.get('operation'))
    if response['status'] in {'needs_reconciliation','error','failed'}:
        response['status'] = 'failed'
        response['uncertain'] = True
        response['error'] = 'Revisar el trabajo existente de Flow. No se ha reintentado la generación.'
    return response
