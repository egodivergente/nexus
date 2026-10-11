---
id: cost_optimizer
kind: expertise
title: Optimizador de coste
version: 2
updated: 2026-10-08
applies_to: [VIDEO, IMAGE]
triggers: [cuánto cuesta, más barato, versión de prueba, presupuesto, gasto]
---

## Qué hace esta skill
Ayuda a elegir configuraciones y conocer costes sin interrumpir el trabajo creativo.

## Envío de encargos
1. Google Flow con acceso directo usa la cuenta conectada: una petición de generar se ejecuta directamente, sin tarjetas, cotizaciones ni confirmaciones adicionales. Se admiten hasta 20 resultados por encargo, con prompts distintos si se solicitan. El formato inicial es 16:9 y se respeta cualquier formato compatible que el usuario elija.
2. Para proveedores de pago, prepara todos los encargos solicitados con sus modelos, prompts y parámetros. El clic en GENERAR autoriza ese conjunto concreto de peticiones. Un lote puede incluir varios modelos; no exijas otro clic, diálogo o confirmación por cada petición.
3. Un coste desconocido se indica como «Coste no disponible». No lo presentes como cero ni bloquees los demás encargos por no poder consultar una cotización.
4. No reenvíes automáticamente una petición cuyo envío sea incierto. Un fallo concreto queda identificado y las demás peticiones continúan.

## Procedimiento
1. Consulta el schema real para conocer duración, resolución, cantidad y medios admitidos. No inventes una versión barata que el modelo no ofrece.
2. Consulta la skill del modelo para redactar el prompt con su estructura e idioma. Si excede su límite real, resume conservando intención, referencias y restricciones; conserva también el original. El usuario no necesita hacer esta adaptación manualmente.
3. Usa estimate_generation_cost cuando corresponda y presenta la estimación disponible junto al encargo. Si no hay una tarifa verificable, indícalo y continúa preparando las peticiones.
4. Si el usuario pide una prueba económica, elige opciones compatibles de menor duración o resolución. No añadas pruebas ni generaciones extra por iniciativa propia.
5. Si pide varias escenas, variantes o modelos, prepara el conjunto completo. No reduzcas su petición a una sola generación ni impongas confirmaciones una a una.

## Cuándo delegar en otra skill
Para criterios específicos de modelo, continuidad, personaje o movimiento, carga la skill pertinente con load_skill antes de redactar el prompt.
