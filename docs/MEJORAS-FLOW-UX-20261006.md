# Mejoras de Flow y UX — NEXUS Windows, 6 de octubre de 2026

Implementadas y aplicadas sobre la instalación local, preservando biblioteca, credenciales cifradas y ajustes. No se generaron medios ni se solicitó inferencia durante la validación.

## Recuperación de trabajos

Los detalles de un trabajo Flow permiten **Recuperar resultado** o **Comprobar resultado**. El endpoint autenticado `flowRecover` utiliza exclusivamente `flow_get_result` con el identificador ya reservado; no cotiza, no crea un trabajo ni vuelve a enviar el prompt.

La recuperación funciona en segundo plano, muestra su estado en las tarjetas y actualiza los detalles abiertos al finalizar. Doble clic y solicitudes simultáneas comparten una única reserva de trabajador. Los resultados completados disponibles se comprueban sin abrir otro trabajador. Si el proceso se interrumpe otra vez, el trabajo queda pendiente de comprobar y permite otra lectura del mismo identificador.

Una revisión persistente de recuperación impide que una ventana antigua restaure el fallo anterior o borre el resultado recuperado. Los errores del trabajador y los fallos al iniciar un hilo liberan la actividad; una reserva de envío fallida no se vuelve a enviar automáticamente. Un trabajo desconocido se rechaza sin generar.

## Cambios de uso

- Corregida la detección de generación Flow por el agente: Nano Banana 2 declara `TEXT_TO_IMAGE` y Veo Fast declara `TEXT_TO_VIDEO`. Antes tenían capacidades vacías y las consultas por capacidad devolvían cero candidatos aunque aparecieran en el catálogo. Escalado y exportación conservan sus capacidades independientes.
- Selector de modelos con formato, familia, proveedor, búsqueda, recuento y carga incremental de 50 modelos. Al usar una imagen como referencia, la selección se restringe a modelos con soporte de imagen declarado.
- Modelos Flow con descripción funcional y el recurso de marca Google ya existente.
- Biblioteca con filtros que se pueden quitar de una vez y mensajes distintos para biblioteca vacía, solicitudes pendientes y búsqueda sin coincidencias. Se conserva la presentación limitada a 36 resultados iniciales.
- Detalles con estados legibles, fecha de creación, estimación separada del cargo real, advertencia de envío incierto y navegación entre las salidas de un mismo trabajo.
- Cerrar detalles devuelve el foco a la tarjeta. Escape cierra el panel cuando no hay un diálogo abierto.
- Los desplegables reflejan su valor y su estado deshabilitado, cierran menús cuyo control desaparece y conservan el foco tras un cambio. Los menús se sitúan dentro del diálogo modal, para que reciban clics en su capa. Escape cierra primero el menú y mantiene abierto el diálogo.
- Consulta de mejoras Flow con errores visibles y posibilidad de volver a consultar. Una respuesta tardía no reabre un diálogo cancelado ni sustituye otro diálogo.
- Opciones de mejora restringidas a resoluciones compatibles e importes numéricos válidos. Una consulta de más de cinco minutos caduca y deshabilita la creación hasta actualizarla. La confirmación utiliza la hora de la consulta original y evita dobles clics.
- El contador de texto conserva los límites conocidos; cuando no existe un límite confirmado muestra solamente los caracteres escritos.

**Nano Banana Lite está bloqueado**: se excluyeron seis variantes de la selección y del catálogo utilizado por el agente; el envío compartido y el host también rechazan sus identificadores. Se conservan los archivos y los trabajos históricos. El catálogo de partida pasa de 843 a **837 modelos**, incluidos los **5** Flow de esta rama. La instalación incorpora además cuatro entradas Flow de una modificación paralela: la última comprobación registró **841 modelos y 9 entradas Flow**, sin Nano Banana Lite. El bloqueo afecta a Nano Banana Lite; no prohíbe Veo Lite.

## Verificación

