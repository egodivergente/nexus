"""Stage the authorized S25 agent/profile migration. Originals and Windows jobs survive.

Credentials are read through the temporary, matching-signed instrumentation and
written directly to Windows DPAPI. No secret is printed or put in a source file.
Run --apply only when replacing the desktop host, after the staged QA passes.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.parse
import uuid

root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'desktop'))
import local_host
from native_operations import atomic_json, WHISPER_SHA
parser=argparse.ArgumentParser()
parser.add_argument('--serial',default='100.119.88.105:5555')
parser.add_argument('--apply',action='store_true')
args=parser.parse_args()
adb=[str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'),'-s',args.serial]
package='com.vidadomu.nexus.agent'
data=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop'
out=root/'outputs/desktop-agent-parity/migration'
stage=out/'stage'
backup=out/'windows-before'
stage.mkdir(parents=True,exist_ok=True)
backup.mkdir(parents=True,exist_ok=True)

if args.apply:
    final_backup=out/('windows-before-apply-'+str(int(time.time())))
    for relative in ['state.json','credentials.dpapi','assistant/index.json','assistant/settings.json','assistant/credentials.dpapi','mcp-servers.json','mcp-credentials.dpapi']:
        source=stage/relative
        if source.exists():
            target=data/relative
            target.parent.mkdir(parents=True,exist_ok=True)
            if target.exists():
                saved=final_backup/relative;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,saved)
            if relative=='state.json' and target.exists():
                staged=json.loads(source.read_text('utf-8'))
                current=json.loads(target.read_text('utf-8'))
                # Keep work that completed or was edited while the migration was being staged.
                merged={**current,**{k:v for k,v in staged.items() if k in {'currency','usdToEurRate','exchangeRateUpdated','interfaceTheme'}}}
                merged['jobs']=local_host.merge_jobs(staged.get('jobs',[]),current.get('jobs',[]))
                atomic_json(target,merged)
            elif relative=='assistant/index.json' and target.exists():
                staged=json.loads(source.read_text('utf-8'));current=json.loads(target.read_text('utf-8'))
                for key,rows in current.items():
                    if not isinstance(rows,list):continue
                    if key=='projectSessions':
                        signatures={json.dumps(row,sort_keys=True) for row in staged.get(key,[])}
                        staged[key]=staged.get(key,[])+[row for row in rows if json.dumps(row,sort_keys=True) not in signatures]
                    else:
                        by_id={row.get('id'):row for row in staged.get(key,[])}
                        original=json.loads((backup/relative).read_text('utf-8')) if (backup/relative).exists() else {}
                        before={row.get('id'):row for row in original.get(key,[])}
                        for row in rows:
                            if row.get('id') not in by_id or row!=before.get(row.get('id')):by_id[row.get('id')]=row
                        staged[key]=list(by_id.values())
                atomic_json(target,staged)
            else:shutil.copy2(source,target)
    conversations=stage/'assistant/conversations'
    if conversations.exists():shutil.copytree(conversations,data/'assistant/conversations',dirs_exist_ok=True)
    print('Applied verified S25 agent migration; existing media and other Windows files preserved.')
    sys.exit(0)

def read(relative):
    return subprocess.check_output(adb+['exec-out','run-as',package,'cat',relative],stderr=subprocess.PIPE)

for relative in ['state.json','credentials.dpapi','assistant/index.json','assistant/settings.json','assistant/credentials.dpapi']:
    source=data/relative
    if source.exists():
        for directory in [backup,stage]:
            target=directory/relative;target.parent.mkdir(parents=True,exist_ok=True)
            if directory==stage or not target.exists():shutil.copy2(source,target)

payload=json.loads(read('files/desktop-key-transfer.json'))
mcp_path=data/'mcp-servers.json'
mcp=json.loads(mcp_path.read_text('utf-8')) if mcp_path.exists() else {}
mcp_secrets=local_host.windows_keys(data/'mcp-credentials.dpapi')
for identifier, connection in payload.get('mcp',{}).items():
    mcp[identifier]={'url':connection['url']}
    if connection.get('token'):mcp_secrets[identifier]=connection['token']
atomic_json(stage/'mcp-servers.json',mcp)
local_host.write_secret(stage/'mcp-credentials.dpapi',mcp_secrets)
providers=local_host.windows_keys(data/'credentials.dpapi')
providers.update({key:value for key,value in payload['providers'].items() if value})
local_host.write_secret(stage/'credentials.dpapi',providers)
assistant=local_host.windows_keys(data/'assistant/credentials.dpapi')
assistant.update({key:value for key,value in payload['assistant'].items() if value})
(stage/'assistant').mkdir(exist_ok=True)
local_host.write_secret(stage/'assistant/credentials.dpapi',assistant)
settings_path=stage/'assistant/settings.json'
settings=json.loads(settings_path.read_text('utf-8')) if settings_path.exists() else {}
source=payload['settings']
for origin,target in [('backend','backend'),('model_id','modelId'),('region','region'),('max_tokens','maxTokens'),('send_images','sendImages'),('excluded_families','excludedFamilies'),('active_project','projectId'),('llm_turn_usd','perTurnUsd'),('llm_daily_usd','dailyUsd')]:
    if origin in source:settings[target]=source[origin]
settings.update(mode='AGENT')
settings['endpoint']='http://127.0.0.1:18787' if source.get('backend')=='proxy' and source.get('model_id','').startswith('openai.') else source.get('endpoint','')
atomic_json(settings_path,settings)

phone=json.loads(read('files/assistant/index.json'))
(out/'phone-index-private.json').write_text(json.dumps(phone),encoding='utf-8')
desktop_path=stage/'assistant/index.json'
desktop=json.loads(desktop_path.read_text('utf-8')) if desktop_path.exists() else {}
media=data/'media'
media.mkdir(exist_ok=True)
mapping_path=out/'uri-mapping-private.json'
mapping=json.loads(mapping_path.read_text('utf-8')) if mapping_path.exists() else {}
missing=[]
for asset in phone.get('assets',[]):
    uri=asset['uri']
    if not uri.startswith('file:'):continue
    if uri in mapping and (data/mapping[uri].lstrip('/')).is_file():continue
    path=urllib.parse.unquote(urllib.parse.urlsplit(uri).path)
    destination=media/(uuid.uuid4().hex+Path(path).suffix.lower())
    result=subprocess.run(adb+['exec-out','run-as',package,'cat',path],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode or not result.stdout:
        missing.append({'asset_id':asset['id'],'reason':'Source file not readable; original URI retained'})
        continue
    destination.write_bytes(result.stdout)
    mapping[uri]='/media/'+destination.name
    atomic_json(mapping_path,mapping)

def remap(value):
    if isinstance(value,str):return mapping.get(value,value)
    if isinstance(value,list):return [remap(item) for item in value]
    if isinstance(value,dict):return {key:remap(item) for key,item in value.items()}
    return value

phone=remap(phone)
chats=stage/'assistant/conversations'
chats.mkdir(exist_ok=True)
names=subprocess.check_output(adb+['shell','run-as',package,'ls','files/assistant/conversations'],stderr=subprocess.PIPE).decode().splitlines()
for name in names:
    if not name.endswith('.json') or Path(name).name!=name:continue
    value=remap(json.loads(read('files/assistant/conversations/'+name)))
    atomic_json(chats/name,value)
try:atomic_json(chats/'s25-current.json',remap(json.loads(read('files/assistant/chat-v1.json'))))
except (ValueError,subprocess.CalledProcessError):pass
for key,rows in phone.items():
    if not isinstance(rows,list):continue
    existing=desktop.get(key,[])
    if key=='projectSessions':
        signatures={json.dumps(row,sort_keys=True) for row in existing}
        desktop[key]=existing+[row for row in rows if json.dumps(row,sort_keys=True) not in signatures]
    else:
        by_id={row.get('id'):row for row in existing}
        by_id.update({row.get('id'):row for row in rows})
        desktop[key]=list(by_id.values())
atomic_json(desktop_path,desktop)

state_path=stage/'state.json'
state=json.loads(state_path.read_text('utf-8')) if state_path.exists() else {}
existing={job['id'] for job in state.get('jobs',[])}
asset_by_id={a['id']:a for a in phone.get('assets',[])}
imported=[]
for generation in phone.get('generations',[]):
    identifier=generation.get('nexusJobId') or generation['id']
    if identifier in existing:continue
    outputs=[asset_by_id[id]['uri'] for id in generation.get('outputAssetIds',[]) if id in asset_by_id]
    imported.append({'id':identifier,'providerId':generation['providerId'],'modelId':generation['modelId'],'prompt':generation.get('prompt',''),
        'modality':generation.get('modality','IMAGE'),'parameters':generation.get('params',{}),'status':{'SUBMITTED':'PROCESSING'}.get(generation.get('status'),generation.get('status','COMPLETED')),
        'outputs':outputs,'startedAt':generation.get('createdAt',0),'remoteId':generation.get('providerJobId'),
        'actualCostUsd':generation.get('actualCostUsd'),'projectId':generation.get('projectId'),'importedFromAndroid':True})
state['jobs']=sorted(state.get('jobs',[])+imported,key=lambda job:job.get('startedAt',0),reverse=True)
currency=payload.get('currency',{})
if isinstance(currency.get('rate'),(int,float)) and currency['rate']>0:
    state.update(currency='USD' if currency.get('preference')=='USD' else 'EUR',usdToEurRate=currency['rate'],exchangeRateUpdated='Tasa guardada del S25')
state['interfaceTheme']='aurora'
atomic_json(state_path,state)

# Reuse the identical downloaded Whisper weights instead of downloading them twice.
whisper=data/'whisper/ggml-base.bin'
whisper.parent.mkdir(exist_ok=True)
if not whisper.exists():
    result=subprocess.run(adb+['exec-out','run-as',package,'cat','files/whisper_models/ggml-base.bin'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode==0 and hashlib.sha256(result.stdout).hexdigest()==WHISPER_SHA:
        whisper.write_bytes(result.stdout)
if whisper.exists():
    with whisper.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    if digest==WHISPER_SHA:
        atomic_json(whisper.with_suffix('.verified.json'),{'sha256':digest,'size':whisper.stat().st_size,'mtime_ns':whisper.stat().st_mtime_ns})

assert local_host.windows_keys(stage/'credentials.dpapi')==providers
assert local_host.windows_keys(stage/'assistant/credentials.dpapi')==assistant
report={'sourcePackage':package,'sourceVersion':'1.9.2-agent-preview','providers':list(payload['providers']),
    'assistantModel':settings['modelId'],'projects':len(phone.get('projects',[])),'assets':len(phone.get('assets',[])),
    'copiedFiles':len(mapping),'unavailableFiles':missing,'generationsImported':len(imported),'windowsJobsPreserved':len(existing),
    'credentialsVerified':True,'paidGenerations':0,'whisperVerified':whisper.with_suffix('.verified.json').exists(), 'chats':len(list(chats.glob('*.json')))}
atomic_json(out/'report-private.json',report)
print(json.dumps({key:value for key,value in report.items() if key!='unavailableFiles'}))
