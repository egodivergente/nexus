"""Persistent image/video jobs, quotes and recovery, registered on the native MCP."""
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

def _verified_file(result):
    from PIL import Image
    path = Path(result["path"]).resolve()
    if not path.is_relative_to((ROOT / "results").resolve()):
        raise ValueError("Result path is outside the configured results folder")
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if result.get("sha256") and digest != result["sha256"]:
        raise ValueError("Stored result file has changed")
    if path.suffix.lower() == ".mp4":
        from video_file import inspect
        metadata = inspect(data)
    else:
        with Image.open(path) as image:
            dimensions, image_format = image.size, image.format
            frames=image.n_frames if image_format=='GIF' else None
            image.verify()
        metadata = {"dimensions": dimensions, "format": image_format}
        if frames is not None:metadata.update(frames=frames,animated=frames>1)
    return {**result, "path": str(path), "files": [str(path)], "bytes": len(data),
        **metadata, "sha256": digest}


def verified_file(result):
    artifacts = result.get('artifacts')
    files = result.get('files') or [result['path']]
    if not artifacts and len(files) == 1:
        return _verified_file(result)
    if not artifacts:
        artifacts = [{'path':path} for path in files]
    proofs = [_verified_file(item) for item in artifacts]
    return {**result, **proofs[0], 'status':result.get('status'), 'files':[p['path'] for p in proofs], 'artifacts':proofs}

