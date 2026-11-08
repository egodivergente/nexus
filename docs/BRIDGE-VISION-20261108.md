# El agente no veía las imágenes con Codex/AGY — 08/11/2026

Rama `fix/bridge-images-20261108`, encima de `fix/codex-e2big-20261107` (tocan el mismo fichero).

## Síntoma

Con el cerebro Codex (p. ej. «Sol»), al pedirle que mire imágenes respondía: «Aquí solo recibo las dimensiones y los avisos de imagen adjunta, sin píxeles visibles».

## Causa confirmada

La app cargaba la imagen (miniatura JPEG ≤1024 px) y la entregaba al proveedor del puente, pero `AntigravityProvider.renderMessage` la convertía en el texto `[Imagen adjunta (jpeg, N bytes)]`. **Nunca se subía al puente.** Además, el registro `VisualEvidence` le decía al modelo que había imágenes en la petición, lo que era falso para este cerebro.

El puente ya tiene una vía para adjuntos: `PUT /v1/uploads/<jobId>/<nombre>` y después `attachments: [{name, uploadedPath, mimeType}]` en `/v1/chat`. Prueba en el puente real del S25 (vía relé ASUS), con franjas de colores impredecibles:

| Petición | Respuesta del modelo |
|---|---|
| imagen naranja · morado · blanco · turquesa, subida como adjunto | «naranja, morado, blanco y turquesa» |
| la misma pregunta sin imagen | «NO VEO IMAGEN.» |
| imagen turquesa · blanco · naranja · morado | «turquesa, blanco, naranja y morado» |

El puente desplegado y Codex leen píxeles en cuanto la imagen se sube. **No hay que cambiar el puente.** La copia del repo `axel-native` no añade `-i` a `codex exec`; la desplegada sí hace que Codex vea la imagen.

## Corrección (solo `agent-core`)

- `AntigravityProvider` sube cada imagen distinta de la petición (sin repetir la misma) con `PUT /v1/uploads/<jobId>/imagen-N.<ext>` y la envía en `attachments` con la ruta que devuelve el puente. En el texto queda `[Imagen adjunta: imagen-N.jpg]`, con el mismo nombre y orden que el adjunto.
- Si una subida falla, el turno se detiene con el motivo («sin la imagen no puede verla, así que no sigo a ciegas»). No se responde sin la imagen ni se quita en silencio.
- Un turno solo de texto no cambia: no hay subidas ni campo `attachments`.
- Las imágenes que llegan son las mismas que ya recibían los demás cerebros: las de los dos últimos mensajes del usuario (política existente del orquestador). Se vuelven a subir en cada ronda de herramientas, porque el puente asocia la subida al `jobId` de cada llamada.

## Carrusel de imágenes del chat

Las fotos se dibujaban en una caja fija de 280×220 dp con `Fit`. Una vertical quedaba pequeña con franjas vacías a los lados, que parecían separación. Ahora la caja toma la proporción real de la imagen: la del adjunto o, si falta, la de la propia imagen al cargar. Llega hasta 360 dp de alto y 320 dp de ancho. Por ejemplo, una 4:5 sale a 288×360 y una 9:16 a 202×360. El hueco entre imágenes baja de 8 a 4 dp.

## Pruebas

- Puente real: tabla de arriba (contrato de subida y visión de Codex).
- Banco con el `server.ts` real: el proveedor Kotlin sube la imagen y el puente la guarda con la **misma huella SHA-256**. El adjunto supera su validación de ruta por `jobId` y entra en el turno.
- `agent-core`: suite completa 430 pruebas, 0 fallos. Las nuevas cubren: bytes exactos subidos, adjuntos y nombres, una imagen repetida que se sube una sola vez, fallo de subida que detiene el turno, y texto sin cambios.
- `imagePreviewSize`: 3/3, ejecutado en la JVM con `ui-unit` de Compose.

**Sin verificar:** la compilación de la app Android (`AssistantImagePreview.kt` y `AssistantScreen.kt`; aquí no hay SDK), un APK nuevo, la prueba en el teléfono y si AGY (Gemini) aprovecha los adjuntos. Para AGY, el puente solo le pasa las rutas como texto.
