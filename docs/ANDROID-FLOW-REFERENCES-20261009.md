# Referencias de Flow en Nexus Agent Android

## Fallos corregidos

La versión anterior describía Flow como TEXT_ONLY, rechazaba mediaInputs y el envío del agente construía GenerationRequest sin plan.media. El conocimiento del agente también decía que las referencias todavía no se enviaban.

Ahora el esquema publica ingredientes de imagen opcionales. El agente conserva las imágenes seleccionadas, sube los archivos al puente privado y persiste todos sus identificadores en la cola, incluidos los bloques posteriores. Los errores de referencias detienen el encargo antes de generar; no se sustituye el pedido por texto solamente ni por otro proveedor.

El puente recibe archivos mediante una ruta autenticada, con transferencia limitada y escritura atómica. Verifica tipo PNG/JPEG/WebP, tamaño e integridad SHA-256. Utiliza File/DataTransfer del navegador para adjuntarlos al compositor de Flow y exige ver todas las miniaturas dentro del compositor antes de pulsar Generar. Un request_id repetido recupera el encargo; si cambian prompt, salida o referencias, rechaza el conflicto mediante un hash de intención. El diario no almacena prompts ni credenciales.

Los límites de 12 imágenes y 16 MB por archivo son protecciones del transporte de Nexus, no límites documentados de Google. Esta ruta admite ingredientes de imagen, conserva los ajustes del proyecto y no controla fotogramas de inicio/final ni vídeo de entrada.

## Validación

- PASS: 105 tests JVM del núcleo y 245 de AgentPreview.
- PASS: 33 tests del puente, incluido arrastre de dos archivos en Chromium real sobre un compositor de prueba; las imágenes de galería no cuentan como adjuntos. Esta prueba no es una generación de Google.
- PASS: compilación AgentPreview. Se comprobó ASUSVIDAL primero; su memoria y metaspace limitaron la compilación. Se detuvo únicamente el daemon identificado de esta tarea y se completó en Windows con prioridad baja, un worker y dos procesadores activos.
- PASS: puente del ASUS desplegado con archivos idénticos a estas fuentes y respaldo del código anterior. No había generaciones activas en su diario al reiniciar.
- PASS: actualización real del Samsung S25, SM-S938B, usuario 0, mediante ADB Tailscale. Nexus Agent 1.8.4-agent-preview, versionCode 28, paquete com.vidadomu.nexus.agent.
- PASS: certificado coincidente con la app anterior: 8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127.
- PASS: SHA-256 del APK firmado e instalado: b9ad1bc5631bdfceb4946eafd1958e29ef304da2bf880406010e1f228ae32110.
- PASS: 110 trabajos y 97 resultados; base de datos, preferencias y datos del asistente idénticos byte a byte entre antes y después de instalar, antes del primer arranque. Se verificó integrity_check=ok.
- PASS: prueba en la APK instalada: supportsImageInput=true, supportsReferenceImages=true, campo reference_ids y subida efectiva de un PNG al puente con identificador válido. La imagen de prueba se creó en caché y se eliminó al acabar. El APK auxiliar también se retiró.
- PASS: modo AGENT y modelo Opus 4.6 conservados.
- PENDIENTE: verificación del arrastre dentro del proyecto real de Google y generación con referencias. La sesión del navegador caducó; la credencial guardada también estaba fuera de su ventana de validez. Se abrió el portal y el usuario está reconectando. La última comprobación mostraba proyectos que no se podían cargar y generation_ready=false. No se ha generado ni consumido cuota para esta prueba.

Los APK, respaldos, informes privados y firmas permanecen fuera de Git, en outputs/android-agent-recovery-20261009/flow-references del workspace original.

Documentación del producto: https://support.google.com/flow/answer/16729550 y https://support.google.com/flow/answer/16353334. Google documenta referencias e ingredientes; esas páginas no prueban por sí mismas la integración de esta APK.
