package com.vidadomu.nexus.ui.cut

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.log.Redactor
import com.vidadomu.nexus.agent.core.tools.UiEvent
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.AssistantTurnService
import com.vidadomu.nexus.cut.CutAgent
import com.vidadomu.nexus.cut.CutChatDocument
import com.vidadomu.nexus.cut.CutChatStore
import com.vidadomu.nexus.cut.NexusCut
import com.vidadomu.nexus.cut.PendingCutChatTurn
import com.vidadomu.nexus.cut.command.CutEvent
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.ErrorCode
import com.vidadomu.nexus.cut.command.GroupStatus
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.command.Request
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

typealias CutChatMessage = com.vidadomu.nexus.cut.CutChatMessage
typealias CutChatRole = com.vidadomu.nexus.cut.CutChatRole

data class CutChatUiState(
    val messages: List<CutChatMessage> = emptyList(),
    val draft: String = "",
    val ready: Boolean = false,
    val thinking: Boolean = false,
    val streamingText: String = "",
    val activity: String = "",
    val modelLabel: String = "",
    val libraryResults: List<Attachment> = emptyList(),
    val libraryResultsOpen: Boolean = false,
    /** Pasos que el agente ya ha aplicado en el turno en curso (se ven mientras trabaja). */
    val liveSteps: List<String> = emptyList(),
    /** Ensayos del agente que siguen siendo válidos: el usuario decide si se aplican. */
    val proposals: List<CutProposal> = emptyList(),
    /** Revisión del montaje: cambia con cada edición, deshacer o rehacer, y refresca el estado de cada turno. */
    val revision: Long = 0,
)

/**
 * Lo que el agente propuso sin aplicarlo (dry_run). Guarda la petición exacta, ligada a la revisión en la que
 * se ensayó: si el montaje cambia, «Aplicar» se rechaza en vez de hacer otra cosa.
 */
data class CutProposal(
    val id: String, val label: String, val changeCount: Int, val detail: String?,
    val request: Request, val messageId: String? = null,
)

