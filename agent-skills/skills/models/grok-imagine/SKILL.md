---
id: 'grok-imagine'
kind: model
title: 'Grok Imagine (xAI)'
version: 2
updated: '2026-10-02'
match: ['^xai/grok-imagine', '^runway/grok_imagine']
modalities: 'VIDEO,IMAGE'
strengths: [native_audio, prompt_adherence, text_rendering, photorealism, fast, identity_preservation, physics]
weaknesses: [long_duration, multi_shot, multi_character]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, text_to_image, image_edit, multi_reference_image, lipsync]
avoid_tasks: [avatar, motion_control, upscale, layer_decomposition]
unverified_tags: []
sources:
  - {url: 'https://x.ai/news/grok-imagine-image-2', title: 'Imagine Image 2.0 (xAI)', accessed: '2026-10-02'}
  - {url: 'https://x.ai/news/grok-imagine-quality-mode', title: 'Grok Imagine Quality Mode API (xAI)', accessed: '2026-10-02'}
  - {url: 'https://x.ai/news/grok-imagine-video-1-5', title: 'Grok Imagine Video 1.5 (xAI)', accessed: '2026-10-02'}
  - {url: 'https://x.ai/news/grok-imagine-video-1-5-references', title: 'Imagine Video 1.5 with References (xAI)', accessed: '2026-10-02'}
  - {url: 'https://x.ai/news/grok-imagine-api', title: 'Grok Imagine API (xAI)', accessed: '2026-10-02'}
  - {url: 'https://x.ai/news/grok-imagine-1-5', title: 'Grok Imagine 1.5 Preview (xAI)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/imagine.md', title: 'Imagine Overview (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/images/generation.md', title: 'Image Generation (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/images/editing.md', title: 'Image Editing (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/images/multi-image-editing.md', title: 'Multi-Image Editing (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/video/generation.md', title: 'Video Generation (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md', title: 'Reference-to-Video (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/video/image-to-video.md', title: 'Image-to-Video (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/video/editing.md', title: 'Video Editing (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/model-capabilities/video/extension.md', title: 'Video Extension (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.x.ai/developers/migration/imagine-image-quality-nov-2.md', title: 'Retiro de grok-imagine-image-quality (xAI docs)', accessed: '2026-10-02'}
  - {url: 'https://docs.dev.runwayml.com/guides/models/', title: 'Available AI Models (Runway Dev docs)', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/xai/grok-imagine-image/text-to-image', title: 'Grok Imagine Image T2I (Atlas Cloud)', accessed: '2026-10-02'}
  - {url: 'https://deevid.ai/blog/grok-imagine-video-review', title: 'Grok Imagine Video Review (DeeVid AI)', accessed: '2026-10-02'}
---

## Resumen

Grok Imagine es la familia de imagen y vídeo de xAI sobre el motor autorregresivo Mixture-of-Experts "Aurora" (no difusión); su rasgo distintivo es generar audio sincronizado (diálogo, SFX, música) en la misma pasada que el vídeo, con latencias bajas [[5]](https://x.ai/news/grok-imagine-api). La imagen actual (2.0) añade edición de precisión (varita, segmentación, quitar fondo, multi-referencia) y tipografía legible [[1]](https://x.ai/news/grok-imagine-image-2). Cubre imagen (`image`, `quality`, `2.0`+developer, `edit`) y vídeo (`v1`/`v1.5` en t2v, i2v, reference, edit, extend +developer). Sigue el prompt casi literalmente frase por frase: funciona mejor como acotación de guion en orden cronológico, no como lista de etiquetas sueltas.

## Cuándo elegirlo y cuándo no

- Elige Video 1.5 si quieres un clip corto con diálogo/SFX/ambiente ya integrados, sin audio aparte [[3]](https://x.ai/news/grok-imagine-video-1-5).
- Mejor Kling o Seedance si necesitas continuidad multi-plano o cámara paramétrica: aquí el tope es 1-15 s. Mejor Wan para volumen alto y coste mínimo sin audio nativo.
- Elige Image 2.0 si mezclas texto legible con ediciones iterativas (varita, segmentación, fondo, hasta 5 refs) [[1]](https://x.ai/news/grok-imagine-image-2). Mejor GPT Image si ya vives en OpenAI; mejor Nano Banana o Seedream para edición multi-turno masiva.
- Evita Grok Imagine para doblaje con la voz real del usuario: solo voces predefinidas; audio propio es "a petición" para partners [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).

| Necesidad | Variante recomendada |
|---|---|
| Vídeo con audio nativo, lo más reciente | `grok-imagine-video-v1.5/{text-to-video,image-to-video}` |
| Mantener cara/producto/voz consistentes | `grok-imagine-video-v1.5/reference-to-video` (hasta 7 refs) |
| Fijar encuadre inicial y final exactos | `v1.5*/reference-to-video` con `last_frame`(+`image`) |
| Storyboard con anclas internas al clip | `v1.5*/reference-to-video` con `keyframes` (`timestamp_s` estricto dentro de la duración) |
| Diálogo entre dos voces predefinidas | `v1.5*/reference-to-video` con `reference_audios` (`<AUDIO_0>`, `<AUDIO_1>`) |
| Editar / alargar un vídeo ya generado | `grok-imagine-video/{edit-video,extend-video}` (solo clásica) |
| Imagen con texto legible | `grok-imagine-image-2.0/text-to-image` |
| Edición con varita/segmentación/fondo | `grok-imagine-image-2.0/edit` o `-developer/edit` |
| Collage de hasta 5 referencias en una imagen | `grok-imagine-image-2.0/edit` (multi-imagen) |
| Generación barata sin pretensión de calidad | `grok-imagine-image/text-to-image` |

## Linaje y variantes en Nexus

**Imagen**: (1) `grok-imagine-image` — clásico, 1K/2K, 14 ratios, el más barato [[18]](https://www.atlascloud.ai/models/xai/grok-imagine-image/text-to-image), sin `quality`. (2) `grok-imagine-image-quality` (Quality Mode, GA may-2026) — **en retirada**: desde 2-nov-2026 redirige a `image-2.0` con `quality=low` [[2]](https://x.ai/news/grok-imagine-quality-mode)[[16]](https://docs.x.ai/developers/migration/imagine-image-quality-nov-2.md); no usar en trabajo nuevo. (3) `grok-imagine-image-2.0` (GA 7-ago-2026, **la más reciente, recomendada por defecto**) — `quality`(`low`/`medium`/`auto`), edición hasta 5 fuentes, varita, segmentación, fondo, ratios `21:9`/`5:2` [[1]](https://x.ai/news/grok-imagine-image-2). `-developer/*` y `runway/grok_imagine_image_2/image` son el mismo modelo bajo otro contrato [[8]](https://docs.x.ai/developers/model-capabilities/images/generation.md)[[17]](https://docs.dev.runwayml.com/guides/models/).

**Vídeo**: (1) `grok-imagine-video` — clásico (28-ene-2026, Hotshot + Aurora); t2v/i2v/reference/edit/extend; tope 480p/720p, rechaza `last_frame`/`keyframes` y combinar `image` con referencias [[5]](https://x.ai/news/grok-imagine-api)[[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md)[[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md). **`edit-video`/`extend-video` solo existen aquí**, no en v1.5 [[14]](https://docs.x.ai/developers/model-capabilities/video/editing.md). (2) `grok-imagine-video-1.5-preview` (jun-2026) — primer i2v a 720p [[6]](https://x.ai/news/grok-imagine-1-5). (3) `grok-imagine-video-1.5` (GA 16-jun-2026, **la más reciente, recomendada por defecto**) — mejor física/audio, duración 1-15 s [[3]](https://x.ai/news/grok-imagine-video-1-5); el 31-jul-2026 añadió t2v nativo, 1080p en t2v/i2v, hasta 3 voces, 7 refs de imagen, `last_frame` y hasta 4 `keyframes` [[4]](https://x.ai/news/grok-imagine-video-1-5-references). Reference-to-video queda en 720p incluso en 1.5. En t2v se genera primero una imagen y luego se anima, en la misma llamada [[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md). `-developer/*` y `runway/grok_imagine_1_5/{t2v,i2v}` son el mismo modelo bajo otro contrato [[17]](https://docs.dev.runwayml.com/guides/models/). Según Artificial Analysis, el i2v de 1.5 lideró su ranking con Elo ~1404 [[19]](https://deevid.ai/blog/grok-imagine-video-review) [sin verificar en fuente oficial de xAI].

## Fortalezas

- **native_audio**: diálogo, SFX y ambiente en la misma pasada, no doblados después [[3]](https://x.ai/news/grok-imagine-video-1-5).
- **prompt_adherence**: Image 2.0 y Video 1.5 siguen instrucciones al detalle en composiciones multi-elemento [[1]](https://x.ai/news/grok-imagine-image-2).
- **text_rendering**: Image 2.0 planifica tipografía/maquetación, con texto pequeño legible [[1]](https://x.ai/news/grok-imagine-image-2).
- **photorealism**: la arquitectura Aurora da buena consistencia facial y texturas realistas.
- **fast**: 17-30 s por generación; Video 1.5 Fast casi dobla la velocidad del modelo anterior [[5]](https://x.ai/news/grok-imagine-api).
- **identity_preservation**: reference-to-video mantiene cara, voz, producto o estilo con hasta 7 refs de imagen y 3 de voz [[4]](https://x.ai/news/grok-imagine-video-1-5-references).
- **physics**: 1.5 mejora física/peso del movimiento frente a la clásica, con menos deformaciones [[3]](https://x.ai/news/grok-imagine-video-1-5).

## Debilidades

- **long_duration**: tope de 15 s por generación; la edición hereda duración original con tope 8.7 s [[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md).
- **multi_shot**: sin modo nativo de varios planos; encadenar exige `extend-video` (clásica) o `keyframes` internos (v1.5).
- **multi_character**: cada referencia se etiqueta `<IMAGE_N>`, funciona bien con 1-3 sujetos; con más, sin garantía documentada.

## Tareas ideales / Tareas que debe evitar

**Ideales**: text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, text_to_image, image_edit, multi_reference_image, lipsync (`<AUDIO_N>`).

**Evitar**: avatar (sin retrato parlante persistente), motion_control (sin motion-brush ni trayectorias, todo en prosa), upscale (solo sube `resolution`/`quality`), layer_decomposition (no separa capas/alfa).

## Estructura de prompt recomendada

Idioma óptimo: **inglés** (todos los ejemplos oficiales lo usan; sin evidencia pública sobre español/chino [sin verificar]). Tono: prosa natural con comas y conectores temporales ("then", "as", "while"), nunca listas de etiquetas sueltas. Longitud: 40-120 palabras en vídeo, 10-30 en imagen simple (hasta ~60 en edición multi-imagen). Sin evidencia de que Nexus acepte `--parámetros` tipo Midjourney [sin verificar]: usa los campos estructurados (`aspect_ratio`, `resolution`, `duration`, `quality`), nunca dentro del texto.

Sintaxis especial verificada:
- `<IMAGE_0>`, `<IMAGE_1>`… para referencias, en orden de envío; si fijas `image` como primer fotograma, arrancan en `<IMAGE_1>` [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).
- `<AUDIO_0..2>` para voces preestablecidas (hasta 3), combinables con `<IMAGE_N>` en la misma frase [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).
- Comillas para texto literal a renderizar en imagen [[2]](https://x.ai/news/grok-imagine-quality-mode), y para diálogo hablado en vídeo.
- Sin tags `[corte]`/shot labels ni timecodes en el texto: los anclajes van en el payload (`timestamp_s` de `keyframes`, `image`/`last_frame`).
- Secuencia narrativa en una sola frase ("slow zoom in... then, the model from <IMAGE_0> walks in...") para encadenar beats en un mismo clip [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).

```
[encuadre/cámara inicial] + [sujeto(s), con <IMAGE_N>/<AUDIO_N> si hay referencias] + [acción cronológica, "then"/"as"] + [entorno y luz] + [movimiento de cámara] + [calidad/textura] + [tono/"texto literal" entre comillas si aplica]
```

## Plantillas por tarea

### Text-to-video

Plantilla: `[tipo de plano/cámara] + [sujeto y su estado] + [acción principal] en [entorno], [condición de luz], [detalle de calidad/textura], [tono emocional]`

Ejemplo cinematográfico: *"Close tracking shot as Mateo, late twenties in a worn leather jacket, sprints down a rain-slicked alley chasing his younger brother Vidal, who glances back laughing over his shoulder. Neon signs blur past in the wet reflections, streetlights flickering. The camera stays low and shaky, keeping pace with their footsteps. Gritty, high-contrast night photography, tense but playful energy."* (`duration=10`, `aspect_ratio=16:9`, `resolution="1080p"`).

Ejemplo distinto (producto/acción): *"A glowing crystal-powered rocket launching from the red dunes of Mars, ancient alien ruins lighting up in the background as it soars into a sky full of unfamiliar constellations"* [[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md) (`duration=10`, `aspect_ratio=16:9`, `resolution="720p"`).

Diferencias por variante: solo v1.5 soporta t2v nativo en 1080p y añade `reference_images`/`last_frame`/`keyframes`; la clásica se queda en 480p/720p sin esos extras [[4]](https://x.ai/news/grok-imagine-video-1-5-references)[[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md).

No pongas: parámetros (`--ar`, `--q`) en el texto; cortes "[CUT TO]" o numeración de planos (no hay multi-shot); más de un giro de trama en 15 s; listas de adjetivos sin verbo de acción.

### Image-to-video

Plantilla: `[movimiento de cámara] + as [elemento ya presente en la imagen] [acción/verbo]` — nunca redescribas apariencia, ropa, rostro o encuadre: ya están fijados por la imagen.

Ejemplo cinematográfico (sobre una imagen de Mateo y Vidal sentados a una mesa): *"The camera slowly pushes in as Mateo turns his head toward Vidal and starts speaking, his hand tapping the table twice for emphasis, afternoon light shifting slightly as clouds pass outside the window."*

Ejemplo distinto (producto): *"Make the water crash down and slowly pan out the camera"* [[7]](https://docs.x.ai/developers/model-capabilities/imagine.md).

Diferencias por variante: solo v1.5 soporta 1080p nativo en i2v; añadir `last_frame`/`keyframes`/`reference_images` la convierte en reference-to-video con esa imagen fijada [[13]](https://docs.x.ai/developers/model-capabilities/video/image-to-video.md).

No pongas: descripciones de ropa/cara/fondo ya presentes en la imagen; movimientos de cámara contradictorios en la misma frase; prompt vacío si quieres control fino (sin texto, el modelo improvisa).

### Reference-to-video

Plantilla: `[plano de apertura] + then the [rol] from <IMAGE_0> [acción] wearing/holding [elemento de <IMAGE_1>] [+ hablando con la voz de <AUDIO_0> si hay diálogo] + [gesto/mirada] + [cámara de cierre]`

Ejemplo cinematográfico (Mateo y Vidal, dos referencias de imagen + dos voces): *"Static wide shot of a quiet kitchen at dusk. The man from <IMAGE_0> (Mateo) sits at the table and says, speaking with the voice from <AUDIO_0>, 'You should've called me.' The younger man from <IMAGE_1> (Vidal) leans against the counter, replying with the voice from <AUDIO_1>, 'I tried, the signal was gone up there.' Camera holds steady, warm practical lighting, melancholic tone."*

Ejemplo distinto (moda/producto, patrón oficial): *"slow zoom in on the white fashion runway stage. then, the model from <IMAGE_0> walks in from the back of the shot from the white opening, and gracefully walk out onto the front of the white stage platform. they wear the shirt from <IMAGE_1> and black flared jeans. they look dramatically at the camera. high quality slow motion shot. fun, playful. skin pores. highly detailed faces. perfect shot. they reach the end of the runway and look at the camera as the camera slowly zooms. subtle smile."* [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md) (`duration=10`, `aspect_ratio=16:9`, `resolution="720p"`).

Diferencias por variante: solo v1.5 admite reference-to-video, la clásica la rechaza. Máx. 7 refs imagen / 3 de voz; si fijas `image`, las refs arrancan en `<IMAGE_1>` [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md). Queda en 720p aun en 1.5 (1080p solo en t2v/i2v puros) [[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md).

No pongas: más de 7 refs de imagen o 3 de voz; descripción completa del aspecto de cada referencia (basta el rol: "the person from <IMAGE_0>"); `voice_id` inventado (usa el roster real, p. ej. `eve`, `leo`).

### Start/end frame (first & last frame)

Plantilla: `[transición de cámara entre dos puntos] + settling on [descripción del fotograma final]` — el prompt es opcional si solo quieres interpolación pura entre `image` y `last_frame`.

Ejemplo cinematográfico: *"The camera dollies from the sunlit doorway where Mateo is standing, across the room, settling on Vidal's face as he looks up from the letter, his expression shifting from confusion to relief."* (con `image`=plano de Mateo en la puerta, `last_frame`=plano de Vidal con la carta, `duration=8`, `aspect_ratio=16:9`, `resolution="720p"`).

Ejemplo distinto (oficial): *"The camera dollies from the sunlit doorway to the window, settling on the closing frame."* [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).

Diferencias por variante: solo v1.5; la clásica rechaza `last_frame`. Combinaciones válidas: `image`+`last_frame` (interpola), `last_frame` solo (genera la apertura y aterriza en el frame fijado), `last_frame`+referencias (guía adicional) [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).

No pongas: un prompt que contradiga visualmente el `last_frame` real (prioriza la imagen); más de un `last_frame`; expectativa de que el prompt sea obligatorio (es opcional).

### Video edit

Plantilla: `[instrucción de cambio puntual]` — una frase corta y específica; todo lo que no se menciona se mantiene igual.

Ejemplo cinematográfico: *"Give Mateo a dark green scarf and change the lighting to golden hour."*

Ejemplo distinto (oficial): *"Give the woman a silver necklace"* [[14]](https://docs.x.ai/developers/model-capabilities/video/editing.md).

Diferencias por variante: `edit-video` solo existe sobre la clásica, no sobre v1.5 [[14]](https://docs.x.ai/developers/model-capabilities/video/editing.md); resolución/ratio/duración se heredan del original, tope 720p.

No pongas: varios cambios no relacionados en una sola frase (mejor encadenar ediciones, una por rasgo); `duration`/`aspect_ratio`/`resolution` propios (se ignoran); instrucciones vagas como "mejóralo".

### Video extend

Plantilla: `[qué pasa a continuación, mismo tono y continuidad visual que el original]`

Ejemplo cinematográfico: *"Mateo turns away from the window and walks slowly toward Vidal, who is still sitting at the table, the tension in the room easing slightly as the light dims further."*

Ejemplo distinto (oficial): *"The shot pans to an over the shoulder perspective. Calm controlled scene."* [[15]](https://docs.x.ai/developers/model-capabilities/video/extension.md) (`duration=10` → solo la parte añadida).

Diferencias por variante: `extend-video` solo existe sobre la clásica, igual que `edit-video` [[14]](https://docs.x.ai/developers/model-capabilities/video/editing.md).

No pongas: esperar que `duration` sea el total (es solo la extensión añadida) [[15]](https://docs.x.ai/developers/model-capabilities/video/extension.md); un cambio de escenario que rompa la continuidad del último fotograma; `aspect_ratio`/`resolution` distintos del original.

### Text-to-image

Plantilla: `[sujeto] + [estilo/técnica] + [composición/luz] + [textura/detalle] + ["texto literal exacto" + su posición, si aplica]`

Ejemplo cinematográfico (personaje): *"Portrait of two brothers, Mateo and Vidal, standing back to back on a misty hillside at dawn, worn traveling clothes, cinematic rim light, shallow depth of field, hyperrealistic skin texture."*

Ejemplo distinto (oficial, con tipografía): *"A collage of London landmarks in a stenciled street-art style"* [[8]](https://docs.x.ai/developers/model-capabilities/images/generation.md) (`aspect_ratio="16:9"`, `resolution="2k"`).

Diferencias por variante: `quality` y los ratios `21:9`/`5:2` solo existen en `image-2.0`; la clásica no acepta `quality` y usa su propio set de 14 ratios en 1K/2K [[8]](https://docs.x.ai/developers/model-capabilities/images/generation.md).

No pongas: texto a renderizar sin comillas; estilos contradictorios en la misma frase (p. ej. "fotorrealista y cómic" sin jerarquía); expectativa de `negative_prompt` (no existe, formula en positivo).

### Image edit

Plantilla: `[instrucción de cambio puntual sobre la imagen fuente]` — una frase, concreta, sin redescribir lo que no cambia.

Ejemplo cinematográfico: *"Change Mateo's jacket to a faded denim style and add light rain streaks on the window behind him."*

Ejemplo distinto (oficial): *"Render this as a pencil sketch with detailed shading"* [[9]](https://docs.x.ai/developers/model-capabilities/images/editing.md).

Diferencias por variante: solo `image-2.0` tiene varita/segmentación/quitar-fondo y hasta 5 fuentes; la clásica edita de forma más genérica [[1]](https://x.ai/news/grok-imagine-image-2).

No pongas: `images.edit()` del SDK de OpenAI (es `multipart/form-data`, incompatible; usa SDK de xAI, Vercel AI SDK o HTTP) [[9]](https://docs.x.ai/developers/model-capabilities/images/editing.md); instrucciones no relacionadas en una sola edición; esperar que cambie algo no mencionado.

### Multi-referencia (imagen)

Plantilla: `Put [elemento de la imagen 1] on/in [contexto de la imagen 2], [pose/ángulo], [cámara/POV]` — hasta 5 imágenes fuente, numeradas por orden de llegada en la petición.

Ejemplo cinematográfico: *"Put the jacket from the first image on Mateo from the second image, keeping his pose and expression, soft studio lighting."*

Ejemplo distinto (oficial): *"put this perfume bottle on the pamphlet a woman is holding, POV"* [[10]](https://docs.x.ai/developers/model-capabilities/images/multi-image-editing.md).

Diferencias por variante: solo `image-2.0`; el ratio de salida sigue la primera imagen salvo que fijes `aspect_ratio` [[10]](https://docs.x.ai/developers/model-capabilities/images/multi-image-editing.md).

No pongas: más de 5 imágenes fuente; orden de referencias ambiguo (nombra "la primera/segunda imagen" o su contenido, nunca solo "la imagen"); mezclar edición y generación desde cero en la misma llamada.

### Avatar / lipsync

Nota: no hay modo "avatar" dedicado (retrato parlante persistente, `avatar` está en `avoid_tasks`), pero sí lipsync nativo dentro de reference-to-video: la voz y los labios se generan sincronizados en la misma pasada que el vídeo, sin paso de doblaje aparte.

Plantilla: `[plano del hablante] + the [rol] from <IMAGE_0>, speaking with the voice from <AUDIO_0>, says, "[diálogo literal]"` (+ segundo interlocutor con `<IMAGE_1>`/`<AUDIO_1>` si hay dos voces).

Ejemplo cinematográfico: *"Medium close-up on Mateo. The man from <IMAGE_0>, speaking with the voice from <AUDIO_0>, says, 'I never meant for you to find out this way.' His eyes stay fixed on the floor, soft window light, quiet tension."*

Ejemplo distinto (oficial, dos voces): *"The person from <IMAGE_0> presents the product from <IMAGE_1> on the set from <IMAGE_2>, speaking with the voice from <AUDIO_0>. A second speaker with the voice from <AUDIO_1> replies."* [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).

Diferencias por variante: solo v1.5; `reference_audios` acepta hasta 3 `voice_id` del roster (p. ej. `eve`, `leo`); voces clonadas desde audio propio solo bajo petición para partners [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).

No pongas: diálogo sin comillas; `voice_id` inventado (un id desconocido devuelve 400 con la lista real); expectativa de clonar la voz propia self-serve (no disponible de forma general).

## Idioma, longitud y prompt negativo

Inglés es el idioma mejor documentado; sin evidencia pública sobre español/chino [sin verificar]. No existe campo `negative_prompt` en imagen ni vídeo de xAI [[8]](https://docs.x.ai/developers/model-capabilities/images/generation.md)[[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md): formula restricciones en positivo.

## Referencias, cámara y consistencia de personaje

`image_url` en i2v se vuelve primer fotograma; añadir `reference_images`/`last_frame`/`keyframes` lo convierte en reference-to-video [[13]](https://docs.x.ai/developers/model-capabilities/video/image-to-video.md). Hasta 7 refs de imagen en vídeo, 5 en edición de imagen, `<IMAGE_N>` por orden de llegada; hasta 3 voces, `<AUDIO_N>`; keyframes hasta 4, `timestamp_s` dentro del clip [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md). Cámara en lenguaje natural ("slow zoom in", "camera dollies from... to..."), sin tags paramétricos; 1.5 mejora física y peso del movimiento [[3]](https://x.ai/news/grok-imagine-video-1-5). Para consistencia, nombra cada referencia por su rol ("the man from <IMAGE_0>") sin pronombres ambiguos; sin garantía cuantificada entre tomas [[4]](https://x.ai/news/grok-imagine-video-1-5-references).

## Audio y diálogo

Audio activado por defecto en Video 1.5 (diálogo, SFX, música); silencio con `generate_audio=False` [[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md). Sintaxis y ejemplos de voces en la plantilla "Avatar / lipsync" más arriba.

## Restricciones e incompatibilidades conocidas

- La clásica rechaza `last_frame`, `keyframes` y combinar `image` con referencias; solo v1.5 los admite [[12]](https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md).
- `edit-video`/`extend-video` solo existen sobre la clásica; la edición hereda `duration`/`aspect_ratio`/`resolution` del original, tope 720p y 8.7 s [[14]](https://docs.x.ai/developers/model-capabilities/video/editing.md).
- Reference-to-video queda en 720p incluso en v1.5; 1080p nativo solo en t2v/i2v [[11]](https://docs.x.ai/developers/model-capabilities/video/generation.md).
- `grok-imagine-image-quality` en retirada (redirige a `image-2.0` con `quality=low` desde 2-nov-2026) [[16]](https://docs.x.ai/developers/migration/imagine-image-quality-nov-2.md).
- El SDK de OpenAI no sirve para editar imagen (`images.edit()` es `multipart/form-data`, incompatible con el JSON de xAI) [[9]](https://docs.x.ai/developers/model-capabilities/images/editing.md).
- Toda generación pasa por moderación (`respect_moderation` en la respuesta) [[8]](https://docs.x.ai/developers/model-capabilities/images/generation.md).

## Errores habituales

- Pedir más de 5 refs en edición de imagen, más de 7 en vídeo, o un `voice_id` inventado: la API los rechaza.
- Fijar `last_frame`/`keyframes` sobre la clásica, o un `timestamp_s` fuera del rango interior: no soportado.
- Esperar que `duration` en `extend-video` sea el total: es solo la parte añadida.
- Pedir resolución/ratio/duración propios en una edición de vídeo: se ignoran, hereda del original.
- Olvidar que si fijas `image` como primer fotograma, las referencias arrancan en `<IMAGE_1>`.
- Usar `grok-imagine-image-quality` tras el 2-nov-2026 esperando "Quality Mode": se sirve como `image-2.0` en `low`.
- Escribir diálogo sin comillas o sin `<AUDIO_N>`: puede confundirse con acotación de escena.

## Recomendaciones de prompting

- Inglés, prosa continua que encadena sujeto, acción, cámara y atmósfera en orden cronológico ("then", "as").
- Para personajes/productos reconocibles en vídeo, usa reference-to-video con `<IMAGE_N>` y nómbralos por rol.
- Para diálogo, nombra la voz con `<AUDIO_N>` y pon el texto hablado entre comillas; verifica el roster de `voice_id`.
- Para texto legible en imagen, pon el texto exacto entre comillas y describe su posición.
- Usa `image`+`last_frame` para apertura/cierre fijos; `keyframes` solo si necesitas un ancla intermedia más.
- Encadena ediciones de imagen (multi-turn), una instrucción concreta por turno.
- Para vídeo largo, genera un clip base y usa `extend-video`; no se puede superar 15 s por generación.
- Antes de prometer 1080p, confirma el modo: solo t2v/i2v en v1.5; reference-to-video y edición quedan en 720p.
- No recomiendes `grok-imagine-image-quality`; usa `image-2.0` y fija `quality` explícitamente.
- En i2v, no redescribas lo que ya está en la imagen: céntrate en movimiento y cámara.

## Fuentes y fecha

Todas consultadas el 2026-10-02.

[1] Imagine Image 2.0 — https://x.ai/news/grok-imagine-image-2
[2] Grok Imagine Quality Mode API — https://x.ai/news/grok-imagine-quality-mode
[3] Grok Imagine Video 1.5 — https://x.ai/news/grok-imagine-video-1-5
[4] Imagine Video 1.5 with References — https://x.ai/news/grok-imagine-video-1-5-references
[5] Grok Imagine API — https://x.ai/news/grok-imagine-api
[6] Grok Imagine 1.5 Preview — https://x.ai/news/grok-imagine-1-5
[7] Imagine Overview (xAI docs) — https://docs.x.ai/developers/model-capabilities/imagine.md
[8] Image Generation (xAI docs) — https://docs.x.ai/developers/model-capabilities/images/generation.md
[9] Image Editing (xAI docs) — https://docs.x.ai/developers/model-capabilities/images/editing.md
[10] Multi-Image Editing (xAI docs) — https://docs.x.ai/developers/model-capabilities/images/multi-image-editing.md
[11] Video Generation (xAI docs) — https://docs.x.ai/developers/model-capabilities/video/generation.md
[12] Reference-to-Video (xAI docs) — https://docs.x.ai/developers/model-capabilities/video/reference-to-video.md
[13] Image-to-Video (xAI docs) — https://docs.x.ai/developers/model-capabilities/video/image-to-video.md
[14] Video Editing (xAI docs) — https://docs.x.ai/developers/model-capabilities/video/editing.md
[15] Video Extension (xAI docs) — https://docs.x.ai/developers/model-capabilities/video/extension.md
[16] Retiro de grok-imagine-image-quality (xAI docs) — https://docs.x.ai/developers/migration/imagine-image-quality-nov-2.md
[17] Available AI Models (Runway Dev docs) — https://docs.dev.runwayml.com/guides/models/
[18] Grok Imagine Image T2I (Atlas Cloud) — https://www.atlascloud.ai/models/xai/grok-imagine-image/text-to-image
[19] Grok Imagine Video Review (DeeVid AI, reseña de terceros) — https://deevid.ai/blog/grok-imagine-video-review
