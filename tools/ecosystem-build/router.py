"""Demand-driven build router. No server, paid API, polling service or visible UI."""
import argparse
import base64
import concurrent.futures
import datetime
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tarfile
import time
import uuid
from pathlib import Path
from probe import probe, run, cap_android_ram
from worker import compatible, digest, save

ROOT = Path(__file__).resolve().parent
STATE = Path.home()/".codex"/"build-state"


def config():
    data = json.loads((ROOT/"nodes.json").read_text(encoding="utf-8"))
    overrides=ROOT/"nodes.local.json"
    if overrides.exists():
        local=json.loads(overrides.read_text(encoding="utf-8"))
        for node in data["nodes"]:
            node.update(local.get(node["id"],{}))
    return data["nodes"]


def ssh_args(node):
    args = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=7", "-o", "StrictHostKeyChecking=yes",
            "-o", "ServerAliveInterval=15", "-o", "ServerAliveCountMax=3"]
    if node.get("proxy_jump"): args += ["-J", node["proxy_jump"]]
    if node.get("ssh_port"): args += ["-p", str(node["ssh_port"])]
    if node.get("host_key_alias"): args += ["-o","HostKeyAlias="+node["host_key_alias"]]
    if node.get("known_hosts"): args += ["-o","UserKnownHostsFile="+node["known_hosts"]]
    return args + [node["ssh"]]


def ssh(node, command, timeout=20):
    p = subprocess.run(ssh_args(node)+[command],capture_output=True,timeout=timeout,
                       creationflags=0x08000000 if os.name == "nt" else 0)
    if p.returncode:
        raise RuntimeError(p.stderr.decode(errors="replace").strip() or "SSH terminó con código " + str(p.returncode))
    return p.stdout


def scp(node, source, dest, pull=False):
    args = ["scp", "-q", "-o", "BatchMode=yes", "-o", "ConnectTimeout=7", "-o", "StrictHostKeyChecking=yes"]
    if node.get("proxy_jump"): args += ["-J",node["proxy_jump"]]
    if node.get("ssh_port"): args += ["-P",str(node["ssh_port"])]
    if node.get("host_key_alias"): args += ["-o","HostKeyAlias="+node["host_key_alias"]]
    if node.get("known_hosts"): args += ["-o","UserKnownHostsFile="+node["known_hosts"]]
    remote = node["ssh"]+":"+(source if pull else dest)
    args += [remote,str(dest)] if pull else [str(source),remote]
    p = subprocess.run(args,capture_output=True,timeout=180,creationflags=0x08000000 if os.name == "nt" else 0)
    if p.returncode: raise RuntimeError(p.stderr.decode(errors="replace"))


def probe_command(node):
    script = base64.b64encode((ROOT/"probe.py").read_bytes()).decode()
    payload = base64.b64encode(json.dumps(node).encode()).decode()
    return " ".join(shlex.quote(s) for s in [node.get("python","python3"),"-c",
            "import base64;exec(base64.b64decode("+repr(script)+"))",payload])


def host_probe(node):
    try:
        if node["transport"] == "local": metrics = probe(node)
        else: metrics = json.loads(ssh(node,probe_command(node),30))
        if node.get("expected_hostname") and metrics.get("hostname","").lower() != node["expected_hostname"].lower():
            metrics.update(online=False,error="identidad de host incorrecta")
        return node,metrics
    except Exception as e:
        return node,{"online":False,"error":str(e),"commander_device_id":node.get("commander_device_id")}


def adb_command(bridge, args):
    if bridge["transport"] == "local":
        code, output = run([bridge.get("adb","adb")]+args,8)
        return output if code == 0 else ""
    try:
        return ssh(bridge," ".join(shlex.quote(s) for s in [bridge.get("adb","adb")]+args),12).decode(errors="replace")
    except Exception:
        return ""


