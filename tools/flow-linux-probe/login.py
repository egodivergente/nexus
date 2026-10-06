"""Temporary local-only browser access. Run under Xvfb and a bounded systemd unit."""
import os
from pathlib import Path
import secrets
import string
import subprocess
import time


def main():
    os.umask(0o077)
    root = Path.home() / '.local/share/nexus-flow-probe'
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    password = ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(8))
    password_file = root / 'vnc-password'
    subprocess.run(['x11vnc', '-storepasswd', password, str(password_file)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    (root / 'login-access').write_text(password, encoding='ascii')
    children = []
    try:
        for command in [
            ['x11vnc', '-display', os.environ['DISPLAY'], '-rfbauth', str(password_file),
             '-listen', '127.0.0.1', '-no6', '-rfbport', '5909', '-forever', '-shared', '-noxdamage', '-quiet'],
            ['websockify', '--web=/usr/share/novnc', '127.0.0.1:6099', '127.0.0.1:5909'],
            ['chromium', '--user-data-dir=' + str(root / 'profile'), '--no-first-run',
             '--disable-dev-shm-usage', '--window-size=1280,800', '--window-position=0,0',
             'https://flow.google.com/'],
        ]:
            children.append(subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        deadline = time.monotonic() + 1200
        while time.monotonic() < deadline and all(child.poll() is None for child in children):
            time.sleep(1)
    finally:
        for child in reversed(children):
            if child.poll() is None:
                child.terminate()
        for child in children:
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
        (root / 'login-access').unlink(missing_ok=True)
        password_file.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
