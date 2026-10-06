---
id: vidu
kind: model
title: 'Vidu (Shengshu)'
version: 2
updated: 2026-10-02
match: ['^vidu/']
modalities: [VIDEO]
strengths: [identity_preservation, face_consistency, fast, low_cost, camera_control, native_audio]
weaknesses: [long_duration, photorealism, physics]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame]
avoid_tasks: [text_to_image, image_edit, multi_reference_image, upscale, layer_decomposition, avatar, lipsync, video_edit, video_extend, motion_control]
unverified_tags: [prompt_adherence, dynamic_action, multi_character]
sources:
  - url: https://www.vidu.com/vidu-q3
    title: 'Vidu Q3 — página oficial'
    accessed: 2026-10-02
  - url: https://platform.vidu.com/models
    title: 'Vidu API — mapa de modelos'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/image-to-video-2.0
    title: 'vidu/image-to-video-2.0'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/reference-to-video-2.0
    title: 'vidu/reference-to-video-2.0'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/start-end-to-video-2.0
    title: 'vidu/start-end-to-video-2.0'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q1/text-to-video
    title: 'vidu/q1/text-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/reference-to-video-q1
    title: 'vidu/reference-to-video-q1'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q2/text-to-video
    title: 'vidu/q2/text-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video
    title: 'vidu/q2-pro/reference-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q2-pro-fast/image-to-video
    title: 'vidu/q2-pro-fast/image-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q2-turbo/image-to-video
    title: 'vidu/q2-turbo/image-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q3-pro/text-to-video
    title: 'vidu/q3-pro/text-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q3-turbo/text-to-video
    title: 'vidu/q3-turbo/text-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q3/reference-to-video
    title: 'vidu/q3/reference-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q3-mix/reference-to-video
    title: 'vidu/q3-mix/reference-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q3-pro/start-end-to-video
    title: 'vidu/q3-pro/start-end-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q2/reference-to-video
    title: 'vidu/q2/reference-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q3-pro/image-to-video
    title: 'vidu/q3-pro/image-to-video'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/vidu/q1/image-to-video
    title: 'vidu/q1/image-to-video'
    accessed: 2026-10-02
---

