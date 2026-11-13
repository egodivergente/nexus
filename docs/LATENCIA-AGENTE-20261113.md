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