def complete_block(result, count):
    if not isinstance(result, dict) or count < 1:
        raise ValueError('Flow block missing')
    proof = verified_file(result)
    if len(proof['files']) != count:
        raise ValueError('Flow returned an incomplete block')
    return proof

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
    """Read or recover an existing image or video job. Never submits a generation.

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
                        return complete_block(result, json.loads(row['params_json']).get('count', 1))
                    except (OSError, ValueError):
                        pass
                if result.get('operation')=='edit_video' and result.get('edited_media_id'):
                    # Exact accepted output only; never resubmit after a timeout.
                    try:
                        from video_edit import recover
                        proof=await recover(result)
                        save(request_id,proof)
                        return proof
                    except Exception:
                        return {**result,'status':'needs_reconciliation','detail':'The accepted edit could not be downloaded. No edit resubmitted.'}
            task_id = row["task_id"]
        if not task_id:
            return {"status": "needs_reconciliation", "request_id": request_id,
                "detail": "Submission may be in progress or its task id was not recorded. Do not resubmit."}
        task = native_task(task_id)
        if not task or task["task_type"] not in ("t2i", "t2v", "i2v", "r2v"):
            return {"status": "error", "detail": "No supported task for this profile."}
        params = json.loads(task["payload_json"])
        checkpoint = json.loads(task.get("checkpoint_json") or "{}")
        if not checkpoint.get("project_id") and checkpoint.get("may_have_spent"):
            # The upstream queue can lose its project field on a parser failure.
            # Resolve only the unique project event for this exact owned task.
            log = ROOT / "mcp-client-stderr.log"
            projects = set()
            if log.exists():
                for line in log.read_text(encoding="utf-8", errors="replace").splitlines():
                    try:
                        event = json.loads(line)
                        if event.get("correlation_id") == f"wk-{task_id}" and event.get("event") == "migrated.project_created":
                            value = event.get("project_id", "")
                            if re.fullmatch(r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", value):
                                projects.add(value)
                    except ValueError:
                        pass
            if len(projects) == 1:
                checkpoint["project_id"] = projects.pop()
        request_id = request_id or f"task-{task_id}"
        original_parameters = {key: params[key] for key in ("prompt", "model", "aspect", "count", "mode", "duration", "resolution", "initial_frame", "end_frame", "reference_images") if key in params}
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
    if task["task_type"] in ("t2v", "i2v", "r2v"):
        # Completed native queue entries can be recovered from their persisted
        # result without opening a new generation. An unknown outcome stays held.
        for field in ("result_json", "output_json"):
            stored = json.loads(task.get(field) or "{}")
            files = stored.get("files") or []
            if files:
                result = complete_block({**base, 'status': 'completed', 'path': files[0], 'files': files}, params.get('count', 1))
                save(request_id, result, task_id)
                return result
        from recover_video import run as recover
        source = ROOT / "jobs" / f"{request_id}.request.json"
        source.write_text(json.dumps(params, ensure_ascii=False), encoding="utf-8")
        try:
            recovered = await recover(argparse.Namespace(project=checkpoint["project_id"], request=str(source)))
            if recovered["status"] == "recovered":
                result = verified_file({**recovered, **base, "status":"completed", "recovery_used":True})
            else:
                result = {**base, "status":"needs_reconciliation", "matching_videos":recovered.get("matching_videos")}
        except Exception as exc:
            result = {**base, "status":"needs_reconciliation", "error_class":type(exc).__name__}
            if isinstance(exc, ValueError) and str(exc) in {'Ambiguous video tiles','Unexpected video origin','Unexpected redirect origin','Video download failed','Player download was not MP4'}:
                result['detail']=str(exc)
        save(request_id, result, task_id)
        return result
    from recover_image import run
    source = ROOT / "jobs" / f"{request_id}.request.json"
    source.write_text(json.dumps(params, ensure_ascii=False), encoding="utf-8")
    try:
        recovered = await run(argparse.Namespace(project=checkpoint["project_id"], request=str(source)))
        if recovered["status"] != "recovered":
            result = {**base, "status": "needs_reconciliation", "project_id": checkpoint["project_id"],
                "detail": "The existing project did not yield one exact-prompt image. No prompt was resubmitted."}
        else:
            result = complete_block({**recovered, **base, 'status': 'completed', 'recovery_used': True}, params.get('count', 1))
    except Exception as exc:
        # Exception text may contain a signed media URL. Keep credentials off MCP.
        result = {**base, "status": "needs_reconciliation", "project_id": checkpoint["project_id"],
            "error_class": type(exc).__name__, "detail": "Recovery failed. Retry flow_get_result, never generation."}
    save(request_id, result, task_id)
    return result

async def flow_generate_image(request_id: str, prompt: str, aspect: str = "1:1", model: str = "nano2",
                              reference_images: list[str] | None = None, count: int = 1) -> dict[str, Any]:
    """Generate a native block of 1-4 images using the signed-in Flow account.

    request_id must be stable for this intended generation. Reuse it after any
    timeout or failure: repeating an id never generates twice. A different id
    represents a new credit-consuming generation. Currently supports Nano Banana
    2 and Nano Banana Pro, with up to ten owned reference images.
    On completion return/display files[0]; model_confirmed
    reflects provider attribution. Browser automation runs on the local PC.
    """
    if not IDENTIFIER.fullmatch(request_id):
        return {"status": "error", "detail": "request_id must contain 1-80 letters, digits, underscores or hyphens."}
    if not prompt.strip() or model not in {"nano2", "nano-pro"} or aspect not in {"1:1", "16:9", "9:16", "4:3", "3:4"}:
        return {"status": "error", "detail": "Provide a prompt, model nano2 or nano-pro, and a supported image aspect."}
    from flow_contract import normalize
    from flow_inputs import prepare
    try:
        if type(count) is not int or not 1 <= count <= 4:
            raise ValueError('Native blocks support 1-4 outputs')
        params = prepare(normalize({"model": model, "aspect": aspect, "reference_images": reference_images or [], 'count': count}))
    except (ValueError, OSError):
        return {"status": "error", "detail": "Invalid owned reference images or Flow configuration."}
    params.update(prompt=prompt, count=count)
    with database() as connection:
        try:
            connection.execute("INSERT INTO jobs VALUES (?,?,?,?,?)",
                (request_id, json.dumps(params), None, None, "reserved"))
        except sqlite3.IntegrityError:
            row = connection.execute("SELECT * FROM jobs WHERE request_id=?", (request_id,)).fetchone()
            previous = json.loads(row["params_json"])
            if previous != params:
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
            proof = complete_block({**result, "path": files[0], "request_id": request_id,
                "requested_model": model, "model_confirmed": False}, count)
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
    server.add_tool(flow_generate_video, structured_output=True)
    server.add_tool(flow_get_quote, structured_output=True)
    server.add_tool(flow_get_result, structured_output=True)
    server.add_tool(flow_list_jobs, structured_output=True)
    server.add_tool(flow_get_edit_options, structured_output=True)
    server.add_tool(flow_edit_video, structured_output=True)


async def flow_generate_video(request_id: str, prompt: str, aspect: str = "16:9",
                              model: str = "veo_fast", mode: str = "t2v", duration: int | None = None,
                              resolution: str | None = None, initial_frame: str | None = None,
                              end_frame: str | None = None, reference_images: list[str] | None = None,
                              voice: str | None = None, count: int = 1) -> dict[str, Any]:
    """Generate a native block of 1-4 videos on the user's own Flow account.

    Reuse the same request_id after a timeout: a reserved id never submits twice.
    Bind text, frames or ingredients with explicit duration and native resolution.
    The completed MP4 contains the actual duration and dimensions. Supports
    veo_fast, veo_quality, veo_lite and omni_flash without changing the tier.
    """
    if not IDENTIFIER.fullmatch(request_id) or not prompt.strip() or model not in {'veo_fast','veo_quality','veo_lite','omni_flash'} or aspect not in {"16:9", "9:16"}:
        return {"status": "error", "detail": "Use a stable request_id, a prompt, a supported video model and 16:9 or 9:16."}
    from flow_contract import normalize
    from flow_inputs import prepare
    try:
        if type(count) is not int or not 1 <= count <= 4:
            raise ValueError('Native blocks support 1-4 outputs')
        params = prepare(normalize({"model": model, "aspect": aspect, "mode": mode, "duration": duration, "resolution": resolution,
            "initial_frame": initial_frame, "end_frame": end_frame, "reference_images": reference_images or [], "voice": voice, 'count': count}))
    except (ValueError, OSError):
        return {"status": "error", "detail": "Invalid mode, duration, resolution or owned input images."}
    params.update(prompt=prompt, count=count)
    with database() as connection:
        try:
            connection.execute("INSERT INTO jobs VALUES (?,?,?,?,?)",
                (request_id, json.dumps(params), None, None, "reserved"))
            existing = False
        except sqlite3.IntegrityError:
            row = connection.execute("SELECT params_json FROM jobs WHERE request_id=?", (request_id,)).fetchone()
            if json.loads(row["params_json"]) != params:
                return {"status": "error", "detail": "request_id already belongs to other parameters."}
            existing = True
    if existing:
        return await flow_get_result(request_id=request_id)
    from gflow_cli.mcp.tools import gflow_generate_video
    from flow_voices import install as install_voices,pending_voice
    install_voices()
    token=pending_voice.set(params.get('voice','').lower() or None)
    try:
        native={k:v for k,v in params.items() if k!='voice'}
        result = await gflow_generate_video(**native, profile="nexus",
            project_name=f"Codex {request_id}", ui_mode="classic", wait=True)
    except Exception as exc:
        result = {"status": "needs_reconciliation", "error_class": type(exc).__name__}
    finally:pending_voice.reset(token)
    task_id = result.get("task_id")
    result = {**result, "request_id": request_id, "requested_model": model, "model_confirmed": False}
    files = result.get("files") or []
    if result.get("status") == "completed" and files:
        try:
            result = complete_block({**result, "path": files[0]}, count)
        except (OSError, ValueError):
            result = {"status": "needs_reconciliation", "request_id": request_id, "task_id": task_id}
    save(request_id, result, task_id)
    if result.get("status") != "completed" and task_id:
        return await flow_get_result(request_id=request_id)
    return result


async def flow_get_quote(model: str = "nano2", aspect: str = "1:1", mode: str | None = None,
                         duration: int | None = None, resolution: str | None = None,
                         initial_frame: str | None = None, end_frame: str | None = None,
                         reference_images: list[str] | None = None,voice: str | None = None, count: int = 1) -> dict[str, Any]:
    """Read the exact account settings price; never uploads or submits media."""
    from flow_quote import quote
    from flow_contract import normalize
    from flow_inputs import prepare
    try:
        if type(count) is not int or not 1 <= count <= 4:
            raise ValueError('Native blocks support 1-4 outputs')
        args = {"model": model, "aspect": aspect, "duration": duration, "resolution": resolution, 'count': count,
            "initial_frame": initial_frame, "end_frame": end_frame, "reference_images": reference_images or [], "voice": voice}
        if mode is not None:
            args['mode'] = mode
        params = prepare(normalize(args))
        return {'status':'quoted',**await quote(**params)}
    except Exception as exc:
        return {'status':'error','error_class':type(exc).__name__,'detail':'No verified account price. No generation sent.'}


async def flow_get_edit_options(source_request_id: str) -> dict[str, Any]:
    """Verify the owned source and native Omni edit availability, without submitting."""
    try:
        from flow_session import ensure
        await ensure()
        from video_edit import source_asset,operate
        source=source_asset(source_request_id)
        if source['kind']!='VIDEO':raise ValueError('Video required')
        return await operate(source)
    except Exception as exc:
        return {'status':'error','error_class':type(exc).__name__,'detail':'Native Omni edit is not confirmed for this clip.'}


async def flow_edit_video(request_id: str,source_request_id: str,prompt: str) -> dict[str, Any]:
    """Edit one existing clip up to 10 s with Omni. Keep the original and reuse the id."""
    if not isinstance(request_id,str) or not IDENTIFIER.fullmatch(request_id) or not isinstance(prompt,str) or not prompt.strip():
        return {'status':'error','detail':'A stable id and an edit prompt are required.'}
    try:
        from video_edit import source_asset
        source=source_asset(source_request_id)
        if source['kind']!='VIDEO':raise ValueError()
    except Exception:return {'status':'error','detail':'Choose a verified completed Flow video.'}
    params={'operation':'edit_video','source_request_id':source_request_id,'prompt':prompt.strip(),
        'source_sha256':source['source_sha256'],'model':'omni_flash'}
    with database() as db:
        try:
            db.execute('INSERT INTO jobs VALUES (?,?,?,?,?)',(request_id,json.dumps(params),None,None,'reserved'))
            existing=False
        except sqlite3.IntegrityError:
            row=db.execute('SELECT params_json FROM jobs WHERE request_id=?',(request_id,)).fetchone()
            if json.loads(row['params_json'])!=params:return {'status':'error','detail':'This id belongs to another edit. No new edit submitted.'}
            existing=True
    if existing:return await flow_get_result(request_id=request_id)
    try:
        from flow_session import ensure
        await ensure()
        from video_edit import operate
        result=await operate(source,prompt.strip(),request_id)
    except Exception as exc:
        with database() as db:
            row=db.execute('SELECT result_json FROM jobs WHERE request_id=?',(request_id,)).fetchone()
        result={**json.loads(row['result_json'] or '{}'),'status':'needs_reconciliation','request_id':request_id,
            'operation':'edit_video','source_request_id':source_request_id,'error_class':type(exc).__name__,
            'detail':'Edit outcome requires reconciliation; this id will not submit again.'}
    save(request_id,result)
    return result
