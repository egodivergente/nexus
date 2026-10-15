import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("explorer", Path(__file__).with_name("explorer.py"))
e = importlib.util.module_from_spec(spec)
sys.modules["explorer"] = e
spec.loader.exec_module(e)
PKG = "com.example.privateapp"


def screen(title="Inicio", children="", package=PKG):
    return (f'<hierarchy><node package="{package}" class="FrameLayout" bounds="[0,0][400,800]" '
            f'enabled="true" text="{title}">{children}</node></hierarchy>')


def button(label, y=100, children="", **attrs):
    extra = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in attrs.items())
    return (f'<node package="{PKG}" class="Button" clickable="true" enabled="true" '
            f'text="{label}" bounds="[0,{y}][100,{y+80}]" {extra}>{children}</node>')


class FakeAndroid:
    serial = "test"
    host = ""

    def __init__(self):
        self.state = "home"
        self.taps = []
        self.fail = False

    def validate(self):
        return "Synthetic device"

    def launch(self, package):
        self.state = "home"

    def dump(self):
        if self.fail:
            raise RuntimeError("disconnected")
        return {"home": screen(children=button("Galería") + button("Ajustes", 300)),
                "gallery": screen("Galería"), "settings": screen("Ajustes")}[self.state]

    def screenshot(self):
        return b"\x89PNG\r\n\x1a\nfixture"

    def shell(self, *args):
        if args[:2] == ("input", "tap"):
            self.taps.append((args[2], args[3]))
            self.state = "gallery" if args[3] < 250 else "settings"


class ExplorerTests(unittest.TestCase):
    def test_branch_replay_and_gallery(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            runner = e.Explorer(device, PKG, Path(d) / "run", delay=.001)
            gallery = runner.run()
            report = json.loads(gallery.with_name("report.json").read_text(encoding="utf-8"))
            self.assertEqual(len(report["states"]), 3)
            self.assertEqual(len(report["edges"]), 2)
            self.assertEqual(report["status"], "finished_partial")
            self.assertEqual(device.taps, [(50, 140), (50, 340)])
            self.assertEqual(report["states"][2]["route"], ["Ajustes"])
            self.assertTrue(gallery.exists())

    def test_sensitive_nested_labels_and_edit_controls(self):
        nested = '<node package="com.example.privateapp" text="Generar vídeo"/>'
        xml = screen(children=button("", children=nested) + button("Comprar", 200) +
                     button("Perfil", 300) + button("", 400, resource_id="app:id/deleteButton") +
                     button("Correo", 500, children='<node class="EditText"/>'))
        _, _, actions, skipped, owned = e.inspect(xml, PKG)
        self.assertTrue(owned)
        self.assertEqual([a.label for a in actions], ["Perfil"])
        self.assertEqual(len(skipped), 4)

    def test_foreign_dialog_is_boundary(self):
        xml = screen(children=button("Perfil") + '<node package="com.android.permissioncontroller"/>')
        self.assertFalse(e.inspect(xml, PKG)[4])
        self.assertEqual(e.inspect(xml, PKG)[2], [])

    def test_keyguard_is_boundary_even_with_app_behind(self):
        xml = screen(children=button("Perfil") + '<node package="com.android.systemui" resource-id="element:lockscreen"/>')
        self.assertFalse(e.inspect(xml, PKG)[4])

    def test_github_latest_includes_prerelease(self):
        with patch.object(e, "executable", return_value="gh"):
            github = e.GitHub("owner/repo")
            with patch.object(github, "api", return_value=[
                {"draft": True, "tag_name": "draft", "assets": []},
                {"draft": False, "prerelease": True, "tag_name": "v2-preview",
                 "assets": [{"name": "app.apk"}, {"name": "source.zip"}]}]):
                tag, assets = github.assets()
                self.assertEqual(tag, "v2-preview")
                self.assertEqual(len(assets), 1)

    def test_github_digest_mismatch_rejected(self):
        with patch.object(e, "executable", return_value="gh"), tempfile.TemporaryDirectory() as d:
            github = e.GitHub("owner/repo")
            with patch.object(e, "command", return_value=b"PKtest"):
                with self.assertRaisesRegex(RuntimeError, "SHA-256"):
                    github.download({"name": "app.apk", "id": 1, "digest": "sha256:bad"}, Path(d))
                self.assertFalse((Path(d) / "app.apk").exists())

    def test_unreproducible_root_stops_branch_replay(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            runner = e.Explorer(device, PKG, Path(d) / "run", delay=.001)
            device.launch = lambda package: setattr(device, "state", "settings")
            self.assertFalse(runner.restore(e.inspect(device.dump(), PKG)[0], []))
            self.assertEqual(device.taps, [])
            self.assertIn("inicial cambió", runner.notes[0])

    def test_state_limit(self):
        with tempfile.TemporaryDirectory() as d:
            runner = e.Explorer(FakeAndroid(), PKG, Path(d) / "run", max_states=2, delay=.001)
            runner.run()
            self.assertEqual(len(runner.states), 2)
            self.assertEqual(runner.report["status"], "limit_reached")

    def test_capture_only_has_no_navigation(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            runner = e.Explorer(device, PKG, Path(d) / "run")
            runner.run(capture_only=True)
            self.assertEqual(device.taps, [])
            self.assertEqual(runner.report["status"], "capture_only")

    def test_failure_keeps_report(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            device.fail = True
            runner = e.Explorer(device, PKG, Path(d) / "run")
            with self.assertRaisesRegex(RuntimeError, "disconnected"):
                runner.run(capture_only=True)
            report = json.loads((runner.output / "report.json").read_text())
            self.assertEqual(report["status"], "error")

    def test_gallery_escapes_app_text(self):
        with tempfile.TemporaryDirectory() as d:
            runner = e.Explorer(FakeAndroid(), PKG, Path(d) / "run")
            runner.run(capture_only=True)
            runner.report["states"][0]["title"] = '<script>alert(1)</script>'
            runner.save()
            self.assertNotIn('<script>alert(1)</script>', (runner.output / "index.html").read_text(encoding="utf-8"))

    def test_remote_shell_quoting(self):
        with patch.object(e, "executable", return_value="ssh"), patch.object(e, "command", return_value="ok") as run:
            e.Android("device:123", "vidal@host").shell("am", "start", "-n", "com.app/.Main")
            argv = run.call_args.args[0]
            self.assertIn("StrictHostKeyChecking=yes", argv)
            self.assertIn("adb -s device:123 shell 'am start -n com.app/.Main'", argv[-1])

    def test_github_download_rejects_path_traversal(self):
        with patch.object(e, "executable", return_value="gh"), tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError):
                e.GitHub("owner/repo").download({"id": 1, "name": "../private.apk"}, Path(d))

    def test_blocked_capture_returns_explicit_status(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            device.dump = lambda: screen(package="com.android.launcher")
            runner = e.Explorer(device, PKG, Path(d) / "run")
            runner.run(capture_only=True)
            self.assertEqual(runner.report["status"], "blocked")


if __name__ == "__main__":
    unittest.main()
