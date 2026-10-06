---
id: runway
kind: model
title: 'Runway (Gen-4.5/Gen-4/Aleph 2/Act-Two)'
version: 2
updated: 2026-10-02
match:
  - '^runway/(gen4|aleph|act_two|muse_image|ruby|enhance_frame_rate|magnific)'
  - '^crun/runway/'
modalities: [VIDEO, IMAGE]
strengths: [prompt_adherence, physics, cinematic_look, identity_preservation, camera_control, native_audio, subtle_performance]
weaknesses: [long_duration]
ideal_tasks: [text_to_video, image_to_video, video_edit, text_to_image, multi_reference_image, upscale, avatar]
avoid_tasks: [start_end_frame, motion_control, lipsync]
unverified_tags: [multi_character]
sources:
  - url: https://runway.com/research/introducing-runway-gen-4.5
    title: 'Runway Gen-4.5: State-of-the-Art AI Video Generation'
    accessed: 2026-10-02
  - url: https://runwayml.com/research/introducing-runway-gen-4
    title: 'Runway Gen-4: AI Video Generation with World Consistency'
    accessed: 2026-10-02
  - url: https://runway.com/research/introducing-runway-aleph
    title: 'Introducing Runway Aleph'
    accessed: 2026-10-02
  - url: https://runway.com/research/introducing-act-one
    title: 'Introducing Act-One'
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/guides/models/
    title: 'Runway Dev — Models'
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/api/
    title: 'Runway Dev — API Reference'
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/recipes/reference-media/
    title: 'Runway Dev — Reference media guidelines'
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/recipes/multi-shot-video/
    title: 'Runway Dev — Multi-Shot Video recipe'
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/recipes/product-swap/
    title: 'Runway Dev — Product Swap recipe'
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/api-details/moderation/
    title: 'Runway Dev — Content moderation'
    accessed: 2026-10-02
---

