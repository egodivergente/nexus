---
id: 'wan'
kind: 'model'
title: 'Wan (Alibaba)'
version: 2
updated: '2026-10-02'
match: ['^alibaba/wan', '^atlascloud/wan', '^atlascloud/van', '^crun/wan', '^runway/wan']
modalities: [video, image]
strengths: [native_audio, multi_shot, identity_preservation, long_duration, high_resolution, physics, fast]
weaknesses: [stylized, text_rendering]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, text_to_image, image_edit, multi_reference_image, avatar, lipsync]
avoid_tasks: [upscale, layer_decomposition]
unverified_tags: [stylized, text_rendering]
sources:
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-mix', title: 'Atlas Cloud — Wan 2.2 Animate Mix', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-move', title: 'Atlas Cloud — Wan 2.2 Animate Move', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video', title: 'Atlas Cloud — Wan 2.5 model card', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video', title: 'Atlas Cloud — Wan 2.6 Image-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.7/text-to-video', title: 'Atlas Cloud — Wan 2.7 Text-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.7/reference-to-video', title: 'Atlas Cloud — Wan 2.7 Reference-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.7-pro/text-to-image', title: 'Atlas Cloud — Wan 2.7 Pro Text-to-Image', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video', title: 'Atlas Cloud — Wan 3.0 Text-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video', title: 'Atlas Cloud — Wan 3.0 Reference-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/atlascloud/van-2.6/text-to-video', title: 'Atlas Cloud — Van 2.6 (AtlasCloud)', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/atlascloud/wan-2.2-turbo/infinite-image-to-video', title: 'Atlas Cloud — Wan 2.2 Turbo Infinite I2V', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/atlascloud/wan-2.2/image-to-video', title: 'Atlas Cloud — Wan 2.2 Image-to-Video', accessed: '2026-10-02'}
  - {url: 'https://wan.video/', title: 'Wan AI — sitio oficial', accessed: '2026-10-02'}
  - {url: 'https://arxiv.org/abs/2503.20314', title: 'Wan: Open and Advanced Large-Scale Video Generative Models (arXiv)', accessed: '2026-10-02'}
  - {url: 'https://docs.dev.runwayml.com/guides/models/', title: 'Runway Dev — Available AI Models (wan3, wan3_prime)', accessed: '2026-10-02'}
  - {url: 'https://crun.ai/models', title: 'Crun — AI API Models (Wan 3.0 Video)', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.5/video-extend', title: 'Atlas Cloud — Wan 2.5 Video Extend', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.5/image-edit', title: 'Atlas Cloud — Wan 2.5 Image Edit', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-2.7/video-edit', title: 'Atlas Cloud — Wan 2.7 Video-edit', accessed: '2026-10-02'}
---

## Resumen

Wan es la familia de vídeo (y, desde 2.5, imagen) de Alibaba, con audio nativo sincronizado y, en su gama alta (3.0/3.0 Prime), clips de hasta 30 s combinando referencias de imagen+vídeo+audio [[13]](https://wan.video/)[[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video). Nace como MoE open-source (2.2, 27B/14B activos) [[14]](https://arxiv.org/abs/2503.20314) y evoluciona a un "todo en uno" con audio, edición, extensión de clips y referencia multimodal de personajes. Llega a Nexus por el catálogo Alibaba/AtlasCloud y por los proxies Runway Dev y Crun (`wan3`/`wan3_prime`).

## Cuándo elegirlo y cuándo no

- Wan para **audio nativo** junto al vídeo en un paso [[4]](https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video).
- **3.0/3.0 Prime** si mezclas imagen+vídeo+audio de referencia con identidad a nivel píxel, o partes de un doc/web (`enable_thinking`+`file`/`link`) [[9]](https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video).
- **2.6/2.7** para narrativa multi-plano a coste medio y `video-edit` [[19]](https://www.atlascloud.ai/models/alibaba/wan-2.7/video-edit); **Animate Mix/Move** (2.2) transfiere movimiento desde un vídeo a una imagen [[1]](https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-mix); **Turbo/Infinite** para iterar barato [[11]](https://www.atlascloud.ai/models/atlascloud/wan-2.2-turbo/infinite-image-to-video).
- Mejor **Kling** (cámara milimétrica), **Seedance** (estilizado/anime [sin verificar]), **MiniMax** (precio mínimo sin audio), **HappyHorse** (clips ultracortos baratos).
- No confundas **Van** (AtlasCloud) con Wan: modelo propio de AtlasCloud, no de Alibaba [[10]](https://www.atlascloud.ai/models/atlascloud/van-2.6/text-to-video).

| Necesidad | Variante recomendada |
|---|---|
| Audio+voz sincronizados en un paso | `wan-2.6/image-to-video` |
| Referencia multimodal pixel-perfect (imagen+vídeo+audio) | `wan-3.0-prime/reference-to-video` |
| Generar vídeo a partir de un brief/doc/web | `wan-3.0/reference-to-video` (`enable_thinking`+`file`/`link`) |
| Narrativa larga (30 s) | `wan-3.0/text-to-video` |
| Narrativa multi-plano a coste medio | `wan-2.7/text-to-video` |
| Transferencia de movimiento a un personaje | `wan-2.2/animate-mix` o `-move` |
| Iteración rápida/barata | `wan-2.2-turbo/image-to-video` |
| Editar un vídeo ya existente por texto | `wan-2.7/video-edit` |
| Alargar un vídeo ya generado | `wan-2.5/video-extend` |
| Editar una imagen por texto | `wan-2.5/image-edit` |
| Imagen 4K solo texto | `wan-2.7-pro/text-to-image` |
| Vía agregador externo | `runway/wan3/t2v`, `crun/wan-3.0` |

## Linaje y variantes en Nexus

1. **Wan 2.2** — fundacional, MoE 27B/14B activos [[14]](https://arxiv.org/abs/2503.20314). `image-to-video`(+`-lora`): 480p nativo/720p vía VSR, 3–10 s [[12]](https://www.atlascloud.ai/models/atlascloud/wan-2.2/image-to-video). **Animate** (`-mix`/`-move`) transfiere movimiento/identidad imagen+vídeo, modos `std`/`pro` [[1]](https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-mix). **Turbo/Infinite**: sampler RCM de 4 pasos, duración casi ilimitada [[11]](https://www.atlascloud.ai/models/atlascloud/wan-2.2-turbo/infinite-image-to-video).
2. **Wan 2.5** (24 sept. 2025) — primera con A/V nativo unificado, hasta 1080p/10 s [[3]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video). Variantes: `text-to-image`, `t2v`/`i2v`(`-fast`), **`video-extend`** (`video`+`audio`+`prompt`+`negative_prompt` para continuar un clip) [[17]](https://www.atlascloud.ai/models/alibaba/wan-2.5/video-extend), **`image-edit`** (array `images`+`prompt`+`negative_prompt`+`size`) [[18]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-edit).
3. **Wan 2.6** — más económico, A/V sync en un paso, multilingüe, hasta 15 s, multi-plano con auto-división [[4]](https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video). Incluye `-flash` y `video-to-video`.
4. **Wan 2.7/2.7 Pro** — `text-to-video` añade narrativa multi-plano ("shot a shot") y audio automático, 2–15 s [[5]](https://www.atlascloud.ai/models/alibaba/wan-2.7/text-to-video). `reference-to-video` admite hasta 5 referencias imagen+vídeo y clonación de voz [[6]](https://www.atlascloud.ai/models/alibaba/wan-2.7/reference-to-video). `video-edit` edita un vídeo por texto con imagen(es) y audio guía opcionales (alias `first_frame`, `audio_url`) [[19]](https://www.atlascloud.ai/models/alibaba/wan-2.7/video-edit). **Pro** (solo `text-to-image`) permite 4K, con `color_palette` y `thinking_mode` propios [[7]](https://www.atlascloud.ai/models/alibaba/wan-2.7-pro/text-to-image).
5. **Wan 3.0/3.0 Prime** (ago. 2026, la más reciente) — hasta 30 s en una pasada, duración inteligente (`duration=-1`), audio activado por defecto y desactivable sin coste, y renderizado de UI/texto en pantalla con fidelidad píxel según el fabricante; **no soporta `negative_prompt`** [[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video). `reference-to-video` unifica referencias en un array `refers` (imagen/vídeo/audio con `type`), hasta 10 imágenes/5 vídeos/5 audios, con `enable_thinking` para partir de un documento (`file`) o web (`link`, excluyentes) en vez de solo texto [[9]](https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video). **Prime** iguala capacidades con generación más rápida [[16]](https://crun.ai/models). Recomiéndala por defecto salvo prioridad de coste.
6. **Van (AtlasCloud)** — `van-2.5/2.6`: prestaciones similares (A/V sync, multilingüe, multi-plano), pero no es Alibaba [[10]](https://www.atlascloud.ai/models/atlascloud/van-2.6/text-to-video).
7. **Terceros** — `runway/wan3/*`, `wan3_prime/*` sirven Wan 3.0/Prime vía Runway Dev [[15]](https://docs.dev.runwayml.com/guides/models/); `crun/wan-3.0` vía Crun, 1080p, T2V/I2V/R2V [[16]](https://crun.ai/models). Paridad de parámetros frente al endpoint nativo sin confirmar [sin verificar].

## Fortalezas

- **native_audio** — voz, SFX y lip-sync desde 2.5 [[3]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video).
- **multi_shot** — 2.6/2.7/3.0 generan varios planos coherentes; 2.7 lo controla con bloques "Shot N" en el prompt [[5]](https://www.atlascloud.ai/models/alibaba/wan-2.7/text-to-video).
- **identity_preservation** — reference-to-video mantiene sujetos/props/voces "a nivel píxel", incluso mezclando imagen+vídeo+audio en 3.0 [[9]](https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video).
- **long_duration** — 3.0/3.0 Prime llegan a 30 s con duración inteligente (`-1`); Turbo Infinite sin límite fijo [[11]](https://www.atlascloud.ai/models/atlascloud/wan-2.2-turbo/infinite-image-to-video).
- **high_resolution** — 1080p en 2.5–3.0, 4K en texto-a-imagen Pro [[7]](https://www.atlascloud.ai/models/alibaba/wan-2.7-pro/text-to-image).
- **physics** — simulación de dinámica física realista, según fabricante [[16]](https://crun.ai/models).
- **fast** — Turbo y Prime priorizan baja latencia manteniendo calidad [[11]](https://www.atlascloud.ai/models/atlascloud/wan-2.2-turbo/infinite-image-to-video).

## Debilidades

- **stylized** [sin verificar] — énfasis en estética fotorrealista; sin evidencia de buen desempeño en anime/ilustración marcada (excepción: el modo "Studio Ghibli-inspired" de Wan 2.5 [[3]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video)).
- **text_rendering** [sin verificar] en la mayoría de variantes — Wan 3.0 es la excepción: anuncia renderizado de UI/texto en pantalla "con fidelidad píxel", sin evidencia equivalente en 2.2–2.7 [[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video).
- Tiers flagship (3.0, 3.0 Prime, 2.7 Pro) cuestan más por segundo que Turbo/Van: no son la opción barata.
- Animate Mix/Move exige rostro único visible y encuadre/resolución en rango [[2]](https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-move); Wan 3.0 T2V no admite `negative_prompt` (ver más abajo).

## Tareas ideales / Tareas que debe evitar

**Ideales:** text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, text_to_image, image_edit, multi_reference_image, avatar, lipsync.
**Evitar:** upscale (VSR es solo paso intermedio), layer_decomposition (sin capas explícitas).

## Estructura de prompt recomendada

Prosa natural en inglés. Orden y plantilla general:

```
[Sujeto(s) con rasgo distintivo] [acción/verbo de movimiento]
  en [lugar/entorno], [hora/luz].
Camera: [movimiento/ángulo/distancia].
Style: [referencia cinematográfica/estética, grano, paleta].
Dialogue: "[línea exacta por hablante]" (si hay voz).
Audio: [ambiente/SFX/música] (si el endpoint genera audio).
```

- **Longitud**: 40–120 palabras en vídeo (15–40 en Turbo/`-fast`/`-flash`, una sola acción); 20–80 en imagen.
- **Idioma**: inglés es el más documentado; 2.6+ procesa chino de forma fiable incluso con audio [[4]](https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video); español no documentado [sin verificar].
- **Sintaxis especial**: comillas para diálogo literal por hablante (ejemplo del fabricante: "He whispers softly: 'No matter what happens...'" [[3]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video)); `Shot 1: ... Shot 2: ...` para multi-plano en 2.7/3.0; en reference-to-video nombra cada referencia por su rol ("reference image 1", "the voice in the audio reference"); en 3.0 R2V antepone "Using the attached brief/page..." al usar `file`/`link` con `enable_thinking`.

## Plantillas por tarea

### Text-to-video

Plantilla: `[Sujeto+rasgo], [acción], en [lugar], [luz/hora]. Camera: [movimiento]. Style: [referencia]. Dialogue: "[línea]". Audio: [ambiente/SFX].` Multi-plano (2.7/3.0): antepone `Shot 1: ... Shot 2: ...`.

Ejemplo cinematográfico (multi-plano, 2.7/3.0):
"Shot 1: Mateo, in a worn denim jacket, sprints across a wet rooftop in Bogotá at dusk, neon signs reflecting in puddles, camera low-angle tracking, handheld. Shot 2: his brother Vidal watches from a fire escape and calls out, 'Mateo, espera!' — camera pushes in on his worried face. Style: gritty 35mm, teal-and-orange grade. Audio: traffic, rain, Vidal's echoing voice."

Ejemplo producto/estilizado:
"A wireless earbud case spins slowly on a mirrored black pedestal as studio softbox light rakes across brushed aluminum. Camera: smooth 360-degree orbit ending in a macro push-in on the hinge. Style: minimalist product commercial, high contrast, no people. Audio: a futuristic synth swell, a crisp click as the lid snaps open."

Diferencias: 2.2/2.5 no tienen auto-división de planos (1-2 acciones máx.); 2.7/2.6 leen bloques "Shot N" como planos con transición automática; 3.0 añade `duration=-1` y renderiza texto/UI en pantalla, sin `negative_prompt`.

No pongas:
- Más de 2-3 planos en 2.2/2.5 (no tienen auto-división; se fusionan en una sola acción confusa).
- `negative_prompt` esperando que funcione en Wan 3.0 (no soportado; usa frases positivas).
### Image-to-video

Plantilla: `Starting from the provided image: [sujeto] [acción/movimiento], [dirección/velocidad]. Camera: [movimiento]. Audio: [sonido a añadir] (si aplica).` No redescribas composición, ropa o fondo ya visibles.

Ejemplo cinematográfico (continuación de una foto de los dos hermanos en un porche):
"Starting from the provided image: Vidal slowly turns his head toward Mateo and gives a tired half-smile, his shoulders relaxing as the tension leaves his posture. A light breeze moves his hair and the loose fabric of his shirt. Camera: slow handheld push-in settling on a medium close-up. Audio: wind through the trees, distant cicadas, a soft exhale."

Ejemplo producto (foto de un reloj sobre una mesa):
"Starting from the provided image: the watch's second hand begins ticking steadily, catching a glint of light with every pass. The camera slowly orbits ten degrees to reveal the strap's stitched texture. Audio: a faint, satisfying mechanical tick."

Diferencias: Turbo/Infinite premia prompts cortos (15–40 palabras) centrados en una sola acción; 2.5+ puede describir también el audio porque lo sintetiza; 2.2 base no genera audio.

No pongas:
- Redescripción de composición/ropa/fondo ya presentes en la imagen subida.
- Petición de audio en Wan 2.2 base (no lo genera).
### Reference-to-video

Plantilla: `[Escena nombrando cada referencia por rol: "the character from reference image 1" / "the voice in the audio reference"], [acción], [cámara]. Keep [rasgo] consistent.` En 3.0 con `file`/`link`+`enable_thinking`, antepone "Using the attached brief/page...".

Ejemplo cinematográfico (dos referencias de imagen):
"Using the boy from reference image 1 (Mateo, red cap) and the young man from reference image 2 (Vidal, denim jacket), show them running side by side through a crowded market at golden hour, laughing as they dodge fruit stalls. Camera: tracking shot at eye level. Keep both faces and outfits identical to their reference images."

Ejemplo producto (referencia de imagen + referencia de audio, voz clonada):
"Using the sneaker from reference image 1 and the voice from the audio reference, show it rotating on a dark pedestal while the cloned voice says: 'Built for the streets, made to last.' Camera: slow dolly around the product. Keep the sneaker's color and logo identical to the reference."

Diferencias: 2.7 admite hasta 5 referencias imagen+vídeo y clonación de voz, sin lectura de documentos; 3.0 unifica todo en `refers` (hasta 10 imágenes/5 vídeos/5 audios) y puede partir de un documento o web (`file`/`link`, excluyentes).

No pongas:
- Más sujetos referenciados de los que luego nombras explícitamente en la acción.
- Expectativa de consistencia de voz "a nivel píxel" en 2.7 — esa garantía es propia de 3.0.
### Start/end frame

Plantilla: `First frame: [descripción]. Last frame: [descripción]. Transition: [movimiento que conecta ambos extremos].`

Ejemplo cinematográfico:
"First frame: Mateo stands alone at the bus stop in the rain, suitcase at his feet. Last frame: Vidal's car pulls up beside him, the door swinging open. Transition: rain intensifies as headlights sweep across Mateo's face; camera holds a steady medium shot throughout."

Ejemplo producto:
"First frame: a cracked ceramic mug sits chipped on a wooden shelf. Last frame: the same mug, intact, glowing with warm tea steam. Transition: a slow dissolve as morning light crosses the shelf."

Diferencias: en 3.0 el relleno de movimiento usa duración inteligente y queda fluido sin especificar velocidad; en 2.2/2.5 describe la velocidad del tránsito para evitar cortes abruptos.

No pongas:
- Un sujeto que cambia de identidad entre el primer y el último fotograma (debe ser el mismo personaje/objeto).
- Omisión de la transición esperando que el modelo la invente sin pistas.
### Motion control (Animate Mix/Move)

Plantilla: `Character image: [persona única, de frente, encuadre medio]. Reference video: [acción/movimiento a transferir]. Mode: [std|pro].` No describas la apariencia; céntrate en el movimiento a copiar.

Ejemplo cinematográfico:
"Character image: Vidal, front-facing, medium shot, plain gray background. Reference video: a street dancer performing a slow body-wave that flows into a spin. Mode: pro."

Ejemplo estilizado (mascota de producto):
"Character image: a plush mascot character, front-facing, centered, white background. Reference video: a person waving both arms overhead in celebration before taking a bow. Mode: std."

Diferencias: `std` prioriza velocidad y tolera imágenes imperfectas; `pro` exige encuadre/resolución estrictos pero preserva mejor rasgos finos del rostro [[2]](https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-move).

No pongas:
- Imágenes con el rostro oculto, recortado o en perfil extremo.
- Más de una persona visible en la imagen de personaje.
### Video edit

Plantilla: `Edit the provided video: [cambio solicitado]. Reference (opcional): [imagen/audio guía]. Keep [elemento] unchanged.`

Ejemplo cinematográfico:
"Edit the provided video: replace Mateo's gray hoodie with a red leather jacket. Keep his face, hair, movement, and the rainy alley background exactly as in the source video, same cold blue lighting."

Ejemplo producto/estilizado:
"Edit the provided video: apply a hand-painted watercolor style to the entire clip. Keep the product's shape, logo, and motion identical to the source; only the rendering style changes."

Diferencias: 2.6 ofrece edición genérica vídeo-a-vídeo; 2.7 añade `negative_prompt`, imagen(es) de referencia (alias `first_frame`) y audio guía (`audio_url`) [[19]](https://www.atlascloud.ai/models/alibaba/wan-2.7/video-edit).

No pongas:
- Cambios de guion o duración del clip (eso es edición de contenido, no de vídeo).
- Expectativa de que cambie el audio original si no referencias explícitamente un audio guía.
### Video extend

Plantilla: `Extend the provided video: [qué ocurre a continuación]. Keep [tono/personajes/entorno] consistent with the source clip.`

Ejemplo cinematográfico:
"Extend the provided video: after Vidal catches up to Mateo, they slow to a walk, breathing heavily, and Vidal puts a hand on his brother's shoulder without a word. Keep the same rainy alley, lighting, and wardrobe as the source clip."

Ejemplo producto:
"Extend the provided video: the product keeps rotating a few more seconds, then dissolves into a logo card with the tagline fading in. Keep the same lighting and camera distance as the source clip."

Diferencias: `video-extend` es un endpoint propio de 2.5 con `negative_prompt` y `audio` opcional [[17]](https://www.atlascloud.ai/models/alibaba/wan-2.5/video-extend); confirma si otras generaciones ofrecen el mismo endpoint o solo video-edit genérico.

No pongas:
- Cambios de localización o vestuario entre el clip original y la extensión.
- Un giro de cámara brusco que rompa la continuidad del plano.
### Avatar / lipsync

Plantilla: `[Personaje o "the provided photo"] speaking: "[línea exacta]". Tone: [emoción]. Audio: [estilo de voz/ambiente]. Camera: [estático/sutil].`

Ejemplo cinematográfico (diálogo entre hermanos, basado en el patrón de ejemplo del fabricante):
"A young man sitting on a park bench during sunset. His younger brother Mateo leans his head on his shoulder. Vidal whispers softly: 'No matter what happens, I've got you.' Camera: static medium shot, warm golden-hour light. Audio: gentle wind and a soft ambient score under the dialogue, lips matching the line precisely." [[3]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video)

Ejemplo avatar de marca:
"A middle-aged presenter at a wooden desk in a cozy study, speaking directly to camera: 'This is the moment everything changes.' His expression and lip movement match the line precisely. Audio: warm room tone, no music."

Diferencias: en 2.5 el lip-sync depende del diálogo exacto entre comillas (o un audio de referencia); 2.6 añade estabilidad multi-hablante y multilingüe [[4]](https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video).

No pongas:
- Más de 2-3 hablantes sin diferenciarlos claramente por nombre en el prompt.
- Diálogo indirecto ("dice algo sobre...") en vez de la cita literal entre comillas.
### Text-to-image

Plantilla: `[Sujeto], [composición/encuadre], [iluminación/material], [estilo], [paleta de color].`

Ejemplo cinematográfico:
"Mateo and Vidal, two brothers in worn street clothes, sitting back to back on a rooftop ledge at sunset, Bogotá's skyline hazy behind them, warm backlight, cinematic 35mm photograph, shallow depth of field."

Ejemplo producto estilizado:
"A minimalist frosted-glass perfume bottle floating above still black water, a single dramatic spotlight from above, high-contrast studio photography, teal-and-gold palette."

Diferencias: `wan-2.7-pro/text-to-image` añade `color_palette` (gama tonal como parámetro) y `thinking_mode` (razonamiento previo) [[7]](https://www.atlascloud.ai/models/alibaba/wan-2.7-pro/text-to-image); el tier estándar depende solo del prompt.

No pongas:
- Expectativa de tipografía fiable dentro de la imagen (sin confirmar fuera del vídeo de Wan 3.0).
- Más de un estilo artístico mezclado sin indicar cuál domina.
### Image edit

Plantilla: `Edit the image: [cambio]. Preserve [elemento]. Reference images (opcional): [rol de cada una].` `images` acepta un array: puedes combinar varias imágenes de entrada.

Ejemplo cinematográfico:
"Edit the image: change Vidal's denim jacket to a worn leather bomber jacket. Preserve his face, pose, and rooftop background exactly as in the original."

Ejemplo producto:
"Edit the image: remove the price tag from the sneaker and replace the background with a plain studio-white backdrop. Preserve its shape, color, and cast shadow."

Diferencias: `wan-2.5/image-edit` admite `negative_prompt` y `size` junto al array `images` [[18]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-edit); confírmalo con el schema en otras generaciones.

No pongas:
- Ediciones de texto/tipografía dentro de la imagen (fiabilidad no confirmada).
- Más imágenes de entrada que las que luego nombras en la instrucción.
### Multi-referencia

Plantilla: `[Escena] featuring [sujeto de ref. 1], [sujeto de ref. 2] y [prop de ref. 3], [acción que los relaciona espacialmente]. Keep each identical to its reference.`

Ejemplo cinematográfico:
"A rooftop reunion featuring Mateo (reference image 1) and Vidal (reference image 2) hugging under string lights, while the dog from reference image 3 circles excitedly at their feet. Keep each character's face and clothing identical to its reference."

Ejemplo producto (flat-lay):
"A product flat-lay featuring the sneaker (ref. 1), the backpack (ref. 2), and the watch (ref. 3) arranged on a marble surface under soft overhead light. Keep each product's color and shape identical to its reference."

Diferencias: 2.7 combina hasta 5 referencias imagen+vídeo; 3.0 sube a 10 imágenes/5 vídeos/5 audios en `refers`, con mejor consistencia espacial [[9]](https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video).

No pongas:
- Más referencias que el máximo del endpoint — confírmalo con el schema antes de enviarlas.
- Relaciones espaciales implícitas sin describirlas ("a la izquierda de", "detrás de"): el modelo no las asume.
## Idioma, longitud y prompt negativo

Inglés es el idioma mejor documentado; 2.6+ procesa chino de forma fiable incluso con audio [[4]](https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video); español no documentado [sin verificar]. Longitud: 1–3 frases en imagen/edición, 2–5 en vídeo. `negative_prompt` existe en 2.5 (i2v, video-extend, image-edit) y 2.7 (t2v, r2v, video-edit) [[3]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video)[[6]](https://www.atlascloud.ai/models/alibaba/wan-2.7/reference-to-video), pero **no en Wan 3.0 text-to-video** — formula las restricciones como instrucciones positivas [[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video). Verifica siempre con `get_model_schema`.

## Referencias e imagen inicial

En i2v, sube una imagen nítida con composición y luz definitivas; el modelo anima movimiento, no reinventa apariencia. En reference-to-video, nombra cada referencia por su función ("the character reference", "the voice in the audio reference"); en 3.0 el array `refers` acepta `type` explícito (`image`/`video`/`audio`) o lo infiere de la URL [[9]](https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video). En Animate Mix/Move, la imagen debe mostrar una sola persona de frente, rostro visible, 200–4096 px [[2]](https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-move).

## Cámara y movimiento

Vocabulario en inglés: push in/out, pan, tilt, tracking shot, dolly, locked-off, handheld, orbit. 2.7/3.0 controlan "camera work" por prompt, con relleno de movimiento entre primer/último fotograma más fluido en 3.0 por la duración inteligente [[5]](https://www.atlascloud.ai/models/alibaba/wan-2.7/text-to-video). Intensidad de cámara en escenas multi-personaje no cuantificada [sin verificar]; para coreografías exigentes, compara con Kling.

## Consistencia de personaje

Reference-to-video (2.7+/3.0) ofrece la mayor consistencia: preservación a nivel píxel de rostro, ropa y voz, incluso con varios sujetos a la vez [[9]](https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video). Fuera de ese modo, la identidad se apoya en el multi-shot nativo, sin garantía entre generaciones separadas.

## Audio y diálogo

Desde 2.5, el audio (voz, SFX, lip-sync) se genera nativamente junto al vídeo, con diálogo citado entre comillas y una etiqueta explícita de "lip-sync with audio" en los ejemplos del fabricante [[3]](https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video). 2.6 añade diálogo multi-hablante estable y multilingüe [[4]](https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video). 3.0 permite desactivar audio sin coste extra pero no admite afinarlo con `negative_prompt` [[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video).

## Restricciones e incompatibilidades conocidas

- Animate Mix/Move: vídeo de referencia 2–30 s, una sola persona de frente [[1]](https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-mix).
- Pro habilita 4K solo en texto-a-imagen puro; con imagen de entrada, 1K/2K [[7]](https://www.atlascloud.ai/models/alibaba/wan-2.7-pro/text-to-image).
- Wan 3.0 T2V sin `negative_prompt`; Wan 3.0 R2V exige `file`/`link` mutuamente excluyentes, solo activos con `enable_thinking` [[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video)[[9]](https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video).
- Moderación, límites exactos y disponibilidad regional dependen del contrato: confirma con `get_model_schema`.
- Rutas de terceros pueden no exponer todos los parámetros avanzados del endpoint nativo [sin verificar].

## Errores habituales

- Redescribir la imagen de entrada en i2v en vez del movimiento.
- Pedir reference-to-video con más sujetos de los que el prompt nombra.
- Usar imágenes con rostro oculto o recortado en Animate Mix/Move.
- Confundir Van (AtlasCloud) con Wan (Alibaba).
- Pedir 4K en flujos con imagen de entrada en el tier Pro.
- Esperar `negative_prompt` en Wan 3.0 text-to-video, donde no existe.
- Esperar edición de capas o upscaling dedicado.

## Recomendaciones de prompting

- Diálogo entre comillas por hablante; SFX/ambiente aparte.
- En i2v, céntrate en movimiento/cámara/tiempo, no en la imagen ya dada.
- En reference-to-video, nombra cada referencia por su rol para evitar mezclas; en 3.0 aprovecha `file`/`link` si partes de un brief o una web.
- Usa `pro`/`Prime` si la calidad final importa más que la velocidad; Turbo/`-fast`/`-flash` para iterar con prompts cortos.
- En narrativas largas, describe escena por escena con bloques "Shot N" en 2.7/3.0.
- No prometas duraciones/resoluciones exactas ni `negative_prompt` sin confirmar con `get_model_schema`.
- Recomienda por defecto la generación más reciente (3.0/3.0 Prime) salvo prioridad de coste.
- Para edición o extensión de vídeo, indica explícitamente qué debe permanecer igual, no solo qué cambia.

## Fuentes y fecha

(Todas las fuentes consultadas el 2026-10-02.)

[1] AC — Wan 2.2 Animate Mix — https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-mix
[2] AC — Wan 2.2 Animate Move — https://www.atlascloud.ai/models/alibaba/wan-2.2/animate-move
[3] AC — Wan 2.5 — https://www.atlascloud.ai/models/alibaba/wan-2.5/image-to-video
[4] AC — Wan 2.6 I2V — https://www.atlascloud.ai/models/alibaba/wan-2.6/image-to-video
[5] AC — Wan 2.7 T2V — https://www.atlascloud.ai/models/alibaba/wan-2.7/text-to-video
[6] AC — Wan 2.7 R2V — https://www.atlascloud.ai/models/alibaba/wan-2.7/reference-to-video
[7] AC — Wan 2.7 Pro T2I — https://www.atlascloud.ai/models/alibaba/wan-2.7-pro/text-to-image
[8] AC — Wan 3.0 T2V — https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video
[9] AC — Wan 3.0 R2V — https://www.atlascloud.ai/models/alibaba/wan-3.0/reference-to-video
[10] AC — Van 2.6 (AtlasCloud) — https://www.atlascloud.ai/models/atlascloud/van-2.6/text-to-video
[11] AC — Wan 2.2 Turbo Infinite I2V — https://www.atlascloud.ai/models/atlascloud/wan-2.2-turbo/infinite-image-to-video
[12] AC — Wan 2.2 I2V — https://www.atlascloud.ai/models/atlascloud/wan-2.2/image-to-video
[13] Wan AI oficial — https://wan.video/
[14] Wan arXiv paper — https://arxiv.org/abs/2503.20314
[15] Runway Dev Models (wan3, wan3_prime) — https://docs.dev.runwayml.com/guides/models/
[16] Crun Models (Wan 3.0) — https://crun.ai/models
[17] AC — Wan 2.5 Video Extend — https://www.atlascloud.ai/models/alibaba/wan-2.5/video-extend
[18] AC — Wan 2.5 Image Edit — https://www.atlascloud.ai/models/alibaba/wan-2.5/image-edit
[19] AC — Wan 2.7 Video-edit — https://www.atlascloud.ai/models/alibaba/wan-2.7/video-edit
