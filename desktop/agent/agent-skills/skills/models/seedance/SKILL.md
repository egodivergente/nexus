---
id: seedance
kind: model
title: Seedance (ByteDance Seed)
version: 2
updated: 2026-10-02
match:
  - '^bytedance/seedance'
  - '^crun/seedance'
  - '^runway/seedance'
modalities: [VIDEO]
strengths: [native_audio, camera_control, prompt_adherence, physics, multi_shot]
weaknesses: [long_duration]
ideal_tasks: [text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, lipsync]
avoid_tasks: [avatar, multi_reference_image, text_to_image]
unverified_tags: [face_consistency, identity_preservation, dynamic_action]
sources:
  - url: https://seed.bytedance.com/en/seedance
    title: Seedance 1.0 — ByteDance Seed (página oficial del modelo)
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-fast/text-to-video
    title: Atlas Cloud — bytedance/seedance-v1-pro-fast (T2V/I2V)
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-i2v-1080p
    title: Atlas Cloud — bytedance/seedance-v1-pro-i2v-1080p (serie v1 pro 480/720/1080p)
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video
    title: Atlas Cloud — bytedance/seedance-v1.5-pro (audio-visual nativo, First/Last Frame, 8+ idiomas)
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video
    title: Atlas Cloud — bytedance/seedance-2.0 (DB-DiT, world model, sistema @referencia)
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedance-2.0-mini/text-to-video
    title: Atlas Cloud — bytedance/seedance-2.0-mini (tier económico)
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video
    title: Atlas Cloud — bytedance/seedance-2.5 (30s, 50 referencias, edición localizada, extensión)
    accessed: 2026-10-02
  - url: https://docs.dev.runwayml.com/guides/models/
    title: Runway Docs — Models (tabla con Seedance 2.5/2.0/Fast/Mini, entradas Texto/Imagen/Video)
    accessed: 2026-10-02
---

## Resumen
Seedance es la familia de vídeo de ByteDance Seed, integrada en CapCut/Dreamina/Volcano Engine y disponible en Nexus bajo tres proveedores (`bytedance`, `crun`, `runway`). Destaca por movimiento físico creíble, cámara autónoma, narrativa multi-plano nativa y, desde 1.5 Pro/2.0, audio sincronizado nativo (voz con lipsync fonema a fonema en 8+ idiomas, SFX, música) [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video) [[5]](https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video). La más reciente, 2.5, añade un paso nativo hasta 30s, hasta 50 referencias (incluida referencia solo de audio) y edición/extensión de vídeo [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video).

