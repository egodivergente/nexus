---
id: 'flux'
kind: 'model'
title: 'FLUX (Black Forest Labs) — imagen y vídeo'
version: 2
updated: '2026-10-02'
match: ['^black-forest-labs/flux', '^crun/flux']
modalities: [IMAGE, VIDEO]
strengths: [identity_preservation, prompt_adherence, text_rendering, photorealism, native_audio, fast, high_resolution]
weaknesses: []
ideal_tasks: [text_to_image, image_edit, multi_reference_image, text_to_video, image_to_video, start_end_frame, video_edit, video_extend]
avoid_tasks: [avatar, layer_decomposition]
unverified_tags: [multi_character]
sources:
  - url: 'https://bfl.ai/models/flux-kontext'
    title: 'FLUX.1 Kontext'
    accessed: '2026-10-02'
  - url: 'https://bfl.ai/models/flux-2'
    title: 'FLUX.2'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/flux_2/flux2_overview'
    title: 'FLUX.2 Overview'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/flux_2/flux2_text_to_image'
    title: 'FLUX.2 Text to Image'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_editing_overview'
    title: 'Image editing overview'
    accessed: '2026-10-02'
  - url: 'https://bfl.ai/blog/flux-3'
    title: 'FLUX 3 (blog)'
    accessed: '2026-10-02'
  - url: 'https://bfl.ai/blog/flux-3-video'
    title: 'FLUX 3 Video, Part 1'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/flux_3/flux3_video'
    title: 'Video (FLUX 3) API'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/flux_3/flux3_image'
    title: 'FLUX 3 Image API'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_video_overview'
    title: 'Video Generation overview'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_video_text_to_video'
    title: 'Text-to-Video'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_video_image_to_video'
    title: 'Image-to-Video'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_video_audio'
    title: 'Audio and speech'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_video_camera_terms'
    title: 'Camera terms'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_unified_basics'
    title: 'Prompting Basics'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/guides/prompting_video_editing'
    title: 'Video Editing (guía)'
    accessed: '2026-10-02'
  - url: 'https://docs.bfl.ai/flux_tools/flux_video_edit'
    title: 'FLUX Video Edit'
    accessed: '2026-10-02'
  - url: 'https://www.atlascloud.ai/models/black-forest-labs/flux-dev'
    title: 'Flux-Dev'
    accessed: '2026-10-02'
  - url: 'https://www.atlascloud.ai/models/black-forest-labs/flux-schnell'
    title: 'Flux-Schnell'
    accessed: '2026-10-02'
  - url: 'https://www.atlascloud.ai/models/black-forest-labs/flux-kontext-dev'
    title: 'FLUX-Kontext-Dev'
    accessed: '2026-10-02'
---

