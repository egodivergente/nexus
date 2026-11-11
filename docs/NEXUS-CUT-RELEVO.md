# Nexus Cut — relevo (2026-11-11)

Para quien continúe (Codex u otro agente). Rama: `feat/nexus-cut-20261111`. No trabajar sobre `main` (va muy atrás) ni sobre `feat/flow-live-20261108` (es la base vc204, no se toca).
Decisiones de arquitectura: `docs/NEXUS-CUT.md`. Reglas del repositorio: `AGENTS.md`.

**Trabajo posterior solicitado por Vidal:** pestaña propia de Cut, editor oscuro y chat integrado; estado actual en `docs/NEXUS-CUT-CHAT.md`. Esa petición sustituye la decisión inicial de acceso sin pestaña descrita en la secuencia histórica de abajo.

**Continuación verificada del 11/11:** integración de los pasos 1–5 compilada y publicada; vc205 firmada y entregada al S25 por Tailscale con hashes comprobados e instalación confirmada por Vidal. Después, por petición expresa de Vidal, el recorrido funcional se ejecutó por ADB en el **Pixel 8 Pro físico**: Biblioteca → Montar 2 vídeos → dividir, borrar, deshacer → exportar 720p → MP4 visible y reproduciéndose en Biblioteca. PASS en Pixel; no se atribuye esa prueba al S25.

## Estado verificado

| Pieza | Estado | Evidencia |
|---|---|---|
| Hito A: `:cut-core` (motor de Corte + bus `CutSession` + registro de capacidades) | Hecho | `gradlew :cut-core:test` → 101 pruebas, 0 fallos |
| Herramientas del agente `cut_catalog/describe/read/edit/create` (`agent-core/.../tools/CutTools.kt`) | Hecho | `CutToolsTest` (2) y suite `:agent-core:test`: 456 registradas, 452 PASS, 4 omitidas, 0 fallos |
| Toolchain Kotlin 2.3.20, compileSdk 36, Media3 1.11.1 | Hecho | APK `assembleAgentPreview` compilada en AXELPC sobre `8885fb2` |
| App vc205: 922 pruebas unitarias | 915 PASS, 7 FAIL, 0 omitidas | Los siete fallos coinciden exactamente por clase, método y aserción con los XML de vc204 (31 pruebas seleccionadas, 24 PASS y esos 7 FAIL). No son regresiones de Nexus Cut ni se han desactivado. |
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

- C: composer del agente dentro del editor; `audio.find_silences` (formas de onda ya existen en `WaveformRepository`); avisar en el editor de lo que hizo el agente (`CutEvent`).
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
