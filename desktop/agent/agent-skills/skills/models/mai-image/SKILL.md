---
id: 'mai-image'
kind: model
title: 'MAI-Image (Microsoft)'
version: 2
updated: 2026-10-02
match: ['^microsoft/mai-image']
modalities: ['image']
strengths: [text_rendering, photorealism, identity_preservation, prompt_adherence, multi_character, low_cost]
weaknesses: [stylized, high_resolution, multi_shot]
ideal_tasks: [text_to_image, image_edit, multi_reference_image]
avoid_tasks: [text_to_video, image_to_video, avatar, upscale]
unverified_tags: [stylized]
sources:
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image'
    title: 'MAI-Image-2.6 Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit'
    title: 'MAI-Image-2.6 Edit (Image-to-Image) — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image'
    title: 'MAI-Image-2.5-Pro Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/edit'
    title: 'MAI-Image-2.5-Pro Edit (Image-to-Image) — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.5/text-to-image'
    title: 'MAI-Image-2.5 Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.5/edit'
    title: 'MAI-Image-2.5 Edit (Image-to-Image) — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.5-flash/text-to-image'
    title: 'MAI-Image-2.5-Flash Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.5-flash/edit'
    title: 'MAI-Image-2.5-Flash Edit (Image-to-Image) — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/text-to-image'
    title: 'MAI-Image-2.6-Flash Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/edit'
    title: 'MAI-Image-2.6-Flash Edit (Image-to-Image) — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://fal.ai/models/microsoft/mai-image-2.6'
    title: 'microsoft/mai-image-2.6 — fal.ai (modelos relacionados/competidores)'
    accessed: 2026-10-02
---

## Resumen

