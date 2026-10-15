# Verificación · 2026-10-15

## Renderizado desde las fuentes

- 36 pruebas Python pasan, incluyendo copia aislada, preservación de fuentes originales,
  exclusión de claves, validación de rutas, imágenes inválidas, selección del host y
  CLI de listado sin ADB. La ventana se construyó y se comprobó el botón Renderizar código.
- Compilación y render real en Windows, Java 17, Gradle 8.9, AGP 8.7.3,
  Kotlin 2.0.21 y Paparazzi 1.3.5: `BUILD SUCCESSFUL in 5m 14s` en la ejecución
  de recuperación, con recursos y código de la app ya compilados. El primer arranque
  completo necesitó más tiempo de compilación/preparación; no se afirma que un render
  desde cero tarde 49 segundos.
- JUnit del motor: 6 tests, 0 fallos, 0 errores, 0 omitidos, 49.632 segundos dentro
  de esa ejecución Gradle. Se recuperaron 6 PNG y se abrió la lámina: Biblioteca vacía,
  Modelos con datos sintéticos y el panel de progreso, cada uno en Aurora y Claro.
  Son llamadas a `LibraryScreen`, `ModelsScreen`, `GenerationProgressPanel` y `NexusTheme`
  del código local. El manifiesto declara alcance y datos. Los logos asíncronos pueden
  quedar en placeholder; no se presenta eso como fallo de la app en producción.
- El ASUS compiló las fuentes pero se quedó bajo presión de memoria al preparar las
  pruebas. Se detuvieron exclusivamente los procesos iniciados por esta tarea. Se
  continuó en Windows con prioridad BelowNormal y ActiveProcessorCount=2 comprobados.
  El heap del compilador se ajustó a 1024 MiB y el metaspace a 640 MiB tras un intento
  demasiado limitado. El motor nuevo prioriza ASUS solo con margen de memoria y sin
  otra compilación; la ejecución local usa esos límites y la caché de compilación.
- Artefactos locales: `sessions/source-render-nexus-20261015-03/`, con `render.json`,
  `source-provenance.json`, registro, galería, lámina y originales. La procedencia
  registra los hashes de las fuentes locales exactas, incluidas las no versionadas;
  no se afirma equivalencia con la APK Agent instalada. El reintento reutilizó esa
  misma copia y actualizó únicamente los límites de JVM, conservando su procedencia.
- No se usó móvil ni emulador en este modo. Cobertura actual: los seis estados
  registrados; Agente, Lienzo y demás estados necesitan sus propios fixtures.

## Exploración de APK instalada

- Python 3.12 en Windows: 27 pruebas unitarias pasan. Cubren navegación ramificada,
  recuperación de rutas, límites, persistencia ante error, controles sensibles,
  diálogos externos, keyguard, escape HTML, SSH, descargas GitHub con digest,
  persistencia de preferencias, detección de móviles, selección con cambio de puerto,
  CLI para agentes con JSON limpio, inferencia del package, recorrido por anchura,
  listado de acciones pendientes, detección de capturas modificadas, estabilización
  después del splash, prioridades de navegación y JSON Unicode en consola Windows.
- Prueba real sobre NEXUS Agent ya instalado en el S25 (`com.vidadomu.nexus.agent`,
  interfaz `v1.9.5-agent-preview`, build 39), ADB local `192.168.1.134:5555`:
  una pasada de 3 minutos obtuvo 9 estados, 9 acciones, 94 acciones pendientes y
  14 controles omitidos, finalizó con `limit_reached` y devolvió JSON válido.
  Se abrió su lámina y se verificaron capturas reales del chat, diálogos, proyecto,
  ajustes y saldo. No se reinstaló la app. Los artefactos privados están excluidos de Git.
  Esta prueba corrigió una salida Unicode incompatible con la consola y una captura
  prematura del splash, además de priorizar pestañas y navegación frente al chat.
  La pasada posterior, con las prioridades corregidas, obtuvo Agente, Lienzo, Modelos
  y Biblioteca en 4 capturas y 3 pulsaciones, todas propias de la app, con SHA-256
  verificado y sin avisos de imagen uniforme. Finalizó por el límite de 4 estados;
  se revisó visualmente su lámina y el JSON de stdout era válido. Cobertura parcial.
- Modo agente real en el Pixel: creó `agent.json` y una lámina JPG desde XML/PNG
  del dispositivo. Marcó `blocked`, captura uniforme y SHA-256 válido; se abrió la
  lámina y se comprobó que muestra la captura negra con aviso de revisión.
  No se presenta esa captura como interfaz de la app ni se valida un recorrido real.
- Detección automática real desde Windows: S25 `192.168.1.134:5555` y Pixel por mDNS.
  La GUI seleccionó el S25 y guardó las preferencias en una carpeta temporal de prueba.
  Esta comprobación solo enumeró dispositivos; no lanzó apps ni exploró sus pantallas.
- GUI Tkinter: ventana construida, bucle de eventos ejecutado y cierre comprobado.
  No se ha hecho una revisión visual completa de todos los controles de la ventana.
