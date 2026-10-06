---
id: 'luma'
kind: 'model'
title: 'Luma (Ray 3.2 / Uni-1 / Uni-1 Max)'
version: 2
updated: '2026-10-02'
match: ['^luma/']
modalities: 'VIDEO,IMAGE'
strengths: [prompt_adherence, text_rendering, photorealism, cinematic_look, camera_control, high_resolution, stylized]
weaknesses: [face_consistency, multi_character, physics, long_duration]
ideal_tasks: [text_to_video, image_to_video, start_end_frame, video_edit, video_extend, reference_to_video, text_to_image, image_edit, multi_reference_image]
avoid_tasks: [lipsync, avatar]
unverified_tags: [identity_preservation]
sources:
  - {url: 'https://docs.agents.lumalabs.ai/guides/model/', title: 'Luma Agents API - Models', accessed: '2026-10-02'}
  - {url: 'https://docs.agents.lumalabs.ai/guides/videos/', title: 'Luma Agents API - Videos overview', accessed: '2026-10-02'}
  - {url: 'https://docs.agents.lumalabs.ai/guides/videos/generation/', title: 'Luma Agents API - Video generation', accessed: '2026-10-02'}
  - {url: 'https://docs.agents.lumalabs.ai/guides/videos/editing/', title: 'Luma Agents API - Video editing', accessed: '2026-10-02'}
  - {url: 'https://docs.agents.lumalabs.ai/guides/videos/reframing/', title: 'Luma Agents API - Video reframing', accessed: '2026-10-02'}
  - {url: 'https://docs.agents.lumalabs.ai/guides/images/prompt-guide/', title: 'Luma Agents API - Prompting guide (Uni-1)', accessed: '2026-10-02'}
  - {url: 'https://docs.agents.lumalabs.ai/guides/faq/', title: 'Luma Agents API - FAQ', accessed: '2026-10-02'}
  - {url: 'https://docs.lumalabs.ai/docs/video-generation.md', title: 'Luma Dream Machine API (legacy Ray 2) - Video Generation', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/luma/ray-3.2/text-to-video', title: 'Atlas Cloud - Luma Ray 3.2 Text-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/luma/ray-3.2/image-to-video', title: 'Atlas Cloud - Luma Ray 3.2 Image-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/luma/uni-1-max/text-to-image', title: 'Atlas Cloud - Luma Uni-1 Max Text-to-Image', accessed: '2026-10-02'}
---

## Resumen

Luma es la familia de Luma AI: **Ray 3.2** para vídeo (texto/imagen a vídeo, edición vídeo-a-vídeo, extensión y reencuadre) y **Uni-1 / Uni-1 Max** para imagen (texto a imagen y edición). Destaca por seguir el prompt con precisión cinematográfica — "razona" la escena en vez de pattern-matching de keywords — y por un pipeline de edición de vídeo con control fino por `keyframes` y señales (pose/depth/normals/trayectoria/cara). No es la familia más fuerte en consistencia facial, multi-personaje ni física de contacto compleja.

## Cuándo elegirlo y cuándo no

- **Ray 3.2**: prosa de cámara fiel [[1]](https://docs.agents.lumalabs.ai/guides/model/), reencuadrar sin regenerar (`video_reframe`, poco común) [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/), editar vídeo anclando el look por keyframes/señales [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).
- Mejor **Runway** para consistencia de personaje/producto entre tomas [sin verificar]; mejor **Kling** para física de contacto o varios personajes — no documentado en Ray 3.2.
- Uni-1 Max > Uni-1 para prompts exigentes: mismo prompt, Max solo sube calidad/precio [[1]](https://docs.agents.lumalabs.ai/guides/model/). Mejor **GPT Image** para edición conversacional [sin verificar]; mejor **Nano Banana** para iterar rápido [sin verificar].
- Ray 3.2 no genera audio [[9]](https://www.atlascloud.ai/models/luma/ray-3.2/text-to-video); usa otra familia para audio/lipsync.

| Necesidad | Variante recomendada |
|---|---|
| Vídeo desde texto, control de cámara en prosa | `luma/ray-3.2/text-to-video` |
| Animar imagen con transición a un frame final | `luma/ray-3.2/image-to-video` (`start_frame`/`end_frame`) |
| Coreografiar movimiento en varios puntos del clip | `luma/ray-3.2/image-to-video` con `video.keyframes`/`keyframe_indexes` |
| Continuar o prefijar un clip ya generado | `luma/ray-3.2/video-extend` (`generation_id` en `start_frame`/`end_frame`) |
| Cambiar el look de un vídeo grabado conservando movimiento | `luma/ray-3.2/video-to-video` |
| Cambiar aspect ratio sin tocar el contenido | `luma/ray-3.2/reframe` |
| Imagen desde texto, uso general | `luma/uni-1/text-to-image` |
| Imagen desde texto, máxima fidelidad/composición | `luma/uni-1-max/text-to-image` |
| Editar imagen con instrucción de texto, un cambio quirúrgico | `luma/uni-1/edit` o `luma/uni-1-max/edit` |
| Combinar estilo + personaje + composición de varias imágenes | `luma/uni-1/text-to-image` con `image_ref` multi-referencia |

## Linaje y variantes en Nexus

La "Dream Machine API" (Ray 1 → Ray 2 → Ray 2 Flash → Ray 3 → Ray 3 Reference/Refiner) dio paso a la **Luma Agents API** actual, que unifica imagen y vídeo bajo `/v1/generations`, con migración oficial hacia **Ray 3.2** [[2]](https://docs.agents.lumalabs.ai/guides/videos/). Ray 3.2 es la única generación de vídeo en Nexus; no recomiendes "Ray 2".

Mapa de ids (sintaxis exacta en "Plantillas por tarea"):
- `luma/ray-3.2/text-to-video` — `type:"video"` sin frames ancla.
- `luma/ray-3.2/image-to-video` — `type:"video"` con `start_frame`/`end_frame` o `keyframes`+`keyframe_indexes` [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- `luma/ray-3.2/video-extend` — `type:"video"` con `generation_id` previo como ancla [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- `luma/ray-3.2/video-to-video` — `type:"video_edit"`, conditioning por `auto_controls`/`strength`/`controls` [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).
- `luma/ray-3.2/reframe` — `type:"video_reframe"` [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/).
- `luma/uni-1/text-to-image` y `luma/uni-1/edit` — modelo de imagen por defecto [[1]](https://docs.agents.lumalabs.ai/guides/model/).
- `luma/uni-1-max/text-to-image` y `luma/uni-1-max/edit` — mismo contrato, mayor calidad y precio [[1]](https://docs.agents.lumalabs.ai/guides/model/).

Sin tiers "pro/std/fast/4k": Ray 3.2 diferencia por tarea; Uni-1/Max solo en calidad/precio sobre el mismo prompt [[1]](https://docs.agents.lumalabs.ai/guides/model/).

## Fortalezas

- **prompt_adherence**: "razona" el prompt en vez de pattern-matching de keywords, con estructuras complejas (sujeto, escena, cámara, luz) [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).
- **text_rendering**: Uni-1 renderiza texto legible en carteles/UI; cadenas cortas (<20 caracteres) más fiables [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- **cinematic_look**/**camera_control**: responde bien a dolly, paneo, aéreo en prosa [[9]](https://www.atlascloud.ai/models/luma/ray-3.2/text-to-video) [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- **high_resolution**: vídeo hasta 1080p con HDR opcional; imagen lista para impresión/editorial [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- **photorealism**/**stylized**: casos en producto y documental, con estilos culturales (manga, ukiyo-e, cine negro, 76+ estilos) [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).

## Debilidades

- **face_consistency**: sin seed público ni reproducibilidad determinista; mantener una cara depende de reusar `image_ref`, sin garantía estricta [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- **multi_character**: flujo recomendado usa una sola referencia, no multi-personaje robusto [sin verificar].
- **physics**: sin simulación física avanzada declarada [sin verificar].
- **long_duration**: tope 5s/10s por generación; sin clip largo nativo salvo `video_extend` encadenado [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/) [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/).

## Tareas ideales / Tareas que debe evitar

**Ideales**: text_to_video, image_to_video, start_end_frame, video_edit, video_extend, reference_to_video, text_to_image, image_edit, multi_reference_image (9 refs creación, 8 edición) [[1]](https://docs.agents.lumalabs.ai/guides/model/) [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).

**Evitar**: lipsync y avatar — sin endpoint ni voz; vídeo mudo [[9]](https://www.atlascloud.ai/models/luma/ray-3.2/text-to-video).

## Estructura de prompt recomendada

Inglés es el idioma documentado en los ejemplos oficiales; español [sin verificar], mejor evitarlo en prompts exigentes. Tono: prosa de brief de dirección, nunca "sopa de keywords" (`8k, ultra detailed, masterpiece`) — desaconsejado explícitamente [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/). Instrucción clave al principio: el modelo pondera más lo que lee primero [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).

Longitudes (Uni-1/Max): texto-a-imagen 80–250 palabras; multi-referencia 100–300; edición 30–100, quirúrgico [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/). Vídeo admite 1–6.000 caracteres, pero conviene 40–120 palabras centradas en sujeto, acción, cámara, luz y ritmo [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

Sin `negative_prompt`: describe en positivo o pide preservar ("keep everything else the same") [[7]](https://docs.agents.lumalabs.ai/guides/faq/). Sin `--parámetros` de Midjourney ni tags entre corchetes (`[dolly_in]`) — existían en la legacy Ray 2 y no se traducen a Ray 3.2 [[8]](https://docs.lumalabs.ai/docs/video-generation.md). Texto en imagen entre comillas con la cadena exacta.

Plantilla general (vídeo):
```
[Sujeto] [acción concreta] en [entorno + hora del día],
[movimiento de cámara específico] mientras [descripción de la luz],
atmósfera/mood: [una o dos palabras emocionales].
```

Plantilla general (imagen):
```
A [sujeto], in [estilo visual], with [iluminación],
[encuadre/composición], [entorno/fondo],
mood: [emoción], details: [2-3 detalles concretos que importan]
```

## Plantillas por tarea

### Text-to-video

Plantilla: `[Sujeto] [acción], [movimiento de cámara] mientras [descripción de luz], atmósfera [mood].` Orden: sujeto+acción primero (pesa más), cámara y luz después, mood al final [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/) [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/). Sin tags entre corchetes ni `--ar`: todo en prosa.

Ejemplo cinematográfico: `Two brothers, Mateo and Vidal, sit on a wooden dock at dusk, their fishing rods propped against a railing. A slow dolly push-in drifts toward their shoulders as Mateo laughs and nudges Vidal with his elbow, warm amber light from the setting sun skimming the lake surface, calm and nostalgic atmosphere, faint ripples catching the last light.`

Ejemplo distinto (producto/acción): `A chrome running shoe rotates slowly on a mirrored pedestal in a dark studio, a single rim light sweeping across its laces as sparks of orange dust drift past in slow motion, sleek and futuristic mood, tight macro framing on the sole's texture.`

Diferencias de variante: no hay "fast"/"turbo"; Ray 3.2 es la única variante, salvo que a 10s se pierden `start_frame`/`end_frame`/`hdr` [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

No pongas: duración/resolución dentro del texto (van en `video.duration`/`video.resolution`) [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/); keywords sueltas en vez de prosa; más de un beat de acción en 5s; diálogo entre comillas esperando que se oiga — no hay audio.

### Image-to-video

Plantilla: `[Qué hace o cómo se mueve el sujeto], [movimiento de cámara], [qué cambia en luz/ambiente].` No redescribas aspecto físico ni encuadre — ya vienen de la imagen; describe solo movimiento y evolución [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

Ejemplo cinematográfico (parte de una foto de Mateo mirando por una ventana): `Mateo slowly turns his head from the window toward the camera, a faint smile forming, the camera holds a steady slow push-in, soft morning light brightens gradually across his face.`

Ejemplo distinto (parte de una foto de producto en mesa): `The perfume bottle rotates gently on its base, camera orbits halfway around at eye level, soft studio light sweeps across the glass revealing refracted highlights.`

Nota: `start_frame` solo, `end_frame` solo o ambos son válidos; omitir ambos cae a texto-a-vídeo puro [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

No pongas: `start_frame`/`end_frame` con `duration:"10s"` — se rechaza [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/); describir ropa/cara/decorado ya presentes (ruido); esperar que `aspect_ratio` reencuadre la imagen de origen.

### Start/end frame

Plantilla: `[Transición desde el estado del frame inicial hacia el estado del frame final], [movimiento de cámara que acompaña el cambio].` Pensado para cuando tienes dos fotogramas concretos (apertura y cierre) y quieres que el modelo interpole el movimiento entre ambos [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

Ejemplo cinematográfico: `The character turns to face the camera and smiles, slight push-in, warm interior light holds steady` — con `video.start_frame` apuntando a la foto de perfil de Vidal y `video.end_frame` a la foto de Vidal sonriendo de frente.

Ejemplo distinto: `The flower bud opens gradually into full bloom as the camera holds a static macro shot, color saturation deepens from pale pink to vivid magenta` — `start_frame` en capullo cerrado, `end_frame` en flor abierta.

Nota: para más de dos anclas usa `keyframes`+`keyframe_indexes`, excluyente con `start_frame`/`end_frame` [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

No pongas: `start_frame`/`end_frame` junto a `keyframes` — error 400 [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/); duración 10s con frames ancla; frame final idéntico palabra por palabra al inicial.

### Reference-to-video (multi-keyframe)

Plantilla: usa varias imágenes como anclas en posiciones arbitrarias del clip, describe solo la progresión: `[Sujeto/escena] [evoluciona a través de los beats anclados], [cámara que acompaña].` La forma más potente de dirigir Ray 3.2: `keyframes` (1–64 imágenes) y `keyframe_indexes` (rejilla duración×24fps: 5s→0–120, 10s→0–240) en arrays paralelos [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

Ejemplo cinematográfico: `Mateo and Vidal row a small boat across the lake, the camera tracks alongside at water level, drifting from the misty dawn start toward the warm midday light of the far shore` con `keyframes` = [foto de salida al amanecer, foto a mitad de lago, foto de llegada al mediodía] y `keyframe_indexes` = [0, 60, 120] para un clip de 5s.

Ejemplo distinto: `A hot-air balloon drifts across the valley as the sun rises`, con `keyframes` = [globo en el lanzamiento, globo a media altura, globo contra el amanecer] y `keyframe_indexes` = [0, 60, 120] [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

Nota: a diferencia de `start_frame`/`end_frame`, `keyframes` sí permite `duration:"10s"` y `hdr:true` [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

No pongas: `keyframes` sin `keyframe_indexes` — error 400 [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/); índices fuera de rango (máx. 120 en 5s, 240 en 10s); más de 64 keyframes.

### Video extend

Plantilla: `continue the camera move into [siguiente beat de acción]` o, hacia atrás, `[qué pasaba justo antes de este plano]`. Pasa el `generation_id` de un vídeo previo en `video.start_frame.generation_id` (extiende adelante) o `video.end_frame.generation_id` (prepone atrás), en una petición `type:"video"` normal [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

Ejemplo cinematográfico: `continue the camera move into the forest clearing where Mateo waits by the fire` sobre el `generation_id` del clip anterior de Vidal caminando hacia el bosque.

Ejemplo distinto: `the drone continues its ascent above the canyon, revealing the full rock formation` sobre el `generation_id` de un sobrevuelo previo.

Nota: extend corre en rango dinámico estándar (`hdr`/`exr_export` rechazados); `loop:true` solo hacia delante [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).

No pongas: `hdr:true`/`exr_export` en extend — se rechazan [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/); un segundo `generation_id` combinado con otra keyframe; un prompt que repite la escena anterior en vez de describir la continuación.

### Video edit

Plantilla: `Transform the scene into [nuevo look/estilo], [qué parte de la composición/movimiento se preserva].` Referencia la fuente con `source.generation_id` o una URL/vídeo inline [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).

Ejemplo cinematográfico: `Transform the scene into moonlit 35mm film footage, keep the same camera motion and composition as Mateo and Vidal walk down the dock.` Con `video.edit.controls.face.enabled:true` si quieres condicionar la identidad facial durante el reprocesado.

Ejemplo distinto: `Transform the scene into a hand-painted watercolor style, keep the product's silhouette and rotation speed unchanged.`

Control: empieza con `auto_controls:true` (deriva conditioning de la fuente) [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/). Para precisión, `strength` en tres bandas: `adhere_1..3` (fiel), `flex_1..3` (equilibrado, `flex_2` por defecto) y `reimagine_1..3` (prompt domina) [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/). Quirúrgico: `controls` por señal (excluyente con `auto_controls`) — `pose` (`strength: precise/coarse`), `depth` (`blur` 0–1), `normals` (`augmentation` 0–1), `trajectory` (`sparsity` 0–1), `face` (identidad) [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/). En ediciones no triviales, fija 2-3 `video.edit.keyframes` — más potente que solo ajustar `strength` [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).

No pongas: `image_ref` — rechazado en `video_edit`, la referencia es siempre la fuente [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/); `strength`/`controls` junto a `auto_controls:true` — excluyentes; `aspect_ratio` esperando reencuadrar — se hereda del fuente; redescribir toda la escena en vez de decir solo qué cambia.

### Reframe

Plantilla: `Extend the scene into [forma de cámara objetivo, ej. cinematic widescreen]` — describe cómo rellenar el área nueva (outpainting), nunca qué cambiar del contenido existente, que se preserva fotograma a fotograma [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/).

Ejemplo: `Extend the scene into a tall vertical frame, widen the dock and lake horizon above and below Mateo and Vidal without altering their actions.`

Ejemplo distinto: `Extend the scene into a wide cinematic frame, reveal more of the studio backdrop around the rotating shoe without changing its motion.`

No pongas: HDR, loop, `video.edit` o frames ancla — se rechazan [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/); fuente de más de 10s — techo de entrada/salida; instrucciones sobre qué cambiar de la acción original.

### Text-to-image

Plantilla:
```
A [subject], in [style], with [lighting], [camera/composition],
[environment/background], mood: [emotion], details: [key specifics]
```
80–250 palabras documentadas para esta tarea [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).

Ejemplo cinematográfico: `Two brothers, Mateo and Vidal, standing shoulder to shoulder on a weathered pier at golden hour, documentary photography style, warm backlight silhouetting their figures, medium wide shot at eye level, calm lake and distant mountains in the background, mood: quiet brotherhood, details: windblown hair, worn flannel jackets, faint reflection on the water.`

Ejemplo distinto: `A ceramic artist shaping a lopsided bowl, documentary photography style, soft window lighting, close-up shot, cluttered home studio background, mood: focused and quiet, details: clay-covered hands, imperfect texture.` [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/)

Diferencias de variante: Uni-1 y Max aceptan exactamente el mismo prompt/`aspect_ratio`/`image_ref` — solo cambia calidad/precio [[1]](https://docs.agents.lumalabs.ai/guides/model/).

No pongas: negative prompts ni "keyword soup" (`8k, ultra detailed, masterpiece`) — desaconsejado [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/); texto largo (>20 caracteres) esperando render perfecto [[7]](https://docs.agents.lumalabs.ai/guides/faq/); nombres de personas reales esperando evitar moderación [[7]](https://docs.agents.lumalabs.ai/guides/faq/).

### Image edit

Plantilla: `Change [elemento concreto] to [nueva versión]. Keep everything else the same.` 30–100 palabras, un cambio por iteración [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).

Ejemplo cinematográfico: `Change Vidal's jacket to a deep green color. Keep everything else the same, including his pose and the dock background.`

Ejemplo distinto: `Change the sky to a dramatic sunset with deep orange and purple clouds. Keep everything else the same.` [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/)

Diferencias de variante: Uni-1 y Max comparten el mismo contrato de edición; usa Max para más detalle/coherencia de iluminación [[1]](https://docs.agents.lumalabs.ai/guides/model/).

No pongas: el prompt entero reescrito — rompe la preservación implícita [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/); varios cambios no relacionados en la misma instrucción — mejor iterar paso a paso [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/); instrucciones vagas tipo "mejora la imagen".

### Multi-referencia de imagen

Plantilla:
```
IMAGE1 (STYLE): [descripción del estilo]
IMAGE2 (CHARACTER): [descripción del personaje]
Create a [subject] that combines the visual style of IMAGE1 and the
character from IMAGE2. [Detalles de escena.]
```
100–300 palabras cuando el guiado va fuerte por referencias [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/). Etiqueta el rol de cada referencia (style/character/composition/color/lighting/texture/mood); hasta 9 refs en creación, 8 en edición (el slot restante lo ocupa la imagen fuente) [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/) [[1]](https://docs.agents.lumalabs.ai/guides/model/).

Ejemplo cinematográfico: `IMAGE1 (STYLE): warm 35mm film documentary look with soft grain. IMAGE2 (CHARACTER): Mateo, the younger brother, in a navy flannel jacket. Create a photo of Mateo standing on a wooden dock at dusk that combines the visual style of IMAGE1 and the character from IMAGE2, calm lake behind him.`

Ejemplo distinto: `IMAGE1 (STYLE): minimalist studio product photography with a dark gradient background. IMAGE2 (COMPOSITION): centered hero shot with a single rim light. Create a photo of a glass perfume bottle that combines the style of IMAGE1 and the composition of IMAGE2.`

No pongas: más de 9 referencias en creación u 8 en edición — se rechaza [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/); referencias sin etiqueta de rol — pierde la asignación; esperar consistencia de personaje sin reutilizar la misma referencia frontal.

## Idioma, longitud y prompt negativo

Inglés documentado; español [sin verificar]. Longitudes por tarea: ver "Estructura de prompt" y cada `###` de "Plantillas por tarea". Sin `negative_prompt`: describe en positivo o pide preservar [[7]](https://docs.agents.lumalabs.ai/guides/faq/). El schema en vivo de Nexus manda sobre límites citados aquí.

## Referencias e imagen inicial

En imagen, `image_ref` etiquetado por rol es el mecanismo — ver "Multi-referencia de imagen" [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/). En vídeo no existe `image_ref`: equivalente son `start_frame`/`end_frame` y `keyframes`+`keyframe_indexes` [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/). En edición, `video.edit.keyframes` cumple ese papel sobre la rejilla del fuente [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).

## Cámara y movimiento

Ray 3.2 sigue cámara descrita en prosa (plano, movimiento, lente, ángulo), nunca con tags cerrados `[dolly_in]` — existían en la legacy Ray 2 y no se traducen a la Agents API [[8]](https://docs.lumalabs.ai/docs/video-generation.md) [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/). En edición hay además control por señal (`pose`/`depth`/`normals`/`trajectory`/`face`, ver "Video edit") [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).

## Consistencia de personaje

En edición de vídeo, `video.edit.controls.face` condiciona identidad facial [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/) — [sin verificar] si garantiza identidad en generaciones nuevas (`type:"video"`), sin ese control. En imagen, reusa la misma `image_ref` con etiqueta constante (`IMAGE2 (CHARACTER):`); sin seed determinista, depende de la fuerza de la referencia [[7]](https://docs.agents.lumalabs.ai/guides/faq/).

## Restricciones e incompatibilidades conocidas

- Moderación bloquea CSAM, sexual explícito, deepfakes no consentidos, violencia gráfica, autolesión, odio y uso engañoso de marcas; corre en entrada y salida, no se desactiva [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- Ray 3.2 no produce audio [[9]](https://www.atlascloud.ai/models/luma/ray-3.2/text-to-video); sin seed público ni reproducibilidad determinista [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- `video_edit` rechaza `image_ref`/`loop`/`end_frame`; `video_reframe` rechaza HDR/EXR/loop/`video.edit`/frames ancla [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/) [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/). `auto_controls:true` excluye `strength`/`controls` manuales [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).
- 10s incompatible con HDR o `start_frame`/`end_frame` (sí con `keyframes`) [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/). Fuente máxima: ≤18s edición, ≤10s reencuadre [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/) [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/).

## Errores habituales

- Pedir `negative_prompt` — no existe [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- Combinar `loop`/`hdr` con `duration:"10s"` — se rechaza [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- Pasar `aspect_ratio` en `video_edit` esperando cambiar encuadre — se ignora [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).
- Texto largo (>20 caracteres) en imagen esperando render perfecto — puede deformarse [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- Nombrar a una persona real esperando evitar moderación — causa falsos positivos [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- Anclar más de dos momentos solo con `start_frame`/`end_frame` — hace falta `keyframes` [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- Mezclar `auto_controls:true` con `strength`/`controls` manual — excluyentes [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).
- Pensar que Uni-1 Max necesita prompt distinto — ambos leen el mismo [[1]](https://docs.agents.lumalabs.ai/guides/model/).

## Recomendaciones de prompting

- Escribe en inglés como brief de dirección: sujeto + acción + cámara + luz + mood, prosa natural, instrucción clave al principio [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).
- Texto en imagen entre comillas con la cadena exacta, corta [[7]](https://docs.agents.lumalabs.ai/guides/faq/).
- En vídeo-a-vídeo no trivial, define 2-3 `keyframes`; empieza con `auto_controls:true` y pasa a manual solo si necesitas más precisión [[4]](https://docs.agents.lumalabs.ai/guides/videos/editing/).
- Para mantener personaje entre imágenes, fija una referencia frontal limpia y reutilízala con la misma etiqueta [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).
- Al reencuadrar vídeo, describe el relleno del lienzo nuevo, no el contenido existente [[5]](https://docs.agents.lumalabs.ai/guides/videos/reframing/).
- En image-to-video no redescribas la apariencia ya presente — solo movimiento, cámara y luz [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- No mezcles `type:"video"` con parámetros de `video.edit` — excluyentes [[3]](https://docs.agents.lumalabs.ai/guides/videos/generation/).
- Itera en pasos pequeños ("Modify") en vez de reescribir todo el prompt [[6]](https://docs.agents.lumalabs.ai/guides/images/prompt-guide/).
- No esperes audio nativo ni lipsync; combina con otro modelo [[9]](https://www.atlascloud.ai/models/luma/ray-3.2/text-to-video).

## Fuentes y fecha

[1] Luma Agents API - Models — https://docs.agents.lumalabs.ai/guides/model/ — consultado 2026-10-02
[2] Luma Agents API - Videos overview (enlace a Migration guide) — https://docs.agents.lumalabs.ai/guides/videos/ — consultado 2026-10-02
[3] Luma Agents API - Video generation — https://docs.agents.lumalabs.ai/guides/videos/generation/ — consultado 2026-10-02
[4] Luma Agents API - Video editing — https://docs.agents.lumalabs.ai/guides/videos/editing/ — consultado 2026-10-02
[5] Luma Agents API - Video reframing — https://docs.agents.lumalabs.ai/guides/videos/reframing/ — consultado 2026-10-02
[6] Luma Agents API - Prompting guide (Uni-1) — https://docs.agents.lumalabs.ai/guides/images/prompt-guide/ — consultado 2026-10-02
[7] Luma Agents API - FAQ — https://docs.agents.lumalabs.ai/guides/faq/ — consultado 2026-10-02
[8] Luma Dream Machine API (legacy, Ray 2) - Video Generation — https://docs.lumalabs.ai/docs/video-generation.md — consultado 2026-10-02
[9] Atlas Cloud - Luma Ray 3.2 Text-to-Video — https://www.atlascloud.ai/models/luma/ray-3.2/text-to-video — consultado 2026-10-02
