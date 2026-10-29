"""Read-only file projection of the shared asset index and generation history."""
import hashlib
import json
from pathlib import Path
import threading

from gallery_sources import GallerySources
from content_identity import verified_content


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

    @staticmethod
    def _origin(asset):
        # Match Android LibraryOrigin: a generated/shared file keeps that origin
        # even when it also has a parent. Project membership is independent.
        source=asset.get('source') or 'IMPORTED'
        return source if source in {'GENERATED','SHARED'} else 'EDITED' if asset.get('parentAssetId') is not None else 'IMPORTED'

    @classmethod
    def _membership(cls, asset, assignment_source='asset'):
        member={key:asset.get(key) for key in ('projectId','sceneId','shotId','parentAssetId','mimeType')}
        member.update(source=asset.get('source') or 'IMPORTED',role=asset.get('role') or 'OTHER',
                      originCategory=cls._origin(asset),assignmentSource=assignment_source,
                      characterIds=list(asset.get('characterIds') or []),tags=list(asset.get('tags') or []))
        for key in ('assetId','generationId','sourceJobId'):
            if asset.get(key) is not None:member[key]=asset[key]
        return member

    @staticmethod
    def _classify(entry, members, known_projects, explicit):
        project_ids=list(dict.fromkeys(member['projectId'] for member in members if member.get('projectId') is not None))
        entry.update(memberships=members,projectIds=project_ids,
                     knownProjectIds=[value for value in project_ids if value in known_projects],
                     projectAssignmentExplicit=explicit)
        # Retain the scalar for old consumers, with the complete assignment above.
        entry['projectId']=project_ids[0] if project_ids else None
        if members:
            for key in ('role','originCategory','sceneId','shotId','parentAssetId','characterIds','tags'):
                entry[key]=members[0].get(key)

    def _local(self, asset):
        paths=self.sources._asset_paths(asset)
        return paths[0] if paths else None

    def preview_state(self):
        with self.lock:
            return {'jobs':[{'id':a['id'],'modality':self.modality(a),'outputs':[a['uri']],
                             'providerId':'local_media','name':a.get('displayName','Archivo'),
                             'status':'COMPLETED'} for a in self._index().get('assets',[])
                            if isinstance(a,dict) and isinstance(a.get('id'),str) and isinstance(a.get('uri'),str)]}

    def _content_keys(self, index):
        # Most originals have different byte counts. Hash only candidate groups
        # and retain every project membership when two paths contain one file.
        sizes={};assets={};asset_paths={}
        for asset in index.get('assets',[]):
            if not isinstance(asset,dict) or asset.get('libraryDeleted'):continue
            local=self._local(asset)
            if local is None:continue
            try:size=local.stat().st_size
            except OSError:continue
            sizes.setdefault(size,set()).add(local)
            assets.setdefault(local,[]).append(asset.get('id'))
            asset_paths[asset.get('id')]=list(dict.fromkeys(self.sources._asset_paths(asset)))
        result={}
        for paths in sizes.values():
            if len(paths)<2:continue
            for path in paths:
                identity=verified_content(path)
                if identity:
                    for identifier in assets[path]:
                        candidates=asset_paths[identifier]
                        if len(candidates)>1 and {verified_content(candidate) for candidate in candidates}!={identity}:continue
                        result[identifier]=f'content:{identity[0]}:{identity[1]}'
        return result

    def snapshot(self):
        with self.lock:
            state=self.read_state() or {};index=self._index()
            self.sources._reload()
            content_keys=self._content_keys(index)
            jobs=[j for j in state.get('jobs',[]) if isinstance(j,dict) and isinstance(j.get('id'),str)]
            signature=json.dumps([{key:j.get(key) for key in ('id','status','outputs','projectId','name','modelId','providerId',
                                 'modality','role','startedAt')} | {'parameterProjectId':(j.get('parameters') or {}).get('__project_id')}
                                 for j in jobs],sort_keys=True)
            key=(self.index_key,self._stamp(self.data/'remote-media-cache.json'),
                 self._stamp(self.data/'media-aliases.json'),self._stamp(self.data/'originals-protection.json'),signature,
                 tuple(sorted(content_keys.items())))
            if key==self.cached_key and self.cached is not None:return self.cached
            self.sources._reload()
            projects=[p for p in index.get('projects',[]) if isinstance(p,dict) and isinstance(p.get('id'),str)]
            known_projects={p['id'] for p in projects}
            generations=[g for g in index.get('generations',[]) if isinstance(g,dict)]
            generation_by_id={g['id']:g for g in generations if isinstance(g.get('id'),str)}
            # Android's LibraryProjectIndex uses the last revision for this job.
            generation_by_job={g['nexusJobId']:g for g in generations if isinstance(g.get('nexusJobId'),str)}
            protected={}
            if self.archive:
                with self.archive.lock:
                    protected={k:dict(v) for k,v in self.archive.records.items() if v.get('state')=='archived'}
            path_keys={};uri_keys={};identity_jobs={};job_entries=[]
            for job in jobs:
                for ordinal,output in enumerate(job.get('outputs') or []):
                    uri=output if isinstance(output,str) else output.get('url','') if isinstance(output,dict) else ''
                    if not uri:continue
                    identifier=f"job:{job['id']}:{ordinal}";keys=[identifier]
                    uri_keys.setdefault(uri,set()).add(identifier)
                    if isinstance(output,dict) and isinstance(output.get('localUri'),str):
                        uri_keys.setdefault(output['localUri'],set()).add(identifier)
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
                    for value in keys:identity_jobs.setdefault(value,set()).add(identifier)
                    job_entries.append({'id':identifier,'sourceJobId':job['id'],'outputIndex':ordinal,
                        'displayName':job.get('name') or job.get('modelId') or 'Resultado',
                        'modality':job.get('modality','CHAT'),'uri':uri,'localUri':local_uri,
                        'mimeType':record.get('mimeType') if record else None,
                        'sizeBytes':record.get('sizeBytes') if record else None,
                        'createdAt':job.get('startedAt',0),'projectId':job.get('projectId'),
                        'providerId':job.get('providerId'),'modelId':job.get('modelId'),
                        'status':job.get('status'),'source':'GENERATED','identityKeys':keys})
            generation_by_asset={}
            for generation in generations:
                for asset_id in generation.get('outputAssetIds') or []:
                    generation_by_asset.setdefault(asset_id,generation)
            entries=[];hidden=[];visible_keys=set();hidden_jobs=set();job_members={}
            for asset in index.get('assets',[]):
                if not isinstance(asset,dict) or not isinstance(asset.get('id'),str) or not isinstance(asset.get('uri'),str):continue
                local=self._local(asset);local_uri='/media/'+local.name if local and local.parent==self.media else None
                keys=['asset:'+asset['id']]
                if asset['id'] in content_keys:keys.append(content_keys[asset['id']])
                if local_uri:keys.append('local:'+local_uri)
                linked=set(uri_keys.get(asset['uri'],set())) | (path_keys.get(str(local),set()) if local else set())
                for value in keys:linked.update(identity_jobs.get(value,set()))
                keys.extend(sorted(linked))
                if asset.get('libraryDeleted'):
                    hidden.append('asset:'+asset['id']);hidden_jobs.update(linked);continue
                visible_keys.update(keys)
                generation=generation_by_id.get(asset.get('generationId')) or generation_by_asset.get(asset['id'],{})
                member=self._membership(dict(asset,assetId=asset['id'],generationId=generation.get('id')))
                for identifier in linked:job_members.setdefault(identifier,[]).append(member)
                entry={'id':'asset:'+asset['id'],'assetId':asset['id'],'generationId':generation.get('id'),
                    'displayName':asset.get('displayName') or 'Archivo','modality':self.modality(asset),
                    'uri':asset['uri'],'localUri':local_uri,'createdAt':asset.get('createdAt',0),
                    'mimeType':asset.get('mimeType'),'sizeBytes':asset.get('sizeBytes'),
                    'projectId':asset.get('projectId'),'providerId':generation.get('providerId') or 'local_media',
                    'modelId':generation.get('modelId') or 'shared-library','status':'COMPLETED',
                    'source':asset.get('source') or 'IMPORTED','identityKeys':keys}
                self._classify(entry,[member],known_projects,True)
                if len(linked)==1:
                    link=next(iter(linked));job_key,ordinal=link.rsplit(':',1)
                    entry.update(sourceJobId=job_key.removeprefix('job:'),outputIndex=int(ordinal))
                entries.append(entry)
            hidden.extend(sorted(hidden_jobs-visible_keys))
            jobs_by_id={job['id']:job for job in jobs}
            for entry in job_entries:
                members=job_members.get(entry['id'])
                if members:
                    self._classify(entry,members,known_projects,True)
                else:
                    job=jobs_by_id[entry['sourceJobId']];generation=generation_by_job.get(job['id'],{})
                    parameters=job.get('parameters') or {}
                    project_id=parameters.get('__project_id')
                    if project_id is None:project_id=generation.get('projectId')
                    if project_id is None:project_id=job.get('projectId')
                    member=self._membership({'projectId':project_id,'source':'GENERATED','role':job.get('role') or 'OUTPUT',
                        'sourceJobId':job['id'],'generationId':generation.get('id'),'shotId':generation.get('shotId')},'legacy-job')
                    self._classify(entry,[member],known_projects,False)
            entries.extend(j for j in job_entries if j['id'] not in hidden)
            result={'revision':hashlib.sha256(repr(key).encode()).hexdigest(),'entries':entries,
                'hiddenIdentityKeys':hidden,'projects':projects,
                # Android folders are projects, never inferred path hierarchies.
                # The UI counts deduplicated visible files before hiding empty
                # archived folders; keep their metadata available here.
                'folders':[{'id':p['id'],'projectId':p['id'],'name':p.get('name') or '',
                            'archived':bool(p.get('archived')),'kind':'project','parentId':None} for p in projects],
                'scenes':[s for s in index.get('scenes',[]) if isinstance(s,dict)],
                'shots':[s for s in index.get('shots',[]) if isinstance(s,dict)],
                'counts':{'indexedFiles':sum(not a.get('libraryDeleted') for a in index.get('assets',[]) if isinstance(a,dict)),
                          'historyOutputs':len(job_entries),'originals':self.archive.public() if self.archive else {}}}
            self.cached_key,self.cached=key,result
            return result
