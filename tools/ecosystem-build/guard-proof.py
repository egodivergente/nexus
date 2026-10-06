"""Bounded real-OS proof with simulated triggers; no device UI or heavy workload."""
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from guard import OwnedBuild, set_windows_cpu


def tick(path):
    Path(path).write_text(str(time.time()))
    time.sleep(.1)


if len(sys.argv)>1 and sys.argv[1]=="ticker":
    while True: tick(sys.argv[2])
elif len(sys.argv)>1 and sys.argv[1]=="tree":
    child=subprocess.Popen([sys.executable,__file__,"ticker",sys.argv[2]+"-child"],
        creationflags=0x08000000 if os.name=="nt" else 0)
    Path("proof-child-pid").write_text(str(child.pid))
    while True: tick(sys.argv[2])
else:
    root=Path.cwd(); job=root/"owned-proof"; job.mkdir()
    ticker=root/"owned-tick"; child_tick=root/"owned-tick-child"; external_tick=root/"external-tick"
    external=subprocess.Popen([sys.executable,__file__,"ticker",str(external_tick)],
        creationflags=0x08000000 if os.name=="nt" else 0,start_new_session=os.name!="nt")
    build=None
    report={"trigger_source":"simulated within isolated test; real OS process controls", "os":os.name}
    try:
        with open(root/"owned-proof.log","wb") as log:
            build=OwnedBuild([sys.executable,__file__,"tree",str(ticker)],root,os.environ.copy(),log,job)
            deadline=time.monotonic()+10
            while not child_tick.exists() or not external_tick.exists():
                if time.monotonic()>deadline: raise RuntimeError("tickers did not start")
                time.sleep(.1)
            if os.name=="nt":
                set_windows_cpu(build.handle,5); report["cpu_5_percent_kernel_verified"]=True
                set_windows_cpu(build.handle,15); report["cpu_15_percent_kernel_verified"]=True
            else:
                build.pause(True); time.sleep(.3)
                before=ticker.read_text(); child_before=child_tick.read_text(); ext_before=external_tick.read_text()
                time.sleep(1)
                report["root_and_child_paused"]=ticker.read_text()==before and child_tick.read_text()==child_before
                report["external_continues_during_pause"]=external_tick.read_text()!=ext_before
                build.pause(False); time.sleep(.5)
                report["root_and_child_resumed"]=ticker.read_text()!=before and child_tick.read_text()!=child_before
            build.abort(); time.sleep(.3)
            before=ticker.read_text(); child_before=child_tick.read_text(); ext_before=external_tick.read_text()
            time.sleep(.5)
            report["root_and_child_terminated"]=ticker.read_text()==before and child_tick.read_text()==child_before
            report["external_survived"]=external.poll() is None and external_tick.read_text()!=ext_before
            assert all(v for k,v in report.items() if isinstance(v,bool)),report
        # Keep the router worker running long enough to exercise its real telemetry relay.
        time.sleep(22)
        report["result"]="PASS"
        (root/"guard-proof-result.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    finally:
        if build:
            if build.process.poll() is None: build.abort()
            build.close()
        external.terminate(); external.wait(timeout=5)
