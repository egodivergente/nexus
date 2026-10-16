---
id: voz
kind: model
title: Voz — texto a voz y transcripción (MiniMax Speech, Gemini TTS, ElevenLabs v3, xAI, Seed Audio/ASR)
version: 2
updated: 2026-10-16
match:
  - '^minimax/speech'
  - '^google/gemini-[0-9.]+-(flash|pro)-tts'
  - '^elevenlabs/'
  - '^xai/(tts|stt)'
  - '^bytedance/seed-(asr|audio)'
modalities: [AUDIO]
strengths: [fast, low_cost, prompt_adherence]
weaknesses: []
ideal_tasks: []
avoid_tasks: [text_to_video, text_to_image]
unverified_tags: [prompt_adherence]
sources:
  - url: https://static.atlascloud.ai/model/readme/minimax-speech-2.6-hd.md
    title: MiniMax Speech 2.6 HD — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/google-gemini-2.5-pro-tts.md
    title: Gemini 2.5 Pro TTS — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/elevenlabs-v3-text-to-speech.md
    title: ElevenLabs V3 Text-to-Speech — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/xai-tts-v1.md
    title: xAI TTS v1 — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/xai-stt-v1.md
    title: xAI STT v1 — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/bytedance-seed-audio-1.0.md
    title: Seed Audio 1.0 — README oficial
    accessed: 2026-10-16
  - url: https://static.atlascloud.ai/model/readme/bytedance-seed-asr-2.0.md
    title: Seed ASR 2.0 — README oficial
    accessed: 2026-10-16
---

## Resumen
Modelos de voz del catálogo: síntesis (texto → voz) con MiniMax Speech 2.6 HD/Turbo, Gemini 2.5 Flash/Pro y 3.1 Flash TTS, ElevenLabs v3, xAI TTS v1 y Seed Audio 1.0; y transcripción (voz → texto) con xAI STT v1 y Seed ASR 2.0. Para transcribir en local y sin coste, Nexus tiene además Whisper en el teléfono.

## Cuándo elegirlo y cuándo no
| Necesidad | Elección |
|---|---|
| Locución en español natural con control de emoción, velocidad y tono | MiniMax Speech 2.6 HD (`language_boost=Spanish`, `emotion`) |
| Mucho volumen o prisa | MiniMax Speech 2.6 Turbo o Gemini Flash TTS |
| Dirigir la interpretación con una instrucción en texto («dilo despacio y con calma») | Gemini TTS (la instrucción va dentro de `text`) |
| Narración larga muy expresiva con voces de catálogo | ElevenLabs v3 (hasta 5.000 caracteres por petición) |
| Pausas, risas, susurros por etiquetas; telefonía 8 kHz; clonar una voz | xAI TTS v1 |
| Imitar la voz de un audio de referencia | Seed Audio 1.0 (hasta 3 audios de referencia) |
| Transcribir con hablantes y marcas de tiempo | xAI STT v1 o Seed ASR 2.0 |
| Transcribir sin gastar y sin subir el audio | Whisper local de Nexus |

