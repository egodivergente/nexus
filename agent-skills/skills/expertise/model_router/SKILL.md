---
id: model_router
kind: expertise
title: Enrutador de modelos
version: 2
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]
triggers: [qué modelo, qué generador, recomiéndame, cuál es mejor, generar vídeo, generar imagen, elegir modelo, para este prompt]
---

## Qué hace esta skill
Cómo responder a «¿qué generador me recomiendas para este prompt y por qué?» y a «hazme el prompt para el modelo X». El criterio editorial sale de las skills de familia (compare_model_families, load_skill); la disponibilidad, el schema y el coste salen del registro (recommend_models, get_model_schema, estimate_generation_cost). Nunca afirmes de memoria una capacidad: compruébala.

## Regla de mando
El usuario decide. Si nombra un modelo o una familia, se usa ese (como mucho un aviso de una frase si hay un problema técnico real). Las familias excluidas en ajustes (por defecto Veo) no se proponen salvo que el usuario las pida por su nombre. Si el proyecto activo tiene «modelos preferidos» en su memoria, ponlos primero en la lista corta.

## Procedimiento: «¿qué generador para este prompt?»
1. Lee el prompt y los adjuntos y deduce:
   - modalidad y tarea (texto solo → text_to_*; keyframe → image_to_video; varias fotos de un personaje → reference_to_video o multi_reference_image; vídeo de entrada → video_edit / video_extend / motion_control; fotograma inicial y final → start_end_frame);
   - si hay diálogo, voz, canto o SFX (necesita audio nativo y lipsync);
   - si hay texto legible en la imagen (carteles, logos, rótulos);
   - personajes recurrentes del proyecto (identidad) y cuántos aparecen a la vez;
   - complejidad de cámara y de acción, duración prevista, estilo (fotorreal vs. estilizado), y presupuesto.
2. Haz una lista corta de 2-4 familias con la matriz orientativa de abajo, sin las excluidas.
3. Llama a compare_model_families con esa lista y la tarea, y a recommend_models con el TaskSpec (prioridades = los rasgos que de verdad importan en ESTE prompt, en orden).
4. Elige la variante concreta del registro: la generación más reciente de la familia salvo motivo (coste, una capacidad que solo tiene la anterior). Usa las tiers fast/turbo/mini/lite para pruebas y la pro/max/4K para la toma final.
5. Responde así (breve):
   - **Recomendado:** familia + id de la variante. Por qué: 2-3 razones ligadas a ESTE prompt (no marketing).
   - **Alternativa:** otra familia real y su contrapartida (más barata, mejor en X, peor en Y).
   - Coste estimado y su fiabilidad, si lo tienes.
   - Ofrece escribir el prompt para la elegida.

## Procedimiento: «hazme el prompt para el modelo X»
1. Resuelve la variante exacta (search_models / get_model) y su tarea.
2. load_skill(familia, «Plantillas por tarea») y, si hace falta, «Estructura de prompt» e «Idioma».
3. Escribe el prompt en el idioma y la sintaxis de ESA familia y variante (orden de elementos, longitud, etiquetas de cámara, @referencias, diálogo entre comillas, texto literal entre comillas…). Integra la memoria del proyecto (estilo visual, reglas, descripción estable de personajes) sin contradecir la imagen de referencia.
4. Revísalo con create_generation_prompt y corrige los avisos (duración, ratio y resolución van en parámetros, no en el texto; prompt negativo solo si el schema lo admite).
5. Entrégalo listo para pegar, en un bloque, con el negativo aparte si aplica, y una línea con los parámetros sugeridos.

## Matriz orientativa (confírmala con compare_model_families; las skills de familia mandan)
Vídeo:
- Diálogo, voz o canto con lipsync en la misma pasada: Seedance 2.x, Kling 2.6+/3.0, MiniMax H3, Gemini Omni, Grok Imagine vídeo, LTX con audio. Avatar desde foto + audio propio: skill avatar-lipsync (OmniHuman, Kling avatar, Fabric, InfiniteTalk).
- Identidad de personaje desde varias fotos: Kling O3 reference-to-video, Seedance 2.x reference, Vidu Q2/Q3 reference, Wan 2.7/3.0 reference, MiniMax H3 reference.
- Copiar el movimiento de un vídeo: Kling motion control (2.6/3.0), Wan 2.2 animate, Runway Act-Two (actuación facial y corporal).
- Editar un vídeo existente: Runway Aleph 2, Kling O3 video-edit, Gemini Omni video-edit, Wan 2.7 video-edit, HappyHorse video-edit, Grok Imagine edit, Luma Ray v2v, Seedance vía runway/…/v2v.
- Extender un clip: Seedance 2.5, Gemini Omni, Wan 2.5, LTX, PixVerse v6, FLUX 3, Grok Imagine.
- Fotograma inicial y final: Kling, Vidu, PixVerse, FLUX 3 first-last.
- Cámara compleja escrita en prosa y look cinematográfico: Kling 3.0, Runway Gen-4.5, Seedance 2.x.
- Iterar barato: tiers fast/turbo/mini/lite (Wan, PixVerse, Vidu turbo, LTX fast, Seedance mini, Kling turbo/std).
Imagen:
- Texto legible y diseño gráfico (pósters, logos, rótulos): Ideogram v4, GPT Image 2.x, Nano Banana Pro, Qwen-Image.
- Editar preservando identidad o producto: Nano Banana (2/Pro), GPT Image edit, Seedream 4.x/5 edit, FLUX Kontext, Qwen-Image edit.
- Serie coherente de imágenes (storyboard, variaciones de un personaje): Seedream sequential, Nano Banana multi-referencia.
- Estética artística, «look» de autor: Midjourney v8.x (Youchuan).
- Fotorrealismo general: Seedream 5 Pro, Nano Banana Pro, MAI-Image, FLUX.2 Pro, GPT Image 2.5 [ranking sin verificar: compáralas].
- Separar en capas: Seedream 5 Pro layer-decomposition.

## Errores a evitar
- Recomendar una versión vieja cuando existe una más reciente de la misma familia sin decir por qué.
- Dar 4 alternativas: una buena basta.
- Justificar con adjetivos genéricos («mejor calidad») en vez de con lo que pide el prompt.
- Escribir el prompt antes de cargar la skill de la familia elegida.
- Proponer una familia excluida.
