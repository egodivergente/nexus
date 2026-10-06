---
id: 'midjourney'
kind: 'model'
title: 'Midjourney vía Youchuan (悠船) — v8.1 y v8.2'
version: 2
updated: 2026-10-02
match: ['^youchuan/']
modalities: 'IMAGE'
strengths: [cinematic_look, stylized, high_resolution, fast, prompt_adherence]
weaknesses: [text_rendering, identity_preservation, multi_character]
ideal_tasks: [text_to_image, image_edit, multi_reference_image, image_to_video, layer_decomposition]
avoid_tasks: [text_to_video, reference_to_video, start_end_frame, video_edit, video_extend, motion_control, lipsync, avatar, upscale]
unverified_tags: []
sources:
  - {url: 'https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image', title: 'Atlas Cloud — Youchuan V8.2 Text-to-Image', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/youchuan/v8.1/text-to-image', title: 'Atlas Cloud — Youchuan V8.1 Text-to-Image', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video', title: 'Atlas Cloud — Youchuan V8.2 Image-to-Video', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/youchuan/v8.2/blend', title: 'Atlas Cloud — Youchuan V8.2 Blend', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/youchuan/v8.2/style-transfer', title: 'Atlas Cloud — Youchuan V8.2 Style Transfer', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/youchuan/v8.2/remove-background', title: 'Atlas Cloud — Youchuan V8.2 Remove Background', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image', title: 'Atlas Cloud — Youchuan V8.2 Image-to-Image', accessed: 2026-10-02}
  - {url: 'https://docs.midjourney.com/hc/en-us/articles/32859204029709-Parameter-List', title: 'Midjourney — Parameter List', accessed: 2026-10-02}
  - {url: 'https://blakecrosley.com/guides/midjourney', title: 'Blake Crosley — Midjourney 8.2 Guide', accessed: 2026-10-02}
---

## Resumen

Midjourney, servido en Nexus vía **Youchuan (悠船)** — socio chino autorizado por Midjourney, Inc. — destaca por estética cinematográfica reconocible, variedad artística controlable y 2K nativo sin upscaler separado [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image). Cubre texto-a-imagen, edición guiada por imagen, fusión de hasta 5 imágenes (blend), restilizado preservando composición, recorte de fondo e imagen-a-vídeo. No es un clon: es la misma tecnología de Midjourney localizada para China [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image).

## Cuándo elegirlo y cuándo no

