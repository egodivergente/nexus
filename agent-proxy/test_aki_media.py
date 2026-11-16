import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from aki_media import prepare
from aki_proxy import AkiClient, MODEL
from nexus_proxy import ProxyError


class MediaTest(unittest.TestCase):
    def request(self, block):
        return {"modelId": MODEL, "messages": [{"role": "user", "content": [block]}]}

    def test_image_profile_exact_guard_and_cleanup(self):
        request = self.request({"image": {"format": "png", "source": {"bytes": b"fixture"}}})
        with tempfile.TemporaryDirectory() as root:
            with prepare(request, root) as (converted, name, commands):
                profile = Path(root) / name
                command = next(iter(commands))
                image = Path(command.removeprefix("view "))
                self.assertEqual(image.read_bytes(), b"fixture")
                self.assertNotIn("bytes", json.dumps(converted))
                if os.name == "posix":
                    self.assertEqual(profile.stat().st_mode & 0o777, 0o700)
                hooks = json.loads((profile / "hooks.json").read_text())
                self.assertIn("PreToolUse", hooks["hooks"])
                for tool, text, code in [("Bash", command, 0), ("Bash", command + "; id", 2),
                                         ("Bash", "cat /etc/passwd", 2), ("Read", command, 2)]:
                    result = subprocess.run([sys.executable, str(profile / "guard.py")],
                        input=json.dumps({"tool_name": tool, "tool_input": {"command": text}}),
                        text=True, capture_output=True)
                    self.assertEqual(result.returncode, code)
            self.assertFalse(profile.exists())
            self.assertFalse(image.exists())

    def test_text_document(self):
        req = self.request({"document": {"format": "txt", "source": {"bytes": "café".encode()}}})
        with prepare(req) as (converted, profile, commands):
            self.assertIn("café", converted["messages"][0]["content"][0]["text"])
            self.assertEqual(profile, "nexus-bridge")
            self.assertFalse(commands)

    def test_office_and_signed_context_explicit_error(self):
        for block in [{"document": {"format": "docx", "source": {"bytes": b"data"}}},
                      {"reasoningContent": {}}]:
            with self.assertRaises(ProxyError):
                with prepare(self.request(block)):
                    self.fail("must reject")

    def test_pdf_does_not_silently_truncate(self):
        with patch("aki_media._run", return_value=b"Pages: 21\n"):
            with self.assertRaises(ProxyError):
                with prepare(self.request({"document": {"format": "pdf", "source": {"bytes": b"pdf"}}})):
                    self.fail("must reject")

    def test_visual_events_required_and_host_actions_rejected(self):
        request = self.request({"image": {"format": "png", "source": {"bytes": b"fixture"}}})
        for mode in ("ok", "missing", "host", "failed"):
            def run(command, **kwargs):
                profile = Path.home() / ".aki/profiles_v3" / command[2]
                view = json.loads((profile / "allowed.json").read_text())[0]
                events = []
                if mode != "missing":
                    events = [{"type": "tool_use", "name": "Bash", "input": {"command": view if mode != "host" else "id"}},
                              {"type": "tool_result", "name": "Bash", "status": "done", "output": "error" if mode == "failed" else ""}]
                events += [{"type": "text_delta", "content": '{"content":[{"text":"Seen"}],"stopReason":"end_turn"}'},
                           {"type": "done", "total_tools": int(mode != "missing"), "input_tokens": 5, "output_tokens": 5}]
                return subprocess.CompletedProcess(command, 0, "\n".join(map(json.dumps, events)), "")
            client = AkiClient("aki", "/tmp", runner=run)
            if mode == "ok":
                self.assertEqual(client.converse(**request)["stopReason"], "end_turn")
            else:
                with self.assertRaises(ProxyError): client.converse(**request)

    def _events_client(self, make_events, models=(MODEL,)):
        def run(command, **kwargs):
            profile = Path.home() / ".aki/profiles_v3" / command[2]
            views = json.loads((profile / "allowed.json").read_text())
            events = make_events(views) + [
                {"type": "text_delta", "content": '{"content":[{"text":"Seen"}],"stopReason":"end_turn"}'}]
            tools = sum(e["type"] == "tool_result" for e in events)
            events.append({"type": "done", "total_tools": tools, "input_tokens": 5, "output_tokens": 5})
            return subprocess.CompletedProcess(command, 0, "\n".join(map(json.dumps, events)), "")
        return AkiClient("aki", "/tmp", runner=run, allowed_models=models)

    def test_blocked_chained_attempt_is_tolerated_if_every_view_still_happens(self):
        request = self.request({"image": {"format": "png", "source": {"bytes": b"fixture"}}})
        def events(views):
            use = lambda c: {"type": "tool_use", "name": "Bash", "input": {"command": c}}
            res = lambda st: {"type": "tool_result", "name": "Bash", "status": st, "output": ""}
            return [use(views[0] + "; id"), res("error"), use(" " + views[0] + " "), res("done")]
        self.assertEqual(self._events_client(events).converse(**request)["stopReason"], "end_turn")

    def test_attempt_that_actually_ran_is_rejected(self):
        request = self.request({"image": {"format": "png", "source": {"bytes": b"fixture"}}})
        def events(views):
            use = lambda c: {"type": "tool_use", "name": "Bash", "input": {"command": c}}
            res = lambda: {"type": "tool_result", "name": "Bash", "status": "done", "output": ""}
            return [use(views[0] + "; id"), res(), use(views[0]), res()]
        with self.assertRaises(ProxyError): self._events_client(events).converse(**request)

    def test_blocked_attempts_without_viewing_everything_still_fail(self):
        request = self.request({"image": {"format": "png", "source": {"bytes": b"fixture"}}})
        def events(views):
            return [{"type": "tool_use", "name": "Bash", "input": {"command": "id"}},
                    {"type": "tool_result", "name": "Bash", "status": "error", "output": ""}]
        with self.assertRaises(ProxyError): self._events_client(events).converse(**request)

    def test_configured_haiku_is_exact_allowlist(self):
        haiku = "anthropic.claude-haiku-5-5"
        request = {"modelId": haiku, "messages": [{"role": "user", "content": [{"text": "hola"}]}]}
        def run(command, **kwargs):
            assert command[command.index("--model") + 1] == haiku
            events = [{"type": "text_delta", "content": '{"content":[{"text":"ok"}],"stopReason":"end_turn"}'},
                      {"type": "done", "total_tools": 0, "input_tokens": 1, "output_tokens": 1}]
            return subprocess.CompletedProcess(command, 0, "\n".join(map(json.dumps, events)), "")
        self.assertEqual(AkiClient("aki", "/tmp", runner=run, allowed_models=(haiku,)).converse(**request)["stopReason"], "end_turn")
        with self.assertRaises(ProxyError):
            AkiClient("aki", "/tmp", runner=run, allowed_models=(MODEL,)).converse(**request)


if __name__ == "__main__": unittest.main()
