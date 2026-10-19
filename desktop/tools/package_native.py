"""Package the unsigned native WebView2 build without private data."""
from pathlib import Path
import hashlib
import json
import zipfile


root = Path(__file__).resolve().parents[1]
workspace = root.parent
dist = root / "dist"
output = workspace / "entregas" / "Nexus-Desktop-Windows-x64-20261002-v2-UNSIGNED.zip"
archive_root = Path("Nexus-Desktop")

CORE_DIST_FILES = (
    "Nexus.exe",
    "Microsoft.Web.WebView2.Core.dll",
    "Microsoft.Web.WebView2.WinForms.dll",
    "WebView2Loader.dll",
)
RUNTIME_DIST_FILES = (
    "local_host.py",
    "agent_host.py",
    "flow_agent.py",
    "flow_automation.py",
    "flow_batch.py",
    "flow_connection.py",
    "flow_contract.py",
    "flow_provider.py",
    "nexus.ico",
)
DOCUMENTATION_FILES = (root / "README.md", root / "SIGNING.md")


def require_file(path: Path) -> Path:
    if not path.is_file():
        raise FileNotFoundError(f"Falta el archivo requerido para el paquete: {path}")
    return path


def require_tree(path: Path, label: str) -> list[Path]:
    if not path.is_dir():
        raise FileNotFoundError(f"Falta el directorio requerido para el paquete ({label}): {path}")
    files = sorted(file for file in path.rglob("*") if file.is_file())
    if not files:
        raise FileNotFoundError(f"El directorio requerido está vacío ({label}): {path}")
    return files


def archive_name(file: Path) -> Path:
    try:
        return archive_root / file.relative_to(dist)
    except ValueError:
        return archive_root / file.name


def is_private_path(name: str) -> bool:
    normalized = name.replace("\\", "/").lower()
    return (
        "credential" in normalized
        or "state.json" in normalized
        or "/media/" in normalized
    )


def package_files() -> tuple[list[Path], int, int]:
    core = [require_file(dist / name) for name in CORE_DIST_FILES]
    runtime = [require_file(dist / name) for name in RUNTIME_DIST_FILES]
    docs = [require_file(file) for file in DOCUMENTATION_FILES]
    ui = require_tree(dist / "ui", "UI")
    agent = require_tree(dist / "agent", "agent")

    files = [*core, *runtime, *ui, *agent, *docs]
    private = [archive_name(file).as_posix() for file in files if is_private_path(archive_name(file).as_posix())]
    if private:
        raise RuntimeError(
            "Datos privados seleccionados para el paquete: " + ", ".join(private)
        )
    return files, len(ui), len(agent)


files, ui_count, agent_count = package_files()
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for file in files:
        archive.write(file, archive_name(file))

with zipfile.ZipFile(output) as archive:
    if archive.testzip() is not None:
        raise RuntimeError("CRC incorrecto")
    names = archive.namelist()
    missing = [
        name
        for name in (
            *(f"Nexus-Desktop/{name}" for name in CORE_DIST_FILES),
            *(f"Nexus-Desktop/{name}" for name in RUNTIME_DIST_FILES),
            "Nexus-Desktop/README.md",
            "Nexus-Desktop/SIGNING.md",
        )
        if name not in names
    ]
    if missing:
        raise RuntimeError("Faltan rutas requeridas en el paquete: " + ", ".join(missing))
    if not any(name.startswith("Nexus-Desktop/ui/") for name in names):
        raise RuntimeError("Falta la UI en el paquete")
    if not any(name.startswith("Nexus-Desktop/agent/") for name in names):
        raise RuntimeError("Falta el runtime del agente en el paquete")
    if any(is_private_path(name) for name in names):
        raise RuntimeError("Datos privados en el paquete")

print(
    json.dumps(
        {
            "package": str(output),
            "files": len(files),
            "uiFiles": ui_count,
            "agentFiles": agent_count,
            "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
            "crc": "PASS",
        }
    )
)
