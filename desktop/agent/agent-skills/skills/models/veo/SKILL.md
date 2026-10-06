---
id: veo
kind: model
title: 'Veo (Google DeepMind)'
version: 2
updated: 2026-10-02
match: ['^google/veo', '^crun/google/veo', '^runway/veo']
modalities: [VIDEO]
strengths: [native_audio, prompt_adherence, physics, camera_control, cinematic_look]
weaknesses: [identity_preservation, long_duration]
ideal_tasks: [text_to_video, image_to_video, start_end_frame, reference_to_video, video_extend, lipsync]
avoid_tasks: [video_edit, avatar]
unverified_tags: [face_consistency, high_resolution]
sources:
  - url: https://ai.google.dev/gemini-api/docs/video
    title: 'Video generation in the Gemini API'
    accessed: 2026-10-02
  - url: https://ai.google.dev/gemini-api/docs/models/veo-3.1-generate-preview
    title: 'Veo 3.1 model page'
    accessed: 2026-10-02
  - url: https://ai.google.dev/gemini-api/docs/models/veo-3.1-lite-generate-preview
    title: 'Veo 3.1 Lite Preview model page'
    accessed: 2026-10-02
  - url: https://deepmind.google/models/veo/
    title: 'Veo 3.1 — Google DeepMind'
    accessed: 2026-10-02
  - url: https://cloud.google.com/vertex-ai/generative-ai/docs/video/video-gen-prompt-guide
    title: 'Video generation prompt guide'
    accessed: 2026-10-02
  - url: https://www.atlascloud.ai/models/google/veo3.1/reference-to-video
    title: 'Veo 3.1 Reference-to-Video (Atlas Cloud)'
    accessed: 2026-10-02
---

> **Exclusión por defecto:** Veo está excluido salvo petición explícita. Úsala solo en ese caso o para comparar.

