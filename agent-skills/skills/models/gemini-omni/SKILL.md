---
id: 'gemini-omni'
kind: model
title: 'Gemini Omni Flash / Omni 1.1 Flash (Google DeepMind)'
version: 2
updated: 2026-10-02
match: ['^google/gemini-omni', '^runway/gemini_omni']
modalities: ['video']
strengths: [native_audio, cinematic_look, physics, text_rendering, long_duration, fast, high_resolution]
weaknesses: [multi_character, low_cost]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, multi_reference_image]
avoid_tasks: [motion_control, lipsync, avatar, text_to_image, image_edit]
unverified_tags: [camera_control]
sources:
  - url: 'https://ai.google.dev/gemini-api/docs/omni'
    title: 'Gemini Omni Flash — Gemini API'
    accessed: 2026-10-02
  - url: 'https://ai.google.dev/gemini-api/docs/video'
    title: 'Video generation overview, Omni vs Veo'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/text-to-video'
    title: 'Omni 1.1 Flash — Text to Video (Atlas Cloud)'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/image-to-video'
    title: 'Omni 1.1 Flash — Image to Video (Atlas Cloud)'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/reference-to-video'
    title: 'Omni 1.1 Flash — Reference to Video (Atlas Cloud)'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/video-edit'
    title: 'Omni 1.1 Flash — Video Edit (Atlas Cloud)'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/video-extend'
    title: 'Omni 1.1 Flash — Video Extend (Atlas Cloud)'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-flash/text-to-video'
    title: 'Omni Flash — Text to Video (Atlas Cloud)'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-flash/image-to-video-developer'
    title: 'Omni Flash — Image to Video Developer (Atlas Cloud)'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/google/gemini-omni-flash/reference-to-video-developer'
    title: 'Omni Flash — Reference to Video Developer (Atlas Cloud)'
    accessed: 2026-10-02
---

## Resumen

