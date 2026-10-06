"""Persistent single-image jobs and recovery, registered on the native MCP."""
import argparse
import hashlib
import json
import re
import sqlite3
from contextlib import contextmanager, closing
from pathlib import Path
from typing import Any
from launcher import ROOT

IDENTIFIER = re.compile(r"[A-Za-z0-9_-]{1,80}\Z")

@contextmanager
def database():
    folder = ROOT / "jobs"
    folder.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(folder / "bridge.db", timeout=10)
    connection.row_factory = sqlite3.Row
    connection.execute("""CREATE TABLE IF NOT EXISTS jobs (
        request_id TEXT PRIMARY KEY, params_json TEXT NOT NULL,
        task_id TEXT, result_json TEXT, state TEXT NOT NULL)""")
    connection.commit()
    try:
        with connection:
            yield connection
    finally:
        connection.close()

def save(request_id, result, task_id=None):
    with database() as connection:
        connection.execute("UPDATE jobs SET result_json=?, state=?, task_id=COALESCE(?,task_id) WHERE request_id=?",
            (json.dumps(result, ensure_ascii=False), result["status"], task_id, request_id))

def verified_file(result):
    from PIL import Image
    path = Path(result["path"]).resolve()
    if not path.is_relative_to((ROOT / "results").resolve()):
        raise ValueError("Result path is outside the configured results folder")
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if result.get("sha256") and digest != result["sha256"]:
        raise ValueError("Stored result file has changed")
    with Image.open(path) as image:
        dimensions, image_format = image.size, image.format
        image.verify()
    return {**result, "path": str(path), "files": [str(path)], "bytes": len(data),
        "dimensions": dimensions, "format": image_format, "sha256": digest}

def native_task(task_id):
    path = ROOT / "state" / "gflow.db"
    if not path.exists():
        return None
    with closing(sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)) as connection:
        connection.row_factory = sqlite3.Row
        row = connection.execute("SELECT * FROM generation_queue WHERE task_id=? AND profile_name='nexus'",
            (task_id,)).fetchone()
    return dict(row) if row else None

async def flow_get_result(request_id: str | None = None, task_id: str | None = None) -> dict[str, Any]:
    """Read or recover an existing image job. Never submits a generation.

    Pass the stable request_id used by flow_generate_image, or a native gflow
    task_id when importing an already submitted task. Returns a verified local
    file, or needs_reconciliation without resubmitting the prompt.
    """
    if bool(request_id) == bool(task_id):
        return {"status": "error", "detail": "Provide exactly one of request_id or task_id."}
    if not IDENTIFIER.fullmatch(request_id or task_id or ""):
        return {"status": "error", "detail": "Invalid job identifier."}
    with database() as connection:
        if request_id:
            row = connection.execute("SELECT * FROM jobs WHERE request_id=?", (request_id,)).fetchone()
            if not row:
                return {"status": "error", "detail": "Unknown request_id."}
            if row["result_json"]:
                result = json.loads(row["result_json"])
                if result.get("path"):
                    try:
                        return verified_file(result)
                    except (OSError, ValueError):
                        pass
            task_id = row["task_id"]
        if not task_id:
            return {"status": "needs_reconciliation", "request_id": request_id,
                "detail": "Submission may be in progress or its task id was not recorded. Do not resubmit."}
        task = native_task(task_id)
        if not task or task["task_type"] != "t2i":
            return {"status": "error", "detail": "No image task for this profile."}
        params = json.loads(task["payload_json"])
        checkpoint = json.loads(task.get("checkpoint_json") or "{}")
        request_id = request_id or f"task-{task_id}"
        original_parameters = {key: params[key] for key in ("prompt", "model", "aspect", "count")}
        connection.execute("INSERT OR IGNORE INTO jobs VALUES (?,?,?,?,?)",
            (request_id, json.dumps(original_parameters), task_id, None, "submitted"))
    base = {"request_id": request_id, "task_id": task_id,
        "requested_model": params.get("model"), "model_confirmed": False}
    if not checkpoint.get("project_id") or not checkpoint.get("may_have_spent"):
        result = {**base, "status": "needs_reconciliation",
            "detail": "No authoritative submitted-project checkpoint. No new generation will be sent."}
        save(request_id, result, task_id)
        return result
    if task["status"] in ("pending", "processing"):
        return {**base, "status": "in_progress"}
    from recover_image import run
    source = ROOT / "jobs" / f"{request_id}.request.json"
    source.write_text(json.dumps(params, ensure_ascii=False), encoding="utf-8")
    try:
        recovered = await run(argparse.Namespace(project=checkpoint["project_id"], request=str(source)))
        if recovered["status"] != "recovered":
            result = {**base, "status": "needs_reconciliation", "project_id": checkpoint["project_id"],
                "detail": "The existing project did not yield one exact-prompt image. No prompt was resubmitted."}
        else:
            result = verified_file({**recovered, **base, "status": "completed", "recovery_used": True})
    except Exception as exc:
        # Exception text may contain a signed media URL. Keep credentials off MCP.
        result = {**base, "status": "needs_reconciliation", "project_id": checkpoint["project_id"],
            "error_class": type(exc).__name__, "detail": "Recovery failed. Retry flow_get_result, never generation."}
    save(request_id, result, task_id)
    return result

