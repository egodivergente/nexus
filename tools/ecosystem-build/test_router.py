"""Observable routing, transfer and resource-protection contracts."""
import copy
import io
import json
import tempfile
import tarfile
import unittest
from pathlib import Path
from router import snapshot, decision, validate_recipe
from worker import compatible, prepare, digest, save


class RoutingTests(unittest.TestCase):
    def setUp(self):
        self.node={"id":"asus","label":"ASUS","transport":"ssh","dedicated":True,"base_score":80,"reserve_ram_mb":512}
        self.metrics={"online":True,"os":"linux","arch":"x86_64","ram_available_mb":2400,
                      "disk_free_mb":10000,"cpu_percent":10,"io_pressure":0,"memory_pressure":0,
                      "temperature_c":40,"tools":{"java":"java","gcc":"gcc"},"java_major":17,
                      "android_platforms":["android-35"],"aapt2_ok":True,"process_probe_ok":True,
                      "active_build_pids":[],"locked":False}
        self.recipe={"kind":"android-gradle","host_os":["linux","android"],"java_major":17,
                     "compile_sdk":35,"tasks":[":app:assembleDebug"],"source_includes":["app"],"min_ram_mb":1100}

    def test_available_dedicated_beats_interactive_pc(self):
        pc={**self.node,"id":"pc","dedicated":False,"base_score":20}
        result=decision(self.recipe,{"asus":(self.node,self.metrics),"pc":(pc,{**self.metrics,"interactive":True})})
        self.assertEqual(result["selected"],"asus")

    def test_busy_node_is_never_forced_by_preference(self):
        result=decision(self.recipe,{"asus":(self.node,{**self.metrics,"locked":True})},"asus")
        self.assertIsNone(result["selected"])

    def test_no_spare_ram_means_wait_instead_of_swap(self):
        result=decision(self.recipe,{"asus":(self.node,{**self.metrics,"ram_available_mb":1300})})
        self.assertEqual(result["state"],"waiting")

    def test_windows_exe_never_sent_to_linux_without_cross_compiler(self):
        recipe={"kind":"command","host_os":["windows"],"requires_tools":["csc"]}
        self.assertTrue(compatible(self.node,self.metrics,recipe))

    def test_pressure_and_cpu_make_node_ineligible(self):
        for update in ({"cpu_percent":95},{"memory_pressure":25},{"io_pressure":60},
                       {"temperature_c":95},{"active_build_pids":[123]},{"process_probe_ok":False}):
            with self.subTest(update=update):
                self.assertTrue(compatible(self.node,{**self.metrics,**update},self.recipe))

    def test_sdk_and_jdk_and_native_aapt_required(self):
        for update in ({"java_major":21},{"android_platforms":[]},{"aapt2_ok":False}):
            with self.subTest(update=update):
                self.assertTrue(compatible(self.node,{**self.metrics,**update},self.recipe))

    def test_phone_adb_alone_is_not_an_executor(self):
        phone={**self.node,"id":"pixel","transport":"android","base_score":110}
        result=decision(self.recipe,{"pixel":(phone,{**self.metrics,"os":"android","battery_percent":90,"charging":True})})
        self.assertIsNone(result["selected"])

    def test_s25_in_use_or_hot_or_unmeasured_cannot_run(self):
        node={**self.node,"require_screen_off":True,"max_temperature_c":38,"expected_model":"phone","expected_serial":"123"}
        good={**self.metrics,"os":"android","model":"phone","physical_serial":"123","battery_percent":90,"charging":True,"screen_on":False,"temperature_c":32}
        self.assertEqual(compatible(node,good,self.recipe),[])
        for update in ({"screen_on":True},{"screen_on":None},{"temperature_c":41},{"battery_percent":None},{"battery_percent":2}):
            with self.subTest(update=update): self.assertTrue(compatible(node,{**good,**update},self.recipe))

    def test_task_name_cannot_inject_gradle_options(self):
        self.recipe["tasks"]=["-Ievil.gradle"]
        with self.assertRaises(ValueError): validate_recipe(self.recipe)

    def test_unknown_metrics_do_not_qualify_or_crash_routing(self):
        result=decision(self.recipe,{"asus":(self.node,{**self.metrics,"ram_available_mb":None,"cpu_percent":None,"disk_free_mb":None})})
        self.assertEqual(result["state"],"waiting")


class TransferTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.base=Path(self.tmp.name); self.source=self.base/"src"; self.source.mkdir()
        self.job=self.base/"job"; self.job.mkdir()
    def tearDown(self): self.tmp.cleanup()

    def test_excludes_credentials_old_outputs_and_windows_paths(self):
        for name in ("app/Main.kt","app/build/old.apk","app/.env","app/signing.jks","app/local.properties"):
            p=self.source/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text("data")
        sha=snapshot(self.source,["app"],self.job)
        manifest=json.loads((self.job/"source-manifest.json").read_text())
        self.assertEqual(set(manifest),{"app/Main.kt"})
        out=prepare(self.job,{"source_archive_sha256":sha})
        self.assertEqual((out/"app/Main.kt").read_text(),"data")

    def test_source_hash_rejects_tampered_transfer(self):
        (self.source/"file").write_text("one")
        sha=snapshot(self.source,["file"],self.job)
        with open(self.job/"source.tar","ab") as f: f.write(b"tampered")
        with self.assertRaises(ValueError): prepare(self.job,{"source_archive_sha256":sha})

    def test_refuses_archive_path_traversal(self):
        with tarfile.open(self.job/"source.tar","w") as archive:
            info=tarfile.TarInfo("../escape"); info.size=1; archive.addfile(info,io.BytesIO(b"x"))
        save(self.job/"source-manifest.json",{"../escape":"irrelevant"})
        with self.assertRaises(ValueError): prepare(self.job,{"source_archive_sha256":digest(self.job/"source.tar")})
        self.assertFalse((self.base/"escape").exists())

    def test_source_include_cannot_leave_workspace(self):
        (self.base/"outside").write_text("secret")
        with self.assertRaises(ValueError): snapshot(self.source,["../outside"],self.job)


if __name__=="__main__": unittest.main()
