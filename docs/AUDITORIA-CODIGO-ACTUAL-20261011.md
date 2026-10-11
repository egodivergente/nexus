# Auditoría de apoyo sobre la versión actual

Revisión del 11 de octubre de 2026, solicitada por el usuario para ayudar al hilo `01a118e5-7a95-7b21-a22d-1724e15d35b8` mientras implementa novedades. Se revisó en lectura `C:\Users\User\.codex\worktrees\nexus-android-parity\NEXUS`, rama `codex/studio-foundations-canvas`, base publicada `3f305f8b94b827e3fc20f8039cbad8089f758e6a` y sus cambios pendientes. Esos cambios están en desarrollo; los hallazgos describen el momento de la revisión y requieren comprobación tras su corrección. Esta nota se conserva en la rama del rediseño anterior para no modificar el worktree de la tarea activa.

El S25 se comprobó mediante ADB con `1.8.7-agent-preview`, versionCode 31. No se instaló una APK desde esta revisión. No se llamó a AWS, Atlas ni a ningún servidor MCP real. Se transmitieron los hallazgos al hilo indicado, con autorización del usuario.

## Hallazgos enviados

| Prioridad | Hallazgo y recorrido | Evidencia | Estado |
|---|---|---|---|
| P1 | El proxy no decodifica `document.source.bytes`. `ConverseCodec` envía base64 JSON; `NexusProxy._decode_message` sólo convierte imágenes y pasa el documento como `str` al SDK, en vez de sus bytes originales. | Fixture PDF local: `build_request` devuelve `str`, distinto del contenido binario. SHA-256 del proxy probado: `1f90fbc1dea015a7c2754270e46c66b055ff5e35385c6a3c103aedd3e4931bb9`. | Reproducido localmente; enviado para corrección. |
| P1/P2 | Los límites de adjuntos y transporte no coinciden. Android admite hasta cinco documentos de 4.500.000 bytes; el proxy admite cuerpos de 8.388.608 bytes. | Dos documentos dentro del límite individual producen un JSON de 12.000.313 bytes, HTTP 413 y cero llamadas al upstream falso. | Reproducido localmente; enviado para corrección. |
| P1 | `call_mcp_tool` es ORGANIZE y se ejecuta directamente en modo AGENT. `AgentInternet` reenvía cualquier nombre y argumentos a `tools/call`, sin la revisión de coste de `SpendGuard`/`GuardedExecutor` ni la tarjeta GENERAR. | Trazado `NativeOperationTools → AgentOrchestrator.runTool → AgentInternet.run → McpSession.call`. El MCP de Atlas documenta herramientas de generación. Las reglas vigentes exigen GENERAR para esos proveedores. | Ruta verificada en código; no se ejecutó una operación externa o de pago. |
| P1 | El selector compartido ahora llama siempre a `AgentMediaStore.persist`, cuyo filtro acepta imagen, vídeo o documento y rechaza `audio/*`. | `CreateScreen` usa `contractPicker.launch(arrayOf(field.mime))`; `RunwayContracts` contiene campos reales `referenceAudio`, incluidos modelos Hailuo/Seedance. Antes, los tipos distintos de imagen/vídeo podían volver directamente al formulario. | Regresión verificada en el recorrido del código; prueba en dispositivo pendiente. |
| P2 | Los documentos antiguos permanecen como `ByteArray` en el contexto y se reenvían en cada turno. | `AgentOrchestrator.trimHistory` reemplaza imágenes antiguas, pero no documentos; conserva hasta 60 mensajes. Esto hace crecer memoria y petición aun sin nuevos adjuntos. | Retención verificada en código; consumo y límites en dispositivo no medidos. |
| P2 | Un resultado MCP con `isError=true` y HTTP 200 termina marcado como herramienta exitosa. | `AgentInternet.rpc` devuelve el objeto; `NativeTool.execute` lo envuelve con `ok`, y `AgentOrchestrator` registra éxito. | Propagación verificada en código; prueba con servidor falso pendiente. |

La falta de validación del nombre/schema descubierto en `call_mcp_tool` se comunicó junto al hallazgo de revisión de coste. Un nombre declarado seguro por un servidor externo no constituye autorización de gasto. La solución debe conservar herramientas de consulta y las capacidades solicitadas, con una política explícita para operaciones externas y los contratos de autorización existentes.

La normalización MIME añadida durante la revisión corrige el caso de PDF importado como `application/octet-stream`, que podía no aparecer en Recientes. Se comprobó su presencia en el código posterior y no se presenta como fallo pendiente. Su prueba funcional sigue pendiente.

## Comprobaciones

Las 13 pruebas existentes de `agent-proxy/test_nexus_proxy.py` terminaron en **OK**, en 0,136 s, sobre una copia privada con cliente Bedrock falso. No cubren documentos; por eso esos tests en verde no invalidan los dos fallos reproducidos. Los scripts, fixtures y logs de apoyo permanecen en `outputs/ui-minimal-20261009/current-code-audit`, fuera de Git.

Esta auditoría no modifica lógica del agente, proveedores, cobro, persistencia ni la interfaz de la tarea activa. Las correcciones y su validación corresponden al hilo en curso. La adaptación del rediseño seguirá desde su fuente publicada cuando termine.

## Fuentes técnicas

- [AWS DocumentSource](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentSource.html): el SDK recibe bytes originales del documento.
- [AWS DocumentBlock](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentBlock.html): formatos y nombre del bloque.
- [MCP oficial de Atlas](https://www.atlascloud.ai/docs/mcp-server): lista de herramientas, incluidas las de generación y consulta.