## Resumen
Vidu (Shengshu Technology, con Tsinghua University) es la familia de vídeo generativo china centrada en velocidad, precio accesible y **consistencia de sujeto desde imágenes de referencia**, con cuatro tareas: texto-a-vídeo, imagen-a-vídeo, referencia-a-vídeo (1-4 imágenes de sujeto, o varios sujetos por `@id` según la generación) y frame inicial/fin. La generación vigente es **Q3** (pro/turbo/mix), con hasta 16 s en una pasada, "Smart Cuts" para narrativa multi-plano y audio nativo (diálogo con lipsync, voz, SFX, música) en inglés, japonés y chino [[1]](https://www.vidu.com/vidu-q3). El campo `prompt` es texto libre en prosa: Vidu solo usa `@tags` cuando el schema expone un array `subjects` con `id` (Q2/Q2-Pro reference-to-video), invocable como `@subjectId` en el texto [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video) [[17]](https://www.atlascloud.ai/models/vidu/q2/reference-to-video). Recomienda **Q3-pro/turbo** por defecto; usa 2.0/Q1 solo si el usuario pide coste mínimo o legado.

## Cuándo elegirlo y cuándo no
- Elige Vidu para **mantener identidad de un personaje/objeto/logo desde 1-4 fotos** sin vídeo de arranque: caso de uso insignia desde 2.0 [[4]](https://www.atlascloud.ai/models/vidu/reference-to-video-2.0) [[7]](https://www.atlascloud.ai/models/vidu/reference-to-video-q1).
- Elige Vidu si necesitas **varios sujetos nombrados en el mismo plano**: solo Q2/Q2-Pro reference-to-video expone `subjects[].id` invocable con `@id`, hasta 7 sujetos [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video) [[17]](https://www.atlascloud.ai/models/vidu/q2/reference-to-video).
- Elige Vidu para prototipar **rápido y barato** antes de escalar a otro modelo (Q1, Q2-turbo) [[6]](https://www.atlascloud.ai/models/vidu/q1/text-to-video) [[11]](https://www.atlascloud.ai/models/vidu/q2-turbo/image-to-video).
- Elige **Q3-pro/turbo** si el plano necesita **audio nativo** (voz/diálogo/SFX/música), **16 s** en una sola toma, o cámara dirigida con precisión fotograma a fotograma [[1]](https://www.vidu.com/vidu-q3) [[12]](https://www.atlascloud.ai/models/vidu/q3-pro/text-to-video).
- Mejor **Kling**/**Seedance** si necesitas >16s, edición por texto, extensión o motion control: Vidu no los cubre.
- Mejor **PixVerse** para iteración masiva al coste mínimo [sin verificar].
- Mejor **Kling**/**Seedance** para física de objetos y acción dinámica a máxima fidelidad: Vidu no documenta un motor de física dedicado.
- Evita Vidu si el pedido es imagen (texto-a-imagen, edición de imagen, upscaling): la familia en Nexus es solo vídeo.

| Necesidad | Variante recomendada |
|---|---|
| Calidad/producción actual, con audio y 16s | `vidu/q3-pro/*` |
| Mismo nivel, más rápido/económico | `vidu/q3-turbo/*` (sin reference-to-video) |
| Multi-sujeto nombrado con `@id` (hasta 7) | `vidu/q2-pro/reference-to-video`, `vidu/q2/reference-to-video` |
| Multi-sujeto por imágenes mezcladas (Q3) | `vidu/q3-mix/reference-to-video` |
| Equilibrio calidad-coste intermedio | `vidu/q2-pro/*`, `vidu/q2/*` |
| Máxima velocidad dentro de Q2 | `vidu/q2-turbo/*`, `vidu/q2-pro-fast/*` |
| Borrador barato, 5s fijos, 1080p | `vidu/q1/*` |
| Identidad desde foto única, estilo 2.0 | `vidu/image-to-video-2.0`, `vidu/reference-to-video-2.0` |
| Transición entre dos fotogramas, hasta 16s con audio | `vidu/q3-pro/start-end-to-video`, `vidu/q3-turbo/start-end-to-video` |
| Transición entre dos fotogramas, 4/8s fijos | `vidu/start-end-to-video-2.0` |

## Linaje y variantes en Nexus
1. **2.0** (`image-to-video-2.0`, `reference-to-video-2.0`, `start-end-to-video-2.0`): base, hasta 1280×720, 4-8s fijos (`duration`: 4 o 8), 1-3 imágenes en reference-to-video, buena consistencia facial [[3]](https://www.atlascloud.ai/models/vidu/image-to-video-2.0) [[4]](https://www.atlascloud.ai/models/vidu/reference-to-video-2.0) [[5]](https://www.atlascloud.ai/models/vidu/start-end-to-video-2.0). Legado: no recomendar por defecto.
2. **Q1**: t2v/i2v/reference-to-video/start-end, 5s fijos, 1080p, audio opcional (`generate_audio`, `bgm`), estilo `general`/`anime` en t2v, "Prompt Enhancer" [[6]](https://www.atlascloud.ai/models/vidu/q1/text-to-video). Reference-to-video admite 1-3 imágenes y hasta 1500 caracteres de prompt [[7]](https://www.atlascloud.ai/models/vidu/reference-to-video-q1); image-to-video Q1, en cambio, acepta hasta **5000 caracteres** de prompt, el límite más alto documentado en la familia [[19]](https://www.atlascloud.ai/models/vidu/q1/image-to-video).
3. **Q2 / Q2-Pro / Q2-Pro-Fast / Q2-Turbo**: 1-10s, 540/720/1080p. Q2 y Q2-Pro reference-to-video sustituyen el array plano de imágenes por `subjects[]` (cada sujeto con su `id` e imágenes propias, hasta 7 sujetos distintos) y permiten dirigir a cada uno por nombre dentro del prompt con `@id` [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video) [[17]](https://www.atlascloud.ai/models/vidu/q2/reference-to-video). Q2-Pro-Fast replica la calidad de Q2-Pro más rápido; Q2-Turbo es el más rápido/barato del grupo pero **no** ofrece reference-to-video, solo image-to-video y start-end [[10]](https://www.atlascloud.ai/models/vidu/q2-pro-fast/image-to-video) [[11]](https://www.atlascloud.ai/models/vidu/q2-turbo/image-to-video).
4. **Q3 / Q3-Pro / Q3-Turbo / Q3-Mix — más reciente** (30 enero 2026, Shengshu + Tsinghua University): hasta **16 s** en una pasada en las cuatro tareas (t2v, i2v, reference-to-video, start/end), **audio-vídeo nativo** (diálogo con lipsync, SFX, música vía `generate_audio`/`bgm`), "Smart Cuts" para detección automática de cortes de escena y narrativa multi-plano, cámara nativa por fotograma, y resolución con super-resolución opcional (`1080p-sr`, `1440p-sr` vía FlashVSR) [[1]](https://www.vidu.com/vidu-q3) [[12]](https://www.atlascloud.ai/models/vidu/q3-pro/text-to-video) [[18]](https://www.atlascloud.ai/models/vidu/q3-pro/image-to-video) [[16]](https://www.atlascloud.ai/models/vidu/q3-pro/start-end-to-video). Q3-Pro usa `images[1-4]` planas en reference-to-video (sin `@id`), igual que Q3-Mix [[14]](https://www.atlascloud.ai/models/vidu/q3/reference-to-video) [[15]](https://www.atlascloud.ai/models/vidu/q3-mix/reference-to-video); **Q3-Turbo no incluye reference-to-video** en el catálogo revisado, solo t2v/i2v/start-end [[13]](https://www.atlascloud.ai/models/vidu/q3-turbo/text-to-video). Multilingüe (EN/JP/CN) y multi-hablante [[1]](https://www.vidu.com/vidu-q3). **Recomiéndala por defecto** salvo coste mínimo explícito o necesidad de `@id` multi-sujeto (usa Q2-Pro en ese caso).
5. **Fuera de alcance**: el mapa oficial también lista `vidu-s2-avatar`, `vidu-s2-editing` y `viduq3-drama`; no están en el catálogo de ids de Nexus cubierto aquí [[2]](https://platform.vidu.com/models).

## Fortalezas
- **identity_preservation**/**face_consistency**: reference-to-video preserva rostro, estilo y apariencia del sujeto entre fotogramas desde 2.0 [[4]](https://www.atlascloud.ai/models/vidu/reference-to-video-2.0) [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video).
- **fast**/**low_cost**: Q1 y los tiers "turbo"/"fast" priorizan latencia mínima y precio accesible [[6]](https://www.atlascloud.ai/models/vidu/q1/text-to-video) [[11]](https://www.atlascloud.ai/models/vidu/q2-turbo/image-to-video).
- **camera_control**: Q3 añade control de cámara nativo a nivel de fotograma (paneos, push-ins, tracking) [[1]](https://www.vidu.com/vidu-q3).
- **native_audio**: audio sincronizado opcional desde Q1 vía `generate_audio`/`bgm`, nativo con lipsync y multi-hablante en Q3 [[6]](https://www.atlascloud.ai/models/vidu/q1/text-to-video) [[1]](https://www.vidu.com/vidu-q3).
- Multi-sujeto por `@id` (feature documentada, no incluida como strength formal en la cabecera): Q2/Q2-Pro reference-to-video resuelve hasta 7 sujetos nombrados, útil para diálogos entre varios personajes sin fundirlos en una sola referencia [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video).

## Debilidades
- **long_duration**: tope de 16 s incluso en Q3; 2.0 queda fijo en 4/8s y Q1 en 5s — encadena generaciones para narrativas más largas.
- **photorealism**/**physics**: sin motor de física ni modo fotorrealista dedicado comparable a Kling/Seedance; cautela en acción física compleja.
- Sin `video_edit`/`video_extend`/`motion_control` en los ids cubiertos.
- Q3-Turbo no ofrece reference-to-video: si necesitas identidad + velocidad máxima, el tier más rápido disponible con referencia es Q2-Turbo (solo image-to-video) o Q2-Pro-Fast.
- [sin verificar] comportamiento con varios personajes principales fuera de reference-to-video (en t2v/i2v puro no hay mecanismo de anclaje por nombre).
- [sin verificar] seguimiento de instrucciones causales multi-paso complejas.

## Tareas ideales / Tareas que debe evitar
- Ideal: `text_to_video`, `image_to_video`, `reference_to_video` (identidad desde 1-4 fotos, o multi-sujeto por `@id` en Q2/Q2-Pro), `start_end_frame` (transición entre dos fotogramas).
- Evitar: `text_to_image`, `image_edit`, `multi_reference_image`, `upscale`, `layer_decomposition`, `avatar`, `lipsync` (como tarea independiente), `video_edit`, `video_extend`, `motion_control` — ninguno de estos modos existe en los ids de Vidu cubiertos en Nexus.

## Estructura de prompt recomendada
Inglés en prosa natural y concreta; 1-2 frases, ~15-60 palabras (los prompts por defecto del propio fabricante en los schemas son de una sola frase, p. ej. "Santa Claus and the bear hug by the lakeside." o "the girl walks from the painting to the room, put the coffee cup on the table"). Describe sujeto, acción/motion y entorno; evita listas de etiquetas sueltas tipo Danbooru. Si usas `subjects[]` con `id`, invoca cada sujeto con `@id` la primera vez que aparece en la frase.

```
[Quién/qué — sujeto, personaje o @id de referencia] + [acción y motion concretos] + [entorno, luz y atmósfera] + [opcional: diálogo entre comillas si hay audio] + [opcional: estilo/intensidad en prosa, p. ej. "slow and gentle"]
```
No uses `--parámetros` de Midjourney: no están documentados para Vidu [sin verificar]; duración, resolución, aspect ratio, estilo (`general`/`anime`), audio (`generate_audio`, `bgm`, `audio_type`) y amplitud de movimiento (`movement_amplitude`: auto/small/medium/large) van por el schema (`get_model_schema`), nunca como texto dentro de `prompt`.

## Plantillas por tarea

### Text-to-video (Q1 / Q2 / Q3-Pro / Q3-Turbo)
Plantilla: `[Sujeto y escena] + [acción principal] + [entorno/luz] + [opcional: diálogo entre comillas si vas a generar audio] + [opcional: tono/atmósfera]`. No incluyas duración, resolución ni `--ar`: van en `aspect_ratio`, `resolution`, `duration` del schema; el estilo (`general`/`anime`) tampoco se escribe en el texto, es un parámetro aparte en Q1 y Q3-Pro [[6]](https://www.atlascloud.ai/models/vidu/q1/text-to-video) [[12]](https://www.atlascloud.ai/models/vidu/q3-pro/text-to-video).

- Cinematográfico con personajes: "Mateo and Vidal, two brothers in their twenties, sit on a weathered wooden dock at sunset with fishing rods in hand. Mateo laughs and nudges Vidal's shoulder, pointing at a fish jumping near the shore. Golden light reflects off the calm lake, a gentle breeze moves their hair, warm nostalgic mood, 35mm film look."
- Producto / estilizado: "A sleek silver perfume bottle rotates slowly on a black mirrored pedestal, water droplets sliding down its surface in slow motion. Soft studio lighting creates sharp highlights, faint mist swirls around the base, minimalist high-end commercial style, elegant futuristic mood."

Diferencias por variante: Q1 queda fijo en 5s/1080p y no genera más de un plano; **Q3-Pro/Turbo** soportan hasta 16s y "Smart Cuts" detecta cortes de escena automáticamente, así que puedes describir 2-3 beats narrativos encadenados en el mismo prompt ("…then the camera cuts to…") sin pedir video_edit aparte [[1]](https://www.vidu.com/vidu-q3) [[12]](https://www.atlascloud.ai/models/vidu/q3-pro/text-to-video). Q3-Turbo prioriza velocidad: mantén una sola acción clara por frase para no perder matices decorativos [sin verificar].

No pongas: duración/resolución/aspect ratio en el texto; etiquetas sueltas separadas por comas en vez de prosa; diálogo entre comillas si no vas a activar `generate_audio`; más de 2-3 beats narrativos en Q1 (no tiene Smart Cuts, intentará comprimirlos en 5s).

### Image-to-video (2.0 / Q1 / Q2-Pro-Fast / Q2-Turbo / Q3-Pro / Q3-Turbo)
Plantilla: `[Acción nueva del sujeto de la imagen] + [motion/cámara] + [opcional: cambios de entorno o luz que la imagen no muestra]`. No redescribas el aspecto físico, ropa o encuadre ya presentes en la imagen subida; eso lo aporta `image`, no el texto [[3]](https://www.atlascloud.ai/models/vidu/image-to-video-2.0).

- Cinematográfico con personaje (parte de una foto de Vidal mirando al horizonte): "He slowly turns his head toward the camera and gives a quiet, knowing smile. The wind picks up, moving his jacket and hair, as the camera dollies in gently to a medium close-up, holding on his face for a long beat before he looks back out toward the horizon."
- Producto (parte de una foto fija de una zapatilla sobre fondo blanco): "The sneaker rotates 180 degrees on an invisible axis while a splash of water freezes mid-air around it, dynamic studio lighting sweeps across the surface, camera pushes in slightly for a dramatic reveal, then pulls back as the water droplets scatter and settle in slow motion."

Diferencias por variante: Q1 permite hasta **5000 caracteres** de prompt (el límite más alto de la familia) y queda fijo en 5s/1080p [[19]](https://www.atlascloud.ai/models/vidu/q1/image-to-video); 2.0 limita a 1500 caracteres y 4 u 8s fijos [[3]](https://www.atlascloud.ai/models/vidu/image-to-video-2.0); **Q3-Pro/Turbo** suben a 16s con audio nativo opcional y permiten `1080p-sr`/`1440p-sr` (super-resolución FlashVSR) [[18]](https://www.atlascloud.ai/models/vidu/q3-pro/image-to-video).

No pongas: descripciones del rostro, ropa o fondo que ya se ven en la imagen; parámetros de cámara tipo `--camera` (usa prosa: "camera pans left", "slow dolly-in"); duración/resolución en el texto; audio o diálogo si no vas a activar `generate_audio`.

### Reference-to-video (2.0 / Q1 / Q2 / Q2-Pro / Q3 / Q3-Mix)
Plantilla sin `@id` (2.0, Q1, Q3, Q3-Mix — `images[]` plano, 1-4 imágenes del mismo sujeto o grupo): `[Qué hace el sujeto de referencia] + [entorno] + [motion/cámara]`. Plantilla con `@id` (Q2, Q2-Pro — `subjects[]` con hasta 7 sujetos distintos): `[@id1 hace algo] + [y/mientras @id2 hace otra cosa] + [entorno/motion]`.

- Cinematográfico, sin `@id` (3 imágenes del mismo chico, Mateo): "The person from the reference images, Mateo, walks slowly through a sunlit outdoor market, glancing at fruit stalls with curiosity, a warm breeze moving his shirt, handheld camera following him at eye level, pausing briefly to pick up an orange and smell it before continuing down the row of stalls."
- Multi-sujeto con `@id` (Q2-Pro, subjects `mateo` y `vidal`): "@mateo laughs and claps @vidal on the back as they walk down a quiet street at dusk, @vidal smiles and shakes his head in disbelief, streetlights flicker on one by one behind them, their long shadows stretching across the pavement, warm cinematic tone, slow handheld camera trailing a few steps behind."

Diferencias por variante: 2.0/Q1 aceptan 1-3 imágenes de un único sujeto, sin IDs [[4]](https://www.atlascloud.ai/models/vidu/reference-to-video-2.0) [[7]](https://www.atlascloud.ai/models/vidu/reference-to-video-q1); **Q2/Q2-Pro** sustituyen eso por `subjects[]` (cada uno con `id` e imágenes propias, hasta 3 imágenes por sujeto y 7 sujetos en total) y el `id` se invoca literalmente como `@id` dentro del prompt [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video) [[17]](https://www.atlascloud.ai/models/vidu/q2/reference-to-video); **Q3/Q3-Mix** vuelven a `images[1-4]` planas (sin `@id`), pensadas para fundir varios sujetos o estilos en una sola escena sin direccionarlos por nombre [[14]](https://www.atlascloud.ai/models/vidu/q3/reference-to-video) [[15]](https://www.atlascloud.ai/models/vidu/q3-mix/reference-to-video).

No pongas: rasgos físicos ya visibles en las imágenes de referencia; `@id` en 2.0/Q1/Q3/Q3-Mix (no existe ese campo, se ignorará como texto literal); más de 7 sujetos o más de 3 imágenes por sujeto en Q2/Q2-Pro; referencias de baja resolución (<128×128px) o con aspecto fuera de 1:4-4:1.

### Start/end frame (2.0 / Q3-Pro / Q3-Turbo)
Plantilla: `[Transición entre el fotograma inicial y el final] + [tono/luz/ritmo]`. La acción descrita debe ser físicamente coherente con ambas imágenes subidas (`image` y `end_image`); Vidu exige que la densidad de píxeles entre ambas esté en una proporción de 0.8 a 1.25 [[5]](https://www.atlascloud.ai/models/vidu/start-end-to-video-2.0) [[16]](https://www.atlascloud.ai/models/vidu/q3-pro/start-end-to-video).

- Cinematográfico (frame inicial: Vidal de pie en la puerta; frame final: Vidal sentado a la mesa): "Vidal walks calmly from the doorway to the table, pulls out the chair, and sits down with a tired sigh, soft kitchen light staying constant throughout the motion, his shoulders relaxing as he finally rests after a long day."
- Producto / transformación (frame inicial: un coche de juguete; frame final: un coche deportivo real): "The toy car morphs smoothly into a full-size sports car, metal panels unfolding and reflecting bright showroom light, dynamic energetic transition, camera orbiting around it as the final paint finish gleams under the spotlights."

Diferencias por variante: 2.0 fija la duración en 4 u 8s exactos y no genera audio [[5]](https://www.atlascloud.ai/models/vidu/start-end-to-video-2.0); **Q3-Pro/Turbo** permiten de 1 a 16s, audio nativo (`generate_audio`, `bgm`) y super-resolución (`1080p-sr`/`1440p-sr`), por lo que puedes describir una transición más larga y con sonido ambiente o diálogo [[16]](https://www.atlascloud.ai/models/vidu/q3-pro/start-end-to-video).

No pongas: una transición que contradiga la pose/encuadre de alguno de los dos fotogramas; imágenes con relación de aspecto muy distinta entre sí (fuera de 0.8-1.25); diálogo con lipsync si la variante es 2.0 (no genera audio); duración exacta en el texto, usa `duration` del schema.

## Idioma, longitud y prompt negativo
Inglés es el idioma mejor documentado por el fabricante y el que usan todos los ejemplos por defecto en los schemas revisados; prompts cortos y concretos, normalmente una sola frase compuesta. El campo `prompt` acepta hasta 1500 caracteres en la mayoría de variantes (2.0, Q1 reference-to-video, Q2, Q3) [[7]](https://www.atlascloud.ai/models/vidu/reference-to-video-q1) [[5]](https://www.atlascloud.ai/models/vidu/start-end-to-video-2.0), salvo **Q1 image-to-video**, que admite hasta 5000 caracteres [[19]](https://www.atlascloud.ai/models/vidu/q1/image-to-video); confirma siempre el límite real por variante con el schema en vivo. No hay campo de negative prompt documentado en las fichas revisadas [sin verificar]: expresa restricciones en positivo dentro del prompt (p. ej. "clear daytime sky" en vez de "no night, no clouds").

## Referencias e imagen inicial
Reference-to-video en 2.0/Q1 acepta 1-3 imágenes (PNG/JPEG/JPG/WebP, mín. 128×128px, aspecto 1:4-4:1, máx. 50MB) de un único sujeto [[7]](https://www.atlascloud.ai/models/vidu/reference-to-video-q1). Q3/Q3-Mix amplían a 1-4 imágenes bajo el mismo esquema plano [[14]](https://www.atlascloud.ai/models/vidu/q3/reference-to-video). Q2/Q2-Pro cambian de esquema: cada sujeto es un objeto `{id, images[1-3]}` dentro de `subjects[1-7]`, con límite de 20MB por request en vez de 50MB por imagen [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video). Usa siempre imágenes nítidas, bien iluminadas y con el sujeto centrado; evita recortes extremos. Start/end frame necesita `image` inicial y `end_image` final con densidad de píxeles comparable entre sí [[5]](https://www.atlascloud.ai/models/vidu/start-end-to-video-2.0) [[16]](https://www.atlascloud.ai/models/vidu/q3-pro/start-end-to-video).

## Cámara y movimiento
Desde Q3, control de cámara nativo a nivel de fotograma (paneos, push-ins, tracking shots) que se dirige describiéndolo en prosa dentro del prompt ("the camera slowly pushes in", "handheld tracking shot") [[1]](https://www.vidu.com/vidu-q3). En 2.0/Q1/Q2, la intensidad general se ajusta con `movement_amplitude` (`auto`/`small`/`medium`/`large`) del schema, no con vocabulario de cámara en el prompt [[6]](https://www.atlascloud.ai/models/vidu/q1/text-to-video); describir la acción en prosa ("walks slowly") sigue influyendo. [sin verificar vocabulario de cámara detallado fuera de Q3].

## Consistencia de personaje
Garantizada en los modos reference-to-video de todas las generaciones: identidad, estilo y apariencia se preservan desde las imágenes subidas [[4]](https://www.atlascloud.ai/models/vidu/reference-to-video-2.0) [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video). Para **más de un personaje principal con identidad propia**, usa Q2/Q2-Pro con `subjects[]` y direcciona cada uno por `@id`; para fundir varios sujetos en una escena sin direccionarlos por nombre, usa Q3-Mix. En t2v/i2v genéricos no hay garantía entre generaciones separadas: reutiliza siempre la misma referencia validada.

## Audio y diálogo
Audio opcional (`generate_audio`, `bgm`, y en algunos tiers `audio_type`: all/speech_only/sound_effect_only) desde Q1, disponible en t2v, i2v, reference-to-video y start/end de Q1/Q2/Q3 [[6]](https://www.atlascloud.ai/models/vidu/q1/text-to-video) [[9]](https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video). Q3 lo hace **nativo por diseño**: diálogo con lipsync, voz, SFX y música en una pasada, multi-hablante, en inglés/japonés/chino [[1]](https://www.vidu.com/vidu-q3). Escribe el diálogo entre comillas dentro del prompt y describe tono/emoción justo antes o después ("she says, in a warm voice, \"We made it.\""); activa `generate_audio` en el schema o el texto se generará mudo. 2.0 no documenta generación de audio: no esperes diálogo ni SFX en esa generación.

## Restricciones e incompatibilidades conocidas
Solo vídeo: ningún id de Vidu en Nexus hace text_to_image/image_edit/upscale/layer_decomposition, ni tiene video_edit/video_extend/motion_control. Reference-to-video limita a 1-4 imágenes planas (2.0/Q1/Q3/Q3-Mix) o a 7 sujetos con 3 imágenes cada uno (Q2/Q2-Pro); confirma el máximo exacto, resolución y duración por variante con `get_model_schema`. Q3-Turbo no expone reference-to-video.

## Errores habituales
- Recomendar 2.0/Q1 por defecto en vez de Q3-pro/turbo.
- Redescribir el aspecto físico del sujeto cuando ya hay imagen de referencia, en vez de acción/motion/entorno.
- Usar `@id` en variantes que no tienen `subjects[]` (2.0, Q1, Q3, Q3-Mix): el `@id` se interpretará como texto literal, no como referencia.
- Pedir video_edit, video_extend o motion_control a Vidu: no existen en esta familia en Nexus.
- Subir referencias de baja calidad o mal recortadas esperando identidad consistente igualmente.
- Escribir prompts largos en etiquetas sueltas en vez de una o dos frases naturales.
- Pedir diálogo o SFX sin activar `generate_audio`, o esperarlo en 2.0 (no genera audio).
- Esperar física o fotorrealismo al nivel de Kling/Seedance en acción compleja.

## Recomendaciones de prompting
- Recomienda Q3-pro/turbo por defecto; usa 2.0/Q1/Q2 solo si piden barato, legado o `@id` multi-sujeto (Q2-Pro).
- Escribe en inglés, 1-2 frases naturales (~15-60 palabras); evita listas de etiquetas.
- En reference-to-video sin `@id`, sube 1-4 imágenes claras del sujeto y describe solo lo nuevo.
- En reference-to-video con `@id` (Q2/Q2-Pro), nombra cada sujeto al inicio de su acción.
- En start/end frame, verifica coherencia visual y de densidad de píxeles entre fotogramas antes de describir la transición.
- Para audio/diálogo en Q1/Q2/Q3, activa `generate_audio`, usa comillas e indica tono; confirma idioma de voz (EN/JP/CN en Q3).
- No pidas duración/resolución/aspect ratio/estilo en el texto: usa el schema (`duration`, `resolution`, `aspect_ratio`, `style`, `movement_amplitude`).
- Para >16s, encadena generaciones con la misma referencia validada.
- Si la tarea exige editar/extender vídeo o coreografía, cambia a Kling o Seedance.

## Fuentes y fecha
[1] Vidu Q3 — https://www.vidu.com/vidu-q3 — consultado 2026-10-02.
[2] Vidu API models — https://platform.vidu.com/models — consultado 2026-10-02.
[3] vidu/image-to-video-2.0 — https://www.atlascloud.ai/models/vidu/image-to-video-2.0 — 2026-10-02.
[4] vidu/reference-to-video-2.0 — https://www.atlascloud.ai/models/vidu/reference-to-video-2.0 — 2026-10-02.
[5] vidu/start-end-to-video-2.0 — https://www.atlascloud.ai/models/vidu/start-end-to-video-2.0 — 2026-10-02.
[6] vidu/q1/text-to-video — https://www.atlascloud.ai/models/vidu/q1/text-to-video — 2026-10-02.
[7] vidu/reference-to-video-q1 — https://www.atlascloud.ai/models/vidu/reference-to-video-q1 — 2026-10-02.
[8] vidu/q2/text-to-video — https://www.atlascloud.ai/models/vidu/q2/text-to-video — 2026-10-02.
[9] vidu/q2-pro/reference-to-video — https://www.atlascloud.ai/models/vidu/q2-pro/reference-to-video — 2026-10-02.
[10] vidu/q2-pro-fast/image-to-video — https://www.atlascloud.ai/models/vidu/q2-pro-fast/image-to-video — 2026-10-02.
[11] vidu/q2-turbo/image-to-video — https://www.atlascloud.ai/models/vidu/q2-turbo/image-to-video — 2026-10-02.
[12] vidu/q3-pro/text-to-video — https://www.atlascloud.ai/models/vidu/q3-pro/text-to-video — 2026-10-02.
[13] vidu/q3-turbo/text-to-video — https://www.atlascloud.ai/models/vidu/q3-turbo/text-to-video — 2026-10-02.
[14] vidu/q3/reference-to-video — https://www.atlascloud.ai/models/vidu/q3/reference-to-video — 2026-10-02.
[15] vidu/q3-mix/reference-to-video — https://www.atlascloud.ai/models/vidu/q3-mix/reference-to-video — 2026-10-02.
[16] vidu/q3-pro/start-end-to-video — https://www.atlascloud.ai/models/vidu/q3-pro/start-end-to-video — 2026-10-02.
[17] vidu/q2/reference-to-video — https://www.atlascloud.ai/models/vidu/q2/reference-to-video — 2026-10-02.
[18] vidu/q3-pro/image-to-video — https://www.atlascloud.ai/models/vidu/q3-pro/image-to-video — 2026-10-02.
[19] vidu/q1/image-to-video — https://www.atlascloud.ai/models/vidu/q1/image-to-video — 2026-10-02.
