---
id: continuity_supervisor
kind: expertise
title: Supervisor de continuidad
version: 1
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]
triggers: [continuidad, no cuadra con el plano anterior, revisar secuencia, raccord]
---

## Qué hace esta skill
Revisa que una secuencia de planos (imagen o vídeo) mantenga coherencia entre sí antes de darla por buena: personaje, vestuario, luz, hora del día, posición en el espacio y estilo visual. Complementa a `character_consistency` (identidad del personaje) y a `shot_planner` (estructura de la secuencia), centrándose en la revisión cruzada entre planos ya generados.

## Procedimiento
1. Antes de aprobar un plano nuevo, compáralo contra el keyframe/referencia del personaje y contra el plano inmediatamente anterior de la misma escena, usando el índice de assets (`asset_librarian`) para localizar ambos.
2. Verifica estos puntos de raccord, en este orden:
   - Identidad del personaje (rasgos fijos, ropa, accesorios) — si cambia sin motivo narrativo, es un fallo de continuidad.
   - Luz y hora del día coherentes con la escena (si el plano anterior era de noche, este no debería amanecer sin un corte narrativo que lo explique).
   - Posición/dirección de movimiento en el espacio (si el personaje salía por la derecha, no debería entrar por la derecha en el plano siguiente dentro de la misma acción continua).
   - Estilo visual y tono de color consistentes dentro de la misma escena.
3. Si detectas una inconsistencia, no la "arregles" improvisando una descripción nueva: vuelve a `character_consistency` o `cinematic_director` para corregir el prompt del plano afectado y regenerarlo, citando qué punto de raccord falló.
4. Registra el resultado de la revisión (aprobado / regenerar con motivo) en el índice de assets como metadato del plano, para que quede trazabilidad de qué versión es la válida.
5. Esta skill solo revisa y señala problemas: no lanza generaciones por sí misma, eso corresponde a `video_generation_expert`/`image_generation_expert` tras la corrección.

## Checklist
- ¿El personaje mantiene sus rasgos fijos respecto al plano anterior?
- ¿La luz/hora del día es coherente dentro de la misma escena?
- ¿La dirección de movimiento en el espacio tiene sentido entre planos?
- ¿Se registró la decisión (aprobado/regenerar) en el índice de assets?

## Errores a evitar
- Aprobar una secuencia sin comparar explícitamente contra el plano anterior.
- Corregir un fallo de continuidad "a ojo" en el prompt sin anotar qué regla de raccord se violó.
- Mezclar revisión de continuidad con decisiones de qué modelo usar (eso es tarea de `model_router`).

## Ejemplo breve
Si el plano anterior mostraba a Julián saliendo de cuadro por la derecha corriendo, y el plano siguiente de la misma acción continua lo muestra entrando por la derecha también, hay un fallo de raccord de dirección: debería entrar por la izquierda. Se marca "regenerar: dirección de movimiento inconsistente" y se devuelve a `cinematic_director`.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.
