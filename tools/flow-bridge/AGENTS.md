# Operar Flow desde Codex

Usar `flow_generate_image` o `flow_generate_video` con un request_id único por intención, persistirlo y reutilizarlo ante cualquier interrupción. Leer el estado del resultado y mostrar el archivo validado. Usar `flow_get_result` para recuperar; no repetir una generación cuyo envío pueda haber consumido cuota.

Si estas herramientas aún no están en el catálogo del turno, llamar al cliente MCP de `$HOME/.codex/integrations/google-flow/client.py` con su Python privado. Consultar README.md para argumentos y estado comprobado. El CLI nativo queda como diagnóstico; las generaciones habituales pasan por la capa con identificadores.

No exportar perfiles, cookies, credenciales, URLs firmadas ni logs privados. No presentar el resultado como vídeo validado o como integración con NEXUS Agent. Las nuevas pruebas de generación utilizan cuota; no ejecutar lotes de verificación sin una petición que los justifique.

Modelos habilitados: Nano Banana Pro (preferido), Nano Banana 2, Veo 3.1 Fast, Quality, Lite y Omni 1.1 Flash. Nano Banana 2 Lite sigue prohibido, incluso como fallback. La cuenta y los campos de modelo se comprobaron el 2026-10-06; las solicitudes de inspección se abortaron antes de Google. No afirmar que se completó una generación nueva de cada modelo. Consultar `../../docs/FLOW-MODELOS-DISPONIBLES.md`.
