---
id: camera_motion
kind: expertise
title: Movimiento de cámara
version: 1
updated: 2026-10-02
applies_to: [VIDEO]
triggers: [movimiento de cámara, paneo, travelling, dolly, zoom, cómo muevo la cámara]
---

## Qué hace esta skill
Da el criterio para describir movimientos de cámara en prompts de vídeo de forma clara y realizable, evitando ambigüedad y sobrecarga. Se usa junto a `prompt_engineer` y `cinematic_director`, nunca de forma aislada del resto de la estructura del prompt.

## Regla central: un movimiento por plano
Cada generación de vídeo debe pedir **un único movimiento de cámara**, no una combinación. Si la escena necesita varios movimientos (p. ej. un paneo seguido de un zoom), eso son dos planos distintos, no una sola generación: usa `shot_planner` para dividirlos.

## Vocabulario de movimientos y cuándo usarlos
- **Plano fijo (static shot)**: para diálogo, actuación o detalle donde la cámara no debe distraer.
- **Paneo (pan left/right)**: para seguir una acción horizontal o revelar un entorno.
- **Dolly in / dolly out**: para intensificar o relajar emocionalmente un momento (acercarse a una cara en un clímax, alejarse al cerrar una escena).
- **Travelling / tracking shot**: para seguir a un personaje que se desplaza.
- **Zoom**: usar con moderación; muchos modelos lo interpretan distinto a un dolly (cambia el campo de visión en vez de la posición física de la cámara) — confírmalo con la skill del modelo si el matiz importa.
- **Grúa / crane (up/down)**: para revelar escala o cambiar de un plano íntimo a uno general.
- **Contrapicado / picado (low-angle / high-angle)**: no es movimiento sino encuadre; se puede combinar con un movimiento simple si el modelo lo permite, pero decláralo como encuadre inicial, no como acción.

## Procedimiento
1. Decide la función narrativa del plano (ver `cinematic_director`) y elige el movimiento que mejor la sirve, no el más vistoso.
2. Escribe el movimiento en una frase corta y literal ("dolly in lento hacia el rostro") dentro de la sección "cámara" del prompt, no mezclado con la acción del personaje.
3. Antes de confirmar, revisa la skill del modelo elegido: algunas familias documentan `camera_control` como fortaleza verificada y otras como `[sin verificar]` — si está sin verificar, avisa al usuario de que el resultado puede no respetar el movimiento pedido.
4. Si el plano requiere control de encuadre inicial y final (p. ej. empezar en plano general y acabar en primer plano), usa la tarea `start_end_frame` si el modelo la soporta, en vez de intentar describirlo todo en un único movimiento continuo.

## Errores a evitar
- Pedir "cámara dinámica y envolvente" sin especificar un movimiento concreto.
- Combinar paneo + zoom + dolly en la misma generación.
- Asumir que todos los modelos interpretan igual "zoom" vs. "dolly" sin comprobarlo.

## Ejemplo breve
Para un primer plano emocional del estribillo, mejor "dolly in lento hacia el rostro de Mateo" que "cámara dinámica alrededor del personaje con zoom y giro" — esto último son al menos dos movimientos y debería dividirse en dos planos si de verdad se necesitan ambos.

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.
