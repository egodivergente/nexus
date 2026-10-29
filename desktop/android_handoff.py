"""Non-paid handoff through Nexus Agent's existing, authorized ADB draft bridge."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import threading
import time
import uuid
import xml.etree.ElementTree as ET

from native_operations import atomic_json
from workspace_continuity import inert_values, text

PACKAGE = 'com.vidadomu.nexus.agent'
AUTHORITY = 'content://' + PACKAGE + '.agent'
MODELS = {'Pixel 8 Pro':'Pixel 8 Pro', 'SM-S938B':'S25 Ultra', 'SM_S938B':'S25 Ultra'}
FLAGS = getattr(subprocess, 'CREATE_NO_WINDOW', 0)


class AndroidHandoff:
    def __init__(self, data=None, adb=None, runner=None):
        fallback = Path(os.environ.get('LOCALAPPDATA', '')) / 'Android' / 'Sdk' / 'platform-tools' / 'adb.exe'
        self.adb = str(adb or shutil.which('adb') or fallback)
        self.runner = runner or subprocess.run
        self.lock = threading.RLock()
        self.receipt_file = Path(data) / 'workspace-continuity' / 'android-handoffs.json' if data is not None else None
        self.data = Path(data) if data is not None else None
        try:
            self.receipts = json.loads(self.receipt_file.read_text('utf-8')) if self.receipt_file and self.receipt_file.exists() else {}
        except (OSError, ValueError):
            self.receipts = {}
        if not isinstance(self.receipts, dict):
            self.receipts = {}

    def _run(self, arguments, timeout=20):
        try:
            result = self.runner([self.adb, *arguments], capture_output=True, text=True, encoding='utf-8',
                                 timeout=timeout, creationflags=FLAGS)
        except (OSError, subprocess.TimeoutExpired):
            raise ValueError('No se pudo comprobar Nexus Agent por ADB. No se ha repetido la operación.') from None
        if result.returncode:
            raise ValueError('El móvil no respondió por el transporte ADB seleccionado.')
        return result.stdout

    def _call(self, serial, method, value=None):
        args = ['-s',serial,'shell','content','call','--uri',AUTHORITY,'--method',method]
        if value is not None:
            encoded = base64.urlsafe_b64encode(json.dumps(value,ensure_ascii=False,separators=(',',':')).encode()).decode()
            if len(encoded) > 96000:
                raise ValueError('El borrador supera el tamaño admitido por el móvil.')
            args += ['--arg',encoded]
        raw = self._run(args)
        match = re.search(r'nexus_result=([A-Za-z0-9_\-=]+)',raw)
        try:
            result = json.loads(base64.urlsafe_b64decode(match.group(1))) if match else None
        except (ValueError, TypeError):
            result = None
        if not isinstance(result,dict) or result.get('ok') is not True:
            raise ValueError('Nexus Agent no aceptó este borrador. Comprueba el modelo y sus parámetros en el móvil.')
        return result

    @staticmethod
    def capabilities():
        return {'nativeDraft':True,'prompt':True,'parameters':True,'mediaReferences':False,
                'activeProject':False,'corte':False,'paidExecutionEnabled':False,
                'limitations':['Las referencias y el proyecto se conservan en el trabajo compartido; el puente Android actual prepara modelo, prompt y parámetros.']}

    def devices(self):
        inventory = self._run(['devices','-l'])
        result = []
        for line in inventory.splitlines()[1:]:
            fields = line.split()
            if len(fields) < 2 or fields[1] != 'device':
                continue
            serial = fields[0]
            model = self._run(['-s',serial,'shell','getprop','ro.product.model']).strip()
            if model not in MODELS:
                continue
            result.append({'serial':serial,'model':model,'name':MODELS[model]})
        # A phone can appear via USB, mDNS and Tailscale; prefer its physical USB
        # transport, then retain one route per model for this personal two-phone setup.
        result.sort(key=lambda item:(':' in item['serial'] or item['serial'].startswith('adb-'),item['name']))
        unique = {}
        for item in result:
            unique.setdefault(item['model'],item)
        return {'devices':list(unique.values()),'capabilities':self.capabilities()}

    def probe(self, serial):
        # Validate the user-selected serial against today's inventory before using it.
        inventory = self._run(['devices','-l'])
        available = {line.split()[0] for line in inventory.splitlines()[1:] if len(line.split()) >= 2 and line.split()[1]=='device'}
        if serial not in available:
            raise ValueError('Este transporte del móvil ya no está disponible. Actualiza los dispositivos.')
        model = self._run(['-s',serial,'shell','getprop','ro.product.model']).strip()
        if model not in MODELS:
            raise ValueError('El dispositivo seleccionado no es tu Pixel 8 Pro ni tu S25 Ultra.')
        package = self._run(['-s',serial,'shell','dumpsys','package',PACKAGE])
        code = re.search(r'\bversionCode=(\d+)',package)
        version = re.search(r'\bversionName=([^\s]+)',package)
        if not code:
            raise ValueError('Nexus Agent no está instalado en este móvil.')
        status = self._call(serial,'status')
        if status.get('protocol') != 1 or status.get('paidExecutionEnabled') is not False:
            raise ValueError('El puente de Nexus Agent no ofrece el contrato de borradores sin ejecución.')
        return {'serial':serial,'model':model,'name':MODELS[model],'versionCode':int(code.group(1)),
                'versionName':version.group(1) if version else status.get('appVersion'), 'capabilities':self.capabilities()}

    def prepare(self, snapshot, serial, launch=False):
        if not isinstance(snapshot,dict) or not isinstance(snapshot.get('canvas'),dict):
            raise ValueError('Trabajo guardado no válido.')
        try:
            snapshot_id = str(uuid.UUID(snapshot['id']))
        except (KeyError, ValueError, TypeError, AttributeError):
            raise ValueError('Identidad del trabajo guardado no válida.') from None
        canvas = snapshot['canvas'];values = inert_values(canvas.get('values',{}))
        if not isinstance(values,dict) or any(isinstance(v,(dict,list)) or v is None for v in values.values()):
            raise ValueError('El puente Android actual solo admite parámetros simples. El trabajo completo se conserva.')
        parameters = {key:('true' if value is True else 'false' if value is False else str(value)) for key,value in values.items()}
        request = {'modelId':text(canvas.get('modelId'),400,'modelo',False),
                   'providerId':text(canvas.get('providerId'),100,'proveedor',False),
                   'prompt':text(canvas.get('prompt',''),16000,'prompt'),'parameters':parameters}
        with self.lock:
            device = self.probe(serial)
            before = self._call(serial,'list_drafts').get('drafts',[])
            receipt_key = snapshot_id + ':' + device['model']
            receipt = self.receipts.get(receipt_key,{})
            request_hash = hashlib.sha256(json.dumps(request,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
            if receipt.get('requestHash') not in {None,request_hash}:
                raise ValueError('El contenido de esta versión de trabajo ha cambiado.')
            if receipt.get('uncertain'):
                raise ValueError('El envío anterior no se pudo verificar. Revisa los borradores del móvil antes de volver a enviarlo.')
            draft_id = receipt.get('draftId')
            existing = next((row for row in before if row.get('draftId')==draft_id),None)
            if existing is None:
                # Persist an uncertainty marker BEFORE dispatch; an interrupted
                # acknowledgement never authorizes silently creating another copy.
                self.receipts[receipt_key] = {'uncertain':True,'requestHash':request_hash}
                if self.receipt_file:
                    atomic_json(self.receipt_file,self.receipts)
                prepared = self._call(serial,'prepare_draft',request)
                draft_id = prepared.get('draftId')
                try:
                    draft_id = str(uuid.UUID(draft_id))
                except (ValueError, TypeError, AttributeError):
                    raise ValueError('El móvil no confirmó la identidad del borrador. No se ha repetido el envío.') from None
                if prepared.get('paidExecutionEnabled') is not False or prepared.get('status')!='prepared_not_submitted':
                    raise ValueError('No se pudo verificar el borrador como preparado sin ejecutar.')
                after = self._call(serial,'list_drafts').get('drafts',[])
                verified = next((row for row in after if row.get('draftId')==draft_id
                                 and row.get('modelId')==request['modelId'] and row.get('providerId')==request['providerId']),None)
                if verified is None:
                    raise ValueError('No se pudo verificar el borrador guardado en el móvil. No se ha repetido el envío.')
                self.receipts[receipt_key] = {'draftId':draft_id,'modelId':request['modelId'],'providerId':request['providerId'],
                                              'requestHash':request_hash}
                if self.receipt_file:
                    atomic_json(self.receipt_file,self.receipts)
            elif existing.get('modelId')!=request['modelId'] or existing.get('providerId')!=request['providerId']:
                raise ValueError('El borrador del móvil ya no coincide con este trabajo.')
            native_backup = None
            if launch is True:
                native_backup = self.launch(serial,draft_id)
            return {**device,'draftId':draft_id,'snapshotId':snapshot_id,'prepared':True,'launched':launch is True,
                    'status':'prepared_not_submitted','paidExecutionEnabled':False,
                    'missingReferences':len(canvas.get('references',[])), 'projectTransferred':False,
                    'preservedAndroidDraft':native_backup}

    def launch(self, serial, draft_id):
        draft_id = str(uuid.UUID(draft_id))
        if self.data is None:
            raise ValueError('Configura el almacén local para conservar el borrador anterior del móvil antes de abrir otro.')
        self.probe(serial)
        if not any(row.get('draftId') == draft_id for row in self._call(serial,'list_drafts').get('drafts',[])):
            raise ValueError('El borrador preparado ya no está disponible en el móvil.')
        script = "run-as " + PACKAGE + " sh -c 'if [ -f shared_prefs/nexus_creation_v1.xml ]; then cat shared_prefs/nexus_creation_v1.xml; else echo \"<map />\"; fi'"
        raw = self._run(['-s',serial,'exec-out',script])
        if len(raw) > 2_000_000:
            raise ValueError('No se pudo conservar el borrador anterior del móvil.')
        try:
            root = ET.fromstring(raw)
            if root.tag != 'map':
                raise ValueError()
            draft = next((node.text for node in root if node.get('name') == 'draft'), None)
            draft = json.loads(draft) if draft else None
            if draft is not None and not isinstance(draft,dict):
                raise ValueError()
        except (ET.ParseError, ValueError):
            raise ValueError('No se pudo verificar la copia del borrador anterior del móvil. No se ha cambiado el Lienzo.') from None
        backup_id = None
        if draft is not None:
            backup_id = str(uuid.uuid4())
            target = self.data / 'workspace-continuity' / 'android-draft-backups' / (backup_id + '.json')
            atomic_json(target,{'createdAt':int(time.time()*1000),'serial':serial,'draft':draft})
            if json.loads(target.read_text('utf-8')).get('draft') != draft:
                raise ValueError('La copia del borrador anterior no se pudo verificar. No se ha cambiado el Lienzo.')
        self._run(['-s',serial,'shell','am','start','-n',PACKAGE+'/com.vidadomu.nexus.MainActivity',
                   '--es','nexus_agent_draft_id',draft_id])
        return backup_id

    def discard_owned_test(self, serial, draft_id):
        """Used only by an explicitly authorized, isolated physical acceptance test."""
        if draft_id not in {receipt.get('draftId') for receipt in self.receipts.values()}:
            raise ValueError('Este adaptador no creó el borrador de prueba indicado.')
        return self._call(serial,'discard_draft',{'draftId':str(uuid.UUID(draft_id))})
