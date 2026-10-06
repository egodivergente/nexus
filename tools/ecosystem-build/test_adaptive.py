import json
import os
import tempfile
import time
import unittest
import subprocess
from pathlib import Path, PurePosixPath
from unittest.mock import patch
from history import signature, executor, workload, samples, estimate, build_statistics
from guard import policy, runtime_metrics, watch
from router import decision, relay_health, start_job
import test_router


class AdaptiveTests(unittest.TestCase):
    def setUp(self):
        base=test_router.RoutingTests(); base.setUp()
        self.node=base.node; self.metrics=base.metrics; self.recipe=base.recipe

    def record(self,node,seconds,mode="cold"):
        return {"node":node["id"],"executor":executor(node),"seconds":seconds,"cache_class":mode,
                "source_signature":"source","finished_at":time.time(),"job_id":node["id"],"timing":"build"}

    def test_measured_faster_dedicated_wins_lower_base_score(self):
        fast={**self.node,"id":"fast","base_score":0}
        records=[self.record(self.node,600),self.record(fast,100)]
        result=decision(self.recipe,{"asus":(self.node,self.metrics),"fast":(fast,self.metrics)},timings=records,source_signature="source")
        self.assertEqual(result["selected"],"fast")
        self.assertEqual(result["selection_basis"],"observed_build_times_with_live_load")

    def test_history_cannot_override_busy_node(self):
        fast={**self.node,"id":"fast","base_score":0}
        result=decision(self.recipe,{"asus":(self.node,self.metrics),"fast":(fast,{**self.metrics,"locked":True})},
                        timings=[self.record(self.node,600),self.record(fast,1)],source_signature="source")
        self.assertEqual(result["selected"],"asus")

    def test_cached_and_cold_are_not_compared_in_auto(self):
        fast={**self.node,"id":"fast","base_score":0}
        result=decision(self.recipe,{"asus":(self.node,self.metrics),"fast":(fast,self.metrics)},
                        timings=[self.record(self.node,600),self.record(fast,1,"cached")],source_signature="source")
        self.assertEqual(result["selection_basis"],"live_resource_policy")

    def test_unknown_sample_keeps_fallback_policy(self):
        fast={**self.node,"id":"fast","base_score":0}
        result=decision(self.recipe,{"asus":(self.node,self.metrics),"fast":(fast,self.metrics)},
                        timings=[self.record(self.node,600)],source_signature="source")
        self.assertEqual(result["selection_basis"],"live_resource_policy")

    def test_old_executor_and_cached_other_source_excluded(self):
        record=self.record(self.node,1,"cached")
        self.assertIsNone(estimate([record],{**self.node,"env":{"JAVA_HOME":"new"}},"source","cached")["estimate"])
        self.assertIsNone(estimate([record],self.node,"changed-source","cached")["estimate"])

    def test_gradle_observed_cache_classes(self):
        with tempfile.TemporaryDirectory() as tmp:
            log=Path(tmp)/"build.log"
            for text,mode in [("", "unknown"),("> Task :a\n> Task :b", "cold"),
                ("> Task :a FROM-CACHE\n> Task :b\n> Task :c\n", "partial"),
                ("> Task :a FROM-CACHE\n> Task :b UP-TO-DATE\n> Task :c NO-SOURCE\n", "cached")]:
                log.write_text(text)
                self.assertEqual(build_statistics(log,"android-gradle")["cache_class"],mode)

    def test_samples_ignore_failed_old_project_and_guard_pauses(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp); job=base/"jobs"/"one"; job.mkdir(parents=True)
            request={"source_path":str(base/"src"),"node":self.node,"recipe":self.recipe}
            result={"state":"succeeded","node":"asus","started_at":time.time()-10,"finished_at":time.time(),"build_seconds":5,
                    "build_statistics":{"cache_class":"cold"},"protection_events":[{"action":"run"}]}
            (job/"request.json").write_text(json.dumps(request)); (job/"source-manifest.json").write_text('{}')
            (job/"status.json").write_text(json.dumps(result))
            self.assertEqual(len(samples(base,base/"src",self.recipe)),1)
            self.assertEqual(samples(base,base/"other",self.recipe),[])
            for change in ({"state":"failed"},{"finished_at":time.time()-31*86400},{"protection_events":[{"action":"pause"}]}):
                (job/"status.json").write_text(json.dumps({**result,**change}))
                self.assertEqual(samples(base,base/"src",self.recipe),[])

    def test_variant_changes_are_separate_workloads(self):
        self.assertNotEqual(workload(self.recipe),workload({**self.recipe,"tasks":[":app:assembleRelease"]}))
        self.assertEqual(workload(self.recipe),workload({**self.recipe,"max_heap_mb":900,"name":"different label"}))

    def test_mobile_use_and_stale_health_pause(self):
        node={"device_kind":"android","reserve_ram_mb":1024,"min_battery_percent":20,"max_temperature_c":40}
        good={"ram_available_mb":4000,"mobile_health_valid":True,"battery_percent":80,"charging":True,"temperature_c":30,"screen_on":False}
        with patch("guard.os.name","posix"):
            self.assertEqual(policy(node,good)[0],"run")
            for change in ({"screen_on":True},{"screen_on":None},{"mobile_health_valid":False},{"battery_percent":5},{"temperature_c":41}):
                self.assertEqual(policy(node,{**good,**change})[0],"pause")
            self.assertEqual(policy(node,{**good,"temperature_c":39},paused=True)[0],"pause")
            self.assertEqual(policy(node,{**good,"ram_available_mb":500})[0],"abort")

    def test_windows_user_activity_throttles_and_reserve_aborts(self):
        with patch("guard.os.name","nt"):
            self.assertEqual(policy({"reserve_ram_mb":3072},{"ram_available_mb":5000,"interactive":True})[0],"throttle")
            self.assertEqual(policy({"reserve_ram_mb":3072},{"ram_available_mb":5000,"interactive":False})[0],"run")
            self.assertEqual(policy({"reserve_ram_mb":3072},{"ram_available_mb":2000,"interactive":True})[0],"abort")

    def test_mobile_health_requires_same_job_identity_and_freshness(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"health.json"
            node={"device_kind":"android","device_metrics_file":str(path),"expected_serial":"pixel","expected_model":"Pixel"}
            good={"job_id":"owned","physical_serial":"pixel","model":"Pixel","measured_at":time.time(),"ram_available_mb":4000}
            for change,valid in [({},True),({"job_id":"other"},False),({"physical_serial":"other"},False),({"measured_at":time.time()-61},False)]:
                path.write_text(json.dumps({**good,**change}))
                with patch("guard.windows_metrics",return_value={"ram_available_mb":5000}),patch("guard.linux_metrics",return_value={"ram_available_mb":5000}):
                    self.assertEqual(runtime_metrics(node,"owned")["mobile_health_valid"],valid)

    def test_watch_pause_resume_then_memory_abort_only_owns_build(self):
        class Process:
            done=False
            def poll(self): return 125 if self.done else None
            def wait(self,timeout=None):
                if self.done: return 125
                raise subprocess.TimeoutExpired("owned",timeout)
        class Build:
            process=Process(); paused=False; controls=[]
            def pause(self,value): self.paused=value; self.controls.append(("pause",value))
            def abort(self): self.process.done=True; self.controls.append(("abort",True))
            def close(self): self.controls.append(("close",True))
        build=Build()
        node={"device_kind":"android","reserve_ram_mb":1024}
        good={"ram_available_mb":4000,"mobile_health_valid":True,"screen_on":False,"battery_percent":80,"charging":True,"temperature_c":25}
        sequence=[good,{**good,"screen_on":True},good,{**good,"ram_available_mb":500},{**good,"ram_available_mb":500}]
        with tempfile.TemporaryDirectory() as tmp,patch("guard.os.name","posix"),patch("guard.runtime_metrics",side_effect=sequence):
            result={}
            self.assertEqual(watch(build,node,PurePosixPath(tmp),result,lambda *a:None),125)
        self.assertIn(("pause",True),build.controls)
        self.assertIn(("pause",False),build.controls)
        self.assertEqual(build.controls.count(("abort",True)),1)
        self.assertIn("RAM",result["protection_abort"])

    @unittest.skipUnless(os.name=="nt","Windows API only")
    def test_empty_windows_job_cpu_rates_confirmed_by_kernel(self):
        # Empty job: verify real API without scheduling workload on an occupied PC.
        import ctypes
        from ctypes import wintypes
        from guard import set_windows_cpu
        k=ctypes.WinDLL("kernel32",use_last_error=True)
        k.CreateJobObjectW.argtypes=[ctypes.c_void_p,wintypes.LPCWSTR]; k.CreateJobObjectW.restype=wintypes.HANDLE
        k.CloseHandle.argtypes=[wintypes.HANDLE]
        handle=k.CreateJobObjectW(None,None)
        self.assertTrue(handle)
        try:
            set_windows_cpu(handle,5)
            set_windows_cpu(handle,15)
        finally: k.CloseHandle(handle)

    def test_watch_windows_cpu_changes_are_verified_before_continuing(self):
        class Process:
            polls=0
            def poll(self):
                self.polls+=1
                return None if self.polls<=4 else 0
            def wait(self,timeout=None):
                if timeout is None: return 0
                raise subprocess.TimeoutExpired("owned",timeout)
        class Build:
            process=Process(); paused=False
            def close(self): pass
            def abort(self): raise AssertionError("should not abort")
        good={"ram_available_mb":5000,"interactive":False,"cpu_percent":10}
        with tempfile.TemporaryDirectory() as tmp:
            job=Path(tmp)
            with patch("guard.os.name","nt"),patch("guard.runtime_metrics",side_effect=[{**good,"interactive":True},good]),patch("guard.set_windows_cpu") as cap:
                result={}
                self.assertEqual(watch(Build(),{"reserve_ram_mb":3072,"cpu_cap_percent":15},job,result,lambda *a:None,123),0)
                self.assertEqual([c.args for c in cap.call_args_list],[(123,5),(123,15)])
                self.assertEqual(result["active_cpu_cap_percent"],15)

    def test_relay_is_job_scoped_atomic_and_stops_on_completion(self):
        node={"id":"pixel","device_kind":"android","device_metrics_file":"/private/jobs/owned/device-health.json"}
        host={"id":"pc","transport":"local"}
        with tempfile.TemporaryDirectory() as tmp:
            record={"job_id":"owned","node":node,"local_path":tmp}
            health={"physical_serial":"pixel","model":"Pixel","measured_at":time.time()}
            with patch("router.config",return_value=[host]),patch("router.status",side_effect=[{"state":"running"},{"state":"succeeded"}]),patch("router.adb_inventory",return_value={"pixel":health}),patch("router.mobile_executor",return_value={"transport":"ssh"}),patch("router.scp") as copy,patch("router.ssh") as remote,patch("router.time.sleep"):
                relay_health(record)
            self.assertEqual(copy.call_count,1)
            self.assertEqual(copy.call_args.args[2],node["device_metrics_file"]+".next")
            self.assertEqual(remote.call_args.args[1],"mv /private/jobs/owned/device-health.json.next /private/jobs/owned/device-health.json")
            self.assertEqual(json.loads((Path(tmp)/"relay-health.json").read_text())["job_id"],"owned")

    def test_relay_exits_for_disappeared_worker_without_touching_adb(self):
        with tempfile.TemporaryDirectory() as tmp,patch("router.config",return_value=[]),patch("router.status",return_value={"state":"unknown","reason":"worker desaparecido sin resultado"}),patch("router.adb_inventory") as adb:
            relay_health({"job_id":"owned","node":{},"local_path":tmp})
            adb.assert_not_called()

    def test_ambiguous_dispatch_still_monitors_same_mobile_without_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp); source=base/"src"; source.mkdir(); (source/"one.py").write_text("pass")
            node={"id":"pixel","transport":"ssh","device_kind":"android","state_root":"/private","device_metrics_file":"/old"}
            bridge={"id":"pc","transport":"local"}
            result={"pixel":(node,{"adb_bridge":"pc"}),"pc":(bridge,{})}
            with patch("router.STATE",base/"state"),patch("router.ssh",side_effect=[b"",RuntimeError("unknown dispatch")]) as ssh,patch("router.scp"),patch("router.adb_inventory",return_value={"pixel":{"physical_serial":"p","model":"Pixel"}}),patch("router.launch_relay") as relay:
                record=start_job(source,{"source_includes":["one.py"]},{"selected":"pixel"},result)
                self.assertEqual(record["state"],"dispatch_unknown")
                self.assertEqual(ssh.call_count,2)
                relay.assert_called_once_with(record)
                self.assertIn(record["job_id"],record["node"]["device_metrics_file"])


if __name__=="__main__": unittest.main()
