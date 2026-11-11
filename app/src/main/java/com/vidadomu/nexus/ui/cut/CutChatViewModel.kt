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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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

    init {
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
        document = document.copy(messages = document.messages + user, draft = originalDraft, pending = PendingCutChatTurn(user.id, user.at))
        _ui.update { it.copy(messages = document.messages, draft = "", thinking = true, streamingText = "", activity = "Preparando el montaje") }
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
                    val items = document.messages.toMutableList()
                    if (failure == null) {
                        answer?.let { items += CutChatMessage(id(), CutChatRole.ASSISTANT, it, System.currentTimeMillis(), by?.let(::safe)) }
                        items += notes.filter { it.isNotBlank() }.map { notice(it) }
                    } else {
                        _ui.value.streamingText.takeIf { it.isNotBlank() }?.let {
                            items += CutChatMessage(id(), CutChatRole.ASSISTANT, safe(it), System.currentTimeMillis(), by?.let(::safe), error = true)
                        }
                        items += notice(failure!!, error = true)
                    }
                    val nextDraft = _ui.value.draft.ifBlank { if (failure != null) originalDraft else "" }
                    document = document.copy(messages = items, draft = nextDraft, pending = null)
                    activeTurnId = null
                    _ui.update { it.copy(messages = items, draft = nextDraft, thinking = false, streamingText = "", activity = "") }
                    saveOrDisable()
                }
            }
        }
        return true
    }

    fun stop() { turnJob?.cancel(CancellationException("Detenido desde el editor.")) }

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