- GitHub real: acceso al repositorio privado `egodivergente/nexus` con la sesión
  existente de GitHub CLI. Release `v1.9.4-agent`, asset `643882626`, descargado
  `Nexus-Agent-1.9.4-preview.apk`, package detectado `com.vidadomu.nexus.agent`.
  SHA-256 `3a8552ff74ea6677618c75e9a80af43df2cc8d70c086a101998d33aa688dc353`,
  verificado frente al digest del asset de GitHub. No se instaló esa descarga.
- Inventario real del código: rama `main`, SHA
  `7d2bc72df7b7f80198c2b853990967c784b685c0`, 28 archivos candidatos inspeccionados,
  19 referencias de navegación encontradas. Esto no demuestra su cobertura en una APK.
- Pixel 8 Pro: ADB autorizado a través de ASUSVIDAL, serial observado
  `192.168.1.146:43849`. Se lanzó la app de pruebas ya instalada
  `com.vidadomu.nexus.uiqa` y se recuperaron XML y PNG reales. El sistema mantuvo
  `NotificationShade`/`element:lockscreen` delante de la app; la captura era negra.
  El explorador registró `blocked` y no ejecutó pulsaciones de exploración.
  Recorrido real entre varias pantallas: **NO PROBADO**. No se borraron datos,
  desinstalaron apps ni cambiaron ajustes persistentes del dispositivo.

Los artefactos de estas comprobaciones permanecen en `sessions/`, excluido de Git.
No hay validación de cobertura total ni de funcionamiento con cualquier APK.

## Source discovery extension — 2026-10-15

- PASS: 40 Python tests, including source inventory, ignored outputs, comments/strings,
  literal routes, file-qualified registration and low-memory rejection before Gradle.
- PASS: local inventory: 10 screens, 53 composables, four registered functions.
  Evidence: `sessions/discovery-20261015-02/discovery.json` (private, ignored).
- PASS: inventory also executed against the separate nexus-android-parity checkout;
  report: `sessions/discovery-agent-20261015-01/discovery.json`.
- NOT VALIDATED: six new Canvas cases and the separate ten-case Agent/Canvas profile.
  Local build session `sessions/source-render-expanded-20261015-01` was stopped when
  Windows free physical RAM fell to about 250 MiB; ASUS had about 1420 MiB available.
  No new screenshots were produced. Only the owned wrapper/daemon were stopped.
- No physical device used, no original Android source files modified, no LLM calls.

## Generic project adapter — 2026-10-15

- PASS: 51 Python tests in the full explorer suite.

- PASS: source profiles are no longer implicitly selected for NEXUS. New installations
  have no fixed application package or repository; saved user preferences are retained.
- PASS: independent Weather and Notes projects in Python integration tests, different
  modules and package names, Compose previews, XML layouts, rejected private/member/
  parameterized previews, Groovy snapshot injection and multi-module selection.
- PASS: real automatic preparation, Kotlin compilation and Paparazzi render for an
  independent synthetic Weather app (`sample.weather`, module `weather`). Two images:
  Compose WeatherPreview and XML forecast. JUnit tests=2, failures=0, errors=0.
  BUILD SUCCESSFUL in 4m 35s; 24 tasks (20 executed, 4 from cache).
  Actual images visually inspected. No Android device used.
- Evidence (ignored session): `sessions/generic-weather-result-20261015/result.json`,
  `render-1/render.json`, `render-1/render.log`, `render-1/source-provenance.json`,
  `render-1/source/weather/build/test-results/testDebugUnitTest/TEST-explorer.generated.GeneratedRenderTest.xml`.
- PASS: real GitHub clone through existing gh authentication for octocat/Hello-World,
  commit 7fd1a60b01f91b314f59955a4e4d4e80d8edf11d. Classified needs_adapter; no invented UI.
  Private-repository access was not newly tested. Existing GitHub APK release support remains.
- Groovy generation is tested in Python, not a separate native compilation. Flutter,
  React Native and arbitrary stateful interfaces are not claimed as automatic source renders.
- Earlier NEXUS Agent/Canvas fixtures still have the previously documented validation
  limits; successful Weather rendering does not validate those fixtures.

## NEXUS source render validated — 2026-10-15

- PASS: all 12 scenarios in `render_profiles/nexus.json` compiled and rendered from the
  current local NEXUS sources (`versionName=1.6.3-results`, versionCode 17).
- JUnit: tests=12, failures=0, errors=0, skipped=0, execution 32.714 seconds.
  Gradle: BUILD SUCCESSFUL in 5m 17s, 26 actionable tasks, 22 executed, 4 from cache.
- Visual review: Canvas empty/loading/error, Aurora/Light; empty Library, Models and
  Progress in both themes. Loading and error indicators appear in the relevant Canvas
  screenshots. Model logos can remain async placeholders as previously documented.
- ASUS connection failed; compilation used the PC with limited workers/priority.
  No phone, emulator, actual account data or paid generations were used.
- Evidence: ignored session `sessions/nexus-source-confirmed-20261015-01/`:
  `render.json`, `source-provenance.json`, `render.log`, both `source-overview-*.jpg`,
  and `source/app/build/test-results/testDebugUnitTest/TEST-explorer.render.ExplorerSourceRenderTest.xml`.
- Original source/profile snapshots and hashes were retained. The report adds the
  post-capture review separately; the distributed profile now records completed validation.
- This supersedes the pending status for the local NEXUS Canvas fixture only. The
  separate `nexus-agent.json` profile and the S25's newer Agent APK are not covered.
