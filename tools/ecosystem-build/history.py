"""Read completed job evidence; never guess a cached build from a CPU model."""
import hashlib
import json
import re
import statistics
import time
from pathlib import Path


def signature(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def workload(recipe):
    # Scheduling budgets and labels do not change the requested product.
    return signature({k: recipe.get(k) for k in ("kind", "tasks", "command", "env", "java_major",
        "compile_sdk", "requires_tools", "source_includes", "artifacts")})


def executor(node):
    return signature({k: node.get(k) for k in ("id", "device_kind", "state_root", "env", "tools",
        "expected_hostname", "expected_serial", "host_key_alias", "cpu_affinity", "cpu_cap_percent")})


def build_statistics(log, kind):
    if kind != "android-gradle":
        return {"cache_class": "unclassified"}
    text = Path(log).read_text(errors="replace")
    task_lines = re.findall(r"^> Task [^\r\n]+", text, re.M)
    reused = sum("FROM-CACHE" in line or "UP-TO-DATE" in line for line in task_lines)
    actionable = [line for line in task_lines if "NO-SOURCE" not in line and "SKIPPED" not in line]
    if not actionable:
        return {"cache_class": "unknown"}
    ratio = reused / len(actionable)
    return {"cache_class": "cold" if reused == 0 else ("cached" if ratio >= .5 else "partial"),
            "reused_tasks": reused, "observed_tasks": len(actionable), "reuse_ratio": round(ratio, 3)}


def samples(state, source, recipe, now=None):
    found = []
    key = workload(recipe)
    for job in (Path(state) / "jobs").glob("*"):
        try:
            request = json.loads((job / "request.json").read_text())
            result = json.loads((job / "status.json").read_text())
            if result.get("state") != "succeeded" or any(e.get("action")!="run" for e in result.get("protection_events",[])):
                continue
            if Path(request["source_path"]).resolve() != Path(source).resolve() or workload(request["recipe"]) != key:
                continue
            finished = result["finished_at"]
            if not 0 <= (now or time.time()) - finished <= 30 * 86400:
                continue
            duration = result.get("build_seconds", finished - result["started_at"])
            if duration <= 0:
                continue
            cache = result.get("build_statistics") or build_statistics(job / "build.log", recipe["kind"])
            manifest = json.loads((job / "source-manifest.json").read_text())
            found.append({"job_id": job.name, "node": result["node"], "executor": executor(request["node"]),
                "source_signature": signature(manifest), "seconds": duration, "finished_at": finished,
                "cache_class": cache["cache_class"], "timing": "build" if "build_seconds" in result else "legacy_worker"})
        except (OSError, ValueError, KeyError, TypeError):
            continue
    return found


def estimate(records, node, source_signature, cache_mode="auto"):
    own = [r for r in records if r["node"] == node["id"] and r["executor"] == executor(node)]
    exact = [r for r in own if r["source_signature"] == source_signature]
    modes = {}
    for mode in ("cold", "cached", "partial", "unclassified"):
        values = [r for r in (exact if mode != "cold" else own) if r["cache_class"] == mode]
        values = sorted(values, key=lambda r: r["finished_at"], reverse=True)[:5]
        if values:
            modes[mode] = {"seconds": round(statistics.median(r["seconds"] for r in values), 2),
                "samples": len(values), "job_ids": [r["job_id"] for r in values],
                "exact_source": all(r["source_signature"] == source_signature for r in values),
                "timing": sorted(set(r["timing"] for r in values))}
    # Automatic routing compares cold evidence for Gradle; actual future cache hits are unknown.
    mode = ("cold" if any(r["cache_class"] != "unclassified" for r in own) else "unclassified") if cache_mode == "auto" else cache_mode
    return {"basis": mode, "estimate": modes.get(mode), "observed": modes,
            "future_cache_hits": "unknown", "confidence": "limited" if modes.get(mode, {}).get("samples", 0) < 3 else "measured"}
