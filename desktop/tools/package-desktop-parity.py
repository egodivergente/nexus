"""Package the current Windows runtime and source, excluding all private profiles."""
from pathlib import Path
import hashlib, json, zipfile
root=Path(__file__).resolve().parents[2]
desktop=root/'desktop';output=root/'entregas/Nexus-Studio-Agent-1.9.2-Windows.zip';output.parent.mkdir(exist_ok=True)
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
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in files:archive.write(path,Path('Nexus-Studio')/path.relative_to(root))
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert not any(part in name for name in archive.namelist() for part in ['.dpapi','state.json','local-session.json','rootkey.csv','index.json'])
report={'package':str(output),'files':len(files),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'bytes':output.stat().st_size}
(root/'outputs/desktop-agent-parity/package.json').write_text(json.dumps(report),encoding='utf-8');print(json.dumps(report))
