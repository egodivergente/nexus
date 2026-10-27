"""Evaluate an APK Explorer ZIP with an independent metric (not the engine's own).

Unique = clusters of screenshots whose 64-bit dHash differ by > 6 bits (visual)
Usage: python eval_zip.py <zip> [--sheet out.png]
"""
import io, json, sys, zipfile
from PIL import Image, ImageDraw


def dhash(img, n=8):
    g = img.convert("L").resize((n + 1, n), Image.LANCZOS)
    px = list(g.getdata())
    bits = 0
    for y in range(n):
        for x in range(n):
            bits = (bits << 1) | (px[y * (n + 1) + x] > px[y * (n + 1) + x + 1])
    return bits


def ham(a, b):
    return bin(a ^ b).count("1")


def main():
    path = sys.argv[1]
    sheet = sys.argv[sys.argv.index("--sheet") + 1] if "--sheet" in sys.argv else None
    z = zipfile.ZipFile(path)
    names = sorted(n for n in z.namelist() if n.lower().endswith(".png") and "/" not in n or n.lower().endswith(".png"))
    imgs = [(n, Image.open(io.BytesIO(z.read(n))).convert("RGB")) for n in names]
    hashes = [dhash(i) for _, i in imgs]
    clusters = []
    for idx, h in enumerate(hashes):
        for c in clusters:
            if ham(c[0], h) <= 6:
                c[1].append(idx)
                break
        else:
            clusters.append([h, [idx]])
    total = len(imgs)
    unique = len(clusters)
    meta = json.loads(z.read("agent.json")) if "agent.json" in z.namelist() else {}
    out = {
        "zip": path,
        "total_captures": total,
        "unique_visual": unique,
        "visual_duplicates": total - unique,
        "engine_status": meta.get("status"),
        "engine_dupes_skipped": meta.get("duplicate_frames_skipped", meta.get("duplicates_skipped")),
        "bytes": sum(i.file_size for i in z.infolist() if i.filename.endswith(".png")),
    }
    print(json.dumps(out, ensure_ascii=False, indent=1))
    for c in clusters:
        if len(c[1]) > 1:
            print("dup cluster:", [names[i] for i in c[1]])
    if sheet:
        w, h = 180, 400
        cols = min(9, max(1, total))
        rows = (total + cols - 1) // cols
        im = Image.new("RGB", (cols * w, rows * (h + 16)), "white")
        d = ImageDraw.Draw(im)
        for k, (n, i) in enumerate(imgs):
            t = i.copy()
            t.thumbnail((w - 4, h))
            x, y = (k % cols) * w, (k // cols) * (h + 16)
            im.paste(t, (x + 2, y + 16))
            d.text((x + 3, y + 2), n[-22:], fill="black")
        im.save(sheet)


main()

