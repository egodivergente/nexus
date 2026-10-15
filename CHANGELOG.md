# Changelog — NEXUS

Todos los cambios relevantes de **Nexus Agent** (Android) y **Nexus Studio** (Windows) se documentan en este archivo.

## [Unreleased] - rama `ux/premium-10` (Windows)

Pasada de pulido UX/UI de Nexus Studio para Windows (solo `desktop/ui`, sin cambios de lógica ni de datos):

- **Vídeo:** miniaturas con primer fotograma en Biblioteca, Lienzo e Historial.
- **Contraste:** el color del texto sobre el acento se calcula por tema (WCAG 4.5:1).
- **Objetivos clicables y texto:** ningún botón por debajo de 24 px (WCAG 2.5.8); pestañas 30 px, barra de estado 26 px; texto mínimo 11 px.
- **Marca:** insignia de versión corta en la barra lateral sin recorte; la versión de bienvenida, vista previa y guía se mantiene (decisión de 1.9.5).
- **Historial:** agrupa 3 o más generaciones idénticas seguidas (×N, desplegable), «Hoy/Ayer», «Local» / «Sin coste» con explicación en lugar de «—».
- **Modelos:** selectores de filtro sin recorte; «Desde X · sin confirmar» con tooltip.
- **Ajustes:** tarjetas en columnas sin huecos; temas con vista previa y nombre visibles.
- **Biblioteca / Lienzo:** buscador con un solo borde; miniaturas de «Producción reciente» con su tamaño real, alineadas con el compositor.
- **Saldo y gasto:** título y fecha en dos líneas, importe a la derecha.

## [1.9.5-agent] - 2026-10-15

Release `v1.9.5-agent` (Android `1.9.5-agent-preview` / `versionCode 39` y Windows `1.9.5-agent-desktop`) que incorpora el logo oficial de NEXUS y el indicador de versión en el preview/onboarding de apertura, pantallas de bienvenida y cabeceras en móvil y EliteBook.

### Logo oficial de NEXUS y versión en preview / onboarding (Android y Windows)
1. **Android (`1.9.5-agent-preview` · `versionCode 39`)**:
   - Incorporados los recursos gráficos oficiales `drawable/nexus_logo.png` (isotipo + wordmark NEXUS) y `drawable/nexus_mark.png`, y actualizado el icono adaptativo `mipmap-anydpi-v26/ic_launcher.xml` para usar el isotipo oficial.
   - Añadido `NexusStartupOnboardingOverlay` en `MainActivity.kt`, que muestra al abrir la aplicación (y al regresar tras segundo plano) una tarjeta de preview/onboarding con el logo oficial de NEXUS y el badge de versión `v1.9.5-agent-preview · build 39` (se desvanece suavemente o se cierra al tocar; desactivado en `BuildConfig.OFFLINE_QA`).
   - Actualizado `AssistantScreen.kt` (`EmptyHint` y `NexusTopAppBar`) para mostrar el logo oficial completo y la píldora de versión `v1.9.5-agent-preview · build 39` en el onboarding del asistente y en la barra superior.
2. **Windows (`1.9.5-agent-desktop` en HP EliteBook)**:
   - Añadido `mountStartupOnboarding()` (`#nx-startup-onboarding`) en `desktop/ui/desktop-design.js` y `desktop-design.css`, que muestra el logo oficial `branding/nexus-logo.png` y el badge `v1.9.5-agent-desktop` cada vez que se abre Nexus Studio.
   - Actualizados el onboarding de bienvenida (`desktopWelcome`), el banner superior de conversaciones activas (`.nx-chat-brand-banner`), el lienzo de previsualización (`.studio-blank`), la guía de primeros pasos (`#studio-start-guide`), la cabecera de Crear (`.nx-creative-brand`), la marca lateral (`.sidebar .brand`) y la barra superior (`#parity-version-chip` en `agent-parity.js` / `agent-parity.css`) para presentar el logo oficial completo sin recortes junto con la versión activa `v1.9.5-agent-desktop`.
3. **Recuperación automática de vistas previas en Biblioteca (`studio-gallery.js`, `polish.js`, `local_host.py`)**:
   - Priorización de copias locales `/media/...` y URLs permanentes frente a URLs temporales firmadas de 24 h (`X-Tos-Expires=`, `Expires=`) en `merge_jobs` y `studio-gallery.js`.
   - En `polish.js`, cuando falla la carga de una imagen en `.result img`, antes de mostrar «Vista previa no disponible» se prueban el resto de salidas de `job.outputs` y, para tareas de `atlas_cloud` con `remoteId`, se consulta `/api/v1/model/prediction/{remoteId}` para recuperar y persistir la URL permanente archivada en `atlas-media.oss-us-west-1.aliyuncs.com/assetd-history/v1/...`.
4. **Auditoría y refactorización integral de UI/UX (Windows Desktop y Android APK)**:
   - **Windows (`desktop/ui/`)**: Unificación de cabeceras en una única Top Bar de `56px` con botón de colapsar sidebar integrado junto al logo (`#creative-dock-toggle`); eliminación de controles duplicados (`#header-actions [data-nav="canvas"]`, botones `+` redundantes); sistema de jerarquía visual de 3 niveles (Primario, Secundario y Badges informativos); rejilla con eje vertical izquierdo único (`24px`), tarjetas de sugerencias e historial rediseñadas (`44x44px`), selectores `.select-trigger` alineados a la izquierda con chevron fijo a la derecha, truncado seguro (`ellipsis`) y composer unificado con `resize: none` y barra interna de herramientas.
   - **Android (`app/src/main/java/com/vidadomu/nexus/`)**: Unificación del eje vertical izquierdo (`screenMargin = 16.dp`) y altura de Top Bar (`topBarHeight = 56.dp`) en todas las pantallas (`Tokens.kt`); `StudioBalanceChip` rediseñado como píldora compacta de 1 línea con divisores sutiles de `0.5.dp` en `NexusTopAppBar` y `NexusBottomBar`; eliminación de las 4 barras superiores apiladas en `Biblioteca` (`LibraryScreen.kt`) integrando búsqueda y contador en la Top Bar y «Imágenes del proyecto · Gestionar» en la tira única de filtros; selector de proyecto con chevron desplegable visible en `Asistente` (`AssistantScreen.kt`), burbujas de usuario e `InputBar` refinados (`AssistantExperience.kt`) y truncado seguro de 1 línea en `PlanCardView`, `GenerationSummaryRow` y `CreateScreen`.

