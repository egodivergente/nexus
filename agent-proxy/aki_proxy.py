"""Authenticated loopback Converse adapter for the user's existing AKI session.

Text, Nexus tools, images and PDF through a restricted attachment-view profile.
Other AKI host tools are denied. No AWS fallback or model alias.
"""
from __future__ import annotations
import argparse
import json
import logging
import os
from pathlib import Path
import subprocess
import threading
import time
from http.server import ThreadingHTTPServer
from nexus_proxy import NexusProxy, ProxyError, load_config, make_handler
from aki_media import prepare

MODEL = "openai.gpt-5.6-sol"


class AkiClient:
    def __init__(self, binary: str, workspace: str, runner=subprocess.run):
        self.binary, self.workspace, self.runner = binary, workspace, runner
        self.lock = threading.Lock()

    def converse(self, **request):
        if request.get("modelId") != MODEL:
            raise ProxyError(422, "model_not_allowed", "Este puente solo admite GPT-5.6 Sol.")
        env = dict(os.environ)
        for key in ("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN", "AWS_BEARER_TOKEN_BEDROCK",
                    "AWS_PROFILE", "AWS_WEB_IDENTITY_TOKEN_FILE", "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI",
                    "AWS_CONTAINER_CREDENTIALS_FULL_URI"):
            env.pop(key, None)
        env.update(AWS_SHARED_CREDENTIALS_FILE="/dev/null", AWS_CONFIG_FILE="/dev/null", AWS_EC2_METADATA_DISABLED="true")
        if not self.lock.acquire(timeout=8.0):
            raise ProxyError(429, "busy", "AKI está atendiendo otra petición de Nexus.")
        try:
            try:
                started = time.monotonic()
                with prepare(request) as (converted, profile, allowed_views):
                    prompt = "Continue this Nexus request. Return only the specified response JSON:\n" + json.dumps(converted, ensure_ascii=False, separators=(",", ":"))
                    result = self.runner([self.binary, "--profile", profile, "--model", MODEL,
                                          "--output-format", "stream-json"],
                                         input=prompt, text=True, capture_output=True, cwd=self.workspace,
                                         env=env, timeout=max(1, 150 - (time.monotonic() - started)))
            except subprocess.TimeoutExpired:
                raise ProxyError(504, "aki_timeout", "AKI no terminó dentro del plazo; no se reintentó automáticamente.")
        finally:
            self.lock.release()
        if result.returncode:
            # Never forward CLI stderr: it may contain account information.
            raise ProxyError(502, "aki_failed", "AKI rechazó la petición. Comprueba su sesión y el modelo; no se utilizó AWS directo.")
        chunks, usage, viewed, host_calls = [], None, set(), 0
        for line in result.stdout.splitlines():
            try:
                event = json.loads(line)
            except ValueError:
                raise ProxyError(502, "aki_protocol", "Salida de AKI no válida.")
            if event.get("type") == "text_delta":
                chunks.append(event.get("content", ""))
            elif event.get("type") == "done":
                usage = event
            elif event.get("type") == "tool_use" and event.get("input"):
                tool_input = event["input"]
                command = tool_input.get("command") if isinstance(tool_input, dict) else None
                if event.get("name") != "Bash" or command not in allowed_views:
                    raise ProxyError(502, "aki_tools", "AKI intentó una operación fuera de los adjuntos autorizados.")
                viewed.add(command)
            elif event.get("type") == "tool_result":
                host_calls += 1
                if event.get("name") != "Bash" or event.get("status") != "done" or event.get("output"):
                    raise ProxyError(502, "aki_media", "No se pudo verificar la lectura visual del adjunto.")
        if not usage or usage.get("total_tools", 0) != host_calls or (host_calls and not allowed_views) or viewed != allowed_views:
            raise ProxyError(502, "aki_tools", "AKI no completó la lectura autorizada de todos los adjuntos.")
        try:
            answer = json.loads("".join(chunks))
            content = answer["content"]
            assert isinstance(content, list) and content
            allowed = {t["toolSpec"]["name"] for t in request.get("toolConfig", {}).get("tools", [])}
            ids = set()
            has_tools = False
            for block in content:
                assert isinstance(block, dict)
                if set(block) == {"text"}:
                    assert isinstance(block["text"], str)
                else:
                    assert set(block) == {"toolUse"}
                    call = block["toolUse"]
                    assert set(call) == {"toolUseId", "name", "input"}
                    assert call["name"] in allowed and isinstance(call["input"], dict)
                    assert isinstance(call["toolUseId"], str) and 0 < len(call["toolUseId"]) <= 64
                    assert call["toolUseId"] not in ids
                    ids.add(call["toolUseId"])
                    has_tools = True
            assert answer["stopReason"] == ("tool_use" if has_tools else "end_turn")
            inp, out = usage["input_tokens"], usage["output_tokens"]
            assert type(inp) is int and type(out) is int and inp >= 0 and out >= 0
        except (ValueError, KeyError, TypeError, AssertionError):
            raise ProxyError(502, "aki_protocol", "AKI no devolvió una respuesta válida para Nexus; no se ejecutaron acciones.")
        return {"output": {"message": {"role": "assistant", "content": content}},
                "stopReason": answer["stopReason"], "usage": {"inputTokens": inp, "outputTokens": out}}


class AkiProxy(NexusProxy):
    @staticmethod
    def _map_aws_error(error):
        if isinstance(error, ProxyError):
            return error
        return ProxyError(502, "aki_error", "Error del puente AKI.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()
    cfg = load_config(args.config)
    if cfg.get("bind", "127.0.0.1") != "127.0.0.1":
        raise ValueError("AKI bridge must bind only to 127.0.0.1")
    if cfg.get("allowed_models") != [MODEL]:
        raise ValueError("Explicit Sol-only allowlist required")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    proxy = AkiProxy(cfg, bedrock=AkiClient(cfg["aki_binary"], cfg["aki_workspace"]))
    handler = make_handler(proxy)
    # OkHttp reuses connections across tool rounds; frame every reply with Content-Length.
    handler.protocol_version = "HTTP/1.1"
    server = ThreadingHTTPServer(("127.0.0.1", int(proxy.cfg["port"])), handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
