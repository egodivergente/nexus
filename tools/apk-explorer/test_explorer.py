import importlib.util
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import struct
import zlib
import unittest
from unittest.mock import Mock, patch

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
    def test_quick_mode_prioritizes_navigation_over_chat_and_scroll(self):
        actions = [e.Action("scroll", (0, 200, 100, 800), "Chat"),
                   e.Action("tap", (0, 300, 100, 350), "Detalle del mensaje"),
                   e.Action("tap", (0, 900, 100, 1000), "Lienzo"),
                   e.Action("tap", (0, 900, 100, 1000), "Modelos")]
        self.assertEqual([a.label for a in e.navigation_first(actions)],
                         ["Lienzo", "Modelos", "Detalle del mensaje", "Chat"])

    def test_capture_waits_for_splash_to_settle(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            device.dump = Mock(side_effect=[
                screen("Splash"), screen("App lista"), screen("App lista")])
            runner = e.Explorer(device, PKG, Path(d) / "run", delay=.001)
            raw = runner.stable_dump()
            self.assertEqual(e.inspect(raw, PKG)[1], "App lista")
            self.assertEqual(device.dump.call_count, 3)

    def test_agent_json_escapes_symbols_for_windows_consoles(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            device.dump = lambda: screen("⚙ Opciones")
            device.screenshot = self.png
            output = io.StringIO()
            with patch.object(e, "Android", return_value=device), contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                e.main(["--inspect", PKG, "--serial", "test", "--json", "--capture-only", "--delay", ".001",
                        "--output", str(Path(d) / "run")])
            self.assertTrue(output.getvalue().isascii())
            self.assertEqual(json.loads(output.getvalue())["screens"][0]["title"], "⚙ Opciones")

    @staticmethod
    def png():
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
        raw = b"".join(b"\0" + bytes((30, 80, 150) if y % 20 < 10 else (200, 210, 220)) * 80 for y in range(160))
        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 80, 160, 8, 2, 0, 0, 0)) +
                chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

    def test_agent_cli_detects_phone_and_emits_only_json(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            device.screenshot = self.png
            found = [{"serial": "test", "model": "SM_S938B", "label": "S25"}]
            output = io.StringIO()
            with patch.object(e, "Android", return_value=device), patch.object(e, "GitHub", side_effect=AssertionError("Unneeded GitHub call")), \
                 patch.object(e, "discover_devices", return_value=("", found, [])), patch.object(e, "load_settings", return_value=e.DEFAULT_SETTINGS.copy()), \
                 contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                e.main(["--inspect", PKG, "--json", "--delay", ".001", "--output", str(Path(d) / "run")])
            manifest = json.loads(output.getvalue())
            self.assertEqual(manifest["coverage"]["app_states"], 3)
            self.assertFalse(manifest["coverage"]["complete"])
            self.assertTrue(Path(manifest["manifest"]).exists())
            self.assertTrue(all(s["integrity_verified"] for s in manifest["screens"]))

    def test_agent_apk_infers_package_before_install(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / "private.apk"
            apk.write_bytes(b"PKfixture")
            device = FakeAndroid()
            device.install = lambda path: None
            device.screenshot = self.png
            with patch.object(e, "Android", return_value=device), patch.object(e, "apk_package", return_value=PKG) as infer, \
                 patch.object(e, "load_settings", return_value=e.DEFAULT_SETTINGS.copy()), contextlib.redirect_stdout(io.StringIO()):
                e.main(["--inspect", str(apk), "--serial", "test", "--max-states", "1", "--delay", ".001", "--output", str(Path(d) / "run")])
            infer.assert_called_once_with(apk)
            report = json.loads((Path(d) / "run/report.json").read_text(encoding="utf-8"))
            self.assertEqual(report["package"], PKG)
            self.assertIn("sha256", report["apk"])

    def test_breadth_visits_siblings_before_detail(self):
        class Nested(FakeAndroid):
            def dump(self):
                if self.state == "gallery":
                    return screen("Galería", button("Detalle", 500))
                if self.state == "detail":
                    return screen("Detalle")
                return super().dump()

            def shell(self, *args):
                if self.state == "gallery" and args[:2] == ("input", "tap"):
                    self.state = "detail"
                else:
                    super().shell(*args)
        with tempfile.TemporaryDirectory() as d:
            runner = e.Explorer(Nested(), PKG, Path(d) / "run", delay=.001, strategy="breadth")
            runner.run()
            self.assertEqual([x["action"] for x in runner.edges], ["Galería", "Ajustes", "Detalle"])

    def test_bundle_exposes_pending_actions_without_touching_device(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            device.screenshot = self.png
            runner = e.Explorer(device, PKG, Path(d) / "run", max_states=2, delay=.001)
            runner.run()
            with patch.object(e, "Android", side_effect=AssertionError("Must not access ADB")):
                bundle = e.agent_bundle(runner.output)
            self.assertEqual(bundle["coverage"]["pending_actions"], 1)
            self.assertEqual(bundle["pending_actions"][0]["label"], "Ajustes")
            self.assertEqual(bundle["status"], "limit_reached")
            try:
                import PIL
            except ImportError:
                return
            self.assertTrue(Path(bundle["overviews"][0]).exists())

    def test_bundle_detects_modified_capture(self):
        with tempfile.TemporaryDirectory() as d:
            device = FakeAndroid()
            device.screenshot = self.png
            runner = e.Explorer(device, PKG, Path(d) / "run")
            runner.run(capture_only=True)
            image = runner.output / "screen-0001.png"
            image.write_bytes(image.read_bytes() + b"modified")
            bundle = e.agent_bundle(runner.output)
            self.assertFalse(bundle["screens"][0]["integrity_verified"])
            self.assertIn("digest", bundle["screens"][0]["capture_warning"])

    def test_preferences_defaults_and_roundtrip(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "settings.json"
            self.assertEqual(e.load_settings(path)["repo"], "")
            e.save_settings({"host": e.ASUS_HOST, "serial": "phone:123", "repo": "owner/private",
                             "token": "must-not-be-saved"}, path)
            restored = e.load_settings(path)
            self.assertEqual(restored["host"], e.ASUS_HOST)
            self.assertEqual(restored["serial"], "phone:123")
            self.assertNotIn("token", path.read_text())

    def test_invalid_preferences_recover(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "settings.json"
            path.write_text('{"minutes": 9000, "max_states": false, "host": null}')
            result = e.load_settings(path)
            self.assertEqual(result["minutes"], 15)
            self.assertEqual(result["max_states"], 60)
            self.assertEqual(result["host"], "")
            path.write_text("broken json")
            self.assertEqual(e.load_settings(path), e.DEFAULT_SETTINGS)

    def test_phone_selection_survives_port_changes(self):
        devices = e.parse_devices("List of devices attached\n"
                                  "tv:5555 device model:BeyondTV\n"
                                  "pixel:4321 device model:Pixel_8_Pro\n"
                                  "samsung:5555 device model:SM_S938B\n"
                                  "offline:123 offline model:SM_S938B\n")
        self.assertEqual(len(devices), 3)
        self.assertEqual(e.preferred_device(devices)["serial"], "samsung:5555")
        self.assertEqual(e.preferred_device(devices, "old-pixel:987", "Pixel_8_Pro")["serial"], "pixel:4321")

    def test_discovery_falls_back_to_asus(self):
        with patch.object(e.Android, "devices", side_effect=["List of devices attached\n",
                    "List of devices attached\nphone:123 device model:SM_S938B\n"]):
            host, devices, errors = e.discover_devices("", fallback=True)
            self.assertEqual(host, e.ASUS_HOST)
            self.assertEqual(devices[0]["serial"], "phone:123")
            self.assertEqual(errors, [])

    def test_manual_connection_does_not_switch_hosts(self):
        with patch.object(e.Android, "devices", return_value="List of devices attached\n") as fetch:
            host, devices, _ = e.discover_devices(e.ASUS_HOST)
            self.assertEqual(host, e.ASUS_HOST)
            self.assertEqual(devices, [])
            self.assertEqual(fetch.call_count, 1)

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
