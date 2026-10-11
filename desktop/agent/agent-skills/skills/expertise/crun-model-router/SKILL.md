---
id: crun-model-router
kind: expertise
title: crun-model-router
version: 1
updated: 2026-10-11
applies_to: [IMAGE, VIDEO, AUDIO]
triggers: ["crun", "model router"]
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


# Crun Model Router

Use `../../runtime/crun_cli.py` and `../../catalog/models.json`. Treat the catalog as the source of truth for routing labels and priority, and the authenticated Models endpoint as the source of truth for the selected model's current input schema.

## Build routing intent

Normalize the request into one JSON object:

```json
{
  "modality": "image|video|audio",
  "operation": "text-to-image|image-edit|text-to-video|image-to-video|reference-to-video|text-to-speech|music-generate|...",
  "quality": "balanced|best",
  "speed": "balanced|fast",
  "native_audio": false,
  "reference_media": []
}
```

Preserve explicit constraints. Infer the operation from supplied media only when unambiguous.

## Route and inspect

Prefer a JSON file or stdin for portable invocation:

```text
python <runtime>/crun_cli.py models route --intent-file <intent.json>
python <runtime>/crun_cli.py models describe --model <selected-model>
```

Use `selected` by default. Present `alternatives` only when their tradeoffs matter or the user asks. Read `required_input_fields` and `input_schema`, then ask only for required information that cannot be derived. Never invent fields or copy a sibling model's schema.

Use `models list` when the static candidate is rejected or missing. Never infer capability or priority from a model name or schema; update `models.json` explicitly. Report a hard-constraint conflict instead of weakening it silently.

After routing, hand off affordability checks to `../crun-account-credits/load_skill: crun-agent-skills` and all creation or monitoring work to `../crun-task-runner/load_skill: crun-agent-skills`.
