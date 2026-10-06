---
id: minimax
kind: model
title: 'MiniMax Hailuo / H3 (vídeo)'
version: 2
updated: 2026-10-02
match:
  - '^minimax/(hailuo|h3)'
  - '^crun/minimax'
  - '^runway/(hailuo|h3)'
modalities: [VIDEO]
strengths: [prompt_adherence, physics, dynamic_action, native_audio, text_rendering, camera_control, fast]
weaknesses: [long_duration]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, motion_control]
avoid_tasks: [lipsync, avatar]
unverified_tags: [face_consistency, multi_character]
sources:
  - url: https://www.minimax.io/blog/minimax-h3
    title: 'MiniMax H3: An Open Model Breaking the Boundaries Between Tasks and Modalities'
    accessed: 2026-10-02
  - url: https://www.minimax.io/news/minimax-hailuo-23
    title: 'MiniMax Hailuo 2.3: A New Level of Complex Video Performance & Media Agent'
    accessed: 2026-10-02
  - url: https://www.minimax.io/news/minimax-hailuo-02
    title: 'MiniMax Hailuo 02, World-Class Quality, Record-Breaking Cost Efficiency'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3/text-to-video
    title: 'MiniMax H3 Text-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3/image-to-video
    title: 'MiniMax H3 Image-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3/reference-to-video
    title: 'MiniMax H3 Reference-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-fast/reference-to-video
    title: 'MiniMax H3 Fast Reference-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-fast/text-to-video
    title: 'MiniMax H3 Fast Text-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-fast/image-to-video
    title: 'MiniMax H3 Fast Image-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-max/text-to-video
    title: 'MiniMax H3 Max Text-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-max/image-to-video
    title: 'MiniMax H3 Max Image-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-max-turbo/text-to-video
    title: 'MiniMax H3 Max Turbo Text-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-max-turbo/image-to-video
    title: 'MiniMax H3 Max Turbo Image-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/h3-developer/image-to-video
    title: 'MiniMax H3-Developer Image-to-Video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/hailuo-2.3/fast
    title: 'MiniMax Hailuo 2.3 — Fast Model'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro
    title: 'MiniMax Hailuo 2.3 — Image-to-Video Pro'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/hailuo-2.3/t2v-pro
    title: 'MiniMax Hailuo 2.3 — Text-to-Video Pro'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/minimax/hailuo-02/pro
    title: 'MiniMax Hailuo 02 Pro'
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/guides/models/
    title: 'Runway — Models (Guides)'
    accessed: 2026-10-02
---

## Resumen
MiniMax (Hailuo AI) tiene dos líneas de vídeo: **Hailuo** (02/2.3, física/acción, fotorrealista) y **H3**, omni-modal (texto+imagen+vídeo+audio en un contexto, audio estéreo nativo). Destaca en instrucciones complejas, física fiel y, solo H3, audio sincronizado y texto/logo legible sin modelo aparte. En Runway, `hailuo3` es **H3** (no "Hailuo 3") y `h3_max` es **H3 Max** — ver "Linaje".

## Cuándo elegirlo y cuándo no
- **Hailuo 2.3** (pro/standard): física corporal compleja, micro-expresiones faciales, estilos no fotorrealistas (anime, ilustración, tinta); plano único 6-10s, sin audio. `t2v-pro`: 5s fijos solo-texto; `i2v-pro`: imagen + prompt opcional, encuadre/identidad estables [[16]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro) [[17]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/t2v-pro).
- **H3** (`minimax/h3*`, `crun/minimax-h3`, `runway/hailuo3`): varias fuentes de contexto (imagen+vídeo+audio), texto/logo legible, frame inicial *y* final en una sola imagen-a-vídeo, o audio sincronizado incluido.
- **H3 Fast / H3 Max Turbo** o **Hailuo 2.3 Fast**: iterar movimiento barato antes de un render final en `h3` (2K) o `h3-developer` (ESR hasta 4K); Hailuo 2.3 Fast añade "prompt expansion" automática [[15]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/fast).
- Mejor **Kling** (Motion Control dedicado `character_orientation`, o edición de vídeo por texto sin reconstruir el clip); **Seedance** (secuencia multi-plano con transición de estilo, sin audio nativo); **Wan** (presupuesto ajustado, t2v/i2v simple); **Runway Gen-4.5** (consistencia de personaje/mundo entre tomas distintas desde una sola imagen).

