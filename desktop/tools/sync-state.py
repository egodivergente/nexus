import os, json, sqlite3, tarfile
from pathlib import Path
import xml.etree.ElementTree as ET

out = Path('outputs/desktop-20261001').resolve()
data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
data.mkdir(parents=True, exist_ok=True)
(data / 'media').mkdir(exist_ok=True)

archive_path = out / 'private-mobile-backup.tar'
dbdir = out / 'db-source'
prefs = {}

if archive_path.exists():
    with tarfile.open(archive_path) as t:
        for member in t.getmembers():
            if member.name == 'shared_prefs/catalog_customization_v1.xml':
                f = t.extractfile(member)
                if f:
                    for item in ET.fromstring(f.read()):
                        if item.tag == 'set':
                            prefs[item.get('name')] = [x.text for x in item]

jobs = []
for file in dbdir.glob('*.db'):
    con = sqlite3.connect(file)
    con.row_factory = sqlite3.Row
    cur = con.execute("select name from sqlite_master where type='table' and name='generations'")
    if not cur.fetchone():
        con.close()
        continue
    for row in con.execute('select * from generations order by started_at desc'):
        j = dict(row)
        params = json.loads(j.get('parameters_json') or '{}')
        outputs = [{'url': o['url'], 'type': o['type']} for o in con.execute('select * from outputs where job_id=?', (j['id'],))]
        jobs.append({
            'id': j['id'],
            'providerId': j['provider_id'],
            'modelId': j['model_id'],
            'prompt': j['prompt'],
            'modality': j['modality'],
            'parameters': params,
            'status': j['status'],
            'outputs': outputs,
            'startedAt': j['started_at'],
            'remoteId': j['provider_job_id'],
            'importedFromAndroid': True,
            'batchId': params.get('__comparison_id')
        })
    con.close()

statefile = data / 'state.json'
state = json.loads(statefile.read_text(encoding='utf-8-sig')) if statefile.exists() else {}
existing = state.get('jobs', [])
ids = {x['id'] for x in existing}
state['jobs'] = existing + [j for j in jobs if j['id'] not in ids]
if not state.get('favorites'):
    state['favorites'] = [x.split(':', 1)[-1] for k, v in prefs.items() if 'favorite' in k for x in v if x]
if not state.get('hidden'):
    state['hidden'] = [x.split(':', 1)[-1] for k, v in prefs.items() if 'hidden' in k for x in v if x]

statefile.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Imported {len(jobs)} jobs and saved state to {statefile}')
