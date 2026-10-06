"""One host, one build; isolated snapshot, resource limits, recoverable results."""
import ctypes
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import time
import tempfile
from pathlib import Path
from probe import probe
from guard import OwnedBuild, watch
from history import build_statistics


def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1048576), b""): h.update(block)
    return h.hexdigest()


def save(path, data):
    # Status readers/relay may write concurrently; each writer owns its temp file.
    with tempfile.NamedTemporaryFile(mode="w",encoding="utf-8",dir=path.parent,prefix=path.name+".",suffix=".tmp",delete=False) as stream:
        json.dump(data,stream,indent=2); tmp=Path(stream.name)
    try: os.replace(tmp,path)
    finally: tmp.unlink(missing_ok=True)


def compatible(node, metrics, recipe):
    why = []
    if not metrics.get("online"): return [metrics.get("error", "sin acceso")]
    if node.get("expected_hostname") and metrics.get("hostname", "").lower() != node["expected_hostname"].lower():
        why.append("identidad de host incorrecta")
    allowed = recipe.get("host_os", [])
    if allowed and metrics.get("os") not in allowed: why.append("sistema operativo incompatible")
    arches = recipe.get("host_arch", [])
    if arches and metrics.get("arch") not in arches: why.append("arquitectura de host incompatible")
    if metrics.get("locked") or metrics.get("active_build_pids"): why.append("otra compilación activa")
    if not metrics.get("process_probe_ok"): why.append("no se pudieron comprobar los procesos activos")
    if metrics.get("ram_available_mb") is None:
        why.append("RAM disponible sin comprobar")
    elif metrics["ram_available_mb"] < recipe.get("min_ram_mb", 1024) + node.get("reserve_ram_mb", 512):
        why.append("RAM disponible insuficiente con reserva")
    if metrics.get("disk_free_mb") is None: why.append("espacio libre sin comprobar")
    elif metrics["disk_free_mb"] < recipe.get("min_disk_mb", 2048): why.append("espacio libre insuficiente")
    if metrics.get("cpu_percent") is None: why.append("carga CPU sin comprobar")
    elif metrics["cpu_percent"] > recipe.get("max_cpu_percent", 75): why.append("CPU ocupada")
    if metrics.get("memory_pressure") is not None and metrics["memory_pressure"] > 10: why.append("presión de memoria")
    if metrics.get("io_pressure") is not None and metrics["io_pressure"] > 35: why.append("disco saturado")
    if metrics.get("temperature_c") is not None and metrics["temperature_c"] > node.get("max_temperature_c", 85): why.append("temperatura alta")
    if metrics.get("os") == "android" or node.get("device_kind")=="android":
        physical_ram=metrics.get("android_ram_available_mb")
        if physical_ram is not None and physical_ram < recipe.get("min_ram_mb",1024)+node.get("android_reserve_ram_mb",node.get("reserve_ram_mb",512)):
            why.append("RAM física Android insuficiente con reserva")
        if metrics.get("model")!=node.get("expected_model") or metrics.get("physical_serial")!=node.get("expected_serial"):
            why.append("identidad física Android sin comprobar")
        if metrics.get("battery_percent") is None or metrics.get("temperature_c") is None:
            why.append("batería/temperatura sin comprobar")
        elif metrics["battery_percent"] < node.get("min_battery_percent",20):
            why.append("batería por debajo del mínimo incluso conectado")
        elif not metrics.get("charging") and metrics["battery_percent"] < 50:
            why.append("batería insuficiente sin alimentación")
        if node.get("require_screen_off") and metrics.get("screen_on") is not False:
            why.append("móvil principal en uso o estado desconocido")
        if not node.get("dedicated") and (not metrics.get("tools",{}).get("taskset") or not node.get("cpu_affinity")):
            why.append("límite de CPU del móvil principal sin configurar")
    tools = metrics.get("tools", {})
    for t in recipe.get("requires_tools", []):
        if not tools.get(t): why.append("falta herramienta: " + t)
    if recipe["kind"] == "android-gradle":
        if metrics.get("java_major") != recipe.get("java_major", 17): why.append("JDK requerido no comprobado")
        if "android-"+str(recipe["compile_sdk"]) not in metrics.get("android_platforms", []): why.append("falta plataforma Android")
        if not metrics.get("aapt2_ok"): why.append("AAPT2 incompatible o ausente")
        override=node.get("env",{}).get("AAPT2")
        if override and override.replace("\\","/").rsplit("/",1)[-1] not in {"aapt2","aapt2.exe"}:
            why.append("el override de Gradle requiere el nombre de ejecutable aapt2")
    if recipe.get("kind") not in ("android-gradle", "command"): why.append("tipo de receta desconocido")
    if metrics.get("os") == "windows" and not recipe.get("low_impact", False):
        why.append("receta Windows sin límites de memoria/paralelismo declarados")
    return why


