"""Render the original Android Nexus mark as a Windows multi-size icon."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
image = Image.new('RGBA', (512, 512), '#090b10')
draw = ImageDraw.Draw(image)
scale = 512 / 108
point = lambda x, y: (round(x * scale), round(y * scale))
draw.polygon([point(0, 0), point(108, 108), point(108, 0)], fill='#151a26')
vertices = [point(54, 24), point(80, 39), point(80, 69),
            point(54, 84), point(28, 69), point(28, 39)]
draw.line(vertices + vertices[:1], fill='#00e5ff', width=17, joint='curve')
center = point(54, 54)
for x, y in (vertices[0], vertices[2], vertices[4]):
    draw.line([x, y, *center], fill='#7c4dff', width=12)
for x, y in (vertices[1], vertices[3], vertices[5]):
    draw.line([x, y, *center], fill='#00e5ff', width=9)
for x, y in vertices:
    draw.ellipse((x - 14, y - 14, x + 14, y + 14), fill='#7c4dff')
x, y = center
draw.ellipse((x - 24, y - 24, x + 24, y + 24), fill='#00e5ff')
image.save(root / 'nexus.ico', format='ICO', sizes=[(16, 16), (24, 24), (32, 32),
                                                   (48, 48), (64, 64), (128, 128), (256, 256)])
