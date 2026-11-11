# Nexus Cut — relevo (2026-11-11)

Para quien continúe (Codex u otro agente). Rama: `feat/nexus-cut-20261111`. No trabajar sobre `main` (va muy atrás) ni sobre `feat/flow-live-20261108` (es la base vc204, no se toca).
Decisiones de arquitectura: `docs/NEXUS-CUT.md`. Reglas del repositorio: `AGENTS.md`.

**Estado actual vc208:** control de proyectos y Biblioteca por el agente implementado; fuente `2d6557bccfa2bc23ca3bc32d0b15b5ad1573eb8b`, **1.9.172-agent-preview**, compilada e instalada por ADB en Pixel con firma y hash comprobados. Traslado, verificación, archivado recuperable y deshacer probados con proyectos de prueba; búsqueda, muro, visor, añadir y deshacer comprobados por interfaz con modelo local y herramientas reales. Agent: 477 PASS + 4 omitidas; Cut: 103 PASS; app: 942 PASS y los mismos 7 FAIL previos. **S25 pendiente de reconexión ADB**: no se afirma instalación vc208 ni QA en ese teléfono. Artefacto, pruebas, fallos corregidos y límites en `docs/NEXUS-AGENT-PROJECT-CONTROL.md`.

**Estado anterior vc207:** centro de proyectos implementado, fuente `71de517999e2ebf498051aabc681703e50624d6f`, **1.9.171-agent-preview**, compilado e instalado por ADB como actualización en Pixel y S25. Firma, versión y hash comprobados; proyectos originales conservados. Recorrido de proyectos, material, varios montajes, borrador del agente y archivado/restauración probado en Pixel; portada revisada en S25. App: 936 PASS y los mismos 7 FAIL previos; 13 pruebas nuevas correctas. Detalle y límites en `docs/NEXUS-CUT-PROJECTS.md`. Los apartados vc205/vc206 siguientes son antecedentes históricos.

**Trabajo posterior solicitado por Vidal:** pestaña propia de Cut, editor oscuro y chat integrado; estado actual en `docs/NEXUS-CUT-CHAT.md`. Esa petición sustituye la decisión inicial de acceso sin pestaña descrita en la secuencia histórica de abajo.

**Estado actual vc206:** fuente `97af8b48dee194012d038f78717fd37d636f26f2`, **1.9.170-agent-preview**, compilada en AXELPC e instalada como actualización en Pixel y S25 con certificado, versión y hash comprobados. Barra con seis pestañas, lista y creación de montajes, chat persistente y deshacer compartido comprobados por interfaz en Pixel. La prueba del chat usa un cerebro scripted local y herramientas reales; no se llamó a un LLM externo. El S25 recibió la APK por Tailscale y, tras la petición de Vidal de completar la instalación, se actualizó por ADB sin borrar datos; Vidal confirmó versión y pestaña Cut visible.

**Continuación verificada del 11/11:** integración de los pasos 1–5 compilada y publicada; vc205 firmada y entregada al S25 por Tailscale con hashes comprobados e instalación confirmada por Vidal. Después, por petición expresa de Vidal, el recorrido funcional se ejecutó por ADB en el **Pixel 8 Pro físico**: Biblioteca → Montar 2 vídeos → dividir, borrar, deshacer → exportar 720p → MP4 visible y reproduciéndose en Biblioteca. PASS en Pixel; no se atribuye esa prueba al S25.

## Estado verificado

| Pieza | Estado | Evidencia |
|---|---|---|
| Hito A: `:cut-core` (motor de Corte + bus `CutSession` + registro de capacidades) | Hecho | `gradlew :cut-core:test` → 101 pruebas, 0 fallos |
| Herramientas del agente y herramientas vinculadas del chat vc206 | Hecho | Suite `:agent-core:test`: 461 registradas, 457 PASS, 4 omitidas, 0 fallos; cinco pruebas nuevas del destino fijo y los trabajos locales |
| Toolchain Kotlin 2.3.20, compileSdk 36, Media3 1.11.1 | Hecho | APK `assembleAgentPreview` compilada en AXELPC sobre `8885fb2` |
| App vc205: 922 pruebas unitarias | 915 PASS, 7 FAIL, 0 omitidas | Los siete fallos coinciden exactamente por clase, método y aserción con los XML de vc204 (31 pruebas seleccionadas, 24 PASS y esos 7 FAIL). No son regresiones de Nexus Cut ni se han desactivado. |
| App vc206: 930 pruebas unitarias | 923 PASS, 7 FAIL, 0 errores, 0 omitidas | **BUILD FAILED** por los mismos siete casos preexistentes: seis mensajes de aserción idénticos; `ScreenInitializationTest` sigue esperando 4 y observa 6 en lugar de 5 por la pestaña nueva. Primera ejecución con 8 FAIL: expectativa de `OutputExportSpecTest` corregida de 5 a 6 y Cut; repetición con 7 FAIL, sin desactivar pruebas. |
| `media.import` / `render.export` / `render.status` / `render.cancel` | Hecho en núcleo | `DeviceCapabilitiesTest` (3) |

