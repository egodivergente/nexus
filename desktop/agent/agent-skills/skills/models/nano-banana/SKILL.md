---
id: nano-banana
kind: model
title: 'Nano Banana / Gemini Image (Google): Nano Banana, Nano Banana 2 (Lite) y Nano Banana Pro'
version: 2
updated: 2026-10-02
match:
  - '^google/nano-banana'
  - '^google/gemini-[0-9.]+-(flash|pro)-image'
  - '^runway/gemini_(2\.5_flash|image)'
modalities: [IMAGE]
strengths: [identity_preservation, text_rendering, high_resolution, prompt_adherence, multi_character, fast]
weaknesses: []
ideal_tasks: [text_to_image, image_edit, multi_reference_image]
avoid_tasks: [upscale, layer_decomposition]
unverified_tags: []
sources:
  - url: https://ai.google.dev/gemini-api/docs/image-generation
    title: 'Nano Banana image generation — Google AI for Developers'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/google/nano-banana-pro/edit
    title: 'Nano Banana Pro (Gemini 3 Pro Image) — Model Card — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/google/nano-banana-2/edit
    title: 'Google Nano Banana 2 Edit — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/google/nano-banana-2/reference-to-image
    title: 'Google Nano Banana 2 Reference to Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/google/nano-banana-2-lite/text-to-image
    title: 'Google Nano Banana 2 Lite — Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: https://cloud.google.com/blog/products/ai-machine-learning/nano-banana-2-and-nano-banana-pro-are-generally-available
    title: 'Nano Banana 2 and Nano Banana Pro are generally available — Google Cloud Blog'
    accessed: 2026-10-02
---

