---
id: 'ltx'
kind: 'model'
title: 'LTX (Lightricks LTX-2.3 / LTX-2.5)'
version: 2
updated: '2026-10-02'
match: ['^ltx']
modalities: 'VIDEO'
strengths: [native_audio, high_resolution, camera_control, prompt_adherence, multi_shot, long_duration]
weaknesses: [identity_preservation, physics, text_rendering, multi_character]
ideal_tasks: [text_to_video, image_to_video, start_end_frame, video_extend]
avoid_tasks: [text_to_image, image_edit, multi_reference_image, upscale, layer_decomposition, lipsync, avatar, motion_control, reference_to_video]
unverified_tags: []
sources:
  - {url: 'https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video', title: 'API Reference — Generate video from text', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/models', title: 'Supported Models (LTX-2.5 / LTX-2.3)', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/models/ltx-2-5', title: 'LTX-2.5 model page — endpoints, support matrix, duración automática', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/models/ltx-2-3', title: 'LTX-2.3 model page — endpoints, support matrix', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide', title: 'Prompting Guide oficial de LTX', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/input-formats', title: 'Input Formats — imagen/vídeo/audio', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-image-to-video', title: 'API Reference — Generate video from image', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-audio-to-video', title: 'API Reference — Generate video from audio', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend', title: 'API Reference — Extend video duration', accessed: '2026-10-02'}
  - {url: 'https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-retake', title: 'API Reference — Retake video section', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/ltx-2.3-quality/text-to-video', title: 'Atlas Cloud — LTX 2.3 Quality Text-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/ltx-2.3-quality/extend-video', title: 'Atlas Cloud — LTX 2.3 Quality Extend Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/ltx/ltx-2.3-pro/audio-to-video', title: 'Atlas Cloud — LTX-2.3 Pro Audio-to-Video (API nativa Lightricks)', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/ltx/ltx-2.5-pro/text-to-video', title: 'Atlas Cloud — LTX-2.5 Pro Text-to-Video (API nativa Lightricks)', accessed: '2026-10-02'}
---

## Resumen

