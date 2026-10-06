---
id: 'gpt-image'
kind: 'model'
title: 'GPT Image (OpenAI) — familia 1 / 1-mini / 1.5 / 2 / 2.5 Flare / 2.5 Sunburst'
version: 2
updated: 2026-10-02
match: ['^openai/gpt-image', '^runway/gpt_image']
modalities: 'IMAGE'
strengths: [prompt_adherence, text_rendering, identity_preservation, high_resolution, stylized, low_cost]
weaknesses: [fast, multi_character]
ideal_tasks: [text_to_image, image_edit, multi_reference_image]
avoid_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, motion_control, lipsync, avatar, upscale, layer_decomposition]
unverified_tags: []
sources:
  - {url: 'https://developers.openai.com/api/docs/guides/image-generation', title: 'OpenAI API docs — Image generation', accessed: 2026-10-02}
  - {url: 'https://developers.openai.com/api/docs/guides/image-prompting', title: 'OpenAI API docs — Image prompting (GPT Image 2.5/2/1.5/1)', accessed: 2026-10-02}
  - {url: 'https://developers.openai.com/api/docs/deprecations', title: 'OpenAI API docs — Deprecations (shutdown 1 y 1.5)', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/openai/gpt-image-1/text-to-image', title: 'Atlas Cloud — gpt-image-1', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/openai/gpt-image-1-mini/text-to-image', title: 'Atlas Cloud — gpt-image-1-mini', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/openai/gpt-image-2-developer/text-to-image', title: 'Atlas Cloud — gpt-image-2-developer', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare/text-to-image', title: 'Atlas Cloud — gpt-image-2.5-flare', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare-developer/text-to-image', title: 'Atlas Cloud — gpt-image-2.5-flare-developer', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/openai/gpt-image-2.5-sunburst/text-to-image', title: 'Atlas Cloud — gpt-image-2.5-sunburst', accessed: 2026-10-02}
---

## Resumen