## Resumen
FLUX (Black Forest Labs) es la familia de imagen y vídeo más completa de Nexus de un solo fabricante: boceto rápido (`schnell`/`[klein]`), producción fotorrealista (`FLUX.2 pro/flex/max`), edición por instrucción (`Kontext`, `FLUX.2`/`FLUX 3 edit`) y, desde FLUX 3, vídeo con audio nativo e imagen con grounding web y cajas [[2]](https://bfl.ai/models/flux-2)[[6]](https://bfl.ai/blog/flux-3)[[9]](https://docs.bfl.ai/flux_3/flux3_image). Destaca por tipografía fiable y alta adherencia al prompt [[3]](https://docs.bfl.ai/flux_2/flux2_overview).

## Cuándo elegirlo y cuándo no
**Imagen.** Elige FLUX con texto legible, color exacto por hex, edición con hasta 10 referencias o grounding web [[3]](https://docs.bfl.ai/flux_2/flux2_overview)[[9]](https://docs.bfl.ai/flux_3/flux3_image). Mejor **Nano Banana** (conversacional), **Seedream** (coste mínimo), **Midjourney** (pictórico), **GPT Image** (multi-paso).

**Vídeo.** Elige FLUX 3 Video con audio nativo multilingüe, hasta 20s, keyframes/start+end-frame o continuación de clip [[7]](https://bfl.ai/blog/flux-3-video)[[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video). Mejor **Kling**/**Seedance** sin audio; mejor **Wan** para pesos abiertos [[7]](https://bfl.ai/blog/flux-3-video).

| Necesidad | Variante |
| - | - |
| Boceto rápido | `flux-schnell`, `flux-2-klein` |
| LoRA/fine-tuning propio | `flux-dev(-lora)` |
| Edición, pesos abiertos | `flux-kontext-dev(-lora)` |
| Producción final/tipografía | `flux-2-pro`/`flux-2-flex` |
| Máxima calidad, grounding web o cajas de posición | `flux-2-max` o `flux-3-image` si está en Nexus — confirma el id con `get_model_schema` |
| Vídeo texto/imagen con audio | `flux-3-video` modo `t2v`/`i2v` |
| Transición entre imágenes o storyboard | `flux-3-video` modo `i2v` (start+end, keyframes) |
| Continuar/editar clip | `flux-3-video` modo `v2v`, tool `video-edit` |

## Linaje y variantes en Nexus
1. **FLUX.1 `[dev]`/`[schnell]`**: pesos abiertos (12B), `schnell` prioriza velocidad y `dev` calidad [[17]](https://www.atlascloud.ai/models/black-forest-labs/flux-dev)[[18]](https://www.atlascloud.ai/models/black-forest-labs/flux-schnell). **Kontext `[dev]` (+lora)** añade edición por instrucción [[1]](https://bfl.ai/models/flux-kontext)[[19]](https://www.atlascloud.ai/models/black-forest-labs/flux-kontext-dev).
2. **FLUX.2**: `[klein]` sub-segundo/abierto (sin auto-expansión), `[pro]` producción, `[flex]` tipografía, `[max]` máxima calidad con grounding; hasta 4MP, 8-10 referencias [[3]](https://docs.bfl.ai/flux_2/flux2_overview). En Nexus: `flux-2-pro`/`flux-2-flex`.
3. **FLUX 3**: multimodal unificada (imagen+vídeo+audio+acción) [[6]](https://bfl.ai/blog/flux-3): **FLUX 3 Video** (t2v/i2v con audio, keyframes, start+end-frame, `v2v`) [[8]](https://docs.bfl.ai/flux_3/flux3_video) y **FLUX 3 Image** (edición, hasta 10 referencias, grounding por defecto, cajas 0-1000, hasta 4K) [[9]](https://docs.bfl.ai/flux_3/flux3_image). Edición de vídeo va aparte, en `FLUX Video Edit [fast]` (`video-edit-v1`), sin máscara ni referencia externa [[16]](https://docs.bfl.ai/flux_tools/flux_video_edit).

**Más reciente**: imagen → `FLUX 3 Image`; vídeo → `flux-3-video` [[9]](https://docs.bfl.ai/flux_3/flux3_image). [sin verificar] si `flux-3-image` está en el catálogo — confirma con `get_model_schema`.

## Fortalezas
- **Tipografía/texto y adherencia**: renderiza texto complejo e infografías (posición/estilo por comillas), posición/luz/composición precisas [[2]](https://bfl.ai/models/flux-2).
- **Identidad/multi-referencia y grounding web**: hasta 8-10 referencias mantienen personaje, producto o estilo entre escenas; con `grounding` busca en la web antes de generar [[3]](https://docs.bfl.ai/flux_2/flux2_overview)[[9]](https://docs.bfl.ai/flux_3/flux3_image).
- **Audio nativo (vídeo)**: diálogo, ambiente, efectos y música sincronizados, varios idiomas incl. español [[12]](https://docs.bfl.ai/guides/prompting_video_audio).
- **Velocidad/coste y resolución**: `schnell`/`[klein]`/`draft` para iterar barato; 4K en imagen, `hd/fhd/qhd/uhd` en vídeo [[18]](https://www.atlascloud.ai/models/black-forest-labs/flux-schnell)[[8]](https://docs.bfl.ai/flux_3/flux3_video).

## Debilidades
- Sin `seed`/`negative_prompt`: dos generaciones pueden variar; va todo en positivo [[13]](https://docs.bfl.ai/guides/prompting_unified_basics)[[8]](https://docs.bfl.ai/flux_3/flux3_video).
- FLUX Video Edit: clips hasta 15s/720p, sin referencia ni máscara, sin cambiar duración/aspecto [[16]](https://docs.bfl.ai/flux_tools/flux_video_edit).
- FLUX 3 Video empata (no supera) a Seedance 2.0 en i2v según BFL; `[klein]` no expande el prompt, un prompt corto sale pobre sin detalle manual [[7]](https://bfl.ai/blog/flux-3-video)[[3]](https://docs.bfl.ai/flux_2/flux2_overview).

## Tareas ideales / Tareas que debe evitar
- **Ideales**: text_to_image, image_edit, multi_reference_image, text_to_video, image_to_video, start_end_frame, video_edit, video_extend.
- **Evitar**: avatar y layer_decomposition — sin avatar hablante con audio externo ni descomposición en capas; FLUX 3 genera su propio audio pero no hace lipsync a audio externo ya grabado.

## Estructura de prompt recomendada
Inglés es el idioma mejor documentado; vídeo admite también español, francés, alemán, japonés, portugués, ruso y chino, con cambio de idioma dentro de la misma toma [[12]](https://docs.bfl.ai/guides/prompting_video_audio). Prosa natural: un prompt corto funciona porque FLUX lo expande (salvo `[klein]`), pero cada detalle a controlar va explícito [[13]](https://docs.bfl.ai/guides/prompting_unified_basics). Sintaxis especial: comillas para texto/diálogo, "image 1"/"image 2" por posición, hex, cajas `[top,left,bottom,right]` (rejilla 0-1000) al final, timecodes `[0.0s-2.5s]`, `HARD CUT` entre planos [[9]](https://docs.bfl.ai/flux_3/flux3_image).

```text
Imagen (texto-a-imagen o edición):
[medio/estilo], [sujeto y pose/acción], [entorno], [luz y efecto], [color/hex],
[detalle pequeño visible] [, cajas opcionales: [top,left,bottom,right] por elemento]

Vídeo (una toma, "one-liner"):
[encuadre/cámara] de [sujeto] [acción] en [entorno]. [detalle de movimiento y atmósfera].
Diálogo/voiceover: ["frase exacta"] — [quién habla]. Ambiente: [...]. Efectos: [...]. Música: [...].

Vídeo (varios planos, esquema de 6 campos):
Core summary: [frase con quién, dónde y el arco completo] | Scene: [entorno, luz, profundidad]
Subject description: [sujeto fijo, igual en todos los planos] | Dynamic narrative (con timecodes): [cámara+acción]
Audio: [paisaje sonoro] | Style & color: [realismo, paleta, grano]
```

Longitud: una frase basta para explorar; 40-120 palabras en vídeo y 20-80 en imagen dan control fino [[11]](https://docs.bfl.ai/guides/prompting_video_text_to_video).

## Plantillas por tarea

### Text-to-image
**Plantilla**: `[medio/estilo], [sujeto y pose], [entorno], [luz], [paleta/hex], [detalle de composición] [, cajas [top,left,bottom,right] por elemento si necesitas layout exacto]`

**Ejemplo cinematográfico**: `"Grainy 35mm film still, two brothers, Mateo and Vidal, sit on a rusted pickup truck tailgate at dusk on a dirt road, Mateo in a faded denim jacket laughing, Vidal quiet beside him looking at the horizon, warm amber backlight from the setting sun, dust haze in the air, cool blue shadows creeping in from the left"`.

**Ejemplo producto/estilizado**: `"Minimalist product photography, a single ceramic coffee cup on a concrete pedestal, soft studio top light, the color of the cup starts with #3a2e2a at the base and finishes with #c9a66b near the rim, small steam wisp rising, clean grey gradient background"` [[3]](https://docs.bfl.ai/flux_2/flux2_overview).

**Diferencias por variante**: `[klein]` no expande el prompt internamente — detalla a mano posición, luz y color o el resultado sale genérico [[3]](https://docs.bfl.ai/flux_2/flux2_overview). `[max]`/FLUX 3 Image con `grounding` activo pueden incorporar datos reales recientes (marcador, clima, evento) sin que los describas pixel a pixel [[9]](https://docs.bfl.ai/flux_3/flux3_image). Para layout exacto por caja, usa FLUX 3 Image o Kontext; en `[klein]`/`schnell` las cajas no están documentadas [sin verificar].

**No pongas**: adjetivos vacíos como "beautiful", "masterpiece" o "iconic" sin referente visual [[13]](https://docs.bfl.ai/guides/prompting_unified_basics); negaciones sueltas tipo "sin fondo feo" (describe el fondo que sí quieres); varias paletas de color sin indicar a qué objeto corresponde cada hex; texto a renderizar sin comillas.

### Image edit
**Plantilla**: `[cambio concreto] sobre [elemento identificado por posición o descripción]. [qué debe permanecer igual].`

**Ejemplo cinematográfico**: `"On image 1, change only Vidal's jacket to a weathered olive-green field coat; keep his pose, the lighting and Mateo exactly as they are."`

**Ejemplo producto**: `"Change the color of only the ceramic cup to #e01075; keep the pedestal, the lighting and the background unchanged"` [[5]](https://docs.bfl.ai/guides/prompting_editing_overview).

**Diferencias por variante**: Kontext (FLUX.1) y FLUX.2/FLUX 3 Image comparten el mismo contrato de edición (sin modo, sin máscara ni fuerza: todo va en el prompt) [[5]](https://docs.bfl.ai/guides/prompting_editing_overview); FLUX 3 Image añade cajas al final del prompt para recolorear, mover, reemplazar o quitar un elemento en una región exacta cuando el texto solo no basta por tener varios objetos similares [[9]](https://docs.bfl.ai/flux_3/flux3_image).

**No pongas**: varios cambios distintos encajados en una sola instrucción larga — itera paso a paso en vez de acumular (p. ej. Kontext: `"Remove the thing from her face"` luego, sobre el resultado, `"She is now taking a selfie in the streets of Freiburg"`) [[1]](https://bfl.ai/models/flux-kontext); un campo `mode`, `mask` o `strength` (no existen, van en el prompt) [[5]](https://docs.bfl.ai/guides/prompting_editing_overview); referencias sin nombrar su posición cuando hay más de una imagen.

### Multi-referencia
**Plantilla**: `[qué aporta image 1: sujeto/escena], [qué aporta image 2: estilo/objeto/pose], ..., [cómo se combinan]`

**Ejemplo cinematográfico**: `"Image 1 is the scene: a dim kitchen at night. Image 2 is Mateo's face and build. Image 3 is Vidal's face and build. Place both brothers at the kitchen table from image 1, Mateo on the left pouring coffee, Vidal on the right reading a letter, keeping each one's face from their reference image."`

**Ejemplo producto**: `"Image 1 is the subject: a leather watch. Image 2 is the style: a warm sunset studio backdrop. Turn image 1 in the style of image 2"` [[5]](https://docs.bfl.ai/guides/prompting_editing_overview).

**Diferencias por variante**: FLUX.2 admite hasta 8 referencias por API (10 en playground); FLUX 3 Image documenta hasta 10, cada una entre 256x256 px y 16MP [[3]](https://docs.bfl.ai/flux_2/flux2_overview)[[9]](https://docs.bfl.ai/flux_3/flux3_image). El `aspect_ratio` por defecto ("auto") sigue a la primera imagen de la lista: pon primero la que define el encuadre que quieres conservar, o fíjalo explícito [[5]](https://docs.bfl.ai/guides/prompting_editing_overview). [sin verificar] estabilidad con más de 2-3 personajes distintos simultáneos en una sola composición.

**No pongas**: referencias sin rol explícito ("image 2" sin decir si es estilo, sujeto u objeto); más de 2-3 personajes nuevos en una sola composición sin verificar antes el resultado; mezclar referencias de estilo y de identidad sin aclarar cuál manda en caso de conflicto.

### Text-to-video
**Plantilla (una toma)**: `[encuadre/cámara] de [sujeto] [acción] en [entorno]. [atmósfera y detalle de movimiento].`
**Plantilla (varios planos)**: `SHOT ONE: [plano uno]. HARD CUT. SHOT TWO: [plano dos]. HARD CUT. SHOT THREE: [plano tres]. [nota de continuidad de audio/estilo].`

**Ejemplo cinematográfico**: `"SHOT ONE: wide tracking shot of two brothers, Mateo and Vidal, walking down a quiet gravel road at golden hour, long shadows stretching behind them. HARD CUT. SHOT TWO: close-up on Mateo's face as he glances back at Vidal, a faint smile. Warm backlight throughout, one acoustic guitar theme carrying across both shots. Soft wind and gravel crunching underfoot."` (~55 palabras).

**Ejemplo estilizado/acción**: `"2D hand-drawn animation of a fox leaping through a paper-cut forest at dawn, bold flat colors, a quick low tracking shot following its paws kicking up stylized paper leaves, playful xylophone sting syncing to each leap."` (~40 palabras) [[8]](https://docs.bfl.ai/flux_3/flux3_video).

**Diferencias por variante**: con `draft: true` el resultado siempre sale en `hd` para iterar barato antes de confirmar en resolución final con `draft_enhance` [[8]](https://docs.bfl.ai/flux_3/flux3_video); el prompt corto deja framing/mood a la suerte, el largo da precisión pero sobrecargarlo puede volver el movimiento menos coherente — empieza corto y añade solo lo que quieras fijar [[11]](https://docs.bfl.ai/guides/prompting_video_text_to_video).

**No pongas**: más de 2-3 beats de acción en un clip corto sin `HARD CUT` entre ellos (se mezclan); vocabulario de cámara apilado sin intención, p. ej. "low aerial handheld orbit push-in" en vez de un término de encuadre y uno de movimiento [[10]](https://docs.bfl.ai/guides/prompting_video_camera_terms); una lista de exclusiones (no hay `negative_prompt`: dilo en positivo) [[13]](https://docs.bfl.ai/guides/prompting_unified_basics); un diálogo citado sin decir quién habla (se lee como texto en pantalla) [[12]](https://docs.bfl.ai/guides/prompting_video_audio).

### Image-to-video
Variante **Startframe** (una imagen fija el primer frame exacto; el prompt dirige el movimiento hacia delante) [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).

**Plantilla**: `[verbo de movimiento/cámara], [hacia dónde o cómo evoluciona la acción]. [atmósfera, sin redescribir el aspecto de la imagen].`

**Ejemplo cinematográfico** (partiendo de una foto fija de Mateo y Vidal sentados en el porche): `"Slow push-in as Vidal turns his head toward Mateo and starts speaking. A light breeze moves through the trees behind them. Warm late-afternoon light holds steady, soft ambient cicada sound rising slightly."` (~35 palabras).

**Ejemplo producto/acción** (partiendo de una foto fija de un coche parado): `"Handheld tracking shot as the car pulls away fast, dust kicking up behind the tires, camera whipping slightly to follow, engine roar building and fading into the distance."` (~28 palabras) [[8]](https://docs.bfl.ai/flux_3/flux3_video).

**Diferencias por variante**: Startframe es el modo más literal (preserva el primer frame exacto); si en cambio quieres fijar también el frame final o varios puntos intermedios, usa Start/end frame o Keyframes (sección siguiente) en lugar de forzar Startframe con una descripción larga del destino [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).

**No pongas**: una redescripción de cómo es la imagen (ya la tiene; solo dirige el movimiento) [[15]](https://docs.bfl.ai/guides/prompting_video_overview); cambios de identidad, vestuario o escenario que contradigan la foto de partida; más de un salto de cámara brusco en un clip corto.

### Start/end frame (incluye Keyframes)
Dentro del mismo modo `i2v`: **Start + end frame** (dos imágenes fijan la primera y última toma; FLUX 3 interpola el movimiento entre ambas) y **Keyframes** (tres o más imágenes, o pares `[segundos, imagen]`, como puntos de paso ordenados de una sola toma continua) [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).

**Plantilla (start+end)**: `[qué transición ocurre entre el primer y el último fotograma]. [ritmo/duración de la transición].`
**Plantilla (keyframes)**: `[qué pasa en cada tramo entre un keyframe y el siguiente, en orden]. [consistencia de look entre tramos].`

**Ejemplo cinematográfico (start+end)**: partiendo de una foto de Mateo solo en la cocina de noche (start) y una foto de Mateo y Vidal juntos a la mesa, de día (end): `"Night fades gradually into morning: the warm kitchen lamp dims as cold blue dawn light grows through the window, and Vidal quietly enters to sit across from Mateo as the room brightens."` (~34 palabras).

**Ejemplo estilizado (keyframes, 3 puntos a 0s/2.5s/5s)**: `"A seed on bare soil grows into a sapling, then into a full tree: steady time-lapse push-in, leaves unfurling and branches thickening smoothly between each stage, consistent soft morning light throughout."` (~30 palabras) [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).

**Diferencias por variante**: cuanto más distintas sean las dos imágenes de un start+end, más libertad (y menos previsibilidad) tiene el modelo para rellenar el intermedio — mantén mismo sujeto/escena/cámara entre ambas si quieres un resultado controlado [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video); las solicitudes con timestamps o 3+ keyframes necesitan `duration` explícita [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).

**No pongas**: una descripción distinta de cada imagen de referencia en vez de la transición entre ellas (eso ya lo "ve" el modelo); keyframes con looks visuales distintos entre sí sin motivo narrativo (se leerá como corte, no como una sola toma) [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video); una duración objetivo sin fijar el parámetro `duration` cuando usas 3+ keyframes.

### Video extend
Modo `v2v` ("Video Continuation"): se envía un clip existente como `start_video`; el prompt describe cómo sigue la acción sin cortar, heredando movimiento, sujetos y look del clip original [[15]](https://docs.bfl.ai/guides/prompting_video_overview).

**Plantilla**: `[qué hace el sujeto a continuación, en el mismo tono y cámara del clip]. [cómo evoluciona la atmósfera, sin redescribir lo ya filmado].`

**Ejemplo cinematográfico** (continuando un clip donde Mateo y Vidal caminan por un pasillo): `"Vidal reaches the door at the end of the hallway and pushes it open; the camera keeps following close behind as warm sunlight floods in from outside, Mateo close behind him."` (~32 palabras).

**Ejemplo acción** (continuando un clip de un SUV derrapando): `"The SUV swings through another wide slide toward the camera, dust boiling off its tires as it powers past the unfinished high-rises behind it."` (~24 palabras) [[8]](https://docs.bfl.ai/flux_3/flux3_video).

**Diferencias por variante**: no hay variante dedicada — es el modo `v2v` del mismo `flux-3-video`; la fidelidad depende de que el clip de origen tenga suficiente movimiento/calidad reconocibles en sus últimos fotogramas [[8]](https://docs.bfl.ai/flux_3/flux3_video).

**No pongas**: una redescripción completa de la escena ya filmada (el clip ya la define: solo dirige lo que pasa después); un cambio de identidad, vestuario o encuadre que rompa la continuidad con el clip de origen; varios saltos narrativos grandes en una sola extensión corta.

### Video edit
Herramienta separada `FLUX Video Edit [fast]` (`video-edit-v1`): se envía el clip en `video` y la instrucción en `prompt`; todo lo que el prompt no mencione se queda igual que en el original (duración, encuadre, cámara, timing y audio) [[16]](https://docs.bfl.ai/flux_tools/flux_video_edit).

**Plantilla**: `[qué cambiar: quitar/añadir/sustituir/restilizar/cambiar diálogo]. [dónde y con qué aspecto, si hace falta precisión]. [qué debe permanecer igual].`

**Ejemplo cinematográfico**: `"Add a second figure, Vidal, walking into frame from the right side of the hallway to join Mateo; keep the camera movement, lighting and pace of the original clip exactly as filmed."`

**Ejemplo producto/restyle**: `"Turn the illustrated claw-machine scene into a photographic-looking scene: a purple plush toy with soft fabric texture, visible seams, and a metal and glass claw with realistic reflections"` [[14]](https://docs.bfl.ai/guides/prompting_video_editing).

**Diferencias por variante**: no hay tiers — una sola herramienta `[fast]`; para una segunda ronda de cambios, usa el resultado anterior como nueva fuente y describe el siguiente cambio, en vez de acumular varias instrucciones contra el clip original [[14]](https://docs.bfl.ai/guides/prompting_video_editing).

**No pongas**: una instrucción que pida cambiar duración, resolución o aspect ratio (se heredan del origen y el endpoint los rechaza) [[16]](https://docs.bfl.ai/flux_tools/flux_video_edit); un nuevo diálogo más largo de lo que dura la línea original (se corta, no se alarga) [[14]](https://docs.bfl.ai/guides/prompting_video_editing); una imagen, vídeo o máscara de referencia (no soportado: usa video extend para continuar un clip) [[16]](https://docs.bfl.ai/flux_tools/flux_video_edit); varios cambios acumulados en una sola instrucción larga en vez de iterar sobre el resultado anterior.

## Idioma, longitud y prompt negativo
Idioma óptimo: inglés (los ejemplos oficiales de Black Forest Labs están en inglés); prosa natural, sin listas de etiquetas. Prompt negativo:
No existe `negative_prompt` en imagen ni en vídeo: lo que no quieras va en positivo [[13]](https://docs.bfl.ai/guides/prompting_unified_basics)[[8]](https://docs.bfl.ai/flux_3/flux3_video). En audio, nombra la fuente que sí quieres en vez de "silencio" [[12]](https://docs.bfl.ai/guides/prompting_video_audio). Longitud/idioma: ver Estructura.

## Referencias e imagen inicial
Nombra cada imagen por posición ("image 1", "image 2") con rol explícito (sujeto, estilo, producto, escena); el orden fija el `aspect_ratio` por defecto [[5]](https://docs.bfl.ai/guides/prompting_editing_overview). En vídeo, `keyframes` cubre Startframe/Start+end/Keyframes (ver Image-to-video). Dirige el movimiento o la transición; no redescribas la imagen de partida [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).

## Cámara y movimiento
FLUX 3 Video entiende vocabulario extenso: plano (close-up, wide shot), ángulo (low angle, bird's eye), movimiento (dolly in, tracking shot, handheld), foco (rack focus), óptica (anamorphic, fisheye) [[10]](https://docs.bfl.ai/guides/prompting_video_camera_terms). Un término de encuadre y uno de movimiento por plano; apilar varios lo hace menos legible.

## Consistencia de personaje
En imagen, hasta 8-10 referencias simultáneas mantienen personaje, producto o estilo entre escenas [[3]](https://docs.bfl.ai/flux_2/flux2_overview)[[9]](https://docs.bfl.ai/flux_3/flux3_image). En vídeo no hay `seed`: reutiliza la misma imagen como `keyframes` (Startframe) [[8]](https://docs.bfl.ai/flux_3/flux3_video); el esquema de 6 campos fija una "Subject description" idéntica en todos los planos [[11]](https://docs.bfl.ai/guides/prompting_video_text_to_video). [sin verificar] estabilidad con 3+ personajes en un clip largo.

## Audio y diálogo
FLUX 3 Video genera audio sincronizado por defecto (desactivable con `generate_audio: false`) [[8]](https://docs.bfl.ai/flux_3/flux3_video). Hasta cuatro capas si importan: habla, ambiente, efectos y música [[12]](https://docs.bfl.ai/guides/prompting_video_audio). Palabras exactas entre comillas con quién habla: hablante visible da lipsync automático; sin hablante ni "voiceover"/"narration" puede leerse como texto en pantalla. Anclas concretas (edad/acento, registro, actitud), no adjetivos genéricos. En varios idiomas en una voz, etiqueta cada línea y aclara que es el mismo hablante [[12]](https://docs.bfl.ai/guides/prompting_video_audio).

## Restricciones e incompatibilidades conocidas
- FLUX Video Edit: origen hasta 15s/50MiB, mínimo ~17 fotogramas a 24fps, >720p se reescala a 720p, sin referencia ni máscara, sin cambiar duración/resolución/aspecto [[16]](https://docs.bfl.ai/flux_tools/flux_video_edit).
- FLUX 3 Video: duración 5-20s o `auto`, aspectos fijos (schema de Nexus); `Error`/`Request Moderated`/`Content Moderated` son terminales [[8]](https://docs.bfl.ai/flux_3/flux3_video).
- FLUX 3 Image: referencias 256x256 px-16MP, hasta 10; sin `mode`, `mask` ni `strength` [[9]](https://docs.bfl.ai/flux_3/flux3_image)[[13]](https://docs.bfl.ai/guides/prompting_unified_basics).

## Errores habituales
- Tratar una lista de exclusiones como campo separado: en FLUX va en el prompt positivo [[13]](https://docs.bfl.ai/guides/prompting_unified_basics).
- Diálogo sin decir quién habla, o guion más largo que el plano: se lee como texto, no voz [[12]](https://docs.bfl.ai/guides/prompting_video_audio).
- Varios cambios en una instrucción larga en vez de iterar, o pedir duración/aspecto distintos en `video-edit-v1` (los hereda del origen) [[5]](https://docs.bfl.ai/guides/prompting_editing_overview)[[16]](https://docs.bfl.ai/flux_tools/flux_video_edit).
- Redescribir la imagen/vídeo de partida en i2v/v2v en vez de dirigir solo el movimiento [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).

## Recomendaciones de prompting
- Empieza corto, añade detalle solo donde quieras control explícito (posición, luz, hex, cámara) [[13]](https://docs.bfl.ai/guides/prompting_unified_basics).
- Palabras exactas entre comillas y describe aparte dónde van [[13]](https://docs.bfl.ai/guides/prompting_unified_basics).
- En edición itera paso a paso sobre el resultado anterior [[5]](https://docs.bfl.ai/guides/prompting_editing_overview).
- En i2v elige Startframe/Start+end/Keyframes según cuánto movimiento fijar [[9]](https://docs.bfl.ai/guides/prompting_video_image_to_video).
- Un término de encuadre y uno de movimiento por plano, `HARD CUT`/timesteps para timing, hablante o "voiceover" nombrado [[11]](https://docs.bfl.ai/guides/prompting_video_text_to_video)[[12]](https://docs.bfl.ai/guides/prompting_video_audio).
- Usa `flux-schnell`/`[klein]`/`draft` para iterar; sube a `flux-2-pro`/`[max]`/`flux-3-video` en la toma final.

## Fuentes y fecha
Consultadas el 2026-10-02.
[1] FLUX Kontext — https://bfl.ai/models/flux-kontext
[2] FLUX.2 — https://bfl.ai/models/flux-2
[3] FLUX.2 Overview — https://docs.bfl.ai/flux_2/flux2_overview
[4] FLUX.2 Text to Image — https://docs.bfl.ai/flux_2/flux2_text_to_image
[5] Image editing overview — https://docs.bfl.ai/guides/prompting_editing_overview
[6] FLUX 3 blog — https://bfl.ai/blog/flux-3
[7] FLUX 3 Video, Part 1 — https://bfl.ai/blog/flux-3-video
[8] Video (FLUX 3) API — https://docs.bfl.ai/flux_3/flux3_video
[9] FLUX 3 Image / Image-to-Video — https://docs.bfl.ai/flux_3/flux3_image, https://docs.bfl.ai/guides/prompting_video_image_to_video
[10] Camera terms — https://docs.bfl.ai/guides/prompting_video_camera_terms
[11] Text-to-Video — https://docs.bfl.ai/guides/prompting_video_text_to_video
[12] Audio and speech — https://docs.bfl.ai/guides/prompting_video_audio
[13] Prompting Basics — https://docs.bfl.ai/guides/prompting_unified_basics
[14] Video Editing — https://docs.bfl.ai/guides/prompting_video_editing
[15] Video Generation — https://docs.bfl.ai/guides/prompting_video_overview
[16] FLUX Video Edit — https://docs.bfl.ai/flux_tools/flux_video_edit
[17] Flux-Dev (Atlas Cloud) — https://www.atlascloud.ai/models/black-forest-labs/flux-dev
[18] Flux-Schnell (Atlas Cloud) — https://www.atlascloud.ai/models/black-forest-labs/flux-schnell
[19] Flux-Kontext-Dev (Atlas Cloud) — https://www.atlascloud.ai/models/black-forest-labs/flux-kontext-dev