LTX (Lightricks) es la familia de vídeo generativo más rápida y barata de Nexus en 720p-4K, y la única con **audio sincronizado nativo** en el mismo paso que el vídeo, en sus tiers Pro y en LTX-2.5 Fast [[1]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video). Destaca por control de cámara explícito, 4K a 24/48 fps y, desde LTX-2.5, escenas **multi-shot nativas** [[3]](https://docs.ltx.io/models/ltx-2-5). No es la familia a elegir si lo crítico es la identidad facial entre generaciones o el texto en pantalla.

## Cuándo elegirlo y cuándo no

- Elige **LTX-2.5 Pro** para la mejor calidad/textura/tipografía, con audio nativo y multi-shot [[3]](https://docs.ltx.io/models/ltx-2-5).
- Elige **Fast** (2.5 o 2.3) para iterar rápido/barato o un clip largo en una sola pasada (hasta 20 s en 720p/1080p) [[4]](https://docs.ltx.io/models/ltx-2-3).
- Elige **LTX-2.3-pro** o **LTX-2.3-quality (extend-video)** para alargar un vídeo existente — única generación con `video_extend`; LTX-2.5 aún no lo soporta [[12]](https://www.atlascloud.ai/models/ltx-2.3-quality/extend-video).
- Mejor **Kling** si lo crítico es identidad facial o física exigente: "la física caótica puede introducir artefactos" [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- Mejor **Seedance**/**Wan** si necesitas texto en pantalla exacto; **Wan** si el flujo necesita LoRAs locales fuera de Nexus.

| Necesidad | Variante recomendada |
|---|---|
| Clip con audio sincronizado, calidad máxima | `ltx/ltx-2.5-pro/*` |
| Iteración rápida/barata, clip largo en una toma | `ltx/ltx-2.5-fast/*` o `ltx/ltx-2.3-fast/*` |
| Vídeo guiado por audio/diálogo existente | `ltx/ltx-2.5-pro/audio-to-video` o `ltx/ltx-2.5-fast/audio-to-video` |
| Alargar un vídeo ya generado | `ltx/ltx-2.3-pro/extend` o `ltx-2.3-quality/extend-video` |
| Interpolar primer→último fotograma | `ltx/ltx-2.5-pro/image-to-video` o `ltx/ltx-2.3-pro/image-to-video` con `last_frame_uri` |
| Escena de varios cortes con continuidad de voz/luz | `ltx/ltx-2.5-pro/text-to-video` o `ltx/ltx-2.5-fast/text-to-video` (multi-shot nativo) |

## Linaje y variantes en Nexus

LTX-2 es la arquitectura de 2ª generación de Lightricks, en tiers **fast** y **pro** [[2]](https://docs.ltx.io/models):

- **LTX-2.3** (`ltx/ltx-2.3-fast/*`, `ltx/ltx-2.3-pro/*`): hasta 4K/24-48fps; solo Pro genera audio y soporta `extend`/`retake` [[4]](https://docs.ltx.io/models/ltx-2-3).
- **LTX-2.3 "quality"** (`ltx-2.3-quality/*`): misma generación, contrato de Atlas Cloud; también expone `video_extend` [[12]](https://www.atlascloud.ai/models/ltx-2.3-quality/extend-video).
- **LTX-2.5** (`ltx/ltx-2.5-fast/*`, `ltx/ltx-2.5-pro/*`) — **más reciente**: mejor decodificador, más fidelidad en textura/caras/tipografía, **multi-shot nativo** y **duración automática**; ambos tiers generan audio, pero sin `extend`/`retake` todavía [[3]](https://docs.ltx.io/models/ltx-2-5).

Por defecto usa **LTX-2.5**, salvo `video_extend` (solo en la línea 2.3).

## Fortalezas

- **native_audio**: música, ambiente y diálogo sincronizados en el mismo request (`generate_audio`); LTX-2.3 Fast es la única variante muda [[1]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video).
- **high_resolution**: hasta 4K (3840×2160) en landscape y portrait, 24 o 48 fps [[3]](https://docs.ltx.io/models/ltx-2-5).
- **camera_control**: `camera_motion` con 8 valores (dolly/jib/focus_shift/static) además de lenguaje de cámara en prosa [[1]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video).
- **prompt_adherence**: LTX-2.5 es oficialmente más fiel al prompt, con más detalle en textura, caras y tipografía que 2.3 [[3]](https://docs.ltx.io/models/ltx-2-5).
- **multi_shot**: LTX-2.5 genera varios planos con cortes en una toma, manteniendo personaje, luz, estilo y voz entre cortes [[3]](https://docs.ltx.io/models/ltx-2-5).
- **long_duration**: los tiers Fast llegan a 20 s en una pasada a 720p/1080p sin encadenar `extend` [[4]](https://docs.ltx.io/models/ltx-2-3).

## Debilidades

- **identity_preservation**: sin referencia de personaje multi-imagen; el único anclaje es la primera (y opcional última) imagen del propio clip, sin garantía entre generaciones distintas [[7]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-image-to-video).
- **physics**: el movimiento muy caótico puede introducir artefactos; la guía recomienda acción plausible y simple [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- **text_rendering**: LTX-2.5 mejora el texto corto, pero la ortografía exacta y su consistencia entre fotogramas "no están garantizadas" [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- **multi_character**: sin mecanismo propio para varios personajes a la vez; la guía recomienda pocos personajes claros, no una escena abarrotada [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).

## Tareas ideales / Tareas que debe evitar

**Ideales**: `text_to_video`, `image_to_video`, `start_end_frame` (vía `last_frame_uri`), `video_extend` (`ltx-2.3-pro` o `ltx-2.3-quality/extend-video`).

**Evitar**: `text_to_image`, `image_edit`, `multi_reference_image`, `upscale`, `layer_decomposition` (fuera del alcance de la familia en Nexus); `lipsync`/`avatar` (el "Dub-It" es un IC-LoRA local/ComfyUI, fuera de la API [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide)); `motion_control` (herramienta local de ComfyUI); `reference_to_video` (sin referencia de personaje/estilo independiente del primer fotograma).

## Estructura de prompt recomendada

LTX responde mejor a **un párrafo fluido en prosa** (nunca listas de tags) que recorra, en orden, los seis elementos clave de la guía oficial: plano → escena/luz → acción → personaje → cámara → audio [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide):

```
A [shot scale, e.g. wide shot / medium close-up] of [scene: lighting, color palette, textures, atmosphere, location].
[Character(s): age, hairstyle, clothing, distinguishing features] [present-tense action verb — walks, turns, exhales, reaches] [physical detail/emotion as a gesture], as [secondary action or reaction].
The camera [movement in prose, described relative to the subject — tracks, pushes in, pans across, circles around], arriving on [the resulting framing after the move].
[Ambient sound / music]. "[Spoken dialogue in quotes]" (language/accent if not neutral).
```

- **Idioma óptimo**: inglés (sin confirmación de calidad en español `[sin verificar]`).
- **Longitud**: plano único, 4-8 frases; escena tipo guión más larga si cada frase añade detalle concreto; multi-shot: 2-4 cortes [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- **Tono**: prosa cinematográfica natural, verbos en presente que generan movimiento, no solo adjetivos de apariencia estática [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- **Sintaxis especial**: `camera_motion` (8 valores enum) va en el request, no en el texto; diálogo siempre entre comillas, con hablante e idioma/acento si no es neutro ("Reporter: \"...\""). No existe sintaxis `--parámetros` de Midjourney; todo va en el JSON de la API [[1]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video). El *prompt enhancer* (`--enhance-prompt`) solo existe en pipelines locales/ComfyUI, no en la API directa [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- LTX-2.5 tiene **duración automática** (`duration: null`): dimensiona el clip a la acción descrita, sin estirar momentos no escritos. Para un silencio o "beat", escríbelo explícitamente [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).

## Plantillas por tarea

### Text-to-video

```
A [shot scale] shot of [scene: lighting, color palette, textures, atmosphere, location].
[Character(s): age, look, wardrobe] [present-tense verb] [physical action/detail], as [secondary action or reaction].
The camera [movement relative to the subject], arriving on [resulting framing].
[Ambient sound / music]. "[Dialogue]" (speaker, language/accent if needed).
```

**Ejemplo 1 (cinematográfico, personajes)**: "A wide shot opens on a sunlit wheat field at golden hour, dust motes drifting through warm backlight. Mateo, a lanky teenager in a faded denim jacket, sprints ahead through the tall grass, laughing, while his younger brother Vidal, in a striped t-shirt, stumbles trying to keep up and drops his wooden toy sword. The camera tracks alongside them at waist height, then pushes in as Mateo turns and crouches to pick it up, handing it back. Birdsong and rustling wheat fill the air. Vidal grins. Mateo: \"Come on, slowpoke — race you to the barn!\""

**Ejemplo 2 (acción/estilizado, sin personajes nombrados)**: "A low-angle tracking shot follows a chrome motorcycle weaving through a neon-lit cyberpunk market at night, rain slicking the pavement, pink and cyan signage reflecting off puddles. The rider, clad in a matte-black helmet and armored jacket, leans hard into a sharp turn as sparks skitter off the exhaust and stray vendors leap out of the way. The camera pans right, then whips forward into a close-up on the rider's visor, reflecting the blurred lights. A synth-heavy score pulses alongside screeching tires and distant alarms."

**Diferencias entre variantes**: la sintaxis de prompt es idéntica en Fast y Pro, y en 2.3 y 2.5 — el `support matrix` oficial no documenta ninguna tolerancia distinta a la complejidad del prompt por tier; la diferencia real entre Fast y Pro es velocidad/coste, no estructura de texto [[2]](https://docs.ltx.io/models) [[3]](https://docs.ltx.io/models/ltx-2-5) [[4]](https://docs.ltx.io/models/ltx-2-3). Solo LTX-2.5 soporta multi-shot y duración automática; en LTX-2.3 escribe siempre un único plano continuo y fija `duration` explícita.

**No pongas**:
- Listas de tags separados por comas al estilo Kling/Seedance ("cyberpunk, neon, 4k"): la guía pide prosa fluida, el formato rinde peor [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- Varios personajes secundarios sin identificarlos: una escena abarrotada se lee peor que una enfocada [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- Pausas no escritas esperando que el modelo las añada con duración automática.
- Fuentes de luz contradictorias en el mismo plano.

### Image-to-video

```
[Primary motion of the subject already framed in the image], as [secondary motion or reaction].
The camera [movement relative to the subject already in frame], arriving on [resulting framing].
[Resulting ambient sound / dialogue].
```

No redescribas la apariencia ya fijada por la imagen (ropa, color, composición, iluminación de origen): el prompt debe aportar **movimiento**, no una segunda descripción visual. La guía recomienda mantener un único plano continuo al animar un primer fotograma, salvo que describas explícitamente un corte [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).

**Ejemplo 1 (cinematográfico, continuación de una foto de los hermanos en la cocina)**: "Mateo turns away from the window and walks toward the table, pulling out a chair for his brother; Vidal climbs up and starts swinging his legs, laughing at something off-screen. The camera slowly pushes in from behind Mateo's shoulder, settling on both their faces as morning light shifts across the table. Spoons clink against bowls and a kettle starts to whistle in the background."

**Ejemplo 2 (producto, sin personajes)**: "The bottle rotates slowly clockwise on its pedestal as a soft rim light sweeps across the glass, catching the amber liquid inside. A single droplet of condensation slides down the side and lands on the marble surface below. The camera arcs around to the left, settling on a three-quarter view as a low chime rings out."

**Diferencias entre variantes**: `image-to-video`, `camera_motion` y `last_frame_uri` están disponibles en los cuatro tiers principales (2.3 Fast/Pro, 2.5 Fast/Pro) [[3]](https://docs.ltx.io/models/ltx-2-5) [[4]](https://docs.ltx.io/models/ltx-2-3); LTX-2.5 conserva más fidelidad de la imagen de entrada (textura, caras) gracias a su decodificador mejorado [[3]](https://docs.ltx.io/models/ltx-2-5).

**No pongas**:
- Redescribir ropa, color o composición ya fijados en la imagen de entrada — desperdicia presupuesto de prompt sin añadir movimiento.
- Contradecir el encuadre o la perspectiva ya establecidos por la imagen.
- Pedir un corte a otro plano sin avisarlo: por defecto el modelo asume una sola toma continua desde el primer fotograma [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- Combinar duración automática con `last_frame_uri` — incompatibles (ver sección siguiente) [[7]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-image-to-video).

### Start/end frame (image-to-video con `last_frame_uri`)

```
[Path/motion connecting the first frame to the arrival composition], ending on [exact framing/pose/light described by the last-frame image].
[Camera behavior across the interpolation].
[Ambient sound].
```

`last_frame_uri` interpola entre `image_uri` (primer fotograma) y esta imagen (último fotograma); exige `duration` explícita en segundos — **no admite duración automática** [[7]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-image-to-video).

**Ejemplo 1 (cinematográfico)**: primer fotograma: los hermanos de pie frente a la puerta; último fotograma: ambos sentados a la mesa. "Mateo pushes the door open and steps inside, Vidal trailing behind clutching his backpack; they cross the room together as the camera tracks backward ahead of them, arriving on both brothers settling into their chairs at the table, exactly as framed in the final image. Floorboards creak and a radio hums softly in the background." (`duration: 6`)

**Ejemplo 2 (producto)**: primer fotograma: botella cerrada; último fotograma: tapón retirado y apoyado al lado. "The hand reaches in from the right, twists the cap counter-clockwise, and lifts it away, setting it down beside the bottle exactly as shown in the final frame. The camera holds a static three-quarter view throughout. A soft click accompanies the cap's release, followed by quiet ambient hum." (`duration: 4`)

**Diferencias entre variantes**: idéntico en los cuatro tiers (2.3/2.5 × Fast/Pro) [[3]](https://docs.ltx.io/models/ltx-2-5) [[4]](https://docs.ltx.io/models/ltx-2-3).

**No pongas**:
- `duration: null` junto con `last_frame_uri` — el campo exige un valor numérico; son incompatibles [[7]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-image-to-video).
- Un corte abrupto a mitad de camino: el modelo interpola un único movimiento continuo entre ambos fotogramas.
- Dejar el "cómo llegamos" totalmente abierto: sin camino intermedio descrito, puede recurrir a movimientos bruscos (relacionado con `physics`) [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- Composiciones radicalmente distintas entre ambos fotogramas sin describir cómo se resuelve el salto.

### Video extend (`ltx-2.3-pro` / `ltx-2.3-quality/extend-video`)

```
[Only the new continuation]: what happens next, matching the source clip's established lighting, style, and motion.
[Optional: an explicit beat or pause if you want a change of pace].
```

Parámetros clave de la API nativa: `mode` (`end`, por defecto, continúa al final; o `start`, añade antes del primer fotograma), `duration` (2-20 s), `context` (1-20 s de contexto tomado del vídeo de origen; `context + duration` en frames no puede superar 505 frames ≈21 s a 24 fps) [[9]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend). El vídeo de entrada debe ser 16:9 o 9:16, hasta 4K, con un mínimo de 73 frames (~3 s a 24 fps) [[9]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend).

**Ejemplo 1 (cinematográfico)**: el clip de origen termina con los hermanos corriendo por el campo de trigo. "Mateo and Vidal reach the edge of the field and burst out onto a dirt path, slowing to a walk as they catch their breath, the wooden sword swinging loosely at Vidal's side. The late afternoon light deepens into amber, and crickets begin to chirp."

**Ejemplo 2 (acción)**: el clip de origen termina con la moto cruzando el mercado nocturno. "The motorcycle clears the market and bursts onto an open highway bridge, rain easing into a light drizzle under flickering sodium lights. The rider straightens up, exhaling, as the synth score settles into a steadier rhythm."

**Diferencias entre variantes**: `extend` solo existe en `ltx-2-3-pro` (por defecto en el campo `model` del endpoint nativo) y en el contrato alternativo `ltx-2.3-quality/extend-video` de Atlas Cloud; LTX-2.5 no soporta `extend` en ningún tier por ahora [[3]](https://docs.ltx.io/models/ltx-2-5) [[9]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend).

**No pongas**:
- Redescribir el contenido ya existente en el clip de origen — el prompt (máx. 5000 caracteres) debe describir solo la parte nueva [[9]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend).
- Pedir `extend` con aspect ratio distinto de 16:9/9:16, o en `ltx-2-5-pro`/`fast` — no soportado [[9]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend).
- Omitir `mode` para extender por el principio: por defecto extiende por el final.

### Multi-shot (solo LTX-2.5)

```
[Shot 1: shot scale, scene, character(s), action, camera]. [Named cut — "A hard cut transitions to…", "A match cut connects…", "The image dissolves into…"] [Shot 2: re-establish shot scale/angle/lighting; re-identify recurring subjects; state audio continuity]. (repetir 2-4 cortes)
```

Escribe la escena como **un único párrafo cronológico** ("Initially…", "A moment later…", "Simultaneously…"), nunca una lista numerada de planos ni sluglines de guion sin describir el corte en prosa; en cada corte, reafirma plano/ángulo/luz, reidentifica a quien reaparece y di si la banda sonora continúa o cambia [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).

**Ejemplo 1 (oficial, resumido)**: "A wide shot frames a rainy city intersection at dusk, neon signs reflecting on wet asphalt. A young woman in a yellow raincoat walks toward camera as rain falls and cars pass behind her; soft synth music and traffic noise fill the air. The shot transitions to a medium close-up of her face under the hood, raindrops catching the neon; the synth score continues across the cut, traffic muffled. She speaks quietly, \"He's late.\" A hard cut jumps to a low-angle shot of a man's scuffed boots stepping into a puddle; the music drops to a low drone." [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide)

**Ejemplo 2 (personajes, 2 cortes)**: "A wide shot establishes a dusty barn interior at midday, sunlight slicing through gaps in the wooden walls. Mateo leans against a stack of hay bales, catching his breath, while Vidal runs in circles waving the wooden sword overhead; distant birdsong and creaking wood fill the air. A hard cut transitions to a close-up of Vidal's face, grinning as he collapses onto a hay bale, Mateo's laughter continuing uninterrupted across the cut."

**Diferencias entre variantes**: exclusivo de LTX-2.5 (Fast y Pro); LTX-2.3 no soporta cortes dentro de una misma generación — en 2.3 escribe siempre un plano único [[3]](https://docs.ltx.io/models/ltx-2-5) [[4]](https://docs.ltx.io/models/ltx-2-3). La guía recomienda 2-4 cortes por generación; más cortes exigen beats más cortos y claros por plano [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).

**No pongas**:
- Lista numerada de planos o sluglines de guion sin describir el corte en prosa — el modelo necesita el corte nombrado en el párrafo.
- Geografía contradictoria o cambios de vestuario no explicados entre cortes, salvo que digas que el corte salta en el tiempo/lugar [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- Más de 4-5 cortes sin acortar cada beat, u olvidar la continuidad de audio en cada corte.

## Idioma, longitud y prompt negativo

Idioma óptimo: inglés. No hay campo `negative_prompt` — expresa restricciones de forma afirmativa ("cielo despejado" en vez de "sin lluvia") [[1]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video). Longitud: 4-8 frases por plano simple; guión o multi-shot pueden ser más largos si cada frase añade información concreta [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide). Verifica el límite real de caracteres de cada endpoint en `get_model_schema`.

## Referencias e imagen inicial

`image_uri` fija el primer fotograma; `last_frame_uri` (opcional) fija el último e interpola entre ambos — no combinable con duración automática [[7]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-image-to-video). No existe referencia de estilo/personaje independiente de la imagen de entrada: la identidad va en el prompt o en la imagen inicial.

## Cámara y movimiento

Vocabulario explícito vía `camera_motion` (8 valores): `dolly_in/out/left/right`, `jib_up/down`, `focus_shift`, `static` [[1]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video). En prosa también entiende "tracks", "pans across", "circles around", "tilts upward", "pushes in/pulls back", "handheld", "over-the-shoulder" [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide). Describe siempre el encuadre resultante *después* del movimiento, no solo el movimiento [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).

## Consistencia de personaje

LTX no garantiza identidad entre generaciones distintas. En un mismo clip multi-shot, reutiliza los mismos identificadores visuales al reaparecer un personaje ("la mujer del abrigo rojo, antes en la mesa, ahora…") [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide). Entre generaciones separadas, usa siempre la misma `image_uri` o manténlo en encuadre único.

## Audio y diálogo

Los tiers con audio (`ltx/ltx-2.3-pro/*`, `ltx/ltx-2.5-fast/*`, `ltx/ltx-2.5-pro/*`) generan ambiente, música y diálogo sincronizados en el mismo paso (`generate_audio`, activo por defecto) [[8]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-audio-to-video). Escribe el diálogo entre comillas, con idioma/acento si hace falta: `Reporter: "Thank you, Sylvia..."` [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide). En **audio-to-video** (`audio_uri`), el audio de entrada fija la duración de salida; `image_uri` es opcional. La familia no ofrece `lipsync`/`avatar` en Nexus: el "Dub-It" (`[Speaker] is speaking [Language/Accent], saying: "[Dialogue]"`) es un IC-LoRA local/ComfyUI, fuera del catálogo [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).

## Restricciones e incompatibilidades conocidas

- Contenido rechazado por filtros de seguridad devuelve error 422 en todos los endpoints [[1]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video).
- `extend`/`retake` solo en `ltx-2-3-pro` (y `ltx-2.3-quality`); LTX-2.5 no los soporta; límites de aspect ratio/frames en su plantilla, arriba [[9]](https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend).
- No existe `negative_prompt` documentado.
- URL de entrada: HTTPS, dominio público, sin redirecciones; límites de tamaño por método (15 MB imagen / 32 MB vídeo-audio; 7-15 MB en Data URI) [[6]](https://docs.ltx.io/input-formats).

## Errores habituales

- Pegar un prompt de Kling/Seedance (tags, shot-list) sin reescribirlo en prosa fluida — el contenido pasa, el formato rinde peor [[5]](https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide).
- Usar LTX-2.3 Fast esperando audio: ese tier es siempre mudo [[4]](https://docs.ltx.io/models/ltx-2-3).
- Pedir `extend` en LTX-2.5 o combinar duración automática con `last_frame_uri`: ambos incompatibles.
- Mezclar fuentes de luz contradictorias en el mismo plano, o esperar texto en pantalla perfecto.

## Recomendaciones de prompting

- Escribe en inglés, en párrafo fluido, con verbos de acción reales, no listas de tags.
- Describe el encuadre resultante tras cada movimiento de cámara; usa `camera_motion` para lo simple y prosa para lo compuesto.
- Plano simple: 4-8 frases; multi-shot: nombra el corte en la prosa y reidentifica a quien reaparece.
- Diálogo siempre entre comillas, con idioma/acento si no es neutro.
- Al animar una imagen, describe el movimiento relativo al contenido ya fijado, nunca la apariencia ya visible.
- Con `last_frame_uri`, describe el camino intermedio y fija siempre `duration` explícita.
- Con `extend`, describe solo la continuación, respetando el límite de 505 frames entre contexto y duración.
- Evita física caótica; si quieres una pausa con duración automática, escríbela explícitamente.

## Fuentes y fecha

Todas consultadas el 2026-10-02.
[1] Generate video from text — https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-text-to-video
[2] Supported Models — https://docs.ltx.io/models
[3] LTX-2.5 model page — https://docs.ltx.io/models/ltx-2-5
[4] LTX-2.3 model page — https://docs.ltx.io/models/ltx-2-3
[5] Prompting Guide oficial — https://docs.ltx.io/api-documentation/implementation-guides/prompting-guide
[6] Input Formats — https://docs.ltx.io/input-formats
[7] Generate video from image — https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-image-to-video
[8] Generate video from audio — https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-audio-to-video
[9] Extend video duration — https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-extend
[10] Retake video section — https://docs.ltx.io/api-documentation/api-reference/async-video-generation/submit-retake
[11] Atlas Cloud — LTX 2.3 Quality Text-to-Video — https://www.atlascloud.ai/models/ltx-2.3-quality/text-to-video
[12] Atlas Cloud — LTX 2.3 Quality Extend Video — https://www.atlascloud.ai/models/ltx-2.3-quality/extend-video
[13] Atlas Cloud — LTX-2.3 Pro Audio-to-Video (API nativa) — https://www.atlascloud.ai/models/ltx/ltx-2.3-pro/audio-to-video
[14] Atlas Cloud — LTX-2.5 Pro Text-to-Video (API nativa) — https://www.atlascloud.ai/models/ltx/ltx-2.5-pro/text-to-video
