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
import threading
import time
import unicodedata
import uuid
import webbrowser
import xml.etree.ElementTree as ET

HOME = Path(__file__).resolve().parent


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
                 minutes=15, depth=10, delay=1.2, stop=None, log=print):
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
        self.report = {"package": package, "created": datetime.now().isoformat(),
                       "status": "running", "coverage": "Parcial; no garantiza todas las interfaces.",
                       "states": [], "edges": self.edges, "notes": self.notes}

    def available(self):
        return (not self.stop.is_set() and time.monotonic() < self.deadline and
                len(self.states) < self.max_states and self.count < self.max_actions)

    def observe(self, route):
        raw = self.device.dump()
        key, title, actions, skipped, owned = inspect(raw, self.package)
        if key not in self.states:
            number = len(self.states) + 1
            stem = f"screen-{number:04}"
            png = self.device.screenshot()
            (self.output / (stem + ".png")).write_bytes(png)
            (self.output / (stem + ".xml")).write_text(raw, encoding="utf-8")
            state = {"id": key, "number": number, "title": title, "image": stem + ".png",
                     "xml": stem + ".xml", "route": route, "skipped": skipped,
                     "owned": owned, "sha256": hashlib.sha256(png).hexdigest()}
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
        if inspect(self.device.dump(), self.package)[0] != root_key:
            self.notes.append("La pantalla inicial cambió; una ruta no pudo reproducirse.")
            return False
        for expected, action, target in path:
            if inspect(self.device.dump(), self.package)[0] != expected or not self.perform(action):
                return False
            if inspect(self.device.dump(), self.package)[0] != target:
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
            raw = self.device.dump()
            target = inspect(raw, self.package)[0]
            known = target in self.states
            target, next_actions = self.observe(next_route)
            self.edges.append({"from": key, "to": target, "action": action.label,
                               "kind": action.kind, "bounds": action.bounds})
            self.save()
            if not known:
                self.crawl(root_key, target, next_actions, path + [(key, action, target)], next_route)

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
                self.device.launch(self.package)
                self.stop.wait(self.delay)
            key, actions = self.observe([])
            if not self.states[key]["owned"]:
                self.notes.append("La app objetivo no está visible o hay un diálogo/superficie externa.")
                self.report["status"] = "blocked"
            elif capture_only:
                self.report["status"] = "capture_only"
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
    fields = {}
    for label, key, default in [("SSH al equipo con ADB (vacío = este PC)", "host", ""),
                                ("Serial ADB del dispositivo", "serial", ""),
                                ("Package de la app (com.empresa.app)", "package", ""),
                                ("APK local (opcional; se instala conservando datos)", "apk", ""),
                                ("Repositorio GitHub privado: propietario/repo (opcional)", "repo", ""),
                                ("Tag de release (vacío = última publicada)", "tag", ""),
                                ("Referencia del código (vacío = rama predeterminada)", "ref", "")]:
        ttk.Label(frame, text=label).pack(anchor="w", pady=(8, 0))
        fields[key] = tk.StringVar(value=default)
        ttk.Entry(frame, textvariable=fields[key]).pack(fill="x")
    messages = queue.Queue()
    stop = threading.Event()
    busy = False
    assets = []
    last = [None]
    options = ttk.Frame(frame)
    options.pack(fill="x", pady=10)
    ttk.Label(options, text="Máx. pantallas").pack(side="left")
    limit = tk.IntVar(value=60)
    ttk.Spinbox(options, from_=1, to=500, width=6, textvariable=limit).pack(side="left", padx=8)
    ttk.Label(options, text="Minutos").pack(side="left")
    minutes = tk.IntVar(value=15)
    ttk.Spinbox(options, from_=1, to=120, width=6, textvariable=minutes).pack(side="left", padx=8)
    source = tk.BooleanVar(value=False)
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

    def devices():
        v = values()
        background(lambda: messages.put(("log", Android(host=v["host"]).devices())))

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
    ttk.Button(buttons, text="Elegir APK", command=pick_apk).pack(side="left", padx=3)
    ttk.Button(buttons, text="APK de GitHub", command=releases).pack(side="left", padx=3)
    ttk.Button(buttons, text="Explorar", command=start).pack(side="left", padx=3)
    ttk.Button(buttons, text="Detener", command=stop.set).pack(side="left", padx=3)
    ttk.Button(buttons, text="Galería", command=lambda: webbrowser.open(last[0].as_uri()) if last[0] else None).pack(side="left", padx=3)

    def poll():
        nonlocal busy, assets
        while not messages.empty():
            kind, value = messages.get_nowait()
            if kind == "done":
                busy = False
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
        if busy:
            stop.set()
            log.insert("end", "Deteniendo… espera a que se guarde el informe antes de cerrar.\n")
        else:
            root.destroy()
    root.protocol("WM_DELETE_WINDOW", close)
    poll()
    root.mainloop()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package")
    parser.add_argument("--serial", default="")
    parser.add_argument("--ssh", default="")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--repo", help="Repositorio GitHub privado: propietario/nombre")
    parser.add_argument("--ref", default="")
    parser.add_argument("--source-map", action="store_true")
    parser.add_argument("--release", help="Tag o 'latest': descargar APK de una release")
    parser.add_argument("--asset", help="Nombre exacto del APK si la release contiene varios")
    parser.add_argument("--devices", action="store_true")
    parser.add_argument("--capture-only", action="store_true", help="Captura la pantalla actual sin navegar")
    parser.add_argument("--max-states", type=int, default=80)
    parser.add_argument("--max-actions", type=int, default=180)
    parser.add_argument("--minutes", type=float, default=15)
    parser.add_argument("--depth", type=int, default=10)
    parser.add_argument("--delay", type=float, default=1.2)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if len(__import__("sys").argv) == 1:
        gui()
        return
    if args.devices:
        print(Android(host=args.ssh).devices())
        return
    if min(args.max_states, args.max_actions, args.minutes, args.depth, args.delay) <= 0:
        parser.error("Los límites y la espera deben ser positivos.")
    if not args.package:
        parser.error("Es necesario --package.")
    if (args.source_map or args.release) and not args.repo:
        parser.error("Es necesario --repo para consultar GitHub.")
    if args.capture_only and (args.apk or args.release):
        parser.error("--capture-only no admite instalaciones.")
    device = Android(args.serial, args.ssh)
    device.validate()
    output = (args.output or session_path()).resolve()
    explorer = Explorer(device, args.package, output, args.max_states, args.max_actions,
                        args.minutes, args.depth, args.delay)
    github = GitHub(args.repo) if args.repo else None
    apk = args.apk
    if args.release:
        tag, assets = github.assets("" if args.release == "latest" else args.release)
        chosen = [a for a in assets if not args.asset or a["name"] == args.asset]
        if len(chosen) != 1:
            parser.error("Elige --asset entre: " + ", ".join(a["name"] for a in assets))
        apk = github.download(chosen[0], output.parent / (output.name + "-downloads"))
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
        return
    print("Galería:", result)
    if explorer.report["status"] == "blocked":
        raise SystemExit(2)


if __name__ == "__main__":
    main()
