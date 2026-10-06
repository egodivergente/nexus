---
id: kling
kind: model
title: 'Kling (Kuaishou)'
version: 2
updated: 2026-10-02
match: ['^kwaivgi/kling', '^crun/kling']
modalities: [VIDEO]
strengths: [prompt_adherence, camera_control, cinematic_look, native_audio, high_resolution, physics]
weaknesses: [long_duration, stylized]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, motion_control, video_edit, avatar, lipsync]
avoid_tasks: [text_to_image, image_edit, upscale, layer_decomposition]
unverified_tags: [face_consistency, natural_motion, multi_character]
sources:
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/text-to-video
    title: 'Kling O3 Pro — T2V'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video
    title: 'Kling O3 Pro — Reference-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit
    title: 'Kling O3 Pro — Video Edit'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-4k/text-to-video
    title: 'Kling V3.0 4K — T2V'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/text-to-video
    title: 'Kling V3.0 Std — T2V'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control
    title: 'Kling v2.6 Pro — Motion Control'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/avatar
    title: 'Kling v2.6 Pro — Avatar'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/image-to-video
    title: 'Kling v2.6 Pro — I2V (audio)'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video
    title: 'Kling v2.5 Turbo Pro — I2V'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v2.1-i2v-pro/start-end-frame
    title: 'Kling v2.1 Pro — Start/End Frame'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-v1.6-i2v-pro
    title: 'Kling v1.6 — I2V Pro'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-video-o1/text-to-video
    title: 'Kling O1 Omni — T2V'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/kwaivgi/kling-effects
    title: 'Kling Effects'
    accessed: 2026-10-02
---

## Resumen
Kling (Kuaishou) es la familia de vídeo generativo más amplia del catálogo de Nexus: de v1.6 a la generación actual v3.0/O3 (std/pro/turbo/4K), con texto-a-vídeo, imagen-a-vídeo, referencia-a-vídeo, frame inicial/final, control de movimiento, avatar parlante y edición de vídeo por texto. Destaca por seguir instrucciones multi-paso, control de cámara en prosa y, desde v2.6, audio nativo sincronizado con hasta 2 voces de diálogo en v3.0. Para peticiones genéricas, recomienda **v3.0 u O3**, no v1.6/v2.0/v2.1 (legado).

## Cuándo elegirlo y cuándo no
- Elige Kling para **cámara en prosa** (dolly, grúa, tracking), **audio nativo** sincronizado (v2.6+/v3.0) o **edición de vídeo por texto** sin máscaras (O3 video-edit) [[7]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control) [[3]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit).
- Elige O3 Reference-to-Video con varias fotos de un personaje para identidad consistente sin vídeo de arranque [[2]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video); elige Motion Control para transferir una coreografía desde vídeo de referencia [[7]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control).
- Mejor **Seedance** para planos largos en una toma; **Runway Gen-4.5** para la consistencia de personaje más robusta entre tomas; **MiniMax**/**Vidu**/**Wan**/**PixVerse** para iterar barato antes de escalar a Kling pro/4K.
- Para avatares con lipsync desde audio propio, Kling avatar (v2.6) es sólido; para voz generada además de lipsync, compara con MiniMax o Runway.

| Necesidad | Variante |
|---|---|
| Máxima fidelidad | kling-v3.0-4k, kling-video-o3-4k |
| Calidad/coste en producción | kling-v3.0-pro, kling-video-o3-pro |
| Borrador rápido y barato | kling-v3.0-turbo, kling-v3.0-std |
| Identidad desde varias fotos | kling-video-o3-pro/reference-to-video, -std |
| Transferir coreografía | kling-v2.6-pro/motion-control, kling-v3.0-pro/motion-control, crun/kling-v3-motion-control-pro |
| Avatar parlante | kling-v2.6-pro/avatar, -std/avatar |
| Editar vídeo existente | kling-video-o3-pro/video-edit, -std |
| Frame inicial y final | kling-v2.1-i2v-pro/start-end-frame, v2.5-turbo-pro, v3.0-*/image-to-video |
| Diálogo con hasta 2 voces | kling-v3.0-std/pro/turbo/4k (voice_list) |
| Efecto rápido para redes | kling-effects |

