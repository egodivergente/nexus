"""Package the unsigned native WebView2 build without private data."""
from pathlib import Path
import hashlib
import json
import zipfile

root=Path(__file__).resolve().parents[1]
workspace=root.parent
dist=root/'dist'
output=workspace/'entregas'/'Nexus-Desktop-Windows-x64-20261002-v2-UNSIGNED.zip'
files=[dist/name for name in ('Nexus.exe','Microsoft.Web.WebView2.Core.dll',
       'Microsoft.Web.WebView2.WinForms.dll','WebView2Loader.dll')]
files += [file for file in (dist/'ui').rglob('*') if file.is_file()]
files += [root/'README.md',root/'SIGNING.md']
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for file in files:
        name=Path('Nexus-Desktop')/(file.relative_to(dist) if file.is_relative_to(dist) else file.name)
        archive.write(file,name)
with zipfile.ZipFile(output) as archive:
    if archive.testzip() is not None:raise RuntimeError('CRC incorrecto')
    if any('credential' in name.lower() or 'state.json' in name.lower() or '/media/' in name.lower() for name in archive.namelist()):
        raise RuntimeError('Datos privados en el paquete')
print(json.dumps({'package':str(output),'files':len(files),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'crc':'PASS'}))