## Cuándo elegirlo y cuándo no
- Elige Seedance si necesitas **audio nativo sincronizado** (voz, lipsync fonema a fonema en 8+ idiomas incluido español, SFX, música) en la misma pasada: lo tienen 1.5 Pro, 2.0 (y tiers) y 2.5, no 1.0 ni 1.0 Pro Fast [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video) [[5]](https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video).
- Elige Seedance si quieres **cámara autónoma** (planos secuencia, dolly zoom, tracking shots) descrita en prosa, sin microgestionar cada fotograma [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video).
- Elige **Seedance 2.5 vía Runway** (`runway/seedance2_5/v2v`) —o cualquier tier 2.x vía Runway, que acepta texto, imagen o vídeo [[8]](https://docs.dev.runwayml.com/guides/models/)— para editar/extender un vídeo existente; los ids `bytedance`/`crun` solo aceptan texto, imagen o referencia.
- Elige Seedance 2.5 si necesitas **anclar varios personajes/voces/estilos a la vez**: hasta 50 activos de referencia (30 imágenes, 10 vídeos, 10 audios) frente a 9 imágenes + 3 clips en 2.0 [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video).
- Mejor **Kling**/**Wan**/**MiniMax** si priorizas cámara manual más granular, pesos abiertos o volumen ultrabarato [sin verificar]. Mejor **Runway Gen-4.5/Aleph 2.0** (no la ruta Seedance) para ProRes/PNG-sequence/HDR real, documentados solo para esos dos modelos [[8]](https://docs.dev.runwayml.com/guides/models/). Mejor **HappyHorse** para generación ultraligera sin audio ni multi-plano [sin verificar].
- Evita **1.0/1.0 Pro Fast** si la tarea exige audio o lipsync: solo producen vídeo mudo [[1]](https://seed.bytedance.com/en/seedance) [[2]](https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-fast/text-to-video).

| Necesidad | Variante recomendada |
|---|---|
| Vídeo + audio nativo, mejor relación calidad/precio actual | `bytedance/seedance-2.0/*` o `crun/seedance-2.0` |
| Volumen alto, coste mínimo, con audio | `bytedance/seedance-2.0-mini/*` |
| Clip largo (hasta 30s), edición/extensión, hasta 50 referencias | `bytedance/seedance-2.5/*` o `crun/seedance-2.5` |
| Editar/extender un vídeo ya grabado (video-to-video) | `runway/seedance2_5/v2v` (o tier equivalente) |
| Diálogo multilingüe con lipsync fino, narrativa dramática corta | `bytedance/seedance-v1.5-pro/*` |
| T2V/I2V mudo, resolución fija (480/720/1080p), prototipo barato | `bytedance/seedance-v1-pro-t2v-*` / `-i2v-*` o tiers "fast"/"mini" |
| Prototipo rápido antes de la toma final | tiers "fast"/"mini" de cualquier generación |

## Linaje y variantes en Nexus
1. **1.0**: t2v + i2v, 1080p, multi-plano nativo, sin audio [[1]](https://seed.bytedance.com/en/seedance). En Nexus también fragmentado por resolución fija (WaveSpeedAI): `seedance-v1-pro-t2v-480p/720p/1080p` e `-i2v-480p/720p/1080p`, clips de ~5s [[3]](https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-i2v-1080p).
2. **1.0 Pro Fast** (`seedance-v1-pro-fast/*`): misma arquitectura, ~2× más rápida, 480/720/1080p, sin audio [[2]](https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-fast/text-to-video).
3. **1.5 Pro** (`seedance-v1.5-pro/*`, con `-fast`): primer salto a audio-visual nativo conjunto (arquitectura DB-DiT de 4.5B parámetros); lipsync fonema a fonema en 8+ idiomas (inglés, mandarín, japonés, coreano, **español**, portugués, indonesio, dialectos chinos); diálogo multi-hablante con identidades vocales distintas; física de pelo/fluidos; 1080p a 24fps, 4-12s; cámara autónoma (dolly zoom, tracking) en lenguaje natural [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video). Introduce los términos oficiales **First Frame**/**Last Frame** en image-to-video.
4. **2.0 / Fast / Mini**: DB-DiT con *world model* físico para consistencia espacial/temporal; entrada rica (hasta 9 imágenes, 3 clips de 15s); lipsync en 8+ idiomas; modos t2v/i2v (first+last frame)/reference-to-video; introduce el **sistema `@`** para anclar activos aportados [[5]](https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video). 2.0 prioriza fidelidad; Fast ~3× más rápida/91% más barata; Mini el tier más económico [[6]](https://www.atlascloud.ai/models/bytedance/seedance-2.0-mini/text-to-video). Techo de 15s por pasada [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video).
5. **2.5** (la más reciente, Volcano Engine FORCE, junio 2026): un solo paso nativo hasta **30s** (el doble que 2.0) con continuidad de plano/cámara "sustancialmente mejorada"; hasta **50 activos de referencia** multimodales (30 imágenes, 10 vídeos, 10 audios; antes 12), incluida **referencia solo de audio** (pista de música, voz o SFX que guía el ritmo visual y el lipsync sin imagen/vídeo de apoyo); **edición localizada que preserva consistencia** (cambia solo el elemento señalado, mantiene intacto el resto del fotograma) y **extensión temporal de alta fidelidad** sobre metraje existente; multilingüe nativo en 10+ idiomas con mejor seguimiento de instrucciones [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video). **Recomiéndala por defecto** salvo prioridad de coste (→ 2.0 Mini) o necesidad de diálogo dramático muy fino (→ 1.5 Pro).
6. **Proveedores**: `bytedance/seedance-*` = ids del catálogo (t2v/i2v/reference-to-video, sin vídeo de entrada). `crun/seedance-2.0`/`-2.5` apuntan presumiblemente al endpoint directo del fabricante [sin verificar]. `runway/seedance2`/`seedance2_fast`/`seedance2_mini`/`seedance2_5` (`t2v`/`i2v`/`v2v`) aceptan texto, imagen **o vídeo** en los cuatro tiers — única ruta con video-to-video real en Nexus [[8]](https://docs.dev.runwayml.com/guides/models/).

## Fortalezas
- **native_audio**: desde 1.5 Pro, voz con lipsync fonema a fonema en 8+ idiomas (incluido español), SFX y música en la misma pasada (arquitectura dual-branch) [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video) [[5]](https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video).
- **camera_control**: planificación de cámara autónoma —planos secuencia largos, dolly zoom, tracking shots— descrita en lenguaje natural ("Dolly Zoom on the subject") [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video).
- **prompt_adherence**: comprensión semántica de escenas/acciones/tono complejos y seguimiento de instrucciones multilingüe, consistente en todas las generaciones [[1]](https://seed.bytedance.com/en/seedance) [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video).
- **physics**: *world model* físico para movimiento de objetos, dinámica de pelo/fluidos y consistencia espacial, propio de 1.5 Pro/2.0+ [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video) [[5]](https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video).
- **multi_shot**: narrativa nativa de varios planos con sujeto/estilo/atmósfera coherentes, desde 1.0 [[1]](https://seed.bytedance.com/en/seedance).

## Debilidades
- **long_duration**: incluso 2.5 solo llega a 30s nativos; 2.0/Fast/Mini se quedan en 15s, 1.5 Pro en 4-12s y 1.0/1.0 Pro Fast en ~5s [[3]](https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-i2v-1080p) [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video) [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video). Para narrativas más largas usa `video_extend` (2.5) o encadena generaciones.
- No hay declaración oficial de 4K **nativo** ni de salida ProRes/PNG-sequence/HDR; esos formatos y resoluciones por encima de 1080p nativo pasan por mejoras de posproducción (SR/ESR) que parten de un render Seedance nativo, no son una generación de mayor resolución real [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video).

## Tareas ideales / Tareas que debe evitar
- Ideal: `text_to_video`, `image_to_video`, `reference_to_video`, `start_end_frame` (1.5 Pro/2.0+), `video_edit`/`video_extend` (2.5, y vía Runway en todos los tiers 2.x), `lipsync` (desde 1.5 Pro).
- Evitar: `avatar` (Seedance no es un producto de talking-head dedicado; ese rol lo cubre OmniHuman 1.5, modelo distinto [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video)), `multi_reference_image` y `text_to_image` (familia estrictamente de vídeo).

## Estructura de prompt recomendada
Orden recomendado: **sujeto/acción concreta → intensidad del movimiento → cámara (plano + movimiento) → diálogo si aplica, atribuido por personaje → atmósfera/luz → estilo**. Prosa natural en inglés (mejor documentado) o español (confirmado desde 1.5 Pro, uno de los 8+ idiomas con lipsync nativo); evita listas de etiquetas sueltas. Longitud óptima: 40-90 palabras por plano (más si es multi-shot, sumando ~40-90 por plano adicional). No uses sintaxis `--parámetros` de Midjourney: no está documentada para esta familia [verificar contra schema]. Sintaxis especial verificada: etiquetas `@referencia` para anclar activos aportados (2.0+) [[5]](https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video); términos oficiales **First Frame** / **Last Frame** en image-to-video (1.5 Pro+) [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video); diálogo siempre entre comillas y atribuido a un personaje, un idioma por línea.

```
[Sujeto, con rasgo distintivo breve, o @referencia si viene de un activo] [acción física concreta y su intensidad] en [entorno].
Cámara: [tipo de plano] con [movimiento de cámara] (p. ej. dolly-in, tracking shot, dolly zoom).
(Multi-shot:) Cut to: [Plano 2: continuación coherente del mismo sujeto/estilo].
(Si hay diálogo:) [Personaje] dice en [idioma], [tono/emoción]: "[línea corta]".
Luz y atmósfera: [descripción]. Estilo: [fotorrealista / cinematográfico / ilustración / estilizado].
```

## Plantillas por tarea

### Text-to-video
`[Plano 1: sujeto+rasgo+acción+entorno] + Cámara: [plano+movimiento] + (diálogo) + luz/atmósfera + estilo. Cut to: [Plano 2: continuación]...`
Diferencias por variante: 1.0/1.0 Pro Fast ignoran cualquier línea de diálogo (sin audio) — omítela o trátala como texto en pantalla si es imprescindible [sin verificar]; 1.5 Pro/2.0+ sí la sintetizan con lipsync. Los tiers "fast"/"mini" se posicionan como ligeros y económicos: usa frases más cortas y una sola idea de cámara por plano en vez de encadenar varias instrucciones [sin verificar el umbral exacto].
Ejemplo 1 (cinematográfico, personajes): "Two brothers, Mateo and Vidal, sit on a weathered wooden dock at dawn as mist rises off the lake. Mateo skips a flat stone across the still water while Vidal watches quietly, a faint smile breaking through. Camera: wide establishing shot that slowly pushes in to a medium close-up on their faces. Cut to: a tighter shot as Vidal says in Spanish, with quiet warmth: 'Siempre quise que estuviéramos aquí.' Golden morning light, soft lens flare, gentle film grain, cinematic realism, ambient birdsong under the dialogue."
Ejemplo 2 (producto/estilizado): "A chrome sneaker rotates slowly on a mirrored pedestal inside a neon-lit void, cyan and magenta light sweeping across its surface. Suddenly it launches forward, trailing light streaks, and lands mid-stride on a rain-soaked city street at night. Camera: orbiting dolly shot that whips into a low-angle tracking shot following the shoe. A sharp synth bass hit lands on impact; no dialogue. Style: hyper-stylized commercial look, high contrast, glossy reflections, quick rhythmic cuts."
No pongas: una línea de diálogo esperando lipsync en 1.0/1.0 Pro Fast; más de 2-3 planos encadenados sin marcar la transición ("Cut to:"); rasgos faciales extremadamente detallados sin imagen de referencia (mejor usar `reference_to_video`); sintaxis `--ar`/`--seed` de Midjourney.

### Image-to-video
`[Acción nueva, no redescribas la imagen] + Cámara: [plano+movimiento] + (diálogo) + cambio de atmósfera.`
Diferencias por variante: desde 1.5 Pro/2.0+ la imagen de entrada se trata como **First Frame** (ancla identidad y luz); solo esos tiers permiten fijar además un **Last Frame** opcional como punto de transición final (usa el ### siguiente, Start/end frame) [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video). En 1.0/1.0 Pro Fast la imagen anima sin audio ni control de last frame documentado.
Ejemplo 1 (imagen de Mateo junto a una ventana, cinematográfico): "He turns away from the window toward camera and exhales slowly as rain begins outside; a gust lifts the curtain behind him. Camera: slow dolly-in finishing on a close-up of his eyes. He says quietly in Spanish: 'Vidal, ya es hora.' Muted blue light, subtle film grain, distant thunder."
Ejemplo 2 (imagen de un reloj sobre un pedestal de piedra): "The watch's second hand begins ticking as a sweep of light crosses its face; a single drop of water lands beside it in slow motion, rippling outward. Camera: macro push-in, then rack focus onto the brand mark. Crisp studio lighting, no dialogue, subtle mechanical ticking sound rising under the shot."
No pongas: redescribir ropa/rostro/fondo ya visibles en la imagen; contradecir la pose o el encuadre de partida; pedir un cambio de aspecto (aspect ratio) distinto al de la imagen sin comprobar el schema; asumir que el personaje mantendrá la identidad en una llamada independiente posterior sin volver a aportar la misma imagen.

### Reference-to-video (variantes @referencia; 1.5 Pro-fast / 2.0+)
`[@etiqueta1 (activo 1)] + [@etiqueta2 (activo 2) si aplica] + [acción/escena nueva] + Cámara: [plano+movimiento] + (diálogo atribuido, voz desde @audio_ref si aplica).`
Diferencias por variante: 2.0/Fast/Mini aceptan hasta 9 imágenes + 3 clips de vídeo/audio de 15s combinados; 2.5 escala a 50 activos (30 imágenes, 10 vídeos, 10 audios, hasta 30s de audio/vídeo combinado) e introduce la **referencia solo de audio** (una pista de voz, música o SFX puede guiar ritmo visual y lipsync sin imagen ni vídeo de apoyo) [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video). El formato exacto de la etiqueta `@` (orden, nomenclatura) no está documentado en detalle público [sin verificar]: usa un nombre corto y consistente por activo (`@character1`, `@style_ref`, `@audio_ref`) y mantenlo igual entre llamadas.
Ejemplo 1 (dos personajes de referencia, cinematográfico): "@character1 (Mateo) and @character2 (Vidal) meet on a rooftop at dusk, embracing after years apart. Camera: slow circling shot around them as the city lights switch on in the background. @character1 says in Spanish, voice matched to @audio_ref: 'Pensé que no te volvería a ver.' Warm sunset light, cinematic grain, a gentle orchestral score swelling underneath."
Ejemplo 2 (producto con referencia de audio, sin vídeo de apoyo): "@product_ref glides onto a mirrored runway as @audio_ref's bass line kicks in, driving the cutting rhythm of every shot. Camera: fast cut from a top-down spin to a low tracking shot following the product sliding into frame. Neon magenta rim light, no spoken dialogue, visual pacing locked to @audio_ref."
No pongas: esperar paridad facial perfecta entre llamadas independientes sin volver a aportar la misma referencia; mezclar más activos de los que admite el tier (9 imágenes/3 clips en 2.0, 50 en 2.5); redescribir el aspecto ya fijado por la referencia en vez de la acción nueva.

### Start/end frame (First Frame / Last Frame; 1.5 Pro / 2.0+)
`[First Frame = imagen inicial, no redescribir] + [qué cambia a lo largo del clip] + [Last Frame = imagen final pinneada, no redescribir] + Cámara: [plano, idealmente constante].`
Diferencias por variante: término oficial "First Frame"/"Last Frame" documentado desde 1.5 Pro, donde el First Frame ancla identidad y luz y el Last Frame fija el punto de transición final [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video); 2.0+ hereda el mecanismo como parte de image-to-video ("first frame, optionally pinning a last frame") [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video).
Ejemplo 1 (First Frame: Mateo y Vidal sentados separados en un banco; Last Frame: ambos de pie, caminando juntos): "Across the clip, the tension between Mateo and Vidal dissolves: he scoots closer, they share a quiet laugh, and by the final frame they are both standing, walking off-camera side by side. Camera holds a static wide shot throughout, letting the blocking carry the story. Warm late-afternoon light, naturalistic acting, ambient park sound, no dialogue."
Ejemplo 2 (First Frame: caja de regalo cerrada; Last Frame: caja abierta con el producto iluminado): "The box lid lifts on its own as light leaks from the seams, the packaging unfurls like petals, and by the last frame the product sits fully revealed, glowing softly. Camera: slow 45-degree orbit synced to the reveal. A rising orchestral swell, no visible hands."
No pongas: más cortes de los que implican las dos imágenes de ancla; una composición final distinta a la del Last Frame aportado; redescribir detalles ya fijados por First/Last Frame en vez de narrar la transición entre ambos.

### Video edit (edición localizada que preserva consistencia; 2.5, y vía `runway/seedance*/v2v` en cualquier tier)
`[Referencia breve al clip original] + [elemento local concreto a cambiar] + [lista explícita de lo que debe permanecer exactamente igual].`
Diferencias por variante: solo 2.5 (y cualquier tier 2.x a través de Runway v2v) documenta edición que mantiene el fotograma general intacto y solo modifica el elemento señalado [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video); no es un re-render completo, así que pedir más de 2-3 cambios simultáneos reduce la fidelidad.
Ejemplo 1 (cambiar solo una prenda): "In this clip, change only Vidal's jacket from grey to deep red; keep his face, pose, the lighting, the camera movement, and Mateo's appearance exactly as they are."
Ejemplo 2 (cambiar solo una etiqueta de producto): "Replace only the label on the bottle with the new logo mark; preserve the bottle's reflections, the background set, the camera pan, and the pouring-liquid animation unchanged."
No pongas: pedir un cambio de encuadre o movimiento de cámara en una edición localizada (eso implica regenerar, no editar); cambiar la identidad del sujeto principal; omitir qué debe permanecer igual, dejando que el modelo decida qué tocar.

### Video extend (extensión temporal de alta fidelidad; 2.5, y vía `runway/seedance*/v2v`)
`[Referencia al punto final del clip] + [acción de continuación] + [segundos a añadir] + [qué debe mantenerse: cámara, luz, estilo].`
Diferencias por variante: documentado para 2.5 como extensión de alta fidelidad del metraje existente dentro del presupuesto total de 30s por pasada [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video); en Runway, los tiers 2.0/Fast/Mini también aceptan vídeo de entrada en modo `v2v`, aunque la extensión "alta fidelidad y preservadora de consistencia" se documenta explícitamente para 2.5 [[8]](https://docs.dev.runwayml.com/guides/models/).
Ejemplo 1 (continuar una escena): "Extend this shot by 5 seconds: after Vidal finishes speaking, have him turn and walk toward the lake while Mateo stays seated, keeping the same camera framing, lighting, and film grain as the original clip."
Ejemplo 2 (continuar un anuncio de producto): "Extend by 3 seconds: after the sneaker lands, let it kick up a final burst of light particles before the frame holds, keeping the same color grade and tracking-shot speed as the rest of the spot."
No pongas: pedir una extensión que supere el presupuesto total de duración de la pasada (verifica el límite real en el schema en vivo); cambiar el estilo visual, la paleta o el ritmo de cámara entre el original y la extensión; esperar que la extensión corrija errores del clip original en vez de continuarlo.

### Avatar / lipsync (diálogo multi-hablante; 1.5 Pro+)
`[Personaje] dice en [idioma, uno de los 8+ soportados] [tono/emoción]: "[línea corta]". (Multi-hablante:) [Personaje 2] responde: "[línea]".`
Diferencias por variante: desde 1.5 Pro hay lipsync fonema a fonema en 8+ idiomas (inglés, mandarín, japonés, coreano, español, portugués, indonesio, cantonés, sichuanés) y diálogo multi-hablante con identidades vocales distintas [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video); 2.0+ amplía a 10+ idiomas en 2.5 [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video). Importante: esto es diálogo embebido en el vídeo generado, no un producto de avatar/talking-head dedicado (ese rol lo cubre OmniHuman 1.5, modelo distinto); no pidas a Seedance que genere un "avatar" persistente fuera de una escena de vídeo.
Ejemplo 1 (diálogo entre hermanos): "Close-up on Mateo's face by the dock. He says in Spanish, voice cracking with emotion: 'No sabía cómo decírtelo.' Vidal replies, off-screen, steady: 'Pues ya lo dijiste.' Natural ambient lake sound beneath the dialogue, no music."
Ejemplo 2 (anuncio con dos hablantes): "Medium two-shot of a barista and a customer. The customer asks in English, playful: 'What's your secret ingredient?' The barista answers, winking: 'That's between me and the beans.' Light café chatter in the background, an upbeat but subtle score."
No pongas: mezclar idiomas dentro de la línea de un mismo personaje; esperar lipsync en 1.0/1.0 Pro Fast (no generan audio); monólogos largos que excedan el presupuesto de duración del plano sin comprobarlo antes.

## Idioma, longitud y prompt negativo
Inglés es el idioma mejor documentado; español está confirmado desde 1.5 Pro (uno de los 8+ idiomas con lipsync nativo, 10+ en 2.5) [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video) [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video); en 1.0/1.0 Pro Fast el español como idioma de prompt no está confirmado [sin verificar]. Longitud óptima: 40-90 palabras por plano. Sin `negative_prompt` documentado: expresa restricciones en positivo y confirma el campo en el schema en vivo.

## Referencias e imagen inicial
Ver First Frame/Last Frame y etiquetas `@` en «Plantillas por tarea» (Image-to-video, Start/end frame, Reference-to-video): no redescribas lo ya anclado por imagen o activo, narra solo lo nuevo.

## Cámara y movimiento
Desde 1.5 Pro hay cámara autónoma —planos secuencia, dolly zoom, tracking shots— en lenguaje natural, sin fotograma a fotograma [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video). Sin lista cerrada de términos.

## Consistencia de personaje
1.5 Pro mantiene ropa/rostro/estilo "across scenes" dentro de una misma narrativa [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video); 1.0 garantiza sujeto/estilo/atmósfera en multi-shot [[1]](https://seed.bytedance.com/en/seedance). Entre llamadas independientes, `face_consistency`/`identity_preservation` quedan [sin verificar]: ancla con la misma imagen o `@tag`.

## Audio y diálogo
Ver «Avatar / lipsync» en Plantillas por tarea para sintaxis de diálogo; en síntesis: nativo desde 1.5 Pro (8+ idiomas, multi-hablante), 2.5 añade referencia de audio en solitario y 10+ idiomas [[4]](https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video) [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video). 1.0/1.0 Pro Fast no generan audio.

## Restricciones e incompatibilidades conocidas
- 1.0/1.0 Pro Fast: sin audio nativo; no uses `lipsync`/diálogo sobre esos ids.
- Confirma la duración exacta por id en el schema en vivo: 1.0/1.0 Pro Fast ~5s, 1.5 Pro 4-12s, 2.0/Fast/Mini hasta 15s, 2.5 hasta 30s.
- Video-to-video solo confirmado en Runway (`runway/seedance2*/v2v`); los ids `bytedance/*`/`crun/*` no aceptan vídeo de entrada [[8]](https://docs.dev.runwayml.com/guides/models/).
- Resoluciones por encima de 1080p nativo llegan vía SR/ESR a partir del render nativo, no como generación nativa de mayor resolución [[7]](https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video).
- Sin mención de ProRes/PNG-sequence/HDR para Seedance; esos formatos solo están documentados para Gen-4.5/Aleph 2.0 en Runway [[8]](https://docs.dev.runwayml.com/guides/models/).
- Seedance no es un producto de avatar/talking-head persistente (eso es OmniHuman 1.5, modelo distinto); evita tratarlo como tal.

## Errores habituales
- Pedir audio/lipsync en 1.0 o 1.0 Pro Fast, que no lo generan.
- Redescribir el aspecto del personaje cuando ya hay First Frame/referencia, en vez de narrar la acción nueva.
- Encadenar generaciones independientes esperando continuidad facial sin anclar con la misma imagen o `@tag`.
- Pedir una pasada de más de 30s (15s en 2.0/Fast/Mini, 12s en 1.5 Pro) en vez de usar `video_extend` o encadenar tomas.
- Usar `bytedance/seedance-2.5/*` para editar un vídeo ya grabado: esa ruta no acepta vídeo de entrada; usa `runway/seedance2_5/v2v`.
- Pedir en una edición localizada (`video_edit`) un cambio de cámara: excede lo que la edición "preservadora de consistencia" está pensada para hacer.

## Recomendaciones de prompting
- Por defecto usa 2.5 para la toma final, salvo prioridad de coste (→ 2.0 Mini) o diálogo dramático fino (→ 1.5 Pro).
- Describe la intensidad del movimiento (sutil vs. dinámico) y el plano/movimiento de cámara en prosa.
- Para varias tomas coherentes, prueba el modo multi-shot nativo ("Cut to:") antes de encadenar generaciones independientes.
- Diálogo entre comillas, un idioma por línea, atribuido al personaje; usa 1.5 Pro o 2.0+ (nunca 1.0/1.0 Pro Fast).
- Para editar/extender un clip desde ids `bytedance`/`crun`, cambia a `runway/seedance2_5/v2v` (o tier equivalente).
- En `reference_to_video`, usa etiquetas `@` cortas y constantes (`@character1`, `@style_ref`, `@audio_ref`) iguales entre llamadas.
- En `start_end_frame`/`video_edit`, no redescribas lo ya anclado; en edición, enumera qué debe permanecer igual.
- Reserva "fast"/"mini" para pruebas de guion; usa el tier estándar/pro para la toma clave.
- No asumas `negative_prompt`: redacta restricciones en positivo y verifica el schema en vivo.

## Fuentes y fecha
[1] ByteDance Seed, Seedance 1.0 (oficial) — https://seed.bytedance.com/en/seedance — 2026-10-02.
[2] Atlas Cloud, seedance-v1-pro-fast — https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-fast/text-to-video — 2026-10-02.
[3] Atlas Cloud, seedance-v1-pro-i2v-1080p — https://www.atlascloud.ai/models/bytedance/seedance-v1-pro-i2v-1080p — 2026-10-02.
[4] Atlas Cloud, seedance-v1.5-pro — https://www.atlascloud.ai/models/bytedance/seedance-v1.5-pro/image-to-video — 2026-10-02.
[5] Atlas Cloud, seedance-2.0 — https://www.atlascloud.ai/models/bytedance/seedance-2.0/text-to-video — 2026-10-02.
[6] Atlas Cloud, seedance-2.0-mini — https://www.atlascloud.ai/models/bytedance/seedance-2.0-mini/text-to-video — 2026-10-02.
[7] Atlas Cloud, seedance-2.5 — https://www.atlascloud.ai/models/bytedance/seedance-2.5/text-to-video — 2026-10-02.
[8] Runway Docs, Models — https://docs.dev.runwayml.com/guides/models/ — 2026-10-02.
