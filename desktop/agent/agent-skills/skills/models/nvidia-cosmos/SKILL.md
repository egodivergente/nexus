---
id: nvidia-cosmos
kind: model
title: NVIDIA Cosmos 3 Super (texto a imagen, imagen a vídeo)
version: 2
updated: 2026-10-16
match:
  - '^nvidia/cosmos'
modalities: [IMAGE, VIDEO]
strengths: [physics, photorealism, prompt_adherence]
weaknesses: [long_duration]
ideal_tasks: [text_to_image, image_to_video]
avoid_tasks: [lipsync, video_extend]
unverified_tags: [physics, photorealism]
sources:
  - url: https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-image-to-video.md
    title: NVIDIA Cosmos 3 Super Image-to-Video — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-text-to-image.md
    title: NVIDIA Cosmos 3 Super Text-to-Image — README oficial
    accessed: 2026-10-16
---

## Resumen
Modelos de NVIDIA orientados a simulación del mundo físico: Text-to-Image fotorrealista y Image-to-Video que anima una imagen fija con movimiento físicamente plausible durante 1–7 s a 24 fps [[1]](https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-image-to-video.md)[[2]](https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-text-to-image.md). Ambos admiten prompt negativo, semilla, pasos y `guidance_scale`.

## Cuándo elegirlo y cuándo no
| Necesidad | Elección |
|---|---|
| Animar una foto con física creíble (agua, tela, humo, vehículos) | Cosmos Image-to-Video |
| Foto realista de un entorno o producto con control fino | Cosmos Text-to-Image |
| Planos de más de 7 s, audio o diálogo | Otra familia (Kling, Seedance, Veo) |
| Texto legible en la imagen | GPT Image o Ideogram |

## Linaje y variantes en Nexus
- `nvidia/cosmos-3-super/text-to-image`: `image_size` por preset (square_hd, portrait/landscape 4:3 y 16:9), `guidance_scale` 1–20 (por defecto 4) [[2]](https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-text-to-image.md).
- `nvidia/cosmos-3-super/image-to-video`: `duration` 1–7 s, `guidance_scale` por defecto 6, `num_inference_steps` 1–100 [[1]](https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-image-to-video.md).

## Fortalezas
- **physics** [sin verificar]: presentado para movimiento físicamente fundamentado.
- **photorealism** [sin verificar]: orientado a fotorrealismo.
- **prompt_adherence**: `guidance_scale` alto obedece más al texto.

## Debilidades
- **long_duration**: máximo 7 s por clip.
- Sin audio.

## Tareas ideales / Tareas que debe evitar
- **Ideales**: `text_to_image`, `image_to_video` de planos de ambiente o producto.
- **Evitar**: `lipsync`, `video_extend`.

## Estructura de prompt recomendada
Sujeto + acción física concreta + cámara + entorno y dinámica (viento, agua, luz). Para vídeo, describe el movimiento explícitamente [[1]](https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-image-to-video.md).

## Plantillas por tarea
### Image-to-video
«La ola rompe contra las rocas y la espuma se dispersa; cámara fija a la altura del agua; luz de atardecer, viento suave». `negative_prompt`: «flickering, blur, distortion, sudden cuts, static». `guidance_scale` 5–7.
### Text-to-image
Sujeto, entorno, luz y perspectiva de cámara en una frase; `landscape_16_9` para composiciones cinematográficas; `negative_prompt`: «blurry, low quality, distorted, watermark, cartoon» [[2]](https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-text-to-image.md).

## Idioma, longitud y prompt negativo
Prompt en inglés, hasta 2.500 caracteres. Usa el `negative_prompt` (es un campo propio) para artefactos típicos.

## Referencias e imagen inicial
Image-to-video: una imagen (png/jpeg/webp) que fija composición e identidad.

## Cámara y movimiento
Indica cámara fija o un movimiento simple; los cambios bruscos en 7 s se ven poco naturales.

## Consistencia de personaje
La aporta la imagen inicial; no redescribas al sujeto, describe su acción.

## Restricciones e incompatibilidades conocidas
Vídeo a 24 fps y 1–7 s; sin audio.

## Errores habituales
- Poner la duración en el prompt: va en `duration`.
- `guidance_scale` muy alto en vídeo: movimiento rígido; quédate en 5–7.

## Recomendaciones de prompting
- Haz una prueba de 1–2 s para validar el movimiento antes del clip completo [[1]](https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-image-to-video.md).
- Fija la semilla al iterar el texto.

## Fuentes y fecha
[1] NVIDIA Cosmos 3 Super Image-to-Video — README oficial — https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-image-to-video.md — consultado 2026-10-16
[2] NVIDIA Cosmos 3 Super Text-to-Image — README oficial — https://static.atlascloud.ai/model/readme/nvidia-cosmos-3-super-text-to-image.md — consultado 2026-10-16
