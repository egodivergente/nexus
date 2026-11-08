#!/usr/bin/env python3
"""Flow en directo para Nexus.

Muestra en el móvil, en tiempo real, la pestaña de Google Flow del Edge de AXELPC que ya tiene tu sesión
(perfil ``ChatGPT-Flow``, CDP en 127.0.0.1:9338), y devuelve tus toques y tu texto a esa pestaña.
Todo lo que se descarga desde esa pestaña queda como «resultado» para que Nexus lo guarde en la Biblioteca.

- Pantalla: ``Page.startScreencast`` (JPEG) por WebSocket; solo envía cuando la página cambia.
- Control: ``Input.dispatchMouseEvent`` / ``Input.insertText`` / ``Input.dispatchKeyEvent``.
- Descargas: ``Browser.setDownloadBehavior`` (allowAndName) a una carpeta propia. Las descargas de otras
  pestañas del mismo Edge se mueven a la carpeta Descargas del usuario con su nombre, como antes.
- Acceso: solo desde tus dispositivos de Tailscale (se comprueba con ``tailscale whois``) o desde el propio PC.
  No hay token que copiar.

Uso: python flow_live.py  (variables FLOW_LIVE_* para cambiar puertos y rutas)
"""
from __future__ import annotations

import asyncio
import base64
import hashlib
import ipaddress
import json
import logging
import logging.handlers
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

import uvicorn
import websockets
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import FileResponse, JSONResponse, Response
from starlette.routing import Route, WebSocketRoute
from starlette.websockets import WebSocket, WebSocketDisconnect

VERSION = "1.0.0"
FLOW_URL = os.environ.get("FLOW_LIVE_URL", "https://flow.google.com/")
CDP_PORT = int(os.environ.get("FLOW_LIVE_CDP_PORT", "9338"))
ENSURE_BROWSER = os.environ.get("FLOW_LIVE_ENSURE", str(Path.home() / "flow-chatgpt-bridge" / "ensure-browser.ps1"))
ROOT = Path(os.environ.get("FLOW_LIVE_ROOT", str(Path.home() / "nexus-flow-live")))
HOST = os.environ.get("FLOW_LIVE_HOST", "100.72.111.97")
PORT = int(os.environ.get("FLOW_LIVE_PORT", "8797"))
TAILNET = ipaddress.ip_network("100.64.0.0/10")
ALLOWED_HOSTS = {h.strip().lower() for h in os.environ.get(
    "FLOW_LIVE_HOSTS", f"{HOST},axelpc.tail6ac6b4.ts.net,axelpc,127.0.0.1,localhost").split(",") if h.strip()}
ASSET_DAYS = 14
FLOW_HOSTS = ("flow.google.com", "labs.google", "accounts.google.com", "myaccount.google.com", "www.google.com",
              "accounts.youtube.com", "gds.google.com", "support.google.com", "policies.google.com")

ROOT.mkdir(parents=True, exist_ok=True)
(ROOT / "downloads").mkdir(exist_ok=True)
(ROOT / "assets").mkdir(exist_ok=True)
log = logging.getLogger("flow-live")
_handler = logging.handlers.RotatingFileHandler(ROOT / "flow-live.log", maxBytes=1_000_000, backupCount=3, encoding="utf-8")
_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
log.addHandler(_handler)
log.setLevel(logging.INFO)
if sys.stdout and sys.stdout.isatty():
    log.addHandler(logging.StreamHandler(sys.stdout))


class CdpError(RuntimeError):
    pass


# ---------------------------------------------------------------- CDP

