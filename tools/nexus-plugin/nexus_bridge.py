"""Nexus Android bridge. Only named, local draft operations; no provider HTTP client."""
from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess
import uuid
from typing import Any

PACKAGE = "com.vidadomu.nexus.agent"
ALLOWED_METHODS = {"status", "catalog", "model", "prepare_draft", "list_drafts", "discard_draft"}


class BridgeError(RuntimeError):
    pass


def decode_result(output: str) -> dict[str, Any]:
    match = re.search(r"nexus_result=([A-Za-z0-9_=-]+)", output)
    if not match:
        raise BridgeError("Nexus no respondió. Necesita la versión 1.5.0 o posterior y ADB autorizado.")
    try:
        result = json.loads(base64.urlsafe_b64decode(match.group(1)))
    except (ValueError, UnicodeError):
        raise BridgeError("Respuesta local de Nexus no válida.") from None
    if not isinstance(result, dict):
        raise BridgeError("Respuesta local de Nexus no válida.")
    return result


class NexusBridge:
    def __init__(self, adb: str | None = None, hardware_serial: str | None = None):
        self.adb = adb or os.environ.get("NEXUS_ADB") or shutil.which("adb")
        self.hardware_serial = hardware_serial or os.environ.get("NEXUS_DEVICE_SERIAL", "")
        if not self.adb:
            raise BridgeError("No se encuentra ADB. Configura NEXUS_ADB con la ruta de platform-tools/adb.")
        if not re.fullmatch(r"[A-Za-z0-9_-]{4,64}", self.hardware_serial):
            raise BridgeError("Configura NEXUS_DEVICE_SERIAL con el número de serie físico del teléfono.")

    def _run(self, args: list[str]) -> str:
        try:
            result = subprocess.run([self.adb, *args], capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=25,
                                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        except subprocess.TimeoutExpired:
            raise BridgeError("La conexión ADB ha agotado el tiempo. Comprueba el teléfono.") from None
        except OSError:
            raise BridgeError("No se pudo iniciar ADB.") from None
        if result.returncode:
            # Do not echo arguments, raw adb output or any prompt supplied by the user.
            raise BridgeError("ADB no pudo completar la operación local. Comprueba conexión y autorización.")
        return result.stdout.strip()

    def device(self) -> tuple[str, str]:
        candidates = [line.split()[0] for line in self._run(["devices"]).splitlines()
                      if len(line.split()) >= 2 and line.split()[1] == "device"]
        matching = []
        for target in candidates:
            try:
                serial = self._run(["-s", target, "shell", "getprop", "ro.serialno"])
                if serial == self.hardware_serial:
                    matching.append(target)
            except BridgeError:
                continue
        if not matching:
            raise BridgeError("El teléfono configurado no está conectado por ADB. No se utilizará otro dispositivo.")
        # A single physical phone may appear over TLS and TCP simultaneously.
        target = sorted(matching, key=lambda value: (value.endswith(":5555"), value))[0]
        current_user = self._run(["-s", target, "shell", "am", "get-current-user"])
        if current_user != "0":
            raise BridgeError("Activa el perfil personal del teléfono para usar Nexus.")
        model = self._run(["-s", target, "shell", "getprop", "ro.product.model"])
        return target, model

    def call(self, method: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        if method not in ALLOWED_METHODS:
            raise BridgeError("Operación no admitida. Este puente no ejecuta generaciones.")
        encoded = base64.urlsafe_b64encode(json.dumps(payload or {}, ensure_ascii=False).encode()).decode()
        if len(encoded) > 96000:
            raise BridgeError("Solicitud demasiado grande.")
        target, model = self.device()
        output = self._run(["-s", target, "shell", "content", "call", "--user", "0", "--uri",
                            f"content://{PACKAGE}.agent", "--method", method, "--arg", encoded])
        result = decode_result(output)
        if method == "status":
            result["deviceModel"] = model
            result["connection"] = "authorized_adb"
        return result

    def open_draft(self, draft_id: str) -> dict[str, Any]:
        try:
            canonical = str(uuid.UUID(draft_id))
        except (ValueError, AttributeError):
            raise BridgeError("Identificador de borrador inválido.") from None
        if canonical != draft_id:
            raise BridgeError("Identificador de borrador inválido.")
        available = self.call("list_drafts")
        if not available.get("ok") or not any(d.get("draftId") == canonical for d in available.get("drafts", [])):
            raise BridgeError("El borrador preparado ya no está disponible en Nexus.")
        target, _ = self.device()
        output = self._run(["-s", target, "shell", "am", "start", "--user", "0", "-n",
                           f"{PACKAGE}/com.vidadomu.nexus.MainActivity", "--es", "nexus_agent_draft_id", canonical])
        if "Error:" in output or "Exception" in output:
            raise BridgeError("Android no pudo abrir Nexus para revisar el borrador.")
        return {"ok": True, "status": "review_requested", "draftId": canonical,
                "paidExecutionEnabled": False,
                "next": "Revisa el diálogo en Nexus. No se ha enviado ninguna generación."}
