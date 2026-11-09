"""Record the source actually compiled by Desktop; never import into its unused duplicate tree."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
AGENT = ROOT / 'desktop/agent'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write():
    files = {}
    for folder in ('agent-core/src', 'desktop/agent/src', 'desktop/agent/agent-skills'):
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and path.suffix in {'.kt', '.json', '.md', '.yaml', '.yml'}:
                files[path.relative_to(ROOT).as_posix()] = digest(path)
    for name in ('desktop/agent/build.gradle.kts', 'desktop/agent/core/build.gradle.kts', 'desktop/agent/settings.gradle.kts'):
        files[name] = digest(ROOT / name)
    upstream = subprocess.check_output(['git', 'rev-parse', 'c4cb3675'], cwd=ROOT, text=True).strip()
    manifest = {
        'format': 2,
        'version': '2026.11.09-desktop-audiovisual',
        'reviewedAndroidCommit': upstream,
        'compiledCore': 'agent-core/src/main/kotlin',
        'duplicateCoreTreeCompiled': False,
        'sourceState': 'working_tree_sha256',
        'relationship': 'Shared audiovisual agent core; Desktop portable imports, PDF visual reading and persistence test context retained. Android UI and runtime are separate.',
        'files': files,
        'runtime': {path.name: digest(path) for path in sorted((AGENT / 'runtime').glob('*.jar'))},
    }
    (AGENT / 'desktop-parity-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    core = {key: value for key, value in files.items() if key.startswith('agent-core/')}
    (AGENT / 'android-source-manifest.json').write_text(json.dumps({
        'format': 2, 'reviewedAndroidCommit': upstream, 'compiledSource': 'agent-core/src',
        'desktopAdaptations': [
            'AssetIndex.importProject with narrative associations and injectable persistence context',
            'Responses PDF visual pages and one-pass document reading',
            'Kotlin 2.3 compatible temporary-directory test',
        ],
        'files': core,
    }, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return {'sourceFiles': len(files), 'runtimeJars': len(manifest['runtime']), 'reviewedAndroidCommit': upstream}


if __name__ == '__main__':
    print(json.dumps(write()))
