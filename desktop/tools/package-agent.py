"""Package the application and verify provenance without including private state."""
from pathlib import Path
import hashlib
import json
import os
import zipfile

root = Path(__file__).resolve().parents[1]
workspace = root.parent
evidence = workspace / 'outputs/desktop-parity-20261006'
source = evidence / 'drive-source/Nexus'
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
verified = []
for original_dir, target_dir in [(source / 'agent-core/src', root / 'agent/core/src'),
                                 (source / 'agent-skills', root / 'agent/agent-skills')]:
    for original in original_dir.rglob('*'):
        if not original.is_file():
            continue
        target = target_dir / original.relative_to(original_dir)
        if not target.is_file() or sha(original) != sha(target):
            raise RuntimeError(f'Source mismatch: {target}')
        verified.append(str(target.relative_to(root)))

for name, expected in {
    'core.jar': 'bb4567115638b410c045c341153a09a8d17eabcabb56315ac12bebda34431052',
    'nexus-desktop-agent.jar': '396e0871d6cf444e371999e9cf98c603dd8f906351aa5a478718f5d6883dd3f3',
}.items():
    if sha(root / 'agent/runtime' / name) != expected:
        raise RuntimeError(f'ASUS artifact mismatch: {name}')

data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
before = json.loads((evidence / 'private-before/state.json').read_text(encoding='utf-8-sig'))
after = json.loads((data / 'state.json').read_text(encoding='utf-8-sig'))
old_jobs = {job['id']: job for job in before['jobs']}
new_jobs = {job['id']: job for job in after['jobs']}
assert old_jobs.keys() <= new_jobs.keys(), 'Missing original jobs'
for key, old in old_jobs.items():
    current = new_jobs[key]
    for output in old.get('outputs', []):
        assert output in current.get('outputs', []), 'Missing original output'
assert sha(data / 'credentials.dpapi') == sha(evidence / 'private-before/credentials.dpapi')

files = [(root / name, Path(name)) for name in
         ['local_host.py', 'agent_host.py', 'install-local.ps1', 'run.vbs', 'Nexus-Studio.bat', 'nexus.ico', 'README-AGENTE.md']]
for name in ['Nexus.exe', 'Microsoft.Web.WebView2.Core.dll', 'Microsoft.Web.WebView2.WinForms.dll', 'WebView2Loader.dll']:
    files.append((root / 'dist' / name, Path(name)))
for folder in ['ui', 'agent/runtime', 'agent/assets', 'agent/agent-skills']:
    files.extend((path, path.relative_to(root)) for path in (root / folder).rglob('*')
                 if path.is_file() and path.name not in {'qa.html', 'qa-bridge.js'})
target = workspace / 'entregas/Nexus-Agente-Windows-1.7.0-20261006.zip'
with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path, relative in files:
        archive.write(path, Path('Nexus-Agente') / relative)
with zipfile.ZipFile(target) as archive:
    assert archive.testzip() is None
    assert all(not any(key in name.lower() for key in ['credentials.dpapi', 'state.json', '/media/', 'private-before']) for name in archive.namelist())
report = {'source_zip_sha256': sha(evidence / 'drive-source.zip'), 'original_source_files_verified': len(verified),
          'runtime_sha256': {path.name: sha(path) for path in (root / 'agent/runtime').glob('*.jar')},
          'native_exe_sha256': sha(root / 'dist/Nexus.exe'), 'package_sha256': sha(target),
          'package': str(target), 'package_files': len(files), 'crc': 'PASS',
          'original_jobs_preserved': len(old_jobs), 'provider_credentials_preserved': True}
(evidence / 'delivery-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report))
