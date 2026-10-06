---
id: otros-imagen
kind: model
title: 'Otros de imagen: HiDream O1 1.5, Krea 2 Turbo, Z-Image Turbo, Tencent Image Upscaler'
version: 2
updated: 2026-10-02
match: ['hidream', 'z-image', 'krea', '^tencent/image']
modalities: IMAGE
strengths: [fast, low_cost, photorealism, text_rendering, prompt_adherence, identity_preservation, high_resolution]
weaknesses: [face_consistency, multi_character, cinematic_look]
ideal_tasks: [text_to_image, image_edit, multi_reference_image, upscale]
avoid_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, motion_control, lipsync, avatar, layer_decomposition]
unverified_tags: []
sources:
  - url: 'https://www.atlascloud.ai/models/hidream-o1-1.5/edit'
    title: 'HiDream O1 1.5 Edit — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/hidream-o1-1.5/text-to-image'
    title: 'HiDream O1 1.5 Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/krea-2-turbo/text-to-image'
    title: 'Krea 2 Turbo Text-to-Image — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/tencent/image/upscaler'
    title: 'Tencent Image Upscaler API — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/z-image/turbo'
    title: 'Z-Image-Turbo — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://www.atlascloud.ai/models/z-image/turbo-lora'
    title: 'Z-Image-Turbo LoRA — Atlas Cloud'
    accessed: 2026-10-02
  - url: 'https://huggingface.co/HiDream-ai/HiDream-O1-Image-Dev'
    title: 'HiDream-ai/HiDream-O1-Image-Dev — model card (Hugging Face)'
    accessed: 2026-10-02
  - url: 'https://huggingface.co/Tongyi-MAI/Z-Image-Turbo'
    title: 'Tongyi-MAI/Z-Image-Turbo — model card (Hugging Face)'
    accessed: 2026-10-02
  - url: 'https://www.krea.ai/krea-2'
    title: 'Krea 2: AI Image Foundation Model & Style Control — krea.ai'
    accessed: 2026-10-02
  - url: 'https://huggingface.co/Qwen/Qwen-Image'
    title: 'Qwen/Qwen-Image — model card (Hugging Face)'
    accessed: 2026-10-02
---

## Resumen

"Otros de imagen" agrupa cuatro herramientas sin parentesco técnico que cubren huecos que FLUX, Seedream y Qwen-Image no cubren igual: generación barata y rapidísima (Z-Image Turbo, Krea 2 Turbo), un todo-en-uno t2i+edición+personalización (HiDream O1 1.5) y un realce de resolución puro (Tencent Image Upscaler). Elige por tarea, no por "parecido" entre modelos: cada uno espera un estilo de prompt distinto y mezclar plantillas entre ellos degrada el resultado.

## Cuándo elegirlo y cuándo no