def adb_inventory(bridge, phones):
    # Connect only current, advertised connect endpoints for these two physical serials.
    # Never pair, switch tcpip mode, restart the shared ADB server or probe arbitrary LAN ports.
    services = adb_command(bridge,["mdns","services"])
    for line in services.splitlines():
        if "_adb-tls-connect._tcp" in line and any(p["expected_serial"] in line for p in phones):
            endpoint = line.split()[-1]
            if re.fullmatch(r"[\d.]+:\d+",endpoint): adb_command(bridge,["connect",endpoint])
    output = adb_command(bridge,["devices","-l"])
    found = {}
    for line in output.splitlines():
        fields = line.split()
        if len(fields)<2 or fields[1]!="device": continue
        serial = fields[0]
        hints = line.replace("_"," ")
        candidates = [p for p in phones if p["id"] not in found and (p["expected_serial"] in line or p["expected_model"] in hints)]
        if not candidates: continue
        model = adb_command(bridge,["-s",serial,"shell","getprop","ro.product.model"]).strip()
        physical = adb_command(bridge,["-s",serial,"shell","getprop","ro.serialno"]).strip()
        for p in candidates:
            if model != p["expected_model"] or physical != p["expected_serial"]: continue
            battery = adb_command(bridge,["-s",serial,"shell","dumpsys","battery"])
            mem = adb_command(bridge,["-s",serial,"shell","cat","/proc/meminfo"])
            power = adb_command(bridge,["-s",serial,"shell","dumpsys","power"])
            def cpu_sample():
                raw=adb_command(bridge,["-s",serial,"shell","head","-1","/proc/stat"]).split()
                try: return list(map(int,raw[1:9])) if raw and raw[0]=="cpu" else []
                except ValueError: return []
            cpu_a=cpu_sample(); time.sleep(0.6); cpu_b=cpu_sample()
            cpu_delta=[b-a for a,b in zip(cpu_a,cpu_b)]
            def number(pattern,text,scale=1):
                m = re.search(pattern,text)
                return int(m[1])/scale if m else None
            found[p["id"]] = {"online":True,"adb_access":True,"adb_bridge":bridge["id"],"adb_serial":serial,
                "physical_serial":physical,"model":model,"os":"android","arch":"aarch64",
                "ram_available_mb":number(r"MemAvailable:\s+(\d+)",mem,1024),
                "battery_percent":number(r"level:\s+(\d+)",battery),
                "temperature_c":number(r"temperature:\s+(\d+)",battery,10),
                "cpu_percent":round(100*(1-(cpu_delta[3]+cpu_delta[4])/max(1,sum(cpu_delta))),1) if len(cpu_delta)>=5 else None,
                "charging":bool(re.search(r"(?:AC|USB|Wireless) powered: true",battery)),
                "screen_on":("mWakefulness=Awake" in power) if "mWakefulness=" in power else None,
                "measured_at":time.time(),"background_executor":False,"error":"falta ejecutor de fondo verificado en Termux"}
    return found


def mobile_executor(node,metrics,hosts):
    executor=node["executor"]
    execnode=dict(node); execnode.update(executor); execnode["transport"]="ssh"
    if executor.get("transport")=="adb-ssh":
        bridge=next(n for n in hosts if n["id"]==metrics["adb_bridge"])
        serial=metrics["adb_serial"]; target="tcp:"+str(executor.get("android_ssh_port",8022))
        forwards=adb_command(bridge,["forward","--list"])
        port=None
        for line in forwards.splitlines():
            fields=line.split()
            if len(fields)==3 and fields[0]==serial and fields[2]==target:
                port=fields[1].removeprefix("tcp:"); break
        if not port:
            port=adb_command(bridge,["-s",serial,"forward","tcp:0",target]).strip()
        if not port or not port.isdigit(): raise ValueError("no se pudo establecer el forward SSH privado")
        execnode["ssh"]=executor["ssh_user"]+"@127.0.0.1"; execnode["ssh_port"]=int(port)
        if bridge["transport"]=="ssh": execnode["proxy_jump"]=bridge["ssh"]
        else: execnode.pop("proxy_jump",None)
    return execnode