MAI-Image es la familia de imagen de Microsoft AI, hoy en generación 2.6 (flagship y Flash), con la 2.5 (base, Flash, Pro) como generación anterior aún en Nexus. Destaca por el renderizado de texto dentro de la imagen y por edición quirúrgica con composición multi-referencia (hasta 5 imágenes) en 2.6 [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image)[[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). Microsoft la posiciona cerca de GPT Image 2 en calidad pero con mejor precio por punto de Elo [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image). Apunta a fotorrealismo comercial (producto, marca, retrato, señalética), no a ilustración artística libre.

## Cuándo elegirlo y cuándo no

- **MAI-Image 2.6**: texto legible que renderizar o varias referencias que fusionar [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit).
- **MAI-Image 2.6-Flash**: volumen — prototipos, catálogos, personalización [[9]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/text-to-image).
- **MAI-Image 2.5-Pro**: solo si ya trabajas en pipeline 2.5; para nuevo trabajo, 2.6 lo supera casi en todo [[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image). Evita el resto de 2.5 en proyectos nuevos: 2.6 mejora mucho el texto (+91 Elo), retratos, 3D, y suma auto aspect ratio y búsqueda web [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image).
- **Mejor Nano Banana/Pro**: edición conversacional ultrarrápida al mínimo coste [[11]](https://fal.ai/models/microsoft/mai-image-2.6). **Mejor GPT Image**: ecosistema OpenAI/edición multi-turno; en benchmarks quedan cerca en iluminación y UI/UX [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image). **Mejor Seedream**: lotes masivos de ilustración/estilo. **Mejor FLUX**: pesos abiertos/ControlNet (MAI-Image es cerrado). **Mejor Midjourney**: estética pictórica/moodboard — MAI-Image prioriza fotorrealismo comercial y texto, no exploración estilística libre [sin verificar].

| Necesidad | Variante recomendada |
| --- | --- |
| Cartel/packaging con texto exacto | MAI-Image 2.6 (t2i) |
| Fusionar producto+modelo+estilo de 3 fotos | MAI-Image 2.6 Edit |
| Lote de cientos de variantes | MAI-Image 2.6-Flash Edit |
| Imagen anclada a hecho/lugar real reciente | MAI-Image 2.6 + `enable_web_search` |
| Prototipo barato antes de la final | 2.6-Flash → remate en 2.6 |
| Retoque puntual (recolor, limpiar artefacto) sin tocar el resto | MAI-Image 2.6 Edit, `size:"default"` |
| Recomponer un encuadre o cambiar su relación de aspecto | MAI-Image 2.6 (t2i o edit), `size:"auto"` |
| Reescribir texto de un cartel/UI ya existente | MAI-Image 2.6 Edit |
| Pipeline ya construido sobre 2.5 que no se puede migrar ya | MAI-Image 2.5 / 2.5-Pro (sin multi-referencia) |

## Linaje y variantes en Nexus

1. **2.5 / 2.5-Flash** (2 jun 2026): t2i e image-to-image edit; fotorrealismo, retratos, texto mejorado. Flash: misma arquitectura, optimizada en velocidad/coste [[5]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5/text-to-image)[[7]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-flash/text-to-image).
2. **2.5-Pro** (preview 23 jul 2026, `2026-06-19`): tier premium — consistencia de objeto en escenas densas, más razonamiento visual y precisión de materiales [[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image).
3. **2.6 / 2.6-Flash** (`2026-07-31`) — **generación más reciente, recomendar por defecto**. Suma sobre 2.5: hasta 5 referencias por petición, `size:"auto"`, y `enable_web_search`. +79 Elo sobre 2.5 (mayor salto en texto +91 Elo, retratos, 3D); nº2 en Arena t2i, nº1 en edición según Artificial Analysis [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image)[[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). 2.6-Flash: ~2.8× más rápida que GPT-Image-2-Medium, <50% del coste del flagship, mismas capacidades [[9]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/text-to-image).

| Id Nexus | Tier | Nota |
| --- | --- | --- |
| `mai-image-2.5/*` | base 2.5 | reemplazada por 2.6; 1 sola referencia en edit |
| `mai-image-2.5-flash/*` | fast/cheap 2.5 | menor fidelidad que 2.5 base |
| `mai-image-2.5-pro/*` | pro 2.5 | premium de 2.5; sin multi-referencia |
| `mai-image-2.6/*` | flagship 2.6 | **recomendado por defecto**; multi-ref 1–5, web search, auto aspect ratio |
| `mai-image-2.6-flash/*` | fast/cheap 2.6 | mismas capacidades que 2.6, más rápida y barata |

No hay tier "developer" en este catálogo.

## Fortalezas

- **Renderizado de texto** — texto citado entre comillas sale legible en carteles, packaging, señalética, UI; +91 Elo sobre 2.5 [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image).
- **Fotorrealismo** — iluminación, materiales y piel consistentes, fuerte en 2.6 y 2.5-Pro [[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image).
- **Preservación de identidad** — en edit mantiene cara/identidad/composición; 2.5-Pro y 2.6 la sostienen entre poses y ángulos [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit).
- **Fidelidad a la instrucción** — razona sobre objetos, escala, posición e iluminación, de modo que instrucciones ambiguas o aplicadas sobre una foto real resuelven en resultados físicamente plausibles [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit).
- **Multi-personaje/multi-referencia** — solo 2.6: hasta 5 referencias combinables, cada una reconocible; una referencia "bloqueada" (producto, personaje, logo) se mantiene estable mientras recompones o redimensionas [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit).
- **Bajo coste relativo** — mejor precio por punto de Elo según Microsoft; 2.6-Flash cuesta <50% del flagship con igual funcionalidad [[9]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/text-to-image).

## Debilidades

- **Resolución limitada** — tope ~1.5K-class (2.359.296 px, lados entre 768 y 2346) en 2.6; 2.5 ~1024×1024-equivalente; sin salida nativa 4K [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image).
- **Una imagen por petición** — sin variantes/grid ni storyboard multi-shot nativo [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image).
- **Estilo** — optimizado para fotorrealismo comercial; sin evidencia de fuerza en ilustración pictórica tipo Midjourney [sin verificar].

## Tareas ideales / Tareas que debe evitar

**Ideales:** `text_to_image`, `image_edit`, `multi_reference_image` (solo 2.6/2.6-Flash).
**Evitar:** `text_to_video`, `image_to_video` (sin vídeo/movimiento), `avatar` (sin lipsync/avatar), `upscale` (sin endpoint dedicado; resolución fija por tier).

## Estructura de prompt recomendada

Orden que mejor sigue el modelo: **sujeto (con atributos clave) → entorno → medio/lente/encuadre → iluminación → ánimo/estilo → texto exacto a renderizar (si aplica)**. Esta jerarquía está explícita en la documentación oficial del modelo, que recomienda nombrar primero el sujeto y cerrar con "medium and lens" e iluminación [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image)[[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image). Prosa natural en inglés, no listas de etiquetas sueltas separadas por comas sin verbo; nombra materiales explícitamente ("brushed aluminum", "wet asphalt") para aprovechar su razonamiento físico sobre objetos, escala y luz [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). Idioma confirmado en specs: "English (primary)" [[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image); otros idiomas, sin garantía [sin verificar]. Longitud: hasta 32.000 tokens de contexto de prompt, pero 40–150 palabras suelen bastar para una imagen; usa el margen largo solo para listas de edición detalladas o guías de marca [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image)[[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). Sintaxis especial: el texto a renderizar va literal entre comillas dobles seguido de su posición ("a poster with the headline \"Summer Sessions\" centered at the top") [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image); no hay `--ar`/`--style` tipo Midjourney ni tags `@referencia` — el aspect ratio va por el campo JSON `size` (`"ancho*alto"`, `"default"` o `"auto"`), nunca por sintaxis dentro del prompt; en multi-referencia (edit), las imágenes se nombran por posición/orden ("the first image", "the second image"), no por `@tag` [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit).

```
[subject with key attributes], [environment], [medium/lens/framing], [lighting], [mood/style]. [Optional: "exact text to render" + its position].
```

## Plantillas por tarea

### Text-to-image

Plantilla (orden: sujeto → entorno → medio/lente/encuadre → iluminación → ánimo → texto):
```
[subject with distinguishing attributes], [environment/setting], [medium, lens, framing — e.g. "studio product photograph, 85mm, shallow depth of field"], [lighting], [mood/style]. [Optional: A [poster/sign/label] reads "[EXACT TEXT]" [position, e.g. "centered at the top"]].
```
Diferencias por variante: 2.6/2.6-Flash entienden `size:"auto"` (deja que el modelo elija el encuadre) y pueden anclar el prompt a hechos reales con `enable_web_search`; 2.5/2.5-Flash/2.5-Pro solo tienen tamaños fijos y sin búsqueda web, así que para esas variantes especifica siempre un `size` explícito y evita referencias a eventos/lugares que cambien con el tiempo [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image)[[5]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5/text-to-image). 2.6-Flash tolera prompts algo más escuetos que el flagship 2.6 sin perder legibilidad de texto, pero para retratos y materiales complejos el 2.6 estándar sigue resolviendo mejor matices finos de luz [[9]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/text-to-image).

Ejemplo 1 (cinematográfico, dos personajes):
```
Two brothers, Mateo (late 20s, dark curly hair, denim jacket) and Vidal (early 20s, buzz cut, olive field jacket), sit shoulder to shoulder on a rusted pickup tailgate at the edge of a sunflower field at golden hour. Shot on 50mm, shallow depth of field, warm backlight rimming their hair, dust motes drifting in the air, quiet nostalgic mood, muted film-grain color grade.
```
Ejemplo 2 (producto, distinto registro):
```
A matte-black wireless earbud case floating above a pool of rippling mercury-like liquid on a seamless charcoal backdrop, studio product photograph, 100mm macro lens, hard rim light with soft fill, high-contrast commercial mood. A small embossed tag on the case reads "PULSE" centered on the lid.
```
No pongas: adjetivos vacíos sin sustento visual ("beautiful", "amazing"); texto a renderizar sin comillas ni posición; más de un sujeto principal compitiendo por el centro de atención sin indicar jerarquía; peticiones de varias variantes o un grid en una sola llamada (el endpoint entrega una imagen por petición).

### Image edit (una referencia)

Plantilla (orden: qué cambiar → estado final → qué preservar → texto si aplica):
```
Change [element] to [target end-state], keep [everything to preserve — background, lighting, pose, composition] unchanged. [Optional: replace the text "[OLD TEXT]" with "[NEW TEXT]"].
```
Diferencias por variante: en 2.5/2.5-Flash/2.5-Pro solo se admite una `image` de entrada — sin composición multi-referencia; la instrucción debe bastar por sí sola para describir el estado final [[6]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5/edit). En 2.6/2.6-Flash, aunque envíes una sola referencia, puedes además pedir recomposición de encuadre con `size:"auto"` (en 2.5 el tamaño es fijo por tier) [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit).

Ejemplo 1 (retoque narrativo con personajes):
```
In this photo of Mateo and Vidal standing in front of the barn, change the overcast sky to a dramatic orange sunset sky with scattered clouds, and add warm rim light on both brothers' faces to match. Keep their poses, clothing, the barn, and the foreground grass exactly unchanged.
```
Ejemplo 2 (producto, texto):
```
Replace the plain cardboard box with a glossy white box with rounded corners, keep the product's position, shadow, and the studio background unchanged. Replace the text "SAMPLE" on the lid with "LIMITED EDITION", same font weight and placement.
```
No pongas: solo lo que quitar sin decir el estado final deseado (mejor "replace the background with a clean white studio sweep" que "remove the background"); omitir qué debe permanecer igual, lo que deja la composición a la suerte; cambiar luz, layout y paleta a la vez en una sola iteración; esperar que recorte o escale la imagen sin pedirlo (`size:"default"` preserva el encuadre original, úsalo salvo que quieras recomponer).

### Multi-referencia (solo 2.6/2.6-Flash edit)

Plantilla (orden: define el lienzo con la primera imagen → asigna rol a cada referencia por posición → instrucción de composición → qué preservar de cada una):
```
Use [element] from the first image as the base/canvas. Take [element] from the second image, and [element] from the third image. [Composition instruction — how they combine]. Keep the lighting/style of [the Nth image].
```
Diferencias por variante: esta capacidad existe solo en 2.6 y 2.6-Flash; 2.5-family no acepta más de una imagen de referencia, así que cualquier prompt que diga "from the first/second image" no tiene efecto fuera de 2.6 [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit)[[6]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5/edit). El orden del array `reference_images` importa en la práctica: la documentación oficial recomienda poner primero la imagen que define el lienzo/canvas y referirse a las demás por su rol en el prompt ("keep the product from the second image, the lighting from the third") [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit).

Ejemplo 1 (personajes + localización, cinematográfico):
```
Use the barn interior from the first image as the setting. Place Mateo and Vidal, exactly as they appear in the second image, standing near the open doors. Match the warm late-afternoon light already present in the first image, and keep both brothers' faces, clothing, and poses from the second image unchanged.
```
Ejemplo 2 (producto + modelo + fondo, comercial):
```
Put the jacket from the second image on the person from the first image. Set the scene against the storefront background from the third image, matching that image's lighting and color temperature. Keep the person's face and pose from the first image unchanged.
```
No pongas: más de 5 imágenes de referencia (el límite documentado del endpoint); varias referencias compitiendo por describir el mismo elemento sin indicar cuál prevalece; referencias de más de 1.376.256 px sin reescalar antes de enviarlas; dar por hecho que el orden del array es irrelevante — ponlo deliberadamente y nómbralo en el prompt.

## Idioma, longitud y prompt negativo

Inglés confirmado como idioma óptimo [[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image); hasta 32.000 tokens, en la práctica un párrafo claro basta. No existe `negative_prompt` en ningún endpoint — solo `prompt`, `size`, `enable_web_search` (2.6/t2i), `reference_images` (edit), `enable_sync_mode`, `enable_base64_output` [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image)[[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). Verifica siempre contra `get_model_schema`. Expresa restricciones dentro del prompt ("without any text overlay", "keep the background unchanged").

## Referencias e imagen inicial

En 2.6/2.6-Flash edit, `reference_images` acepta 1–5 imágenes (JPEG/PNG, URL o base64 mezclables en el mismo array, <1.376.256 px c/u); actúan como contenido, layout o guía de estilo, no solo lienzo [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). Nómbralas por posición ("the second image") y, en la práctica, coloca primero la que define el lienzo/canvas [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). En 2.5/2.5-Flash/2.5-Pro edit solo hay una `image` — sin multi-referencia [[6]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5/edit). Para ediciones in-place deja `size` en `"default"` (preserva la relación de aspecto de la imagen de entrada); usa `"auto"` solo para recomponer encuadre o convertir una referencia en un asset con otra forma.

## Cámara y movimiento

Modelo de imagen fija, sin control de movimiento de cámara. Responde bien a lenguaje fotográfico estático — focal length, ángulo, profundidad de campo, encuadre explícito de primer/segundo plano y espacio negativo para copy — para dirigir el estilo de "toma" [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image). No esperes paneos ni storyboard en una sola llamada.

## Consistencia de personaje

En 2.5-Pro y 2.6, mantiene a una persona reconocible entre poses, ángulos, expresiones, ropa e iluminación distintos [[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image). En 2.6 edit, una referencia "bloqueada" (producto, personaje, logo) permanece estable mientras recompones o redimensionas la escena a su alrededor [[2]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit). Técnica recomendada: repetir el mismo bloque descriptivo del personaje palabra por palabra entre peticiones t2i sucesivas; en edit multi-referencia (2.6), fijar el personaje/producto como referencia y repetirla en cada petición. En 2.5 base/Flash solo se documenta consistencia dentro de una misma edición, no entre llamadas separadas [[6]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5/edit).

## Restricciones e incompatibilidades conocidas

- Sin `negative_prompt` en ningún endpoint — confirma contra el schema en vivo.
- Edit de 2.5/2.5-Flash/2.5-Pro: una sola referencia; multi-referencia solo desde 2.6.
- Resolución tope ≈1536×1536 (lados 768–2346, hasta 2.359.296 px) en 2.6; ≈1024×1024 en 2.5 — sin salida nativa 4K [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image).
- `enable_web_search` solo existe en 2.6/2.6-Flash [[1]](https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image).
- Respuestas incluyen `has_nsfw_contents`; hay moderación de contenido además del filtrado de datos [[3]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image).
- Edita solo imágenes sobre las que tengas derechos de uso [[4]](https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/edit).

## Errores habituales

- Pedir multi-referencia en un id `2.5*` (usa `2.6`/`2.6-flash`).
- Texto a renderizar sin comillas o sin posición.
- Describir solo lo que se quita, no el estado final deseado.
- No decir qué debe permanecer igual al pedir un cambio puntual.
- Enviar referencias de más de 1.376.256 px sin reescalar.
- Esperar un `negative_prompt` inexistente en esta familia.
- Pedir resolución 4K esperando entrega nativa.
- Cambiar luz, layout y paleta a la vez en una sola iteración.
- En multi-referencia, no indicar el orden/rol de cada imagen ("the first/second image") y dejar que el modelo adivine cuál es el lienzo.

## Recomendaciones de prompting

- Texto a renderizar entre comillas exactas, con su posición.
- Lenguaje de medio y lente en vez de adjetivos vacíos.
- Orden sujeto → entorno → medio/estilo → iluminación → ánimo.
- En edición, declara qué cambiar y qué preservar; describe el estado final, no solo la ausencia.
- En multi-referencia, nombra cada imagen por posición y pon primero la que define el lienzo.
- `size:"auto"` sin lienzo fijo; `"default"` para preservar encuadre en edición.
- `enable_web_search` solo para hechos reales recientes (añade coste/latencia); apágalo en prompts imaginativos.
- Itera un solo eje por vez (luz, layout o paleta).
- Personajes recurrentes: repite el bloque descriptivo verbatim o fija la referencia.
- Trabajo nuevo: parte de 2.6/2.6-Flash; 2.5-family solo por compatibilidad.

## Fuentes y fecha

[1] MAI-Image-2.6 Text-to-Image — https://www.atlascloud.ai/models/microsoft/mai-image-2.6/text-to-image — consultado 2026-10-02
[2] MAI-Image-2.6 Edit — https://www.atlascloud.ai/models/microsoft/mai-image-2.6/edit — consultado 2026-10-02
[3] MAI-Image-2.5-Pro Text-to-Image — https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/text-to-image — consultado 2026-10-02
[4] MAI-Image-2.5-Pro Edit — https://www.atlascloud.ai/models/microsoft/mai-image-2.5-pro/edit — consultado 2026-10-02
[5] MAI-Image-2.5 Text-to-Image — https://www.atlascloud.ai/models/microsoft/mai-image-2.5/text-to-image — consultado 2026-10-02
[6] MAI-Image-2.5 Edit — https://www.atlascloud.ai/models/microsoft/mai-image-2.5/edit — consultado 2026-10-02
[7] MAI-Image-2.5-Flash Text-to-Image — https://www.atlascloud.ai/models/microsoft/mai-image-2.5-flash/text-to-image — consultado 2026-10-02
[8] MAI-Image-2.5-Flash Edit — https://www.atlascloud.ai/models/microsoft/mai-image-2.5-flash/edit — consultado 2026-10-02
[9] MAI-Image-2.6-Flash Text-to-Image — https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/text-to-image — consultado 2026-10-02
[10] MAI-Image-2.6-Flash Edit — https://www.atlascloud.ai/models/microsoft/mai-image-2.6-flash/edit — consultado 2026-10-02
[11] microsoft/mai-image-2.6 en fal.ai — https://fal.ai/models/microsoft/mai-image-2.6 — consultado 2026-10-02
