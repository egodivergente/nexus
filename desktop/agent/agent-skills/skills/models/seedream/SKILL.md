---
id: seedream
kind: model
title: 'Seedream (ByteDance Seed)'
version: 2
updated: 2026-10-02
match:
  - '^bytedance/seedream'
  - '^runway/seedream'
modalities: [IMAGE]
strengths: [prompt_adherence, text_rendering, high_resolution, photorealism, identity_preservation]
weaknesses: []
ideal_tasks: [text_to_image, image_edit, multi_reference_image, layer_decomposition]
avoid_tasks: []
unverified_tags: []
sources:
  - url: https://www.atlascloud.ai/models/bytedance/seedream-v4
    title: 'Seedream 4 — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedream-v4.5
    title: 'Seedream 4.5 — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedream-v4.7/text-to-image
    title: 'Seedream 4.7 — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedream-v5.0-lite
    title: 'Seedream 5.0 Lite — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image
    title: 'Seedream 5.0 Pro — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit
    title: 'Seedream 5.0 Pro Edit — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/layer-decomposition
    title: 'Seedream 5.0 Pro Layer Decomposition — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://seed.bytedance.com/en/seedream5_0_pro
    title: 'Seedream 5.0 Pro — ByteDance Seed (página oficial)'
    accessed: 2026-10-02
  - url: https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide
    title: 'Seedream 4.0-4.5 prompt guide — BytePlus ModelArk'
    accessed: 2026-10-02
  - url: https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide
    title: 'Seedream 5.0 pro/flash interactive editing guide — BytePlus ModelArk'
    accessed: 2026-10-02
---