## Resumen
Runway es la familia de vídeo/imagen de Runway ML: **Gen-4.5** es el modelo insignia, Nº1 en Artificial Analysis Text-to-Video con 1.247 Elo, por delante de Veo 3, Kling 2.5 y Sora 2 Pro [[1]](https://runway.com/research/introducing-runway-gen-4.5). La acompañan **Gen-4 Turbo** (i2v rápido), **Aleph 2** (edición vídeo-a-vídeo), **Act-Two** (transferencia de actuación), **Gen-4 Image/Turbo** y **Muse Image** (t2i con referencias), y utilidades **Ruby** (SDR→HDR), **Enhance Frame Rate** y **Magnific** (upscalers, sin campo de texto). Nexus enruta por esta API modelos de terceros (`runway/seedance2`, `runway/wan3`, `runway/gpt_image_2`...): los cubre la skill de su propia familia, no esta.

## Cuándo elegirlo y cuándo no
- **Gen-4.5**: mejor adherencia/física con cámara descrita, 2-10 s/llamada; **Gen-4 Turbo**: mismo uso, prioriza velocidad/coste, `promptImage` obligatorio [[6]](https://docs.dev.runwayml.com/api/) [[1]](https://runway.com/research/introducing-runway-gen-4.5).
- **Aleph 2**: edita un vídeo ya existente (objetos/ángulo/estilo/luz) conservando la cámara original — no genera desde cero [[4]](https://runway.com/research/introducing-runway-aleph) [[9]](https://docs.dev.runwayml.com/recipes/product-swap/). **Act-Two**: transfiere una actuación real (expresión/voz) desde vídeo de referencia, no texto [[5]](https://runway.com/research/introducing-act-one).
- **Gen-4 Image/Turbo**: foto realista, hasta 3 refs nombradas (`@tag`); **Muse Image**: hasta 10 refs, prompt más largo [[6]](https://docs.dev.runwayml.com/api/).
- Frente a **Kling**: Kling permite clips nativos más largos; Runway compite en adherencia/física (encadena con Multi-Shot Video). Frente a **Seedance/MiniMax/Wan** (terceros en la misma API, p. ej. `runway/seedance2`): úsalos por coste/velocidad; Runway nativo gana en consistencia de mundo/personaje y edición real. Frente a **Luma**: Luma gana en coherencia de "mundo" en tomas largas; Gen-4.5 gana en adherencia verbatim y en edición (Aleph).
- Evita Runway para **lipsync** por guion puro, **motion control** por coordenadas/motion-brush (solo prosa de cámara) o **start/end frame** (`image_to_video` solo admite primer fotograma) [[6]](https://docs.dev.runwayml.com/api/).

| Necesidad | Variante recomendada |
|---|---|
| Mejor calidad vídeo t2v/i2v | `runway/gen4.5` |
| Vídeo rápido/económico | `runway/gen4_turbo/i2v` |
| Editar vídeo grabado | `runway/aleph2/v2v` |
| Transferir actuación/lipsync real | `runway/act_two/performance` |
| Imagen con 1-3 refs nombradas | `runway/gen4_image` / `/gen4_image_turbo` |
| Imagen con hasta 10 refs o prompt largo | `runway/muse_image/image` |
| Upscale imagen | `runway/magnific_precision_upscaler_v2` |
| Subir resolución de vídeo (≤30 s) / solo framerate (≤300 s) | `.../magnific_video_upscaler_creative` / `.../enhance_frame_rate` |
| SDR→HDR | `runway/ruby/hdr` |

## Linaje y variantes en Nexus
- **Gen-4** (2025): consistencia de personaje/mundo con una sola imagen de referencia, sin fine-tuning; introdujo "coverage" (varios ángulos) [[3]](https://runwayml.com/research/introducing-runway-gen-4). **Gen-4 Turbo** (`runway/gen4_turbo/i2v`): tier rápido/económico derivado, `promptImage` requerido [[6]](https://docs.dev.runwayml.com/api/).
- **Gen-4.5** (`runway/gen4.5`, `/i2v`, `/t2v`; 1 dic. 2025): **sucesor directo, variante de vídeo recomendada por defecto**; Nº1 Artificial Analysis T2V, mejor adherencia/física/consistencia que Gen-4 a coste similar; duración entera de 2-10 s por llamada [[1]](https://runway.com/research/introducing-runway-gen-4.5) [[6]](https://docs.dev.runwayml.com/api/).
- **Aleph** (jul. 2025) → **Aleph 2** (`runway/aleph2/v2v`, vigente): edición de vídeo en contexto; esquema completo en `### Video edit` [[4]](https://runway.com/research/introducing-runway-aleph) [[6]](https://docs.dev.runwayml.com/api/).
- **Act-One** (oct. 2024) → **Act-Two** (`runway/act_two/performance`, vigente): transferencia de actuación; esquema completo en `### Avatar / lipsync` [[5]](https://runway.com/research/introducing-act-one) [[6]](https://docs.dev.runwayml.com/api/).
- **Gen-4 Image** (`/gen4_image/image`, 0-3 refs) y **Gen-4 Image Turbo** (`/gen4_image_turbo/image`, 1-3 refs obligatorias): detalle de ambas y de **Muse Image** (`/muse_image/image`, hasta 10 refs, prompt 4.000 car.) en `### Text-to-image` y `### Multi-referencia` [[6]](https://docs.dev.runwayml.com/api/).
- **Ruby** (`/ruby/hdr`), **Enhance Frame Rate** y **Magnific** (ver `### Upscale`): postproducción pura, sin campo de texto [[6]](https://docs.dev.runwayml.com/api/) [[1]](https://docs.dev.runwayml.com/guides/models/).
- Ids de terceros en la misma API (`runway/seedance2`, `runway/wan3`, `runway/gpt_image_2`...) **no son esta familia**: los documenta la skill de Seedance/Wan/GPT Image.

## Fortalezas
- **Prompt adherence**: Gen-4.5 lidera Artificial Analysis T2V (1.247 Elo) con "precisión física y visual sin precedentes". **Physics**: peso/momento realista, dinámica de líquidos y colisiones creíbles, según ejemplos del fabricante [[1]](https://runway.com/research/introducing-runway-gen-4.5).
- **Cinematic look**: demos oficiales cubren fotorrealismo, estilizado y "slice of life" sobre la misma base [[1]](https://runway.com/research/introducing-runway-gen-4.5). **Identity preservation**: Gen-4 resolvió consistencia de personaje/objeto desde una sola imagen de referencia, en luz/localización distintas, sin reentrenar [[3]](https://runwayml.com/research/introducing-runway-gen-4).
- **Camera control**: los prompts oficiales de Gen-4.5 incluyen cámara explícita ("panning and trucking", "the camera follows") y el modelo la sigue en los ejemplos publicados [[1]](https://runway.com/research/introducing-runway-gen-4.5).
- **Native audio**: Gen-4.5/Aleph 2 generan audio por defecto (`audio: true`) [[8]](https://docs.dev.runwayml.com/recipes/multi-shot-video/) [[9]](https://docs.dev.runwayml.com/recipes/product-swap/). **Subtle performance**: Act-Two/Act-One preservan "eye-lines, micro expressions, pacing and delivery" [[5]](https://runway.com/research/introducing-act-one).

## Debilidades
- **Long duration**: 2-10 s por llamada en `image_to_video` de Gen-4.5, con límites equivalentes en las recetas revisadas (Multi-Shot Video: 5/10/15 s; Product Swap: 4-15 s); para narrativas largas, encadena llamadas [[6]](https://docs.dev.runwayml.com/api/) [[8]](https://docs.dev.runwayml.com/recipes/multi-shot-video/) [[9]](https://docs.dev.runwayml.com/recipes/product-swap/). Gen-4.5 reconoce limitaciones propias de la IA de vídeo: razonamiento causal invertido, permanencia de objeto imperfecta, sesgo de éxito [[1]](https://runway.com/research/introducing-runway-gen-4.5).
- [sin verificar] **multi_character**: Act-One muestra diálogo multi-cámara con dos personajes, pero cada uno se renderiza desde una actuación separada, no interactuando en una sola pasada [[5]](https://runway.com/research/introducing-act-one).

## Tareas ideales / Tareas que debe evitar
Ideal: `text_to_video`/`image_to_video` (Gen-4.5, Gen-4 Turbo); `video_edit` (Aleph 2); `text_to_image` (Gen-4 Image, Muse Image); `multi_reference_image`; `upscale` (Magnific, sin texto); `avatar` (Act-Two).
Evitar: `start_end_frame` — solo primer fotograma [[6]](https://docs.dev.runwayml.com/api/); `motion_control` por trayectoria/zona — no existe ese parámetro; `lipsync` por guion puro — Act-Two necesita vídeo-performance real [[6]](https://docs.dev.runwayml.com/api/).

## Estructura de prompt recomendada
Idioma óptimo: **inglés** (todos los ejemplos del fabricante y de la API están en inglés; calidad en español sin confirmar [sin verificar]). Tono: prosa natural de director de fotografía, nunca listas de keywords sueltas estilo etiqueta [[1]](https://runway.com/research/introducing-runway-gen-4.5). Longitud: vídeo 1-4 frases (≈15-60 palabras) con cámara/luz/acción explícitas; imagen 1-2 frases (≈15-40 palabras) con composición y luz. Sintaxis especial: `@tag` para referencias nombradas en Gen-4 Image/Muse Image; sin `[corte]` ni timecodes dentro de `promptText` (los timecodes van en `keyframes.at` de Aleph 2, fuera del texto); sin flags `--parámetro` de Midjourney [sin verificar, no documentados].

Plantilla general (vídeo, Gen-4.5/Gen-4 Turbo):
```
[sujeto(s): quién/qué, rasgo distintivo breve] + [acción concreta en curso] +
[entorno y calidad de luz] + [cámara: tipo de plano + verbo de movimiento explícito] +
[estilo cinematográfico/formato: 35mm, documental, comercial...] +
[detalle físico o emocional que ancla el realismo]
```
Ejemplo oficial del fabricante: "Camera is panning and trucking across a kitchen table with different objects placed on the counter. The light is low, it comes through a window, casting shadows and dappled light" [[1]](https://runway.com/research/introducing-runway-gen-4.5).

La plantilla de imagen y la de cada tarea concreta están en `## Plantillas por tarea`: Aleph 2 describe solo el *cambio*; Act-Two no tiene `promptText`; Magnific/Enhance Frame Rate no tienen ningún campo de texto.

## Plantillas por tarea

### Text-to-video (Gen-4.5, Gen-4 Turbo)
Plantilla:
```
[sujeto(s): quién, rasgo visual breve] + [acción concreta] + [entorno y luz] +
[cámara: tipo de plano + verbo de movimiento] + [estilo cinematográfico] + [detalle físico/emocional]
```
Ejemplo 1 (cinematográfico, personajes):
"Two brothers, Mateo and Vidal, sit on a wooden dock at dawn, mist rising off the lake behind them. Mateo skips a stone across the water while Vidal watches, hugging his knees. The camera is low and static at water level, then slowly trucks in as the sun breaks through the fog. Soft golden light, 35mm film grain, quiet handheld documentary feel, their breath visible in the cold morning air."

Ejemplo 2 (producto/estilizado):
"A chrome perfume bottle spins slowly on a mirrored black pedestal under a single spotlight. Droplets of condensation form and slide down the glass in slow motion. The camera orbits 180 degrees around the bottle, pulling back to reveal rose petals scattering across the surface in a sudden gust of wind. Hyper-glossy studio lighting, high-contrast commercial look, crisp reflections throughout."

Diferencias por variante: Gen-4 Turbo usa la misma sintaxis de `promptText`, pero prioriza velocidad/coste; [sin verificar] tolera peor que Gen-4.5 varias acciones simultáneas no relacionadas en la misma frase — si usas Turbo, describe una acción principal por toma.

No pongas: listas de keywords sueltas separadas por comas en vez de prosa; más de una acción principal no relacionada por toma; instrucciones de "fotograma final" (no existe ese control, ver Image-to-video); texto en pantalla esperando render tipográfico perfecto (no es un rasgo verificado de esta familia).

### Image-to-video (Gen-4.5, Gen-4 Turbo)
Plantilla — describe solo **movimiento**, nunca la apariencia que ya está en la imagen:
```
[qué hace(n) el/los sujeto(s) de la imagen a partir de ahora] + [cómo se mueve la cámara] +
[qué cambia de luz/atmósfera durante el plano] + [ritmo/tono del movimiento]
```
Ejemplo 1 (cinematográfico; la imagen ya muestra a Mateo y Vidal sentados en el muelle):
"Mateo turns his head toward Vidal and starts speaking, his hand gesturing toward the lake. Vidal nods slowly and smiles back at him. The camera trucks in subtly from a static start as mist drifts across the frame and the morning light intensifies behind them. Natural, unhurried pacing, like a quiet conversation caught in a single continuous take."

Ejemplo 2 (producto; la imagen ya muestra una zapatilla sobre una mesa):
"The sneaker rotates slowly as unseen hands lace it tight, pulling the laces taut in one fluid motion. The camera arcs around from a low three-quarter angle to a frontal hero shot as the light sweeps across the material to reveal its texture. Energetic but smooth motion, commercial pacing, no hard cuts."

Diferencias por variante: `promptImage` es **obligatorio** en Gen-4 Turbo (no opcional como podría parecer en otros flujos) [[6]](https://docs.dev.runwayml.com/api/); en ambas variantes el array `PromptImages` admite exactamente 1 elemento con `position: "first"` — no hay segundo elemento para fotograma final [[6]](https://docs.dev.runwayml.com/api/).

No pongas: redescribir el aspecto físico de personajes/objetos ya visibles en la imagen (desperdicia presupuesto de `promptText` y confunde al modelo); pedir un "fotograma final" distinto — solo se admite el primero; usar la imagen de referencia como mero "mood board" de estilo en vez de contenido inicial real; pedir cambios de identidad del sujeto (eso es edición — ver Video edit, no `image_to_video`).

### Motion control (sin parámetro nativo — Gen-4.5, Gen-4 Turbo)
Runway no expone motion-brush ni coordenadas de trayectoria en los endpoints de vídeo revisados: todo el control de movimiento se logra con verbos de cámara explícitos dentro de `promptText` [[6]](https://docs.dev.runwayml.com/api/) [[1]](https://runway.com/research/introducing-runway-gen-4.5). Plantilla:
```
[sujeto/punto de partida del encuadre] + ["the camera" + verbo explícito: pans/trucks/dollies/orbits/arcs/tilts/cranes] +
[punto de inicio → punto final del movimiento] + [velocidad: slowly/quickly/smoothly] + [qué revela el movimiento al terminar]
```
Ejemplo 1 (cinematográfico, personajes):
"Mateo stands at the edge of the dock. The camera starts in a tight close-up on his face, then slowly dollies backward and cranes upward to reveal Vidal rowing a small boat into frame, with the full lake and misty mountains spreading out behind them. Smooth, deliberate movement, like a crane shot opening up a scene, soft diffused morning light throughout."

Ejemplo 2 (acción/producto):
"A race car blurs past, camera left to right, tires screeching. The camera pans hard to track it, then whips into a fast 360-degree orbit around the car as it skids to a stop in a cloud of dust, settling into a low, static hero angle. Fast, kinetic motion with motion blur, high-energy commercial editing rhythm."

Diferencias por variante: ninguna documentada en el parámetro (no existe un control dedicado en ningún tier); [sin verificar] Gen-4 Turbo sigue mejor un único verbo de cámara por toma que combinaciones encadenadas (pan + crane + orbit en la misma frase).

No pongas: coordenadas de píxeles, zonas de pantalla o "motion brush" por regiones — no existe ese parámetro [[6]](https://docs.dev.runwayml.com/api/); trayectorias con puntos de control numéricos o splines; un asset externo de "camera path": todo el control pasa por texto natural en inglés.

### Video edit (Aleph 2)
Plantilla:
```
promptText: "[verbo de edición: add/remove/replace/transform] + [elemento objetivo concreto] + [resultado deseado de luz/estilo/objeto]. [segundo cambio si aplica]. Keep [qué debe permanecer igual: movimiento de cámara, ritmo, posición]."
keyframes (opcional, hasta 5): [{ uri: "...", at: segundos }]
```
Ejemplo 1 (cinematográfico; vídeo de entrada = Mateo y Vidal caminando por un parque a mediodía):
"Change the time of day from midday to a dramatic golden-hour sunset. Add warm orange rim light along both brothers' silhouettes as they keep walking. Replace the wooden bench they pass with a weathered stone fountain, and add soft falling autumn leaves drifting through the frame. Keep their walking pace and the camera's slow tracking motion exactly as in the original."

Ejemplo 2 (producto/estilizado; vídeo de entrada = producto girando sobre fondo blanco de estudio):
"Replace the plain white studio background with a neon-lit cyberpunk alley, add rain streaks falling across the frame, and change the product's material from matte plastic to glossy chrome. Keep the camera's slow orbit and the product's exact position unchanged — only transform the environment and lighting around it."

Diferencias por variante: solo existe la variante `aleph2`; `promptText` es opcional (hasta 1.000 car.) — si se omite, el cambio se guía solo por `keyframes` [[6]](https://docs.dev.runwayml.com/api/).

No pongas: una redescripción completa de la escena original (gasta presupuesto de texto y confunde la edición con una generación nueva); un personaje/objeto totalmente ajeno sin relación con el vídeo de entrada (es edición, no `text_to_video`); `keyframes` con `at` fuera de la duración real del vídeo de entrada; expectativa de que cambie el movimiento de cámara original — Aleph 2 lo conserva [[4]](https://runway.com/research/introducing-runway-aleph).

### Avatar / lipsync (Act-Two)
No existe `promptText`: el control es 100% por media. Plantilla conceptual (lo que el asistente debe pedir/mapear a parámetros, no un texto libre):
```
character: { type: "image"|"video", uri: "[imagen o vídeo con la cara reconocible del personaje]" }
reference: { type: "video", uri: "[vídeo de 3-30 s con la actuación/voz real a transferir]" }
ratio: "[relación de aspecto de salida]"
```
Ejemplo 1 (cinematográfico — descripción en lenguaje natural de qué media combinar):
"Use the portrait photo of Vidal as the character, keeping his static kitchen background. Apply the performance from reference-performance.mp4, where an actor delivers an anxious monologue — raising his eyebrows and glancing nervously toward the door, speaking in a hushed, urgent tone. Vidal's expression, head turns, and voice should mirror that reference performance exactly, in a 16:9 frame."

Ejemplo 2 (comercial/mascota):
"Use the full-body video of the mascot costume character as the character input, so it keeps its own body movements in its original animated environment. Apply the performance from announcer-reference.mp4: energetic hand gestures, a wide grin, and an enthusiastic, fast-paced delivery. Voice and timing should stay synced exactly to the reference clip, start to finish."

Diferencias por variante: solo existe `act_two`; si `character` es imagen, el personaje queda en su entorno estático original; si es vídeo, conserva además parte de su propio movimiento mientras adopta la actuación de `reference` [[6]](https://docs.dev.runwayml.com/api/).

No pongas: diálogo o emoción escritos como si existiera un `promptText` (ese campo no existe aquí) [[6]](https://docs.dev.runwayml.com/api/); un vídeo de referencia de menos de 3 s o más de 30 s (fuera del rango documentado del parámetro `reference`); expectativa de sincronía labial a partir de guion puro sin vídeo-performance real.

### Text-to-image (Gen-4 Image, Gen-4 Image Turbo, Muse Image)
Plantilla:
```
"[composición/encuadre] + [sujeto(s) con detalle visual concreto] + [luz/atmósfera] + [estilo fotográfico o artístico]"
```
Ejemplo 1 (cinematográfico, personajes, sin referencias):
"A medium shot of two brothers, Mateo and Vidal, sitting on a weathered wooden dock at sunrise, mist rising off a calm lake behind them. Mateo wears a faded denim jacket, Vidal a grey wool sweater. Soft golden backlight, shallow depth of field, 35mm film photography look, warm nostalgic tone."

Ejemplo 2 (producto/estilizado):
"A minimalist still life of a single ceramic coffee cup on a marble countertop, steam curling upward, morning light streaming through a nearby window casting long soft shadows. Clean negative space, muted earth tones, editorial product photography style, ultra-sharp focus."

Diferencias por variante: Gen-4 Image admite 0-3 `referenceImages`; Gen-4 Image Turbo exige mínimo 1 referencia (no genera "en blanco") [[6]](https://docs.dev.runwayml.com/api/); Muse Image acepta `promptText` hasta 4.000 car. (4x el límite de Gen-4 Image) y, si recibe `referenceImages` (hasta 10), "edita y combina" esas imágenes según el prompt en vez de generar solo desde texto [[6]](https://docs.dev.runwayml.com/api/).

No pongas: texto renderizado en pantalla esperando precisión tipográfica perfecta sin fuente que confirme ese rasgo en esta familia; más de 3 referencias en Gen-4 Image/Turbo (usa Muse Image); una descripción sin estilo fotográfico explícito esperando un look pictórico por defecto.

### Multi-referencia (Gen-4 Image, Muse Image)
Plantilla:
```
"[composición que describe la relación entre referencias] con @tag1 y @tag2 [acción/relación entre ambos], [luz/estilo]"
referenceImages: [{ uri: "...", tag: "tag1" }, { uri: "...", tag: "tag2" }, ...]
```
Ejemplo 1 (cinematográfico — combinar foto de Mateo + foto de Vidal en una escena nueva):
"@mateo and @vidal stand side by side on a rooftop at dusk, the city skyline glittering behind them. @mateo has his arm around @vidal's shoulder and both are laughing. Warm neon light from below mixes with the deep blue dusk sky, cinematic color grade, shot on a 50mm lens."

Ejemplo 2 (producto — producto + textura de referencia):
"Place @product_a, the chrome watch, on top of @texture_b, the cracked marble surface, lit by a single hard spotlight from the upper left. Sharp reflections, high-contrast studio photography, deep black background surrounding both elements."

Diferencias por variante: Gen-4 Image/Turbo limitan a 3 refs etiquetadas (`tag` de 3-16 caracteres, letras/números/guion bajo); Muse Image sube el tope a 10 refs y permite prompts mucho más largos para describir relaciones complejas entre ellas [[6]](https://docs.dev.runwayml.com/api/).

No pongas: referencias sin `tag` o con tags ambiguos/repetidos; más de 3 refs en Gen-4 Image/Turbo (usa Muse Image); imágenes de baja resolución o con varios sujetos ya encuadrados juntos — degradan la fidelidad de cada referencia individual [[7]](https://docs.dev.runwayml.com/recipes/reference-media/).

### Upscale (Magnific, Enhance Frame Rate)
Ninguno de estos tres endpoints tiene campo de texto: solo el asset de entrada y parámetros numéricos [[6]](https://docs.dev.runwayml.com/api/). Plantilla (parámetros, no prompt):
```
magnific_precision_upscaler_v2: { imageUri, scaleFactor: [2|4|8|16], sharpen: 0-100 }
magnific_video_upscaler_creative: { videoUri (<=30 s), resolution: [720p..4k], creativity: 0 (fiel) .. alto (mas detalle generado) }
enhance_frame_rate: { videoUri (<=300 s), targetFramerate }
```
Ejemplo 1 (imagen — instrucción en lenguaje natural que el asistente traduce a parámetros): "Upscale this product photo 4x (`scaleFactor: 4`) and apply moderate sharpening (`sharpen` ≈ 20) to recover crisp edges without over-smoothing the surface."

Ejemplo 2 (vídeo — ídem): "Upscale this 1080p interview clip to 4k with `magnific_video_upscaler_creative`, `creativity` baja (≈15) para mantener la toma fiel al original; el clip dura 22 s, dentro del límite de 30 s."

Diferencias por variante: `magnific_precision_upscaler_v2` opera por imagen con `scaleFactor` discreto (2/4/8/16) y `sharpen`; `magnific_video_upscaler_creative` opera por vídeo (máx. 30 s) con `resolution` y un knob `creativity` (0 = fiel al original, valores altos = más detalle generado por IA); `enhance_frame_rate` solo cambia el framerate (sin tocar resolución), hasta 300 s [[6]](https://docs.dev.runwayml.com/api/).

No pongas: una descripción de escena esperando que cambie el contenido — ninguno de estos tres acepta `promptText` [[6]](https://docs.dev.runwayml.com/api/); un vídeo de más de 30 s en `magnific_video_upscaler_creative` (usa `enhance_frame_rate` si solo necesitas framerate, o corta el clip); expectativa de que el upscaler corrija errores de composición o encuadre — solo mejora resolución/nitidez/detalle sobre lo ya grabado.

## Idioma, longitud y prompt negativo
Inglés. `promptText` máx. 1.000 car. en Gen-4.5/Gen-4 Image/Turbo, 4.000 en Muse Image, 1.000 opcional en Aleph 2; Act-Two y los endpoints de upscale/HDR no tienen `promptText` [[6]](https://docs.dev.runwayml.com/api/). Sin `negative_prompt` documentado: expresa restricciones en positivo (p. ej. "clean background, no text overlays" en vez de "sin texto"); el esquema en vivo de Nexus manda si cambia.

## Referencias e imagen inicial
`promptImage` en vídeo solo admite **un** fotograma inicial, fijo (`position: "first"`) [[6]](https://docs.dev.runwayml.com/api/); detalle de tope de referencias por variante de imagen en `### Multi-referencia`. Buenas prácticas comunes: máxima resolución, sujeto aislado, luz uniforme, una imagen por sujeto salvo que busques varios ángulos del mismo [[7]](https://docs.dev.runwayml.com/recipes/reference-media/).

## Cámara y movimiento
Gen-4.5 sigue instrucciones de cámara explícitas en prosa ("panning and trucking", "orbiting", "handheld documentary style") [[1]](https://runway.com/research/introducing-runway-gen-4.5); el vocabulario y las limitaciones completas están en `### Motion control` dentro de `## Plantillas por tarea`.

## Consistencia de personaje
Gen-4 resolvió consistencia de personaje/objeto desde una sola imagen de referencia, en luz/localización distintas, sin fine-tuning [[3]](https://runwayml.com/research/introducing-runway-gen-4). Gen-4 Image/Muse Image lo extienden a imagen fija vía `@tag` reutilizado (`### Multi-referencia`). Act-Two no preserva identidad así: la define el `character` de entrada, no el modelo [[6]](https://docs.dev.runwayml.com/api/).

## Audio y diálogo
Las recetas sobre Gen-4.5/Aleph 2 generan audio por defecto (`audio: true`); sin sintaxis documentada de diálogo/SFX en `promptText` [sin verificar] [[8]](https://docs.dev.runwayml.com/recipes/multi-shot-video/) [[9]](https://docs.dev.runwayml.com/recipes/product-swap/). Act-Two transfiere voz solo si `reference` (3-30 s) la incluye [[5]](https://runway.com/research/introducing-act-one).

## Restricciones e incompatibilidades conocidas
Moderación activa por defecto ("auto") sobre imagen y texto; una generación moderada cuesta créditos igual que una exitosa y suma al límite que puede suspender la cuenta; `contentModeration.publicFigureThreshold: "low"` relaja la restricción sobre figuras públicas reconocibles [[10]](https://docs.dev.runwayml.com/api-details/moderation/) [[6]](https://docs.dev.runwayml.com/api/). Aleph 2 no entrega HDR nativo (encadena Ruby, SDR, máx. 30 s, <4.096 px/lado) [[1]](https://docs.dev.runwayml.com/guides/models/).

## Errores habituales
- Pedir un clip único de 30-60 s en una sola llamada (límite 2-10 s/llamada); usa Multi-Shot Video o encadena llamadas [[6]](https://docs.dev.runwayml.com/api/) [[8]](https://docs.dev.runwayml.com/recipes/multi-shot-video/).
- Aportar "fotograma final" a `image_to_video` o coordenadas de "motion brush": ninguno existe en el esquema revisado [[6]](https://docs.dev.runwayml.com/api/).
- Describir la emoción en texto en Act-Two o esperar `promptText` en Magnific/Enhance Frame Rate: ninguno acepta texto [[6]](https://docs.dev.runwayml.com/api/).
- Pasar más de 3 refs a Gen-4 Image/Turbo; usa Muse Image [[6]](https://docs.dev.runwayml.com/api/).

## Recomendaciones de prompting
- Inglés, prosa de director de fotografía: sujeto+acción, entorno/luz, cámara explícita, estilo.
- Fija una imagen "canónica" por personaje recurrente y reutilízala (mismo `@tag`) en todas las tomas.
- En `image_to_video`, describe solo el movimiento nuevo: la apariencia ya está fijada por la imagen.
- En Aleph 2, describe solo la edición deseada y cierra con "Keep [lo que no debe cambiar]".
- Para cámara, usa un verbo explícito por toma ("the camera trucks/orbits/dollies...") sin encadenar varios, sobre todo en Gen-4 Turbo.
- Para narrativas de más de ~10 s, usa Multi-Shot Video o encadena llamadas en vez de forzar un clip largo.
- No esperes fotograma final, control de movimiento por trayectoria ni `promptText` en los upscalers: fuera de alcance documentado.

## Fuentes y fecha
Todas consultadas y verificadas (HTTP 200) el 2026-10-02.
[1] runway.com/research/introducing-runway-gen-4.5 · [1b] docs.dev.runwayml.com/guides/models/ · [3] runwayml.com/research/introducing-runway-gen-4 · [4] runway.com/research/introducing-runway-aleph · [5] runway.com/research/introducing-act-one · [6] docs.dev.runwayml.com/api/ (esquemas de todos los modelos citados) · [7] docs.dev.runwayml.com/recipes/reference-media/ · [8] docs.dev.runwayml.com/recipes/multi-shot-video/ · [9] docs.dev.runwayml.com/recipes/product-swap/ · [10] docs.dev.runwayml.com/api-details/moderation/.
