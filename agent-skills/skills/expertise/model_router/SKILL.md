---
id: model_router
kind: expertise
title: Enrutador de modelos
version: 4
updated: 2026-10-24
applies_to: [VIDEO, IMAGE]
triggers: [qué modelo, qué generador, recomiéndame, cuál es mejor, generar vídeo, generar imagen, elegir modelo, para este prompt]
---

## Qué hace esta skill
Cómo responder a «¿qué generador me recomiendas para este prompt y por qué?» y a «hazme el prompt para el modelo X». El criterio editorial sale de las skills de familia (compare_model_families, load_skill); la disponibilidad, el schema y el coste salen del registro (recommend_models, get_model_schema, estimate_generation_cost). Nunca afirmes de memoria una capacidad: compruébala.

## Regla de mando
Calidad y adecuación al encargo primero; rapidez irrelevante, precio secundario. `recommend_models` aplica el modo experto: descarta contratos no verificables o requisitos incompatibles, sin ajustar silenciosamente duración ni resolución. Solo consulta modelos visibles del catálogo del usuario, nunca una lista adicional de Flow. No pidas puntuaciones al usuario ni presentes el score interno como una nota de calidad. Compara costes efectivos únicamente cuando tengan fuente, fecha y configuración equivalentes; tarifa base o desconocida no demuestra ahorro. Separa claramente evidencia documental de criterio editorial.
El usuario decide. Si nombra un modelo o una familia, se usa ese (como mucho un aviso de una frase si hay un problema técnico real). Las familias excluidas en ajustes (por defecto Veo) no se proponen salvo que el usuario las pida por su nombre. Si el proyecto activo tiene «modelos preferidos» en su memoria, ponlos primero en la lista corta.

## Procedimiento: «¿qué generador para este prompt?»
1. Lee el prompt y los adjuntos y deduce:
   - modalidad y tarea (texto solo → text_to_*; keyframe → image_to_video; varias fotos de un personaje → reference_to_video o multi_reference_image; vídeo de entrada → video_edit / video_extend / motion_control; fotograma inicial y final → start_end_frame);
   - si hay diálogo, voz, canto o SFX (necesita audio nativo y lipsync);
   - si hay texto legible en la imagen (carteles, logos, rótulos);
   - personajes recurrentes del proyecto (identidad) y cuántos aparecen a la vez;
   - complejidad de cámara y de acción, duración prevista, estilo (fotorreal vs. estilizado), y presupuesto.
2. Haz una lista corta de 2-4 familias con documentación actual verificable de abajo, sin las excluidas.
3. Llama a compare_model_families con esa lista y la tarea, y a recommend_models con el TaskSpec (prioridades = los rasgos que de verdad importan en ESTE prompt, en orden).
4. Elige la variante concreta con evidencia para la tarea. No deduzcas calidad, velocidad o compatibilidad del nombre fast/pro/max ni de que sea la versión más reciente: verifica capacidades, coste y comparativas disponibles.
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

## Investigación obligatoria por tarea
No uses una matriz estática de ganadores. Las familias, versiones y prestaciones cambian.

1. Extrae requisitos verificables: duración, resolución, audio, fotogramas inicial/final, número de referencias, continuidad, edición o movimiento. Separa requisitos imprescindibles de preferencias.
2. Obtén candidatos del catálogo real y verifica el schema de cada variante. Anota el ID exacto y proveedor: dos endpoints de una familia pueden admitir cosas distintas.
3. Usa web_search con el nombre exacto de la variante y la capacidad concreta. Abre la documentación oficial o notas de lanzamiento con read_web_page. Una descripción del buscador por sí sola no basta.
4. Por cada afirmación decisiva conserva URL, fecha de consulta, versión y evidencia que la respalda. La documentación demuestra soporte; no demuestra que sea mejor que un rival.
5. Para afirmar superioridad visual exige una evaluación comparable (misma tarea, referencias, parámetros y versiones). Si no existe, presenta la elección como candidata compatible con tus requisitos, no como hecho probado.
6. Comprueba el coste en el esquema/configuración real, distinguiendo estimación y tarifa. No conviertas una unidad desconocida a segundos.
7. Entrega recomendación breve con razón concreta, alternativa y enlaces. Si la documentación o el buscador fallan, explica el dato que queda sin verificar y busca otra fuente primaria.

## Ejemplos de preguntas que requieren pruebas
- «Mejor para dos personajes abrazándose»: comprobar múltiples referencias/identidad y evidencia de interacción corporal; no confundir resolución con consistencia.
- «Mejor para doblaje»: comprobar vídeo+audio de entrada, sincronía labial, idiomas y duración. Audio nativo no implica doblaje de un vídeo existente.
- «Mejor para editar un plano»: comprobar endpoint video-to-video/edit, conservación de duración y controles; text-to-video no demuestra edición.
