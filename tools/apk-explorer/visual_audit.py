"""Compare native Explorer ZIPs by recorded route, never by image order.

Usage: python visual_audit.py BEFORE.zip AFTER.zip --out audit-folder
Requires Pillow. Outputs local evidence, not an automatic aesthetic verdict.
"""
import argparse
import html
import io
import json
from pathlib import Path
import re
import zipfile
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageStat


class Evidence:
    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)
        try:
            if sum(e.file_size for e in self.zip.infolist()) > 60 * 1024 * 1024:
                raise ValueError("ZIP exceeds 60 MiB")
            self.report = json.loads(self.read("agent.json"))
            if self.report.get("schema") != 2:
                raise ValueError("Use captures from Explorer 0.2.0 or newer")
        except Exception:
            self.zip.close()
            raise

    def read(self, name):
        entry = self.zip.getinfo(name)
        if entry.file_size > 15 * 1024 * 1024:
            raise ValueError("Evidence entry too large")
        return self.zip.read(entry)

    def image(self, state):
        with Image.open(io.BytesIO(self.read(state["image"]))) as im:
            if im.width * im.height > 10_000_000:
                raise ValueError("Image too large")
            return im.convert("RGB")


def hints(xml, density):
    """Accessibility geometry hints; require visual review, not confirmed defects."""
    root = ET.fromstring(xml)
    found = []
    for n in root.iter("node"):
        if n.get("clickable") != "true":
            continue
        values = [int(x) for x in re.findall(r"-?\d+", n.get("bounds", ""))]
        if len(values) != 4:
            continue
        x, y, right, bottom = values
        width, height = (right-x)/density, (bottom-y)/density
        label = " ".join(filter(None, (n.get("text"), n.get("content-desc"))))
        if 0 < min(width, height) < 48:
            found.append({"kind": "review_small_target", "label": label,
                          "resource_id": n.get("resource-id"), "bounds": values,
                          "width_dp": round(width, 1), "height_dp": round(height, 1)})
    return found


def compare(before, after, destination):
    destination = Path(destination)
    # Avoid mixing old screenshots with a new report.
    destination.mkdir(parents=True, exist_ok=False)
    a = Evidence(before)
    try:
        b = Evidence(after)
        try:
            return _compare(a, b, destination)
        finally:
            b.zip.close()
    finally:
        a.zip.close()


def _compare(a, b, out):
    ar, br = a.report, b.report
    if ar["package"] != br["package"]:
        raise ValueError("Cannot compare different applications")
    if not br.get("replay"):
        raise ValueError("AFTER must be a replay of the saved reference")
    steps_a, steps_b = ar["route"]["steps"], br["route"]["steps"]
    environment_equal = ar.get("environment") == br.get("environment")
    report = {"schema": 1, "package": ar["package"], "environment_equal": environment_equal,
              "before_version": ar.get("app_version"), "after_version": br.get("app_version"),
              "replay_status": br.get("status"), "coverage_complete": False,
              "limitations": ["Actions are replayed; app data and remote content are not reset.",
                              "Pixel changes do not establish whether design improved.",
                              "Geometry hints require manual review; expanded touch areas may differ."],
              "pairs": [], "missing_steps": []}
    after_states = {s["route_step"]: s for s in br["states"]}
    cards = []
    for state in ar["states"]:
        step = state["route_step"]
        target = after_states.get(step)
        if target is None or steps_a[:step] != steps_b[:step]:
            report["missing_steps"].append(step)
            continue
        left, right = a.image(state), b.image(target)
        prefix = f"step-{step:03d}"
        left.save(out / f"{prefix}-before.png")
        right.save(out / f"{prefix}-after.png")
        pair = {"route_step": step, "before": state["image"], "after": target["image"],
                "same_dimensions": left.size == right.size,
                "comparable_environment": environment_equal,
                "review_hints": hints(b.read(target["xml"]), br["environment"]["density_dpi"]/160)}
        diff_html = ""
        if left.size == right.size:
            diff = ImageChops.difference(left, right)
            pair["mean_channel_delta"] = round(sum(ImageStat.Stat(diff).mean)/3, 4)
            mask = ImageChops.lighter(ImageChops.lighter(diff.getchannel(0), diff.getchannel(1)), diff.getchannel(2))
            hist = mask.histogram()
            pair["changed_pixel_percent"] = round(100*sum(hist[17:])/(left.width*left.height), 4)
            diff.save(out / f"{prefix}-diff.png")
            diff_html = f'<figure><figcaption>Diferencias</figcaption><img src="{prefix}-diff.png"></figure>'
        report["pairs"].append(pair)
        cards.append(f'<article><h2>Paso {step}</h2><div class="row"><figure><figcaption>Antes</figcaption><img src="{prefix}-before.png"></figure><figure><figcaption>Después</figcaption><img src="{prefix}-after.png"></figure>{diff_html}</div></article>')
    report["comparison_complete"] = (not report["missing_steps"] and bool(report["pairs"])
                                       and br.get("status") == "replay_complete"
                                       and steps_a == steps_b and environment_equal
                                       and all(p["same_dimensions"] for p in report["pairs"]))
    (out / "comparison.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (out / "index.html").write_text('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Auditoría visual</title><style>body{background:#10232d;color:#eee;font:16px system-ui;padding:24px}.row{display:flex;gap:12px;flex-wrap:wrap}figure{margin:0;max-width:30%}img{width:100%}figcaption{padding:12px}article{margin:32px 0}</style><h1>'+html.escape(ar["package"])+"</h1><p>Comparación de recorridos. Revisa contenido dinámico y estado inicial antes de atribuir diferencias al diseño.</p>"+f'<p>Comparación completa: {report["comparison_complete"]}; pasos ausentes: {report["missing_steps"]}</p>'+"".join(cards), encoding="utf-8")
    (out / "AUDIT.md").write_text("# Auditoría para el agente\n\n"
        "1. Lee comparison.json: comprueba entorno, pasos ausentes y estado del replay.\n"
        "2. Revisa los pares antes/después. Distingue cambios de contenido de cambios de diseño.\n"
        "3. Evalúa jerarquía, alineación, ritmo/espaciado, tipografía, contraste visual, consistencia, estados y accesibilidad.\n"
        "4. Documenta cada hallazgo con paso, captura, elemento, impacto y confianza. Los review_hints no son fallos confirmados.\n"
        "5. Inspecciona el código para proponer cambios concretos; no inventes rutas ni atribuyas código por su aspecto.\n"
        "6. Después de editar y compilar, repite la referencia y verifica los mismos pares.\n\n"
        "No asignar puntuación estética automática ni afirmar cobertura total. No se restauran datos de la app.\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    report = compare(args.before, args.after, args.out)
    print(json.dumps({"report": str(args.out.resolve()), "pairs": len(report["pairs"]),
                      "comparison_complete": report["comparison_complete"]}))


if __name__ == "__main__":
    main()
