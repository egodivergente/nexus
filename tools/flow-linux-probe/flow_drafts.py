"""Preserve a pre-existing text draft locally before temporarily borrowing Flow's composer."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import time


def preserve(root: Path, project_url: str, text: str) -> Path:
    directory = root / 'flow-drafts'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    identity = hashlib.sha256((project_url + '\n' + text).encode('utf-8')).hexdigest()
    target = directory / (identity + '.json')
    if target.is_file():
        return target
    temporary = directory / ('.draft-' + secrets.token_hex(12))
    try:
        with temporary.open('x', encoding='utf-8') as output:
            os.chmod(temporary, 0o600)
            json.dump({'project_url': project_url, 'text': text, 'preserved_at': time.time()}, output, ensure_ascii=False)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    return target


async def restore_if_untouched(composer, previous, submitted):
    current = await composer.evaluate('e => (e.value ?? e.innerText) || ""')
    if not current.strip() or current == submitted:
        await composer.fill(previous, timeout=5000)
        return True
    return False  # A manual edit made during generation always wins.
