"""Private streaming reference upload and visible Flow ingredient verification."""
import asyncio
import base64
import hashlib
import os
from pathlib import Path
import re
import secrets

MAX_IMAGES = None  # Unknown Google maximum; local preferences live in the Android editor.
MAX_BYTES = None
REFERENCE_ID = re.compile(r'^ref_[0-9a-f]{64}$')
EXTENSIONS = {'image/png': '.png', 'image/jpeg': '.jpg', 'image/webp': '.webp'}


class ReferenceError(Exception):
    def __init__(self, code, status_code=400):
        super().__init__(code)
        self.code, self.status_code = code, status_code


def image_type(header):
    if header.startswith(b'\x89PNG\r\n\x1a\n'):
        return 'image/png'
    if header.startswith(b'\xff\xd8\xff'):
        return 'image/jpeg'
    if header.startswith(b'RIFF') and header[8:12] == b'WEBP':
        return 'image/webp'
    raise ReferenceError('invalid_reference_image')


def reference_paths(root, ids):
    if not isinstance(ids, list) or MAX_IMAGES is not None and len(ids) > MAX_IMAGES or not all(
            isinstance(value, str) and REFERENCE_ID.fullmatch(value) for value in ids):
        raise ReferenceError('invalid_reference_ids')
    if len(ids) != len(set(ids)):
        raise ReferenceError('duplicate_reference')
    paths = []
    directory = root / 'flow-references'
    for value in ids:
        matches = [directory / (value + suffix) for suffix in EXTENSIONS.values()
                   if (directory / (value + suffix)).is_file()]
        if len(matches) != 1 or matches[0].is_symlink():
            raise ReferenceError('reference_not_found', 404)
        path = matches[0]
        if path.stat().st_size <= 0 or MAX_BYTES is not None and path.stat().st_size > MAX_BYTES:
            raise ReferenceError('invalid_reference_image')
        # Verify bytes again: never accept an arbitrary server path or remote URL.
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(chunk)
        if digest.hexdigest() != value[4:]:
            raise ReferenceError('reference_integrity_failed', 409)
        paths.append(path)
    return paths


async def receive_reference(request, root):
    declared = request.headers.get('content-type', '').split(';')[0].lower()
    if declared not in EXTENSIONS:
        raise ReferenceError('invalid_reference_type', 415)
    length = request.headers.get('content-length')
    if MAX_BYTES is not None and length and length.isdigit() and int(length) > MAX_BYTES:
        raise ReferenceError('reference_too_large', 413)
    directory = root / 'flow-references'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = directory / ('.upload-' + secrets.token_hex(16))
    size, header, digest = 0, bytearray(), hashlib.sha256()
    try:
        with temporary.open('xb') as output:
            os.chmod(temporary, 0o600)
            async for chunk in request.stream():
                size += len(chunk)
                if MAX_BYTES is not None and size > MAX_BYTES:
                    raise ReferenceError('reference_too_large', 413)
                if len(header) < 16:
                    header.extend(chunk[:16 - len(header)])
                digest.update(chunk)
                output.write(chunk)
            if size == 0 or image_type(bytes(header)) != declared:
                raise ReferenceError('invalid_reference_image')
            output.flush()
            os.fsync(output.fileno())
        value = 'ref_' + digest.hexdigest()
        target = directory / (value + EXTENSIONS[declared])
        os.replace(temporary, target)
        return {'reference_id': value, 'bytes': size, 'mime_type': declared}
    finally:
        temporary.unlink(missing_ok=True)


# Flow (oct 2026) movió el cuadro de prompt a un div editable «¿Qué quieres crear?» (también el del agente de Flow).
# El antiguo input «Texto editable» es ahora el TÍTULO del proyecto: no se debe escribir ahí nunca.
COMPOSER = ('[contenteditable="true"][aria-label="¿Qué quieres crear?"], '
            '[contenteditable="true"][aria-label="What do you want to create?"]')


async def composer_reference_count(page):
    # Minimal ancestor containing both observed composer and Generate controls.
    # Gallery images and model logos outside the composer cannot confirm an upload.
    return await page.locator(COMPOSER).first.evaluate("""input => {
        let node = input.parentElement;
        while (node && !(node.querySelector('button[aria-label="Iniciar generación"]') &&
            node.querySelector('button[aria-label="Añadir ingredientes a ventana para peticiones"]'))) node = node.parentElement;
        if (!node || node === document.body || node === document.documentElement) return -1;
        return [...node.querySelectorAll('img')].filter(img =>
            img.complete && img.naturalWidth > 0 && img.getBoundingClientRect().width > 0).length;
    }""")


async def attach_references(page, paths):
    if not paths:
        return
    baseline = await composer_reference_count(page)
    if baseline != 0:
        raise ReferenceError('flow_references_not_empty' if baseline > 0 else 'flow_reference_controls_unavailable', 409)
    # Google documents dragging local images into the prompt box. Keep the
    # browser's native File/DataTransfer path; never invoke Google's private RPCs.
    composer = page.locator(COMPOSER).first
    try:
        for index, path in enumerate(paths):
            raw = path.read_bytes()
            mime = image_type(raw[:16])
            data = await page.evaluate_handle("""({encoded, name, mime}) => {
                const bytes = Uint8Array.from(atob(encoded), c => c.charCodeAt(0));
                const transfer = new DataTransfer();
                transfer.items.add(new File([bytes], name, {type:mime}));
                return transfer;
            }""", {'encoded': base64.b64encode(raw).decode(), 'name': path.name, 'mime': mime})
            try:
                await composer.dispatch_event('dragenter', {'dataTransfer': data})
                await composer.dispatch_event('dragover', {'dataTransfer': data})
                await composer.dispatch_event('drop', {'dataTransfer': data})
            finally:
                await data.dispose()
            for _ in range(60):
                if await composer_reference_count(page) == index + 1:
                    break
                await asyncio.sleep(0.5)
            else:
                raise ReferenceError('flow_references_unconfirmed', 409)
        if await composer_reference_count(page) != len(paths):
            raise ReferenceError('flow_references_unconfirmed', 409)
    except ReferenceError:
        raise
    except Exception as error:
        raise ReferenceError('flow_reference_upload_failed', 409) from error
