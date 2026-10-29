"""Explicit library moves and import registration through the existing index API.

Only associations change. Original bytes, URIs, asset IDs and generation history
remain intact; a redundant association is retired rather than deleting its row.
"""
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import time

from content_identity import verified_content
from gallery_sources import GallerySources
from library_snapshot import LibrarySnapshot


class LibraryOperations:
    def __init__(self, host, read_state, snapshot=None):
        self.host=host
        self.data=Path(host.data).parent
        self.read_state=read_state
        self.sources=GallerySources(self.data,read_state)
        self.projection=LibrarySnapshot(self.data,read_state)
        self.snapshot=snapshot or self.projection.snapshot

    @contextmanager
    def _locked(self):
        if not self.host.lock.acquire(blocking=False):
            raise ValueError('Espera a que termine la operación activa antes de organizar la biblioteca.')
        try:
            self.host.start()
            yield
        finally:
            self.host.lock.release()

    def _index(self):
        value=self.host.exchange('snapshot',{}).get('index')
        if not isinstance(value,dict):raise ValueError('No se pudo leer el índice de la biblioteca.')
        return value

    @staticmethod
    def _destination(index, project_id, allow_archived=False):
        if project_id is None:return
        if not isinstance(project_id,str) or not project_id:
            raise ValueError('Selecciona un proyecto de destino válido.')
        project=next((p for p in index.get('projects',[]) if p.get('id')==project_id),None)
        if project is None:raise ValueError('El proyecto de destino ya no existe.')
        if project.get('archived') and not allow_archived:
            raise ValueError('Confirma el proyecto archivado como destino.')

    def _content(self, asset, size=None):
        paths=list(dict.fromkeys(self.sources._asset_paths(asset)))
        if size is not None:
            try:
                if not any(path.stat().st_size==size for path in paths):return None
            except OSError:return None
        identities={identity for path in paths if (identity:=verified_content(path)) is not None}
        return next(iter(identities)) if len(identities)==1 else None

    def _group(self, entry, assets, state):
        seeds={m.get('assetId') for m in entry.get('memberships',[]) if m.get('assetId')}
        if entry.get('assetId'):seeds.add(entry['assetId'])
        seeds.update(key[6:] for key in entry.get('identityKeys',[]) if key.startswith('asset:'))
        uris={uri for uri in (entry.get('uri'),entry.get('localUri')) if uri}
        uris.update(a['uri'] for a in assets if a['id'] in seeds)
        group=[a for a in assets if not a.get('libraryDeleted') and (a['id'] in seeds or a.get('uri') in uris)]
        identities={value for a in group if (value:=self._content(a)) is not None}
        if entry.get('sourceJobId') is not None:
            try:
                _,_,path=self.sources.resolve(entry['sourceJobId'],entry.get('outputIndex',0),state=state,reload=False)
                if path is not None and (identity:=verified_content(path)) is not None:identities.add(identity)
            except (ValueError,OSError):pass
        if len(identities)>1:
            raise ValueError('Los originales de esta entrada no coinciden. Actualiza la biblioteca antes de moverla.')
        identity=next(iter(identities),None)
        if identity is not None:
            grouped={a['id'] for a in group}
            group.extend(a for a in assets if not a.get('libraryDeleted') and a['id'] not in grouped
                         and self._content(a,identity[0])==identity)
        if not group and any(a.get('libraryDeleted') and a.get('uri') in uris for a in assets):
            raise ValueError('Este archivo está retirado de la biblioteca; no se restaurará al moverlo.')
        return group

    @staticmethod
    def _legacy_asset(entry, state, project_id):
        job=next((j for j in state.get('jobs',[]) if j.get('id')==entry.get('sourceJobId')),None)
        if job is None:raise ValueError('El resultado seleccionado ya no existe.')
        ordinal=entry.get('outputIndex')
        outputs=job.get('outputs') or []
        if not isinstance(ordinal,int) or isinstance(ordinal,bool) or not 0<=ordinal<len(outputs):
            raise ValueError('El resultado seleccionado cambió.')
        output=outputs[ordinal]
        uri=output if isinstance(output,str) else output.get('url') if isinstance(output,dict) else None
        if not isinstance(uri,str) or not uri:raise ValueError('El resultado seleccionado no tiene un original válido.')
        identifier='ast-library-'+hashlib.sha256(json.dumps([job['id'],ordinal,uri],ensure_ascii=False).encode()).hexdigest()[:32]
        return {'id':identifier,'uri':uri,'type':job.get('modality') if job.get('modality') in {'IMAGE','VIDEO','AUDIO'} else 'OTHER',
                'role':'OUTPUT','source':'GENERATED','displayName':job.get('name') or job.get('modelId') or 'Resultado',
                'projectId':project_id,'createdAt':int(job.get('startedAt') or time.time()*1000),
                'generationId':entry.get('memberships',[{}])[0].get('generationId')}

    @staticmethod
    def _move_row(asset, destination, index, retire=False):
        value=dict(asset,projectId=destination)
        if retire:value['libraryDeleted']=True
        # Scene and shot IDs belong to one project. Do not leave a moved file
        # pointing into the old project's scene tree; all lineage stays intact.
        for field,collection in (('sceneId','scenes'),('shotId','shots')):
            identifier=asset.get(field)
            relation=next((row for row in index.get(collection,[]) if row.get('id')==identifier),None)
            if relation is not None and relation.get('projectId')!=destination:value[field]=None
        return value

    @staticmethod
    def _applied(expected, actual):
        # The Kotlin serializer may fill omitted defaults. Every field we sent
        # must nevertheless be present with exactly the requested value.
        return actual is not None and all(actual.get(key)==value for key,value in expected.items())

    def command(self, args):
        if not isinstance(args,dict) or args.get('op')!='move':
            raise ValueError('Operación de biblioteca no admitida.')
        identifiers=args.get('entryIds')
        if (not isinstance(identifiers,list) or not identifiers or len(identifiers)>1000
                or any(not isinstance(value,str) or not value for value in identifiers)):
            raise ValueError('Selecciona entre uno y mil archivos de la biblioteca.')
        identifiers=list(dict.fromkeys(identifiers))
        if 'projectId' not in args:raise ValueError('Falta el proyecto de destino.')
        destination=args['projectId'];scoped='sourceProjectId' in args;origin=args.get('sourceProjectId')
        with self._locked():
            index=self._index()
            self._destination(index,destination,args.get('allowArchivedProject') is True)
            known={p['id'] for p in index.get('projects',[])}
            if scoped and origin is not None and (not isinstance(origin,str) or origin not in known):
                raise ValueError('El proyecto de origen ya no existe.')
            state=self.read_state() or {}
            self.sources._reload()
            projected={entry['id']:entry for entry in self.snapshot()['entries']}
            assets=[a for a in index.get('assets',[]) if isinstance(a,dict)]
            changes={};plans={};already=[];merged=set();merge_proofs={}
            for identifier in identifiers:
                entry=projected.get(identifier)
                if entry is None:raise ValueError('Un archivo seleccionado cambió o está retirado. Actualiza la biblioteca.')
                group=self._group(entry,assets,state)
                if scoped and origin is None:
                    # Android's unfiled view also includes dangling project IDs.
                    targets=group if not any(a.get('projectId') in known for a in group) else []
                else:
                    targets=[a for a in group if not scoped or a.get('projectId')==origin]
                destination_rows=[a for a in group if a.get('projectId')==destination]
                if group and not targets:
                    if destination_rows:already.append(identifier);continue
                    raise ValueError('El archivo ya no pertenece al proyecto de origen. Actualiza la biblioteca.')
                if not group:
                    projects=entry.get('projectIds') or []
                    origin_matches=not scoped or (not any(p in known for p in projects) if origin is None else origin in projects)
                    if not origin_matches:raise ValueError('El resultado ya no pertenece al proyecto de origen.')
                    asset=self._legacy_asset(entry,state,destination)
                    if any(a['id']==asset['id'] for a in assets):raise ValueError('La identidad del resultado cambió. Actualiza la biblioteca.')
                    changes[asset['id']]=asset;plans[identifier]={asset['id']};assets.append(asset)
                    continue
                targets=[a for a in targets if a.get('projectId')!=destination]
                if not targets:already.append(identifier);continue
                canonical=destination_rows[0]['id'] if destination_rows else targets[0]['id']
                canonical_asset=next(a for a in group if a['id']==canonical)
                canonical_content=self._content(canonical_asset)
                required=set()
                for asset in targets:
                    retiring=(asset['id']!=canonical and canonical_content is not None
                              and self._content(asset,canonical_content[0])==canonical_content)
                    changes[asset['id']]=self._move_row(asset,destination,index,retiring)
                    required.add(asset['id'])
                    if retiring:
                        merged.add(asset['id'])
                        merge_proofs[asset['id']]=(asset,canonical_asset,canonical_content)
                plans[identifier]=required
            # Do not hide an association using a content proof invalidated while
            # the selection was being prepared. The originals themselves remain
            # untouched, including when this validation rejects the operation.
            for asset,canonical,identity in merge_proofs.values():
                if self._content(asset,identity[0])!=identity or self._content(canonical,identity[0])!=identity:
                    raise ValueError('Un original cambió durante el movimiento. Actualiza la biblioteca.')
            if changes:
                try:self.host.exchange('importAssets',{'assets':list(changes.values())})
                except Exception:pass
            # A lost acknowledgement must never cause another blind write or a
            # false success. Read back the existing native index, also on error.
            actual_index=self._index()
            actual={a['id']:a for a in actual_index.get('assets',[])}
            applied={identifier for identifier,value in changes.items() if self._applied(value,actual.get(identifier))}
            completed=[entry_id for entry_id,required in plans.items() if required<=applied]
            failures=[{'entryId':entry_id,'message':'No se pudo comprobar el movimiento completo de este archivo.'}
                      for entry_id in identifiers if entry_id not in completed and entry_id not in already]
            result={'status':'partial' if failures else 'completed','movedEntries':len(completed),
                    'alreadyAtDestination':len(already),'updatedAssets':len(applied),'mergedAssets':len(merged & applied),
                    'failures':failures,'snapshot':self.snapshot()}
            if failures:result['message']='Se movió solo una parte; revisa la biblioteca antes de volver a intentar.'
            return result

    def register_import(self, item, project_id, *, allow_archived=False):
        """item is a MediaImporter server receipt, never client-supplied metadata."""
        with self._locked():
            index=self._index()
            self._destination(index,project_id,allow_archived)
            self.sources._reload()
            uri=item.get('url');identity=(item.get('size'),item.get('sha256'))
            path=self.sources._local(uri)
            if path is None or verified_content(path)!=identity:
                raise ValueError('El archivo importado cambió. Vuelve a seleccionarlo.')
            assets=[a for a in index.get('assets',[]) if isinstance(a,dict)]
            matching=[a for a in assets if a.get('uri')==uri or self._content(a,identity[0])==identity]
            # Reimporting cannot move an existing file to another project or
            # silently restore a retired row. Keep the canonical metadata as-is.
            existing=next((a for a in matching if not a.get('libraryDeleted')),None)
            if existing is None and matching:existing=matching[0]
            if existing:
                return {'status':'retired' if existing.get('libraryDeleted') else 'exists',
                        'assetId':existing['id'],'projectId':existing.get('projectId')}
            mime=item.get('mime') or 'application/octet-stream'
            asset={'id':'asset_'+hashlib.sha256(uri.encode()).hexdigest()[:32],'uri':uri,
                   'type':'IMAGE' if mime.startswith('image/') else 'VIDEO' if mime.startswith('video/') else 'AUDIO' if mime.startswith('audio/') else 'OTHER',
                   'role':'REFERENCE','source':'IMPORTED','displayName':item.get('name') or 'Archivo',
                   'mimeType':mime,'createdAt':int(time.time()*1000),'projectId':project_id}
            if any(a['id']==asset['id'] for a in assets):raise ValueError('La identidad del archivo ya está ocupada.')
            try:self.host.exchange('importAssets',{'assets':[asset]})
            except Exception:pass
            actual=self._index().get('assets',[])
            registered=next((a for a in actual if a.get('uri')==uri),None)
            if registered is None or not self._applied(asset,registered):
                raise ValueError('No se pudo confirmar el registro del archivo en la biblioteca.')
            return {'status':'created','assetId':registered['id'],'projectId':registered.get('projectId')}