class Cdp:
    """Una sola conexión al navegador en modo «flatten»: las sesiones de pestaña viajan por el mismo socket."""

    def __init__(self) -> None:
        self.ws = None
        self.seq = 0
        self.pending: dict[int, asyncio.Future] = {}
        self.listeners: list = []
        self.closed = asyncio.Event()

    @staticmethod
    def version() -> dict | None:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{CDP_PORT}/json/version", timeout=3) as r:
                return json.load(r)
        except Exception:
            return None

    async def connect(self) -> None:
        info = await asyncio.to_thread(self.version)
        if not info:
            raise CdpError("El navegador de Flow no responde en CDP")
        self.ws = await websockets.connect(info["webSocketDebuggerUrl"], max_size=None, ping_interval=None)
        self.closed.clear()
        asyncio.create_task(self._read())

    async def _read(self) -> None:
        try:
            async for raw in self.ws:
                msg = json.loads(raw)
                if "id" in msg:
                    fut = self.pending.pop(msg["id"], None)
                    if fut and not fut.done():
                        fut.set_result(msg)
                    continue
                for fn in list(self.listeners):
                    try:
                        fn(msg)
                    except Exception:
                        log.exception("listener")
        except Exception as e:
            log.warning("CDP cerrado: %s", e)
        finally:
            for fut in self.pending.values():
                if not fut.done():
                    fut.set_exception(CdpError("CDP cerrado"))
            self.pending.clear()
            self.closed.set()

    async def send(self, method: str, params: dict | None = None, session: str | None = None, timeout: float = 30) -> dict:
        if self.ws is None or self.closed.is_set():
            raise CdpError("Sin conexión con el navegador")
        self.seq += 1
        fut = asyncio.get_running_loop().create_future()
        self.pending[self.seq] = fut
        msg = {"id": self.seq, "method": method, "params": params or {}}
        if session:
            msg["sessionId"] = session
        await self.ws.send(json.dumps(msg))
        res = await asyncio.wait_for(fut, timeout)
        if "error" in res:
            raise CdpError(f"{method}: {res['error'].get('message')}")
        return res.get("result", {})


# ---------------------------------------------------------------- teclas

KEYS = {
    "Enter": (13, "Enter", "\r"), "Backspace": (8, "Backspace", None), "Tab": (9, "Tab", None),
    "Escape": (27, "Escape", None), "Delete": (46, "Delete", None), "Home": (36, "Home", None),
    "End": (35, "End", None), "PageUp": (33, "PageUp", None), "PageDown": (34, "PageDown", None),
    "ArrowLeft": (37, "ArrowLeft", None), "ArrowUp": (38, "ArrowUp", None),
    "ArrowRight": (39, "ArrowRight", None), "ArrowDown": (40, "ArrowDown", None), " ": (32, "Space", " "),
}
COMMANDS = {"selectAll": ("a", 65), "copy": ("c", 67), "paste": ("v", 86), "undo": ("z", 90), "redo": ("y", 89), "cut": ("x", 88)}


def safe_name(name: str) -> str:
    name = re.sub(r"[\\/:*?\"<>|\x00-\x1f]", "_", name or "").strip(" .") or "archivo"
    return name[:150]


# ---------------------------------------------------------------- estado en vivo

class Client:
    def __init__(self, ws: WebSocket) -> None:
        self.ws = ws
        self.frame: bytes | None = None
        self.ready = asyncio.Event()
        self.sent_meta: tuple | None = None


