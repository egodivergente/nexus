"""Authorized personal-profile migration. Credentials travel in memory to Windows DPAPI, never stdout."""
from pathlib import Path
import subprocess,json,tarfile,io,sqlite3,os,hashlib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
out=root.parent/'outputs/desktop-20261001';out.mkdir(exist_ok=True)
adb=['adb','-s','192.168.1.134:5555']
def cmd(*args):return subprocess.check_output(adb+list(args),stderr=subprocess.PIPE)
assert cmd('shell','getprop','ro.serialno').decode().strip()=='R5CXC3J630Y'
assert cmd('shell','am','get-current-user').decode().strip()=='0'
raw=cmd('exec-out','run-as','com.vidadomu.nexus','cat','files/desktop-key-transfer.json')
values=json.loads(raw)
assert all(k in ['atlas_cloud','runway','crun'] and isinstance(v,str) for k,v in values.items())
subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-File',str(root/'tools/protect-keys.ps1')],input=raw,capture_output=True,check=True)
data=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop'
status=json.loads((data/'migration-status.json').read_text(encoding='utf-8-sig'))
assert set(status['imported'])>=set(values)
raw=b'';values.clear()
cmd('shell','run-as','com.vidadomu.nexus','rm','files/desktop-key-transfer.json')
archive=cmd('exec-out','run-as','com.vidadomu.nexus','tar','-cf','-','databases','shared_prefs')
(out/'private-mobile-backup.tar').write_bytes(archive)
dbdir=out/'db-source';dbdir.mkdir(exist_ok=True)
prefs={}
with tarfile.open(fileobj=io.BytesIO(archive)) as t:
 for member in t.getmembers():
  if member.name.startswith('databases/') and member.isfile():
   (dbdir/Path(member.name).name).write_bytes(t.extractfile(member).read())
  if member.name=='shared_prefs/catalog_customization_v1.xml':
   for item in ET.fromstring(t.extractfile(member).read()):
    if item.tag=='set':prefs[item.get('name')]=[x.text for x in item]
jobs=[]
for file in dbdir.glob('*.db'):
 con=sqlite3.connect(file);con.row_factory=sqlite3.Row
 if not con.execute("select name from sqlite_master where name='generations'").fetchone():con.close();continue
 assert con.execute('pragma integrity_check').fetchone()[0]=='ok'
 for row in con.execute('select * from generations order by started_at desc'):
  j=dict(row);params=json.loads(j.get('parameters_json') or '{}')
  outputs=[{'url':o['url'],'type':o['type']} for o in con.execute('select * from outputs where job_id=?',(j['id'],))]
  jobs.append({'id':j['id'],'providerId':j['provider_id'],'modelId':j['model_id'],'prompt':j['prompt'],'modality':j['modality'],'parameters':params,'status':j['status'],'outputs':outputs,'startedAt':j['started_at'],'remoteId':j['provider_job_id'],'importedFromAndroid':True,'batchId':params.get('__comparison_id')})
 con.close()
statefile=data/'state.json'
state=json.loads(statefile.read_text(encoding='utf-8-sig')) if statefile.exists() else {}
existing=state.get('jobs',[]);ids={x['id'] for x in existing};state['jobs']=existing+[j for j in jobs if j['id'] not in ids]
if not state.get('favorites'):
 state['favorites']=[x.split(':',1)[-1] for k,v in prefs.items() if 'favorite' in k for x in v]
if not state.get('hidden'):
 state['hidden']=[x.split(':',1)[-1] for k,v in prefs.items() if 'hidden' in k for x in v]
statefile.write_text(json.dumps(state,ensure_ascii=False),encoding='utf-8')
apkpath=cmd('shell','pm','path','com.vidadomu.nexus').decode().splitlines()[0].removeprefix('package:')
apk=cmd('exec-out','cat',apkpath)
(out/'Nexus-S25-source.apk').write_bytes(apk)
report={'device':'SM-S938B','profile':0,'providers':status['imported'],'jobsImported':len(jobs),'outputsImported':sum(len(j['outputs']) for j in jobs),'sourceApkSha256':hashlib.sha256(apk).hexdigest(),'keysProtection':'DPAPI CurrentUser','generationCalls':0}
(out/'migration.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