def ecosystem():
    nodes = config(); hosts = [n for n in nodes if n["transport"] != "android"]
    phones = [n for n in nodes if n["transport"] == "android"]
    results = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(host_probe,n) for n in hosts]
        inventories = [pool.submit(adb_inventory,n,phones) for n in hosts if n["transport"]=="local" or n.get("adb")]
        for f in futures:
            n,m = f.result(); results[n["id"]] = (n,m)
        found = {}
        for f in inventories:
            for phone_id, metrics in f.result().items():
                found.setdefault(phone_id, []).append(metrics)
    code, raw = run(["tailscale","status","--json"],5)
    try: peers = list(json.loads(raw).get("Peer",{}).values()) if code==0 else []
    except (ValueError,AttributeError): peers=[]
    for n in phones:
        routes = sorted(found.get(n["id"], []), key=lambda m: m["adb_bridge"] != "elitebook")
        metrics = dict(routes[0]) if routes else {"online":False,"adb_access":False,"background_executor":False,"error":"sin transporte ADB autorizado actual"}
        peer = next((p for p in peers if p.get("HostName")==n.get("tailscale_name")),{})
        metrics["tailscale_online"] = peer.get("Online")
        if n.get("executor") and metrics.get("adb_access"):
            for route in routes:
                try:
                    execnode=mobile_executor(n,route,hosts)
                    _,host=host_probe(execnode)
                    if not host.get("online"):
                        metrics["error"]=host.get("error","ejecutor de fondo no disponible")
                        continue
                    for key in ("battery_percent","charging","temperature_c","screen_on","physical_serial","model","adb_bridge","adb_serial","measured_at"):
                        host[key]=route.get(key)
                    cap_android_ram(host,route)
                    if host.get("os")=="android": host["cpu_percent"]=route.get("cpu_percent")
                    host["background_executor"]=True
                    results[n["id"]]=(execnode,host)
                    break
                except Exception as e:
                    metrics["error"]=str(e)
            if n["id"] in results:
                continue
        results[n["id"]]=(n,metrics)
    return results


def decision(recipe, results, preferred=None):
    candidates=[]
    for node,metrics in results.values():
        if node["transport"]=="android" and not metrics.get("background_executor"):
            why=[metrics.get("error","sin ejecutor de fondo verificado")]
            if metrics.get("battery_percent") is not None and metrics["battery_percent"] < node.get("min_battery_percent",20):
                why.append("batería por debajo del mínimo incluso conectado")
        else:
            why=compatible(node,metrics,recipe)
        # Dedicated device preference plus live load; this is a policy score, not a speed benchmark.
        score=node.get("base_score",0) - (metrics.get("cpu_percent") if metrics.get("cpu_percent") is not None else 100)*0.7 - (metrics.get("io_pressure") or 0)*0.4
        if not node.get("dedicated") and metrics.get("interactive",True): score-=40
        if preferred and preferred==node["id"]: score+=1000
        if recipe.get("prefer") == node["id"]: score+=10
        candidates.append({"node":node["id"],"label":node["label"],"eligible":not why,"reasons":why,"score":round(score,1),"metrics":metrics})
    ready=sorted((c for c in candidates if c["eligible"]),key=lambda c:c["score"],reverse=True)
    return {"measured_at":time.time(),"selected":ready[0]["node"] if ready else None,"candidates":candidates,
            "state":"ready" if ready else "waiting","policy":"compatibilidad, reserva de recursos y prioridad de equipos dedicados"}


EXCLUDED_DIRS={".git",".gradle",".idea",".venv","venv","node_modules","build","dist","outputs","entregas","__pycache__",".codex-remote-attachments"}


