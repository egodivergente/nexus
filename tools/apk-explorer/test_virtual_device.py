import tempfile
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

import explorer as e
import virtual_device as v


class VirtualTests(unittest.TestCase):
    def device(self):
        with patch.object(e, "executable", return_value="adb"):
            device = v.VirtualAndroid("test")
        device.display = 7
        device.package = "com.example.app"
        device.scrcpy = Mock()
        device.scrcpy.poll.return_value = None
        return device

    def test_routes_all_input_to_virtual_display(self):
        device = self.device()
        with patch.object(device, "ensure_display"), patch.object(e.Android, "shell") as shell:
            device.shell("input", "tap", 20, 30)
            shell.assert_called_once_with("input", "-d", 7, "tap", 20, 30)

    def test_no_force_stop(self):
        device = self.device()
        with patch.object(e.Android, "shell") as shell:
            with self.assertRaises(RuntimeError):
                device.shell("am", "force-stop", "--user", "0", device.package)
            shell.assert_not_called()

    def test_missing_display_never_falls_back(self):
        device = self.device()
        with patch.object(device, "guard_main", return_value={0: "main"}), patch.object(device, "call") as call:
            with self.assertRaises(RuntimeError):
                device.shell("input", "tap", 20, 30)
            call.assert_not_called()

    def test_stop_if_user_opens_target_app(self):
        device = self.device()
        windows = "Display: mDisplayId=0\n mFocusedApp=com.example.app/.Main\nDisplay: mDisplayId=7\n mCurrentFocus=null"
        with patch.object(e.Android, "shell", return_value=windows):
            with self.assertRaises(RuntimeError):
                device.guard_main()

    def test_rejects_physical_hierarchy(self):
        device = self.device()
        with patch.object(device, "request", return_value=b'<hierarchy display-id="0"/>'):
            with self.assertRaises(RuntimeError):
                device.dump()

    def test_explicit_launch_display(self):
        device = self.device()
        with patch.object(device, "ensure_display"), patch.object(e.Android, "shell", side_effect=["com.example.app/.Main", "Success"]) as shell:
            device.launch(device.package)
            self.assertEqual(shell.call_args.args, ("am", "start", "--user", "0", "--display", 7, "-n", "com.example.app/.Main"))

    def test_run_cleans_up_even_when_begin_fails(self):
        device = Mock(virtual=True, serial="test", host="", evidence={})
        device.validate.return_value = "model"
        device.begin.side_effect = RuntimeError("unavailable")
        with tempfile.TemporaryDirectory() as folder:
            runner = e.Explorer(device, "com.example.app", Path(folder)/"run")
            with self.assertRaises(RuntimeError):
                runner.run()
        device.close.assert_called_once()

    def test_continuous_walk_does_not_reset_or_launch(self):
        device = Mock(virtual=True)
        with tempfile.TemporaryDirectory() as folder:
            runner = e.Explorer(device, "com.example.app", Path(folder), delay=.001)
            runner.observe = Mock(return_value=("child", []))
            runner.save = Mock()
            runner.crawl_virtual("root", [e.Action("tap", (0, 0, 100, 100), "Modelos")])
        device.shell.assert_called_once_with("input", "tap", 50, 50)
        device.launch.assert_not_called()


if __name__ == "__main__":
    unittest.main()
