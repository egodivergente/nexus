---
id: output_quality
kind: expertise
title: Revisión visual y upscale
version: 1
updated: 2026-10-16
applies_to: [IMAGE, VIDEO]
triggers: [upscale, ampliar imagen, mejorar calidad, glitch, artefactos, borroso, nitidez, revisar resultado]
---

Revisa el archivo real y propone una corrección concreta, conservando el original.

## Revisión
1. Localiza el asset exacto. Usa inspect_image_quality para imagen: entrega medidas locales y contenido visual en un paso. Revisa encuadre e intención del usuario antes de calificar un defecto.
2. Examina identidad y anatomía, manos, duplicados, uniones imposibles, bordes, texto, halos y continuidad de ropa/luz. Diferencia un defecto observado de una sospecha; indica su zona. El bokeh o una textura suave no son automáticamente errores.
3. Resolución, luminancia y varianza de bordes son medidas, no un veredicto. No inventes puntuaciones ni un umbral universal para detectar glitches.
4. En vídeo usa una muestra de fotogramas; si hay un instante sospechoso extrae fotogramas alrededor de ese tiempo. Una muestra no acredita continuidad perfecta ni ausencia de parpadeos en todo el clip.

## Corrección y upscale
- Baja resolución con contenido correcto: search_models/recommend_models para tarea upscale e IMAGE; get_model_schema; prepare_image_upscale con el asset y parámetros reales. No inventes factores admitidos.
- Identidad, manos, texto o composición incorrectos: propone edición localizada o regeneración con referencias; el upscale puede ampliar el defecto.
- Para conservar detalles, prioriza un modo fiel si el schema lo documenta. Un modo creativo puede introducir cambios: úsalo cuando lo pida el usuario.
- No presentes un redimensionado local como superresolución IA. No declares mejora hasta disponer del resultado.

## Rapidez y memoria
Carga una miniatura y solo las referencias relevantes. Reutiliza schema y lectura visual durante el encargo. Conserva decisiones confirmadas en update_project_memory; consulta get_project_overview y search_project_memory al retomar el proyecto. No lances evaluaciones repetidas ni revisiones en segundo plano.
