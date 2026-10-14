"""Bounded local PDF text extraction; scanned pages retain the original document route."""
import json
from pathlib import Path
import subprocess
import sys

def extract_pdf(path):
    executable = Path(sys.executable)
    if executable.name.lower() == 'pythonw.exe':
        executable = executable.with_name('python.exe')
    try:
        result = subprocess.run([str(executable), str(Path(__file__).resolve()), str(path)], capture_output=True, timeout=30, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    except subprocess.TimeoutExpired:
        raise ValueError('El PDF ha agotado el tiempo de lectura local. El original se conserva; puedes reintentar.') from None
    if result.returncode:
        raise ValueError('No se pudo leer el PDF. Comprueba que abre correctamente y no requiere contraseña.')
    return json.loads(result.stdout.decode('utf-8'))

def _has_embedded_image(page):
    try:
        resources = page.get('/Resources') or {}
        if hasattr(resources, 'get_object'):
            resources = resources.get_object()
        xobjects = resources.get('/XObject') or {}
        if hasattr(xobjects, 'get_object'):
            xobjects = xobjects.get_object()
        for obj in xobjects.values():
            resolved = obj.get_object() if hasattr(obj, 'get_object') else obj
            if isinstance(resolved, dict) and str(resolved.get('/Subtype') or '') == '/Image':
                return True
    except Exception:
        return True
    return False

if __name__ == '__main__':
    from pypdf import PdfReader
    reader = PdfReader(sys.argv[1])
    total_pages = len(reader.pages)
    pages = []
    complete = total_pages > 0
    for index, page in enumerate(reader.pages):
        text = (page.extract_text() or '').strip()
        if not text or _has_embedded_image(page):
            complete = False
            break
        pages.append((index + 1, text))
    result = ('Extracción textual del PDF. Las imágenes y gráficos no se han analizado; el original se conserva.\n' + '\n'.join(f'[Página {num}]\n{text}' for num, text in pages)) if complete else None
    sys.stdout.buffer.write(json.dumps(result,ensure_ascii=False).encode('utf-8'))

