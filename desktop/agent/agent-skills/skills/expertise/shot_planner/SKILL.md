---
id: shot_planner
kind: expertise
title: Planificador de planos
version: 1
updated: 2026-10-02
applies_to: [VIDEO]
triggers: [plan de rodaje, desglose de escena, cuántos planos, planifica la secuencia]
---

## Qué hace esta skill
Descompone una escena o secuencia narrativa en una lista de planos individuales, cada uno con sus parámetros definidos, **sin lanzar ninguna generación**. Es un paso de planificación que alimenta después a `video_generation_expert`, `cinematic_director` y `cost_optimizer`.

## Procedimiento
1. Parte de la descripción narrativa de la escena (ej. una estrofa o estribillo de una canción) y divide en los planos mínimos necesarios para contarla, evitando tanto la fragmentación excesiva como planos que intenten abarcar demasiada acción.
2. Para cada plano, define:
   - **Duración aproximada** (segundos), coherente con el ritmo de la escena (un corte rápido necesita planos cortos; un momento contemplativo, más largos).
   - **Tipo de plano** (general, medio, primer plano, detalle), según su función narrativa.
   - **Movimiento de cámara** (uno solo, ver `camera_motion`).
   - **Continuidad** respecto al plano anterior: qué debe mantenerse igual (personaje, luz, posición en el espacio) y qué cambia deliberadamente.
3. Presenta la lista de planos al usuario en español, de forma clara (tabla o lista numerada), antes de que ninguna skill de generación la ejecute.
4. Marca explícitamente qué planos dependen de una imagen de referencia ya existente (ver `asset_librarian`) y cuáles necesitan generarse primero como imagen base.
5. Entrega la lista a `cost_optimizer` para una estimación de coste conjunta antes de iniciar la generación plano por plano, y a `continuity_supervisor` para revisión cruzada una vez generados.

## Checklist
- ¿Cada plano tiene una función narrativa clara y no se solapa con el anterior?
- ¿Se definió duración, tipo de plano, cámara y continuidad para cada uno?
- ¿Se identificaron las dependencias de referencia de imagen entre planos?
- ¿Se evitó lanzar generaciones desde esta misma skill?

## Errores a evitar
- Fragmentar en demasiados planos muy cortos sin justificación narrativa (encarece y complica la continuidad).
- Diseñar un plano que combine varios movimientos de cámara o varios cambios de escena a la vez.
- Pasar a generación sin presentar antes el plan completo al usuario.

## Ejemplo breve
Para una estrofa de 15 segundos: Plano 1 (0-5s, general, estático, establece la azotea de noche) → Plano 2 (5-10s, medio, dolly in, Mateo empieza a cantar) → Plano 3 (10-15s, primer plano, estático, microexpresión en el estribillo). Cada plano se entrega a `video_generation_expert` por separado, con su propia confirmación de coste.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.
