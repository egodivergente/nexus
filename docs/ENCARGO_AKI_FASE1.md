# Encargo para Aki — Fase 1, siguientes puntos

Estado: tu commit `c4e63ca` (Biblioteca) está integrado en la 1.9.40 (vc74) de la rama `codex/windows-corte-integration-20261018` (commit `e96b90e`).
Cambio mío: la cuadrícula (`LazyVerticalGrid`) no admite `stickyHeader`, así que la cabecera de día quedó como `item(span=…)` normal. No lo vuelvas a poner.

## Lo que sigue (lo más pesado primero)

1. **Historial** (`GenerationsScreen.kt`, `GenerationSummaryRow.kt`):
   - Un componente único de coste (€) por fila.
   - Total de gasto por día en la cabecera del día.
   - Fallos con el motivo en una línea y botón «Reintentar».
   - Prompt a 2 líneas.
2. El siguiente punto de `docs/PROMPT_MAESTRO_MEJORAS_UX.md` que toque.

## Reglas

- Parte de `codex/windows-corte-integration-20261018` (fetch antes de empezar), no de `feat/byteplus-custom-providers-20261017`.
- Un commit por punto en tu rama `feat/ux-fase1-20261018`; push sin force.
- No toques versión, `CHANGELOG.md` ni `gradlew.bat`. Eso lo hace el PC al entregar.
- No contrates servicios ni generes cargos.
- Al subir, deja en el mensaje del commit qué punto cierra. El PC integra, compila e instala; si falla, te devuelve el error.