def snapshot(source, includes, job):
    if not includes: raise ValueError("indicar source_includes; no enviar todo el directorio por defecto")
    files={}
    for include in includes:
        start=(source/include).resolve()
        if not start.is_relative_to(source) or not start.exists(): raise ValueError("source_includes inválido: "+include)
        paths=[start] if start.is_file() else start.rglob("*")
        for p in paths:
            rel=p.relative_to(source)
            if any(part in EXCLUDED_DIRS or part.startswith(".codex-") for part in rel.parts): continue
            if p.is_symlink(): raise ValueError("resolver enlace de fuente antes de transferir: "+str(rel))
            if not p.is_file(): continue
            if p.name=="local.properties" or p.name.startswith(".env") or p.suffix.lower() in {".keystore",".jks",".pem",".key",".p12",".pfx"}: continue
            files[rel.as_posix()]=p
    manifest={}
    # Hash the actual archived bytes: source edits during transfer never create an ambiguous snapshot.
    with tarfile.open(job/"source.tar","w") as archive:
        import io
        for name,p in sorted(files.items()):
            data=p.read_bytes(); manifest[name]=hashlib.sha256(data).hexdigest()
            info=tarfile.TarInfo(name); info.size=len(data); info.mode=0o700 if p.name=="gradlew" else 0o600
            archive.addfile(info,io.BytesIO(data))
    if not manifest: raise ValueError("instantánea de código vacía")
    save(job/"source-manifest.json",manifest)
    return digest(job/"source.tar")


def start_job(source,recipe,choice,results):
    node=results[choice["selected"]][0]
    jobid=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")+"-"+uuid.uuid4().hex[:8]
    job=STATE/"jobs"/jobid; job.mkdir(parents=True)
    archive_sha=snapshot(source,recipe["source_includes"],job)
    save(job/"request.json",{"node":node,"recipe":recipe,"source_path":str(source),"source_archive_sha256":archive_sha})
    save(job/"decision.json",choice)
    for name in ("worker.py","probe.py"): shutil.copy2(ROOT/name,job/name)
    remote=node["state_root"].rstrip("/")+"/jobs/"+jobid if node["transport"]=="ssh" else str(job)
    record={"job_id":jobid,"node":node,"job_path":remote,"local_path":str(job),"state":"staging"}
    save(job/"record.json",record)
    if node["transport"]=="ssh":
        ssh(node,"umask 077; mkdir -p "+shlex.quote(remote))
        for name in ("source.tar","source-manifest.json","request.json","worker.py","probe.py"):
            scp(node,job/name,remote+"/"+name)
        if node.get("device_metrics_file"):
            bridgeid=results[node["id"]][1]["adb_bridge"]
            bridge=results[bridgeid][0]
            health=adb_inventory(bridge,[node]).get(node["id"])
            if not health: raise ValueError("se perdió la identidad ADB del móvil durante la preparación")
            save(job/"device-health.json",health)
            scp(node,job/"device-health.json",node["device_metrics_file"])
        cmd="nohup "+" ".join(shlex.quote(s) for s in [node.get("python","python3"),remote+"/worker.py",remote])+" >"+shlex.quote(remote+"/worker.log")+" 2>&1 </dev/null &"
        # Write intent first. An ambiguous SSH return must never trigger a duplicate build.
        record["state"]="dispatching"; save(job/"record.json",record)
        try: ssh(node,cmd,12)
        except Exception as e:
            record.update(state="dispatch_unknown",reason=str(e)); save(job/"record.json",record)
            return record
    else:
        p=subprocess.Popen([sys.executable,str(job/"worker.py"),str(job)],stdout=open(job/"worker.log","wb"),
            stderr=subprocess.STDOUT,creationflags=(0x08000000|0x200|0x4000) if os.name=="nt" else 0,
            start_new_session=os.name!="nt")
        record["worker_pid"]=p.pid
    record["state"]="dispatched"; save(job/"record.json",record)
    return record


def get_record(jobid):
    if not re.fullmatch(r"[\w-]+",jobid): raise ValueError("job_id inválido")
    return json.loads((STATE/"jobs"/jobid/"record.json").read_text())


