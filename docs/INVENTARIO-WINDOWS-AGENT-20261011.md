# Inventario de Nexus Agent en Windows

La tabla registra ubicaciones y alcance. Las herramientas son las mismas de Android y un extra Windows; la validación no implica haber ejecutado una generación pagada con cada proveedor.

| Función de UI | Ubicación Windows | Acceso |
|---|---|---|
| Proyecto / cambiar o crear | Asistente → selector o Proyecto | 1 |
| Memoria, personajes y notas | Asistente → Proyecto | 1 |
| Referencias e importación | Asistente → Archivos de referencia | 1 |
| Exportar ZIP / selección / borrado lógico | Proyecto → Archivos del proyecto | 2 |
| Conversaciones archivadas / restaurar | Asistente → Conversaciones → conversación | 2 |
| Plan histórico y reutilización | Conversación → Preparar en el lienzo | 2 |
| Modo Asistente/Copiloto/Agente | Selector superior del Asistente | 1 |
| Modelo / conexión / presupuesto / claves | Ajustes → Modelo y conexión | 2 |
| Prompt y adjuntos | Entrada del Asistente o Lienzo | 1 |
| Plan: generar, editar, lienzo, descartar, resultados | Tarjeta de encargo del Asistente | 1 |
| Selector, búsqueda, familias y favoritos | Modelos / selector del Lienzo | 1 |
| Modelos ocultos y parámetros | Modelos → menú / variante | 2 |
| Prompt, presets, medios y parámetros | Lienzo; panel plegable de parámetros | 2 |
| Cotización y generación | Barra del Lienzo | 1 |
| Comparación y lotes de generación | Lienzo → Crear lote o comparar | 2 |
| Historial, búsqueda y estados | Historial | 1 |
| Reutilización / recuperación / diagnóstico / lote | Historial → ··· | 2 |
| Saldo, última consulta, actualización y costes | Chip de saldo de la cabecera | 1 |
| Galería, filtros, favoritos y colecciones | Biblioteca | 1 |
| Comparación A/B de resultados | Biblioteca → seleccionar → Comparar A/B | selección + 1 |
| Visor / animar / referencia / revisión / descarga | Biblioteca → resultado | 2 |
| Edición, fotogramas, audio y Whisper | Biblioteca → Edición local | 1 |
| Renombrar / convertir / eliminar de biblioteca | Archivo del proyecto → acción | 2 |
| Encargos locales y estado | Asistente o Biblioteca → Encargos locales | 1 |
| MCP / conectar / descubrir / desconectar | Ajustes → Internet y MCP | 2 |
| Flow: conexión, opciones, referencias y automatización | Controles Flow existentes en Lienzo/Ajustes | 2 |
| Tema, moneda, letra y avisos | Ajustes | 1 |
| Diagnóstico del sistema | Ajustes → Diagnóstico | 2 |
| Alexia | Biblioteca → Abrir Alexia en S25 | 1 |

## Toolkit disponible

| Herramienta | Implementación |
|---|---|
| `list_models` | Toolkit compartido / operación Windows equivalente |
| `search_models` | Toolkit compartido / operación Windows equivalente |
| `get_model` | Toolkit compartido / operación Windows equivalente |
| `get_model_schema` | Toolkit compartido / operación Windows equivalente |
| `recommend_models` | Toolkit compartido / operación Windows equivalente |
| `validate_parameters` | Toolkit compartido / operación Windows equivalente |
| `estimate_generation_cost` | Toolkit compartido / operación Windows equivalente |
| `list_skills` | Toolkit compartido / operación Windows equivalente |
| `load_skill` | Toolkit compartido / operación Windows equivalente |
| `create_generation_prompt` | Toolkit compartido / operación Windows equivalente |
| `compare_model_families` | Toolkit compartido / operación Windows equivalente |
| `prepare_generation` | Toolkit compartido / operación Windows equivalente |
| `update_plan` | Toolkit compartido / operación Windows equivalente |
| `submit_generation` | Toolkit compartido / operación Windows equivalente |
| `get_generation_status` | Toolkit compartido / operación Windows equivalente |
| `cancel_generation` | Toolkit compartido / operación Windows equivalente |
| `list_projects` | Toolkit compartido / operación Windows equivalente |
| `create_project` | Toolkit compartido / operación Windows equivalente |
| `open_project` | Toolkit compartido / operación Windows equivalente |
| `update_project_memory` | Toolkit compartido / operación Windows equivalente |
| `search_assets` | Toolkit compartido / operación Windows equivalente |
| `inspect_asset_metadata` | Toolkit compartido / operación Windows equivalente |
| `list_shots` | Toolkit compartido / operación Windows equivalente |
| `get_generation_history` | Toolkit compartido / operación Windows equivalente |
| `associate_asset_to_project` | Toolkit compartido / operación Windows equivalente |
| `move_asset` | Toolkit compartido / operación Windows equivalente |
| `rename_asset` | Toolkit compartido / operación Windows equivalente |
| `web_search` | Toolkit compartido / operación Windows equivalente |
| `read_web_page` | Toolkit compartido / operación Windows equivalente |
| `list_mcp_servers` | Toolkit compartido / operación Windows equivalente |
| `connect_mcp_server` | Toolkit compartido / operación Windows equivalente |
| `list_mcp_tools` | Toolkit compartido / operación Windows equivalente |
| `call_mcp_tool` | Toolkit compartido / operación Windows equivalente |
| `disconnect_mcp_server` | Toolkit compartido / operación Windows equivalente |
| `get_active_generations` | Toolkit compartido / operación Windows equivalente |
| `recover_generation` | Toolkit compartido / operación Windows equivalente |
| `diagnose_flow` | Toolkit compartido / operación Windows equivalente |
| `repair_flow` | Toolkit compartido / operación Windows equivalente |
| `get_nexus_settings` | Toolkit compartido / operación Windows equivalente |
| `update_nexus_settings` | Toolkit compartido / operación Windows equivalente |
| `rename_asset_file` | Toolkit compartido / operación Windows equivalente |
| `convert_image_asset` | Toolkit compartido / operación Windows equivalente |
| `inspect_media_asset` | Toolkit compartido / operación Windows equivalente |
| `get_whisper_status` | Toolkit compartido / operación Windows equivalente |
| `prepare_whisper_model` | Toolkit compartido / operación Windows equivalente |
| `edit_video_assets` | Toolkit compartido / operación Windows equivalente |
| `extract_video_frames` | Toolkit compartido / operación Windows equivalente |
| `transcribe_media_asset` | Toolkit compartido / operación Windows equivalente |
| `get_media_edit_job` | Toolkit compartido / operación Windows equivalente |
| `atlas_get_balance` | Toolkit compartido / operación Windows equivalente |
| `get_model_brief` | Adaptador Windows adicional |
