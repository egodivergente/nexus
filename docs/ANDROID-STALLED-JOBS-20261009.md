# Recuperación de Nexus Agent en Samsung — 2026-10-09

## Dispositivo y alcance

Samsung S25 Ultra (SM-S938B), perfil Android 0, `com.vidadomu.nexus.agent` 1.8.2-agent-preview (versionCode 26). Consulta de trabajos existentes y estado de Flow, conservación de datos y credenciales. No se enviaron nuevas generaciones ni reintentos de pago.

## Tres vídeos aparentemente atascados

Los tres trabajos de Atlas Cloud `minimax/h3/reference-to-video` seguían PROCESSING localmente, aunque la consulta de sus identificadores remotos devolvía HTTP 500 con `data.status=failed` y un rechazo upstream por superar nueve imágenes de referencia. La implementación descartaba todo HTTP no satisfactorio antes de interpretar el estado terminal, por lo que el seguimiento no finalizaba.

Se contrastó la identidad de cada predicción y se respaldaron la base de datos y preferencias antes de actualizar únicamente esos tres registros a FAILED con su causa. Verificación posterior: SQLite `integrity_check=ok`, 103 generaciones, 95 resultados conservados y preferencias de credenciales idénticas. La copia original y evidencias privadas quedan fuera de Git.

Durante la transferencia inicial de la base por stdin, ADB truncó los bytes en Windows. La aplicación permaneció detenida; se corrigió inmediatamente mediante `adb push` y copia en su directorio privado, verificando el archivo íntegro y su hash antes de reabrirla. No quedó instalada la copia truncada ni se perdieron datos.

Cambios permanentes en código:

- Interpretar errores HTTP de Atlas como terminales únicamente con ID coincidente y estado remoto confirmado. Los errores transitorios, HTML, estados activos y respuestas de otra predicción siguen siendo reintentables.
- Aplicar el máximo confirmado de nueve imágenes para H3 cuando su esquema omite `maxItems`, manteniendo límites publicados más estrictos. Validar antes de construir la solicitud compartida de agente y lienzo, sin descartar referencias silenciosamente.
- Recuperar el seguimiento de todos los trabajos activos con ID remoto al abrir la app, usando trabajo único KEEP y requisito de conexión.
- Mostrar errores de seguimiento también en tarjetas activas, conservando el trabajo sin reenviarlo.

Referencia primaria del límite: https://github.com/MiniMax-AI/MiniMax-H3. La respuesta real de Atlas confirmó el mismo máximo.

## Agente y Sonnet

Se cambió la preferencia existente de Haiku a `us.anthropic.claude-sonnet-4-6`, conservando backend y región. Una instrumentación temporal sobre el paquete instalado confirmó ese valor en tiempo de ejecución. Esto verifica selección persistida, no una conversación facturada nueva.

El código de la barra inferior restauraba la pila anidada de Agente, incluyendo Ajustes. Se desactiva la restauración de esa pila al tocar Agente; la conversación continúa siendo propiedad del ViewModel de la actividad. Se incorpora una prueba Compose de Agente → Ajustes → Modelos → Agente.

## Flow

Consulta autenticada desde el paquete real de Agent en S25: HTTP 200, `bridge_status=connected`, `generation_ready=true`, emparejamiento presente. También se verificó el estado en el ASUS. Dos consultas anteriores desde el dispositivo expiraron mientras el ASUS compilaba; una consulta posterior tuvo éxito. No se atribuye una causa definitiva a esos timeouts.

La instrumentación consultó únicamente estado, sin publicar prompts ni registrar tokens. No se comprobó una generación completa con créditos. El puente actual trabaja con el proyecto abierto de Flow.

## Procedencia y límite de instalación

La fuente sincronizada más reciente disponible corresponde a 1.8.1 (versionCode 25), commit base `bbd024a`. El APK 1.8.2 contiene clases adicionales de intención creativa y seguimiento de trabajos que no están en esa fuente. La corrección se desarrolla en una rama aislada sobre la fuente disponible, sin sustituir el APK Agent instalado por una versión anterior. La instalación definitiva requiere recuperar las fuentes exactas de 1.8.2 e integrar estas correcciones.

## Validación

- ASUS: copia aislada del código actual con manifiesto de hashes; Java 17 y SDK 35; Gradle con prioridad baja, un trabajador y sin paralelismo.
- `:agent-core:test` y `:app:testOfflineQaUnitTest`: 337 pruebas, cero fallos y cero errores.
- El empaquetado en ASUS se detuvo tras verificar presión de memoria, uso de swap y esperas de E/S sostenidas; se detuvo exclusivamente el daemon de esta copia aislada. Los informes unitarios se recuperaron.
- Fallback de empaquetado en AXELPC: Java 17, SDK 35, un trabajador, sin paralelismo y prioridad BelowNormal.
- `:app:assembleOfflineQa` y `:app:assembleOfflineQaAndroidTest`: BUILD SUCCESSFUL en Windows (18m 56s). Se comprobó que las 412 entradas del manifiesto remoto y las fuentes locales no tienen diferencias semánticas; dos ficheros solo difieren en finales de línea.
- APK QA y paquete de pruebas instalados mediante actualización, conservando datos. Identidad `com.vidadomu.nexus.qa`, versionCode 25; firma verificada y SHA-256 del APK instalado coincidente con el compilado.
- APK QA SHA-256: `c23d3c77dbe1bdf315b1f1711b35a7d422188b72f78f8d4bc67ec412959b0c19`.
- APK de pruebas SHA-256: `2e781fc8186352c13469035249e5a9ba586b2a2bc85fb6244febca124b191e89`.
- Prueba Compose de navegación en S25 desbloqueado: PASS, `OK (1 test)`, Agente → Ajustes → Modelos → Agente vuelve a la conversación. El intento previo con el dispositivo bloqueado falló con `No compose hierarchies found`; se repitió tras desbloqueo confirmado por el usuario.
- En una verificación posterior, la base ya tenía 106 trabajos (85 completados, 21 fallidos) y el modelo seleccionado figuraba como Opus 4.6. Se conservaron esas modificaciones posteriores: los tres trabajos recuperados continúan FAILED y SQLite íntegra. La selección inicial de Sonnet quedó verificada entonces, sin sobrescribir después la preferencia nueva.
- Se retiró la instrumentación temporal de diagnóstico de Flow y la copia transitoria de la base en `/data/local/tmp`; los respaldos originales siguen conservados fuera de Git.