## Resumen
Nano Banana es la familia de generación/edición de imágenes nativa de Gemini: cuatro modelos (2 Lite, 2, Pro y el legado) con edición conversacional sin máscaras, consistencia de personaje/objeto entre referencias y texto legible en la imagen [[1]](https://ai.google.dev/gemini-api/docs/image-generation). En Nexus cubre text-to-image, image-edit y reference-to-image/multi-referencia. Recomienda **Nano Banana 2** por defecto; **Pro** para encargos complejos (texto, localización, grounding); **2 Lite** para volumen/velocidad; el **legado** debe evitarse — Google pide migrar fuera de él [[1]](https://ai.google.dev/gemini-api/docs/image-generation).

## Cuándo elegirlo y cuándo no
- Elige **Nano Banana 2** para el equilibrio por defecto calidad/velocidad/coste, hasta 10 refs de objeto y 4 de personaje, con 4K nativo [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Elige **Nano Banana Pro** con mucho texto en la imagen, localización/traducción de texto embebido, consistencia de marca, control fino de cámara/luz/grading, composición compleja a partir de varias imágenes de entrada o grounding con Search [[1]](https://ai.google.dev/gemini-api/docs/image-generation)[[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).
- Elige **Nano Banana 2 Lite** si manda volumen/latencia y no necesitas multi-referencia ni edición secuencial multi-turno — el fabricante dice que Lite "not optimized for multiple reference inputs or multi-turn sequential editing" [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Evita el **Nano Banana** original (legado): Google recomienda migrar a 2 Lite por mejor calidad, velocidad y coste [[1]](https://ai.google.dev/gemini-api/docs/image-generation). Mantenlo solo por compatibilidad con integraciones ya cerradas sobre `gemini-2.5-flash-image`.
- Frente a **GPT Image**: Nano Banana 2/Pro gana en edición conversacional sin máscaras y en multi-referencia nativa (hasta 10-14 imágenes combinadas); GPT Image compite en composición de posters/infografías con texto en Latin/CJK [sin verificar comparativa directa de calidad final].
- Frente a **Seedream/FLUX**: compiten en fotorrealismo y velocidad; Nano Banana aporta "thinking" previo a la generación y grounding con búsqueda real para datos verificables [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Frente a **Midjourney**: Midjourney domina en estética estilizada vía `--parámetros` cerrados; Nano Banana sigue mejor instrucciones literales largas y lenguaje natural, y permite iterar conversacionalmente sobre la misma imagen sin reiniciar el prompt [sin verificar comparativa directa].
- Frente a **Ideogram**: Ideogram es especialista histórico en texto en imagen; Nano Banana Pro reivindica el mismo terreno con "advanced localization" (traduce y re-renderiza texto embebido entre idiomas) [[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).
- Frente a **MAI-Image/Qwen-Image/Grok Imagine Image**: sin evidencia comparativa directa [sin verificar].

| Necesidad | Variante recomendada |
|---|---|
| General, uso cotidiano | Nano Banana 2 (`gemini-3.1-flash-image`) |
| Complejo: mucho texto, localización, grounding, 4K | Nano Banana Pro (`gemini-3-pro-image`) |
| Alto volumen / baja latencia / coste mínimo | Nano Banana 2 Lite (`gemini-3.1-flash-lite-image`) |
| Imagen fija extraída de un vídeo + prompt | Nano Banana 2 / 2 Lite / Pro (reference-to-image) |
| Personaje consistente en 5+ tomas o escenas | Nano Banana Pro (hasta 5 refs de personaje) |
| Composición de producto desde varias fotos de entrada | Nano Banana Pro (composición compleja multi-input) |
| Edición rápida en una sola instrucción, sin iterar | Nano Banana 2 Lite |
| Legado (`gemini-2.5-flash-image`) sin otra razón | evitar si hay alternativa |

## Linaje y variantes en Nexus
1. **Nano Banana** = `gemini-2.5-flash-image`, pionero legado; hasta 3 imágenes de entrada, 1024px, optimizado para alto volumen y baja latencia [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
2. **Nano Banana Pro** = `gemini-3-pro-image`, lanzado 20-nov-2025 [[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit); GA ampliada 28-may-2026 [[6]](https://cloud.google.com/blog/products/ai-machine-learning/nano-banana-2-and-nano-banana-pro-are-generally-available). Thinking previo, grounding Search, localización avanzada, composición compleja desde varias imágenes de entrada, hasta 4K [[1]](https://ai.google.dev/gemini-api/docs/image-generation)[[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).
3. **Nano Banana 2** = `gemini-3.1-flash-image`, generalista versátil para todas las tareas, equilibra velocidad con 4K de última generación, world knowledge y renderizado de texto fiable; sobresale en procesado de múltiples imágenes de referencia y consistencia [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
4. **Nano Banana 2 Lite** = `gemini-3.1-flash-lite-image`, el más rápido y barato de la familia, pensado para velocidad y escala cuando esas son las restricciones operativas principales; hasta 14 refs de objeto, sin multi-referencia compleja ni edición secuencial multi-turno [[1]](https://ai.google.dev/gemini-api/docs/image-generation)[[3]](https://www.atlascloud.ai/models/google/nano-banana-2-lite/text-to-image).

**Mapa de ids:** `google/nano-banana/*`(+`-developer`) → legado (`-developer`="cheaper and less stable" [verificar schema]). `google/nano-banana-2/*`, `.../2-lite/*` (+`-developer`) → Nano Banana 2 / 2 Lite. `google/nano-banana-pro/*` (+`-developer`,`-ultra`) → Pro (`-ultra`=mayor resolución [verificar schema]). `google/gemini-3-pro-image`, `-3.1-flash-image`, `-2.5-flash-image` → mismos modelos, id canónico. `runway/gemini_2.5_flash/image`, `gemini_image3.1_flash/image`, `gemini_image3_pro/image` → mismos tres modelos vía Runway API/Crun; trátalos igual. Recomendada por defecto: **Nano Banana 2**; para encargos exigentes, **Pro**.

## Fortalezas
- Edición conversacional sin máscaras, interpretando estructura de escena y semántica de objetos en una sola instrucción natural [[4]](https://www.atlascloud.ai/models/google/nano-banana-2/edit).
- Consistencia de identidad/personaje entre tomas, con plantilla oficial para vistas 360° iterando ángulo a ángulo [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Renderizado de texto claro y coherente — punto fuerte explícito de Pro, incluida traducción/localización de texto embebido entre idiomas [[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).
- Resolución alta: 2 y Pro hasta 4K; el legado se queda en 1024px [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Buen seguimiento de instrucciones incluso en el tier Lite [[3]](https://www.atlascloud.ai/models/google/nano-banana-2-lite/text-to-image).
- Multi-personaje/multi-referencia: hasta 14 imágenes combinadas (hasta 5 personajes en Pro, hasta 4 en Nano Banana 2) [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Composición compleja desde varias imágenes de entrada: Pro puede sintetizar elementos de varias fuentes y texto en una sola escena cohesiva [[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).
- Velocidad: Lite genera en ~4s, la más rápida de la familia [[3]](https://www.atlascloud.ai/models/google/nano-banana-2-lite/text-to-image).

## Debilidades
- Lite "not optimized for multiple reference inputs or multi-turn sequential editing" [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- El legado admite solo 3 imágenes de entrada y 1024px, frente a 10-14 y 4K de los nuevos [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- El número exacto de imágenes de salida pedido no siempre se respeta [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Grounding con Search en Nano Banana 2 no soporta imágenes reales de personas vía web search [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- No es modelo de vídeo (`gemini-omni*` es familia hermana) ni tiene layer_decomposition o upscaling dedicado documentados.

## Tareas ideales / Tareas que debe evitar
- **Ideal:** `text_to_image` (texto legible, estilos muy variados), `image_edit` conversacional (añadir/quitar elementos, inpainting semántico, transferencia de estilo), `multi_reference_image` (personajes/objetos/estilo coherentes combinando varias imágenes de entrada).
- **Evitar:** `upscale` y `layer_decomposition` (no documentados; no hay separación en capas ni upscaler dedicado). En Lite, evita además muchas referencias simultáneas o edición secuencial multi-turno compleja.

## Estructura de prompt recomendada
Idioma óptimo: **inglés** (también ar-EG, de-DE, es-MX, fr-FR, hi-IN, id-ID, it-IT, ja-JP, ko-KR, pt-BR, ru-RU, ua-UA, vi-VN, zh-CN con buen rendimiento) [[1]](https://ai.google.dev/gemini-api/docs/image-generation). Tono: prosa natural y descriptiva en frases completas, no etiquetas sueltas tipo Midjourney; sin sintaxis `--parámetros` [verificar en schema vivo de Nexus]. Longitud: tan larga como haga falta para ser hiperespecífico — el fabricante recomienda describir con detalle rico en lugar de listas cortas de palabras clave [[1]](https://ai.google.dev/gemini-api/docs/image-generation). Sintaxis especial: texto a renderizar entre comillas dobles dentro del prompt; para escenas complejas, divide la petición en pasos ("First, create... Then, add... Finally, place...") en vez de meterlo todo en una frase [[1]](https://ai.google.dev/gemini-api/docs/image-generation). No hay tags `@referencia` ni `[corte]`: las imágenes de referencia se adjuntan como input además del texto, y el prompt las nombra en prosa ("the woman from the second image", "this man", "image 1").

```
[Tipo de toma / plano] de [sujeto hiperespecífico: rasgos, ropa, materiales, textura concretos]
en/con [entorno o contexto], [intención o uso de la imagen si aplica].
[Descripción de la luz: tipo, dirección, calidad].
Shot/visto desde [ángulo de cámara] con [tipo de lente/objetivo].
[Estilo visual o medio: foto, ilustración, 3D...]. [Aspect ratio].
```
Ejemplo general (plantilla oficial de escena fotorrealista) [[1]](https://ai.google.dev/gemini-api/docs/image-generation):
```
A photorealistic wide-angle shot of a vibrant coral reef teeming with tropical fish. Crystal-clear
turquoise water with sunbeams filtering down from the surface, illuminating a sea turtle gliding
gracefully over the coral. Shot from a low perspective with a wide-angle lens. Aspect ratio 16:9.
```
Para escenas con múltiples elementos, construye la petición en pasos secuenciales en lugar de una sola frase larga: "First, create a background of a serene, misty forest at dawn. Then, in the foreground, add a moss-covered ancient stone altar. Finally, place a single, glowing sword on top of the altar." [[1]](https://ai.google.dev/gemini-api/docs/image-generation).

## Plantillas por tarea

### Text-to-image
Orden que mejor sigue el modelo: tipo de toma → sujeto hiperespecífico (incluye accesorios/acción si es ilustración) → entorno/intención → estilo/medio → luz → cámara/lente → aspect ratio. Para texto embebido, el texto exacto va entre comillas dobles y se describe su tipografía/color aparte.
```
A [photorealistic/style] [type of shot] of a [subject with concrete material/texture/color details]
[doing / wearing / in] [setting]. [Light description]. Shot from a [camera angle] with a [lens type].
The design features [visual qualities] and [color/background]. ["Exact text"] in [font/placement]. [Aspect ratio].
```
Ejemplo cinematográfico con personajes:
```
A photorealistic medium shot of two brothers, Mateo and Vidal, sitting on a weathered wooden dock
at dusk, their fishing rods propped beside them. Mateo, the older one in a faded green jacket,
laughs while pointing at the orange horizon; Vidal, younger, in a grey hoodie, watches the water
ripple. Warm golden-hour light rakes across their faces from the left. Shot from eye level with a
50mm lens, shallow depth of field blurring the lake behind them. Aspect ratio 16:9.
```
Ejemplo estilizado/producto, distinto registro:
```
A kawaii-style sticker of a happy red panda wearing a tiny bamboo hat, munching on a green bamboo
leaf. The design features bold, clean outlines, simple cel-shading, and a vibrant color palette.
The background must be solid white. Square aspect ratio.
```
Diferencias por variante: Pro entiende mejor instrucciones de localización/traducción de texto embebido y composición desde varias referencias a la vez; Lite responde bien a prompts cortos y directos pero pierde fidelidad si el prompt mezcla muchas referencias o pasos secuenciales largos [[1]](https://ai.google.dev/gemini-api/docs/image-generation)[[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).
No pongas:
- Listas de palabras clave sueltas tipo Midjourney (`panda, cute, sticker, white bg`): describe en prosa.
- `--ar`, `--style`, `--no` u otros flags de Midjourney (no soportados).
- Texto a renderizar sin comillas ni descripción de tipografía — sale deformado o inconsistente.
- Negaciones directas ("sin gente", "no cars"): usa negativos semánticos (ver más abajo).

### Image edit
Patrón oficial: nombra la fuente ("the provided image of…" o "this [sujeto]"), pide la acción (add/remove/modify/change only), y cierra con qué debe integrarse o permanecer igual. El modelo iguala estilo, luz y perspectiva del original automáticamente, no hace falta pedirlo salvo casos difíciles.
```
Using the provided image of [subject], please [add/remove/modify] [element] to/from the scene.
Ensure the change is [cómo debe integrarse: luz, perspectiva, escala]. Keep [qué conservar] unchanged.
```
Ejemplo cinematográfico con personajes (edición de foto existente):
```
Using the provided photo of Mateo and Vidal on the dock, please remove the fishing rods and add a
small wooden toolbox between them. Make the toolbox look weathered and matched to the warm
golden-hour lighting already in the shot. Keep both brothers' poses and expressions unchanged.
```
Ejemplo de producto/inpainting semántico:
```
Using the provided image of a living room, change only the blue sofa to be a vintage, brown leather
chesterfield sofa. Keep everything else in the image exactly the same, including the pillows on the
sofa and the ambient lighting, unchanged.
```
Variante de transferencia de estilo (misma familia de tarea, patrón distinto):
```
Transform the provided photograph of a modern city street at night into the artistic style of
Vincent van Gogh's "Starry Night". Preserve the original composition of buildings and cars, but
render all elements with swirling, impasto brushstrokes and a dramatic palette of deep blues and
bright yellows.
```
Diferencias por variante: Pro resuelve mejor cambios que exigen razonar sobre varias instrucciones encadenadas o relocalizar texto dentro de la imagen editada; en Lite conviene una sola instrucción de edición por turno — no está pensado para edición secuencial multi-turno [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
No pongas:
- Coordenadas de píxeles o referencias a "máscara"/"capa" manual: no hay edición por máscara explícita, se edita describiendo el cambio.
- Varias ediciones no relacionadas en la misma instrucción si usas Lite — pide una por turno.
- "Keep everything else the same" sin indicar también explícitamente qué cambiar primero: el orden importa para que ancle bien el cambio.
- Negaciones ("quita el coche que no quiero") en vez de la acción directa ("remove the car").

### Multi-referencia (combinar varias imágenes / personajes / objetos)
Patrón oficial: identifica cada imagen de entrada por su contenido o número ("the first image", "image 2", "this man", "the woman from the second image"), describe qué elemento de cada una tomar, y describe la escena final combinada.
```
Create a new image by combining elements from the provided images. Take the [elemento de la
imagen 1] and place it with/on the [elemento de la imagen 2]. The final image should be a
[descripción de la escena final: ángulo, luz, estilo].
```
Ejemplo cinematográfico con personajes (dos referencias de personaje):
```
An office group photo combining Mateo (from the first image) and Vidal (from the second image),
standing side by side in front of a glass office wall, both making funny exaggerated faces at the
camera. Match the lighting and color grading between the two so they look shot in the same room.
```
Ejemplo de producto/e-commerce (otro registro):
```
Create a professional e-commerce fashion photo. Take the blue floral dress from the first image and
let the woman from the second image wear it. Generate a realistic, full-body shot of the woman
wearing the dress, with the lighting and shadows adjusted to match the outdoor environment.
```
Diferencias por variante: Nano Banana 2 admite hasta 10 referencias de objeto + 4 de personaje; Pro sube a 6 de objeto + 5 de personaje + 3 de estilo y añade composición compleja desde múltiples imágenes de entrada con texto; Lite llega a 14 imágenes pero el fabricante advierte que no está optimizado para múltiples referencias combinadas ni edición secuencial — úsalo con 1-2 referencias simples [[1]](https://ai.google.dev/gemini-api/docs/image-generation)[[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).
No pongas:
- Más referencias que el límite del tier sin comprobar `get_model_schema` — el exceso se puede ignorar o degradar el resultado.
- Ambigüedad sobre qué imagen es cuál ("combina las fotos" sin decir qué tomar de cada una).
- Referencias de personas reales de internet esperando grounding con Search: ese modo no soporta imágenes reales de personas vía búsqueda web [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Pedir que el número de imágenes de salida sea exacto como requisito duro: el fabricante avisa que no siempre se respeta [[1]](https://ai.google.dev/gemini-api/docs/image-generation).

### Reference-to-image (vídeo → imagen fija)
`[qué extraer/sintetizar del vídeo], [estilo de salida], [qué conservar del origen], [aspect ratio]`
Ejemplo cinematográfico:
```
Using this video of Mateo and Vidal walking along the dock as reference, create a cinematic
poster-style still of the moment Mateo turns back to laugh at Vidal, backlit by the setting sun.
Portrait 2:3, warm film grain, keep their silhouettes and the dock's geometry faithful to the clip.
```
Ejemplo de producto:
```
Using this product-unboxing video as reference, generate a single high-resolution hero shot of the
device floating against a seamless studio-grey background, as if freshly extracted from the clip's
best-lit frame. Square aspect ratio, soft studio lighting.
```
Diferencias por variante: exclusivo de Nano Banana 2 / 2 Lite / Pro — el legado no admite vídeo de referencia [[5]](https://www.atlascloud.ai/models/google/nano-banana-2/reference-to-image).
No pongas:
- Expectativa de obtener vídeo de salida: la salida es siempre una imagen fija.
- Más de 10 referencias adicionales junto al vídeo sin verificar el límite vigente en el schema [[5]](https://www.atlascloud.ai/models/google/nano-banana-2/reference-to-image).
- Descripciones que re-narran toda la apariencia del vídeo en vez de indicar el instante/encuadre a extraer y el estilo deseado.

### Consistencia de personaje a 360°
`A studio portrait of [persona], against [fondo], [looking forward / in profile looking right / etc.]`
Ejemplo:
```
A studio portrait of this man against a plain white background, in profile looking right, soft
diffused studio lighting, neutral expression.
```
Siguiente toma del mismo personaje (reutilizando la imagen ya generada):
```
A studio portrait of this same man against the same white background, now looking directly at the
camera with a slight smile, identical lighting setup.
```
No pongas:
- Solo la imagen original como referencia en la segunda y siguientes tomas: incluye también las ya generadas del personaje para anclar mejor la consistencia [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Poses muy complejas sin adjuntar una imagen de referencia de esa pose.

## Idioma, longitud y prompt negativo
Inglés es el idioma óptimo (ver lista en "Estructura de prompt"). No hay `negative_prompt` documentado; usa "semantic negative prompts": describe la escena deseada en positivo en vez de negar ("an empty, deserted street with no signs of traffic" en vez de "no cars") [[1]](https://ai.google.dev/gemini-api/docs/image-generation). Verifica el schema en vivo de Nexus por si el proveedor expone un campo propio.

## Referencias e imagen inicial
Para consistencia de personaje, incluye en cada prompt sucesivo las imágenes ya generadas (no solo la original); para poses complejas añade una referencia de la pose [[1]](https://ai.google.dev/gemini-api/docs/image-generation). Límites por tier: 14 objeto (Lite); 10 objeto + 4 personaje (Nano Banana 2); 6 objeto + 5 personaje + 3 estilo (Pro) [[1]](https://ai.google.dev/gemini-api/docs/image-generation). En reference-to-image puedes combinar un vídeo con hasta 10 referencias adicionales [[5]](https://www.atlascloud.ai/models/google/nano-banana-2/reference-to-image). Para combinar varias imágenes en una escena nueva, nómbralas en el prompt por su contenido o número de orden ("the first image", "the woman from the second image") en lugar de etiquetas tipo `@ref1` [[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit).

## Cámara y movimiento
Son modelos de imagen fija: "movimiento" no aplica. Sí entienden bien lenguaje fotográfico/cinematográfico (`wide-angle shot`, `macro shot`, `low-angle perspective`, `medium shot`, isométrico, profundidad de campo, tipo de lente, transformaciones día↔noche, grading de color) [[1]](https://ai.google.dev/gemini-api/docs/image-generation)[[2]](https://www.atlascloud.ai/models/google/nano-banana-pro/edit). No hay control de cámara en el tiempo: no genera vídeo.

## Consistencia de personaje
Punto fuerte documentado con plantilla concreta (retrato de estudio + ángulo), reutilizando imágenes previas como contexto. No garantiza pixel-perfect ni es inmune a deriva en cadenas largas — re-ancla periódicamente con la referencia original [[1]](https://ai.google.dev/gemini-api/docs/image-generation).

## Restricciones e incompatibilidades conocidas
- Sin entrada de audio; vídeo de referencia solo en Gemini 3.1 Flash Image y 3.1 Flash-Lite Image, no en el legado [[1]](https://ai.google.dev/gemini-api/docs/image-generation)[[5]](https://www.atlascloud.ai/models/google/nano-banana-2/reference-to-image).
- "Thinking" en Gemini 3/3.1 va activado por defecto, **no se puede desactivar** vía API, y se factura aunque no se muestre [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Marca de agua invisible SynthID en toda imagen; Imagen (Google) está retirado, todo se redirige a Nano Banana [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Requiere tener los derechos necesarios sobre cualquier imagen subida; no genera contenido que infrinja derechos de terceros ni material engañoso, acosador o dañino — sujeto a la política de uso prohibido del fabricante [[1]](https://ai.google.dev/gemini-api/docs/image-generation).

## Errores habituales
- Usar negaciones directas en vez de negativos semánticos.
- Meter una escena compleja en una sola frase en vez de dividirla en pasos [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Pedir texto en la imagen sin fijarlo antes entre comillas.
- Usar Lite para muchas referencias o edición secuencial compleja.
- Usar el legado por defecto sin motivo, cuando Google recomienda migrar [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- No identificar cada imagen de entrada por su contenido/número al combinar varias referencias, dejando ambigüedad sobre qué tomar de cuál.
- Exigir un número exacto de imágenes de salida como requisito duro, sin margen.

## Recomendaciones de prompting
- Sé hiperespecífico: materiales, texturas y formas concretas, no categorías genéricas [[1]](https://ai.google.dev/gemini-api/docs/image-generation).
- Da el contexto/intención de la imagen, no solo su descripción literal.
- Itera conversacionalmente con cambios pequeños, uno por turno si usas Lite.
- Para texto en la imagen, fija primero el texto exacto entre comillas y describe tipografía/color.
- Usa lenguaje fotográfico/cinematográfico (tipo de plano, lente, ángulo, luz) para dirigir la composición.
- Para un personaje entre tomas, realimenta las imágenes ya generadas, no solo la original.
- Al combinar varias imágenes, nombra cada una por su contenido o número de orden explícitamente.
- Para datos verificables usa Nano Banana 2/Pro con grounding de Search, no el legado.
- Elige el tier por la necesidad real: Lite (volumen/velocidad, 1-2 refs), 2 (general, multi-referencia estándar), Pro (texto/localización, composición compleja multi-input).

## Fuentes y fecha
[1] "Nano Banana image generation" — Google AI for Developers — https://ai.google.dev/gemini-api/docs/image-generation — consultado 2026-10-02.
[2] "Nano Banana Pro" — Model Card — Atlas Cloud — https://www.atlascloud.ai/models/google/nano-banana-pro/edit — consultado 2026-10-02.
[3] "Nano Banana 2 Lite — Text-to-Image" — Atlas Cloud — https://www.atlascloud.ai/models/google/nano-banana-2-lite/text-to-image — consultado 2026-10-02.
[4] "Nano Banana 2 Edit" — Atlas Cloud — https://www.atlascloud.ai/models/google/nano-banana-2/edit — consultado 2026-10-02.
[5] "Nano Banana 2 Reference to Image" — Atlas Cloud — https://www.atlascloud.ai/models/google/nano-banana-2/reference-to-image — consultado 2026-10-02.
[6] "Nano Banana 2 and Nano Banana Pro are generally available" — Google Cloud Blog — https://cloud.google.com/blog/products/ai-machine-learning/nano-banana-2-and-nano-banana-pro-are-generally-available — consultado 2026-10-02.
