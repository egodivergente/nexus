"""Run the prebuilt QA APKs on an isolated emulator and preserve raw instrumentation evidence."""
from pathlib import Path
import subprocess
import re
import xml.etree.ElementTree as ET

SUITE = "com.vidadomu.nexus.assistant.AssistantPresentationUiTest"
METHODS = {"settingsPersistImmediatelyAndResetOnlyPresentation", "preferenceObservationStopsWhenUnregistered", "agentImageOpensInForegroundAndReturnsWithoutLosingChat", "completedCardsActuallyResizeWithoutLosingResultsOrLogos", "unknownProgressHasNoPercentageAndStopsAtCompletion", "cardSizesKeepLogosAndActionsAndMeasuredProgress"}
PACKAGE = "com.vidadomu.nexus.qa"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"


def parse_instrumentation(text):
    """Use actual Android status bundles, never infer success from adb's exit code."""
    bundle, results, started = {}, {}, set()
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, sep, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            if sep: bundle[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(":", 1)[1])
            if "test" in bundle:
                name = bundle["test"]
                if bundle.get("class") != SUITE or name not in METHODS:
                    raise ValueError("Unexpected instrumentation test: " + repr(bundle))
                if code == 1:
                    if name in started: raise ValueError("Duplicate test start: " + name)
                    started.add(name)
                else:
                    if name not in started or name in results: raise ValueError("Unpaired or duplicate test result: " + name)
                    results[name] = (code, bundle.get("stack") or bundle.get("stream") or "status=" + str(code))
            bundle = {}
    if set(results) != METHODS: raise ValueError("Missing test results: " + repr(METHODS - set(results)))
    if not re.search(r"^INSTRUMENTATION_CODE: -1\s*$", text, re.M):
        raise ValueError("Instrumentation did not finish normally")
    if "INSTRUMENTATION_FAILED" in text or "INSTRUMENTATION_ABORTED" in text:
        raise ValueError("Instrumentation failed/aborted")
    root = ET.Element("testsuite", name=SUITE, tests=str(len(results)), failures=str(sum(c != 0 for c, _ in results.values())), errors="0", skipped="0")
    for name, (code, message) in sorted(results.items()):
        case = ET.SubElement(root, "testcase", classname=SUITE, name=name)
        if code != 0: ET.SubElement(case, "failure", message="Android instrumentation status " + str(code)).text = message
    ET.SubElement(root, "system-out").text = text
    return root


def main():
    evidence = Path("ui-evidence")
    evidence.mkdir(exist_ok=True)
    def adb(*args, check=True):
        result = subprocess.run(["adb", "-s", "emulator-5554", *args], capture_output=True, text=True, errors="replace")
        print(result.stdout + result.stderr, flush=True)
        if check and result.returncode: raise RuntimeError("adb failed: " + repr(args))
        return result
    apks = list(Path("qa-apks").rglob("*.apk"))
    app = [p for p in apks if p.name == "app-offlineQa.apk"]
    test = [p for p in apks if p.name == "app-offlineQa-androidTest.apk"]
    if len(app) != 1 or len(test) != 1: raise ValueError("Expected exact QA APK pair: " + repr(apks))
    adb("install", "-r", "-t", str(app[0]))
    adb("install", "-r", "-t", str(test[0]))
    # Cold bytecode verification under emulator load can consume Android's startup deadline.
    # Compile the same APKs ahead of launch, without relaxing ANR or test timeouts.
    for package in [PACKAGE, PACKAGE + ".test"]:
        compiled = adb("shell", "cmd", "package", "compile", "-m", "speed", "-f", package)
        if "Success" not in compiled.stdout: raise RuntimeError("Ahead-of-time compilation failed: " + package)
    registered = adb("shell", "pm", "list", "instrumentation").stdout
    if RUNNER not in registered or "target=" + PACKAGE not in registered: raise ValueError("QA runner not registered")
    result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", SUITE, RUNNER, check=False)
    raw = result.stdout + result.stderr
    (evidence / "instrumentation-raw.txt").write_text(raw, encoding="utf-8")
    log = subprocess.run(["adb", "-s", "emulator-5554", "logcat", "-d"], capture_output=True, text=True, errors="replace")
    (evidence / "logcat.txt").write_text(log.stdout + log.stderr, encoding="utf-8")
    pull = adb("pull", "/sdcard/Android/data/" + PACKAGE + "/files/studio-ux", str(evidence), check=False)
    root = parse_instrumentation(raw)
    folder = Path("app/build/outputs/androidTest-results/connected")
    folder.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(folder / ("TEST-" + SUITE + ".xml"), encoding="utf-8", xml_declaration=True)
    if result.returncode or pull.returncode or int(root.get("failures")):
        raise RuntimeError("UI tests or screenshot export failed; inspect raw evidence")
    print("PASS: six presentation tests, all actual Android results received.", flush=True)

if __name__ == "__main__": main()
