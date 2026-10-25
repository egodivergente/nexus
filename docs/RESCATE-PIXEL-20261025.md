# Rescate Nexus en Pixel 8 Pro — 25/10/2026

Rama: `claude/rescate-pixel-20261025` (desde `codex/artbox-resume-20261024` @ `a31bb8a`, la 1.9.76/vc110 instalada en el Pixel).
Dispositivo de pruebas: **Pixel 8 Pro** (`husky`, serie `38271FDJG00264`, Android 17 `CP3A.260905.009`), por ADB inalámbrico desde AXELPC.
El **Samsung S25 Ultra** (`SM_S938B`) estaba conectado al mismo ADB y **no se ha tocado**: todos los comandos usan `-s` con la serie del Pixel y el script `pixel-qa.ps1` aborta si el modelo no es «Pixel 8 Pro».

Estados usados: `comprobado`, `corregido y probado`, `implementado sin probar`, `bloqueado con causa`, `aplazado explícitamente`.

## 1. Fuente de verdad encontrada

- `main` estaba en 1.9.29/vc63. El trabajo vivía en ~86 ramas; la más alta publicada era 1.9.75/vc109.
- La **1.9.76/vc110 instalada en el Pixel no existía en GitHub**: estaba en `C:\Users\User\.codex\worktrees\artbox-catalog-fixes` (rama `codex/artbox-resume-20261024`, 5 commits locales). Se publicó tal cual (`a31bb8a`) antes de modificar nada. Sus 3 cambios sin commitear (aprovisionamiento local de clave fal, finales de línea de `gradlew.bat`) se dejaron intactos en ese worktree.
- El trabajo de esta rama se compila en un worktree nuevo: `C:\Users\User\.codex\worktrees\claude-rescate-pixel`.

## 2. Protección de datos

- Copia completa de los datos de la app en el Pixel antes de instalar nada (`run-as … tar`, sin `cache`/`code_cache`):
  `C:\Users\User\nexus-build-out\pixel-baseline-20261025\pixel-agent-data-backup.tar`, 5.745,7 MB,
  SHA-256 `B978D6196925A3AFA90D03B2F3A96708118334FFA301BDB412777F520D92C776`.
  Verificada con `tar -t`: 3.339 entradas, 0 errores, 3 bases de datos, 24 preferencias, 64 conversaciones.
- La APK se firma con la misma identidad que la instalada (`~/.gradle/nexus-agent-signing.properties`), para actualizar in situ sin desinstalar ni borrar datos.

## 3. Línea base medida en el Pixel (1.9.76/vc110)

| Evidencia | Hallazgo |
|---|---|
| `logcat -b crash` | `ForegroundServiceDidNotStopInTimeException` (dataSync, `SystemForegroundService`) a las 02:19:59 |
| `dumpsys dropbox` | ANR 23/10 ×5, 24/10 ×2, 25/10 ×4 (00:41, 00:48, 01:16, 02:19) y uno en vivo a las 05:06 |
| Traza ANR 1.9.74 | hilo principal deserializando `ChatItem$Assistant` (historial del chat) |
| Trazas ANR 1.9.76 | «Application does not have a focused window», hilo principal ocioso |
| `exit-info` | ANR y crash consecutivos 02:19:47/02:19:59 |
| Prefs `catalog_customization_v1` | ninguna clave oculta guardada: «Quitar Gemini 2.5 Flash» / «Nano Banana 2 Lite» nunca persistieron |
| Arranque en frío limpio | `am start -W` TotalTime 2.564 ms |

**Factor del sistema observado (no es código de Nexus):** con Alexia (`com.axel.library`) abierta como *burbuja de app* expandida (modo burbujas de Android 17), SystemUI dejó una capa congelada encima; Nexus tenía el foco del gestor de ventanas pero no era visible, y cualquier tecla producía ANR atribuidos a Nexus. A las 02:20:11 **`com.android.settings` registró el mismo ANR**. Se resolvió deteniendo Alexia (en pausa, sin borrar datos) y con el reinicio de SystemUI que siguió.

## 4. Causas raíz y correcciones (P0)

