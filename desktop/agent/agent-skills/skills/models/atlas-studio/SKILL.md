---
id: atlas-studio
kind: model
title: 'Atlas Studio — flujos de anuncio y producto (TVC Maker, Food Motion, Trend Remix, UGC Ad, Virtual Try-On, Product Visuals)'
version: 2
updated: 2026-10-16
match:
  - '^atlascloud/studio/'
modalities: [VIDEO, IMAGE]
strengths: [cinematic_look, identity_preservation]
weaknesses: [long_duration]
ideal_tasks: [reference_to_video, image_to_video]
avoid_tasks: [text_to_video, video_extend]
unverified_tags: [cinematic_look, identity_preservation]
sources:
  - url: https://static.atlascloud.ai/model/schema/atlascloud-studio-tvc-maker.json
    title: Atlas Studio TVC Maker — schema y descripción del catálogo
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/schema/atlascloud-studio-trend-remix.json
    title: Atlas Studio Trend Remix — schema y descripción del catálogo
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/schema/atlascloud-studio-product-visuals.json
    title: Atlas Studio Product Visuals — schema y descripción del catálogo
    accessed: 2026-10-16
---

## Resumen
Plantillas de flujo de Atlas que convierten una foto de producto (y opcionalmente un modelo o un vídeo de referencia) en piezas de marketing completas. Planifican el guion o las tomas y renderizan con Seedance 2.5 (vídeo) o como fotos de estudio (Product Visuals). Se paga por duración o por imagen; según el catálogo, una ejecución fallida no se cobra [sin verificar]. No hay README público: esta guía sale del schema y la descripción del catálogo.

## Cuándo elegirlo y cuándo no
| Necesidad | Plantilla |
|---|---|
| Anuncio de marca premium desde una foto y un brief | `tvc-maker` |
| Comida o bebida con vertidos, vapor, crujidos | `food-motion` |
| Rehacer un vídeo viral con tu producto (estructura y ritmo, no copia) | `trend-remix` (requiere vídeo de referencia 4–20 s) |
| Anuncio tipo creador con voz y lipsync | `ugc-ad` (`ad_type`: recomendación, antes/después, unboxing) |
| Prenda o accesorio puesto en un modelo | `virtual-try-on` |
| Fotos de producto de estudio (hero, detalle, póster) | `product-visuals` (`visual_type`) |
| Control fino plano a plano | Seedance o Kling directamente |

## Linaje y variantes en Nexus
Todas comparten `product_image` (una foto que fija la identidad del producto), `user_prompt` (marca, puntos fuertes, público y tono) y, en vídeo, `duration`, `ratio`, `resolution` (480p/720p/1080p nativos o -esr con superresolución posterior, más cara), `generate_audio` y `count` (1–2 propuestas, el precio se multiplica). `model_image` es opcional: si falta, el flujo crea un modelo virtual [[1]](https://static.atlascloud.ai/model/schema/atlascloud-studio-tvc-maker.json)[[2]](https://static.atlascloud.ai/model/schema/atlascloud-studio-trend-remix.json).

## Fortalezas
- **identity_preservation** [sin verificar]: el producto se fija con su foto; envase, material y textos salen de ella.
- **cinematic_look** [sin verificar]: el flujo planifica concepto, guion y fotogramas clave.

## Debilidades
- **long_duration**: piezas cortas (unos segundos a ~20 s según plantilla).
- Menos control plano a plano que un modelo de vídeo directo.

## Tareas ideales / Tareas que debe evitar
- **Ideales**: `reference_to_video` e `image_to_video` comerciales a partir de una foto de producto.
- **Evitar**: escenas narrativas sin producto, `video_extend`.

## Estructura de prompt recomendada
`user_prompt` = marca + producto + 2–3 beneficios + público + tono + llamada a la acción. Ej.: «Cerveza artesana Barrica: tostada, intensa, para cenas con amigos; tono cálido y nocturno en Madrid; cierre con el logo». Lo técnico (duración, formato, resolución, audio) va en parámetros.

## Plantillas por tarea
### Reference-to-video
Trend Remix: `reference_video` con un clip que ya funciona; `duration` vacío para igualar su duración; `ratio=9:16` para redes.
### Image-to-video
TVC Maker o Food Motion: `product_image` limpia de frente, `duration=10`, `resolution=720p` para validar y 1080p para la final.

## Idioma, longitud y prompt negativo
`user_prompt` en el idioma del público objetivo; sin prompt negativo.

## Referencias e imagen inicial
Foto del producto nítida, completa y con etiqueta legible. Para Try-On, la prenda sola sobre fondo neutro.

## Cámara y movimiento
Lo decide el flujo; en Trend Remix se toma del vídeo de referencia.

## Consistencia de personaje
Pasa `model_image` para mantener al mismo modelo en varias piezas.

## Audio y diálogo
`generate_audio=yes` añade sonido ambiente y música ligera; UGC Ad genera además la voz del creador.

## Restricciones e incompatibilidades conocidas
- Trend Remix: vídeo de referencia de 4–20 s, ≤20 MB, mp4/mov [[2]](https://static.atlascloud.ai/model/schema/atlascloud-studio-trend-remix.json).
- Product Visuals: `resolution` 1k/2k/4k; algunas proporciones no tienen nivel intermedio [[3]](https://static.atlascloud.ai/model/schema/atlascloud-studio-product-visuals.json).

## Errores habituales
- `count=2` sin avisar: duplica el coste.
- Subir una foto de producto con varios artículos: mezcla identidades.
- Usar -esr de 4K para una prueba: es la opción más cara.

## Recomendaciones de prompting
- Valida a 720p y una propuesta; sube resolución y propuestas al aprobar.
- Da beneficios concretos, no adjetivos genéricos.

## Fuentes y fecha
[1] Atlas Studio TVC Maker — schema y descripción del catálogo — https://static.atlascloud.ai/model/schema/atlascloud-studio-tvc-maker.json — consultado 2026-10-16
[2] Atlas Studio Trend Remix — schema y descripción del catálogo — https://static.atlascloud.ai/model/schema/atlascloud-studio-trend-remix.json — consultado 2026-10-16
[3] Atlas Studio Product Visuals — schema y descripción del catálogo — https://static.atlascloud.ai/model/schema/atlascloud-studio-product-visuals.json — consultado 2026-10-16
