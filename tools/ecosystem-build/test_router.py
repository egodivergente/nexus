"""Observable routing, transfer and resource-protection contracts."""
import copy
import io
import json
import tempfile
import tarfile
import unittest
from unittest.mock import patch
from pathlib import Path
from router import snapshot, decision, validate_recipe, mobile_executor, status
from worker import compatible, prepare, digest, save
from probe import cap_android_ram, linux_metrics


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

    def test_custom_aapt2_name_rejected_before_gradle(self):
        node={**self.node,"env":{"AAPT2":"/isolated/aapt2-arm64"}}
        self.assertTrue(compatible(node,self.metrics,self.recipe))
        node["env"]["AAPT2"]="/isolated/bin/aapt2"
        self.assertEqual(compatible(node,self.metrics,self.recipe),[])

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

    def test_native_linux_vm_keeps_physical_phone_guards(self):
        node={**self.node,"device_kind":"android","expected_model":"Pixel 8 Pro","expected_serial":"123","min_battery_percent":20}
        good={**self.metrics,"model":"Pixel 8 Pro","physical_serial":"123","battery_percent":80,"charging":True}
        self.assertEqual(compatible(node,good,self.recipe),[])
        for update in ({"physical_serial":"wrong"},{"battery_percent":5},{"battery_percent":None}):
            with self.subTest(update=update): self.assertTrue(compatible(node,{**good,**update},self.recipe))

    def test_guest_ram_cannot_hide_physical_phone_memory_pressure(self):
        result={**self.metrics,"ram_available_mb":4300}
        cap_android_ram(result,{"ram_available_mb":1300})
        self.assertEqual(result["vm_ram_available_mb"],4300)
        self.assertEqual(result["ram_available_mb"],1300)
        self.assertTrue(compatible(self.node,result,self.recipe))
        cap_android_ram(result,{"ram_available_mb":None})
        self.assertIsNone(result["ram_available_mb"])

    def test_native_vm_and_physical_reserves_are_both_enforced(self):
        node={**self.node,"device_kind":"android","expected_model":"Pixel","expected_serial":"123","reserve_ram_mb":512,"android_reserve_ram_mb":1024}
        good={**self.metrics,"ram_available_mb":1700,"android_ram_available_mb":4400,"model":"Pixel","physical_serial":"123","battery_percent":80,"charging":True}
        self.assertEqual(compatible(node,good,self.recipe),[])
        self.assertTrue(compatible(node,{**good,"android_ram_available_mb":2000},self.recipe))

    def test_android_proc_stat_denied_is_unknown_instead_of_crash(self):
        def restricted_read(path):
            return "MemAvailable: 5000000\nMemTotal: 12000000" if str(path)=="/proc/meminfo" else ""
        with patch("probe.read",side_effect=restricted_read),patch("probe.os.getloadavg",side_effect=OSError("denied"),create=True),patch("probe.time.sleep"):
            result=linux_metrics()
        self.assertIsNone(result["cpu_percent"])
        self.assertIsNone(result["load_per_cpu"])
        self.assertGreater(result["ram_available_mb"],4000)

    def test_adb_ssh_reuses_matching_forward_and_pinned_identity(self):
        node={**self.node,"executor":{"transport":"adb-ssh","ssh_user":"droid","android_ssh_port":2222,"host_key_alias":"pixel"}}
        metrics={"adb_bridge":"asus","adb_serial":"physical"}
        hosts=[{**self.node,"ssh":"user@verified-asus"}]
        with patch("router.adb_command",return_value="other tcp:123 tcp:2222\nphysical tcp:456 tcp:2222\n") as adb:
            result=mobile_executor(node,metrics,hosts)
        self.assertEqual(result["ssh_port"],456)
        self.assertEqual(result["proxy_jump"],"user@verified-asus")
        self.assertEqual(result["host_key_alias"],"pixel")
        self.assertEqual(adb.call_count,1)

    def test_adb_ssh_refuses_unverified_forward(self):
        node={**self.node,"executor":{"transport":"adb-ssh","ssh_user":"droid","android_ssh_port":2222}}
        with patch("router.adb_command",return_value=""):
            with self.assertRaises(ValueError): mobile_executor(node,{"adb_bridge":"asus","adb_serial":"physical"},[self.node])

    def test_status_reconnects_to_same_phone_without_dispatch(self):
        node={**self.node,"device_kind":"android","expected_serial":"123","state_root":"/state","ssh_port":100}
        fresh={**node,"ssh_port":200}
        with tempfile.TemporaryDirectory() as tmp:
            record={"job_id":"existing","node":node,"job_path":"/state/jobs/existing","local_path":tmp,"state":"dispatch_unknown"}
            with patch("router.ssh",side_effect=[RuntimeError("port changed"),b'{"state":"succeeded"}']) as ssh_call, patch("router.ecosystem",return_value={node["id"]:(fresh,{"background_executor":True})}):
                self.assertEqual(status(record)["state"],"succeeded")
            self.assertEqual(record["node"]["ssh_port"],200)
            self.assertEqual(ssh_call.call_count,2)
            self.assertTrue(all("cat /state/jobs/existing/status.json" == call.args[1] for call in ssh_call.call_args_list))

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
