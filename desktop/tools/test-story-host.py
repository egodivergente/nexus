"""Protect concurrent story editing and the private Flow Live boundary, without APIs."""
import queue
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from agent_host import AgentHost
import flow_live_proxy as proxy


class StoryHostTest(unittest.TestCase):
    def test_story_uses_same_engine_while_chat_owns_main_lock(self):
        with tempfile.TemporaryDirectory() as folder:
            host = AgentHost(Path(folder), Path(folder), lambda _: {}, lambda *_: None)
            host.start = Mock()
            active = {'id': 'chat-1', 'op': 'send', 'owner': 'window-1', 'turn_id': 'turn-1'}
            host.active_exchange = active
            host.lock.acquire()
            writes = []
            def write(message):
                writes.append(message)
                host.story_pending[message['requestId']].put({
                    'ok': True, 'requestId': message['requestId'], 'value': {'revision': 2}})
            host.write = write
            try:
                result = host.command({'owner': 'window-1', 'op': 'storyCommand',
                                       'args': {'projectId': 'p', 'input': {'action': 'update_block'}}})
                self.assertEqual(result['result'], {'revision': 2})
                self.assertIs(host.active_exchange, active)
                self.assertEqual(len(writes), 1)
                self.assertEqual(writes[0]['op'], 'storyCommand')
                self.assertEqual(host.story_pending, {})
                self.assertIn(writes[0]['requestId'], host.story_request_ids)
            finally:
                host.lock.release()

    def test_ambiguous_edit_timeout_never_replays_or_stops_chat(self):
        with tempfile.TemporaryDirectory() as folder:
            host = AgentHost(Path(folder), Path(folder), lambda _: {}, lambda *_: None)
            host.start, host.write, host.close = Mock(), Mock(), Mock()
            with patch('agent_host.queue.Queue') as make_queue:
                make_queue.return_value.get.side_effect = queue.Empty()
                with self.assertRaisesRegex(RuntimeError, 'Comprueba el contenido'):
                    host.story_command({'owner': 'window-1', 'op': 'storyCommand', 'args': {}})
            host.write.assert_called_once()
            host.close.assert_not_called()
            self.assertEqual(host.story_pending, {})


class FlowBoundaryTest(unittest.TestCase):
    def handler(self, path, headers=None):
        value = Mock()
        value.path, value.headers = path, headers or {}
        value.server.server_port, value.server.token = 63896, 'local-session'
        return value

    @patch.object(proxy, '_http')
    def test_denies_missing_session_and_arbitrary_paths(self, upstream):
        bad = self.handler('/flow-live/health')
        proxy.route(bad)
        bad.send_error.assert_called_with(403)
        unsupported = self.handler('/flow-live/arbitrary?token=local-session')
        proxy.route(unsupported)
        unsupported.send_error.assert_called_with(404)
        upstream.assert_not_called()

    @patch.object(proxy, '_websocket')
    def test_websocket_requires_exact_loopback_origin(self, upstream):
        bad = self.handler('/flow-live/live?token=local-session', {'Origin': 'https://other.example'})
        proxy.route(bad)
        bad.send_error.assert_called_with(403)
        good = self.handler('/flow-live/live?token=local-session', {'Origin': 'http://127.0.0.1:63896'})
        proxy.route(good)
        upstream.assert_called_once_with(good)

    @patch.object(proxy, '_http')
    def test_offline_profile_never_connects_flow(self, upstream):
        handler = self.handler('/flow-live/health?token=local-session')
        proxy.route(handler, offline=True)
        self.assertEqual(handler.respond.call_args.args[-1], 503)
        upstream.assert_not_called()


if __name__ == '__main__':
    unittest.main()
