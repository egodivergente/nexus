"""Configure the desktop using the user's existing Bedrock access, without logging secrets."""
import json
import os
from pathlib import Path
import subprocess
import urllib.request

result = subprocess.run(
    ['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=7', 'vidal@100.112.57.2',
     'sudo -n cat /etc/axel-agent/aws.env'],
    capture_output=True, text=True, check=True)
values = {}
for line in result.stdout.splitlines():
    key, separator, value = line.partition('=')
    if separator:
        values[key.strip()] = value.strip().strip('"').strip("'")
if not values.get('AWS_ACCESS_KEY_ID') or not values.get('AWS_SECRET_ACCESS_KEY'):
    raise RuntimeError('El acceso existente no contiene las credenciales necesarias.')
data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
session = json.loads((data / 'local-session.json').read_text())
base = f"http://127.0.0.1:{session['port']}"
token = json.load(urllib.request.urlopen(base + '/session'))['token']
body = {'id':'connect-bedrock', 'op':'agent', 'args':{
    'owner':'desktop-setup', 'op':'settings', 'args':{
        'backend':'direct', 'region':values.get('AWS_DEFAULT_REGION','us-east-1'),
        'modelId':'us.anthropic.claude-sonnet-4-6',
        'accessKey':values['AWS_ACCESS_KEY_ID'], 'secretKey':values['AWS_SECRET_ACCESS_KEY'],
        'sessionToken':values.get('AWS_SESSION_TOKEN',''), 'clearCredentials':True}}}
request = urllib.request.Request(base + '/rpc', data=json.dumps(body).encode(),
    headers={'Content-Type':'application/json','Origin':base,'X-Nexus-Session':token})
response = json.load(urllib.request.urlopen(request, timeout=40))
if not response.get('ok'):
    raise RuntimeError('No se pudo aplicar la conexion al motor del asistente.')
snapshot = response['value']['result']
snapshot = snapshot.get('snapshot', snapshot)
print(json.dumps({'configured':True,'offline':snapshot.get('offline'),
                  'model':response['value']['settings']['modelId']}))