GPT Image es la familia de imagen de OpenAI (generación y edición desde texto), con un backbone tipo GPT que destaca en seguir instrucciones complejas y literales y en renderizar texto legible dentro de la imagen [[1]](https://developers.openai.com/api/docs/guides/image-generation). La generación actual, **GPT Image 2.5** (**Sunburst** y **Flare**), es la recomendada por OpenAI para cualquier integración nueva [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Solo cubre texto-a-imagen y edición, no vídeo. En Nexus convive la ruta OpenAI directa (`openai/gpt-image-*`) con la ruta Runway API/Crun (`runway/gpt_image_*`), mismas versiones del modelo.

## Cuándo elegirlo y cuándo no

- **Elige GPT Image** si el plano exige texto exacto y legible (carteles, packaging, infografías, cómics, UI mockups), ediciones por instrucción natural (ropa, quitar objetos, traducir un rótulo) preservando identidad, o combinar hasta 16 referencias con roles distintos (sujeto/estilo/prenda/fondo) [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- **Nano Banana**: iteración conversacional rápida/barata. **Seedream**: estética de producto/moda sin describir luz/textura. **FLUX**: coste puro — Sunburst puede tardar hasta 2 min en prompts complejos [[1]](https://developers.openai.com/api/docs/guides/image-generation). **Midjourney**: variedad "cinematográfica" por defecto. **Ideogram**: solo tipografía/logo.
- **MAI-Image, Qwen-Image, Grok Imagine Image**: útiles si conviene evitar OpenAI (cuota/moderación) o para chino nativo (Qwen); GPT Image suele ser más consistente en edición natural.
- **No elijas GPT Image** para vídeo, lipsync o cámara (solo `text_to_image`/`image_edit`), ni si una región debe quedar pixel-perfect en ediciones repetidas: compón el parche sobre el original en vez de confiar solo en el prompt [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

| Necesidad | Variante recomendada |
|---|---|
| Mejor calidad/edición hoy | `gpt-image-2.5-sunburst` |
| Mejor latencia con buena calidad | `gpt-image-2.5-flare` |
| Integración legacy ya validada | `gpt-image-2` |
| Prototipo low-cost desechable | `gpt-image-1-mini` |
| Logo/producto sobre fondo transparente | `gpt-image-2.5-flare` o `-sunburst` (`background="transparent"`) |
| Traducir texto en una imagen preservando el layout | `gpt-image-2.5-flare` o `-sunburst` (edit) |
| Evitar en proyectos nuevos | `gpt-image-1`, `gpt-image-1.5` |

## Linaje y variantes en Nexus

1. **GPT Image 1** — tamaños fijos, calidad `low/medium/high` [[4]](https://www.atlascloud.ai/models/openai/gpt-image-1/text-to-image). **Deprecado, se apaga el 23/10/2026** [[3]](https://developers.openai.com/api/docs/deprecations).
2. **GPT Image 1 Mini** — más barato, para UI/concept art rápido, no calidad final [[5]](https://www.atlascloud.ai/models/openai/gpt-image-1-mini/text-to-image).
3. **GPT Image 1.5** — mejora `input_fidelity` (preserva caras/logos), mismos tamaños. **Deprecado, se apaga el 01/12/2026** [[3]](https://developers.openai.com/api/docs/deprecations).
4. **GPT Image 2** — resolución libre hasta 4K, transparencia en preview, `input_fidelity` fijo a alto [[2]](https://developers.openai.com/api/docs/guides/image-prompting). `-developer` en Atlas es el mismo modelo vía Images API nativa [[6]](https://www.atlascloud.ai/models/openai/gpt-image-2-developer/text-to-image) — impacto práctico del contrato [sin verificar].
5. **GPT Image 2.5 — Flare y Sunburst** — generación actual recomendada [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Sunburst = base, máxima calidad [[9]](https://www.atlascloud.ai/models/openai/gpt-image-2.5-sunburst/text-to-image); Flare = pequeño/rápido, calidad ≈ GPT Image 2. Ambos añaden `xhigh`/`max`, 16 referencias, transparencia completa, prompts hasta 32.000 caracteres, hasta 3840×2160 [[2]](https://developers.openai.com/api/docs/guides/image-prompting)[[7]](https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare/text-to-image). `-developer` = mismo modelo vía Images API nativa [[8]](https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare-developer/text-to-image).
6. **Runway API / Crun** (`runway/gpt_image_2/image`, `_2_5_flare/image`, `_2_5_sunburst/image`) — ids de Nexus fuera de Atlas, mismas versiones; confirma con `get_model_schema` — [sin verificar] el detalle del contrato.

**La más reciente y recomendada por defecto: `gpt-image-2.5-sunburst` (calidad) / `gpt-image-2.5-flare` (velocidad).** Evita `gpt-image-1`/`1.5` salvo necesidad explícita de reproducir una integración legacy.

**Diferencias de prompt entre variantes dentro de la misma tarea:** la guía oficial de prompting usa los mismos prompts de ejemplo para Flare y Sunburst y advierte que la salida puede diferir entre modelos aunque el texto sea idéntico — para elegir, mantén prompt/referencias/tamaño/calidad fijos y compara ambos outputs [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Con **1** y **1.5** evita `input_fidelity` como palanca fina (en 2/2.5 ya no existe ese parámetro: siempre alta fidelidad) [[2]](https://developers.openai.com/api/docs/guides/image-prompting). **Mini** tolera prompts más simples y directos (menos cláusulas de restricción) porque se usa para iteración rápida, no para el entregable final [[5]](https://www.atlascloud.ai/models/openai/gpt-image-1-mini/text-to-image).

## Fortalezas

- **prompt_adherence** — sigue instrucciones complejas y literales ("cambia solo X, preserva Y") por secciones [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- **text_rendering** — tipografía legible; texto exacto entre comillas, "una sola vez, sin texto adicional" [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- **identity_preservation** — en edición fija rostro, piel, pose y proporciones si se indica qué preservar [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- **high_resolution** — GPT Image 2 y 2.5 admiten hasta 3840×2160 (4K) [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- **stylized** — de fotorrealismo a ilustración infantil, cómic, infografía o pixel-art, con transferencia de estilo [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- **low_cost** — Mini (gen. 1) y Flare (gen. 2.5) priorizan iteración barata frente a sus hermanas de máxima calidad [[5]](https://www.atlascloud.ai/models/openai/gpt-image-1-mini/text-to-image).

## Debilidades

- **fast** (ausencia de) — prompts complejos pueden tardar hasta 2 minutos, sobre todo en Sunburst/calidades altas; para latencia usa Flare y baja calidad [[1]](https://developers.openai.com/api/docs/guides/image-generation).
- **multi_character** — puede fallar en mantener consistencia de personajes recurrentes o marca entre generaciones, y en colocar elementos con precisión en composiciones estructuradas [[1]](https://developers.openai.com/api/docs/guides/image-generation).

## Tareas ideales / Tareas que debe evitar

**Ideales:** `text_to_image`, `image_edit`, `multi_reference_image`.
**Evitar:** `text_to_video`, `image_to_video`, `reference_to_video`, `start_end_frame`, `video_edit`, `video_extend`, `motion_control`, `lipsync`, `avatar`, `upscale`, `layer_decomposition`.

## Estructura de prompt recomendada

Idioma óptimo: **inglés** (toda la guía oficial y sus ejemplos lo están); entiende español razonablemente, pero traducir mejora la previsibilidad [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Tono: **prosa natural con secciones etiquetadas**, no tags sueltos tipo Danbooru ni `--parámetros` de Midjourney — el propio modelo "lee" el prompt como instrucciones de un director de arte, no como palabras clave [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Para prompts complejos, organiza explícitamente en escena → sujeto → detalles → restricciones, con etiquetas si ayuda a mantener el formato legible y editable (prosa descriptiva, JSON-like o instrucciones numeradas son todas válidas: elige la que sea más fácil de mantener, no la "sintaxis especial") [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Longitud libre; hasta 32.000 caracteres en 2.5 — pero la densidad de detalle útil (materiales, iluminación, encuadre) importa más que la longitud bruta [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Tamaño/calidad/fondo/formato van en campos del API (`size`, `quality`, `background`, `output_format`), nunca como sufijos en el texto del prompt [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

```
[Tipo de imagen y uso] of/for [sujeto principal], [encuadre y acción/pose].
Scene: [entorno, hora del día, composición, aspect ratio implícito].
Style: [medio visual: foto/ilustración/render], [iluminación], [cámara/lente si aplica], [paleta], [textura/materiales].
Text (if any): exact text "[texto]" — must appear exactly once, fully legible, no typos.
Preserve (edits only): [identidad/rostro/pose/fondo/geometría de producto que no debe cambiar].
Constraints: no extra text, no watermarks, no unrelated logos, no [elemento no deseado].
```

## Plantillas por tarea

### Text-to-image

Orden que mejor sigue el modelo: tipo de imagen + sujeto → encuadre/acción → estilo (medio, iluminación, cámara, paleta) → texto exacto si lo hay → restricciones. Para prompts largos, usa secciones etiquetadas (`Scene:`, `Style:`, `Text:`) en vez de una sola frase corrida; el modelo las procesa como bloques de instrucción independientes [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Nombra materiales y relaciones espaciales explícitamente ("ceramic mug on a wooden table, steam rising") en vez de adjetivos vagos ("nice", "beautiful") [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

```
Create a [photorealistic photograph / illustration / diagram / UI mockup / logo] of [sujeto],
[encuadre/acción/pose]. Scene: [entorno, hora del día, composición].
Style: [medio visual], [cámara/lente], [iluminación], [paleta], [textura/materiales].
Text (if any): exact text "[texto]" — appears once, fully legible.
No extra text, no watermarks, no unrelated logos, no [elemento no deseado].
```

Ejemplo cinematográfico (personajes): `Photorealistic cinematic photograph of two brothers, Mateo and Vidal, sitting on a weathered wooden dock at sunset, fishing rods in hand. Mateo (early 20s, dark curly hair, grey hoodie) laughs while pointing at the water; Vidal (late teens, buzz cut, denim jacket) watches with a quiet smile. Scene: calm lake, mist rising, warm orange backlight, distant pine forest in silhouette. Style: 35mm film look, medium shot, 50mm lens, shallow depth of field, soft golden-hour light, natural film grain. No text, no watermarks, no logos.` [[2]](https://developers.openai.com/api/docs/guides/image-prompting)

Ejemplo distinto (producto estilizado): `Create an original, non-infringing logo for a company called "Field & Flour", a local bakery. The logo should feel warm, simple, and modern — a wheat-stalk icon paired with clean sans-serif type. Style: flat vector illustration, two-tone warm palette (terracotta and cream), minimal shapes, high contrast. Text (exact): "Field & Flour" — appears once, bold, centered, perfectly legible at small sizes. No watermarks, no gradients, no 3D effects.` [[2]](https://developers.openai.com/api/docs/guides/image-prompting)

**Diferencias por variante:** en Flare/Sunburst (2.5) puedes pedir tamaños libres hasta 4K y usar `background="transparent"` con PNG/WebP para logos/producto sin fondo [[2]](https://developers.openai.com/api/docs/guides/image-prompting); en GPT Image 1/1-mini los tamaños son fijos, así que describe el encuadre pensando en una relación de aspecto predefinida, no libre [[4]](https://www.atlascloud.ai/models/openai/gpt-image-1/text-to-image).

**No pongas:**
- Sufijos tipo `--ar 16:9` o `--q 2` en el texto: no los interpreta, fija `size`/`quality` en los campos del API [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- Adjetivos vagos sin sustantivo concreto ("hazlo épico", "bonito") en vez de describir luz, material y composición.
- Texto exacto sin comillas ni la instrucción "appears once, legible": sin eso el modelo puede repetirlo o deformarlo.
- Varias ideas de sujeto distintas en una sola frase sin separar escena/estilo: confunde la composición.

### Image edit

Orden óptimo: instrucción de cambio concreto → qué preservar explícitamente (identidad, pose, fondo, iluminación, geometría) → restricciones de marca/texto. La guía oficial insiste en decir **qué no debe cambiar**, no solo qué cambiar — es la causa más común de ediciones que alteran cosas no pedidas [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Para ediciones locales de precisión (una zona exacta), usa máscara con mismo formato/tamaño y canal alfa que la imagen de entrada en vez de depender solo del texto [[1]](https://developers.openai.com/api/docs/guides/image-generation). Para traducir texto conservando el layout, pide explícitamente mantener el diseño sin cambios salvo el idioma del texto [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

```
Edit the image to [cambio concreto, una sola acción clara].
Preserve [identidad/rostro/piel/pose/fondo/iluminación/geometría de producto] exactly.
Match [lighting/perspective/style] of the original.
Do not change [aspecto que debe quedar intacto]. No extra text, no watermarks, no logos.
```

Ejemplo cinematográfico (personajes): `Edit the image so that Mateo and Vidal are now wearing matching navy fishing jackets instead of their current clothes. Preserve their faces, skin tone, expressions, hairstyles, poses, and the lake/sunset background exactly. Match the existing warm golden-hour lighting on the new jackets. Do not change the framing, the dock, or the water reflections. No watermarks, no added text.` [[2]](https://developers.openai.com/api/docs/guides/image-prompting)

Ejemplo distinto (producto): `Extract the product from the input image and isolate it on a fully transparent background. Output: centered product, crisp silhouette, no halos or fringing. Preserve the product geometry and label legibility exactly. Add only light polishing to reflections. Do not restyle, recolor, or add a shadow or checkerboard pattern.` [[2]](https://developers.openai.com/api/docs/guides/image-prompting)

**Diferencias por variante:** en GPT Image 2 y 2.5, `input_fidelity` ya no existe como parámetro (siempre alta fidelidad), así que no lo menciones en el prompt ni lo busques en el schema al migrar desde 1/1.5, donde sí controlaba cuánto se preservaba el rostro/logo de entrada [[2]](https://developers.openai.com/api/docs/guides/image-prompting). GPT Image 1 soporta edición con referencias y máscaras pero con tamaños fijos; mantén esta ruta solo para integraciones legacy ya validadas [[4]](https://www.atlascloud.ai/models/openai/gpt-image-1/text-to-image).

**No pongas:**
- "Mejóralo" o "hazlo mejor" sin especificar qué cambiar: el modelo no sabe qué margen de libertad tiene y puede alterar identidad o fondo.
- Una edición sin la cláusula "do not change X / preserve Y": es la causa principal de resultados que cambian más de lo pedido.
- `input_fidelity` en prompts para GPT Image 2/2.5 (parámetro obsoleto en esas versiones).
- Confiar solo en texto para una zona que debe quedar pixel-perfect entre iteraciones: usa máscara o composición manual sobre el original.

### Multi-referencia

Orden óptimo: numerar cada imagen de entrada → asignar rol explícito (sujeto, estilo, prenda, fondo) a cada una → describir cómo deben combinarse y qué elemento va dónde → restricción final de "no cambiar nada más". Asignar roles por número reduce confusión cuando hay varias referencias con información superpuesta (p. ej. dos imágenes de personas) [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Para transferir solo el estilo, asigna a la referencia un rol limitado (paleta/textura/medio visual) y describe el sujeto nuevo por separado, para que no copie también el contenido de la referencia [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

```
Image 1: [rol — e.g. scene/background]. Image 2: [rol — e.g. subject/clothing/style]. Image 3: [rol, si aplica].
Combine them: place/dress/transfer [elemento de imagen N] into [imagen M],
matching [lighting/composition/perspective]. Do not change anything else about [imagen base].
```

Ejemplo cinematográfico (personajes): `Image 1: background — a rocky lakeshore at dusk with warm orange light. Image 2: subject — Mateo, the young man with dark curly hair and a grey hoodie. Image 3: subject — Vidal, his brother, buzz cut and denim jacket. Place both brothers from images 2 and 3 into the lakeshore scene from image 1, standing side by side near the water, matching the dusk lighting and color temperature. Do not change their faces, clothing, or the background composition otherwise.` [[2]](https://developers.openai.com/api/docs/guides/image-prompting)

Ejemplo distinto (producto/estilo): `Image 1: style reference — a pixel-art illustration, limited palette, blocky shading. Image 2: subject — a modern motorcycle on a white studio background. Use the visual style (palette, texture, pixel-art medium) from image 1 and generate the motorcycle from image 2 rendered entirely in that pixel-art style, keeping its silhouette and proportions recognizable. Do not keep the original photographic rendering.` [[2]](https://developers.openai.com/api/docs/guides/image-prompting)

**Diferencias por variante:** 2.5 (Flare/Sunburst) acepta hasta 16 imágenes de referencia por request; en 1/1-mini/1.5 el límite es menor y los tamaños de salida son fijos, así que simplifica a 2-3 referencias con roles muy claros para evitar que el modelo mezcle información [[2]](https://developers.openai.com/api/docs/guides/image-prompting)[[7]](https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare/text-to-image).

**No pongas:**
- Varias referencias sin numerar ni asignar rol: el modelo puede confundir qué imagen aporta la apariencia, cuál el estilo y cuál el fondo [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- Una referencia de estilo sin aclarar qué parte de ella transferir (paleta/textura/medio) — si no, puede copiar también el contenido/sujeto de la referencia.
- Más de 16 referencias en 2.5, o más de las que soporte la variante elegida (verifica con `get_model_schema`).
- Redescribir en texto la apariencia exacta de cada referencia en vez de dejar que la imagen la aporte: solo describe cómo combinarlas, no vuelvas a especificar rasgos ya visibles.

## Idioma, longitud y prompt negativo

Óptimo en inglés; comprende español razonablemente, pero la documentación oficial y sus ejemplos están en inglés [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Sin mínimo de longitud; hasta 32.000 caracteres en 2.5. No hay `negative_prompt` dedicado (confirmar con el schema en vivo de Nexus): las restricciones van dentro del prompt ("do not add extra text", "no watermarks", "avoid cinematic lighting") [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

## Referencias e imagen inicial

Numera y nombra el rol de cada imagen de entrada (sujeto, estilo, prenda, fondo); hasta 16 referencias por request en 2.5 [[2]](https://developers.openai.com/api/docs/guides/image-prompting)[[7]](https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare/text-to-image). La máscara debe tener mismo formato/tamaño que la imagen a editar y canal alfa [[1]](https://developers.openai.com/api/docs/guides/image-generation). `input_fidelity` (alto/bajo) controla cuánto se preserva de la entrada; en GPT Image 2 y 2.5 ya no aplica (siempre alta fidelidad) — omítelo al migrar desde 1/1.5 [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Para ediciones iterativas, pasa cada salida como entrada de la siguiente edición y repite literalmente qué preservar, en vez de confiar en que el modelo "recuerde" lo anterior [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

## Cámara y movimiento

No hay movimiento real (imagen fija), pero el vocabulario de cámara influye en encuadre y "look": distancia focal, tipo de toma, medio fotográfico, profundidad de campo. Trata las especificaciones de cámara como señales de apariencia, no simulación física exacta [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

## Consistencia de personaje

Preserva identidad en una edición puntual si se indica explícitamente qué fijar (rostro, piel, pose, proporciones) [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Para series (p. ej. varias escenas de Mateo y Vidal), crea una "ficha de personaje" (apariencia, vestuario, tono) en la primera generación y repítela literalmente en cada edición posterior, usando la imagen generada como input siguiente [[2]](https://developers.openai.com/api/docs/guides/image-prompting). Sin esa técnica, la consistencia entre generaciones independientes no está garantizada [[1]](https://developers.openai.com/api/docs/guides/image-generation).

## Restricciones e incompatibilidades conocidas

- Moderación en input/output; `moderation`: `auto` (estándar) o `low` (menos restrictivo) [[1]](https://developers.openai.com/api/docs/guides/image-generation).
- Error `moderation_blocked` con categorías públicas (`harassment`, `self-harm`, `sexual`, `violence`); no reintentar sin modificar el prompt [[1]](https://developers.openai.com/api/docs/guides/image-generation).
- Transparencia real requiere `background: "transparent"` + `png`/`webp`; comprueba el canal alfa, un fondo de cuadrícula no es transparencia [[2]](https://developers.openai.com/api/docs/guides/image-prompting).
- `gpt-image-1` se apaga el 23/10/2026, `gpt-image-1.5` el 01/12/2026 [[3]](https://developers.openai.com/api/docs/deprecations).

## Errores habituales

- Editar sin decir qué debe permanecer igual; para zonas pixel-perfect, compositar en vez de confiar en el prompt.
- Usar sintaxis `--parámetros` de Midjourney: GPT Image la ignora.
- No numerar las referencias por rol en flujos multi-imagen.
- Seguir usando `gpt-image-1`/`1.5` en integraciones nuevas (deprecados).
- Mencionar `input_fidelity` en prompts para 2/2.5, donde ya no es un parámetro.
- Comparar Flare vs. Sunburst sin fijar prompt, referencias, tamaño y calidad: la diferencia de calidad/latencia solo es fiable si todo lo demás es idéntico [[2]](https://developers.openai.com/api/docs/guides/image-prompting).

## Recomendaciones de prompting

- Empieza en `gpt-image-2.5-sunburst` (calidad) o `-flare` (velocidad); evita 1 y 1.5.
- Estructura: escena → sujeto → estilo → texto exacto → restricciones, en prosa natural con secciones etiquetadas si el prompt es largo.
- En ediciones, indica siempre qué cambia Y qué se preserva (identidad, fondo, luz, geometría).
- Pon el texto exacto entre comillas y añade "appears once, fully legible, no extra text".
- Numera y asigna rol a cada referencia en flujos multi-imagen; no redescribas lo que ya es visible en la referencia.
- Para transparencia, pide `background="transparent"` + PNG/WebP y verifica el canal alfa.
- En series de personajes, repite literalmente la "ficha de personaje" en cada edición encadenada.
- Verifica parámetros y límites (nº de referencias, tamaños) con `get_model_schema` de Nexus.

## Fuentes y fecha

[1] OpenAI API docs — Image generation — https://developers.openai.com/api/docs/guides/image-generation — 2026-10-02
[2] OpenAI API docs — Image prompting (GPT Image 2.5/2/1.5/1) — https://developers.openai.com/api/docs/guides/image-prompting — 2026-10-02
[3] OpenAI API docs — Deprecations — https://developers.openai.com/api/docs/deprecations — 2026-10-02
[4] Atlas Cloud — gpt-image-1 — https://www.atlascloud.ai/models/openai/gpt-image-1/text-to-image — 2026-10-02
[5] Atlas Cloud — gpt-image-1-mini — https://www.atlascloud.ai/models/openai/gpt-image-1-mini/text-to-image — 2026-10-02
[6] Atlas Cloud — gpt-image-2-developer — https://www.atlascloud.ai/models/openai/gpt-image-2-developer/text-to-image — 2026-10-02
[7] Atlas Cloud — gpt-image-2.5-flare — https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare/text-to-image — 2026-10-02
[8] Atlas Cloud — gpt-image-2.5-flare-developer — https://www.atlascloud.ai/models/openai/gpt-image-2.5-flare-developer/text-to-image — 2026-10-02
[9] Atlas Cloud — gpt-image-2.5-sunburst — https://www.atlascloud.ai/models/openai/gpt-image-2.5-sunburst/text-to-image — 2026-10-02
