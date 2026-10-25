"""Package a built Desktop-only runtime, never a user's profile or credentials.

Run after Gradle :core:test :test runtime. The ZIP contains the local Python/Edge
edition, not an unsigned native exe. Existing launchers/USB relays stay external.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

VERSION = '1.9.6-agent-desktop'
ROOT = Path(__file__).resolve().parents[2]
DESKTOP = ROOT / 'desktop'
REQUIRED_JARS = ('core', 'nexus-desktop-agent', 'kotlin-stdlib')
JAR_NAME = re.compile(r'(?P<artifact>.+?)(?:-(?P<version>\d[\w.+-]*))?\.jar', re.IGNORECASE)
SECRET_TOKENS = {'credential', 'credentials', 'secret', 'secrets', 'password', 'passwords', 'passwd',
                 'apikey', 'apikeys', 'dpapi', 'keystore', 'privatekey'}
SECRET_PAIRS = {('api', 'key'), ('api', 'keys'), ('private', 'key'), ('local', 'session'),
                ('access', 'token'), ('auth', 'token'), ('refresh', 'token')}
DATA_SUFFIXES = {'.json', '.txt', '.yaml', '.yml'}
DATA_SECRET_TOKENS = {'token', 'tokens', 'session', 'sessions', 'keys'}


def secret_name(name):
    tokens = re.split(r'[._\-\s]+', name.lower())
    if name.lower().startswith('.env'):
        return True
    if SECRET_TOKENS.intersection(tokens) or SECRET_PAIRS.intersection(zip(tokens, tokens[1:])):
        return True
    return Path(name).suffix.lower() in DATA_SUFFIXES and bool(DATA_SECRET_TOKENS.intersection(tokens))


def runtime_jars(runtime):
    jars = {}
    for path in sorted(runtime.glob('*.jar')):
        match = JAR_NAME.fullmatch(path.name)
        artifact = match['artifact'].lower() if match else path.name.lower()
        if artifact in jars:
            raise ValueError(f'Duplicate runtime jar {artifact}: {jars[artifact].name}, {path.name}; clean the runtime folder.')
        jars[artifact] = path
    missing = [name for name in REQUIRED_JARS if name not in jars]
    if missing:
        raise ValueError('Missing compiled Desktop runtime (' + ', '.join(missing) + '); run :core:test :test runtime first.')
    return list(jars.values())


def collect(runtime):
    files = {}
    for path in DESKTOP.iterdir():
        if path.is_file() and path.suffix in {'.py', '.ps1', '.bat', '.vbs', '.ico', '.md', '.txt'}:
            files[path.relative_to(ROOT).as_posix()] = path
    for folder in ('ui', 'agent/assets', 'agent/agent-skills'):
        for path in (DESKTOP / folder).rglob('*'):
            if not path.is_file() or any(p in {'__pycache__', '.gradle', 'build', 'node_modules'} for p in path.relative_to(DESKTOP / folder).parts):
                continue
            if path.name.startswith('qa') or path.suffix not in {'.js', '.css', '.html', '.json', '.md', '.png', '.svg', '.webp', '.ico', '.txt', '.yaml', '.yml'}:
                continue
            files[path.relative_to(ROOT).as_posix()] = path
    for path in runtime_jars(runtime):
        files['desktop/agent/runtime/' + path.name] = path
    for name in ('android-source-manifest.json', 'desktop-parity-manifest.json'):
        path = DESKTOP / 'agent' / name
        if path.is_file(): files[path.relative_to(ROOT).as_posix()] = path
    report = ROOT / 'docs/DESKTOP-UPDATE-20261025.md'
    if report.is_file(): files['docs/DESKTOP-UPDATE-20261025.md'] = report
    for path in files.values():
        if path.is_symlink() or secret_name(path.name):
            raise ValueError('Unsafe package entry: ' + path.name)
    return files


def verify(output, manifest):
    with zipfile.ZipFile(output) as archive:
        broken = archive.testzip()
        if broken is not None:
            raise ValueError('CRC check failed for ' + broken)
        for name, expected in manifest['files'].items():
            if hashlib.sha256(archive.read('Nexus-Studio/' + name)).hexdigest() != expected:
                raise ValueError('SHA-256 mismatch for ' + name)


def package(output, runtime, source):
    files = collect(runtime)
    payload = {name: path.read_bytes() for name, path in sorted(files.items())}
    manifest = {'version': VERSION, 'sourceCommit': source,
                'edition': 'Python/Edge, Windows x64, Java 17',
                'files': {name: hashlib.sha256(data).hexdigest() for name, data in payload.items()}}
    payload['package-manifest.json'] = json.dumps(manifest, ensure_ascii=False, indent=2).encode('utf-8')
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in payload.items():
            info = zipfile.ZipInfo('Nexus-Studio/' + name, date_time=(2026, 10, 25, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    try:
        verify(output, manifest)
    except Exception:
        output.unlink(missing_ok=True)
        raise
    report = {'version': VERSION, 'sourceCommit': source, 'files': len(payload),
              'bytes': output.stat().st_size, 'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
              'package': str(output), 'crc': 'PASS'}
    output.with_suffix('.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--runtime', type=Path, default=DESKTOP / 'agent/build/runtime')
    args = parser.parse_args()
    print(json.dumps(package(args.output, args.runtime, args.source_commit)))