## Resumen
Seedream (ByteDance Seed) unifica texto-a-imagen, edición guiada por referencia y composición multi-imagen; destaca en seguir instrucciones complejas, renderizar texto legible y resolución nativa alta [[1]](https://www.atlascloud.ai/models/bytedance/seedream-v4). La generación más reciente en Nexus es **Seedream 5.0** (*lite*/*pro*, jun.-jul. 2026); por debajo, 4.7, 4.5 (fidelidad profesional) y 4.0 (base, sept. 2025) [[5]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image). 5.0 Pro añade razonamiento compositivo avanzado, edición por coordenadas `<point>`/`<bbox>` y `layer_decomposition`, única para separar un bitmap en capas editables [[7]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/layer-decomposition) [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide).

## Cuándo elegirlo y cuándo no
Elige Seedream para texto legible en la imagen (carteles, UI, infografías multilingües), razonamiento compositivo (diagramas, storyboards multi-panel), fusionar varias referencias en un resultado coherente o localizar una edición con precisión de píxel.
- vs. **gpt-image**: Seedream reporta ventaja en layout/tipografía frente a Nano Banana 2, según el fabricante [[5]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image).
- vs. **nano-banana**: Nano Banana admite hasta 14 referencias; Seedream Pro Edit llega a 10 [[6]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit).
- vs. **flux**: FLUX gana en edición local rápida/barata; Seedream rinde mejor en prompts largos y densos, y en localizar ediciones por coordenadas en vez de solo descripción textual.
- vs. **qwen-image**/**mai-image**: [sin verificar] sin benchmark cruzado revisado.
- En la familia: `sequential`/`edit-sequential` para series; `edit` simple para un resultado puntual; `edit` con `<point>`/`<bbox>` (solo 5.0 Pro/Lite) cuando la zona a tocar es ambigua en texto; `layer-decomposition` (solo 5.0 Pro) para capas editables.

| Necesidad | Variante recomendada |
| --- | --- |
| Mejor calidad única, infografía compleja | `bytedance/seedream-v5.0-pro/text-to-image` |
| Edición con 1-10 refs, identidad/luz/tono | `bytedance/seedream-v5.0-pro/edit` |
| Edición localizada por punto/caja (zona ambigua en texto) | `bytedance/seedream-v5.0-pro/edit` o `v5.0-lite/edit` con `<point>`/`<bbox>` |
| Separar un diseño en capas editables | `bytedance/seedream-v5.0-pro/layer-decomposition` |
| Boceto/plano a imagen fotorrealista | `bytedance/seedream-v4.5/edit` o `v5.0-pro/edit` |
| Producción en volumen, coste contenido | `bytedance/seedream-v4.7/text-to-image` o `/edit` |
| Serie coherente de imágenes (storyboard, set de iconos) | `.../sequential` o `.../edit-sequential` (4.0, 4.5, 4.7, 5.0-lite) |
| Volumen muy alto, coste mínimo | `bytedance/seedream-v5.0-lite` |
| Vía Runway API/Crun | `runway/seedream5_lite/image`, `runway/seedream5_pro/image` |

## Linaje y variantes en Nexus
- **4.0** (sept. 2025): base DiT+VAE, 4K nativo [[1]](https://www.atlascloud.ai/models/bytedance/seedream-v4). Ids: `seedream-v4`, `/edit`, `/edit-sequential`, `/sequential`.
- **4.5** (dic. 2025): más fidelidad profesional, preservando estructura/luz/color al editar [[2]](https://www.atlascloud.ai/models/bytedance/seedream-v4.5). Ids: `seedream-v4.5`, `/edit`, `/edit-sequential`, `/sequential`.
- **4.7**: equilibrio calidad/edición local con coste cercano a 4.0, para producción a gran escala [[3]](https://www.atlascloud.ai/models/bytedance/seedream-v4.7/text-to-image). Ids: `/text-to-image`, `/edit`, `/edit-sequential`, `/sequential`.
- **5.0 Lite** (feb. 2026): Chain-of-Thought y búsqueda web en tiempo real según el fabricante; prioriza velocidad/volumen [[4]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-lite). También soporta edición por coordenadas `<point>`/`<bbox>` según la guía de interacción [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide). Ids: `seedream-v5.0-lite`, `/edit`, `/edit-sequential`, `/sequential`.
- **5.0 Pro** (jun.-jul. 2026): flagship, máxima adherencia a prompts largos, tipografía CJK, edición por `<point>`/`<bbox>` normalizados 0-999 y `layer-decomposition` [[5]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image) [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide). **No** tiene `/sequential` ni `/edit-sequential`: una sola imagen por llamada, como motor de "hero assets". Ids: `/text-to-image`, `/edit`, `/layer-decomposition`. En BytePlus el id de referencia del backend es `dola-seedream-5-0-pro-260628` [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide); existe también un tier **"flash"** en BytePlus, ausente en Nexus (solo `lite`/`pro`). Runway/Crun: `runway/seedream5_lite/image`, `runway/seedream5_pro/image` [sin verificar contrato exacto, revisa `get_model_schema`].

## Fortalezas
- **prompt_adherence**: interpretación multi-paso de prompts largos (relaciones espaciales, layout, luz, intención estilística), "notablemente superior" en 5.0 Pro [[5]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image). También convierte razonamiento en contenido visual denso (fórmulas, diagramas, ilustraciones educativas) cuando se usa terminología técnica precisa [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide).
- **text_rendering**: tipografía/layout de diseño, CJK preciso; el fabricante dice superar a Nano Banana 2 aquí [[5]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image).
- **multi_reference_image**: 1-10 referencias por llamada, razonando entre ellas para transferir sujeto, estilo o fusionar elementos; soporta además pistas visuales (flechas, cajas, garabatos) sobre la propia imagen de referencia [[6]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit) [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide).
- **high_resolution**: 4K nativo desde 4.0 [[1]](https://www.atlascloud.ai/models/bytedance/seedream-v4); en 5.0 Pro, tier ~1.5K económico y 2K premium (16:9 hasta ≈2848×1600) [[5]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image).
- **photorealism**: iluminación física, sombras y piel convincentes; ejemplos oficiales de retoque facial realista [[8]](https://seed.bytedance.com/en/seedream5_0_pro).
- **identity_preservation**: Pro Edit preserva rasgos faciales, luz y color del material fuente al modificar [[6]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit).

## Debilidades
- 5.0 Pro no genera series en Nexus (sin `sequential`/`edit-sequential`): para secuencias, usa 4.0/4.5/4.7/5.0-lite.
- [sin verificar] identidad facial frente a Nano Banana Pro/FLUX Kontext, sin benchmark cruzado revisado.
- [sin verificar] el tier "flash" del fabricante no está confirmado en el catálogo de Nexus.
- Mejora de 4.7 sobre 4.5 descrita solo cualitativamente; usa el schema en vivo para coste/latencia [[3]](https://www.atlascloud.ai/models/bytedance/seedream-v4.7/text-to-image).
- La edición por `<point>`/`<bbox>` depende de que quien construye el prompt calcule bien la normalización 0-999; un error de escala produce ediciones desplazadas [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide).

## Tareas ideales / Tareas que debe evitar
Ideal: `text_to_image`, `image_edit` (descriptiva y por coordenadas en 5.0 Pro/Lite), `multi_reference_image` (hasta 10 refs en 5.0 Pro Edit, boceto/plano→imagen), `layer_decomposition` (solo `v5.0-pro/layer-decomposition`).
Evitar: series coherentes en `v5.0-pro/*` (no existe en Nexus); vídeo o cámara dinámica (imagen fija).

## Estructura de prompt recomendada
Lenguaje natural coherente **sujeto + acción + entorno**, con estilo/color/luz/composición solo si importa estéticamente; conciso y preciso suele batir a largo y florido, sobre todo desde que 4.0/4.5 entienden mejor el prompt que Seedream 3.0 y ya no necesitan "apilar" vocabulario ornamentado [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). Longitud óptima: 20-80 palabras para una imagen suelta; prompts más largos y estructurados (listas de elementos por zona) solo cuando la escena es realmente densa (infografías, interiores con muchos objetos, storyboards). Idioma: inglés es el idioma con más evidencia oficial de la guía de prompting; español y otros idiomas funcionan directamente, incluso para el texto que debe aparecer legible en la imagen.

```
[Sujeto + acción + entorno, una frase natural]
+ [estilo / paleta / luz / composición, solo si importa estéticamente]
+ "[texto exacto]" entre comillas dobles si debe aparecer legible en la imagen
+ para edición: [acción: add/remove/replace/modify] + [elemento objetivo, descrito o con <point>/<bbox>]
+ ", keeping [qué permanece igual: pose, luz, fondo, expresión...]"
+ para multi-referencia: "Image 1" / "Image 2"... + [qué extraer de cada una] + [escena final]
```
Tono: prosa natural, no etiquetas sueltas — la guía oficial marca literalmente "Avoid: Girl, umbrella, tree-lined street, oil painting texture" frente a "Recommended: A girl in a lavish dress walking under a parasol along a tree-lined path, in the style of a Monet oil painting" [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). Sintaxis especial: comillas dobles para texto exacto en la imagen; numerar siempre las referencias como "Image 1", "Image 2"... cuando hay más de una imagen de entrada [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide); en 5.0 Pro/Lite, `<point>x y</point>` y `<bbox>x1 y1 x2 y2</bbox>` normalizados 0-999 (origen 0,0 arriba-izquierda, 999,999 abajo-derecha), escritos pegados a la etiqueta de imagen, p. ej. `Image 1<point>520 460</point>` [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide); en `layer-decomposition`, `<bbox>` en escala 0-1000 [[7]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/layer-decomposition); frases disparadoras "a series"/"a set"/número exacto para activar `sequential` [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). [sin verificar] soporte de `--parámetros` estilo Midjourney.

## Plantillas por tarea

### Text-to-image
`[Sujeto + acción + entorno] + [estilo / paleta / luz / composición] + "[texto exacto]" si aplica`

Si hay un caso de uso concreto (logo, póster, UI), decláralo explícitamente al inicio ("Design a logo for...", "Generate a poster with...") en vez de describir solo el resultado visual [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide).

- Cinematográfico: `Mateo, a weathered fisherman in his forties wearing a cracked leather jacket, and his younger brother Vidal, in a wool beanie, mend a beached wooden boat together on a pebble beach at dawn on the Galician coast. Low raking golden light, thin fog rolling over the water, a fishing net stretched between them, realistic wet-wood texture, medium-shot composition, 35mm cinematic photography style.`
- Producto/estilizado: `A minimalist perfume bottle made of frosted amber glass floats above a reflective black marble pedestal, surrounded by drifting gold dust particles and a single orchid petal. Soft volumetric studio lighting from the upper left, a deep shadow gradient background, ultra-clean product photography style, the brand name "LUMEN" engraved on the cap in thin serif letters.`
- Diferencias entre variantes: 5.0 Pro sostiene prompts largos y densos (infografías, diagramas con terminología técnica) mejor que 4.0/4.7, que rinden de forma más estable con frases concisas de una sola escena [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide) [[5]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image).
- No pongas: listas de etiquetas sueltas separadas por comas sin verbo ni relación espacial (p. ej. "cat, bookstore, warm light, 4k, best quality"); texto que deba aparecer legible sin comillas dobles ("Avoid: Generate a poster titled Seedream 4.5" frente a "Recommended: ... with the title \"Seedream 4.5\"") [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide); mezclar dos idiomas de texto en la imagen sin indicar cuál va en cada elemento; adjetivos redundantes apilados en vez de una frase descriptiva completa.

### Image edit (edición por descripción y por coordenadas en 5.0 Pro/Lite)
`[Acción: add/remove/replace/modify] + [elemento objetivo, descrito con precisión o localizado con <point>/<bbox>] + ", keeping [qué permanece igual]"`

Usa instrucciones concisas e inequívocas, evita pronombres vagos y, si otros elementos deben permanecer igual, dilo explícitamente: la guía oficial contrasta "Recommended: Dress the tallest panda in pink Peking Opera costume and headgear, keeping its pose unchanged" con "Avoid: Put that one in pink clothes" [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide).

- Cinematográfico (descriptiva): `Replace the plain hoodie Mateo is wearing with a weathered leather jacket with a torn left sleeve, keeping his stance, the neon-lit alley behind him, and his brother Vidal's expression in the background completely unchanged.`
- Producto/acción (por coordenadas, solo 5.0 Pro/Lite): `Replace the object at Image 1<point>520 460</point> with a chrome trophy, keeping the marble pedestal, the studio lighting, and the floor reflections unchanged.` [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide)
- Diferencias entre variantes: 4.0/4.5/4.7 solo editan por descripción textual del elemento objetivo; **únicamente 5.0 Pro y 5.0 Lite** entienden `<point>`/`<bbox>` normalizados 0-999 para localizar la edición con precisión de píxel, incluida edición cruzada entre imágenes, p. ej. `Use the subject in Image 2<bbox>118 331 933 871</bbox> to replace the subject in Image 1<bbox>179 283 796 986</bbox>` [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide). Cuando el `<bbox>` cubre varios sujetos, añade una descripción adicional ("the person on the left", "the cat wearing a hat") para que el modelo elija el objetivo correcto [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide). Cuando la escena es compleja y difícil de describir solo con texto, usa flechas, cajas o garabatos sobre la imagen de referencia en vez de forzar una descripción textual larga [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide).
- No pongas: pronombres vagos como "that one" o "it" cuando hay varios candidatos; coordenadas en píxeles crudos sin normalizar a la escala 0-999; `<point>`/`<bbox>` en ids 4.0/4.5/4.7 (no lo soportan); varias ediciones distintas encadenadas con "and" sin indicar qué se conserva — divide en pasos o usa `edit-sequential` si necesitas una progresión.

### Multi-referencia (combinación, transferencia de estilo, boceto → imagen)
`"Image 1": [qué extraer] + "Image 2": [qué extraer] + ... + [escena final deseada, con layout]`

Numera siempre las referencias ("Image 1", "Image 2"...) y describe qué extraer de cada una antes de la escena final; para combinar, el patrón oficial es "replace the character in Image 2 with the character from Image 1, and generate the result in the style of Image 3" [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide).

- Cinematográfico: `Replace the character in Image 2 with Vidal from Image 1, and render the result in the gritty film-noir lighting style of Image 3, keeping the pier backdrop and the fishing boat from Image 2 unchanged.`
- Producto/estilizado (transferencia de estilo): `Apply the matte ceramic texture and warm terracotta palette from Image 2 to the sneaker in Image 1, keeping its silhouette, laces, and studio background exactly the same.` (patrón base: "Apply the style of Image 2 to Image 1" [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide))
- Boceto/plano a imagen (variante dentro de la misma tarea): sube una referencia nítida; si contiene anotaciones de texto, indica "Generate based on the text in the image"; aclara el sujeto principal y el requisito ("a high-fidelity UI interface", "a modern living room"); fija qué debe coincidir exactamente con el boceto (disposición de muebles, orientación, estructura espacial) [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). Ejemplo real de la guía: `Based on this floor plan, generate a photorealistic image of a "modern minimalist furnished living room + open dining area". The room layout and furniture placement should exactly match the reference. Use a Mediterranean color palette, and keep the spatial structure and orientation consistent with the example. Do not include any text or hand-drawn edges from the original sketch.` [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide)
- Diferencias entre variantes: hasta 10 referencias documentadas en 5.0 Pro Edit [[6]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit); en 5.0 Pro/Lite puedes además anclar el resultado de una referencia dentro de la caja de otra con `<bbox>`, p. ej. `Place the subject from Image 1<bbox>179 283 796 986</bbox> at the position of Image 2<bbox>118 331 933 871</bbox>` [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide); 4.0/4.5/4.7 solo combinan por descripción textual, sin coordenadas.
- No pongas: referencias sin numerar ("the image" cuando hay varias); mezclar en una sola cláusula qué se extrae y cómo queda la escena final sin separarlos; asumir que el límite de referencias es ilimitado — revisa el schema en vivo; dejar sin resolver qué pasa con los elementos de la imagen base que no se mencionan.

### Layer decomposition (automática y dirigida, solo 5.0 Pro)
`[sin prompt: separación automática]` | `Split out [elemento 1], [elemento 2] and [elemento 3] into separate layers, keeping [atributo] intact` | `<bbox>x1 y1 x2 y2</bbox>` por capa si necesitas precisión exacta

Subir la imagen sin ningún prompt activa la separación automática en texto, sujetos, decoración y fondo; para controlar exactamente qué se separa, nombra los elementos en lenguaje natural [[7]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/layer-decomposition).

- Cinematográfico: `Split out Mateo in the foreground, Vidal standing slightly behind him, the glowing neon shop sign text, and the rain-soaked street in the background into four separate layers, keeping each subject's own lighting, shadows and reflections intact so every layer can be recomposed independently.`
- Producto/estilizado: `Separate this sneaker product shot into independent layers: the shoe itself, the floating dust particles, the brand logotype text, and the gradient studio backdrop, preserving the shoe's original shadows and highlights on its own layer.`
- Diferencias entre variantes: tarea exclusiva de `v5.0-pro/layer-decomposition`; no existe en 4.0/4.5/4.7/5.0-lite ni en `v5.0-pro/edit` o `/text-to-image`. Máximo documentado de 16 capas por imagen [[7]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/layer-decomposition).
- No pongas: pedir esta tarea en cualquier id que no sea `v5.0-pro/layer-decomposition`; prompts de edición ("replace", "add") en vez de nombrar elementos a separar; esperar que separe más de las ~16 capas documentadas sin verificar el límite real en el schema.

### Sequential / edit-sequential (serie coherente, 4.0/4.5/4.7/5.0-lite)
`Generate [N] [tipo de asset] for [tema], covering [variantes/escenas], keeping [estilo/personaje] consistent.`

Dispara el modo serie con "a series"/"a set" o especificando el número exacto de imágenes; soporta continuidad de personaje y estilo unificado, útil para storyboards, cómics, sets de iconos o packs de producto [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide).

- `Generate four film storyboard images, corresponding to the following scenes: astronauts repairing a spacecraft in a space station, suddenly encountering an asteroid belt attack, astronauts taking emergency evasive action, and narrowly returning to the spacecraft after being injured.` [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide)
- `Generate seven mobile phone wallpapers for Monday through Sunday, featuring natural landscapes, with each image labeled with the corresponding date.` [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide)
- También válido para sets de producto: `Generate a set of three product shots of the same sneaker: front view, side view and top-down view, keeping color, materials and studio lighting consistent across all three.`
- No pongas: pedir una serie al id `v5.0-pro/*` (no soporta esta variante en Nexus); omitir la frase disparadora ("a series"/"a set"/número exacto) y esperar continuidad igualmente.

## Idioma, longitud y prompt negativo
Inglés es el idioma con más evidencia oficial; español y otros idiomas funcionan directamente, incluso para texto multilingüe en la imagen [[8]](https://seed.bytedance.com/en/seedream5_0_pro). Conciso para escenas simples (20-50 palabras); largo y estructurado para escenas densas como storyboards o infografías (hasta ~80-120 palabras, describiendo elemento por elemento) [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). No hay `negative_prompt` documentado; expresa restricciones en positivo, p. ej. "Do not include any text or hand-drawn edges from the original sketch" en vez de un campo negativo separado [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). El schema en vivo manda sobre cualquier opción no documentada aquí.

## Referencias e imagen inicial
Numera las referencias (Image 1, Image 2...) y describe qué extraer de cada una antes de la escena final [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). Para bocetos/wireframes/planos: entrega una referencia nítida, indica si debe generarse texto desde anotaciones ("Generate based on the text in the image"), aclara el sujeto/requisito principal y fija qué debe coincidir exactamente (layout, orientación, estructura espacial) [[9]](https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide). En 5.0 Pro/Lite, convierte anotaciones visuales (punto o caja dibujada sobre el lienzo) a `<point>`/`<bbox>` normalizadas 0-999 pegadas a la etiqueta de imagen, para más precisión que una descripción puramente textual [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide).

## Cámara y movimiento
No aplica movimiento temporal ni cámara dinámica: imágenes fijas. El equivalente más cercano es el control espacial del encuadre (aspect ratio en lenguaje natural, "16:9"/"vertical 9:16", "medium-shot composition") y, en 5.0 Pro/Lite, selección exacta de región vía `<point>`/`<bbox>` para dirigir dónde recae la edición [[10]](https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide).

## Consistencia de personaje
`v5.0-pro/edit` documenta explícitamente preservación de identidad facial, luz y tono al transferir/fusionar sujetos [[6]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit). En 4.0-4.7, la documentación habla de "reference/subject consistency" de forma genérica para `sequential`/`edit-sequential`, sin detallar fidelidad facial exacta [[1]](https://www.atlascloud.ai/models/bytedance/seedream-v4): [sin verificar] ahí, revisa visualmente antes de encadenar el resultado como referencia de la siguiente llamada.

## Restricciones e incompatibilidades conocidas
- No genera vídeo, audio ni voz.
- `layer-decomposition`: máximo documentado de 16 capas por imagen [[7]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/layer-decomposition).
- `edit` en 5.0 Pro: 1-10 referencias documentadas; verifica el límite exacto en el schema en vivo [[6]](https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit).
- `<point>`/`<bbox>` solo en 5.0 Pro/Lite; en el resto de la familia se ignoran o fallan — usa descripción textual pura.
- La variante Pro no soporta `sequential`/`edit-sequential` en Nexus.
- [sin verificar] políticas de moderación específicas; asume las reglas generales de Nexus/BytePlus.

## Errores habituales
- Varios cambios distintos en una sola frase ambigua, en vez de instrucciones concretas una por una.
- No especificar qué permanece igual al editar (pose, color, tipografía, encuadre, expresión).
- Pronombres vagos ("that one", "it") cuando hay varios sujetos candidatos en la imagen.
- Coordenadas en píxeles crudos sin normalizar a 0-999/0-1000 en `<point>`/`<bbox>`, o usarlas en variantes que no las soportan (4.0/4.5/4.7).
- Pedir `sequential` al id `v5.0-pro/*`, que no lo soporta en Nexus.
- Prompts recargados de adjetivos cuando una frase concisa describe mejor sujeto + acción + entorno.
- Asumir que el tier "flash" está disponible en Nexus; solo existen `lite` y `pro`.
- Referencias múltiples sin numerar ("Image 1", "Image 2"...), lo que hace ambiguo qué extraer de dónde.

## Recomendaciones de prompting
- Sujeto + acción + entorno en una frase natural antes de añadir estilo/luz/composición.
- Comillas dobles para cualquier texto que deba aparecer legible en la imagen.
- En edición, nombra qué cambia y qué se mantiene igual explícitamente ("keeping ... unchanged").
- Con sujetos ambiguos, descríbelos con detalle o usa `<point>`/`<bbox>` en 5.0 Pro/Lite, añadiendo una descripción extra si la caja cubre varios candidatos.
- Para boceto/plano → imagen, indica si hay texto a renderizar desde anotaciones y qué debe coincidir exactamente con la referencia.
- Para series, dispara "a series"/"a set" y usa un id `/sequential` o `/edit-sequential` (no la Pro base).
- Reserva `layer-decomposition` para entregables editables por capas; nombra los elementos a separar si no quieres el modo automático.
- Verifica límites de referencias, resolución y coste en el schema en vivo del id concreto.

## Fuentes y fecha
[1] Seedream 4 — Atlas Cloud — https://www.atlascloud.ai/models/bytedance/seedream-v4 — consultado 2026-10-02.
[2] Seedream 4.5 — Atlas Cloud — https://www.atlascloud.ai/models/bytedance/seedream-v4.5 — consultado 2026-10-02.
[3] Seedream 4.7 — Atlas Cloud — https://www.atlascloud.ai/models/bytedance/seedream-v4.7/text-to-image — consultado 2026-10-02.
[4] Seedream 5.0 Lite — Atlas Cloud — https://www.atlascloud.ai/models/bytedance/seedream-v5.0-lite — consultado 2026-10-02.
[5] Seedream 5.0 Pro — Atlas Cloud — https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/text-to-image — consultado 2026-10-02.
[6] Seedream 5.0 Pro Edit — Atlas Cloud — https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/edit — consultado 2026-10-02.
[7] Seedream 5.0 Pro Layer Decomposition — Atlas Cloud — https://www.atlascloud.ai/models/bytedance/seedream-v5.0-pro/layer-decomposition — consultado 2026-10-02.
[8] Seedream 5.0 Pro — ByteDance Seed (página oficial) — https://seed.bytedance.com/en/seedream5_0_pro — consultado 2026-10-02.
[9] Seedream 4.0-4.5 prompt guide — BytePlus ModelArk — https://docs.byteplus.com/en/docs/modelark/seedream-4-0-5-0-prompt-guide — consultado 2026-10-02.
[10] Seedream 5.0 pro/flash interactive editing guide — BytePlus ModelArk — https://docs.byteplus.com/en/docs/modelark/seedream-5-0-pro-editing-guide — consultado 2026-10-02.
