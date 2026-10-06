"""Launch the pinned Flow runtime without inheriting another project's .env."""
from pathlib import Path
import os
import sys

ROOT = Path.home() / ".codex" / "integrations" / "google-flow"

def environment():
    return {
        "GFLOW_CLI_HOME": str(ROOT / "state"),
        "GFLOW_CLI_OUTPUT_DIR": str(ROOT / "results"),
        "GFLOW_CLI_PROFILE": "nexus",
        "GFLOW_CLI_FLOW_HOST": "auto",
        "GFLOW_CLI_CONCURRENCY": "1",
        "GFLOW_CLI_HEADLESS": "false",
        "GFLOW_CLI_AUTH_LOGIN_TIMEOUT": "3600",
        "GFLOW_CLI_BROWSER_WINDOW_POSITION": "100,100",
        "PLAYWRIGHT_BROWSERS_PATH": str(ROOT / "browsers"),
        "PYTHONUTF8": "1",
        "PYTHONIOENCODING": "utf-8",
    }

def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    os.environ.update(environment())
    os.chdir(ROOT)
    from runtime_compat import install
    install()
    from gflow_cli.cli import main as gflow_main
    gflow_main()

if __name__ == "__main__":
    main()
