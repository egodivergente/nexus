# Integración de Studio en Nexus Agent · 20-10-2026

Base: `c650f14e39bce30185bf8ba5d48c451b15e29f6b`, publicación Android vc95 con Drive y continuidad entre teléfonos.
Rama de entrega: `codex/agent-studio-integration-20261020`. Paquete conservado: `com.vidadomu.nexus.agent`.
Versión unificada: `1.9.63-agent-preview` / vc97; certificado instalado conservado mediante configuración privada externa.

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
- Continuidad Windows: motor y ajustes de `DeviceSync`, publicados por la tarea correspondiente en `3f9f0170a727483a2eded5533766acc9bdd307ed`, con JSON estricto de `239df6f` y `ad5a6ec`.

- Cierre Drive `16bbcde` (incluye `edf8d7c` y `9fda60`): equivalencia solo por SHA demostrado; rutas locales desconocidas conservan conflictos. Oculta copias de la misma URL en el mismo proyecto conservando todos los IDs y el historial. La solicitud manual tiene cola independiente de la espera automática y no cancela transferencias activas.

- Turnos reales en segundo plano y evidencia visual: fusión completa de `cb0871f822630543537ebe80cfa151941c8f00a5`. Sesión del asistente en Application; servicio con bloqueo parcial de CPU, cancelación y timeout, sin reenvío automático. Registro de píxeles realmente presentes y marcas de muestreo de vídeos. Conserva las mejoras ya instaladas en la variante background vc96.

## Procedencia y decisiones

Ideas/código de `03f21760bf10616c964a6999435f7d69f7e497f6` (Studio), `f4f3ef8c386ac14f56c4ca2239b1cb65c5017530` (respuestas enriquecidas) y rama Flow hasta `704eab0be644fe0c936b8f042d4b9ab8d233d223`. Se integran selectivamente sobre la base actual del Agent.
No se cambia el identificador a `.studio`, no se copia la importación de credenciales de aquel build ni su carga editorial asíncrona que puede dejar guías cacheadas antes de estar preparadas.

## Validación

- Compilación arm64 y pruebas JVM correctas: 707 pruebas superadas (305 núcleo + 402 app), 4 omitidas y ningún fallo.
- Validación física previa a la reconciliación background: diez casos superados en Pixel 8 Pro con el paquete aislado `com.vidadomu.nexus.mediaqa`: PNG real y cancelación, fuente inválida, A/B visual y accesible, XLSX/DTD, resultado adjuntado desde la píldora, captura al Lienzo, tres transcripciones Whisper con reutilización/liberación, historial SQLite, navegación y persistencia. Se verificaron mediante ejecuciones dirigidas; no se afirma que las diez pasaran en una única ejecución.
- La prueba de captura detectó un Toast fuera del hilo principal. Corregido iniciando la acción en `Dispatchers.Main.immediate`; la extracción mantiene su trabajo pesado en IO. La repetición física de la captura pasa.
- El test de navegación usaba texto excluido de la semántica accesible. Actualizado para accionar las pestañas por su descripción real; comprobación física correcta.
- Whisper nativo del APK final tiene el mismo SHA-256 que el binario físico probado: `2285788d57e27a072ff15a0b4af66131545aaf0d51627653910941b81d89742d`.
- APK unificada Agent vc97: SHA-256 `49874ff7c95649df809882e21edf0b98418f0b72be980d48bac490df867f8244`; certificado SHA-256 `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. Manifiesto sin `testOnly`; instalable como actualización del paquete existente.
- No se han ejecutado generaciones de pago. Flow remoto y los proveedores requieren sus puentes/configuraciones; las pruebas locales no acreditan cada proveedor.
- Compilación unificada: `:agent-core:test :app:testAgentPreviewUnitTest :app:assembleAgentPreview :app:assembleOfflineQa :app:assembleOfflineQaAndroidTest`, offline, un worker, sin paralelismo y con prioridad baja en AXELPC. Resultado correcto, 19 min 44 s.
- S25 Ultra (SM-S938B): actualización real vc96 → vc97 sin abrir MainActivity, UID conservado, SHA del APK instalado idéntico al entregable. Los 1030 medios, 4 proyectos y 563 generaciones conservan todos sus IDs; índice idéntico inmediatamente después de instalar. Copia privada previa de metadatos realizada; sin borrado de datos. Proveedor DeviceSync despertado y habilitado.
- Pixel: la instalación principal sigue en vc96 background. ADB y mDNS dejaron de estar disponibles al terminar el build; instalación vc97 y repetición física conjunta pendientes de recuperar USB/depuración inalámbrica. No se atribuyen las pruebas anteriores al artefacto vc97.
- Continuidad real S25 vc97 ↔ Windows: la tarea coordinada confirmó POST/callback cifrado por LAN, creación y actualización de una credencial, envío de una skill a S25 y retorno de su edición con SHA idéntico. Se revisaron los informes privados `live-initial.json` y `live-updated.json` (credencial/skill correctas) y `chat-originals-live-proof.json` (20/20 originales verificados, 15 chats enriquecidos). El informe inicial `live-wire-proof.json` precede a esos resultados y no representa el estado final. No se generaliza este resultado al Pixel desconectado.
- Para reactivar DeviceSync mediante `device_sync_configure`, pasar `peer` explícitamente junto con `enabled`: el contrato actual vacía el peer si se omite. El helper de la tarea coordinada ya lo tiene en cuenta; los cambios temporales de ruta se restauran al terminar las pruebas.
- Los metadatos privados y registros se guardan fuera de Git. Las pruebas Drive completas siguen en su tarea correspondiente; estos resultados de continuidad no acreditan la descarga de toda la biblioteca.
