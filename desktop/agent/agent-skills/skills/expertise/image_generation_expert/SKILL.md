---
id: image_generation_expert
kind: expertise
title: Experto en generación de imagen
version: 1
updated: 2026-10-02
applies_to: [IMAGE]
triggers: [generar imagen, editar imagen, carátula, póster, imagen de personaje]
---

## Qué hace esta skill
Guía el flujo de trabajo para tareas de imagen (text_to_image, image_edit, multi_reference_image, layer_decomposition, upscale), coordinando con `model_router` para la elección de modelo y con `prompt_engineer`/`character_consistency` para la redacción del prompt. No afirma capacidades de un modelo concreto: eso lo confirma `get_model_schema` y la skill de ese modelo.

## Procedimiento
1. Clasifica la tarea dentro del vocabulario controlado: ¿es generación desde cero (`text_to_image`), edición de una imagen existente (`image_edit`), combinación de varias referencias (`multi_reference_image`), separación en capas (`layer_decomposition`) o ampliación de resolución (`upscale`)?
2. Pasa esa clasificación al `model_router` junto con el nº de imágenes de referencia disponibles (`imageInputs`) y las prioridades relevantes (`text_rendering` si hay texto en la imagen, `identity_preservation` si hay personaje recurrente, `photorealism` o `stylized` según el objetivo).
3. Carga la skill del modelo recomendado y usa su plantilla de prompting específica (texto-a-imagen o edición), no una genérica.
4. Para edición, sigue el patrón "instrucción concreta + qué debe mantenerse igual" (ver `prompt_engineer` y la skill del modelo): nunca reescribas la imagen entera cuando el objetivo es un cambio puntual.
5. Si la imagen final servirá como referencia para vídeo (i2v) o para mantener continuidad entre escenas, guarda su metadato en el índice de assets (ver `asset_librarian`) antes de seguir.
6. Si hay texto legible en la imagen (carátulas, títulos), prioriza modelos cuya skill mencione `text_rendering` como fortaleza, y especifica tipografía/disposición en el prompt.

## Checklist
- ¿La tarea está bien clasificada en el vocabulario controlado?
- ¿Se consultó `recommend_models` en vez de elegir el modelo a mano?
- ¿El prompt sigue la plantilla específica de la skill del modelo?
- ¿Se registró la imagen resultante en el índice de assets si va a reutilizarse?

## Errores a evitar
- Tratar una edición puntual como una regeneración completa desde cero.
- No verificar `get_model_schema` antes de asumir cuántas imágenes de referencia acepta una variante.
- Olvidar registrar en el índice de assets una imagen que luego se usará como referencia para vídeo.

## Ejemplo breve
Petición: "cambia el fondo de esta foto de Mateo a un escenario de concierto". Es `image_edit`, no `text_to_image`: el prompt debe decir "cambia el fondo a un escenario de concierto con luces azules, mantén la pose y la ropa de la persona" en vez de redescribir a Mateo entero.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.


Si la imagen final incluye a un personaje recurrente, coordina con `character_consistency` antes de dar el prompt por definitivo.
