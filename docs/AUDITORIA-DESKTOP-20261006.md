# Auditoría de NEXUS para Windows — 6 de octubre de 2026

Se revisaron el host Python, la interfaz JavaScript, el puente de Google Flow y la instalación local. Se corrigieron dos riesgos de pérdida de datos y tres problemas de integridad/validación. Las pruebas no solicitaron generaciones ni inferencia.

## Hallazgos y correcciones

| Prioridad | Fallo comprobado | Corrección | Verificación |
|---|---|---|---|
| P1 | Si fallaba una descarga después de elegir un archivo existente, se truncaba o eliminaba el original. | Descargar en un temporal del mismo directorio y reemplazar únicamente al finalizar; comprobar Content-Length cuando exista; limpiar solamente el temporal. | Timeout, descarga truncada y reemplazo denegado conservan el original. Descarga completa lo reemplaza. |
| P1 | Una ventana con una biblioteca antigua podía borrar trabajos añadidos desde otra; solo se protegían los trabajos Flow ausentes. | Combinar trabajos de todos los proveedores por ID y proteger los estados terminales frente a una copia antigua. Una reconciliación puede pasar de fallo incierto a completado. | Guardados con copias antiguas mantienen Atlas, Runway, Crun y Flow. No se modifica el objeto recibido. Resultados y estados terminales se conservan. |
| P2 | Tener una salida parcial convertía un estado `failed` o `processing` en `COMPLETED`. | Respetar el estado explícito del proveedor; aceptar resultados inmediatos sin estado; limpiar errores anteriores al completar. | 14 escenarios de respuesta de proveedores, incluidos resultados parciales y recuperación. |
| P2 | El envío compartido conservaba referencias a parámetros, archivos y precio modificables durante una espera. | Tomar una copia del plan confirmado antes del primer `await`. | Cambiar el objeto original durante el guardado no altera cuerpo, referencia, parámetros ni precio enviados por el simulador. |
| P2 | Content-Length no numérico y mensajes RPC de tipo incorrecto podían acabar en una excepción del manejador HTTP. | Rechazar esas entradas con HTTP 400 antes del despacho. | Solicitudes HTTP reales contra un servidor aislado. Origen o token incorrectos siguen devolviendo 403; no se ejecuta ninguna operación. |

Archivos de implementación: `desktop/local_host.py` y `desktop/ui/app.js`. Pruebas nuevas: `desktop/tools/test-host-integrity.py`, `desktop/tools/test-dispatch-integrity.js` y `desktop/tools/audit-installed.py`.

La protección de la biblioteca cubre trabajos e integridad del resultado. Los cambios simultáneos de preferencias, presets y borradores siguen teniendo semántica de último guardado; no se ha añadido sincronización colaborativa.

## Pruebas ejecutadas

- Integridad del host: **9 pruebas PASS**. Descargas atómicas, biblioteca entre ventanas, reconciliación, conservación de múltiples resultados y validación HTTP.
- Integridad de envío: **PASS** para las respuestas de cuatro proveedores y la copia del plan durante un guardado suspendido; cero tráfico de red.
- Motor de formularios: **20 pruebas PASS**; límites de texto: **14 comprobaciones PASS**; estimaciones del agente Flow: **7 escenarios PASS**.
- Host del agente: **6 pruebas PASS**; caché de esquemas: **4 PASS**; proveedor local Flow: **6 PASS**.
- Puente Flow: **25 pruebas PASS**, usando su entorno virtual fijado. Incluyen idempotencia, fallos ambiguos, comprobación de archivos, vídeos y exportaciones.
- Regresión de creación: **PASS** en orden de guardado, recuperación de borrador, invalidación de presupuestos antiguos, copia de configuración y diseños de 1360/760 píxeles.
- Regresión del asistente: **PASS** en Enter, Shift+Enter, IME, foco, conservación del texto y reconexión real del host, incluido cambio de token HTTP 403. Cuatro tamaños de ventana; respuestas de conversación simuladas.
- Biblioteca con **1.000 trabajos simulados**: muestra inicial de **36 tarjetas**, filtros y comparación funcionales; cinco renderizados entre **15,2 y 21,6 ms** en esta medición. Polling sin cambios conserva los nodos y no guarda el estado; un lote pausado no envía la siguiente solicitud simulada.

Los primeros lanzamientos de dos pruebas de interfaz no detectaron su host porque el ejecutable del entorno virtual de Windows crea otro proceso con un PID distinto. Se ejecutaron correctamente con el Python base que ya dispone de Playwright. El puente Flow necesita su propio entorno virtual para importar gflow-cli. Esto se distinguió de fallos del producto.

## Instalación comprobada

La instalación contiene **843 modelos**, de los cuales **5** pertenecen al puente de la cuenta Flow; **91 trabajos**, **90 salidas registradas** y **34 herramientas del agente**. El catálogo Flow no contiene Nano Banana Lite. La cifra de salidas registradas no garantiza que cada URL externa antigua siga disponible.

Se capturaron Biblioteca, Modelos, Crear, Asistente y Ajustes a **1360, 760 y 480 píxeles**: quince combinaciones sin errores de JavaScript ni desbordamiento horizontal. También se inspeccionaron capturas del lienzo estrecho, biblioteca y asistente cargado. Las URLs externas y consultas de saldo se bloquearon en esta comprobación, por lo que los mensajes «No disponible» de las capturas no son una prueba del saldo real.

Se reinició únicamente el host local propio, oculto, con prioridad BelowNormal y el mismo puerto, después de comprobar que el agente estaba libre y que no había envíos locales ni trabajadores Flow activos. Se verificó que los hashes de biblioteca, credenciales cifradas y ajustes del asistente permanecían iguales. Se conservaron cinco tareas Atlas con estado remoto PROCESSING; reiniciar el host local no cancela sus tareas en el proveedor. Sus estados actuales no se consultaron al proveedor en esta auditoría.

No se cambió Kotlin ni se generaron APK, EXE o JAR. La comprobación previa de 93 pruebas Kotlin pertenece a la implementación anterior de Flow; no se presenta como una nueva compilación de esta auditoría. Las pruebas Android quedan fuera de este resultado.

## Límites y mejora pendiente

La mejora pendiente de recuperación desde la interfaz quedó resuelta en la implementación posterior de [Flow y UX](MEJORAS-FLOW-UX-20261006.md): los detalles ofrecen «Recuperar resultado» o «Comprobar resultado». El host consulta `flow_get_result` con el mismo identificador y sin volver a generar. Se mantiene la regla de no reenviar automáticamente una generación ambigua.

Esta auditoría no volvió a generar medios, no probó el upscale de vídeo 4K de pago, no verificó saldos o cargos y no comprobó individualmente los 843 modelos contra sus proveedores. No constituye una prueba de penetración completa.

La evidencia principal se conserva en `outputs/audit-nexus-desktop-20261006/`, excluido de Git. Las regresiones existentes también usan sus directorios aislados `outputs/app-optimization-20261006/` y `outputs/assistant-reliability/`. Ninguna captura ni almacén de datos privados se publica. Las correcciones se publican en `codex/nexus-desktop-audit`, basada en la rama de la integración Flow. Se preservan los cambios de interfaz de otras tareas del workspace; no se incluyen indiscriminadamente en este commit.
