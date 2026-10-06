"""Copy verified tools out of the mounted pre-upgrade VM; never modify the backup."""
import hashlib
import json
import os
import shutil
import signal
import subprocess
from pathlib import Path

state=Path.home()/".local/share/ecosystem-build"
old=Path("/mnt/backup/home/droid/aki/toolchain")
new=state/"toolchain-20261006"; new.mkdir(exist_ok=True)
def sha(p):
    h=hashlib.sha256()
    with open(p,"rb") as f:
        for block in iter(lambda:f.read(1048576),b""): h.update(block)
    return h.hexdigest()
counts={}
for name in ("jdk17","android-sdk"):
    src=old/name; dst=new/name
    shutil.copytree(src,dst,symlinks=True,dirs_exist_ok=True)
    count=0
    for p in src.rglob("*"):
        if p.is_file() and not p.is_symlink():
            if sha(p)!=sha(dst/p.relative_to(src)): raise ValueError("copy mismatch: "+str(p))
            count+=1
    counts[name]=count
# Dependency/wrapper caches are reusable, not project output or signing credentials.
for rel in ("wrapper","caches/modules-2","caches/jars-9","caches/transforms-4"):
    src=old/"gradle-home"/rel
    if src.is_dir(): shutil.copytree(src,new/"gradle-home"/rel,symlinks=True,dirs_exist_ok=True)
record={"source":str(old),"destination":str(new),"verified_tool_files":counts,
        "aapt2_sha256":sha(state/"aapt2-arm64"),"backup_untouched":True}
(state/"toolchain-restoration.json").write_text(json.dumps(record,indent=2))
unit='''[Unit]
Description=Private Pixel ecosystem build SSH
After=network.target
[Service]
Type=simple
ExecStart=/usr/sbin/sshd -D -f /home/droid/.local/share/ecosystem-build/sshd_config -E /home/droid/.local/share/ecosystem-build/sshd.log
Restart=on-failure
RestartSec=5
[Install]
WantedBy=multi-user.target
'''
local=state/"ecosystem-build-ssh.service"; local.write_text(unit)
system=Path("/etc/systemd/system/ecosystem-build-ssh.service")
if system.exists() and system.read_text()!=unit: raise ValueError("existing service differs; preserved")
subprocess.run(["sudo","install","-m","644",str(local),str(system)],check=True)
if subprocess.run(["systemctl","is-active","--quiet","ecosystem-build-ssh.service"]).returncode:
    pidfile=state/"sshd.pid"
    if pidfile.exists():
        pid=int(pidfile.read_text()); cmd=Path("/proc")/str(pid)/"cmdline"
        if cmd.exists() and str(state/"sshd_config").encode() in cmd.read_bytes():
            subprocess.run(["sudo","kill","-TERM",str(pid)],check=True)
subprocess.run(["sudo","systemctl","daemon-reload"],check=True)
subprocess.run(["sudo","systemctl","enable","--now","ecosystem-build-ssh.service"],check=True)
print(json.dumps(record),flush=True)
