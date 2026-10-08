# Telemetría básica de NEXUS Studio

El botón «Telemetría» de la cabecera abre el panel. Las mediciones viven en memoria durante la sesión; al recargar se reinician. El registro conserva un máximo de 200 operaciones, con contadores acumulados desde que se cargó el componente o se vació el historial.

- Sonda del servicio local: GET a `/health` en loopback, con un máximo de cinco segundos. En el host nativo usa la operación existente `init`. Se ejecuta al abrir el panel, al pulsar «Medir ahora» y cada diez segundos mientras el panel está abierto, visible y la medición activa.
- RPC: tiempo desde que la interfaz envía una operación hasta recibir su respuesta o fallo. Incluye trabajo del host y del proveedor; no representa la latencia pura de red ni el tiempo de generación hasta el resultado final.
- Por proveedor: media de respuestas correctas y número de correctas/errores en las últimas 200 operaciones. Las consultas de estado, subidas y envíos se distinguen en la actividad reciente.
- Contadores: operaciones RPC en vuelo, trabajos activos según sus estados existentes y errores registrados.
- Recursos: memoria JavaScript aproximada si Chromium expone el dato y duración de la sesión. No es la RAM total ni una medida de CPU del proceso o equipo.
- Control: pausar/reanudar medición, vaciar historial y exportar JSON. Estas acciones no pausan ni cancelan los encargos.

Cada entrada guarda solo operación de una lista conocida, proveedor conocido, duración, hora, resultado y código genérico de error. No copia argumentos, texto de errores, prompts, claves, rutas, URLs ni respuestas. Exportar descarga esos mismos metadatos localmente; no envía analítica externa.

`node desktop/tools/test-studio-telemetry.js` comprueba límites, limpieza de datos, pausa, vaciado y la función RPC real en casos de éxito, rechazo, timeout y fallo del puente. Comprueba también que se conservan los valores y errores devueltos al solicitante.

`desktop/tools/test-studio-gallery.py` comprueba el panel con el host local aislado: sonda real, error controlado, pausa, exportación, vaciado, cierre y disposición a 1360/480 px. Los envíos a modelos se simulan; no se consumen créditos. No se requiere compilar ni empaquetar el escritorio para cargar el componente.
