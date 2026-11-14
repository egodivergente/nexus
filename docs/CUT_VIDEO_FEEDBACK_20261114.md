# Cut: anotaciones del vídeo del 14/11/2026

Base comprobada: APK vc214 del S25, marcador de fuente `c0961dd1`, rama
`aki/story-sync-hydrogen-20261114`. La rama de este encargo es
`codex/cut-video-gestures-20261114`. No se usa el checkout antiguo vc175.

El vídeo original y su audio se inspeccionan localmente. La transcripción se
realizó con el Whisper ya instalado; vídeo, audio y transcripción quedan fuera
de Git. Anotaciones y decisiones:

| Observación | Corrección |
|---|---|
| 0–18 s: desplazarse durante la reproducción | Scrub recuerda si estaba reproduciendo y reanuda al soltar. Durante reproducción, arrastrar la pista no mueve el clip seleccionado. |
| 18–28 s: vídeo pequeño, ampliar con dos dedos | Pinch y desplazamiento de la previsualización, doble toque/Ajustar para volver. TextureView admite la transformación de la vista; no cambia el encuadre exportado. |
| 28–40 s: paneles como cortinas, tamaños regulables | Separadores arrastrables entre previsualización/línea de tiempo y agente, con acciones de accesibilidad y tamaños acotados al espacio disponible. |
| 40–48 s: esconder agente y dejar un círculo | Minimizar elimina el panel entero y conserva borrador/conversación; el círculo de herramientas lo recupera. |
| Mensaje posterior: copiar varios mensajes o chat entero | Selector común con Todo el chat, selección múltiple y copia íntegra en orden, disponible en Cut y en las acciones del chat principal. |
| Mensaje posterior: cinco clips separados con audio | render.export admite each_clip o clip_ids, procesa ventanas inmutables en cola y devuelve todos los asset_ids. No borra clips ni utiliza ripple/undo. La UI permite montaje/cada clip/selección. |

La exportación por segmento conserva lo que se ve y oye en su intervalo: capas,
audio, velocidades y efectos. Se evalúan en el tiempo original y la salida empieza
en cero. No se cambia la revisión ni el historial del montaje. Cancelar detiene
lo pendiente y conserva los archivos completos ya guardados. Las opciones de
salida se pasan como overrides; abrir/exportar no edita los ajustes del proyecto.

Se añaden pruebas de ventanas con extremos y velocidad, cinco segmentos y audio
vinculado, selección/deduplicación, copia íntegra, distribución del espacio,
zoom, intención de reproducción y cola/cancelación. La instrumentación usa dos
renders de prueba privados, con audio, sin entradas en Biblioteca, sin llamadas
a modelos y sin editar montajes. El recorrido de vista recibe el id del montaje
explícitamente y compara el proyecto antes/después.

Estado de esta revisión: implementación lista para compilar/verificar. Los
resultados de AWS, firma, instalación y dispositivo se añadirán tras comprobarlos.
