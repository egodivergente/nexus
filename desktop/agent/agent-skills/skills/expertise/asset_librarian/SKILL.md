---
id: asset_librarian
kind: expertise
title: Bibliotecario de assets
version: 1
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]
triggers: [índice de assets, dónde está la imagen, organizar proyecto, qué escenas tengo]
---

## Qué hace esta skill
Define cómo organizar y consultar el índice de assets del proyecto (proyectos → escenas → planos → personajes), manteniendo metadatos y lineage sin tocar nunca los ficheros físicos directamente.

## Jerarquía de organización
- **Proyecto**: unidad de más alto nivel (ej. un vídeo musical concreto).
- **Escena**: bloque narrativo dentro del proyecto (ej. "estribillo 1", "intro").
- **Plano**: unidad mínima generada (una imagen o un clip de vídeo concreto).
- **Personaje**: entidad recurrente (Mateo, Julián) con su referencia canónica y variantes aprobadas.

## Procedimiento
1. Antes de generar algo que vaya a reutilizarse, consulta el índice para ver si ya existe un asset equivalente (referencia de personaje, plano anterior de la misma escena) en vez de regenerar desde cero.
2. Al crear un asset nuevo, regístralo en el índice con sus metadatos: proyecto, escena, plano, personaje(s) implicados, modelo usado, y **lineage** (de qué asset(s) de entrada proviene, si es una edición o una continuación).
3. **Nunca muevas ni renombres el fichero físico** en disco para "organizarlo"; toda reorganización se hace en los metadatos del índice (etiquetas, relaciones proyecto/escena/plano), dejando el fichero donde el pipeline de generación lo dejó.
4. Si un asset queda obsoleto (se generó una versión mejor del mismo plano), no lo borres: márcalo como "sustituido por X" en el índice, conservando el lineage completo para poder entender el historial si hace falta.
5. Cuando otra skill (`character_consistency`, `continuity_supervisor`, `shot_planner`) necesite una referencia, resuélvela consultando el índice, no pidiendo al usuario que la busque manualmente.

## Checklist
- ¿Se consultó el índice antes de regenerar algo que podría ya existir?
- ¿El asset nuevo quedó registrado con proyecto/escena/plano/personaje y lineage?
- ¿Se evitó mover o renombrar el fichero físico?
- ¿Las versiones obsoletas se marcaron como sustituidas en vez de borrarse?

## Errores a evitar
- Tratar el índice como una copia redundante que se puede ignorar "porque el fichero ya está ahí".
- Renombrar o mover ficheros pensando que así se "limpia" el proyecto.
- Perder el lineage al registrar una edición como si fuera un asset nuevo sin relación con el original.

## Ejemplo breve
Si se genera una nueva versión del plano 3 de la escena "estribillo 1" porque la anterior tenía un fallo de continuidad, el fichero antiguo no se borra ni se renombra: se marca en el índice como "sustituido por plano3_v2", conservando ambos para trazabilidad.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.
