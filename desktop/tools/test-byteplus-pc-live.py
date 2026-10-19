"""Opt-in real BytePlus -> installed Windows tools acceptance; isolated DPAPI profile.

Without --live this script makes no account calls. No media generation is submitted.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import uuid

def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def run(desktop,production):
    # Load the selected installation's host as well as its JVM runtime.
    sys.path.insert(0,str(desktop))
    from agent_host import AgentHost
    from corte_host import CorteHost
    from local_host import windows_keys,write_secret
    from native_operations import NativeOperations

    protected=[production/'assistant/settings.json',production/'assistant/credentials.dpapi']
    before={str(p):fingerprint(p) for p in protected}
    settings=json.loads(protected[0].read_text('utf-8'))
    profile=copy.deepcopy(settings.get('brainProfiles',{}).get('byteplus'))
    if not profile:raise ValueError('Falta el perfil BytePlus configurado.')
    key=windows_keys(protected[1]).get('brain:byteplus')
    if not key:raise ValueError('Falta la credencial BytePlus en DPAPI.')
    if key in json.dumps(profile):raise ValueError('La clave no debe estar dentro del perfil JSON.')
    if profile.get('protocol')!='OPENAI_RESPONSES':raise ValueError('Esta aceptación espera el perfil Responses configurado.')
    profile['limits']={**(profile.get('limits') or {}),'maxToolRounds':5,'maxTokens':1024,
                       'maxCallsPerTurn':6,'turnTimeoutSeconds':150,'httpTimeoutSeconds':60}
    label='BytePlus-PC-'+uuid.uuid4().hex[:10]
    theme='qa-'+uuid.uuid4().hex[:12]
    audit=[];events=[];blocked=[];done=threading.Event()
    with tempfile.TemporaryDirectory(prefix='nexus-byteplus-pc-') as temporary:
        data=Path(temporary)
        host=AgentHost(desktop,data,windows_keys,write_secret)
        native=NativeOperations(data,host,lambda:{'interfaceTheme':theme,'rememberParams':False},lambda _:None,None,lambda:{})
        host.corte=CorteHost(desktop,data,native)
        class AuditedNative:
            def __getattr__(self,name):return getattr(native,name)
            def run(self,payload):
                op=payload['operation'];args=payload.get('input') or {}
                if op not in {'get_nexus_settings','corte_read_project','corte_apply_ops'}:
                    blocked.append(op);raise ValueError('Herramienta fuera de esta prueba.')
                if op=='corte_apply_ops':
                    ops=json.loads(args['ops_json'])
                    if ops!=[{'op':'add_marker','at_us':1000000,'label':label}]:
                        blocked.append(op);raise ValueError('La prueba solo permite su marcador exacto.')
                value=native.run(payload)
                audit.append({'operation':op,'ok':not value.get('isError',False),
                              'revision':value.get('revision'),'theme':value.get('theme')})
                return value
        host.native=AuditedNative()
        def pump():
            while not done.wait(.02):
                for event in host.events('byteplus-qa'):
                    name=event['op'];events.append(name)
                    allowed=name in {'stream','progress','uiEvent'}
                    host.reply({'owner':'byteplus-qa','id':event['id'],'ok':allowed,
                                'value':None,'error':'No se envían generaciones ni consultas externas en esta prueba.'})
        thread=threading.Thread(target=pump,daemon=True)
        try:
            host.command({'op':'settings','owner':'byteplus-qa','args':{'backend':'profile','mode':'AGENT',
                          'brainProfile':profile,'brainKey':key,'limits':{'maxToolRounds':5,'maxTokens':1024,'turnTimeoutSeconds':150}}})
            assert key not in (data/'assistant/settings.json').read_text('utf-8')
            assert key.encode() not in (data/'assistant/credentials.dpapi').read_bytes()
            project=host.command({'op':'createProject','owner':'byteplus-qa','args':{'name':'BytePlus PC acceptance'}})['result']['projectId']
            original=host.corte.run({'method':'create_project','project_id':project,'name':'BytePlus PC acceptance'})
            thread.start()
            prompt=('Comprobación autorizada en el proyecto de prueba abierto. Primero usa get_nexus_settings y corte_read_project. '
                    'Después ejecuta corte_apply_ops con exactamente este array: '+json.dumps([{'op':'add_marker','at_us':1000000,'label':label}])+'. '
                    'Finalmente usa corte_read_project para verificar la edición. Responde brevemente con el tema real que devolvió '
                    'get_nexus_settings, la etiqueta del marcador y la revisión leída tras editar. No crees medios ni hagas otras operaciones.')
            result=host.command({'op':'send','owner':'byteplus-qa','turn_id':uuid.uuid4().hex,'args':{'text':prompt}})['result']
            persisted=host.corte.run({'method':'read_project','project_id':project})
            operations=[entry['operation'] for entry in audit]
            assert not blocked,blocked
            assert 'get_nexus_settings' in operations,operations
            assert operations.count('corte_apply_ops')==1,operations
            edit_index=operations.index('corte_apply_ops')
            assert 'corte_read_project' in operations[:edit_index],operations
            assert 'corte_read_project' in operations[edit_index+1:],operations
            assert all(entry['ok'] for entry in audit),audit
            assert persisted['revision']==original['revision']+1,persisted['revision']
            markers=persisted['project']['timeline']['markers']
            assert len(markers)==1 and markers[0]['label']==label,markers
            assert theme in result['text'] and label in result['text'],result['text']
            host.corte.close()
            reopened=host.corte.run({'method':'read_project','project_id':project})
            assert reopened['project']==persisted['project']
            undone=host.corte.run({'method':'undo','project_id':project,'expected_revision':reopened['revision'],
                                  'request_id':uuid.uuid4().hex,'transaction_id':reopened['undo_id']})
            assert undone['project']['timeline']==original['project']['timeline']
            report={'status':'PASS','scope':'Real BytePlus Responses + Windows native settings + Corte marker edit/readback',
                    'desktop':str(desktop),'model':profile['modelId'],'protocol':profile['protocol'],
                    'configuredStreaming':profile.get('capabilities',{}).get('streaming',False),
                    'streamEvents':events.count('stream'),'toolAudit':audit,'agentTools':result.get('tools'),
                    'inputTokens':result.get('inputTokens'),'outputTokens':result.get('outputTokens'),
                    'toolRounds':result.get('toolRounds'),'answer':result['text'],
                    'persistedRevision':persisted['revision'],'reopened':True,'localUndo':True,
                    'productionPreferencesUnchanged':True,'mediaGenerationSubmitted':False}
        finally:
            done.set()
            if thread.ident:thread.join(3)
            host.close();host.corte.close();native.executor.shutdown()
            assert before=={str(p):fingerprint(p) for p in protected},'Cambiaron ajustes/credenciales de producción durante la prueba.'
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--live',action='store_true',help='Ejecutar inferencia real con la cuenta BytePlus configurada.')
    parser.add_argument('--desktop-root',type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument('--production-data',type=Path,default=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop')
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    if not args.live:
        print('SKIP: requiere --live; no se han hecho llamadas externas.');return
    report=run(args.desktop_root.resolve(),args.production_data.resolve())
    if args.report:
        args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
