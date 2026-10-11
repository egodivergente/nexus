---
id: crun-agent-skills
kind: expertise
title: crun-agent-skills
version: 1
updated: 2026-10-11
applies_to: [IMAGE, VIDEO, AUDIO]
triggers: ["crun", "agent skills"]
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


# Crun Media Agent Skills

Use this skill as the entry point for Crun media work. Resolve all commands from this directory:

- `runtime/crun_cli.py` handles model discovery, credit checks, task creation, status, polling, local-file upload, and
  generated-media downloads. Upload with `crun_cli.py upload <local-file>`.
- `catalog/models.json` is the local routing catalog.
- `skills/` contains the core pipeline skills, and `skills/scenarios/` the scenario skills, listed below; read the
  relevant child `load_skill: crun-agent-skills` before performing that part of the workflow.

## Select the workflow

**Core pipeline skills** — the shared infrastructure every request flows through (route → estimate → create/monitor):

| Need                                        | Read and use                           |
|---------------------------------------------|----------------------------------------|
| Choose a model from a broad request         | `load_skill: crun-model-router`    |
| Check balance or affordability              | `load_skill: crun-account-credits` |
| Create, monitor, resume, or retrieve a task | `load_skill: crun-task-runner`     |

**Scenario skills** (`skills/scenarios/`) — matched by user intent; each one composes the core pipeline internally:

| Need                                                                | Read and use                                            |
|---------------------------------------------------------------------|---------------------------------------------------------|
| Generate static image or animated GIF memes                         | `load_skill: crun-meme-generator`         |
| Generate multi-panel educational comics & storyboards               | `load_skill: crun-educational-comic`      |
| Enhance an uploaded image or video                                  | `load_skill: crun-media-enhancer`         |
| Enhance character action, pose & camera motion (T2I, I2I, T2V, I2V) | `load_skill: crun-action-camera-enhancer` |
| Generate character reference sheets                                 | `load_skill: crun-character-reference`    |
| Replicate, restyle, or remake photos (portrait, pose, style)        | `load_skill: crun-photo-replication`      |
| Discover and apply Kling, Vidu, or ByteDance effect templates       | `load_skill: crun-effect-template`        |
| Generate promotional images or videos directly from website/product URL | `load_skill: crun-url-promo-generator`  |

For a broad end-to-end request — the common case where the user describes the media they want but does not know Crun
model names or payloads — follow the "Orchestrate safely" steps below, reading each child skill as that step needs it.
Treat `crun-task-runner` as the single authority for task creation, authorization gates, timeout recovery, failure
handling, and result delivery. Read it before creating any task.

## Pass JSON portably

Prefer a UTF-8 JSON file on Windows and whenever shell quoting is uncertain:

```text
python <skill-root>/runtime/crun_cli.py task estimate --model <model> --input-file <input.json>
python <skill-root>/runtime/crun_cli.py models route --intent-file <intent.json>
```

Use `-` instead of a path to read that JSON object from stdin. Use `--input-json` or `--intent-json` only when the
current shell can preserve the JSON string exactly. Each JSON source must contain one object.

Upload a new local input image, video, or audio file before constructing model input:

```text
python <skill-root>/runtime/crun_cli.py upload <local-file>
```

Use the returned `file_url`. Never send Base64, data URIs, or local paths as Crun media inputs. For derivative work,
reuse the resource URL returned by the earlier Crun task instead of downloading and uploading it again.

## Orchestrate safely

1. Identify the output modality and operation. Collect only indispensable inputs, and require source media for edits,
   references, lip-sync, face-swap, watermark-removal, voice-cloning, and similar transformations.
2. Upload only new local source media (`crun_cli.py upload`); reuse an existing Crun resource URL directly.
3. Route only when the user did not specify an exact model (`crun-model-router`), then inspect the selected model's live
   schema and construct only supported input fields.
4. Estimate the exact model input before every new task (`crun-account-credits`). For a routed model, report the model,
   estimated credits, and relevant settings, then obtain explicit confirmation. Skip only this extra confirmation when
   the user explicitly named the model — never treat the original generation request as the confirmation.
5. Create once with `task create`, capture the returned `task_id`, and continue only with `task wait` or `task status`
   using that ID.
6. Follow `crun-task-runner` for all failures, timeouts, retries, and result delivery.

Use balanced quality and speed when the user has no preference. Ask about native audio only when it changes the viable
models, and do not ask the user to choose a provider unless the alternatives have materially different tradeoffs.

Always estimate affordability before creating any task, including tasks for a user-specified model. Require
`affordable: true` from `task estimate` before `task create`. The reason is that `CreateTask` charges credits and is
never retried, so an unaffordable or malformed submission wastes a real charge that cannot be undone.

Keep `task run` and `media run` only for compatibility or deliberate one-shot use. They create a task directly, so
estimate affordability yourself beforehand and confirm a routed model first — they do not gate on either. Do not use
them as the default agent workflow.

## Handle sensitive media

Before transforming a real person's face or voice, or removing a watermark, require confirmation that the user owns or
is authorized to transform the source. Preserve any disclosure or labeling request. Refuse impersonation, fraud,
non-consensual sexual content, and unauthorized removal of an ownership mark.

Keep API keys out of task payloads, output, and committed files. Let the runtime resolve `CRUN_API_KEY` in this order:
the `~/.crun/.env` file first, then the `CRUN_API_KEY` environment variable. When no key is configured, the runtime
returns `configuration_options` — an ordered list with a permanent setup command for each method split by platform
(`macos_linux`, `windows_cmd`, `windows_powershell`). Recommend the first option: the CLI's own
`python "<absolute path to runtime>/crun_cli.py" config set-api-key <your_api_key>` command (already fully resolved in
the
payload), which validates the key and persists it into `~/.crun/.env` so it works across sessions on every platform.
