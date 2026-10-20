# Turnos del agente en segundo plano — 1.9.62 / vc96

Base comprobada: ambos teléfonos tenían `com.vidadomu.nexus.agent`,
1.9.60-sync-preview / vc94. Esta rama incorpora además la sincronización móvil
publicada en `c650f14` (1.9.61/vc95), sin modificar sus claves ni opciones.

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
