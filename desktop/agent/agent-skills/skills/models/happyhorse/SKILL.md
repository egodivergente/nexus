---
id: happyhorse
kind: model
title: 'HappyHorse (Alibaba / Qwen)'
version: 2
updated: 2026-10-02
match: ['^alibaba/happyhorse', '^runway/happyhorse']
modalities: VIDEO
strengths: [identity_preservation, fast, low_cost, high_resolution, prompt_adherence]
weaknesses: [long_duration, native_audio, multi_shot, camera_control]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, video_edit]
avoid_tasks: [motion_control, lipsync, avatar, start_end_frame]
unverified_tags: [natural_motion, physics, dynamic_action, cinematic_look, multi_character]
sources:
  - {url: 'https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt', title: 'HappyHorse 1.1 Text-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt', title: 'HappyHorse 1.1 Image-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt', title: 'HappyHorse 1.1 Reference-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/text-to-video/llms.txt', title: 'HappyHorse 1.0 Text-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/image-to-video/llms.txt', title: 'HappyHorse 1.0 Image-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/reference-to-video/llms.txt', title: 'HappyHorse 1.0 Reference-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt', title: 'HappyHorse 1.0 Video Edit — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video/llms.txt', title: 'Wan 3.0 Text-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-pro/text-to-video/llms.txt', title: 'Kling v3.0 Pro Text-to-Video — API reference', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video/llms.txt', title: 'Seedance 2.5 Text-to-Video — API reference', accessed: 2026-10-02}
---

## Resumen

HappyHorse (Alibaba/Qwen) cubre texto-a-vídeo, imagen-a-vídeo, referencia-a-vídeo y edición de vídeo, en clips de 3-15 s a 720p/1080p [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt). No es un modelo de física o cámara avanzada: es rápido y barato para bocetos, animar artwork y editar vídeo existente. La 1.1 es la generación vigente: mismas tareas que 1.0 salvo edición, a mitad de precio y con más ratios [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt) [[4]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/text-to-video/llms.txt). El vocabulario de prompt que mejor entiende es prosa de una a tres frases con sujeto, acción, entorno y cámara/ánimo; no espera listas de etiquetas ni sintaxis especial [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt).

## Cuándo elegirlo y cuándo no

Elige HappyHorse para iterar rápido y barato: borradores, animar una imagen fija, mantener un sujeto/objeto con 1-9 referencias, o editar un vídeo existente [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt) [[2]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt) [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt) [[7]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt).

No lo elijas si necesitas clips de hasta 30 s (Wan 3.0) [[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video/llms.txt); audio nativo (Seedance 2.5) [[10]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video/llms.txt); storyboard multi-plano por parámetro (Kling v3.0 Pro, hasta 6 planos) [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-pro/text-to-video/llms.txt); o `motion_control`/`lipsync`/`avatar`/control de cámara explícito (ninguna variante los expone).

| Necesidad | Variante recomendada |
|---|---|
| Boceto rápido texto→vídeo, barato | `alibaba/happyhorse-1.1/text-to-video` |
| Animar una imagen fija | `alibaba/happyhorse-1.1/image-to-video` |
| Mantener personaje/objeto/estilo con 1-9 referencias | `alibaba/happyhorse-1.1/reference-to-video` |
| Editar un vídeo existente | `alibaba/happyhorse-1.0/video-edit` (único con esta tarea) |
| Clip largo (>15 s), premium o multi-plano | Wan 3.0 / Kling v3.0 Pro, no HappyHorse |
| Audio nativo generado | Seedance 2.5, no HappyHorse |
| Prompt con control de cámara explícito (dolly, grúa, orbit) | otro modelo con `camera_control` documentado, no HappyHorse |
| Lipsync/avatar hablando a cámara | otro modelo con `lipsync`/`avatar`, no HappyHorse |

## Linaje y variantes en Nexus

- **HappyHorse 1.0**: `alibaba/happyhorse-1.0/{text-to-video,image-to-video,reference-to-video,video-edit}`. 720p/1080p, ratios `16:9 9:16 1:1 4:3 3:4`, referencias hasta 10 MB, precio más alto [[4]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/text-to-video/llms.txt) [[5]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/image-to-video/llms.txt). Única generación con `video-edit` (entrada hasta 60 s, procesa solo los primeros 15 s; `audio_setting` auto/origin) [[7]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt).
- **HappyHorse 1.1** (más reciente, usar por defecto en t2v/i2v/ref): mismas tareas generativas, añade `480p`, más ratios (`4:5 5:4 9:21 21:9`), referencias hasta 20 MB, y aproximadamente mitad de coste por segundo que 1.0 en t2v [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt) [[2]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt) [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt). Todavía sin `video-edit` propio.
- **Ids fuera de Atlas**: `runway/happyhorse_1_0/i2v` y `.../t2v` son el mismo HappyHorse 1.0 vía proveedores Runway API y Crun; úsalos solo como *fallback*, equivalen a 1.0 (más caro, sin 480p ni ratios extra de 1.1).
- No hay tiers "pro/std/fast/turbo/4k" dentro de HappyHorse: la única palanca de calidad/coste es la generación (1.0 vs 1.1) y la resolución elegida por tarea.
- **Diferencias de redacción entre generaciones**: ninguna documentada. 1.0 y 1.1 comparten el mismo esquema de `prompt` (máx. 2500 caracteres, misma guía "describe subject, action, camera feel, and mood") en las tres tareas generativas [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt) [[4]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/text-to-video/llms.txt); la única variante que exige un prompt distinto por naturaleza de tarea es `video-edit` (instrucción de edición, no descripción de escena desde cero) [[7]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt).

## Fortalezas

- **identity_preservation**: `reference-to-video` acepta 1-9 imágenes para preservar sujetos/objetos/elementos visuales [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt).
- **fast**: pensado para "feedback rápido de vídeo" en exploración de ideas [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt).
- **low_cost**: en 1.1, coste por segundo en t2v/i2v/ref por debajo del de Kling v3.0 Pro y Seedance 2.5 [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt) [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-pro/text-to-video/llms.txt) [[10]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video/llms.txt).
- **high_resolution**: salida nativa hasta 1080p en todas las tareas de vídeo [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt).
- **prompt_adherence**: flujo "prompt-first"/"prompt-guided"; el fabricante recomienda sujeto, acción, cámara y ánimo como control principal [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt).

## Debilidades

- **long_duration**: tope de 15 s, frente a 30 s adaptativos de Wan 3.0 [[8]](https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video/llms.txt).
- **native_audio**: ninguna tarea sintetiza audio nuevo; `video-edit` solo conserva/recalcula el audio de origen, a diferencia de Seedance 2.5 [[10]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video/llms.txt).
- **multi_shot**: sin parámetro de storyboard como `multi_shot`/`multi_prompt` de Kling v3.0 Pro; cada generación es un plano continuo [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-pro/text-to-video/llms.txt).
- **camera_control**: el esquema solo expone prompt, resolución, ratio, duración y seed; sin vocabulario de cámara [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt).

## Tareas ideales / Tareas que debe evitar

**Ideales**: `text_to_video`, `image_to_video`, `reference_to_video`, `video_edit` (solo 1.0).

**Evitar**: `motion_control`, `lipsync`, `avatar`, `start_end_frame` (`image-to-video` solo fija el primer fotograma, sin control de fotograma final) [[2]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt).

## Estructura de prompt recomendada

Idioma óptimo: inglés (documentación oficial y guía de uso en inglés; español/chino [sin verificar]). Tono: prosa natural de una a tres frases encadenadas por comas, nunca lista de etiquetas separadas por `|` o `::`. Longitud ideal: 30-80 palabras para t2v/i2v/ref (el límite duro es 2500 caracteres de prompt, pero pasar de ~100 palabras no añade control extra, solo diluye la frase principal) [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt). Sin sintaxis especial documentada: no hay `@referencias`, tags de cámara entre corchetes, `[corte]`, timecodes ni `--parámetros` de Midjourney [sin verificar]; la única forma de referirse a una imagen de referencia es nombrarla en prosa ("the person from image one", "the product shown in the second photo"), porque el modelo recibe el array de imágenes sin metadatos de rol [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt).

Orden recomendado por el fabricante (guía oficial "describe subject, action, camera feel, and mood") [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt):

```
[Sujeto + atributos clave], [acción/movimiento principal en presente],
en/durante [entorno, momento del día], [iluminación/atmósfera/ánimo],
estilo [género visual/referencia de look], cámara [encuadre inicial → movimiento]
```

Para tareas con imagen o referencias, sustituye "sujeto + atributos" por el rol de cada imagen (no redescribas su apariencia) y céntrate en acción, interacción y cámara.

## Plantillas por tarea

### Text-to-video

Plantilla: `[Sujeto + rasgos distintivos], [acción concreta y única], en [lugar/momento], [luz/atmósfera], estilo [género visual], cámara [movimiento/encuadre].`

Ejemplo cinematográfico: "Two brothers, Mateo and Vidal, in worn denim jackets, race their bicycles down a dirt road at golden hour, dust kicking up behind their wheels and dry wind bending the tall grass on either side, warm backlight rimming their silhouettes, shot in a gritty 16mm coming-of-age drama style, camera tracking low and handheld alongside them before pulling back into a wide shot as they crest the hill laughing."

Ejemplo producto/estilizado: "A matte black wireless earbud case rotates slowly on a mirrored obsidian surface, its lid popping open to release a soft cyan glow, droplets of water bead and roll off the curved lid in slow motion, studio softboxes reflecting as clean highlights, minimalist white background, camera orbiting smoothly from a low angle up to a top-down hero shot, crisp commercial advertising look."

Diferencias por variante: ninguna de redacción entre 1.0 y 1.1; la diferencia es de parámetros (1.1 añade `480p` y ratios `4:5 5:4 9:21 21:9`) [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt) [[4]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/text-to-video/llms.txt).

No pongas: duración/resolución/ratio dentro del texto (van en parámetros aparte); más de una acción principal por clip (el modelo no gestiona bien dos arcos narrativos en 3-15 s); diálogo entre comillas esperando sincronía labial (no hay `lipsync`); vocabulario de cámara ultra-técnico (dolly zoom, dutch angle) sin confirmar que el modelo lo reconoce — mejor describir el efecto visual en lenguaje llano.

### Image-to-video

Plantilla: `[Qué hace/cómo se mueve el sujeto que ya aparece en la imagen], [dirección/velocidad/intensidad del movimiento], [qué elementos del fondo se animan], [qué permanece fijo], cámara [estática/leve movimiento, opcional].`

Ejemplo cinematográfico (sobre una imagen ya existente de Mateo y Vidal, sin redescribir su aspecto): "Mateo turns his head slowly toward Vidal and starts laughing, his shoulders shaking gently, loose strands of hair lifting in a light breeze, the wheat field behind them swaying slowly from left to right, dust motes drifting through the sunbeam between the two boys, camera holds a slow, almost imperceptible push-in."

Ejemplo producto (sobre una foto fija del estuche de auriculares): "The earbud case lid lifts open on its own, releasing a soft cyan light that flickers once, a single water droplet rolls down the curved lid and falls off frame, the mirrored surface below ripples faintly from the vibration, camera stays locked off, static framing throughout."

Diferencias por variante: ninguna de redacción; 1.1 acepta imágenes hasta 20 MB frente a 10 MB en 1.0, mismo esquema de prompt opcional en ambas [[2]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt) [[5]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/image-to-video/llms.txt).

No pongas: una redescripción de la ropa/rasgos físicos del sujeto (ya están fijados por la imagen y pueden entrar en conflicto); un sujeto o escenario distinto al de la imagen; petición de fotograma final o `start_end_frame` (la tarea solo fija el primer fotograma) [[2]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt); un prompt largo cuando solo quieres animar la imagen tal cual — el campo es opcional, déjalo vacío o muy breve en ese caso.

### Reference-to-video

Plantilla: `[Nombra el papel de cada imagen: "the person from image one", "the product from image two"], [acción/interacción concreta entre ellas], en [entorno], estilo/cámara [opcional].`

Ejemplo cinematográfico (dos referencias de personaje + un objeto): "The boy from image one, Mateo, walks up to the boy from image two, Vidal, and hands him the wooden toy sailboat shown in image three; they kneel together by the edge of a pond in a park at midday, soft natural light, camera at eye level slowly circling them as the boat is set on the water."

Ejemplo producto (referencia de prenda + referencia de persona): "The sneaker from image one appears on the model's foot from image two as she steps onto a rain-slicked city crosswalk at night, neon reflections rippling across the puddles with every step, camera low and tracking alongside her stride, moody cinematic color grade."

Diferencias por variante: ninguna de redacción; 1.1 admite imágenes hasta 20 MB (lado corto mínimo 400 px) frente a 10 MB en 1.0, mismo límite de 1-9 referencias y mismo requisito de describir el papel de cada una en ambas [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt) [[6]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/reference-to-video/llms.txt).

No pongas: más de 9 imágenes; referencias sin nombrar su papel explícito en el texto (el modelo no sabe qué rol cumple cada imagen si no se lo dices) [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt); mezclar referencias de persona y de estilo/paleta sin aclarar cuál es cuál; esperar que preserve detalles finos de rostro en plano muy cerrado sin fuente que lo confirme [sin verificar].

### Video edit (solo 1.0)

Plantilla: `[Instrucción de edición única y acotada], [qué debe permanecer igual: fondo/iluminación/cámara/audio], [referencias opcionales con su papel], audio_setting [auto/origin].`

Ejemplo cinematográfico: "Change Mateo's gray hoodie to a red leather jacket while he walks down the street with Vidal; keep the background, lighting, camera motion, and audio exactly as in the original video."

Ejemplo producto (con imagen de referencia): "Replace the product on the table with the sneaker shown in the reference image, keeping the same lighting, reflections, and camera pan; set audio_setting to origin so the original ambient sound is preserved."

Diferencias por variante: `video-edit` solo existe en 1.0, no tiene equivalente en 1.1; la redacción es de instrucción de edición ("change X to Y", "replace X with Y"), no de descripción de escena desde cero como en t2v/i2v/ref [[7]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt).

No pongas: varias instrucciones de edición distintas en el mismo prompt (una por generación); nada que dependa de material más allá de los primeros 15 s de un vídeo de hasta 60 s de entrada, porque solo se procesa ese tramo inicial [[7]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt); `audio_setting: auto` cuando quieres conservar el audio original exacto — en ese caso usa `origin`; vídeos que no sean MP4/MOV o superen 100 MB.

## Idioma, longitud y prompt negativo

Inglés confirmado; español/chino [sin verificar]. Límite de 2500 caracteres de prompt [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt). Sin `negative_prompt` documentado (a diferencia de Kling v3.0 Pro) [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-pro/text-to-video/llms.txt); formula restricciones en positivo (en vez de "no niebla", escribe "cielo despejado"). El schema en vivo de Nexus manda si cambia.

## Referencias e imagen inicial

- `image-to-video`: 1 imagen de primer fotograma, mín. 300×300 px, ratio 1:2.5–2.5:1, hasta 20 MB en 1.1 (10 MB en 1.0) [[2]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt).
- `reference-to-video`: 1-9 imágenes, lado corto mín. 400 px, hasta 20 MB en 1.1 (10 MB en 1.0); describe el papel de cada referencia en el prompt [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt).
- `video-edit`: hasta 5 imágenes opcionales de apariencia/estilo, ratio 1:2.5–2.5:1 [[7]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt).

## Cámara y movimiento

Sin parámetros de cámara dedicados; toda indicación de cámara va en el texto del prompt [[1]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt). Describe el efecto deseado en lenguaje llano ("empuje lento hacia el sujeto", "la cámara sigue a la altura del hombro") en vez de jerga técnica no confirmada. Calidad de seguimiento, naturalidad del movimiento y física de objetos no documentadas [sin verificar].

## Consistencia de personaje

`reference-to-video` es la vía recomendada para mantener personas/objetos/props reconocibles (1-9 referencias) [[3]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt); no `image-to-video` (solo anima un fotograma) ni `text-to-video` (sin anclaje visual). Preservación facial fina en primeros planos [sin verificar].

## Restricciones e incompatibilidades conocidas

- Sin `motion_control`, `lipsync` ni `avatar` en ninguna generación; sin `start_end_frame` en `image-to-video` [[2]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt).
- `video-edit` solo procesa los primeros 15 s de un vídeo de hasta 60 s, y solo existe en 1.0 [[7]](https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt).
- Políticas de moderación de contenido no detalladas en las páginas consultadas [sin verificar].

## Errores habituales

- Pedir más de 15 s o fotograma final en `image-to-video`.
- Mandar más de 9 imágenes a `reference-to-video` sin describir el papel de cada una.
- Usar `runway/happyhorse_1_0/*` esperando precio/ratios de 1.1: esos ids son la generación 1.0.
- Pedir audio generado desde cero (voces/música/SFX): solo hay control de conservar/recalcular en `video-edit`.
- Escribir el prompt como lista de tags en lugar de prosa de sujeto/acción/cámara/ánimo.
- Esperar edición de un vídeo largo completo: `video-edit` solo cubre los primeros 15 s.
- Redescribir la apariencia del sujeto de la imagen/referencia en vez de centrarse en movimiento e interacción.

## Recomendaciones de prompting

- Usa 1.1 por defecto en t2v/i2v/ref; cae a 1.0 solo para `video-edit`.
- Describe sujeto, acción, entorno y cámara en 1-3 frases en inglés, en prosa, 30-80 palabras.
- En `reference-to-video`, asigna un papel explícito a cada imagen en el texto ("the person from image one...").
- En `image-to-video`, deja el prompt vacío/breve si solo quieres animar la imagen tal cual; si lo usas, céntrate en movimiento, no en apariencia.
- Itera en `480p` (solo 1.1) y sube a `1080p` para la entrega final.
- Formula restricciones en positivo: no hay `negative_prompt`.
- Si necesitas >15 s, multi-plano o audio nativo, recomienda Wan 3.0, Kling v3.0 Pro o Seedance 2.5 en su lugar.
- En `video-edit`, un cambio por generación, verifica que lo relevante caiga en los primeros 15 s, y usa `audio_setting: origin` si quieres conservar el audio tal cual.

## Fuentes y fecha

[1] HappyHorse 1.1 Text-to-Video — API reference — https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/text-to-video/llms.txt — consultado 2026-10-02
[2] HappyHorse 1.1 Image-to-Video — API reference — https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/image-to-video/llms.txt — consultado 2026-10-02
[3] HappyHorse 1.1 Reference-to-Video — API reference — https://www.atlascloud.ai/models/alibaba/happyhorse-1.1/reference-to-video/llms.txt — consultado 2026-10-02
[4] HappyHorse 1.0 Text-to-Video — API reference — https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/text-to-video/llms.txt — consultado 2026-10-02
[5] HappyHorse 1.0 Image-to-Video — API reference — https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/image-to-video/llms.txt — consultado 2026-10-02
[6] HappyHorse 1.0 Reference-to-Video — API reference — https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/reference-to-video/llms.txt — consultado 2026-10-02
[7] HappyHorse 1.0 Video Edit — API reference — https://www.atlascloud.ai/models/alibaba/happyhorse-1.0/video-edit/llms.txt — consultado 2026-10-02
[8] Wan 3.0 Text-to-Video — API reference — https://www.atlascloud.ai/models/alibaba/wan-3.0/text-to-video/llms.txt — consultado 2026-10-02
[9] Kling v3.0 Pro Text-to-Video — API reference — https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-pro/text-to-video/llms.txt — consultado 2026-10-02
[10] Seedance 2.5 Text-to-Video — API reference — https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video/llms.txt — consultado 2026-10-02