class Live:
    def __init__(self) -> None:
        self.cdp: Cdp | None = None
        self.target: str | None = None
        self.session: str | None = None
        self.stack: list[str] = []          # pestañas abiertas por la nuestra (popups), la última es la visible
        self.clients: set[Client] = set()
        self.frame: bytes | None = None
        self.meta: dict = {}
        self.view = {"mode": "desktop", "width": 1024, "vw": 412, "vh": 860, "dpr": 2.6, "quality": 45, "fps": 6, "maxpx": 820}
        self.info = {"url": "", "title": "", "loading": False}
        self.casting = False
        self.last_pub = 0.0
        self.pub_timer = None
        self.downloads: dict[str, dict] = {}
        self.assets_file = ROOT / "assets" / "index.json"
        self.assets: list[dict] = self._load_assets()
        self.lock = asyncio.Lock()
        self.target_file = ROOT / "target.json"
        self.claimed_at = 0.0
        self.guard_task: asyncio.Task | None = None

    # -- persistencia de resultados
    def _load_assets(self) -> list[dict]:
        try:
            data = json.loads(self.assets_file.read_text(encoding="utf-8"))
        except Exception:
            return []
        cutoff = time.time() - ASSET_DAYS * 86400
        keep = []
        for a in data:
            p = ROOT / "assets" / a.get("file", "")
            if a.get("ts", 0) >= cutoff and p.is_file():
                keep.append(a)
            elif p.is_file():
                p.unlink(missing_ok=True)
        return keep

    def _save_assets(self) -> None:
        tmp = self.assets_file.with_suffix(".tmp")
        tmp.write_text(json.dumps(self.assets, ensure_ascii=False, indent=1), encoding="utf-8")
        os.replace(tmp, self.assets_file)

    # -- conexión
    async def ensure(self) -> None:
        async with self.lock:
            if self.cdp and not self.cdp.closed.is_set() and self.session:
                return
            if not Cdp.version():
                log.info("Arrancando el navegador de Flow")
                await asyncio.to_thread(subprocess.run, ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ENSURE_BROWSER],
                                        capture_output=True, timeout=60, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                for _ in range(20):
                    if Cdp.version():
                        break
                    await asyncio.sleep(1)
            self.cdp = Cdp()
            await self.cdp.connect()
            self.cdp.listeners.append(self._on_event)
            await self.cdp.send("Target.setDiscoverTargets", {"discover": True})
            await self.claim_downloads(force=True)
            await self._attach_main()
            if not self.guard_task or self.guard_task.done():
                self.guard_task = asyncio.create_task(self._guard_downloads())

    async def claim_downloads(self, force: bool = False) -> None:
        """Fija el destino de descargas del navegador. Otra herramienta conectada al mismo Edge (p. ej. la
        sincronización de ChatGPT-Flow con Playwright) puede cambiarlo para todo el navegador, así que se reafirma
        antes de cada toque y cada 30 s."""
        if not force and time.monotonic() - self.claimed_at < 3:
            return
        try:
            await self.cdp.send("Browser.setDownloadBehavior", {"behavior": "allowAndName",
                                "downloadPath": str(ROOT / "downloads"), "eventsEnabled": True}, timeout=5)
            self.claimed_at = time.monotonic()
        except (CdpError, asyncio.TimeoutError) as e:
            log.warning("descargas: %s", e)

    async def _guard_downloads(self) -> None:
        while self.cdp and not self.cdp.closed.is_set():
            await asyncio.sleep(30)
            await self.claim_downloads(force=True)

    async def _attach_main(self) -> None:
        targets = (await self.cdp.send("Target.getTargets"))["targetInfos"]
        pages = {t["targetId"]: t for t in targets if t["type"] == "page"}
        saved = None
        try:
            saved = json.loads(self.target_file.read_text()).get("target")
        except Exception:
            pass
        if saved not in pages:
            created = await self.cdp.send("Target.createTarget", {"url": FLOW_URL, "newWindow": True})
            saved = created["targetId"]
            self.target_file.write_text(json.dumps({"target": saved}))
            log.info("Nueva ventana de Flow para Nexus: %s", saved)
        self.stack = [saved]
        await self._attach(saved)

    async def _attach(self, target: str) -> None:
        self.casting = False
        self.target = target
        self.session = (await self.cdp.send("Target.attachToTarget", {"targetId": target, "flatten": True}))["sessionId"]
        s = self.session
        await self.cdp.send("Page.enable", {}, s)
        await self.cdp.send("Runtime.enable", {}, s)
        await self.cdp.send("Emulation.setFocusEmulationEnabled", {"enabled": True}, s)
        try:
            win = await self.cdp.send("Browser.getWindowForTarget", {"targetId": target})
            if win.get("bounds", {}).get("windowState") == "minimized":
                await self.cdp.send("Browser.setWindowBounds", {"windowId": win["windowId"], "bounds": {"windowState": "normal"}})
        except CdpError:
            pass
        await self.apply_view()
        info = await self.cdp.send("Target.getTargetInfo", {"targetId": target})
        self.info.update(url=info["targetInfo"].get("url", ""), title=info["targetInfo"].get("title", ""))
        if self.clients:
            await self.start_cast()
        await self.broadcast_state()

    async def apply_view(self) -> None:
        v = self.view
        if not self.session:
            return
        if v["mode"] == "mobile":
            w, h = int(v["vw"]), int(v["vh"])
            dsf = max(1.0, min(2.0, float(v["dpr"])))
            mobile = True
        else:
            w = int(max(480, min(1920, v["width"])))
            h = int(round(w * v["vh"] / max(1, v["vw"])))
            dsf = 1.0
            mobile = False
        self.size = (w, h, dsf)
        await self.cdp.send("Emulation.setDeviceMetricsOverride", {"width": w, "height": h, "deviceScaleFactor": round(dsf, 2),
                            "mobile": mobile, "screenWidth": w, "screenHeight": h}, self.session)
        await self.cdp.send("Emulation.setTouchEmulationEnabled", {"enabled": mobile, "maxTouchPoints": 5 if mobile else 1}, self.session)
        if self.casting:
            await self.start_cast(restart=True)

    async def start_cast(self, restart: bool = False) -> None:
        if not self.session:
            return
        w, h, dsf = getattr(self, "size", (1024, 2000, 1.0))
        if restart and self.casting:
            try:
                await self.cdp.send("Page.stopScreencast", {}, self.session)
            except CdpError:
                pass
        # El fotograma se reduce en origen (maxWidth) sin cambiar la maquetación: menos datos móviles.
        px = max(360, min(int(w * dsf), int(self.view.get("maxpx", 900))))
        await self.cdp.send("Page.startScreencast", {"format": "jpeg", "quality": int(self.view["quality"]),
                            "maxWidth": px, "maxHeight": int(px * h / max(1, w)), "everyNthFrame": 1}, self.session)
        self.casting = True
        await self.snapshot()

    async def stop_cast(self) -> None:
        if self.casting and self.session:
            try:
                await self.cdp.send("Page.stopScreencast", {}, self.session)
            except CdpError:
                pass
        self.casting = False

    async def snapshot(self) -> None:
        """Fotograma inmediato (el screencast solo emite cuando algo cambia)."""
        try:
            shot = await self.cdp.send("Page.captureScreenshot", {"format": "jpeg", "quality": int(self.view["quality"])}, self.session, timeout=10)
        except (CdpError, asyncio.TimeoutError) as e:
            log.info("captura: %s", e)
            return
        w, h, _ = getattr(self, "size", (1024, 2000, 1.0))
        self.meta = {"deviceWidth": w, "deviceHeight": h, "offsetTop": 0}
        self._publish(base64.b64decode(shot["data"]))

    def _publish(self, jpeg: bytes) -> None:
        """Entrega como mucho `fps` fotogramas por segundo; el último siempre llega (los intermedios se descartan)."""
        self.frame = jpeg
        interval = 1.0 / max(1, int(self.view.get("fps", 6)))
        wait = interval - (time.monotonic() - self.last_pub)
        if wait <= 0:
            self._deliver()
        elif not self.pub_timer:
            self.pub_timer = asyncio.get_running_loop().call_later(wait, self._deliver)

    def _deliver(self) -> None:
        self.pub_timer = None
        self.last_pub = time.monotonic()
        for c in list(self.clients):
            c.frame = self.frame
            c.ready.set()

    # -- eventos del navegador
    def _on_event(self, msg: dict) -> None:
        method = msg.get("method")
        p = msg.get("params", {})
        if method == "Page.screencastFrame" and msg.get("sessionId") == self.session:
            self.meta = p.get("metadata", {})
            self._publish(base64.b64decode(p["data"]))
            asyncio.create_task(self._ack(p["sessionId"], msg.get("sessionId")))
        elif method == "Target.targetInfoChanged":
            t = p["targetInfo"]
            if t["targetId"] == self.target:
                self.info.update(url=t.get("url", ""), title=t.get("title", ""))
                asyncio.create_task(self.broadcast_state())
        elif method == "Target.targetCreated":
            t = p["targetInfo"]
            if t["type"] == "page" and t.get("openerId") in self.stack:
                self.stack.append(t["targetId"])
                asyncio.create_task(self._attach(t["targetId"]))
        elif method == "Target.targetDestroyed":
            tid = p["targetId"]
            if tid in self.stack:
                self.stack.remove(tid)
                if tid == self.target:
                    self.session = None
                    if self.stack:
                        asyncio.create_task(self._attach(self.stack[-1]))
                    else:
                        asyncio.create_task(self._reattach())
        elif method == "Page.frameStartedLoading" and msg.get("sessionId") == self.session:
            self.info["loading"] = True
            asyncio.create_task(self.broadcast_state())
        elif method in ("Page.loadEventFired", "Page.frameStoppedLoading") and msg.get("sessionId") == self.session:
            self.info["loading"] = False
            asyncio.create_task(self.broadcast_state())
        elif method == "Browser.downloadWillBegin":
            mine = p.get("frameId") in self.stack or p.get("frameId") == self.target
            log.info("Descarga: %s · frame=%s · nuestra=%s · pestañas=%s", p.get("suggestedFilename"), p.get("frameId"), mine, self.stack)
            self.downloads[p["guid"]] = {"name": safe_name(p.get("suggestedFilename", "")), "url": p.get("url", ""), "mine": mine}
            if mine:
                asyncio.create_task(self.broadcast({"type": "download", "name": self.downloads[p["guid"]]["name"], "state": "start"}))
        elif method == "Browser.downloadProgress":
            if p.get("state") in ("completed", "canceled"):
                asyncio.create_task(self._finish_download(p["guid"], p["state"]))

    async def _ack(self, frame_session: int, session: str) -> None:
        # Un «ack» algo retrasado frena a Chrome (menos JPEG que codificar); el tope real lo pone _publish.
        await asyncio.sleep(0.5 / max(1, int(self.view.get("fps", 6))))
        try:
            await self.cdp.send("Page.screencastFrameAck", {"sessionId": frame_session}, session)
        except Exception:
            pass

    async def _reattach(self) -> None:
        try:
            self.target_file.unlink(missing_ok=True)
            await self._attach_main()
        except Exception:
            log.exception("reattach")

    async def _finish_download(self, guid: str, state: str) -> None:
        info = self.downloads.pop(guid, {"name": "archivo", "mine": True})
        src = ROOT / "downloads" / guid
        for _ in range(20):
            if src.exists() or state != "completed":
                break
            await asyncio.sleep(0.25)
        if state != "completed" or not src.exists():
            src.unlink(missing_ok=True)
            return
        if not info["mine"]:
            # Descarga de otra pestaña del mismo Edge: va a Descargas con su nombre, como si no estuviéramos.
            dest_dir = Path.home() / "Downloads"
            dest = dest_dir / info["name"]
            stem, ext, n = dest.stem, dest.suffix, 1
            while dest.exists():
                dest = dest_dir / f"{stem} ({n}){ext}"; n += 1
            await asyncio.to_thread(shutil.move, str(src), str(dest))
            return
        asset_id = uuid.uuid4().hex[:16]
        name = info["name"]
        ext = Path(name).suffix.lower() or ".bin"
        dest = ROOT / "assets" / f"{asset_id}{ext}"
        await asyncio.to_thread(shutil.move, str(src), str(dest))
        digest = await asyncio.to_thread(lambda: hashlib.sha256(dest.read_bytes()).hexdigest())
        mime = mimetypes.guess_type(name)[0] or "application/octet-stream"
        asset = {"id": asset_id, "name": name, "mime": mime, "size": dest.stat().st_size, "sha256": digest,
                 "ts": time.time(), "file": dest.name, "origin": "download"}
        self.assets.append(asset)
        self._save_assets()
        log.info("Resultado guardado: %s (%s, %d B)", name, mime, asset["size"])
        await self.broadcast({"type": "asset", "asset": public_asset(asset)})

    # -- difusión
    def state(self) -> dict:
        w, h, _ = getattr(self, "size", (0, 0, 1))
        return {"type": "state", **self.info, "tabs": len(self.stack), "view": self.view, "page": [w, h]}

    async def broadcast_state(self) -> None:
        await self.broadcast(self.state())

    async def broadcast(self, msg: dict) -> None:
        text = json.dumps(msg, ensure_ascii=False)
        for c in list(self.clients):
            try:
                await c.ws.send_text(text)
            except Exception:
                self.clients.discard(c)

    # -- entrada
    def _xy(self, ev: dict) -> tuple[float, float]:
        w = float(self.meta.get("deviceWidth") or getattr(self, "size", (1024, 0, 1))[0])
        h = float(self.meta.get("deviceHeight") or getattr(self, "size", (0, 2000, 1))[1])
        x = max(0.0, min(1.0, float(ev.get("x", 0)))) * w
        y = max(0.0, min(1.0, float(ev.get("y", 0)))) * h - float(self.meta.get("offsetTop", 0) or 0)
        return round(x, 1), round(max(0.0, y), 1)

    async def mouse(self, kind: str, x: float, y: float, **extra) -> None:
        await self.cdp.send("Input.dispatchMouseEvent", {"type": kind, "x": x, "y": y, **extra}, self.session)

    async def handle(self, ev: dict) -> None:
        t = ev.get("t")
        s = self.session
        if not s:
            return
        if t in ("click", "down", "key", "command", "order"):
            await self.claim_downloads()
        if t == "click":
            x, y = self._xy(ev)
            n = int(ev.get("n", 1))
            await self.mouse("mouseMoved", x, y)
            for i in range(1, n + 1):
                await self.mouse("mousePressed", x, y, button="left", buttons=1, clickCount=i)
                await self.mouse("mouseReleased", x, y, button="left", buttons=0, clickCount=i)
            asyncio.create_task(self.report_focus())
        elif t == "down":
            x, y = self._xy(ev)
            await self.mouse("mouseMoved", x, y)
            await self.mouse("mousePressed", x, y, button="left", buttons=1, clickCount=1)
        elif t == "move":
            x, y = self._xy(ev)
            await self.mouse("mouseMoved", x, y, button="left", buttons=1)
        elif t == "up":
            x, y = self._xy(ev)
            await self.mouse("mouseReleased", x, y, button="left", buttons=0, clickCount=1)
        elif t == "hover":
            x, y = self._xy(ev)
            await self.mouse("mouseMoved", x, y)
        elif t == "wheel":
            x, y = self._xy(ev)
            await self.mouse("mouseWheel", x, y, deltaX=float(ev.get("dx", 0)), deltaY=float(ev.get("dy", 0)))
        elif t == "text":
            text = str(ev.get("s", ""))[:20000]
            if text:
                await self.cdp.send("Input.insertText", {"text": text}, s)
        elif t == "key":
            await self.key(str(ev.get("k", "")))
        elif t == "command":
            name = str(ev.get("c", ""))
            if name in COMMANDS:
                key, code = COMMANDS[name]
                base = {"key": key, "code": f"Key{key.upper()}", "windowsVirtualKeyCode": code, "modifiers": 2, "commands": [name]}
                await self.cdp.send("Input.dispatchKeyEvent", {"type": "rawKeyDown", **base}, s)
                await self.cdp.send("Input.dispatchKeyEvent", {"type": "keyUp", **base}, s)
        elif t == "nav":
            url = str(ev.get("url", "")).strip()
            if url.startswith("https://"):
                await self.cdp.send("Page.navigate", {"url": url}, s)
        elif t == "home":
            await self.cdp.send("Page.navigate", {"url": FLOW_URL}, s)
        elif t == "reload":
            await self.cdp.send("Page.reload", {}, s)
        elif t in ("back", "forward"):
            hist = await self.cdp.send("Page.getNavigationHistory", {}, s)
            i = hist["currentIndex"] + (-1 if t == "back" else 1)
            if 0 <= i < len(hist["entries"]):
                await self.cdp.send("Page.navigateToHistoryEntry", {"entryId": hist["entries"][i]["id"]}, s)
            elif t == "back" and len(self.stack) > 1:
                await self.cdp.send("Target.closeTarget", {"targetId": self.target})
        elif t == "view":
            for k in ("mode", "width", "vw", "vh", "dpr", "quality", "fps", "maxpx"):
                if k in ev:
                    self.view[k] = ev[k] if k == "mode" else float(ev[k])
            self.view["quality"] = int(max(30, min(90, self.view["quality"])))
            self.view["fps"] = int(max(2, min(30, self.view["fps"])))
            await self.apply_view()
            await self.broadcast_state()
        elif t == "refresh":
            await self.snapshot()
        elif t == "order":
            await self.order(str(ev.get("text", ""))[:20000])

    async def order(self, text: str) -> None:
        """Encargo al agente de Flow, a la vista: abre un proyecto si hace falta, escribe en «¿Qué quieres crear?» y envía."""
        text = text.strip()
        if not text:
            return
        find = ("(()=>{const e=document.querySelector('[role=textbox][aria-label=\"¿Qué quieres crear?\"],div.ProseMirror[contenteditable=true]');"
                "if(!e)return null;e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2]})()")
        async def locate():
            r = await self.cdp.send("Runtime.evaluate", {"expression": find, "returnByValue": True}, self.session, timeout=5)
            return r.get("result", {}).get("value")
        pos = await locate()
        if not pos:
            # En la portada no está el cuadro: «Nuevo proyecto» lo abre.
            await self.broadcast({"type": "order", "state": "opening"})
            await self.cdp.send("Runtime.evaluate", {"expression": "(()=>{const b=[...document.querySelectorAll('button,[role=button]')].find(b=>/Nuevo proyecto|New project/i.test(b.innerText||''));if(b)b.click();return !!b})()", "returnByValue": True}, self.session)
            for _ in range(30):
                await asyncio.sleep(0.5)
                pos = await locate()
                if pos:
                    break
        if not pos:
            await self.broadcast({"type": "order", "state": "failed", "message": "No encuentro el cuadro «¿Qué quieres crear?» de Flow. Abre un proyecto y vuelve a enviarlo."})
            return
        x, y = pos
        await self.mouse("mouseMoved", x, y)
        await self.mouse("mousePressed", x, y, button="left", buttons=1, clickCount=1)
        await self.mouse("mouseReleased", x, y, button="left", buttons=0, clickCount=1)
        await asyncio.sleep(0.2)
        await self.cdp.send("Input.insertText", {"text": text}, self.session)
        await asyncio.sleep(0.3)
        await self.key("Enter")
        await self.broadcast({"type": "order", "state": "sent"})
        log.info("Encargo enviado a Flow (%d caracteres)", len(text))

    async def report_focus(self) -> None:
        """Tras un toque: ¿quedó el cursor en un campo de texto? Nexus abre entonces su teclado."""
        await asyncio.sleep(0.2)
        expr = ("(()=>{let e=document.activeElement;while(e&&e.shadowRoot&&e.shadowRoot.activeElement)e=e.shadowRoot.activeElement;"
                "return !!e&&(e.isContentEditable||e.tagName==='TEXTAREA'||(e.tagName==='INPUT'&&!/^(button|checkbox|radio|submit|file|range|color)$/i.test(e.type)))})()")
        try:
            r = await self.cdp.send("Runtime.evaluate", {"expression": expr, "returnByValue": True}, self.session, timeout=5)
            await self.broadcast({"type": "focus", "editable": bool(r.get("result", {}).get("value"))})
        except Exception:
            pass

    async def key(self, k: str) -> None:
        vk, code, text = KEYS.get(k, (0, k, None))
        if not vk and len(k) == 1:
            await self.cdp.send("Input.insertText", {"text": k}, self.session)
            return
        down = {"type": "keyDown" if text else "rawKeyDown", "windowsVirtualKeyCode": vk, "code": code, "key": k}
        if text:
            down["text"] = text
        await self.cdp.send("Input.dispatchKeyEvent", down, self.session)
        await self.cdp.send("Input.dispatchKeyEvent", {"type": "keyUp", "windowsVirtualKeyCode": vk, "code": code, "key": k}, self.session)


