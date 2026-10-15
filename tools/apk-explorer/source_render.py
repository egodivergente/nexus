"""Host-side Compose rendering in a private, isolated copy of a source project."""
from __future__ import annotations

import hashlib
import html
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tarfile
import uuid

HERE = Path(__file__).resolve().parent
DEFAULT_HOST = "vidal@100.112.57.2"
IGNORED_PARTS = {".git", ".gradle", "build", "node_modules", "__pycache__", ".idea"}
PRIVATE_SUFFIXES = {".jks", ".keystore", ".pem", ".key", ".p12", ".pfx", ".apk", ".aab"}


def local_available_mb():
    if os.name != "nt":
        return None
    import ctypes
    class MemoryStatus(ctypes.Structure):
        _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
            (name, ctypes.c_ulonglong) for name in ("total", "available", "page_total", "page_available", "virtual_total", "virtual_available", "extended")]
    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
        raise RuntimeError("No se pudo consultar la memoria disponible para renderizar.")
    return status.available // (1024 * 1024)


def inside(root, relative):
    relative = Path(relative)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError("El perfil contiene una ruta fuera del proyecto: " + str(relative))
    path = root / relative
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError("La ruta sale del proyecto: " + str(relative))
    return path


def profile_for(project, config=None):
    path = Path(config).resolve() if config else project / "apk-explorer.render.json"
    if not path.exists() and not config:
        agent = project / "app/src/main/java/com/vidadomu/nexus/assistant/AssistantScreen.kt"
        path = HERE / ("render_profiles/nexus-agent.json" if agent.is_file() else "render_profiles/nexus.json")
    profile = json.loads(path.read_text(encoding="utf-8"))
    if profile.get("schema_version") != 1 or profile.get("engine") != "paparazzi":
        raise ValueError("Perfil de renderizado no compatible.")
    for name in ("module", "variant", "plugin_version", "test_class"):
        if not re.fullmatch(r"[A-Za-z0-9_.:-]+", profile[name]):
            raise ValueError("Campo de perfil inválido: " + name)
    for required in profile.get("requires", []):
        if not inside(project, required).is_file():
            raise ValueError("Este perfil no corresponde a las fuentes seleccionadas. Falta: " + required)
    fixture = inside(path.parent, profile["fixture"])
    if not fixture.is_file() or not profile.get("states"):
        raise ValueError("El perfil necesita un fixture Kotlin y estados registrados.")
    return profile, path, fixture


