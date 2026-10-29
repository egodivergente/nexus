"""Build a code-only package with a complete artifact manifest; never package user data."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile

parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');args=parser.parse_args()
root=Path(__file__).resolve().parents[2];desktop=root/'desktop';out=root/'entregas';out.mkdir(exist_ok=True)
subprocess.run([sys.executable,'-X','utf8',str(desktop/'tools/verify-corte-source.py')],check=True)
sys.path.insert(0,str(desktop))
from local_host import DESKTOP_VERSION
version=DESKTOP_VERSION
files={}
def include(path,relative):
    if path.is_file():files[str(relative).replace('\\','/')]=path
for p in desktop.glob('*.py'):include(p,Path('desktop')/p.name)
for name in ['install-local.ps1','run.vbs','Nexus-Studio.bat','nexus.ico','requirements-media.txt','requirements-continuity.txt','setup-media.ps1']:
    include(desktop/name,Path('desktop')/name)
for folder in ['ui','agent/runtime','agent/assets']:
    for p in (desktop/folder).rglob('*'):
        if p.is_file() and '__pycache__' not in p.parts:include(p,Path('desktop')/p.relative_to(desktop))
# Package current authoritative skills, rather than an older duplicated tree.
for p in (root/'agent-skills').rglob('*'):
    include(p,Path('desktop/agent/agent-skills')/p.relative_to(root/'agent-skills'))
for name in ['test-modern-install.py','test-desktop-local-files.py','test-corte-ui.py','test-corte-media.js','test-corte-windows.py','test-windows-library.py','test-windows-agent-providers.py','test-custom-media.py','test-project-portable.py','test-agent-cancel.py','test-project-controls-ui.py','test-byteplus-pc-live.py','test-byteplus-documents.py','agent-qa-schema.json']:
    include(desktop/'tools'/name,Path('desktop/tools')/name)
for p in (root/'tools/flow-bridge').glob('*.py'):include(p,Path('tools/flow-bridge')/p.name)
for name in ['android-source-manifest.json', 'desktop-parity-manifest.json']:
    include(desktop/'agent'/name,Path('desktop/agent')/name)
include(root/'docs/DESKTOP-MODERN-20261026.md',Path('docs/DESKTOP-MODERN-20261026.md'))
include(root/'docs/DESKTOP-PREMIUM-20261029.md',Path('docs/DESKTOP-PREMIUM-20261029.md'))
for name in ['test-gallery-previews.py','test-gallery-sources.py','test-library-workflow.py','test-library-model.js','test-originals-protection.js','test-original-archive.py','test-workspace-continuity.py','test-android-handoff.py','test-drive-sync.py']:
    include(desktop/'tools'/name,Path('desktop/tools')/name)
for p in (root/'docs/windows-corte').glob('*'):
    include(p,Path('docs/windows-corte')/p.name)
for required in ['desktop/corte_host.py','desktop/corte_render.py','desktop/agent/runtime/corte-model.jar','desktop/agent/runtime/corte-timeline.jar','desktop/agent/runtime/corte-storage.jar','desktop/agent/runtime/nexus-desktop-agent.jar','desktop/ui/corte.js']:
    if required not in files:raise ValueError('Missing packaged component: '+required)
manifest={'version':version,'status':'PARTIAL_PREVIEW','nexusBase':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
          'corte':json.loads((desktop/'corte/source-lock.json').read_text('utf-8'))['commit'],
          'files':{name:hashlib.sha256(p.read_bytes()).hexdigest() for name,p in sorted(files.items())}}
archive=out/('Nexus-Corte-'+version+'-Windows.zip')
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name,p in sorted(files.items()):z.write(p,'Nexus-Studio/'+name)
    z.writestr('Nexus-Studio/desktop/build-info.json',json.dumps(manifest,indent=2))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name in z.namelist():
        if any(part in name.lower() for part in ['credentials.dpapi','local-session.json','state.json','index.json','.keystore','private-before']):raise ValueError('Private filename in package: '+name)
digest=hashlib.sha256(archive.read_bytes()).hexdigest()
report={'version':version,'status':'PARTIAL_PREVIEW','archive':str(archive),'sha256':digest,'bytes':archive.stat().st_size,'files':len(files),'activated':False}
if args.install:
    import os
    target=Path(os.environ['LOCALAPPDATA'])/'Programs/NexusStudio'/(version+'-'+digest[:8])
    target.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            if not (target/info.filename).resolve().is_relative_to(target.resolve()):raise ValueError('Invalid archive path')
        z.extractall(target)
    installed=target/'Nexus-Studio'
    for name,expected in manifest['files'].items():
        if hashlib.sha256((installed/name).read_bytes()).hexdigest()!=expected:raise ValueError('Installed hash mismatch: '+name)
    report['installed']=str(installed/'desktop')
    # Keep the active production host, tasks and shortcuts untouched.
    report['activation']='Installed; activation is a separate explicit step.'
evidence=root/'outputs/windows-corte';evidence.mkdir(parents=True,exist_ok=True)
(evidence/'package.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
