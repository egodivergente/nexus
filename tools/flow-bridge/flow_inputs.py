"""Accept only owned image files and freeze their content before reservation."""
import hashlib
import os
from pathlib import Path
from launcher import ROOT

def freeze(paths):
    from PIL import Image
    roots = [(ROOT/'inputs').resolve()]
    if os.environ.get('LOCALAPPDATA'):
        roots.append((Path(os.environ['LOCALAPPDATA'])/'NexusDesktop/media').resolve())
    result=[]
    for value in paths:
        path=Path(value).resolve(strict=True)
        if not any(path.is_relative_to(root) for root in roots) or path.suffix.lower() not in {'.png','.jpg','.jpeg','.webp'}:
            raise ValueError('Imagen fuera del almacén privado de Nexus/Flow.')
        if path.stat().st_size>20*1024*1024:raise ValueError('La imagen supera 20 MB.')
        content=path.read_bytes()
        import io
        with Image.open(io.BytesIO(content)) as image:
            if image.format not in {'PNG','JPEG','WEBP'} or image.width*image.height>40_000_000:
                raise ValueError('Formato o dimensiones de imagen no admitidos.')
            image.verify()
        name=hashlib.sha256(content).hexdigest()+path.suffix.lower()
        destination=ROOT/'inputs'/name
        destination.parent.mkdir(parents=True,exist_ok=True)
        if destination.exists():
            if destination.read_bytes()!=content:raise ValueError('La imagen privada ha cambiado.')
        else:
            # Exclusive creation prevents another job replacing an input snapshot.
            try:
                with destination.open('xb') as output:output.write(content)
            except FileExistsError:
                if destination.read_bytes()!=content:raise ValueError('La imagen privada ha cambiado.')
        result.append(str(destination))
    return result

def prepare(params):
    result=dict(params)
    for key in ('initial_frame','end_frame'):
        if result.get(key):result[key]=freeze([result[key]])[0]
    if result.get('reference_images'):result['reference_images']=freeze(result['reference_images'])
    return result