- **HiDream O1 1.5 t2i**: texto largo en la imagen o composición multi-objeto compleja; en GenEval/DPG-Bench del fabricante supera a FLUX.2 Dev, Qwen-Image y Seedream-4.0 en overall [[7]](https://huggingface.co/HiDream-ai/HiDream-O1-Image-Dev).
- **HiDream O1 1.5 edit**: editar/personalizar con 1-varias referencias preservando identidad [[1]](https://www.atlascloud.ai/models/hidream-o1-1.5/edit). Mejor **Qwen-Image Edit** si es edición instructiva simple sin componer referencias [[10]](https://huggingface.co/Qwen/Qwen-Image).
- **Z-Image Turbo**: volumen/velocidad, 8 pasos, latencia sub-segundo [[8]](https://huggingface.co/Tongyi-MAI/Z-Image-Turbo). Mejor **Krea 2 Turbo** si quieres dirigir la estética con moodboards/referencias en vez de prompts largos [[9]](https://www.krea.ai/krea-2).
- **Z-Image Turbo LoRA**: identidad de marca/personaje recurrente sin reentrenar, hasta 3 LoRAs por request [[6]](https://www.atlascloud.ai/models/z-image/turbo-lora).
- **Tencent Image Upscaler**: solo sube resolución/nitidez sin tocar contenido [[4]](https://www.atlascloud.ai/models/tencent/image/upscaler); no inventa detalle como un denoising alto en FLUX/Seedream.
- Evita la familia para texto chino con máxima fidelidad tipográfica o edición de referencia muy precisa: ahí **Qwen-Image/Qwen-Image Edit** sigue siendo más fuerte [[10]]. Evítala también para el tier de calidad tope: Nexus solo expone HiDream O1 1.5 (no "Pro" 200B+) y Krea 2 **Turbo** (no "RAW") [[7]][[9]].

| Necesidad | Variante recomendada |
|---|---|
| Texto largo/multilenguaje legible en la imagen | HiDream O1 1.5 text-to-image |
| Composición multi-objeto compleja descrita en prosa | HiDream O1 1.5 text-to-image |
| Edición/personalización multi-referencia con identidad | HiDream O1 1.5 edit |
| Combinar producto + modelo + fondo en una sola imagen | HiDream O1 1.5 edit (multi-referencia) |
| Iteración rápida/barata en volumen (miniaturas, variantes) | Z-Image Turbo |
| Mismo estilo/personaje recurrente sin reentrenar | Z-Image Turbo LoRA |
| Iteración rápida con estética dirigida por referencia/moodboard | Krea 2 Turbo |
| Dirección de arte tipo poster/producto con pocas palabras | Krea 2 Turbo |
| Subir resolución de una imagen ya generada en otro modelo | Tencent Image Upscaler |
| Preparar una imagen de catálogo/e-commerce para impresión | Tencent Image Upscaler (`fidelity`) |

## Linaje y variantes en Nexus

- **HiDream**: I1 (17B, 2025) → E1 (edición, 2025) → **O1-Image** (2026): "Pixel-level Unified Transformer" de 8B sin VAE ni encoder de texto separado; unifica t2i+edición+personalización, nativo hasta 2048×2048 [[7]]. Tiers: O1-Image (50 pasos), O1-Image-Dev (28, distilado) y O1-Image-Pro (200B+, no en Nexus) [[7]]. `hidream-o1-1.5/*` es esta generación O1, no I1/E1; el punto de versión "1.5" no se cotejó contra el changelog público [sin verificar].
- **Z-Image** (Tongyi-MAI, 6B): base (50 pasos), **Turbo** (8 pasos/NFE, la más rápida), Omni-Base y Edit [[8]]. `z-image/turbo` = backbone turbo; `z-image/turbo-lora` = mismo backbone + hasta 3 ranuras LoRA [[5]][[6]]. Nexus no expone Z-Image base ni Edit.
- **Krea 2** (may. 2026): primer modelo fundacional propio de Krea AI, sucesor de Krea 1; variantes públicas RAW (calidad completa) y **Turbo** (~15 s) [[9]]. Nexus solo cataloga `krea-2-turbo/text-to-image`.
- **Tencent Image Upscaler**: herramienta única, no generativa; servicio de super-resolución de Tencent Cloud MPS con cuatro motores en el mismo endpoint (`standard`, `super`, `ultra` por defecto, `fidelity`) [[4]].

## Fortalezas

- **fast**: Z-Image Turbo (8 pasos, sub-segundo, 16 GB VRAM) [[8]]; Krea 2 Turbo ~15 s/imagen [[9]].
- **low_cost**: HiDream t2i ≈$0.044/img, Krea 2 Turbo ≈$0.0088/MP, Tencent Upscaler $0.024/img [[2]][[3]][[4]].
- **photorealism**: HiDream O1 1.5 y Z-Image Turbo se describen explícitamente orientados a calidad fotorrealista [[1]][[8]].
- **text_rendering**: HiDream soporta texto largo/multi-región/multilingüe con layout [[7]]; Z-Image Turbo renderiza bilingüe inglés/chino [[8]].
- **prompt_adherence**: HiDream O1-Image (8B) iguala/supera a FLUX.2 Dev, Qwen-Image y Seedream-4.0 en GenEval/DPG-Bench overall, según el fabricante [[7]].
- **identity_preservation**: HiDream O1 1.5 edit admite personalización multi-referencia preservando identidad entre escenas, con condicionamiento opcional de layout/esqueleto [[1]][[7]].
- **high_resolution**: HiDream nativo hasta 2048×2048 [[7]]; Tencent Upscaler escala hasta 10× [[4]].

## Debilidades

- **face_consistency**: fuera de HiDream edit, Z-Image Turbo y Krea 2 Turbo son t2i puros — solo reproducibilidad por `seed`, no identidad facial estable entre prompts [[3]][[5]].
- **multi_character**: ninguna fuente del fabricante documenta control fino de coherencia entre varios personajes en el mismo plano para estos modelos [sin verificar].
- **cinematic_look**: ninguno se presenta como motor de look cinematográfico; apuntan a foto de producto/documento (HiDream, Z-Image) o dirección de arte (Krea 2), no a estética de rodaje [[7]][[8]][[9]].

## Tareas ideales / Tareas que debe evitar

- **Ideales**: `text_to_image` (HiDream t2i, Z-Image Turbo/LoRA, Krea 2 Turbo), `image_edit` (HiDream edit), `multi_reference_image` (HiDream edit con varias `reference_image_urls`), `upscale` (Tencent Image Upscaler).
- **Evitar**: toda tarea de vídeo (`text_to_video`, `image_to_video`, `reference_to_video`, `start_end_frame`, `video_edit`, `video_extend`, `motion_control`), `lipsync`, `avatar` — ninguno genera vídeo/audio — y `layer_decomposition` (sin evidencia de salida en capas separadas).

## Estructura de prompt recomendada

Longitud óptima: 15-40 palabras en Z-Image Turbo/Krea 2 Turbo (frase densa de dirección de arte); hasta 60-80 palabras en HiDream si hay texto largo a renderizar o varias referencias que coordinar. Idioma óptimo: **inglés** en los cuatro — Z-Image Turbo también acepta chino de forma nativa, y HiDream/Krea 2 no tienen fuente que confirme fiabilidad en español [sin verificar]. Tono: prosa natural y descriptiva, no etiquetas sueltas separadas por comas tipo Midjourney; ninguno de los cuatro documenta sintaxis especial de `@referencias`, `[corte]`/shot labels, timecodes o parámetros `--ar`/`--chaos` en el propio texto del prompt — esos controles van siempre en campos de API separados (`aspect_ratio`, `reference_image_urls`, `type`/`mode` en el upscaler), nunca inline [[1]][[3]][[4]][[8]]. El texto que debe aparecer literalmente dentro de la imagen (carteles, pantallas, etiquetas) sí se marca entre comillas dentro del prompt — es la única sintaxis especial verificada de esta familia [[7]].

```
[sujeto principal, 3-8 palabras]
[acción/pose/expresión]
[entorno y fondo]
[estilo visual / iluminación / paleta]
["texto exacto entre comillas" si debe aparecer en la imagen, + ubicación/tipografía breve]
[detalles de calidad opcionales: 8k, close-up, highly detailed]
```

## Plantillas por tarea

### Text-to-image (HiDream O1 1.5 / Z-Image Turbo / Turbo LoRA / Krea 2 Turbo)

Plantilla general (sujeto → acción/pose → entorno → estilo/iluminación → texto en comillas si aplica → calidad):
```
[sujeto], [acción/pose], en [entorno]. [Estilo/iluminación/paleta].
Texto visible: "[texto exacto]" en [ubicación/tipografía]. [calidad opcional]
```

Ejemplo cinematográfico con personajes (apto HiDream, prosa completa, 68 palabras):
`Two brothers, Mateo and Vidal, sit on a rusted fire escape overlooking a rain-slicked city street at dusk. Mateo, younger, leans forward gripping a worn leather notebook; Vidal, older, rests a hand on his shoulder, both lit by the warm orange glow of a diner sign below. Muted cinematic color grade, soft volumetric light through drizzle, shallow depth of field, 35mm film grain, melancholic but hopeful mood.`

Ejemplo distinto — producto estilizado (apto Z-Image Turbo / Krea 2 Turbo, 28 palabras):
`Product shot of a matte black wireless earbud case floating above a reflective obsidian surface, soft studio rim lighting, deep teal gradient background, subtle lens flare, 8k, highly detailed, commercial advertising look.`

Diferencias por variante: **HiDream O1 1.5** tolera y recompensa prosa larga, composición multi-objeto y texto en comillas con layout preciso — es la única con texto renderizado fiable [[7]]. **Z-Image Turbo** rinde mejor con una sola frase densa de atributos (sujeto, paleta, iluminación, calidad) como el ejemplo oficial `"Astronaut in a jungle, cold color palette, muted colors, detailed, 8k"`, pero también admite párrafos largos sin dividir en etiquetas [[8]]; con `turbo-lora` el adaptador de marca/personaje se elige por parámetro, nunca se nombra en el texto [[6]]. **Krea 2 Turbo** funciona mejor con frases cortas de dirección de arte (encuadre + paleta + textura) — si hay moodboard/referencia de estilo, el prompt puede reducirse a una línea porque la referencia hace el trabajo pesado [[3]][[9]].

No pongas:
- Parámetros de API dentro del texto (`--ar 16:9`, `--steps 30`, `seed=123`): van en campos separados del schema.
- Negative prompts largos en Z-Image Turbo esperando efecto de CFG alto: en modo turbo se recomienda `guidance_scale=0`, así que las exclusiones deben ir en positivo [[8]].
- Más de 20 atributos sueltos en Krea 2 Turbo: está pensado para prompt corto + referencia visual, no para sustituirla con texto exhaustivo [[9]].
- Texto a renderizar sin comillas ni ubicación: HiDream necesita el string exacto entre comillas y, si es largo, su posición/tipografía aproximada para no improvisar el layout [[7]].

### Image edit (HiDream O1 1.5)

Plantilla (instrucción → qué mantener → qué cambiar → referencia):
```
[instrucción de edición] usando [1 imagen de referencia].
Mantén: [identidad, pose, composición que no debe tocarse].
Cambia: [fondo, ropa, iluminación, estilo].
```

Ejemplo cinematográfico con personajes (edición de una foto de Vidal, 52 palabras):
`Using the reference photo of Vidal, keep his exact face, hairstyle, and seated pose unchanged. Change the setting to a dim recording studio at night, add a vintage microphone in front of him, warm amber key light from the left, soft smoke haze, cinematic color grade, shallow depth of field.`

Ejemplo distinto — producto (edición de una foto de calzado, 36 palabras):
`Using the reference photo of the sneaker, keep the exact shape, laces, and sole design unchanged. Change the background to a wet asphalt street at night with neon reflections, add dramatic rim lighting and light rain splashes.`

Diferencias por variante: no hay otra variante de edición en esta familia — Z-Image Turbo, Turbo LoRA y Krea 2 Turbo son t2i puros sin entrada de imagen [[3]][[5]][[6]]; para edición instructiva simple sin componer referencias, HiDream sigue siendo la única opción de la familia, aunque Qwen-Image Edit puede rendir mejor en ese caso concreto [[10]].

No pongas:
- Instrucciones vagas tipo "mejora la imagen": especifica qué atributo cambia y cuál se congela (identidad, pose, composición).
- Más referencias de las necesarias para una edición simple: cada URL extra añade ambigüedad sobre qué atributo manda.
- Cambios de identidad facial en el mismo prompt que pide "mantener la cara": son instrucciones contradictorias, el modelo prioriza de forma impredecible.

### Multi-referencia (HiDream O1 1.5)

Plantilla (nombra o numera el rol de cada referencia):
```
Reference 1 ([rol, p. ej. "person"]): [qué aportar de ella].
Reference 2 ([rol, p. ej. "background"]): [qué aportar de ella].
Compose: [acción/pose/escena final que combina ambas].
```

Ejemplo cinematográfico con personajes (combinar dos referencias — Mateo y un escenario, 58 palabras):
`Reference 1 (person): Mateo, preserve his face and build exactly. Reference 2 (location): the abandoned train station, preserve its architecture and lighting mood. Compose: Mateo standing alone on the platform at dawn, hands in pockets, looking down the empty tracks, fog rolling in, muted cinematic color grade, long shadows.`

Ejemplo distinto — personalización de producto en campaña (combinar modelo + producto, 44 palabras):
`Reference 1 (person): the model, preserve her exact face and pose. Reference 2 (product): the handbag, preserve its exact color and hardware. Compose: her holding the handbag against a plain studio backdrop, soft even lighting, commercial catalog look.`

Diferencias por variante: solo HiDream O1 1.5 ofrece multi-referencia en esta familia; el condicionamiento opcional de layout/esqueleto ayuda cuando se quiere fijar la pose exacta además de la identidad [[1]][[7]]. Ninguna variante Turbo/LoRA/Krea admite varias imágenes de entrada simultáneas [sin verificar].

No pongas:
- Referencias sin rol claro ("usa estas dos fotos" sin decir qué aporta cada una).
- Instrucciones de composición que contradicen la referencia de fondo (p. ej. pedir "en la playa" cuando Reference 2 es un interior) sin aclarar que se reemplaza.
- Expectativa de coherencia entre más de dos-tres sujetos a la vez: no hay fuente que confirme control fino multi-personaje más allá de combinar identidad + escena [sin verificar].

### Upscale (Tencent Image Upscaler)

Plantilla (sin prompt de texto, solo parámetros estructurales):
```
image_url: [URL pública http(s), sin firma de expiración corta]
type: standard | super | ultra | fidelity
mode: percent | aspect | fixed
[percent: N  |  width/height: N]
```

Ejemplo cinematográfico con personajes (ampliar un fotograma de Mateo y Vidal generado antes):
`image_url: [URL de la imagen a escalar]
type: fidelity
mode: percent
percent: 2.0`

Ejemplo distinto — catálogo de producto para e-commerce:
`image_url: [URL de la imagen a escalar]
type: standard
mode: fixed
width: 4000
height: 4000`

Diferencias por variante: no hay variantes de prompt — es el único modelo de la familia sin campo de texto; la única "diferencia" relevante es el motor elegido en `type` (`standard`/`super`/`ultra` priorizan nitidez general, `fidelity` prioriza no alterar detalle fino de producto/texto) [[4]].

No pongas:
- Ninguna instrucción creativa ("añade más detalle", "hazlo más realista"): el servicio no interpreta texto, es super-resolución pura y lo ignora o falla [[4]].
- Una URL firmada de corta duración: Tencent MPS la descarga de forma asíncrona y puede tardar más que la caducidad del enlace [[4]].
- Expectativa de que invente contenido nuevo (ojos, texto, texturas que no existían): solo realza lo ya presente, no genera [[4]].

## Idioma, longitud y prompt negativo

- **HiDream O1 1.5**: inglés óptimo, hasta 5000 caracteres; sin `negative_prompt` en el schema — expresa restricciones en positivo [[1]][[2]].
- **Z-Image Turbo**: bilingüe inglés/chino [[8]]; sin `negative_prompt`; el fabricante recomienda `guidance_scale=0.0` en turbo, lo que reduce el negative prompting por CFG [[8]].
- **Krea 2 Turbo**: inglés óptimo; sin negative prompt documentado — usa `seed` para iterar [[3]].
- **Tencent Image Upscaler**: no aplica, sin campo de texto [[4]].
- El schema en vivo de Nexus manda si el proveedor añade `negative_prompt` más adelante.

## Referencias e imagen inicial

- **HiDream O1 1.5**: `reference_image_urls` se omite en t2i puro; 1 URL para editar; varias para personalización multi-sujeto, cada una debería nombrarse por su rol en el prompt ("Reference 1: person", "Reference 2: background"); la salida puede heredar el aspect ratio de la referencia [[1]][[2]].
- **Krea 2 Turbo / Z-Image Turbo**: sin campo de referencia en Atlas Cloud — t2i puro; los "moodboards" de Krea son función de su app, no verificados en el endpoint de Nexus [sin verificar].
- **Z-Image Turbo LoRA**: sin imágenes de referencia; personalización vía hasta 3 adaptadores LoRA por parámetro [[6]].
- **Tencent Image Upscaler**: `image_url` es la única entrada, pública y accesible por HTTP(S) [[4]].

## Cámara y movimiento

No hay vocabulario de movimiento (imagen fija), pero sí de encuadre: HiDream admite presets de aspecto (`square_hd`, `portrait_4_3`, `portrait_16_9`, `landscape_4_3`, `landscape_16_9`) [[1]][[2]]; Krea 2 responde bien a encuadres simples como "close up shot" [[9]]. No esperes control de lente/focal fino tipo cine en ninguno.

## Consistencia de personaje

Solo **HiDream O1 1.5 edit** ofrece una vía explícita: personalización multi-referencia que preserva identidad entre escenas, con condicionamiento opcional de layout/esqueleto para pose/composición [[1]][[7]]. En Z-Image Turbo, Turbo LoRA, Krea 2 Turbo y Tencent Upscaler no hay mecanismo de identidad: solo repetibilidad por `seed`, que no mantiene a "la misma persona" entre prompts distintos [[3]][[5]][[6]].

## Restricciones e incompatibilidades conocidas

- Tencent Image Upscaler es asíncrono: se envía la tarea, se recibe `task_id` y se consulta hasta `completed` [[4]].
- Tencent Image Upscaler no añade objetos ni cambia composición, solo resolución/nitidez [[4]].
- HiDream O1 1.5 limita el prompt a 5000 caracteres [[1]][[2]].
- Krea 2 Turbo permite hasta 4 imágenes por llamada vía `num_images` [[3]].
- Ninguno documenta políticas de moderación propias en las fuentes consultadas; aplican las políticas generales de Nexus [sin verificar].

## Errores habituales

- Pedir "máxima calidad" a Krea 2 Turbo o Z-Image Turbo esperando el nivel RAW/base del fabricante — Nexus solo expone los tiers rápidos [[8]][[9]].
- Usar HiDream para iteración rápida de bocetos: su fuerte es texto largo/edición por referencia, no velocidad.
- Mandar negative prompts largos a Z-Image Turbo esperando el efecto de CFG alto, cuando turbo recomienda `guidance_scale=0` [[8]].
- Enviar a Tencent Upscaler una URL firmada de corta duración: necesita descargarla ella misma de forma pública [[4]].
- Pasar varias `reference_image_urls` a HiDream sin aclarar qué aporta cada una.
- Confundir Z-Image Turbo LoRA con edición por referencia: son LoRAs por parámetro, no imágenes de entrada [[6]].
- Escribir el prompt de HiDream en formato de etiquetas sueltas tipo Midjourney: rinde peor que la prosa natural con la que se evaluó [[7]].

## Recomendaciones de prompting

- En HiDream, el texto exacto a renderizar va entre comillas, con tipografía/ubicación breve.
- Sube `guidance_scale` en HiDream si interpreta de más; bájalo si es demasiado literal [[1]].
- En Z-Image Turbo, escribe una sola frase densa de atributos — entrenado y evaluado así, no con etiquetas sueltas [[8]].
- En Krea 2 Turbo, prioriza frases cortas de dirección de arte; si hay referencia de estilo, que ella haga el trabajo pesado [[9]].
- En personalización HiDream, numera o nombra el rol de cada referencia ("Reference 1: person", "Reference 2: location").
- Antes de llamar a Tencent Upscaler, confirma que la URL es pública y estable, sin caducar antes del polling.
- No reutilices el mismo prompt en HiDream y Z-Image Turbo esperando resultados comparables: estilos de prompt distintos (prosa vs. ficha de atributos).
- Para lotes grandes con la misma estética barata, usa Z-Image Turbo (o Turbo LoRA con marca fija) en vez de HiDream.
- Al editar con HiDream, separa siempre "mantén" de "cambia" en frases distintas: reduce la ambigüedad sobre qué atributo prevalece.

## Fuentes y fecha

[1] HiDream O1 1.5 Edit — Atlas Cloud — https://www.atlascloud.ai/models/hidream-o1-1.5/edit — consultado 2026-10-02
[2] HiDream O1 1.5 Text-to-Image — Atlas Cloud — https://www.atlascloud.ai/models/hidream-o1-1.5/text-to-image — consultado 2026-10-02
[3] Krea 2 Turbo Text-to-Image — Atlas Cloud — https://www.atlascloud.ai/models/krea-2-turbo/text-to-image — consultado 2026-10-02
[4] Tencent Image Upscaler API — Atlas Cloud — https://www.atlascloud.ai/models/tencent/image/upscaler — consultado 2026-10-02
[5] Z-Image-Turbo — Atlas Cloud — https://www.atlascloud.ai/models/z-image/turbo — consultado 2026-10-02
[6] Z-Image-Turbo LoRA — Atlas Cloud — https://www.atlascloud.ai/models/z-image/turbo-lora — consultado 2026-10-02
[7] HiDream-ai/HiDream-O1-Image-Dev — model card (Hugging Face) — https://huggingface.co/HiDream-ai/HiDream-O1-Image-Dev — consultado 2026-10-02
[8] Tongyi-MAI/Z-Image-Turbo — model card (Hugging Face) — https://huggingface.co/Tongyi-MAI/Z-Image-Turbo — consultado 2026-10-02
[9] Krea 2: AI Image Foundation Model & Style Control — krea.ai — https://www.krea.ai/krea-2 — consultado 2026-10-02
[10] Qwen/Qwen-Image — model card (Hugging Face) — https://huggingface.co/Qwen/Qwen-Image — consultado 2026-10-02
