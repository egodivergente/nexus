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

## Reverificación del seguimiento: 11 de octubre, 03:51 UTC

El hilo continúa activo. La rama publicada ha avanzado a `fd5f71b9432bfbaa7f2a8a1585ae1f6d8bf8db53` (habilidades Crun); la entrega 1.8.8 sigue pendiente. Esta comprobación lee sus cambios en curso y no modifica sus fuentes ni inicia compilaciones.

| Hallazgo anterior | Comprobación nueva | Límite de la conclusión |
|---|---|---|
| Decodificación de documentos | **PASS local**: el proxy devuelve los bytes originales de un documento de prueba. | Cliente Bedrock falso; no prueba del proxy desplegado ni de AWS. |
| Tamaño del cuerpo | **PASS local**: dos y cinco documentos de 4.500.000 bytes generan cuerpos de 12.000.297 y 30.000.537 bytes y alcanzan el upstream falso con el nuevo límite de 41.943.040 bytes. | Presupuesto amplio sólo en el fixture; la reserva predeterminada presenta la restricción descrita debajo. |
| Audio importado | El filtro admite `audio/` y el registro usa `AssetType.AUDIO`. | Corrección presente en código; importación real pendiente. |
| Contexto binario | La poda conserva hasta cinco documentos distintos y sustituye los anteriores por marcadores de procedencia. | Código revisado; memoria y comportamiento conversacional no medidos. |
| MCP y revisión de coste | Se descubre el nombre en el catálogo, se exigen argumentos obligatorios y `readOnlyHint=true`, y se bloquean nombres con verbos de modificación o generación. | Política cambiada; no se ha validado funcionalmente ni se considera una garantía basada sólo en anotaciones de un servidor externo. |
| MCP `isError` | Se convierte `isError=true` en `ToolInputException`. | Propagación corregida en código; prueba con servidor MCP falso y S25 pendiente. |

Nuevo límite reproducido: con `daily_token_budget=2.000.000` predeterminado, un único documento permitido de 4,5 MB produce un cuerpo de 6.000.217 bytes. La reserva `len(raw)//3 + maxTokens` estima 2.000.172 tokens y rechaza la petición con HTTP 402, con cero llamadas al upstream. La aceptación del transporte no garantiza la aceptación presupuestaria. Se comunicaron los datos al hilo para informar esta restricción en la experiencia de adjuntos o ajustar la estimación conservando los controles de presupuesto; no se cambió la política desde esta auditoría.

La copia probada del proxy tiene SHA-256 `1c6af66fb3169046459edc0357014a67df5180f06e1e9644ecf22871be6522b0`. Las **16 pruebas** de la suite copiada terminaron en **OK**, en 0,689 s. Evidencia privada adicional: `outputs/ui-minimal-20261009/current-code-audit/recheck-20261011-0351/results.json`. Sin llamadas externas, operaciones de pago ni instalación.

## Fuentes técnicas

- [AWS DocumentSource](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentSource.html): el SDK recibe bytes originales del documento.
- [AWS DocumentBlock](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentBlock.html): formatos y nombre del bloque.
- [MCP oficial de Atlas](https://www.atlascloud.ai/docs/mcp-server): lista de herramientas, incluidas las de generación y consulta.