def status(record, tail=0):
    node=record["node"]; path=record["job_path"]; local=Path(record["local_path"])
    if node["transport"]=="ssh":
        try: raw=ssh(node,"cat "+shlex.quote(path+"/status.json"))
        except Exception as initial_error:
            try:
                if node.get("device_kind") != "android": raise initial_error
                fresh, metrics = ecosystem()[node["id"]]
                if (not metrics.get("background_executor") or fresh.get("state_root") != node.get("state_root")
                        or fresh.get("expected_serial") != node.get("expected_serial")):
                    raise initial_error
                # Reconnect to the same physical device and job; never dispatch another worker.
                node=fresh
                raw=ssh(node,"cat "+shlex.quote(path+"/status.json"))
                record["node"]=node; save(local/"record.json",record)
            except Exception as e:
                return {"job_id":record["job_id"],"state":"unknown","reason":str(e),"dispatch_state":record["state"]}
        result=json.loads(raw); save(local/"status.json",result)
        if tail:
            try: result["log_tail"]=ssh(node,"tail -n "+str(tail)+" "+shlex.quote(path+"/build.log")).decode(errors="replace")
            except Exception: pass
    else:
        result=json.loads((local/"status.json").read_text()) if (local/"status.json").exists() else {"state":"starting"}
        if tail and (local/"build.log").exists(): result["log_tail"]="\n".join((local/"build.log").read_text(errors="replace").splitlines()[-tail:])
    if result.get("state") in {"running","starting"} and result.get("worker_pid"):
        alive=None
        if node["transport"]=="ssh":
            try:
                alive=ssh(node,"if kill -0 "+str(int(result["worker_pid"]))+" 2>/dev/null; then printf alive; else printf gone; fi")==b"alive"
            except Exception: pass
        elif os.name=="nt":
            import ctypes
            from ctypes import wintypes
            k=ctypes.WinDLL("kernel32",use_last_error=True)
            k.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]; k.OpenProcess.restype=wintypes.HANDLE
            h=k.OpenProcess(0x1000,False,int(result["worker_pid"]))
            if h:
                k.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
                code=wintypes.DWORD(); alive=bool(k.GetExitCodeProcess(h,ctypes.byref(code))) and code.value==259
                k.CloseHandle.argtypes=[wintypes.HANDLE]; k.CloseHandle(h)
            elif ctypes.get_last_error()==87: alive=False
        else:
            try: os.kill(int(result["worker_pid"]),0); alive=True
            except ProcessLookupError: alive=False
            except PermissionError: pass
        if alive is False:
            result.update(state="unknown",reason="worker desaparecido sin resultado final; resolver procesos/artefactos antes de reenviar")
    return result


def collect(record,dest):
    result=status(record)
    if result["state"] not in {"succeeded","failed"}: raise ValueError("el trabajo no ha terminado: "+result["state"])
    local=Path(record["local_path"]); node=record["node"]; remote=record["job_path"]
    names=["build.log","status.json","artifact-manifest.json","artifacts.tar"]
    if node["transport"]=="ssh":
        for name in names:
            try: scp(node,remote+"/"+name,local/name,pull=True)
            except RuntimeError:
                if name in {"artifacts.tar","artifact-manifest.json"} and not result.get("artifact_archive_sha256"): continue
                raise
    dest=dest.resolve(); dest.mkdir(parents=True,exist_ok=True)
    if result.get("artifact_archive_sha256"):
        if digest(local/"artifacts.tar")!=result["artifact_archive_sha256"]: raise ValueError("SHA-256 de transferencia incorrecto")
        manifest=json.loads((local/"artifact-manifest.json").read_text())
        artifact_dest=dest/record["job_id"]
        if artifact_dest.is_symlink() or not artifact_dest.resolve().is_relative_to(dest): raise ValueError("destino de artefactos inválido")
        artifact_dest.mkdir(exist_ok=True)
        seen=set()
        with tarfile.open(local/"artifacts.tar") as archive:
            for m in archive:
                p=Path(m.name)
                if not m.isfile() or p.is_absolute() or ".." in p.parts or m.name not in manifest: raise ValueError("entrada de artefacto inválida")
                if m.name in seen: raise ValueError("artefacto duplicado")
                seen.add(m.name)
                target=artifact_dest/p
                if not target.resolve().is_relative_to(artifact_dest.resolve()): raise ValueError("artefacto fuera de destino")
                target.parent.mkdir(parents=True,exist_ok=True)
                with archive.extractfile(m) as inp,open(target,"wb") as out: shutil.copyfileobj(inp,out)
                if digest(target)!=manifest[m.name]: raise ValueError("SHA-256 de artefacto incorrecto")
        if seen!=set(manifest): raise ValueError("faltan artefactos del manifiesto")
        for name in ("status.json","artifact-manifest.json","decision.json","source-manifest.json","build.log"):
            if (local/name).exists(): shutil.copy2(local/name,artifact_dest/name)
        result["collected_path"]=str(artifact_dest); result["integrity_verified"]=True
    save(local/"collection.json",result)
    return result