| Comprobación | Resultado |
|---|---|
| Backend de recuperación, reservas, interrupciones, datos malformados y bloqueo Lite | **10 pruebas PASS** |
| Adaptador Flow, detección por capacidad y restricciones de salida | **7 pruebas PASS** |
| Herramientas Kotlin instaladas `list_models` y `get_model_brief` | **PASS**: detectan generación de imagen/vídeo y escalado de imagen por proveedor, modalidad y capacidad |
| Integridad de biblioteca y descargas de la auditoría anterior | **9 pruebas PASS** |
| Respuestas de proveedores, copia del plan confirmado y bloqueo Lite del cliente | **PASS** |
| Motor de formularios, límites de texto y estimaciones Flow | **20 pruebas, 14 comprobaciones y 7 escenarios PASS** |
| Recorridos de UI: recuperación única, varias salidas, filtros, paginación, teclado, cancelación, error y caducidad de opciones | **PASS**, sin errores de JavaScript |
| Selector y diálogo de mejoras a 1360, 760 y 480 píxeles | **PASS**, sin desbordamiento horizontal; capturas inspeccionadas |
| Cinco pantallas de la instalación real a tres anchos | **15 combinaciones PASS**; 91 trabajos, 90 salidas registradas y 34 herramientas conservados |
| Regresiones de creación y del asistente | **PASS** en borradores, guardado ordenado, presupuestos, teclado y reconexión |
| Recuperación real de una imagen previamente completada | **PASS** por HTTP local autenticado → MCP → copia verificada |

La prueba real recuperó una imagen existente en un directorio aislado de NEXUS. Se simuló la ausencia de resultado únicamente en esa copia del registro local; no se alteró el trabajo real de Flow. El SHA-256 de original y copia coincide. La cola nativa tenía **4 generaciones antes y 4 después**; se comprobaron también los hashes de los datos protegidos de la aplicación principal.

Las pruebas de UI usan respuestas de proveedor simuladas y el modo aislado bloquea los envíos reales. La comprobación de la instalación bloquea consultas externas y operaciones de generación: el texto de conexión/saldo de sus capturas no verifica el estado real de la cuenta. La respuesta real del MCP se validó por separado.

La regresión del agente ejecuta el JAR instalado con un proxy LLM simulado en localhost, credenciales ficticias y datos temporales. Las respuestas de herramientas y esquemas proceden del motor Kotlin y del catálogo Flow reales; las llamadas al modelo de lenguaje están simuladas y no consumen inferencia. No se recompiló el agente ni se alteró su política de confirmación. Una ventana que ya estuviera abierta debe recargarse una vez para cargar el catálogo y los scripts actualizados.

La regresión antigua de saldos esperaba dos textos «No disponible». Una modificación paralela del workspace cambió el texto de conexión de Flow. Se conservó esa modificación y se adaptó solo la copia privada de la prueba para comprobar el contrato del saldo sin exigir el texto antiguo.

Al ampliarse el catálogo Flow en la tarea paralela, la comprobación privada de la instalación pasó a admitir las entradas adicionales y comprobar específicamente la ausencia de Nano Banana Lite; la expresión antigua confundía Veo Lite con el modelo de imagen prohibido. Los recorridos de filtros comprueban las cantidades del catálogo actual. La declaración de capacidades se aplica a las entradas de generación desde texto del adaptador, por lo que también cubre las variantes añadidas sin mezclar escalado/exportación con generación.

No se modificó Kotlin ni se compiló o empaquetó APK, EXE o JAR. No se repitió la generación de imagen/vídeo ni el upscale 4K de pago. No se afirma que cada modelo del catálogo o cada URL histórica se haya validado contra su proveedor.

La evidencia privada está en `outputs/flow-ux-20261006/`, excluida de Git. La rama `codex/nexus-flow-ux` parte de `codex/nexus-desktop-audit` y publica únicamente estas mejoras, preservando los cambios de otras tareas del workspace, incluida su gestión de conexión Flow.
