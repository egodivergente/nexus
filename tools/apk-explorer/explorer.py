"""Explorador local de APK privadas. Python 3.10+, ADB y gh opcional."""
from __future__ import annotations

import argparse
import base64
from dataclasses import dataclass
from datetime import datetime
import hashlib
import html
import json
import os
from pathlib import Path
import queue
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time
import unicodedata
import uuid
import webbrowser
import xml.etree.ElementTree as ET

HOME = Path(__file__).resolve().parent
ASUS_HOST = "vidal@100.112.57.2"
DEFAULT_SETTINGS = {"host": "", "serial": "", "package": "com.vidadomu.nexus.agent",
                    "apk": "", "repo": "egodivergente/nexus", "tag": "", "ref": "",
                    "max_states": 60, "minutes": 15, "source": False, "device_model": ""}


def settings_path():
    base = Path(os.environ.get("LOCALAPPDATA") or Path.home() / ".config")
    return base / "APK Explorer" / "settings.json"


def load_settings(path=None):
    result = DEFAULT_SETTINGS.copy()
    try:
        stored = json.loads((path or settings_path()).read_text(encoding="utf-8"))
        if isinstance(stored, dict):
            for key, default in DEFAULT_SETTINGS.items():
                if key in stored and type(stored[key]) is type(default):
                    result[key] = stored[key]
        if not 1 <= result["max_states"] <= 500:
            result["max_states"] = 60
        if not 1 <= result["minutes"] <= 120:
            result["minutes"] = 15
    except (OSError, ValueError):
        pass
    return result


