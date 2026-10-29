"""Windows launch regression: bounded recovery and work-area/theme continuity."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import local_host as host


class ExistingWorkspace(unittest.TestCase):
    def test_healthy_instance_is_reused_without_a_process_command(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{"app":"Nexus Studio"}'
        with patch.object(host.urllib.request, 'urlopen', return_value=response) as request, \
                patch.object(host.subprocess, 'run', side_effect=AssertionError('Must preserve the live instance')):
            self.assertEqual(host.existing_host_url({'port': 63896}), 'http://127.0.0.1:63896/')
            request.assert_called_once_with('http://127.0.0.1:63896/health', timeout=0.5)

    def test_invalid_or_unrelated_session_does_not_open_any_network_address(self):
        with patch.object(host.urllib.request, 'urlopen') as request:
            for value in [None, '', 0, -1, 65536, 'evil.example/path', '12/other']:
                self.assertIsNone(host.existing_host_url({'port': value}))
            request.assert_not_called()

    def test_stale_instance_recovers_without_a_slow_windows_process_query(self):
        with patch.object(host.urllib.request, 'urlopen', side_effect=TimeoutError), \
                patch.object(host.subprocess, 'run', side_effect=AssertionError('No WMI')):
            self.assertIsNone(host.existing_host_url({'port': 63896}))

    def test_unrelated_loopback_service_is_not_accepted_as_nexus(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{"app":"Other app"}'
        with patch.object(host.urllib.request, 'urlopen', return_value=response):
            self.assertIsNone(host.existing_host_url({'port': 63896}))


class NativeWindow(unittest.TestCase):
    def launch(self, width, height, available=True):
        def area(_action, _parameter, rectangle, _flags):
            rectangle._obj.right = width
            rectangle._obj.bottom = height
            return available

        with tempfile.TemporaryDirectory() as folder, patch.object(host, 'DATA', Path(folder)), \
                patch.object(host, 'edge_path', return_value='msedge.exe'), \
                patch.object(host.ctypes.windll.user32, 'SystemParametersInfoW', side_effect=area), \
                patch.object(host.subprocess, 'Popen') as process:
            host.open_window('http://127.0.0.1:63896/')
            return process.call_args.args[0]

    def test_elitebook_window_fits_above_taskbar(self):
        args = self.launch(1366, 728)
        self.assertIn('--window-size=1280,728', args)
        self.assertNotIn('--force-dark-mode', args)

    def test_small_work_area_and_wide_monitor(self):
        self.assertIn('--window-size=800,600', self.launch(800, 600))
        self.assertIn('--window-size=1280,840', self.launch(2560, 1400))

    def test_work_area_unavailable_has_a_conservative_fallback(self):
        self.assertIn('--window-size=1280,720', self.launch(0, 0, available=False))


if __name__ == '__main__':
    unittest.main()
