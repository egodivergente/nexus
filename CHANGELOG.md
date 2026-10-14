# Changelog — NEXUS

Todos los cambios relevantes de **Nexus Agent** (Android) y **Nexus Studio** (Windows) se documentan en este archivo.

## [1.9.4-audit] - 2026-10-14

Conjunto de mejoras de seguridad de cobro, idempotencia, concurrencia, recuperación ante fallos, puentes (Flow y Sol/AKI), paridad entre núcleos del asistente y pulido de interfaz tras la auditoría integral sobre `v1.9.3-agent`.

### Seguridad de cobro e idempotencia (`agent-core`, Android y Windows)
- **Protección de planes con envío incierto (`B-C1`, `B-A6`)**:
  - `SpendGuard.requestConfirmation` conserva los estados `PlanState.UNCERTAIN` y `PlanState.CONFIRMED` frente a re-llamadas del modelo en vez de degradarlos a `AWAITING_USER_CONFIRMATION`.
  - `PrepareGenerationTool` bloquea la ejecución directa de Google Flow (`execute=true`) cuando ya existe un plan con idéntica huella en `PlanState.UNCERTAIN` sin `new_copy=true`.
  - En Android (`NexusAgentAdapters.kt`), el adaptador de envío comprueba `__submission_uncertain == "true"` antes de evaluar `job.status == GenerationStatus.FAILED`, marcando el plan como `UNCERTAIN` en lugar de devolver un error reintentable al agente.
- **Confirmación de un solo uso y sincronización (`B-A1`)**:
  - `SpendGuard` sincroniza sus mutaciones (`@Synchronized`), impide que `confirmByUser` sobrescriba un plan ya en `PlanState.CONFIRMED`, exige `states[plan.id] == PlanState.CONFIRMED` en `consume`, y rechaza re-envíos de planes ya en vuelo en `submitAccountFlow`.
  - En Android (`AssistantViewModel.kt`), se bloquea el envío de nuevos turnos de chat mientras exista un plan con envío en curso (`busy`).
- **Persistencia atómica tras envío al proveedor (`B-A2`, `B-A3`)**:
  - En Android (`Repositories.kt`), el bloque de llamada al proveedor + guardado de `providerJobId` en Room + encolado de `GenerationPollingWorker` se ejecuta bajo `withContext(NonCancellable + Dispatchers.IO)` para que el cierre de la pantalla `CreateScreen` no cancele la corrutina tras haber enviado la tarea al proveedor.
  - En Windows (`desktop/local_host.py`), el servidor persiste inmediatamente `remoteId`, `status` y `provider` en `state.json` bajo `STATE_LOCK` nada más recibir respuesta de `provider_request` en `operation == 'generate'`, evitando perder el identificador remoto si la pestaña se cierra antes del guardado del cliente.
  - Se afinó la clasificación de errores definitivos (`definite`) en `Repositories.kt` para no tratar cadenas genéricas como `"timeout"` dentro del mensaje de excepción como no-facturables (`B-M4`).
- **Guardia contra doble envío en Google Flow (`B-A4`, `B-M7`)**:
  - `FlowBridgeProvider.pollJobStatus` devuelve inmediatamente las tareas terminales (`COMPLETED`, `FAILED`, `CANCELLED`) y rechaza despachar tareas `queue_` que no estén en `QUEUED` o `PROCESSING`, limpiando `providerJobId = null` cuando falla el envío inicial.
  - `GenerationRepository.refreshConfirmedCost` incorpora bloqueo por `jobId` (`pollLocks`) y omite tareas de Flow, `queue_` o no completadas. En `GenerationsScreen.kt`, el botón `"Consultar coste"` solo se muestra en generaciones completadas de proveedores con consulta de coste por API.
- **Preservación de créditos esperados en Windows Flow (`B-A5`)**:
  - `desktop/ui/app.js` conserva el valor numérico de `plan.cost.amount` en `flowSubmit`, y `desktop/flow_automation.py` garantiza que `expected_credits` sea siempre un entero no negativo antes de invocar `flow_process_asset`.

### Agente, presupuesto y paridad `agent-core` / `desktop/agent`
- **Recuperación limpia tras cancelación de turno (`B-A8`)**:
  - `AgentOrchestrator.kt` añade bloques sintéticos `ContentBlock.ToolResult(..., isError = true)` para cualquier `ToolUse` pendiente cuando se cancela un turno (`CancellationException`), evitando que el siguiente mensaje falle la validación de Bedrock Converse (`toolResult blocks at messages.N.content`).
  - Se captura `TimeoutCancellationException` por herramienta sin abortar todo el turno como cancelación del usuario.
- **Presupuesto de contexto y límites de salida (`B-M10`)**:
  - `AgentOrchestrator.kt` limita cada `ToolResult` serializado a `32_000` caracteres e impide ejecutar bloques `ToolUse` truncados cuando `stopReason == StopReason.MAX_TOKENS`, manteniendo `BudgetedLlmProvider` acotado a `BedrockDirectProvider` para que los modelos del puente (`openai.gpt-5.6-sol`) deleguen el presupuesto en el proxy.
- **Unificación de `NativeOperations.kt` y sincronización de skills (`B-A9`, `B-M1`, `B-M2`)**:
  - `agent-core` incorpora `"sol"` en el `enum` de `agent_model` de `configure_assistant` y propaga `isError == "true"` de operaciones nativas como fallos reales (`fail(...)`) al orquestador.
  - Se sincronizaron `agent-core` y `agent-skills` (incluyendo `mateo_madrid_no_duerme/SKILL.md`) hacia `desktop/agent/` mediante `desktop/tools/sync-agent-core.py` y se recompilaron los JAR de `desktop/agent/runtime/`.

