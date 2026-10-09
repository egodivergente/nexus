# Fotografías guardadas por proyecto

Los selectores de imágenes del asistente, las tarjetas de referencias, el proyecto y el lienzo ofrecen una cuadrícula de archivos ya importados al proyecto activo, búsqueda por nombre y selección múltiple. Se mantiene la opción de importar desde el dispositivo. Los campos de una sola imagen permiten seleccionar una fotografía.

Al importar con un proyecto activo se copia el archivo a `files/agent_media` y se registra en el índice existente `files/assistant/index.json`. Las referencias importadas previamente también aparecen. Reutilizar una fotografía guardada conserva su archivo, identificador y MIME; no crea una nueva copia. Quitar un adjunto del mensaje no borra la fotografía del proyecto. Sin proyecto activo, se conserva la importación habitual sin asignarla a un proyecto.

La lista se limita al proyecto activo y a los tipos admitidos por el campo. Los resultados generados siguen en Biblioteca. No se modifican generación, cobro, colas ni la base de datos de trabajos. Los archivos permanecen locales; importar no envía una generación ni una petición al LLM.

La solicitud del selector se conserva si Android recrea la pantalla. El proyecto de destino se fija al abrirlo; un cambio de proyecto durante la importación no reasigna esas fotografías al nuevo proyecto.

## Validación

- `ProjectMediaLibraryTest`: aislamiento por proyecto, MIME, búsqueda, orden y deduplicación de URI.
- `ProjectMediaPickerTest`: copia privada que sobrevive al borrado del original sintético, reutilización sin otra copia, lectura del índice desde disco y selección de una foto guardada.

Comprobación del 09-10-2026: compilación en ASUSVIDAL con un worker y prioridad baja, `BUILD SUCCESSFUL`; 263 tests de la app, cero fallos, errores u omisiones. Los APK AgentPreview, OfflineQa y de instrumentación contienen el selector nuevo. En el Pixel 8 Pro, la prueba `importedPhotoSurvivesOriginalRemovalAndReusingDoesNotCopyAgain` terminó con `OK (1 test)` en el paquete aislado `com.vidadomu.nexus.uiqa`. La prueba visual de selección no se ejecutó porque el Pixel seguía bloqueado. No se instaló AgentPreview en el S25.

Este cambio se publica aparte de los retoques de fase 9 que ya estaban sin commit en el worktree. Esos retoques se conservan. La APK de comprobación se construye con el estado completo del worktree; el manifiesto de fuentes y los resultados quedan fuera de Git, en los outputs del encargo.
