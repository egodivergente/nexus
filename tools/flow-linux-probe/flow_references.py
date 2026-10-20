"""Bounded private reference upload and visible Flow ingredient verification."""
import asyncio
import base64
import hashlib
import os
from pathlib import Path
import re
import secrets

MAX_IMAGES = 12  # Nexus transport cap; not a claim about Google's model limits.
MAX_BYTES = 16 * 1024 * 1024
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
    if not isinstance(ids, list) or len(ids) > MAX_IMAGES or not all(
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
        if not 0 < path.stat().st_size <= MAX_BYTES:
            raise ReferenceError('invalid_reference_image')
        # Verify bytes again: never accept an arbitrary server path or remote URL.
        if hashlib.sha256(path.read_bytes()).hexdigest() != value[4:]:
            raise ReferenceError('reference_integrity_failed', 409)
        paths.append(path)
    return paths


async def receive_reference(request, root):
    declared = request.headers.get('content-type', '').split(';')[0].lower()
    if declared not in EXTENSIONS:
        raise ReferenceError('invalid_reference_type', 415)
    length = request.headers.get('content-length')
    if length and length.isdigit() and int(length) > MAX_BYTES:
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
                if size > MAX_BYTES:
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


def frame_paths(root, initial_frame_id=None, end_frame_id=None):
    """Resolve one or two uploaded frame IDs without accepting arbitrary paths or URLs."""
    if end_frame_id is not None and initial_frame_id is None:
        raise ReferenceError('end_frame_requires_initial', 400)
    ids = [value for value in (initial_frame_id, end_frame_id) if value is not None]
    if any(not isinstance(value, str) or not REFERENCE_ID.fullmatch(value) for value in ids):
        raise ReferenceError('invalid_frame_ids', 400)
    if len(ids) != len(set(ids)):
        raise ReferenceError('duplicate_frame', 400)
    return reference_paths(root, ids)


def frame_upload_sequence(initial_frame, end_frame=None):
    """Ordered labeled inputs for Flow's dedicated Frames mode."""
    if initial_frame is None:
        raise ReferenceError('initial_frame_required', 400)
    if end_frame == initial_frame and end_frame is not None:
        raise ReferenceError('duplicate_frame', 400)
    return [('Inicio', initial_frame)] + ([('Fin', end_frame)] if end_frame is not None else [])


async def attach_video_frames(page, initial_frame, end_frame=None):
    sequence = frame_upload_sequence(initial_frame, end_frame)
    # These controls are scoped to Flow's "Vídeo > Fotogramas" composer.
    video = page.get_by_role('button', name=re.compile(r'^Vídeo$|^Video$', re.I))
    if await video.count():
        await video.first.click(timeout=5000)
    frames = page.get_by_role('button', name=re.compile(r'^Fotogramas$|^Frames$', re.I))
    if not await frames.count():
        raise ReferenceError('flow_frame_mode_unavailable', 409)
    await frames.first.click(timeout=5000)
    for label, path in sequence:
        slot = page.get_by_role('button', name=re.compile(rf'^{re.escape(label)}$', re.I))
        if not await slot.count():
            raise ReferenceError('flow_frame_controls_unavailable', 409)
        try:
            async with page.expect_file_chooser(timeout=5000) as chooser_info:
                await slot.first.click(timeout=5000)
            chooser = await chooser_info.value
            await chooser.set_files(str(path))
        except ReferenceError:
            raise
        except Exception as error:
            raise ReferenceError('flow_frame_upload_failed', 409) from error


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
