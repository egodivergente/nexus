# Reforma estética de NEXUS Windows — 6 de octubre de 2026

Implementada sobre la interfaz utilizada por el lanzador local de NEXUS. El cambio se centra en la presentación y mantiene los recorridos existentes de configuración, cotización, confirmación y generación.

## Diseño aplicado

- Paleta carbón con superficies diferenciadas: fondo `#14171c`, navegación `#1b1e25`, panel `#20242c`, panel elevado `#272c36`, texto `#f1f3f7` y secundario `#a9b1c0`.
- Tipografía Segoe UI Variable de Windows, ritmo de espaciado común, bordes discretos, radios de 9 a 18 píxeles y controles compactos. Los acentos turquesa, ámbar y violeta y el tema negro profundo siguen disponibles.
- Navegación con selección clara, etiquetas accesibles y barra de iconos cuando la ventana se estrecha. Se conserva el símbolo original de NEXUS y los recursos de marca de los proveedores.
- Bienvenida del asistente con tres puntos de partida. Elegir uno rellena el borrador y devuelve el foco al editor; no envía una conversación, no prepara un pago y no genera automáticamente.
- Compositor con jerarquía más clara, atajos visibles, mensajes con pequeños identificadores y tarjetas de planes coherentes con el resto del estudio.
- Lienzo con bienvenida, selección de modelo diferenciada, controles agrupados y resultado/actividad en un panel lateral cuando hay espacio.
- Biblioteca con tarjetas de medios, filtros agrupados y panel de detalles elevado. Selector de modelos, diálogos y ajustes usan las mismas superficies y controles.
- Barra de cuentas más discreta. Se mantienen los importes y los estados; la hora de consulta sigue accesible en la descripción del elemento, sin repetirla en cada cuenta.

Los nuevos recursos son `desktop/ui/desktop-design.css` y `desktop/ui/desktop-design.js`, cargados al final de `index.html`. No se cambió el backend ni se empaquetó un EXE, JAR o APK. La tecnología de renderizado existente se conserva; esta tarea no la sustituye por un nuevo framework nativo.

## Verificación

**PASS:** 40 combinaciones de presentación: bienvenida del asistente, creación, selector, biblioteca, detalles y ajustes en seis tamaños; lienzo configurado en tres tamaños y conversación en uno. Tamaños utilizados: 1920 × 1080, 1360 × 900, 1024 × 768, 760 × 900, 480 × 850 y 1280 × 600.

**PASS:** sin errores de JavaScript ni desbordamiento horizontal; compositor y acción de generación dentro de la ventana; accesibilidad de la navegación compacta; conservación del borrador al abrir/cerrar el selector; Escape cierra primero su desplegable; tema, acentos y reducción de movimiento preservados. Los accesos de bienvenida solo rellenan el borrador y no invocan el agente.

**PASS:** regresión anterior de Flow con el nuevo diseño cargado: recuperación única del resultado, filtros, paginación, foco, cancelación, errores de opciones y caducidad de la cotización. Se adaptó únicamente el lanzador privado de pruebas para utilizar el Chromium ya instalado porque Edge se cerró al iniciar la prueba y la versión del navegador esperada por el entorno Python no estaba instalada. No se descargó un navegador.

Las pruebas utilizan datos temporales, recursos de imagen de prueba y respuestas simuladas. No consumen inferencia ni generaciones. Las capturas de cuentas y conversación son escenarios de prueba, no una comprobación de saldos, conexión Google o respuestas reales del LLM. Se inspeccionaron visualmente capturas de bienvenida, conversación, selector, ajustes y lienzo configurado.

**NO PROBADO en esta ejecución:** recorrido de las pantallas con el servicio principal abierto. Su puerto guardado rechazó la conexión; el intento de arrancar el servicio fue rechazado por la revisión automática de ejecución con el motivo genérico «blocked by policy». No se intentó eludir ese bloqueo. El rediseño queda incorporado a los archivos que carga el acceso directo, y se dispone de `desktop/tools/verify-desktop-design.py` para realizar la comprobación de solo lectura cuando NEXUS esté abierto.

La evidencia de esta tarea está en `outputs/desktop-design-20261006/`, excluida de Git. Los cambios de otras tareas del workspace, las credenciales y la biblioteca no forman parte del commit de diseño.