## Capa Android escrita en d687cbf (primera compilación completada)

Estos archivos se compilaron por primera vez el 11/11/2026 en AXELPC, sobre `d687cbf`, sin errores. La integración y la prueba física se registran abajo; compilar no acredita el recorrido en el teléfono.

- `app/.../cut/engine|media|export/` — motor de Corte trasladado (paquetes renombrados). `export/ExportService.kt` y `ExportManager.kt` adaptados: el render se valida y entra en la Biblioteca (ya no usa SAF).
- `app/.../cut/media/MediaProbe.kt` — acepta `mimeHint` para URIs `file://`.
- `app/.../cut/NexusCut.kt` — espacio de trabajo único (`files/cut/projects`), implementación Android de `CutLibrary` y `CutRenderer`, `openWith()` (reutiliza el montaje del proyecto/escena, nunca duplica) y canal `openRequests`.
- `app/.../ui/cut/` — `CutEditorViewModel`, `CutEditorScreen`, `CutTimelineCanvas` + `CutTimelineGeometry` (de Corte), `CutPalette` (colores del tema activo).

## Secuencia original del hito B (completada; acceso actualizado después)

1. **Compilar** en AXELPC: `C:\Users\User\nexus-flow-live\build-cut.ps1 -Tasks :cut-core:test,:agent-core:test,:app:assembleAgentPreview -Tag cut-b1` (deja `RESULTADO:` al final de `C:\Users\User\nexus-cut-b1-build.log`). Corregir errores de compilación de `app/.../cut` y `ui/cut`.
2. **Manifest** (`app/src/main/AndroidManifest.xml`): añadir
   - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING" />`
   - `<service android:name=".cut.export.ExportService" android:exported="false" android:foregroundServiceType="mediaProcessing|dataSync" />`
3. **Navegación** (`MainActivity.kt`): destino `composable("cut/{projectId}")` que muestre `CutEditorScreen(projectId, onBack = { navController.popBackStack() })`; ocultar la barra inferior cuando la ruta empiece por `cut/`; recoger `NexusCut.openRequests` en un `LaunchedEffect` y navegar a `cut/$id`. No es una pestaña nueva.
4. **Agente** (`assistant/AssistantRuntime.kt`): en `buildDeps()` pasar `cut = com.vidadomu.nexus.cut.NexusCut.workspace(app)`; en `uiContext` añadir `com.vidadomu.nexus.agent.core.tools.CutTools.context(deps.cut!!)` a la lista. Actualizar el recuento de herramientas en `OrchestratorTest` si lo afecta (con `cut` son +5; hoy el test usa deps sin `cut` → 42).
5. **Entradas**: Biblioteca (selección múltiple → «Montar» → `NexusCut.openWith(app, assetIds, proyectoActivo)`), Guion/escena (`openWith(app, assetsDeLaEscena, projectId, sceneId)`), visor de una generación (`OutputViewerScreen`, «Editar en Nexus Cut»).
6. **Prueba funcional en el S25** (sin ADB): versionCode 205 / versionName nuevo en `app/build.gradle.kts`; entregar con `C:\Users\User\nexus-entregar-tailscale.ps1 -Version 1.9.169` (firma y abre el instalador en el teléfono; es actualización, conserva datos). Comprobar a mano: Biblioteca → Montar 2 vídeos → dividir, borrar, deshacer → exportar 720p → el MP4 aparece en la Biblioteca y se reproduce.

## Hitos siguientes

