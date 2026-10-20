# Turnos del agente en segundo plano — 1.9.62 / vc96

Base inicial comprobada: ambos teléfonos tenían `com.vidadomu.nexus.agent`,
1.9.60-sync-preview / vc94. Antes de esta instalación ya se habían actualizado
a 1.9.61-sync-preview / vc95. Esta rama incorpora la sincronización móvil
publicada en `c650f14`, sin modificar sus claves ni opciones.

## Corrección

Antes, `AssistantTurnService` solo mostraba una notificación. El trabajo dependía
del `viewModelScope` de la Activity, los errores al iniciar el servicio se
ocultaban y el servicio no adquiría un bloqueo de CPU. Son condiciones
comprobadas en el código; no se conserva una traza del fallo original del usuario
que permita atribuir cada interrupción a una de ellas.

Ahora la sesión del asistente pertenece a la Application y se reutiliza al
reabrir la Activity. El servicio ejecuta la petición después de promoverse a
primer plano. Devuelve el resultado o error al mismo estado de conversación.
Un fallo de promoción impide empezar la llamada, en lugar de continuar sin
protección. Tanto el envío normal como el diagnóstico usan esta ruta.

El servicio mantiene un bloqueo parcial de CPU durante el turno, con una concesión
de diez minutos renovada mientras haya trabajo. Lo libera al terminar, cancelar,
destruirse o recibir el límite de tiempo de Android. «Parar» funciona desde el
chat y desde la notificación. El servicio es `START_NOT_STICKY`: una interrupción
no repite solicitudes ni herramientas que podrían haber consumido cuota.

Se conservan los límites configurados del usuario. El bloqueo mantiene la CPU,
no enciende la pantalla ni desactiva las políticas globales de batería.
Referencias oficiales: [wake locks](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock)
y [límite de los servicios dataSync](https://developer.android.com/develop/background-work/services/fgs/timeout).

Esta entrega incorpora también los controles de evidencia visual descritos en
`VISUAL-EVIDENCE-20261020.md`, adaptados al contrato Android de documentos nativos.

## Validación

`AssistantBackgroundTurnTest` solo se ejecuta en `.backgroundqa`, una instalación
aislada sin las claves ni los chats de la aplicación principal. Usa un LLM local
simulado para probar el envío real del ViewModel, destrucción de Activity,
pantalla apagada, persistencia de la respuesta, identidad de la sesión al
reabrir, cancelación, solicitudes consecutivas y destrucción del servicio.
Comprueba el servicio real y la adquisición/liberación del bloqueo de CPU.

No envía inferencias ni generaciones a proveedores. Las pruebas y la instalación
final deben informarse separadamente; no equivalen a validar una conversación
real con Antigravity ni a recuperar automáticamente un proceso forzado a cerrar.

La variante Agent conserva la firma privada existente; no se crea una keystore.
Los APK, certificados, informes de pruebas y verificaciones del dispositivo se
guardan en `outputs/android-background-20261020/`, fuera de Git.

## Resultado comprobado

- Compilación completa en AXELPC: `:agent-core:test`, `:app:assembleOfflineQa`,
  `:app:assembleOfflineQaAndroidTest` y `:app:assembleAgentPreview` PASS.
- Núcleo: 288 pruebas, 284 PASS, 4 omitidas, 0 fallos.
- Pixel 8 Pro físico: 4/4 pruebas instrumentadas PASS en 8,188 segundos.
  El envío del ViewModel terminó con Activity destruida y pantalla apagada;
  se comprobó la respuesta guardada, la misma sesión al reabrir y la liberación
  del servicio/bloqueo. También pasaron cancelación, destrucción del servicio
  sin reenvío y tres turnos consecutivos sin repetición.
- Una primera prueba de persistencia usaba `AtomicFile.openRead()` en un bucle
  concurrente con la escritura. Esa lectura puede retirar el archivo `.new`:
  se corrigió la prueba para observar el archivo comprometido sin recuperarlo
  mientras el escritor está activo. El fallo inicial no demostraba una parada
  del proveedor: la respuesta ya había terminado y estaba en el estado del chat.
- Instalación sobre vc95, con la firma existente, en Pixel 8 Pro y Samsung S25
  Ultra (SM-S938B): **1.9.62-background-preview / vc96**. Inicio de la Activity PASS
  en ambos. Los cuatro archivos comprobados (configuración del asistente,
  perfiles, preferencias seguras y chat activo) mantuvieron sus hashes antes y
  después de la actualización. No se desinstaló la aplicación principal.
- Se retiraron únicamente los dos paquetes temporales `.backgroundqa` y
  `.backgroundqa.test` del Pixel, creados para estas pruebas.

APK: 42.571.820 bytes.

SHA-256: `e8a45b8d08605a5e0f03a3a574f78a9b73c0f74d560ab71be54957dcfc995d8e`.

Certificado SHA-256:
`8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.

El hash del APK instalado se verificó en los dos teléfonos. Los informes locales
son `pixel-instrumentation.txt`, `installed-update.json`, `launch-health.json`
y `build-identity.json` dentro de la carpeta de outputs indicada arriba.
La prueba usa un proveedor simulado: no valida la disponibilidad o conexión real
con Antigravity durante un turno largo. No se ejecutaron inferencias ni
operaciones multimedia de pago. La detención forzada del proceso y las
restricciones del sistema siguen siendo interrupciones, no reintentos automáticos.