async def flow_generate_image(request_id: str, prompt: str, aspect: str = "1:1", model: str = "nano2") -> dict[str, Any]:
    """Generate one image using the signed-in personal Flow account.

    request_id must be stable for this intended generation. Reuse it after any
    timeout or failure: repeating an id never generates twice. A different id
    represents a new credit-consuming generation. Currently supports Nano Banana
    2 text-to-image. On completion return/display files[0]; model_confirmed
    reflects provider attribution. Browser automation runs on the local PC.
    """
    if not IDENTIFIER.fullmatch(request_id):
        return {"status": "error", "detail": "request_id must contain 1-80 letters, digits, underscores or hyphens."}
    if not prompt.strip() or model != "nano2" or aspect not in {"1:1", "16:9", "9:16", "4:3", "3:4"}:
        return {"status": "error", "detail": "Provide a prompt, model nano2, and a supported image aspect."}
    params = {"prompt": prompt, "model": model, "aspect": aspect, "count": 1}
    with database() as connection:
        try:
            connection.execute("INSERT INTO jobs VALUES (?,?,?,?,?)",
                (request_id, json.dumps(params), None, None, "reserved"))
        except sqlite3.IntegrityError:
            row = connection.execute("SELECT * FROM jobs WHERE request_id=?", (request_id,)).fetchone()
            previous = json.loads(row["params_json"])
            if {key: previous.get(key) for key in params} != params:
                return {"status": "error", "detail": "request_id already belongs to different parameters. No generation sent."}
            existing = True
        else:
            existing = False
    if existing:
        return await flow_get_result(request_id=request_id)
    from gflow_cli.mcp.tools import gflow_generate_image
    try:
        result = await gflow_generate_image(**params, profile="nexus",
            project_name=f"Codex {request_id}", wait=True)
    except Exception as exc:
        result = {"status": "needs_reconciliation", "request_id": request_id,
            "error_class": type(exc).__name__, "detail": "Submission outcome uncertain. Do not use a new request_id to retry."}
        save(request_id, result)
        return result
    task_id = result.get("task_id")
    save(request_id, {**result, "request_id": request_id}, task_id)
    files = result.get("files") or []
    if result.get("status") == "completed" and files:
        try:
            proof = verified_file({**result, "path": files[0], "request_id": request_id,
                "requested_model": model, "model_confirmed": False})
            save(request_id, proof, task_id)
            return proof
        except (OSError, ValueError):
            pass
    return await flow_get_result(request_id=request_id)

async def flow_list_jobs(limit: int = 20) -> dict[str, Any]:
    """List this bridge's persistent local jobs, including recovered results."""
    with database() as connection:
        rows = connection.execute("SELECT request_id,task_id,state,result_json FROM jobs ORDER BY rowid DESC LIMIT ?",
            (max(1, min(limit, 100)),)).fetchall()
    return {"jobs": [{"request_id": row["request_id"], "task_id": row["task_id"], "status": row["state"],
        "files": (json.loads(row["result_json"]) if row["result_json"] else {}).get("files", [])} for row in rows]}

def install():
    from gflow_cli.mcp.server import server
    from gflow_cli.mcp import tools
    server.add_tool(flow_generate_image, structured_output=True)
    server.add_tool(flow_get_result, structured_output=True)
    server.add_tool(flow_list_jobs, structured_output=True)
