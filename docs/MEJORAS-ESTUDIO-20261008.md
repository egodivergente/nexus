# Mejoras de NEXUS Studio — 8 de octubre de 2026

Se mejoró el guardado del espacio de trabajo, la carga de la galería y el feedback de creación sobre la interfaz existente.

## Comportamiento

- El pie del estudio muestra Guardado, Cambios pendientes, Guardando o Sin guardar. Un fallo conserva el borrador y ofrece Reintentar guardado. El detalle del fallo queda en la descripción del indicador.
- Una respuesta de guardado antigua no marca como guardados los cambios posteriores. Se conserva la cola de guardados y el aviso al cerrar con cambios pendientes.
- Ctrl+S (o Cmd+S) guarda el estado actual. Ctrl+Enter (o Cmd+Enter) activa el botón Generar del lienzo cuando está disponible; conserva las confirmaciones existentes, no actúa en el compositor del asistente ni genera mientras hay un diálogo abierto.
- La guía de tres pasos aparece en el lienzo para un espacio sin creaciones ni prompt. Se puede cerrar de forma persistente y volver a abrir con Guía en el pie. Sus acciones eligen un modelo, enfocan el prompt o abren los resultados; no envían generaciones.
- La carga progresiva añade hasta 36 tarjetas a la cuadrícula conservando los nodos existentes. Las actualizaciones de trabajos reemplazan solo las tarjetas cambiadas y mantienen las demás, el orden y el desplazamiento.
- Los errores al abrir un modelo o consultar su coste permanecen en el lienzo y ofrecen cargar de nuevo sus controles, consultar otra vez, elegir otro modelo o abrir Ajustes. Se conserva el prompt. Un presupuesto desconocido mantiene la política anterior de envío y no se presenta como coste cero.
- Los estados de carga del modelo, consulta de coste y envío se anuncian con ARIA. Consultar coste permanece deshabilitado durante la consulta incluso después de redibujar el lienzo. Preparar o enviar deja de mostrarse como un error de validación.
- Un envío fallido ofrece revisar su trabajo en Actividad. Para Flow, Comprobar trabajo Flow llama a la recuperación existente con el mismo identificador. El aviso no incorpora una acción para reenviar la generación.
- Los errores de modelos y cotizaciones antiguos se descartan después de cambiar de modelo. Una adaptación tardía de prompt tampoco sustituye el texto de la nueva selección.

La galería sigue acumulando tarjetas al desplazarse; esta mejora no constituye virtualización con un número fijo de nodos. Se conserva la retirada deliberada del comparador de resultados. Los estilos nuevos usan los tokens existentes, sin cambiar los temas.

## Verificación

- `python -B desktop/tools/test-studio-ux.py`: PASS. Guardados concurrentes con borrador posterior, protección al cerrar, fallo y reintento, Ctrl+S, Ctrl+Enter con diálogo o botón deshabilitado, guía persistente, 1.000 trabajos simulados, conservación de nodos durante paginación y polling y búsqueda posterior. Capturas de 1360, 760 y 480 píxeles sin desbordamiento horizontal ni errores de página.
- `python -B desktop/tools/test-studio-gallery.py`: PASS. Visor, múltiples resultados, favoritos, colecciones, filtros, borrador, recuperación de ocultos, preparación de vídeo, edición y confirmación de encargos a varios modelos y telemetría. No se suministró un clip local: reproducción real de vídeo no probada en esta ejecución.
- `node desktop/tools/test-engine.js`: 20 PASS.
- `node desktop/tools/test-dispatch-integrity.js`: PASS; 14 escenarios de estado de proveedores y conservación del plan confirmado durante un guardado.
- `python -B desktop/tools/test-studio-feedback.py`: PASS. Reintento de modelo conservando el prompt, rechazo de errores de modelo/coste antiguos, adaptación tardía del prompt descartada, consulta pendiente tras redibujar, aviso y reintento de coste, precios desconocidos, bloqueo durante el envío y recuperación del mismo identificador Flow. Capturas a 1360, 760 y 480 píxeles sin errores ni desbordamiento horizontal.
- `python -B desktop/tools/test-order-costs.py`: 3 PASS, incluida la política de precios desconocidos de los proveedores.

Las pruebas de interfaz usan Edge sin ventana, un host aislado y un directorio temporal. Las operaciones de proveedores se bloquean; los envíos de generación de las pruebas son simulados. No se ha comprobado una generación real ni el estado de autenticación o saldo de las cuentas. No se han compilado ni empaquetado APK, EXE o JAR.

La evidencia local está en `outputs/studio-ux-20261008/`, `outputs/studio-feedback-20261008/` y `outputs/studio-gallery-20261008/`; no se publica el estado temporal ni datos de usuario.
