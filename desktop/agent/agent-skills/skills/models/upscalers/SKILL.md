---
id: upscalers
kind: model
title: Upscalers y limpieza (Atlas Image/Video Upscaler, Photo Cleanup, BytePlus Video Upscaler)
version: 2
updated: 2026-10-16
match:
  - '^atlascloud/(image-upscaler|photo-cleanup|video-upscaler)'
  - '^byteplus/video/upscaler'
modalities: [IMAGE, VIDEO]
strengths: [high_resolution, identity_preservation, fast, low_cost]
weaknesses: [long_duration]
ideal_tasks: [upscale]
avoid_tasks: [text_to_image, text_to_video, image_edit, video_edit]
unverified_tags: []
sources:
  - url: https://static.atlascloud.ai/model/readme/atlascloud-image-upscaler.md
    title: Atlas Image Upscaler — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/atlascloud-photo-cleanup.md
    title: Atlas Photo Cleanup — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/atlascloud-video-upscaler.md
    title: Atlas Video Upscaler — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/byteplus-video-upscaler.md
    title: BytePlus Video Upscaler — README oficial
    accessed: 2026-10-16
---

## Resumen
Herramientas de posproducción que mejoran un archivo existente sin cambiar su contenido: Atlas Image Upscaler (amplía una imagen ×1–×4), Atlas Photo Cleanup (quita polvo, rayas, ruido y microtextura repetida de una imagen), Atlas Video Upscaler (vídeo a 1080p o 2K) y BytePlus Video Upscaler (vídeo hasta 8K con motor estándar por escena o profesional) [[1]](https://static.atlascloud.ai/model/readme/atlascloud-image-upscaler.md)[[3]](https://static.atlascloud.ai/model/readme/atlascloud-video-upscaler.md)[[4]](https://static.atlascloud.ai/model/readme/byteplus-video-upscaler.md). Ninguna tiene prompt: todo se decide con parámetros.

## Cuándo elegirlo y cuándo no
| Necesidad | Elección |
|---|---|
| Ampliar una imagen ya buena (entrega, impresión) | Atlas Image Upscaler (`outscale` 2–4) |
| Imagen generada con microtextura, «escamas», polvo o ruido | Atlas Photo Cleanup, y después ampliar si hace falta |
| Vídeo corto a 1080p/2K, fuente ≤30 fps | Atlas Video Upscaler (el más simple) |
| Vídeo generado por IA, UGC, serie corta o película antigua, o 4K/8K | BytePlus Video Upscaler (`scene` adecuada) |
| Vídeo real a 4K con plantillas por tipo de material | Tencent Video Upscaler (skill propia) |
| Cambiar contenido, encuadre o estilo | No es para esto: usa un modelo de edición |

## Linaje y variantes en Nexus
- `atlascloud/image-upscaler`: RealESRGAN síncrono; `outscale` 1.0–4.0 [[1]](https://static.atlascloud.ai/model/readme/atlascloud-image-upscaler.md).
- `atlascloud/photo-cleanup`: cadena limpieza de polvo + reducción de ruido, solo imágenes fijas [[2]](https://static.atlascloud.ai/model/readme/atlascloud-photo-cleanup.md).
- `atlascloud/video-upscaler`: `target_resolution` 1080p o 2K (2560×1440); 4K anunciado para más adelante [[3]](https://static.atlascloud.ai/model/readme/atlascloud-video-upscaler.md).
- `byteplus/video/upscaler`: `tool_version` standard (rápido, por escena) o professional (30+ algoritmos, unas 10× más caro); `resolution` 240p–8k o tope de lado corto; interpolación de fps opcional [[4]](https://static.atlascloud.ai/model/readme/byteplus-video-upscaler.md).

## Fortalezas
- **identity_preservation**: no redibujan sujetos ni composición.
- **high_resolution**: de ×4 en imagen a 8K en vídeo (BytePlus).
- **fast / low_cost**: Image Upscaler y Photo Cleanup son síncronos y baratos; el motor estándar de BytePlus está pensado para coste contenido.

## Debilidades
- **long_duration**: Atlas Video Upscaler limita la entrada a 53 s en 1080p y 23 s en 2K, y exige ≤30 fps [[3]](https://static.atlascloud.ai/model/readme/atlascloud-video-upscaler.md).
- Photo Cleanup puede ablandar detalle real o crear halos si el artefacto es fuerte; para defectos graves es mejor regenerar [[2]](https://static.atlascloud.ai/model/readme/atlascloud-photo-cleanup.md).
- Ninguno arregla anatomía, texto mal escrito o errores de contenido.

## Tareas ideales / Tareas que debe evitar
- **Ideales**: `upscale` de imagen o vídeo, limpieza previa a la entrega.
- **Evitar**: `text_to_image`, `text_to_video`, `image_edit`, `video_edit`.

## Estructura de prompt recomendada
No hay prompt. Prepara el encargo sin `prompt` y con:
1. El archivo de entrada (imagen con rol `first_frame`/`reference` según el schema, o vídeo con rol `video`).
2. Los parámetros del schema: `outscale` (imagen), `target_resolution` (Atlas vídeo) o `tool_version` + `scene` + `resolution` (BytePlus).
3. Nada más: no inventes nitidez, estilo ni fps si el usuario no los pide.

## Plantillas por tarea
### Upscale
- Imagen para entrega: Atlas Image Upscaler, `outscale` 2 (o 4 para impresión grande), `output_format` png si se va a seguir editando.
- Imagen generada con textura rara: primero Photo Cleanup; revisa el resultado; después Image Upscaler.
- Clip IA a 1080p barato y rápido: Atlas Video Upscaler `1080p` si dura ≤53 s y va a ≤30 fps.
- Clip IA a 4K: BytePlus `tool_version=standard`, `scene=aigc`, `resolution=4k`; `professional` solo si el usuario acepta el coste (≈10×).
- Película antigua o muy degradada: BytePlus `scene=old_film`.

## Idioma, longitud y prompt negativo
No aplica: sin campo de texto.

## Referencias e imagen inicial
Un único archivo de entrada, accesible por URL pública y estable mientras el proveedor lo descarga (BytePlus lo descarga antes de procesar) [[4]](https://static.atlascloud.ai/model/readme/byteplus-video-upscaler.md).

## Cámara y movimiento
No cambian cámara ni movimiento; la interpolación de fps de BytePlus solo añade fotogramas.

## Consistencia de personaje
Se conserva: no regeneran el contenido.

## Restricciones e incompatibilidades conocidas
- Atlas Video Upscaler: máximo 53 s (1080p) o 23 s (2K) y fuente ≤30 fps [[3]](https://static.atlascloud.ai/model/readme/atlascloud-video-upscaler.md).
- BytePlus: `scene` solo tiene efecto con `tool_version=standard`; `resolution` y el tope de lado corto son excluyentes [[4]](https://static.atlascloud.ai/model/readme/byteplus-video-upscaler.md).
- Photo Cleanup no procesa vídeo [[2]](https://static.atlascloud.ai/model/readme/atlascloud-photo-cleanup.md).

## Errores habituales
- Meter «súbelo a 4K» como prompt: va en parámetros.
- Usar Atlas Video Upscaler con un clip de 60 fps o más largo de lo permitido: falla; usa BytePlus.
- Elegir `professional` por defecto: multiplica el coste; pregúntalo.
- Limpiar con Photo Cleanup una imagen que ya está bien: puede perder detalle.

## Recomendaciones de prompting
- Pregunta el destino (redes, TV, impresión) y elige el nivel mínimo que lo cubre.
- Para vídeo, di duración y fps de la fuente antes de elegir modelo.

## Fuentes y fecha
[1] Atlas Image Upscaler — README oficial — https://static.atlascloud.ai/model/readme/atlascloud-image-upscaler.md — consultado 2026-10-16
[2] Atlas Photo Cleanup — README oficial — https://static.atlascloud.ai/model/readme/atlascloud-photo-cleanup.md — consultado 2026-10-16
[3] Atlas Video Upscaler — README oficial — https://static.atlascloud.ai/model/readme/atlascloud-video-upscaler.md — consultado 2026-10-16
[4] BytePlus Video Upscaler — README oficial — https://static.atlascloud.ai/model/readme/byteplus-video-upscaler.md — consultado 2026-10-16
