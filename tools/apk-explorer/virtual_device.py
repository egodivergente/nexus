"""Independent virtual display; explicit addressing with no physical-screen fallback."""
import base64
import os
from pathlib import Path
import queue
import re
import subprocess
import tempfile
import threading
import time
import uuid
import xml.etree.ElementTree as ET

from explorer import Android, HOME, command, executable


def display_blocks(raw):
    return {int(m.group(1)): m.group(2) for m in re.finditer(
        r"Display: mDisplayId=(\d+)(.*?)(?=\n\s*Display: mDisplayId=|\Z)", raw, re.S)}


def build_probe(destination):
    sdk = Path(os.environ.get("ANDROID_HOME") or Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk")
    jars = sorted(sdk.glob("platforms/android-*/android.jar"), reverse=True)
    compilers = sorted(sdk.glob("build-tools/*/d8.bat" if os.name == "nt" else "build-tools/*/d8"), reverse=True)
    if not jars or not compilers:
        raise RuntimeError("El modo virtual requiere SDK Android (API 34+) y build-tools para su lector.")
    command([executable("javac"), "-source", "8", "-target", "8", "-cp", jars[0],
             "-d", destination, HOME / "VirtualProbe.java"])
    command([compilers[0], "--lib", jars[0], "--output", destination, destination / "VirtualProbe.class"])
    return destination / "classes.dex"


class VirtualAndroid(Android):
    virtual = True

    def __init__(self, serial="", host="", size="720x1600/320"):
        super().__init__(serial, host)
        if host:
            raise RuntimeError("Modo virtual: usa una conexión ADB desde este PC; SSH aún no está implementado.")
        if not re.fullmatch(r"[1-9]\d{2,3}x[1-9]\d{2,3}/[1-9]\d{1,3}", size):
            raise ValueError("Resolución virtual inválida: ejemplo 720x1600/320")
        self.size = size
        self.display = None
        self.scrcpy = self.probe = self.temp = self.logfile = None
        self.remote = None
        self.package = None
        self.replies = queue.Queue()
        self.evidence = {"mode": "virtual_display", "ime_policy": "local", "force_stop": False}

    def begin(self, package):
        self.package = package
        self.validate()
        if int(super().shell("getprop", "ro.build.version.sdk")) < 34:
            raise RuntimeError("El lector virtual requiere Android 14 o posterior.")
        self.guard_main()
        scrcpy = executable("scrcpy")
        self.temp = tempfile.TemporaryDirectory(prefix="apk-explorer-virtual-")
        folder = Path(self.temp.name)
        dex = build_probe(folder)
        self.remote = "/data/local/tmp/apk-explorer-" + uuid.uuid4().hex + ".dex"
        self.call("push", str(dex), self.remote)
        self.logfile = (folder / "scrcpy.log").open("wb")
        self.scrcpy = subprocess.Popen([scrcpy, "-s", self.serial, "--new-display=" + self.size,
            "--no-window", "--no-audio", "--no-power-on", "--display-ime-policy=local",
            "--no-vd-system-decorations", "--max-fps=2", "--record=" + str(folder / "stream.mkv")],
            stdout=self.logfile, stderr=subprocess.STDOUT,
            creationflags=0x08000000 if os.name == "nt" else 0)
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            raw = (folder / "scrcpy.log").read_text(errors="replace")
            match = re.search(r"New display: .*?\(id=(\d+)\)", raw)
            if match:
                self.display = int(match[1])
                break
            if self.scrcpy.poll() is not None:
                raise RuntimeError("No se pudo crear la pantalla virtual: " + raw[-1800:])
            time.sleep(.2)
        if not self.display:
            raise RuntimeError("scrcpy no confirmó una pantalla virtual distinta de 0.")
        self.evidence["display_id"] = self.display
        self.probe = subprocess.Popen([self.adb, "-s", self.serial, "shell",
            f"CLASSPATH={self.remote} app_process / VirtualProbe {self.display}"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            text=True, encoding="utf-8", creationflags=0x08000000 if os.name == "nt" else 0)
        def read():
            for line in self.probe.stdout:
                self.replies.put(line.strip())
            self.replies.put("ERROR El lector virtual se cerró.")
        threading.Thread(target=read, daemon=True).start()
        if self.reply() != "READY":
            raise RuntimeError("No se pudo iniciar el lector virtual.")

    def reply(self):
        try:
            return self.replies.get(timeout=35)
        except queue.Empty:
            raise RuntimeError("El lector virtual no respondió; no se usará la pantalla principal.")

    def guard_main(self):
        blocks = display_blocks(super().shell("dumpsys", "window", "displays"))
        if 0 not in blocks:
            raise RuntimeError("No se pudo comprobar la pantalla principal; operación cancelada.")
        main = blocks.get(0, "")
        foreground = "\n".join(line.strip() for line in main.splitlines()
                               if "mFocusedApp=" in line or "mCurrentFocus=" in line)
        if self.package and self.package + "/" in foreground:
            raise RuntimeError("La app objetivo está en tu pantalla principal. Se detiene para no compartir su tarea contigo.")
        self.evidence.setdefault("main_before", foreground)
        self.evidence["main_last"] = foreground
        return blocks

    def ensure_display(self):
        if not self.display or not self.scrcpy or self.scrcpy.poll() is not None:
            raise RuntimeError("Pantalla virtual cerrada; operación cancelada.")
        if self.display not in self.guard_main():
            raise RuntimeError("La pantalla virtual desapareció; operación cancelada.")

    def shell(self, *args, **kwargs):
        if args and args[0] == "input":
            self.ensure_display()
            return super().shell("input", "-d", self.display, *args[1:], **kwargs)
        if args[:2] == ("am", "force-stop"):
            raise RuntimeError("force-stop está prohibido en modo virtual.")
        return super().shell(*args, **kwargs)

    def launch(self, package):
        self.ensure_display()
        activity = super().shell("cmd", "package", "resolve-activity", "--brief", "--user", "0", package)
        component = next((line.strip() for line in reversed(activity.splitlines())
            if re.fullmatch(r"[\w.]+/[\w.$]+", line.strip())), None)
        if not component:
            raise RuntimeError("No se encontró la actividad de inicio.")
        result = super().shell("am", "start", "--user", "0", "--display", self.display, "-n", component)
        if "Error" in result or "Exception" in result:
            raise RuntimeError(result)
        self.ensure_display()

    def request(self, operation):
        self.ensure_display()
        self.probe.stdin.write(operation + "\n")
        self.probe.stdin.flush()
        response = self.reply()
        if not response.startswith("OK "):
            raise RuntimeError(response)
        return base64.b64decode(response[3:], validate=True)

    def dump(self):
        raw = self.request("xml").decode("utf-8")
        root = ET.fromstring(raw)
        if root.get("display-id") != str(self.display):
            raise RuntimeError("La jerarquía no pertenece a la pantalla virtual.")
        return raw

    def screenshot(self):
        png = self.request("png")
        if not png.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Captura virtual inválida.")
        return png

    def close(self):
        if self.probe:
            try:
                self.probe.stdin.write("quit\n"); self.probe.stdin.flush()
                self.probe.wait(timeout=5)
            except (OSError, subprocess.TimeoutExpired):
                self.probe.kill(); self.probe.wait(timeout=5)
        if self.scrcpy:
            self.scrcpy.terminate()
            try:
                self.scrcpy.wait(timeout=8)
            except subprocess.TimeoutExpired:
                self.scrcpy.kill(); self.scrcpy.wait(timeout=5)
        if self.logfile:
            self.logfile.close()
        if self.remote:
            super().shell("rm", "-f", self.remote)
        try:
            if self.display:
                for _ in range(10):
                    blocks = display_blocks(super().shell("dumpsys", "window", "displays", timeout=8))
                    if 0 in blocks and self.display not in blocks:
                        self.evidence["closed"] = True
                        self.evidence["main_after"] = "\n".join(line.strip() for line in blocks[0].splitlines()
                            if "mFocusedApp=" in line or "mCurrentFocus=" in line)
                        break
                    time.sleep(.3)
                else:
                    raise RuntimeError("No se pudo confirmar la eliminación de la pantalla virtual.")
        finally:
            if self.temp:
                self.temp.cleanup()
