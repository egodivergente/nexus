---
id: modelos-3d
kind: model
title: Modelos 3D (HI3D, Hunyuan3D, Meshy 7, Tripo H3.1, Seed3D 2.0)
version: 2
updated: 2026-10-16
match:
  - '^hi3d/'
  - '^tencent/hunyuan3d'
  - '^meshy-v7/'
  - '^tripo-h'
  - '^bytedance/seed3d'
modalities: [IMAGE]
strengths: [high_resolution, fast]
weaknesses: []
ideal_tasks: []
avoid_tasks: [text_to_video, image_to_video, image_edit]
unverified_tags: []
sources:
  - url: https://static.atlascloud.ai/model/readme/hi3d-v3.0-quality-image-to-3d.md
    title: HI3D v3.0 Quality — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/meshy-v7-multi-image-to-3d.md
    title: Meshy 7 Multi-Image-to-3D — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/meshy-v7-text-to-3d.md
    title: Meshy 7 Text-to-3D — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/tripo-h3.1-image-to-3d.md
    title: Tripo H3.1 Image-to-3D — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/tencent-hunyuan3d-pro-image-to-3d.md
    title: Hunyuan 3D — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/schema/bytedance-seed3d-v2.0-image-to-3d.json
    title: Seed3D 2.0 — schema publicado
    accessed: 2026-10-16
---

