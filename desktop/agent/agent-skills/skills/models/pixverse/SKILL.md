---
id: pixverse
kind: model
title: 'PixVerse (C1 / V6)'
version: 2
updated: 2026-10-02
match: ['^pixverse/']
modalities: [video]
strengths: [dynamic_action, cinematic_look, native_audio, multi_shot, high_resolution, prompt_adherence]
weaknesses: [subtle_performance, text_rendering]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, video_extend, multi_reference_image]
avoid_tasks: [video_edit, motion_control, lipsync, avatar, text_to_image, image_edit, upscale, layer_decomposition]
unverified_tags: [subtle_performance, text_rendering]
sources:
  - {url: 'https://docs.platform.pixverse.ai/', title: 'PixVerse Platform Docs — Introduction', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/model-overview-2140345m0', title: 'PixVerse Platform Docs — Model Overview', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/capability-matrix-2144288m0', title: 'PixVerse Platform Docs — Capability matrix', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/c1-2067883m0', title: 'PixVerse Platform Docs — C1 model', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/v6-2056814m0', title: 'PixVerse Platform Docs — V6 model', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/text-to-video-882970m0', title: 'PixVerse Platform Docs — Text-to-video guide', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/image-to-video-882971m0', title: 'PixVerse Platform Docs — Image-to-video guide', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/transitionfirst-last-frame-feature-882973m0', title: 'PixVerse Platform Docs — Transition guide', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/extend-1268531m0', title: 'PixVerse Platform Docs — Extend guide', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/fusionreference-to-video-1339253m0', title: 'PixVerse Platform Docs — Fusion guide', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0', title: 'PixVerse Platform Docs — Fusion V6 video-reference guide', accessed: 2026-10-02}
  - {url: 'https://docs.platform.pixverse.ai/motion-controlmimic-1982936m0', title: 'PixVerse Platform Docs — Motion Control (Mimic)', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/pixverse/c1/text-to-video', title: 'Atlas Cloud — PixVerse C1 Text-to-Video', accessed: 2026-10-02}
  - {url: 'https://www.atlascloud.ai/models/pixverse/v6/video-extend', title: 'Atlas Cloud — PixVerse V6 Video Extend', accessed: 2026-10-02}
---

## Resumen

PixVerse (C1 / V6) es el generador de vídeo de PixVerse Inc., orientado a planos de acción e impacto visual: combate, VFX de partículas/luz, fantasía y transformaciones, con audio sincronizado opcional y hasta 1080p/15s nativos en cualquier endpoint [[1]](https://docs.platform.pixverse.ai/) [[4]](https://docs.platform.pixverse.ai/c1-2067883m0). V6 es el modelo general de producción, con multi-clip por parámetro, Fusion omni (imágenes + vídeos de referencia) y extensión nativa; C1 es la variante paralela "cinema-quality" especializada en combate/VFX y storyboard multi-panel, sin extend [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0). En Nexus cubre texto/imagen-a-vídeo, Fusion, transición y, solo en V6, extensión.

## Cuándo elegirlo y cuándo no

Elige PixVerse para **acción física intensa** (combate, artes marciales, VFX elementales, cámara rápida) con audio sincronizado barato y storyboard-a-vídeo. **C1** cuando el plano es combate/VFX/fantasía o parte de un storyboard multi-panel con varios personajes [[4]](https://docs.platform.pixverse.ai/c1-2067883m0). **V6** cuando necesitas multi-clip por parámetro, extender un clip ya generado, o Fusion omni con hasta 10 imágenes y 2 vídeos de referencia para heredar cámara/estilo de un vídeo existente [[5]](https://docs.platform.pixverse.ai/v6-2056814m0) [[11]](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0).

Mejor **Kling** para interpretación facial sutil o lipsync real; **Vidu** para consistencia de personaje sin sobreactuación de cámara; **Seedance** para fotorrealismo con seguimiento literal del prompt; **Wan** si necesitas pesos abiertos o control tipo ControlNet; **Runway Gen-4.5** para edición/control fino de un plano ya rodado [sin verificar, no cubierto por fuentes de esta skill].

| Necesidad | Variante recomendada |
| --- | --- |
| Combate, artes marciales, VFX de magia/fuego/hielo | `pixverse/c1/text-to-video`, `pixverse/c1/image-to-video` |
| Storyboard multi-panel con varios personajes a vídeo | `pixverse/c1/reference-to-video` |
| Animar una foto con audio sincronizado barato | `pixverse/v6/image-to-video` |
| Transición cinematográfica entre dos fotogramas | `pixverse/v6/start-end-to-video` (o `c1/start-end-to-video` si hay combate) |
| Fusionar 1-3 referencias de sujeto/fondo en una escena | `pixverse/v6/reference-to-video` o `pixverse/c1/reference-to-video` |
| Heredar cámara/estilo de un vídeo existente (hasta 10 img + 2 vídeos) | `pixverse/v6/reference-to-video` con `reference_mode: omni` |
| Continuar un clip ya generado | `pixverse/v6/video-extend` (único con extend) |
| Multi-clip controlado por parámetro (sin repetir prompt largo) | `pixverse/v6/text-to-video` o `pixverse/v6/image-to-video` |

## Linaje y variantes en Nexus

PixVerse ha iterado v3.5→v4→v4.5→v5.5→V6, sumando audio sincronizado (desde v5.5), Fusion (desde v4.5) y multi-clip en cada salto [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0) [[9]](https://docs.platform.pixverse.ai/fusionreference-to-video-1339253m0). **V6** es el flagship de propósito general: texto/imagen-a-vídeo, transición, extend y Fusion omni con vídeos de referencia; soporta `generate_multi_clip_switch` como parámetro explícito en texto e imagen-a-vídeo [[5]](https://docs.platform.pixverse.ai/v6-2056814m0) [[11]](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0). **C1** es una variante paralela posterior (no un sucesor numérico de V6): combate/VFX, storyboard multi-panel y consistencia multi-personaje reforzada, con multi-clip "por prompt" (sin switch dedicado) y sin extend [[4]](https://docs.platform.pixverse.ai/c1-2067883m0).

Mapa de ids: `pixverse/c1/{text-to-video,image-to-video,reference-to-video,start-end-to-video}` → C1 (acción/VFX/storyboard, sin extend, multi-clip solo narrado en el prompt). `pixverse/v6/{text-to-video,image-to-video,reference-to-video,start-end-to-video,video-extend}` → V6 (propósito general, único con extend y con `generate_multi_clip_switch`). Ambos modelos aceptan `prompt` de hasta 5000 caracteres y `seed` 0-2147483647 [[4]](https://docs.platform.pixverse.ai/c1-2067883m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0). No hay ids PixVerse fuera del catálogo Atlas en Nexus para esta familia.

## Fortalezas

- **dynamic_action**: combate cuerpo a cuerpo y con armas con peso físico creíble, cámara rápida y VFX de partículas/elementos (viento, rayo, hielo, fuego) [[4]](https://docs.platform.pixverse.ai/c1-2067883m0).
- **cinematic_look**: C1 se posiciona como "cinema-quality" con iluminación/composición cinematográfica y transforma elementos culturales (p. ej. Tai Chi, formaciones estelares) en visuales de fantasía [[4]](https://docs.platform.pixverse.ai/c1-2067883m0).
- **native_audio**: `generate_audio_switch` da audio sincronizado en los cuatro endpoints de C1 y en los cinco de V6 [[4]](https://docs.platform.pixverse.ai/c1-2067883m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0).
- **multi_shot**: multi-clip narrado por prompt en C1; multi-clip por parámetro (`generate_multi_clip_switch`) en texto e imagen-a-vídeo de V6 [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0).
- **high_resolution**: hasta 1080p/15s nativos en cualquier endpoint de ambos modelos [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0) [[13]](https://www.atlascloud.ai/models/pixverse/c1/text-to-video).
- **prompt_adherence**: interpreta con precisión instrucciones de texto, con buen desempeño declarado en anime/animación [[1]](https://docs.platform.pixverse.ai/).

## Debilidades

- **subtle_performance** [sin verificar]: frente a Kling, PixVerse prioriza dinamismo de cámara/acción sobre matiz facial en drama contenido; no hay fuente oficial que lo cuantifique.
- **text_rendering** [sin verificar]: sin garantía documentada de texto legible en pantalla; no lo prometas salvo iteración y revisión manual.

## Tareas ideales / Tareas que debe evitar

Ideales: `text_to_video`, `image_to_video`, `reference_to_video` (Fusion, 1-3 imágenes en modo base; hasta 10 imágenes + 2 vídeos en V6 Omni), `start_end_frame`, `video_extend` (solo V6), `multi_reference_image`.

Evitar en Nexus (fuera del catálogo de esta familia, aunque PixVerse los ofrezca como endpoints propios del fabricante): `video_edit` (endpoints Restyle/Swap/Modify), `motion_control` (endpoint Mimic), `lipsync` (endpoint Lip Sync), `avatar`, `text_to_image`, `image_edit`, `upscale`, `layer_decomposition` [[1]](https://docs.platform.pixverse.ai/) [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0) [[12]](https://docs.platform.pixverse.ai/motion-controlmimic-1982936m0). Confirma con `get_model_schema` antes de asumir que alguno de estos existe como id de Nexus.

## Estructura de prompt recomendada

El fabricante recomienda prompts de **25-200 palabras** (máx. técnico 5000 caracteres), en **inglés**, en prosa natural continua (sin tags tipo Midjourney ni `--parámetros`), con orden escena/sujeto → acción concreta → cámara → luz/atmósfera → audio opcional [[6]](https://docs.platform.pixverse.ai/text-to-video-882970m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0). No hay sintaxis `--ar`/`--seed` soportada en el texto del prompt [sin verificar]: esos valores van en campos propios del esquema (`aspect_ratio`, `seed`), nunca en la frase.

```
[sujeto con rasgo distintivo], [acción/coreografía concreta y verbo activo],
[entorno/escenario], [cámara: tipo de plano + movimiento],
[luz/atmósfera], [estilo opcional], [audio/sonido opcional]
```

Ejemplo (C1, acción, listo para pegar): "A warrior in weathered leather armor charges across a rain-soaked battlefield at dusk, swinging a curved blade in a wide arc. The camera tracks low and fast as sparks fly from clashing steel. Volumetric lightning flashes illuminate the scene, thunder rumbles beneath clashing metal, cinematic combat choreography."

## Plantillas por tarea

### Text-to-video

Plantilla: `[sujeto + rasgo distintivo], [acción/coreografía concreta], [entorno], [cámara: plano + movimiento], [luz/atmósfera], [audio opcional].` En V6, si usas `generate_multi_clip_switch`, narra cada escena como una frase/párrafo separado en orden cronológico; el corte entre clips lo gestiona el parámetro, no una etiqueta de texto. En C1 el multi-shot se narra íntegro dentro del prompt, encadenando frases de acción sin un marcador de "corte" confirmado [sin verificar] [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0) [[6]](https://docs.platform.pixverse.ai/text-to-video-882970m0).

Ejemplo 1 (cinematográfico, C1): "Mateo, a wiry teenager in a muddy school uniform, sprints through a flooded alley at dusk, chased by his older brother Vidal, who laughs and splashes through puddles right behind him. The camera tracks alongside at a low angle, handheld and urgent. Neon shop signs reflect in the water as rain streaks past the lens, warm and cool light mixing, cinematic chase energy, synchronized splashing footsteps and distant thunder."

Ejemplo 2 (producto/estilizado, V6): "A chrome sports sneaker spins slowly on a black mirrored pedestal as electric blue plasma arcs crawl across its surface. The camera orbits a full 360 degrees at eye level, then pushes into a tight macro shot on the stitching. Sharp studio lighting flares off the metal accents while a synthetic whoosh and electric crackle build underneath, hyper-stylized product reveal."

Diferencias por variante: C1 añade peso físico y VFX en escenas de combate/fantasía que V6 no está afinado para igualar [[4]](https://docs.platform.pixverse.ai/c1-2067883m0); V6 es la única con `generate_multi_clip_switch` como parámetro dedicado [[5]](https://docs.platform.pixverse.ai/v6-2056814m0).

No pongas: `--ar`, `--seed` ni otros flags de Midjourney dentro del texto; negaciones largas en el prompt (usa `negative_prompt` si el esquema lo expone); más de 5000 caracteres; etiquetas de "panel 1 / panel 2" sin confirmar que C1 las reconoce como tales.

### Image-to-video

Plantilla: `[qué debe moverse / verbo de acción], [dirección e intensidad del movimiento], [cámara], [atmósfera/luz nueva], [audio opcional].` No redescribas lo que ya es visible y estático en la imagen (ropa, color de pelo, fondo): solo el movimiento nuevo que quieres añadir [[7]](https://docs.platform.pixverse.ai/image-to-video-882971m0).

Ejemplo 1 (cinematográfico, imagen de un chico junto a una ventana): "Vidal turns away from the window and walks slowly toward the camera, his expression softening into a tired smile. Steam rises from the mug in his hand as he lifts it to drink. The camera pushes in gently, soft golden lamp light flickers across his face, quiet ambient room tone."

Ejemplo 2 (producto, imagen de una zapatilla en pedestal): "The sneaker lifts off the pedestal and rotates mid-air as the plasma arcs intensify and wrap fully around the sole. The camera pulls back in a slow zoom-out, revealing a reflective floor beneath it. Lighting pulses in sync with the arcs, a rising synthetic hum builds under the motion."

Diferencias por variante: C1 image-to-video conserva mejor el peso físico en movimientos de combate/VFX sobre una foto de acción; V6 image-to-video es la opción con `generate_multi_clip_switch` si quieres encadenar varias animaciones de la misma imagen [[4]](https://docs.platform.pixverse.ai/c1-2067883m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0). La imagen debe tener al menos ~1024×1024px, sujeto claro y sin marcas de agua para mejores resultados [[7]](https://docs.platform.pixverse.ai/image-to-video-882971m0).

No pongas: redescripciones de ropa/color/fondo ya visibles en la imagen; cambios de identidad del sujeto (otra cara, otra edad); texto en pantalla sin verificación previa; imágenes con watermarks o iluminación pobre como entrada.

### Reference-to-video (Fusion)

Dos variantes de sintaxis según el modo:

**Fusion base** (modelo v4.5 en adelante, incluye C1 y V6 estándar): `image_references` es un array de 1 a 3 objetos, cada uno con `img_id`, `ref_name` único y `type: subject|background`; en el prompt cita cada referencia con `@ref_name` seguido de un espacio, exactamente igual que el `ref_name` declarado [[9]](https://docs.platform.pixverse.ai/fusionreference-to-video-1339253m0). Plantilla: `@sujeto1 [acción] @sujeto2 en/con @fondo, [entorno], [cámara], [luz].`

**V6 Omni** (solo V6, `reference_mode: omni`): admite hasta 10 imágenes de referencia y hasta 2 vídeos de referencia (`video_references`), con el total de los vídeos de referencia ≤15s; cuando usas `video_references`, `duration` debe ser 0 (la salida hereda la duración del vídeo de referencia más largo) y `aspect_ratio` puede ser `"auto"` [[11]](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0). El modelo puede imitar sujeto, acción, cámara y estilo visual del vídeo de referencia.

Ejemplo 1 (Fusion base, hermanos): "@mateo and @vidal walk side by side through @alley at golden hour, laughing and nudging each other's shoulders. The camera follows from slightly behind and below, warm backlight rims their silhouettes, relaxed documentary handheld feel."

Ejemplo 2 (V6 Omni, producto imitando un vídeo de referencia): "@sneaker rotates on @pedestal exactly matching the camera orbit and lighting rhythm of the reference clip, but the plasma arcs are replaced with slow-drifting golden particle trails, cinematic studio ambiance, subtle synth hum."

Diferencias por variante: C1 Fusion añade conversión de storyboard multi-panel a vídeo con un solo clic, útil para escenas con varios personajes ya bocetadas [[4]](https://docs.platform.pixverse.ai/c1-2067883m0); V6 es el único con `video_references`/modo omni [[11]](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0).

No pongas: `ref_name` con espacios o que no coincida exactamente con lo declarado en `image_references`/`video_references`; más de 3 imágenes en Fusion base; más de 10 imágenes o 2 vídeos en modo Omni; `duration` distinto de 0 cuando hay `video_references`.

### Start/end frame (transición)

Plantilla: `[descripción de cómo ocurre la transformación entre el primer y el último fotograma].` El prompt debe describir el proceso de transición, no una escena nueva ajena a ambos fotogramas; soportado en C1 y V6 por igual, duración 1-15s [[8]](https://docs.platform.pixverse.ai/transitionfirst-last-frame-feature-882973m0) [[4]](https://docs.platform.pixverse.ai/c1-2067883m0).

Ejemplo 1 (hermanos, primer fotograma: Mateo corriendo bajo lluvia; último: calle soleada): "Mateo's muddy sneakers slowly dry and clean themselves as the flooded alley drains away beneath him, sunlight breaking through the clouds while Vidal's laughing silhouette fades into a calm, sunny street scene."

Ejemplo 2 (producto, primer fotograma: zapatilla sucia; último: zapatilla en estudio): "The scuffed, dusty sneaker gradually polishes itself to a mirror shine as swirling embers coalesce into sharp studio lighting, revealing the pristine final product shot centered on a seamless white backdrop."

Diferencias por variante: ninguna de sintaxis entre C1 y V6 en esta tarea; la diferencia es de estilo resultante (C1 añade más peso dramático/VFX si el prompt lo pide) [[4]](https://docs.platform.pixverse.ai/c1-2067883m0).

No pongas: contenido ajeno a la transición entre esos dos fotogramas (una trama nueva no conectada a ambos); fotogramas con proporciones de imagen muy distintas entre sí [sin verificar]; prompts vacíos esperando que el modelo "adivine" la transición.

### Video extend (solo V6)

Plantilla: `[cómo debe continuar la acción desde el último fotograma del clip].` Se parte de un `source_video_id` generado antes en PixVerse o de un vídeo externo subido (.mp4/.mov, máx. 1920p, 50MB, 30s) [[10]](https://docs.platform.pixverse.ai/extend-1268531m0). No disponible en C1 [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0).

Ejemplo 1 (hermanos, continuando la persecución): "Vidal keeps running after Mateo as the alley opens into a wide plaza, the camera continuing its low tracking shot while the rain eases into a light drizzle and streetlights flicker on overhead."

Ejemplo 2 (producto, continuando el giro): "The camera continues orbiting the sneaker as it rises further into frame, the plasma arcs fading into a soft golden glow before the shot settles on a clean, minimal studio backdrop."

Diferencias por variante: no aplica (función exclusiva de V6).

No pongas: pedir `video_extend` sobre `model: c1` (no existe ese endpoint para C1); cambios bruscos de estilo/iluminación incoherentes con el clip original; vídeos fuente que superen 1920p, 50MB o 30s de duración.

## Idioma, longitud y prompt negativo

Idioma óptimo: **inglés**; sin confirmación oficial de calidad en español/chino para estos endpoints [sin verificar]. Longitud recomendada por el fabricante: **25-200 palabras**, con un límite técnico duro de 5000 caracteres en el campo `prompt` tanto en C1 como en V6 [[6]](https://docs.platform.pixverse.ai/text-to-video-882970m0) [[4]](https://docs.platform.pixverse.ai/c1-2067883m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0). Existe `negative_prompt` documentado en las respuestas de texto e imagen-a-vídeo [[6]](https://docs.platform.pixverse.ai/text-to-video-882970m0) [[7]](https://docs.platform.pixverse.ai/image-to-video-882971m0); verifica su presencia real por endpoint/tier con `get_model_schema` antes de prometerlo, porque no aparece en las tablas de parámetros de C1/V6.

## Referencias e imagen inicial

En imagen-a-vídeo sube la imagen (JPG/PNG/WebP, máx. ~10000px, idealmente ≥1024×1024px, sujeto claro y bien iluminado, sin watermarks) y describe solo el movimiento nuevo, no lo estático [[7]](https://docs.platform.pixverse.ai/image-to-video-882971m0). En Fusion base usa sujetos aislados y fondos limpios, con `ref_name` cortos sin espacios citados con `@` + espacio, hasta 3 referencias por petición [[9]](https://docs.platform.pixverse.ai/fusionreference-to-video-1339253m0). En V6 Omni puedes aportar hasta 10 imágenes y hasta 2 vídeos de referencia (≤15s en total) para heredar sujeto, cámara y estilo de un vídeo existente, fijando `reference_mode: omni` y `duration: 0` [[11]](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0).

## Cámara y movimiento

El vocabulario de cámara va en prosa ("the camera tracks low and fast", "slow pan", "dolly in", "crane shot", "360 orbit"); no hay tags de cámara propietarios confirmados [sin verificar]. C1 está afinado específicamente para combinar cámara rápida con combate, con fuerte sensación de velocidad y presencia [[4]](https://docs.platform.pixverse.ai/c1-2067883m0). En transición/extend el movimiento de cámara se infiere sobre todo de la continuidad entre fotogramas o del clip previo, más que de una instrucción de cámara aislada en el prompt.

## Consistencia de personaje

Fusion mantiene identidad visual de hasta 3 referencias etiquetadas en modo base, apto para escenas con dos o tres sujetos [[9]](https://docs.platform.pixverse.ai/fusionreference-to-video-1339253m0); V6 Omni amplía esto a 10 imágenes de referencia [[11]](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0). C1 añade consistencia reforzada entre personajes, entornos y multi-shot, incluyendo conversión de storyboard multi-panel a vídeo con un clic [[4]](https://docs.platform.pixverse.ai/c1-2067883m0). Sin Fusion ni un `seed` fijo no hay garantía documentada de identidad facial estable entre generaciones independientes.

## Audio y diálogo

El audio sincronizado (`generate_audio_switch`) genera ambiente/SFX acorde a la acción del plano, no diálogo dirigible por texto [[4]](https://docs.platform.pixverse.ai/c1-2067883m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0). Lip Sync y Sound Effects son endpoints independientes del fabricante (fuera del catálogo de Nexus para esta familia según `avoid_tasks`) [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0). No prometas voz, diálogo con sincronía labial ni letras de canción controlables desde estos ids de texto/imagen/Fusion/transición/extend.

## Restricciones e incompatibilidades conocidas

Toda generación pasa moderación de contenido; si falla (status 7), el crédito se reembolsa automáticamente y hay que reformular el prompt [[6]](https://docs.platform.pixverse.ai/text-to-video-882970m0). C1 no soporta `video_extend` bajo ningún endpoint [[3]](https://docs.platform.pixverse.ai/capability-matrix-2144288m0). Fusion omni (vídeos de referencia) solo existe en V6, exige `reference_mode: omni`, máximo 2 vídeos y 15s totales de referencia, y obliga a `duration: 0` [[11]](https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0). El campo `prompt` tiene un tope duro de 5000 caracteres en ambos modelos [[4]](https://docs.platform.pixverse.ai/c1-2067883m0) [[5]](https://docs.platform.pixverse.ai/v6-2056814m0).

## Errores habituales

- Describir en exceso lo estático de la imagen de entrada en vez del movimiento deseado en image-to-video.
- `ref_name` con espacios o que no coincide literalmente con `image_references`/`video_references`.
- Pedir `video_extend` a `model: c1` (solo V6 lo soporta).
- Superar 3 referencias en Fusion base o 10 imágenes/2 vídeos en modo Omni.
- Prompts por debajo de 25 palabras, que reducen la fidelidad de cámara y atmósfera.
- Asumir lipsync, voz o diálogo dirigible a partir del audio sincronizado ambiental.
- Dejar `duration` distinto de 0 al usar `video_references` en modo Omni.
- Olvidar que la transición y el extend describen un proceso de continuidad, no una escena nueva desconectada.

## Recomendaciones de prompting

- Escribe en inglés, 25-200 palabras, prosa continua: sujeto → acción → cámara → luz/atmósfera → audio opcional.
- Para acción física, nombra el tipo de combate/VFX concreto y usa C1.
- Al animar imágenes, describe solo el movimiento nuevo, nunca lo que ya es visible.
- En Fusion base usa `ref_name` cortos sin espacios, citados `@nombre` + espacio, máximo 3 imágenes.
- En V6 Omni usa `video_references` para heredar cámara/estilo de un vídeo real, con `duration: 0`.
- Fija `seed` si vas a iterar sobre la misma composición o personaje entre generaciones.
- Usa V6 si vas a extender el clip después; C1 no tiene esa opción.
- Verifica `get_model_schema` antes de asumir `negative_prompt` o relaciones de aspecto concretas.
- No prometas texto legible en pantalla, lipsync ni diálogo dirigible con estos ids.

## Fuentes y fecha

[1] PixVerse Docs — Introduction — https://docs.platform.pixverse.ai/ — consultado 2026-10-02
[2] PixVerse Docs — Model Overview — https://docs.platform.pixverse.ai/model-overview-2140345m0 — consultado 2026-10-02
[3] PixVerse Docs — Capability matrix — https://docs.platform.pixverse.ai/capability-matrix-2144288m0 — consultado 2026-10-02
[4] PixVerse Docs — C1 model — https://docs.platform.pixverse.ai/c1-2067883m0 — consultado 2026-10-02
[5] PixVerse Docs — V6 model — https://docs.platform.pixverse.ai/v6-2056814m0 — consultado 2026-10-02
[6] PixVerse Docs — Text-to-video guide — https://docs.platform.pixverse.ai/text-to-video-882970m0 — consultado 2026-10-02
[7] PixVerse Docs — Image-to-video guide — https://docs.platform.pixverse.ai/image-to-video-882971m0 — consultado 2026-10-02
[8] PixVerse Docs — Transition guide — https://docs.platform.pixverse.ai/transitionfirst-last-frame-feature-882973m0 — consultado 2026-10-02
[9] PixVerse Docs — Fusion guide — https://docs.platform.pixverse.ai/fusionreference-to-video-1339253m0 — consultado 2026-10-02
[10] PixVerse Docs — Extend guide — https://docs.platform.pixverse.ai/extend-1268531m0 — consultado 2026-10-02
[11] PixVerse Docs — Fusion V6 video-ref guide — https://docs.platform.pixverse.ai/fusion-reference-to-video-v6-video-reference-integration-guide-2269890m0 — consultado 2026-10-02
[12] PixVerse Docs — Motion Control (Mimic) — https://docs.platform.pixverse.ai/motion-controlmimic-1982936m0 — consultado 2026-10-02
[13] Atlas Cloud — PixVerse C1 Text-to-Video — https://www.atlascloud.ai/models/pixverse/c1/text-to-video — consultado 2026-10-02
[14] Atlas Cloud — PixVerse V6 Video Extend — https://www.atlascloud.ai/models/pixverse/v6/video-extend — consultado 2026-10-02
