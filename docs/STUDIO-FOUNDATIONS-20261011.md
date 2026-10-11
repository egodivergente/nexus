# Studio 1.8.6: base visual y Lienzo

Primera entrega del rediseño, sobre 1.8.5 (versionCode 29), conservando los flujos de generación, referencias y proyectos. Nueva versión: 30.

- Color.kt centraliza la paleta; Type.kt, la tipografía; Tokens.kt, geometría y tiempos.
- StudioComponents.kt reúne campos tonales, chips, chrome y eliminación de referencias.
- StudioCanvasComponents.kt contiene prompt expansible y CTA de generación con coste.
- El prompt muestra un recuento aproximado (caracteres/4), no el tokenizer del proveedor.
- Cierre visual de 20 dp dentro de un objetivo táctil de 48 dp.
- Desenfoque nativo en la capa decorativa de las barras; no es una captura desenfocada del contenido subyacente. Texto y controles permanecen nítidos.
- Pulso únicamente durante el envío; transiciones de presión de 120 ms y cambios de tamaño de 180 ms.
- Se mantienen los temas guardados y el acceso a encargos a varios modelos.

Alcance pendiente: rediseño específico de catálogo, agente, conexiones y biblioteca. Esta entrega no acredita rendimiento a 120 Hz. Por petición del usuario no se ejecutan auditorías, pruebas funcionales ni benchmarks; se compila el APK para su instalación conservando datos.
