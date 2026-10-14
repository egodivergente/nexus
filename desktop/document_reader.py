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

if __name__ == '__main__':
    from pypdf import PdfReader
    reader = PdfReader(sys.argv[1])
    total_pages = len(reader.pages)
    pages = []
    non_empty = 0
    for index, page in enumerate(reader.pages):
        text = (page.extract_text() or '').strip()
        if text:
            non_empty += 1
            pages.append((index + 1, text))
    usable = non_empty > 0 and (non_empty == total_pages or (total_pages > 2 and non_empty * 2 >= total_pages))
    result = ('Extracción textual del PDF. Las imágenes y gráficos no se han analizado; el original se conserva.\n' + '\n'.join(f'[Página {num}]\n{text}' for num, text in pages)) if usable else None
    sys.stdout.buffer.write(json.dumps(result,ensure_ascii=False).encode('utf-8'))
