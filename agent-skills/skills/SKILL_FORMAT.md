# Formato de skills de Nexus Agent (v2)

Cada skill es una carpeta con un `SKILL.md`: cabecera YAML (datos que lee el router) + cuerpo Markdown (lo que lee el LLM cuando la skill se carga). Se actualizan sin recompilar: la app usa la copia empaquetada en assets y, si existe, la copia más reciente en `files/skills/` (bundle descargado o copiado por ADB).

## Regla de oro
Una skill NO repite datos que Nexus ya conoce en vivo: parámetros, opciones, duraciones, resoluciones, campos de medios y precios salen SIEMPRE del registro y del schema real (`get_model_schema`, `estimate_generation_cost`). La skill aporta criterio editorial: cuándo elegir el modelo, cómo escribirle el prompt, qué evitar.
Si una afirmación no tiene fuente oficial o contrastable, va marcada como `[sin verificar]` en el cuerpo y su etiqueta va en `unverified_tags`.

## Cabecera de skill de modelo (`skills/models/<familia>/SKILL.md`)
```yaml
---
id: kling                      # único, minúsculas
kind: model
title: Kling (Kuaishou)
version: 1                     # se incrementa en cada cambio
updated: 2026-10-02
match:                         # regex sobre el id del registro de Nexus
  - '^kwaivgi/kling'
  - '^crun/kling'
modalities: [VIDEO]
strengths: [face_consistency, camera_control]      # vocabulario controlado (abajo)
weaknesses: [long_duration]
ideal_tasks: [image_to_video, reference_to_video]
avoid_tasks: [lipsync]
unverified_tags: [camera_control]                  # subconjunto sin fuente firme
sources:
  - url: https://...
    title: ...
    accessed: 2026-10-02
---
```

## Cuerpo de skill de modelo (v2: secciones `##`, en español, en este orden)
1. Resumen (2-4 frases; la primera línea es la del índice)
2. Cuándo elegirlo y cuándo no (frente a hermanos y competidores de Nexus; tabla necesidad → variante) — lo usa `compare_model_families`
3. Linaje y variantes en Nexus (generaciones, qué cambió, mapa de ids → generación/tier; cuál es la más reciente)
4. Fortalezas
5. Debilidades
6. Tareas ideales / Tareas que debe evitar
7. Estructura de prompt recomendada (orden, longitud, idioma, sintaxis especial, plantilla)
8. Plantillas por tarea (un `###` por tarea: Text-to-video, Image-to-video, Reference-to-video, Start/end frame, Motion control, Video edit, Video extend, Text-to-image, Image edit, Multi-referencia, Avatar/lipsync…; plantilla + ejemplos listos para pegar) — `create_generation_prompt` devuelve la del `task`
9. Idioma, longitud y prompt negativo
10. Referencias e imagen inicial
11. Cámara y movimiento
12. Consistencia de personaje
13. Audio y diálogo (si aplica)
14. Restricciones e incompatibilidades conocidas
15. Errores habituales
16. Recomendaciones de prompting
17. Fuentes y fecha

Citas inline `[[n]](url)`; sin precios ni listas cerradas de opciones del API. Tamaño orientativo 9-16 KB (load_skill recorta a 24 KB; se puede pedir una sección).

## Cabecera de skill transversal (`skills/expertise/<id>/SKILL.md`)
```yaml
---
id: prompt_engineer
kind: expertise
title: Ingeniero de prompts
version: 1
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]     # modalidades
triggers: [prompt, reescribir]  # palabras que sugieren cargarla
---
```

## Vocabulario controlado
Tareas: text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, motion_control, lipsync, avatar, text_to_image, image_edit, multi_reference_image, upscale, layer_decomposition
Rasgos: face_consistency, identity_preservation, subtle_performance, natural_motion, dynamic_action, camera_control, cinematic_look, prompt_adherence, text_rendering, photorealism, stylized, native_audio, multi_shot, long_duration, fast, low_cost, high_resolution, physics, multi_character
