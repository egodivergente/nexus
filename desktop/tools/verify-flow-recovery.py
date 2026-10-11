"""Read an already completed real Flow job through the recovery endpoint.

No prompt, quote, generate or process_asset call. Main application data is read-only.
"""
import hashlib
import http.client
import json
import os
from pathlib import Path
import sqlite3
import sys
import threading
import time
import secrets
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import flow_provider as flow
import local_host as host

root = Path(__file__).resolve().parents[2]
out = root / 'outputs/flow-ux-20261006/live-recovery'
out.mkdir(parents=True, exist_ok=True)
main_data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'

def queue_count():
    path = flow.RUNTIME / 'state/gflow.db'
    connection = sqlite3.connect(path.as_uri()+'?mode=ro', uri=True)
    try:
        return connection.execute('SELECT COUNT(*) FROM generation_queue').fetchone()[0]
    finally:
        connection.close()

connection = sqlite3.connect((main_data / 'flow-jobs.sqlite').as_uri()+'?mode=ro', uri=True)
try:
    owned = next((row for row in connection.execute('SELECT id,params,result FROM jobs')
        if json.loads(row[1]).get('model')=='nano2' and row[2]
        and json.loads(row[2]).get('status')=='completed'), None)
finally:
    connection.close()
if not owned:
    raise RuntimeError('No existing completed own-account image is available.')
source = json.loads(owned[2])
path = Path(source['path'])
original_digest = hashlib.sha256(path.read_bytes()).hexdigest()
protected = [main_data/'state.json', main_data/'credentials.dpapi',
             main_data/'assistant/settings.json', main_data/'assistant/credentials.dpapi']
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None
before = {str(path):digest(path) for path in protected}
native_before = queue_count()
data = out / ('isolated-'+str(time.time_ns()))
connection = flow.ledger(data)
with connection:
    # Only the isolated Nexus record is made incomplete; the real Flow job stays completed.
    connection.execute('INSERT INTO jobs VALUES (?,?,NULL)', (owned[0], owned[1]))
connection.close()
server = host.ThreadingHTTPServer(('127.0.0.1', 0), host.Handler)
server.keys, server.token = {}, secrets.token_hex(32)
origin = f'http://127.0.0.1:{server.server_port}'
real_dispatch = host.dispatch
def read_only_dispatch(keys, operation, args):
    if operation not in ('flowRecover', 'flowStatus'):
        raise ValueError('Recovery verification allows existing-result reads only.')
    return real_dispatch(keys, operation, args)
def request(operation):
    connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=15)
    try:
        connection.request('POST', '/rpc', json.dumps({'id':'recovery-proof','op':operation,'args':{'request_id':owned[0]}}),
            {'Origin':origin,'X-Nexus-Session':server.token,'Content-Type':'application/json'})
        response = connection.getresponse()
        envelope = json.loads(response.read())
        if not envelope.get('ok'):raise RuntimeError(envelope.get('error'))
        return envelope['value']
    finally:
        connection.close()
thread = threading.Thread(target=server.serve_forever, daemon=True)
with patch.object(host, 'DATA', data), patch.object(host, 'OFFLINE_QA', False), patch.object(host, 'dispatch', read_only_dispatch):
    thread.start()
    try:
        start = request('flowRecover')
        deadline = time.monotonic()+120
        while time.monotonic()<deadline:
            result = request('flowStatus')
            if result['status']!='in_progress': break
            time.sleep(.25)
        else: raise RuntimeError('Read-only recovery did not finish within two minutes.')
    finally:
        server.shutdown(); server.server_close(); thread.join(timeout=2)
assert result['status']=='completed', result
assert result['id']==owned[0]
assert result['sha256']==original_digest
copy = data/'media'/result['outputs'][0].split('/')[-1]
assert hashlib.sha256(copy.read_bytes()).hexdigest()==original_digest
assert hashlib.sha256(path.read_bytes()).hexdigest()==original_digest
assert native_before==queue_count(), 'The native generation queue changed.'
assert before=={str(path):digest(path) for path in protected}, 'Main application data changed.'
report = {'status':'PASS','transport':'authenticated loopback HTTP to MCP','sameRequestId':True,'recoveryOnly':True,'sourceUnchanged':True,
          'mainDataUnchanged':True,'sha256':original_digest,'nativeGenerationsBefore':native_before,
          'nativeGenerationsAfter':queue_count(),'outputs':len(result['outputs']),'newGenerations':0}
(out/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
