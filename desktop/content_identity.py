"""Verified local content identity, shared by imports and project moves."""
import hashlib
import os
from pathlib import Path
import threading

_LOCK = threading.RLock()
_CACHE = {}


def _identity(stat):
    return stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime_ns


def _stamp(stream):
    stat = os.fstat(stream.fileno())
    change = stat.st_ctime_ns
    if os.name == 'nt':
        # Python's Windows st_ctime is creation time. NTFS ChangeTime also
        # invalidates the cache after edits that restore the old last-write time.
        import ctypes
        from ctypes import wintypes
        import msvcrt

        class FileBasicInfo(ctypes.Structure):
            _fields_ = [(name, ctypes.c_longlong) for name in
                        ('creation', 'access', 'write', 'change')] + [('attributes', wintypes.DWORD)]

        info = FileBasicInfo()
        query = ctypes.windll.kernel32.GetFileInformationByHandleEx
        query.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
        query.restype = wintypes.BOOL
        if not query(msvcrt.get_osfhandle(stream.fileno()), 0, ctypes.byref(info), ctypes.sizeof(info)):
            raise OSError('No se pudo verificar la identidad del archivo.')
        change = info.change
    return _identity(stat), change


def verified_content(path):
    """Return (byte count, SHA-256) only while a regular file stays unchanged."""
    path = Path(path)
    try:
        if path.is_symlink() or not path.is_file():
            return None
        before = path.stat()
        key = str(path.resolve())
        with path.open('rb') as stream:
            stamp = _stamp(stream)
            if stamp[0] != _identity(before):
                return None
            with _LOCK:
                cached = _CACHE.get(key)
                if cached and cached[0] == stamp:
                    return before.st_size, cached[1]
            digest = hashlib.sha256()
            while block := stream.read(1024 * 1024):
                digest.update(block)
            if _stamp(stream) != stamp:
                return None
        if _identity(path.stat()) != stamp[0]:
            return None
        value = digest.hexdigest()
        with _LOCK:
            if len(_CACHE) >= 4096:
                _CACHE.clear()
            _CACHE[key] = stamp, value
        return before.st_size, value
    except OSError:
        return None
