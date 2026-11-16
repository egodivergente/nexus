"""Genera el icono de Windows (nexus.ico, ui/branding/nexus.ico y ui/favicon.ico) a partir del lobo de Nexus."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
mark = Image.open(root / 'ui' / 'branding' / 'nexus-mark.png').convert('RGBA')
canvas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
inner = round(256 * 0.94)  # 6 % de margen para que el lobo no toque el borde
mark = mark.resize((inner, inner), Image.LANCZOS)
canvas.paste(mark, ((256 - inner) // 2, (256 - inner) // 2), mark)
sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
for target in (root / 'nexus.ico', root / 'ui' / 'branding' / 'nexus.ico', root / 'ui' / 'favicon.ico'):
    canvas.save(target, format='ICO', sizes=sizes)