## Linaje y variantes en Nexus
- MiniMax Speech 2.6: HD (fidelidad) y Turbo (latencia); 15 voces predefinidas, 16 idiomas por `language_boost`, `speed` 0,5–2, `pitch` −12–12, `emotion` [[1]](https://static.atlascloud.ai/model/readme/minimax-speech-2.6-hd.md).
- Gemini TTS: 2.5 Flash, 2.5 Pro (calidad de estudio) y 3.1 Flash; 30 voces (`voice_name`), salida WAV 24 kHz, hasta 10.000 caracteres [[2]](https://static.atlascloud.ai/model/readme/google-gemini-2.5-pro-tts.md).
- ElevenLabs v3: 21 voces (`voice`), `stability` 0–1 (expresivo ↔ estable), normalización de texto [[3]](https://static.atlascloud.ai/model/readme/elevenlabs-v3-text-to-speech.md).
- xAI TTS v1: 80+ voces en 20 idiomas, 14 etiquetas instantáneas y 13 de estilo envolventes, códecs de telefonía, clonación con hasta 120 s de referencia [[4]](https://static.atlascloud.ai/model/readme/xai-tts-v1.md).
- Seed Audio 1.0: voz desde texto con referencias de audio (`@audio1`…) o una imagen; cada referencia lleva un solo tipo [[6]](https://static.atlascloud.ai/model/readme/bytedance-seed-audio-1.0.md).
- xAI STT v1: 24+ idiomas, palabras con tiempos, diarización, hasta 100 términos de sesgo [[5]](https://static.atlascloud.ai/model/readme/xai-stt-v1.md). Seed ASR 2.0: 51 idiomas, puntuación, diarización y palabras clave [[7]](https://static.atlascloud.ai/model/readme/bytedance-seed-asr-2.0.md).

## Fortalezas
- **fast / low_cost**: Turbo y Flash para borradores y volumen.
- **prompt_adherence** [sin verificar]: Gemini y xAI se dirigen con instrucciones o etiquetas en el propio texto.

## Debilidades
- Las voces son de catálogo: no hay una voz concreta «de la marca» salvo clonación (xAI) o referencia (Seed Audio).
- La pronunciación de nombres propios puede fallar; prueba con una frase corta antes de un texto largo.

## Tareas ideales / Tareas que debe evitar
- **Ideales**: locución, narración, doblaje de un texto ya escrito, subtítulos desde audio.
- **Evitar**: generar vídeo o imagen; para lipsync de un avatar usa la skill «avatar-lipsync».

## Estructura de prompt recomendada
- El texto a decir es el `text` (o `prompt`, según el schema). Escríbelo tal cual se pronunciará: números y siglas como se leen si el modelo no normaliza.
- Gemini TTS: instrucción de estilo + dos puntos + texto, en una línea: `Dilo con calma y cercanía: …`.
- xAI TTS: inserta las etiquetas de su documentación donde deben ocurrir (pausa, risa, susurro); no las inventes.
- MiniMax/ElevenLabs: la emoción y la estabilidad van en parámetros, no en el texto.

## Plantillas por tarea
### Locución de anuncio (30 s)
Texto de ~70–80 palabras en frases cortas; MiniMax HD, `language_boost=Spanish`, `emotion=happy` o `calm` según la marca, `speed` 1.0–1.1.
### Narración larga
Divide por párrafos; ElevenLabs v3 con `stability` 0,5–0,7 para mantener el mismo timbre entre partes.
### Transcripción con hablantes
xAI STT v1 con diarización o Seed ASR 2.0 con `enable_speaker_info=true` y `enable_punc=true`.

## Idioma, longitud y prompt negativo
- Español: MiniMax (`language_boost=Spanish`), Gemini, ElevenLabs y xAI lo admiten. Límites: 10.000 caracteres (MiniMax, Gemini), 5.000 (ElevenLabs).
- No hay prompt negativo.

## Referencias e imagen inicial
Solo Seed Audio usa referencias: hasta 3 audios o un `speaker`, o una imagen; nunca mezcles audio e imagen en la misma petición [[6]](https://static.atlascloud.ai/model/readme/bytedance-seed-audio-1.0.md).

## Cámara y movimiento
No aplica.

## Consistencia de personaje
Para la misma voz en varias piezas, fija modelo, voz y parámetros; con ElevenLabs sube `stability`.

## Audio y diálogo
Para diálogo entre personajes genera cada intervención por separado con su voz y únelas con la edición local de Nexus.

## Restricciones e incompatibilidades conocidas
- Gemini TTS factura por cada 1.000 caracteres y devuelve WAV 24 kHz [[2]](https://static.atlascloud.ai/model/readme/google-gemini-2.5-pro-tts.md).
- Seed ASR: `context` es una cadena JSON de palabras clave, no un objeto [[7]](https://static.atlascloud.ai/model/readme/bytedance-seed-asr-2.0.md).

## Errores habituales
- Poner en el texto «voz femenina alegre»: se leerá en voz alta. Va en parámetros (salvo instrucción de estilo en Gemini).
- Pagar una transcripción que Whisper local hace gratis.
- Textos sin puntuación: la entonación sale plana.

## Recomendaciones de prompting
- Pide una muestra de una frase antes de locutar el texto completo.
- Escribe para el oído: frases cortas, sin paréntesis ni listas.

## Fuentes y fecha
[1] MiniMax Speech 2.6 HD — README oficial — https://static.atlascloud.ai/model/readme/minimax-speech-2.6-hd.md — consultado 2026-10-16
[2] Gemini 2.5 Pro TTS — README oficial — https://static.atlascloud.ai/model/readme/google-gemini-2.5-pro-tts.md — consultado 2026-10-16
[3] ElevenLabs V3 Text-to-Speech — README oficial — https://static.atlascloud.ai/model/readme/elevenlabs-v3-text-to-speech.md — consultado 2026-10-16
[4] xAI TTS v1 — README oficial — https://static.atlascloud.ai/model/readme/xai-tts-v1.md — consultado 2026-10-16
[5] xAI STT v1 — README oficial — https://static.atlascloud.ai/model/readme/xai-stt-v1.md — consultado 2026-10-16
[6] Seed Audio 1.0 — README oficial — https://static.atlascloud.ai/model/readme/bytedance-seed-audio-1.0.md — consultado 2026-10-16
[7] Seed ASR 2.0 — README oficial — https://static.atlascloud.ai/model/readme/bytedance-seed-asr-2.0.md — consultado 2026-10-16
