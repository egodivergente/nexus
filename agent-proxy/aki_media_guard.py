"""Fail-closed hook. Only an exact, generated attachment view may execute."""
import json
from pathlib import Path
import sys

try:
    event = json.loads(sys.stdin.read(65536))
    allowed = json.loads(Path(__file__).with_name("allowed.json").read_text())
    valid = event.get("tool_name") == "Bash" and event.get("tool_input", {}).get("command") in allowed
except BaseException:
    valid = False
if not valid:
    print("Only the supplied attachment view commands are permitted.", file=sys.stderr)
    sys.exit(2)
