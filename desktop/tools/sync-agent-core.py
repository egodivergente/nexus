"""Import the verified Android agent into Windows without changing its source checkout."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
args = parser.parse_args()
desktop = Path(__file__).resolve().parents[1]
manifest = []
for origin, destination in [('agent-core/src', 'agent/core/src'), ('agent-skills/skills', 'agent/agent-skills/skills')]:
    for source in (args.source / origin).rglob('*'):
        if not source.is_file():
            continue
        target = desktop / destination / source.relative_to(args.source / origin)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        manifest.append({'source': str(source.relative_to(args.source)).replace('\\', '/'),
                         'target': str(target.relative_to(desktop)).replace('\\', '/'),
                         'sha256': hashlib.sha256(source.read_bytes()).hexdigest()})
(desktop / 'agent/android-source-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(f'Imported {len(manifest)} agent and skill files')
