# Operar Flow desde Codex

Usar `flow_generate_image` con un request_id único por intención, persistirlo y reutilizarlo ante cualquier interrupción. Leer el estado del resultado y mostrar el archivo validado. Usar `flow_get_result` para recuperar; no repetir una generación cuyo envío pueda haber consumido cuota.

Si estas herramientas aún no están en el catálogo del turno, llamar al cliente MCP de `$HOME/.codex/integrations/google-flow/client.py` con su Python privado. Consultar README.md para argumentos y estado comprobado. El CLI nativo queda como diagnóstico; las generaciones habituales pasan por la capa con identificadores.

No exportar perfiles, cookies, credenciales, URLs firmadas ni logs privados. No presentar el resultado como vídeo validado o como integración con NEXUS Agent. Las nuevas pruebas de generación utilizan cuota; no ejecutar lotes de verificación sin una petición que los justifique.
