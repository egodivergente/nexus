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


## Segunda entrega: 1.8.7 (versionCode 31)

- Catálogo activo (CatalogTreeScreen): búsqueda y filtros en una píldora; filtros tonales con háptica; logos de 40 dp; títulos de una línea, tamaño adaptado al ancho y elipsis; precio en chip para la cuadrícula. También se limita el título de las variantes.
- Agente: llamadas de herramientas plegadas por mensaje; avisos de error nuevos en Snackbar con detalle persistente plegable; reintento del turno fallido con sus referencias originales. Los adjuntos del borrador actual se conservan y cambiar de conversación/proyecto invalida el reintento anterior.
- Conexiones: campos AWS, Atlas y tokens ocultos inicialmente, con alternancia de visibilidad; selectores segmentados para backend y familia LLM; saldos destacados sin segunda caja interior.
- Biblioteca: filtros y búsqueda tonales, miniaturas cuadradas con crop, transición de carga de 180 ms y respuesta de presión; conserva navegación, proyectos y referencias.

El APK se compila directamente en ASUS con un trabajador. No se ejecutan pruebas funcionales, auditorías visuales ni benchmarks por petición del usuario. Las transiciones añadidas no constituyen una medición de 120 Hz. Se conserva el catálogo y los contratos de proveedores.
