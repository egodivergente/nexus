# Operar Flow desde Codex

Usar `flow_generate_image` o `flow_generate_video` con un request_id único por intención, persistirlo y reutilizarlo ante cualquier interrupción. Leer el estado del resultado y mostrar el archivo validado. Usar `flow_get_result` para recuperar; no repetir una generación cuyo envío pueda haber consumido cuota.

Si estas herramientas aún no están en el catálogo del turno, llamar al cliente MCP de `$HOME/.codex/integrations/google-flow/client.py` con su Python privado. Consultar README.md para argumentos y estado comprobado. El CLI nativo queda como diagnóstico; las generaciones habituales pasan por la capa con identificadores.

No exportar perfiles, cookies, credenciales, URLs firmadas ni logs privados. Nano Banana Lite está prohibido por el usuario, también como fallback. Solo Nano Banana 2 y Veo Fast están habilitados. La validación real y sus límites están en README.md: gateway de NEXUS probado, conversación LLM y Android no probados. Las nuevas pruebas de generación utilizan cuota; no ejecutar lotes de verificación sin una petición que los justifique.

Para mejorar un resultado existente, consultar `flow_get_asset_options(source_request_id)` y enviar `flow_process_asset` con otro request_id estable, la resolución habilitada y el importe exacto en `expected_credits`. Imagen: 2k/4k; vídeo: 1080p/4k; gif exporta el vídeo existente. Guardar el original. Un resultado ambiguo se consulta con flow_get_result; nunca se repite el clic ni se regenera el prompt. No presentar 4K de vídeo como probado: su precio se leyó (50 puntos), pero la prueba de pago no se ejecutó.
