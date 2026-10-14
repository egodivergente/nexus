package com.vidadomu.nexus.assistant

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.agent.AgentConversation
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.log.Redactor
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.spend.SpendViolation
import com.vidadomu.nexus.agent.core.spend.SubmissionUncertain
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.PlanBuild
import com.vidadomu.nexus.agent.core.tools.PlanEdit
import com.vidadomu.nexus.agent.core.tools.UiEvent
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.create.creationReadiness
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock

/** Elementos del chat. Las tarjetas de plan se pintan a partir de [AssistantUiState.plans]. */
@kotlinx.serialization.Serializable
sealed interface ChatItem {
    val id: String
    @kotlinx.serialization.Serializable
    data class User(override val id: String, val text: String, val attachments: List<Attachment>) : ChatItem
    @kotlinx.serialization.Serializable
    data class Assistant(override val id: String, val text: String, val toolCalls: List<String>, val stop: String) : ChatItem
    @kotlinx.serialization.Serializable
    data class PlanCard(override val id: String, val planId: String) : ChatItem
    @kotlinx.serialization.Serializable
    data class Proposal(override val id: String, val actionId: String, val description: String, val state: ProposalState = ProposalState.PENDING, val result: String? = null) : ChatItem
    @kotlinx.serialization.Serializable
    data class Notice(override val id: String, val text: String, val error: Boolean = false) : ChatItem
    /** Persisted by 1.8.2; opening history never changes the current mode. */
    @kotlinx.serialization.Serializable
    data class ModeSwitch(override val id: String, val text: String, val resolved: Boolean = false) : ChatItem
}

enum class ProposalState { PENDING, APPLYING, APPLIED, REJECTED, FAILED }

/** Estado de una tarjeta de plan. [plan] es siempre la versión registrada en el SpendGuard. */
@kotlinx.serialization.Serializable
data class PlanUi(
    val plan: GenerationPlan,
    val executable: Boolean,
    val state: PlanState,
    val busy: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val issues: List<String> = emptyList(),
    val jobId: String? = null,
    val jobStatus: String? = null,
    val jobProgress: Float? = null,
    /** Old saved cards require an explicit selection before a batch can send them. */
    val includeInBatch: Boolean = false,
)

data class AssistantUiState(
    val items: List<ChatItem> = emptyList(),
    val plans: Map<String, PlanUi> = emptyMap(),
    val thinking: Boolean = false,
    val scrollRequest: Long = 0,
    val lastLatencyMs: Long? = null,
    val catalog: List<com.vidadomu.nexus.agent.core.registry.ModelSummary> = emptyList(),
    val projectAssets: List<com.vidadomu.nexus.agent.core.assets.Asset> = emptyList(),
    val settings: AssistantSettings = AssistantSettings(),
    val mode: AgentMode = AgentMode.AGENT,
    val projects: List<Project> = emptyList(),
    val activeProjectId: String? = null,
    val pendingAttachments: List<Attachment> = emptyList(),
    val attaching: Boolean = false,
    val skillsCount: Int = 0,
    val skillErrors: List<String> = emptyList(),
    /** Mensaje efímero (snackbar) con id, para que dos iguales seguidos se muestren los dos. */
    val message: Pair<Long, String>? = null,
)

