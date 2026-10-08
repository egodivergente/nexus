# Encargos con configuración propia por modelo

Desde Crear → Encargar a varios modelos, añadir modelos con + y preparar el encargo. Cada ficha tiene su prompt, parámetros nativos, número de solicitudes y referencias. El editor utiliza el esquema normalizado existente; los campos o costes que no estén soportados bloquean el envío.

Los cambios pertenecen a la ficha del modelo, sin modificar el borrador principal. Las referencias se copian solo a entradas compatibles por tipo y nombre; las ambiguas deben asignarse o retirarse explícitamente. Se pueden añadir o retirar referencias en una ficha sin afectar al resto. Los metadatos de duración de vídeo/audio se leen localmente al seleccionar archivos, antes de calcular su coste.

Modificar prompt, parámetros o referencias invalida el coste de esa ficha. «Actualizar costes» recalcula las estimaciones. Una respuesta de precio que llegue después de otra edición se descarta mediante una revisión del plan. El envío exige validación, coste vigente de todos los modelos y un total máximo de 120 solicitudes, con 1–20 por modelo. Los costes incompletos se presentan como parciales.

Los cambios del editor se conservan al cerrarlo y volver a preparar los mismos modelos dentro de la sesión si el borrador principal no ha cambiado. Este borrador de fichas no persiste tras recargar; el último encargo enviado sí conserva su registro de solicitudes en el estado privado existente.

Al confirmar se congela el plan de cada solicitud. Se mantienen hasta tres envíos simultáneos, con una sola solicitud de Flow en vuelo. Pausa/detención afectan a pendientes; no cancelan lo ya enviado. «Ver solicitudes» muestra pendientes, enviándose, enviados, trabajos en proceso, completados y envíos sin confirmar. Los pendientes detenidos se marcan «Sin enviar». No hay reenvíos automáticos.

El último encargo se puede abrir desde la cabecera después de terminar o recargar. Los resultados se enlazan al visor por trabajo. Cuando existe un trabajo real, el estado mostrado se toma de ese trabajo; «Enviado» por sí solo no significa generación completada. Tras reiniciar, una solicitud que nunca llegó a enviarse permanece sin enviar y una solicitud interrumpida no se presenta como confirmada sin evidencia del trabajo.

## Validación

- `node desktop/tools/test-studio-batch.js`: concurrencia y límite, planes independientes, estados por solicitud, sesión única de Flow, pausa/detención y fallo sin reintentos.
- `desktop/tools/test-studio-gallery.py`: dos prompts y parámetros diferentes, referencias aisladas, límites inválidos, coste invalidado tras editar, recalcular antes de enviar, mantener el borrador principal, reabrir el editor, estado durante/después del envío y registro tras recarga. Capturas del editor a 1360/480 px. Incluye las regresiones de galería y telemetría.

Los envíos y precios del escenario de prueba son simulados. No se generan medios con proveedores ni se consumen créditos. Las capturas están en `outputs/studio-gallery-20261008/` y usan modelos/precios de prueba; no se publican esos outputs ni archivos privados.

Este cambio carga JavaScript/CSS sobre el host existente. No requiere empaquetar APK/EXE y no implica validación de nuevas generaciones reales.
