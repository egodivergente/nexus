---
id: 'qwen-image'
kind: model
title: 'Qwen-Image (Alibaba) — familia text-to-image y edit'
version: 2
updated: 2026-10-02
match: ['qwen-image']
modalities: ['image']
strengths: [text_rendering, prompt_adherence, identity_preservation, photorealism, stylized, high_resolution]
weaknesses: [face_consistency, fast, high_resolution]
ideal_tasks: [text_to_image, image_edit, multi_reference_image]
avoid_tasks: [text_to_video, image_to_video, video_edit, lipsync, avatar]
unverified_tags: []
sources:
  - {url: 'https://qwenlm.github.io/blog/qwen-image/', title: 'Qwen-Image: Crafting with Native Text Rendering (blog oficial)', accessed: 2026-10-02}
  - {url: 'https://qwenlm.github.io/blog/qwen-image-edit/', title: 'Qwen-Image-Edit: Image Editing with Higher Quality and Efficiency (blog oficial)', accessed: 2026-10-02}
  - {url: 'https://github.com/QwenLM/Qwen-Image', title: 'QwenLM/Qwen-Image — README y herramienta de Prompt Enhance', accessed: 2026-10-02}
  - {url: 'https://huggingface.co/Qwen/Qwen-Image-Edit-2509', title: 'Qwen/Qwen-Image-Edit-2509 — model card, QwenImageEditPlusPipeline', accessed: 2026-10-02}
  - {url: 'https://www.alibabacloud.com/help/en/model-studio/qwen-image-max', title: 'Alibaba Cloud Model Studio — qwen-image-max Model Info', accessed: 2026-10-02}
  - {url: 'https://www.alibabacloud.com/help/en/model-studio/qwen-image-edit-plus', title: 'Alibaba Cloud Model Studio — qwen-image-edit-plus Model Info', accessed: 2026-10-02}
  - {url: 'https://fal.ai/models/fal-ai/qwen-image', title: 'fal.ai — fal-ai/qwen-image (parámetros, negative_prompt, guidance_scale)', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image', title: 'Atlas Cloud — Qwen-Image 3.0 Pro (Text-to-Image)', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/qwen/qwen-image-2.0-pro/text-to-image', title: 'Atlas Cloud — Qwen Image 2.0 Pro Text-to-Image', accessed: 2026-10-02}
  - {url: 'https://replicate.com/qwen/qwen-image-edit', title: 'Replicate — qwen/qwen-image-edit', accessed: 2026-10-02}
---

## Resumen

Qwen-Image (Alibaba) es una familia de modelos de **imagen** (texto→imagen y edición por instrucciones), sobre un MMDiT de 20B, con un rasgo diferencial claro: **renderizado de texto en píxel** de calidad SOTA en inglés y, sobre todo, en chino, ideal para carteles, packaging o infografías con texto legible. Destaca también en edición semántica (restyle, rotación de objeto, cambio de ropa/fondo) y de apariencia (inpainting que preserva el resto del encuadre). No genera vídeo ni audio: en Nexus es puramente imagen, complementaria a Seedream, GPT-Image, Nano Banana, FLUX e Ideogram.

## Cuándo elegirlo y cuándo no

- **Elige Qwen-Image / Max / 3.0** si el plano necesita **texto incrustado y legible** (carteles bilingües CN/EN, packaging, portadas) o composición compleja a seguir al pie de la letra.
- **Elige Edit / Edit Plus (2509) / 3.0 Pro Edit** para **editar una foto existente** manteniendo el resto del encuadre, o edición semántica (restyle, rotación, cambio de ropa) preservando identidad.
- **Mejor GPT-Image** para razonamiento multimodal fino sobre instrucciones ambiguas.
- **Mejor Nano Banana** para edición conversacional iterativa en chat con referencias flexibles.
- **Mejor Seedream** para máximo fotorrealismo de retrato "a la primera".
- **Mejor FLUX** si priorizas velocidad/coste bajo y LoRAs de comunidad.
- **Mejor Ideogram** para tipografía de marca muy controlada en inglés.
- **No la uses** para vídeo, lipsync ni avatar.