def save_settings(values, path=None):
    path = path or settings_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    # Only preferences, never credentials or arbitrary extra fields.
    clean = {key: values.get(key, default) for key, default in DEFAULT_SETTINGS.items()}
    temporary = path.with_name("settings-" + uuid.uuid4().hex + ".tmp")
    temporary.write_text(json.dumps(clean, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def parse_devices(output):
    devices = []
    for line in output.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            model = next((p[6:] for p in parts[2:] if p.startswith("model:")), "Android")
            name = {"SM_S938B": "Samsung S25 Ultra", "Pixel_8_Pro": "Pixel 8 Pro"}.get(model, model.replace("_", " "))
            devices.append({"serial": parts[0], "model": model, "label": f"{name} · {parts[0]}"})
    return devices


def preferred_device(devices, serial="", model=""):
    for key, value in (("serial", serial), ("model", model), ("model", "SM_S938B"), ("model", "Pixel_8_Pro")):
        if value:
            match = next((d for d in devices if d[key] == value), None)
            if match:
                return match
    return devices[0] if devices else None


def discover_devices(host, fallback=False):
    errors = []
    hosts = list(dict.fromkeys([host] + (["", ASUS_HOST] if fallback else [])))
    for candidate in hosts:
        try:
            found = parse_devices(Android(host=candidate).devices())
            if found:
                return candidate, found, errors
        except (RuntimeError, OSError, subprocess.TimeoutExpired) as exc:
            errors.append(f"{candidate or 'Este PC'}: {exc}")
    return host, [], errors


def command(args, timeout=60, binary=False):
    p = subprocess.run([str(a) for a in args], capture_output=True, timeout=timeout,
                       creationflags=0x08000000 if os.name == "nt" else 0)
    if p.returncode:
        raise RuntimeError(p.stderr.decode("utf-8", "replace").strip() or
                           p.stdout.decode("utf-8", "replace").strip() or f"Error {p.returncode}")
    return p.stdout if binary else p.stdout.decode("utf-8", "replace").strip()


def executable(name):
    found = shutil.which(name)
    if not found and name == "adb":
        candidate = Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"
        if candidate.exists():
            found = str(candidate)
    if not found:
        raise RuntimeError(f"No se encuentra {name}. Instálalo y añádelo al PATH.")
    return found


def apk_package(apk):
    roots = [Path(v) for v in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")) if v]
    roots.append(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk")
    tools = [shutil.which("aapt2"), shutil.which("aapt")]
    for root in roots:
        tools.extend(str(p) for p in sorted((root / "build-tools").glob("*/aapt*"), reverse=True)
                     if p.name in ("aapt2", "aapt", "aapt.exe", "aapt2.exe"))
    for tool in filter(None, tools):
        try:
            match = re.search(r"package: name='([^']+)'", command([tool, "dump", "badging", apk]))
            if match:
                return match[1]
        except (RuntimeError, OSError, subprocess.TimeoutExpired):
            continue
    return ""


class Android:
    def __init__(self, serial="", host=""):
        self.serial, self.host = serial, host
        self.adb = "adb" if host else executable("adb")

    def call(self, *args, binary=False, timeout=60, select=True):
        argv = [self.adb] + (["-s", self.serial] if select and self.serial else []) + list(args)
        if self.host:
            argv = [executable("ssh"), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
                    "-o", "ConnectTimeout=8", self.host, shlex.join(argv)]
        return command(argv, timeout, binary)

    def devices(self):
        return self.call("devices", "-l", select=False)

    def shell(self, *args, **kwargs):
        # ADB shell joins arguments itself; quote for the Android shell as well.
        return self.call("shell", shlex.join([str(a) for a in args]), **kwargs)

    def validate(self):
        if not self.serial or not re.fullmatch(r"[A-Za-z0-9_.:\-]+", self.serial):
            raise RuntimeError("Selecciona un serial ADB explícito y válido.")
        if self.call("get-state") != "device":
            raise RuntimeError("Dispositivo no autorizado o desconectado.")
        return self.shell("getprop", "ro.product.model")

    def install(self, apk):
        apk = Path(apk).resolve(strict=True)
        if apk.suffix.lower() != ".apk":
            raise RuntimeError("Selecciona un archivo .apk.")
        if not self.host:
            result = self.call("install", "-r", str(apk), timeout=180)
        else:
            remote = f"/tmp/apk-explorer-{uuid.uuid4().hex}.apk"
            command([executable("scp"), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
                     str(apk), f"{self.host}:{remote}"], timeout=180)
            try:
                result = self.call("install", "-r", remote, timeout=180)
            finally:
                command([executable("ssh"), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
                         self.host, "rm -f " + shlex.quote(remote)])
        if "Success" not in result:
            raise RuntimeError(result)

    def launch(self, package):
        activity = self.shell("cmd", "package", "resolve-activity", "--brief", "--user", "0", package)
        component = next((line.strip() for line in reversed(activity.splitlines())
                          if re.fullmatch(r"[\w.]+/[\w.$]+", line.strip())), None)
        if not component:
            raise RuntimeError(f"Sin actividad de inicio para {package}: {activity}")
        result = self.shell("am", "start", "--user", "0", "-n", component)
        if "Error" in result or "Exception" in result:
            raise RuntimeError(result)

    def dump(self):
        remote = f"/sdcard/Download/apk-explorer-{uuid.uuid4().hex}.xml"
        try:
            self.shell("uiautomator", "dump", remote, timeout=35)
            raw = self.shell("cat", remote)
            ET.fromstring(raw)
            return raw
        finally:
            self.shell("rm", "-f", remote)

    def screenshot(self):
        png = self.call("exec-out", "screencap", "-p", binary=True)
        if not png.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("El dispositivo no devolvió una captura PNG válida.")
        return png


class GitHub:
    def __init__(self, repo):
        repo = repo.strip().removeprefix("https://github.com/").removesuffix(".git").rstrip("/")
        if not re.fullmatch(r"[\w.-]+/[\w.-]+", repo):
            raise ValueError("Repositorio: propietario/nombre o URL de GitHub.")
        self.repo = repo
        self.gh = executable("gh")

    def api(self, endpoint):
        return json.loads(command([self.gh, "api", f"repos/{self.repo}/{endpoint}"]))

    def assets(self, tag=""):
        from urllib.parse import quote
        if tag:
            release = self.api("releases/tags/" + quote(tag, safe=""))
        else:
            releases = self.api("releases?per_page=30")
            release = next((r for r in releases if not r.get("draft")), None)
            if not release:
                raise RuntimeError("Este repositorio no tiene releases publicadas. Usa una APK local.")
        return release["tag_name"], [a for a in release["assets"] if a["name"].lower().endswith(".apk")]

    def download(self, asset, folder):
        folder.mkdir(parents=True, exist_ok=True)
        name = asset["name"]
        if Path(name).name != name or "/" in name or "\\" in name:
            raise ValueError("Nombre de asset inválido.")
        data = command([self.gh, "api", f"repos/{self.repo}/releases/assets/{int(asset['id'])}",
                        "-H", "Accept: application/octet-stream"], timeout=180, binary=True)
        if not data.startswith(b"PK"):
            raise RuntimeError("El asset descargado no es un APK/ZIP.")
        digest = hashlib.sha256(data).hexdigest()
        expected = asset.get("digest", "") or ""
        if expected.startswith("sha256:") and expected[7:] != digest:
            raise RuntimeError("El SHA-256 no coincide con el asset publicado en GitHub.")
        path = folder / name
        path.write_bytes(data)
        path.with_suffix(".provenance.json").write_text(json.dumps({"repo": self.repo,
            "asset_id": asset["id"], "name": name, "sha256": digest,
            "github_digest_verified": expected.startswith("sha256:")}, indent=2), encoding="utf-8")
        return path

    def source_map(self, ref=""):
        # Read blobs through the API: no clone, hooks, builds or execution of repository code.
        if not ref:
            ref = self.api("")["default_branch"]
        from urllib.parse import quote
        commit = self.api("commits/" + quote(ref, safe=""))["sha"]
        tree = self.api(f"git/trees/{commit}?recursive=1")
        paths = [x for x in tree["tree"] if x["type"] == "blob" and
                 x["path"].endswith((".kt", ".java", ".xml")) and
                 re.search(r"nav|route|screen|activity|AndroidManifest", x["path"], re.I) and
                 x.get("size", 0) < 200_000]
        results = []
        for entry in paths[:30]:
            blob = self.api("git/blobs/" + entry["sha"])
            if blob.get("encoding") != "base64":
                continue
            text = base64.b64decode(blob["content"]).decode("utf-8", "replace")
            for number, line in enumerate(text.splitlines(), 1):
                if re.search(r"composable\s*[(<]|dialog\s*[(<]|<activity\b|<fragment\b|<dialog\b|(?:route|startDestination)\s*=", line):
                    results.append({"path": entry["path"], "line": number,
                                    "candidate": line.strip()[:350]})
        return {"repo": self.repo, "ref": ref, "commit": commit, "candidates": results,
                "files_inspected": min(30, len(paths)), "candidate_files": len(paths),
                "tree_truncated": tree.get("truncated", False),
                "note": "Inventario estático parcial. No demuestra que una pantalla se haya visitado."}


def normalized(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text.lower()) if not unicodedata.combining(c))


BLOCKED = re.compile(r"\b(delete|remove|erase|clear|reset|purchase|buy|pay|subscribe|send|submit|"
                     r"generate|order|confirm|accept|allow|install|uninstall|logout|sign out|"
                     r"eliminar|borrar|vaciar|restablecer|comprar|pagar|suscribir\w*|enviar|"
                     r"generar|confirmar|aceptar|permitir|instalar|desinstalar|cerrar sesion|"
                     r"guardar|save|publicar|publish|upload|subir|transfer\w*|checkout)\b")


@dataclass
class Action:
    kind: str
    bounds: tuple
    label: str

    def run(self, device):
        x1, y1, x2, y2 = self.bounds
        x, y = (x1 + x2) // 2, (y1 + y2) // 2
        if self.kind == "tap":
            device.shell("input", "tap", x, y)
        else:
            delta = int((y2 - y1) * .30)
            device.shell("input", "swipe", x, y + delta, x, y - delta, 350)


def navigation_first(actions):
    bottom = max((a.bounds[3] for a in actions), default=0)
    def priority(action):
        if action.kind == "scroll":
            return 3
        label = normalized(action.label)
        if bottom and action.bounds[1] >= bottom * .88:
            return 0
        if re.search(
                r"\b(inicio|home|modelos|models|biblioteca|library|biblio|galeria|gallery|"
                r"lienzo|canvas|ajustes|settings|perfil|profile|proyecto|project)\b", label):
            return 1
        return 2
    return sorted(actions, key=priority)


def inspect(raw, package):
    root = ET.fromstring(raw)
    nodes = list(root.iter("node"))
    app_nodes = [n for n in nodes if n.get("package") == package]
    # System permission dialogs and other apps are boundaries, never explored.
    foreign = {n.get("package", "") for n in nodes} - {package, "", "com.android.systemui"}
    lockscreen = any("lockscreen" in n.get("resource-id", "").lower() or
                     "keyguard" in n.get("resource-id", "").lower() for n in nodes)
    owned = bool(app_nodes) and not foreign and not lockscreen
    signature = [(n.get("package"), n.get("class"), n.get("resource-id"), n.get("text"),
                  n.get("content-desc"), n.get("bounds"), n.get("checked"), n.get("selected"))
                 for n in nodes if n.get("package") != "com.android.systemui"]
    key = hashlib.sha256(json.dumps(signature, ensure_ascii=False).encode()).hexdigest()[:20]
    actions, skipped, seen = [], [], set()
    for node in app_nodes if owned else []:
        if node.get("enabled") != "true":
            continue
        bounds = tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
        if len(bounds) != 4 or bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
            continue
        descendants = list(node.iter("node"))
        label = " ".join(filter(None, [v for n in descendants for v in
                         (n.get("text", ""), n.get("content-desc", ""))])).strip()
        label = label or node.get("resource-id") or node.get("class", "elemento")
        risk_text = " ".join(n.get("resource-id", "") + " " + n.get("text", "") + " " +
                             n.get("content-desc", "") for n in descendants)
        risk_text = re.sub(r"([a-z])([A-Z])", r"\1 \2", risk_text).replace("_", " ")
        if node.get("clickable") == "true":
            if (BLOCKED.search(normalized(risk_text)) or any(n.get("password") == "true" or
                    "EditText" in n.get("class", "") for n in descendants)):
                skipped.append(label[:180])
            elif ("tap", bounds) not in seen:
                actions.append(Action("tap", bounds, label[:180]))
                seen.add(("tap", bounds))
        if node.get("scrollable") == "true" and ("scroll", bounds) not in seen:
            actions.append(Action("scroll", bounds, "Desplazar " + label[:120]))
            seen.add(("scroll", bounds))
    title = next((n.get("text") for n in app_nodes if n.get("text")), "Pantalla")
    return key, title[:120], actions, skipped, owned


class Explorer:
    def __init__(self, device, package, output, max_states=80, max_actions=180,
                 minutes=15, depth=10, delay=1.2, stop=None, log=print, strategy="depth"):
        if not re.fullmatch(r"[A-Za-z][\w]*(?:\.[A-Za-z][\w]*)+", package):
            raise ValueError("Indica el package Android, por ejemplo com.empresa.app.")
        self.device, self.package, self.output = device, package, Path(output)
        self.max_states, self.max_actions = max_states, max_actions
        self.minutes = minutes
        self.deadline = time.monotonic() + minutes * 60
        self.depth, self.delay = depth, delay
        self.stop, self.log = stop or threading.Event(), log
        self.states, self.edges, self.notes = {}, [], []
        self.count = 0
        self.strategy = strategy
        self.report = {"package": package, "created": datetime.now().isoformat(),
                       "status": "running", "coverage": "Parcial; no garantiza todas las interfaces.",
                       "strategy": strategy, "limits": {"states": max_states, "actions": max_actions,
                                                         "minutes": minutes, "depth": depth},
                       "states": [], "edges": self.edges, "notes": self.notes}

    def available(self):
        return (not self.stop.is_set() and time.monotonic() < self.deadline and
                len(self.states) < self.max_states and self.count < self.max_actions)

    def stable_dump(self):
        previous = None
        raw = ""
        for _ in range(4):
            raw = self.device.dump()
            key = inspect(raw, self.package)[0]
            if key == previous:
                return raw
            previous = key
            if self.stop.wait(min(self.delay, .3)):
                return raw
        self.notes.append("La interfaz seguía cambiando al capturar; revisar esta ruta.")
        return raw

    def observe(self, route, raw=None):
        raw = raw if raw is not None else self.stable_dump()
        key, title, actions, skipped, owned = inspect(raw, self.package)
        if key not in self.states:
            number = len(self.states) + 1
            stem = f"screen-{number:04}"
            png = self.device.screenshot()
            (self.output / (stem + ".png")).write_bytes(png)
            (self.output / (stem + ".xml")).write_text(raw, encoding="utf-8")
            state = {"id": key, "number": number, "title": title, "image": stem + ".png",
                     "xml": stem + ".xml", "route": route, "skipped": skipped,
                     "owned": owned, "sha256": hashlib.sha256(png).hexdigest(),
                     "available_actions": [{"kind": a.kind, "label": a.label, "bounds": a.bounds}
                                           for a in actions]}
            self.states[key] = state
            self.report["states"].append(state)
            self.log(f"{number:03} · {title}" + (" [límite externo]" if not owned else ""))
            self.save()
        return key, actions if owned else []

    def perform(self, action):
        if not self.available():
            return False
        self.count += 1
        action.run(self.device)
        self.stop.wait(self.delay)
        return not self.stop.is_set()

    def restore(self, root_key, path):
        if not self.available():
            return False
        self.device.shell("am", "force-stop", "--user", "0", self.package)
        self.device.launch(self.package)
        self.stop.wait(self.delay)
        current = inspect(self.stable_dump(), self.package)[0]
        if current != root_key:
            self.notes.append("La pantalla inicial cambió; una ruta no pudo reproducirse.")
            return False
        for expected, action, target in path:
            if current != expected or not self.perform(action):
                self.notes.append("No se pudo repetir un paso de la ruta: " + action.label)
                return False
            current = inspect(self.stable_dump(), self.package)[0]
            if current != target:
                self.notes.append("Ruta no reproducible: " + action.label)
                return False
        return True

    def crawl(self, root_key, key, actions, path, route):
        if len(path) >= self.depth:
            self.notes.append(f"Profundidad máxima en {key}.")
            return
        for index, action in enumerate(actions):
            if not self.available():
                return
            if index and not self.restore(root_key, path):
                return
            if not self.perform(action):
                return
            next_route = route + [action.label]
            raw = self.stable_dump()
            target = inspect(raw, self.package)[0]
            known = target in self.states
            target, next_actions = self.observe(next_route, raw)
            self.edges.append({"from": key, "to": target, "action": action.label,
                               "kind": action.kind, "bounds": action.bounds})
            self.save()
            if not known:
                self.crawl(root_key, target, next_actions, path + [(key, action, target)], next_route)

    def crawl_breadth(self, root_key, actions):
        from collections import deque
        pending = deque([(root_key, actions, [], [])])
        first = True
        while pending and self.available():
            key, candidates, path, route = pending.popleft()
            if len(path) >= self.depth:
                self.notes.append(f"Profundidad máxima en {key}.")
                continue
            for action in navigation_first(candidates):
                if not self.available():
                    return
                if not first and not self.restore(root_key, path):
                    break
                first = False
                if not self.perform(action):
                    return
                raw = self.stable_dump()
                target = inspect(raw, self.package)[0]
                known = target in self.states
                next_route = route + [action.label]
                target, next_actions = self.observe(next_route, raw)
                self.edges.append({"from": key, "to": target, "action": action.label,
                                   "kind": action.kind, "bounds": action.bounds})
                self.save()
                if not known:
                    pending.append((target, next_actions, path + [(key, action, target)], next_route))

    def run(self, capture_only=False, source=None):
        self.output.mkdir(parents=True, exist_ok=False)
        self.deadline = time.monotonic() + self.minutes * 60
        try:
            self.report["device"] = self.device.validate()
            self.report["serial"] = self.device.serial
            self.report["transport"] = self.device.host or "local"
            if source:
                self.report["source"] = source
            if not capture_only:
                if self.strategy == "breadth":
                    self.device.shell("am", "force-stop", "--user", "0", self.package)
                self.device.launch(self.package)
                self.stop.wait(self.delay)
            key, actions = self.observe([])
            if not self.states[key]["owned"]:
                self.notes.append("La app objetivo no está visible o hay un diálogo/superficie externa.")
                self.report["status"] = "blocked"
            elif capture_only:
                self.report["status"] = "capture_only"
            else:
                if self.strategy == "breadth":
                    self.crawl_breadth(key, actions)
                else:
                    self.crawl(key, key, actions, [], [])
                self.report["status"] = "stopped" if self.stop.is_set() else (
                    "limit_reached" if not self.available() else "finished_partial")
        except Exception as exc:
            self.report["status"] = "error"
            self.notes.append(str(exc))
            raise
        finally:
            self.report["actions_executed"] = self.count
            self.save()
        return self.output / "index.html"

    def save(self):
        self.report["actions_executed"] = self.count
        target = self.output / "report.json"
        temporary = target.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(target)
        esc = html.escape
        cards = "".join(f'<article><a href="{s["image"]}"><img loading="lazy" src="{s["image"]}"></a>'
                        f'<h2>{s["number"]}. {esc(s["title"])}</h2>'
                        f'<p>{esc(" → ".join(s["route"]) or "Inicio")}</p>'
                        f'<details><summary>Acciones omitidas ({len(s["skipped"])})</summary>'
                        f'<pre>{esc(chr(10).join(s["skipped"]))}</pre></details>'
                        f'<p><a href="{s["xml"]}">Árbol de interfaz</a></p></article>'
                        for s in self.report["states"])
        source = esc(json.dumps(self.report.get("source", {}), ensure_ascii=False, indent=2))
        page = f'''<!doctype html><html lang="es"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>APK Explorer · {esc(self.package)}</title><style>
body{{font:15px system-ui;background:#11151d;color:#e5eafa;margin:30px}}a{{color:#a6bfff}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(245px,1fr));gap:20px}}
article{{background:#1e2533;padding:15px;border-radius:16px;overflow-wrap:anywhere}}
img{{width:100%;height:400px;object-fit:contain;background:#090b10}}h2{{font-size:17px}}
pre{{white-space:pre-wrap;overflow-wrap:anywhere}}p{{color:#b8c5d9}}input{{padding:12px;width:90%;max-width:500px;margin-bottom:20px}}
</style><h1>APK Explorer</h1><p>{esc(self.package)} · {len(self.states)} capturas ·
{esc(self.report['status'])} · {self.count} acciones</p>
<p>Cobertura parcial. Las rutas del código son candidatas, las capturas prueban las visitas.</p>
<p><a href="report.json">Informe y mapa de navegación JSON</a></p>
<input placeholder="Filtrar pantallas o rutas" aria-label="Filtrar" oninput="document.querySelectorAll('article').forEach(x=>x.hidden=!x.innerText.toLowerCase().includes(this.value.toLowerCase()))">
<div class="grid">{cards}</div><h2>Observaciones</h2><pre>{esc(chr(10).join(self.notes))}</pre>
<details><summary>Inventario del código GitHub</summary><pre>{source}</pre></details></html>'''
        (self.output / "index.html").write_text(page, encoding="utf-8")


def session_path():
    return HOME / "sessions" / (datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6])


def agent_bundle(folder):
    """Package existing evidence without touching a device or repeating exploration."""
    folder = Path(folder).resolve(strict=True)
    report = json.loads((folder / "report.json").read_text(encoding="utf-8"))
    screens, pending = [], []
    traversed = {(x["from"], x["kind"], tuple(x["bounds"])) for x in report["edges"]}
    for state in report["states"]:
        labels = []
        for node in ET.fromstring((folder / state["xml"]).read_text(encoding="utf-8")).iter("node"):
            if node.get("password") == "true":
                continue
            for value in (node.get("text"), node.get("content-desc")):
                if value and value not in labels:
                    labels.append(value[:240])
        screens.append({"number": state["number"], "id": state["id"], "title": state["title"],
                        "image": str(folder / state["image"]), "ui_tree": str(folder / state["xml"]),
                        "route": state["route"], "owned": state["owned"], "sha256": state["sha256"],
                        "visible_labels": labels[:40], "omitted_controls": state["skipped"]})
        actual_digest = hashlib.sha256((folder / state["image"]).read_bytes()).hexdigest()
        screens[-1]["integrity_verified"] = actual_digest == state["sha256"]
        if not screens[-1]["integrity_verified"]:
            screens[-1]["capture_warning"] = "Image digest differs from the recorded capture."
        for action in state.get("available_actions", []):
            if (state["id"], action["kind"], tuple(action["bounds"])) not in traversed:
                pending.append({"screen": state["number"], **action})
    manifest = {"schema_version": 1, "status": report["status"], "package": report["package"],
                "output": str(folder), "gallery": str(folder / "index.html"),
                "report": str(folder / "report.json"), "manifest": str(folder / "agent.json"),
                "overviews": [], "screens": screens, "pending_actions": pending,
                "coverage": {"complete": False, "visited_states": len(screens),
                             "app_states": sum(s["owned"] for s in screens),
                             "actions_executed": report.get("actions_executed", 0),
                             "pending_actions": len(pending) if all("available_actions" in s for s in report["states"]) else None,
                             "omitted_controls": sum(len(s["omitted_controls"]) for s in screens)},
                "notes": report["notes"][:], "limits": report.get("limits"),
                "strategy": report.get("strategy"), "apk": report.get("apk"), "source": report.get("source"),
                "next_step": "Open the overview images, then original screenshots for details. "
                             "Screenshots are visual evidence; labels and source candidates are not visual validation."}
    try:
        from PIL import Image, ImageDraw, ImageFont, ImageStat
        font_path = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts/segoeui.ttf"
        font = ImageFont.truetype(str(font_path), 17) if font_path.exists() else ImageFont.load_default(size=17)
        for offset in range(0, len(screens), 9):
            batch = screens[offset:offset + 9]
            sheet = Image.new("RGB", (min(3, len(batch)) * 300, ((len(batch) + 2) // 3) * 730), "#11151d")
            draw = ImageDraw.Draw(sheet)
            for index, screen in enumerate(batch):
                x, y = (index % 3) * 300, (index // 3) * 730
                with Image.open(screen["image"]) as original:
                    picture = original.convert("RGB")
                w, h = picture.size
                middle = picture.crop((int(w * .05), int(h * .05), int(w * .95), int(h * .90)))
                stats = ImageStat.Stat(middle.resize((64, 64)).convert("L"))
                if stats.stddev[0] < 2 and not screen.get("capture_warning"):
                    screen["capture_warning"] = "Uniform capture: may be blank, protected or not rendered; inspect original."
                picture.thumbnail((280, 615))
                sheet.paste(picture, (x + (300 - picture.width) // 2, y + 55))
                title = f'{screen["number"]:02}. {screen["title"]}'
                draw.text((x + 10, y + 10), title[:30], fill="#e5eafa", font=font)
                route = " > ".join(screen["route"]) or "Inicio"
                draw.text((x + 10, y + 675), route[:32], fill="#b8c5d9", font=font)
                if screen.get("capture_warning") or not screen["owned"]:
                    draw.text((x + 10, y + 702), "REVISAR CAPTURA", fill="#ffbc7a", font=font)
            target = folder / f"overview-{offset // 9 + 1:03}.jpg"
            sheet.save(target, quality=88)
            manifest["overviews"].append(str(target))
    except (ImportError, OSError, ValueError) as exc:
        manifest["notes"].append("No se completó la lámina resumen: " + str(exc))
    (folder / "agent.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def gui():
    import tkinter as tk
    from tkinter import ttk, filedialog, messagebox
    root = tk.Tk()
    root.title("APK Explorer · Privado y local")
    root.geometry("850x800")
    frame = ttk.Frame(root, padding=20)
    frame.pack(fill="both", expand=True)
    ttk.Label(frame, text="APK Explorer", font=("Segoe UI", 24, "bold")).pack(anchor="w")
    ttk.Label(frame, text="Explora tus apps y reúne sus interfaces en una galería local.").pack(anchor="w")
    saved = load_settings()
    fields = {}
    connection = tk.StringVar()
    device_choice = tk.StringVar()
    device_options = []
    for label, key, default in [("Conexión", "host", ""),
                                ("Móvil (detección automática)", "serial", ""),
                                ("Package de la app (com.empresa.app)", "package", ""),
                                ("APK local (opcional; se instala conservando datos)", "apk", ""),
                                ("Repositorio GitHub privado: propietario/repo (opcional)", "repo", ""),
                                ("Tag de release (vacío = última publicada)", "tag", ""),
                                ("Referencia del código (vacío = rama predeterminada)", "ref", "")]:
        ttk.Label(frame, text=label).pack(anchor="w", pady=(8, 0))
        fields[key] = tk.StringVar(value=saved.get(key, default))
        if key == "host":
            connection_box = ttk.Combobox(frame, textvariable=connection, state="readonly")
            profiles = {"Este PC": "", "ASUS": ASUS_HOST}
            if saved["host"] not in profiles.values():
                profiles[saved["host"]] = saved["host"]
            connection_box["values"] = tuple(profiles)
            connection.set(next(name for name, host in profiles.items() if host == saved["host"]))
            connection_box.pack(fill="x")
        elif key == "serial":
            device_box = ttk.Combobox(frame, textvariable=device_choice, state="readonly")
            device_box.pack(fill="x")
        elif key == "package":
            package_box = ttk.Combobox(frame, textvariable=fields[key])
            package_box.pack(fill="x")
        else:
            ttk.Entry(frame, textvariable=fields[key]).pack(fill="x")
    messages = queue.Queue()
    stop = threading.Event()
    busy = False
    assets = []
    last = [None]
    options = ttk.Frame(frame)
    options.pack(fill="x", pady=10)
    ttk.Label(options, text="Máx. pantallas").pack(side="left")
    limit = tk.IntVar(value=saved["max_states"])
    ttk.Spinbox(options, from_=1, to=500, width=6, textvariable=limit).pack(side="left", padx=8)
    ttk.Label(options, text="Minutos").pack(side="left")
    minutes = tk.IntVar(value=saved["minutes"])
    ttk.Spinbox(options, from_=1, to=120, width=6, textvariable=minutes).pack(side="left", padx=8)
    source = tk.BooleanVar(value=saved["source"])
    ttk.Checkbutton(frame, text="Leer candidatos de pantallas del código GitHub", variable=source).pack(anchor="w")
    ttk.Label(frame, text="No sube APK ni capturas. Omite acciones sensibles reconocibles; usa datos de prueba.").pack(anchor="w")
    buttons = ttk.Frame(frame)
    buttons.pack(fill="x", pady=10)
    log = tk.Text(frame, height=10, wrap="word", font=("Consolas", 10))
    log.pack(fill="both", expand=True)

    def background(fn):
        nonlocal busy
        if busy:
            return
        busy = True
        def work():
            try:
                fn()
            except Exception as exc:
                messages.put(("log", "ERROR: " + str(exc)))
            finally:
                messages.put(("done", None))
        threading.Thread(target=work, daemon=True).start()

    def values():
        return {k: v.get().strip() for k, v in fields.items()}

    def persist(*_):
        try:
            updated = values() | {"max_states": limit.get(), "minutes": minutes.get(),
                                  "source": source.get(), "device_model": saved["device_model"]}
            save_settings(updated)
        except (OSError, tk.TclError) as exc:
            log.insert("end", "No se pudieron guardar las preferencias: " + str(exc) + "\n")

    def devices(auto=False):
        v = values()
        def work():
            messages.put(("devices", discover_devices(v["host"], fallback=auto)))
        background(work)

    def choose_connection(_=None):
        if busy:
            connection.set(next(name for name, host in profiles.items() if host == fields["host"].get()))
            return
        fields["host"].set(profiles[connection.get()])
        fields["serial"].set("")
        device_choice.set("")
        device_box["values"] = ()
        persist()
        devices()

    def choose_device(_=None):
        selected = next((d for d in device_options if d["label"] == device_choice.get()), None)
        if selected:
            fields["serial"].set(selected["serial"])
            saved["device_model"] = selected["model"]
            persist()

    def apps():
        v = values()
        def work():
            device = Android(v["serial"], v["host"])
            device.validate()
            packages = device.shell("pm", "list", "packages", "-3", "--user", "0")
            messages.put(("packages", sorted(line.removeprefix("package:").strip()
                          for line in packages.splitlines() if line.startswith("package:"))))
        background(work)

    def releases():
        v = values()
        def work():
            tag, found = GitHub(v["repo"]).assets(v["tag"])
            messages.put(("assets", (tag, found)))
        background(work)

    def download(index, repo):
        asset = assets[index]
        def work():
            path = GitHub(repo).download(asset, session_path() / "downloads")
            messages.put(("apk", str(path)))
            package = apk_package(path)
            if package:
                messages.put(("package", package))
        background(work)

    def pick_apk():
        path = filedialog.askopenfilename(filetypes=[("APK", "*.apk")])
        if path:
            fields["apk"].set(path)
            def work():
                package = apk_package(path)
                if package:
                    messages.put(("package", package))
                else:
                    messages.put(("log", "No se pudo detectar el package; introdúcelo en el campo correspondiente."))
            background(work)

    def start():
        v = values()
        try:
            n, mins = limit.get(), minutes.get()
            if not 1 <= n <= 500 or not 1 <= mins <= 120:
                raise ValueError("Revisa los límites de pantallas y minutos.")
            use_source = source.get()
        except (ValueError, tk.TclError) as exc:
            messagebox.showerror("Límites", str(exc))
            return
        if busy:
            return
        persist()
        stop.clear()
        def work():
            device = Android(v["serial"], v["host"])
            output = session_path()
            explorer = Explorer(device, v["package"], output, max_states=n, minutes=mins,
                                stop=stop, log=lambda s: messages.put(("log", s)))
            device.validate()
            if v["apk"]:
                messages.put(("log", "Instalando APK…"))
                device.install(v["apk"])
                explorer.report["apk"] = {"name": Path(v["apk"]).name,
                    "sha256": hashlib.sha256(Path(v["apk"]).read_bytes()).hexdigest()}
            source_map = GitHub(v["repo"]).source_map(v["ref"]) if use_source else None
            last[0] = output / "index.html"
            try:
                result = explorer.run(source=source_map)
                messages.put(("open", result))
            finally:
                messages.put(("log", "Informe: " + str(last[0])))
        background(work)

    ttk.Button(buttons, text="Dispositivos", command=devices).pack(side="left", padx=3)
    ttk.Button(buttons, text="Apps instaladas", command=apps).pack(side="left", padx=3)
    ttk.Button(buttons, text="Elegir APK", command=pick_apk).pack(side="left", padx=3)
    ttk.Button(buttons, text="APK de GitHub", command=releases).pack(side="left", padx=3)
    ttk.Button(buttons, text="Explorar", command=start).pack(side="left", padx=3)
    ttk.Button(buttons, text="Detener", command=stop.set).pack(side="left", padx=3)
    ttk.Button(buttons, text="Galería", command=lambda: webbrowser.open(last[0].as_uri()) if last[0] else None).pack(side="left", padx=3)

    def poll():
        nonlocal busy, assets, device_options
        while not messages.empty():
            kind, value = messages.get_nowait()
            if kind == "done":
                busy = False
            elif kind == "devices":
                host, device_options, errors = value
                fields["host"].set(host)
                connection.set(next(name for name, address in profiles.items() if address == host))
                device_box["values"] = tuple(d["label"] for d in device_options)
                chosen = preferred_device(device_options, fields["serial"].get(), saved["device_model"])
                if chosen:
                    device_choice.set(chosen["label"])
                    choose_device()
                    log.insert("end", f"Conectado: {connection.get()} → {chosen['label']}\n")
                else:
                    fields["serial"].set("")
                    device_choice.set("")
                    log.insert("end", "No hay móviles ADB autorizados disponibles.\n" + "\n".join(errors) + "\n")
            elif kind == "packages":
                package_box["values"] = tuple(value)
                if value:
                    package_box.event_generate("<Down>")
                log.insert("end", f"{len(value)} apps disponibles en el selector de app.\n")
            elif kind == "package":
                fields["package"].set(value)
            elif kind == "apk":
                fields["apk"].set(value)
                log.insert("end", "APK descargada: " + value + "\n")
            elif kind == "assets":
                tag, assets = value
                if not assets:
                    log.insert("end", "La release no contiene APK. Puedes usar un archivo local.\n")
                    continue
                dialog = tk.Toplevel(root)
                dialog.title("APK de GitHub · " + tag)
                repo = fields["repo"].get()
                for i, asset in enumerate(assets):
                    def choose(index=i):
                        dialog.destroy()
                        download(index, repo)
                    ttk.Button(dialog, text=asset["name"], command=choose).pack(fill="x", padx=20, pady=5)
            elif kind == "open":
                webbrowser.open(value.as_uri())
            else:
                log.insert("end", value + "\n")
                log.see("end")
        root.after(150, poll)

    def close():
        persist()
        if busy:
            stop.set()
            log.insert("end", "Deteniendo… espera a que se guarde el informe antes de cerrar.\n")
        else:
            root.destroy()
    root.protocol("WM_DELETE_WINDOW", close)
    connection_box.bind("<<ComboboxSelected>>", choose_connection)
    device_box.bind("<<ComboboxSelected>>", choose_device)
    save_timer = [None]
    def schedule_save(*_):
        if save_timer[0]:
            root.after_cancel(save_timer[0])
        save_timer[0] = root.after(600, persist)
    for variable in [*fields.values(), limit, minutes, source]:
        variable.trace_add("write", schedule_save)
    poll()
    root.after(200, lambda: devices(auto=True))
    root.mainloop()


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package")
    parser.add_argument("--serial")
    parser.add_argument("--ssh")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--repo", help="Repositorio GitHub privado: propietario/nombre")
    parser.add_argument("--ref", default="")
    parser.add_argument("--source-map", action="store_true")
    parser.add_argument("--release", help="Tag o 'latest': descargar APK de una release")
    parser.add_argument("--asset", help="Nombre exacto del APK si la release contiene varios")
    parser.add_argument("--devices", action="store_true")
    parser.add_argument("--capture-only", action="store_true", help="Captura la pantalla actual sin navegar")
    parser.add_argument("--inspect", nargs="?", const="", help="Inspección visual para agentes: APK o package; detecta conexión")
    parser.add_argument("--json", action="store_true", help="Devuelve el manifiesto JSON por stdout, progreso por stderr")
    parser.add_argument("--bundle", type=Path, help="Prepara capturas de una sesión existente para un agente, sin usar ADB")
    parser.add_argument("--max-states", type=int)
    parser.add_argument("--max-actions", type=int)
    parser.add_argument("--minutes", type=float)
    parser.add_argument("--depth", type=int, default=10)
    parser.add_argument("--delay", type=float, default=1.2)
    parser.add_argument("--output", type=Path)
    actual_argv = sys.argv[1:] if argv is None else argv
    args = parser.parse_args(actual_argv)
    if not actual_argv:
        gui()
        return
    quick = args.inspect is not None
    if args.json and not (quick or args.bundle):
        parser.error("--json requiere --inspect o --bundle.")
    if args.bundle:
        result = agent_bundle(args.bundle)
        print(json.dumps(result, ensure_ascii=True) if args.json else result["manifest"])
        return result
    saved = load_settings() if quick else DEFAULT_SETTINGS.copy()
    args.max_states = args.max_states if args.max_states is not None else (12 if quick else 80)
    args.max_actions = args.max_actions if args.max_actions is not None else (40 if quick else 180)
    args.minutes = args.minutes if args.minutes is not None else (3 if quick else 15)
    explicit_host = args.ssh is not None
    args.ssh = args.ssh if explicit_host else (saved["host"] if quick else "")
    if quick:
        args.repo = args.repo or saved["repo"]
        if args.inspect:
            if args.inspect.lower().endswith(".apk"):
                args.apk = Path(args.inspect)
            else:
                args.package = args.package or args.inspect
    if args.devices:
        print(Android(host=args.ssh).devices())
        return
    if min(args.max_states, args.max_actions, args.minutes, args.depth, args.delay) <= 0:
        parser.error("Los límites y la espera deben ser positivos.")
    if (args.source_map or args.release) and not args.repo:
        parser.error("Es necesario --repo para consultar GitHub.")
    if args.capture_only and (args.apk or args.release):
        parser.error("--capture-only no admite instalaciones.")
    output = (args.output or session_path()).resolve()
    if output.exists():
        parser.error("La carpeta de salida ya existe; usa --bundle para leerla sin repetir la exploración.")
    github = GitHub(args.repo) if args.source_map or args.release else None
    apk = args.apk
    if args.release:
        tag, assets = github.assets("" if args.release == "latest" else args.release)
        chosen = [a for a in assets if not args.asset or a["name"] == args.asset]
        if len(chosen) != 1:
            parser.error("Elige --asset entre: " + ", ".join(a["name"] for a in assets))
        apk = github.download(chosen[0], output.parent / (output.name + "-downloads"))
    args.package = args.package or (apk_package(apk) if apk else None) or (saved["package"] if quick and not apk else None)
    if not args.package:
        parser.error("No se pudo determinar el package. Indica --package com.empresa.app.")
    if quick and not args.serial:
        host, found, errors = discover_devices(args.ssh, fallback=not explicit_host)
        selected = preferred_device(found, saved["serial"] if host == saved["host"] else "", saved["device_model"])
        if not selected:
            raise RuntimeError("Sin dispositivo autorizado disponible. " + " ".join(errors))
        args.ssh, args.serial = host, selected["serial"]
    device = Android(args.serial or "", args.ssh)
    device.validate()
    explorer = Explorer(device, args.package, output, args.max_states, args.max_actions,
                        args.minutes, args.depth, args.delay,
                        log=lambda message: print(message, file=sys.stderr if args.json else sys.stdout),
                        strategy="breadth" if quick else "depth")
    if apk:
        device.install(apk)
        explorer.report["apk"] = {"name": apk.name,
                                  "sha256": hashlib.sha256(apk.read_bytes()).hexdigest()}
    sources = github.source_map(args.ref) if args.source_map else None
    try:
        result = explorer.run(args.capture_only, sources)
    except KeyboardInterrupt:
        explorer.report["status"] = "interrupted"
        explorer.save()
        result = output / "index.html"
    except Exception:
        if quick and (output / "report.json").exists():
            manifest = agent_bundle(output)
            if args.json:
                print(json.dumps(manifest, ensure_ascii=True))
                raise SystemExit(1)
        raise
    if quick:
        manifest = agent_bundle(output)
        print(json.dumps(manifest, ensure_ascii=True) if args.json else "Manifiesto para agente: " + manifest["manifest"])
    else:
        print("Galería:", result)
    if explorer.report["status"] == "blocked":
        raise SystemExit(2)
    return manifest if quick else None


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as exc:
        if "--json" in sys.argv:
            print(json.dumps({"schema_version": 1, "status": "error", "error": str(exc)}, ensure_ascii=True))
        else:
            print("ERROR:", exc, file=sys.stderr)
        raise SystemExit(1)
