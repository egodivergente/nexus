"""Prepare supplied transparent Nexus artwork for UI and Windows icons.

Trim transparent margins and resample for icon sizes without redrawing artwork.
"""
import argparse
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BRANDING = ROOT / 'ui' / 'branding'


def prepare(source):
    image = Image.open(source).convert('RGBA')
    alpha = image.getchannel('A')
    bounds = alpha.getbbox()
    if not bounds:
        raise ValueError('The supplied logo is empty.')
    rows = [y for y in range(bounds[1], bounds[3]) if alpha.crop((0, y, image.width, y + 1)).getbbox()]
    gaps = [(right - left, left + 1) for left, right in zip(rows, rows[1:]) if right - left > 2]
    if not gaps:
        raise ValueError('No transparent separation between symbol and wordmark.')
    symbol = image.crop((0, bounds[1], image.width, max(gaps)[1]))
    symbol = symbol.crop(symbol.getchannel('A').getbbox())
    BRANDING.mkdir(parents=True, exist_ok=True)
    image.save(BRANDING / 'nexus-source.png')
    image.crop(bounds).save(BRANDING / 'nexus-logo.png', optimize=True)
    symbol.save(BRANDING / 'nexus-mark.png', optimize=True)
    canvas = Image.new('RGBA', (512, 512))
    icon = symbol.copy()
    icon.thumbnail((448, 448), Image.Resampling.LANCZOS)
    canvas.alpha_composite(icon, ((512 - icon.width) // 2, (512 - icon.height) // 2))
    sizes = [(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)]
    for destination in [ROOT / 'nexus.ico', ROOT / 'ui' / 'favicon.ico', BRANDING / 'nexus.ico']:
        canvas.save(destination, format='ICO', sizes=sizes)
    print('Nexus logo and multi-size Windows icons prepared.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', default=str(BRANDING / 'nexus-source.png'))
    prepare(Path(parser.parse_args().source))