def main():
    if hasattr(sys.stdout,"reconfigure"): sys.stdout.reconfigure(encoding="utf-8")
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest="action",required=True)
    sub.add_parser("probe")
    pc=sub.add_parser("probe-command"); pc.add_argument("--node",required=True)
    for name in ("plan","submit"):
        p=sub.add_parser(name); p.add_argument("--recipe",type=Path,required=True)
        p.add_argument("--source",type=Path,default=Path.cwd()); p.add_argument("--prefer")
        if name=="submit": p.add_argument("--wait-seconds",type=int,default=0)
    p=sub.add_parser("status"); p.add_argument("job_id"); p.add_argument("--tail",type=int,default=0)
    p=sub.add_parser("collect"); p.add_argument("job_id"); p.add_argument("--dest",type=Path,required=True)
    args=parser.parse_args()
    if args.action=="probe": output={n:metrics for n,(_,metrics) in ecosystem().items()}
    elif args.action=="probe-command": output=probe_command(next(n for n in config() if n["id"]==args.node))
    elif args.action in {"plan","submit"}:
        recipe=json.loads(args.recipe.read_text(encoding="utf-8-sig")); validate_recipe(recipe)
        deadline=time.monotonic()+getattr(args,"wait_seconds",0)
        while True:
            results=ecosystem(); choice=decision(recipe,results,args.prefer)
            if args.action=="plan" or choice["selected"] or time.monotonic()>=deadline: break
            print(json.dumps({"state":"waiting","next_probe_seconds":15}),flush=True)
            time.sleep(min(15,max(0,deadline-time.monotonic())))
        output=choice if args.action=="plan" or not choice["selected"] else start_job(args.source.resolve(),recipe,choice,results)
    elif args.action=="status": output=status(get_record(args.job_id),min(max(args.tail,0),100))
    else: output=collect(get_record(args.job_id),args.dest)
    print(json.dumps(output,ensure_ascii=False,indent=2),flush=True)
    return 2 if output.get("state") in {"failed","waiting","unknown","dispatch_unknown"} else 0


def validate_recipe(recipe):
    if recipe.get("kind") not in {"android-gradle","command"}: raise ValueError("kind debe ser android-gradle o command")
    if not recipe.get("source_includes"): raise ValueError("source_includes requerido")
    if not recipe.get("host_os"): raise ValueError("host_os requerido; compatibilidad no se infiere por extensión")
    if recipe["kind"]=="android-gradle":
        if not isinstance(recipe.get("compile_sdk"),int) or not recipe.get("tasks"): raise ValueError("compile_sdk y tasks requeridos")
        if any(not re.fullmatch(r"[\w:]+",t) for t in recipe["tasks"]): raise ValueError("tasks solo admite nombres de tareas Gradle")
    elif not recipe.get("command") or not recipe.get("requires_tools"):
        raise ValueError("command y requires_tools requeridos")
    for pattern in recipe.get("artifacts",[])+recipe.get("reports",[]):
        if Path(pattern).is_absolute() or ".." in Path(pattern).parts: raise ValueError("patrón fuera del proyecto")


if __name__=="__main__":
    try: sys.exit(main())
    except Exception as e:
        print(json.dumps({"state":"error","reason":str(e)},ensure_ascii=False),file=sys.stderr); sys.exit(1)
