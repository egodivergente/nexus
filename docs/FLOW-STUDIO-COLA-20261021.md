# Flow Studio: no abortar el envío si Flow está ocupado — 2026-10-21

Síntoma: «No se pudo completar la acción en Flow.» al enviar desde Flow Studio (2 referencias, ×4),
con el botón detenido en «Enviando a Flow…».

Causa: `sendAll()` repetía `bridgeStatus()` y abortaba con «Flow ya no está listo» si Flow no estaba
listo en ese instante (generando el encargo anterior, cambio AXELPC/ASUS…). El aviso genérico ocultaba
el motivo. El envío solo encola en la cola local, que ya espera a que Flow esté listo.

Cambio (`FlowStudioScreen.kt`): solo se detiene si el puente no responde (con su motivo real); si
responde pero Flow está ocupado, se suben las referencias y los prompts entran en la cola. El aviso
muestra el mensaje real cuando no hay traducción amigable.

Entrega: rama `ccr-089ea54a-2ujt1h` (sobre `codex/agent-minimal-controls-20261021`), 1.9.66-agent / vc100.
- Compilación: AXELPC, `:app:assembleAgentPreview`, BUILD SUCCESSFUL (9m55s), en
  `C:/Users/User/.codex/worktrees/nexus-ccr-flowstudio`.
- APK: `com.vidadomu.nexus.agent` 1.9.66-agent-preview / vc100, 43 116 285 bytes,
  SHA-256 `6001307854FCFEFA74F46BFDDE58C891959B881D4845D9B6E81FD6A558343BA1`.
- Firma: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127` (la original).
- No instalada; sin pruebas en dispositivo. ADB en AXELPC solo veía el S25 (`SM_S938B`); el Pixel no estaba conectado.