def windows_job(cap, memory_mb):
    """Limit the entire process tree before the build starts. No kill-on-close."""
    from ctypes import wintypes
    k = ctypes.WinDLL("kernel32", use_last_error=True)
    k.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
    k.CreateJobObjectW.restype = wintypes.HANDLE
    k.GetCurrentProcess.restype = wintypes.HANDLE
    k.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
    k.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
    class CpuRate(ctypes.Structure):
        _fields_ = [("flags", wintypes.DWORD), ("rate", wintypes.DWORD)]
    class BasicLimits(ctypes.Structure):
        _fields_ = [("process_time",ctypes.c_int64),("job_time",ctypes.c_int64),("flags",wintypes.DWORD),
                    ("min_ws",ctypes.c_size_t),("max_ws",ctypes.c_size_t),("active",wintypes.DWORD),
                    ("affinity",ctypes.c_size_t),("priority",wintypes.DWORD),("scheduling",wintypes.DWORD)]
    class IoCounters(ctypes.Structure):
        _fields_ = [("v"+str(i),ctypes.c_ulonglong) for i in range(6)]
    class ExtendedLimits(ctypes.Structure):
        _fields_ = [("basic",BasicLimits),("io",IoCounters),("process_memory",ctypes.c_size_t),
                    ("job_memory",ctypes.c_size_t),("peak_process",ctypes.c_size_t),("peak_job",ctypes.c_size_t)]
    handle = k.CreateJobObjectW(None, None)
    if not handle: raise ctypes.WinError(ctypes.get_last_error())
    rate = CpuRate(1 | 4, int(cap)*100)
    if not k.SetInformationJobObject(handle, 15, ctypes.byref(rate), ctypes.sizeof(rate)):
        raise ctypes.WinError(ctypes.get_last_error())
    limits=ExtendedLimits(); limits.basic.flags=0x200; limits.job_memory=int(memory_mb)*1048576
    if not k.SetInformationJobObject(handle,9,ctypes.byref(limits),ctypes.sizeof(limits)):
        raise ctypes.WinError(ctypes.get_last_error())
    if not k.AssignProcessToJobObject(handle, k.GetCurrentProcess()):
        raise ctypes.WinError(ctypes.get_last_error())
    k.SetPriorityClass.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    if not k.SetPriorityClass(k.GetCurrentProcess(), 0x4000):
        raise ctypes.WinError(ctypes.get_last_error())
    verified_rate=CpuRate(); verified_limits=ExtendedLimits()
    k.QueryInformationJobObject.argtypes=[wintypes.HANDLE,ctypes.c_int,ctypes.c_void_p,wintypes.DWORD,ctypes.c_void_p]
    if not k.QueryInformationJobObject(handle,15,ctypes.byref(verified_rate),ctypes.sizeof(verified_rate),None):
        raise ctypes.WinError(ctypes.get_last_error())
    if not k.QueryInformationJobObject(handle,9,ctypes.byref(verified_limits),ctypes.sizeof(verified_limits),None):
        raise ctypes.WinError(ctypes.get_last_error())
    if verified_rate.rate!=int(cap)*100 or not verified_rate.flags & 4 or verified_limits.job_memory!=int(memory_mb)*1048576:
        raise OSError("los límites Windows no coinciden")
    return handle


def prepare(job, request):
    source = job/"source"; source.mkdir()
    manifest = json.loads((job/"source-manifest.json").read_text())
    if digest(job/"source.tar") != request["source_archive_sha256"]: raise ValueError("source.tar SHA-256 incorrecto")
    with tarfile.open(job/"source.tar") as archive:
        for member in archive:
            name = Path(member.name)
            if not member.isfile() or name.is_absolute() or ".." in name.parts or member.name not in manifest:
                raise ValueError("Entrada de fuente inválida: " + member.name)
            target = source/name
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as inp, open(target, "xb") as out: shutil.copyfileobj(inp,out)
            target.chmod(0o700 if member.mode & 0o111 else 0o600)
    if any(not (source/p).is_file() or digest(source/p) != h for p,h in manifest.items()):
        raise ValueError("Manifiesto de código no coincide")
    return source


