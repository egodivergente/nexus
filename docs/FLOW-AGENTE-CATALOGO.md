# Flow en el agente de Nexus — 2026-10-06

Una ventana abierta podía entregar al motor un catálogo antiguo de Flow. El agente acababa negando la generación de imágenes aunque Crear ofreciera Nano Banana. Las respuestas de los callbacks `models` y `schema` ahora se completan con el contrato vigente del servicio local. Se conservan los demás proveedores y los modelos ocultos; las respuestas necesitan el propietario y el identificador de un callback pendiente.

El motor incluye el inventario actual de Flow en el contexto antes de responder. La política distingue explícitamente Nano Banana Lite, prohibido, de Nano Banana Pro y Nano Banana 2, permitidos. Pro es la opción preferida para la petición de una imagen cualquiera, que delega el tema y conserva los valores del esquema. Las reglas de confirmación de una generación siguen aplicándose.

Las tarjetas del agente muestran el estado del trabajo de Flow y, cuando termina, su imagen o vídeo local y un enlace de descarga. El refresco del trabajo actualiza la tarjeta en el chat. Una URL externa o un trabajo incompleto no se presenta como un resultado local completado.

## Comprobación

- `desktop/tools/test-flow-agent-catalog.py`: motor Kotlin real, LLM local de prueba y ventana simulada sin los modelos nuevos. Descubre Pro, Nano Banana 2, fotogramas y escalado; recupera el esquema y rechaza respuestas ajenas o repetidas. No envía generaciones.
- `desktop/tools/test-flow-agent-result.js`: presentación de resultados locales completados, contenido escapado y estados pendientes.
- Receta `tools/ecosystem-build/recipes/nexus-flow-agent-policy.json`: compilación y pruebas en ASUS mediante el selector. Artefactos y fuentes recuperados con SHA-256 verificado. El trabajo del Pixel se detuvo tras la pausa de protección por pantalla encendida.
- Petición real en Nexus con el LLM configurado: «Genera una imagen cualquiera con Google Flow y tráemela aquí». Preparó Pro, leyó «Incluido», y se confirmó una sola generación. Flow entregó un JPEG de 1376 × 768, validado por su hash y mostrado como completado dentro del chat. El modelo solicitado fue `nano-pro`; el puente conserva `model_confirmed=false`, por lo que esta prueba no establece una atribución independiente del modelo por parte del proveedor.

Las capturas, el resultado, los registros y los artefactos permanecen en el almacenamiento local privado. La interfaz de una ventana ya abierta requiere recargarse para mostrar la nueva vista; la corrección del catálogo se aplica en el servicio actualizado.
