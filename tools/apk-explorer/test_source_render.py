import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import source_render as r
import explorer as e


class SourceRenderTests(unittest.TestCase):
    def test_low_memory_returns_error_without_starting_gradle(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            with patch.object(r, 'local_available_mb', return_value=300), patch.object(r, 'select_host', return_value=''), patch.object(r, 'logged_run', side_effect=AssertionError('Must not build')):
                result = r.render_source(root, Path(folder)/'output', config, log=lambda _: None)
            self.assertEqual(result['status'], 'error')
            self.assertIn('300 MiB', result['error'])
            self.assertEqual(result['states'], [])

    def test_auto_host_requires_memory_and_no_existing_build(self):
        from subprocess import CompletedProcess
        with patch.object(r.subprocess, "run", return_value=CompletedProcess([], 0, b"3500000\n0\n", b"")):
            self.assertEqual(r.select_host("auto", lambda _: None), r.DEFAULT_HOST)
        with patch.object(r.subprocess, "run", return_value=CompletedProcess([], 0, b"1000000\n0\n", b"")):
            self.assertEqual(r.select_host("auto", lambda _: None), "")
        with patch.object(r.subprocess, "run", return_value=CompletedProcess([], 0, b"3500000\n2\n", b"")):
            self.assertEqual(r.select_host("auto", lambda _: None), "")

    def test_explicit_host_is_preserved_without_probing_other_hosts(self):
        with patch.object(r.subprocess, "run", side_effect=AssertionError("Unexpected probe")):
            self.assertEqual(r.select_host("vidal@selected", lambda _: None), "vidal@selected")

    def fixture(self, folder):
        root = Path(folder) / "project"
        (root / "app/src/main").mkdir(parents=True)
        (root / "app/build.gradle.kts").write_text('plugins { id("com.android.application") }\n')
        (root / "app/src/main/RealUi.kt").write_text("@Composable fun RealUi() {}")
        (root / "app/src/main/private.key").write_text("private")
        (root / "app/src/main/.env").write_text("private")
        (root / "Fixture.kt").write_text("class ExplorerSourceRenderTest")
        config = root / "apk-explorer.render.json"
        profile = {"schema_version": 1, "engine": "paparazzi", "plugin_version": "1.3.5",
                   "module": "app", "variant": "Debug", "test_class": "ExplorerSourceRenderTest",
                   "fixture": "Fixture.kt", "inputs": ["app/build.gradle.kts", "app/src/main"],
                   "states": [{"test": "example", "title": "Example", "scope": "screen", "data": "test"}]}
        config.write_text(json.dumps(profile))
        return root, config

    def test_snapshot_preserves_source_and_excludes_private_files(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            original = (root / "app/build.gradle.kts").read_bytes()
            profile, work, archive, provenance = r.prepare(root, Path(folder) / "output")
            self.assertEqual((root / "app/build.gradle.kts").read_bytes(), original)
            self.assertIn("app.cash.paparazzi", (work / "app/build.gradle.kts").read_text())
            self.assertTrue((work / "app/src/explorerRender/Fixture.kt").exists())
            self.assertFalse((work / "app/src/main/private.key").exists())
            self.assertFalse((work / "app/src/main/.env").exists())
            self.assertTrue(archive.exists())
            self.assertEqual(provenance["files"]["app/build.gradle.kts"], r.hashlib.sha256(original).hexdigest())

    def test_profile_cannot_escape_project(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            profile = json.loads(config.read_text())
            profile["requires"] = ["../outside.kt"]
            config.write_text(json.dumps(profile))
            with self.assertRaises(ValueError):
                r.profile_for(root)

    def test_snapshot_rejects_whole_workspace_input(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            profile = json.loads(config.read_text())
            profile["inputs"] = ["."]
            config.write_text(json.dumps(profile))
            with self.assertRaisesRegex(ValueError, "raíz"):
                r.prepare(root, root / "sessions/output")

    def test_missing_sources_are_not_rendered_as_placeholders(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            profile, _, _ = r.profile_for(root)
            output = Path(folder) / "output"
            output.mkdir()
            result = r.collect(output, profile, {"project": str(root)}, "")
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["missing_states"], ["example"])
            self.assertEqual(result["states"], [])
            self.assertFalse(result["device_used"])

    def test_invalid_image_does_not_count_as_rendered(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            profile, _, _ = r.profile_for(root)
            output = Path(folder) / "output"
            (output / "rendered").mkdir(parents=True)
            (output / "rendered/Example_Test_example.png").write_bytes(b"not an image")
            result = r.collect(output, profile, {"project": str(root)}, "")
            self.assertEqual(result["status"], "partial")
            self.assertEqual(result["states"], [])
            self.assertEqual(result["invalid_images"], ["Example_Test_example.png"])

    def test_build_error_is_reported_and_not_a_render_success(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            with patch.object(r, "logged_run", side_effect=RuntimeError("compile failed")):
                result = r.render_source(root, Path(folder) / "output", host="vidal@host", log=lambda _: None)
            self.assertEqual(result["status"], "error")
            self.assertIn("compile failed", result["error"])
            self.assertEqual(result["states"], [])

    def test_cli_lists_source_states_without_using_adb(self):
        with tempfile.TemporaryDirectory() as folder:
            root, config = self.fixture(folder)
            stdout = io.StringIO()
            with patch.object(e, "Android", side_effect=AssertionError("No ADB")), contextlib.redirect_stdout(stdout):
                e.main(["--render", str(root), "--render-list", "--json"])
            data = json.loads(stdout.getvalue())
            self.assertEqual(data["mode"], "source_render")
            self.assertEqual(data["states"][0]["test"], "example")


if __name__ == "__main__":
    unittest.main()
