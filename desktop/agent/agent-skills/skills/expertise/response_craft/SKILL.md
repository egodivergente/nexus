---
id: response_craft
kind: expertise
title: Jerarquía y paneles en las respuestas
version: 1
updated: 2026-10-19
applies_to: []
triggers: [recuadro, resumen, resaltar, encuesta, comparar opciones, checklist, lista de pasos, panel]
---

Cómo dar jerarquía visual a una respuesta del chat de Nexus y cuándo usar un panel interactivo en vez de prosa. La app pinta cada marca con su tema (claro/oscuro); el texto nunca incluye colores, tamaños ni HTML.

## Regla de oro
Cada recurso visual gasta atención del usuario. Úsalo solo si cambia lo que el usuario hace o entiende; una respuesta corta no necesita nada de esto. Si dudas, texto llano.

## Escalera de énfasis (de menos a más)
1. **Negrita** (`**x**`): el término clave de cada punto de una lista.
2. Resaltado (`==x==`): lo único que el usuario no puede perderse (un dato que cambia su decisión). Máximo 2 por respuesta; si todo está resaltado, nada lo está.
3. Recuadro `:::tip` o `:::result`: una pauta útil o el resultado de lo que se hizo.
4. Recuadro `:::warn`: coste, pérdida de datos o acción irreversible. Nunca para avisos genéricos.
5. Recuadro `:::decision`: lo que necesitas que el usuario decida para seguir.

Los títulos `##` solo si hay varias partes. No uses emojis ni mayúsculas para llamar la atención.

## Recuadros
Se abren con `:::tipo` en una línea y se cierran con `:::` en otra. Dentro admiten párrafos y listas (no otros recuadros).

```
:::tldr
Kling 3.0 encaja mejor: mantiene la cara en image-to-video y entra en tu presupuesto.
:::
```

- `:::tldr` abre respuestas de más de 6 líneas con la conclusión en 1-2 líneas; el detalle va debajo.
- Un recuadro por función y respuesta como norma; más de tres seguidos es ruido.
- Cierra siempre con `:::`. Si lo olvidas, el recuadro llega hasta la primera línea en blanco.

## Paneles interactivos (` ```nexus-ui `)
Un bloque con un único objeto JSON válido (comillas dobles, sin comentarios). Máximo uno por respuesta. Lo que el usuario toque te llega como su siguiente mensaje, igual que si lo hubiera escrito: el panel no ejecuta nada, no gasta y no cambia ningún encargo. Un JSON inválido o fuera de límites se muestra como bloque de código, así que revísalo antes.

### Encuesta (`poll`)
Para elegir con descripción de cada opción, o marcar varias. 2-6 opciones; `multi: true` permite marcar varias y enviar.
```nexus-ui
{"type":"poll","id":"ratio","question":"¿En qué formatos lo quieres?","multi":true,
 "options":[{"id":"h","label":"16:9","hint":"YouTube y cine"},{"id":"v","label":"9:16","hint":"Reels y Shorts"},{"id":"s","label":"1:1"}]}
```
Las opciones pueden ser textos sueltos: `"options":["Rápido","Equilibrado"]`. Al enviar, el usuario escribe las etiquetas marcadas separadas por comas.

### Pasos que se tachan (`steps`)
Para un procedimiento que el usuario sigue fuera del chat. 1-12 pasos cortos; el avance se queda en el panel y no se envía.
```nexus-ui
{"type":"steps","title":"Antes de lanzar el lote","items":["Revisar las referencias","Confirmar la duración","Pulsar GENERAR en la tarjeta"]}
```

### Comparación (`compare`)
Para elegir entre 2-6 candidatos lado a lado con una frase cada uno. Si solo vas a comparar datos, una tabla basta.
```nexus-ui
{"type":"compare","title":"Dos caminos","options":[{"label":"Kling 3.0","hint":"Cara estable, 10 s"},{"label":"Seedance","hint":"Más movimiento"}]}
```

## Cuándo NO usar un panel
- Elección simple de 2-4 opciones, un sí/no con matiz o una confirmación: usa `suggest_replies` (botones sobre el teclado). Un panel solo si necesitas descripciones, multiselección, pasos o comparación.
- Confirmar un gasto: el envío siempre es el botón de la tarjeta, nunca un panel ni una respuesta rápida.
- Cuando la respuesta se deduce de lo que ya sabes del proyecto o de la conversación: decide y dilo.
- Preguntas abiertas.

## Errores habituales
- Resaltar tres frases «importantes» en el mismo párrafo.
- Meter un `:::warn` para algo que no cuesta ni se pierde.
- Un panel con opciones que no son mutuamente claras o con texto largo (usa `hint` corto, máximo ~120 caracteres).
- Repetir en prosa lo que el panel ya muestra.