- C: composer del agente dentro del editor y avisos de sus acciones mediante `CutEvent` **hechos en vc206**; chat persistente y deshacer compartido comprobados en Pixel. Sigue pendiente `audio.find_silences` (formas de onda ya existen en `WaveformRepository`).
- D: inspector (transformar, color, keyframes, transiciones) — las capacidades ya existen en el núcleo, falta la interfaz.
- E: traer proyectos antiguos de Corte (su puente aún no los exporta), retirar `CorteBridge.kt` y herramientas `corte_*`, formato portable para Nexus Desktop.

## Reglas que el usuario ha pedido

No inventar estados ni resultados; un build fallido se informa como fallido. No instalar sobre datos sin actualización compatible. No generaciones de pago en pruebas. Sin force-push, sin sobrescribir ramas ajenas, secretos y firmas fuera de Git.

## Continuación del hito B — 11/11/2026

1. **Primera compilación: PASS.** Ejecutado `build-cut.ps1 -Tasks :cut-core:test,:agent-core:test,:app:assembleAgentPreview -Tag cut-b1` en AXELPC sobre `d687cbf`. `BUILD SUCCESSFUL in 6m 11s`; no fue necesario corregir errores de compilación. XML de esta ejecución: `cut-core` 101 pruebas, 101 PASS; `agent-core` 456 registradas, 452 PASS y 4 omitidas, sin fallos ni errores. No se ejecutó todavía la suite de la app ni la prueba funcional del S25.
2. **Manifest: integrado.** Permiso `FOREGROUND_SERVICE_MEDIA_PROCESSING` y servicio privado `ExportService` con tipos `mediaProcessing|dataSync`. `:app:processAgentPreviewMainManifest` PASS; comprobados permiso y servicio en el manifest fusionado.
3. **Navegación: integrada.** Destino `cut/{projectId}`, recogida de `NexusCut.openRequests`, navegación sin duplicar el destino superior y sin barra inferior dentro del editor. Atrás vuelve al origen; «Ver» tras exportar abre Biblioteca. `:app:compileAgentPreviewKotlin` PASS. No se añadió una pestaña.
4. **Agente: conectado.** `buildDeps()` recibe `NexusCut.workspace(app)` y `uiContext` incluye `CutTools.context`. `:app:compileAgentPreviewKotlin :agent-core:test` PASS; la suite del núcleo se reutilizó (`UP-TO-DATE`) de la ejecución del paso 1. El test con deps sin Cut conserva 42 herramientas; `CutToolsTest` ya comprueba 47 con Cut. Sin llamadas a proveedores.
5. **Entradas: integradas.** Biblioteca: selección múltiple → «Montar», en orden visible y con proyecto activo; Guion: escena o proyecto en orden narrativo, con vínculos n:m y legacy; visor: «Editar en Nexus Cut». Todas llaman a `NexusCut.openWith` y editan mediante `CutSession`. Reabrir no duplica clips ni restaura los borrados; aperturas simultáneas se serializan y una edición concurrente del agente se protege por revisión. `:app:testAgentPreviewUnitTest` con filtros de Cut, Biblioteca, navegación y visor: **22 pruebas PASS**, sin omitidas (10 nuevas de Cut y 12 existentes). No es la suite completa.

   Solo se montan copias locales identificadas. Para resultados antiguos múltiples cuyas URIs se relocalizaron sin conservar una asociación inequívoca con cada output, el visor rechaza adivinar la posición; se puede elegir el asset local en Biblioteca. El fallback de generación monoresultado sí está cubierto por prueba.
