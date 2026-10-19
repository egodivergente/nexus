"""Bounded PDF text extraction and visual page previews; originals remain intact."""
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

def _has_visual_content(page):
    try:
        resources = page.get('/Resources') or {}
        if hasattr(resources, 'get_object'):
            resources = resources.get_object()
        xobjects = resources.get('/XObject') or {}
        if hasattr(xobjects, 'get_object'):
            xobjects = xobjects.get_object()
        for obj in xobjects.values():
            resolved = obj.get_object() if hasattr(obj, 'get_object') else obj
            if isinstance(resolved, dict) and str(resolved.get('/Subtype') or '') in {'/Image','/Form'}:
                return True
    except Exception:
        return True
    try:
        content=page.get_contents()
        if content is not None and any(operator in {b'Do',b're',b'm',b'l',b'c',b'v',b'y',b'sh',b'INLINE IMAGE'} for _,operator in content.operations):
            return True
    except Exception:
        return True
    return False

def render_pdf(path):
    executable=Path(sys.executable)
    if executable.name.lower()=='pythonw.exe':executable=executable.with_name('python.exe')
    try:
        result=subprocess.run([str(executable),str(Path(__file__).resolve()),str(path),'--render'],capture_output=True,timeout=30,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    except subprocess.TimeoutExpired:
        raise ValueError('El PDF agotó el tiempo de preparación visual. El original se conserva.') from None
    if result.returncode:raise ValueError('No se pudo preparar el PDF visual. Comprueba las dependencias multimedia y el archivo.')
    return json.loads(result.stdout.decode('utf-8'))


def _render(path):
    import base64,io
    import pypdfium2 as pdfium
    pdf=pdfium.PdfDocument(path);pages=[]
    try:
        total=len(pdf)
        if not total:raise ValueError('PDF vacío.')
        count=min(total,8)
        for index in range(count):
            page=pdf[index]
            try:
                width,height=page.get_size();bitmap=page.render(scale=min(2.0,1280/max(width,height)))
                try:
                    image=bitmap.to_pil().convert('RGB')
                    try:
                        output=io.BytesIO();image.save(output,format='JPEG',quality=85)
                        pages.append({'page':index+1,'format':'jpeg','bytes':base64.b64encode(output.getvalue()).decode()})
                    finally:image.close()
                finally:bitmap.close()
            finally:page.close()
        return {'pages':pages,'total_pages':total,'complete':count==total,
                'coverage':f'Páginas 1-{count} de {total}. '+('Documento visual completo.' if count==total else 'LECTURA PARCIAL: las páginas restantes no se han enviado ni analizado.')}
    finally:pdf.close()


if __name__ == '__main__' and '--render' in sys.argv:
    sys.stdout.buffer.write(json.dumps(_render(sys.argv[1]),ensure_ascii=False).encode('utf-8'))
    raise SystemExit(0)


if __name__ == '__main__':
    from pypdf import PdfReader
    reader = PdfReader(sys.argv[1])
    total_pages = len(reader.pages)
    pages = []
    complete = total_pages > 0
    for index, page in enumerate(reader.pages):
        text = (page.extract_text() or '').strip()
        if not text or _has_visual_content(page):
            complete = False
            break
        pages.append((index + 1, text))
    result = ('Extracción textual del PDF. Las imágenes y gráficos no se han analizado; el original se conserva.\n' + '\n'.join(f'[Página {num}]\n{text}' for num, text in pages)) if complete else None
    sys.stdout.buffer.write(json.dumps(result,ensure_ascii=False).encode('utf-8'))

