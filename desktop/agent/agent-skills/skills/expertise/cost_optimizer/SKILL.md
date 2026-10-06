---
id: cost_optimizer
kind: expertise
title: Optimizador de coste
version: 1
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]
triggers: [cuánto cuesta, más barato, versión de prueba, presupuesto, gasto]
---

## Qué hace esta skill
Da el procedimiento para generar con criterio de coste contenido, sin automatizar gasto: toda generación de pago se confirma individualmente con el usuario, nunca en bucle ni de forma encadenada automática.

## Reglas fijas (no negociables)
1. **Nunca se ejecuta una generación de pago sin confirmación explícita del usuario**, aunque ya se haya confirmado una generación similar antes en la misma conversación.
2. **Cada generación se confirma por separado.** No agrupes varias generaciones bajo una sola confirmación ("genero las 5 escenas") ni asumas luz verde para repetir una generación que falló.
3. **Prohibido cualquier bucle de generación automática** (reintentos silenciosos, generación de variantes en cadena sin pausa de confirmación).
4. Antes de proponer una variante barata de prueba, **comprueba con `get_model_schema`** que esa variante (menor duración, menor resolución) existe de verdad para el modelo elegido — no la propongas si el schema no la ofrece.

## Procedimiento
1. Antes de generar, usa `estimate_generation_cost` con el TaskSpec final y comunica el coste estimado al usuario en español, de forma clara (no solo un número suelto: en qué consiste esa generación).
2. Si el objetivo es probar un prompt o una composición antes de la toma final, propón explícitamente la variante más económica que el schema permita (menor duración/resolución), explicando que es para validar antes de escalar — y espera confirmación igualmente.
3. Si el usuario pide repetir una generación que no quedó bien, trátalo como una generación nueva: nuevo coste estimado, nueva confirmación.
4. Si una tarea requiere varias generaciones encadenadas (p. ej. una secuencia de planos de `shot_planner`), presenta el coste total estimado de la secuencia, pero sigue pidiendo confirmación plano por plano antes de lanzar cada una.
5. Si el presupuesto (`budget`) del TaskSpec es `low`, prioriza en `model_router` las variantes "fast/lite/mini/std" de la familia recomendada antes que "pro/4K/max".

## Checklist
- ¿Se mostró el coste estimado antes de pedir confirmación?
- ¿La confirmación es específica para esta generación, no genérica para "todo el proyecto"?
- ¿La variante barata propuesta existe realmente en el schema del modelo?
- ¿Se evitó cualquier automatismo de reintento o cadena sin pausa?

## Errores a evitar
- Lanzar una generación "mientras el usuario decide" para ganar tiempo.
- Asumir que una variante barata existe sin comprobar el schema.
- Interpretar una confirmación anterior como válida para una generación distinta.

## Ejemplo breve
Antes de generar un plano final de 10s en 1080p, se puede proponer primero una prueba de 5s en 720p (si el schema del modelo lo permite) para validar composición y prompt a menor coste, explicando el ahorro aproximado y pidiendo confirmación para esa prueba y, después, otra confirmación aparte para la versión final.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.
