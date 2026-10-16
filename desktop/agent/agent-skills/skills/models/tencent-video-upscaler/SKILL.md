---
id: tencent-video-upscaler
kind: model
title: Tencent Video Upscaler (MPS)
version: 2
updated: 2026-10-16
match:
  - '^tencent/video/upscaler'
modalities: [VIDEO]
strengths: [high_resolution, identity_preservation]
weaknesses: [long_duration]
ideal_tasks: [upscale]
avoid_tasks: [text_to_video, image_to_video, video_edit]
unverified_tags: []
sources:
  - url: https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md
    title: Tencent Video Upscaler — README oficial en Atlas Cloud
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/schema/tencent-video-upscaler.json
    title: Tencent Video Upscaler — schema Input publicado
    accessed: 2026-10-16
---

## Resumen
Mejora y sube de resolución un vídeo existente (hasta 4K) con la super-resolución de Tencent Cloud MPS, sin cambiar su contenido [[1]](https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md). No genera ni edita: recibe un vídeo y una plantilla y devuelve el mismo plano con más detalle. NO tiene campo de prompt: toda la configuración va en parámetros.

## Cuándo elegirlo y cuándo no
| Necesidad | Elección |
|---|---|
| «Haz upscale de este vídeo a 4K con Tencent» | Este modelo, plantilla de nivel 4K de la escena adecuada |
| Entregar en 1080p/2K/4K un clip ya generado en otro modelo | Este modelo con el nivel de destino |
| Vídeo antiguo, comprimido o con ruido | Plantilla de restauración del nivel deseado |
| Personas pequeñas o lejanas en plano | Plantilla de optimización de caras pequeñas |
| Crear o cambiar contenido, movimiento o estilo | Otro modelo (no es generativo) |
| Una imagen fija | Tencent Image Upscaler (skill «otros-imagen») |

## Linaje y variantes en Nexus
Un único id, `tencent/video/upscaler`, con familias de plantilla por tipo de escena (imagen real, anime/cómic, restauración, caras pequeñas y una ajustada a salidas de Seedance 2) y cuatro niveles de salida: 720P, 1080P, 2K y 4K [[1]](https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md). Las plantillas exactas salen del schema real (`get_model_schema`); no las inventes.

## Fortalezas
- **high_resolution**: lleva un clip a 1080P, 2K o 4K manteniendo el contenido [[1]](https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md).
- **identity_preservation**: no redibuja personajes ni composición; solo detalle, nitidez y limpieza.
- Plantillas especializadas por tipo de material (real, animación, restauración, caras pequeñas).

## Debilidades
- **long_duration**: cobra por segundo del vídeo de entrada y por nivel; los clips largos en 4K encarecen mucho. Muestra siempre el coste de la tarjeta.
- No corrige encuadre, movimiento ni errores de contenido del original.
- Necesita una URL de vídeo accesible y estable mientras Tencent la descarga.

## Tareas ideales / Tareas que debe evitar
- **Ideales**: `upscale` de vídeo real o animado, restauración de material antiguo, entrega final en mayor resolución.
- **Evitar**: `text_to_video`, `image_to_video`, `video_edit`: no genera ni modifica el contenido.

## Estructura de prompt recomendada
No hay prompt. `prepare_generation` se llama SIN `prompt` y con:
1. `media`: el vídeo original con rol `video` (attachment_id o asset_id del índice).
2. `params.template`: familia según el contenido + nivel de destino (el sufijo del nivel decide resolución y tarifa).
3. Opcional `params.fps` solo si el usuario pide interpolar (multiplica la tarifa a partir de 30 y de 60 fps) [[1]](https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md).
4. Opcional `params.short` (lado corto en píxeles) solo si el usuario pide un tamaño concreto, y siempre dentro del rango del nivel elegido; si no, omítelo.
Nexus añade solo el campo `resolution` que exige el backend, deducido del nivel de la plantilla: no lo pases tú.

## Plantillas por tarea
### Upscale
- «Súbelo a 4K» con imagen real: `template` de la familia live-action en nivel 4K.
- Anime, dibujo o cómic: familia anime en el nivel pedido.
- Grabación antigua, ruido o compresión: familia restore en el nivel pedido.
- Personas lejanas o caras pequeñas: familia smallface del tipo de material.
- Clip generado con Seedance 2: familia seedance2-enhance.
- Si el usuario no dice el nivel, pregunta o usa el que pidió para la entrega; no subas a 4K por defecto sin mostrar el coste.

## Idioma, longitud y prompt negativo
No aplica: el schema no tiene `prompt` ni `negative_prompt`. Nunca metas parámetros de API en texto libre.

## Referencias e imagen inicial
La única entrada es el vídeo original (`video_url`). Conserva el original: el resultado es un archivo nuevo.

## Cámara y movimiento
No modifica la cámara ni el movimiento; la interpolación de fps solo suaviza.

## Consistencia de personaje
Se conserva porque no regenera el contenido.

## Restricciones e incompatibilidades conocidas
- El valor por defecto `short=0` no es válido con los niveles 1080P, 2K y 4K: Nexus lo omite y el proveedor usa el tamaño de la plantilla [[2]](https://static.atlascloud.ai/model/schema/tencent-video-upscaler.json).
- El backend de Atlas rechazó envíos sin `resolution` («resolution is required (720p, 1080p, 1440p or 4k)», observado en Nexus en octubre de 2026) aunque el schema no lo declara; Nexus lo deduce de la plantilla (2K = 1440p).

## Errores habituales
- Escribir «upscale a 4K» como prompt: el modelo no tiene prompt; va en `template`.
- Pedir `short` fuera del rango del nivel (p. ej. 1080 con una plantilla 4K): Nexus lo rechaza antes de enviar.
- Crear otra tarjeta para corregir: usa `update_plan(order_id)` sobre el mismo encargo.
- Decir que está «enviado» o «generando» antes de que el usuario pulse Generar y el encargo esté en proceso.

## Recomendaciones de prompting
- Elige la familia de plantilla por el tipo de material antes que por el nivel.
- Indica el coste por segundo del nivel y la duración del clip; si es largo, sugiere un nivel menor o recortar antes.

## Fuentes y fecha
[1] Tencent Video Upscaler — README oficial en Atlas Cloud — https://static.atlascloud.ai/model/readme/tencent-video-upscaler.md — consultado 2026-10-16
[2] Tencent Video Upscaler — schema Input publicado — https://static.atlascloud.ai/model/schema/tencent-video-upscaler.json — consultado 2026-10-16
