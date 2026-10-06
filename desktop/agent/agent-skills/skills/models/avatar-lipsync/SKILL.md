---
id: 'avatar-lipsync'
kind: 'model'
title: 'Avatares y lipsync (OmniHuman, Sync, VEED, InfiniteTalk, Kling Avatar)'
version: 2
updated: '2026-10-02'
match: ['avatar-omni-human', '^sync/', '^veed/', 'infinitetalk', 'lipsync', '^kwaivgi/kling-v[0-9.]+-(pro|std)/avatar']
modalities: ['VIDEO']
strengths: [identity_preservation, natural_motion, subtle_performance, high_resolution, long_duration, fast]
weaknesses: [dynamic_action, camera_control, physics, multi_character]
ideal_tasks: [avatar, lipsync]
avoid_tasks: [text_to_video, motion_control, multi_reference_image, text_to_image]
unverified_tags: []
sources:
  - {url: 'https://www.atlascloud.ai/models/bytedance/avatar-omni-human-v1.5', title: 'Atlas Cloud — Avatar Omni Human 1.5', accessed: '2026-10-02'}
  - {url: 'https://omnihuman-lab.github.io/v1_5/', title: 'OmniHuman-1.5 — página oficial de investigación (ByteDance)', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/atlascloud/infinitetalk', title: 'Atlas Cloud — InfiniteTalk', accessed: '2026-10-02'}
  - {url: 'https://arxiv.org/abs/2508.14033', title: 'InfiniteTalk: Audio-driven Video Generation for Sparse-Frame Video Dubbing (arXiv 2508.14033)', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/sync/lipsync-v3', title: 'Atlas Cloud — Sync.so Lipsync v3', accessed: '2026-10-02'}
  - {url: 'https://sync.so/docs/introduction', title: 'Sync Labs Docs — Introduction', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/veed/fabric-1.0/image-to-video', title: 'Atlas Cloud — VEED Fabric 1.0 Image-to-Video', accessed: '2026-10-02'}
  - {url: 'https://www.atlascloud.ai/models/veed/fabric-1.0/fast/image-to-video', title: 'Atlas Cloud — VEED Fabric 1.0 Fast Image-to-Video', accessed: '2026-10-02'}
  - {url: 'https://fal.ai/models/veed/lipsync', title: 'fal.ai — VEED Lipsync', accessed: '2026-10-02'}
  - {url: 'https://replicate.com/kwaivgi/kling-avatar-v2', title: 'Replicate — Kling Avatar V2 (kwaivgi) README', accessed: '2026-10-02'}
  - {url: 'https://fal.ai/models/fal-ai/kling-video/v2.6-pro/avatar', title: 'fal.ai — Kling Video v2.6 Pro Avatar', accessed: '2026-10-02'}
  - {url: 'https://fal.ai/models/fal-ai/kling-video/v2.6-std/avatar', title: 'fal.ai — Kling Video v2.6 Std Avatar', accessed: '2026-10-02'}
  - {url: 'https://docs.dev.runwayml.com/guides/models/', title: 'Runway Dev Docs — Models (incluye Act-Two, id act_two)', accessed: '2026-10-02'}
  - {url: 'https://academy.runwayml.com/tutorial/act-two-expressive-performances', title: 'Runway Academy — Act-Two Expressive Character Performances', accessed: '2026-10-02'}
  - {url: 'https://fal.ai/models/fal-ai/runway-act-two', title: 'fal.ai — Runway Act-Two', accessed: '2026-10-02'}
---

## Resumen

Familia de modelos que animan la boca y el rostro de una persona a partir de audio: desde una sola foto (avatar) o re-sincronizando los labios de un vídeo ya grabado (lipsync/doblaje). Incluye OmniHuman 1.5 de ByteDance (el más expresivo e interpretativo), InfiniteTalk (el único pensado para monólogos muy largos), Sync lipsync v3 (el estándar de doblaje profesional, 4K nativo [[6]](https://sync.so/docs/introduction)), VEED Fabric 1.0/fast (foto+audio rápido y barato, expuesto en Nexus bajo un endpoint "image-to-video" aunque el movimiento lo dicta el audio) y VEED lipsync (re-doblaje mínimo, el más económico), más Kling Avatar 2.6 pro/std de Kuaishou (foto+audio con buena nitidez facial y admite personajes animados o estilizados además de humanos reales, 1080p/48fps [[10]](https://replicate.com/kwaivgi/kling-avatar-v2)). Ninguno de ellos genera voz ni música: todos reciben el audio ya grabado y solo mueven la cara.

## Cuándo elegirlo y cuándo no

Elige esta familia si el objetivo es "esta persona (o personaje) habla/canta esto" con cámara estática o casi estática. No la elijas si necesitas movimiento de cámara real, acción corporal o un personaje moviéndose por un entorno: para eso usa Kling, Seedance, Wan, MiniMax H3 o Runway Gen-4.5 (vídeo general) y luego, si hace falta, re-sincroniza los labios con Sync o VEED lipsync sobre ese clip. Frente a Gemini Omni para vídeo (que genera audio nativo dentro del propio clip), esta familia gana cuando el audio ya existe y debe respetarse exactamente (doblaje, locución grabada, canción). Runway Act-Two (sin id propio en este catálogo de Nexus) es el competidor más cercano a OmniHuman cuando hace falta transferir una interpretación completa de un actor (gestos, cabeza, expresión), no solo sincronizar labios con audio; avisa de que no hay ficha dedicada si el usuario pide eso.

Dentro de la familia:
- **Doblaje/relocalización de un vídeo ya grabado** (cambiar idioma o voz sin tocar nada más) → Sync lipsync v3 si la fidelidad manda; VEED lipsync si el presupuesto manda.
- **Avatar desde una sola foto + guion largo** (podcast, clase, vídeo de +1 minuto) → InfiniteTalk (hasta 10 minutos en una sola llamada [[3]](https://www.atlascloud.ai/models/atlascloud/infinitetalk)).
- **Avatar con interpretación dramática, canto o gestos que acompañan la emoción del audio** → OmniHuman 1.5 (es el único que "lee" el subtexto emocional y mueve manos/cuerpo, no solo la boca [[2]](https://omnihuman-lab.github.io/v1_5/)).
- **Avatar rápido y barato desde foto + música o voz corta** → VEED Fabric 1.0 fast (480p/720p, facturación por segundo de audio [[8]](https://www.atlascloud.ai/models/veed/fabric-1.0/fast/image-to-video)).
- **Avatar con buena nitidez facial, personajes no humanos (cartoon, animal) o posibilidad de matizar el tono con texto** → Kling Avatar 2.6 pro (1080p/48 fps, admite prompt de texto que complementa al audio y acepta humanos realistas, animales, cartoons o personajes estilizados [[10]](https://replicate.com/kwaivgi/kling-avatar-v2)); su variante std es la opción más barata de Kling para lo mismo.

| Necesidad | Variante recomendada |
|---|---|
| Doblaje profesional, voz nueva sobre vídeo existente | Sync lipsync v3 |
| Doblaje low-cost, sin prompts, dos inputs | VEED lipsync |
| Avatar desde foto, guion/podcast largo (>1 min) | InfiniteTalk |
| Avatar con interpretación emocional, canto, gestos | OmniHuman 1.5 |
| Avatar rápido/barato desde foto + audio corto | VEED Fabric 1.0 fast |
| Avatar con mejor nitidez facial y tono ajustable por texto | Kling Avatar 2.6 pro |
| Avatar foto+audio, presupuesto ajustado, calidad decente | Kling Avatar 2.6 std |
| Avatar con personaje no humano (cartoon, animal, mascota) | Kling Avatar 2.6 pro/std |
| Spot corto foto+audio clasificado como "image-to-video" en el catálogo | VEED Fabric 1.0 (estándar o fast) |

## Linaje y variantes en Nexus

- **OmniHuman 1 → OmniHuman 1.5** (ByteDance): la v1.5 añade arquitectura "dual-system" (LLM multimodal + Diffusion Transformer, "Sistema 1/Sistema 2") para vídeos de más de un minuto, cámara continua, interacciones multi-personaje y lectura del contexto emocional del audio [[2]](https://omnihuman-lab.github.io/v1_5/). El id Nexus `bytedance/avatar-omni-human-v1.5` ya es esta generación reciente.
- **Sync Labs**: lipsync-1.9 → lipsync-2 → lipsync-2-pro → **sync-3** (id Nexus `sync/lipsync-v3`), la más reciente y la única con 4K nativo de cara; las anteriores están limitadas a 512×512 [[6]](https://sync.so/docs/introduction).
- **VEED Fabric 1.0**: dos tiers, `veed/fabric-1.0/image-to-video` (estándar) y `veed/fabric-1.0/fast/image-to-video` (más rápido y barato, mismo rango 480p/720p) [[7]](https://www.atlascloud.ai/models/veed/fabric-1.0/image-to-video)[[8]](https://www.atlascloud.ai/models/veed/fabric-1.0/fast/image-to-video). Siempre parte de imagen fija, no re-sincroniza vídeo; a pesar del nombre "image-to-video" en el id, su motor de movimiento es el audio, no una descripción de cámara/acción libre.
- **VEED lipsync** (`veed/lipsync`): producto distinto de Fabric, solo re-sincroniza labios de un vídeo existente, sin prompt [[9]](https://fal.ai/models/veed/lipsync).
- **InfiniteTalk** (`atlascloud/infinitetalk`): backbone Wan2.1 I2V-14B con cross-attention de audio dedicado y streaming que permite hasta 10 minutos por request, con modo de dos personas [[4]](https://arxiv.org/abs/2508.14033).
- **Kling Avatar 2.6** (Kuaishou/kwaivgi): ids `kwaivgi/kling-v2.6-pro/avatar` y `kwaivgi/kling-v2.6-std/avatar` — pro da mejor detalle facial y movimiento más suave; ambos a 1080p/48 fps con prompt de texto opcional que matiza el tono y admite personajes no humanos [[10]](https://replicate.com/kwaivgi/kling-avatar-v2).

## Fortalezas

- **identity_preservation**: todos preservan peinado, ropa y fondo del origen [[1]](https://www.atlascloud.ai/models/bytedance/avatar-omni-human-v1.5)[[9]](https://fal.ai/models/veed/lipsync).
- **natural_motion**: parpadeo, micro-expresiones y pose de cabeza realistas en OmniHuman 1.5, Sync v3 y Kling Avatar [[1]](https://www.atlascloud.ai/models/bytedance/avatar-omni-human-v1.5)[[10]](https://replicate.com/kwaivgi/kling-avatar-v2).
- **subtle_performance**: OmniHuman 1.5 interpreta el subtexto emocional del audio sin necesidad de prompt de texto [[2]](https://omnihuman-lab.github.io/v1_5/).
- **high_resolution**: Sync v3 llega a 4K nativo de cara; OmniHuman 1.5 y Kling Avatar a 1080p [[6]](https://sync.so/docs/introduction)[[10]](https://replicate.com/kwaivgi/kling-avatar-v2).
- **long_duration**: InfiniteTalk sostiene hasta 10 minutos por llamada mediante streaming; Sync admite hasta 30 minutos según el plan [[3]](https://www.atlascloud.ai/models/atlascloud/infinitetalk)[[6]](https://sync.so/docs/introduction).
- **fast**: VEED Fabric 1.0 fast y VEED lipsync están optimizados para turnaround rápido y bajo coste [[8]](https://www.atlascloud.ai/models/veed/fabric-1.0/fast/image-to-video).

## Debilidades

- **dynamic_action**: ninguno anima acción corporal compleja; el cuerpo apenas se mueve salvo gestos puntuales en OmniHuman 1.5.
- **camera_control**: la mayoría usa plano fijo heredado de la imagen/vídeo de entrada; solo OmniHuman 1.5 declara cámara continua, y es la excepción [[2]](https://omnihuman-lab.github.io/v1_5/).
- **physics**: no simulan física de ropa, pelo suelto ni objetos.
- **multi_character**: salvo el modo dual de InfiniteTalk y las interacciones de OmniHuman 1.5, el resto asume un único hablante por plano [[3]](https://www.atlascloud.ai/models/atlascloud/infinitetalk).

## Tareas ideales / Tareas que debe evitar

- Ideales: **avatar** (foto o personaje fijo + audio → vídeo que habla) y **lipsync** (vídeo ya grabado + audio nuevo → labios re-sincronizados: doblaje, cambio de voz, localización).
- Evitar: **text_to_video** (no generan escena desde cero), **motion_control** (no se controla trayectoria/cámara con precisión), **multi_reference_image** (una sola imagen/vídeo de identidad, no mezcla de referencias) y **text_to_image** (no generan imágenes fijas).
- **image_to_video** merece una aclaración aparte: VEED Fabric 1.0 vive en el catálogo bajo ese id de tarea, pero no es un modelo de image-to-video genérico (no acepta "la cámara hace un dolly hacia la ventana" ni "el personaje se levanta y camina"); solo anima foto+audio igual que el resto de la familia. Si el usuario pide movimiento de cámara libre o acción desde una imagen fija sin audio, deriva a la skill de image-to-video genérico (Kling, Seedance, Wan, Runway Gen-4.5), no a esta familia.

## Estructura de prompt recomendada

La mayoría de estos modelos **no necesita prompt de texto**: el input principal es imagen/vídeo + audio. Donde existe un campo de texto (OmniHuman 1.5, Kling Avatar, opcionalmente VEED Fabric), trátalo como una capa de dirección que **complementa**, no sustituye, al audio. Plantilla general:

```
[encuadre y plano: front-facing portrait / medium shot] + [sujeto: quién es, en 3-6 palabras, sin repetir rasgos ya visibles en la foto] + [tono/emoción con la que habla: calm confidence / heartfelt / enthusiastic] + [gesto opcional, uno solo: raises hand / nods / smiles] + [restricción de cámara si el modelo lo admite: static shot / slow push-in]
```

- Idioma: inglés funciona mejor en todos los campos de prompt; OmniHuman 1.5 entiende instrucciones en varios idiomas pero el audio puede ser multilingüe (chino, inglés, japonés, coreano, español, indonesio) independientemente del idioma del prompt [[1]](https://www.atlascloud.ai/models/bytedance/avatar-omni-human-v1.5).
- Longitud: 1–2 frases (15–40 palabras) para los que aceptan prompt; cero palabras para Sync v3 y VEED lipsync, que no tienen campo de prompt de estilo.
- Tono: prosa natural y descriptiva, no listas de tags ni `--parámetros` de Midjourney [verificar en el schema de Nexus si se aceptan flags].
- Sintaxis especial: ninguno de estos modelos usa `@tags`, `[corte]` ni timecodes en el prompt; la "sintaxis especial" real está en los campos no textuales (sync_mode en Sync v3, bounding boxes de hablante en InfiniteTalk dual).

## Plantillas por tarea

### Avatar (OmniHuman 1.5 / Kling Avatar 2.6 / InfiniteTalk)
```
[plano: front-facing portrait / medium shot of] + [sujeto en 3-6 palabras] + [verbo "speaks/sings" + tono emocional] + [gesto único opcional] + [cámara si el modelo la admite: static shot | slow push-in | continuous camera movement]
```
Ejemplos listos para pegar:
- Cinematográfico con personajes: "Front-facing medium shot of Mateo, a man in his thirties sitting across a kitchen table from his younger brother Vidal, speaking with quiet, heartfelt sincerity as he apologizes, pausing briefly to look down before meeting Vidal's eyes again, slow push-in, warm late-afternoon light."
- Producto / estilizado: "Front-facing portrait of a cheerful cartoon fox mascot wearing a tiny chef's hat, speaking with upbeat enthusiasm while presenting a new snack flavor, one quick hand gesture pointing at the product, static shot, bright studio lighting."

Diferencias por variante dentro de esta tarea:
- OmniHuman 1.5 es el único que sigue instrucciones de cámara continua y gestos corporales encadenados sin que se vean forzados; tolera prompts algo más largos y con matices emocionales encadenados ("primero duda, luego sonríe") [[2]](https://omnihuman-lab.github.io/v1_5/).
- Kling Avatar 2.6 pro/std acepta personajes no humanos (cartoon, animal, estilizado) además de humanos realistas; su prompt funciona mejor como una sola instrucción de tono, no como una secuencia de beats [[10]](https://replicate.com/kwaivgi/kling-avatar-v2).
- InfiniteTalk prácticamente no usa prompt de estilo: su input relevante es la imagen, el audio (hasta 10 minutos) y, en modo dual, las cajas de cada hablante; si se añade texto, mantenlo a una frase de contexto general, no de dirección escena por escena [[3]](https://www.atlascloud.ai/models/atlascloud/infinitetalk)[[4]](https://arxiv.org/abs/2508.14033).

No pongas en esta tarea:
- Diálogo escrito dentro del prompt esperando que el modelo lo "diga" — el habla sale del audio, no del texto.
- Movimiento de cámara agresivo (dolly rápido, barrido, zoom brusco) salvo en OmniHuman 1.5, y aun así con moderación.
- Más de un gesto corporal encadenado en Kling Avatar o InfiniteTalk — lo ignoran o producen artefactos.
- Coreografía corporal compleja o desplazamientos por el espacio — ninguno de estos modelos anima acción corporal real.

### Image-to-video (VEED Fabric 1.0 / VEED Fabric 1.0 fast)
```
[plano: front-facing portrait of] + [sujeto en pocas palabras] + [tono con el que habla, igual de simple que en avatar] + [máximo un gesto, opcional] + [sin instrucciones de cámara libre: el modelo no sigue trayectorias de dolly/paneo propias de un image-to-video genérico]
```
Ejemplos listos para pegar:
- Cinematográfico con personajes: "Front-facing shot of Vidal, a young man in a worn denim jacket, speaking calmly and warmly to the camera as if addressing his brother Mateo directly, a small reassuring nod halfway through, static shot, soft indoor lighting."
- Producto / estilizado: "Front-facing portrait of a sneaker brand spokesperson speaking with energetic excitement about a new release, one confident hand gesture, static shot, clean white studio background."

Diferencias por variante dentro de esta tarea:
- `veed/fabric-1.0/image-to-video` (estándar) tolera prompts algo más descriptivos y produce transiciones de expresión más suaves.
- `veed/fabric-1.0/fast/image-to-video` prioriza velocidad y coste bajo: usa frases más cortas y un único rasgo de tono — con prompts largos tiende a simplificar o ignorar matices secundarios [[8]](https://www.atlascloud.ai/models/veed/fabric-1.0/fast/image-to-video).

No pongas en esta tarea:
- Instrucciones de cámara libre ("dolly hacia la ventana", "el plano gira alrededor del personaje") — no es un image-to-video genérico, es foto+audio igual que el resto de la familia.
- Acción corporal o desplazamiento del personaje por el entorno.
- Prompts largos y con varios beats en la variante fast; simplifica a una sola idea de tono.

### Lipsync / doblaje (Sync lipsync v3 / VEED lipsync)
Estos dos no tienen campo de prompt de texto: el "prompt" real es la combinación de vídeo origen + audio nuevo (+ parámetro de sincronía si hay desajuste de duración). Plantilla de los inputs, no de texto libre:
```
video_url: [clip ya grabado con la cara visible durante todo el segmento] + audio_url: [pista nueva, cualquier idioma] + sync_mode (solo Sync v3): cut_off | loop | bounce | silence | remap
```
Ejemplos listos para pegar (como descriptor de la llamada, no como prompt de escena):
- Doblaje profesional: `video_url` = entrevista grabada en inglés con el presentador mirando a cámara; `audio_url` = locución doblada en español, 4 segundos más larga que el original; `sync_mode: remap` para estirar el ritmo sin cortar la toma [[5]](https://www.atlascloud.ai/models/sync/lipsync-v3).
- Doblaje económico: `video_url` = clip corto de un creador hablando a cámara; `audio_url` = la misma locución regrabada con otra voz de TTS, misma duración exacta — VEED lipsync no necesita `sync_mode` porque no lo expone [[9]](https://fal.ai/models/veed/lipsync).

No pongas en esta tarea:
- Prompts de estilo o de escena ("plano cinematográfico", "luz dorada") — se ignoran o el esquema los rechaza, porque no hay campo de texto de dirección.
- Un vídeo origen con la cara en perfil, oculta parcialmente o con oclusión: la sincronía e identidad se degradan en toda la familia.
- Dejar que `sync_mode` quede en `cut_off` por defecto cuando el audio es mucho más largo que el vídeo — corta contenido relevante; usa `remap` o `loop` según el caso.

## Idioma, longitud y prompt negativo

Prompt en inglés cuando exista campo de texto; el audio puede estar en cualquier idioma soportado (OmniHuman 1.5: chino, inglés, japonés, coreano, español, indonesio [[1]](https://www.atlascloud.ai/models/bytedance/avatar-omni-human-v1.5)). Ninguna ficha documenta `negative_prompt` para esta familia — confirma en el schema en vivo de Nexus; si no existe, simplifica el prompt positivo en vez de listar negaciones.

## Referencias e imagen inicial

Avatar: una sola imagen frontal, buena resolución, rostro visible sin ángulos extremos ni oclusión [[10]](https://replicate.com/kwaivgi/kling-avatar-v2). Lipsync: el "vídeo de referencia" es el propio clip a redoblar, con la cara visible durante todo el segmento. InfiniteTalk en modo de dos personas requiere además las cajas de cada hablante y su pista de audio por separado [[3]](https://www.atlascloud.ai/models/atlascloud/infinitetalk).

## Cámara y movimiento

Plano fijo por defecto en Sync, VEED (Fabric y lipsync) y Kling Avatar. OmniHuman 1.5 es la excepción documentada: admite movimiento de cámara continuo cuando se pide por prompt [[2]](https://omnihuman-lab.github.io/v1_5/) — no lo asumas en el resto, y especialmente no en VEED Fabric a pesar de su clasificación como "image-to-video" en el catálogo.

## Consistencia de personaje

Garantizan identidad, peinado, vestuario y fondo del origen durante el clip; no garantizan consistencia entre generaciones distintas (sin semilla de personaje reutilizable documentada). Para una serie del mismo avatar, reutiliza la misma imagen/vídeo de referencia en cada llamada.

## Audio y diálogo

Todos son **audio-driven**: no generan voz ni música, solo mueven cara/labios para encajar con el audio del usuario. OmniHuman 1.5 interpreta además prosodia y semántica para animar gestos y emoción, útil para "digital singer" o interpretación dramática sin texto adicional [[2]](https://omnihuman-lab.github.io/v1_5/). Sync v3 está pensado para doblaje: cambia idioma o voz de un vídeo sin re-grabar [[5]](https://www.atlascloud.ai/models/sync/lipsync-v3).

## Restricciones e incompatibilidades conocidas

- No generan audio ni voz: siempre hace falta un archivo de audio ya preparado (TTS o grabación real).
- VEED lipsync y Sync no admiten dirección de estilo por texto; un prompt de escena se ignora o es rechazado por el esquema.
- Ángulos extremos, oclusión facial u origen de baja resolución degradan sincronía e identidad en toda la familia [[10]](https://replicate.com/kwaivgi/kling-avatar-v2).
- Generación asíncrona habitual (se envía tarea, se consulta resultado); el tiempo de espera varía con la duración del audio [[1]](https://www.atlascloud.ai/models/bytedance/avatar-omni-human-v1.5).

## Errores habituales

- Pedir movimiento de cámara o acción corporal compleja a VEED, Sync o Kling Avatar: lo ignoran o producen artefactos.
- Escribir el diálogo dentro del prompt esperando que el modelo lo "diga": el habla sale del audio, el prompt solo matiza tono/gesto.
- Usar una foto de perfil o con el rostro cubierto: identidad y lipsync se degradan notablemente.
- Pedir subtítulos o texto en pantalla: no es tarea de esta familia.
- Olvidar `sync_mode` en Sync v3 cuando audio y vídeo original tienen duraciones distintas, dejando que `cut_off` corte contenido relevante.
- Tratar `veed/fabric-1.0/image-to-video` como un image-to-video genérico y pedirle trayectorias de cámara o acción corporal propias de Kling/Seedance/Wan.

## Recomendaciones de prompting

- Si hay campo de prompt, usa frases cortas con tono y un único gesto, no listas de acciones.
- Deja que el audio lleve el peso del diálogo; el prompt es un matiz de dirección, no un guion.
- Para doblaje, prioriza Sync v3 si la fidelidad facial importa más que el coste, y VEED lipsync para volumen alto con presupuesto ajustado.
- Para guiones largos (podcast, clase), usa InfiniteTalk en vez de encadenar varias llamadas cortas.
- Para interpretación emocional o canto, usa OmniHuman 1.5 con emociones simples ("quiet confidence", "heartfelt confession").
- Para personajes no humanos (mascota, cartoon, animal) usa Kling Avatar 2.6, no OmniHuman ni InfiniteTalk, cuyos ejemplos documentados son siempre humanos.
- No pidas a VEED Fabric 1.0 movimiento de cámara libre: es foto+audio como el resto de la familia, aunque su id lo clasifique como "image-to-video".
- Verifica en el esquema en vivo de Nexus si el id concreto acepta `negative_prompt`, `sync_mode` o resolución antes de prometerlo.
- Si el usuario pide transferir toda la interpretación de un actor (gestos + cuerpo), aclara que esta familia no lo cubre tan bien como Runway Act-Two, sin ficha propia en este catálogo.

## Fuentes y fecha

[1] Atlas Cloud — Avatar Omni Human 1.5 — https://www.atlascloud.ai/models/bytedance/avatar-omni-human-v1.5 — consultado 2026-10-02
[2] OmniHuman-1.5 — página oficial de investigación (ByteDance) — https://omnihuman-lab.github.io/v1_5/ — consultado 2026-10-02
[3] Atlas Cloud — InfiniteTalk — https://www.atlascloud.ai/models/atlascloud/infinitetalk — consultado 2026-10-02
[4] InfiniteTalk: Audio-driven Video Generation for Sparse-Frame Video Dubbing (arXiv 2508.14033) — https://arxiv.org/abs/2508.14033 — consultado 2026-10-02
[5] Atlas Cloud — Sync.so Lipsync v3 — https://www.atlascloud.ai/models/sync/lipsync-v3 — consultado 2026-10-02
[6] Sync Labs Docs — Introduction — https://sync.so/docs/introduction — consultado 2026-10-02
[7] Atlas Cloud — VEED Fabric 1.0 Image-to-Video — https://www.atlascloud.ai/models/veed/fabric-1.0/image-to-video — consultado 2026-10-02
[8] Atlas Cloud — VEED Fabric 1.0 Fast Image-to-Video — https://www.atlascloud.ai/models/veed/fabric-1.0/fast/image-to-video — consultado 2026-10-02
[9] fal.ai — VEED Lipsync — https://fal.ai/models/veed/lipsync — consultado 2026-10-02
[10] Replicate — Kling Avatar V2 (kwaivgi) README — https://replicate.com/kwaivgi/kling-avatar-v2 — consultado 2026-10-02
[11] fal.ai — Kling Video v2.6 Pro Avatar — https://fal.ai/models/fal-ai/kling-video/v2.6-pro/avatar — consultado 2026-10-02
[12] fal.ai — Kling Video v2.6 Std Avatar — https://fal.ai/models/fal-ai/kling-video/v2.6-std/avatar — consultado 2026-10-02
[13] Runway Dev Docs — Models (incluye Act-Two, id act_two) — https://docs.dev.runwayml.com/guides/models/ — consultado 2026-10-02
[14] Runway Academy — Act-Two Expressive Character Performances — https://academy.runwayml.com/tutorial/act-two-expressive-performances — consultado 2026-10-02
[15] fal.ai — Runway Act-Two — https://fal.ai/models/fal-ai/runway-act-two — consultado 2026-10-02