### Puentes Sol / AKI y red (`agent-proxy`, Android y Windows)
- **Alineación de timeouts, salud real del túnel y fallback Wi-Fi LAN (`B-A7`)**:
  - `BedrockProxyProvider.kt` amplía `readTimeout` a `170s` y `callTimeout` a `180s` (superando los `150s` de `aki_proxy.py`).
  - `agent-proxy/aki_proxy.py` espera hasta `8.0s` en `AkiClient.lock.acquire(timeout=8.0)` antes de devolver `429 Busy`, y distingue salidas vacías por expiración de sesión OAuth (`[cli] Error: failed to load AWS credentials` con código de salida `0` -> `aki_auth_expired`) frente a errores de lectura de adjuntos (`aki_tools`).
  - `desktop/sol_bridge.py` verifica `/health` por HTTP antes de dar por bueno un puerto abierto, limpia procesos `ssh.exe` colgados tras timeout y añade fallback automático a la IP Wi-Fi local del S25 (`192.168.1.134:8022`) cuando Tailscale (`100.119.88.105:8022`) está desconectado.
  - `desktop/install-local.ps1` actualiza también el acceso directo anclado en la barra de tareas (`User Pinned\TaskBar\Nexus Studio.lnk`) para evitar que lance un worktree antiguo.
- **Autenticación previa a lectura de cuerpo y protección SSRF (`B-B2`, `B-B3`, `B-B5`)**:
  - `agent-proxy/nexus_proxy.py` valida el encabezado `Authorization` antes de leer `Content-Length` bytes del socket.
  - `desktop/native_internet.py` y `AgentInternet.kt` bloquean direcciones loopback, privadas, link-local y reservadas en `fetch_web_page` / `AgentInternet.page` para prevenir SSRF hacia `127.0.0.1`.
  - `desktop/native_internet.py` restringe la limpieza de tokens MCP en fallo `401/403` al servidor afectado en lugar de vaciar todos los servidores configurados.

### Estabilidad, base de datos, multimedia e interfaz
- **Cancelación y borrado de tareas (`B-A11`, `B-M9`, `B-B4`)**:
  - En Android (`Repositories.kt`, `GenerationsScreen.kt`, `NexusAgentAdapters.kt`, `GenerationPollingWorker.kt`), se añadió `cancelJob(jobId)` conectado al agente y a la UI (`"Cancelar seguimiento"` en tareas activas y `"Eliminar del historial"` en tareas terminales), deteniendo el worker de inmediato si la tarea se cancela o elimina.
  - En Windows (`desktop/local_host.py`), `merge_jobs` y `write_state` respetan `deletedJobIds` y protegen `read_state()` con `STATE_LOCK` para que un sondeo en segundo plano no resucite tareas borradas.
- **Protección de índice Alexia y base de datos Room (`B-A10`, `B-M8`)**:
  - `AlexiaBridge.kt` evita sobrescribir un `axel-index.json` no vacío con un índice vacío si falla la lectura o el parseo JSON, y se eliminó la doble llamada a `AlexiaBridge.classifyJob` en `GenerationPollingWorker.kt`.
  - `NexusDatabase.kt` añade `onDowngrade` no destructivo y conversión tolerante a valores desconocidos en `Converters`.
- **Proveedores y red en Android (`B-M3`, `B-M5`)**:
  - `AtlasCloudProvider.kt` falla explícitamente si la respuesta carece de `id` de predicción en lugar de generar un `unknown_<timestamp>` imposible de sondear.
  - `NetworkModule.kt` respeta cualquier encabezado `Authorization` explícito en `AuthInterceptor`, y `SettingsRepository.testConnection` envía la cabecera `X-Nexus-Provider: atlas_cloud`.
- **Multimedia, PDF, caché y UI en Windows y Android (`B-M11`..`B-M15`, `B-H1`, `B-B1`)**:
  - `AssistantViewModel.kt` restablece `includeInBatch = false` al restaurar planes persistidos.
  - `desktop/ui/assistant.js` persiste `Agent.owner` en `sessionStorage` para no mezclar historiales al recargar.
  - `desktop/native_operations.py` limpia siempre el archivo temporal `.wav` de Whisper en un bloque `finally:` y resuelve alias de caché (`asset-file?...`) al renombrar archivos en la biblioteca.
  - `desktop/document_reader.py` conserva el texto extraído en PDFs multipágina aunque alguna página intermedia esté en blanco.
  - `desktop/flow_provider.py` evita recalcular el SHA-256 dos veces sobre el mismo archivo descargado cuando `verify_media_file` ya lo calculó.
  - Mejoras visuales en `NativeParameterControl.kt` (`verticalArrangement = Arrangement.spacedBy(6.dp)` en `FlowRow`), `InlineNotice.kt` (iconos diferenciados para error vs información) y `desktop/ui/studio-ux.css` (espaciado y truncado limpio en barra de estado inferior).
  - Añadido `.gitattributes` en la raíz con `eol=lf` para `gradlew` y `*.sh`.

---

## [1.9.3-agent] - 2026-10-13

- Consolidación en `main` de **Nexus Agent 1.9.3** para Android (`versionCode 37`, opciones visuales de modelos), **Nexus Studio 1.9.3** para Windows (biblioteca, lector de PDF de hasta 50 páginas y visor multi-salida) y el widget de Termux para el puente **Nexus–AKI** (`Sol`).
- Véase [`docs/RELEASE-193-CONSOLIDADA-20261013.md`](docs/RELEASE-193-CONSOLIDADA-20261013.md).
