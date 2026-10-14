# Mejoras implementadas tras la auditoría integral — 14 de octubre de 2026

Este documento detalla las correcciones y mejoras aplicadas sobre la fuente unificada de **NEXUS** (`main`) tras la auditoría técnica de Android, Windows, `agent-core`, `agent-proxy` y `tools/flow-bridge`.

## 1. Resumen de hallazgos resueltos

| Código | Severidad | Componente | Cambio aplicado |
|---|---|---|---|
| `B-C1` | Crítico | `agent-core` (`SpendGuard.kt`) | `requestConfirmation` preserva `PlanState.UNCERTAIN` y `PlanState.CONFIRMED` para impedir que una re-llamada del LLM reactive la confirmación de un plan cuyo envío quedó incierto o ya fue confirmado. |
| `B-A1` | Alto | `agent-core` (`SpendGuard.kt`, `PlanTools.kt`), Android (`AssistantViewModel.kt`) | Sincronización `@Synchronized` en `SpendGuard`; `confirmByUser` y `submitAccountFlow` rechazan planes ya en `PlanState.CONFIRMED`; `consume` exige `PlanState.CONFIRMED`; `AssistantViewModel.send()` bloquea nuevos turnos mientras un plan está `busy`. |
| `B-A2` | Alto | Windows (`desktop/local_host.py`) | En `operation == 'generate'`, el host guarda inmediatamente `remoteId`, `status` y `provider` en `state.json` bajo `STATE_LOCK` tras recibir la respuesta del proveedor, antes de devolver la respuesta HTTP al navegador. |
| `B-A3` | Alto | Android (`Repositories.kt`) | `submitGeneration` ejecuta el envío al proveedor, el guardado de `providerJobId` en Room y el encolado en `WorkManager` dentro de `withContext(NonCancellable + Dispatchers.IO)`. |
| `B-A4` | Alto | Android (`FlowBridgeProvider.kt`, `Repositories.kt`, `GenerationsScreen.kt`) | `pollJobStatus` ignora tareas terminales y no despacha tareas `queue_` fallidas; `refreshConfirmedCost` excluye Flow/`queue_` y tareas no completadas; la UI oculta `"Consultar coste"` cuando no aplica. |
| `B-A5` | Alto | Windows (`desktop/ui/app.js`, `desktop/flow_automation.py`) | `flowSubmit` conserva el importe numérico de `plan.cost.amount` y `flow_automation.py` garantiza un `int >= 0` para `expected_credits` antes de llamar a `flow_process_asset`. |
| `B-A6` | Alto | Android (`NexusAgentAdapters.kt`), `agent-core` (`PlanTools.kt`) | El adaptador comprueba `__submission_uncertain == "true"` antes de `job.status == FAILED`; `PrepareGenerationTool` bloquea el reenvío directo (`execute=true`) en Google Flow cuando existe un plan idéntico en `PlanState.UNCERTAIN`. |
| `B-A7` | Alto | `agent-core` (`BedrockProxyProvider.kt`), `agent-proxy` (`aki_proxy.py`), Windows (`desktop/sol_bridge.py`, `desktop/install-local.ps1`) | `BedrockProxyProvider` sube `readTimeout` a `170s` y `callTimeout` a `180s`; `AkiClient` espera hasta `8.0s` por el lock y distingue la expiración de credenciales OAuth (`aki_auth_expired` cuando `aki` sale con código `0` y error en `stderr`) frente a errores de adjuntos; `sol_bridge.py` verifica `/health` por HTTP, limpia procesos `ssh.exe` colgados y hace fallback automático a la IP LAN del S25 (`192.168.1.134`) si Tailscale está desconectado; `install-local.ps1` actualiza también el icono anclado en la barra de tareas. |
| `B-A8` | Alto | `agent-core` (`AgentOrchestrator.kt`) | Al cancelarse un turno con `ToolUse` pendientes, se insertan bloques `ContentBlock.ToolResult(..., isError = true)` para mantener la validez de la conversación en Bedrock Converse; se captura `TimeoutCancellationException` por herramienta. |
| `B-A9` | Alto | `agent-core` (`NativeOperations.kt`) | Unificado con `desktop/agent/core`: incluye `"sol"` en el `enum` de `configure_assistant` y convierte `isError == "true"` en fallo (`fail(...)`). |
| `B-A10` | Alto | Android (`AlexiaBridge.kt`) | Evita sobrescribir un `axel-index.json` existente no vacío con un índice vacío cuando falla la lectura o el parseo JSON. |
| `B-A11` | Alto | Android (`Repositories.kt`, `GenerationsScreen.kt`, `NexusAgentAdapters.kt`) | Implementado `cancelJob(jobId)` que marca la tarea como `CANCELLED`, cancela su trabajo único en `WorkManager` y expone `"Cancelar seguimiento"` en la UI y en el agente. |
| `B-M1` / `B-M2` | Medio | `desktop/agent/` | Sincronizados `agent-core` y `agent-skills` (`mateo_madrid_no_duerme/SKILL.md`) hacia `desktop/agent/` con `desktop/tools/sync-agent-core.py` y actualizados los JAR de `desktop/agent/runtime/`. |
| `B-M3` | Medio | Android (`AtlasCloudProvider.kt`) | Rechaza respuestas de envío sin `id` de predicción válido en vez de fabricar `unknown_<timestamp>`. |
| `B-M4` | Medio | Android (`Repositories.kt`) | Clasificación de errores definitivos (`definite`) restringida a errores HTTP `4xx` explícitos (salvo `408`/`429`) y errores de validación local. |
| `B-M5` | Medio | Android (`NetworkModule.kt`, `Repositories.kt`) | `AuthInterceptor` respeta encabezados `Authorization` ya presentes y `testConnection` especifica `X-Nexus-Provider: atlas_cloud`. |
| `B-M7` | Medio | Android (`GenerationPollingWorker.kt`, `Repositories.kt`) | Eliminada la doble clasificación `AlexiaBridge.classifyJob` en el worker y añadido `pollLocks` a `refreshConfirmedCost`. |
| `B-M8` | Medio | Android (`NexusDatabase.kt`) | Añadido `onDowngrade` no destructivo y parseo seguro con `runCatching` en los `TypeConverter` de enums. |
| `B-M9` | Medio | Android (`GenerationPollingWorker.kt`) | Si la tarea fue eliminada de Room o ya es terminal, el worker finaliza inmediatamente con `Result.success()`. |
| `B-M10` | Medio | `agent-core` (`AgentOrchestrator.kt`) | Truncado de `ToolResult` a `32_000` caracteres y bloqueo de `ToolUse` truncados cuando `stopReason == MAX_TOKENS` (manteniendo `BudgetedLlmProvider` exclusivo de `BedrockDirectProvider` para compatibilidad con `openai.gpt-5.6-sol`). |
| `B-M11` | Medio | Android (`AssistantViewModel.kt`) | Al restaurar planes desde almacenamiento, se fuerza `includeInBatch = false`. |
| `B-M12` | Medio | Windows (`desktop/ui/assistant.js`) | `Agent.owner` se persiste en `sessionStorage` para conservar el contexto del propietario tras recargar. |
| `B-M13` | Medio | Windows (`desktop/native_operations.py`) | `rename_asset_file` resuelve rutas `/api/asset-file?...` de la caché local cuando la salida original era una URL remota. |
| `B-M14` | Medio | Windows (`desktop/document_reader.py`) | El lector de PDF conserva el texto extraído en documentos multipágina aunque alguna página intermedia venga vacía. |
| `B-M15` | Medio | `.gitattributes` | Añadido `.gitattributes` con `eol=lf` para `gradlew` y `*.sh`. |
| `B-H1` | Medio | Windows (`desktop/flow_provider.py`) | Evita leer dos veces el archivo multimedia para calcular SHA-256 cuando `verify_media_file` ya lo devuelve. |
| `B-B1`..`B-B5` | Bajo | Windows, Android, `agent-proxy` | Limpieza garantizada (`finally:`) de `.wav` temporales en Whisper; limpieza de tokens MCP acotada por host; bloqueo SSRF en `fetch_web_page` / `AgentInternet.page`; soporte `deletedJobIds` + `STATE_LOCK` en `local_host.py`; validación de `Authorization` antes de leer el body en `nexus_proxy.py`. |

## 2. Verificación ejecutada

- **Pruebas unitarias de `agent-core` y Android (`:agent-core:test :app:testDebugUnitTest`)**: PASS (incluyendo nuevos tests de regresión en `ConfirmationSafetyTest` y `OrchestratorTest`).
- **Pruebas y empaquetado del agente JVM de Windows (`desktop/agent test :core:test runtime`)**: PASS y JARs actualizados en `desktop/agent/runtime/`.
- **Pruebas de integridad del host Windows (`desktop/tools/test-host-integrity.py`)**: 11 pruebas PASS (incluyendo no-resurrección de tareas eliminadas y persistencia inmediata de `remoteId`).
- **Pruebas de automatización de Flow (`desktop/tools/test-flow-automation.py`)**: `QueueTests` (16 pruebas) PASS, incluyendo preservación de `expected_credits` numérico.
- **Pruebas de integridad de despacho UI (`desktop/tools/test-dispatch-integrity.js`)**: 14 casos PASS.
- **Pruebas de regresión de biblioteca y PDF (`desktop/tools/test-library-pdf-193.py`)**: PASS.
- **Pruebas del proxy AKI (`agent-proxy/test_*.py`)**: 34 pruebas PASS.
- **Operaciones de pago ejecutadas**: 0.
