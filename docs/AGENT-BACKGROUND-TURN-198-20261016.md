# Turnos del asistente en segundo plano — Android 1.9.8

## Cambio

Al enviar un mensaje, Nexus inicia un servicio Android de primer plano con tipo
`dataSync`. El turno sigue ejecutándose en el `AssistantViewModel`; el servicio
mantiene el proceso en la categoría de trabajo visible para Android mientras la
actividad está minimizada. Una notificación silenciosa informa de que el agente
sigue trabajando. El servicio se detiene al completar, fallar o cancelar el turno.

No cambia el proveedor LLM, las herramientas, los límites de petición, el historial,
el cobro ni el comportamiento de generación. La respuesta y el error siguen el flujo
actual del chat.

En el mismo parche, la navegación inferior vuelve a la pestaña que estaba debajo de
Ajustes/Flow sin restaurar una pantalla de Ajustes obsoleta. Los resultados abiertos
o creados por herramientas se adjuntan a la respuesta: imágenes visibles, vídeo y
audio reproducibles, y documentos o archivos con apertura desde la tarjeta del chat.

## Límite

Esto evita que Android trate el trabajo como una actividad ordinaria en segundo plano;
no puede recuperar un turno si el usuario fuerza la detención de Nexus, reinicia el
teléfono o el sistema termina el proceso por una condición extrema. El servicio es
`START_NOT_STICKY`, para no fingir que un turno en memoria puede reanudarse tras morir
el proceso.

## Verificación

- `:app:assembleAgentPreview`: **PASS** en AXELPC; no se ejecutaron tests.
- S25 (`SM-S938B`): instalación en sitio `-r`, **PASS**; paquete comprobado como
  `1.9.8-agent-preview`, `versionCode 42`. El SHA-256 del APK instalado es
  `D1A7F57A45439DFE2690D864EF02BFAFC4C65EC4227B834AF7C89DF787D58E68`.
- Se conservó el historial y la configuración del paquete anterior. La firma coincide
  con el APK instalado antes de actualizar.
- No se hizo una llamada real al agente ni se verificaron visualmente los nuevos
  reproductores o el cambio de navegación; no hubo operaciones pagadas.
