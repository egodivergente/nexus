import unittest
from unittest import mock

import flow_watchdog


class _Stop(Exception):
    pass


class WatchdogTest(unittest.TestCase):
    def test_backoff_grows_and_caps(self):
        values, current = [], 0
        for _ in range(8):
            current = flow_watchdog.next_backoff(current)
            values.append(current)
        self.assertEqual(values[:4], [15, 30, 60, 120])
        self.assertEqual(values[-1], flow_watchdog.BACKOFF_MAX)

    def test_restarts_after_threshold_and_respects_backoff(self):
        args = flow_watchdog.parse_args(['--url', 'http://127.0.0.1:1/', '--restart', 'echo x', '--failures', '1',
                                         '--log-file', self._log()])
        ticks = iter(range(0, 10_000, 20))
        sleeps = []

        def sleep(_):
            sleeps.append(1)
            if len(sleeps) >= 6:
                raise _Stop()

        with mock.patch.object(flow_watchdog, 'alive', return_value=(False, 'URLError')), \
                mock.patch.object(flow_watchdog, 'restart', return_value=True) as restart:
            with self.assertRaises(_Stop):
                flow_watchdog.run(args, sleep=sleep, clock=lambda: next(ticks))
        self.assertEqual(restart.call_count, 3)

    def test_healthy_bridge_is_never_restarted(self):
        args = flow_watchdog.parse_args(['--restart', 'echo x', '--log-file', self._log()])
        calls = []

        def sleep(_):
            calls.append(1)
            if len(calls) >= 5:
                raise _Stop()

        with mock.patch.object(flow_watchdog, 'alive', return_value=(True, 'http 200')), \
                mock.patch.object(flow_watchdog, 'restart') as restart:
            with self.assertRaises(_Stop):
                flow_watchdog.run(args, sleep=sleep, clock=lambda: 0)
        restart.assert_not_called()

    def test_dry_run_does_not_execute(self):
        logger = flow_watchdog.make_logger(self._log())
        with mock.patch.object(flow_watchdog.subprocess, 'run') as run:
            self.assertTrue(flow_watchdog.restart(['systemctl', '--user', 'restart', 'x'], logger, dry_run=True))
        run.assert_not_called()

    def test_restart_args(self):
        self.assertEqual(flow_watchdog.restart_args('systemctl --user restart a.service', ''),
                         ['systemctl', '--user', 'restart', 'a.service'])
        self.assertEqual(flow_watchdog.restart_args('', r'C:\x\r.ps1')[-1], r'C:\x\r.ps1')
        self.assertEqual(flow_watchdog.restart_args('', ''), [])

    def _log(self):
        import tempfile, os
        return os.path.join(tempfile.mkdtemp(), 'w.log')


if __name__ == '__main__':
    unittest.main()
