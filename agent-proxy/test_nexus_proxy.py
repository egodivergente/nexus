"""Pruebas del proxy sin AWS: cliente Bedrock falso. python -m unittest test_nexus_proxy -v"""
import base64
import json
import tempfile
import unittest
from pathlib import Path

import nexus_proxy as np

TOKEN = "nxp_test_token"
MODEL = "test.claude-model"


class FakeBedrock:
    def __init__(self, error=None, usage=(40, 200)):
        self.calls, self.error, self.usage = [], error, usage

    def count_tokens(self, **req):
        return {"inputTokens": self.usage[0]}

    def converse(self, **req):
        self.calls.append(req)
        if self.error:
            raise self.error
        return {
            "output": {"message": {"role": "assistant", "content": [{"text": "hola"}, {"toolUse": {"toolUseId": "t1", "name": "list_models", "input": {}}}]}},
            "stopReason": "tool_use",
            # Como un modelo real: nunca devuelve más salida que maxTokens.
            "usage": {"inputTokens": self.usage[0], "outputTokens": min(self.usage[1], req["inferenceConfig"]["maxTokens"]), "totalTokens": 0},
            "ResponseMetadata": {"RequestId": "secret-ish"},
        }


class AwsError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.response = {"Error": {"Code": code, "Message": "detalle"}}


def body(**over):
    b = {"modelId": MODEL, "messages": [{"role": "user", "content": [{"text": "hola"}]}], "inferenceConfig": {"maxTokens": 100000, "temperature": 3}}
    b.update(over)
    return json.dumps(b).encode()


