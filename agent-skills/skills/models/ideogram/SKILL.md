---
id: 'ideogram'
kind: 'model'
title: 'Ideogram — v4 Quality y v4 Turbo (texto en imagen, diseño gráfico, logos, pósters)'
version: 2
updated: 2026-10-02
match: ['ideogram']
modalities: 'IMAGE'
strengths: [text_rendering, prompt_adherence, stylized, high_resolution, fast]
weaknesses: [photorealism]
ideal_tasks: [text_to_image]
avoid_tasks: [image_edit, multi_reference_image, upscale, layer_decomposition, text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, motion_control, lipsync, avatar]
unverified_tags: [photorealism]
sources:
  - {url: 'https://www.atlascloud.ai/models/ideogram/v4/quality/text-to-image', title: 'Ideogram v4 Quality Text-to-Image — Atlas Cloud', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/ideogram/v4/turbo/text-to-image', title: 'Ideogram v4 Turbo Text-to-Image — Atlas Cloud', accessed: 2026-10-02}
  - {url: 'https://developer.ideogram.ai/ideogram-api/api-overview', title: 'API Overview — Ideogram Documentation', accessed: 2026-10-02}
  - {url: 'https://developer.ideogram.ai/api-reference/images/generate/ideogram-4', title: 'Generate with Ideogram 4.0 — Ideogram API Reference', accessed: 2026-10-02}
  - {url: 'https://developer.ideogram.ai/api-reference/images/generate/ideogram-4-5', title: 'Generate with Ideogram 4.5 — Ideogram API Reference', accessed: 2026-10-02}
  - {url: 'https://developer.ideogram.ai/api-reference/images/precise-edit/ideogram-4-5', title: 'Precise edit with Ideogram 4.5 — Ideogram API Reference', accessed: 2026-10-02}
  - {url: 'https://developer.ideogram.ai/api-reference/images/generate/auto', title: 'Generate with automatic model selection — Ideogram API Reference', accessed: 2026-10-02}
  - {url: 'https://replicate.com/ideogram-ai/ideogram-v4-quality', title: 'Ideogram 4.0 Quality — Replicate', accessed: 2026-10-02}
  - {url: 'https://replicate.com/ideogram-ai/ideogram-v4-turbo', title: 'Ideogram 4.0 Turbo — Replicate', accessed: 2026-10-02}
  - {url: 'https://replicate.com/ideogram-ai', title: 'ideogram-ai — modelos publicados en Replicate', accessed: 2026-10-02}
---

## Resumen

Ideogram es la familia de texto-a-imagen de Ideogram AI conocida por fidelidad al prompt y tipografía nítida dentro de la imagen: logos, carteles, portadas, memes, infografías, packaging [[3]](https://developer.ideogram.ai/ideogram-api/api-overview). En Nexus solo está disponible la generación **Ideogram 4.0** en dos velocidades de render — **Quality** (`ideogram/v4/quality/text-to-image`) y **Turbo** (`ideogram/v4/turbo/text-to-image`) —, ambas solo `text_to_image`: no hay edición, referencias múltiples ni vídeo en el catálogo actual de Nexus para esta familia. El fabricante ya ofrece **Ideogram 4.5**, más reciente y con edición precisa por instrucción, pero esa generación no está en el catálogo de Nexus hoy [[5]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4-5). Para esta familia, "escribir el prompt como mejor lo entiende el modelo" significa, por encima de todo, tratar el texto a renderizar como un campo distinto de la descripción visual: entre comillas, corto, con tipografía y posición explícitas.

## Cuándo elegirlo y cuándo no

- **Elige Ideogram** cuando el plano exige **texto exacto y legible como elemento central** de la composición: logotipos, carteles de evento, portadas de álbum/libro, memes, infografías, etiquetas de producto, mockups de packaging, títulos de redes sociales, miniaturas de vídeo con titular grande. Es la especialista de la casa para esto frente a modelos generalistas de imagen [[3]](https://developer.ideogram.ai/ideogram-api/api-overview).
- **GPT Image**: edición por instrucción, hasta 16 referencias y 4K; cubre más tareas que Ideogram en Nexus y renderiza texto razonablemente bien, pero no es su especialidad declarada.
- **Nano Banana**: edición conversacional sin máscara y consistencia de personaje entre turnos; Ideogram no edita en Nexus, así que si el plano necesita "retoca esto que ya generé" hay que saltar de familia.
- **Qwen-Image**: mejor si el texto debe ir en chino o mezclar chino/inglés, o para priorizar coste bajo con buena tipografía; Ideogram es más fuerte en inglés y en lenguaje gráfico occidental (carteles, logos, memes).
- **Seedream**: mejor para estética de producto/moda fotográfica sin tener que describir luz/textura a mano; Ideogram es más "gráfico/diseño" que fotográfico puro [sin verificar].
- **FLUX / Midjourney**: mejor si el objetivo es imagen artística o fotorrealista sin texto crítico; si el prompt no lleva ningún texto a renderizar, Ideogram no aporta su ventaja diferencial y conviene usar otra familia.
- **No elijas Ideogram** para fotorrealismo de retrato/producto [sin verificar], ni para editar una imagen ya generada, combinar varias referencias, hacer upscale dedicado, descomponer en capas o cualquier tarea de vídeo: el catálogo de Nexus para esta familia es únicamente `text_to_image`.

| Necesidad | Variante recomendada |
|---|---|
| Cartel, logo o portada con texto crítico, entrega final | `ideogram/v4/quality/text-to-image` |
| Mismo caso pero con muchas iteraciones de composición antes de pulir | `ideogram/v4/turbo/text-to-image` |
| Meme o miniatura rápida con un titular corto | `ideogram/v4/turbo/text-to-image` |
| Infografía o packaging con varios bloques de texto a la vez | `ideogram/v4/quality/text-to-image`, dividiendo el copy en varias generaciones si no sale nítido todo a la vez |
| Edición de una imagen ya generada (texto, color, objeto) | otra familia (GPT Image o Nano Banana) — Ideogram no edita en Nexus |
| Varias imágenes de referencia a combinar o fijar personaje | otra familia (GPT Image, Nano Banana) |
| Fotorrealismo de producto/retrato sin texto superpuesto | Seedream, GPT Image o Nano Banana |
| Texto en chino o mixto chino/inglés | Qwen-Image |

## Linaje y variantes en Nexus

El fabricante numera generaciones completas (2.0 → 2a → 3.0 → 4.0 → 4.5); dentro de cada una, la velocidad de render se elige por parámetro, no por modelo distinto [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4). Para **Ideogram 4.0**, `rendering_speed` acepta `turbo`, `default` o `quality` [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4):

- `ideogram/v4/quality/text-to-image` → `rendering_speed: quality` — máxima fidelidad y nitidez de texto, admite más bloques de copy y detalle de composición antes de perder legibilidad; más lento. Es el id que mejor "entiende" un prompt largo y matizado.
- `ideogram/v4/turbo/text-to-image` → `rendering_speed: turbo` — más rápido, pensado para iterar composición antes de pulir con `quality`; tolera peor los prompts con mucho detalle simultáneo (texto largo, múltiples bloques de copy, paletas complejas), así que conviene simplificar la plantilla cuando se usa esta variante.
- Nexus no expone el valor intermedio `default` como id propio.

Más reciente en el fabricante pero **no presente en Nexus hoy**: **Ideogram 4.5**, con edición precisa por instrucción (`precise-edit`), máscara opcional y hasta cuatro referencias, preservando los píxeles que la edición no menciona [[5]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4-5)[[6]](https://developer.ideogram.ai/api-reference/images/precise-edit/ideogram-4-5). Si el catálogo de Nexus incorpora `ideogram-4-5`, debería tratarse como la opción nueva por defecto para generación y la única con edición/multi-referencia; mientras tanto, usa **Quality** por defecto y **Turbo** para boceto/iteración, sabiendo que ninguna de las dos edita ni acepta referencias.

## Fortalezas

- **text_rendering** — pensado específicamente para que el texto dentro de la imagen salga legible y correcto; es la razón de ser de la familia [[3]](https://developer.ideogram.ai/ideogram-api/api-overview).
- **prompt_adherence** — "known for prompt fidelity"; sigue instrucciones de composición y contenido con precisión, incluida la posición relativa de elementos si se describe con claridad [[3]](https://developer.ideogram.ai/ideogram-api/api-overview).
- **stylized** — fuerte en lenguaje gráfico: cartelería, iconografía, ilustración plana, tipografía como elemento de diseño, más que en busca de foto realista [[8]](https://replicate.com/ideogram-ai/ideogram-v4-quality).
- **high_resolution** — admite resoluciones nativas hasta 2048×2048 y relaciones de aspecto extremas (lado largo hasta ~3328 px) sin upscaler aparte [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4).
- **fast** — la variante Turbo prioriza velocidad de render sobre acabado fino para iterar composición rápido [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4).

## Debilidades

- **photorealism** [sin verificar] — la familia está orientada a diseño gráfico y tipografía más que a fotorrealismo de piel/óptica; no hay documentación oficial que lo confirme o lo desmienta explícitamente, pero el posicionamiento del fabricante ("prompt fidelity and crisp typography" frente a modelos generalistas) sugiere ese reparto de fortalezas [[3]](https://developer.ideogram.ai/ideogram-api/api-overview).
- Sin edición ni multi-referencia en Nexus: cualquier retoque sobre una imagen generada requiere otra familia, porque los ids de Nexus para Ideogram son solo `text-to-image`.
- Sin `negative_prompt` documentado en los endpoints directos `ideogram-4`/`ideogram-4-5`: solo aparece en el selector `auto` del fabricante [[7]](https://developer.ideogram.ai/api-reference/images/generate/auto)[[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4). Confirma con el schema en vivo de Nexus antes de asumir que existe.
- Degrada rápido con mucho texto simultáneo: cuantos más bloques de copy distintos se pidan en una sola imagen, más probable que alguno salga ilegible o mal colocado; no es una familia para maquetar párrafos enteros.

## Tareas ideales / Tareas que debe evitar

**Ideales:** `text_to_image`.
**Evitar:** `image_edit`, `multi_reference_image`, `upscale`, `layer_decomposition`, `text_to_video`, `image_to_video`, `reference_to_video`, `start_end_frame`, `video_edit`, `video_extend`, `motion_control`, `lipsync`, `avatar` — ninguna de estas existe hoy para `ideogram` en el catálogo de Nexus.

## Estructura de prompt recomendada

Idioma óptimo: **inglés** para la descripción de la escena/estilo; el texto que debe aparecer impreso en la imagen puede ir en cualquier idioma (va literal entre comillas) y se recomienda escribirlo exactamente como debe salir, con mayúsculas/minúsculas y puntuación finales [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4). El modelo acepta **prompt en lenguaje natural** o un **JSON estructurado de Ideogram 4.0** (si envías JSON válido, se usa tal cual y se salta el "magic prompt" salvo que lo fuerces) [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4). Tono: prosa natural y directa en frases cortas, no listas de tags sueltos separados por comas al estilo Midjourney/Stable Diffusion clásico — Ideogram sigue mejor instrucciones de composición escritas como oraciones. Longitud ideal: 1–3 frases (20–60 palabras) para la variante Quality; 1–2 frases más simples (15–40 palabras) para Turbo, que tolera peor el detalle simultáneo. Sin sintaxis `--parámetros` de Midjourney — no la reconoce; aspecto/resolución/velocidad van en campos del API (`resolution`, `rendering_speed`), nunca en el texto [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4). Sintaxis especial propia de esta familia: el texto a renderizar siempre entre comillas dobles, lo más cerca posible del final del prompt y precedido por una pista explícita de posición/tipografía; no hay tags de cámara ni `[corte]`/shot labels porque la familia es de imagen fija, no de vídeo.

Orden que mejor sigue el modelo:

```
[Tipo de pieza] de/para [sujeto o tema],
[estilo visual], [paleta de color].
[Texto exacto a renderizar]: "[texto literal]" en [posición], tipografía [estilo de letra].
[Composición/fondo/elementos secundarios adicionales, opcional].
```

Hay un control `magic_prompt` (`auto`/`on`/`off`) que reescribe y expande el prompt natural antes de generar; en `off` se respeta tu redacción casi literal y solo se convierte a formato estructurado — útil si quieres control fino sobre el texto exacto o si ya escribes siguiendo la plantilla anterior [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4).

## Plantillas por tarea

### Text-to-image (Quality / Turbo)

Plantilla general, en el orden que mejor sigue el modelo:

```
A [tipo de pieza: poster/logo/cover/label/meme/infographic] for/of [sujeto o escena],
[estilo visual: flat illustration / vintage badge / cinematic photo / 3D render / line art], [paleta de 2-3 colores],
[composición/encuadre/elementos secundarios/fondo].
Large legible text: "[texto literal exacto]" positioned [arriba/centro/pie], in a [serif/sans-serif/script/bold] typeface.
```

Diferencias Quality vs. Turbo: con **Quality** se puede mantener la plantilla completa, con hasta 2 bloques de texto distintos (p. ej. titular + subtítulo) y detalle fino de composición (varios elementos secundarios, textura, iluminación matizada) sin que la tipografía se degrade. Con **Turbo**, simplifica: un solo bloque de texto, una frase de estilo/paleta y poca descripción de fondo — si se le pide el mismo nivel de detalle que a Quality, tiende a perder nitidez en letras pequeñas o en elementos secundarios finos [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4).

**Ejemplo 1 — cinematográfico, con personajes:**
```
A dramatic cinematic movie poster for an independent thriller, two brothers Mateo and Vidal standing
back to back on a rain-soaked city street at night, neon signs reflecting on wet asphalt, teal and
amber color grading, shallow depth of field. Large legible title text: "BLOOD BROTHERS" centered near
the top, in a bold condensed sans-serif typeface, with smaller tagline text below it: "One secret. Two
sides.".
```
(68 palabras, variante Quality: dos bloques de texto, iluminación matizada.)

**Ejemplo 2 — producto/estilizado, sin personajes:**
```
A minimalist product poster for a sports energy drink can, floating against a plain electric-blue
background, dynamic splash of orange liquid frozen mid-motion around the can, flat vector style with
sharp highlights. Large legible text: "IGNITE" below the can, bold italic sans-serif typeface, white
color.
```
(44 palabras, válido también para Turbo si se reduce aún más la descripción del splash.)

**No pongas:**
- Más de dos bloques de texto distinto en una sola imagen (titular + subtítulo es el límite razonable); tres o más frases simultáneas pierden fidelidad tipográfica.
- "Logo profesional" o "cartel bonito" sin tipografía, paleta ni composición concretas: Ideogram necesita dirección de diseño explícita, no adjetivos vagos.
- Sintaxis de Midjourney (`--ar 16:9`, `--style raw`, `::`) ni etiquetas sueltas tipo `[masterpiece, 4k, trending]`: no las interpreta como control, solo como texto descriptivo literal.
- Texto a renderizar sin comillas o mezclado dentro de la descripción de la escena: ponlo siempre entre comillas y lo más cerca posible del final del prompt para que el modelo lo distinga del resto.

## Idioma, longitud y prompt negativo

Idioma óptimo del prompt descriptivo: **inglés**; el texto a imprimir puede ir en el idioma del diseño final, entre comillas tal cual debe aparecer (español, inglés u otros alfabetos latinos funcionan razonablemente bien; para chino/mixto chino-inglés, Qwen-Image es más fiable). Longitud: 20–60 palabras de descripción en Quality, 15–40 en Turbo; el texto a renderizar debe ser corto (una frase breve o menos) para máxima nitidez — frases de más de ocho a diez palabras legibles de un vistazo tienden a perder definición. Sin `negative_prompt` documentado en los endpoints directos — solo en `auto` [[7]](https://developer.ideogram.ai/api-reference/images/generate/auto)[[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4). Para restringir, dilo en positivo ("fondo liso, sin elementos adicionales", "una sola línea de texto") en vez de negarlo, y verifica el schema en vivo de Nexus antes de asumir que el campo existe.

## Referencias e imagen inicial

Los ids de Ideogram en Nexus (`v4/quality` y `v4/turbo`) no aceptan imagen de referencia ni inicial: generación pura desde texto, sin keyframes ni `image_url` de entrada. El fabricante ofrece referencias de estilo/producto (hasta 4) en **Ideogram 4.5 Precise Edit**, no presente en Nexus [[6]](https://developer.ideogram.ai/api-reference/images/precise-edit/ideogram-4-5). Si el plano parte de una imagen existente o de referencias de marca/producto que hay que respetar visualmente, usa GPT Image o Nano Banana para esa parte y reserva Ideogram para generar desde cero la pieza final de texto/diseño (p. ej. el cartel o el logo que incorpora los elementos ya definidos, descritos de nuevo en el prompt en vez de adjuntados como imagen).

## Cámara y movimiento

No aplica: texto-a-imagen estático, sin controles de cámara, encuadre dinámico ni vídeo en Nexus. Si el plano necesita composición cinematográfica, descríbela como atributo fijo de la imagen (ángulo, plano, profundidad de campo) dentro de la frase de estilo, no como movimiento.

## Consistencia de personaje

No es el punto fuerte de la familia; no hay mecanismo documentado (ni referencia de personaje, ni seed de identidad) para fijar la misma cara o el mismo diseño de personaje entre generaciones distintas de Ideogram en Nexus [sin verificar]. Si el plano requiere el mismo personaje (p. ej. Mateo y Vidal) en varias piezas gráficas, resuelve la identidad visual en GPT Image o Nano Banana —o define el personaje una vez con suficiente detalle textual repetido en cada prompt— y reserva Ideogram para la capa de texto/diseño final sobre esa base.

## Audio y diálogo

No aplica: familia solo de imagen, sin audio, voz ni lipsync.

## Restricciones e incompatibilidades conocidas

- `enable_copyright_detection` puede marcar imágenes como `is_image_safe: false`; añade latencia si se activa [[4]](https://developer.ideogram.ai/api-reference/images/generate/ideogram-4).
- Un JSON estructurado válido se usa literalmente y se salta el "magic prompt" salvo `magic_prompt: on`; el comportamiento ante JSON mal formado no está documentado [sin verificar].
- No hay edición, multi-referencia ni vídeo en los ids de Nexus para esta familia: solo generación pura desde texto, cada llamada es independiente de la anterior.
- No hay control de posición por coordenadas (bounding box) documentado para el texto en los endpoints directos: la posición se indica en lenguaje natural ("centrado", "en la parte inferior"), no por coordenadas exactas [sin verificar].

## Errores habituales

- Pedir 3-4 bloques de texto distintos a la vez: pierde fidelidad tipográfica cuanto más texto simultáneo se solicita. Divide en piezas o prioriza un titular con, como mucho, un subtítulo corto.
- Describir "logo profesional" o "cartel bonito" sin tipografía, paleta ni composición concretas: Ideogram necesita dirección de diseño, no adjetivos vagos.
- Usar sintaxis de Midjourney (`--ar`, `--style`, `::`) o listas de tags sueltos: no las interpreta como control; usa los campos `resolution`/`rendering_speed` del API y escribe el resto en prosa.
- Pedir una edición puntual sobre una imagen ya generada: con los ids actuales de Nexus hay que regenerar desde cero o cambiar de familia (GPT Image, Nano Banana).
- Asumir que existe `negative_prompt` o control de posición por coordenadas: ninguno está documentado en los endpoints directos; verifica siempre el schema en vivo.
- Usar la misma plantilla de detalle rico pensada para Quality en Turbo: Turbo tolera menos detalle simultáneo y conviene simplificar la frase de estilo y reducir a un solo bloque de texto.

## Recomendaciones de prompting

- Pon el texto a renderizar entre comillas, una o dos veces como máximo (titular + subtítulo), corto y en el idioma final deseado.
- Especifica tipografía (serif/sans-serif/script/bold/condensed) y posición del texto en vez de dejarlo a la suerte del modelo.
- Da una paleta de color concreta (2-3 colores) en vez de "colores bonitos" o "paleta vibrante".
- Usa Turbo para iterar composición con prompts simples y Quality para la entrega final con el texto ya fijado y detalle rico.
- Si el resultado tipográfico no es exacto, prueba `magic_prompt: off` para respetar tu redacción casi literal en vez de dejar que el modelo la reescriba.
- Reparte piezas con mucho copy (infografías, packaging con varias caras) en varias generaciones en vez de pedirlo todo en una sola imagen.
- Para fotorrealismo de producto o retrato sin texto crítico, cambia a Seedream, GPT Image o Nano Banana; reserva Ideogram para la capa de texto/diseño final.
- Si el personaje o la referencia visual debe mantenerse entre piezas, fíjalos primero en GPT Image/Nano Banana y repite la descripción textual en cada prompt de Ideogram; no esperes edición ni multi-referencia de estos ids.
- Escribe la descripción de escena/estilo en inglés aunque el texto a renderizar vaya en español u otro idioma: la fidelidad de composición mejora en inglés.
- No mezcles sintaxis de otros generadores (tags sueltos, `--parámetros`): Ideogram sigue mejor frases completas y directas.

## Fuentes y fecha

[1] Ideogram v4 Quality Text-to-Image — Atlas Cloud — https://www.atlascloud.ai/models/ideogram/v4/quality/text-to-image — consultado 2026-10-02
[2] Ideogram v4 Turbo Text-to-Image — Atlas Cloud — https://www.atlascloud.ai/models/ideogram/v4/turbo/text-to-image — consultado 2026-10-02
[3] API Overview — Ideogram Documentation — https://developer.ideogram.ai/ideogram-api/api-overview — consultado 2026-10-02
[4] Generate with Ideogram 4.0 — Ideogram API Reference — https://developer.ideogram.ai/api-reference/images/generate/ideogram-4 — consultado 2026-10-02
[5] Generate with Ideogram 4.5 — Ideogram API Reference — https://developer.ideogram.ai/api-reference/images/generate/ideogram-4-5 — consultado 2026-10-02
[6] Precise edit with Ideogram 4.5 — Ideogram API Reference — https://developer.ideogram.ai/api-reference/images/precise-edit/ideogram-4-5 — consultado 2026-10-02
[7] Generate with automatic model selection — Ideogram API Reference — https://developer.ideogram.ai/api-reference/images/generate/auto — consultado 2026-10-02
[8] Ideogram 4.0 Quality — Replicate — https://replicate.com/ideogram-ai/ideogram-v4-quality — consultado 2026-10-02
[9] Ideogram 4.0 Turbo — Replicate — https://replicate.com/ideogram-ai/ideogram-v4-turbo — consultado 2026-10-02
[10] ideogram-ai — modelos publicados en Replicate — https://replicate.com/ideogram-ai — consultado 2026-10-02