- **Elige Midjourney/Youchuan** para variedad estética "de director" sin detallar cada parámetro técnico, concept art pulido o restyling de composición ya aprobada [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[5]](https://www.atlascloud.ai/models/youchuan/v8.2/style-transfer).
- **GPT Image** si el texto en imagen debe ser exacto o la edición muy literal — aquí la imagen de entrada es inspiración, no edición pixel-perfect [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image).
- **Nano Banana** si necesitas identidad consistente entre tomas: Youchuan no expone nada equivalente a Omni/Character Reference ni al Edit Model de V8.2 (ver Linaje) [[8]](https://docs.midjourney.com/hc/en-us/articles/32859204029709-Parameter-List)[[9]](https://blakecrosley.com/guides/midjourney).
- **Seedream/FLUX** para fotorrealismo literal con mínima interpretación o coste mínimo; incluso en "raw" Midjourney conserva sesgo estético propio [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image).
- **Ideogram** si solo importa tipografía/logo exacto: el texto mejoró ("usa comillas") pero sigue "estrechando", no cerrando, la brecha con especialistas [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[9]](https://blakecrosley.com/guides/midjourney).
- **Evita esta familia** para vídeo desde texto, lipsync, avatar, cámara fina o identidad garantizada entre generaciones [[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video).

| Necesidad | Variante recomendada |
|---|---|
| Calidad/estética reciente, texto desde cero | `youchuan/v8.2/text-to-image` |
| Reinterpretar libremente una imagen con un nuevo prompt | `youchuan/v8.2/image-to-image` |
| Restyling con la misma composición exacta | `youchuan/v8.2/style-transfer` |
| Fusionar 2-5 imágenes (personaje+escenario, paleta+sujeto) | `youchuan/v8.2/blend` |
| Cutout para compositing | `youchuan/v8.2/remove-background` |
| Animar una imagen fija (primer fotograma) | `youchuan/v8.2/image-to-video` |
| Reproducir una integración ya validada con v8.1 | `youchuan/v8.1/*` |
| Identidad de personaje garantizada entre tomas | ninguna de esta familia — usa Nano Banana |

## Linaje y variantes en Nexus

Midjourney nativo: V8.0 Alpha (retirado 24/07/2026) → V8.1 (default 14/04/2026-23/07/2026) → V8.2 (default desde 24/07/2026, foco en estética/Personalization) [[9]](https://blakecrosley.com/guides/midjourney). Nexus expone **v8.1** y **v8.2**, mismos seis endpoints, arquitectura PyTorch nativa de GPU, ~4-5× más rápidas que versiones previas (cifra del fabricante), 2K sin upscaler [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[2]](https://www.atlascloud.ai/models/youchuan/v8.1/text-to-image). Nexus no expone perfiles de Personalization, así que la diferencia práctica entre tags es de "sabor": **v8.2 es más audaz/editorial por defecto; v8.1 es más calmado** — usa v8.2 salvo que una integración ya validada dependa del look de v8.1 [[9]](https://blakecrosley.com/guides/midjourney).

`image-to-video` **no es vídeo V8 nativo**: es el modelo separado **V1** (18/06/2025), solo imagen-a-vídeo (nunca texto-a-vídeo), clip base de 5s a 24fps extensible a ~21s en incrementos de 4s, 480p base con 720p en tiers superiores, control **Low/High Motion**, Auto/Manual, y loop con fotograma final en Midjourney nativo [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video)[[9]](https://blakecrosley.com/guides/midjourney). "v8.1"/"v8.2" ahí es nomenclatura de catálogo de Nexus, no una versión de vídeo distinta: no hay diferencia real de comportamiento entre ambos tags para este endpoint.

**El Edit Model nativo (`--edit`, desde 27/08/2026 en V8.2) no está expuesto en Youchuan.** Edita por instrucciones escritas desde hasta 4 imágenes de referencia y sustituye a Omni Reference, Character Reference y Retexture en Midjourney nativo [[9]](https://blakecrosley.com/guides/midjourney). Los endpoints de Youchuan (`image-to-image`, `style-transfer`, `blend`) son anteriores a esa unificación y siguen separados por función: ninguno acepta 4 referencias + instrucciones en un solo paso. Trata cualquier equivalencia 1:1 con `--edit` como **[sin verificar]**.

**Mapeo de parámetros Midjourney → Youchuan** (todos compatibles nativamente en V8.1/V8.2 [[9]](https://blakecrosley.com/guides/midjourney)):
- `--ar`/`--stylize`/`--c` (chaos)/`--w` (weird) → campos estructurados `aspect_ratio`/`stylize`/`chaos`/`weird` en los endpoints de imagen de Youchuan [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image). Escribirlos como texto `--flag valor` dentro del prompt: **[sin verificar]** — usa el schema (`get_model_schema`).
- `--no [elementos]` (negative prompt): su exposición como campo en Youchuan no está documentada — **[sin verificar]**, describe en positivo como respaldo.
- `--raw` y `--iw` (Image Weight): no listados en los endpoints de Youchuan; `image-to-image` aplica explícitamente un balance imagen/texto fijo por defecto [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image) — ambos **[sin verificar]**.
- `--sref`: en Youchuan es el campo `sref` dentro de `image-to-image` [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image); para preservar composición exacta usa el endpoint dedicado `style-transfer` (Retexture) en vez de `sref` [[5]](https://www.atlascloud.ai/models/youchuan/v8.2/style-transfer).
- `--cref`/`--oref`/`--edit`: **no disponibles en Youchuan**; en Midjourney nativo V7 usa `--oref` y V8.1/V8.2 lo sustituyen por `--edit` [[8]](https://docs.midjourney.com/hc/en-us/articles/32859204029709-Parameter-List)[[9]](https://blakecrosley.com/guides/midjourney). Más cercano disponible: `blend` (2-5 imágenes, sin instrucciones de edición dirigidas) [[4]](https://www.atlascloud.ai/models/youchuan/v8.2/blend).

## Fortalezas

- **cinematic_look** — estética pulida, re-tuneada "en el espíritu de V7" [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image).
- **stylized** — rango amplio entre fotorrealismo e ilustración vía stylize/chaos/weird [[9]](https://blakecrosley.com/guides/midjourney).
- **high_resolution** — 2K nativo sin upscaler, 1.3 GPU-min en HD [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[9]](https://blakecrosley.com/guides/midjourney).
- **fast** — ~4-5× más rápida que versiones previas (cifra fabricante) [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image).
- **prompt_adherence** — mejor seguimiento de instrucciones vs V8.0; entiende prosa mejor que keywords [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[9]](https://blakecrosley.com/guides/midjourney).

## Debilidades

- **text_rendering** — mejorado con comillas en el prompt pero detrás de especialistas; "estrecha", no cierra, la brecha [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[9]](https://blakecrosley.com/guides/midjourney).
- **identity_preservation** — sin Character/Omni Reference en V8.1/V8.2 nativo, y su sustituto (Edit Model, `--edit`, hasta 4 referencias) no está expuesto por ningún endpoint de Youchuan [[8]](https://docs.midjourney.com/hc/en-us/articles/32859204029709-Parameter-List)[[9]](https://blakecrosley.com/guides/midjourney).
- **multi_character** — sin anclaje documentado entre generaciones; Midjourney mismo advierte que escenas con "grupos de personajes en formación sincronizada" o coreografía precisa degradan el resultado en vídeo [[9]](https://blakecrosley.com/guides/midjourney); solo `blend` o repetir descripción física mitigan parcialmente.

## Tareas ideales / Tareas que debe evitar

**Ideales:** `text_to_image`, `image_edit` (image-to-image, style-transfer), `multi_reference_image` (blend), `image_to_video`, `layer_decomposition` (remove-background).
**Evitar:** `text_to_video`, `reference_to_video`, `start_end_frame`, `video_edit`, `video_extend`, `motion_control`, `lipsync`, `avatar`, `upscale`.

## Estructura de prompt recomendada

Idioma óptimo: **inglés** — toda la documentación usada aquí es en inglés [[9]](https://blakecrosley.com/guides/midjourney); español/chino funcionan en Midjourney nativo pero su fidelidad relativa es **[sin verificar]**. Tono: **prosa natural**, como describir una foto a un director de fotografía — listas de adjetivos sueltos ("beautiful, stunning, 8k, masterpiece") degradan el resultado [[9]](https://blakecrosley.com/guides/midjourney). Jerarquía: plano → sujeto físico → acción/pose → vestuario → entorno → estilo/medio → cámara/luz → mood → parámetros al final [[9]](https://blakecrosley.com/guides/midjourney). Longitud: una frase a un párrafo corto (20-80 palabras en imagen); en Midjourney nativo los parámetros van al final con `--`, pero en Nexus van en los campos del schema [[8]](https://docs.midjourney.com/hc/en-us/articles/32859204029709-Parameter-List). Sintaxis especial: **comillas** para el texto exacto en la imagen [[9]](https://blakecrosley.com/guides/midjourney); nombres de director/fotógrafo como ancla de estilo ("Roger Deakins cinematography") [[9]](https://blakecrosley.com/guides/midjourney); medio y equipo técnico preciso ("35mm film, Kodak Portra 400", "85mm lens, f/1.8") en vez de adjetivos vagos [[9]](https://blakecrosley.com/guides/midjourney).

Plantilla general (la "Cinematic Template" documentada, válida como base también para los endpoints de Youchuan vía el campo `prompt`) [[9]](https://blakecrosley.com/guides/midjourney):

```
[tipo de plano] of [sujeto + descripción física], [acción/pose], [vestuario/estilismo],
[entorno/fondo], [estilo/medio], captured with [cuerpo de cámara] using [lente],
[descripción de iluminación], [mood/atmósfera]
(parámetros aparte, en los campos del schema: aspect_ratio, stylize 0-1000, chaos 0-100, weird 0-3000, negativo si existe)
```

En Nexus, prioriza siempre el schema (`get_model_schema`) sobre `--flags` escritos en el texto: Youchuan los describe como campos estructurados del API (`stylize`, `chaos`, `weird`, `aspect_ratio`, `hd`, `quality`, `seed`), y su aceptación literal como texto `--s 200` dentro del prompt es **[sin verificar]**.

## Plantillas por tarea

### Text-to-image
Orden recomendado (ver plantilla general arriba, versión compacta): `[plano] of [sujeto físico], [acción], [vestuario], [entorno], [estilo/medio], [cámara/luz], [mood]` → parámetros del schema: aspect_ratio=[...], stylize=[...], negativo (solo si el schema tiene ese campo)=[...]. Sintaxis especial: comillas `"texto"` para rotular texto exacto en la imagen; nombre de director/fotógrafo para anclar estilo.

```
[tipo de plano] of [sujeto, descripción física breve], [acción/pose], [entorno/fondo],
[estilo visual/medio], [iluminación], [mood]
(aspect_ratio, stylize y chaos van en los campos del schema)
```

Ejemplo 1 (cinematográfico, hermanos Mateo y Vidal): `Medium shot of two brothers, Mateo (early 20s, tousled dark hair, denim jacket) and Vidal (late teens, buzzcut, backpack slung over one shoulder), standing beside a stalled car on a rain-slicked highway at dusk, hazard lights blinking behind them, tense silence between them. Documentary photography style, Roger Deakins cinematography, cool blue dusk light with warm headlight glow, melancholic tension` → parámetros del schema: aspect_ratio=16:9, stylize=150, chaos=10, negativo (solo si el schema tiene ese campo)=cartoon, illustration [[9]](https://blakecrosley.com/guides/midjourney).

Ejemplo 2 (producto, estilizado): `Hero product shot of a matte black mechanical watch suspended above a pool of liquid mercury, dramatic rim light, obsidian reflections, minimalist dark studio backdrop, luxury editorial style, neon sign in the background reading "TIME WAITS"` → parámetros del schema: aspect_ratio=1:1, stylize=400, weird=200, hd [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[9]](https://blakecrosley.com/guides/midjourney).

Diferencias por variante: ambas leen prosa natural igual; v8.2 es más audaz/editorial por defecto, v8.1 más calmado — útil si una integración ya está afinada sobre ese sabor [[9]](https://blakecrosley.com/guides/midjourney). Ninguna acepta multi-prompts (`::`), Quality (`--q 2`/`--q 4`), Niji ni Turbo — eso solo existe en V7 [[9]](https://blakecrosley.com/guides/midjourney).

**No pongas:** cadenas de adjetivos sueltos tipo "beautiful, stunning, 8k, masterpiece" (degrada el resultado); frases o párrafos largos de texto para que aparezcan en la imagen (sigue siendo debilidad relativa aun con comillas); `--cref`/`--oref`/`--edit` como texto esperando anclar identidad (no soportados en esta familia ni expuestos por Youchuan); parámetros a mitad del prompt en vez de al final.

### Image edit (image-to-image / style-transfer)
Dos sub-herramientas. **image-to-image**: la imagen es inspiración suelta; el prompt de texto es obligatorio y resuelve lo ambiguo; admite `sref` para un estilo separado del contenido [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image). **style-transfer**: la imagen fija composición/geometría; el prompt describe solo el estilo/material objetivo, nunca el contenido (ya viene de la imagen) [[5]](https://www.atlascloud.ai/models/youchuan/v8.2/style-transfer).

```
[image-to-image]  [imagen de entrada] + [qué reinterpretar/cambiar], [nueva acción/contexto], [estilo/medio] (+ sref opcional)
[style-transfer]  [imagen de entrada] + [estilo/material/medio objetivo, sin describir la escena]
```

Ejemplo 1 (image-to-image, cinematográfico, hermanos — imagen de entrada: foto de Mateo y Vidal sentados en un porche): `Reimagine this scene as a tense confrontation at night: Mateo stands and grips the porch railing while Vidal stays seated looking away, storm clouds rolling in overhead, same backyard, 35mm film grain, light turning stormy and cold, muted earth tones` [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image).

Ejemplo 2 (style-transfer, producto — imagen de entrada: foto de un reloj sobre mesa de madera): `ukiyo-e woodblock print, bold black outlines, flat indigo and vermillion palette, visible woodgrain texture, Edo-period print aesthetic` [[5]](https://www.atlascloud.ai/models/youchuan/v8.2/style-transfer).

Diferencias por variante: idéntico comportamiento documentado entre v8.1 y v8.2 para estos endpoints; la diferencia es de sabor estético general, no de sintaxis [[9]](https://blakecrosley.com/guides/midjourney). Ninguna variante de Youchuan expone `--iw` (balance imagen/texto fijo) ni el `--edit` nativo de 4 referencias [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image)[[9]](https://blakecrosley.com/guides/midjourney).

**No pongas:** instrucciones de reproducción pixel-perfect en `image-to-image` (es reinterpretación, no edición exacta); descripciones del contenido/escena en `style-transfer` (ya viene de la imagen; solo frena el restyling); mezclar ambos objetivos (restyling + reinterpretación libre) en un único prompt — usa el endpoint correcto para cada uno; `--iw` como texto esperando que controle el balance.

### Multi-referencia (blend)
Sintaxis: 2-5 imágenes (orden influye en qué domina) + prompt **opcional** — a diferencia del `/blend` nativo de Discord (solo imágenes), Youchuan sí acepta texto para dirigir la fusión [[4]](https://www.atlascloud.ai/models/youchuan/v8.2/blend).

```
[2-5 imágenes, en orden de peso deseado] + [prompt opcional: qué dominará — sujeto/estilo/paleta de cuál imagen]
```

Ejemplo 1 (cinematográfico, hermanos — imágenes: retrato de Mateo y Vidal + bosque con niebla): `Fuse the two brothers' silhouettes and postures from the first image into the misty pine-forest atmosphere of the second image, cold blue-grey palette dominant, their expressions unchanged` [[4]](https://www.atlascloud.ai/models/youchuan/v8.2/blend).

Ejemplo 2 (producto/acción — imágenes: coche deportivo + tormenta eléctrica): `Merge the sports car's sleek silhouette from the first image with the electric energy and violet lightning palette of the second image, dynamic wet-asphalt studio lighting` [[4]](https://www.atlascloud.ai/models/youchuan/v8.2/blend).

Diferencias por variante: sin diferencia documentada de sintaxis entre v8.1 y v8.2; el límite de 2-5 imágenes es una característica estable de Blend, independiente de la versión [[4]](https://www.atlascloud.ai/models/youchuan/v8.2/blend).

**No pongas:** más de 5 imágenes (el endpoint las ignora o falla); esperar fusión literal de un rasgo concreto (p. ej. "exactamente esta cara") sin iterar varias combinaciones; usarlo como sustituto garantizado de identidad entre tomas — no fija rasgos faciales con precisión; omitir el prompt opcional cuando necesitas dirigir qué domina (sin él, el resultado es una fusión puramente visual).

### Layer decomposition (remove-background)
Sintaxis: **solo imagen de entrada, sin campo de prompt** — es una utilidad de un paso que siempre devuelve un recorte con fondo transparente [[6]](https://www.atlascloud.ai/models/youchuan/v8.2/remove-background).

```
[solo la imagen a recortar; no hay texto que escribir]
```

Caso 1 (cinematográfico): subes el fotograma de Mateo y Vidal frente al coche y pides el recorte de los dos para compositarlos sobre otro fondo — no se escribe prompt, el resultado es el PNG transparente [[6]](https://www.atlascloud.ai/models/youchuan/v8.2/remove-background).

Caso 2 (producto): subes la foto del reloj sobre mesa de madera y pides el cutout para montarlo sobre un fondo de catálogo — tampoco hay prompt, solo la imagen [[6]](https://www.atlascloud.ai/models/youchuan/v8.2/remove-background).

Diferencias por variante: sin diferencia documentada entre v8.1 y v8.2 para este endpoint.

**No pongas:** texto esperando controlar el estilo o el nivel de recorte (el endpoint no acepta prompt); bordes finos como pelo suelto o humo esperando precisión perfecta sin revisión manual posterior; varias capas o elementos — el resultado es un único cutout de sujeto/fondo, no una descomposición en múltiples capas.

### Image-to-video
Campos reales del endpoint de Nexus: `image` (primer fotograma, obligatorio), `prompt` (texto opcional sobre el movimiento/escena, NO sobre el aspecto ya fijado en la imagen), `resolution` (480p/720p), `motion` (low/high) [[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video). No describas de nuevo el aspecto de los personajes o el objeto: eso ya está fijado por la imagen; el prompt debe centrarse en cámara, ritmo y qué se mueve.

```
[imagen de entrada: primer fotograma] + [prompt opcional, en prosa, SOLO movimiento/cámara/ritmo —
sin redescribir apariencia] + motion: [low|high] + resolution: [480p|720p]
```

Ejemplo 1 (cinematográfico, hermanos — partiendo de la imagen de Mateo y Vidal junto al coche averiado): `Slow cinematic dolly-in toward both brothers, a light wind moves their jackets and hair, the hazard lights continue blinking in a steady rhythm behind them, shallow depth of field holds focus on their faces as the background softly blurs, subtle and restrained movement throughout, no sudden gestures` — `motion: low`, `resolution: 720p` [[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video)[[9]](https://blakecrosley.com/guides/midjourney).

Ejemplo 2 (producto, distinto — partiendo de la imagen del reloj sobre mercurio): `The watch slowly rotates in place as the liquid mercury beneath it ripples outward in widening waves, the rim light sweeps gradually across the case, the camera holds static while reflections shift and sparkle, energetic but controlled motion` — `motion: high`, `resolution: 480p` [[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video)[[9]](https://blakecrosley.com/guides/midjourney).

Diferencias por variante: el tag "v8.1"/"v8.2" en este endpoint es solo nomenclatura de catálogo — el modelo subyacente es V1, independiente de la versión de imagen; no hay diferencia de comportamiento esperable entre ambos tags [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video). La guía recomienda **low motion** por defecto y advierte que **high motion** puede producir movimientos irreales o con artefactos, especialmente en escenas con varios personajes o coreografía precisa [[9]](https://blakecrosley.com/guides/midjourney).

**No pongas:** descripción del aspecto físico de los sujetos (ya está fijado por la imagen; solo confunde al modelo); vídeo desde texto puro (el endpoint exige `image`, no hay texto-a-vídeo); coreografías complejas o grupos sincronizados en `motion: high` (la guía documenta glitches en estos casos); control de loop/fotograma final por texto — el loop con end-frame existe en Midjourney nativo pero no está expuesto como campo en este endpoint de Nexus (**[sin verificar]**).

## Idioma, longitud y prompt negativo

Inglés mejor documentado [[9]](https://blakecrosley.com/guides/midjourney). Longitud corta a media; evita listas de adjetivos. `--no [elementos]` (negative prompt) está confirmado para toda la familia V8 nativa [[9]](https://blakecrosley.com/guides/midjourney); su exposición como campo `negative_prompt` en los endpoints de Youchuan no aparece documentada — **[sin verificar]**. Alternativa robusta: describe en positivo exactamente lo que sí quieres en vez de negar lo que no.

## Referencias e imagen inicial

- **Style reference (`sref`)**: transfiere paleta, luz, técnica y mood, no composición [[9]](https://blakecrosley.com/guides/midjourney); dentro de `image-to-image` es un campo separado del contenido [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image); para restyling con composición preservada usa `style-transfer`, no `sref` [[5]](https://www.atlascloud.ai/models/youchuan/v8.2/style-transfer).
- **Image prompt (`image-to-image`)**: "prompt visual" — inspiración, no reproducción exacta; el prompt de texto es obligatorio y resuelve la ambigüedad [[7]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image).
- **Blend**: 2-5 imágenes, cada una aporta concepto/sujeto/estilo; orden y prompt opcional dirigen qué domina; imágenes con la misma proporción que el resultado deseado funden de forma más predecible [[4]](https://www.atlascloud.ai/models/youchuan/v8.2/blend).
- **Edit Model nativo (`--edit`)**: hasta 4 imágenes de referencia + instrucciones escritas en Midjourney nativo V8.1/V8.2 — no expuesto por ningún endpoint de Youchuan documentado [[9]](https://blakecrosley.com/guides/midjourney).

## Cámara y movimiento

Solo en `image-to-video` (modelo V1) [[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video): `motion` admite **low** (sutil, mejor en retratos/escenas estáticas) o **high** (dinámico, mejor en acción/paisajes, con riesgo de artefactos) [[9]](https://blakecrosley.com/guides/midjourney). `prompt` acepta cámara en prosa ("slow dolly-in", "camera holds static") según el ejemplo del propio endpoint [[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video); no hay vocabulario de cámara cerrado — trata términos específicos como **[sin verificar]**.

## Consistencia de personaje

Sin anclaje confirmado en Youchuan: Character/Omni Reference son de V7, y su sustituto (Edit Model, `--edit`) no está expuesto en ningún endpoint de Youchuan [[8]](https://docs.midjourney.com/hc/en-us/articles/32859204029709-Parameter-List)[[9]](https://blakecrosley.com/guides/midjourney). Recomendado: `blend` con la misma imagen de referencia repetida entre tomas, o repetir rasgos físicos idénticos palabra por palabra — ninguna técnica garantiza coincidencia exacta de rostro.

## Restricciones e incompatibilidades conocidas

- Dataset no publicado; litigio activo (*Disney Enterprises, Inc. v. Midjourney, Inc.*, 2:25-cv-05275, 11/06/2025) [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image).
- Sin Omni/Character Reference, Edit Model, multi-prompts, Quality (`--q 2`/`--q 4`), Niji ni Turbo mode en Youchuan [[1]](https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image)[[9]](https://blakecrosley.com/guides/midjourney).
- `image-to-video` es solo imagen-a-vídeo, sin loop/end-frame expuesto en Nexus y sin benchmarks públicos del fabricante [[3]](https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video).
- `motion: high` puede producir glitches con varios personajes o coreografía precisa — advertencia explícita de la guía [[9]](https://blakecrosley.com/guides/midjourney).

## Errores habituales

- Escribir `--ar`/`--sref`/`--s`/`--no` como texto cuando el schema de Nexus ya expone esos campos (`aspect_ratio`, `sref`, `stylize`, etc.).
- Prompts "sopa de palabras clave" en vez de prosa plano→sujeto→contexto→estilo→cámara→mood [[9]](https://blakecrosley.com/guides/midjourney).
- Redescribir el aspecto físico del sujeto en `image-to-video` en vez de centrarse solo en el movimiento.
- Esperar consistencia de personaje sin usar `blend` ni repetir descripción física exacta.
- Pedir vídeo desde texto puro (`image-to-video` requiere `image`) o edición pixel-perfect en `image-to-image` (es reinterpretación guiada).
- Confundir `style-transfer` (preserva composición, solo estilo) con `image-to-image` (reinterpreta libremente).
- Pedir texto largo o multi-frase en imagen: sigue siendo debilidad relativa aun con comillas.

## Recomendaciones de prompting

- Escribe en inglés, prosa natural tipo "describiendo una foto a un director de fotografía"; evita adjetivos genéricos sueltos.
- Sigue la jerarquía: plano → sujeto físico → acción → vestuario → entorno → estilo/medio → cámara/luz → mood → parámetros al final.
- Usa comillas para el texto exacto que debe aparecer en la imagen, una sola vez y corto.
- Para restyling preservando composición, usa `style-transfer`; para reinterpretación libre con nuevo contexto, usa `image-to-image`.
- Para fusionar conceptos de varias imágenes, usa `blend` (2-5) con un prompt opcional que indique qué debe dominar.
- En `image-to-video`, describe solo movimiento/cámara/ritmo — nunca redescribas el aspecto ya fijado por la imagen de entrada.
- Prefiere `motion: low` salvo que la escena sea de un solo sujeto/paisaje y necesites energía; evita `motion: high` con varios personajes.
- Usa los campos del schema de Nexus en vez de `--flags` en texto, salvo que verifiques lo contrario con `get_model_schema`.
- No asumas Character/Omni Reference ni Edit Model: repite descripción física o usa `blend` como mejor aproximación disponible.
- Prefiere v8.2 por defecto; usa v8.1 solo para reproducir una integración ya validada con ese sabor.

## Fuentes y fecha

[1] Atlas Cloud — Youchuan V8.2 Text-to-Image — https://www.atlascloud.ai/models/youchuan/v8.2/text-to-image — 2026-10-02
[2] Atlas Cloud — Youchuan V8.1 Text-to-Image — https://www.atlascloud.ai/models/youchuan/v8.1/text-to-image — 2026-10-02
[3] Atlas Cloud — Youchuan V8.2 Image-to-Video (V1) — https://www.atlascloud.ai/models/youchuan/v8.2/image-to-video — 2026-10-02
[4] Atlas Cloud — Youchuan V8.2 Blend — https://www.atlascloud.ai/models/youchuan/v8.2/blend — 2026-10-02
[5] Atlas Cloud — Youchuan V8.2 Style Transfer — https://www.atlascloud.ai/models/youchuan/v8.2/style-transfer — 2026-10-02
[6] Atlas Cloud — Youchuan V8.2 Remove Background — https://www.atlascloud.ai/models/youchuan/v8.2/remove-background — 2026-10-02
[7] Atlas Cloud — Youchuan V8.2 Image-to-Image — https://www.atlascloud.ai/models/youchuan/v8.2/image-to-image — 2026-10-02
[8] Midjourney — Parameter List (doc oficial) — https://docs.midjourney.com/hc/en-us/articles/32859204029709-Parameter-List — 2026-10-02
[9] Blake Crosley — Midjourney 8.2 Guide — https://blakecrosley.com/guides/midjourney — 2026-10-02
