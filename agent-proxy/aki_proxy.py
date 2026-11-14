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
from nexus_proxy import NexusProxy, ProxyError, load_config
from aki_media import prepare

MODEL = "openai.gpt-5.6-sol"
EFFORTS = ("low", "medium", "high", "xhigh", "max")
CLAUDE_MODELS = {f"global.anthropic.claude-{family}-5-5": default for family, default in
                 (("haiku", "medium"), ("sonnet", "high"), ("opus", "medium"))}
SUPPORTED_MODELS = {MODEL, *CLAUDE_MODELS,
                    *(f"{model}@{effort}" for model in CLAUDE_MODELS for effort in EFFORTS)}


def resolve_model(model_id, effort=None):
    if model_id not in SUPPORTED_MODELS:
        raise ProxyError(422, "model_not_allowed", "Modelo no admitido por el puente AKI.")
    model, _, suffix = model_id.partition("@")
    effort = effort or suffix or CLAUDE_MODELS.get(model, "low")
    if effort not in EFFORTS:
        raise ProxyError(422, "effort_not_allowed", "Esfuerzo admitido: low, medium, high, xhigh o max.")
    return model, effort


class AkiClient:
    def __init__(self, binary: str, workspace: str, runner=subprocess.run):
        self.binary, self.workspace, self.runner = binary, workspace, runner
        self.lock = threading.Lock()

    def converse(self, **request):
        model, effort = resolve_model(request.get("modelId"), request.get("akiEffort"))
        if model != MODEL:
            request = {**request, "akiProfileLlm": {"provider": "bedrock", "modelId": model,
                       "effort": effort, "reasoning": {"enabled": True}}}
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
                    converted = {k: v for k, v in converted.items() if k not in {"akiProfileLlm", "akiEffort"}}
                    prompt = "Continue this Nexus request. Return only the specified response JSON:\n" + json.dumps(converted, ensure_ascii=False, separators=(",", ":"))
                    result = self.runner([self.binary, "--profile", profile, "--model", model,
                                          "--output-format", "stream-json"],
                                         input=prompt, text=True, capture_output=True, cwd=self.workspace,
                                         env=env, timeout=max(1, 150 - (time.monotonic() - started)))
            except subprocess.TimeoutExpired:
                raise ProxyError(504, "aki_timeout", "AKI no terminó dentro del plazo; no se reintentó automáticamente.")
        finally:
            self.lock.release()
        if "unknown model" in (result.stderr or "").lower():
            raise ProxyError(502, "aki_model_unavailable", "Este AKI no reconoce el modelo; actualiza el binario. No se acepta una respuesta de otro modelo.")
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
        if not usage:
            stderr_text = result.stderr or ""
            if "failed to load AWS credentials" in stderr_text or "session may have expired" in stderr_text:
                raise ProxyError(502, "aki_auth_expired", "La sesión OAuth de AKI en el S25 (auth.json) ha caducado. Sincroniza auth.json desde un equipo con postura AEA o selecciona un modelo de Bedrock directo.")
            raise ProxyError(502, "aki_failed", "AKI rechazó la petición. Comprueba su sesión y el modelo; no se utilizó AWS directo.")
        if usage.get("total_tools", 0) != host_calls or (host_calls and not allowed_views) or viewed != allowed_views:
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
    def build_request(self, body):
        request = super().build_request(body)
        effort = body.get("akiEffort")
        if effort is not None:
            resolve_model(request["modelId"], effort)
            request["akiEffort"] = effort
        return request

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
    if not cfg.get("allowed_models") or not set(cfg["allowed_models"]) <= SUPPORTED_MODELS:
        raise ValueError("Explicit supported AKI model allowlist required")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    proxy = AkiProxy(cfg, bedrock=AkiClient(cfg["aki_binary"], cfg["aki_workspace"]))
    from aki_openai import make_aki_handler
    handler = make_aki_handler(proxy)
    # OkHttp reuses connections across tool rounds; frame every reply with Content-Length.
    handler.protocol_version = "HTTP/1.1"
    server = ThreadingHTTPServer(("127.0.0.1", int(proxy.cfg["port"])), handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
