import hashlib
import json
import os
import platform
from pathlib import Path

Path("smoke-result.json").write_text(json.dumps({"hostname":platform.node(),"arch":platform.machine(),
    "pid":os.getpid(),"source_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    "result":"background Python executed from verified source snapshot"}),encoding="utf-8")
