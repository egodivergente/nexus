"""Chat Completions adapter for Nexus custom brains, using the same AKI session."""
import json
import time
import uuid

from nexus_proxy import ProxyError, make_handler


def to_converse(body):
    if not isinstance(body, dict):
        raise ProxyError(400, "bad_request", "Se esperaba un objeto JSON.")
    messages, system = [], []
    for message in body.get("messages", []):
        role, value = message.get("role"), message.get("content")
        if role in {"system", "developer"}:
            if not isinstance(value, str):
                raise ProxyError(400, "bad_request", "Instrucciones de sistema no válidas.")
            system.append({"text": value})
            continue
        content = []
        if role == "tool":
            content.append({"toolResult": {"toolUseId": message["tool_call_id"],
                            "content": [{"text": value if isinstance(value, str) else json.dumps(value)}]}})
            role = "user"
        elif role in {"user", "assistant"}:
            if isinstance(value, str):
                content.append({"text": value})
            elif isinstance(value, list):
                for part in value:
                    if part.get("type") == "text":
                        content.append({"text": part["text"]})
                    elif part.get("type") == "image_url":
                        url = part.get("image_url", {}).get("url", "")
                        if not url.startswith("data:image/") or ";base64," not in url:
                            raise ProxyError(422, "attachment_unreadable", "Envía la imagen como datos locales.")
                        mime, data = url.split(";base64,", 1)
                        content.append({"image": {"format": mime.split("/")[-1], "source": {"bytes": data}}})
                    else:
                        raise ProxyError(422, "attachment_unreadable", "Contenido no compatible con el puente AKI.")
            for call in message.get("tool_calls", []):
                fn = call["function"]
                args = json.loads(fn.get("arguments") or "{}")
                if not isinstance(args, dict):
                    raise ProxyError(400, "bad_request", "Argumentos de herramienta no válidos.")
                content.append({"toolUse": {"toolUseId": call["id"], "name": fn["name"], "input": args}})
        else:
            raise ProxyError(400, "bad_request", "Rol de mensaje no válido.")
        if content:
            if messages and messages[-1]["role"] == role:
                messages[-1]["content"].extend(content)
            else:
                messages.append({"role": role, "content": content})
    request = {"modelId": body.get("model"), "messages": messages,
               "inferenceConfig": {"maxTokens": body.get("max_completion_tokens") or body.get("max_tokens") or 4096}}
    if system:
        request["system"] = system
    effort = body.get("reasoning_effort") or (body.get("reasoning") or {}).get("effort")
    if effort is not None:
        request["akiEffort"] = effort
    if (body.get("thinking") or {}).get("type") == "disabled":
        raise ProxyError(422, "thinking_not_supported", "Usa pensamiento automático y selecciona el esfuerzo.")
    if body.get("tools"):
        request["toolConfig"] = {"tools": [{"toolSpec": {
            "name": t["function"]["name"], "description": t["function"].get("description", ""),
            "inputSchema": {"json": t["function"].get("parameters", {"type": "object"})}}} for t in body["tools"]]}
    return request


def from_converse(response, model):
    blocks = response["output"]["message"]["content"]
    message = {"role": "assistant", "content": "\n".join(b["text"] for b in blocks if "text" in b) or None}
    calls = [{"id": b["toolUse"]["toolUseId"], "type": "function", "function": {
        "name": b["toolUse"]["name"], "arguments": json.dumps(b["toolUse"]["input"], ensure_ascii=False)}}
        for b in blocks if "toolUse" in b]
    if calls:
        message["tool_calls"] = calls
    usage = response.get("usage", {})
    inp, out = usage.get("inputTokens", 0), usage.get("outputTokens", 0)
    return {"id": "chatcmpl-" + uuid.uuid4().hex, "object": "chat.completion", "created": int(time.time()),
            "model": model, "choices": [{"index": 0, "message": message,
                "finish_reason": "tool_calls" if calls else "stop"}],
            "usage": {"prompt_tokens": inp, "completion_tokens": out, "total_tokens": inp + out}}


def make_aki_handler(proxy):
    class Handler(make_handler(proxy)):
        protocol_version = "HTTP/1.1"

        def do_GET(self):
            if self.path not in {"/v1/models", "/models"}:
                return super().do_GET()
            try:
                proxy.authenticate(self.headers.get("Authorization"))
                self._send(200, {"object": "list", "data": [
                    {"id": model, "object": "model", "owned_by": "aki"} for model in proxy.cfg["allowed_models"]]})
            except ProxyError as e:
                self._send(e.status, {"error": {"code": e.code, "message": e.message}})

        def do_POST(self):
            if self.path not in {"/v1/chat/completions", "/chat/completions"}:
                return super().do_POST()
            try:
                auth = self.headers.get("Authorization")
                proxy.authenticate(auth)
                length = int(self.headers.get("Content-Length") or 0)
                if length <= 0 or length > int(proxy.cfg["max_request_bytes"]):
                    raise ProxyError(413, "too_large", "Tamaño de petición no admitido.")
                body = json.loads(self.rfile.read(length))
                if body.get("stream"):
                    raise ProxyError(422, "stream_not_supported", "Desactiva streaming para este puente AKI.")
                request = to_converse(body)
                response = proxy.converse(auth, json.dumps(request).encode())
                self._send(200, from_converse(response, request["modelId"]))
            except ProxyError as e:
                self.close_connection = True
                self._send(e.status, {"error": {"code": e.code, "message": e.message}})
            except (ValueError, TypeError, KeyError, AttributeError):
                self.close_connection = True
                self._send(400, {"error": {"code": "bad_request", "message": "Petición de chat no válida."}})
            except Exception:
                self.close_connection = True
                self._send(500, {"error": {"code": "internal", "message": "Error interno del puente AKI."}})
    return Handler
