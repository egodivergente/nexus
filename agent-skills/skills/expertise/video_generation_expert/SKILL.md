---
id: video_generation_expert
kind: expertise
title: Experto en generación de vídeo
version: 1
updated: 2026-10-02
applies_to: [VIDEO]
triggers: [generar vídeo, animar imagen, clip, escena de vídeo musical]
---

## Qué hace esta skill
Guía el flujo de trabajo para tareas de vídeo (text_to_video, image_to_video, reference_to_video, start_end_frame, video_edit, video_extend, motion_control, lipsync, avatar), coordinando con `model_router`, `prompt_engineer`, `camera_motion` y `character_consistency`. No afirma capacidades de un modelo concreto: eso lo confirma `get_model_schema` y la skill de ese modelo.

## Procedimiento
1. Clasifica la tarea en el vocabulario controlado y determina si hay imagen(es) de partida, si se necesita control de último frame (`needsLastFrame`) o audio nativo (`needsAudio`).
2. Pasa esto al `model_router` con las prioridades relevantes (`face_consistency` y `natural_motion` para personajes recurrentes, `camera_control` si el plano lo exige, `native_audio` si se necesita sonido integrado, `long_duration` solo si de verdad se necesita un clip largo).
3. Carga la skill del modelo recomendado y usa su plantilla específica de i2v o t2v, respetando sus particularidades (algunas familias piden describir solo el cambio respecto a la imagen, otras aceptan descripción completa de escena).
4. Antes de generar, confirma en el schema la duración y resolución reales disponibles; no asumas valores de otra variante o de otro modelo.
5. Si el plano forma parte de una secuencia más larga, usa primero `shot_planner` para descomponerla, y después genera plano por plano verificando continuidad con `continuity_supervisor`.
6. Un solo movimiento de cámara por generación (ver `camera_motion`); si se necesita más de un movimiento, divide en varios planos.

## Checklist
- ¿Se consultó `recommend_models` en vez de elegir el modelo a mano?
- ¿Se comprobó el schema real antes de pedir duración/resolución/último frame?
- ¿El prompt describe solo lo nuevo cuando hay imagen de referencia?
- ¿Se evitó mezclar varios movimientos de cámara en un plano?
- ¿Se verificó continuidad con el plano anterior antes de confirmar la generación?

## Errores a evitar
- Pedir lipsync o audio nativo a un modelo cuya skill no lo menciona como capacidad disponible.
- Generar un plano largo cuando el objetivo real es una secuencia de varios planos cortos encadenables.
- No comprobar el schema antes de asumir que una variante "fast" admite las mismas opciones que la variante "pro" de la misma familia.

## Ejemplo breve
Petición: "anima esta foto de Julián para que mire a cámara y sonría". Es `image_to_video` con `needsLastFrame: false`; el prompt describe solo "gira la mirada hacia cámara y sonríe levemente, plano fijo", sin repetir el aspecto físico que ya aporta la imagen.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.


Recuerda que toda generación de vídeo se confirma individualmente con el usuario (ver `cost_optimizer`), nunca en bucle.
