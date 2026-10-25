"""Fixture tests for package-update.py on a fake repository; never touches the real tree."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import zlib

SCRIPT = Path(__file__).resolve().parent / 'package-update.py'
spec = importlib.util.spec_from_file_location('package_update', SCRIPT)
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)

RUNTIME_JARS = ('core.jar', 'nexus-desktop-agent.jar', 'kotlin-stdlib-2.0.21.jar', 'kotlin-stdlib-jdk8-1.8.21.jar',
                'kotlin-stdlib-jdk7-1.8.21.jar', 'kotlinx-coroutines-core-jvm-1.8.1.jar', 'okhttp-4.12.0.jar',
                'okio-jvm-3.6.0.jar', 'annotations-13.0.jar')
REQUIRED = ('core.jar', 'nexus-desktop-agent.jar', 'kotlin-stdlib-2.0.21.jar')


class Fixture:
    def __init__(self, base, *parts):
        self.root = Path(base, *parts, 'repo')
        self.desktop = self.root / 'desktop'
        self.runtime = self.desktop / 'agent/build/runtime'
        files = {
            'desktop/local_host.py': 'print(1)\n',
            'desktop/conversation_store.py': 'x = 1\n',
            'desktop/README.md': '# r\n',
            'desktop/nexus.ico': b'\x00\x01ico',
            'desktop/credentials.dpapi': b'SECRET-DPAPI',
            'desktop/local-session.json': '{"token": "SECRET"}',
            'desktop/state.json': '{"jobs": ["SECRET"]}',
            'desktop/ui/app.js': 'app()\n',
            'desktop/ui/style.css': 'a{}\n',
            'desktop/ui/qa.html': '<qa>',
            'desktop/ui/qa-bridge.js': 'qa()',
            'desktop/ui/__pycache__/cached.js': 'stale',
            'desktop/ui/node_modules/dep/index.js': 'dep',
            'desktop/ui/notes.bak': 'backup',
            'desktop/agent/assets/catalog.json': '{}',
            'desktop/agent/agent-skills/flow/SKILL.md': '# skill\n',
            'desktop/agent/agent-skills/flow/build/out.md': 'built',
            'desktop/agent/android-source-manifest.json': '{"commit": "abc"}',
            'desktop/agent/src/main/kotlin/Main.kt': 'fun main() {}',
            'docs/DESKTOP-UPDATE-20261025.md': '# update\n',
            'docs/OTHER.md': 'other',
        }
        files.update({'desktop/agent/build/runtime/' + name: name.encode() * 3 for name in RUNTIME_JARS})
        for name, content in files.items():
            self.write(name, content)

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        (path.write_bytes if isinstance(content, bytes) else lambda c: path.write_text(c, 'utf-8'))(content)
        return path

    def patched(self):
        return patch.multiple(packager, ROOT=self.root, DESKTOP=self.desktop)


class PackageUpdateTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.base = Path(self.folder.name)
        self.fixture = Fixture(self.base)

    def tearDown(self):
        self.folder.cleanup()

    def build(self, name='out/Nexus.zip', fixture=None):
        fixture = fixture or self.fixture
        output = self.base / name
        with fixture.patched():
            return output, packager.package(output, fixture.runtime, 'deadbeef')

    def names(self, output):
        with zipfile.ZipFile(output) as archive:
            return {n.removeprefix('Nexus-Studio/') for n in archive.namelist()}

    def test_contents_include_runtime_and_exclude_profile_credentials_and_caches(self):
        output, _ = self.build()
        names = self.names(output)
        expected = {'desktop/local_host.py', 'desktop/conversation_store.py', 'desktop/README.md', 'desktop/nexus.ico',
                    'desktop/ui/app.js', 'desktop/ui/style.css', 'desktop/agent/assets/catalog.json',
                    'desktop/agent/agent-skills/flow/SKILL.md', 'desktop/agent/android-source-manifest.json',
                    'docs/DESKTOP-UPDATE-20261025.md', 'package-manifest.json'}
        expected |= {'desktop/agent/runtime/' + jar for jar in RUNTIME_JARS}
        self.assertEqual(expected, names)
        with zipfile.ZipFile(output) as archive:
            blob = b''.join(archive.read(n) for n in archive.namelist())
        self.assertNotIn(b'SECRET', blob)

    def test_every_entry_lives_under_one_safe_prefix(self):
        output, _ = self.build()
        with zipfile.ZipFile(output) as archive:
            for info in archive.infolist():
                self.assertTrue(info.filename.startswith('Nexus-Studio/'))
                self.assertNotIn('..', Path(info.filename).parts)
                self.assertNotIn('\\', info.filename)
                self.assertEqual(zipfile.ZIP_DEFLATED, info.compress_type)
                self.assertEqual((2026, 10, 25, 0, 0, 0), info.date_time)

    def test_crc_hashes_manifest_and_report_agree(self):
        output, report = self.build()
        with zipfile.ZipFile(output) as archive:
            self.assertIsNone(archive.testzip())
            for info in archive.infolist():
                self.assertEqual(info.CRC, zlib.crc32(archive.read(info)) & 0xFFFFFFFF)
            manifest = json.loads(archive.read('Nexus-Studio/package-manifest.json'))
            self.assertEqual(('1.9.6-agent-desktop', 'deadbeef'), (manifest['version'], manifest['sourceCommit']))
            self.assertEqual(set(manifest['files']), self.names(output) - {'package-manifest.json'})
            for name, digest in manifest['files'].items():
                self.assertEqual(digest, hashlib.sha256((self.fixture.root / name.replace('desktop/agent/runtime/', 'desktop/agent/build/runtime/')).read_bytes()).hexdigest())
                self.assertEqual(digest, hashlib.sha256(archive.read('Nexus-Studio/' + name)).hexdigest())
        data = output.read_bytes()
        self.assertEqual((hashlib.sha256(data).hexdigest(), len(data), len(manifest['files']) + 1, 'PASS'),
                         (report['sha256'], report['bytes'], report['files'], report['crc']))
        self.assertEqual(report, json.loads(output.with_suffix('.json').read_text('utf-8')))

    def test_output_is_byte_identical_across_runs_and_mtimes(self):
        first, _ = self.build('a/Nexus.zip')
        for path in self.fixture.root.rglob('*'):
            if path.is_file():
                path.touch()
        second, _ = self.build('b/Nexus.zip')
        self.assertEqual(first.read_bytes(), second.read_bytes())
        other = Fixture(self.base, 'elsewhere')
        third, _ = self.build('c/Nexus.zip', other)
        self.assertEqual(first.read_bytes(), third.read_bytes())

    def test_missing_or_incomplete_runtime_is_refused(self):
        for jar in REQUIRED:
            with self.subTest(missing=jar):
                fixture = Fixture(self.base, 'missing-' + jar)
                (fixture.runtime / jar).unlink()
                with self.assertRaises(ValueError):
                    self.build('x.zip', fixture)
        with self.fixture.patched(), self.assertRaises(ValueError):
            packager.package(self.base / 'y.zip', self.base / 'no-runtime', 'deadbeef')
        self.assertFalse((self.base / 'y.zip').exists())

    def test_credential_and_session_names_abort_packaging(self):
        for name in ('desktop/ui/credentials.json', 'desktop/ui/local-session.json', 'desktop/agent/assets/.env.json',
                     'desktop/ui/keys.dpapi.json'):
            with self.subTest(name=name):
                fixture = Fixture(self.base, 'unsafe-' + Path(name).name)
                fixture.write(name, '{"secret": 1}')
                with self.assertRaises(ValueError):
                    self.build('unsafe.zip', fixture)

    def test_repository_under_a_build_folder_keeps_the_ui(self):
        fixture = Fixture(self.base, 'build')
        output, _ = self.build('build.zip', fixture)
        self.assertIn('desktop/ui/app.js', self.names(output))
        self.assertNotIn('desktop/agent/agent-skills/flow/build/out.md', self.names(output))

    def test_secret_like_names_are_rejected(self):
        for name in ('secrets.json', 'api-keys.json', 'api_key.txt', 'token.json', 'auth-token.js', 'session.json',
                     'Passwords.md', 'private-key.txt', 'keys.yaml', 'release.keystore.json', '.env'):
            with self.subTest(name=name):
                self.assertTrue(packager.secret_name(name))
        fixture = Fixture(self.base, 'secrets')
        fixture.write('desktop/ui/secrets.json', '{"apiKey": "SECRET"}')
        with self.assertRaises(ValueError):
            self.build('secrets.zip', fixture)
        self.assertFalse((self.base / 'secrets.zip').exists())

    def test_legitimate_names_are_not_false_alarms(self):
        for name in ('app.envelope.js', 'environment.md', 'crun-account-credits', 'SKILL.md', 'tokens.css',
                     'keyboard.js', 'monkey.png', 'session-ui.js', 'conversation_store.py', 'secretary.md'):
            with self.subTest(name=name):
                self.assertFalse(packager.secret_name(name))
        fixture = Fixture(self.base, 'envelope')
        fixture.write('desktop/ui/app.envelope.js', 'ok()')
        output, _ = self.build('envelope.zip', fixture)
        self.assertIn('desktop/ui/app.envelope.js', self.names(output))

    def test_real_repository_tree_has_no_false_alarms(self):
        runtime = self.base / 'real-runtime'
        runtime.mkdir()
        for jar in REQUIRED:
            (runtime / jar).write_bytes(b'jar')
        files = packager.collect(runtime)
        self.assertIn('desktop/local_host.py', files)
        self.assertTrue(any(name.startswith('desktop/ui/') for name in files))

    def test_required_jars_accept_plain_or_versioned_names_only(self):
        for names in (('core-1.9.6.jar', 'nexus-desktop-agent-1.9.6.jar', 'kotlin-stdlib-2.0.21.jar'),
                      ('core.jar', 'nexus-desktop-agent.jar', 'kotlin-stdlib-2.0.21.jar')):
            with self.subTest(names=names):
                runtime = self.base / ('ok-' + names[0])
                runtime.mkdir()
                for name in names:
                    (runtime / name).write_bytes(b'jar')
                self.assertEqual(sorted(names), sorted(p.name for p in packager.runtime_jars(runtime)))
        for impostor in ('coreutils.jar', 'core-jvm.jar', 'nexus-desktop-agent-sources-x.jar', 'kotlin-stdlib-jdk8-1.8.21.jar'):
            with self.subTest(impostor=impostor):
                runtime = self.base / ('bad-' + impostor)
                runtime.mkdir()
                required = dict(zip(('core', 'nexus-desktop-agent', 'kotlin-stdlib'), REQUIRED))
                replaced = next(key for key in sorted(required, key=len, reverse=True) if impostor.startswith(key))
                for key, name in required.items():
                    (runtime / (impostor if key == replaced else name)).write_bytes(b'jar')
                with self.assertRaises(ValueError):
                    packager.runtime_jars(runtime)

    def test_duplicate_runtime_artifacts_are_refused(self):
        for extra in ('core-1.9.5.jar', 'nexus-desktop-agent-1.9.5.jar', 'kotlin-stdlib-1.9.0.jar',
                      'okhttp-4.11.0.jar', 'core-1.9.6.jar'):
            with self.subTest(extra=extra):
                fixture = Fixture(self.base, 'dup-' + extra)
                (fixture.runtime / extra).write_bytes(b'old')
                with self.assertRaises(ValueError) as raised:
                    self.build('dup.zip', fixture)
                self.assertIn('Duplicate', str(raised.exception))
                self.assertFalse((self.base / 'dup.zip').exists())

    def test_crc_failure_raises_and_removes_the_zip(self):
        with patch.object(zipfile.ZipFile, 'testzip', return_value='Nexus-Studio/desktop/ui/app.js'):
            with self.assertRaises(ValueError) as raised:
                self.build('crc.zip')
        self.assertIn('CRC', str(raised.exception))
        self.assertFalse((self.base / 'crc.zip').exists())
        self.assertFalse((self.base / 'crc.json').exists())

    def test_hash_mismatch_raises_and_removes_the_zip(self):
        original = zipfile.ZipFile.read
        def tampered(archive, name, *args):
            data = original(archive, name, *args)
            return data + b'!' if str(getattr(name, 'filename', name)).endswith('app.js') else data
        with patch.object(zipfile.ZipFile, 'read', tampered):
            with self.assertRaises(ValueError) as raised:
                self.build('hash.zip')
        self.assertIn('SHA-256', str(raised.exception))
        self.assertFalse((self.base / 'hash.zip').exists())

if __name__ == '__main__':
    unittest.main()
