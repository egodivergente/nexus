"""Bounded, persistent gallery thumbnails from the user's existing originals.

Only a registered job/output can be requested. Sources and generation records
remain unchanged; downloaded originals and derived JPEGs live in a private cache.
"""
import hashlib
import json
import mimetypes
import os
from pathlib import Path
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

from gallery_sources import GallerySources

MAX_REMOTE_BYTES = 64 * 1024 * 1024
FLAGS = getattr(subprocess, 'CREATE_NO_WINDOW', 0)
PROCESS_PREVIEW_WORKERS = threading.BoundedSemaphore(2)


class PreviewUnavailable(ValueError):
    pass


class GalleryPreviews:
    def __init__(self, data, read_state, ffmpeg, recover_outputs=None, allow_remote=True):
        self.data = Path(data)
        self.sources = GallerySources(self.data, read_state)
        self.directory = self.data / 'gallery-previews'
        self.originals = self.directory / 'originals'
        self.originals.mkdir(parents=True, exist_ok=True)
        self.ffmpeg = ffmpeg
        self.recover_outputs = recover_outputs
        self.allow_remote = allow_remote
        self.workers = PROCESS_PREVIEW_WORKERS
        self.guard = threading.Lock()
        self.failures = {}

    @staticmethod
    def key(job_id, index, url):
        return hashlib.sha256(json.dumps([job_id, index, url], ensure_ascii=False).encode()).hexdigest()

    def cached_original(self, key):
        return next((p for p in self.originals.glob(key + '.*') if p.suffix != '.part'), None)

    def original(self, job_id, index=0):
        job, url, path = self.sources.resolve(job_id, index)
        return job, url, path or self.cached_original(self.key(job_id, index, url))

    def thumbnail(self, job_id, index=0):
        job, url, source = self.original(job_id, index)
        if job.get('modality') not in {'IMAGE', 'VIDEO'}:
            raise PreviewUnavailable('Este archivo no necesita una miniatura visual.')
        identity = self.key(job_id, index, url)
        if not self.workers.acquire(timeout=25):
            raise PreviewUnavailable('La miniatura sigue en preparación. Vuelve a intentarlo.')
        try:
            with self.guard:
                failed_at = self.failures.get(identity, 0)
            if source is None and failed_at and time.monotonic() - failed_at < 45:
                raise PreviewUnavailable('No se ha podido recuperar el original todavía.')
            if source is None:
                if not self.allow_remote:
                    raise PreviewUnavailable('El original no está disponible en este equipo.')
                try:
                    source = self._download(url, identity, job.get('modality'))
                except (OSError, ValueError, urllib.error.URLError):
                    # A status lookup recovers an existing prediction; it never
                    # submits a new generation or consumes generation credits.
                    fresh = self.recover_outputs(job) if self.recover_outputs else []
                    if index >= len(fresh):
                        raise PreviewUnavailable('El enlace del original ya no está disponible.') from None
                    source = self._download(fresh[index], identity, job.get('modality'))
            stat = source.stat()
            signature = hashlib.sha256(f'{source}:{stat.st_size}:{stat.st_mtime_ns}:v1'.encode()).hexdigest()
            target = self.directory / (signature + '.jpg')
            if target.is_file() and target.stat().st_size:
                return target
            temporary = target.with_name(target.stem + '.' + uuid.uuid4().hex + '.part.jpg')
            try:
                if job['modality'] == 'IMAGE':
                    from PIL import Image, ImageOps
                    with Image.open(source) as image:
                        image.draft('RGB', (640, 640))
                        image = ImageOps.exif_transpose(image)
                        image.thumbnail((640, 640), Image.Resampling.LANCZOS)
                        if image.mode in {'RGBA', 'LA'}:
                            matte = Image.new('RGB', image.size, (20, 25, 32))
                            matte.paste(image, mask=image.getchannel('A'))
                            image = matte
                        else:
                            image = image.convert('RGB')
                        image.save(temporary, 'JPEG', quality=84, optimize=True)
                else:
                    response = subprocess.run([
                        self.ffmpeg(), '-v', 'error', '-nostdin', '-y', '-threads', '1',
                        '-filter_threads', '1', '-ss', '0', '-i', str(source), '-an',
                        '-vf', 'thumbnail=12,scale=640:640:force_original_aspect_ratio=decrease',
                        '-frames:v', '1', '-threads', '1', '-q:v', '3', str(temporary)],
                        capture_output=True, timeout=30,
                        creationflags=FLAGS | getattr(subprocess, 'BELOW_NORMAL_PRIORITY_CLASS', 0))
                    if response.returncode or not temporary.is_file() or not temporary.stat().st_size:
                        raise PreviewUnavailable('No se ha podido leer un fotograma del vídeo.')
                os.replace(temporary, target)
            finally:
                temporary.unlink(missing_ok=True)
            return target
        except (OSError, ValueError, subprocess.SubprocessError):
            # A provider can label an expired/error body as binary media. Only
            # discard our downloaded cache copy, never the user's original.
            if source is not None and source.parent.resolve() == self.originals.resolve():
                source.unlink(missing_ok=True)
            with self.guard:
                self.failures[identity] = time.monotonic()
            raise PreviewUnavailable('No se ha podido preparar la miniatura del original.') from None
        finally:
            self.workers.release()

    def _download(self, url, key, modality):
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password:
            raise PreviewUnavailable('El enlace del original no es válido.')
        request = urllib.request.Request(url, headers={'User-Agent': 'Nexus/1.0 (Windows; gallery)'})
        temporary = self.originals / (key + '.' + uuid.uuid4().hex + '.part')
        deadline = time.monotonic() + 20
        try:
            with urllib.request.urlopen(request, timeout=12) as response:
                mime = response.headers.get_content_type()
                if not mime.startswith(('image/', 'video/')) and mime not in {'application/octet-stream', 'binary/octet-stream'}:
                    raise PreviewUnavailable('El enlace no devolvió un archivo multimedia.')
                declared = int(response.headers.get('Content-Length', '0'))
                if declared > MAX_REMOTE_BYTES:
                    raise PreviewUnavailable('El original supera el tamaño de la caché de miniaturas.')
                extension = mimetypes.guess_extension(mime) or Path(parsed.path).suffix.lower()
                if extension not in {'.png', '.jpg', '.jpeg', '.webp', '.gif', '.mp4', '.mov', '.webm'}:
                    extension = '.mp4' if modality == 'VIDEO' else '.png'
                total = 0
                with temporary.open('wb') as stream:
                    while block := response.read(256 * 1024):
                        if time.monotonic() > deadline:
                            raise PreviewUnavailable('El original ha tardado demasiado en responder.')
                        total += len(block)
                        if total > MAX_REMOTE_BYTES:
                            raise PreviewUnavailable('El original supera el tamaño de la caché de miniaturas.')
                        stream.write(block)
                if not total:
                    raise PreviewUnavailable('El original está vacío.')
            target = self.originals / (key + extension)
            os.replace(temporary, target)
            return target
        finally:
            temporary.unlink(missing_ok=True)
