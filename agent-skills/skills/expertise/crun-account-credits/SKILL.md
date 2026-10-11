---
id: crun-account-credits
kind: expertise
title: crun-account-credits
version: 1
updated: 2026-10-11
applies_to: [IMAGE, VIDEO, AUDIO]
triggers: ["crun", "account credits"]
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


# Crun Account Credits

Use `../../runtime/crun_cli.py`. Let it resolve `CRUN_API_KEY` (the `~/.crun/.env` file first, then the environment variable). If configuration is missing, the error carries `configuration_options`; recommend the first option — the ready-to-run `python "<absolute path>/crun_cli.py" config set-api-key <your_api_key>` command, which persists the key into `~/.crun/.env` — without exposing or requesting the key in chat.

## Check the balance

```text
python <runtime>/crun_cli.py credits
```

Report the numeric `balance` exactly as returned. Do not infer currency or generation count from the balance alone.

## Check affordability

Estimate the final model and exact validated input before every new task:

```text
python <runtime>/crun_cli.py task estimate --model <model> --input-file <input.json>
```

Use `estimated_credits`, `balance`, and `affordable` from the response. Do not submit when `affordable` is false or missing. Re-estimate after changing the model or any price-sensitive input, and before each batch unless the user explicitly accepts partial completion.

When the balance is insufficient, report the current model, estimate, and balance. Obtain explicit permission before proposing a different model, then validate and estimate the replacement. Follow `../crun-task-runner/load_skill: crun-agent-skills` for every creation and authorization gate.
