import json
import subprocess
import unittest
import tempfile
from pathlib import Path
from aki_proxy import AkiClient, MODEL
from nexus_proxy import ProxyError, Usage


class BridgeTest(unittest.TestCase):
    def request(self):
        return dict(modelId=MODEL, messages=[{"role":"user","content":[{"text":"settings"}]}],
                    toolConfig={"tools":[{"toolSpec":{"name":"get_nexus_settings"}}]})

    def client(self, answer, tools=0):
        def run(command, **kwargs):
            self.assertNotIn("AWS_SECRET_ACCESS_KEY", kwargs["env"])
            self.assertIn("nexus-bridge", command)
            self.assertIn("settings", kwargs["input"])
            data = [dict(type="text_delta", content=json.dumps(answer)),
                    dict(type="done", input_tokens=42, output_tokens=10, total_tools=tools)]
            return subprocess.CompletedProcess(command, 0, "\n".join(map(json.dumps, data)), "")
        return AkiClient("aki", "/tmp", runner=run)

    def test_text(self):
        result = self.client(dict(content=[{"text":"OK"}],stopReason="end_turn")).converse(**self.request())
        self.assertEqual(result["output"]["message"]["content"], [{"text":"OK"}])
        self.assertEqual(result["usage"]["inputTokens"],42)

    def test_tool_request(self):
        content=[{"toolUse":{"toolUseId":"one","name":"get_nexus_settings","input":{}}}]
        self.assertEqual(self.client(dict(content=content,stopReason="tool_use")).converse(**self.request())["stopReason"],"tool_use")

    def test_unknown_tool(self):
        content=[{"toolUse":{"toolUseId":"one","name":"Bash","input":{}}}]
        with self.assertRaises(ProxyError):
            self.client(dict(content=content,stopReason="tool_use")).converse(**self.request())

    def test_host_tools_rejected(self):
        with self.assertRaises(ProxyError):
            self.client(dict(content=[{"text":"OK"}],stopReason="end_turn"),1).converse(**self.request())

    def test_wrong_model(self):
        request=self.request();request["modelId"]="claude"
        with self.assertRaises(ProxyError): self.client({}).converse(**request)

    def test_attachment_rejected(self):
        request=self.request();request["messages"][0]["content"].append({"image":{}})
        with self.assertRaises(ProxyError): self.client({}).converse(**request)

    def test_explicit_disabled_token_cap_keeps_usd_cap(self):
        with tempfile.TemporaryDirectory() as directory:
            usage = Usage(Path(directory))
            usage.reserve(0, 900000, 5, None)
            with self.assertRaises(ProxyError): usage.reserve(6, 1, 5, None)

    def test_default_token_cap_still_enforced(self):
        with tempfile.TemporaryDirectory() as directory:
            usage = Usage(Path(directory))
            with self.assertRaises(ProxyError): usage.reserve(0, 500001, 5, 500000)


if __name__ == "__main__": unittest.main()