## Resumen
Generan modelos 3D texturizados (GLB/OBJ/FBX/USDZ/STL según el modelo) a partir de una foto, varias vistas o un texto. HI3D (cuatro niveles, hasta 4 vistas), Hunyuan3D (Rapid y Pro, imagen o texto), Meshy 7 (imagen, multi-imagen o texto; texturas PBR hasta 8K), Tripo H3.1 (imagen o texto, malla quad opcional) y Seed3D 2.0 (imagen, entrega un .zip) [[1]](https://static.atlascloud.ai/model/readme/hi3d-v3.0-quality-image-to-3d.md)[[2]](https://static.atlascloud.ai/model/readme/meshy-v7-multi-image-to-3d.md)[[4]](https://static.atlascloud.ai/model/readme/tripo-h3.1-image-to-3d.md)[[5]](https://static.atlascloud.ai/model/readme/tencent-hunyuan3d-pro-image-to-3d.md).

## Cuándo elegirlo y cuándo no
| Necesidad | Elección |
|---|---|
| Objeto real con fotos de varios lados | HI3D (hasta 4 vistas) o Meshy 7 Multi-Image |
| Piezas finas y aristas duras (asas, correas, mecánica) | HI3D v3.0 Quality (2048³) |
| Activo final para impresión o primer plano | HI3D v3.0 Master (presupuesto de 5M caras; lento) |
| Prototipo rápido desde texto | Meshy 7 Text-to-3D en `preview`, o Hunyuan3D Rapid |
| Personaje para animar (A/T-pose) | Meshy 7 Text-to-3D |
| Topología quad para DCC | Tripo H3.1 con `quad=true` (sale FBX) |
| Una sola foto, salida sencilla | Seed3D 2.0 o Tripo H3.1 |

## Linaje y variantes en Nexus
- HI3D: v2.1 Fast y Pro (1536³), v3.0 Quality (2048³) y Master (2048³, 5M caras). Parámetros comunes: `reference_images` (hasta 3 extra), `multi_images_bit`, `should_texture`, `enable_pbr`, `shading`, `rmbg`, `target_polycount` [[1]](https://static.atlascloud.ai/model/readme/hi3d-v3.0-quality-image-to-3d.md).
- Hunyuan3D: Rapid (destilado, iteración) y Pro (texto ≤1.024 caracteres o una imagen 128–5.000 px, ≤6 MB) [[5]](https://static.atlascloud.ai/model/readme/tencent-hunyuan3d-pro-image-to-3d.md).
- Meshy 7: image-to-3d, multi-image-to-3d (la primera imagen es la frontal) y text-to-3d (`mode` full o preview); `ultra_mode`, `texture_prompt`, `texture_resolution` 2k/4k/8k [[2]](https://static.atlascloud.ai/model/readme/meshy-v7-multi-image-to-3d.md)[[3]](https://static.atlascloud.ai/model/readme/meshy-v7-text-to-3d.md).
- Tripo H3.1: `texture_quality`/`geometry_quality` standard o detailed, `face_limit`, `auto_size` en metros, `quad` [[4]](https://static.atlascloud.ai/model/readme/tripo-h3.1-image-to-3d.md).
- Seed3D 2.0: `subdivision_level` high/medium/low (1M/500k/100k caras), `file_format` glb/obj/usd/usdz [[6]](https://static.atlascloud.ai/model/schema/bytedance-seed3d-v2.0-image-to-3d.json).

## Fortalezas
- **high_resolution**: HI3D 2048³ y Meshy hasta 8K de textura.
- **fast**: HI3D v2.1 Fast, Hunyuan3D Rapid y Meshy `preview` para iterar.

## Debilidades
- Con una sola foto, la espalda y los lados se inventan: pide más vistas si importan.
- Fondos recargados o sujetos recortados empeoran la reconstrucción.

## Tareas ideales / Tareas que debe evitar
- **Ideales**: producto, prop, personaje estático o mueble para 3D, AR o impresión.
- **Evitar**: vídeo o edición de imagen.

## Estructura de prompt recomendada
- Imagen a 3D: sin prompt (salvo `texture_prompt` en Meshy). Foto nítida, sujeto centrado y completo, fondo limpio, luz uniforme.
- Texto a 3D: objeto + forma + material + estilo + pose si es personaje, en una frase (≤800 en Meshy, ≤1.024 en Tripo/Hunyuan). Ej.: «Silla de cafetería de hierro forjado negro, asiento de madera de roble, estilo parisino, tamaño real».

## Plantillas por tarea
### Foto de producto a 3D
HI3D v3.0 Quality con la frontal en `image` y hasta 3 vistas en `reference_images`; `enable_pbr=true`; GLB.
### Prototipo desde texto
Meshy 7 Text-to-3D `mode=preview` para validar la silueta; repetir en `full` cuando el usuario apruebe.
### Personaje para animar
Meshy 7 Text-to-3D en A-pose o T-pose; topología `quad` con remallado si va a un motor.

## Idioma, longitud y prompt negativo
Escribe el prompt en inglés si el modelo lo recomienda; Hunyuan3D Pro admite inglés o chino. Tripo text-to-3d tiene `negative_prompt`.

## Referencias e imagen inicial
- HI3D: `multi_images_bit` indica qué vistas (frontal, trasera, izquierda, derecha) ocupan los huecos.
- Meshy multi-image: 1–4 vistas del MISMO objeto; la primera es la frontal [[2]](https://static.atlascloud.ai/model/readme/meshy-v7-multi-image-to-3d.md).
- Seed3D: proporción entre 0,4 y 2,5, ≤10 MB [[6]](https://static.atlascloud.ai/model/schema/bytedance-seed3d-v2.0-image-to-3d.json).

## Cámara y movimiento
No aplica.

## Consistencia de personaje
Para varias poses, genera una vez y riga en DCC en lugar de regenerar.

## Restricciones e incompatibilidades conocidas
- HI3D: `enable_pbr` y `shading` requieren `should_texture=true` [[1]](https://static.atlascloud.ai/model/readme/hi3d-v3.0-quality-image-to-3d.md).
- Meshy: `topology` y `target_polycount` requieren `should_remesh=true` [[3]](https://static.atlascloud.ai/model/readme/meshy-v7-text-to-3d.md).
- Seed3D entrega un .zip con el archivo dentro.

## Errores habituales
- Subir una foto con varios objetos: reconstruye todo junto.
- Pedir HI3D Master de entrada: es el más lento; valida antes con Quality o Fast.
- Activar `quad` en Tripo esperando GLB: sale FBX.

## Recomendaciones de prompting
- Pregunta el destino (impresión, juego, AR) para fijar polígonos y formato.
- Valida la silueta barata antes de texturizar en alta.

## Fuentes y fecha
[1] HI3D v3.0 Quality — README oficial — https://static.atlascloud.ai/model/readme/hi3d-v3.0-quality-image-to-3d.md — consultado 2026-10-16
[2] Meshy 7 Multi-Image-to-3D — README oficial — https://static.atlascloud.ai/model/readme/meshy-v7-multi-image-to-3d.md — consultado 2026-10-16
[3] Meshy 7 Text-to-3D — README oficial — https://static.atlascloud.ai/model/readme/meshy-v7-text-to-3d.md — consultado 2026-10-16
[4] Tripo H3.1 Image-to-3D — README oficial — https://static.atlascloud.ai/model/readme/tripo-h3.1-image-to-3d.md — consultado 2026-10-16
[5] Hunyuan 3D — README oficial — https://static.atlascloud.ai/model/readme/tencent-hunyuan3d-pro-image-to-3d.md — consultado 2026-10-16
[6] Seed3D 2.0 — schema publicado — https://static.atlascloud.ai/model/schema/bytedance-seed3d-v2.0-image-to-3d.json — consultado 2026-10-16
