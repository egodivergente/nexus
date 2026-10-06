# Modalidades de la cuenta Flow en Nexus

Verificación del 2026-10-06 en la cuenta existente, mediante el puente local.

| Modelo | Entradas | Formato | Duración | Resolución nativa |
|---|---|---|---|---|
| Nano Banana Pro / Nano Banana 2 | Texto y hasta 10 imágenes de referencia | 1:1, 16:9, 9:16, 4:3, 3:4 | — | Escalados disponibles sobre el resultado |
| Omni 1.1 Flash | Texto, inicial, inicial/final, hasta 7 referencias | 16:9, 9:16 | 4, 6, 8, 10 s | 360p, 720p |
| Veo 3.1 Fast / Lite | Texto, inicial, inicial/final, hasta 3 referencias | 16:9, 9:16 | 4, 6, 8 s; referencias: 8 s | 720p; mejora posterior a 1080p/4K según el menú |
| Veo 3.1 Quality | Texto, inicial, inicial/final | 16:9, 9:16 | 4, 6, 8 s | 720p; mejora posterior según el menú |

El selector de un modelo de Flow abre por defecto las variantes de Google Flow. La disponibilidad de la configuración se consulta automáticamente y caduca a los cinco minutos. Un valor real de cero se muestra como **Incluido**; no se convierte en dólares ni se solicita una clave de API. Los créditos que pueda indicar Flow corresponden a la cuenta. No se compra crédito ni se modifica la suscripción.

Los fotogramas y referencias usan los identificadores del almacén privado de Nexus. El puente verifica las imágenes y guarda instantáneas por SHA-256 antes de reservar su trabajo. Un identificador reservado no puede cambiar entradas, modelo, duración o resolución ni repetir el envío.

Se inspeccionaron solicitudes reales de texto, fotogramas y referencias, abortando todos los POST en la fase de envío. Se comprobó que los RPC conservan los fotogramas en sus campos estructurales, la lista de referencias y el modelo exacto. Las referencias no se dan por confirmadas porque sus identificadores aparezcan en el prompt. Se corrigieron los controles `4 s`/`6 s`/`8 s`/`10 s`, las duraciones de referencias de Omni y los contratos de inicio/final `*_fl` de Veo.

La lectura de Nano Banana Pro 1:1 devolvió cero. También se leyeron configuraciones de Omni 4 s/360p, Omni con referencias 10 s/720p y Veo con fotogramas 4 s. Cada lectura corresponde a su configuración; no se reutiliza el importe de otro modelo o modalidad. Las inspecciones no completaron generaciones nuevas ni prueban todas las combinaciones posibles.

La biblioteca de escritorio ofrece **Editar con Omni** sobre vídeos completados de Flow de hasta 10 segundos. Se edita el clip completo, manteniendo el original. La lectura del editor confirma disponibilidad; su importe no está expuesto y se muestra **Disponible en tu cuenta**, sin convertirlo en cero créditos. El RPC `jIps6` debe conservar el vídeo original en su campo estructural, el intervalo completo, el formato, el prompt y el modelo `abra_edit`. El identificador se reserva antes del envío y no se repite tras un fallo. Un resultado aceptado se recupera exclusivamente por el identificador de su activo nuevo.

Omni con referencias ofrece 15 voces predefinidas observadas en el selector de esta cuenta. La voz viaja por el campo `reference_audio` del trabajo nativo y se comprueba en el campo de audio del RPC, además de las referencias visuales. Una voz en el texto del prompt no demuestra selección. La disponibilidad/cotización incluye la voz elegida. **Automática** conserva la selección por defecto sin imponer una voz.

Se comprobaron la disponibilidad del editor con un clip existente de ocho segundos y solicitudes reales de edición y de referencias con Charon, abortadas antes de Google. Las pruebas no completaron una edición o un vídeo nuevo con voz. La personalización/creación de voces, el recorte de entradas largas y la importación de vídeos externos siguen pendientes. La ayuda actual de Google indica que la extensión con Omni está **próximamente**; la extensión disponible pertenece a Veo Lite y tampoco está integrada aquí. La matriz de capacidades del servicio está en [Google Flow](https://support.google.com/flow/answer/16352836?hl=en), y la edición/extensión en [su ayuda de edición](https://support.google.com/flow/answer/16935718?hl=en).

Pruebas locales: `desktop/tools/test-flow-modalities.py`, `desktop/tools/test-flow-model-arguments.js`, `desktop/tools/test-flow-auto-availability.js`, `tools/flow-bridge/test_bridge.py`, `tools/flow-bridge/test_video_guard.py`. El diagnóstico `probe_modalities.py` solo admite imágenes sintéticas del almacén privado y nunca debe ejecutarse sin el bloqueo de todos los POST antes del clic final. Sus evidencias locales no se publican.

Edición/voces: `desktop/tools/test-flow-edit.py`, `tools/flow-bridge/test_video_edit.py`, `tools/flow-bridge/test_flow_voices.py`; ejecución con el Python privado del puente. No compilan APK/EXE ni envían medios nuevos.