| Necesidad | Variante recomendada |
|---|---|
| Texto incrustado bilingüe CN/EN, cartel/infografía | Qwen-Image 3.0 (T2I) o Max |
| Máximo fotorrealismo, mínimo "look de IA" | Qwen-Image-Max |
| Editar foto manteniendo el resto intacto | Edit Plus (2509) / 3.0 Pro Edit |
| Mezclar 2-3 referencias (persona+producto/persona) | Edit Plus (2509) o 3.0 Pro Edit |
| Explorar variaciones rápido / boceto | Qwen-Image 2.0 o Plus |
| ControlNet nativo (profundidad, bordes, pose) | Edit Plus (2509) |

## Linaje y variantes en Nexus

Orden cronológico/capacidad (más reciente arriba):

1. **Qwen-Image 3.0 / 3.0 Pro** (`qwen-image-3.0-pro/edit`, `qwen-image-3.0-pro/text-to-image`, `qwen-image-3.0/edit`, `qwen-image-3.0/text-to-image`) — generación más nueva. `prompt_extend` (DPE "direct" / APE "agent"), resolución T2I hasta 2048×2048, Edit hasta 1440×1440, seed reproducible, hasta 3 referencias en Edit. Atlas la marca **"invite-beta, interface testing"**: punta de gama pero con posible inestabilidad [[8]](https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image). "Pro" sube resolución/fidelidad frente a "3.0" estándar. Esta numeración es terminología de Atlas Cloud, no de la doc pública de Alibaba Cloud Model Studio (tiers oficiales: `qwen-image`, `-plus`, `-max`, `-edit`, `-edit-plus`) **[sin verificar la correspondencia exacta Atlas↔DashScope]**.
2. **Qwen-Image 2.0 / 2.0 Pro** (`qwen/qwen-image-2.0-pro/*`, `qwen/qwen-image-2.0/*`) — generación anterior en Atlas. "Pro" mejora detalle de pelo/joyas/piel y adherencia; resolución 512-2048 px, aspect ratios fijos [[9]](https://www.atlascloud.ai/models/qwen/qwen-image-2.0-pro/text-to-image).
3. **Qwen-Image-Max / Plus (DashScope oficial)** (`alibaba/qwen-image/text-to-image-max`, `-plus`) — tiers oficiales de Alibaba Cloud. Max reduce el "look de IA" frente a Plus, piel/materiales más realistas; equivale al snapshot `qwen-image-max-2025-12-30` [[5]](https://www.alibabacloud.com/help/en/model-studio/qwen-image-max). Plus es el balance calidad/coste.
4. **Qwen-Image-Edit-Plus / 2509** (`alibaba/qwen-image/edit-plus`, `-edit-plus-20251215`) — iteración sept. 2025 (`QwenImageEditPlusPipeline`): edición multi-imagen (1-3 refs), mejor consistencia facial/producto/texto, ControlNet nativo [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509). El snapshot `-20251215` es una versión fechada posterior del mismo linaje.
5. **Qwen-Image-Edit original (ago. 2025)** (`alibaba/qwen-image/edit`, `atlascloud/qwen-image/edit`) — primera Edit: edición semántica/apariencia, texto bilingüe [[2]](https://qwenlm.github.io/blog/qwen-image-edit/). Consistencia facial más débil, corregida después.
6. **Qwen-Image T2I original (ago. 2025)** (`atlascloud/qwen-image/text-to-image`) — modelo base abierto, 20B MMDiT, introdujo el renderizado de texto SOTA [[1]](https://qwenlm.github.io/blog/qwen-image/).

**Recomendación por defecto:** T2I → `qwen-image-3.0-pro/text-to-image` (o `-max` si se prioriza estabilidad frente al beta); Edición → `qwen-image-3.0-pro/edit` o `edit-plus-20251215` si necesitas estabilidad de producción.

## Fortalezas

- **text_rendering** — SOTA en texto dentro de la imagen (EN y especialmente CN), en píxel real, sin overlays [[1]](https://qwenlm.github.io/blog/qwen-image/).
- **prompt_adherence** — SOTA en GenEval, DPG, OneIG-Bench; sigue bien prompts largos y multi-atributo [[1]](https://qwenlm.github.io/blog/qwen-image/), [[9]](https://www.atlascloud.ai/models/qwen/qwen-image-2.0-pro/text-to-image).
- **identity_preservation** — Edit/Edit Plus preservan identidad en ediciones de estilo, rotación o cambio de fondo/ropa [[2]](https://qwenlm.github.io/blog/qwen-image-edit/), [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509).
- **photorealism** — Max reduce el aspecto "sintético", piel/materiales más realistas que Plus [[5]](https://www.alibabacloud.com/help/en/model-studio/qwen-image-max).
- **stylized** — Fotorrealismo, anime, impresionismo, 3D y óleo en el mismo modelo [[1]](https://qwenlm.github.io/blog/qwen-image/).
- **high_resolution** — Hasta 2048×2048 en T2I (3.0 Pro) y 1440×1440 en Edit [[8]](https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image).

## Debilidades

- **face_consistency** — El Edit original tenía consistencia facial/producto más débil; Edit Plus/2509 lo corrige explícitamente, confirmando el punto flojo previo [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509).
- **fast** — Tiers altos con límites de tasa muy bajos (2 RPM en `qwen-image-max` vs. 120 RPM del base), penalizando iteración rápida en el tier más fotorrealista [[5]](https://www.alibabacloud.com/help/en/model-studio/qwen-image-max).
- **high_resolution** — El techo nativo (2048×2048 T2I, 1440×1440 Edit) queda por debajo de rivales con salida nativa 4K; gran formato puede requerir upscaling externo [[8]](https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image).

## Tareas ideales / Tareas que debe evitar

- **Ideales:** `text_to_image`, `image_edit`, `multi_reference_image`.
- **Evitar:** `text_to_video`, `image_to_video`, `video_edit`, `lipsync`, `avatar` — usa Kling, Seedance, Wan o Runway Gen-4.5 para vídeo.

## Estructura de prompt recomendada

Prosa natural y descriptiva en inglés o chino, no tags al estilo Midjourney ni listas de keywords sueltas separadas por comas sin verbo. Longitud ideal: **40-120 palabras en T2I** (óptimo 60-90 para escenas con varios sujetos), **15-60 palabras en Edit** (una instrucción clara, no un párrafo narrativo), **20-80 palabras cuando se combinan referencias**. El modelo sigue mejor un orden sujeto → entorno → estilo → luz → texto que un orden invertido o tags al azar [[1]](https://qwenlm.github.io/blog/qwen-image/), [[9]](https://www.atlascloud.ai/models/qwen/qwen-image-2.0-pro/text-to-image).

Sintaxis especial que SÍ entiende:
- **Comillas rectas `"..."`** alrededor del texto exacto a renderizar — es la señal más fuerte para separar "qué decir" de "cómo se ve la escena" [[1]](https://qwenlm.github.io/blog/qwen-image/).
- En Edit multi-imagen, **referencia explícita por posición** ("Image 1", "Image 2", "Image 3") para indicar de qué foto sale cada elemento [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509).
- Cláusulas de preservación ("keeping the same X", "manteniendo igual Y") para delimitar qué NO debe cambiar en una edición.

Sintaxis que NO entiende de forma nativa: parámetros de Midjourney (`--ar`, `--v`, `--stylize`), tags `@referencia`, timecodes, shot labels tipo `[corte]` (son conceptos de vídeo; Qwen-Image es solo imagen fija) [verificar si Nexus traduce `--ar` antes de llegar al modelo].

```
[sujeto principal], [acción/pose/expresión], en [entorno + relación espacial de elementos secundarios].
Estilo [fotorrealista / anime / ilustración / render 3D / óleo / impresionista].
Iluminación [tipo de luz, hora del día], [plano/encuadre si aplica, p. ej. "primer plano", "plano general"].
Texto visible: "[texto exacto, en EN o CN]" en [ubicación: letrero / portada / etiqueta].
[Detalle fino opcional: textura, material, clima, lente].
```

El repo oficial recomienda su herramienta **Prompt Enhance** (`rewrite()`, basada en Qwen-Plus/Qwen-VL-Max) para expandir prompts cortos en algo con esta estructura automáticamente [[3]](https://github.com/QwenLM/Qwen-Image); en Atlas Cloud equivale a `prompt_extend`/`prompt_extend_mode` (`direct`=DPE, `agent`=APE) en 2.0/3.0 Pro [[8]](https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image) — [verificar si Nexus lo expone vía `get_model_schema`].

## Plantillas por tarea

### Text-to-image

```
[sujeto(s) + rasgo distintivo breve], [acción/pose/expresión], en [entorno detallado, hora/clima].
[relación espacial entre sujeto y elementos secundarios].
Estilo [fotorrealista/anime/ilustración/3D/óleo], iluminación [tipo, dirección, hora del día], [lente/plano].
Texto visible: "[texto exacto]" en [ubicación: letrero, portada, etiqueta, pantalla].
```

Ejemplo 1 (cinematográfico, dos personajes):
```
Two brothers, Mateo and Vidal, sit on a wooden pier at golden hour, Mateo laughing while
pointing at the lake, Vidal holding a thermos and smiling quietly beside him. Mist rises
off the still water behind them, pine trees silhouetted on the far shore. Photorealistic
style, warm backlight, shallow depth of field, 50mm look. A hand-carved sign on the pier
post reads "Lago Azul — Cabaña 7".
```

Ejemplo 2 (producto, distinto registro):
```
A minimalist glass perfume bottle floats mid-air above a reflective black surface, droplets
of water suspended around it, soft studio lighting from above casting a long gradient
shadow. Hyperrealistic product photography style, dark moody background, subtle rim light
on the glass edges. The label on the bottle reads "NOIR 02" in a thin serif font.
```

Diferencias por variante: `3.0`/`3.0-pro` admiten resolución más alta (hasta 2048×2048) y toleran prompts más largos y multi-atributo sin perder adherencia, pero Atlas las marca invite-beta [[8]](https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image); `2.0 Pro` mejora detalle de pelo/joyas/piel frente a `2.0` estándar con el mismo prompt [[9]](https://www.atlascloud.ai/models/qwen/qwen-image-2.0-pro/text-to-image); `Max` reduce el "look de IA" y favorece prompts con menos adjetivos de estilo y más descripción física/material realista [[5]](https://www.alibabacloud.com/help/en/model-studio/qwen-image-max).

No pongas:
- Parámetros `--ar`/`--v`/`--stylize` de Midjourney esperando que los interprete.
- Tags sueltos sin verbo ni relación espacial ("hombre, lago, atardecer, cinematic, 8k").
- El texto a renderizar mezclado dentro de la descripción de la escena sin comillas.
- Más de un bloque de texto largo por imagen: cuanto más texto, más riesgo de errores tipográficos.

### Image edit

```
[instrucción de edición puntual sobre la imagen de referencia], manteniendo [qué debe permanecer igual: pose/luz/fondo/identidad].
```

Ejemplo 1 (cinematográfico, edición de escena con personajes):
```
In this photo of the two brothers Mateo and Vidal on the pier, change the time of day to
blue hour with warm cabin lights turning on behind them, keeping their poses, expressions
and the wooden pier exactly the same.
```

Ejemplo 2 (edición de producto/entorno, distinto registro):
```
Add a wooden signboard above the shop door that reads "OPEN", with a realistic reflection
on the wet pavement; keep the building facade, lighting direction and camera angle unchanged.
```

Diferencias por variante: el **Edit original** (ago. 2025) resuelve bien restyle, rotación de objeto y cambio de ropa/fondo, pero la consistencia facial es más débil en instrucciones que impliquen rostro visible desde otro ángulo; **Edit Plus/2509** corrige explícitamente ese punto y añade ControlNet nativo (profundidad, bordes, pose), por lo que acepta instrucciones que referencian estructura/pose de forma más literal ("sigue la pose de esta imagen de referencia de pose") [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509), [[2]](https://qwenlm.github.io/blog/qwen-image-edit/).

No pongas:
- Instrucciones vagas ("mejóralo", "hazlo más bonito") sin especificar qué cambiar.
- Varias ediciones no relacionadas en una sola frase (cambia el fondo Y la ropa Y el peinado): divide en pasos.
- Pedir un rostro nuevo "de memoria" sin imagen de referencia nítida y frontal: la identidad se degrada.

### Multi-referencia (Edit Plus / 2509, hasta 3 imágenes)

```
Combine [elemento de Image 1] with [elemento de Image 2] and [elemento de Image 3]; [instrucción final de composición/pose/acción].
```

Ejemplo 1 (cinematográfico, fusión de referencias con personajes):
```
Combine Mateo from Image 1 with the cabin interior from Image 2: place him sitting by the
fireplace shown in Image 2, wearing the red jacket from Image 3, same facial identity as
Image 1, warm firelight on his face.
```

Ejemplo 2 (producto + persona, distinto registro):
```
Combine the model's pose from Image 1 with the handbag from Image 2; she now holds the
handbag in her right hand exactly as shown, same lighting as Image 1, studio background
unchanged.
```

Diferencias por variante: el óptimo documentado es **1-3 imágenes de referencia**; con 3 imágenes el modelo necesita que cada una se nombre explícitamente ("Image 1/2/3") y qué elemento tomar de cada una, porque sin esa etiqueta tiende a mezclar atributos de forma menos predecible [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509). `3.0 Pro Edit` admite igualmente hasta 3 referencias según Atlas, pero está en fase invite-beta [[8]](https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image).

No pongas:
- Más de 3 referencias esperando mejor resultado: el óptimo documentado es 1-3 [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509).
- Referencias sin numerar ("la imagen con el vestido") en vez de "Image 2".
- Pedir que combine 3 estilos visuales distintos a la vez sin indicar cuál domina.

## Idioma, longitud y prompt negativo

Idioma óptimo: **inglés o chino** (el chino es el diferencial para texto en imagen) [[1]](https://qwenlm.github.io/blog/qwen-image/). El español sirve para describir la escena, pero el texto a renderizar debe escribirse literalmente en EN/CN, entre comillas. Varios endpoints exponen `negative_prompt` explícito (según fuente; el schema en vivo de Nexus manda) [[7]](https://fal.ai/models/fal-ai/qwen-image). Sin ese campo, formula la restricción como negación en el prompt positivo ("sin texto adicional", "sin marca de agua").

## Referencias e imagen inicial

En Edit, nombra cada referencia por posición ("Image 1/2/3") y qué elemento de cada una conservar (prenda, pose, rostro). Edit Plus/2509 soporta persona+persona, persona+producto, persona+escena, óptimo 1-3 imágenes [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509). En inpainting, indica qué región debe permanecer sin cambios.

## Cámara y movimiento

No aplica: imagen fija, sin control de movimiento. Sí admite **síntesis de nueva vista** (rotar un objeto 90°/180°), lo más cercano a "cámara" aquí [[2]](https://qwenlm.github.io/blog/qwen-image-edit/).

## Consistencia de personaje

Edit Plus/2509 mejora la preservación de identidad facial/producto frente al Edit original [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509). Técnica: parte de una referencia nítida y frontal, e indica "manteniendo el mismo rostro/identidad" al pedir cambios de ropa, fondo o estilo.

## Restricciones e incompatibilidades conocidas

- Solo imagen: no produce vídeo, audio ni lipsync.
- `3.0`/`3.0-pro` marcadas por Atlas como **invite-beta/interface testing**, salida que "no representa calidad final" [[8]](https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image): cuidado al recomendarlas para entregables finales.
- El checker de seguridad de endpoints de terceros (p. ej. fal.ai) puede devolver imagen negra si marca contenido inseguro; deshabilitarlo requiere autorización de cuenta [[7]](https://fal.ai/models/fal-ai/qwen-image).

## Errores habituales

- Texto sin comillas o mezclado con la descripción: puede confundir qué cadena renderizar.
- Usar sintaxis de Midjourney (`--ar`, `--v`) esperando que la interprete nativamente [verificar si Nexus la traduce].
- Pasar más de 3 referencias a Edit Plus esperando mejor resultado (óptimo 1-3) [[4]](https://huggingface.co/Qwen/Qwen-Image-Edit-2509).
- Mezclar edición y generación nueva en el mismo prompt.
- Asumir que "3.0 Pro" entrega siempre calidad de producción: es beta de interfaz.

## Recomendaciones de prompting

- Texto a renderizar entre comillas y en EN/CN, nunca en español.
- Prosa sujeto → entorno → estilo → luz → texto, no tags sueltos.
- En ediciones, sé explícito sobre qué debe permanecer igual.
- Usa el prompt enhancer/`prompt_extend` con prompts de partida muy cortos.
- Para fotorrealismo sin "look de IA", prioriza Max/3.0 Pro sobre estándar/Plus.
- En multi-referencia, mantente en 1-3 imágenes y nómbralas explícitamente.
- Si la entrega es crítica, prefiere variantes no-beta (Edit Plus/2509 estable o Max) sobre 3.0/3.0 Pro.
- No esperes control de cámara ni movimiento: usa una familia de vídeo de Nexus para eso.

## Fuentes y fecha

[1] Qwen-Image: Crafting with Native Text Rendering (blog oficial) — https://qwenlm.github.io/blog/qwen-image/ — consultado 2026-10-02
[2] Qwen-Image-Edit: Image Editing with Higher Quality and Efficiency (blog oficial) — https://qwenlm.github.io/blog/qwen-image-edit/ — consultado 2026-10-02
[3] QwenLM/Qwen-Image — README (Prompt Enhance) — https://github.com/QwenLM/Qwen-Image — consultado 2026-10-02
[4] Qwen/Qwen-Image-Edit-2509 — model card, QwenImageEditPlusPipeline — https://huggingface.co/Qwen/Qwen-Image-Edit-2509 — consultado 2026-10-02
[5] Alibaba Cloud Model Studio — qwen-image-max Model Info — https://www.alibabacloud.com/help/en/model-studio/qwen-image-max — consultado 2026-10-02
[6] Alibaba Cloud Model Studio — qwen-image-edit-plus Model Info — https://www.alibabacloud.com/help/en/model-studio/qwen-image-edit-plus — consultado 2026-10-02
[7] fal.ai — fal-ai/qwen-image (negative_prompt, guidance_scale) — https://fal.ai/models/fal-ai/qwen-image — consultado 2026-10-02
[8] Atlas Cloud — Qwen-Image 3.0 Pro (Text-to-Image) — https://www.atlascloud.ai/models/qwen-image-3.0-pro/text-to-image — consultado 2026-10-02
[9] Atlas Cloud — Qwen Image 2.0 Pro Text-to-Image — https://www.atlascloud.ai/models/qwen/qwen-image-2.0-pro/text-to-image — consultado 2026-10-02
[10] Replicate — qwen/qwen-image-edit — https://replicate.com/qwen/qwen-image-edit — consultado 2026-10-02
