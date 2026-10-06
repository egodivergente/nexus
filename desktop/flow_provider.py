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
from flow_contract import normalize, quote_key, INPUTS

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
    result = [dict(common, id='google-flow/nano-pro', providerModelId='nano-pro', displayName='Nano Banana Pro · tu cuenta Flow',
        modelFamily='Nano Banana', modalities=['IMAGE'], capabilities=['TEXT_TO_IMAGE'], isFlagship=True, parameters=[aspect(['1:1','16:9','9:16','4:3','3:4'])],
        shortDescription='Tu opción preferida: Nano Banana Pro con tu cuenta de Google Flow.'),
        dict(common, id='google-flow/nano2', providerModelId='nano2', displayName='Nano Banana 2 · tu cuenta Flow',
        modelFamily='Nano Banana', modalities=['IMAGE'], capabilities=['TEXT_TO_IMAGE'], parameters=[aspect(['1:1','16:9','9:16','4:3','3:4'])],
        shortDescription='Crea una imagen con Nano Banana 2 usando tu cuenta de Google Flow.'),
        dict(common, id='google-flow/veo-fast', providerModelId='veo_fast', displayName='Veo 3.1 Fast · tu cuenta Flow',
        modelFamily='Veo', modalities=['VIDEO'], capabilities=['TEXT_TO_VIDEO'], parameters=[aspect(['16:9','9:16'])],
        shortDescription='Genera un vídeo con Veo Fast usando tus créditos de Google Flow. Duración automática.')]
    for identifier,name,description in [
        ('veo_quality','Veo 3.1 Quality','Vídeo con Veo Quality y la duración automática de Flow.'),
        ('veo_lite','Veo 3.1 Lite','Vídeo con Veo Lite y la duración automática de Flow.'),
        ('omni_flash','Omni 1.1 Flash','Vídeo con Omni Flash y los ajustes predeterminados de Flow.')]:
        result.append(dict(common,id='google-flow/'+identifier,providerModelId=identifier,displayName=name+' · tu cuenta Flow',
            modelFamily='Omni' if identifier=='omni_flash' else 'Veo',modalities=['VIDEO'],capabilities=['TEXT_TO_VIDEO'],parameters=[aspect(['16:9','9:16'])],
            shortDescription=description))
    for identifier,name,modality,options in [
        ('image_upscale','Escalar imagen Flow','IMAGE',['2k','4k']),
        ('video_upscale','Escalar vídeo Flow','VIDEO',['1080p','4k']),
        ('video_gif','Exportar GIF de Flow','IMAGE',['gif'])]:
        result.append(dict(common,id='google-flow/'+identifier,providerModelId=identifier,displayName=name,
            modelFamily='Flow',modalities=[modality],promptRequired=False,supportedResolutions=options,capabilities=['UPSCALE'] if 'upscale' in identifier else ['EXPORT'],
            parameters=[{'key':'source_request_id','displayName':'Trabajo original de Flow','type':'TEXT','nativeType':'string','isRequired':True,
                'description':'jobId del historial de NEXUS o nexus_job_id del asset, de un resultado completado google_flow. No usar asset_id ni generation_id.'},
                {'key':'resolution','displayName':'Resolución','type':'ENUM','nativeType':'string','options':options,'defaultValue':options[0],'isRequired':True}],
            shortDescription='Mejora/exporta un resultado existente de tu cuenta Flow. Usa el jobId de su historial; conserva el original.'))
    # Separate entries keep required frames/references explicit in every form,
    # including assistant-created plans. There are no hidden conditional fields.
    video_models=[m for m in result if m['providerModelId'] in {'veo_fast','veo_quality','veo_lite','omni_flash'}]
    field=lambda key,label,maximum=1,required=False: {'key':key,'label':label,'mime':'image/*','maximum':maximum,
        'required':required,'minItems':1 if required else 0,'encoding':'array' if key=='reference_images' else 'string'}
    result.append(dict(common,id='google-flow/video_edit',providerModelId='video_edit',displayName='Editar vídeo · Omni 1.1',
        modelFamily='Omni',modalities=['VIDEO'],capabilities=['VIDEO_TO_VIDEO'],flowOperation='edit_video',
        shortDescription='Describe los cambios sobre un vídeo de Flow de hasta 10 segundos. Conserva el original.',
        parameters=[{'key':'source_request_id','displayName':'Vídeo original de Flow','type':'TEXT','nativeType':'string',
            'isRequired':True,'description':'Trabajo completado de la biblioteca. El original se conserva.'}]))
    for model in result:
        if model['providerModelId'] in {'nano2','nano-pro'}:
            model.update(supportsImageInput=True,inputMode='TEXT_OR_IMAGE',maxImages=10,
                contractMedia=[field('reference_images','Imágenes de referencia',10)],
                capabilities=['TEXT_TO_IMAGE','IMAGE_TO_IMAGE'])
    for base in video_models:
        identifier=base['providerModelId'];omni=identifier=='omni_flash'
        for mode,suffix,label in [('t2v','','Texto'),('i2v','-frames','Fotogramas'),('r2v','-references','Referencias')]:
            if mode=='r2v' and identifier=='veo_quality':continue
            model=base if mode=='t2v' else dict(base)
            durations=['4','6','8','10'] if omni else ['8'] if mode=='r2v' else ['4','6','8']
            model.update(id=base['id']+suffix,flowMode=mode,displayName=base['displayName'].split(' · ')[0]+' · '+label,
                supportsImageInput=mode!='t2v',inputMode='TEXT_ONLY' if mode=='t2v' else 'TEXT_AND_IMAGE',
                capabilities=[{'t2v':'TEXT_TO_VIDEO','i2v':'IMAGE_TO_VIDEO','r2v':'REFERENCE_TO_VIDEO'}[mode]],
                contractMedia=[] if mode=='t2v' else [field('initial_frame','Fotograma inicial',required=True),field('end_frame','Fotograma final (opcional)')] if mode=='i2v' else [field('reference_images','Imágenes de referencia',7 if omni else 3,True)],
                parameters=[aspect(['16:9','9:16']),{'key':'duration','displayName':'Duración (segundos)','type':'ENUM',
                    'nativeType':'integer','options':durations,'defaultValue':'8','isRequired':True},
                    {'key':'resolution','displayName':'Resolución nativa','type':'ENUM','nativeType':'string',
                        'options':['360p','720p'] if omni else ['720p'],'defaultValue':'720p','isRequired':True}],
                supportedResolutions=['360p','720p'] if omni else ['720p'],
                shortDescription={'t2v':'Crea vídeo desde texto.','i2v':'Anima un fotograma inicial; puedes fijar también el fotograma final.',
                    'r2v':'Usa hasta '+str(7 if omni else 3)+' imágenes como ingredientes.'}[mode]+' Ajustes y créditos de tu cuenta Flow.')
            if mode!='t2v':result.append(model)
            if omni and mode=='r2v':
                model['parameters'].append({'key':'voice','displayName':'Voz predefinida','type':'ENUM','nativeType':'string',
                    'options':['Automática','Achernar','Achird','Algenib','Algieba','Alnilam','Aoede','Autonoe','Callirrhoe',
                        'Charon','Despina','Enceladus','Erinome','Fenrir','Gacrux','Iapetus'],
                    'defaultValue':'Automática','isRequired':False})
    return result

