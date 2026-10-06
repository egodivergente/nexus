import unittest
from worker import resource_node, compatible


class SigningProfileTest(unittest.TestCase):
    def setUp(self):
        self.node = {"transport": "local", "reserve_ram_mb": 3072, "cpu_cap_percent": 15}
        self.recipe = {
            "resource_profile": "apk-sign", "kind": "command", "host_os": ["windows"],
            "low_impact": True, "max_memory_mb": 512, "min_ram_mb": 256,
            "max_cpu_percent": 90,
            "command": ["java", "-Xmx128m", "-jar", "C:/sdk/apksigner.jar", "sign", "input.apk"],
        }

    def test_signing_limits_and_original_unchanged(self):
        effective = resource_node(self.node, self.recipe)
        self.assertEqual(effective["reserve_ram_mb"], 1024)
        self.assertEqual(effective["cpu_cap_percent"], 5)
        self.assertEqual(self.node["reserve_ram_mb"], 3072)

    def test_normal_build_unchanged(self):
        self.assertIs(resource_node(self.node, {"kind": "android-gradle"}), self.node)

    def test_unbounded_or_other_command_rejected(self):
        for override in ({"max_memory_mb": 1024}, {"command": ["gradle", "assemble"]},
                         {"low_impact": False}, {"min_ram_mb": 0}):
            with self.subTest(override=override), self.assertRaises(ValueError):
                resource_node(self.node, dict(self.recipe, **override))

    def test_admission_still_protects_resources_and_locks(self):
        metrics = dict(online=True, os="windows", process_probe_ok=True,
                       ram_available_mb=1600, cpu_percent=80, disk_free_mb=5000)
        self.assertEqual(compatible(self.node, metrics, self.recipe), [])
        self.assertTrue(compatible(self.node, dict(metrics, ram_available_mb=1200), self.recipe))
        self.assertTrue(compatible(self.node, dict(metrics, cpu_percent=95), self.recipe))
        self.assertTrue(compatible(self.node, dict(metrics, locked=True), self.recipe))


if __name__ == "__main__":
    unittest.main()
