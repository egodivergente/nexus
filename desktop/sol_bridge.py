"""Reach the S25's existing authenticated Sol bridge through the existing SSH key.

No new service, account or APK is installed on the phone. This connection uses
Tailscale when active and falls back to the local Wi-Fi address of the S25 when
on the same LAN; the separate Bedrock option stays available.
"""
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

PORT = 18787
TARGETS = ('u0_a455@100.119.88.105', 'u0_a455@192.168.1.134')


def _healthy():
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{PORT}/health', timeout=0.6) as response:
            return response.status == 200
    except Exception:
        return False


def _stop_tunnel(proc):
    if proc is not None and proc.poll() is None:
        try:
            proc.terminate()
            proc.wait(timeout=1)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass


def _ordered_targets():
    reachable = []
    fallback = []
    for target in TARGETS:
        host_ip = target.split('@', 1)[-1]
        try:
            with socket.socket() as probe:
                probe.settimeout(0.8)
                if probe.connect_ex((host_ip, 8022)) == 0:
                    reachable.append(target)
                else:
                    fallback.append(target)
        except Exception:
            fallback.append(target)
    return reachable + fallback


def ensure(host):
    settings = host.settings()
    if settings.get('backend') != 'proxy' or settings.get('endpoint') != f'http://127.0.0.1:{PORT}':
        return
    if _healthy():
        return
    _stop_tunnel(getattr(host, 'tunnel', None))
    ssh = Path('C:/Windows/System32/OpenSSH/ssh.exe')
    for target in _ordered_targets():
        host.tunnel = subprocess.Popen([str(ssh), '-N', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
            '-o', 'ConnectTimeout=4', '-o', 'ExitOnForwardFailure=yes', '-o', 'ServerAliveInterval=30',
            '-o', 'ServerAliveCountMax=2', '-L', f'127.0.0.1:{PORT}:127.0.0.1:8787',
            '-p', '8022', target], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        deadline = time.monotonic() + 5.5
        while time.monotonic() < deadline:
            if host.tunnel.poll() is not None:
                break
            with socket.socket() as probe:
                probe.settimeout(.2)
                if probe.connect_ex(('127.0.0.1', PORT)) == 0 and _healthy():
                    return
            time.sleep(.1)
        _stop_tunnel(host.tunnel)
    raise ValueError('No se pudo conectar con Sol en el S25. Comprueba Tailscale/Wi-Fi y el puente del móvil.')