def parameters(args):
    model = args.get('model')
    if model=='video_edit':
        source=args.get('source_request_id','')
        if not isinstance(source,str) or not IDENTIFIER.fullmatch(source):raise ValueError('Elige un vídeo original de Flow.')
        return {'model':model,'source_request_id':source}
    if model in {'image_upscale','video_upscale','video_gif'}:
        source=args.get('source_request_id','')
        options={'image_upscale':{'2k','4k'},'video_upscale':{'1080p','4k'},'video_gif':{'gif'}}
        if not isinstance(source,str) or not IDENTIFIER.fullmatch(source) or args.get('resolution') not in options[model]:
            raise ValueError('Elige un trabajo original de Flow y una resolución compatible.')
        return {'model':model,'source_request_id':source,'resolution':args['resolution']}
    return normalize(args)

def media_parameters(data, params):
    """Resolve opaque imported IDs, never caller-provided filesystem paths."""
    result=dict(params)
    root=(Path(data)/'media').resolve()
    for key in INPUTS:
        values=params.get(key,[])
        if isinstance(values,str):values=[values]
        paths=[]
        for value in values:
            if not re.fullmatch(r'[a-f0-9]{32}\.(?:png|jpg|jpeg|webp)',value):
                raise ValueError('Importa la imagen en Nexus antes de usarla con Flow.')
            path=(root/value).resolve(strict=True)
            if path.parent!=root or not path.is_file():raise ValueError('Imagen local no disponible.')
            paths.append(str(path))
        if paths:result[key]=paths if key=='reference_images' else paths[0]
    return result

