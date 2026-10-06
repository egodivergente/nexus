"""Read-only, bounded host probe. Standard library only; JSON in/out."""
import ctypes
import json
import os
import platform
import re
import shutil
import subprocess
import time
from pathlib import Path


BUILD_MARKERS = ("GradleDaemon", "GradleWrapperMain", "org.gradle.launcher", "KotlinCompileDaemon",
                 "MSBuild.dll", "MSBuild.exe", "csc.exe", "cmake --build", "ninja", "pyinstaller")


def run(argv, timeout=5):
    try:
        p = subprocess.run(argv, capture_output=True, text=True, timeout=timeout,
                           creationflags=0x08000000 if os.name == "nt" else 0)
        return p.returncode, (p.stdout + p.stderr).strip()
    except (OSError, subprocess.TimeoutExpired) as e:
        return -1, str(e)


def read(path):
    try:
        return Path(path).read_text().strip()
    except OSError:
        return ""


def windows_metrics():
    from ctypes import wintypes
    k = ctypes.WinDLL("kernel32", use_last_error=True)
    class Memory(ctypes.Structure):
        _fields_ = [("length", wintypes.DWORD), ("load", wintypes.DWORD)] + [
            (name, ctypes.c_ulonglong) for name in ("total", "free", "page_total", "page_free", "virtual", "virtual_free", "extended")]
    m = Memory(); m.length = ctypes.sizeof(m)
    if not k.GlobalMemoryStatusEx(ctypes.byref(m)):
        raise OSError("GlobalMemoryStatusEx")
    def cpu():
        values = [ctypes.c_ulonglong() for _ in range(3)]
        if not k.GetSystemTimes(*(ctypes.byref(v) for v in values)):
            raise OSError("GetSystemTimes")
        return [v.value for v in values]
    a = cpu(); time.sleep(0.6); b = cpu()
    total = b[1] + b[2] - a[1] - a[2]
    class Input(ctypes.Structure):
        _fields_ = [("size", wintypes.UINT), ("tick", wintypes.DWORD)]
    inp = Input(); inp.size = ctypes.sizeof(inp)
    ok = ctypes.windll.user32.GetLastInputInfo(ctypes.byref(inp))
    idle = ((k.GetTickCount() - inp.tick) & 0xffffffff) / 1000 if ok else None
    return {"ram_available_mb": m.free // 1048576, "ram_total_mb": m.total // 1048576,
            "cpu_percent": round(100 * (1 - (b[0] - a[0]) / max(1, total)), 1),
            "idle_seconds": idle, "interactive": idle is None or idle < 180,
            "temperature_c": None, "memory_pressure": None, "io_pressure": None}


def linux_metrics():
    mem = dict(re.findall(r"^(\w+):\s+(\d+)", read("/proc/meminfo"), re.M))
    def cpu():
        return list(map(int, read("/proc/stat").splitlines()[0].split()[1:9]))
    a = cpu(); time.sleep(0.6); b = cpu(); delta = [y-x for x,y in zip(a,b)]
    def pressure(kind):
        match = re.search(r"some avg10=([\d.]+)", read("/proc/pressure/" + kind))
        return float(match[1]) if match else None
    temps = []
    for p in Path("/sys/class/thermal").glob("thermal_zone*/temp"):
        try:
            t = float(read(p)) / 1000
            if 0 < t < 150: temps.append(t)
        except ValueError:
            pass
    return {"ram_available_mb": int(mem.get("MemAvailable", mem.get("MemFree", 0))) // 1024,
            "ram_total_mb": int(mem.get("MemTotal", 0)) // 1024,
            "cpu_percent": round(100 * (1 - (delta[3]+delta[4]) / max(1,sum(delta))),1),
            "load_per_cpu": os.getloadavg()[0] / max(1,os.cpu_count() or 1),
            "memory_pressure": pressure("memory"), "io_pressure": pressure("io"),
            "temperature_c": max(temps) if temps else None, "interactive": False}


