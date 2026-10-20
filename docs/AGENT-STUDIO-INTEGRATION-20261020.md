# Integración de Studio en Nexus Agent · 20-10-2026

Base: `c650f14e39bce30185bf8ba5d48c451b15e29f6b`, publicación Android vc95 con Drive y continuidad entre teléfonos.
Rama de entrega: `codex/agent-studio-integration-20261020`. Paquete conservado: `com.vidadomu.nexus.agent`.
Versión compilada: `1.9.62-agent-preview` / vc96; certificado instalado conservado mediante configuración privada externa.

## Funciones integradas

- Estado global de trabajos activos y resultados recién terminados, con progreso medido cuando existe, apertura del visor y envío del resultado al chat.
- Ajustes rápidos desde las tarjetas: opciones del schema real del modelo, sin proporciones inventadas. Cada cambio pasa por reconstrucción del plan, recotización e invalidación de la confirmación anterior.
- Cortinilla A/B para imágenes, accesible mediante acción de progreso además del gesto. Respeta las reglas existentes para seleccionar copias locales fiables.
- Extracción del fotograma más próximo a la posición del reproductor, guardado como PNG y enviado al Lienzo con su MIME correcto. Si supera 16 megapíxeles, obtiene una copia de como máximo 4096 px de lado para contener la memoria; conserva el vídeo original. Libera el bitmap/retriever y elimina archivos incompletos al fallar o cancelar.
- Recuadros semánticos y paneles del chat: elecciones, comparaciones y listas de pasos. Contestar envía texto al asistente; el panel no ejecuta herramientas ni autoriza pagos por sí mismo. Skill `response_craft` incluida.
- Importación de lotes CSV, TSV, TXT/MD y XLSX desde Lienzo; límites de lote configurables, lectura acotada y protección frente a expansión ZIP y entidades XML. Un envío parcial no reofrece automáticamente el mismo lote completo.
- Flow: fotogramas Inicio/Fin e intercambio; importación de resultados existentes a Biblioteca y selector de resolución. Las operaciones remotas requieren el puente Flow compatible; no se ejecutan generaciones de pago como prueba de esta entrega.
- Historial: actualización de la lista en memoria después de transacciones, evitando consultar todo el historial en cada progreso. Se mantiene la exclusión mutua de las importaciones de continuidad y se reordena si cambia la fecha del trabajo. Esta operación no se presenta como O(1).
- Whisper: un contexto reutilizable, con mutex, comprobación de modelo y liberación tras 60 segundos sin uso o presión de memoria. Hilos limitados a 1–4 según hardware; no se atribuye una aceleración sin medirla.
- Límite de documentos del usuario, cierre por timeout del servicio del asistente, notificaciones en español y tipografía de al menos 12 sp. La recuperación en un chat nuevo conserva proyecto y reenvía el turno fallido con sus adjuntos.
- Continuidad Windows: se incorporan exclusivamente `DeviceSync.kt` y `DeviceSyncSettings.kt` publicados por la tarea correspondiente en `3f9f0170a727483a2eded5533766acc9bdd307ed`, con JSON estricto de `239df6f` y `ad5a6ec`.

- Cierre Drive `16bbcde` (incluye `edf8d7c` y `9fda60`): equivalencia solo por SHA demostrado; rutas locales desconocidas conservan conflictos. Oculta copias de la misma URL en el mismo proyecto conservando todos los IDs y el historial. La solicitud manual tiene cola independiente de la espera automática y no cancela transferencias activas.

## Procedencia y decisiones

Ideas/código de `03f21760bf10616c964a6999435f7d69f7e497f6` (Studio), `f4f3ef8c386ac14f56c4ca2239b1cb65c5017530` (respuestas enriquecidas) y rama Flow hasta `704eab0be644fe0c936b8f042d4b9ab8d233d223`. Se integran selectivamente sobre la base actual del Agent.
No se cambia el identificador a `.studio`, no se copia la importación de credenciales de aquel build ni su carga editorial asíncrona que puede dejar guías cacheadas antes de estar preparadas.

## Validación

- Compilación arm64 y pruebas JVM correctas: 700 pruebas superadas (298 núcleo + 402 app), 4 omitidas y ningún fallo.
- Diez casos físicos superados en Pixel 8 Pro con el paquete aislado `com.vidadomu.nexus.mediaqa`: PNG real y cancelación, fuente inválida, A/B visual y accesible, XLSX/DTD, resultado adjuntado desde la píldora, captura al Lienzo, tres transcripciones Whisper con reutilización/liberación, historial SQLite, navegación y persistencia. Se verificaron mediante ejecuciones dirigidas; no se afirma que las diez pasaran en una única ejecución.
- La prueba de captura detectó un Toast fuera del hilo principal. Corregido iniciando la acción en `Dispatchers.Main.immediate`; la extracción mantiene su trabajo pesado en IO. La repetición física de la captura pasa.
- El test de navegación usaba texto excluido de la semántica accesible. Actualizado para accionar las pestañas por su descripción real; comprobación física correcta.
- Whisper nativo del APK final tiene el mismo SHA-256 que el binario físico probado: `2285788d57e27a072ff15a0b4af66131545aaf0d51627653910941b81d89742d`.
- APK Agent vc96: SHA-256 `09cb6cb744f57ffcb399b1bac766a584eb3f393aeae77bc8c7ec64cc96bd528b`; certificado SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. Manifiesto sin `testOnly`; instalable como actualización del paquete existente.
- No se han ejecutado generaciones de pago. Flow remoto y los proveedores requieren sus puentes/configuraciones; las pruebas locales no acreditan cada proveedor.
- Instalación de producción coordinada con Drive/continuidad: pendiente de completar y registrar. Los metadatos privados y registros se guardan en `outputs/studio-integration/`, fuera de Git.
