"""Build the private, portable Nexus Studio bundle without user data."""
from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parents[1]
workspace = root.parent
output = workspace / 'entregas' / 'Nexus-Studio-Privado-Windows-20261002-v2.zip'
files = [root / name for name in ('local_host.py', 'install-local.ps1', 'nexus.ico', 'README-LOCAL.md')]
files.extend(p for p in (root / 'ui').rglob('*') if p.is_file() and p.name not in {'qa.html', 'qa-bridge.js'})
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for file in files:
        archive.write(file, Path('Nexus-Studio') / file.relative_to(root))
with zipfile.ZipFile(output) as archive:
    if archive.testzip() is not None:
        raise RuntimeError('El ZIP no supera la verificación CRC.')
    if any('credentials' in name or 'state.json' in name or '/media/' in name for name in archive.namelist()):
        raise RuntimeError('El paquete contiene datos privados.')
print(json.dumps({'package': str(output), 'files': len(files), 'bytes': output.stat().st_size,
                  'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'crc': 'PASS'}))
