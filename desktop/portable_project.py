"""Self-contained project archives with verified files and resumable, additive imports."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import uuid
import zipfile

from native_operations import atomic_json, resolve_media
from corte_render import local_path

COLLECTIONS=('scenes','shots','characters','assets','generations')
RELATIONS={'sceneId','shotId','characterIds','referenceAssetIds','outputAssetIds','parentAssetId','generationId','parentGenerationId'}
TOKEN='nexus-bundle:'


def digest(path):
    with Path(path).open('rb') as f:
        return hashlib.file_digest(f,'sha256').hexdigest()


def rewrite(value, replacements):
    if isinstance(value,str):return replacements.get(value,value)
    if isinstance(value,list):return [rewrite(v,replacements) for v in value]
    if isinstance(value,dict):return {replacements.get(k,k):rewrite(v,replacements) for k,v in value.items()}
    return value


class PortableProject:
    def __init__(self,host,allow_network=True):
        self.host=host;self.data=host.data.parent;self.allow_network=allow_network

    def command(self,op,args):
        if not self.host.lock.acquire(blocking=False):raise ValueError('Espera a que termine el turno antes de transportar un proyecto.')
        try:
            self.host.start()
            # A render uses immutable snapshots. No edits are allowed while capturing an archive.
            with self.host.corte.lock:
                if op=='export':return self.export(args['projectId'])
                if op=='import':return self.import_archive(args['id'],args['request_id'])
                raise ValueError('Operación portable desconocida.')
        finally:self.host.lock.release()

    def export(self,project_id):
        index=self.host.exchange('snapshot',{})['index']
        project=next((p for p in index['projects'] if p['id']==project_id),None)
        if not project:raise ValueError('Proyecto desconocido.')
        rows={r['id']:(collection,r) for collection in COLLECTIONS for r in index.get(collection,[])}
        selected={identifier for identifier,(_,r) in rows.items() if r.get('projectId')==project_id}
        # Include ancestry and external references rather than silently losing relationships.
        while True:
            refs=set()
            for identifier in selected:
                for key,value in rows[identifier][1].items():
                    if key in RELATIONS:refs.update(value if isinstance(value,list) else [value])
            added=(refs & rows.keys())-selected
            if not added:break
            selected.update(added)
        bundle={'schemaVersion':1,'projects':[project],**{c:[{**r,'projectId':project_id} for identifier,(kind,r) in rows.items() if kind==c and identifier in selected] for c in COLLECTIONS},'projectSessions':[]}
        corte_file=self.data/'projects'/project_id/'edit.json'
        corte=self.host.corte.exchange('export_document',{'project_id':project_id})['document'] if corte_file.exists() else None
        files={};uris={};origin={'projectId':project_id,'entities':{identifier:{'projectId':rows[identifier][1].get('projectId'),'uri':rows[identifier][1].get('uri'),'nexusJobId':rows[identifier][1].get('nexusJobId'),'providerJobId':rows[identifier][1].get('providerJobId')} for identifier in selected}}
        def include(uri,path):
            path=Path(path)
            if not path.is_file():raise ValueError('Falta un medio. Revincúlalo antes de exportar el proyecto.')
            ext=path.suffix.lower()
            if not re.fullmatch(r'\.[a-z0-9]{1,8}',ext):raise ValueError('Extensión de archivo incompatible.')
            name='media/'+digest(path)+ext
            files[name]=path;uris[uri]=TOKEN+name
        for asset in bundle['assets']:
            if not self.allow_network and asset['uri'].startswith(('http:','https:')):raise ValueError('La prueba aislada no descarga archivos externos.')
            _,path=self.host.native.asset(asset['id'],[{**asset,'libraryDeleted':False}])
            include(asset['uri'],path)
        if corte:
            for media in corte['project']['media']:include(media['uri'],local_path(media['uri']))
        payloads={'metadata/index.json':rewrite(bundle,uris),'metadata/origin.json':origin}
        if corte:payloads['metadata/corte.json']=rewrite(corte,uris)
        identifier=uuid.uuid4().hex+'.zip';directory=self.data/'media';directory.mkdir(parents=True,exist_ok=True)
        target=directory/identifier;temporary=target.with_suffix('.partial')
        manifest={'format':'nexus-portable-project','version':1,'files':{}}
        try:
            with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as archive:
                for name,path in files.items():
                    expected=digest(path);archive.write(path,name)
                    if digest(path)!=expected:raise ValueError('Un medio cambió durante la exportación. Repite la exportación.')
                    manifest['files'][name]={'sha256':expected,'size':path.stat().st_size}
                for name,value in payloads.items():
                    raw=json.dumps(value,ensure_ascii=False).encode('utf-8');archive.writestr(name,raw)
                    manifest['files'][name]={'sha256':hashlib.sha256(raw).hexdigest(),'size':len(raw)}
                archive.writestr('manifest.json',json.dumps(manifest))
            temporary.replace(target)
        finally:temporary.unlink(missing_ok=True)
        return {'url':'/media/'+identifier,'name':project['name']+'.nexus.zip','assets':len(bundle['assets']),'hasMontage':bool(corte),'sha256':digest(target)}

    def unpack(self,source,stage):
        with zipfile.ZipFile(source) as archive:
            entries=archive.infolist();names=[i.filename for i in entries]
            if len(names)>10000 or len(set(names))!=len(names) or 'manifest.json' not in names:raise ValueError('Archivo portable inválido o con entradas repetidas.')
            manifest_info=archive.getinfo('manifest.json')
            if manifest_info.file_size>4*1024*1024:raise ValueError('Manifiesto demasiado grande.')
            manifest=json.loads(archive.read(manifest_info))
            if manifest.get('format')!='nexus-portable-project' or manifest.get('version')!=1:raise ValueError('Formato de proyecto incompatible.')
            declared=manifest['files']
            if set(names)!=(set(declared)|{'manifest.json'}):raise ValueError('El archivo no coincide con el manifiesto.')
            total=sum(i.file_size for i in entries)
            if total>20*1024**3 or total>shutil.disk_usage(self.data).free//2:raise ValueError('No hay espacio suficiente para importar este proyecto.')
            for info in entries:
                name=info.filename
                if name=='manifest.json':continue
                if not (re.fullmatch(r'metadata/(index|corte|origin)\.json',name) or re.fullmatch(r'media/[a-f0-9]{64}\.[a-z0-9]{1,8}',name)):
                    raise ValueError('Ruta no admitida dentro del paquete.')
                if info.is_dir() or (info.external_attr>>16)&0o170000==0o120000:raise ValueError('El paquete contiene un enlace o directorio no admitido.')
                if info.file_size!=declared[name]['size']:raise ValueError('Tamaño de archivo incorrecto.')
                if name.startswith('metadata/') and info.file_size>20*1024*1024:raise ValueError('Metadatos demasiado grandes.')
                destination=stage/name;destination.parent.mkdir(parents=True,exist_ok=True)
                h=hashlib.sha256();count=0
                with archive.open(info) as reader,destination.open('wb') as writer:
                    while block:=reader.read(1024*1024):
                        count+=len(block)
                        if count>info.file_size:raise ValueError('Contenido expandido inválido.')
                        writer.write(block);h.update(block)
                if count!=info.file_size or h.hexdigest()!=declared[name]['sha256']:raise ValueError('La integridad del archivo no coincide con el manifiesto.')
            if 'metadata/index.json' not in declared:raise ValueError('El paquete no incluye el proyecto Nexus.')
            return manifest

    def import_archive(self,identifier,request_id):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,100}',request_id):raise ValueError('Identificador de importación inválido.')
        source=resolve_media(self.data,identifier)
        if source.suffix.lower()!='.zip':raise ValueError('Selecciona un proyecto portable ZIP.')
        transaction=self.data/'project-imports'/request_id;transaction.mkdir(parents=True,exist_ok=True)
        journal_file=transaction/'journal.json';fingerprint=digest(source)
        if journal_file.exists():
            journal=json.loads(journal_file.read_text('utf-8'))
            if journal['archiveSha256']!=fingerprint:raise ValueError('Este identificador pertenece a otra importación.')
            if journal['phase']=='COMPLETED':return journal['result']
        else:
            manifest=self.unpack(source,transaction/'verified')
            bundle=json.loads((transaction/'verified/metadata/index.json').read_text('utf-8'))
            if len(bundle.get('projects',[]))!=1:raise ValueError('El paquete debe contener un proyecto.')
            original_id=bundle['projects'][0]['id'];new_id='prj_'+uuid.uuid4().hex[:12]
            ids={original_id:new_id}
            for collection in COLLECTIONS:
                for row in bundle.get(collection,[]):
                    if row['id'] in ids:raise ValueError('Identidades repetidas en los metadatos.')
                    ids[row['id']]=collection.rstrip('s')+'_'+uuid.uuid4().hex[:12]
            targets={name:uuid.uuid4().hex+Path(name).suffix for name in manifest['files'] if name.startswith('media/')}
            uri_map={TOKEN+name:'/media/'+target for name,target in targets.items()}
            bundle=rewrite(bundle,{**ids,**uri_map});bundle['projectSessions']=[]
            for asset in bundle.get('assets',[]):
                if asset['uri'] not in uri_map.values():raise ValueError('El paquete contiene un activo sin medio incluido.')
            for record in bundle.get('generations',[]):
                record['nexusJobId']='imported_'+record['id'];record['providerJobId']=None
            document_path=transaction/'verified/metadata/corte.json'
            corte=json.loads(document_path.read_text('utf-8')) if document_path.exists() else None
            if corte:
                if corte['project']['id']!=original_id:raise ValueError('El montaje corresponde a otro proyecto.')
                for media in corte['project']['media']:
                    if media['uri'] not in uri_map:raise ValueError('El montaje contiene un medio sin incluir.')
                corte=rewrite(corte,{**ids,**{TOKEN+name:(self.data/'media'/target).resolve().as_uri() for name,target in targets.items()}})
                corte['receipts']={}
                self.host.corte.exchange('validate_document',{'project_id':new_id,'document':corte})
            # Validate index structure/relations with the shared core before publishing any files.
            self.host.exchange('validateProjectBundle',{'index':bundle})
            journal={'phase':'VERIFIED','archiveSha256':fingerprint,'projectId':new_id,'targets':targets,'hashes':{name:manifest['files'][name]['sha256'] for name in targets},'index':bundle,'document':corte}
            atomic_json(journal_file,journal)
        new_id=journal['projectId']
        current=self.host.exchange('snapshot',{})['index']
        if not any(p['id']==new_id for p in current['projects']):
            for name,target in journal['targets'].items():
                staged=transaction/'verified'/name;destination=self.data/'media'/target
                expected=journal['hashes'][name]
                if digest(staged)!=expected:raise ValueError('Cambió un archivo preparado para importar.')
                destination.parent.mkdir(parents=True,exist_ok=True)
                if destination.exists():
                    if digest(destination)!=expected:raise ValueError('El destino cambió durante una importación interrumpida.')
                else:
                    temporary=destination.with_name(destination.name+'.import-'+request_id)
                    with temporary.open('wb') as writer,staged.open('rb') as reader:
                        shutil.copyfileobj(reader,writer);writer.flush();os.fsync(writer.fileno())
                    if digest(temporary)!=expected:raise ValueError('No se pudo copiar el archivo íntegro.')
                    os.link(temporary,destination);temporary.unlink()
            if journal['document']:
                self.host.corte.exchange('import_document',{'project_id':new_id,'document':journal['document']})
            self.host.exchange('importProjectBundle',{'index':journal['index']})
        self.host.exchange('project',{'id':new_id});self.host.save_settings({'projectId':new_id})
        result={'projectId':new_id,'assets':len(journal['index'].get('assets',[])),'hasMontage':bool(journal['document'])}
        journal.update(phase='COMPLETED',result=result);atomic_json(journal_file,journal)
        return result
