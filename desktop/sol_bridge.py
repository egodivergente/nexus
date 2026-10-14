"""Reach the S25's existing authenticated Sol bridge through the existing SSH key.

No new service, account or APK is installed on the phone. This connection needs
the S25 online in Tailscale; the separate Bedrock option stays available.
"""
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

PORT = 18787


def _healthy():
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{PORT}/health', timeout=0.6) as response:
            return response.status == 200
    except Exception:
        return False


def ensure(host):
    settings = host.settings()
    if settings.get('backend') != 'proxy' or settings.get('endpoint') != f'http://127.0.0.1:{PORT}':
        return
    if _healthy():
        return
    existing = getattr(host, 'tunnel', None)
    if existing is not None and existing.poll() is None:
        try:
            existing.terminate()
            existing.wait(timeout=1)
        except Exception:
            pass
    ssh = Path('C:/Windows/System32/OpenSSH/ssh.exe')
    host.tunnel = subprocess.Popen([str(ssh), '-N', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
        '-o', 'ConnectTimeout=7', '-o', 'ExitOnForwardFailure=yes', '-o', 'ServerAliveInterval=30',
        '-o', 'ServerAliveCountMax=2', '-L', f'127.0.0.1:{PORT}:127.0.0.1:8787',
        '-p', '8022', 'u0_a455@100.119.88.105'], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    deadline = time.monotonic()+8
    while time.monotonic() < deadline:
        if host.tunnel.poll() is not None:
            raise ValueError('No se pudo conectar con Sol en el S25. Comprueba Tailscale y el puente del móvil.')
        with socket.socket() as probe:
            probe.settimeout(.2)
            if probe.connect_ex(('127.0.0.1', PORT)) == 0 and _healthy():
                return
        time.sleep(.1)
    try:
        host.tunnel.terminate()
    except Exception:
        pass
    raise ValueError('El S25 no respondió. El mensaje se conserva; no se ha enviado a otro modelo.')
