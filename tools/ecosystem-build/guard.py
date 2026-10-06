"""A per-build resource guard. Signals target only the owned session/job."""
import ctypes
import json
import os
import signal
import subprocess
import sys
import time
from pathlib import Path
from probe import linux_metrics, windows_metrics, cap_android_ram


def runtime_metrics(node, job_id):
    data = windows_metrics() if os.name == "nt" else linux_metrics()
    if node.get("device_kind") == "android":
        try:
            health = json.loads(Path(node["device_metrics_file"]).read_text())
            valid = (health.get("job_id") == job_id and health.get("physical_serial") == node["expected_serial"]
                and health.get("model") == node["expected_model"] and 0 <= time.time() - health["measured_at"] < 60)
            if not valid:
                raise ValueError("muestra móvil caducada o identidad incorrecta")
            data["os"] = "android" if "com.termux" in str(Path.home()) else "linux"
            cap_android_ram(data, health)
            for key in ("battery_percent", "charging", "temperature_c", "screen_on"):
                data[key] = health.get(key)
            data["mobile_health_valid"] = True
        except (OSError, KeyError, ValueError, TypeError):
            data["mobile_health_valid"] = False
    return data


def policy(node, metrics, paused=False):
    reserve = node.get("reserve_ram_mb", 512)
    free = metrics.get("ram_available_mb")
    physical = metrics.get("android_ram_available_mb")
    if free is not None and free < reserve:
        return "abort", "RAM por debajo de la reserva"
    if physical is not None and physical < node.get("android_reserve_ram_mb", reserve):
        return "abort", "RAM física Android por debajo de la reserva"
    if (metrics.get("memory_pressure") or 0) > 10:
        return "abort", "presión de memoria sostenida"
    if free is None:
        return "throttle" if os.name == "nt" else "pause", "RAM sin comprobar"
    mobile = node.get("device_kind") == "android"
    if mobile:
        if not metrics.get("mobile_health_valid"):
            return "pause", "sin muestra móvil reciente y verificada"
        if metrics.get("screen_on") is not False:
            return "pause", "móvil en uso o pantalla sin comprobar"
        battery = metrics.get("battery_percent")
        if battery is None or battery < node.get("min_battery_percent", 20):
            return "pause", "batería por debajo del mínimo o sin comprobar"
        if not metrics.get("charging") and battery < 50:
            return "pause", "alimentación insuficiente"
    temp = metrics.get("temperature_c")
    maximum = node.get("max_temperature_c", 40 if mobile else 85)
    if (mobile and temp is None) or (temp is not None and temp > maximum - (2 if paused else 0)):
        return "pause" if os.name != "nt" else "throttle", "temperatura alta o sin comprobar"
    if (metrics.get("io_pressure") or 0) > (20 if paused else 35):
        return "pause" if os.name != "nt" else "throttle", "disco saturado"
    if os.name == "nt" and (metrics.get("interactive", True) or (metrics.get("cpu_percent") or 0) > 85):
        return "throttle", "uso interactivo o CPU ocupada"
    return "run", "recursos disponibles"


def set_windows_cpu(handle, cap):
    from ctypes import wintypes
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    class Rate(ctypes.Structure):
        _fields_ = [("flags", wintypes.DWORD), ("rate", wintypes.DWORD)]
    kernel.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
    kernel.QueryInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD, ctypes.c_void_p]
    rate = Rate(5, int(cap) * 100)
    if not kernel.SetInformationJobObject(handle, 15, ctypes.byref(rate), ctypes.sizeof(rate)):
        raise ctypes.WinError(ctypes.get_last_error())
    actual = Rate()
    if not kernel.QueryInformationJobObject(handle, 15, ctypes.byref(actual), ctypes.sizeof(actual), None):
        raise ctypes.WinError(ctypes.get_last_error())
    if actual.rate != rate.rate or actual.flags != rate.flags:
        raise OSError("límite CPU dinámico no confirmado")