## Resumen
Veo 3.1 (Google DeepMind) genera vídeo con **audio nativo sincronizado** (ambiente, diálogo, música) y destaca en adherencia al prompt y física realista [[1]](https://deepmind.google/models/veo/). Google recomienda Gemini Omni Flash como opción por defecto para vídeo general, y reserva Veo 3.1 para extensión de escena, frame final y dirección por imagen [[2]](https://ai.google.dev/gemini-api/docs/video).

## Cuándo elegirlo y cuándo no
- Elige Veo si necesitas **audio nativo** en la misma pasada o **start/end-frame** para encadenar planos [[2]](https://ai.google.dev/gemini-api/docs/video).
- Mejor **Gemini Omni Flash** para edición multi-turno o consistencia de personaje, fortaleza que Google atribuye a Omni Flash, no a Veo [[2]](https://ai.google.dev/gemini-api/docs/video).
- Mejor **Kling** para duración larga o 4K confirmado; mejor **Seedance** para muchos planos baratos sin audio nativo.
- En la familia: "fast" para iterar barato, completa cuando audio y detalle final importan.

| Necesidad | Variante |
|---|---|
| Corte barato con audio | veo3.1-fast |
| Volumen alto, sin 4K/extensión | veo3.1-lite |
| Audio + máxima fidelidad | veo3.1 completo |
| Continuidad exacta entre fotogramas | start-end-frame |
| Personaje con hasta 3 referencias | reference-to-video |

## Linaje y variantes en Nexus
Veo 3 introdujo audio nativo; Veo 3.1 mejoró adherencia al prompt y control del audio [[1]](https://deepmind.google/models/veo/). En Nexus la familia ya corre sobre **3.1**, la más reciente: `google/veo3.1*` (completo), `google/veo3.1-fast*` (económico) y `google/veo3.1-lite*` (volumen alto, sin 4K ni extensión [[3]](https://ai.google.dev/gemini-api/docs/models/veo-3.1-lite-generate-preview)), en text-to-video, image-to-video, start-end-frame-to-video y reference-to-video. También vía `crun/google/veo...` y `runway/veo3.1(_fast)/i2v|t2v` [sin verificar].

## Fortalezas
- native_audio: ambiente, efectos y música sincronizados en la misma pasada [[1]](https://deepmind.google/models/veo/).
- prompt_adherence: mejora explícita ("Follows prompts like never before") [[1]](https://deepmind.google/models/veo/).
- physics: "real world physics" como mejora de realismo [[1]](https://deepmind.google/models/veo/).
- camera_control / cinematic_look: vocabulario amplio de cámara con ejemplos oficiales [[5]](https://cloud.google.com/vertex-ai/generative-ai/docs/video/video-gen-prompt-guide).

## Debilidades
- identity_preservation: Google atribuye consistencia de personaje y edición multi-turno a Omni Flash, no a Veo [[2]](https://ai.google.dev/gemini-api/docs/video).
- long_duration: clips de 4/6/8s (8s obligatorio en 1080p Lite); nada de clips largos nativos [[3]](https://ai.google.dev/gemini-api/docs/models/veo-3.1-lite-generate-preview).

## Tareas ideales / Tareas que debe evitar
- Ideal: text_to_video, image_to_video, start_end_frame, reference_to_video (≤3 imágenes) [[6]](https://www.atlascloud.ai/models/google/veo3.1/reference-to-video); video_extend si el schema lo permite, no en Lite [[3]](https://ai.google.dev/gemini-api/docs/models/veo-3.1-lite-generate-preview); lipsync por el audio nativo.
- Evitar: video_edit multi-turno (Google remite a Omni Flash) [[2]](https://ai.google.dev/gemini-api/docs/video); avatar dedicado (no es modo del registro).

## Estructura de prompt recomendada
Inglés, prosa descriptiva (no tags), orden: Sujeto → Acción → Escena → Cámara → Estilo → Audio/diálogo [[5]](https://cloud.google.com/vertex-ai/generative-ai/docs/video/video-gen-prompt-guide).
```
[Sujeto específico] + [acción/gestos] + [escena: lugar, hora, clima] +
[ángulo + movimiento de cámara] + [lente/estilo] + [sonido o diálogo en comillas]
```
Ejemplo: "A seasoned detective leans forward in a dim interrogation room, eyes narrowing. Static medium shot, shallow depth of field. He says: 'Your story has holes.' Faint rain against the window."

## Plantillas por tarea
- **Text-to-video:** "A majestic bald eagle glides low over a misty mountain lake at dawn. Sweeping aerial drone shot. Wind rushing, distant birdsong."
- **Image-to-video / start-end-frame:** describe solo el cambio entre fotogramas: "The character slowly turns and smiles; slow dolly in, golden-hour light, ambient café chatter."
- **Reference-to-video (≤3 imágenes):** nombra qué aporta cada referencia: "Using the person from image 1 and the jacket from image 2, she walks through a rain-soaked alley at night; tracking shot, neon reflections." No repitas rasgos ya visibles.

## Idioma, longitud, referencias y prompt negativo
Inglés, prosa densa; [sin verificar] soporte robusto de español/chino. Límite de entrada: 1.024 tokens [[4]](https://ai.google.dev/gemini-api/docs/models/veo-3.1-generate-preview). Image-to-video acepta imagen inicial y, en 3.1 completo, `lastFrame` para interpolar [[6]](https://www.atlascloud.ai/models/google/veo3.1/reference-to-video). Negative prompt: describe en positivo lo que no quieres ("wall, frame"), no uses "no"/"don't" [[5]](https://cloud.google.com/vertex-ai/generative-ai/docs/video/video-gen-prompt-guide); confirma en el schema en vivo.

## Cámara, personaje y audio
Vocabulario oficial: ángulos (eye-level, low/high-angle, bird's-eye, Dutch, close-up, POV), movimientos (pan, tilt, dolly, truck, pedestal, zoom, crane, handheld, whip pan, arc shot) y lentes (wide-angle, telephoto, depth of field, lens flare, rack focus, dolly zoom); algunos avanzados "no están oficialmente soportados" [[5]](https://cloud.google.com/vertex-ai/generative-ai/docs/video/video-gen-prompt-guide). identity_preservation débil/[sin verificar]: usa la misma referencia validada entre tomas [[2]](https://ai.google.dev/gemini-api/docs/video). Audio nativo siempre presente; diálogo entre comillas y línea `Audio:` para sonidos extra [[1]](https://deepmind.google/models/veo/).

## Restricciones e incompatibilidades conocidas
Veo 3.1 Lite no soporta 4K ni extensión; los filtros de seguridad se aplican a entrada y salida [[3]](https://ai.google.dev/gemini-api/docs/models/veo-3.1-lite-generate-preview). No es la opción de Google para edición multi-turno [[2]](https://ai.google.dev/gemini-api/docs/video).

## Errores habituales
Pedir edición iterativa en varios turnos (Google remite esa capacidad a Omni Flash); repetir rasgos físicos ya presentes en la imagen/referencia en vez de describir solo el cambio.

## Recomendaciones de prompting
- Escribe en inglés, en prosa: sujeto→acción→escena→cámara→estilo→audio.
- Describe también el paisaje sonoro: el audio es nativo.
- Para continuidad entre tomas usa start/end-frame y describe solo la transición.
- En reference-to-video, indica qué aporta cada imagen.
- Prototipa en "fast" y escala a la completa solo si el audio/detalle lo justifican.
- En negative prompt lista sustantivos a evitar, no órdenes; no asumas consistencia facial sin validar.

## Fuentes y fecha
[1] Veo 3.1 — Google DeepMind — https://deepmind.google/models/veo/ — 2026-10-02.
[2] Video generation in the Gemini API — https://ai.google.dev/gemini-api/docs/video — 2026-10-02.
[3] Veo 3.1 Lite Preview — https://ai.google.dev/gemini-api/docs/models/veo-3.1-lite-generate-preview — 2026-10-02.
[4] Veo 3.1 model page — https://ai.google.dev/gemini-api/docs/models/veo-3.1-generate-preview — 2026-10-02.
[5] Video generation prompt guide — https://cloud.google.com/vertex-ai/generative-ai/docs/video/video-gen-prompt-guide — 2026-10-02.
[6] Veo 3.1 Reference-to-Video (Atlas Cloud) — https://www.atlascloud.ai/models/google/veo3.1/reference-to-video — 2026-10-02.
