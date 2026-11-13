# Latencia del agente: GPT-6.1 Terra, Gemini 4 Hydrogen y Gemini 4 Argon (13-11-2026)

Banco `agent-core/src/test/kotlin/com/vidadomu/nexus/agent/core/BridgeLatencyBench.kt` (commit `f9107704`), ejecutado en AXELPC contra el puente AXEL del **Pixel 8 Pro** (vc208, `adb forward tcp:18788 tcp:8788`). Agente real de Nexus (prompt de sistema, herramientas y rondas de `AgentOrchestrator`) con el catálogo de prueba de `Fixtures.kt` y backend falso: **ninguna generación enviada** (`submitted = 0` en los 18 turnos). Nivel medio en los tres. 3 rondas, orden alterno. Resultados crudos: `C:\Users\User\nexus-bench-full.jsonl`.

| Turno | Terra | Hydrogen | Argon |
|---|---|---|---|
| Encargo (foto → vídeo 5 s con Kling O3 Pro, Seedance 2.0 y Veo 3.1), mediana | 42,9 s | **35,3 s** | 49,0 s |
| — tiempos | 35,3 / 42,9 / 48,0 | 35,3 / 37,6 / 29,8 | 46,5 / 49,0 / 83,9 |
| — llamadas al modelo | 2 | 3 | 3 (5 en la ronda de 83,9 s) |
| Consulta («¿qué modelos de vídeo con sonido tengo?»), mediana | 39,6 s | **17,2 s** | 30,9 s |
| — tiempos | 62,8 / 39,6 / 37,4 | 25,0 / 14,4 / 17,2 | 30,9 / 20,7 / 33,1 |
| Segundos por llamada al modelo (aprox.) | ≈20 | **≈9–12** | ≈15–16 |

El tiempo de herramientas de Nexus fue de 4–290 ms por turno: casi todo el tiempo es el modelo y el puente.

## Hallazgos

1. **Hydrogen es el más rápido por llamada** (más o menos la mitad que Terra) y funciona en el puente del Pixel aunque su `/health` solo anuncie Argon.
2. **Veo 3.1 solo admite 4, 6 u 8 s.** Hydrogen y Argon pasaron los 5 s pedidos: Veo falló en el primer `prepare_orders` y lo rehicieron con 6 s, lo que cuesta una llamada más. Terra no pasó duración: Veo quedó en 8 s por defecto (1,6 USD) sin respetar los 5 s pedidos.
3. **Defecto de Nexus:** cuando un lote se completa en dos `prepare_orders`, el texto final solo resume la última llamada («Preparado 1 encargo») aunque existan las 3 tarjetas.
4. Argon, en una ronda, retocó los planes con `update_plan` dos veces (83,9 s) sin que se pidiera.

## Mejoras propuestas en Nexus (independientes del modelo)

- `prepare_orders`: ajustar un parámetro común al valor admitido más cercano de cada modelo (5 s → 4 o 6 s en Veo) y avisarlo en `adaptations`, en vez de fallar. Quita la llamada extra.
- Resumen final del lote: contar todas las tarjetas preparadas en el turno, no solo las de la última llamada.

Límites: catálogo de prueba de 6 modelos (el real tiene cientos), un solo dispositivo y red, 3 repeticiones.

# Acierto en tareas complejas (13-11-2026)

Banco `AgentQualityBench.kt` (commit `b7c5ebc1`), mismo puente del Pixel, nivel medio, 2 rondas con orden alterno, ninguna generación enviada. Resultados crudos: `C:\Users\User\nexus-quality-q1.jsonl`.

| Caso (comprobaciones) | Terra | Hydrogen | Argon |
|---|---|---|---|
| Guion de escena con reglas del proyecto (6) | 6/6 · 6/6 (≈19 s) | 6/6 · 6/6 (≈16–21 s) | 6/6 · 6/6 (≈13–18 s) |
| Conversación larga, 4 turnos con reglas iniciales (7) | 5/7 · 5/7 (86–109 s) | **7/7 · 7/7** (35–83 s) | 7/7 · 7/7 (100–170 s) |
| Montaje en Nexus Cut de 4 pasos en un cambio (5) | 2/5 · 2/5 (68–80 s) | error del puente · 0/5 | **3/5 · 3/5** (130 s) |
| Revisión de 2 imágenes contra un encargo (3) | 1/3 · 1/3 | **3/3 · 3/3** (17–19 s) | 3/3 · 3/3 (25–27 s) |
| **Total** | 28/42 | 32/42 | **38/42** |

## Qué hay detrás

- **Guion:** los tres respetan las reglas del proyecto (chaqueta verde, gafas de Lucía, nunca de noche) sin herramientas: la memoria del proyecto ya va en el prompt.
- **Conversación larga:** Terra no aplicó al vídeo de Seedance las reglas del primer turno (10 s y 1080p) en ninguna ronda, aunque al final las recordaba. Hydrogen y Argon, sí.
- **Revisión:** Terra contestó «no puedo ver sus píxeles» en ambas rondas: las imágenes no llegan a Codex por el puente (limitación de Nexus/puente, no del modelo; no se inventó el veredicto). Hydrogen y Argon acertaron cuál cumplía con el orden alternado.
- **Nexus Cut:** ningún modelo lo resolvió entero. Argon dejó bien orden, corte y rótulo tras varios intentos (5 a 11 `cut_edit`, de 1 a 5 rechazados) y en 3 pasos en vez de 1. Terra hizo un solo cambio sin errores pero colocó las tomas detrás de la música (en 12 s y 60 s), así que el corte cayó en otro sitio. Hydrogen falló a la primera y no reintentó; en otra ronda el puente devolvió una respuesta vacía.

## Defectos de Nexus que explican parte del fallo en Cut

1. «Música a −12 dB» en un único cambio es imposible hoy: `audio.set` necesita el id del clip y ese id no existe hasta después de colocarlo. Propuesta: que lo colocado quede seleccionado dentro de la transacción (las operaciones siguientes sin `clip_ids` lo usan) y que `timeline.place` acepte volumen por elemento.
2. `timeline.place` sin `at_us` coloca tras el final de todas las pistas destino, incluida la de audio vinculada: si la música cayó en A1, las tomas van detrás. Propuesta: al final de la pista de imagen.
3. Las imágenes adjuntas no llegan a Codex por el puente.