def prepare(project, output, config=None):
    project, output = Path(project).resolve(strict=True), Path(output).resolve()
    profile, profile_path, fixture = profile_for(project, config)
    output.mkdir(parents=True, exist_ok=False)
    (output / "render-profile.json").write_bytes(profile_path.read_bytes())
    work = output / "source"
    work.mkdir()
    files = {}
    for entry in profile["inputs"]:
        if Path(entry) == Path("."):
            raise ValueError("El perfil debe listar entradas de código explícitas, no toda la raíz del proyecto.")
        item = inside(project, entry)
        if not item.exists():
            raise ValueError("No existe la entrada del perfil: " + entry)
        candidates = [item] if item.is_file() else sorted(item.rglob("*"))
        for source in candidates:
            relative = source.relative_to(project)
            if (source.resolve().is_relative_to(output) or source.is_symlink() or not source.is_file() or
                    any(part in IGNORED_PARTS for part in relative.parts) or
                    source.suffix.lower() in PRIVATE_SUFFIXES or source.name.startswith(".env") or
                    source.name.lower() in {"local.properties", "credentials.json", "secrets.json"}):
                continue
            inside(project, relative)
            data = source.read_bytes()
            target = work / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            files[relative.as_posix()] = hashlib.sha256(data).hexdigest()
    module = profile["module"].replace(":", "/").strip("/")
    build = inside(work, module + "/build.gradle.kts")
    text = build.read_text(encoding="utf-8")
    if not re.search(r"plugins\s*\{", text):
        raise ValueError("Se necesita un módulo Android con plugins en build.gradle.kts.")
    if "app.cash.paparazzi" in text:
        raise ValueError("El módulo ya configura Paparazzi; prepara un perfil compatible sin duplicarlo.")
    text = re.sub(r"plugins\s*\{", 'plugins {\n    id("app.cash.paparazzi") version "' +
                  profile["plugin_version"] + '"', text, count=1)
    text += '''
// APK Explorer: added only in this isolated source snapshot.
android.sourceSets.getByName("test").java.setSrcDirs(listOf("src/explorerRender"))
dependencies { testImplementation("org.mockito:mockito-core:5.14.2") }
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "512m"
    maxParallelForks = 1
    jvmArgs("-XX:ActiveProcessorCount=2", "-XX:MaxMetaspaceSize=512m")
}
'''
    build.write_text(text, encoding="utf-8")
    test = work / module / "src/explorerRender" / fixture.name
    test.parent.mkdir(parents=True, exist_ok=True)
    test.write_bytes(fixture.read_bytes())
    wrapper = work / "gradlew"
    if wrapper.exists():
        wrapper.write_bytes(wrapper.read_bytes().replace(b"\r\n", b"\n"))
    (work / "gradle.properties").write_text("android.useAndroidX=true\nandroid.nonTransitiveRClass=true\n"
        "org.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8\n"
        "kotlin.compiler.execution.strategy=in-process\norg.gradle.workers.max=1\n", encoding="utf-8")
    git = shutil.which("git")
    revision = None
    if git:
        result = subprocess.run([git, "-C", str(project), "rev-parse", "HEAD"], capture_output=True)
        if not result.returncode:
            revision = result.stdout.decode().strip()
    provenance = {"project": str(project), "git_head": revision,
                  "source_basis": "Current local files, including uncommitted and untracked source; hashes identify actual inputs.",
                  "files": files, "profile": str(profile_path),
                  "profile_snapshot": str(output / "render-profile.json"),
                  "profile_sha256": hashlib.sha256(profile_path.read_bytes()).hexdigest(),
                  "fixture_sha256": hashlib.sha256(fixture.read_bytes()).hexdigest()}
    (output / "source-provenance.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding="utf-8")
    archive = output / "source.tar.gz"
    with tarfile.open(archive, "w:gz") as stream:
        for path in sorted(work.rglob("*")):
            if path.is_file():
                stream.add(path, arcname=path.relative_to(work).as_posix())
    return profile, work, archive, provenance


def logged_run(argv, path, timeout=1800, cwd=None, env=None):
    with path.open("ab") as log:
        process = subprocess.Popen(argv, stdout=log, stderr=subprocess.STDOUT, cwd=cwd, env=env,
                                   creationflags=0x08004000 if os.name == "nt" else 0)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait(timeout=20)
            raise RuntimeError("El renderizado superó el tiempo límite. Consulta render.log.")
    if code:
        raise RuntimeError(f"Falló el comando de renderizado (código {code}). Consulta {path}")


def collect(output, profile, provenance, host, error=None):
    module = profile["module"].replace(":", "/").strip("/")
    snapshots = output / "rendered"
    images = sorted(snapshots.rglob("*.png")) if snapshots.exists() else []
    states, invalid_images = [], []
    for image in images:
        case = next((s for s in profile["states"] if ("_" + s["test"] + ".png") in image.name), None)
        if not case:
            continue
        data = image.read_bytes()
        if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 45 or b"IEND" not in data[-12:]:
            invalid_images.append(image.name)
            continue
        states.append({"number": len(states) + 1, "title": case["title"], "scope": case["scope"],
                       "data": case["data"], "test": case["test"], "image": str(image.resolve()),
                       "source": case.get("source"), "symbol": case.get("symbol"),
                       "sha256": hashlib.sha256(data).hexdigest()})
    missing = [s["test"] for s in profile["states"] if s["test"] not in {x["test"] for x in states}]
    result = {"schema_version": 1, "mode": "source_render", "engine": "paparazzi/" + profile["plugin_version"],
              "status": "error" if error else ("rendered" if not missing and states else "partial"),
              "project": provenance["project"], "host": host or "local", "output": str(output),
              "gallery": str(output / "index.html"), "manifest": str(output / "render.json"),
              "provenance": str(output / "source-provenance.json"), "log": str(output / "render.log"),
              "discovery": str(output / "discovery.json") if (output / "discovery.json").exists() else None,
              "states": states, "missing_states": missing, "error": error, "invalid_images": invalid_images,
              "coverage": "Only registered source states; no claim of whole-app coverage or installed APK equivalence.",
              "device_used": False, "overviews": [], "render_notes": profile.get("render_notes", [])}
    try:
        from PIL import Image, ImageDraw, ImageFont
        font_path = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts/segoeui.ttf"
        font = ImageFont.truetype(str(font_path), 16) if font_path.exists() else ImageFont.load_default(size=16)
        for start in range(0, len(states), 6):
            batch = states[start:start + 6]
            sheet = Image.new("RGB", (min(3, len(batch)) * 300, ((len(batch) + 2) // 3) * 700), "#11151d")
            draw = ImageDraw.Draw(sheet)
            for index, state in enumerate(batch):
                with Image.open(state["image"]) as original:
                    picture = original.convert("RGB")
                picture.thumbnail((280, 625))
                x, y = index % 3 * 300, index // 3 * 700
                sheet.paste(picture, (x + (300 - picture.width) // 2, y + 45))
                draw.text((x + 10, y + 12), state["title"][:32], fill="white", font=font)
            target = output / f"source-overview-{start // 6 + 1:03}.jpg"
            sheet.save(target, quality=90)
            result["overviews"].append(str(target))
    except (ImportError, OSError, ValueError) as exc:
        result["overview_warning"] = str(exc)
    esc = html.escape
    cards = "".join(f'<article><h2>{esc(s["title"])}</h2><a href="{Path(s["image"]).relative_to(output).as_posix()}">'
        f'<img src="{Path(s["image"]).relative_to(output).as_posix()}"></a><p>{esc(s["scope"])} · {esc(s["data"])}</p></article>' for s in states)
    (output / "index.html").write_text(f'''<!doctype html><html lang="es"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Renderizado desde código</title>
<style>body{{font:15px system-ui;background:#11151d;color:#e5eafa;padding:24px}}.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:20px}}article{{background:#1e2533;padding:15px;border-radius:14px}}img{{width:100%;max-height:650px;object-fit:contain}}a{{color:#a6bfff}}pre{{white-space:pre-wrap}}</style>
<h1>Renderizado desde el código</h1><p>{esc(result['status'])} · {len(states)} estados registrados · sin móvil ni emulador</p>
<p>Funciones de interfaz reales, con datos controlados. No demuestra el estado de la APK instalada.</p>
<p><a href="render.json">Manifiesto</a> · <a href="source-provenance.json">Fuentes y hashes</a> · <a href="render.log">Registro de compilación</a> · <a href="discovery.html">Pantallas detectadas</a></p>
<pre>{esc(error or '')}</pre><div class="grid">{cards}</div><p>Estados pendientes: {esc(', '.join(missing))}</p></html>''', encoding="utf-8")
    (output / "render.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


def select_host(host, log):
    if host != "auto":
        return host
    try:
        result = subprocess.run(["ssh", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
            "-o", "ConnectTimeout=8", DEFAULT_HOST,
            "awk '/^MemAvailable:/ {print $2}' /proc/meminfo; pgrep -fc '[o]rg.gradle.launcher.daemon.bootstrap.GradleDaemon|[o]rg.gradle.wrapper.GradleWrapperMain' || true"], capture_output=True, timeout=12)
        lines = result.stdout.splitlines()
        available = int(lines[0]) // 1024 if result.returncode == 0 and lines else 0
        busy = int(lines[1]) if len(lines) > 1 else 0
        if available >= 2200 and not busy:
            return DEFAULT_HOST
        log(f"ASUS sin margen para el motor ({available} MiB disponibles, {busy} procesos Gradle); usando este PC con prioridad baja y 2 hilos.")
    except (OSError, ValueError, subprocess.SubprocessError):
        log("ASUS no disponible; usando este PC con prioridad baja y 2 hilos.")
    return ""


def gradle_arguments(profile):
    module = profile["module"].strip(":")
    task = f":{module}:recordPaparazzi{profile['variant']}"
    return [task, "--tests", profile["test_class"], "--max-workers=1", "--no-parallel", "--no-daemon", "--console=plain",
            "--no-configuration-cache", "--build-cache", "-Pkotlin.compiler.execution.strategy=in-process",
            "-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=640m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Dfile.encoding=UTF-8"]


def render_source(project, output, config=None, host="auto", log=print):
    output = Path(output).resolve()
    profile, work, archive, provenance = prepare(project, output, config)
    from source_discovery import discover, write_report
    write_report(discover(project, config), output)
    args = gradle_arguments(profile)
    provenance["gradle_arguments"] = args
    build_log = output / "render.log"
    error = None
    try:
        host = select_host(host, log)
        if not host:
            available = local_available_mb()
            if available is not None and available < 2200:
                raise RuntimeError(f"PC sin memoria suficiente: {available} MiB disponibles; se necesitan 2200 MiB libres antes de compilar. No se ha iniciado Gradle.")
        log("Fuentes aisladas; iniciando renderizado en " + (host or "este equipo") + "…")
        if host:
            if not re.fullmatch(r"[\w.-]+@[\w.:-]+", host):
                raise ValueError("Destino SSH inválido.")
            ssh = ["ssh", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes", "-o", "ConnectTimeout=8"]
            remote = "/home/vidal/.cache/apk-explorer-render/" + uuid.uuid4().hex
            provenance["remote_workspace"] = remote
            logged_run(ssh + [host, "mkdir -p " + shlex.quote(remote)], build_log, 30)
            logged_run(["scp", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes", str(archive), host + ":" + remote + "/source.tar.gz"], build_log, 180)
            script = (f"set -e\ncd {shlex.quote(remote)}\ntar -xzf source.tar.gz\nchmod +x gradlew\n"
                "export JAVA_HOME=/home/vidal/aki/toolchain/jdk17\n"
                "export ANDROID_HOME=/home/vidal/aki/toolchain/android-sdk\n"
                "export GRADLE_USER_HOME=/home/vidal/aki/toolchain/gradle-home\n"
                'export PATH="$JAVA_HOME/bin:$PATH"\n'
                "nice -n 10 ./gradlew " + shlex.join(args) + "\n")
            try:
                logged_run(ssh + [host, "bash -c " + shlex.quote(script)], build_log)
            finally:
                # Retrieve even partial images; never copy the server checkout or credentials.
                snapshots = profile["module"].replace(":", "/").strip("/") + "/src/test/snapshots"
                pack = f"cd {shlex.quote(remote)} && if test -d {shlex.quote(snapshots)}; then tar -czf rendered.tar.gz -C {shlex.quote(snapshots)} . && sha256sum rendered.tar.gz > rendered.sha256; fi"
                logged_run(ssh + [host, pack], build_log, 30)
                try:
                    logged_run(["scp", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes", host + ":" + remote + "/rendered.tar.gz", str(output / "rendered.tar.gz")], build_log, 120)
                    logged_run(["scp", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes", host + ":" + remote + "/rendered.sha256", str(output / "rendered.sha256")], build_log, 30)
                except RuntimeError:
                    pass
            provenance["remote_workspace"] = remote
            archive_result = output / "rendered.tar.gz"
            if archive_result.exists():
                expected = (output / "rendered.sha256").read_text().split()[0]
                if hashlib.sha256(archive_result.read_bytes()).hexdigest() != expected:
                    raise RuntimeError("El archivo recibido no coincide con el SHA-256 del ASUS.")
                provenance["remote_artifact_sha256"] = expected
                with tarfile.open(archive_result) as stream:
                    stream.extractall(output / "rendered", filter="data")
        else:
            wrapper = work / ("gradlew.bat" if os.name == "nt" else "gradlew")
            if os.name != "nt":
                wrapper.chmod(0o700)
            sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
            if not sdk and os.name == "nt":
                sdk = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
            if sdk:
                (work / "local.properties").write_text("sdk.dir=" + sdk.replace("\\", "/") + "\n", encoding="utf-8")
            argv = [str(wrapper)] + args
            if os.name == "nt":
                argv = ["cmd", "/d", "/c"] + argv
            logged_run(argv, build_log, cwd=work)
            source = work / profile["module"].replace(":", "/").strip("/") / "src/test/snapshots"
            if source.exists():
                shutil.copytree(source, output / "rendered")
    except (OSError, RuntimeError, subprocess.SubprocessError, tarfile.TarError) as exc:
        error = str(exc)
    (output / "source-provenance.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding="utf-8")
    result = collect(output, profile, provenance, host, error)
    log("Renderizado: " + result["status"] + " · " + str(len(result["states"])) + " imágenes")
    return result
