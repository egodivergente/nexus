# Chat: modelos, encargos y temas

## Comportamiento

- Al escribir un fragmento de nombre en el chat o en el prompt del lienzo, se muestran modelos del catálogo cargado. `seed` incluye Seedream v5.0 Pro y sus variantes; también se reconocen familias como Kling, Flux, Wan, Veo, Qwen y nombres como Gen4. Las variantes principales de las versiones actuales aparecen primero.
- Flechas seleccionan una opción; Enter o Tab inserta su nombre exacto. Escape cierra la lista. El clic mantiene el foco. Solo se sustituye el fragmento situado junto al cursor: el resto del mensaje permanece intacto. La selección no envía el mensaje.
- Ajustes contiene seis temas: Grafito, Océano, Ámbar, Bosque, Carbón y Negro puro. Cambian fondo, capas, texto, bordes y acento; se aplican al instante y se guardan por usuario. Conservan las clases de navegación y composición.
- Un clic en Generar envía los planes concretos mostrados, sin segundo diálogo. Los encargos pendientes también tienen una acción fija encima del editor, accesible aunque se esté leyendo otro punto del historial.
- Un lote conserva cada plan y sus parámetros. No se añade `count` a modelos que no lo declaran, ni se descartan los planes restantes para fabricar una generación agrupada. Un fallo no impide enviar los demás. Los envíos inciertos no se repiten.
- Los resultados permanecen después de la respuesta del turno que los originó. Los resultados de pago y Flow se guardan junto a la conversación. Una petición nueva no se mezcla con una generación anterior que usaba el mismo prompt; tampoco se agrupan configuraciones o referencias diferentes.
- Flow sigue su ruta directa, sin tarjetas de generación ni confirmaciones adicionales. Las instrucciones del motor y la skill de costes admiten la autorización de un lote concreto con una sola acción.

## Verificación

Las pruebas usan un host local aislado y respuestas o envíos simulados. No prueban una generación de pago real ni sustituyen una validación de todas las cuentas y modelos.

- `test-chat-workflow.py`: catálogo real, teclado, inserción en medio de un mensaje, foco, conservación de scroll, seis temas y persistencia tras recarga; dos confirmaciones simuladas desde un clic, fallo aislado, envío incierto excluido, ausencia de `count` inventado, orden cronológico, parámetros distintos separados y resultados pagados conservados.
- `test-chat-ux.py`: editor estable, caret, seguimiento de respuestas, lectura de mensajes anteriores, carga tardía de medios y borrador durante una respuesta; tamaños de 1920, 1280 y 480 px.
- `test-generation-batch-card.py`: ruta Flow sin tarjetas ni confirmaciones, progreso, resultados parciales y contador; tamaños de 1360, 760 y 480 px.
- Comprobaciones JavaScript de dispatch, cola y límites: planes inmutables, máximo de concurrencia, continuidad tras fallos y ausencia de reenvíos automáticos.

La compilación del motor se remite mediante el selector `ecosystem-build`, con la receta limitada a 768 MiB y un worker. La activación en la instancia abierta queda pendiente de un reinicio autorizado: la revisión automática rechazó detener forzosamente su servicio local con el motivo «bloqueado por la política».

## Resultado

- Compilación y 101 pruebas Kotlin: PASS, 17 informes, cero fallos y errores. ASUSVIDAL, trabajo `20261008T055742Z-e1ac66c7`; artefactos recuperados con integridad SHA-256 verificada.
- `test-order-resilience.py` con ese motor compilado: PASS. 23 envíos simulados; el último cruza el protocolo real Kotlin de confirmación y callback desde un único clic, con coste desconocido y original conservado. Cero generaciones de pago.
- Autocompletado, temas, scroll, resultados persistentes y ruta Flow: PASS en los hosts de prueba aislados, sin errores JavaScript.
- Motor listo para cargar: `outputs/chat-agent-build/20261008T055742Z-e1ac66c7/desktop/agent/build/runtime`. No se han sustituido los JAR de la instancia en uso; sus hashes siguen siendo los anteriores.
