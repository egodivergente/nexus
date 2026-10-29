"""Read-only file projection of the shared asset index and generation history."""
import hashlib
import json
from pathlib import Path
import threading

from gallery_sources import GallerySources


class LibrarySnapshot:
    def __init__(self, data, read_state, archive=None):
        self.data, self.read_state, self.archive = Path(data), read_state, archive
        self.sources = GallerySources(self.data, read_state)
        self.media = (self.data / "media").resolve()
        self.lock = threading.RLock()
        self.cached_key = None
        self.cached = None
        self.index_key = None
        self.index = {}

    @staticmethod
    def _stamp(path):
        try:
            stat=path.stat();return stat.st_mtime_ns,stat.st_size
        except OSError:return None

    def _index(self):
        path=self.data/'assistant/index.json';key=self._stamp(path)
        if key!=self.index_key:
            try:
                value=json.loads(path.read_text('utf-8-sig'))
                self.index=value if isinstance(value,dict) else {}
            except (OSError,ValueError):self.index={}
            self.index_key=key
        return self.index

    @staticmethod
    def modality(asset):
        return asset.get('type') if asset.get('type') in {'IMAGE','VIDEO','AUDIO'} else 'CHAT'

    def _local(self, asset):
        paths=self.sources._asset_paths(asset)
        return paths[0] if paths else None

    def preview_state(self):
        with self.lock:
            return {'jobs':[{'id':a['id'],'modality':self.modality(a),'outputs':[a['uri']],
                             'providerId':'local_media','name':a.get('displayName','Archivo'),
                             'status':'COMPLETED'} for a in self._index().get('assets',[])
                            if isinstance(a,dict) and isinstance(a.get('id'),str) and isinstance(a.get('uri'),str)]}

    def snapshot(self):
        with self.lock:
            state=self.read_state() or {};index=self._index()
            jobs=[j for j in state.get('jobs',[]) if isinstance(j,dict)]
            signature=json.dumps([(j.get('id'),j.get('status'),j.get('outputs'),j.get('projectId')) for j in jobs],sort_keys=True)
            key=(self.index_key,self._stamp(self.data/'remote-media-cache.json'),
                 self._stamp(self.data/'media-aliases.json'),self._stamp(self.data/'originals-protection.json'),signature)
            if key==self.cached_key and self.cached is not None:return self.cached
            self.sources._reload()
            protected={}
            if self.archive:
                with self.archive.lock:
                    protected={k:dict(v) for k,v in self.archive.records.items() if v.get('state')=='archived'}
            path_keys={};uri_keys={};job_entries=[]
            for job in jobs:
                for ordinal,output in enumerate(job.get('outputs') or []):
                    uri=output if isinstance(output,str) else output.get('url','') if isinstance(output,dict) else ''
                    if not uri:continue
                    identifier=f"job:{job['id']}:{ordinal}";keys=[identifier]
                    uri_keys.setdefault(uri,set()).add(identifier)
                    local=None
                    try:_,_,local=self.sources.resolve(job['id'],ordinal,state=state,reload=False)
                    except (ValueError,OSError):pass
                    local_uri=None
                    if local and local.parent==self.media:
                        local_uri='/media/'+local.name;keys.append('local:'+local_uri)
                        path_keys.setdefault(str(local),set()).add(identifier)
                    record=protected.get(hashlib.sha256(json.dumps([job['id'],ordinal,uri],ensure_ascii=False).encode()).hexdigest())
                    if record:
                        keys.extend(['asset:'+record['assetId'],'sha:'+record['sha256'],'local:'+record['uri']])
                        local_uri=record['uri']
                    job_entries.append({'id':identifier,'sourceJobId':job['id'],'outputIndex':ordinal,
                        'displayName':job.get('name') or job.get('modelId') or 'Resultado',
                        'modality':job.get('modality','CHAT'),'uri':uri,'localUri':local_uri,
                        'mimeType':record.get('mimeType') if record else None,
                        'sizeBytes':record.get('sizeBytes') if record else None,
                        'createdAt':job.get('startedAt',0),'projectId':job.get('projectId'),
                        'providerId':job.get('providerId'),'modelId':job.get('modelId'),
                        'status':job.get('status'),'source':'GENERATED','identityKeys':keys})
            generation_by_asset={}
            for generation in index.get('generations',[]):
                if not isinstance(generation,dict):continue
                for asset_id in generation.get('outputAssetIds') or []:
                    generation_by_asset.setdefault(asset_id,generation)
            entries=[];hidden=[];visible_keys=set();hidden_jobs=set()
            for asset in index.get('assets',[]):
                if not isinstance(asset,dict) or not isinstance(asset.get('id'),str) or not isinstance(asset.get('uri'),str):continue
                local=self._local(asset);local_uri='/media/'+local.name if local and local.parent==self.media else None
                keys=['asset:'+asset['id']]
                if local_uri:keys.append('local:'+local_uri)
                linked=set(uri_keys.get(asset['uri'],set())) | (path_keys.get(str(local),set()) if local else set())
                keys.extend(sorted(linked))
                if asset.get('libraryDeleted'):
                    hidden.append('asset:'+asset['id']);hidden_jobs.update(linked);continue
                visible_keys.update(keys)
                generation=generation_by_asset.get(asset['id'],{})
                entry={'id':'asset:'+asset['id'],'assetId':asset['id'],'generationId':generation.get('id'),
                    'displayName':asset.get('displayName') or 'Archivo','modality':self.modality(asset),
                    'uri':asset['uri'],'localUri':local_uri,'createdAt':asset.get('createdAt',0),
                    'mimeType':asset.get('mimeType'),'sizeBytes':asset.get('sizeBytes'),
                    'projectId':asset.get('projectId'),'providerId':generation.get('providerId') or 'local_media',
                    'modelId':generation.get('modelId') or 'shared-library','status':'COMPLETED',
                    'source':asset.get('source') or 'SHARED','identityKeys':keys}
                if len(linked)==1:
                    link=next(iter(linked));job_key,ordinal=link.rsplit(':',1)
                    entry.update(sourceJobId=job_key.removeprefix('job:'),outputIndex=int(ordinal))
                entries.append(entry)
            hidden.extend(sorted(hidden_jobs-visible_keys))
            entries.extend(j for j in job_entries if j['id'] not in hidden)
            result={'revision':hashlib.sha256(repr(key).encode()).hexdigest(),'entries':entries,
                'hiddenIdentityKeys':hidden,'projects':[p for p in index.get('projects',[]) if isinstance(p,dict) and not p.get('archived')],
                'counts':{'indexedFiles':sum(not a.get('libraryDeleted') for a in index.get('assets',[]) if isinstance(a,dict)),
                          'historyOutputs':len(job_entries),'originals':self.archive.public() if self.archive else {}}}
            self.cached_key,self.cached=key,result
            return result
