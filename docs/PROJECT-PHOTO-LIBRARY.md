# Fotografías guardadas por proyecto

Los selectores de imágenes del asistente, las tarjetas de referencias, el proyecto y el lienzo ofrecen una cuadrícula de archivos ya importados al proyecto activo, búsqueda por nombre y selección múltiple. Se mantiene la opción de importar desde el dispositivo. Los campos de una sola imagen permiten seleccionar una fotografía.

Al importar con un proyecto activo se copia el archivo a `files/agent_media` y se registra en el índice existente `files/assistant/index.json`. Las referencias importadas previamente también aparecen. Reutilizar una fotografía guardada conserva su archivo, identificador y MIME; no crea una nueva copia. Quitar un adjunto del mensaje no borra la fotografía del proyecto. Sin proyecto activo, se conserva la importación habitual sin asignarla a un proyecto.

La lista de importación se limita al proyecto activo y a los tipos admitidos por el campo. No se modifican generación, cobro, colas ni la base de datos de trabajos. Importar no envía una generación ni una petición al LLM.

La solicitud del selector se conserva si Android recrea la pantalla. El proyecto de destino se fija al abrirlo; un cambio de proyecto durante la importación no reasigna esas fotografías al nuevo proyecto.

## Borrado y exportación ZIP

En **Biblioteca → Imágenes del proyecto · Gestionar** se puede elegir el proyecto, buscar y seleccionar imágenes, borrarlas de la biblioteca con confirmación o exportar un ZIP de las seleccionadas o de todas. También se accede desde **Importar → Gestionar biblioteca**. La gestión incluye imágenes importadas y generadas indexadas en el proyecto. La selección para gestionar no hereda el límite de los campos de una sola referencia. «Todas» incluye el proyecto completo aunque haya una búsqueda activa.

Borrar persiste `libraryDeleted` en el índice existente y actualiza la biblioteca y las referencias del proyecto. Conserva archivos e identificadores para mantener conversaciones, planes y trabajos anteriores; no pretende liberar espacio ni borra originales del dispositivo. La reindexación del historial no hace reaparecer las entradas borradas. La operación se restringe al proyecto capturado y se guarda de forma atómica.

Android permite elegir dónde guardar el ZIP. La selección pendiente se conserva mientras está abierto el selector del sistema. El archivo se prepara en caché privada leyendo los originales por bloques, con nombres planos únicos. Solo se copia al destino cuando está completo; los fallos no se presentan como exportaciones parciales correctas y se intenta retirar el documento incompleto. Las imágenes que solo conservan una URL requieren conexión y un enlace vigente. Los temporales se eliminan al finalizar o cancelar.

La gestión desde Biblioteca permite consultar otro proyecto sin cambiar el proyecto activo del asistente.

## Validación

- `ProjectMediaLibraryTest`: aislamiento por proyecto, MIME, búsqueda, orden y deduplicación de URI.
- `ProjectMediaPickerTest`: copia privada que sobrevive al borrado del original sintético, reutilización sin otra copia, lectura del índice desde disco y selección de una foto guardada.
- `LibraryDeletionTest`: borrado persistente limitado al proyecto, conservación del original y de la identidad, y ausencia de reaparición al reindexar.
- `ProjectMediaZipTest`: bytes exactos de la selección, nombres seguros sin colisiones, exclusión de borrados y otros proyectos, y fallo explícito ante imágenes vacías o inaccesibles.
- `ProjectLibraryStorageTest`: exportación real mediante `ContentResolver`, selección pendiente tras recrear el controlador, lectura del ZIP y exportación de las imágenes restantes tras borrar y recargar el índice.

Comprobación del 10-10-2026: 106 tests de `agent-core` y 266 tests de la app AgentPreview, sin fallos, errores ni omisiones. Compilación en ASUSVIDAL con un worker y prioridad baja.

Comprobación del 09-10-2026: compilación en ASUSVIDAL con un worker y prioridad baja, `BUILD SUCCESSFUL`; 263 tests de la app, cero fallos, errores u omisiones. Los APK AgentPreview, OfflineQa y de instrumentación contienen el selector nuevo. En el Pixel 8 Pro, la prueba `importedPhotoSurvivesOriginalRemovalAndReusingDoesNotCopyAgain` terminó con `OK (1 test)` en el paquete aislado `com.vidadomu.nexus.uiqa`. La prueba visual de selección no se ejecutó porque el Pixel seguía bloqueado. No se instaló AgentPreview en el S25.

Este cambio se publica aparte de los retoques de fase 9 que ya estaban sin commit en el worktree. Esos retoques se conservan. La APK de comprobación se construye con el estado completo del worktree; el manifiesto de fuentes y los resultados quedan fuera de Git, en los outputs del encargo.