class OwnedBuild:
    def __init__(self, command, source, env, log, job):
        self.handle = None
        self.paused = False
        if os.name == "nt":
            # Gate waits before spawning anything; attach it to its own nested job first.
            from ctypes import wintypes
            kernel = ctypes.WinDLL("kernel32", use_last_error=True)
            kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
            kernel.CreateJobObjectW.restype = wintypes.HANDLE
            kernel.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
            self.handle = kernel.CreateJobObjectW(None, None)
            if not self.handle:
                raise ctypes.WinError(ctypes.get_last_error())
            class Basic(ctypes.Structure):
                _fields_=[("process_time",ctypes.c_int64),("job_time",ctypes.c_int64),("flags",wintypes.DWORD),
                    ("min_ws",ctypes.c_size_t),("max_ws",ctypes.c_size_t),("active",wintypes.DWORD),
                    ("affinity",ctypes.c_size_t),("priority",wintypes.DWORD),("scheduling",wintypes.DWORD)]
            class IO(ctypes.Structure):
                _fields_=[("v"+str(i),ctypes.c_ulonglong) for i in range(6)]
            class Extended(ctypes.Structure):
                _fields_=[("basic",Basic),("io",IO),("process_memory",ctypes.c_size_t),("job_memory",ctypes.c_size_t),
                    ("peak_process",ctypes.c_size_t),("peak_job",ctypes.c_size_t)]
            limits=Extended(); limits.basic.flags=0x2000  # KILL_ON_JOB_CLOSE: own descendants only.
            kernel.SetInformationJobObject.argtypes=[wintypes.HANDLE,ctypes.c_int,ctypes.c_void_p,wintypes.DWORD]
            if not kernel.SetInformationJobObject(self.handle,9,ctypes.byref(limits),ctypes.sizeof(limits)):
                error=ctypes.get_last_error(); self.close(); raise ctypes.WinError(error)
            config = job / "build-gate.json"
            config.write_text(json.dumps({"command": command, "release": str(job / "build-release")}), encoding="utf-8")
            self.process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "gate", str(config)],
                cwd=source, env=env, stdout=log, stderr=subprocess.STDOUT, creationflags=0x08000000)
            if not kernel.AssignProcessToJobObject(self.handle, int(self.process._handle)):
                error=ctypes.get_last_error()
                self.process.kill(); self.process.wait(); self.close()
                raise ctypes.WinError(error)
            (job / "build-release").touch()
        else:
            self.process = subprocess.Popen(command, cwd=source, env=env, stdout=log,
                stderr=subprocess.STDOUT, start_new_session=True)

    def pause(self, value):
        if self.process.poll() is None and value != self.paused:
            os.killpg(self.process.pid, signal.SIGSTOP if value else signal.SIGCONT)
            self.paused = value

    def abort(self):
        if self.handle:
            from ctypes import wintypes
            kernel = ctypes.WinDLL("kernel32", use_last_error=True)
            kernel.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
            if not kernel.TerminateJobObject(self.handle, 125):
                raise ctypes.WinError(ctypes.get_last_error())
        else:
            # Own session includes descendants; never search for Gradle by name.
            try:
                os.killpg(self.process.pid, signal.SIGCONT)
                os.killpg(self.process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                pass
            try:
                os.killpg(self.process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        self.process.wait(timeout=10)

    def close(self):
        if self.handle:
            from ctypes import wintypes
            kernel = ctypes.WinDLL("kernel32", use_last_error=True)
            kernel.CloseHandle.argtypes = [wintypes.HANDLE]
            kernel.CloseHandle(self.handle)
            self.handle = None


def watch(build, node, job, result, save, job_handle=None):
    interval = 10
    paused_since = None
    low_memory_count = 0
    current = None
    active_cap = node.get("cpu_cap_percent", 15)
    result["protection_events"] = []
    try:
        while build.process.poll() is None:
            metrics = runtime_metrics(node, job.name)
            if build.process.poll() is not None:
                break
            action, reason = policy(node, metrics, build.paused)
            low_memory_count = low_memory_count + 1 if action == "abort" else 0
            if action == "abort" and low_memory_count < 2:
                action = "throttle" if os.name == "nt" else "pause"
            if os.name == "nt":
                cap = min(5, node.get("cpu_cap_percent", 15)) if action != "run" else node.get("cpu_cap_percent", 15)
                if cap != active_cap:
                    set_windows_cpu(job_handle, cap)
                    active_cap = cap
                result["active_cpu_cap_percent"] = active_cap
            if action == "pause" and os.name != "nt":
                build.pause(True)
                paused_since = paused_since or time.monotonic()
                if time.monotonic() - paused_since >= 300:
                    action, reason = "abort", "pausa de protección supera 5 minutos: " + reason
            elif action == "run":
                if os.name != "nt":
                    build.pause(False)
                paused_since = None
            result["protection"] = {"action": action, "reason": reason, "measured_at": time.time(), "metrics": metrics}
            if current != (action, reason):
                result["protection_events"].append({"action": action, "reason": reason, "at": time.time()})
                current = action, reason
            save(job / "status.json", result)
            if action == "abort":
                result["protection_abort"] = reason
                build.abort()
                break
            try:
                build.process.wait(timeout=interval)
            except subprocess.TimeoutExpired:
                pass
        return build.process.wait()
    except BaseException:
        build.abort()
        raise
    finally:
        build.close()


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "gate":
        data = json.loads(Path(sys.argv[2]).read_text())
        deadline = time.monotonic() + 20
        while not Path(data["release"]).exists():
            if time.monotonic() > deadline:
                sys.exit(125)
            time.sleep(.05)
        sys.exit(subprocess.call(data["command"],creationflags=0x08000000))
