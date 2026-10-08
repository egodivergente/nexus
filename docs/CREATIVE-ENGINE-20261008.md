# NEXUS Creative Engine

Aplicado al escritorio el 2026-10-08. El lienzo reúne prompt, motor, parámetros rápidos del esquema y referencias en un panel central. Formato, resolución y duración solo aparecen cuando los admite el modelo. Semilla y demás parámetros permanecen en «Parámetros y herramientas».

El dock mide 64 px y se expande para mostrar navegación y colecciones. Saldos, telemetría, actividad, último encargo y control de Flow se acceden desde la barra inferior. «Motores configurados» describe configuración, no disponibilidad verificada; no se inventan saldos ni estados de salud.

La acción dividida ofrece generación individual y Multi-Run mediante el editor existente: prompts y ajustes por modelo, hasta tres envíos simultáneos y la cola compartida de Flow. Enter activa el botón existente únicamente si está habilitado; Shift+Enter añade una línea. Se conservan validación, cotización y bloqueo por coste pendiente. La composición IME no envía.

La tira muestra hasta doce trabajos reales de imagen/vídeo, respeta los resultados ocultos y se actualiza al recibir estados sin reemplazar el prompt. Arrastrar una imagen completada al panel la añade como referencia a una entrada compatible; con varias entradas debe soltarse sobre la entrada elegida. No modifica una semilla ni promete interpolación. Los vídeos usan `preload=none`, sin reproducción automática. Movimiento breve con soporte de movimiento reducido.

## Validación

- `desktop/tools/test-creative-engine.py`: controles del esquema, persistencia del borrador, teclado, referencia interna, límite de referencias, coste invalidado, Multi-Run, telemetría y cuatro tamaños (1360, 760, 480 y 1280 px).
- `desktop/tools/test-studio-gallery.py`: regresión de biblioteca, visor, colecciones, encargos por modelo, telemetría y persistencia del último encargo usando los nuevos accesos.
- `desktop/tools/test-flow-control-ui.py`: límites, pausa, cancelación y atribución de modelo desde la barra inferior.
- Pruebas con host y datos temporales aislados. Ninguna generación ni subida de pago. Las capturas emplean datos de prueba y no acreditan disponibilidad de proveedores.

Implementación en `desktop/ui/creative-engine.js` y `creative-engine.css`, cargada por `app.js`. No se recompila ni empaqueta EXE/APK para estos cambios de interfaz; la ventana existente necesita recargar la interfaz para leer los nuevos archivos. Superdesign conserva una vista estática del estado inicial; las capturas de las pruebas verifican la implementación.
