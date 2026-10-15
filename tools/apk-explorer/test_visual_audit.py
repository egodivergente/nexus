import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from PIL import Image
from visual_audit import compare, hints


class VisualAuditTests(unittest.TestCase):
    def evidence(self, folder, name, *, replay=False, color="white", steps=None, status=None, environment=None):
        path = folder / (name + ".zip")
        image = io.BytesIO()
        Image.new("RGB", (40, 80), color).save(image, "PNG")
        steps = [{"kind": "click", "label": "home"}] if steps is None else steps
        report = {"schema": 2, "package": "test.app", "replay": replay,
                  "status": status or ("replay_complete" if replay else "finished_partial"),
                  "environment": environment or {"density_dpi": 320}, "route": {"steps": steps},
                  "states": [{"route_step": i, "image": f"{i}.png", "xml": f"{i}.xml"} for i in range(len(steps)+1)]}
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("agent.json", json.dumps(report))
            for s in report["states"]:
                z.writestr(s["image"], image.getvalue())
                z.writestr(s["xml"], '<hierarchy><node clickable="true" bounds="[0,0][40,60]" text="A"/></hierarchy>')
        return path

    def test_identical_replay(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            r = compare(self.evidence(p, "a"), self.evidence(p, "b", replay=True), p/"out")
            self.assertTrue(r["comparison_complete"])
            self.assertEqual(r["pairs"][0]["changed_pixel_percent"], 0)

    def test_changed_pixels(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            r = compare(self.evidence(p, "a"), self.evidence(p, "b", replay=True, color="black"), p/"out")
            self.assertEqual(r["pairs"][0]["changed_pixel_percent"], 100)

    def test_divergent_route_not_paired(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            r = compare(self.evidence(p, "a"), self.evidence(p, "b", replay=True, steps=[{"kind": "back"}]), p/"out")
            self.assertFalse(r["comparison_complete"])
            self.assertEqual(r["missing_steps"], [1])

    def test_failed_replay_and_environment_not_complete(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            r = compare(self.evidence(p, "a"), self.evidence(p, "b", replay=True, status="error", environment={"density_dpi": 160}), p/"out")
            self.assertFalse(r["comparison_complete"])
            self.assertFalse(r["environment_equal"])

    def test_requires_replay(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            with self.assertRaisesRegex(ValueError, "replay"):
                compare(self.evidence(p, "a"), self.evidence(p, "b"), p/"out")

    def test_geometry_hint_uses_density(self):
        result = hints('<node clickable="true" bounds="[0,0][80,96]"/>', 2)
        self.assertEqual(result[0]["width_dp"], 40)
        self.assertEqual(hints('<node clickable="true" bounds="[0,0][96,96]"/>', 2), [])


if __name__ == "__main__":
    unittest.main()
