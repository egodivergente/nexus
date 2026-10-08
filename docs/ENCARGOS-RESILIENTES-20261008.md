# Encargos por modelo: adaptación y continuidad

La elección del usuario es resumir automáticamente cuando un prompt supera el límite real del modelo y conservar el original.

- El límite procede del esquema del proveedor o de un contrato revisado. No se impone una longitud universal.
- El asistente configurado reescribe únicamente el prompt del modelo afectado. Mantiene referencias, sujetos, acciones, composición y restricciones, sin herramientas de generación. Se valida la longitud de su respuesta completa; nunca se recorta texto por posiciones.
- El original permanece en el borrador de origen, en el plan nativo, en las solicitudes del encargo y en el trabajo guardado. La interfaz permite desplegarlo junto al resumen.
- Los parámetros se adaptan con sus opciones y rangos reales. Un prompt largo no provoca copiar valores incompatibles desde el modelo de origen.
- Los modelos listos pueden enviarse aunque otro necesite una referencia, un ajuste o una adaptación. El historial conserva las solicitudes sin enviar y su motivo.
- Cada fallo de envío queda asociado a su solicitud. Las restantes continúan. Los envíos ambiguos no se reintentan automáticamente.
- Se mantienen hasta 20 solicitudes por modelo, hasta tres envíos simultáneos y una cola compartida para Flow.
- Los cambios actualizan la estimación automáticamente. Una estimación caducada en una cola larga o pausada se consulta de nuevo antes de enviar esa solicitud.
- Si el proveedor no informa del precio, se muestra **Coste no disponible** y el envío sigue disponible. Se transmite `cost: null` con `estimate_status: unavailable`, sin inventar un precio cero ni una conversión de créditos a dólares.

## Límites revisados

Atlas utiliza `minLength`/`maxLength` del esquema Input. Runway usa límites explícitos por endpoint y modelo, con la unidad UTF-16 indicada en sus tipos oficiales: [texto a vídeo](https://github.com/runwayml/sdk-python/blob/main/src/runwayml/types/text_to_video_create_params.py), [imagen a vídeo](https://github.com/runwayml/sdk-python/blob/main/src/runwayml/types/image_to_video_create_params.py) y [texto a imagen](https://github.com/runwayml/sdk-python/blob/main/src/runwayml/types/text_to_image_create_params.py). No se extrapolan límites a variantes sin un límite y una unidad conocidos.

## Verificación

Las pruebas usan datos aislados, un LLM local programado y envíos simulados; no ejecutan generaciones pagadas.

- `test-studio-batch.js`: concurrencia, pausa, parada explícita, cola de Flow, planes inmutables, continuidad después de un fallo y ausencia de reenvío.
- `PromptAdapterTest`: Unicode/UTF-16, límites desconocidos, respuesta excesiva, respuesta incompleta/filtrada y fallo de adaptación.
- `test-order-resilience.py`: UI y protocolo Kotlin reales, original conservado, adaptación de parámetros, modelo pendiente aislado, precio ausente, actualización automática/caducada y 20 solicitudes fallidas sin impedir otros modelos. También comprueba la adaptación mediante `prepare_generation` del chat.
- `test-order-costs.py`: precios ausentes en Atlas/Runway/Crun, valores malformados y bloqueo de generaciones dentro del laboratorio.
- `test-chat-ux.py` y `test-studio-gallery.py`: regresión de conversación, editor, selección, galería y encargos.

La receta `tools/ecosystem-build/recipes/nexus-order-agent.json` reduce el heap del compilador a 192 MiB y los procesos de pruebas a 64 MiB. El selector evalúa disponibilidad antes de cada trabajo y conserva las reservas del dispositivo. Los artefactos y manifiestos se recuperan con verificación SHA-256.

Los contratos y errores efectivos de todas las cuentas y modelos no se prueban con estas simulaciones; un rechazo del proveedor se refleja por solicitud.

## Resultado del encargo

- Compilación y 101 pruebas Kotlin: PASS, sin fallos. ASUSVIDAL, trabajo `20261008T052403Z-d72fdd10`, artefactos recuperados y hashes verificados.
- Interfaz y protocolo del motor compilado: PASS. 22 envíos simulados, incluidos 20 fallos aislados; resumen desde el editor y desde el chat; carrera entre edición y respuesta del resumen comprobada. Cero generaciones pagadas.
- Regresiones de chat, galería, estado del proveedor y costes: PASS.
- La activación en la ventana abierta queda pendiente: la revisión automática rechazó detener de forma forzada el servicio local de NEXUS con el motivo «bloqueado por la política». No se ha reiniciado ni sustituido el motor que esa ventana está usando. El artefacto comprobado está preparado en `outputs/order-agent-build/20261008T052403Z-d72fdd10/desktop/agent/build/runtime`.
