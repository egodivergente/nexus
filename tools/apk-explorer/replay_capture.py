"""Replay a native Explorer reference on an explicitly selected Android device.

No installation, force-stop or physical-screen input. Requires ADB and the
Explorer APK built from android/. Captures run on the virtual display.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import tempfile
import uuid
import zipfile


def replay(reference, output, apk, adb, serial):
    if output.exists():
        raise ValueError("Output already exists; choose a new ZIP")
    with zipfile.ZipFile(reference) as z:
        entry = z.getinfo("route.json")
        if entry.file_size > 100000:
            raise ValueError("Route exceeds size limit")
        raw = z.read(entry)
        route = json.loads(raw)
    package = route["package"]
    if not re.fullmatch(r"[A-Za-z]\w*(\.[A-Za-z]\w*)+", package):
        raise ValueError("Invalid package")
    if route.get("schema") != 1 or len(route["steps"]) > 40:
        raise ValueError("Unsupported route")
    prefix = "/data/local/tmp/apk-explorer-" + uuid.uuid4().hex

    def run(*args, timeout=30, check=True):
        return subprocess.run([str(adb), "-s", serial, *args], check=check,
                              capture_output=True, text=True, timeout=timeout)

    model = run("shell", "getprop", "ro.product.model").stdout.strip()
    with tempfile.TemporaryDirectory() as temporary:
        plan = Path(temporary) / "route.json"
        plan.write_bytes(raw)
        try:
            run("push", str(apk), prefix + ".apk", timeout=90)
            run("push", str(plan), prefix + ".json")
            result = run("shell", f"CLASSPATH={prefix}.apk", "app_process", "/",
                         "com.axel.apkexplorer.Engine", package, "20", prefix + ".zip",
                         prefix + ".json", timeout=240, check=False)
            output.parent.mkdir(parents=True, exist_ok=True)
            run("pull", prefix + ".zip", str(output), timeout=60)
            with zipfile.ZipFile(output) as z:
                report = json.loads(z.read("agent.json"))
            return {"zip": str(output.resolve()), "device": model,
                    "status": report["status"], "captures": len(report["states"]),
                    "error": report.get("error"), "process_exit": result.returncode}
        finally:
            run("shell", "rm", "-f", prefix + ".apk", prefix + ".json", prefix + ".zip", check=False)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("reference", type=Path)
    p.add_argument("--out", required=True, type=Path)
    p.add_argument("--serial", required=True)
    p.add_argument("--adb", default="adb", type=Path)
    p.add_argument("--apk", type=Path, default=Path(__file__).parent / "android/app/build/outputs/apk/debug/app-debug.apk")
    args = p.parse_args()
    result = replay(args.reference, args.out, args.apk, args.adb, args.serial)
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["status"] == "replay_complete" else 2


if __name__ == "__main__":
    raise SystemExit(main())
