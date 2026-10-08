# Archivos de referencia de proyecto

## Uso
En el Asistente o dentro de una colección, abre **Archivos de referencia**. Selecciona el proyecto e importa fotos, vídeo o audio. Se copian al almacenamiento privado local; el original se conserva. El nombre puede cambiarse directamente en el campo **Nombre de referencia** y se guarda al salir del campo.

Ejemplo: importa una foto y nómbrala `Mateo face reference`. Con ese proyecto activo, pide «Genera a Mateo caminando por Madrid usando su face reference». El asistente recibe el inventario de referencias y puede utilizar el asset_id en los medios del pedido. Las imágenes cuyos nombres coinciden con el sujeto mencionado se cargan también como miniaturas visuales, hasta seis por turno. La compatibilidad de entrada depende del modelo elegido.

Las referencias persisten en el índice del proyecto, incluso al comenzar una conversación nueva. Las referencias automáticas se seleccionan únicamente del proyecto activo. Las colecciones existentes se vinculan a un proyecto del índice por su nombre cuando se abre su gestor de archivos.

## Validación
- PASS: importación desde el gestor, renombrado lógico, persistencia en index.json y búsqueda mediante search_assets del motor Kotlin real.
- PASS: referencia cargada visualmente sin adjunto manual y enviada en reference_images de un pedido Flow interceptado.
- PASS: otra conversación conserva la referencia; otro proyecto no la adjunta automáticamente.
- PASS: regresión del chat con 20 resultados, scroll, actualización de resultados y ausencia de tarjetas Flow.
- PASS: seis pruebas del host (credenciales, sesión, configuración).
- Compilación: ASUSVIDAL mediante ecosystem-build, trabajo 20261008T042701Z-5818320b, runtime recuperado y SHA-256 verificado.
- Cero llamadas de generación de pago en las pruebas; selección de archivos simulada con una imagen de ejemplo en datos aislados.

La instancia local se actualizó en el puerto existente; se conservó la conversación y su borrador.
