---
id: crun-task-runner
kind: expertise
title: crun-task-runner
version: 1
updated: 2026-10-11
applies_to: [IMAGE, VIDEO, AUDIO]
triggers: ["crun", "task runner"]
sources:
  - url: https://github.com/CrunTeam/crun-agent-skills
    title: CrunTeam original skills
    accessed: 2026-10-11
---

## Adaptacion nativa para NEXUS Agent Android
Esta version se ejecuta con las herramientas registradas de NEXUS, no con Python ni una shell. Las instrucciones originales que mencionan el CLI, archivos de configuracion o comandos son referencia conceptual: NO pedir al usuario ejecutarlos ni afirmar que se han ejecutado.
- Modelos: `search_models`, `list_models`, `get_model`, `get_model_schema`. Respetar el modelo elegido; no imponer un router. El registro vivo de NEXUS prevalece sobre nombres y catalogos del documento original.
- Coste: `estimate_generation_cost` con la configuracion real. Usar las credenciales Crun ya configuradas en Ajustes; no pedir ni escribir claves en el chat.
- Medios: `search_assets` e `inspect_asset_metadata`; reutilizar referencias del proyecto. Pasar los medios al pipeline nativo, que gestiona su subida. No enviar rutas locales como URLs del proveedor.
- Crear: `prepare_generation` y `submit_generation` mediante el flujo nativo. No crear tarjetas duplicadas ni confirmaciones adicionales a las que ya gestiona la app. Las preferencias explicitas del usuario prevalecen sobre las recomendaciones de confirmacion del texto original.
- Seguimiento: `get_generation_status` con el job_id real; conservarlo y consultar antes de repetir una peticion incierta. No inventar task_id ni resultados.
- Otras habilidades de este paquete: cargar con `load_skill` usando su id, indicado en las referencias de abajo.
- Aplicar el criterio creativo del documento original. Una receta no instala una herramienta nueva: si el registro no dispone de una operacion (por ejemplo un efecto o descarga especifica), indicarlo sin simular exito.

## Guia original Crun adaptada al entorno nativo


# Crun Task Runner

Treat this skill as the authority for task creation, authorization, monitoring, recovery, and result delivery. Use `../../runtime/crun_cli.py`; optionally set `CRUN_BASE_URL` for a non-default endpoint. Never print or persist an API key in task files.

## Prepare media and JSON

Upload a local image, video, or audio file before putting it in a task input:

```text
python <runtime>/crun_cli.py upload <local-file>
```

Put the returned `file_url` in the model input. Never send Base64, data URIs, or local paths. Reuse a prior Crun task's resource URL directly for derivative work.

Prefer UTF-8 files to avoid shell-specific JSON quoting:

```text
python <runtime>/crun_cli.py task estimate --model <model> --input-file <input.json>
```

Use `--input-file -` to read one JSON object from stdin. Use `--input-json` only when the current shell preserves the JSON string exactly.

## Run a task

1. Describe the selected model when its exact input schema is not already known:

   ```text
   python <runtime>/crun_cli.py models describe --model <model>
   ```

2. Construct only fields permitted by `input_schema`.
3. Estimate the final model and exact input before creating any task, including a user-specified model. Require
   `affordable: true`. This matters because `CreateTask` charges credits and is never retried, so submitting an
   unaffordable or malformed task wastes a real charge.
4. If routing selected the model, report the model, estimate, and relevant settings, then wait for explicit confirmation. Do not treat the original generation request as this confirmation. Skip only this extra confirmation when the user explicitly named the model.
5. Submit once and capture the returned `task_id`:

   ```text
   python <runtime>/crun_cli.py task create --model <model> --input-file <input.json>
   ```

6. Continue only with that ID. Prefer a shorter timeout and resume in rounds, because a single long-running command
   can be killed by the host before it returns — losing the recovery snapshot the runtime prints on timeout:

   ```text
   python <runtime>/crun_cli.py task wait --task-id <task_id> --timeout-seconds 120
   ```

   Re-run the same `task wait` to keep polling. Use a longer `--timeout-seconds` only when the command can run in the
   background without being interrupted.

Keep `task run` and `media run` only for compatibility or deliberate one-shot use. Both create a task directly and do
not estimate affordability or satisfy the routed-model confirmation gate; estimate and confirm yourself before using
them, and do not use them as the default agent workflow. Preserve their flushed `task_created` event so an
interruption never causes a duplicate submission.

## Resume and recover

Inspect an existing task with `task status --task-id <task_id>` or resume it with `task wait --task-id <task_id>`. Treat `pending` and `running` as nonterminal, `success` as successful, and `failed` as terminal.

Never inspect `~/.crun/output/yyyy-mm-dd/` to discover a task ID or infer remote state. It is only a download cache, with each task stored in its own `task_id` subdirectory. If no ID was captured, report that instead of guessing.

When local polling times out:

1. Show `details.last_task`, including at least `task_id` and `status` plus useful available fields. Say explicitly when no remote snapshot was captured.
2. Ask the user to choose between continuing the original task and creating a new task. Warn that a new task does not cancel the original, both may complete, and additional credits may be charged.
3. Stop until the user explicitly chooses. Do not automatically query, wait, or create again.

If the user continues, call `task wait` with the original ID. If the user authorizes a new task, validate and estimate it again, verify affordability, and create exactly once. Keep the original ID in the response.

When a task reaches terminal `failed`, report its ID, model, and error, then obtain explicit permission before any replacement or retry. When an estimate is unaffordable, report the model, estimate, and balance, then obtain explicit permission before changing models; validate and estimate the replacement before creation.

## Reliability rules

- Inspect the JSON response `code`, not only HTTP status.
- Never automatically retry `CreateTask`; an uncertain retry can create a duplicate charged task.
- Retry only safe requests and TaskInfo network failures within the configured limits.
- Never change a model or drop rejected input fields silently.
- Treat a task ID handoff as evidence of creation, not completion.
- Treat only TaskInfo for the captured ID as authoritative task state.

## Deliver results

Return normalized `task_id`, `status`, `model`, `credits`, `local_media_paths`, `media_urls`, `media_info`, `suno_data`, `usage`, and `error`, preserving useful specialized result fields.

Prefer local media paths. Where the client renders inline media, preview images or videos with an absolute
forward-slash path such as `![result](/C:/path/to/file.png)`. Use remote URLs as fallback and identify any download
errors instead of claiming a failed download is local.