| ID | Síntoma | Causa | Corrección | Commit |
|---|---|---|---|---|
| P0-1 | Modal «Quitar …» bloqueado | `setCatalogHidden` en el hilo principal recalculaba `captureHiddenRoutes`: clasificadores con `Regex` compiladas en cada llamada × cada modelo × cada clave oculta, sobre la lista duplicada, bajo el monitor del refresco | Claves de visibilidad una vez por modelo, coincidencia por conjunto, regex precompiladas, títulos memorizados; la UI se actualiza al instante y el recálculo va a un hilo serie; `commit()` fuera del hilo principal | `5706f31` |
| P0-2 | ANR al arrancar («Continuar en…») | `projectActivity()` dentro del comparador de ordenación decodificaba todas las conversaciones por comparación, en el hilo principal | Índice incremental de metadatos (memoria + `conversations-index-v1.json`), recalculado en IO solo para archivos cambiados; pantallas observan un `StateFlow` | `5706f31` |
| P0-3 | ANR 1.9.74 (chat) | `restoreChat()` decodificaba el chat en el `init` del ViewModel | Decodificación en IO antes de `chatsReady`; el autoguardado no puede escribir un estado vacío; abrir conversación/proyecto también en IO | `5706f31` |
| P0-4 | Crash dataSync | Copia periódica a Drive (15 min) promovida a servicio dataSync: agota la cuota diaria de Android 15+; WorkManager 2.9.1 no atiende `onTimeout` | WorkManager 2.10.0; la copia periódica no se promueve; promoción denegada no hace fallar trabajos | `5706f31` |

## 5. Otras correcciones de la auditoría

| ID | Incidencia (vídeo) | Corrección | Commit | Estado |
|---|---|---|---|---|
| A-1 | Long-press falla / menú distinto según zona (V1 03:46, 06:56) | `messageLongPress` en fase Initial, cancelable por desplazamiento | `6555e21` | implementado sin probar |
| A-2 | Mensajes largos del usuario sin plegar (V1 01:54) | Plegado a 8 líneas con «Expandir»/«Contraer»; respuestas nunca se pliegan | `6555e21` | implementado sin probar |
| A-3 | Vídeo sin Play cómodo (V1 03:46) | Portada + Play de 60 dp; reproductor creado al pulsar | `65302ed` | implementado sin probar |
| A-4 | Micrófono «No lo he entendido» | El texto lo muestra el reconocedor del sistema; se dejó de forzar el modo sin conexión | `2574702` | implementado sin probar (requiere voz humana) |
| A-5 | Renombrar a «Upscale imagen/vídeo» (V2 01:09) | Renombrado con migración de claves guardadas | `0eb1e67` | implementado sin probar |
| A-6 | «Desde» sin precio | Solo precios reales > 0; sin nota «Precios desde» si nada tiene tarifa | `a9358ee`, `7f6d537` | implementado sin probar |
| A-7 | Tarjetas con huecos (V1 11:22, V2 02:49) | Altura única 148 dp, título de 2 líneas, logo 64 dp | `7f6d537` | implementado sin probar |
| A-8 | Botones Subir/Bajar (V1 11:22) | Eliminados del diálogo; quedan como acciones de TalkBack | `7f6d537` | implementado sin probar |

## 6. Google Flow — diagnóstico

- El servidor del puente (`tools/flow-linux-probe/recovery.py`, `POST /api/flow/jobs`) solo lee prompt, tipo, lotes y referencias. **Ignora `mode`, `initialFrameId` y `endFrameId`** que Android envía y no tiene proporción, duración ni modelo: por eso los selectores no aplican nada.
- El contrato verificado de Desktop (`desktop/flow_contract.py`) fija los valores reales: vídeo 16:9/9:16; 4/6/8 s (Omni 4–10 s; Veo Quality y referencias 8 s); 720p nativo; mejoras de descarga como operación aparte.
- Upscale no está en la UI de Flow (comprobado por búsqueda en `ui/flow`).

## 7. Pendiente (lista honesta)

Se completa conforme avanzan las pruebas en el Pixel.
