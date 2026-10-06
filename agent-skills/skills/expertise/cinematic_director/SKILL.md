---
id: cinematic_director
kind: expertise
title: Director de fotografía / dirección cinematográfica
version: 1
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]
triggers: [plano cinematográfico, dirección de arte, cómo lo filmo, estética]
---

## Qué hace esta skill
Aporta criterio de dirección visual (encuadre, luz, ritmo, coherencia estética entre planos) para vídeos musicales y cortos con personajes recurrentes. Trabaja en conjunto con `prompt_engineer` (estructura del prompt) y `camera_motion` (movimiento de cámara); no sustituye a ninguna de las dos.

## Procedimiento
1. Antes de describir un plano, define su función narrativa: ¿establece lugar, muestra emoción, conecta dos escenas? Eso determina el tipo de plano (general, medio, primer plano, detalle).
2. Usa la estructura sujeto → acción → cámara → luz → estilo → restricciones (de `prompt_engineer`); añade aquí la coherencia estética entre planos consecutivos: mismo tono de color, misma lógica de luz (si la escena anterior era de noche, la siguiente no debería amanecer sin que la narrativa lo pida).
3. Un solo movimiento de cámara por plano (ver `camera_motion`): no acumules intenciones de dirección en una sola generación.
4. Describe microexpresiones y timing concreto en la acción del personaje ("levanta la mirada a mitad del plano y sonríe levemente"), nunca solo adjetivos de estado de ánimo.
5. Para una secuencia de varios planos, usa primero `shot_planner` para descomponer la escena, y luego aplica esta skill plano por plano.
6. Antes de generar, confirma con la skill del modelo elegido (`load_skill`) qué lenguaje cinematográfico entiende mejor esa variante concreta; no asumas que todos los modelos interpretan igual "plano contrapicado" o "dolly in".

## Checklist antes de enviar el prompt
- ¿El plano tiene una función narrativa clara?
- ¿Hay un único movimiento de cámara?
- ¿La luz y el tono de color son coherentes con el plano anterior/siguiente?
- ¿La acción tiene timing y microexpresión concretos, no solo adjetivos?
- ¿Se respetan los descriptores fijos del personaje (ver `character_consistency`)?

## Errores a evitar
- Mezclar varios encuadres o movimientos de cámara "para asegurar" en un solo plano.
- Cambiar de estilo visual entre planos consecutivos sin que la narrativa lo justifique.
- Usar vocabulario cinematográfico muy específico sin haber comprobado que el modelo elegido lo soporta.
- Olvidar la restricción de continuidad de personaje al describir un plano nuevo.

## Ejemplo breve
Escena de estribillo: plano anterior en primer plano cálido de Julián cantando; el siguiente plano no debería saltar a un gran plano general frío sin razón narrativa. Mejor: medio plano, misma temperatura de color, movimiento de cámara único (dolly out suave) para abrir el espacio sin romper la continuidad emocional.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.

Esta skill no decide qué modelo usar ni estima costes; esos criterios viven en `model_router` y `cost_optimizer` respectivamente.
