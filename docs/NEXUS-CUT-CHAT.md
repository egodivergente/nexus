# Nexus Cut — acceso propio y conversación de montaje

Petición de Vidal del 11/11/2026, posterior al hito B: Cut pasa a tener acceso propio en la barra inferior y un editor oscuro con conversación integrada. Sustituye la decisión inicial de no añadir una pestaña.

La portada descrita aquí corresponde a vc206. Desde vc207, Cut reúne los proyectos y sus distintos montajes; estado y validación en `NEXUS-CUT-PROJECTS.md`. El chat propio de cada montaje se conserva.

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

## Compilación y pruebas — 11/11/2026

**1.9.170-agent-preview / vc206**, fuente `97af8b48dee194012d038f78717fd37d636f26f2`. APK `:app:assembleAgentPreview` compilada en AXELPC: **BUILD SUCCESSFUL in 1m 24s**.

- APK SHA-256: `bcc31fd6bc7f6ee20d67c6efc68cd8c49a90a8f0e354c891e347fe91b9396a98`.
- Certificado SHA-256 existente: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
- `agent-core`: **461 registradas, 457 PASS y 4 omitidas**, sin fallos; incluye cinco pruebas nuevas de herramientas vinculadas. Ocho pruebas nuevas de persistencia de chat cubren aislamiento, borradores, interrupciones, redacción, recuperación y ausencia de replay.
- Suite completa de app: **930 pruebas, 923 PASS, 7 FAIL, 0 errores y 0 omitidas**. La invocación termina **BUILD FAILED**; fallan los mismos siete casos que en la base vc204/vc205. Seis mensajes de aserción son idénticos. `ScreenInitializationTest` ya esperaba cuatro pestañas: en la base observaba cinco y ahora observa seis por la nueva pestaña solicitada. Se mantiene activo, sin corregir ni desactivar ese caso preexistente.

La primera ejecución completa tuvo ocho fallos. El adicional era `OutputExportSpecTest.studioNavigationKeepsSettingsOffTheBar`, que aún esperaba cinco pestañas. Se actualizó para esperar seis y comprobar Cut; la repetición dejó únicamente los siete fallos preexistentes. Las primeras 21 pruebas seleccionadas de app habían pasado; ese resultado parcial no sustituye a la suite completa.

## Comprobación física — Pixel 8 Pro

Instalada vc206 como actualización con `adb install -r`, con certificado y SHA-256 del APK instalado comprobados. Se operó la interfaz real y se conservaron las capturas y los datos de comprobación fuera de Git.

| Recorrido | Evidencia observada | Resultado |
|---|---|---|
| Barra principal → Cut | Seis pestañas, entrada propia de Cut y lista de montajes | PASS |
| Nuevo montaje → volver → reabrir | Montaje creado desde la interfaz y recuperado desde su tarjeta | PASS |
| Chat → escribir → Enviar | Proveedor scripted local solicita `cut_read` y `cut_edit`; las herramientas reales leen el contexto y renombran el montaje por `CutSession` | PASS |
| Respuesta del chat | Etiqueta y texto de prueba local; revisión 1, dos mensajes guardados, `pending=null` | PASS |
| Volver y reabrir con un borrador | Conserva los dos mensajes y el borrador sin reenviar; revisión todavía 1 | PASS |
| Rotación y teclado en vertical y horizontal | Capturas reales con composer y Enviar visibles; ajustes de rotación restaurados a sus valores previos (1/0) | PASS |
| Deshacer desde el editor | Revierte el renombrado realizado por el agente y alcanza la revisión 2 | PASS |

La comparación de IDs antes y después de vc206 no muestra ausencias ni añadidos en el índice de Nexus: **6 proyectos, 1 escena, 3 planos, 9 personajes, 2624 assets, 683 generaciones, 466 trabajos y 505 outputs**. El único montaje añadido para QA pertenece al espacio privado de Cut, fuera de ese índice. La comparación de 30 archivos de preferencias no da igualdad global; no se afirma identidad byte a byte del perfil. Las **78 huellas de archivos del chat principal** son idénticas antes y después, incluida la comprobación final. En el log disponible del PID observado no aparecen FATAL EXCEPTION ni ANR de Nexus. Pixel dejado en la lista principal de Cut.

El proveedor scripted es explícito, vive en memoria y se consume una vez; se arma a través del proveedor ADB de depuración protegido por DUMP/UID shell. Armarlo no ejecuta herramientas: el turno comienza al pulsar Enviar en la interfaz. No cambia preferencias de cerebro ni llama a un modelo externo. La lectura, la edición y el deshacer son reales; el cerebro está simulado. Esta prueba no acredita la interpretación de lenguaje natural por el modelo configurado ni una QA con LLM real.

## Entrega al S25

El primer intento falló por timeout de SSH con el S25 offline. Tras el aviso de conexión de Vidal, el reintento de `nexus-entregar-tailscale.ps1` **completó la transferencia** y comprobó paquete, vc206, certificado existente, alineación de 16 KiB y el mismo SHA-256 completo local/S25/Descargas indicado arriba. Se solicitó abrir el instalador sin ADB, desinstalar ni borrar datos.

La instalación final se completó por ADB. Se comprobó el acceso al S25 físico y el certificado de la APK instalada vc205; coincide con la identidad existente. `adb install -r` de vc206 terminó **Success**. `dumpsys package` confirmó **1.9.170-agent-preview / versionCode 206** y el SHA-256 de `base.apk` coincide con el de la entrega. No hubo desinstalación ni borrado de datos.

La comparación de IDs antes y después de la actualización del S25 muestra **0 ausentes y 0 añadidos**: 7 proyectos, 1 escena, 3 planos, 9 personajes, 3177 assets, 780 generaciones, 774 trabajos y 642 outputs. El log disponible del PID de Nexus no contiene FATAL EXCEPTION ni ANR. Vidal confirmó «Instalada: 1.9.170 y Cut visible».

Los intentos de obtener la jerarquía de UI del S25 devolvieron `null root node returned by UiTestAutomationBridge`; no se acredita allí un recorrido automatizado del editor. La comprobación funcional de chat, teclado y deshacer descrita arriba corresponde al Pixel.
