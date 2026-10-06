---
id: prompt_engineer
kind: expertise
title: Ingeniero de prompts
version: 1
updated: 2026-10-02
applies_to: [VIDEO, IMAGE]
triggers: [prompt, reescribir, mejora el prompt, cómo lo describo]
---

## Qué hace esta skill
Da la estructura y disciplina para escribir prompts de imagen/vídeo efectivos, independientemente del modelo final. No sustituye a la skill específica del modelo: siempre adapta el resultado a la sección de "Estructura de prompt recomendada" de la skill del modelo elegido (cárgala con `load_skill` antes de escribir el prompt final).

## Estructura base del prompt
Sigue este orden, en frases cortas y concretas:
`Sujeto → Acción → Cámara → Luz → Estilo → Restricciones`
- **Sujeto**: quién/qué, con descriptores fijos si es un personaje recurrente (ver skill `character_consistency`).
- **Acción**: qué hace, con microexpresiones y timing concretos ("sonríe lentamente a mitad de plano") en vez de adjetivos vagos ("feliz", "dramático").
- **Cámara**: un único movimiento de cámara por plano (ver skill `camera_motion`); no mezclar paneo + zoom + travelling en la misma generación.
- **Luz**: fuente, color, dirección (ej. "luz dorada de atardecer desde la izquierda").
- **Estilo**: referencia visual/cinematográfica concreta (ej. "cinematográfico realista", "videoclip pop urbano").
- **Restricciones**: qué debe mantenerse igual o evitarse (ej. "sin cambiar el color del pelo", "sin texto en pantalla").

## Procedimiento
1. Carga la skill del modelo destino (`load_skill`) y reutiliza su plantilla concreta de i2v/t2v/edición si existe; esta skill transversal es el criterio general, la skill del modelo manda en el detalle.
2. Redacta el prompt final **en inglés**, salvo que la skill del modelo indique explícitamente otro idioma.
3. Si el schema del modelo da una longitud máxima de prompt, respétala recortando restricciones secundarias antes que sujeto/acción.
4. Explica al usuario, **en español**, qué vas a generar y por qué elegiste esa estructura (2-3 frases), antes o junto con el prompt en inglés.
5. Para image-to-video, no repitas en el prompt lo que ya es visible en la imagen de referencia: describe solo el cambio o movimiento nuevo (ver skill `character_consistency`).

## Errores a evitar
- Adjetivos vagos sin anclaje temporal o físico ("una escena épica" en vez de describir qué pasa y cuándo).
- Varios movimientos de cámara a la vez en un plano corto.
- Volver a describir el aspecto físico de un personaje que ya viene de una imagen de referencia.
- Ignorar la plantilla específica de la skill del modelo elegido.
- Prompts en español cuando el modelo funciona mejor en inglés (confírmalo en la skill del modelo).

## Ejemplo breve
En vez de "Mateo canta feliz en la azotea, noche bonita", escribe: "Mateo (sujeto fijo) → levanta el micrófono y cierra los ojos a mitad de la frase (acción con timing) → plano fijo (cámara) → luces de la ciudad detrás, tono azulado (luz) → cinematográfico, grano sutil de película (estilo) → mantener la chaqueta vaquera, sin texto en pantalla (restricciones)".

## Cuándo delegar en otra skill
Si la petición del usuario cae claramente en el ámbito de otra skill de esta lista (p. ej. elección de modelo, coste, continuidad entre planos, movimiento de cámara), delega explícitamente en ella con `load_skill` en vez de improvisar ese criterio aquí.
