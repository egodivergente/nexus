"""Background Flow renewal shared by every Nexus window."""
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import uuid

RUNTIME = Path.home() / '.codex/integrations/google-flow'
LOCK = threading.Lock()
STATE = {'status': 'pending', 'checked_at': 0, 'refreshed': False}
RUNNING = False
INTERACTIVE = False
NEXT_CHECK = 0
LAST_STARTED = -1000

def probe(interactive=False):
    python = RUNTIME / 'venv/Scripts/python.exe'
    script = RUNTIME / 'flow_session.py'
    if not python.is_file() or not script.is_file():
        return {'status': 'unavailable', 'checked_at': time.time()}
    folder = RUNTIME / 'jobs'
    folder.mkdir(parents=True, exist_ok=True)
    output = folder / ('session-' + uuid.uuid4().hex + '.json')
    flags = subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS if os.name == 'nt' else 0
    try:
        command = [str(python), str(script), '--force', '--output', str(output)]
        if interactive:
            command.append('--interactive')
        run = subprocess.run(command,
            cwd=RUNTIME, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, timeout=3900 if interactive else 180, creationflags=flags)
        value = json.loads(output.read_text(encoding='utf-8')) if run.returncode == 0 else {}
        status = value.get('status')
        return {'status': status if status in {'connected','auth_required','busy','error'} else 'error',
                'checked_at': time.time(), 'refreshed': value.get('refreshed') is True,
                'reason': value.get('reason') if value.get('reason') in {'browser_rejected','password_required','verification_required','account_selection_required','google_signin','page_not_ready'} else None}
    except (OSError, ValueError, subprocess.TimeoutExpired):
        return {'status': 'error', 'checked_at': time.time()}
    finally:
        output.unlink(missing_ok=True)

def _refresh(interactive=False):
    global STATE, RUNNING, NEXT_CHECK
    try:
        value = probe(interactive)
    except Exception:
        value = {'status': 'error', 'checked_at': time.time()}
    with LOCK:
        STATE = value
        NEXT_CHECK = time.monotonic() + (300 if value['status'] in {'connected','auth_required'} else 30)
        RUNNING = False

def snapshot(refresh=False, interactive=False):
    global RUNNING, NEXT_CHECK, LAST_STARTED, INTERACTIVE
    with LOCK:
        now = time.monotonic()
        if not RUNNING and (interactive or now-LAST_STARTED >= 15 and (now >= NEXT_CHECK or refresh)):
            # Never open a second browser while a Flow result worker owns it.
            from flow_provider import WORKERS
            if WORKERS:
                NEXT_CHECK = now + 30
                return {**STATE, 'status': 'busy'}
            RUNNING = True
            INTERACTIVE = interactive
            LAST_STARTED = now
            NEXT_CHECK = now + 15
            threading.Thread(target=_refresh, args=(interactive,), daemon=True).start()
        return {**STATE, 'status': ('signing_in' if INTERACTIVE else 'refreshing') if RUNNING else STATE['status']}