## Linaje y variantes en Nexus
- **v1.6/v2.0 (legado)**: primeras generaciones, mejoras incrementales de "prompt adherence" [[11]](https://www.atlascloud.ai/models/kwaivgi/kling-v1.6-i2v-pro). No recomendar por defecto.
- **v2.1**: atención espaciotemporal 3D y física simulada; añade start-end-frame dedicado en pro [[10]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.1-i2v-pro/start-end-frame).
- **v2.5 Turbo Pro**: motor "text-timing" para instrucciones causales multi-paso, más velocidad y `last_image` [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video).
- **v2.6**: primera con audio-vídeo nativo [[8]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/image-to-video); añade Motion Control, con `character_orientation` ("image" o "video") que decide si el resultado sigue el encuadre de la foto o del clip de referencia [[7]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control), y Avatar lipsync audio-driven (sin TTS) en std/pro [[6]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/avatar).
- **v3.0 (más reciente línea "v")**: std/pro/turbo/4K; `negative_prompt`, `cfg_scale` (fuerza de adherencia al prompt), hasta 2 entradas en `voice_list` para diálogo de personajes, duración 5 o 10 s [[4]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-4k/text-to-video) [[5]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/text-to-video). También existe `v3.0-pro/std-motion-control`.
- **Video O1 ("Omni")**: motor unificado multimodal, consistencia de sujeto entre planos y extensión de toma [[12]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o1/text-to-video).
- **Video O3 (std/pro/4K) — gama alta actual**: sobre v3.0, duración 3-15 s, "Prompt Enhancer" integrado que reescribe descripciones de movimiento débiles [[1]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/text-to-video); en pro/std, modos exclusivos reference-to-video (hasta 7 imágenes sin vídeo guía, hasta 4 con vídeo guía, identidad consistente) [[2]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video) y video-edit por texto con hasta 4 imágenes de referencia para guiar el resultado [[3]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit).
- **Kling Effects**: aparte, no numerada; imagen → clip de 5 s con efecto predefinido [[13]](https://www.atlascloud.ai/models/kwaivgi/kling-effects).
- **Ids Crun**: `crun/kling-v3-std/pro` y `crun/kling-v3-motion-control-std/pro`, rutas alternativas de Kling 3.0 vía proveedor Crun de Nexus, equivalentes a `kwaivgi/kling-v3.0-*`. Confirma parámetros por id con `get_model_schema`.

## Fortalezas
- Seguimiento de prompt multi-paso, incluidas instrucciones causales secuenciales [[1]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/text-to-video).
- Control de cámara en prosa (paneo, dolly, grúa, tracking) con estabilidad en movimientos complejos [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video).
- Look cinematográfico consistente de luz/paleta, incluso en transiciones agresivas [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video).
- Audio nativo sincronizado desde v2.6, hasta 2 voces de diálogo en v3.0 [[8]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/image-to-video) [[5]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/text-to-video).
- Tier 4K con mayor fidelidad que std/pro [[4]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-4k/text-to-video).
- Física simulada para movimiento natural desde v2.1 [[12]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o1/text-to-video).
- Video-edit O3 edita por lenguaje natural sin timeline ni máscaras, con fuerte coherencia temporal (poco parpadeo/ghosting) [[3]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit).

## Debilidades
- Duración de 5-10 s en la mayoría de tiers (hasta 15 s en O3), corta para una sola toma larga — encadena generaciones si necesitas más.
- Orientado a fotorrealismo/cine; no se documenta un modo de estilo marcado (anime, 3D cartoon).
- [sin verificar] consistencia facial fuera de los endpoints que la declaran (O3 reference-to-video, avatar); en t2v/i2v genéricos no hay garantía.
- [sin verificar] comportamiento con varios personajes en el mismo plano.
- [sin verificar] la sintaxis exacta para asignar texto de diálogo a cada entrada de `voice_list` dentro del prompt; la fuente solo documenta el parámetro, no el formato del texto hablado.

## Tareas ideales / Tareas que debe evitar
- Ideal: `text_to_video`/`image_to_video` con cámara en prosa; `reference_to_video` (O3) para identidad desde varias fotos; `start_end_frame` para transiciones; `motion_control` para transferir coreografía; `video_edit` (O3) para modificar vídeo por texto; `avatar`/`lipsync` para talking-heads (v2.6).
- Evitar: `text_to_image`, `image_edit`, `upscale`, `layer_decomposition` — Kling en Nexus es solo vídeo; usa un modelo de imagen o upscaling dedicado.

## Estructura de prompt recomendada
Inglés en prosa natural (mejor seguimiento documentado); 1-3 frases, ~25-70 palabras en t2v/i2v, hasta ~90-120 si hay diálogo o varios pasos causales. Sigue mejor instrucciones causales encadenadas ("X, then Y, while Z") que listas de etiquetas sueltas. No hay sintaxis de `@referencia` ni `[corte]`: nombra a sujetos y referencias con palabras ("the man from the first reference", "the woman on the left"). Diálogo entre comillas dentro de la frase cuando el modo admite sonido/voces.

```
[Sujeto(s): quién/qué, 1 rasgo distintivo si no hay imagen/referencia] +
[acción principal, en orden causal si hay varios pasos: "X, then Y, while Z"] +
[cámara: tipo de plano + movimiento — paneo lento / dolly in / grúa / tracking / fijo] +
[entorno y luz] + [estilo visual: cinematográfico, textura de film, etc.] +
[opcional, si el modo lo admite: diálogo entre comillas o nota de sonido/música]
```
No uses `--parámetros` de Midjourney: Kling no los interpreta. Duración, aspect_ratio, sound, voice_list, negative_prompt y cfg_scale van en el schema (`get_model_schema`), no en el texto del prompt.

## Plantillas por tarea

### Text-to-video
`[Tipo de plano] + [sujeto(s) y rasgo distintivo] + [acción, en orden causal] + [entorno/luz] + [cámara] + [estilo]`
En O3, el "Prompt Enhancer" interno reescribe descripciones de movimiento débiles, pero sigue conviniendo describir cámara y luz a mano para control fino [[1]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/text-to-video). En v3.0 puedes añadir diálogo entre comillas si vas a activar `voice_list`, y restricciones en `negative_prompt` en vez de en el texto [[5]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/text-to-video).
- Ejemplo cinematográfico: "Medium shot, two brothers, Mateo and Vidal, stand on a rain-soaked rooftop at dusk. Mateo lights a cigarette, then Vidal places a hand on his shoulder as they watch the city lights below. Camera slowly cranes up and back, warm neon reflections on wet concrete, moody cinematic look, shallow depth of field."
- Ejemplo distinto (producto/acción): "Close-up, a chrome sports watch spins slowly on a black marble pedestal, then the camera pulls back to reveal a sleek showroom with soft blue rim lighting; a rotating turntable keeps the watch centered, glossy commercial look, no people in frame."
No pongas: duraciones ni "4K"/"60fps" en el texto; listas de etiquetas sueltas sin verbo de acción; más de 2-3 beats narrativos en un solo clip corto; nombres de cámaras físicas reales esperando un efecto concreto.

### Image-to-video
`[Acción nueva, en orden causal si hay pasos] + [cámara] + [luz/entorno si difiere de la imagen] + [estilo si difiere]`
En v2.5 Turbo Pro la estabilidad en acción rápida es mayor que en tiers anteriores, por lo que tolera descripciones de movimiento más intensas sin perder coherencia [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video). Desde v2.6, si activas `sound`, puedes añadir una nota de audio ambiental breve al final del prompt [[8]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/image-to-video).
- Ejemplo cinematográfico (imagen de partida: foto de Mateo y Vidal sentados a una mesa): "Mateo slowly turns his head toward Vidal and smiles, then Vidal laughs and leans back in his chair; slow dolly-in, warm late-afternoon light through the window, cinematic realism, ambient café chatter."
- Ejemplo distinto (producto): "The sneaker lifts off the table and rotates mid-air in slow motion while dust particles catch the light; camera orbits around it at eye level, dramatic rim lighting, high-end commercial look."
No pongas: la descripción física del sujeto que ya está en la imagen (ropa, color de pelo, rasgos); una acción incoherente con la pose de partida (p. ej. "se levanta y corre" sobre una foto sentado sin transición); varios cambios de encuadre en el mismo clip corto.

### Start/end frame
`[Transición descrita de principio a fin, coherente con ambos fotogramas] + [cámara] + [estilo]`
Requiere `image` (frame inicial) + `last_image`/`end_image` (frame final); disponible dedicado en v2.1 Pro y también como opción dentro de image-to-video en v2.5 Turbo Pro y v3.0 [[10]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.1-i2v-pro/start-end-frame) [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video).
- Ejemplo cinematográfico: "Mateo closes the door behind him in the first frame; by the final frame he is standing alone in an empty, dimly lit hallway — show him walking forward in slow motion as the lights flicker, static wide shot, melancholic blue tones."
- Ejemplo distinto (producto/naturaleza): "The flower transitions from a tightly closed bud to a fully open bloom; static close-up, soft diffused daylight, gentle petal movement, no camera motion."
No pongas: una acción final que contradiga el frame final subido; movimientos de cámara agresivos (el frame final debe seguir siendo reconocible); más de una transición por generación.

### Reference-to-video (O3)
`[Qué hace(n) el/los sujeto(s) de referencia, nombrados por rol] + [entorno] + [cámara] + [estilo]`
Hasta 7 imágenes de referencia sin vídeo guía, o hasta 4 si añades un vídeo de referencia para el movimiento; con vídeo guía puedes activar `keep_original_sound`, sin vídeo guía puedes activar `sound` para audio generado [[2]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video).
- Ejemplo cinematográfico (referencias: foto de Mateo, foto de Vidal): "The man from the first reference and the man from the second reference, Mateo and Vidal, meet at a rooftop café at sunset; they shake hands and sit, slow tracking shot circling the table, warm golden-hour light, cinematic look."
- Ejemplo distinto (producto con prop de referencia): "The red sports car from the reference image drifts around a mountain hairpin turn at sunset, dust kicking up behind it; low-angle tracking shot, dramatic orange sky, high-energy commercial style."
No pongas: la descripción física de los sujetos ya presentes en las fotos (edad, ropa, rasgos); más de 7 referencias (o más de 4 si hay vídeo guía); una acción que ignore el vídeo guía cuando lo hay.

### Motion control
`[Contexto de escena/entorno] + [qué conserva o cambia respecto al vídeo de referencia] + [estilo/luz]` — el movimiento en sí (coreografía, gesto, acción) lo aporta el vídeo de referencia (3-30 s), no el texto.
Requiere `image` (personaje a animar), `video` (clip de referencia con el movimiento) y `character_orientation` ("image" = sigue encuadre y duración de la foto; "video" = sigue encuadre y duración del clip) [[7]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control). El prompt solo afina escena, luz o estilo; puedes conservar o descartar el audio original del vídeo de referencia.
- Ejemplo cinematográfico: "Same character as the image, Mateo, performs the uploaded dance routine on a rain-slicked rooftop at night, neon signage glowing behind him, cinematic color grade."
- Ejemplo distinto (acción/estilizado): "The animated mascot from the image performs the uploaded martial-arts combo in a bright arena stage, dramatic spotlight, high-contrast comic-style lighting."
No pongas: una descripción de movimiento distinta a la del vídeo de referencia (el modelo transfiere el movimiento del vídeo, no el del texto); un vídeo de referencia fuera del rango 3-30 s; omitir `character_orientation` esperando que el prompt decida el encuadre.

### Video edit (O3)
`[Qué cambiar exactamente, un cambio por generación]` — opcionalmente apóyate en hasta 4 imágenes de referencia para fijar el aspecto del elemento, escena o estilo destino.
Requiere `video` de entrada; `keep_original_sound` está activado por defecto para preservar el audio original [[3]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit).
- Ejemplo cinematográfico: "Replace Vidal's gray jacket with a dark green military-style coat, keep his pose, the rooftop background and the lighting exactly the same."
- Ejemplo distinto (producto/marca): "Change the beer in his hand to a can of cola, keep the same lighting, hand position and background untouched."
No pongas: varios cambios no relacionados en el mismo prompt (p. ej. cambiar ropa y fondo y hora del día a la vez); instrucciones vagas tipo "mejóralo"; referencias de imagen que contradigan el prompt de texto.

### Avatar / lipsync
`[Opcional: tono, expresión o estilo de cámara]` — el habla y el timing vienen siempre del audio subido, nunca del texto del prompt.
Requiere `audio` (pista real, no TTS) e `image` (retrato frontal nítido); el prompt es opcional y solo afina estilo/expresión/cámara; si se omite, usa un estilo neutro de talking-head [[6]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/avatar).
- Ejemplo cinematográfico: "confident presenter in a tech promo, soft studio lighting, subtle head nods, warm engaged expression."
- Ejemplo distinto (social/casual): "friendly customer-service tone, warm smile, relaxed home-office background, natural eye contact with the camera."
No pongas: el guion o diálogo como texto del prompt (el habla sale del audio subido, no del texto); una imagen con el rostro ocluido, borroso o de perfil extremo; instrucciones de movimiento corporal amplio (el modo está pensado para planos de retrato con poco desplazamiento).

### Video extend
`[Qué debe seguir ocurriendo tras el último fotograma, en continuidad de acción y luz]`
Disponible sobre el motor unificado de Video O1 Omni, con consistencia de sujeto entre planos extendidos [[12]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o1/text-to-video). [sin verificar] si el parámetro de extensión está expuesto igual en todos los tiers; confirma con el schema del id concreto.
- Ejemplo cinematográfico: "Mateo keeps walking down the rooftop, now reaching the edge and looking out over the city; continue the same slow crane movement and warm neon lighting from the previous shot."
- Ejemplo distinto (producto): "The watch continues rotating on the pedestal, now the camera pulls back further to reveal the full showroom, same soft blue lighting as before."
No pongas: un cambio brusco de luz, cámara o estilo respecto al clip original; una acción que no continúe físicamente desde el último fotograma; mezclar extensión con cambios de personaje o escena.

## Idioma, longitud y prompt negativo
Inglés es el idioma mejor documentado; frases cortas en prosa, 25-70 palabras (hasta ~120 si hay diálogo o varios pasos causales). v3.0 y O3 exponen **negative prompt** y **CFG scale** (fuerza de adherencia al prompt, por defecto moderada) [[5]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/text-to-video) [[4]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-4k/text-to-video); si la variante no tiene ese campo (confirma con el schema), expresa restricciones en positivo dentro del prompt en vez de "sin X".

## Referencias e imagen inicial
Image-to-video: imagen nítida y bien compuesta; describe solo la acción y la cámara, no el aspecto ya visible. Start/end frame: `image` + `last_image`/`end_image` como keyframes coherentes entre sí (la acción descrita debe poder llegar del uno al otro). Reference-to-video (O3): hasta 7 imágenes (o 4 + vídeo guía); nómbralas por rol ("the woman from the first reference", "the man from the second") [[2]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video). Motion control: imagen de personaje + vídeo de 3-30 s; `character_orientation` decide si sigue el encuadre de la imagen o del vídeo [[7]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control). Video-edit: hasta 4 imágenes de referencia opcionales para fijar el aspecto del elemento o estilo destino, sobre el vídeo de entrada [[3]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit).

## Cámara y movimiento
Describe el movimiento de cámara explícitamente (paneo, dolly in/out, grúa, tracking, fijo); mejor estabilidad en acción de alta velocidad desde v2.5 Turbo Pro [[9]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video). No encadenes más de un movimiento de cámara distinto por generación corta; para secuencias con varios movimientos, divide en clips y encadénalos (start/end frame o video extend) en vez de pedirlos todos en un solo prompt.

## Consistencia de personaje
Garantizada solo donde se declara: O3 reference-to-video (identidad entre fotos de referencia) y avatar (preserva identidad desde la imagen) [[2]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video) [[6]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/avatar). En t2v/i2v genéricos no hay garantía entre generaciones separadas: reutiliza siempre la misma imagen de referencia validada y revisa cada clip antes de encadenar escenas. [sin verificar] el comportamiento con varios personajes nombrados en el mismo plano fuera de reference-to-video.

## Audio y diálogo
Desde v2.6, Kling co-genera audio y vídeo sincronizados con el personaje [[8]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/image-to-video); v3.0 añade `voice_list`, hasta 2 entradas para dar voz a diálogo de personajes además del flag `sound` para efectos/ambiente [[5]](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/text-to-video). [sin verificar] la sintaxis exacta para enlazar texto hablado a cada entrada de `voice_list` dentro del prompt; como aproximación segura, escribe el diálogo entre comillas atribuido a cada personaje en el texto ("Mateo dice: «¿vienes?»") y confirma el formato del parámetro con el schema antes de depender de él. En avatar el audio se sube como pista real (no TTS): respeta timing y emoción del original; el prompt solo afina estilo/expresión [[6]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/avatar). Video-edit y motion-control permiten preservar el audio original (`keep_original_sound`) [[3]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit) [[7]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control).

## Restricciones e incompatibilidades conocidas
- Familia solo de vídeo: ningún endpoint hace text_to_image/image_edit/upscale/layer_decomposition.
- Lipsync/avatar requieren rostro frontal claro, sin oclusiones ni blur fuerte [[6]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/avatar).
- Motion control exige vídeo de referencia de 3-30 s además de la imagen de personaje, y un `character_orientation` explícito [[7]](https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control).
- Reference-to-video limita a 7 imágenes sin vídeo guía o 4 con vídeo guía; video-edit limita a 4 imágenes de referencia [[2]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video) [[3]](https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit).
- No asumas que todas las variantes comparten duración/resolución/voice_list: lo da el schema en vivo.

## Errores habituales
- Recomendar v1.6/v2.0/v2.1 por defecto en vez de v3.0/O3.
- Redescribir el aspecto físico del personaje cuando ya hay imagen/referencias, en vez de acción y cámara.
- Pedir varios cambios de plano o cámaras contradictorias en una generación corta.
- Usar motion-control sin vídeo de referencia válido, esperando que el texto describa la coreografía.
- Escribir el guion de un avatar como prompt de texto en vez de subir el audio real.
- Pedir estilos alejados del fotorrealismo esperando el mismo control que modelos especializados en estilo.
- En video-edit, pedir varios cambios no relacionados a la vez en lugar de un cambio claro por generación.
- Asumir que `voice_list` acepta el diálogo con una sintaxis estándar sin confirmarla contra el schema del id concreto.

## Recomendaciones de prompting
- Prioriza v3.0 u O3 sobre generaciones anteriores salvo petición explícita.
- Escribe la cámara en prosa; usa el schema para duración/resolución/aspecto/voz, no el texto.
- Para personajes recurrentes, fija una imagen de referencia validada; en O3 usa reference-to-video con varias fotos y nómbralas por rol ("the man from the first reference").
- En motion-control, deja que el vídeo de referencia aporte el movimiento; el prompt solo afina escena/estilo, y fija bien `character_orientation`.
- Si hay negative prompt (v3.0/O3), usa frases cortas y concretas en vez de negaciones largas.
- Para avatar/lipsync, sube audio limpio e imagen frontal bien iluminada; el prompt solo da tono/expresión, nunca el guion.
- En video-edit, pide un cambio concreto por generación y apóyate en imágenes de referencia si el resultado debe parecerse a algo exacto.
- Prueba std/turbo antes de escalar a pro/4K con el mismo prompt para validar coste y encuadre.

## Fuentes y fecha
[1] Kling O3 Pro T2V — https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/text-to-video — consultado 2026-10-02.
[2] Kling O3 Pro Reference-to-Video — https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/reference-to-video — consultado 2026-10-02.
[3] Kling O3 Pro Video Edit — https://www.atlascloud.ai/models/kwaivgi/kling-video-o3-pro/video-edit — consultado 2026-10-02.
[4] Kling V3.0 4K T2V — https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-4k/text-to-video — consultado 2026-10-02.
[5] Kling V3.0 Std T2V — https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/text-to-video — consultado 2026-10-02.
[6] Kling v2.6 Pro Avatar — https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/avatar — consultado 2026-10-02.
[7] Kling v2.6 Pro Motion Control — https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/motion-control — consultado 2026-10-02.
[8] Kling v2.6 Pro I2V (audio) — https://www.atlascloud.ai/models/kwaivgi/kling-v2.6-pro/image-to-video — consultado 2026-10-02.
[9] Kling v2.5 Turbo Pro I2V — https://www.atlascloud.ai/models/kwaivgi/kling-v2.5-turbo-pro/image-to-video — consultado 2026-10-02.
[10] Kling v2.1 Pro Start/End Frame — https://www.atlascloud.ai/models/kwaivgi/kling-v2.1-i2v-pro/start-end-frame — consultado 2026-10-02.
[11] Kling v1.6 I2V Pro — https://www.atlascloud.ai/models/kwaivgi/kling-v1.6-i2v-pro — consultado 2026-10-02.
[12] Kling O1 Omni T2V — https://www.atlascloud.ai/models/kwaivgi/kling-video-o1/text-to-video — consultado 2026-10-02.
[13] Kling Effects — https://www.atlascloud.ai/models/kwaivgi/kling-effects — consultado 2026-10-02.