def quote(args, data=None):
    params = parameters(args)
    if not available():
        raise ValueError('El puente local de Flow no está instalado.')
    if params['model']=='video_edit':
        result=call('flow_get_edit_options',{'source_request_id':params['source_request_id']},'edit-options-'+uuid.uuid4().hex)
        if result.get('status')!='available':raise ValueError('Flow no confirma la edición de este vídeo completo (máximo 10 s).')
        # Availability is authoritative; the native editor hides its settings
        # price. Null never masquerades as free or as an estimated credit cost.
        return {'amount':None,'currency':'flow_account','time':int(time.time()*1000),
            'detail':'Edición disponible en tu cuenta Flow. El editor no muestra un importe.',
            'source':'Flow native edit editor'}
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
        value = json.loads(prices.read_text(encoding='utf-8')).get(quote_key(params))
        if value and value.get('aspect')==params['aspect'] and time.time() - value['checked_at'] < 300:
            if value['credits'] is None and params.get('voice'):
                return {'amount':None,'currency':'flow_account','time':int(time.time()*1000),
                    'detail':'Voz disponible en tu cuenta Flow. Importe no expuesto en esta comprobación.',
                    'source':value['source']}
            return {'amount':value['credits'], 'currency':'flow_credits', 'time':int(time.time()*1000),
                'detail':'Incluido en tu cuenta Flow.' if value['credits']==0 else 'Uso de los créditos de tu cuenta Flow.', 'source':'Flow settings'}
    verified=call('flow_get_quote',media_parameters(data,params) if any(k in params for k in INPUTS) and data else params,'quote-'+uuid.uuid4().hex)
    if verified.get('error_class') == 'SessionUnavailable':
        try:session_status=json.loads((RUNTIME/'jobs/session-health.json').read_text(encoding='utf-8')).get('status','error')
        except (OSError,ValueError):session_status='error'
        messages={'auth_required':'Google necesita que completes el acceso a Flow. La renovación automática no ha podido terminar.',
            'busy':'Flow está en uso. La sesión se renovará al terminar el trabajo actual.',
            'error':'No se ha podido renovar la sesión de Flow. Nexus lo volverá a intentar automáticamente.'}
        raise ValueError(messages.get(session_status,messages['error']))
    if verified.get('status') == 'quoted':
        if verified.get('credits') is None and params.get('voice'):
            return {'amount':None,'currency':'flow_account','time':int(time.time()*1000),
                'detail':'Voz disponible en tu cuenta Flow. Importe no expuesto en esta comprobación.',
                'source':verified['source']}
        return {'amount':verified['credits'],'currency':'flow_credits','time':int(time.time()*1000),
            'detail':'Incluido en tu cuenta Flow.' if verified['credits']==0 else 'Uso de los créditos de tu cuenta Flow.', 'source':'Flow settings'}
    if params['model'] in {'nano2','nano-pro'}:
        raise ValueError('No se ha podido leer la configuración del modelo de imagen en Flow. No se ha enviado ninguna generación.')
    raise ValueError('Flow no confirma el importe de esta configuración de vídeo. Vuelve a consultar sus ajustes.')

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

