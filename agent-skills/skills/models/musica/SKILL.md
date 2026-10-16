---
id: musica
kind: model
title: Música (Suno V6, MiniMax Music 2.6/3.0, MiniMax Lyrics)
version: 2
updated: 2026-10-16
match:
  - '^suno/'
  - '^minimax/(music|lyrics)'
modalities: [AUDIO]
strengths: [prompt_adherence, long_duration]
weaknesses: []
ideal_tasks: []
avoid_tasks: [text_to_video, text_to_image]
unverified_tags: [prompt_adherence]
sources:
  - url: https://static.atlascloud.ai/model/readme/suno-chirp-v6.md
    title: Suno V6 — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/minimax-music-3.0.md
    title: MiniMax Music 3.0 — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/minimax-music-2.6.md
    title: MiniMax Music 2.6 — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/minimax-lyrics-generation.md
    title: MiniMax Lyrics Generation — README oficial
    accessed: 2026-10-16
---

## Resumen
Canciones completas con voz o instrumentales: Suno V6 (versión principal, Mini para borradores y Wild para mezclas de género atrevidas; cada petición da 2 tomas y portada) y MiniMax Music 2.6 y 3.0 (hasta ~5 minutos con letra estructurada). MiniMax Lyrics escribe o edita letras con etiquetas de sección listas para los modelos de música [[1]](https://static.atlascloud.ai/model/readme/suno-chirp-v6.md)[[2]](https://static.atlascloud.ai/model/readme/minimax-music-3.0.md)[[4]](https://static.atlascloud.ai/model/readme/minimax-lyrics-generation.md).

## Cuándo elegirlo y cuándo no
| Necesidad | Elección |
|---|---|
| Mejor realismo vocal y dos opciones para elegir | Suno V6 |
| Probar ideas rápido | Suno V6 Mini |
| Algo poco convencional o fusión de géneros | Suno V6 Wild |
| Canción larga con control por secciones y mezcla limpia | MiniMax Music 3.0 |
| Escribir primero la letra o adaptar una existente | MiniMax Lyrics → música |
| Música de fondo sin voz | Cualquiera con modo instrumental |

## Linaje y variantes en Nexus
- `suno/chirp-v6`, `-mini`, `-wild`: dos modos. Inspiración (`custom=false`: el prompt describe la canción) o Personalizado (`custom=true`: el prompt ES la letra; `style`, `title`, `duration` 10–360 s, `negative_tags`). `variety` controla cuánto se alejan las dos tomas [[1]](https://static.atlascloud.ai/model/readme/suno-chirp-v6.md).
- `minimax/music-2.6`: `prompt` de estilo (≤2.000) + `lyrics` con etiquetas (≤3.500) o `is_instrumental` [[3]](https://static.atlascloud.ai/model/readme/minimax-music-2.6.md).
- `minimax/music-3.0` (agosto 2026): menos deriva estructural, arreglos y mezcla más completos, mejor voz; puede escribir su propia letra [[2]](https://static.atlascloud.ai/model/readme/minimax-music-3.0.md).
- `minimax/lyrics-generation`: modos `write_full_song` y `edit`; devuelve título, etiquetas de estilo y letra compatible con `lyrics` [[4]](https://static.atlascloud.ai/model/readme/minimax-lyrics-generation.md).

## Fortalezas
- **long_duration**: hasta ~5 min (MiniMax) o 360 s fijados (Suno personalizado).
- **prompt_adherence** [sin verificar]: el control por etiquetas de sección mejora la estructura.

## Debilidades
- La letra en español puede salir con acentuación forzada; revísala en la primera toma.
- No reproducen canciones protegidas ni imitan artistas concretos: describe el estilo, no el nombre.

## Tareas ideales / Tareas que debe evitar
- **Ideales**: jingle, sintonía, canción de escena, música de fondo instrumental.
- **Evitar**: efectos de sonido sueltos o voz hablada (usa la skill «voz»).

## Estructura de prompt recomendada
- Estilo (Suno inspiración, MiniMax `prompt`): género + tempo/energía + instrumentos + voz + ambiente, en una o dos frases. Ej.: «Pop acústico luminoso, 100 bpm, guitarra y palmas, voz femenina cálida, verano en Madrid».
- Letra (Suno personalizado, MiniMax `lyrics`): etiquetas en su propia línea — `[Intro]`, `[Verse]`, `[Pre Chorus]`, `[Chorus]`, `[Bridge]`, `[Outro]`; una línea por verso; línea vacía = pausa [[3]](https://static.atlascloud.ai/model/readme/minimax-music-2.6.md).

## Plantillas por tarea
### Jingle de 30 s
Suno V6 personalizado: `duration=30`, `style` con género y voz, letra con `[Verse]` corto y `[Chorus]` con el lema repetido dos veces.
### Canción completa
MiniMax Lyrics (`write_full_song`) → revisar con el usuario → MiniMax Music 3.0 con esa letra y el `prompt` de estilo devuelto.
### Instrumental de fondo
MiniMax Music `is_instrumental=true` o Suno `instrumental=true`; describe energía y que no compita con la voz en off.

## Idioma, longitud y prompt negativo
- Escribe la letra en el idioma en que se cantará.
- Suno: `negative_tags` evita estilos (solo modo personalizado). MiniMax no tiene negativo: exprésalo en positivo.

## Audio y diálogo
El resultado ya incluye voz y mezcla; para añadir locución encima, genérala con «voz» y mézclala con la edición local.

## Restricciones e incompatibilidades conocidas
- Suno: `title`, `style`, `duration`, `negative_tags` y pesos solo funcionan con `custom=true` [[1]](https://static.atlascloud.ai/model/readme/suno-chirp-v6.md).
- Suno devuelve siempre dos pistas por petición.

## Errores habituales
- Poner la descripción de estilo dentro de la letra en modo personalizado: se canta.
- Etiquetas de sección en la misma línea que el verso.
- Pedir «como [artista]»: describe rasgos musicales en su lugar.

## Recomendaciones de prompting
- Itera con Suno Mini y pasa a V6 o MiniMax 3.0 para la versión final.
- Fija el tempo y la energía: es lo que más cambia la sensación.

## Fuentes y fecha
[1] Suno V6 — README oficial — https://static.atlascloud.ai/model/readme/suno-chirp-v6.md — consultado 2026-10-16
[2] MiniMax Music 3.0 — README oficial — https://static.atlascloud.ai/model/readme/minimax-music-3.0.md — consultado 2026-10-16
[3] MiniMax Music 2.6 — README oficial — https://static.atlascloud.ai/model/readme/minimax-music-2.6.md — consultado 2026-10-16
[4] MiniMax Lyrics Generation — README oficial — https://static.atlascloud.ai/model/readme/minimax-lyrics-generation.md — consultado 2026-10-16
