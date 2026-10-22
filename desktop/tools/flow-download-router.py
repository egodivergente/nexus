"""Route completed Windows Flow media downloads using their Zone.Identifier origin."""
from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import json
import logging
from logging.handlers import RotatingFileHandler
import msvcrt
import os
from pathlib import Path
import sys
import time
from urllib.parse import urlsplit

MEDIA = {'.jpg', '.jpeg', '.png', '.webp', '.gif', '.avif', '.heic', '.heif',
         '.bmp', '.tif', '.tiff', '.mp4', '.mov', '.webm', '.mkv', '.m4v', '.avi'}


def flow_url(value: str) -> bool:
    if value.startswith('blob:'):
        value = value[5:]
    try:
        u = urlsplit(value.strip())
        parts = u.path.strip('/').split('/')
        return u.scheme == 'https' and (u.hostname == 'flow.google.com' or
            (u.hostname == 'labs.google' and parts[0] == 'fx' and
             any(parts[i:i + 2] == ['tools', 'flow'] for i in range(len(parts) - 1))))
    except ValueError:
        return False


def is_flow_file(path: Path) -> bool:
    try:
        with open(str(path) + ':Zone.Identifier', 'r', encoding='utf-8-sig') as f:
            zone = f.read(16384)
        return any(key.lower() in ('hosturl', 'referrerurl') and flow_url(value)
                   for key, sep, value in (line.partition('=') for line in zone.splitlines())
                   if sep)
    except (OSError, UnicodeError):
        return False


def stamp(path: Path) -> list[int]:
    s = path.stat()
    return [s.st_size, s.st_mtime_ns]


def unlocked(path: Path) -> bool:
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
        wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    kernel.CreateFileW.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.CreateFileW(str(path), 0x80000000, 0, None, 3, 0x80, None)
    if handle == wintypes.HANDLE(-1).value:
        return False
    kernel.CloseHandle(handle)
    return True


def save_json(path: Path, value: dict) -> None:
    tmp = path.with_suffix(path.suffix + '.new')
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(tmp, path)


class Router:
    def __init__(self, sources: list[Path], destination: Path, state: Path, settle: float = 4):
        self.sources = [p.resolve() for p in sources]
        self.destination = destination.resolve()
        self.destination.mkdir(parents=True, exist_ok=True)
        if any(p == self.destination for p in self.sources):
            raise ValueError('Source and destination must differ')
        self.state = state
        self.state.parent.mkdir(parents=True, exist_ok=True)
        self.settle = settle
        self.pending = {}
        self.retry_after = {}
        if state.exists():
            self.known = json.loads(state.read_text(encoding='utf-8'))['known']
        else:
            # A first run never sweeps existing files into a different folder.
            self.known = {}
            for p in self.files():
                try:
                    self.known[str(p)] = stamp(p)
                except OSError:
                    continue
            self.persist()

    def files(self):
        for folder in self.sources:
            if not folder.is_dir():
                continue
            for p in folder.iterdir():
                try:
                    if p.suffix.lower() in MEDIA and p.is_file() and not p.is_symlink():
                        yield p
                except OSError:
                    continue

    def persist(self):
        save_json(self.state, {'version': 1, 'known': self.known})

    def move(self, source: Path) -> Path:
        """Windows rename refuses collisions; a cross-volume source stays untouched."""
        import subprocess
        for index in range(1, 10000):
            name = source.name if index == 1 else f'{source.stem} ({index}){source.suffix}'
            target = self.destination / name
            try:
                os.rename(source, target)
            except FileExistsError:
                continue
            # Renames preserve the old ACL: inherit the bridge's account permissions.
            try:
                result = subprocess.run(['icacls.exe', str(target), '/reset', '/Q'],
                    capture_output=True, timeout=20,
                    creationflags=subprocess.CREATE_NO_WINDOW)
                if result.returncode:
                    raise PermissionError('Could not inherit destination permissions')
            except Exception:
                if not source.exists():
                    os.rename(target, source)
                raise
            return target
        raise FileExistsError('Too many filename collisions')

    def scan(self, now: float | None = None) -> list[Path]:
        now = time.monotonic() if now is None else now
        moved = []
        present = set()
        for p in self.files():
            key = str(p)
            present.add(key)
            try:
                sig = stamp(p)
                if self.known.get(key) == sig or now < self.retry_after.get(key, 0):
                    continue
                previous = self.pending.get(key)
                if previous is None or previous[0] != sig:
                    self.pending[key] = (sig, now)
                    continue
                if not sig[0] or now - previous[1] < self.settle:
                    continue
                if any(Path(str(p) + suffix).exists() for suffix in ('.crdownload', '.part')):
                    continue
                if not is_flow_file(p):
                    self.retry_after[key] = now + 30
                    continue
                if not unlocked(p) or stamp(p) != sig:
                    continue
                target = self.move(p)
                self.known[key] = sig
                self.persist()
                moved.append(target)
                logging.info('routed %s', target.name)
            except (OSError, ValueError) as error:
                self.retry_after[key] = now + 30
                logging.warning('route failed: %s', type(error).__name__)
        self.pending = {k: v for k, v in self.pending.items() if k in present}
        self.retry_after = {k: v for k, v in self.retry_after.items() if k in present}
        return moved


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True, type=Path)
    parser.add_argument('--initialize', action='store_true')
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding='utf-8-sig'))
    runtime = args.config.resolve().parent
    # Keep one process across repeated startup launches.
    lock = (runtime / 'router.lock').open('a+b')
    lock.seek(0)
    try:
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
    except OSError:
        return
    handler = RotatingFileHandler(runtime / 'router.log', maxBytes=512000, backupCount=1,
                                  encoding='utf-8')
    logging.basicConfig(level=logging.INFO, handlers=[handler],
                        format='%(asctime)s %(levelname)s %(message)s')
    router = Router([Path(p) for p in config['sources']], Path(config['destination']),
                    runtime / 'state.json', config.get('settle_seconds', 4))
    if args.initialize:
        return
    while True:
        try:
            moved = router.scan()
            save_json(runtime / 'health.json', {'pid': os.getpid(), 'scanned_at': time.time(),
                'destination': str(router.destination), 'moved_this_scan': len(moved)})
        except Exception as error:
            logging.error('scan failed: %s', type(error).__name__)
        time.sleep(max(2, config.get('poll_seconds', 4)))


if __name__ == '__main__':
    main()
