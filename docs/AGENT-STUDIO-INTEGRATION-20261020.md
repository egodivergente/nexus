# Integración de Studio en Nexus Agent · 20-10-2026

Base: `c650f14e39bce30185bf8ba5d48c451b15e29f6b`, publicación Android vc95 con Drive y continuidad entre teléfonos.
Rama de entrega: `codex/agent-studio-integration-20261020`. Paquete conservado: `com.vidadomu.nexus.agent`.
Versión prevista: `1.9.62-agent-preview` / vc96; certificado instalado conservado mediante configuración privada externa.

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

En curso: pruebas JVM del núcleo y Android, compilación arm64, pruebas físicas de laboratorio separadas de los datos habituales.
La instalación final se coordina con las tareas Drive/continuidad para conservar la copia de originales que está en curso.
