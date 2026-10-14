"""Package the current Windows runtime and source, excluding all private profiles."""
from pathlib import Path
import hashlib, json, shutil, zipfile
root=Path(__file__).resolve().parents[2]
desktop=root/'desktop'
output=root/'entregas/Nexus-Studio-Agent-1.9.4-Windows.zip'
output_x64=root/'entregas/Nexus-Studio-1.9.4-Windows-x64.zip'
output.parent.mkdir(exist_ok=True)
files=[]
for path in desktop.rglob('*'):
    if not path.is_file():continue
    relative=path.relative_to(desktop)
    if any(part in {'__pycache__','.gradle','build','transfer-build','vendor','dist'} for part in relative.parts):continue
    if relative.parts[0] in {'src','vendor'}:continue
    if path.suffix not in {'.py','.ps1','.js','.css','.html','.json','.md','.jar','.kts','.kt','.txt','.ico','.png','.webp','.svg','.bat','.vbs'}:continue
    files.append(path)
for path in (root/'tools/flow-bridge').rglob('*'):
    if path.is_file() and path.suffix in {'.py','.json','.md'} and not any(p in {'__pycache__','upstream','evidence-private'} for p in path.relative_to(root/'tools/flow-bridge').parts):files.append(path)
for relative in ['README.md', 'CHANGELOG.md', 'CONTINUAR.md', 'docs/MEJORAS-AUDITORIA-20261014.md',
                 'docs/RELEASE-193-CONSOLIDADA-20261013.md',
                 'docs/DESKTOP-LIBRARY-PDF-193.md', 'docs/DESKTOP-AGENT-PARITY-20261011.md',
                 'docs/NEXUS-AKI-WIDGET-20261012.md', 'docs/COMPILAR-PIXEL.md']:
    path = root / relative
    if path.is_file():files.append(path)
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in files:archive.write(path,Path('Nexus-Studio')/path.relative_to(root))
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert not any(part in name for name in archive.namelist() for part in ['.dpapi','state.json','local-session.json','rootkey.csv','index.json'])
shutil.copy2(output, output_x64)
sha=hashlib.sha256(output.read_bytes()).hexdigest()
assert hashlib.sha256(output_x64.read_bytes()).hexdigest()==sha
report={'package':str(output),'packageX64':str(output_x64),'files':len(files),'sha256':sha,'bytes':output.stat().st_size,'version':'1.9.4-agent-desktop'}
(root/'outputs/desktop-agent-parity/package.json').write_text(json.dumps(report),encoding='utf-8');print(json.dumps(report))

