# Subagentes de Nexus Agent

Estado: propuesta para aprobación previa a implementación. Fecha: 2026-10-20.

## Experiencia y alcance

El agente principal divide una petición en tareas de investigación, selección de modelos, preparación de prompts y generación. Cada subagente trabaja en una tarea explícita y devuelve un resultado al principal. La generación queda incluida en el diseño solicitado; no se habilita por publicar este documento.

La conversación presenta una tarjeta por tarea con nombre, objetivo, modelo, estado y coste. Estados: pendiente, trabajando, esperando confirmación, enviado al proveedor, terminado, cancelado y fallido. Un detalle desplegable muestra pasos y archivos; el chat principal recibe el resultado útil, sin transcripciones completas de cada trabajador. Las generaciones conservan sus tarjetas de encargo actuales y se enlazan desde la tarea para evitar acciones de pago duplicadas.

V1 propone un nivel de delegación y dos trabajadores simultáneos como valores iniciales, nunca como restricciones fijas. Ambos se editan desde Ajustes → Límites: 0 significa sin tope local. Las tareas que excedan la concurrencia elegida esperan en cola; los hijos pueden delegar hasta la profundidad configurada. La autorización de subagentes para preparar generaciones también es un ajuste del usuario. Estos valores ya pueden guardarse en la APK, pero solo se aplicarán cuando se apruebe e implemente el motor de delegación. El principal puede seguir conversando, pero una nueva instrucción no autoriza por sí misma a reenviar una generación anterior.

## Contrato y ejecución propuestos

`delegate_task` recibe objetivo, rol (investigación, modelos, prompts o generación), dependencias por ID y referencias por asset ID. Devuelve un task ID. `get_delegated_task` consulta progreso y resultado; `cancel_delegated_task` detiene una tarea. Son interfaces propuestas, todavía inexistentes.

Cada tarea conserva parentTurnId, conversación, proyecto, objetivo, estado, modelo, referencias, dependencias, plan/encargo vinculado, coste estimado, coste real si está disponible, y motivo de parada. El principal asigna un contexto mínimo: objetivo, reglas vigentes de proyecto, referencias seleccionadas y resultados de dependencias. No se copian claves, conversaciones ajenas ni toda la biblioteca. Los modelos proceden de los cerebros configurados y sus capacidades; si falta un cerebro compatible, la tarea falla con una explicación y no cambia de proveedor silenciosamente.

El coordinador pertenece al motor del agente. Una cola limita la concurrencia y un registro persistente conserva tareas y relaciones con encargos. El perfil de permisos de un trabajador nunca supera el del principal. Las dependencias tienen que existir dentro del mismo grupo, sin ciclos. Tras reiniciar, las tareas LLM interrumpidas quedan pausadas y requieren reanudación; las generaciones ya enviadas se recuperan por su job ID.

## Coste y confirmación de generaciones

Todos los trabajadores comparten los contadores del mensaje principal y el consumo diario. Cada llamada reserva de forma atómica presupuesto y llamadas disponibles antes de ejecutarse; al terminar liquida consumo real y libera reservas. También se contabilizan los turnos del coordinador. Se mantienen los valores efectivos de AgentLimitsSettings y los límites propios de cada cerebro. El tiempo del turno y las rondas de herramientas se consumen globalmente; delegar no reinicia contadores. Cero sigue significando sin límite en los campos que ya tienen esa semántica.

Sin tarifa conocida se muestra «coste sin estimación» y se mantienen los límites de llamadas y tokens; nunca se muestra coste cero por ausencia de datos. La tarjeta separa consumo del asistente y coste del proveedor multimedia.

Un trabajador de generación prepara un GenerationPlan y se detiene en esperando confirmación. El usuario confirma el plan vigente por el SpendGuard y la huella actuales. La confirmación autoriza solo ese encargo; ni el principal ni el trabajador fabrican tickets o confirman otros planes. Modificar prompt, referencias, modelo o parámetros invalida la aprobación. Una respuesta incierta se reconcilia antes de permitir otra solicitud.

## Cancelación, fallos y aceptación

Cancelar una tarea en cola la retira. Cancelar una tarea LLM interrumpe su petición y bloquea nuevas herramientas. Cancelar un grupo propaga la orden a todos sus hijos y conserva los resultados completados. Un trabajo multimedia aceptado por un proveedor se conserva: solo se intenta cancelación remota si ese proveedor la admite; en otro caso se ofrece detener seguimiento y se advierte que el trabajo puede seguir y consumir créditos.

Los fallos de un trabajador no provocan reintentos de generación. El principal recibe el error y las dependencias que no puedan continuar quedan bloqueadas. La reanudación usa el mismo task ID y conserva encargos enviados, confirmaciones y gasto.

Aceptación antes de habilitar: concurrencia limitada; ningún presupuesto puede excederse por carreras; cancelación de cola/LLM y proveedor diferenciada; resultados parciales conservados; dependencias fallidas visibles; recuperación sin dobles envíos; ningún gasto sin confirmación; aislamiento de conversación/proyecto; y pruebas con proveedores simulados para errores, timeouts, cambio de plan y reinicio. La prueba real de generación requiere una autorización específica de coste y modelo.

## Regla de personalización

Toda restricción de producto introducida por esta función debe tener control visible y persistente en la APK; no se admiten techos internos que sustituyan lo que eligió el usuario. Cero desactiva los máximos locales. Los límites reales del modelo/proveedor se identifican por su contrato; los valores iniciales de Nexus no se presentan como capacidades del proveedor. El diseño no fija un máximo de cuatro trabajadores ni impide de forma irrevocable la delegación entre subagentes.
