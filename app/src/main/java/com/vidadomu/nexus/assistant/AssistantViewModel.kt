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
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Elementos del chat. Las tarjetas de plan se pintan a partir de [AssistantUiState.plans]. */
sealed interface ChatItem {
    val id: String
    data class User(override val id: String, val text: String, val attachments: List<Attachment>) : ChatItem
    data class Assistant(override val id: String, val text: String, val toolCalls: List<String>, val stop: String) : ChatItem
    data class PlanCard(override val id: String, val planId: String) : ChatItem
    data class Proposal(override val id: String, val actionId: String, val description: String, val state: ProposalState = ProposalState.PENDING, val result: String? = null) : ChatItem
    data class Notice(override val id: String, val text: String, val error: Boolean = false) : ChatItem
}

enum class ProposalState { PENDING, APPLYING, APPLIED, REJECTED, FAILED }

/** Estado de una tarjeta de plan. [plan] es siempre la versión registrada en el SpendGuard. */
data class PlanUi(
    val plan: GenerationPlan,
    val executable: Boolean,
    val state: PlanState,
    val busy: Boolean = false,
    val error: String? = null,
    val issues: List<String> = emptyList(),
    val jobId: String? = null,
    val jobStatus: String? = null,
    val jobProgress: Float? = null,
)

