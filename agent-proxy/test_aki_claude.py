import json
from pathlib import Path
import subprocess
import unittest

from aki_openai import from_converse, to_converse
from aki_proxy import AkiClient, CLAUDE_MODELS, EFFORTS, resolve_model
from nexus_proxy import ProxyError


class ClaudeBridgeTest(unittest.TestCase):
    def test_every_model_and_effort_reaches_aki_profile(self):
        for model in CLAUDE_MODELS:
            for effort in EFFORTS:
                def run(command, **kwargs):
                    self.assertEqual(command[command.index("--model") + 1], model)
                    profile = Path.home() / ".aki/profiles_v3" / command[2]
                    llm = json.loads((profile / "manifest.json").read_text())["llm"]
                    self.assertEqual(llm["modelId"], model)
                    self.assertEqual(llm["effort"], effort)
                    self.assertTrue(llm["reasoning"]["enabled"])
                    return subprocess.CompletedProcess(command, 0, '\n'.join(map(json.dumps, [
                        {"type": "text_delta", "content": '{"content":[{"text":"OK"}],"stopReason":"end_turn"}'},
                        {"type": "done", "input_tokens": 3, "output_tokens": 4, "total_tools": 0}])), "")
                AkiClient("aki", "/tmp", runner=run).converse(modelId=model + "@" + effort,
                    messages=[{"role": "user", "content": [{"text": "Hi"}]}])

    def test_invalid_effort_and_silent_model_fallback_rejected(self):
        with self.assertRaises(ProxyError):
            resolve_model(next(iter(CLAUDE_MODELS)), "minimal")
        def run(command, **kwargs):
            return subprocess.CompletedProcess(command, 0, "", "[profile] unknown model 'x', using default")
        with self.assertRaises(ProxyError) as error:
            AkiClient("aki", "/tmp", runner=run).converse(modelId=next(iter(CLAUDE_MODELS)),
                messages=[{"role": "user", "content": [{"text": "Hi"}]}])
        self.assertEqual(error.exception.code, "aki_model_unavailable")

    def test_chat_tool_round_trip(self):
        body = {"model": next(iter(CLAUDE_MODELS)) + "@max", "reasoning_effort": "high", "messages": [
            {"role": "system", "content": "Be concise"},
            {"role": "user", "content": "settings"},
            {"role": "assistant", "content": None, "tool_calls": [{"id": "one", "function": {
                "name": "settings", "arguments": "{}"}}]},
            {"role": "tool", "tool_call_id": "one", "content": "{}"}],
            "tools": [{"function": {"name": "settings", "parameters": {"type": "object"}}}]}
        req = to_converse(body)
        self.assertEqual(req["akiEffort"], "high")
        self.assertEqual(req["messages"][-1]["content"][0]["toolResult"]["toolUseId"], "one")
        response = from_converse({"output": {"message": {"content": req["messages"][-2]["content"]}},
                                  "usage": {"inputTokens": 2, "outputTokens": 3}}, body["model"])
        self.assertEqual(response["choices"][0]["finish_reason"], "tool_calls")
        self.assertEqual(response["usage"]["total_tokens"], 5)


if __name__ == "__main__":
    unittest.main()
