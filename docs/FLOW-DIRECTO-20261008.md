# Flow directo y conversación — 2026-10-08

## Comportamiento
- El asistente de escritorio envía Flow al recibir la petición de generar, sin tarjeta, cotización ni confirmación adicional.
- Hasta 20 resultados por encargo: un lote de variaciones o 20 escenas independientes. Formato predeterminado 16:9; se conserva el formato elegido.
- Mensajes, respuestas y resultados forman una sola conversación con scroll. Las actualizaciones conservan su posición. Los encargos independientes no se agrupan por compartir prompt.
- Mensajes, asociaciones y planes Flow con trabajos se conservan al recargar. Los resultados inciertos no se reenvían y no pausan otros encargos directos independientes; la pausa manual se respeta.

## Validación
- PASS: 19 pruebas de la cola real SQLite/hilos con transporte interceptado, incluidas idempotencia, pausa manual, recuperación y envío directo sin consulta de coste.
- PASS: interfaz en 1360x900, 480x850 y 1280x600; 20 resultados inline, actualización de resultados, posición de scroll y ausencia de tarjetas y confirmaciones Flow.
- PASS: motor Kotlin real con LLM local guionizado; count=20 y 20 escenas distintas en un segundo turno, con formato vertical explícito y sin confirmación. Ninguna llamada de generación ni inferencia externa.
- PASS: catálogo de Flow predetermina 16:9 y acepta 9:16 explícito.
- PASS: pruebas de galería, Creative Engine y Control Flow.
- PASS: compilación del runtime en ASUSVIDAL por ecosystem-build, trabajo 20261008T033355Z-b0e942c4; recuperación y SHA-256 verificados.
- Suite Gradle core:test: pendiente de completar. El selector detuvo los intentos al agotarse la reserva de RAM; la compilación del runtime y la prueba de integración del motor sí finalizaron correctamente.

## Instancia actual
Se actualizó el runtime y el host local en el puerto existente, conservando el estado y los archivos. Antes de recargar se guardaron los cuatro mensajes y dos planes Flow del chat abierto. Después se comprobaron cuatro mensajes, nueve imágenes, cero tarjetas y scroll operativo; el lote existente muestra 8/8. Se conservaron copias locales privadas, fuera de Git.

Runtime SHA-256:
- core.jar: 1d5cbf4dac15480bf9706bce57d099e500b45584afe957665b892e12f945145e
- nexus-desktop-agent.jar: 06f7fff881a7ee97da656f41b3bfd5acc3f948bceb29648ef8bd1433992ff996