def probe(config):
    env = config.get("env", {})
    os.environ.update(env)
    if env.get("JAVA_HOME"):
        os.environ["PATH"] = str(Path(env["JAVA_HOME"])/"bin") + os.pathsep + os.environ.get("PATH", "")
    result = {"hostname": platform.node(), "os": "windows" if os.name == "nt" else
              ("android" if "com.termux" in str(Path.home()) else platform.system().lower()),
              "arch": platform.machine().lower(), "cpu_count": os.cpu_count(), "online": True}
    result.update(windows_metrics() if os.name == "nt" else linux_metrics())
    if result["os"]=="android" or config.get("device_kind")=="android":
        def prop(name):
            code,value=run(["/system/bin/getprop",name])
            return value if code==0 else None
        result["model"]=prop("ro.product.model"); result["physical_serial"]=prop("ro.serialno")
        if config.get("identity_file"):
            try:
                identity=json.loads(Path(config["identity_file"]).read_text())
                if not result["physical_serial"] or result["physical_serial"]=="unknown": result["physical_serial"]=identity.get("physical_serial")
                if not result["model"]: result["model"]=identity.get("model")
            except (OSError,ValueError): pass
        result["battery_percent"]=None; result["charging"]=None; result["screen_on"]=None
        code,battery=run(["termux-battery-status"],8)
        if code==0:
            try:
                data=json.loads(battery)
                result["battery_percent"]=data.get("percentage")
                result["charging"]=data.get("plugged") in {"PLUGGED_AC","PLUGGED_USB","PLUGGED_WIRELESS"}
                result["temperature_c"]=data.get("temperature")
            except (ValueError,AttributeError): pass
        if result["battery_percent"] is None:
            battery_root=Path("/sys/class/power_supply/battery")
            try:
                result["battery_percent"]=int(read(battery_root/"capacity"))
                result["temperature_c"]=int(read(battery_root/"temp"))/10
                result["charging"]=read(battery_root/"status") in {"Charging","Full"}
            except ValueError: pass
        code,power=run(["/system/bin/dumpsys","power"])
        if code==0 and "mWakefulness=" in power:
            result["screen_on"]="mWakefulness=Awake" in power
        if config.get("device_metrics_file"):
            try:
                health=json.loads(Path(config["device_metrics_file"]).read_text())
                if 0 <= time.time()-health["measured_at"] < 60 and health["physical_serial"]==config["expected_serial"] and health["model"]==config["expected_model"]:
                    for key in ("battery_percent","charging","temperature_c","screen_on"):
                        result[key]=health[key]
                    result["device_health_source"]="verified ADB sample under 60 seconds"
            except (OSError,ValueError,KeyError): pass
    state = Path(config["state_root"])
    parent = state
    while not parent.exists(): parent = parent.parent
    result["disk_free_mb"] = shutil.disk_usage(parent).free // 1048576
    tools = {t: shutil.which(t) for t in ("python3", "python", "node", "npm", "gcc", "g++", "dotnet", "mcs", "x86_64-w64-mingw32-gcc", "java", "taskset")}
    for name, path in config.get("tools", {}).items():
        tools[name] = path if Path(path).is_file() else None
    result["tools"] = tools
    code, version = run([tools["java"], "-version"]) if tools.get("java") else (-1, "")
    match = re.search(r'version "(\d+)', version)
    result["java_major"] = int(match[1]) if code == 0 and match else None
    sdk = Path(env.get("ANDROID_HOME", "__missing_sdk__"))
    result["android_platforms"] = sorted(p.name for p in (sdk/"platforms").glob("android-*"))
    result["android_build_tools"] = sorted(p.name for p in (sdk/"build-tools").glob("*"))
    result["aapt2_ok"] = False
    aapt = env.get("AAPT2")
    if not aapt and result["android_build_tools"]:
        aapt = str(sdk/"build-tools"/result["android_build_tools"][-1]/("aapt2.exe" if os.name == "nt" else "aapt2"))
    if aapt:
        code, version = run([aapt, "version"])
        result["aapt2_ok"] = code == 0 and "Android Asset Packaging Tool" in version
    # A running daemon is conservatively considered busy; never stop another chat's processes.
    if os.name == "nt":
        code, output = run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(java|javaw|MSBuild|csc|ninja|cmake|dotnet|python).*' } | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"], 8)
        processes = json.loads(output or "[]") if code == 0 else []
        if isinstance(processes, dict): processes = [processes]
        active = [str(p.get("ProcessId")) for p in processes if any(m.lower() in (p.get("CommandLine") or "").lower() for m in BUILD_MARKERS)]
        result["process_probe_ok"] = code == 0
    else:
        code, output = run(["ps", "-eo", "pid,args"])
        active = [line.split()[0] for line in output.splitlines() if any(m in line for m in BUILD_MARKERS)]
        result["process_probe_ok"] = code == 0
    result["active_build_pids"] = active
    result["locked"] = False
    if state.exists():
        try:
            with open(state/"build.lock", "a+b") as f:
                if os.name == "nt":
                    import msvcrt
                    f.seek(0); f.write(b"0"); f.flush(); f.seek(0)
                    msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
                    msvcrt.locking(f.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            result["locked"] = True
    return result


if __name__ == "__main__":
    import base64, sys
    config = json.loads(base64.b64decode(sys.argv[1]))
    try:
        print(json.dumps(probe(config)))
    except Exception as e:
        print(json.dumps({"online": False, "error": str(e)})); sys.exit(1)
