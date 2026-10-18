"""One local Corte service shared by manual controls and Nexus tools.

Edits are never retried by this transport. Their durable request receipts live
inside the Kotlin project transaction. Original media remains in the library.
"""
import atexit
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time
import uuid

from native_operations import atomic_json, FLAGS


class CorteHost:
    def __init__(self, root, data, native):
        self.root, self.data, self.native = Path(root), Path(data), native
        self.projects = self.data / 'projects'
        self.jobs_dir = self.data / 'corte-jobs'
        self.jobs_dir.mkdir(parents=True, exist_ok=True)
        self.cache = self.data / 'corte-cache'
        self.cache.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.process = None
        self.queue = queue.Queue()
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix='corte-render')
        self.cancelled = set()
        self.agent_context = None
        self.analysis_lock = threading.Lock()
        for p in self.jobs_dir.glob('*.json'):
            job = json.loads(p.read_text('utf-8'))
            if job['state'] in {'QUEUED', 'RUNNING'}:
                job.update(state='INTERRUPTED', error='Render interrumpido; no se ha repetido.')
                atomic_json(p, job)
        atexit.register(self.close)

    def close(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=3)
        if self.process:
            self.process.stdin.close()
            self.process.stdout.close()
            self.process = None

    def exchange(self, op, args):
        with self.lock:
            if not self.process or self.process.poll() is not None:
                java = shutil.which('java') or next(iter(Path('C:/Program Files/Microsoft').glob('jdk-17*/bin/java.exe')), None)
                if not java:
                    raise ValueError('Falta Java 17 para el motor Corte.')
                self.queue = queue.Queue()
                self.process = subprocess.Popen([str(java), '-Xmx512m', '-Dfile.encoding=UTF-8', '-cp',
                    str(self.root/'agent/runtime/*'), 'CorteDesktopMain', str(self.projects)],
                    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                    text=True, encoding='utf-8', creationflags=FLAGS)
                q, process = self.queue, self.process
                def read():
                    for line in process.stdout:
                        q.put(line)
                    q.put(None)
                threading.Thread(target=read, daemon=True).start()
            self.process.stdin.write(json.dumps({'op': op, 'args': args})+'\n')
            self.process.stdin.flush()
            try:
                line = self.queue.get(timeout=45)
            except queue.Empty:
                self.close()
                raise ValueError('Resultado incierto. Consulta el proyecto o recupera el mismo request_id antes de editar.')
            if line is None:
                raise ValueError('Motor Corte cerrado. Recupera el mismo request_id antes de repetir.')
            result = json.loads(line)
            if not result.get('ok'):
                raise ValueError(result.get('error', 'Edición rechazada'))
            return result

    def metadata(self, asset, path, media_id=None):
        mime = asset.get('mimeType') or ''
        image = mime.startswith('image/')
        if image:
            from PIL import Image
            with Image.open(path) as im:
                width, height = im.size
            info = {'width': width, 'height': height, 'streams': [], 'has_audio': False}
        else:
            info = self.native.inspect(path)
        video = next((s for s in info['streams'] if s['codec_type']=='video'), {})
        audio = next((s for s in info['streams'] if s['codec_type']=='audio'), {})
        return {'id': media_id or 'media_'+uuid.uuid4().hex, 'uri': path.resolve().as_uri(),
                'displayName': asset.get('displayName', path.name), 'kind': 'IMAGE' if image else 'VIDEO' if video else 'AUDIO',
                'durationUs': None if image else round(info['durationSec']*1_000_000),
                'width': info.get('width') or 0, 'height': info.get('height') or 0,
                'hasVideo': image or bool(video), 'hasAudio': bool(audio),
                'videoMime': video.get('codec_name'), 'audioMime': audio.get('codec_name'),
                'sampleRate': int(audio.get('sample_rate') or 0), 'channelCount': int(audio.get('channels') or 0),
                'sizeBytes': path.stat().st_size,
                'fingerprint': self.fingerprint(path)}

    @staticmethod
    def fingerprint(path):
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha256').hexdigest()

    def import_media(self, args, assets):
        # Resolve all files before changing the project. Never bind a late result to another project.
        project = self.exchange('read_project', {'project_id': args['project_id']})
        media, links = [], {}
        ids = args.get('asset_ids', [])
        if not isinstance(ids, list) or not 1 <= len(ids) <= 100:
            raise ValueError('Selecciona de 1 a 100 archivos de la biblioteca.')
        for identifier in ids:
            asset, path = self.native.asset(identifier, assets)
            if asset.get('projectId') not in {None, '', args['project_id']}:
                raise ValueError('El resultado pertenece a otro proyecto. Incorpóralo explícitamente antes de colocarlo.')
            existing = next((mid for mid, link in project['links'].items() if link.get('asset_id')==identifier), None)
            m = self.metadata(asset, path, existing)
            media.append(m)
            links[m['id']] = {'asset_id': identifier, 'job_id': asset.get('jobId'),
                'conversation_id': args.get('conversation_id'), 'provider': asset.get('providerId'),
                'model': asset.get('modelId'), 'original_uri': asset.get('uri')}
        return self.exchange('import_media', {**args, 'media': media, 'links': links})

    def run(self, args, assets=None):
        op = args.get('method', 'status')
        if op=='status':
            result=self.exchange('status',{})
            result['methods']=['status','list_projects','read_project','find_silences','apply_ops','undo','redo','import_media','media_analysis','preview','export','cancel_export','jobs','relink']
            result['active_project']=(self.agent_context or {}).get('project_id')
            return result
        if op=='read_project':
            from corte_render import local_path
            result=self.exchange(op,args)
            result['missing_media']=[{'id':m['id'],'name':m['displayName']} for m in result['project']['media'] if not local_path(m['uri']).is_file()]
            return result
        if op=='relink':
            from native_operations import resolve_media
            result=self.exchange('read_project',{'project_id':args['project_id']})
            old=next((m for m in result['project']['media'] if m['id']==args.get('media_id')),None)
            if not old:raise ValueError('Medio desconocido.')
            path=resolve_media(self.data,args['uri'])
            media=self.metadata({'displayName':args.get('name',old['displayName']),'mimeType':args.get('mime','')},path,old['id'])
            if old['fingerprint']!=media['fingerprint'] and not args.get('accept_changed_file'):
                raise ValueError('El archivo tiene otra huella; confirma la sustitución expresamente.')
            if old.get('durationUs') and media.get('durationUs',0)<old['durationUs']:
                raise ValueError('El archivo de sustitución es más corto que el original.')
            return self.exchange(op,{**args,'media':media})
        if op in {'find_silences','media_analysis'}:
            snapshot=self.exchange('read_project',{'project_id':args['project_id']})
            media={m['id']:m for m in snapshot['project']['media']}
            if op=='media_analysis':
                m=media[args['media_id']]
                return self.analysis(m,args)
            ranges=[];details=[]
            for track in snapshot['project']['timeline']['audioTracks']:
                for clip in track['clips']:
                    if args.get('clip_ids') and clip['id'] not in args['clip_ids']:continue
                    result=self.analysis(media[clip['mediaId']],{**args,'clip':clip})
                    ranges.extend(result['ranges']);details.append({'clip_id':clip['id'],'ranges':result['ranges']})
            return {'ok':True,'clips':details,'suggested_op':{'op':'delete_ranges','ranges':ranges,'ripple':True},'revision':snapshot['revision']}
        if op == 'import_media':
            return self.import_media(args, assets if assets is not None else self.native.assets({}))
        if op in {'preview', 'export'}:
            project = self.exchange('read_project', args)
            if project['revision'] != args.get('expected_revision'):
                raise ValueError('El montaje ha cambiado; actualiza antes de exportar.')
            identifier = str(args.get('request_id', ''))
            if not identifier or not all(c.isalnum() or c in '_-' for c in identifier):
                raise ValueError('Falta identificador de exportación válido.')
            path = self.jobs_dir/(identifier+'.json')
            with self.lock:
                if path.exists():
                    old=json.loads(path.read_text('utf-8'))
                    if old['project_id'] != args['project_id'] or old['revision'] != project['revision'] or old['kind'] != op:
                        raise ValueError('Identificador de render reutilizado con otro montaje.')
                    return old
                job = {'id': identifier, 'project_id': args['project_id'], 'revision': project['revision'],
                       'state': 'QUEUED', 'kind': op, 'progress': 0, 'createdAt': int(time.time()*1000)}
                atomic_json(path, job)
                self.executor.submit(self.render, job, project)
            return job
        if op == 'jobs':
            return {'jobs': sorted([json.loads(p.read_text('utf-8')) for p in self.jobs_dir.glob('*.json')], key=lambda j:j['createdAt'], reverse=True)[:100]}
        if op == 'cancel_export':
            self.cancelled.add(args['job_id'])
            return {'requested': True}
        return self.exchange(op, args)

    def analysis(self, media, args):
        with self.analysis_lock:
            return self._analysis(media,args)

    def _analysis(self, media, args):
        from corte_render import local_path
        path=local_path(media['uri'])
        if not path.is_file():raise ValueError('Medio ausente: '+media['displayName'])
        digest=media['fingerprint']
        if not digest or any(c not in '0123456789abcdef' for c in digest):raise ValueError('Huella inválida')
        thumb_record=self.cache/(digest+'.json')
        record=json.loads(thumb_record.read_text('utf-8')) if thumb_record.exists() else {}
        if media['hasVideo'] and not record.get('thumbnail'):
            thumb=self.native.new_path('.png')
            self.native.encode(['-i',path,'-frames:v','1','-vf','scale=240:-1',thumb])
            record['thumbnail']='/media/'+thumb.name;atomic_json(thumb_record,record)
        result={'ok':True,**record}
        if media['hasAudio']:
            waveform=self.cache/(digest+'.peaks');pcm=self.cache/(digest+'.pcm')
            try:
                if not waveform.exists():self.native.encode(['-i',path,'-vn','-ac','1','-ar','16000','-f','s16le',pcm])
                result.update(self.exchange('media_analysis',{'pcm_path':str(pcm),'waveform_path':str(waveform),**{k:v for k,v in args.items() if k in {'pixels','clip','threshold_db','min_ms','pad_ms'}}}))
            finally:
                if pcm.exists():pcm.unlink()
        return result

    def render(self, job, project):
        from corte_render import render_project
        path = self.jobs_dir/(job['id']+'.json')
        def progress(done, total):
            if job['id'] in self.cancelled:
                raise InterruptedError('Exportación cancelada')
            job.update(state='RUNNING', progress=done/total if total else 0)
            atomic_json(path, job)
        try:
            output = self.data/'media'/(uuid.uuid4().hex+'.mp4')
            output.parent.mkdir(parents=True, exist_ok=True)
            render_project(self, project, output, progress, preview=job['kind']=='preview')
            job.update(state='COMPLETED', progress=1, url='/media/'+output.name,
                       sha256=self.fingerprint(output), bytes=output.stat().st_size)
        except InterruptedError as e:
            job.update(state='CANCELLED', error=str(e))
        except Exception as e:
            job.update(state='FAILED', error=str(e)[:1500])
        finally:
            self.cancelled.discard(job['id'])
            atomic_json(path, job)

    def agent_run(self, operation, payload):
        method=operation.removeprefix('corte_')
        args=dict(payload.get('input') or {})
        if method in {'status','list_projects'}:
            return self.run({'method':method})
        # The turn captures explicit project/selection/playhead before the model runs.
        context = self.agent_context
        if not context:
            raise ValueError('Abre un proyecto Nexus antes de editar.')
        args['project_id']=args.get('project_id') or context['project_id']
        if args['project_id'] != context['project_id']:
            raise ValueError('El proyecto solicitado no es el del turno actual.')
        if method == 'read_project':
            context.update(self.exchange(method,args)); return {k:v for k,v in context.items() if not k.startswith('_')}
        duplicate_key=hashlib.sha256(json.dumps({'method':method,'args':args},sort_keys=True).encode()).hexdigest()
        receipts=context.setdefault('_turn_receipts',{})
        if duplicate_key in receipts:
            return receipts[duplicate_key]
        args.setdefault('expected_revision',context['revision'])
        args.setdefault('request_id',uuid.uuid4().hex)
        if method=='apply_ops':
            args['ops']=json.loads(args.pop('ops_json'))
        if method=='undo':
            if not context.get('agent_transaction_id'):
                raise ValueError('No hay transacción del agente identificada para deshacer.')
            args['transaction_id']=context['agent_transaction_id']
        result=self.run({'method':method,**args},payload.get('assets'))
        if 'revision' in result:
            context.update(result)
            if method in {'apply_ops','import_media'}:
                context['agent_transaction_id']=result.get('transaction_id')
        receipts[duplicate_key]=result
        return result
