# Nexus Cut — relevo (2026-11-11)

Para quien continúe (Codex u otro agente). Rama: `feat/nexus-cut-20261111`. No trabajar sobre `main` (va muy atrás) ni sobre `feat/flow-live-20261108` (es la base vc204, no se toca).
Decisiones de arquitectura: `docs/NEXUS-CUT.md`. Reglas del repositorio: `AGENTS.md`.

## Estado verificado

| Pieza | Estado | Evidencia |
|---|---|---|
| Hito A: `:cut-core` (motor de Corte + bus `CutSession` + registro de capacidades) | Hecho | `gradlew :cut-core:test` → 101 pruebas, 0 fallos |
| Herramientas del agente `cut_catalog/describe/read/edit/create` (`agent-core/.../tools/CutTools.kt`) | Hecho | `CutToolsTest` (2) y suite `:agent-core:test` 456, 0 fallos |
| Toolchain Kotlin 2.3.20, compileSdk 36, Media3 1.11.1 | Hecho | APK `assembleAgentPreview` compilada en AXELPC sobre `8885fb2` |
| App: 906 pruebas unitarias | 7 fallan | Fallan igual en la base vc204 intacta (comprobado). No son de Nexus Cut. Lista en la tarea «Update 7 stale Nexus app unit tests». |
| `media.import` / `render.export` / `render.status` / `render.cancel` | Hecho en núcleo | `DeviceCapabilitiesTest` (3) |

## Capa Android escrita en d687cbf (primera compilación completada)

Estos archivos se compilaron por primera vez el 11/11/2026 en AXELPC, sobre `d687cbf`, sin errores. La integración y la prueba física se registran abajo; compilar no acredita el recorrido en el teléfono.

- `app/.../cut/engine|media|export/` — motor de Corte trasladado (paquetes renombrados). `export/ExportService.kt` y `ExportManager.kt` adaptados: el render se valida y entra en la Biblioteca (ya no usa SAF).
- `app/.../cut/media/MediaProbe.kt` — acepta `mimeHint` para URIs `file://`.
- `app/.../cut/NexusCut.kt` — espacio de trabajo único (`files/cut/projects`), implementación Android de `CutLibrary` y `CutRenderer`, `openWith()` (reutiliza el montaje del proyecto/escena, nunca duplica) y canal `openRequests`.
- `app/.../ui/cut/` — `CutEditorViewModel`, `CutEditorScreen`, `CutTimelineCanvas` + `CutTimelineGeometry` (de Corte), `CutPalette` (colores del tema activo).

## Pasos pendientes del hito B, en orden

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
