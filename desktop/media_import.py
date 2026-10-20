"""Bounded local imports. The 4 GiB limit is per file, unrelated to provider limits."""
import base64
import hashlib
import os
from pathlib import Path
import shutil
import threading
import time
import uuid

MAX_IMPORT_BYTES = 4 * 1024**3
CHUNK_BYTES = 4 * 1024**2
MIME = {'.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
        '.webp': 'image/webp', '.mp4': 'video/mp4', '.mov': 'video/quicktime',
        '.webm': 'video/webm', '.mp3': 'audio/mpeg', '.wav': 'audio/wav',
        '.m4a': 'audio/mp4', '.ogg': 'audio/ogg', '.flac': 'audio/flac',
        '.pdf': 'application/pdf', '.txt': 'text/plain', '.md': 'text/markdown',
        '.json': 'text/plain', '.py': 'text/plain', '.js': 'text/plain', '.sh': 'text/plain', '.ps1': 'text/plain',
        '.csv': 'text/csv', '.html': 'text/html', '.srt': 'text/plain', '.zip': 'application/zip'}


def validate(name, size):
    name = Path(str(name)).name
    ext = Path(name).suffix.lower()
    if ext not in MIME:
        raise ValueError('Nexus no admite este formato de archivo.')
    if isinstance(size, bool) or not isinstance(size, int) or not 0 < size <= MAX_IMPORT_BYTES:
        raise ValueError('El archivo debe tener contenido y no superar los 4 GiB (4.294.967.296 bytes).')
    return name, ext


class MediaImporter:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.pending = {}

    def begin(self, name, size):
        name, ext = validate(name, size)
        with self.lock:
            for key, item in list(self.pending.items()):
                if time.monotonic() - item['updated'] > 3600:
                    self.abort(key)
            if len(self.pending) >= 8:
                raise ValueError('Espera a que termine otra importación.')
            reserved = sum(item['size'] - item['received'] for item in self.pending.values())
            if shutil.disk_usage(self.directory).free < size + reserved + 16 * 1024**2:
                raise ValueError('No hay espacio suficiente para copiar el archivo completo.')
            identifier = uuid.uuid4().hex + ext
            part = self.directory / (identifier + '.importing')
            part.touch(exist_ok=False)
            self.pending[identifier] = dict(name=name, size=size, received=0, updated=time.monotonic(),
                                            part=part, digest=hashlib.sha256())
            return {'import_id': identifier, 'chunk_bytes': CHUNK_BYTES, 'received': 0}

    def append(self, identifier, offset, content):
        if not isinstance(content, str) or len(content) > ((CHUNK_BYTES + 2) // 3) * 4:
            raise ValueError('Bloque de importación demasiado grande.')
        try:
            block = base64.b64decode(content, validate=True)
        except (ValueError, TypeError) as error:
            raise ValueError('Bloque de importación no válido.') from error
        return self.append_bytes(identifier, offset, block)

    def append_bytes(self, identifier, offset, block):
        with self.lock:
            item = self._item(identifier)
            if isinstance(offset, bool) or not isinstance(offset, int) or offset != item['received']:
                raise ValueError('La posición del bloque no coincide. No se ha repetido la escritura.')
            if not block or len(block) > CHUNK_BYTES or offset + len(block) > item['size']:
                raise ValueError('El bloque supera el tamaño declarado del archivo.')
            with item['part'].open('ab') as stream:
                stream.write(block)
            item['digest'].update(block)
            item.update(received=offset + len(block), updated=time.monotonic())
            return {'received': item['received']}

    def finish(self, identifier):
        with self.lock:
            item = self._item(identifier)
            if item['received'] != item['size'] or item['part'].stat().st_size != item['size']:
                raise ValueError('Importación incompleta. El archivo todavía no está disponible en Biblioteca.')
            with item['part'].open('ab') as stream:
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(item['part'], self.directory / identifier)
            self.pending.pop(identifier)
            return {'id': identifier, 'name': item['name'], 'size': item['size'],
                    'mime': MIME[Path(identifier).suffix], 'url': '/media/' + identifier,
                    'sha256': item['digest'].hexdigest()}

    def abort(self, identifier):
        with self.lock:
            item = self.pending.pop(identifier, None)
            if item:
                item['part'].unlink(missing_ok=True)
            return {'cancelled': True}

    def _item(self, identifier):
        if identifier not in self.pending:
            raise ValueError('Importación desconocida o caducada. Vuelve a seleccionar el archivo.')
        return self.pending[identifier]

    def copy(self, source):
        source = Path(source).expanduser().resolve(strict=True)
        if not source.is_file():
            raise ValueError('Selecciona un archivo local existente.')
        before = source.stat()
        identifier = self.begin(source.name, before.st_size)['import_id']
        try:
            with source.open('rb') as stream:
                while block := stream.read(CHUNK_BYTES):
                    self.append_bytes(identifier, self.pending[identifier]['received'], block)
            after = source.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                raise ValueError('El original cambió mientras se copiaba. Repite la importación cuando termine de guardarse.')
            return self.finish(identifier)
        except BaseException:
            self.abort(identifier)
            raise