---

## [1.9.4-agent] - 2026-10-14

Release `v1.9.4-agent` (Android `1.9.4-agent` / `versionCode 38` y Windows `1.9.4-agent-desktop`) con las mejoras de seguridad de cobro, idempotencia, concurrencia, puentes (Flow y Sol/AKI), paridad entre núcleos del asistente y las correcciones de la revisión del commit `b5401b8`, conservando intactos los artefactos de la Release `v1.9.3-agent`.

### Correcciones de revisión (`b5401b8`)
1. **Windows — `job_id` obligatorio en `generate` y asignación exacta de `remoteId`**:
   - `desktop/ui/app.js` incluye `job_id: job.id` en cada petición `rpc('generate', ...)`.
   - `desktop/local_host.py` exige `job_id` no vacío y asigna `remoteId` **únicamente** al trabajo cuyo `id == job_id` bajo `STATE_LOCK` (`threading.RLock()`), eliminando la selección implícita del primer trabajo `SENDING` del proveedor.
   - Añadida prueba de concurrencia en `desktop/tools/test-host-integrity.py` con dos envíos simultáneos cuyas respuestas llegan en orden inverso.
2. **PDFs mixtos — preservación de páginas escaneadas o gráficas**:
   - `desktop/document_reader.py` y `AgentDocuments.kt` conservan la vía del documento PDF original (`format = "pdf"`) cuando cualquier página carece de texto extraíble o contiene imágenes incrustadas, evitando descartar páginas escaneadas o gráficas al convertir a texto.
   - Añadida prueba en `desktop/tools/test-library-pdf-193.py` con un PDF mixto de 3 páginas (`texto + página gráfica + texto`).
3. **Google Flow — verificación SHA-256 de la copia local y recuperación automática**:
   - `desktop/flow_provider.py` verifica el hash SHA-256 de la copia local en `media/` antes de reutilizarla y, si está alterada o corrupta (incluso manteniendo el mismo tamaño en bytes), la restaura atómicamente desde el archivo original válido en `RUNTIME / 'results'`.
   - Añadida prueba en `desktop/tools/test-flow-automation.py` con una copia local alterada del mismo tamaño exacto en bytes.
4. **Android — separación entre «Detener seguimiento» y «Cancelar generación»**:
   - `GenerationRepository` y `GenerationCancellationPolicy` (`Repositories.kt`) separan `stopTrackingJob` (acción estrictamente local que detiene el `WorkManager` y pausa el seguimiento con `__tracking_paused = "true"` sin llamar al proveedor) de `cancelRemoteGeneration` (acción explícita que verifica `supportsRemoteCancellation` en el proveedor y explica claramente si el proveedor admite cancelación remota: Runway sí admite `DELETE /v1/tasks/{id}`; Atlas Cloud, cRun y Google Flow no admiten cancelación remota una vez enviada la tarea).
   - `GenerationsScreen.kt` ofrece ambas acciones por separado en el menú de tareas en curso y muestra un diálogo explícito de confirmación para «Cancelar generación».
   - Añadida suite unitaria `GenerationCancellationTest.kt`.
5. **Windows — arranque desde la instalación actual y verificación de accesos directos**:
   - `desktop/local_host.py` registra `root` y `version` (`1.9.4-agent-desktop`) en `local-session.json` y, al detectar el mutex activo (`GetLastError() == 183`), verifica `is_same_installation_root`: si el proceso previo pertenecía a otra ruta (como `Documents\ChatGPT\NEXUS` o una versión anterior), detiene el host antiguo y toma el relevo desde la instalación actual conservando `%LOCALAPPDATA%\NexusDesktop`.
   - `desktop/install-local.ps1` y `desktop/tools/deploy-desktop-parity.py` actualizan y verifican los accesos directos de Escritorio, `Documents\Biblioteca\Aplicaciones\Nexus Studio.lnk` y barra de tareas (`User Pinned\TaskBar\Nexus Studio.lnk`).

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
  - Mejoras visuales en `NativeParameterControl.kt` (`verticalArrangement = Arrangement.spacedBy(6.dp)` en `FlowRow`), `InlineNotice.kt` (iconos diferenciados para error vs información) y `desktop/ui/studio-ux.css` (espaciado y truncado limpio en barra de estado inferior).
  - Añadido `.gitattributes` en la raíz con `eol=lf` para `gradlew` y `*.sh`.

---

## [1.9.3-agent] - 2026-10-13

- Consolidación en `main` de **Nexus Agent 1.9.3** para Android (`versionCode 37`, opciones visuales de modelos), **Nexus Studio 1.9.3** para Windows (biblioteca, lector de PDF de hasta 50 páginas y visor multi-salida) y el widget de Termux para el puente **Nexus–AKI** (`Sol`).
- Véase [`docs/RELEASE-193-CONSOLIDADA-20261013.md`](docs/RELEASE-193-CONSOLIDADA-20261013.md).