/**
 * Chat de creación con planes individuales, envíos por bloque y Flow directo.
 * La recuperación de historial no repite herramientas ni envíos.
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexusApplication
    val runtime = AssistantRuntime.get(app)

    private val _ui = MutableStateFlow(AssistantUiState())
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    private var orchestrator: AgentOrchestrator
    private var conversation: AgentConversation
    private var turnJob: Job? = null
    private val chatStore = AssistantChatStore(app)
    private val submitSlots = kotlinx.coroutines.sync.Semaphore(3)
    private val flowLane = kotlinx.coroutines.sync.Mutex()
    // StateFlow can emit synchronously in init; callbacks need initialized state.
    private val synced = mutableSetOf<String>()
    private var completedSeen = -1
    private var msgSeq = 0L

    init {
        val s = runtime.settings.load()
        orchestrator = runtime.orchestrator(s)
        val savedProject = runtime.settings.activeProjectId()?.takeIf { id -> runtime.assets.findProject(id)?.id == id }
        conversation = orchestrator.newConversation(mode = s.mode, activeProjectId = savedProject)
        _ui.update { it.copy(settings = s, mode = s.mode, skillsCount = runtime.skills.all().size, skillErrors = runtime.skills.errors, projects = runtime.assets.projects(), activeProjectId = savedProject) }
        viewModelScope.launch { runCatching { runtime.assets.startProjectSession(savedProject); runtime.importHistory() }; refreshProjects() }
        restoreChat()
        viewModelScope.launch { runtime.assets.changes.collect { refreshProjects() } }
        viewModelScope.launch { app.modelCatalogRepository.models.collect { models ->
            _ui.update { it.copy(catalog = (models + app.flowBridgeProvider.getModels()).distinctBy { it.id }.map(NexusModelRegistry::summary)) }
        } }
        viewModelScope.launch { ui.collect { state -> chatStore.save(state) } }
        viewModelScope.launch { app.generationRepository.allJobs.collect { jobs -> onJobs(jobs) } }
    }

    // ---- conversación -------------------------------------------------------------------------

    private var failedTurn: Pair<String,List<Attachment>>? = null
    fun canRetryTurn() = failedTurn != null
    fun retryTurn() {
        val request=failedTurn ?: return
        if(_ui.value.thinking) return
        send(request.first,request.second)
    }
    fun send(text: String, retryAttachments: List<Attachment>? = null): Boolean {
        val clean = text.trim()
        val atts = retryAttachments ?: _ui.value.pendingAttachments
        if ((clean.isEmpty() && atts.isEmpty()) || _ui.value.thinking || _ui.value.plans.values.any { it.busy }) return false
        if(atts.count {AgentDocuments.format(it.mimeType,it.name)!=null}>5) {
            _ui.update {it.copy(message=msg("Bedrock admite 5 documentos por mensaje. Quita alguno y envíalo en el siguiente; tu borrador se conserva."))}
            return false
        }
        failedTurn = null
        val latestSettings = runtime.settings.load()
        if (latestSettings != _ui.value.settings) {
            // Native settings tools affect the next request without dropping conversation, plans or proposals.
            orchestrator = runtime.orchestrator(latestSettings)
            _ui.update { it.copy(settings = latestSettings) }
        }
        val shown = clean.ifEmpty { "(adjuntos)" }
        _ui.update { it.copy(items = it.items + ChatItem.User(newId(), shown, atts), pendingAttachments = if(retryAttachments==null) emptyList() else it.pendingAttachments, thinking = true, scrollRequest = it.scrollRequest + 1) }
        val conv = conversation
        conv.session.mode = _ui.value.mode
        conv.session.activeProjectId = _ui.value.activeProjectId
        val started = android.os.SystemClock.elapsedRealtime()
        turnJob = viewModelScope.launch {
            try {
                val r = kotlinx.coroutines.withTimeout(if(atts.any {AgentDocuments.format(it.mimeType,it.name)!=null}) 240_000L else 600_000L) {
                    orchestrator.send(conv, clean.ifEmpty { "Te adjunto estos archivos." }, atts) { ev -> onEvent(ev) }
                }
                if (r.text.isNotBlank() || r.toolCalls.isNotEmpty()) {
                    _ui.update { it.copy(items = it.items + ChatItem.Assistant(newId(), r.text.trim(), r.toolCalls, r.stop)) }
                }
                if (r.stop == "MAX_ROUNDS") notice("He parado: demasiados pasos seguidos en un solo mensaje. Dime cómo seguir.", error = false)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                failedTurn = clean to atts
                notice("La petición ha superado el tiempo de espera. Tus archivos se conservan; puedes reintentar.", error=true)
            } catch (e: CancellationException) {
                notice("Cancelado.")
                throw e
            } catch (e: LlmException) {
                failedTurn = clean to atts
                notice(Redactor.redact(e.message ?: "El asistente no respondió."), error = true)
            } catch (e: Exception) {
                failedTurn = clean to atts
                notice("Error del asistente (${e.javaClass.simpleName}): ${Redactor.redact(e.message?.take(200).orEmpty())}", error = true)
            } finally {
                _ui.update { state ->
                    val cards = state.items.filterIsInstance<ChatItem.PlanCard>().filter { card ->
                        state.plans[card.planId]?.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION)
                    }
                    state.copy(thinking = false, lastLatencyMs = android.os.SystemClock.elapsedRealtime() - started,
                        items = state.items.filterNot { it in cards } + cards)
                }
                refreshProjects()
                // El asistente puede haber entrado en otro proyecto (open_project / create_project).
                conv.session.activeProjectId.takeIf { it != _ui.value.activeProjectId }?.let { selectProject(it) }
            }
        }
        return true
    }

    fun stop() { turnJob?.cancel() }

    private fun onEvent(ev: UiEvent) {
        when (ev) {
            is UiEvent.PlanReady -> _ui.update { s ->
                val state = runtime.guard.state(ev.plan.id) ?: PlanState.PREPARED
                val existing = s.plans[ev.plan.id]
                val ui = (existing ?: PlanUi(ev.plan, ev.executable, state, includeInBatch = true)).copy(plan = ev.plan, executable = ev.executable, state = state, issues = emptyList(), error = null)
                val items = if (s.items.any { it is ChatItem.PlanCard && it.planId == ev.plan.id }) s.items else s.items + ChatItem.PlanCard(newId(), ev.plan.id)
                s.copy(plans = s.plans + (ev.plan.id to ui), items = items)
            }
            is UiEvent.AwaitingConfirmation -> _ui.update { s ->
                val p = s.plans[ev.planId] ?: return@update s
                s.copy(plans = s.plans + (ev.planId to p.copy(state = runtime.guard.state(ev.planId) ?: p.state)))
            }
            is UiEvent.ProposedAction -> _ui.update { it.copy(items = it.items + ChatItem.Proposal(newId(), ev.id, ev.description)) }
            is UiEvent.GenerationSubmitted -> _ui.update { state ->
                state.copy(plans = state.plans + (ev.plan.id to PlanUi(ev.plan, false, PlanState.SUBMITTED, jobId = ev.jobId, jobStatus = "PENDING")),
                    items = state.items + ChatItem.PlanCard(newId(), ev.plan.id))
            }
            is UiEvent.Info -> notice(ev.text)
        }
    }

    fun newConversation() = switchConversation(null)

    internal fun conversations(): List<AssistantChatEntry> = chatStore.conversations()
    internal fun openConversation(id: String) = switchConversation(id)

    private fun switchConversation(id: String?) {
        failedTurn = null
        val current = _ui.value
        if(current.thinking || current.plans.values.any { it.busy }) return
        _ui.update { it.copy(thinking = true) }
        viewModelScope.launch {
            try {
                chatStore.archive(current)
                current.plans.filterValues { it.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.CONFIRMED) }.keys.forEach { runtime.guard.discard(it) }
                val saved = if(id == null) null else chatStore.conversation(id) ?: error("No se pudo abrir la conversación.")
                conversation = orchestrator.newConversation(mode = current.mode, activeProjectId = current.activeProjectId)
                _ui.update { it.copy(items = emptyList(), plans = emptyMap(), pendingAttachments = emptyList(), thinking = false, scrollRequest = it.scrollRequest + 1) }
                if(saved != null) restoreSavedChat(saved)
            } catch(error: Exception) {
                _ui.update { it.copy(thinking = false, message = msg("No se pudo cambiar de conversación. El historial se conserva.")) }
            }
        }
    }

    fun setMode(mode: AgentMode) {
        runtime.settings.setMode(AgentMode.AGENT)
        conversation.session.mode = AgentMode.AGENT
        _ui.update { it.copy(mode = AgentMode.AGENT) }
    }

    fun resolveModeSwitch(id: String) {
        val item = _ui.value.items.firstOrNull { it.id == id } as? ChatItem.ModeSwitch ?: return
        if (item.resolved) return
        setMode(AgentMode.AGENT)
        _ui.update { state -> state.copy(items = state.items.map {
            if (it.id == id && it is ChatItem.ModeSwitch) it.copy(resolved = true) else it
        }) }
    }

    // ---- adjuntos y proyectos -----------------------------------------------------------------

    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(attaching = true) }
        viewModelScope.launch {
            val added = mutableListOf<Attachment>()
            for (u in uris) {
                runCatching { AgentMediaStore.persist(app, u, null) }
                    .onSuccess { added += it }
                    .onFailure { notice("No se pudo adjuntar: ${it.message?.take(160)}", error = true) }
            }
            _ui.update { it.copy(pendingAttachments = (it.pendingAttachments + added).distinctBy { attachment -> attachment.id }, attaching = false) }
            refreshProjects()
        }
    }

    fun removeAttachment(id: String) = _ui.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { a -> a.id == id }) }

    fun selectProject(id: String?) {
        failedTurn = null
        conversation.session.activeProjectId = id
        _ui.update { it.copy(activeProjectId = id, projectAssets = runtime.assets.snapshot().assets.filter { asset -> asset.projectId == id && id != null && !asset.libraryDeleted }) }
        runtime.settings.setActiveProject(id)
        viewModelScope.launch { runCatching { runtime.assets.startProjectSession(id) } }
    }

    /** Guarda la memoria del proyecto editada a mano en el diálogo «Memoria». */
    fun saveProjectMemory(projectId: String, description: String, synopsis: String, tone: String, style: String, rules: String, models: String, keepNotes: Set<Long>?) {
        viewModelScope.launch {
            runCatching {
                runtime.assets.updateProject(projectId, description)
                runtime.assets.updateProjectMemory(projectId) { m ->
                    m.copy(
                        synopsis = synopsis, tone = tone, visualStyle = style,
                        rules = rules.lines(), preferredModels = models.split('\n', ',').map { it.trim() },
                        notes = if (keepNotes == null) m.notes else m.notes.filter { it.at in keepNotes },
                    )
                }
            }.onSuccess { notice("Memoria del proyecto guardada.") }.onFailure { notice("No se pudo guardar: ${it.message?.take(160)}", error = true) }
            refreshProjects()
        }
    }

    fun projectCharacters(projectId: String) = runtime.assets.snapshot().characters.filter { it.projectId == projectId }
    fun projectAssetCount(projectId: String) = runtime.assets.snapshot().assets.count { it.projectId == projectId && !it.libraryDeleted }

    fun createProject(name: String) {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val p = runtime.assets.findProject(clean) ?: runtime.assets.createProject(clean)
            refreshProjects(); selectProject(p.id)
        }
    }

    private fun refreshProjects() = _ui.update { state -> state.copy(projects = runtime.assets.projects(),
        projectAssets = runtime.assets.snapshot().assets.filter { it.projectId == state.activeProjectId && state.activeProjectId != null && !it.libraryDeleted }) }

    fun importProjectFiles(uris: List<Uri>) {
        val projectId = _ui.value.activeProjectId ?: return
        _ui.update { it.copy(attaching = true) }
        viewModelScope.launch {
            try {
                for (uri in uris) runCatching {
                    val att = AgentMediaStore.persist(app, uri, null)
                    runtime.assets.addAsset(com.vidadomu.nexus.agent.core.assets.Asset(att.id, att.uri,
                        com.vidadomu.nexus.agent.core.agent.GenerationLifecycle.typeOfMime(att.mimeType),
                        role = com.vidadomu.nexus.agent.core.assets.AssetRole.REFERENCE, displayName = att.name ?: "Referencia",
                        mimeType = att.mimeType, width = att.width, height = att.height, projectId = projectId, createdAt = System.currentTimeMillis()))
                }.onFailure { notice("No se pudo importar: ${it.message}", true) }
            } finally { _ui.update { it.copy(attaching = false) }; refreshProjects() }
        }
    }

    fun reuseAsset(id: String) {
        val asset = runtime.assets.asset(id) ?: return
        val att = Attachment(asset.id, asset.uri, asset.mimeType ?: "image/*", asset.displayName, asset.width, asset.height, assetId = asset.id)
        _ui.update { state -> state.copy(pendingAttachments = (state.pendingAttachments + att).distinctBy { it.id }) }
    }

    fun renameReference(id: String, name: String) = viewModelScope.launch {
        val asset = runtime.assets.asset(id) ?: return@launch
        if (name.isNotBlank()) runtime.assets.updateAsset(id) { it.copy(displayName = name.trim()) }
        refreshProjects()
    }

    fun generatePending() {
        if(_ui.value.thinking || _ui.value.plans.values.any { it.busy }) return
        _ui.value.plans.values.filter { it.includeInBatch && it.executable && !it.busy && it.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION) }
            .take(20).forEach { generate(it.plan.id, it.plan.fingerprint()) }
    }

    fun outputs(jobId: String?): List<String> = app.generationRepository.allJobs.value.firstOrNull { it.id == jobId }?.outputs?.map { it.localUri ?: it.url }.orEmpty()

    private fun restoreChat() {
        val saved = chatStore.load() ?: return
        restoreSavedChat(saved)
    }

    private fun restoreSavedChat(saved: SavedAssistantChat) {
        val plans = saved.plans.mapValues { (_, p) ->
            if (p.submitting && p.jobId == null) p.copy(state = PlanState.UNCERTAIN, busy = false, submitting = false, includeInBatch = false, error = "La app se cerró durante el envío. Comprueba Historial antes de repetir.")
            else if (p.state in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.CONFIRMED)) {
                runtime.guard.register(p.plan); p.copy(state = PlanState.PREPARED, busy = false, includeInBatch = false)
            } else p.copy(busy = false, includeInBatch = false)
        }
        _ui.update { it.copy(items = saved.items, plans = plans, pendingAttachments = saved.attachments) }
        // Recheck only the old reference-validation failures; never submit restored plans.
        viewModelScope.launch {
            for ((id, p) in plans) {
                if(p.state != PlanState.PREPARED || p.plan.media.isEmpty() || p.error?.startsWith("No se envió: Completa: Imágenes de referencia") != true) continue
                val ready = runCatching { creationReadiness(runtime.backend.toCreateState(p.plan)).canSubmit }.getOrDefault(false)
                if(ready) setPlan(id) { current ->
                    if(current.state == PlanState.PREPARED && current.plan.fingerprint() == p.plan.fingerprint()) current.copy(error = null) else current
                }
            }
        }
        conversation.session.attachments += (saved.sessionAttachments + saved.items.filterIsInstance<ChatItem.User>().flatMap { it.attachments } + saved.attachments).distinctBy { it.id }
        conversation.restoreTextHistory(saved.items.mapNotNull { item -> when(item) {
            is ChatItem.User -> "user" to item.text
            is ChatItem.Assistant -> "assistant" to item.text
            else -> null
        } })
    }

    // ---- propuestas (Copiloto) ----------------------------------------------------------------

    fun applyProposal(itemId: String) {
        val item = _ui.value.items.firstOrNull { it.id == itemId } as? ChatItem.Proposal ?: return
        if (item.state != ProposalState.PENDING) return
        setProposal(itemId) { it.copy(state = ProposalState.APPLYING) }
        viewModelScope.launch {
            runCatching { orchestrator.applyPending(conversation, item.actionId) }
                .onSuccess { r -> setProposal(itemId) { it.copy(state = ProposalState.APPLIED, result = r.toString().take(240)) } }
                .onFailure { e -> setProposal(itemId) { it.copy(state = ProposalState.FAILED, result = e.message?.take(200)) } }
            refreshProjects()
        }
    }

    fun rejectProposal(itemId: String) {
        val item = _ui.value.items.firstOrNull { it.id == itemId } as? ChatItem.Proposal ?: return
        if (item.state != ProposalState.PENDING) return
        orchestrator.rejectPending(conversation, item.actionId)
        setProposal(itemId) { it.copy(state = ProposalState.REJECTED) }
    }

    private fun setProposal(itemId: String, f: (ChatItem.Proposal) -> ChatItem.Proposal) = _ui.update { s ->
        s.copy(items = s.items.map { if (it.id == itemId && it is ChatItem.Proposal) f(it) else it })
    }

    // ---- tarjetas de plan ---------------------------------------------------------------------

    /** Edición del usuario (prompt o parámetros). Se revalida contra el schema e invalida confirmaciones previas. */
    fun editPlan(planId: String, edit: PlanEdit) {
        val p = _ui.value.plans[planId] ?: return
        if (p.state == PlanState.SUBMITTED || p.state == PlanState.DISCARDED || p.state == PlanState.UNCERTAIN || p.busy) return
        setPlan(planId) { it.copy(busy = true, error = null, issues = emptyList()) }
        viewModelScope.launch {
            try {
                when (val b = runtime.deps.plans.rebuild(p.plan, edit)) {
                    is PlanBuild.Ready -> {
                        runtime.guard.replace(b.plan) // lanza SpendViolation si entretanto se envió/descartó
                        // replace() deja el plan en PREPARED y anula cualquier confirmación anterior.
                        setPlan(planId) { it.copy(plan = b.plan, executable = true, state = runtime.guard.state(planId) ?: PlanState.PREPARED, busy = false) }
                    }
                    is PlanBuild.Invalid -> setPlan(planId) { it.copy(busy = false, issues = b.issues.map { i -> "${i.key}: ${i.message}" }) }
                }
            } catch (e: Exception) {
                setPlan(planId) { it.copy(busy = false, error = e.message?.take(200) ?: "No se pudo actualizar el plan.") }
            }
        }
    }

    fun toggleBatchPlan(planId: String) {
        if(_ui.value.thinking) return
        setPlan(planId) { if(it.busy || it.state !in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION)) it else it.copy(includeInBatch = !it.includeInBatch) }
    }

    /** Replace image references on the existing card; no new plan and no submission. */
    fun attachPlanImages(planId: String, uris: List<Uri>) {
        if(uris.isEmpty() || _ui.value.thinking) return
        val current = _ui.value.plans[planId] ?: return
        if(current.busy || current.state !in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION)) return
        setPlan(planId) { it.copy(busy = true, error = null, issues = emptyList()) }
        viewModelScope.launch {
            try {
                val spec = runtime.registry.schema(current.plan.ref).media
                val role = if(spec.imagesArrayField != null) "reference" else "first_frame"
                check(spec.imagesArrayField != null || spec.firstFrameField != null || spec.inputImageField != null) { "Este modelo no admite imágenes de entrada." }
                val maximum = if(role == "reference") spec.maxImages.takeIf { it > 0 } ?: 1 else 1
                check(uris.size <= maximum) { "Este modelo admite hasta $maximum imágenes. Selecciona menos archivos." }
                val selected = uris.map { uri ->
                    val attachment = AgentMediaStore.persist(app, uri, null)
                    check(attachment.mimeType.startsWith("image/")) { "Selecciona imágenes de referencia." }
                    com.vidadomu.nexus.agent.core.tools.MediaRequest(role, attachment.uri, attachment.mimeType, attachment.assetId)
                }
                val keep = current.plan.media.filter { it.role !in setOf("reference", "first_frame") }
                    .map { com.vidadomu.nexus.agent.core.tools.MediaRequest(it.role, it.uri, it.mimeType, it.assetId) }
                when(val result = runtime.deps.plans.rebuild(current.plan, PlanEdit(media = keep + selected))) {
                    is PlanBuild.Ready -> {
                        runtime.guard.replace(result.plan)
                        setPlan(planId) { it.copy(plan = result.plan, busy = false, executable = true, state = PlanState.PREPARED) }
                    }
                    is PlanBuild.Invalid -> setPlan(planId) { it.copy(busy = false, issues = result.issues.map { issue -> issue.message }, error = result.issues.joinToString { issue -> issue.message }) }
                }
            } catch(error: Exception) {
                setPlan(planId) { it.copy(busy = false, error = Redactor.redact(error.message ?: "No se pudieron adjuntar las referencias.")) }
            } finally { refreshProjects() }
        }
    }

    fun discardPlan(planId: String) {
        val p = _ui.value.plans[planId] ?: return
        if (p.busy || p.state == PlanState.SUBMITTED || p.state == PlanState.UNCERTAIN) return
        runtime.guard.discard(planId)
        setPlan(planId) { it.copy(state = PlanState.DISCARDED) }
    }

    /**
     * Envía un plan revisado desde la acción individual o la acción del bloque.
     * La huella evita enviar parámetros distintos de los mostrados.
     */
    fun generate(planId: String, shownFingerprint: String) {
        val p = _ui.value.plans[planId] ?: return
        if (_ui.value.thinking) { setPlan(planId) { it.copy(error = "Espera a que el asistente termine antes de generar.") }; return }
        if (p.busy || !p.executable || p.state !in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.CONFIRMED)) return
        setPlan(planId) { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                chatStore.save(_ui.value)
                val r = submitSlots.withPermit {
                    suspend fun submitNow(): com.vidadomu.nexus.agent.core.spend.SubmitResult {
                        setPlan(planId) { it.copy(submitting = true) }
                        chatStore.save(_ui.value)
                        return runtime.deps.lifecycle.confirmAndSubmit(planId, shownFingerprint)
                    }
                    if (p.plan.ref.providerId == "google_flow") flowLane.withLock { submitNow() }
                    else submitNow()
                }
                val sent = runtime.guard.plan(planId) ?: p.plan
                setPlan(planId) { it.copy(plan = sent, busy = false, submitting = false, state = PlanState.SUBMITTED, jobId = r.jobId, jobStatus = "PENDING") }
                notice("Enviado a ${sent.modelName}. Lo verás en Historial; aquí se actualiza el estado." + (r.note?.takeIf { "lineage" in it }?.let { " $it" } ?: ""))
                onJobs(app.generationRepository.allJobs.value)
            } catch (e: SubmissionUncertain) {
                setPlan(planId) { it.copy(busy = false, submitting = false, state = PlanState.UNCERTAIN, jobId = e.jobId, error = e.message) }
            } catch (e: SpendViolation) {
                val current = runtime.guard.plan(planId)
                setPlan(planId) { it.copy(plan = current ?: it.plan, busy = false, submitting = false, state = runtime.guard.state(planId) ?: it.state, error = e.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setPlan(planId) { it.copy(busy = false, submitting = false, state = runtime.guard.state(planId) ?: PlanState.PREPARED, error = "No se envió: " + Redactor.redact(e.message?.take(200) ?: e.javaClass.simpleName)) }
            }
        }
    }

    /** Estado del Lienzo para revisar el plan a mano (no envía). */
    suspend fun canvasState(planId: String): CreateUiState? {
        val p = _ui.value.plans[planId] ?: return null
        return runCatching { runtime.backend.toCreateState(p.plan) }
            .onFailure { e -> setPlan(planId) { it.copy(error = e.message?.take(200)) } }
            .getOrNull()
    }

    private fun setPlan(planId: String, f: (PlanUi) -> PlanUi) = _ui.update { s ->
        val p = s.plans[planId] ?: return@update s
        s.copy(plans = s.plans + (planId to f(p)))
    }



    private fun onJobs(jobs: List<com.vidadomu.nexus.core.model.GenerationJob>) {
        // Generaciones hechas a mano (fuera del asistente): se indexan y heredan el proyecto activo de ese momento.
        val completed = jobs.count { it.status == GenerationStatus.COMPLETED }
        if (completedSeen >= 0 && completed > completedSeen) viewModelScope.launch { runCatching { runtime.importHistory() }; refreshProjects() }
        completedSeen = completed
        for(job in jobs) {
            val planId = job.parameters[NexusGenerationBackend.PLAN_KEY] ?: continue
            val plan = _ui.value.plans[planId] ?: continue
            if(plan.jobId == null && job.providerJobId != null) setPlan(planId) { it.copy(jobId = job.id, state = PlanState.SUBMITTED, busy = false, submitting = false) }
        }
        val tracked = _ui.value.plans.values.filter { it.jobId != null }
        if (tracked.isEmpty()) return
        val byId = jobs.associateBy { it.id }
        for (p in tracked) {
            val job = byId[p.jobId] ?: continue
            val st = when (job.status) {
                GenerationStatus.COMPLETED -> "COMPLETED"; GenerationStatus.FAILED -> "FAILED"; GenerationStatus.CANCELLED -> "CANCELLED"
                GenerationStatus.UPLOADING, GenerationStatus.QUEUED -> "PENDING"; else -> "PROCESSING"
            }
            if (st != p.jobStatus || job.progress != p.jobProgress || job.error != p.error) setPlan(p.plan.id) { it.copy(jobStatus = st, jobProgress = job.progress, error = job.error) }
            if (st == "COMPLETED" || st == "FAILED" || st == "CANCELLED") {
                val jid = p.jobId!!
                if (synced.add(jid)) viewModelScope.launch { runCatching { runtime.deps.lifecycle.sync(jid) } }
            }
        }
    }

    // ---- ajustes ------------------------------------------------------------------------------

    fun saveSettings(s: AssistantSettings, newToken: String?, newAwsSecret: String? = null): String? = try {
        runtime.settings.save(s, newToken, newAwsSecret)
        val loaded = runtime.settings.load()
        orchestrator = runtime.orchestrator(loaded)
        _ui.update { it.copy(settings = loaded, mode = loaded.mode) }
        null
    } catch (e: IllegalArgumentException) { e.message }

    fun reloadSkills() {
        val store = runtime.reloadSkills()
        orchestrator = runtime.orchestrator(_ui.value.settings)
        _ui.update { it.copy(skillsCount = store.all().size, skillErrors = store.errors, message = msg("Skills recargadas: ${store.all().size}" + if (store.errors.isNotEmpty()) " (${store.errors.size} con errores)" else "")) }
    }

    fun reindexHistory() = viewModelScope.launch {
        val n = runCatching { runtime.importHistory() }.getOrDefault(0)
        _ui.update { it.copy(message = msg("Historial indexado: $n creaciones nuevas")) }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun notice(text: String, error: Boolean = false) = _ui.update { it.copy(items = it.items + ChatItem.Notice(newId(), text, error)) }

    private fun msg(text: String) = (++msgSeq) to text

    private fun newId() = UUID.randomUUID().toString()
}
