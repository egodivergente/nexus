"""Single-worker, crash-safe protection of completed generation originals."""
import hashlib
import json
import mimetypes
import os
from pathlib import Path
import queue
import re
import shutil
import threading
import time
import urllib.parse
import urllib.request
import uuid

from gallery_previews import GalleryPreviews
from media_import import MAX_IMPORT_BYTES, MIME


def file_hash(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


class OriginalArchive:
    def __init__(self, data, read_state, register_assets=None, start_worker=True):
        self.data = Path(data)
        self.media = self.data / 'media'
        self.media.mkdir(parents=True, exist_ok=True)
        self.path = self.data / 'originals-protection.json'
        self.read_state, self.register_assets = read_state, register_assets
        self.lock = threading.RLock()
        try:
            value = json.loads(self.path.read_text('utf-8'))
            self.records = value.get('records', {}) if isinstance(value, dict) else {}
        except (OSError, ValueError):
            self.records = {}
        self.records = {k:v for k,v in self.records.items() if isinstance(v, dict)} if isinstance(self.records,dict) else {}
        self._reconcile_registered_index()
        self.previews = GalleryPreviews(self.data, read_state, lambda: '', allow_remote=False)
        self.work = queue.Queue()
        self.queued = set()
        self.closed = threading.Event()
        if start_worker:
            threading.Thread(target=self._worker, name='nexus-originals', daemon=True).start()

    def _reconcile_registered_index(self):
        """Invalidate legacy acknowledgements without changing index or disk state.

        addAsset can return an existing URI under a different ID. Older releases
        recorded boolean success and incorrectly retained the requested ID.
        The normal registration worker repairs those rows with a verified mapping.
        """
        path = self.data/'assistant'/'index.json'
        if not path.exists():return
        try:
            value=json.loads(path.read_text('utf-8'));assets=value.get('assets',[]) if isinstance(value,dict) else []
            indexed={a['id']:a.get('uri') for a in assets if isinstance(a,dict) and isinstance(a.get('id'),str)} if isinstance(assets,list) else {}
        except (OSError,ValueError):
            indexed={}
        for row in self.records.values():
            asset_id=row.get('assetId')
            valid=isinstance(asset_id,str) and indexed.get(asset_id)==row.get('uri') and asset_id in indexed
            if not valid or not row.get('registered'):
                row['registered']=False
                row['cloudVerifiedAt']=None

    def _save(self):
        temporary = self.path.with_name(self.path.name + '.' + uuid.uuid4().hex + '.tmp')
        try:
            with temporary.open('w', encoding='utf-8') as stream:
                json.dump({'version':1,'records':self.records}, stream, ensure_ascii=False)
                stream.flush();os.fsync(stream.fileno())
            os.replace(temporary, self.path)
        finally:
            temporary.unlink(missing_ok=True)

    def _replace_record(self, key, record):
        previous = self.records.get(key)
        self.records[key] = record
        try:self._save()
        except Exception:
            if previous is None:self.records.pop(key,None)
            else:self.records[key]=previous
            raise

    def schedule(self, state=None, remote=True, retry=False):
        state = state if state is not None else self.read_state()
        for job in (state or {}).get('jobs', []):
            if not isinstance(job, dict) or job.get('status') != 'COMPLETED':continue
            for index, output in enumerate(job.get('outputs') or []):
                url = output if isinstance(output, str) else output.get('url', '') if isinstance(output, dict) else ''
                if not url:continue
                key = self.previews.key(job['id'], index, url)
                with self.lock:
                    previous = self.records.get(key, {})
                    if key in self.queued:continue
                    if previous.get('state') == 'archived' and (self.media / Path(previous.get('uri', '')).name).is_file():continue
                    if not retry and previous.get('retryAt', 0) > time.time():continue
                    self.queued.add(key)
                    self.work.put((job['id'], index, url, remote))
        return self.public()

    def protect(self, job_id, index=0, remote=True):
        job, original, source = self.previews.original(job_id, index)
        key = self.previews.key(job_id, index, original)
        temporary = self.media / ('.original-' + uuid.uuid4().hex + '.part')
        try:
            if source is None:
                if not remote:raise ValueError('El original necesita conexión para recuperarse.')
                source, mime, digest = self._download(original, temporary)
                extension = (Path(urllib.parse.urlsplit(original).path).suffix.lower() if mime == 'application/octet-stream' else mimetypes.guess_extension(mime)) or Path(urllib.parse.urlsplit(original).path).suffix.lower()
                if mime == 'application/octet-stream':mime = MIME.get(extension,mime)
            else:
                digest = file_hash(source)
                extension = source.suffix.lower()
                mime = MIME.get(extension, mimetypes.guess_type(source.name)[0] or 'application/octet-stream')
            if extension == '.jpe':extension = '.jpg'
            if extension not in MIME:raise ValueError('Formato del original todavía no compatible con la biblioteca.')
            expected = job.get('verifiedSha256')
            if len(job.get('outputs', [])) == 1 and isinstance(expected, str) and re.fullmatch('[a-fA-F0-9]{64}', expected) and expected.lower() != digest:
                raise ValueError('La huella del original no coincide con la verificada.')
            if source.parent.resolve() == self.media.resolve() and re.fullmatch(r'[a-f0-9]{32}\.[a-z0-9]+', source.name):
                target = source
            else:
                target = self.media / (digest[:32] + extension)
                if target.is_file() and file_hash(target) != digest:
                    target = self.media / (uuid.uuid4().hex + extension)
                if not target.is_file():
                    if source != temporary:
                        shutil.copyfile(source, temporary)
                        if file_hash(temporary) != digest:raise ValueError('La copia del original no supera la verificación.')
                        with temporary.open('ab') as stream:stream.flush();os.fsync(stream.fileno())
                    os.replace(temporary, target)
            if file_hash(target) != digest:raise ValueError('El original protegido no supera la verificación.')
            record = {'jobId':job_id,'index':index,'sourceUrl':original,'uri':'/media/'+target.name,
                'sha256':digest,'sizeBytes':target.stat().st_size,'mimeType':mime,'state':'archived',
                'archivedAt':int(time.time()*1000),'projectId':job.get('projectId'),
                'createdAt':job.get('startedAt') or int(time.time()*1000),
                'assetId':'asset_original_'+hashlib.sha256(f'{job_id}:{index}:{digest}'.encode()).hexdigest()[:32],
                'displayName':job.get('name') or 'Original protegido','type':job.get('modality','OTHER'),
                'registered':False,'cloudVerifiedAt':None}
            with self.lock:
                previous = self.records.get(key, {})
                if previous.get('sha256') == digest and previous.get('uri') == record['uri']:
                    record.update(assetId=previous.get('assetId') or record['assetId'],
                                  registered=previous.get('registered',False),cloudVerifiedAt=previous.get('cloudVerifiedAt'))
                self._replace_record(key,record)
            return dict(record)
        finally:
            temporary.unlink(missing_ok=True)

    def _download(self, url, target):
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password:
            raise ValueError('El enlace del original no es válido.')
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent':'Nexus/1.0 original archive'}), timeout=20) as response:
            mime = response.headers.get_content_type()
            if not mime.startswith(('image/','video/','audio/')) and mime not in {'application/octet-stream','application/zip'}:
                raise ValueError('El proveedor no devolvió un original multimedia.')
            length = int(response.headers.get('Content-Length','0'))
            if length > MAX_IMPORT_BYTES:raise ValueError('El original supera 4 GiB.')
            if shutil.disk_usage(self.media).free < max(length,64*1024*1024) + 32*1024*1024:
                raise ValueError('No queda espacio suficiente para proteger el original.')
            digest = hashlib.sha256();size = 0;deadline = time.monotonic()+300
            with target.open('wb') as stream:
                while block := response.read(1024*1024):
                    size += len(block)
                    if size > MAX_IMPORT_BYTES or time.monotonic() > deadline:
                        raise ValueError('No se ha completado la descarga del original dentro de sus límites.')
                    digest.update(block);stream.write(block)
                stream.flush();os.fsync(stream.fileno())
            if not size or length and size != length:raise ValueError('El original llegó incompleto.')
        return target,mime,digest.hexdigest()

    def _register(self):
        if not self.register_assets:return
        with self.lock:
            rows = [(key,dict(row)) for key,row in self.records.items() if row.get('state')=='archived' and not row.get('registered')][:16]
        if not rows:return
        assets = [{'id':r['assetId'],'uri':r['uri'],'displayName':r['displayName'],
            'type':r['type'] if r['type'] in {'IMAGE','VIDEO','AUDIO'} else 'OTHER',
            'role':'OUTPUT','source':'GENERATED','createdAt':r.get('createdAt',r['archivedAt']),
            'projectId':r.get('projectId'),'mimeType':r['mimeType'],'sizeBytes':r['sizeBytes']} for _,r in rows]
        accepted=self.register_assets(assets)
        if not isinstance(accepted,dict):return
        with self.lock:
            previous={}
            for key,captured in rows:
                canonical=accepted.get(captured['assetId'])
                if not isinstance(canonical,str) or not canonical or len(canonical)>200 or any(c<' ' for c in canonical):continue
                current=self.records.get(key)
                if (not current or current.get('registered') or current.get('state')!='archived'
                        or any(current.get(field)!=captured.get(field) for field in ('assetId','uri','sha256'))):
                    continue
                previous[key]=dict(current)
                current.update(assetId=canonical,registered=True,cloudVerifiedAt=None)
            if not previous:return
            try:self._save()
            except Exception:
                for key,value in previous.items():self.records[key]=value
                raise

    def _worker(self):
        while not self.closed.is_set():
            try:job,index,url,remote = self.work.get(timeout=3)
            except queue.Empty:
                try:self._register()
                except Exception:pass
                continue
            key = self.previews.key(job,index,url)
            try:self.protect(job,index,remote)
            except Exception:
                with self.lock:
                    try:self._replace_record(key,{'jobId':job,'index':index,'sourceUrl':url,'state':'pending',
                        'retryAt':time.time()+3600,'message':'Original pendiente; se conserva su referencia.'})
                    except OSError:pass
            finally:
                with self.lock:self.queued.discard(key)
                self.work.task_done()
            if self.work.empty() or self.work.qsize()%16==0:
                try:self._register()
                except Exception:pass

    def public(self):
        with self.lock:
            rows=list(self.records.values())
            return {'archived':sum(r.get('state')=='archived' for r in rows),'pending':len(self.queued),
                'failed':sum(r.get('state')!='archived' for r in rows),
                'registered':sum(bool(r.get('registered')) for r in rows),
                'pendingCloud':sum(r.get('state')=='archived' and not r.get('cloudVerifiedAt') for r in rows)}

    def cloud_verified(self, verified_assets):
        with self.lock:
            now=int(time.time()*1000)
            previous={key:dict(row) for key,row in self.records.items()}
            for row in self.records.values():
                if row.get('state')=='archived' and row.get('registered') and verified_assets.get(row.get('assetId')) == row.get('sha256'):
                    row['cloudVerifiedAt']=now
            try:self._save()
            except Exception:self.records=previous;raise

    def close(self):
        self.closed.set()