6. **APK entregada al S25 y recorrido funcional comprobado por ADB en Pixel.** `1.9.169-agent-preview / versionCode 205`, paquete `com.vidadomu.nexus.agent`, fuente `0f3c89750010955948bd84fe09b6850437bd2c81`. `:app:assembleAgentPreview` **BUILD SUCCESSFUL in 2m 36s** en AXELPC. Comprobados versión, permiso, servicio y revisión `0f3c89750010` dentro de los DEX del APK terminado.

   Suite completa `:cut-core:test :agent-core:test :app:testAgentPreviewUnitTest --continue`: **BUILD FAILED** por las siete pruebas preexistentes de la app; 922 pruebas de app, 915 PASS y 7 FAIL, sin errores ni omitidas. Las 16 pruebas de app de Nexus Cut pasan (6 selección de assets, 4 reapertura, 4 hueco inicial y 2 matemáticas de exportación). `cut-core` y `agent-core` fueron `UP-TO-DATE` en esta última invocación: se reutilizaron sus resultados del paso 1.

   `tools/release/entregar-tailscale.ps1` exige paquete/versión, certificado existente, alineación de 16 KiB y SHA-256 completo local/remoto/Descargas. El wrapper privado de AXELPC acepta `-Worktree` para usar la APK de Cut sin tocar la rama base. El primer intento falló con timeout de SSH (el teléfono estaba offline); tras el aviso de Vidal, el segundo completó la transferencia, verificó ambas copias y solicitó abrir el instalador. No se usó ADB ni se ejecutó desinstalación o borrado de datos. No se han hecho generaciones ni llamadas de prueba a proveedores.

   - Certificado SHA-256: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
   - APK SHA-256 local = S25 = Descargas: `57e6a8e0541c366deef1199d26360efbcb2fccbd154715e0a12fb9776551f867` (47 906 831 bytes).
   - **S25:** Vidal indicó primero que seguía en 1.9.168 y posteriormente confirmó «ya instalada» tras la entrega de 1.9.169. Es confirmación manual del usuario, no lectura instrumentada de versión/datos.
   - **S25:** el recorrido de edición/exportación no se ejecutó allí. Vidal pidió sustituir esa comprobación por la prueba mediante ADB en el Pixel.

## Prueba funcional física — Pixel 8 Pro, 11/11/2026

Pixel identificado por ADB, inicialmente vc204 / 1.9.168. Certificado instalado comprobado antes de actualizar: coincide con la identidad anterior. Sin generaciones activas (todos los trabajos terminados o fallidos). `adb install -r` de vc205: **Success**; versión instalada y SHA-256 de `base.apk` coinciden con la entrega anterior. No se desinstaló ni borraron datos.

Se operó la interfaz real por ADB; los JSON privados y las capturas se conservaron fuera de Git. No se usaron llamadas del agente para simular pulsaciones o fabricar el resultado.

| Acción por interfaz | Evidencia contrastada | Resultado |
|---|---|---|
| Biblioteca, filtro vídeo, marcar dos vídeos locales y «Montar» | Editor inmersivo abierto; proyecto persistido con 2 medios, 2 clips de vídeo y 2 de audio; duración 26,648 s | PASS |
| Desplazar cabezal y «Dividir» a 1,960 s | Revisión 2; 3 clips de vídeo y 3 de audio; duración total conservada | PASS |
| «Eliminar» el primer tramo, con ripple | Revisión 3; desaparecen los dos segmentos vinculados; posteriores adelantados 1,960 s | PASS |
| «Deshacer» | Revisión 4; `timeline` restaurada exactamente al JSON posterior a dividir | PASS |
| Exportar, seleccionar 720p y H.264 | Progreso real observado; asset final registrado y MP4 existente, 8 741 357 bytes | PASS |
| Volver a Biblioteca y abrir el MP4 exportado | Nueva miniatura visible; reproducción real con fotogramas distintos, controles de reproducción y progreso observado | PASS |

`ffprobe` del MP4 recuperado del dispositivo: vídeo **H.264, 1280×720, 26,648 s**; audio **AAC, 26,702 s**; duración del contenedor 26,702 s. La Biblioteca registra VIDEO / FINAL / GENERATED, MIME `video/mp4` y tag `nexus-cut`. El registro del montaje contiene, con origen USER, `media.import`, `clip.split`, `clip.delete`, `project.undo` y `project.set_export`.

Comparación antes de instalar y después del recorrido: ningún ID original ausente en proyectos, escenas, planos, personajes, assets, generaciones, trabajos u outputs. Solo se añadió el asset de la exportación; no hay nuevos trabajos ni generaciones. Los hashes de tres ficheros de preferencias cambiaron al arrancar tras la actualización; no se afirma igualdad byte a byte de todo el perfil ni se leyeron valores de credenciales. No se detectaron FATAL EXCEPTION ni ANR de Nexus en el log del proceso de esta prueba. Pixel dejado en Biblioteca, filtro Todos, con el MP4 exportado visible.

## Continuación vc206 — acceso propio y chat, 11/11/2026

Fuente `97af8b48dee194012d038f78717fd37d636f26f2`, **1.9.170-agent-preview / versionCode 206**. `:app:assembleAgentPreview` en AXELPC: **BUILD SUCCESSFUL in 1m 24s**. APK SHA-256 `bcc31fd6bc7f6ee20d67c6efc68cd8c49a90a8f0e354c891e347fe91b9396a98`; certificado existente SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. Instalada en Pixel mediante `adb install -r`, con certificado y hash instalado verificados.

