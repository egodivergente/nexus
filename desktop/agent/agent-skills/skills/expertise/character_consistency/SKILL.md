---
id: character_consistency
kind: expertise
title: Consistencia de personaje
version: 1
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]
triggers: [mismo personaje, que no cambie la cara, Mateo, Julián, personaje recurrente]
---

## Qué hace esta skill
Da el procedimiento para mantener la identidad visual de un personaje recurrente (p. ej. Mateo, Julián) a través de varias generaciones de imagen y vídeo, usando siempre el índice de assets como fuente de verdad, nunca la memoria del modelo entre llamadas independientes.

## Procedimiento
1. Antes de generar, busca en el índice de assets (`asset_librarian`) si el personaje ya tiene una imagen de referencia o keyframe "canónico"; si no existe, créalo primero y regístralo antes de usarlo en más generaciones.
2. Fija un conjunto breve de **descriptores fijos** del personaje (ej. "Mateo: pelo corto castaño, chaqueta vaquera azul, cicatriz leve en la ceja izquierda") y reutilízalos literalmente en los prompts de generación de imagen desde cero; no los repitas cuando ya hay una imagen de referencia de entrada.
3. Para image-to-video o edición a partir de una imagen existente, describe en el prompt **solo el cambio o movimiento nuevo** (acción, cámara, luz, entorno) — nunca vuelvas a describir rasgos físicos que ya son visibles en la imagen, y nunca introduzcas un rasgo que la contradiga (otro color de pelo, otra ropa) salvo que el usuario lo pida explícitamente como cambio.
4. Si la skill del modelo elegido documenta un procedimiento propio de consistencia (p. ej. plantilla de "vista 360°" o límite de imágenes de referencia), síguelo tal cual lo describe esa skill en vez de un procedimiento genérico.
5. Al terminar una generación que usará el personaje más adelante, guarda el resultado en el índice de assets con su lineage (de qué imagen/plano viene) para que la siguiente escena parta de ahí, no de la imagen original más antigua.
6. Si el modelo no documenta `face_consistency`/`identity_preservation` como fortaleza verificada (revisa `unverified_tags` de su skill), avisa al usuario de que el resultado debe revisarse visualmente antes de encadenarlo.

## Checklist
- ¿Existe ya una referencia canónica del personaje en el índice de assets?
- ¿Los descriptores fijos se usan solo cuando no hay imagen de referencia?
- ¿El prompt de i2v describe solo el cambio, no la imagen completa?
- ¿Se registró el resultado nuevo en el índice con su lineage?

## Errores a evitar
- Generar la misma escena repitiendo descriptores físicos distintos cada vez (deriva de identidad).
- Encadenar una escena nueva a partir de una imagen vieja cuando ya existe una más reciente y validada.
- Confiar en la consistencia de un modelo sin revisar si su skill la documenta como verificada o no.

## Ejemplo breve
Si ya existe en el índice una imagen canónica de Mateo con chaqueta vaquera y pelo corto, un prompt de i2v nuevo no debe decir "Mateo, pelo corto, chaqueta vaquera, camina..." sino solo "camina hacia la cámara bajo la lluvia, dolly in lento" — los rasgos ya están en la imagen de entrada.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.


Esta skill no decide qué modelo usar; eso corresponde a `model_router`, que debe priorizar `face_consistency`/`identity_preservation` cuando el personaje es recurrente.