Gemini Omni es la familia de vídeo nativamente multimodal de Google DeepMind: un único transformer que procesa texto, imagen, audio y vídeo a la vez, en lugar de encadenar un generador mudo con un doblaje posterior [[1]](https://ai.google.dev/gemini-api/docs/omni). Su rasgo distintivo es **generar audio nativo sincronizado** (diálogo, ambiente, música, SFX) en la misma pasada, y entender **edición conversacional** sobre un clip existente turno a turno vía `previous_interaction_id` [[1]](https://ai.google.dev/gemini-api/docs/omni). "Omni 1.1 Flash" es la revisión vigente: añade salida hasta 4K con escalera de borrador en 360p, extensión encadenada hasta 40 s totales, primer/último fotograma, referencias de vídeo y nivel de razonamiento (`thinking_level`) [[3]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/text-to-video); recomiéndala siempre frente a la 1.0 salvo que necesites el contrato `-developer` del fabricante.

## Cuándo elegirlo y cuándo no

Elígelo para sonido diegético o diálogo generado junto a la imagen, para editar o continuar un vídeo existente con lenguaje natural turno a turno, o cuando el prompt depende de conocimiento del mundo real (física, cultura, narrativa) que el modelo puede razonar antes de generar [[1]](https://ai.google.dev/gemini-api/docs/omni).

Evítalo si necesitas control de cámara numérico fino: Kling, o3 y Seedance aceptan parámetros de cámara explícitos; Omni solo entiende cámara en lenguaje natural, sin perilla de intensidad. Tampoco si necesitas lipsync sobre audio externo o un avatar con identidad hablante persistente — "voice editing is not supported" y no hay flujo de avatar dedicado [[1]](https://ai.google.dev/gemini-api/docs/omni). Para vídeo mudo barato en volumen, Wan o HappyHorse compensan más al no pagar audio nativo en cada segundo [[7]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/video-extend). Frente a Grok Imagine Video, Omni razona más como "director de cine" (narrativa, física, texto en pantalla) que como "clip viral rápido".

| Necesidad | Variante (prefijo `gemini-omni-1.1-flash/`) |
|---|---|
| Sonido desde cero, sin imagen de partida | `text-to-video` |
| Animar una foto o ilustración existente | `image-to-video` |
| Transición exacta entre dos fotogramas / loop perfecto | `image-to-video` con `image` + `last_image` |
| Mantener personaje, producto o estilo en varias escenas nuevas | `reference-to-video` (hasta 10 imágenes + 3 clips) |
| Editar un vídeo existente con una instrucción puntual | `video-edit` (turno conversacional) |
| Alargar un plano por encima de los ~10 s nativos | `video-extend` (hasta 40 s totales) |
| Acceso de bajo nivel del fabricante, seed fijo, SDK propio | sufijo `-developer` sobre `gemini-omni-flash` (gen. 1) |
| Vídeo mudo en volumen, coste mínimo por clip | mejor Wan o HappyHorse que Omni |
| Avatar con identidad hablante estable o lipsync sobre audio externo | mejor otra familia con avatar dedicado |

## Linaje y variantes en Nexus

**Gen. 1 — Gemini Omni Flash** (`gemini-omni-flash-preview` / `gemini-omni-flash`): lanzamiento original; cuatro tareas sobre el mismo modelo diferenciadas por `task` en `generation_config.video_config` [[8]](https://www.atlascloud.ai/models/google/gemini-omni-flash/text-to-video). Las `-developer` son el mismo modelo con contrato directo del fabricante: hasta 7 imágenes o 1 vídeo fuente, seed fijo [[9]](https://www.atlascloud.ai/models/google/gemini-omni-flash/image-to-video-developer).

**Gen. 2 — Gemini Omni 1.1 Flash** (`gemini-omni-1.1-flash`), **la más reciente, recomiéndala por defecto**: añade salida 4K/1080p con borrador en 360p, extensión encadenada hasta 40 s, primer y último fotograma (`image`+`last_image`), hasta 10 imágenes y 3 vídeos de referencia, y `thinking_level` para controlar cuánto razona antes de generar [[3]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/text-to-video). También edita y extiende multi-turno sobre vídeos generados por el propio modelo vía `previous_interaction_id`, sin re-subir el clip [[1]](https://ai.google.dev/gemini-api/docs/omni).

Mapa de ids: `google/gemini-omni-flash/*(-developer)` → gen. 1; `google/gemini-omni-1.1-flash/*` → gen. 2; `runway/gemini_omni_flash*/{i2v,t2v,v2v}` → mismos modelos vía Runway/Crun [sin verificar]. Sin variantes "fast"/"turbo"/"mini": la única palanca de velocidad/coste es la resolución (360p borrador vs. 1080p/4K final) [[3]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/text-to-video).

## Fortalezas

- **native_audio**: diálogo, ambiente, música y SFX sincronizados en la misma pasada, sin doblaje posterior; por defecto decide un audio "apropiado" si no lo describes [[1]](https://ai.google.dev/gemini-api/docs/omni).
- **cinematic_look**: razona sobre física y lógica narrativa para componer una escena intencional, no solo una animación de la imagen de entrada [[1]](https://ai.google.dev/gemini-api/docs/omni).
- **physics**: combina física creíble (peso, inercia, colisiones) con el conocimiento de mundo de Gemini (historia, ciencia, contexto cultural) [[1]](https://ai.google.dev/gemini-api/docs/omni).
- **text_rendering**: escribe texto en pantalla legible y correcto si se cita explícitamente entre comillas, incluso en elementos de fondo [[1]](https://ai.google.dev/gemini-api/docs/omni).
- **long_duration**: 1.1 encadena extensiones de 3-10 s hasta 40 s totales, usando los últimos 10 s del clip previo como contexto y retocando los fotogramas finales para que la unión sea invisible [[1]](https://ai.google.dev/gemini-api/docs/omni).
- **fast**: posicionado por el propio fabricante como la opción "de alta velocidad" dentro de su catálogo, frente a Veo [[2]](https://ai.google.dev/gemini-api/docs/video).
- **high_resolution**: salida nativa hasta 4K, con escalera de borrador en 360p para iterar barato antes de renderizar en calidad final [[3]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/text-to-video).

## Debilidades

- **multi_character**: razonar sobre varios vídeos de referencia a la vez no está soportado y puede degradar el resultado o producir salidas inesperadas; escenas con muchos personajes recurrentes son más frágiles que en Kling o Seedance [[1]](https://ai.google.dev/gemini-api/docs/omni).
- **low_cost**: no es la opción barata — cobra por segundo según resolución, y el audio nativo no es opcional ni gratis; para clips mudos en volumen compensa más Wan o HappyHorse [[7]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/video-extend).

`camera_control` queda `[sin verificar]`: entiende cámara en lenguaje natural con vocabulario real de rodaje, pero no hay parámetro de intensidad o trayectoria de cámara dedicado como en Kling o Runway.

## Tareas ideales / Tareas que debe evitar

**Ideales**: `text_to_video`, `image_to_video`, `reference_to_video`, `start_end_frame`, `video_edit`, `video_extend`, `multi_reference_image`.
**Evitar**: `motion_control` (sin parámetro numérico de cámara), `lipsync` y `avatar` (sin edición de voz ni identidad hablante persistente — "voice editing is not supported"); `text_to_image`, `image_edit` (es un modelo de vídeo, no genera ni edita imágenes fijas) [[1]](https://ai.google.dev/gemini-api/docs/omni).

## Estructura de prompt recomendada

Tono: prosa natural y descriptiva, como una nota de dirección de foto — no etiquetas tipo Midjourney (`--ar`, `--chaos` no existen aquí; solo valen si el esquema en vivo de Nexus los traduce [verificar]). Orden que mejor sigue el modelo: (1) tipo de plano/cámara, (2) estilo visual, (3) iluminación, (4) localización, (5) sujeto + acción con timing, (6) sonido/diálogo, (7) negaciones. Por defecto el modelo monta varios cortes con narrativa propia si el prompt da pie a ello; para un plano único hay que decirlo explícito ("in a single unbroken scene") [[1]](https://ai.google.dev/gemini-api/docs/omni).

```
[tipo de plano / movimiento de cámara: "static locked-off" | "slow push in" | "handheld tracking shot"],
[estilo visual: fotorrealista / anime / stop-motion...], [iluminación], en [localización],
[sujeto] [acción con timing: "after 3 seconds...", "[0-3s] ... [3-6s] ..."].
Sound design: [música / ambiente / diálogo entre comillas, o "no dialogue"].
[Si es plano único: "in a single unbroken scene, no scene cuts".]
[Negaciones: "no embellishments", "no extra sound effects".]
```

Longitud ideal: 40-120 palabras para vídeo (el límite técnico es ~20.000 caracteres, pero la densidad útil se agota mucho antes) [[1]](https://ai.google.dev/gemini-api/docs/omni). Idioma óptimo: **inglés**, el único evaluado por el fabricante. Sintaxis especial: tags `<FIRST_FRAME>`, `<LAST_FRAME>`, `<IMAGE_REF_N>`, `<VIDEO_REF_N>` insertados en el texto; bloques de declaración `[# Sources ...]` / `[# References ...]` al inicio del prompt; timecodes `[0-3s] ... [3-6s] ...` para cortes y ritmo; comillas literales para texto en pantalla y para diálogo [[1]](https://ai.google.dev/gemini-api/docs/omni).

## Plantillas por tarea

### Text-to-video
`[plano/cámara] of [sujeto] [acción], in [localización], [iluminación], [estilo]. Sound design: [audio]. [Plano único si aplica.] No [elemento no deseado].`

Cinematográfico: *"Static, locked-off wide shot at golden hour on a dirt road at the edge of a wheat field. Mateo, 17, scuffed boots and a denim jacket, walks a few steps ahead of his younger brother Vidal, who keeps glancing back at the farmhouse. Neither speaks for a moment, then Vidal asks, in a low voice, 'Do you think she'll forgive us?' In a single unbroken scene, no cuts. Sound design: wind through the wheat, distant crows, their footsteps on gravel. No extra sound effects."* [[1]](https://ai.google.dev/gemini-api/docs/omni)

Distinto (acción/producto): *"A marble rolling fast on a chain reaction style track, continuous smooth shot, bright studio lighting, high-key colors. The marble triggers a row of dominoes, then launches off a small ramp into a glass jar with a satisfying 'clink'. Sound design: mechanical clicks, a bright chime on landing. No dialogue."* [[1]](https://ai.google.dev/gemini-api/docs/omni)

Diferencia entre variantes: gen. 1 (`gemini-omni-flash`) acepta la misma prosa pero sin `thinking_level` ni la escalera de resolución de 1.1 — espera menos coherencia en planos largos o con mucho timing [[8]](https://www.atlascloud.ai/models/google/gemini-omni-flash/text-to-video).
No pongas: parámetros tipo `--ar 16:9, 4k` sueltos (van en el esquema, no en el texto); adjetivos sin verbo de acción; dos personajes hablando sin indicar quién dice qué; no indicar si quieres plano único o montaje con cortes.

### Image-to-video
`[qué hacer con la imagen], using the image only as [guía/estilo/punto de partida], [movimiento de cámara], do not [qué no copiar literal].`

No redescribas el aspecto de la imagen: dirige solo movimiento y cámara.

Cinematográfico (sobre una foto fija de los hermanos sentados en un porche): *"Animate this photo with a slow, almost imperceptible push-in on the two brothers, as if the camera itself is holding its breath. Mateo's hand tightens slightly on the armrest. A light breeze moves Vidal's hair. Use the photo only as the starting frame, do not change their faces or clothing. Sound design: creaking porch wood, cicadas at dusk."* [[1]](https://ai.google.dev/gemini-api/docs/omni)

Distinto (ilustración de producto): *"Turn this into realistic footage, using the drawing only as a guide for movement, do not show the drawing in the final video. The sneaker rotates slowly on a reflective pedestal under a single spotlight, laces swaying slightly. Sound design: a soft electronic hum, a camera-shutter click at the end."* [[4]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/image-to-video)

Diferencia entre variantes: solo la gen. 1.1 acepta `last_image` para fijar también el fotograma final (ver Start/end frame); en gen. 1 el `image` de entrada es únicamente punto de partida, sin segundo ancla.
No pongas: una descripción exhaustiva de ropa/cara/entorno que ya está en la imagen (lo ignorará o lo contradirá); instrucciones de encuadre que recorten al sujeto fuera de plano; pedir que "se parezca más" a la imagen sin más detalle, es ambiguo.

### Start/end frame (image-to-video 1.1, variantes `image` + `last_image`)
Dos formas válidas: (a) parámetros `image`/`last_image` + prosa libre describiendo la transición; (b) tags `<FIRST_FRAME>`/`<LAST_FRAME>` dentro del propio texto cuando los roles son obvios.

`[movimiento/transición que une el primer y el último fotograma].` o `<FIRST_FRAME> <LAST_FRAME> [acción que los conecta].`

Cinematográfico: *"A smooth cinematic transition: Mateo and Vidal start the shot standing apart on opposite ends of the farmhouse yard, tense and silent, and end it standing side by side, Mateo's hand on his brother's shoulder, as the sun sets behind them."* (con `image` = primer fotograma, `last_image` = fotograma final) [[1]](https://ai.google.dev/gemini-api/docs/omni)

Distinto (loop de producto): *"`<FIRST_FRAME>` `<LAST_FRAME>` the perfume bottle rotates a full 360 degrees under shifting colored light, returning exactly to its starting position for a seamless loop."* — usar la misma imagen como `image` y `last_image` fuerza un bucle perfecto [[1]](https://ai.google.dev/gemini-api/docs/omni)

Diferencia entre variantes: `last_image` y los tags `<FIRST_FRAME>`/`<LAST_FRAME>` solo existen en gen. 1.1; en gen. 1 no hay forma documentada de anclar el fotograma final.
No pongas: dos escenas sin relación visual entre el primer y el último fotograma (el modelo fuerza una interpolación igualmente, pero queda forzada); instrucciones de cámara contradictorias con la pose final de la imagen de llegada.

### Reference-to-video (incl. multi-referencia)
Declaración opcional al inicio + tags en el cuerpo: `[# References <IMAGE_REF_0>@Image1 <VIDEO_REF_0>@Video1] [sujeto] <IMAGE_REF_0> [acción] with the gait of <VIDEO_REF_0>, in [escena nueva]. Use the given image(s)/video(s) as references, not as literal initial frames.`

Cinematográfico (manteniendo a Mateo y Vidal en una escena nueva a partir de dos fotos de referencia): *"The two brothers shown in `<IMAGE_REF_0>` and `<IMAGE_REF_1>` now stand in a dim kitchen at night, Mateo `<IMAGE_REF_0>` leaning against the counter, Vidal `<IMAGE_REF_1>` pacing. A single bulb flickers overhead. Use the given images as references for appearance only, not as literal initial frames. Sound design: a ticking clock, low tense breathing."* [[5]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/reference-to-video)

Distinto (moda, 6 referencias con timecodes): *"[0-3s] A studio fashion sequence. Starting with woman `<IMAGE_REF_0>`, she is holding `<IMAGE_REF_1>` [3-6s] Then we see the man `<IMAGE_REF_2>` holding `<IMAGE_REF_3>` [6-10s] And finally another woman `<IMAGE_REF_4>` who is holding `<IMAGE_REF_5>` while walking. Use the given images as references, not as literal initial frames."* [[1]](https://ai.google.dev/gemini-api/docs/omni)

Diferencia entre variantes: solo gen. 1.1 acepta `<VIDEO_REF_N>` (≤3 clips de ≤3 s, transfieren movimiento/gesto, audio ignorado); gen. 1 y `-developer` solo admiten imágenes (hasta 7 en `-developer`) [[9]](https://www.atlascloud.ai/models/google/gemini-omni-flash/image-to-video-developer).
No pongas: más de 10 imágenes o 3 clips de referencia; dos vídeos de referencia a la vez pidiendo que el modelo "razone" sobre ambos (degrada el resultado); referencias sin la frase final de rol ("use as references, not as literal initial frames"), puede copiarlas literalmente.

### Video edit
`[cambio breve y concreto]. Keep everything else the same.`

Cinematográfico (editando un vídeo generado de Mateo y Vidal sentados en silencio): *"Add a sudden gust of wind that scatters papers off the table between them. Keep everything else the same."* [[1]](https://ai.google.dev/gemini-api/docs/omni)

Distinto: *"Make this video anime. Keep everything else the same."* / *"Change the text on the sign to say 'Omni Flash'."* [[1]](https://ai.google.dev/gemini-api/docs/omni)

Diferencia entre variantes: la edición multi-turno con `previous_interaction_id` (sin re-subir el vídeo) solo está documentada para gen. 1.1; sobre vídeos subidos la edición está limitada por región y duración de entrada (≤10 s) [[1]](https://ai.google.dev/gemini-api/docs/omni).
No pongas: una re-descripción completa de la escena ("en el vídeo del hombre sentado en el sofá, por favor añade un gato negro que corre desde la derecha..."); varios cambios distintos en una sola instrucción; cambios de diálogo sobre un vídeo subido donde el personaje ya habla (no soportado).

### Video extend
`Extend this video. / Continue the scene: [qué pasa a continuación]. [audio si cambia]. [referencias si se introduce un personaje nuevo].`

Cinematográfico: *"Extend this video. Mateo turns and starts walking back toward the farmhouse while Vidal stays frozen in place, calling his name once. The wind picks up in the audio, drowning out his voice."* [[7]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/video-extend)

Distinto (introduciendo un personaje de referencia a mitad de clip): *"Extend this video: have the character shown in `<IMAGE_REF_0>` enter the scene and wave. The music continues into the chorus."* [[1]](https://ai.google.dev/gemini-api/docs/omni)

Diferencia entre variantes: solo gen. 1.1 encadena extensiones hasta 40 s usando los últimos 10 s del clip previo como contexto; sobre vídeos subidos la extensión no está disponible en EEE/Suiza/Reino Unido y debe durar ≤10 s [[1]](https://ai.google.dev/gemini-api/docs/omni).
No pongas: la escena desde el principio (ya tiene hasta 10 s de contexto previo); una extensión que prepone o inserta contenido en medio (solo se puede añadir al final); diálogo nuevo sobre un vídeo subido donde el personaje ya está hablando.

## Idioma, longitud y prompt negativo

Idioma óptimo: **inglés**, el único evaluado por el fabricante — "English (EN) is fully supported, but other languages have not been evaluated, so they may work but results can vary", lo que incluye español [[1]](https://ai.google.dev/gemini-api/docs/omni). Longitud: hasta ~20.000 caracteres de margen técnico, pero 40-120 palabras bastan para una toma bien dirigida; prompts más largos sirven para escenas de varios cortes con timecodes. Sin `negative_prompt`, `system_instructions`, `temperature`, `top_p` ni `stop sequences`: las restricciones van siempre como negación dentro del propio texto ("no dialogue", "no embellishments", "no extra sound effects") [[1]](https://ai.google.dev/gemini-api/docs/omni). El esquema en vivo de Nexus manda sobre el mapeo real de parámetros expuestos.

## Referencias e imagen inicial

Dos sistemas de etiquetado, documentados por el fabricante [[1]](https://ai.google.dev/gemini-api/docs/omni):

**1. Tags simples** (recomendado para casos claros), insertados directamente en el texto: `<FIRST_FRAME>` (imagen como fotograma inicial, ej. *"`<FIRST_FRAME>` a woman is walking"*), `<LAST_FRAME>` (fotograma final, siempre junto a `<FIRST_FRAME>`), `<IMAGE_REF_N>` (imagen como referencia, 0-indexada, ej. *"in the style of `<IMAGE_REF_0>` a woman `<IMAGE_REF_1>` is walking"* combina estilo de la primera imagen con sujeto de la segunda) y `<VIDEO_REF_N>` (vídeo como referencia de personaje u objeto, 0-indexado, ej. *"the person in `<VIDEO_REF_0>` is playing the violin"*).

**2. Bloque de declaración** al inicio del prompt, para casos con varios inputs y roles: `[# Sources <FIRST_FRAME>@Image1]`, `[# Sources <FIRST_FRAME>@Image1 <LAST_FRAME>@Image2]`, `[# Sources <FIRST_FRAME>@Image1 <LAST_FRAME>@Image1]` (misma imagen como inicio y fin = loop), `[# Sources <VIDEO_0>@Video1]` (vídeo fuente a editar), `[# Sources <PREVIOUS_VIDEO>@Video1]` (vídeo del turno anterior a extender), `[# References <IMAGE_REF_0>@Image1 <VIDEO_REF_0>@Video1]` (combinar imagen y vídeo como referencia). La numeración `@ImageN`/`@VideoN` es en base 1, mientras que `<IMAGE_REF_N>`/`<VIDEO_REF_N>` dentro del texto son en base 0 [[1]](https://ai.google.dev/gemini-api/docs/omni).

Cierra siempre con el rol explícito en lenguaje natural: para fotograma inicial, *"Use this image as the starting frame"*; para loop, *"Use this image as the first frame and the last frame"*; para referencias, *"Use the given image(s) as references for video generation. The images should not be used as literal initial frames"* / *"Use the given video(s) as references. Do not use them as a source for video editing"* [[1]](https://ai.google.dev/gemini-api/docs/omni). Las referencias de vídeo (≤3 clips de ≤3 s en 1.1) transfieren **movimiento y gesto** además de apariencia; su audio se ignora siempre, y no se admite subir referencias de audio sueltas [[1]](https://ai.google.dev/gemini-api/docs/omni).

## Cámara y movimiento

Entiende vocabulario real de videografía: tipo de plano ("wide shot", "close-up"), movimiento ("static locked-off", "slow push in", "handheld tracking shot", "natural smartphone zoom") [[3]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/text-to-video). Para timing y cortes, acepta lenguaje natural ("After 3 seconds, a woman enters the scene", "Every 2s cut to a new frame") y timecodes explícitos: `[0-3s] A person is walking [3-6s] They stop and turn around [6-10s] They start running` [[1]](https://ai.google.dev/gemini-api/docs/omni). Por defecto monta varios planos con narrativa propia; para forzar un plano único usa *"In a single unbroken scene"*, *"In a single continuous shot"* o *"No scene cuts"* [[1]](https://ai.google.dev/gemini-api/docs/omni). Sin parámetro numérico de intensidad de cámara documentado — `camera_control` queda `[sin verificar]`.

## Consistencia de personaje

Garantiza similitud de apariencia al anclar el personaje con `reference_images`/`reference_videos` (hasta 10 imágenes y 3 clips en 1.1): extrae apariencia, textura y estructura hacia la escena nueva [[5]](https://www.atlascloud.ai/models/google/gemini-omni-1.1-flash/reference-to-video). No garantiza identidad estable combinando o razonando sobre varios vídeos a la vez — el fabricante advierte degradación o salidas inesperadas [[1]](https://ai.google.dev/gemini-api/docs/omni). Técnica recomendada: 1-3 imágenes claras del sujeto, un único clip si necesitas gesto/andar, e instrucción explícita de rol ("use as reference, not as literal initial frame").

## Audio y diálogo

Genera audio nativo por defecto; si no se describe, decide uno "apropiado" por su cuenta — no asumas silencio [[1]](https://ai.google.dev/gemini-api/docs/omni). Dirígelo como una pista más del plano: *"Include calm background music"*, *"The video has a high energy techno beat"*, *"The audio is a low tinny radio broadcast in the background, playing a song"* [[1]](https://ai.google.dev/gemini-api/docs/omni). Para diálogo entre personajes, cítalo literal y atribúyelo a quién habla (como en los ejemplos de Mateo y Vidal arriba). El texto en pantalla se dirige igual, citándolo literal: *"There is a street sign that says: 'This is an AI generation by Omni', there is a storefront that says: 'All you need AI', there's a car with the number plate: 'OMNI1.1'"* [[1]](https://ai.google.dev/gemini-api/docs/omni). Generar diálogo nuevo sí funciona al extender vídeos generados en multi-turno; sobre vídeos **subidos** donde ya habla alguien no se puede añadir diálogo nuevo, y "Voice editing is not supported" en ningún caso [[1]](https://ai.google.dev/gemini-api/docs/omni).

## Restricciones e incompatibilidades conocidas

- Editar/extender vídeos **subidos** no disponible en EEE, Suiza y Reino Unido (sí sobre vídeos **generados** por el modelo); tampoco imágenes con menores o ciertas personas reconocibles en esas regiones [[1]](https://ai.google.dev/gemini-api/docs/omni).
- Vídeos de entrada para editar/extender: ≤10 s al subirlos (salvo multi-turno); `video_extend` solo añade al final, nunca al principio o en medio [[1]](https://ai.google.dev/gemini-api/docs/omni).
- No añade diálogo nuevo a un vídeo subido donde alguien ya habla (sí si permanece en silencio, o vía multi-turno); razonar sobre varios vídeos de referencia a la vez puede degradar el resultado [[1]](https://ai.google.dev/gemini-api/docs/omni).
- Sin throughput aprovisionado, sin referencias de audio sueltas, sin vídeos de YouTube como fuente [[1]](https://ai.google.dev/gemini-api/docs/omni).
- Marca SynthID invisible en todo clip, detectable programáticamente; filtros de seguridad por región sobre prompt y salida [[1]](https://ai.google.dev/gemini-api/docs/omni).

## Errores habituales

- Re-describir toda la escena en `video-edit` en vez de nombrar solo el cambio puntual ("Keep everything else the same").
- Olvidar "in a single unbroken scene" para un plano único: el modelo monta varios cortes por defecto si el prompt lo sugiere.
- Omitir la descripción del audio esperando silencio: siempre genera uno "apropiado" por su cuenta si no lo diriges.
- Usar `<IMAGE_REF_N>`/`<VIDEO_REF_N>` sin la frase final de rol ("use as reference, not as literal initial frame"): puede copiar la referencia de forma literal en vez de reinterpretarla.
- Pedir diálogo nuevo sobre un vídeo subido donde el personaje ya habla, o combinar más de un clip de referencia esperando razonamiento conjunto fiable.
- Confundir `last_image`/`<LAST_FRAME>` (ancla el fotograma final) con una simple referencia de estilo: son roles distintos y hay que declarar cuál es cuál.

## Recomendaciones de prompting

- Escribe en inglés y en prosa natural dirigida como una nota de foto fija, no en etiquetas sueltas.
- Especifica plano único si no quieres cortes de escena automáticos.
- Describe el audio (música, ambiente, diálogo citado) como parte del plano; no lo dejes a decisión del modelo.
- En `video-edit`, nombra solo lo que cambia + "keep everything else the same"; prompts muy descriptivos producen cambios no deseados.
- En `video-extend`, describe qué pasa después y si cambia el audio, no la escena completa; usa referencias para introducir personajes nuevos a mitad de clip.
- Usa timecodes `[0-3s] ... [3-6s] ...` cuando necesites ritmo o cortes exactos; lenguaje natural ("after 3 seconds...") funciona igual de bien para timing aproximado.
- Las negaciones van siempre en el prompt como texto ("no dialogue", "no embellishments"): no hay campo `negative_prompt`.
- Itera en 360p y renderiza en 1080p/4K solo la toma ganadora, con `seed` fijo si el esquema lo expone.
- No combines más de 1-3 referencias de vídeo si la identidad del personaje es crítica para la escena.
- Cierra siempre el bloque de referencias con el rol explícito ("not as literal initial frames") para evitar copias literales.

## Fuentes y fecha

Todas consultadas el 2026-10-02.
[1] Gemini Omni Flash — Gemini API — ai.google.dev/gemini-api/docs/omni
[2] Video generation overview, Omni vs. Veo — Gemini API — ai.google.dev/gemini-api/docs/video
[3] Omni 1.1 Flash, Text to Video — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-1.1-flash/text-to-video
[4] Omni 1.1 Flash, Image to Video — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-1.1-flash/image-to-video
[5] Omni 1.1 Flash, Reference to Video — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-1.1-flash/reference-to-video
[6] Omni 1.1 Flash, Video Edit — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-1.1-flash/video-edit
[7] Omni 1.1 Flash, Video Extend — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-1.1-flash/video-extend
[8] Omni Flash, Text to Video — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-flash/text-to-video
[9] Omni Flash, Image to Video Developer — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-flash/image-to-video-developer
[10] Omni Flash, Reference to Video Developer — Atlas Cloud — atlascloud.ai/models/google/gemini-omni-flash/reference-to-video-developer