def build_command(source, node, metrics, recipe):
    env = os.environ.copy(); env.update(node.get("env", {}))
    if env.get("JAVA_HOME"): env["PATH"] = str(Path(env["JAVA_HOME"])/"bin") + os.pathsep + env.get("PATH", "")
    # Also applies to npm/native build recipes; override explicitly in the recipe only with bounded values.
    env.update({"CMAKE_BUILD_PARALLEL_LEVEL": "1", "MAX_JOBS": "1", "MAKEFLAGS": "-j1"})
    env.update(recipe.get("env", {}))
    if recipe["kind"] == "android-gradle":
        (source/"local.properties").write_text("sdk.dir=" + env["ANDROID_HOME"].replace("\\", "/") + "\n")
        wrapper = source/("gradlew.bat" if os.name == "nt" else "gradlew")
        if not wrapper.exists(): raise ValueError("falta Gradle Wrapper del código actual")
        if os.name != "nt": wrapper.write_bytes(wrapper.read_bytes().replace(b"\r\n", b"\n"))
        heap = min(recipe.get("max_heap_mb", 1400), max(512, metrics["ram_available_mb"] - node.get("reserve_ram_mb", 512) - 256))
        cmd = (["cmd.exe", "/d", "/c", str(wrapper)] if os.name == "nt" else ["bash", str(wrapper)])
        cmd += ["--no-daemon", "--no-parallel", "--max-workers=1", "--build-cache", "--console=plain",
                "-Dorg.gradle.jvmargs=-Xmx%sm -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8" % heap,
                "-Dorg.gradle.vfs.watch=false", "-Pkotlin.compiler.execution.strategy=in-process"]
        if metrics["os"] == "android":
            cmd += ["-Dorg.gradle.native=false"]
        if env.get("AAPT2"):
            cmd += ["-Pandroid.aapt2FromMavenOverride=" + env["AAPT2"]]
        cmd += recipe["tasks"]
    else:
        cmd = recipe["command"][:]
        if not cmd or not all(isinstance(v,str) for v in cmd): raise ValueError("command debe ser argv")
        cmd[0] = metrics.get("tools", {}).get(cmd[0]) or cmd[0]
    if os.name != "nt":
        cmd = ["nice", "-n", "15"] + cmd
        if metrics["os"]!="android" and shutil.which("ionice"): cmd = ["ionice", "-c", "3"] + cmd
        if node.get("cpu_affinity"):
            cmd=["taskset","-c",",".join(map(str,node["cpu_affinity"]))]+cmd
    return cmd, env


def execute(job):
    request = json.loads((job/"request.json").read_text())
    node = request["node"]; recipe = request["recipe"]
    state = Path(node["state_root"]); state.mkdir(parents=True,exist_ok=True)
    result = {"job_id": job.name, "node": node["id"], "state": "starting", "worker_pid": os.getpid(),
              "started_at": time.time(), "source_archive_sha256": request["source_archive_sha256"]}
    save(job/"status.json", result)
    with open(state/"build.lock", "a+b") as lock:
        try:
            if os.name == "nt":
                import msvcrt
                lock.seek(0); lock.write(b"0"); lock.flush(); lock.seek(0)
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            result.update(state="deferred", reason="otro trabajo posee el bloqueo", finished_at=time.time())
            save(job/"status.json",result); return
        try:
            metrics = probe(node)
            # Our own exclusive lock is expected. External build processes still block.
            metrics["locked"] = False
            why = compatible(node,metrics,recipe)
            if why:
                result.update(state="deferred", reason="; ".join(why), finished_at=time.time())
                save(job/"status.json",result); return
            source = prepare(job, request)
            cmd, env = build_command(source,node,metrics,recipe)
            job_handle = windows_job(node.get("cpu_cap_percent",15),recipe.get("max_memory_mb",1024)) if os.name == "nt" else None
            result.update(state="running", metrics=metrics, cpu_cap_percent=node.get("cpu_cap_percent"),
                          memory_cap_mb=recipe.get("max_memory_mb",1024) if os.name=="nt" else None,
                          limits_verified=os.name=="nt",
                          verified_source_files=len(json.loads((job/"source-manifest.json").read_text())))
            save(job/"status.json",result)
            with open(job/"build.log","wb") as log:
                build=OwnedBuild(cmd,source,env,log,job)
                result.update(build_pid=build.process.pid,build_started_at=time.time(),runtime_guard=True)
                save(job/"status.json",result)
                code = watch(build,node,job,result,save,job_handle)
                result["build_seconds"]=round(time.time()-result["build_started_at"],3)
            result["build_statistics"]=build_statistics(job/"build.log",recipe["kind"])
            if result.get("protection_abort"): code=125
            artifacts = {}
            if code == 0:
                for pattern in recipe.get("artifacts", []):
                    matches = [p for p in source.glob(pattern) if p.is_file() and not p.is_symlink()]
                    if not matches: raise ValueError("artefacto esperado ausente: " + pattern)
                    for p in matches: artifacts[p.relative_to(source).as_posix()] = digest(p)
            # Recover test reports on failures as well.
            for pattern in recipe.get("reports", []):
                for p in source.glob(pattern):
                    if p.is_file() and not p.is_symlink(): artifacts[p.relative_to(source).as_posix()] = digest(p)
            with tarfile.open(job/"artifacts.tar", "w") as archive:
                for name in artifacts: archive.add(source/name,arcname=name,recursive=False)
            save(job/"artifact-manifest.json",artifacts)
            result.update(state="succeeded" if code == 0 else "failed", exit_code=code,
                          artifacts=artifacts, artifact_archive_sha256=digest(job/"artifacts.tar"), finished_at=time.time())
        except Exception as e:
            result.update(state="failed", reason=str(e), finished_at=time.time())
        save(job/"status.json",result)


if __name__ == "__main__":
    execute(Path(sys.argv[1]).resolve())