data class AssistantUiState(
    val items: List<ChatItem> = emptyList(),
    val plans: Map<String, PlanUi> = emptyMap(),
    val thinking: Boolean = false,
    val settings: AssistantSettings = AssistantSettings(),
    val mode: AgentMode = AgentMode.COPILOT,
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
 * Pantalla «¿Qué quieres crear?». Regla de gasto: el único camino a [GenerationLifecycle.confirmAndSubmit]
 * es [generate], que solo se llama desde el botón GENERAR del diálogo de confirmación de ESA tarjeta.
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexusApplication
    val runtime = AssistantRuntime.get(app)

    private val _ui = MutableStateFlow(AssistantUiState())
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    private var orchestrator: AgentOrchestrator
    private var conversation: AgentConversation
    private var turnJob: Job? = null

    init {
        val s = runtime.settings.load()
        orchestrator = runtime.orchestrator(s)
        val savedProject = runtime.settings.activeProjectId()?.takeIf { id -> runtime.assets.findProject(id)?.id == id }
        conversation = orchestrator.newConversation(mode = s.mode, activeProjectId = savedProject)
        _ui.update { it.copy(settings = s, mode = s.mode, skillsCount = runtime.skills.all().size, skillErrors = runtime.skills.errors, projects = runtime.assets.projects(), activeProjectId = savedProject) }
        viewModelScope.launch { runCatching { runtime.assets.startProjectSession(savedProject); runtime.importHistory() }; refreshProjects() }
        viewModelScope.launch { app.generationRepository.allJobs.collect { jobs -> onJobs(jobs) } }
    }

    // ---- conversación -------------------------------------------------------------------------

    fun send(text: String) {
        val clean = text.trim()
        val atts = _ui.value.pendingAttachments
        if ((clean.isEmpty() && atts.isEmpty()) || _ui.value.thinking) return
        val shown = clean.ifEmpty { "(adjuntos)" }
        _ui.update { it.copy(items = it.items + ChatItem.User(newId(), shown, atts), pendingAttachments = emptyList(), thinking = true) }
        val conv = conversation
        conv.session.mode = _ui.value.mode
        conv.session.activeProjectId = _ui.value.activeProjectId
        turnJob = viewModelScope.launch {
            try {
                val r = orchestrator.send(conv, clean.ifEmpty { "Te adjunto estos archivos." }, atts) { ev -> onEvent(ev) }
                if (r.text.isNotBlank() || r.toolCalls.isNotEmpty()) {
                    _ui.update { it.copy(items = it.items + ChatItem.Assistant(newId(), r.text.trim(), r.toolCalls, r.stop)) }
                }
                if (r.stop == "MAX_ROUNDS") notice("He parado: demasiados pasos seguidos en un solo mensaje. Dime cómo seguir.", error = false)
            } catch (e: CancellationException) {
                notice("Cancelado.")
                throw e
            } catch (e: LlmException) {
                notice(Redactor.redact(e.message ?: "El asistente no respondió."), error = true)
            } catch (e: Exception) {
                notice("Error del asistente (${e.javaClass.simpleName}): ${Redactor.redact(e.message?.take(200).orEmpty())}", error = true)
            } finally {
                _ui.update { it.copy(thinking = false) }
                refreshProjects()
                // El asistente puede haber entrado en otro proyecto (open_project / create_project).
                conv.session.activeProjectId.takeIf { it != _ui.value.activeProjectId }?.let { selectProject(it) }
            }
        }
    }

    fun stop() { turnJob?.cancel() }

    private fun onEvent(ev: UiEvent) {
        when (ev) {
            is UiEvent.PlanReady -> _ui.update { s ->
                val state = runtime.guard.state(ev.plan.id) ?: PlanState.PREPARED
                val existing = s.plans[ev.plan.id]
                val ui = (existing ?: PlanUi(ev.plan, ev.executable, state)).copy(plan = ev.plan, executable = ev.executable, state = state, issues = emptyList(), error = null)
                val items = if (s.items.any { it is ChatItem.PlanCard && it.planId == ev.plan.id }) s.items else s.items + ChatItem.PlanCard(newId(), ev.plan.id)
                s.copy(plans = s.plans + (ev.plan.id to ui), items = items)
            }
            is UiEvent.AwaitingConfirmation -> _ui.update { s ->
                val p = s.plans[ev.planId] ?: return@update s
                s.copy(plans = s.plans + (ev.planId to p.copy(state = runtime.guard.state(ev.planId) ?: p.state)))
            }
            is UiEvent.ProposedAction -> _ui.update { it.copy(items = it.items + ChatItem.Proposal(newId(), ev.id, ev.description)) }
            is UiEvent.Info -> notice(ev.text)
        }
    }

    fun newConversation() {
        if (_ui.value.thinking) return
        // Los planes sin enviar de la conversación anterior dejan de ser ejecutables.
        _ui.value.plans.keys.forEach { runtime.guard.discard(it) }
        conversation = orchestrator.newConversation(mode = _ui.value.mode, activeProjectId = _ui.value.activeProjectId)
        _ui.update { it.copy(items = emptyList(), plans = emptyMap(), pendingAttachments = emptyList()) }
    }

    fun setMode(mode: AgentMode) {
        runtime.settings.setMode(mode)
        conversation.session.mode = mode
        _ui.update { it.copy(mode = mode) }
    }

    // ---- adjuntos y proyectos -----------------------------------------------------------------

    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(attaching = true) }
        viewModelScope.launch {
            val added = mutableListOf<Attachment>()
            for (u in uris.take(6)) {
                runCatching { AgentMediaStore.persist(app, u, null) }
                    .onSuccess { added += it }
                    .onFailure { notice("No se pudo adjuntar: ${it.message?.take(160)}", error = true) }
            }
            _ui.update { it.copy(pendingAttachments = it.pendingAttachments + added, attaching = false) }
        }
    }

    fun removeAttachment(id: String) = _ui.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { a -> a.id == id }) }

    fun selectProject(id: String?) {
        conversation.session.activeProjectId = id
        _ui.update { it.copy(activeProjectId = id) }
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
    fun projectAssetCount(projectId: String) = runtime.assets.snapshot().assets.count { it.projectId == projectId }

    fun createProject(name: String) {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val p = runtime.assets.findProject(clean) ?: runtime.assets.createProject(clean)
            refreshProjects(); selectProject(p.id)
        }
    }

    private fun refreshProjects() = _ui.update { it.copy(projects = runtime.assets.projects()) }

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

    fun discardPlan(planId: String) {
        val p = _ui.value.plans[planId] ?: return
        if (p.state == PlanState.SUBMITTED || p.state == PlanState.UNCERTAIN) return
        runtime.guard.discard(planId)
        setPlan(planId) { it.copy(state = PlanState.DISCARDED) }
    }

    /**
     * ÚNICO punto de gasto del asistente. Llamar solo desde el botón GENERAR del diálogo de confirmación
     * de esta tarjeta, con la huella del plan que mostraba ese diálogo. Una pulsación = una generación.
     */
    fun generate(planId: String, shownFingerprint: String) {
        val p = _ui.value.plans[planId] ?: return
        if (_ui.value.thinking) { setPlan(planId) { it.copy(error = "Espera a que el asistente termine antes de generar.") }; return }
        if (p.busy || !p.executable || p.state !in setOf(PlanState.PREPARED, PlanState.AWAITING_CONFIRMATION, PlanState.CONFIRMED)) return
        setPlan(planId) { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val r = runtime.deps.lifecycle.confirmAndSubmit(planId, shownFingerprint)
                val sent = runtime.guard.plan(planId) ?: p.plan
                setPlan(planId) { it.copy(plan = sent, busy = false, state = PlanState.SUBMITTED, jobId = r.jobId, jobStatus = "PENDING") }
                notice("Enviado a ${sent.modelName}. Lo verás en Historial; aquí se actualiza el estado." + (r.note?.takeIf { "lineage" in it }?.let { " $it" } ?: ""))
                onJobs(app.generationRepository.allJobs.value)
            } catch (e: SubmissionUncertain) {
                setPlan(planId) { it.copy(busy = false, state = PlanState.UNCERTAIN, jobId = e.jobId, error = e.message) }
            } catch (e: SpendViolation) {
                val current = runtime.guard.plan(planId)
                setPlan(planId) { it.copy(plan = current ?: it.plan, busy = false, state = runtime.guard.state(planId) ?: it.state, error = e.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setPlan(planId) { it.copy(busy = false, state = runtime.guard.state(planId) ?: PlanState.PREPARED, error = "No se envió: " + Redactor.redact(e.message?.take(200) ?: e.javaClass.simpleName)) }
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

    private val synced = mutableSetOf<String>()

    private var completedSeen = -1

    private fun onJobs(jobs: List<com.vidadomu.nexus.core.model.GenerationJob>) {
        // Generaciones hechas a mano (fuera del asistente): se indexan y heredan el proyecto activo de ese momento.
        val completed = jobs.count { it.status == GenerationStatus.COMPLETED }
        if (completedSeen >= 0 && completed > completedSeen) viewModelScope.launch { runCatching { runtime.importHistory() }; refreshProjects() }
        completedSeen = completed
        val tracked = _ui.value.plans.values.filter { it.jobId != null }
        if (tracked.isEmpty()) return
        val byId = jobs.associateBy { it.id }
        for (p in tracked) {
            val job = byId[p.jobId] ?: continue
            val st = when (job.status) {
                GenerationStatus.COMPLETED -> "COMPLETED"; GenerationStatus.FAILED -> "FAILED"; GenerationStatus.CANCELLED -> "CANCELLED"
                GenerationStatus.UPLOADING, GenerationStatus.QUEUED -> "PENDING"; else -> "PROCESSING"
            }
            if (st != p.jobStatus || job.progress != p.jobProgress) setPlan(p.plan.id) { it.copy(jobStatus = st, jobProgress = job.progress, error = job.error ?: it.error) }
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

    private var msgSeq = 0L
    private fun msg(text: String) = (++msgSeq) to text

    private fun newId() = UUID.randomUUID().toString()
}
