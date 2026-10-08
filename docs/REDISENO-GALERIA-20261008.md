# NEXUS Studio: galería y visor

Implementación del diseño aprobado el 8 de octubre de 2026. La extensión se carga desde `app.js` al terminar de registrar los componentes del escritorio. No cambia el envío a proveedores ni necesita recompilar el ejecutable.

## Cambios

- Galería con recorte uniforme 4:3, imágenes con carga diferida, información y selección al pasar el ratón o enfocar con teclado. El visor conserva la proporción original.
- Previsualización de vídeo muda al pasar el ratón, sin controles nativos en la rejilla. Respeta la preferencia de movimiento reducido. El visor incluye reproducción, posición, sonido y pantalla completa.
- Visor modal con navegación entre trabajos y entre las salidas de un trabajo, prompt, parámetros y semilla cuando existen, y coste registrado como estimación. Acciones de descarga, favorito, reutilización, referencia y preparación de vídeo mediante el selector existente. La mejora de Flow usa la acción existente.
- Selección múltiple contextual, descarga y organización. El comparador de resultados se retiró el 8 de octubre por indicación del usuario.
- Colecciones locales persistentes. Ocultar creaciones conserva archivos e historial; Ctrl+K permite recuperarlas.
- Búsqueda, filtros por formato/proveedor/estado, favoritos, orden, tamaño de miniaturas y una vista de filtros guardada.
- Paleta Ctrl+K para navegar, buscar modelos y abrir la actividad. Flechas/J/K, F y Espacio operan en el visor; Escape cierra y devuelve el foco a la galería.
- Saldos compactos y alertas de consulta fallida o menos de 50 créditos de Runway. El estado de Flow deja de mostrar una renovación indefinida tras 45 segundos y permite reconectar ante error.
- Lanzador Edge con `--force-dark-mode`, efectivo en una nueva ventana. El host nativo ya contenía el atributo DWM de barra oscura.
- El host local evita la resolución DNS inversa al reservar su dirección de loopback; no necesita consultar la red para identificar `127.0.0.1`.

## Validación

`desktop/tools/test-studio-gallery.py` inicia el host en modo QA con un directorio temporal. Intercepta operaciones externas y no utiliza claves ni genera con proveedores. Comprueba visor y salidas, favoritos y colecciones tras recarga, selección sin comparador, Ctrl+K, conservación del borrador, búsqueda, vista guardada, ocultación y recuperación y preparación de vídeo. También comprueba selección de modelos de vídeo y el recorrido de confirmación y envío a dos modelos mediante dispatch simulado. La variable opcional `NEXUS_QA_VIDEO` permite comprobar reproducción y controles con un archivo MP4/WebM local, copiado al directorio temporal sin subirlo ni incluirlo en capturas o commits. El informe indica si esa comprobación se ejecutó.

Comprueba ausencia de desbordamiento horizontal y captura galería/visor a 1360, 760, 480 y 1280 píxeles de ancho. Los logos que aparecen en las capturas son datos de prueba, no creaciones del usuario. Informes y capturas quedan en `outputs/studio-gallery-20261008/`, fuera del commit.

`node desktop/tools/test-engine.js` comprueba los contratos existentes de formularios, referencias, payloads y estimaciones sin llamadas de red.

## Alcance y límites

La carga incremental añade grupos de 36 resultados; no es virtualización del DOM. Las colecciones no incorporan fichas independientes de personajes ni proyectos de producción. No se inventan progreso, ETA, semilla o costes cuando el proveedor no los devuelve. Ocultar sustituye al borrado destructivo. Las acciones de generación siguen necesitando la confirmación y los controles de coste existentes.

La validación visual corresponde a Edge sin ventana con datos aislados. La barra de título del equipo real y los flujos de generación externos no se dan por probados. No se compiló ni instaló un APK o EXE para este cambio.

## Encargos a varios modelos

Desde Crear, «Encargar a varios modelos» abre el catálogo. Los botones + añaden modelos, también en vídeo/audio/texto. «Preparar encargo» valida la adaptación del prompt y referencias y consulta el coste de cada modelo antes de ofrecer «Enviar encargo».

`studio-batch.js` sustituye el envío secuencial por hasta tres solicitudes en vuelo. Reparte las cantidades por turnos entre los modelos; Flow conserva una sola solicitud en vuelo por su sesión compartida. Pausar/detener afecta a los pendientes y no cancela solicitudes ya enviadas. Ante un error deja de iniciar solicitudes, espera las ya iniciadas y muestra los confirmados y pendientes sin reenviar. Cada trabajo conserva su modelo y el identificador del encargo.

`node desktop/tools/test-studio-batch.js` comprueba concurrencia, límite, independencia del plan confirmado, exclusión de Flow, pausa/detención y fallo parcial sin reintentos. Estas pruebas no envían peticiones a proveedores; la ejecución de pago queda sin probar.
