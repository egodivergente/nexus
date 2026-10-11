#!/usr/bin/env python3
"""
Proxy del asistente de Nexus: APK -> este proxy -> Amazon Bedrock (API Converse).

La APK solo conoce la URL del proxy y un token de dispositivo revocable. Las credenciales de AWS
se quedan aquí (cadena estándar de boto3: perfil, variables de entorno, rol de instancia).

Garantías:
  * Token de dispositivo obligatorio (se guardan solo hashes SHA-256 en la configuración).
  * Lista blanca de model IDs / inference profiles (HTTP 422 si no está).
  * Tope de maxTokens, de tamaño de petición y de peticiones por minuto.
  * Presupuesto diario en USD (con precios por millón de tokens de la configuración) y en tokens.
    Al superarlo: HTTP 402 y no se llama a Bedrock.
  * Los registros solo llevan metadatos (modelo, tokens, latencia, nombres de herramientas):
    nunca prompts, imágenes, tokens ni credenciales.

Uso:
  python nexus_proxy.py --new-token              # genera un token y su hash para config.local.json
  python nexus_proxy.py --config config.local.json
  adb reverse tcp:8787 tcp:8787                  # en el móvil: endpoint http://127.0.0.1:8787
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import hmac
import json
import logging
import os
import secrets
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable

LOG = logging.getLogger("nexus_proxy")

DEFAULTS: dict[str, Any] = {
    "bind": "127.0.0.1",
    "port": 8787,
    "region": "us-east-1",
    "aws_profile": None,
    "allowed_models": [],
    "default_model": None,
    "device_token_sha256": [],
    "max_tokens_cap": 8192,
    "max_request_bytes": 40 * 1024 * 1024,
    "max_document_bytes": 4_500_000,
    "max_images_per_request": 20,
    "max_image_bytes": 3_750_000,
    "max_tools": 64,
    # Si es false, todo modelo permitido debe tener precio (si no, el presupuesto en USD no lo frena).
    "allow_unpriced_models": False,
    "requests_per_minute": 30,
    "daily_budget_usd": 5.0,
    "daily_token_budget": 2_000_000,
    # USD por millón de tokens, por model ID. Sin precio -> solo cuenta el tope de tokens.
    "prices_per_mtok": {},
    "usage_dir": "usage",
    "read_timeout_s": 170,
}


class ProxyError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


class Usage:
    """Gasto del día (UTC) en disco: sobrevive a reinicios del proxy."""

    def __init__(self, directory: Path, clock: Callable[[], float] = time.time):
        self.dir = directory
        self.clock = clock
        self.lock = threading.Lock()
        self.reserved_usd = 0.0
        self.reserved_tokens = 0
        self.dir.mkdir(parents=True, exist_ok=True)

    def _path(self) -> Path:
        day = dt.datetime.fromtimestamp(self.clock(), dt.timezone.utc).strftime("%Y-%m-%d")
        return self.dir / f"{day}.json"

    def today(self) -> dict[str, Any]:
        p = self._path()
        if p.exists():
            try:
                return json.loads(p.read_text("utf-8"))
            except (OSError, ValueError):
                pass
        return {"usd": 0.0, "input_tokens": 0, "output_tokens": 0, "requests": 0, "unpriced_requests": 0}

    def reserve(self, usd: float, tokens: int, budget_usd: float, budget_tokens: int) -> None:
        """Aparta el peor caso de esta llamada ANTES de llamar a Bedrock (evita la carrera entre hilos)."""
        with self.lock:
            d = self.today()
            if d["usd"] + self.reserved_usd + usd > budget_usd:
                raise ProxyError(402, "budget_exceeded", "Presupuesto diario del asistente agotado.")
            if d["input_tokens"] + d["output_tokens"] + self.reserved_tokens + tokens > budget_tokens:
                raise ProxyError(402, "budget_exceeded", "Tope diario de tokens del asistente agotado.")
            self.reserved_usd += usd
            self.reserved_tokens += tokens

    def release(self, usd: float, tokens: int) -> None:
        with self.lock:
            self.reserved_usd = max(0.0, self.reserved_usd - usd)
            self.reserved_tokens = max(0, self.reserved_tokens - tokens)

    def add(self, input_tokens: int, output_tokens: int, usd: float | None) -> dict[str, Any]:
        with self.lock:
            d = self.today()
            d["input_tokens"] += input_tokens
            d["output_tokens"] += output_tokens
            d["requests"] += 1
            if usd is None:
                d["unpriced_requests"] += 1
            else:
                d["usd"] = round(d["usd"] + usd, 6)
            tmp = self._path().with_suffix(".tmp")
            tmp.write_text(json.dumps(d), "utf-8")
            os.replace(tmp, self._path())
            return d


class RateLimiter:
    def __init__(self, per_minute: int, clock: Callable[[], float] = time.monotonic):
        self.per_minute, self.clock = per_minute, clock
        self.hits: dict[str, list[float]] = {}
        self.lock = threading.Lock()

    def check(self, key: str) -> None:
        now = self.clock()
        with self.lock:
            h = [t for t in self.hits.get(key, []) if now - t < 60]
            if len(h) >= self.per_minute:
                self.hits[key] = h
                raise ProxyError(429, "rate_limited", "Demasiadas peticiones por minuto.")
            h.append(now)
            self.hits[key] = h


class NexusProxy:
    def __init__(self, config: dict[str, Any], bedrock: Any = None, clock: Callable[[], float] = time.time):
        self.cfg = {**DEFAULTS, **config}
        if not self.cfg["allowed_models"]:
            raise ValueError("allowed_models está vacío: añade el model ID o inference profile permitido.")
        hashes = self.cfg["device_token_sha256"]
        if not hashes:
            raise ValueError("device_token_sha256 está vacío: genera uno con --new-token.")
        if not all(isinstance(h, str) and len(h) == 64 and all(c in "0123456789abcdefABCDEF" for c in h) for h in hashes):
            raise ValueError("device_token_sha256 debe contener hashes SHA-256 en hexadecimal (64 caracteres).")
        self.cfg["device_token_sha256"] = [h.lower() for h in hashes]
        unpriced = [m for m in self.cfg["allowed_models"] if m not in self.cfg["prices_per_mtok"]]
        if unpriced and not self.cfg["allow_unpriced_models"]:
            raise ValueError("Sin precio en prices_per_mtok: " + ", ".join(unpriced) + " (o pon allow_unpriced_models: true).")
        self.usage = Usage(Path(self.cfg["usage_dir"]), clock)
        self.rate = RateLimiter(int(self.cfg["requests_per_minute"]))
        self.auth_failures = RateLimiter(20)
        self._bedrock = bedrock

    # -- Bedrock --------------------------------------------------------------------------------
    @property
    def bedrock(self) -> Any:
        if self._bedrock is None:
            import boto3  # import tardío: las pruebas no necesitan boto3
            from botocore.config import Config

            session = boto3.Session(profile_name=self.cfg["aws_profile"]) if self.cfg["aws_profile"] else boto3.Session()
            self._bedrock = session.client(
                "bedrock-runtime", region_name=self.cfg["region"],
                config=Config(read_timeout=int(self.cfg["read_timeout_s"]), retries={"max_attempts": 2, "mode": "standard"}),
            )
        return self._bedrock

    # -- autenticación --------------------------------------------------------------------------
    def authenticate(self, header: str | None) -> str:
        if not header or not header.startswith("Bearer "):
            raise ProxyError(401, "unauthorized", "Falta el token del dispositivo.")
        digest = sha256_hex(header[7:].strip())
        if not any(hmac.compare_digest(digest, h) for h in self.cfg["device_token_sha256"]):
            self.auth_failures.check("auth")  # >20 fallos/min -> 429 a todos los intentos fallidos
            raise ProxyError(401, "unauthorized", "Token del dispositivo no válido.")
        return digest[:12]

    # -- presupuesto ----------------------------------------------------------------------------
    def check_budget(self) -> None:
        d = self.usage.today()
        if d["usd"] >= float(self.cfg["daily_budget_usd"]):
            raise ProxyError(402, "budget_exceeded", "Presupuesto diario del asistente agotado.")
        if d["input_tokens"] + d["output_tokens"] >= int(self.cfg["daily_token_budget"]):
            raise ProxyError(402, "budget_exceeded", "Tope diario de tokens del asistente agotado.")

    def cost(self, model_id: str, inp: int, out: int) -> float | None:
        p = self.cfg["prices_per_mtok"].get(model_id)
        if not p:
            return None
        return inp / 1e6 * float(p["input"]) + out / 1e6 * float(p["output"])

    # -- petición -------------------------------------------------------------------------------
    def build_request(self, body: dict[str, Any]) -> dict[str, Any]:
        model = body.get("modelId") or self.cfg["default_model"]
        if model not in self.cfg["allowed_models"]:
            raise ProxyError(422, "model_not_allowed", "Ese modelo no está en la lista blanca del proxy.")
        messages = body.get("messages")
        if not isinstance(messages, list) or not messages:
            raise ProxyError(400, "bad_request", "Faltan mensajes.")
        self._images = 0
        req: dict[str, Any] = {"modelId": model, "messages": [self._decode_message(m) for m in messages]}
        if body.get("system") is not None:
            if not isinstance(body["system"], list):
                raise ProxyError(400, "bad_request", "system debe ser una lista.")
            req["system"] = [{"text": str(s["text"])} for s in body["system"] if isinstance(s, dict) and "text" in s]
        if not isinstance(body.get("inferenceConfig") or {}, dict):
            raise ProxyError(400, "bad_request", "inferenceConfig no válido.")
        inf = dict(body.get("inferenceConfig") or {})
        cap = int(self.cfg["max_tokens_cap"])
        inf["maxTokens"] = max(1, min(int(inf.get("maxTokens") or cap), cap))
        if "temperature" in inf:
            inf["temperature"] = max(0.0, min(float(inf["temperature"]), 1.0))
        req["inferenceConfig"] = {k: v for k, v in inf.items() if k in ("maxTokens", "temperature", "topP", "stopSequences")}
        if body.get("toolConfig"):
            req["toolConfig"] = self._check_tools(body["toolConfig"])
        return req

    def _check_tools(self, tc: Any) -> dict[str, Any]:
        tools = tc.get("tools") if isinstance(tc, dict) else None
        if not isinstance(tools, list) or len(tools) > int(self.cfg["max_tools"]):
            raise ProxyError(400, "bad_request", "toolConfig no válido o con demasiadas herramientas.")
        for t in tools:
            spec = t.get("toolSpec") if isinstance(t, dict) else None
            if not isinstance(spec, dict) or not isinstance(spec.get("name"), str) or len(spec["name"]) > 64:
                raise ProxyError(400, "bad_request", "Herramienta con formato no válido.")
        return {"tools": tools, **({"toolChoice": tc["toolChoice"]} if "toolChoice" in tc else {})}

    def _decode_message(self, m: Any) -> dict[str, Any]:
        if not isinstance(m, dict) or m.get("role") not in ("user", "assistant") or not isinstance(m.get("content"), list):
            raise ProxyError(400, "bad_request", "Mensaje con formato no válido.")
        content = []
        documents = 0
        for block in m["content"]:
            if isinstance(block, dict) and "image" in block:
                img = block["image"]
                fmt = img.get("format")
                if fmt not in ("png", "jpeg", "gif", "webp"):
                    raise ProxyError(400, "bad_request", "Formato de imagen no admitido.")
                self._images += 1
                if self._images > int(self.cfg["max_images_per_request"]):
                    raise ProxyError(413, "too_large", "Demasiadas imágenes en la conversación.")
                try:
                    raw = base64.b64decode(img["source"]["bytes"], validate=True)
                except Exception:
                    raise ProxyError(400, "bad_request", "Imagen mal codificada.")
                if len(raw) > int(self.cfg["max_image_bytes"]):
                    raise ProxyError(413, "too_large", "Imagen demasiado grande.")
                content.append({"image": {"format": fmt, "source": {"bytes": raw}}})
            elif isinstance(block, dict) and "document" in block:
                documents += 1
                doc = block["document"]
                if m["role"] != "user" or not isinstance(doc, dict) or documents > 5:
                    raise ProxyError(400, "bad_request", "Solo se admiten hasta cinco documentos por mensaje de usuario.")
                if doc.get("format") not in ("pdf", "csv", "doc", "docx", "xls", "xlsx", "html", "txt", "md"):
                    raise ProxyError(400, "bad_request", "Formato de documento no admitido.")
                name = doc.get("name")
                if not isinstance(name, str) or not name.strip() or len(name) > 200:
                    raise ProxyError(400, "bad_request", "Nombre de documento no valido.")
                try:
                    raw = base64.b64decode(doc["source"]["bytes"], validate=True)
                except Exception:
                    raise ProxyError(400, "bad_request", "Documento mal codificado.")
                if not raw or len(raw) > int(self.cfg["max_document_bytes"]):
                    raise ProxyError(413, "too_large", "Documento vacio o superior a 4,5 MB.")
                content.append({"document": {"format": doc["format"], "name": name, "source": {"bytes": raw}}})
            elif isinstance(block, dict) and "reasoningContent" in block:
                reasoning = block["reasoningContent"]
                if m["role"] != "assistant" or not isinstance(reasoning, dict):
                    raise ProxyError(400, "bad_request", "Contexto de razonamiento no válido.")
                if set(reasoning) == {"redactedContent"}:
                    try:
                        decoded = base64.b64decode(reasoning["redactedContent"], validate=True)
                    except (ValueError, TypeError):
                        raise ProxyError(400, "bad_request", "Contexto de razonamiento mal codificado.")
                    content.append({"reasoningContent": {"redactedContent": decoded}})
                elif set(reasoning) == {"reasoningText"} and isinstance(reasoning["reasoningText"], dict) and all(isinstance(reasoning["reasoningText"].get(key), str) for key in ("text", "signature")):
                    content.append(block)
                else:
                    raise ProxyError(400, "bad_request", "Contexto de razonamiento incompleto.")
            else:
                content.append(block)
        if documents and not any(isinstance(b, dict) and isinstance(b.get("text"), str) and b["text"].strip() for b in content):
            raise ProxyError(400, "bad_request", "Los documentos necesitan una instruccion de texto.")
        return {"role": m["role"], "content": content}

    def converse(self, auth_header: str | None, raw: bytes) -> dict[str, Any]:
        who = self.authenticate(auth_header)
        self.rate.check(who)
        if len(raw) > int(self.cfg["max_request_bytes"]):
            raise ProxyError(413, "too_large", "Petición demasiado grande.")
        try:
            body = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, ValueError):
            raise ProxyError(400, "bad_request", "JSON no válido.")
        if not isinstance(body, dict):
            raise ProxyError(400, "bad_request", "JSON no válido.")
        req = self.build_request(body)
        self.check_budget()
        # Binary document size is not a token count. Ask the model tokenizer when documents are present.
        worst_in, worst_out = len(raw) // 3, req["inferenceConfig"]["maxTokens"]
        has_documents = any("document" in block for message in req["messages"] for block in message["content"] if isinstance(block, dict))
        if has_documents:
            try:
                counted = self.bedrock.count_tokens(modelId=req["modelId"], input={"converse": {
                    key: req[key] for key in ("messages", "system", "toolConfig") if key in req
                }})["inputTokens"]
                if not isinstance(counted, int) or isinstance(counted, bool) or counted < 0:
                    raise ValueError("Invalid token count")
                worst_in = counted
            except Exception:
                # Keep the existing conservative reservation if counting is unsupported or unavailable.
                # The explicit error below explains why a valid document may still exceed that reservation.
                LOG.warning("Document token count unavailable; using conservative reservation")
                remaining = int(self.cfg["daily_token_budget"]) - self.usage.today()["input_tokens"] - self.usage.today()["output_tokens"]
                if worst_in + worst_out > remaining:
                    raise ProxyError(422, "document_token_count_unavailable", "No se pudo contar el documento con Bedrock. Revisa el permiso bedrock:CountTokens, la compatibilidad del modelo y la version de boto3; la reserva provisional supera el presupuesto. El archivo se conserva.")
        worst_usd = self.cost(req["modelId"], worst_in, worst_out) or 0.0
        self.usage.reserve(worst_usd, worst_in + worst_out, float(self.cfg["daily_budget_usd"]), int(self.cfg["daily_token_budget"]))
        started = time.monotonic()
        try:
            resp = self.bedrock.converse(**req)
        except Exception as e:  # botocore.exceptions.ClientError y errores de red
            raise self._map_aws_error(e)
        finally:
            self.usage.release(worst_usd, worst_in + worst_out)
        usage = resp.get("usage") or {}
        inp, out = int(usage.get("inputTokens", 0)), int(usage.get("outputTokens", 0))
        usd = self.cost(req["modelId"], inp, out)
        day = self.usage.add(inp, out, usd)
        tools = [b["toolUse"]["name"] for b in (resp.get("output", {}).get("message", {}).get("content") or []) if "toolUse" in b]
        LOG.info("converse model=%s dev=%s in=%d out=%d usd=%s day_usd=%.4f stop=%s tools=%s ms=%d",
                 req["modelId"], who, inp, out, "?" if usd is None else f"{usd:.4f}", day["usd"],
                 resp.get("stopReason"), ",".join(tools) or "-", int((time.monotonic() - started) * 1000))
        result = {"output": resp.get("output"), "stopReason": resp.get("stopReason"), "usage": usage}
        # boto3 returns this blob as bytes; JSON/Android use the original base64 representation.
        for block in (result.get("output") or {}).get("message", {}).get("content", []):
            reasoning = block.get("reasoningContent", {})
            if isinstance(reasoning.get("redactedContent"), bytes):
                reasoning["redactedContent"] = base64.b64encode(reasoning["redactedContent"]).decode("ascii")
        return result

    @staticmethod
    def _map_aws_error(e: Exception) -> ProxyError:
        code = getattr(e, "response", {}).get("Error", {}).get("Code", e.__class__.__name__)
        msg = str(getattr(e, "response", {}).get("Error", {}).get("Message", ""))[:200]
        if code in ("ThrottlingException", "ServiceQuotaExceededException", "TooManyRequestsException"):
            return ProxyError(429, "throttled", "Bedrock está limitando las peticiones.")
        if code == "ValidationException":
            return ProxyError(400, "bad_request", "Bedrock rechazó la petición: " + msg)
        if code in ("AccessDeniedException", "UnrecognizedClientException", "ExpiredTokenException"):
            # 502 y no 401/403: el problema son los permisos del proxy en AWS, no el token del móvil.
            return ProxyError(502, "aws_access", "El proxy no tiene acceso a ese modelo en Bedrock (permisos o acceso al modelo).")
        if code in ("ResourceNotFoundException", "ModelNotReadyException"):
            return ProxyError(502, "model_unavailable", "Modelo no disponible en esa región o cuenta.")
        return ProxyError(502, "upstream", f"Error de Bedrock ({code}).")


def make_handler(proxy: NexusProxy):
    class Handler(BaseHTTPRequestHandler):
        server_version = "NexusProxy/1"

        def _send(self, status: int, payload: dict[str, Any]) -> None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self) -> None:  # noqa: N802
            if self.path == "/health":
                self._send(200, {"ok": True})
            else:
                self._send(404, {"error": {"code": "not_found", "message": "Ruta desconocida."}})

        def do_POST(self) -> None:  # noqa: N802
            if self.path != "/v1/converse":
                self._send(404, {"error": {"code": "not_found", "message": "Ruta desconocida."}})
                return
            try:
                length = int(self.headers.get("Content-Length") or 0)
                if length > int(proxy.cfg["max_request_bytes"]):
                    raise ProxyError(413, "too_large", "Petición demasiado grande.")
                raw = self.rfile.read(length)
                self._send(200, proxy.converse(self.headers.get("Authorization"), raw))
            except ProxyError as e:
                LOG.warning("rechazo status=%d code=%s", e.status, e.code)
                self._send(e.status, {"error": {"code": e.code, "message": e.message}})
            except Exception as e:  # nunca devolver trazas
                LOG.error("error interno %s", e.__class__.__name__)
                self._send(500, {"error": {"code": "internal", "message": "Error interno del proxy."}})

        def log_message(self, fmt: str, *args: Any) -> None:  # sin cabeceras ni cuerpos
            LOG.debug("http %s", self.requestline.split(" HTTP")[0][:80])

    return Handler


def load_config(path: Path) -> dict[str, Any]:
    cfg = json.loads(path.read_text("utf-8"))
    base = path.parent
    if not Path(cfg.get("usage_dir", DEFAULTS["usage_dir"])).is_absolute():
        cfg["usage_dir"] = str(base / cfg.get("usage_dir", DEFAULTS["usage_dir"]))
    return cfg


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", type=Path, default=Path(__file__).with_name("config.local.json"))
    ap.add_argument("--new-token", action="store_true", help="genera un token de dispositivo y su hash")
    args = ap.parse_args(argv)
    if args.new_token:
        tok = "nxp_" + secrets.token_urlsafe(32)
        print("Token (pégalo en Nexus > Asistente > Ajustes; no lo guardes en el repo):\n  " + tok)
        print("Hash para device_token_sha256 en config.local.json:\n  " + sha256_hex(tok))
        return 0
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if not args.config.exists():
        print(f"No existe {args.config}. Copia config.example.json a config.local.json y edítalo.", file=sys.stderr)
        return 2
    proxy = NexusProxy(load_config(args.config))
    srv = ThreadingHTTPServer((proxy.cfg["bind"], int(proxy.cfg["port"])), make_handler(proxy))
    LOG.info("Nexus proxy en http://%s:%d  modelos=%s  presupuesto=%.2f USD/día",
             proxy.cfg["bind"], proxy.cfg["port"], ",".join(proxy.cfg["allowed_models"]), float(proxy.cfg["daily_budget_usd"]))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
