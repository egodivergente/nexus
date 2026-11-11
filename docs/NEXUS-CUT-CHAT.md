# Nexus Cut — acceso propio y conversación de montaje

Petición de Vidal del 11/11/2026, posterior al hito B: Cut pasa a tener acceso propio en la barra inferior y un editor oscuro con conversación integrada. Sustituye la decisión inicial de no añadir una pestaña.

## Acceso e interfaz

- Barra: Agente / Guion / ArtBox / **Cut** / Biblioteca / Flow. Las rutas guardadas se conservan por nombre.
- Cut muestra los montajes existentes, sus miniaturas locales y duración, y permite crear uno nuevo vinculado al proyecto activo. No duplica los montajes al abrirlos.
- El editor sigue abriéndose en `cut/{projectId}` con sus herramientas propias. Preview sobre negro, línea de tiempo compacta, transporte, herramientas y caja del agente. El tema oscuro es local: no cambia la apariencia guardada del resto de Nexus.
- La conversación se puede ampliar o plegar. El teclado reduce la zona de edición; en paisaje se prioriza el campo de texto. El botón Enviar se convierte en Detener mientras responde.

## Mismo montaje, mismo bus

El chat tiene su propio `AgentConversation`, pero comparte el `CutSession` del editor. Sus herramientas fijan el ID del montaje y rechazan otro, incluso si se cambia de pantalla durante el turno. El cabezal y los clips seleccionados se incluyen en el contexto.

`cut_catalog`, `cut_describe`, `cut_read` y `cut_edit` mantienen sus contratos. El editor añade `cut_run` para una única capacidad JOB, como exportación o cancelación local, siempre mediante `CutSession.execute` y origen AGENT. No altera la lista global de cinco herramientas de Cut. Biblioteca aporta solo búsqueda y lectura de metadatos.

Usa el cerebro configurado y sus límites actuales. En este contexto el modo es AGENT para ejecutar las ediciones solicitadas; no cambia el modo ni el proyecto del chat principal. No habilita generaciones de pago ni imágenes nativas del cerebro. Este chat no recibe automáticamente los píxeles o audio de los vídeos: no se da por hecho que los haya visto u oído.

## Historial y recuperación

Mensajes y borrador se guardan atómicamente en `files/cut/projects/<id>/chat-v1.json`, fuera del chat principal. El mensaje y el marcador de turno pendiente se guardan antes de llamar al modelo. Respuestas, errores y cancelación quedan registrados; el borrador se conserva al fallar.

Al restaurar se recupera texto, nunca llamadas de herramientas. Un turno interrumpido deja un aviso y no se reenvía solo. Cada nuevo turno restaura únicamente parejas de mensajes de texto completas; no restaura firmas internas de otro modelo. Salir del editor cancela su turno propio y conserva lo ya aplicado al montaje.

## Validación

Versión prevista: **1.9.170-agent-preview / vc206**. Primera compilación Kotlin PASS; pruebas seleccionadas de app **21 PASS**. `agent-core`: **461 registradas, 457 PASS y 4 omitidas**, sin fallos; incluye cinco pruebas nuevas de herramientas vinculadas. Ocho pruebas nuevas de persistencia de chat cubren aislamiento, borradores, interrupciones, redacción, recuperación y ausencia de replay.

La prueba de UI del chat usa un proveedor scripted explícito, en memoria y de un solo uso, armado a través del proveedor ADB de depuración protegido por DUMP/UID shell. Lee y modifica la sesión real por las herramientas vinculadas; la respuesta y su etiqueta indican que es una prueba local. No cambia preferencias de cerebro ni llama a un modelo externo. Una edición simulada no acredita la interpretación de lenguaje natural por el cerebro real.

Compilación APK final, suite completa y comprobación física se registrarán al ejecutarlas. No se declaran realizadas por estas pruebas unitarias.