live = Live()


def public_asset(a: dict) -> dict:
    return {k: a[k] for k in ("id", "name", "mime", "size", "sha256", "ts", "origin")}


# ---------------------------------------------------------------- acceso

_whois: dict[str, tuple[float, bool]] = {}
_owner: str | None = None


def tailscale(*args: str) -> dict | None:
    for exe in ("tailscale", r"C:\Program Files\Tailscale\tailscale.exe"):
        try:
            out = subprocess.run([exe, args[0], "--json", *args[1:]], capture_output=True, text=True, timeout=8,
                                 creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            if out.returncode == 0:
                return json.loads(out.stdout)
        except (OSError, ValueError, subprocess.TimeoutExpired):
            continue
    return None


def owner_login() -> str | None:
    global _owner
    if _owner is None:
        st = tailscale("status") or {}
        uid = str((st.get("Self") or {}).get("UserID", ""))
        _owner = ((st.get("User") or {}).get(uid) or {}).get("LoginName")
        log.info("Propietario de la red Tailscale: %s", _owner)
    return _owner


def allowed(ip: str) -> bool:
    try:
        addr = ipaddress.ip_address(ip)
    except ValueError:
        return False
    if addr.is_loopback:
        return True
    if addr not in TAILNET:
        return False
    hit = _whois.get(ip)
    if hit and time.time() - hit[0] < 600:
        return hit[1]
    who = tailscale("whois", ip) or {}
    login = (who.get("UserProfile") or {}).get("LoginName")
    ok = bool(login) and login == owner_login()
    _whois[ip] = (time.time(), ok)
    if not ok:
        log.warning("Acceso rechazado desde %s (%s)", ip, login)
    return ok


def host_ok(host: str | None) -> bool:
    return bool(host) and host.split(":")[0].lower() in ALLOWED_HOSTS


# ---------------------------------------------------------------- HTTP / WebSocket

async def health(request: Request) -> Response:
    if not host_ok(request.headers.get("host")) or not await asyncio.to_thread(allowed, request.client.host):
        return JSONResponse({"error": "forbidden"}, status_code=403)
    try:
        await live.ensure()
        ok = True
    except Exception as e:
        ok = False
        log.warning("health: %s", e)
    return JSONResponse({"ok": ok, "version": VERSION, **{k: live.info[k] for k in ("url", "title")}, "clients": len(live.clients),
                         "assets": len(live.assets)})


async def list_assets(request: Request) -> Response:
    if not host_ok(request.headers.get("host")) or not await asyncio.to_thread(allowed, request.client.host):
        return JSONResponse({"error": "forbidden"}, status_code=403)
    since = float(request.query_params.get("since", "0") or 0)
    return JSONResponse({"assets": [public_asset(a) for a in live.assets if a["ts"] > since]})


async def get_asset(request: Request) -> Response:
    if not host_ok(request.headers.get("host")) or not await asyncio.to_thread(allowed, request.client.host):
        return JSONResponse({"error": "forbidden"}, status_code=403)
    aid = request.path_params["aid"]
    a = next((x for x in live.assets if x["id"] == aid), None)
    if not a:
        return JSONResponse({"error": "not_found"}, status_code=404)
    return FileResponse(ROOT / "assets" / a["file"], media_type=a["mime"], filename=a["name"])


async def ws_live(ws: WebSocket) -> None:
    if not host_ok(ws.headers.get("host")) or not await asyncio.to_thread(allowed, ws.client.host):
        await ws.close(code=4403)
        return
    await ws.accept()
    client = Client(ws)
    try:
        await live.ensure()
    except Exception as e:
        await ws.send_text(json.dumps({"type": "error", "message": f"No se pudo abrir el navegador de Flow: {e}"}))
        await ws.close()
        return
    live.clients.add(client)
    await ws.send_text(json.dumps({"type": "hello", "version": VERSION, "flowUrl": FLOW_URL,
                                   "assets": [public_asset(a) for a in live.assets[-50:]]}, ensure_ascii=False))
    await ws.send_text(json.dumps(live.state(), ensure_ascii=False))

    async def sender() -> None:
        while True:
            await client.ready.wait()
            client.ready.clear()
            frame = client.frame
            if frame is None:
                continue
            meta = (round(live.meta.get("deviceWidth") or 0), round(live.meta.get("deviceHeight") or 0))
            if meta != client.sent_meta:
                client.sent_meta = meta
                await ws.send_text(json.dumps({"type": "meta", "w": meta[0], "h": meta[1]}))
            await ws.send_bytes(frame)

    send_task = asyncio.create_task(sender())
    try:
        if not live.casting:
            await live.start_cast()
        elif live.frame:
            client.frame = live.frame
            client.ready.set()
        while True:
            raw = await ws.receive_text()
            try:
                ev = json.loads(raw)
            except ValueError:
                continue
            try:
                await live.handle(ev)
            except (CdpError, asyncio.TimeoutError) as e:
                log.info("entrada %s: %s", ev.get("t"), e)
                if live.cdp is None or live.cdp.closed.is_set():
                    live.session = None
                    await live.ensure()
    except WebSocketDisconnect:
        pass
    except Exception:
        log.exception("ws")
    finally:
        send_task.cancel()
        live.clients.discard(client)
        if not live.clients:
            await live.stop_cast()


@asynccontextmanager
async def lifespan(_app):
    log.info("Flow en directo %s escuchando en %s:%d", VERSION, HOST, PORT)
    try:
        await live.ensure()
    except Exception as e:
        log.warning("Arranque sin navegador todavía: %s", e)
    yield


app = Starlette(routes=[
    Route("/health", health),
    Route("/assets", list_assets),
    Route("/assets/{aid}", get_asset),
    WebSocketRoute("/live", ws_live),
], lifespan=lifespan)


def main() -> None:
    while True:
        try:
            uvicorn.run(app, host=HOST, port=PORT, log_level="warning", log_config=None, ws_max_size=1 << 20, ws_ping_interval=20, ws_ping_timeout=30)
        except Exception:
            log.exception("servidor")
        time.sleep(5)


if __name__ == "__main__":
    main()