`agent-core`: **461 registradas, 457 PASS y 4 omitidas**, sin fallos. App completa: primera ejecución con **930 pruebas y 8 FAIL**; una expectativa antigua aún exigía cinco pestañas. Se actualizó `OutputExportSpecTest.studioNavigationKeepsSettingsOffTheBar` a seis e inclusión de Cut, sin desactivar la prueba. La repetición terminó **BUILD FAILED**, con **930 pruebas, 923 PASS y los mismos 7 casos preexistentes en FAIL**, 0 errores y 0 omitidas. Al comparar con vc204, seis mensajes de aserción son idénticos. `ScreenInitializationTest` ya esperaba cuatro pestañas y observaba cinco; ahora observa seis por la pestaña solicitada. Ese caso sigue activo y sin modificar. No se presenta la suite como verde.

Por interfaz del Pixel se comprobaron las seis pestañas, la lista de Cut y crear/volver/reabrir un montaje. Escribir y pulsar Enviar ejecutó un turno scripted local: `cut_read` leyó el contexto y `cut_edit` renombró la sesión real mediante el bus. La respuesta y su etiqueta identificaron la prueba local; el estado quedó en revisión 1, con dos mensajes guardados y `pending=null`. Volver y reabrir conservó ambos mensajes y un borrador sin reenviar ni cambiar esa revisión. El botón Deshacer del editor revirtió el renombrado del agente y dejó la revisión 2.

Capturas reales acreditan composer y Enviar visibles con teclado en vertical y horizontal; se restauraron los ajustes de rotación a sus valores previos (1/0). Capturas y datos privados permanecen fuera de Git. No se llamó a un modelo externo: las herramientas y los cambios son reales, el cerebro de QA es scripted; no se acredita interpretación por un LLM real.

Comparación de IDs antes/después de vc206: **0 ausentes y 0 añadidos** en Nexus, con 6 proyectos, 1 escena, 3 planos, 9 personajes, 2624 assets, 683 generaciones, 466 trabajos y 505 outputs. Solo se añadió el montaje de QA en el espacio privado de Cut, fuera del índice de Nexus. La comparación de 30 archivos de preferencias no da igualdad global; no se afirma identidad byte a byte del perfil. Las 78 huellas del chat principal continúan idénticas en la comprobación final. El log disponible del PID observado no muestra FATAL EXCEPTION ni ANR de Nexus. Pixel dejado en la lista principal de Cut.

### Entrega vc206 al S25

El primer intento falló por timeout de SSH con el S25 offline, sin transferencia ni instalador. Tras el aviso de conexión de Vidal, el reintento con `nexus-entregar-tailscale.ps1 -Version 1.9.170 -Worktree C:\Users\User\nexus-cut -ExpectedVersionCode 206` terminó correctamente. Comprobados paquete, versión, certificado existente, alineación de 16 KiB y SHA-256 completo local/S25/Descargas: `bcc31fd6bc7f6ee20d67c6efc68cd8c49a90a8f0e354c891e347fe91b9396a98`. Se solicitó abrir el instalador sin ADB, desinstalación ni borrado de datos.

La instalación final se completó por ADB en el **S25 físico**, inicialmente vc205 / 1.9.169, tras verificar el acceso y el certificado de su APK instalada: coincide con la identidad existente. `adb install -r` de vc206 terminó **Success**. `dumpsys package` confirmó **versionCode 206 / 1.9.170-agent-preview**; el SHA-256 de `base.apk` instalada coincide con `bcc31fd6bc7f6ee20d67c6efc68cd8c49a90a8f0e354c891e347fe91b9396a98`. No hubo desinstalación ni borrado de datos. Vidal confirmó «Instalada: 1.9.170 y Cut visible».

Comparación de IDs antes/después en S25: **0 ausentes y 0 añadidos**, con 7 proyectos, 1 escena, 3 planos, 9 personajes, 3177 assets, 780 generaciones, 774 trabajos y 642 outputs. El log disponible del PID de Nexus no muestra FATAL EXCEPTION ni ANR. Los intentos de capturar la jerarquía de UI devolvieron `null root node returned by UiTestAutomationBridge`; no se presenta como un recorrido automatizado del editor en S25. Las pruebas funcionales de chat, teclado y deshacer de vc206 y de montaje/exportación de vc205 corresponden al Pixel.
