"""Verify the reproducible Corte dependency without trusting a mutable sibling checkout."""
from pathlib import Path
import hashlib
import json
root=Path(__file__).resolve().parents[1]/'corte'
lock=json.loads((root/'source-lock.json').read_text('utf-8'))
for relative,expected in lock['files'].items():
    actual=hashlib.sha256((root/relative).read_bytes().replace(b'\r\n',b'\n')).hexdigest()
    if actual!=expected:raise SystemExit('Corte source mismatch: '+relative)
print('PASS:',len(lock['files']),'Corte files at',lock['commit'])
