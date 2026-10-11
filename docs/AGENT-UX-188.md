# NEXUS Agent 1.8.8 — acciones y continuidad

## Cambios
- Biblioteca: mantener pulsado o botón de opciones; abrir, favorito, copiar prompt, compartir archivo local/enlace, ocultar con Deshacer y vista Ocultos para recuperar después. Los archivos no se borran.
- Selector compartido: Recientes globales y referencias del proyecto. Copia privada de importaciones también sin proyecto; orden por último uso y filtro por formatos admitidos en cada campo.
- Chat: sugerencias de modelos exclusivamente con @, inserción en el cursor; cálculo de scroll con elementos visibles y ajuste al pie de mensajes largos.
- Documentos PDF, DOC/DOCX, TXT, Markdown, HTML, CSV y XLS/XLSX enviados como bloques nativos de Converse. Copias privadas reutilizables; se conserva el borrador ante límites reales de Bedrock (5 documentos por mensaje, 4,5 MB por documento). No hay extracción local, OCR local ni conversión silenciosa.
- Internet: web_search (Bing RSS), read_web_page (texto HTML y enlaces, sin JavaScript ni sesión privada).
- Herramientas externas: conectar, listar, invocar y desconectar MCP Streamable HTTP. Initialize, notificación initialized, sesión y respuesta JSON/SSE. Conexiones persistentes; token Bearer cifrado y asociado al endpoint. El agente puede descubrir y conectar herramientas dentro del mismo turno. Ajustes del agente incluye gestión manual.

## Límites de la entrega
MCP no instala binarios Android ni aporta las credenciales de Codex. No se implementa OAuth interactivo, stdio, antiguo SSE separado ni solicitudes del servidor hacia el cliente (sampling/elicitation). El cliente anuncia capacidades vacías y cierra sus sesiones al terminar. No reintenta invocaciones automáticamente. Las respuestas web/MCP se tratan como datos externos.

La búsqueda pública devolvió HTTP 200 y siete resultados en una comprobación desde Windows. Esto no equivale a una prueba del agente en el S25. Compilación y estado de instalación se comunican por separado; no se han realizado generaciones de pago ni una medición de 120 Hz.

## Contratos consultados
- https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentBlock.html
- https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Message.html
- https://modelcontextprotocol.io/specification/2025-03-26/basic/transports
- https://modelcontextprotocol.io/specification/2025-03-26/basic/lifecycle
- https://modelcontextprotocol.io/specification/2025-03-26/server/tools

## Correcciones de compatibilidad
- Importacion persistente de audio para campos como referenceAudio; conserva MIME y extension.
- Proxy Converse: decodifica documentos a bytes para boto3, valida formato/rol/texto/tamano y admite cuerpos de hasta 40 MiB por defecto. Una configuracion existente con max_request_bytes explicito conserva su limite. Presupuestos monetarios y de tokens sin cambios. Requiere desplegar el proxy actualizado si se usa ese backend.
- Contexto: conserva hasta cinco documentos recientes distintos; los anteriores dejan un marcador explicito de contenido retirado.
- MCP: verifica nombre y campos obligatorios contra el catalogo actual, solo invoca consultas declaradas readOnlyHint, excluye acciones de generacion/escritura y propaga isError como fallo. Las anotaciones del servidor son indicativas, no una garantia de comportamiento del servidor; conectar fuentes de confianza. No se implementa validacion completa de JSON Schema.
- Incluye las doce skills Crun adaptadas al pipeline Android y su licencia original.

- Proxy: documentos usan CountTokens con mensajes, sistema y herramientas para reservar presupuesto por tokens reales, no por bytes base64. Si no esta disponible conserva la reserva anterior e informa el motivo cuando supera el limite. Fuentes: https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_CountTokens.html y https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseTokensRequest.html .

## Entrega verificada en S25 (2026-10-11)
- ASUS: assembleAgentPreview correcto en 6 min 54 s, un worker, prioridad nice 15.
- APK transferido con SHA256 coincidente; firmado con la clave existente. SHA256 del APK firmado: 625d0149cf71b70dbc3adc53fcb23946f069c1e72a32116a28945ecafa0e57db.
- Instalacion adb install --no-streaming -r: Success. Samsung S25: versionCode 32, versionName 1.8.8-agent-preview. Arranque en frio Status ok.
- Ajustes del agente: 736 modelos, 46 skills, cero errores de skills. Backend activo: Bedrock directo.
- Comprobacion manual por ADB: Biblioteca muestra 149 archivos / 138 creaciones; pulsacion larga abre Abrir, Marcar favorito, Copiar prompt, Compartir archivo y Ocultar. Proyecto Mateo conserva 176 referencias.
- Proxy: 18 pruebas locales con Bedrock simulado correctas. No se desplego un proxy remoto (el S25 usa directo).
- Sin generaciones ni inferencia pagadas para validar. Pendientes de prueba real: lectura de documentos por el LLM, recorrido web/MCP del agente y rendimiento a 120 Hz. No se afirma validacion completa de accesibilidad ni de todos los gestos.
