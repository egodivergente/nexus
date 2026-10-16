"""Elige el S25 conectado por ADB según su modelo, sin depender de una IP fija."""
import os
import subprocess
from pathlib import Path

S25_MODEL = 'SM_S938B'


def adb_path():
    return Path(os.environ.get('LOCALAPPDATA', '')) / 'Android/Sdk/platform-tools/adb.exe'


def parse_devices(output, model=S25_MODEL):
    """Devuelve los seriales en estado «device» cuyo modelo coincide, con los de red (IP:puerto) primero."""
    found = []
    for line in output.splitlines()[1:]:
        parts = line.split()
        if len(parts) < 2 or parts[1] != 'device':
            continue
        if f'model:{model}' not in parts:
            continue
        found.append(parts[0])
    return sorted(found, key=lambda serial: (not serial.replace('.', '').replace(':', '').isdigit(), serial))


def find_serial(model=S25_MODEL, preferred=None, timeout=10):
    """Serial del S25 conectado. `preferred` solo se usa si sigue conectado."""
    result = subprocess.run([str(adb_path()), 'devices', '-l'], capture_output=True, text=True, timeout=timeout,
                            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    serials = parse_devices(result.stdout, model)
    if preferred and preferred in serials:
        return preferred
    if not serials:
        raise ValueError('No hay ningún S25 conectado por ADB. Comprueba la depuración inalámbrica o el cable.')
    return serials[0]
