# PR #13 — identidad de las elecciones

La causa era `WidgetCtx.chose`: `answered.contains(label, ignoreCase = true)`
confundía «Kling» con «Kling Pro» tanto en encuestas como en comparaciones.

La selección nueva guarda `WidgetAnswer` junto al mensaje del usuario: ID del
mensaje del asistente, ID del panel e IDs de las opciones. El agente recibe solo
el texto visible de la respuesta. Las opciones con IDs repetidos reciben IDs
únicos y estables; los paneles repetidos, incluso dentro de recuadros, también.

Los historiales anteriores siguen siendo legibles. Las respuestas antiguas se
comparan completas, incluyendo multiselecciones separadas por comas o líneas.
Si el texto antiguo admite dos elecciones distintas, se conserva el mensaje sin
inventar una selección. Los metadatos nuevos resuelven también esos casos.

Una respuesta aceptada bloquea nuevos envíos del panel. Un rechazo por ocupación
permite reintentar. Marcar opciones múltiples o pasos no envía mensajes; solo el
botón de envío o la elección simple/comparativa produce una respuesta textual.
Los IDs, los hints y los campos action del JSON no se ejecutan.

## Validación

- AXELPC, JDK 17: `gradlew.bat -p desktop/agent :core:test :compileKotlin
  --no-daemon --max-workers=2`: 235 pruebas superadas, 4 omitidas; las 9 regresiones
  nuevas superadas. DesktopAgent compilado.
- Android: `:app:compileOfflineQaKotlin` y las dos pruebas de
  `WidgetAnswerPersistenceTest` superadas (0 fallos, 0 omisiones).
- Pixel 8 Pro físico con Android 17, ADB Wi-Fi: cinco pruebas de interacción
  superadas, 0 fallos, 0 errores y 0 omisiones.
  Paquete de QA independiente `com.vidadomu.nexus.pr13qa`, callbacks simulados y sin
  llamadas a proveedores. La invocación de QA omite CMake/Whisper, que estos paneles
  no usan, y limita las fuentes instrumentadas a la carpeta de los tests nuevos.
- La compilación de toda la instrumentación antigua está bloqueada por una llamada
  obsoleta en `UiAuditScreenshotsTest.kt:103` (`AssistantViewModel` pasado donde se
  espera `() -> Unit`). No se ha modificado ese test ajeno al arreglo.
- Las copias de `ChatWidgets.kt` y `ChatMarkdown.kt` en Android y Desktop son
  idénticas dentro de cada rama.
- Commits con `[skip ci]`; ningún workflow de Actions lanzado para la primera
  publicación del arreglo y para el commit de las cinco pruebas físicas.

## Integración

El arreglo del PR parte exactamente de `f4f3ef8`. La adaptación independiente en
`codex/pr13-selection-recent-20261116` parte de `05dcb6a` (rama reciente de Nexus).
Android compila en esa rama; sus dos pruebas de persistencia y la regresión de
selecciones de `StudioIntegrationTest` pasan (3 pruebas, 0 fallos, 0 omisiones).
Se actualizó solo esa regresión existente al nuevo contrato de IDs; los otros
casos del fichero se conservan. Comando: `:app:testOfflineQaUnitTest
--tests '*WidgetAnswerPersistenceTest'
--tests '*StudioIntegrationTest.widgetChoicesAreExactAndDoNotSelectSubstrings'`.
Conserva timestamps, notas/contexto del agente, selección/copias y enlaces de la
interfaz reciente. No se mezclan las versiones antiguas del PR con la aplicación
actual ni se modifican ramas de otros agentes para preparar la adaptación.

Fuentes verificadas: PR #13 `5a10713d`; adaptación reciente `cce78840`. Los commits
posteriores de este informe no modifican código ejecutable. No se aumenta versión.