class ProxyTest(unittest.TestCase):
    def test_signed_and_redacted_reasoning_preserve_provider_context(self):
        signed = {"reasoningText": {"text": "private", "signature": "signed-test"}}
        blocks = [{"reasoningContent": signed}, {"reasoningContent": {"redactedContent": "AQID"}}, {"text": "ready"}]
        messages = [{"role": "assistant", "content": blocks}, {"role": "user", "content": [{"text": "continue"}]}]
        self.proxy.converse("Bearer " + TOKEN, body(messages=messages))
        sent = self.fake.calls[0]["messages"][0]["content"]
        self.assertEqual(sent[0]["reasoningContent"], signed)
        self.assertEqual(sent[1]["reasoningContent"]["redactedContent"], b"\x01\x02\x03")
        real_converse = self.fake.converse
        def with_redacted(**req):
            result = real_converse(**req)
            result["output"]["message"]["content"].insert(0, {"reasoningContent": {"redactedContent": b"\x01\x02\x03"}})
            return result
        self.fake.converse = with_redacted
        response = self.proxy.converse("Bearer " + TOKEN, body())
        self.assertEqual(response["output"]["message"]["content"][0]["reasoningContent"]["redactedContent"], "AQID")
        json.dumps(response)

    def test_invalid_reasoning_is_rejected_without_inference(self):
        for role, reasoning in [("user", {"redactedContent": "AQID"}), ("assistant", {"redactedContent": "!"}), ("assistant", {"reasoningText": {"text": "unsigned"}})]:
            with self.assertRaises(np.ProxyError):
                self.proxy.converse("Bearer " + TOKEN, body(messages=[{"role": role, "content": [{"reasoningContent": reasoning}]}]))
        self.assertEqual(self.fake.calls, [])

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.fake = FakeBedrock()
        self.proxy = self.make()

    def tearDown(self):
        self.tmp.cleanup()

    def make(self, **cfg):
        base = {"allowed_models": [MODEL], "device_token_sha256": [np.sha256_hex(TOKEN)], "usage_dir": str(Path(self.tmp.name) / "u"),
                "prices_per_mtok": {MODEL: {"input": 3.0, "output": 15.0}}, "daily_budget_usd": 1.0}
        base.update(cfg)
        return np.NexusProxy(base, bedrock=self.fake)

    def test_requires_valid_token(self):
        for h in (None, "Bearer nope", "Basic x"):
            with self.assertRaises(np.ProxyError) as c:
                self.proxy.converse(h, body())
            self.assertEqual(c.exception.status, 401)
        self.assertEqual(self.fake.calls, [])

    def test_model_allowlist(self):
        with self.assertRaises(np.ProxyError) as c:
            self.proxy.converse("Bearer " + TOKEN, body(modelId="otro.modelo"))
        self.assertEqual(c.exception.status, 422)
        self.assertEqual(self.fake.calls, [])

    def test_caps_tokens_and_temperature_and_strips_metadata(self):
        r = self.proxy.converse("Bearer " + TOKEN, body())
        req = self.fake.calls[0]
        self.assertEqual(req["inferenceConfig"]["maxTokens"], 8192)
        self.assertEqual(req["inferenceConfig"]["temperature"], 1.0)
        self.assertNotIn("ResponseMetadata", r)
        self.assertEqual(r["stopReason"], "tool_use")

    def test_image_bytes_decoded(self):
        img = base64.b64encode(b"\xff\xd8jpegdata").decode()
        msgs = [{"role": "user", "content": [{"text": "mira"}, {"image": {"format": "jpeg", "source": {"bytes": img}}}]}]
        self.proxy.converse("Bearer " + TOKEN, body(messages=msgs))
        self.assertEqual(self.fake.calls[0]["messages"][0]["content"][1]["image"]["source"]["bytes"], b"\xff\xd8jpegdata")

    def test_document_bytes_decoded(self):
        raw = b"%PDF-example"
        doc = {"document": {"format": "pdf", "name": "Document 1", "source": {"bytes": base64.b64encode(raw).decode()}}}
        self.proxy.converse("Bearer " + TOKEN, body(messages=[{"role": "user", "content": [{"text": "resume"}, doc]}]))
        self.assertEqual(self.fake.calls[0]["messages"][0]["content"][1]["document"]["source"]["bytes"], raw)

    def test_documents_reject_invalid_inputs(self):
        doc = {"document": {"format": "pdf", "name": "Document 1", "source": {"bytes": "YQ=="}}}
        bad = [
            {"role": "assistant", "content": [{"text": "x"}, doc]},
            {"role": "user", "content": [doc]},
            {"role": "user", "content": [{"text": "x"}] + [doc] * 6},
            {"role": "user", "content": [{"text": "x"}, {"document": {"format": "pdf", "name": "Doc", "source": {"bytes": "!!!"}}}]},
        ]
        for message in bad:
            with self.subTest(message=message), self.assertRaises(np.ProxyError):
                self.proxy.build_request(json.loads(body(messages=[message])))
        self.assertEqual(self.fake.calls, [])

    def test_two_full_size_documents_fit_transport(self):
        doc = {"document": {"format": "pdf", "name": "Document 1", "source": {"bytes": base64.b64encode(b"x" * 4_500_000).decode()}}}
        payload = body(messages=[{"role": "user", "content": [{"text": "compare"}, doc, doc]}])
        self.assertLess(len(payload), self.proxy.cfg["max_request_bytes"])
        result = self.proxy.build_request(json.loads(payload))
        self.assertEqual(len(result["messages"][0]["content"][1]["document"]["source"]["bytes"]), 4_500_000)

    def test_large_document_uses_counted_tokens_under_default_budget(self):
        doc = {"document": {"format": "pdf", "name": "Document", "source": {"bytes": base64.b64encode(b"x" * 4_500_000).decode()}}}
        self.proxy.converse("Bearer " + TOKEN, body(messages=[{"role": "user", "content": [{"text": "read"}, doc]}]))
        self.assertEqual(len(self.fake.calls), 1)
        self.assertEqual(self.proxy.usage.reserved_tokens, 0)

    def test_unavailable_token_count_preserves_budget(self):
        def unavailable(**req):
            raise RuntimeError("not supported")
        self.fake.count_tokens = unavailable
        doc = {"document": {"format": "pdf", "name": "Document", "source": {"bytes": base64.b64encode(b"x" * 4_500_000).decode()}}}
        with self.assertRaises(np.ProxyError) as error:
            self.proxy.converse("Bearer " + TOKEN, body(messages=[{"role": "user", "content": [{"text": "read"}, doc]}]))
        self.assertEqual(error.exception.code, "document_token_count_unavailable")
        self.assertEqual(self.fake.calls, [])

    def test_budget_blocks_before_calling_bedrock(self):
        # 1000 in * 3 $/M + 200 out * 15 $/M = 0.006 $ por llamada; presupuesto 0.01 -> la 3.ª se bloquea.
        # Con maxTokens 100 la reserva de peor caso es pequeña: 100 out * 15 $/M + entrada.
        p = self.make(daily_budget_usd=0.01)
        small = body(inferenceConfig={"maxTokens": 100})
        p.converse("Bearer " + TOKEN, small)
        with self.assertRaises(np.ProxyError) as c:
            for _ in range(20):
                p.converse("Bearer " + TOKEN, small)
        self.assertEqual(c.exception.status, 402)
        self.assertLessEqual(p.usage.today()["usd"], 0.01 + 1e-9)
        self.assertEqual(p.usage.reserved_usd, 0.0)

    def test_token_budget_when_unpriced(self):
        p = self.make(prices_per_mtok={}, allow_unpriced_models=True, daily_token_budget=250)
        small = body(inferenceConfig={"maxTokens": 100})
        p.converse("Bearer " + TOKEN, small)   # reserva ~145, gasta 140
        with self.assertRaises(np.ProxyError) as c:
            p.converse("Bearer " + TOKEN, small)  # 140 + 145 > 250
        self.assertEqual(c.exception.status, 402)
        self.assertEqual(p.usage.today()["unpriced_requests"], 1)

    def test_rate_limit(self):
        p = self.make(requests_per_minute=2)
        p.converse("Bearer " + TOKEN, body()); p.converse("Bearer " + TOKEN, body())
        with self.assertRaises(np.ProxyError) as c:
            p.converse("Bearer " + TOKEN, body())
        self.assertEqual(c.exception.status, 429)

    def test_aws_errors_mapped_without_auth_confusion(self):
        for code, status in (("ThrottlingException", 429), ("AccessDeniedException", 502), ("ValidationException", 400), ("ResourceNotFoundException", 502)):
            self.fake.error = AwsError(code)
            with self.assertRaises(np.ProxyError) as c:
                self.proxy.converse("Bearer " + TOKEN, body())
            self.assertEqual(c.exception.status, status, code)

    def test_logs_never_contain_prompt_or_token(self):
        with self.assertLogs("nexus_proxy", level="INFO") as logs:
            self.proxy.converse("Bearer " + TOKEN, body(messages=[{"role": "user", "content": [{"text": "SECRETO_DEL_PROMPT"}]}]))
        joined = "\n".join(logs.output)
        self.assertNotIn("SECRETO_DEL_PROMPT", joined)
        self.assertNotIn(TOKEN, joined)
        self.assertIn("tools=list_models", joined)

    def test_reservation_blocks_worst_case_before_calling(self):
        # 8192 tokens de salida * 15 $/M = 0.12 $ > 0.05 $ de presupuesto: no se llega a Bedrock.
        p = self.make(daily_budget_usd=0.05)
        with self.assertRaises(np.ProxyError) as c:
            p.converse("Bearer " + TOKEN, body())
        self.assertEqual(c.exception.status, 402)
        self.assertEqual(self.fake.calls, [])

    def test_image_limits_and_bad_shapes_are_400s(self):
        img = base64.b64encode(b"x" * 10).decode()
        many = [{"role": "user", "content": [{"image": {"format": "png", "source": {"bytes": img}}}] * 3}]
        p = self.make(max_images_per_request=2)
        with self.assertRaises(np.ProxyError) as c:
            p.converse("Bearer " + TOKEN, body(messages=many))
        self.assertEqual(c.exception.status, 413)
        for bad in (body(system="texto"), body(toolConfig={"tools": "x"}), body(messages=["x"]), b"[1,2]"):
            with self.assertRaises(np.ProxyError) as c:
                self.proxy.converse("Bearer " + TOKEN, bad)
            self.assertEqual(c.exception.status, 400)

    def test_unpriced_model_rejected_at_startup(self):
        with self.assertRaises(ValueError):
            np.NexusProxy({"allowed_models": [MODEL], "device_token_sha256": [np.sha256_hex(TOKEN)], "usage_dir": self.tmp.name}, bedrock=self.fake)
        with self.assertRaises(ValueError):
            np.NexusProxy({"allowed_models": [MODEL], "device_token_sha256": ["no-es-hash"], "prices_per_mtok": {MODEL: {"input": 1, "output": 1}}}, bedrock=self.fake)

    def test_config_requires_allowlist_and_tokens(self):
        with self.assertRaises(ValueError):
            np.NexusProxy({"allowed_models": [], "device_token_sha256": ["x"]}, bedrock=self.fake)
        with self.assertRaises(ValueError):
            np.NexusProxy({"allowed_models": [MODEL], "device_token_sha256": []}, bedrock=self.fake)


if __name__ == "__main__":
    unittest.main()
