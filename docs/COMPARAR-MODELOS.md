# Crear lotes y comparar modelos de imagen

En **Lienzo → Crear lote o comparar**, Nexus permite elegir de 1 a 6 modelos del catálogo visible y de 1 a 20 generaciones por modelo (hasta 120 solicitudes). Cada modelo seleccionado tiene controles − / + para su cantidad. Por ejemplo: cuatro generaciones de Seedream, seis de GPT y dos de Flux forman un lote de doce solicitudes. También admite lotes con un único modelo. El prompt y las referencias proceden de una copia del lienzo tomada al abrir la comparativa. La selección y las cantidades se recuerdan al preparar el lote.

El selector abre **Con referencias** por defecto. Muestra una selección corta, con una versión reciente de Seedream, GPT Image, FLUX, Midjourney y Nano Banana cuando el proveedor configurado dispone de ellas. Los demás modelos y versiones siguen en **Otros modelos y variantes** y en la búsqueda. Los favoritos y el orden personal del catálogo se conservan.

La compatibilidad se comprueba antes de seleccionar: las rutas exclusivas de texto no aparecen junto a tus referencias y el límite de cada variante se muestra en su fila. Los modelos que necesitan menos o más imágenes indican el motivo y no entran en el lote. Si no hay imágenes en el lienzo, puedes cambiar expresamente a **Desde texto**. La ruta exacta se mantiene; no se reemplaza silenciosamente una generación desde texto por una edición.

El formato se presenta como **16:9 · Horizontal**, **9:16 · Vertical**, **1:1 · Cuadrado**, etc. Los píxeles quedan en segundo plano. Las equivalencias aproximadas se marcan con ≈. El valor enviado sigue siendo el original del proveedor, incluidos `1280:720`, `2048*2048` o `2048x2048`. Cuando el modelo destino admite el mismo tamaño, la comparación lo conserva antes de buscar otro tamaño de igual proporción.

Los ajustes solo se trasladan a claves compatibles con el contrato de cada modelo. Las relaciones de aspecto equivalentes se traducen a su valor nativo (por ejemplo, 16:9 a 1920:1080); las opciones no admitidas se indican antes del envío. Las referencias nunca se eliminan silenciosamente: un exceso de archivos, una entrada de máscara o un formato de entrada distinto bloquea ese modelo. Cuando existe un parámetro de cantidad se fija a una salida; algunos proveedores pueden devolver más de un resultado por petición.

La revisión muestra la cantidad, el coste de cada solicitud, el subtotal por modelo y el total estimado. La cantidad del lote multiplica solicitudes independientes, no el parámetro de salidas de la API. Una semilla fija se conserva y puede dar resultados similares o idénticos. Los créditos Crun se suman aparte del dinero. Los precios desconocidos, las tarifas base que no determinan el coste de la configuración y las configuraciones inválidas impiden enviar. Las estimaciones caducan a los cinco minutos y se pueden actualizar.

Las peticiones se despachan sucesivamente y por turnos: primero una de cada modelo, después la segunda de los que tengan más, hasta completar las cantidades. Los trabajos que el proveedor acepta pueden generarse en paralelo. **Detener pendientes** impide los siguientes envíos, sin cancelar los trabajos aceptados. Un fallo detiene las peticiones restantes. No hay reintentos automáticos de generación ni reenvíos de un lote ya iniciado. Cada modelo muestra cuántas solicitudes se han enviado de las previstas.

En **Historial → Comparativas** se abre la cuadrícula de resultados del mismo prompt, con nombre y proveedor, número de generación dentro del modelo, estado individual y acceso a cada resultado. Se conservan las tareas ordinarias del historial y la biblioteca.

## Persistencia y recuperación

La agrupación utiliza metadatos locales en los registros existentes, sin migrar ni sustituir la base de datos. Los metadatos de la comparativa se añaden en el repositorio y no entran en el payload enviado al proveedor.

Tras un cierre, las peticiones aceptadas recuperan su seguimiento. Una petición cuyo envío no se puede confirmar se marca para revisión; nunca se reenvía automáticamente. Los modelos que todavía no se habían enviado requieren una nueva selección consciente. La pantalla indica cuántas peticiones del lote quedaron registradas.

## Verificación sin gasto

- `ComparisonPlanTest`: adaptación, claves exactas de Atlas y Runway, conservación de referencias, máscaras y vídeo incompatibles, cantidad de salidas, totales con monedas distintas, precios ausentes, duplicados, cola sin reintentos y recuperación tras interrupción.
- `ComparisonOfflineTest`: prepara cuatro contratos reales en la variante aislada Nexus Lab, comprueba que el controlador y el distribuidor bloquean el envío antes de subir archivos, y verifica que el historial no cambia.
- Nexus Lab carece del permiso INTERNET y mantiene las barreras de envío del repositorio y del distribuidor. Las tarifas de esta prueba son explícitamente simuladas.

## Límites de esta versión

La comparación es de imágenes. No añade modelos que el catálogo y los proveedores configurados no ofrezcan. Los modelos con referencias que no puedan cotizarse sin subir archivos permanecen bloqueados. La configuración compartida no implica idéntica calidad, resolución o comportamiento entre proveedores. Las generaciones reales quedan pendientes de una ejecución expresa del usuario; las pruebas de desarrollo no las realizan.

Archivos principales: `ComparisonPlan.kt`, `ComparisonController.kt`, `ComparisonScreen.kt`, `GenerationDispatcher.kt`, `ComparisonRecovery.kt`, `CreateScreen.kt`, `CreateViewModel.kt`, `GenerationsScreen.kt` y `Repositories.kt`.
