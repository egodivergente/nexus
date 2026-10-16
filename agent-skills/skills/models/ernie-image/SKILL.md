---
id: ernie-image
kind: model
title: Baidu ERNIE Image Turbo
version: 2
updated: 2026-10-16
match:
  - '^baidu/ernie'
modalities: [IMAGE]
strengths: [fast, low_cost]
weaknesses: []
ideal_tasks: [text_to_image]
avoid_tasks: [image_edit, multi_reference_image]
unverified_tags: [fast, low_cost]
sources:
  - url: https://static.atlascloud.ai/model/schema/baidu-ERNIE-Image-Turbo-text-to-image.json
    title: Baidu ERNIE Image Turbo — schema y descripción del catálogo
    accessed: 2026-10-16
---

## Resumen
Versión rápida y de baja latencia de ERNIE Image (Baidu) para texto a imagen, pensada para iterar y para volumen [sin verificar: descripción del catálogo]. Pocos pasos por defecto (8) y `guidance_scale` bajo; tamaños fijos de hasta 1376×768 [[1]](https://static.atlascloud.ai/model/schema/baidu-ERNIE-Image-Turbo-text-to-image.json).

## Cuándo elegirlo y cuándo no
| Necesidad | Elección |
|---|---|
| Muchas ideas rápidas para elegir dirección | ERNIE Image Turbo |
| Calidad final, texto legible o edición | Seedream, GPT Image, FLUX o Qwen-Image |

## Linaje y variantes en Nexus
Solo `baidu/ERNIE-Image-Turbo/text-to-image`. `size` entre 1024×1024 y formatos horizontales/verticales (1376×768, 768×1376…), `n`, `seed`, `use_pe` (mejora de prompt, activada por defecto) [[1]](https://static.atlascloud.ai/model/schema/baidu-ERNIE-Image-Turbo-text-to-image.json).

## Fortalezas
- **fast / low_cost** [sin verificar]: variante Turbo de pocos pasos.

## Debilidades
- Resolución máxima limitada (lado mayor 1376 px).

## Tareas ideales / Tareas que debe evitar
- **Ideales**: `text_to_image` de exploración.
- **Evitar**: `image_edit`, `multi_reference_image` (no admite imágenes de entrada).

## Estructura de prompt recomendada
Sujeto + estilo + luz + composición en una o dos frases. Con `use_pe=true` el modelo amplía el prompt; desactívalo si necesitas que respete tu texto literal.

## Plantillas por tarea
### Text-to-image
«Retrato editorial de una bailarina en una azotea de Madrid al amanecer, luz dorada lateral, 35 mm, colores suaves». `size` 1376×768 para horizontal.

## Idioma, longitud y prompt negativo
Sin prompt negativo en el schema; expresa las exclusiones en positivo.

## Referencias e imagen inicial
No admite imágenes de entrada.

## Cámara y movimiento
No aplica.

## Consistencia de personaje
Limitada: fija la semilla y repite la descripción.

## Restricciones e incompatibilidades conocidas
Solo los tamaños de la lista; `n` multiplica el coste.

## Errores habituales
- Esperar texto legible o logotipos exactos.
- Subir `num_inference_steps` mucho: pierde la ventaja de velocidad.

## Recomendaciones de prompting
Úsalo para explorar y pasa la idea elegida a un modelo de calidad final.

## Fuentes y fecha
[1] Baidu ERNIE Image Turbo — schema y descripción del catálogo — https://static.atlascloud.ai/model/schema/baidu-ERNIE-Image-Turbo-text-to-image.json — consultado 2026-10-16