/** The montage owns its chat and cancellation; the main Assistant conversation is never selected or edited. */
@UnstableApi
class CutChatViewModel(app: Application, val projectId: String) : AndroidViewModel(app) {
    private val nexus = app as NexusApplication
    private val runtime = AssistantRuntime.get(nexus)
    private val store = CutChatStore(File(nexus.filesDir, "cut/projects"), projectId)
    private val saveMutex = Mutex()
    private var document = CutChatDocument()
    private val _ui = MutableStateFlow(CutChatUiState())
    val ui: StateFlow<CutChatUiState> = _ui.asStateFlow()
    private var turnJob: Job? = null
    private var activeTurnId: String? = null
    private val presentedLibraryEvents = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<UiEvent.ResultsWall, Boolean>())
    private var session: CutSession? = null
    private val turnSteps = ArrayList<String>()
    private val turnGroups = LinkedHashSet<String>()
    private var turnFirstSummary: kotlinx.serialization.json.JsonObject? = null
    private var turnLastSummary: kotlinx.serialization.json.JsonObject? = null
    private val turnProposals = ArrayList<CutProposal>()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Avisos breves de las acciones sobre los turnos (deshacer, aplicar una propuesta). */
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init {
        // La misma sesión que el editor: los cambios del agente se reconocen por su origen y su grupo (el turno).
        viewModelScope.launch {
            val s = try { NexusCut.workspace(nexus).session(projectId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            session = s ?: return@launch
            launch(start = CoroutineStart.UNDISPATCHED) { s.events.collect(::onCutEvent) }
            launch { s.state.map { it.revision }.distinctUntilChanged().collect { r -> _ui.update { it.copy(revision = r) } } }
        }
        viewModelScope.launch {
            try {
                // No autosave runs until the existing chat and any interrupted-turn marker are loaded.
                document = withContext(Dispatchers.IO) { store.load() }
                _ui.value = CutChatUiState(document.messages, document.draft, ready = true, modelLabel = CutAgent.label(nexus, projectId))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _ui.value = CutChatUiState(messages = listOf(notice(
                    "No se pudo abrir el chat del montaje: ${safe(e.message ?: e.javaClass.simpleName)}. El archivo guardado se conserva.", true,
                )))
            }
        }
    }

    fun setDraft(text: String) {
        if (!_ui.value.ready) return
        _ui.update { it.copy(draft = text) }
        document = document.copy(draft = text)
        viewModelScope.launch { saveOrDisable() }
    }

    /** True means accepted locally. The user message and pending marker reach disk before any provider call. */
    fun send(): Boolean {
        val current = _ui.value
        val text = current.draft.trim()
        if (!current.ready || current.thinking || text.isBlank()) return false
        val originalDraft = current.draft
        val history = document.completedTurns()
        val user = CutChatMessage(id(), CutChatRole.USER, safe(text), System.currentTimeMillis())
        val turnId = id()
        activeTurnId = turnId
        presentedLibraryEvents.clear()
        turnSteps.clear(); turnGroups.clear(); turnProposals.clear()
        turnFirstSummary = null; turnLastSummary = null
        document = document.copy(messages = document.messages + user, draft = originalDraft, pending = PendingCutChatTurn(user.id, user.at))
        _ui.update { it.copy(messages = document.messages, draft = "", thinking = true, streamingText = "", activity = "Preparando el montaje", liveSteps = emptyList(), proposals = emptyList()) }
        turnJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var answer: String? = null
            var by: String? = null
            var notes = emptyList<String>()
            var failure: String? = null
            var called = false
            try {
                persistLatest()
                val settings = runtime.settings.load()
                val limits = runtime.settings.effectiveLimits(settings)
                by = CutAgent.label(nexus, projectId)
                _ui.update { it.copy(modelLabel = by.orEmpty()) }
                // A fresh orchestrator/conversation accepts the active model's configuration and no old tool calls.
                val orchestrator = CutAgent.orchestrator(nexus, projectId, settings)
                val nexusProjectId = NexusCut.workspace(nexus).session(projectId).state.value.project.nexusProjectId
                val conversation = orchestrator.newConversation(
                    id = "cutchat_$projectId", mode = AgentMode.AGENT, activeProjectId = nexusProjectId,
                )
                conversation.restoreTextHistory(history)
                val run: suspend () -> com.vidadomu.nexus.agent.core.agent.TurnResult = {
                    orchestrator.send(conversation, text) { event -> onEvent(turnId, event) }
                }
                called = true
                val result = AssistantTurnService.runTurn(nexus) {
                    limits.turnTimeoutMillis?.let { withTimeout(it) { run() } } ?: run()
                }
                // Providers may emit from IO; consume any presentation event before this turn is closed.
                result.events.filterIsInstance<UiEvent.ResultsWall>().forEach(::presentLibraryEvent)
                answer = safe(result.text.trim()).takeIf { it.isNotBlank() }
                notes = result.verifiedNotes.map(::safe) + when (result.stop) {
                    "MAX_ROUNDS" -> listOf("El agente alcanzó el límite de pasos de este turno. Revisa lo aplicado antes de continuar.")
                    else -> emptyList()
                }
                if (answer == null) notes = notes + "El agente terminó sin una respuesta de texto. Revisa el estado del montaje."
            } catch (e: TimeoutCancellationException) {
                failure = "La petición superó el tiempo de espera configurado. Los cambios ya aplicados se conservan; el borrador queda disponible para reintentar."
            } catch (e: CancellationException) {
                failure = "La petición se ha detenido. Los cambios ya aplicados se conservan; no se ha reenviado."
            } catch (e: Exception) {
                failure = if (called) "No se pudo completar la petición: ${safe(e.message ?: e.javaClass.simpleName)}"
                    else "No se pudo preparar o guardar la petición; no se ha enviado al modelo: ${safe(e.message ?: e.javaClass.simpleName)}"
            } finally {
                // Navigation/stop cancels this ViewModel's job, but must still close the persisted pending marker.
                withContext(NonCancellable) {
                    // Deja pasar los últimos eventos del montaje que ya estén en cola antes de cerrar el turno.
                    kotlinx.coroutines.yield()
                    val items = document.messages.toMutableList()
                    // Lo que el agente cambió de verdad en este turno, para revisarlo y deshacerlo de una vez.
                    val group = turnGroups.lastOrNull()
                    val steps = turnSteps.toList()
                    val verified = if (steps.isEmpty()) null else CutTurnSummary.describe(turnFirstSummary, turnLastSummary)
                    var cardId: String? = null
                    if (failure == null) {
                        answer?.let { text ->
                            items += CutChatMessage(id(), CutChatRole.ASSISTANT, text, System.currentTimeMillis(), by?.let(::safe),
                                turnGroup = group, steps = steps, verified = verified, verifiedBefore = turnFirstSummary.takeIf { steps.isNotEmpty() }).also { cardId = it.id }
                        }
                        if (answer == null && steps.isNotEmpty()) items += CutChatMessage(id(), CutChatRole.NOTICE,
                            "El agente aplicó ${steps.size} ${if (steps.size == 1) "cambio" else "cambios"} en el montaje.", System.currentTimeMillis(),
                            turnGroup = group, steps = steps, verified = verified, verifiedBefore = turnFirstSummary.takeIf { steps.isNotEmpty() }).also { cardId = it.id }
                        items += notes.filter { it.isNotBlank() }.map { notice(it) }
                    } else {
                        _ui.value.streamingText.takeIf { it.isNotBlank() }?.let {
                            items += CutChatMessage(id(), CutChatRole.ASSISTANT, safe(it), System.currentTimeMillis(), by?.let(::safe), error = true)
                        }
                        if (steps.isNotEmpty()) items += CutChatMessage(id(), CutChatRole.NOTICE,
                            "Antes de pararse, el agente aplicó ${steps.size} ${if (steps.size == 1) "cambio" else "cambios"}. Puedes deshacerlos aquí.",
                            System.currentTimeMillis(), turnGroup = group, steps = steps, verified = verified, verifiedBefore = turnFirstSummary.takeIf { steps.isNotEmpty() }).also { cardId = it.id }
                        items += notice(failure!!, error = true)
                    }
                    // Solo siguen en pie las propuestas ensayadas sobre la revisión actual (la última de cada turno).
                    val revisionNow = session?.state?.value?.revision
                    val live = turnProposals.filter { it.request.expectedRevision == revisionNow }.takeLast(1)
                        .map { it.copy(messageId = cardId ?: items.lastOrNull()?.id) }
                    val nextDraft = _ui.value.draft.ifBlank { if (failure != null) originalDraft else "" }
                    document = document.copy(messages = items, draft = nextDraft, pending = null)
                    activeTurnId = null
                    _ui.update { it.copy(messages = items, draft = nextDraft, thinking = false, streamingText = "", activity = "", liveSteps = emptyList(), proposals = live) }
                    saveOrDisable()
                }
            }
        }
        return true
    }

    fun stop() { turnJob?.cancel(CancellationException("Detenido desde el editor.")) }

    /** Estado actual de los cambios de un turno en el historial compartido (null si la sesión no está). */
    fun turnStatus(group: String): GroupStatus? = session?.groupStatus(group)

    /**
     * Deshace juntos los pasos del agente de ese turno. Si después se hicieron otros cambios, [includeLater]
     * debe venir confirmado por el usuario: se deshacen también, en orden, y quedan en «Rehacer».
     */
    fun revertTurn(group: String, includeLater: Boolean) {
        val s = session ?: return
        viewModelScope.launch {
            when (val out = s.undoGroup(group, Origin.USER, includeLater)) {
                is Outcome.Done -> _toasts.tryEmit("Turno del agente deshecho")
                is Outcome.Failed -> _toasts.tryEmit(out.error.message)
            }
        }
    }

    fun redoTurn(group: String) {
        val s = session ?: return
        viewModelScope.launch {
            (s.redoGroup(group, Origin.USER) as? Outcome.Failed)?.let { _toasts.tryEmit(it.error.message) }
        }
    }

    /** Aplica tal cual lo que el agente ensayó; si el montaje cambió desde entonces, no se aplica nada. */
    fun applyProposal(proposal: CutProposal) {
        val s = session ?: return
        viewModelScope.launch {
            when (val out = s.execute(proposal.request.copy(dryRun = false, idempotencyKey = "proposal-${proposal.id}"))) {
                is Outcome.Done -> {
                    _ui.update { it.copy(proposals = it.proposals - proposal) }
                    val group = proposal.request.group
                    val items = document.messages.map { m ->
                        if (m.id == proposal.messageId) m.copy(turnGroup = group ?: m.turnGroup, steps = m.steps + safe(out.label),
                            // Lo comprobado pasa a ser el turno entero: del antes del turno al resultado de la propuesta.
                            verified = CutTurnSummary.describe(m.verifiedBefore ?: out.summary, out.summary) ?: m.verified,
                            verifiedBefore = m.verifiedBefore ?: out.summary)
                        else m
                    }
                    document = document.copy(messages = items)
                    _ui.update { it.copy(messages = items) }
                    saveOrDisable()
                    _toasts.tryEmit("Aplicado: ${out.label}")
                }
                is Outcome.Failed -> _toasts.tryEmit(
                    if (out.error.code == ErrorCode.REVISION_CONFLICT) "El montaje cambió después de la propuesta. Pide al agente que la repita."
                    else out.error.message)
            }
        }
    }

    fun dismissProposal(proposal: CutProposal) { _ui.update { it.copy(proposals = it.proposals - proposal) } }

    private fun onCutEvent(event: CutEvent) {
        if (event.origin != Origin.AGENT || activeTurnId == null) return
        when (event) {
            is CutEvent.Committed -> {
                event.group?.let(turnGroups::add)
                turnSteps += safe(event.label)
                if (turnFirstSummary == null) turnFirstSummary = event.summary
                event.summary?.let { turnLastSummary = it }
                _ui.update { it.copy(liveSteps = turnSteps.toList()) }
            }
            is CutEvent.Undone -> { event.group?.let(turnGroups::add); turnSteps += "Deshacer: ${safe(event.label)}"; _ui.update { it.copy(liveSteps = turnSteps.toList()) } }
            is CutEvent.Redone -> { event.group?.let(turnGroups::add); turnSteps += "Rehacer: ${safe(event.label)}"; _ui.update { it.copy(liveSteps = turnSteps.toList()) } }
            is CutEvent.Proposed -> turnProposals += CutProposal(id(), safe(event.label), event.changeCount,
                CutTurnSummary.describe(event.summary, event.summary)?.takeUnless { it == "Comprobado en el montaje" }, event.request)
            is CutEvent.Rejected -> Unit
        }
    }

    fun closeLibraryResults() { _ui.update { it.copy(libraryResultsOpen = false) } }

    fun showLibraryResults() {
        _ui.update { presentCutLibraryResults(it, it.libraryResults, runtime.assets.snapshot()) }
    }

    private fun presentLibraryEvent(event: UiEvent.ResultsWall) {
        if (!presentedLibraryEvents.add(event)) return
        _ui.update { presentCutLibraryResults(it, event.media, runtime.assets.snapshot()) }
    }

    private fun onEvent(turnId: String, event: UiEvent) {
        viewModelScope.launch {
            if (activeTurnId != turnId || !_ui.value.thinking) return@launch
            when (event) {
                is UiEvent.StreamingText -> _ui.update { it.copy(streamingText = safe(event.text)) }
                is UiEvent.Activity -> _ui.update { it.copy(activity = safe(event.text)) }
                is UiEvent.ResultsWall -> presentLibraryEvent(event)
                is UiEvent.Info -> {
                    val text = safe(event.text).trim()
                    if (text.isNotBlank() && document.messages.lastOrNull()?.let { it.role == CutChatRole.NOTICE && it.text == text } != true) {
                        document = document.copy(messages = document.messages + notice(text))
                        _ui.update { it.copy(messages = document.messages) }
                        saveOrDisable()
                    }
                }
                else -> Unit // No generation/proposal cards or main Assistant state in the montage chat.
            }
        }
    }

    private suspend fun persistLatest() = saveMutex.withLock {
        val snapshot = document
        withContext(Dispatchers.IO) { store.save(snapshot) }
    }

    private suspend fun saveOrDisable() {
        try { persistLatest() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val message = notice("No se pudo guardar el chat: ${safe(e.message ?: e.javaClass.simpleName)}. El texto sigue aquí; vuelve a abrir el montaje antes de enviar otra petición.", true)
            document = document.copy(messages = document.messages + message)
            _ui.update { it.copy(messages = document.messages, ready = false) }
            turnJob?.takeIf { it.isActive }?.cancel(CancellationException("El chat no se pudo guardar."))
        }
    }

    override fun onCleared() {
        stop()
        if (_ui.value.ready && !_ui.value.thinking) {
            // A last keystroke may still be queued when navigation clears viewModelScope.
            CoroutineScope(Dispatchers.Main.immediate + NonCancellable).launch { saveOrDisable() }
        }
        super.onCleared()
    }

    private fun notice(text: String, error: Boolean = false) = CutChatMessage(id(), CutChatRole.NOTICE, safe(text), System.currentTimeMillis(), error = error)
    private fun safe(text: String) = Redactor.redact(text)
    private fun id() = UUID.randomUUID().toString()

    class Factory(private val app: Application, private val projectId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CutChatViewModel(app, projectId) as T
    }
}