| Necesidad | Variante recomendada |
|---|---|
| Física de acción exigente, no fotorrealista | `minimax/hailuo-2.3/i2v-pro` / `t2v-pro` |
| Animar imagen con audio, bajo coste | `minimax/h3-fast/image-to-video` |
| Texto/logo legible en pantalla | `minimax/h3/*` o `h3-developer/*` |
| Sujeto consistente desde fotos/vídeos/audio | `minimax/h3/reference-to-video` |
| Frame inicial + final en la misma generación | `minimax/h3*/image-to-video` (cualquier tier) |
| Transferir movimiento desde vídeo existente | `minimax/h3/reference-to-video` (vídeo como referencia) |
| Resolución final 4K | `minimax/h3-developer/*` (ESR) |
| Iterar movimiento rápido y barato | `minimax/h3-max-turbo/*` o `hailuo-2.3/fast` |
| Vía Runway con otros modelos | `runway/hailuo3/*` (=H3) / `runway/h3_max/*` |

## Linaje y variantes en Nexus
1. **Hailuo 02**: 1080P nativo (antes 720P), 6s/10s, física extrema (gimnasia, escombros, rebotes, sacudidas de cámara) vía "Noise-aware Compute Redistribution" [[3]](https://www.minimax.io/news/minimax-hailuo-02) [[18]](https://www.atlascloud.ai/models/minimax/hailuo-02/pro). Nexus: `minimax/hailuo-02/{standard,pro,fast}`, `t2v-*`/`i2v-*`.
2. **Hailuo 2.3** (generación Hailuo más reciente): mejora expresión dinámica, micro-expresiones faciales, respuesta a movimiento y estilos (anime, ilustración, "ink wash", game CG); mismo precio que 02, nuevo tier **Fast** [[2]](https://www.minimax.io/news/minimax-hailuo-23). Nexus: `minimax/hailuo-2.3/{t2v,i2v}-{standard,pro}`, `minimax/hailuo-2.3/fast`. `t2v-pro`: 5s fijos solo-texto; `i2v-pro`: imagen+prompt opcional, "stable composition" (encuadre/color estables en el clip) [[16]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro) [[17]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/t2v-pro); Fast: prompt expansion automática, comprobador de seguridad, 6-10s/1080p [[15]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/fast). **Recomiéndala por defecto sobre Hailuo 02.**
3. **H3** (lanzamiento 2026-07-31, el vídeo más reciente del fabricante): omni-modal, contexto unificado texto+imagen+vídeo+audio, audio estéreo nativo hasta 2K/15s, transferencia de movimiento V2V [[1]](https://www.minimax.io/blog/minimax-h3). H3 fusiona en un modelo tareas antes repartidas (t2v, i2v, frame inicial/final, referencia sujeto/movimiento/voz, edición); en Nexus son tres endpoints por tier (t2v, i2v, reference-to-video); el resto se activa según qué adjuntes. Tiers:
   - `minimax/h3/*`: hasta 2K, 4-15s, t2v/i2v/reference-to-video, frame inicial+final opcional en i2v [[4]](https://www.atlascloud.ai/models/minimax/h3/text-to-video) [[5]](https://www.atlascloud.ai/models/minimax/h3/image-to-video) [[6]](https://www.atlascloud.ai/models/minimax/h3/reference-to-video).
   - `minimax/h3-fast/*`: 480P, tier más barato/rápido, mismas tres tareas y frame inicial+final [[7]](https://www.atlascloud.ai/models/minimax/h3-fast/text-to-video) [[9]](https://www.atlascloud.ai/models/minimax/h3-fast/image-to-video).
   - `minimax/h3-max/*`: 480P/768P, casi instantáneo (5s en <3s de cómputo); solo t2v/i2v [[10]](https://www.atlascloud.ai/models/minimax/h3-max/text-to-video) [[11]](https://www.atlascloud.ai/models/minimax/h3-max/image-to-video). `h3-max-turbo/*`: menor coste/s, también solo t2v/i2v [[12]](https://www.atlascloud.ai/models/minimax/h3-max-turbo/text-to-video) [[13]](https://www.atlascloud.ai/models/minimax/h3-max-turbo/image-to-video).
   - `minimax/h3-developer/*`: 480P/768P con ESR opcional a 1080P/1440P/4K; t2v/i2v/reference-to-video [[14]](https://www.atlascloud.ai/models/minimax/h3-developer/image-to-video). "Developer" suele ser endpoint directo del fabricante con otro contrato — [sin verificar].
   - `crun/minimax-h3`: mismo H3 vía Crun (`h3-t2v`, `h3-i2v`, `h3-r2v`), más `crun/minimax/h3-regeneration` (apunta a "In-Context Regeneration" de H3) [[1]](https://www.minimax.io/blog/minimax-h3) — [sin verificar] como task distinta; confirma en el schema.
4. **Runway:** IDs propios distintos a MiniMax: `hailuo3` = **H3**, `h3_max` = **H3 Max** [[19]](https://docs.dev.runwayml.com/guides/models/). Nexus: `runway/hailuo3/{i2v,t2v,v2v}`, `runway/h3_max/{i2v,t2v}`. **No confundir con "Hailuo 2.3"**: pese al nombre, es H3. Sin guía de prompting propia.

## Fortalezas
- **Seguimiento de instrucciones:** Hailuo 02 declara 85% de respuesta a instrucciones complejas; H3 fuerte en "instruction following"; Hailuo 2.3 Pro añade "high prompt accuracy" [[3]](https://www.minimax.io/news/minimax-hailuo-02) [[17]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/t2v-pro).
- **Física y acción:** Hailuo 02 se posicionó único capaz de gimnasia intrincada (escombros, rebotes, sacudidas de cámara) [[3]](https://www.minimax.io/news/minimax-hailuo-02) [[18]](https://www.atlascloud.ai/models/minimax/hailuo-02/pro); Hailuo 2.3 mejora movimientos corporales complejos y reflejos "orgánicos" en i2v-pro [[2]](https://www.minimax.io/news/minimax-hailuo-23) [[16]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro).
- **Audio nativo:** H3 genera audio estéreo sincronizado (ambiente, efectos, música) sin input de audio aparte, en t2v/i2v/reference-to-video [[1]](https://www.minimax.io/blog/minimax-h3) [[4]](https://www.atlascloud.ai/models/minimax/h3/text-to-video).
- **Texto en pantalla:** H3 se anuncia con "accurate text and brand rendering" [[1]](https://www.minimax.io/blog/minimax-h3).
- **Frame inicial y final integrado:** todos los tiers de H3 image-to-video aceptan frame final opcional en la misma llamada, sin endpoint aparte [[5]](https://www.atlascloud.ai/models/minimax/h3/image-to-video).
- **Cámara y edición por referencia (V2V):** H3 "excelling at... V2V motion transfer" con "precise, controllable multimodal generation and editing" — transfiere movimiento desde un vídeo de referencia o úsalo de base para edición por texto [[1]](https://www.minimax.io/blog/minimax-h3).
- **Velocidad:** `h3-fast`/`h3-max-turbo` son el tier más rápido/económico de H3 (5s en <3s de cómputo); `hailuo-2.3/fast` reduce coste/tiempo hasta 50% [[2]](https://www.minimax.io/news/minimax-hailuo-23) [[11]](https://www.atlascloud.ai/models/minimax/h3-max/image-to-video).

## Debilidades
- **Duración limitada:** máximo documentado 15s (H3 base/fast/developer), 10s (Hailuo 02/2.3) o 5s fijos (`hailuo-2.3/t2v-pro`); sin clips largos nativos. Para >15s, planea cortes/extensión aparte.
- **Sin edición de vídeo dedicada:** a diferencia de Kling, MiniMax no expone `video_edit` propio; se aproxima con el clip como referencia en `reference_to_video` — [sin verificar] su alcance real fuera de "V2V motion transfer".
- Cifras exactas de resolución/duración las fija el schema en vivo (`get_model_schema`); no las prometas de memoria.

## Tareas ideales / Tareas que debe evitar
- **Ideal:** `text_to_video`/`image_to_video` para acción/física exigente (Hailuo) o texto en pantalla y frame inicial+final (H3); `reference_to_video` para anclar identidad desde imágenes/vídeos/audio mixtos (H3/H3 Fast); `motion_control` vía transferencia de movimiento desde vídeo de referencia (H3).
- **Evitar:** `lipsync`/`avatar` — sin evidencia de doblaje labial o avatar dedicado (los productos de voz/música de MiniMax quedan fuera de esta skill). `video_edit` tampoco existe como endpoint propio: para editar un clip grabado, prefiere Kling O3 video-edit.

## Estructura de prompt recomendada
Prosa natural en inglés (documentación y ejemplos oficiales en inglés), frases cortas y concretas, sin etiquetas sueltas ni `--parámetros` de Midjourney (MiniMax no los interpreta; duración/resolución/aspect_ratio van en el schema). Longitud ideal: 1-3 frases (≈25-70 palabras t2v/i2v; hasta 90-120 si nombras varias referencias). Sintaxis: en H3 con varias fuentes, nombra cada una por tipo y número ("Video 1", "Image 2", "Audio 3") y di qué aporta — único patrón documentado; para texto en pantalla, escribe el texto exacto entre comillas.

```
[Sujeto(s): quién/qué, 1 rasgo distintivo si no hay imagen/referencia] +
[acción física concreta, verbo de movimiento] +
[cámara: tipo de plano + movimiento, o "match the camera from Video N" si hay referencia] +
[entorno y luz] +
[estilo/tono: cinematográfico, anime, producto, etc.] +
[opcional: "the text/sign reads '...'" para texto en pantalla; nota breve de audio/música si quieres dirigir el soundtrack generado]
```

## Plantillas por tarea

### Text-to-video (Hailuo 2.3 / H3)
`[Tipo de plano] + [Sujeto + rasgo distintivo] + [acción física concreta, verbo de movimiento] + [cámara/encuadre] + [entorno y luz] + [estilo/tono] + [opcional: texto en pantalla entre comillas, solo H3]`
`t2v-pro` de Hailuo 2.3: 5s fijos solo-texto, un único beat [[17]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/t2v-pro). En H3 añade frase final de sonido/música deseado; el audio se genera junto al vídeo sin pista aparte [[4]](https://www.atlascloud.ai/models/minimax/h3/text-to-video).
- Ejemplo cinematográfico: "Medium shot, two brothers, Mateo and Vidal, stand on a rain-soaked rooftop at dusk; Mateo lights a cigarette and Vidal places a hand on his shoulder as they watch the city lights below, camera slowly cranes up and back, warm neon reflections on wet concrete, moody cinematic look, distant traffic hum and soft rain ambience."
- Ejemplo distinto (producto, texto en pantalla, solo H3): "Close-up, a chrome sports watch spins slowly on a black marble pedestal under soft blue rim lighting; the camera pulls back to reveal a sleek showroom, and a neon sign behind it reads 'TIME REDEFINED', glossy commercial look, subtle synth music building underneath."
No pongas: parámetros `--ar 16:9` de Midjourney [sin verificar en esta familia]; duraciones o "4K"/"60fps" como texto (van en el schema); más de 2 beats narrativos en un plano único de 5-10s; texto en pantalla si usas Hailuo 02/2.3 (ninguna de las dos lo garantiza, resérvalo para H3).

### Image-to-video (todas las variantes H3 y Hailuo 02/2.3)
`[Qué debe animarse] + [acción/movimiento concreto] + [cámara] + [si subes frame final: a qué estado debe llegar la escena] + [opcional, H3: nota de audio]`
En Hailuo 2.3 `i2v-pro` el prompt es opcional (movimiento por defecto si lo omites), encuadre/proporciones/tono estables [[16]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro). En H3, si subes frame final, describe la transición completa, no solo el arranque [[5]](https://www.atlascloud.ai/models/minimax/h3/image-to-video).
- Ejemplo cinematográfico (imagen de partida: foto de Mateo y Vidal sentados a una mesa): "Mateo slowly turns his head toward Vidal and smiles, then Vidal laughs and leans back in his chair; slow dolly-in, warm late-afternoon light through the window, cinematic realism, soft ambient café chatter in the background."
- Ejemplo distinto (producto, frame inicial y final en H3): "The sneaker lifts off the table and rotates mid-air in slow motion while dust particles catch the light, ending centered and sharply lit exactly like the final reference image; camera orbits around it at eye level, dramatic rim lighting, high-end commercial look."
No pongas: la descripción física del sujeto que ya está en la imagen (ropa, color de pelo, rasgos); una acción final que contradiga el frame final que subiste; varios cambios de encuadre dentro de un clip de 5-10s.

### Reference-to-video (H3 / H3 Fast / H3-Developer)
`"Keep [sujeto, nombrado por fuente] consistent. Place them in [escena]. [si hay vídeo de referencia:] match the motion/camera from [Video N]. [si hay audio de referencia:] sync the action to [Audio N]."`
Admite cualquier mezcla de imágenes, vídeos y audio como anclas de identidad; el texto dirige escena y acción, las referencias fijan quién/qué debe reconocerse [[6]](https://www.atlascloud.ai/models/minimax/h3/reference-to-video) [[8]](https://www.atlascloud.ai/models/minimax/h3-fast/reference-to-video). `h3-fast` es el modo más barato para probar combinaciones antes de confirmar en `h3` (2K).
- Ejemplo cinematográfico (referencias: foto de Mateo, foto de Vidal): "Keep the man from Image 1, Mateo, and the man from Image 2, Vidal, consistent. Place them meeting at a rooftop café at sunset; they shake hands and sit down, camera slowly tracking around the table, warm golden-hour light, cinematic look."
- Ejemplo distinto (producto + referencia de ritmo): "Keep the sneaker from Image 1 consistent. Place it spinning on a rotating platform in a neon-lit studio, syncing each rotation to the beat of Audio 2, high-energy commercial style, punchy synth soundtrack."
No pongas: más de 2-3 referencias sin numerarlas por tipo ("Image 1", "Video 2", "Audio 3"); una descripción física de los sujetos que ya aportan las imágenes de referencia; una acción de escena que contradiga el movimiento del vídeo de referencia si lo hay.

### Start/end frame (dentro de image-to-video, H3 — todos los tiers)
`[Transición descrita de principio a fin, coherente con ambos fotogramas] + [cámara] + [estilo]`
No es endpoint separado: se activa subiendo frame inicial y final opcional en la misma llamada de `image-to-video`, en todos los tiers de H3 [[5]](https://www.atlascloud.ai/models/minimax/h3/image-to-video) [[9]](https://www.atlascloud.ai/models/minimax/h3-fast/image-to-video) [[11]](https://www.atlascloud.ai/models/minimax/h3-max/image-to-video). Hailuo 02/2.3 no lo documentan — [sin verificar]; usa H3.
- Ejemplo cinematográfico: "Mateo closes the door behind him in the first frame; by the final frame he is standing alone in a dimly lit hallway — show him walking forward slowly as the lights flicker, static wide shot, melancholic blue tones."
- Ejemplo distinto (producto/naturaleza): "The flower transitions from a tightly closed bud to a fully open bloom exactly as shown in the final frame; static close-up, soft diffused daylight, gentle petal movement, no camera motion."
No pongas: una acción final que contradiga el frame final subido; movimientos de cámara agresivos que dificulten reconocer el frame final; más de una transición por generación de 4-15s.

### Motion control (transferencia de movimiento V2V, dentro de reference-to-video)
`"Reference the [movimiento/cámara] from [Video N]. Apply it to [sujeto nuevo] in [escena nueva]."`
No es un parámetro dedicado como `character_orientation` en Kling: en H3 se pasa un vídeo como referencia en `reference_to_video` describiendo qué movimiento tomar de él; el fabricante lo llama "V2V motion transfer" [[1]](https://www.minimax.io/blog/minimax-h3) [[6]](https://www.atlascloud.ai/models/minimax/h3/reference-to-video).
- Ejemplo cinematográfico: "Reference the slow Hitchcock dolly-zoom from Video 1. Apply it to the detective from Image 2 walking into the warehouse at night, dim fluorescent flicker, tense atmosphere."
- Ejemplo distinto (estilizado/acción): "Reference the martial-arts combo from Video 1. Apply it to the animated mascot from Image 2 performing in a bright arena stage, dramatic spotlight, high-contrast comic-style lighting."
No pongas: una descripción de movimiento que contradiga al vídeo de referencia (el modelo prioriza el movimiento real del vídeo, no tu descripción textual); más de un vídeo de movimiento distinto en la misma generación; esperar un parámetro explícito de encuadre tipo `character_orientation` — en MiniMax se controla solo con prosa.

### Video edit
`[Qué cambiar exactamente del clip existente] + [qué debe permanecer igual]` — sube el clip original como vídeo de referencia dentro de `reference_to_video` y describe el cambio en el prompt; no hay un endpoint `video_edit` separado en el catálogo de MiniMax de Nexus.
H3 se anuncia con "precise, controllable multimodal generation and editing" junto al V2V motion transfer, pero Atlas Cloud no documenta parámetros propios de edición como Kling O3 video-edit [[1]](https://www.minimax.io/blog/minimax-h3). [sin verificar] para ediciones quirúrgicas; usa Kling O3 o Seedance si necesitas control fino.
- Ejemplo cinematográfico (aproximación vía reference-to-video, con el clip original como Video 1): "Keep the scene and camera movement from Video 1 unchanged. Replace Vidal's gray jacket with a dark green military-style coat, same pose, same rooftop background, same lighting."
- Ejemplo distinto (producto, aproximación): "Keep the scene and motion from Video 1 unchanged. Change the drink in his hand to a can of cola, keep the same lighting and background untouched."
No pongas: dar por hecho que existe un parámetro `video_edit` o `keep_original_sound` en MiniMax sin confirmarlo en el schema; varios cambios no relacionados en el mismo prompt; esperar la misma precisión quirúrgica que Kling O3 video-edit — aquí es una aproximación vía referencia, no una tarea dedicada.

## Idioma, longitud y prompt negativo
Idioma óptimo: inglés. Hailuo 2.3 Fast ofrece "automatic prompt enhancement" (tolera prompts cortos/informales) y comprobador de seguridad integrado [[15]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/fast). Sin evidencia de soporte explícito de español — [sin verificar]. Longitud: 1-3 frases en t2v/i2v; algo más larga en reference-to-video al nombrar fuentes. Sin `negative_prompt` documentado; formula exclusiones en positivo ("clean background, no overlay text") y confírmalo en el schema.

## Referencias e imagen inicial
En `image_to_video` la imagen fija el primer frame (y opcionalmente el último, en los cinco tiers de H3): describe solo el movimiento y, si subes frame final, la transición completa [[5]](https://www.atlascloud.ai/models/minimax/h3/image-to-video). En `reference_to_video` (H3/H3 Fast/H3-Developer) las referencias pueden ser imágenes/vídeos de persona/animal/objeto, y audio opcional para sincronizar la acción al ritmo [[8]](https://www.atlascloud.ai/models/minimax/h3-fast/reference-to-video). Nómbralas ("Image 1", "Video 2", "Audio 3") y di qué aporta cada una; única sintaxis de referencia documentada.

## Cámara y movimiento
H3 permite describir cámara por referencia a un vídeo existente ("the Hitchcock camera movement from Video 1"), además de prosa estándar (paneo, dolly, tracking) [[1]](https://www.minimax.io/blog/minimax-h3). Hailuo 2.3 mejora respuesta a comandos de movimiento con cámara dinámica; i2v-pro declara simular con precisión movimiento de cámara, interacciones de objetos y transiciones de profundidad [[2]](https://www.minimax.io/news/minimax-hailuo-23) [[16]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro). Sin vocabulario de cámara cerrado; usa prosa descriptiva, no tags.

## Consistencia de personaje
El reclamo oficial más fuerte es específico de `reference_to_video`: "preserve one or several people, characters, or objects' identities across the whole clip" (H3/H3 Fast/H3-Developer) [[8]](https://www.atlascloud.ai/models/minimax/h3-fast/reference-to-video). Hailuo 2.3 `i2v-pro` declara "stable composition" dentro del clip (encuadre, proporciones, tono de color), que evita que el personaje "derive" visualmente, aunque no equivale a identidad entre generaciones distintas [[16]](https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro). Para `text_to_video` puro no hay declaración equivalente — [sin verificar]. Para personaje recurrente entre planos, usa siempre la misma imagen/vídeo ancla vía `reference_to_video` en vez de repetir el prompt.

## Audio y diálogo
Solo H3 genera audio: "a matching soundtrack (ambient sound, effects, and music) is generated alongside the video — no separate audio input needed" [[4]](https://www.atlascloud.ai/models/minimax/h3/text-to-video). Describe el audio deseado en el mismo prompt; sin evidencia de diálogo con etiquetado de voces, lipsync o control de idioma de voz. En `reference_to_video` puedes subir audio real como referencia para sincronizar la acción al ritmo (no para generar diálogo) [[8]](https://www.atlascloud.ai/models/minimax/h3-fast/reference-to-video). Hailuo 02/2.3 son silenciosos.

## Restricciones e incompatibilidades conocidas
- Sin evidencia de lipsync ni avatar en ninguna variante revisada.
- Sin endpoint `video_edit` propio: se aproxima vía `reference_to_video` con el clip existente como referencia; sin parámetros de edición documentados.
- `h3-developer`: precio/endpoint "refer to other platforms", puede operar bajo contrato distinto — [sin verificar].
- `h3-max`/`h3-max-turbo` no exponen `reference_to_video` (sí h3/h3-fast/h3-developer).
- `crun/minimax/h3-regeneration`: descripción genérica idéntica a text-to-video; no asumas edición sin confirmar en el schema — [sin verificar].
- Sin moderación específica revisada; aplica el filtro general de la plataforma.

## Errores habituales
- Confundir `runway/hailuo3` (es H3) con "Hailuo 2.3"; nombres parecidos, distintos.
- Usar `h3-max`/`h3-max-turbo` para `reference_to_video`, no disponible en esas variantes.
- Pedir texto en pantalla legible en Hailuo 02/2.3; úsalo en H3.
- Tratar `video_edit` como endpoint dedicado con parámetros propios (como Kling): en MiniMax es una aproximación vía `reference_to_video`.
- Olvidar que `hailuo-2.3/t2v-pro` es 5s fijos solo-texto, y pedirle imagen (eso es `i2v-pro`).
- No numerar referencias ("Image 1", "Video 2") al combinar varias fuentes en `reference_to_video`, dejando ambiguo qué aporta cada una.

## Recomendaciones de prompting
- Verbos de movimiento concretos (gira, aterriza, se inclina): punto fuerte en física/acción.
- En H3 con varias referencias, numéralas y di qué aporta cada una ("Video 1"=cámara, "Image 2"=sujeto, "Audio 3"=ritmo).
- Texto/logo legible: usa H3 (no Hailuo), entre comillas tal como debe aparecer.
- Frame inicial y final: en los cinco tiers de H3 image-to-video sin cambiar endpoint; descríbelo como transición completa, no dos escenas sueltas.
- Para "editar" un clip grabado, súbelo como referencia en `reference_to_video`; si necesitas precisión quirúrgica, compara con Kling O3 video-edit.
- Itera movimiento en `h3-fast`/`h3-max-turbo`/`hailuo-2.3/fast` antes de un render caro
- Personaje recurrente: `reference_to_video` con la misma imagen/vídeo ancla, sin repetir prompt en `text_to_video`.
- No prometas diálogo ni lipsync: H3 solo genera ambiente/música/SFX; Hailuo 02/2.3 son silenciosos.
- Confirma en el schema qué tareas y resolución/duración expone la variante.
- Desde Runway: `hailuo3`=H3, `h3_max`=H3 Max; no es "Hailuo 3".

## Fuentes y fecha
Todas las fuentes consultadas el 2026-10-02.

[1] MiniMax — MiniMax H3: An Open Model Breaking the Boundaries Between Tasks and Modalities — https://www.minimax.io/blog/minimax-h3
[2] MiniMax — MiniMax Hailuo 2.3: A New Level of Complex Video Performance & Media Agent — https://www.minimax.io/news/minimax-hailuo-23
[3] MiniMax — MiniMax Hailuo 02, World-Class Quality, Record-Breaking Cost Efficiency — https://www.minimax.io/news/minimax-hailuo-02
[4] Atlas Cloud — H3 Text-to-Video — https://www.atlascloud.ai/models/minimax/h3/text-to-video
[5] Atlas Cloud — H3 Image-to-Video — https://www.atlascloud.ai/models/minimax/h3/image-to-video
[6] Atlas Cloud — H3 Reference-to-Video — https://www.atlascloud.ai/models/minimax/h3/reference-to-video
[7] Atlas Cloud — H3 Fast Text-to-Video — https://www.atlascloud.ai/models/minimax/h3-fast/text-to-video
[8] Atlas Cloud — H3 Fast Reference-to-Video — https://www.atlascloud.ai/models/minimax/h3-fast/reference-to-video
[9] Atlas Cloud — H3 Fast Image-to-Video — https://www.atlascloud.ai/models/minimax/h3-fast/image-to-video
[10] Atlas Cloud — H3 Max Text-to-Video — https://www.atlascloud.ai/models/minimax/h3-max/text-to-video
[11] Atlas Cloud — H3 Max Image-to-Video — https://www.atlascloud.ai/models/minimax/h3-max/image-to-video
[12] Atlas Cloud — H3 Max Turbo Text-to-Video — https://www.atlascloud.ai/models/minimax/h3-max-turbo/text-to-video
[13] Atlas Cloud — H3 Max Turbo Image-to-Video — https://www.atlascloud.ai/models/minimax/h3-max-turbo/image-to-video
[14] Atlas Cloud — H3-Developer Image-to-Video — https://www.atlascloud.ai/models/minimax/h3-developer/image-to-video
[15] Atlas Cloud — Hailuo 2.3 — Fast Model — https://www.atlascloud.ai/models/minimax/hailuo-2.3/fast
[16] Atlas Cloud — Hailuo 2.3 — Image-to-Video Pro — https://www.atlascloud.ai/models/minimax/hailuo-2.3/i2v-pro
[17] Atlas Cloud — Hailuo 2.3 — Text-to-Video Pro — https://www.atlascloud.ai/models/minimax/hailuo-2.3/t2v-pro
[18] Atlas Cloud — Hailuo 02 Pro — https://www.atlascloud.ai/models/minimax/hailuo-02/pro
[19] Runway — Models (Guides) — https://docs.dev.runwayml.com/guides/models/