def work(data, request_id, params, recover=False, recovery_started_at=0):
    try:
        if recover:
            tool='flow_get_result'
            arguments={}
        elif params.get('model')=='video_edit':
            tool='flow_edit_video'
            arguments={k:v for k,v in params.items() if k not in {'model','expected_credits'}}
        elif 'source_request_id' in params:
            tool='flow_process_asset'
            arguments={k:v for k,v in params.items() if k!='model'}
        else:
            tool = 'flow_generate_image' if params['model'] in {'nano2','nano-pro'} else 'flow_generate_video'
            arguments=media_parameters(data,params)
        result = call(tool, {'request_id':request_id, **arguments},
                      'recovery-'+uuid.uuid4().hex if recover else request_id)
        if not isinstance(result, dict):
            raise ValueError('Flow devolvió un resultado no válido.')
    except Exception as exc:
        result = {'status':'needs_reconciliation', 'error_class':type(exc).__name__}
    if recover:
        result['recovery_started_at'] = recovery_started_at
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
    needs_prompt='source_request_id' not in params or params['model']=='video_edit'
    if not IDENTIFIER.fullmatch(request_id) or not isinstance(prompt, str) or (needs_prompt and not prompt.strip()):
        raise ValueError('Identificador o prompt Flow no válido.')
    if needs_prompt:params['prompt'] = prompt.strip()
    # Return an existing reservation before another browser quote/profile lease.
    with LOCK:
        connection=ledger(data)
        try:prior=connection.execute('SELECT params FROM jobs WHERE id=?',(request_id,)).fetchone()
        finally:connection.close()
    if prior:
        comparable=json.loads(prior['params']);comparable.pop('expected_credits',None)
        if comparable!=params:raise ValueError('El identificador pertenece a otro trabajo. No se envía de nuevo.')
        return status(data,request_id)
    # Validate local IDs before reserving; the private bridge freezes the bytes
    # and binds those snapshots to its persistent request before any upload.
    if any(k in params for k in INPUTS):media_parameters(data,params)
    estimate = quote(params,data)
    if args.get('cost') != estimate['amount']:
        raise ValueError('Actualiza la estimación Flow antes del envío.')
    if 'source_request_id' in params and params['model']!='video_edit':params['expected_credits']=estimate['amount']
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

def recover(data, request_id):
    """Recover an existing reservation using only flow_get_result; never submit."""
    if not isinstance(request_id, str) or not IDENTIFIER.fullmatch(request_id):
        raise ValueError('Identificador Flow no válido.')
    with LOCK:
        connection = ledger(data)
        try:
            row = connection.execute('SELECT result FROM jobs WHERE id=?', (request_id,)).fetchone()
            if not row:
                raise ValueError('Trabajo Flow no encontrado. No se envía ninguna generación.')
            active = request_id in WORKERS
        finally:
            connection.close()
    if active:
        return status(data, request_id)
    if row['result'] and json.loads(row['result']).get('status') == 'completed':
        try:
            return status(data, request_id)
        except (OSError, ValueError):
            pass  # The bridge can recover a missing or damaged existing download.
    with LOCK:
        connection = ledger(data)
        try:
            # Recheck under the reservation lock: simultaneous clicks share one worker.
            latest = connection.execute('SELECT result FROM jobs WHERE id=?', (request_id,)).fetchone()['result']
            changed_to_completed = latest != row['result'] and latest and json.loads(latest).get('status') == 'completed'
            if request_id not in WORKERS and not changed_to_completed:
                previous = connection.execute('SELECT result FROM jobs WHERE id=?', (request_id,)).fetchone()['result']
                revision = max(int(time.time()*1000), (json.loads(previous).get('recovery_started_at', 0)+1) if previous else 0)
                pending = {'status':'in_progress', 'recovery_started_at':revision}
                with connection:
                    connection.execute('UPDATE jobs SET result=? WHERE id=?', (json.dumps(pending), request_id))
                WORKERS.add(request_id)
                try:
                    threading.Thread(target=work, args=(data, request_id, {}),
                                     kwargs={'recover':True, 'recovery_started_at':revision}, daemon=True).start()
                except Exception:
                    WORKERS.discard(request_id)
                    with connection:
                        connection.execute('UPDATE jobs SET result=? WHERE id=?', (previous, request_id))
                    raise RuntimeError('No se pudo iniciar la recuperación. No se ha generado nada.') from None
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
    if result.get('status') == 'in_progress' and not active:
        result['status'] = 'needs_reconciliation'
    if result.get('status') == 'completed' and not result.get('path'):
        result['status'] = 'needs_reconciliation'
    response = {'id':request_id, 'status':result.get('status'), 'outputs':[],
                'recovery_started_at':result.get('recovery_started_at', 0),
                'reconciling':bool(active and result.get('recovery_started_at'))}
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
