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
import com.vidadomu.nexus.agent.core.tools.AgentStep
import com.vidadomu.nexus.agent.core.tools.AgentStepStatus
import com.vidadomu.nexus.agent.core.tools.AgentSteps
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** Elementos del chat. Las tarjetas de plan se pintan a partir de [AssistantUiState.plans]. */
@kotlinx.serialization.Serializable
sealed interface ChatItem {
    val id: String
    @kotlinx.serialization.Serializable
    data class User(override val id: String, val text: String, val attachments: List<Attachment>, val at: Long = 0L,
        val widgetAnswer: com.vidadomu.nexus.agent.core.chat.WidgetAnswer? = null) : ChatItem
    @kotlinx.serialization.Serializable
    data class Assistant(override val id: String, val text: String, val toolCalls: List<String>, val stop: String,
        val media: List<Attachment> = emptyList(), val at: Long = 0L, val tookMs: Long = 0L,
        /** Who answered («AGY · Gemini 3.8 Flash», «Bedrock · …»): the agent can change between messages. */
        val by: String = "",
        /** What the agent did to get here (model calls and tools, with their duration and outcome); empty in older chats. */
        val steps: List<AgentStep> = emptyList()) : ChatItem
    /** Una tarjeta por encargo: [orderId] es persistente y [planId] apunta al plan vigente. */
    @kotlinx.serialization.Serializable
    data class PlanCard(override val id: String, val planId: String, val orderId: String? = null) : ChatItem
    @kotlinx.serialization.Serializable
    data class Proposal(override val id: String, val actionId: String, val description: String, val state: ProposalState = ProposalState.PENDING, val result: String? = null) : ChatItem
    @kotlinx.serialization.Serializable
    data class Notice(override val id: String, val text: String, val error: Boolean = false,
        /** Legacy notices have no event time: restoring them must not announce a fresh error. */
        val at: Long = 0L,
        /** Activity of the turn this notice closed (a stopped or failed answer keeps what it had done). */
        val steps: List<AgentStep> = emptyList()) : ChatItem
    /** Persisted by 1.8.2; opening history never changes the current mode. */
    @kotlinx.serialization.Serializable
    data class ModeSwitch(override val id: String, val text: String, val resolved: Boolean = false) : ChatItem
    /** A board of imported images the user can arrange and draw on; its content lives in [ChatBoardStore]. */
    @kotlinx.serialization.Serializable
    data class Board(override val id: String, val boardId: String) : ChatItem
    /** Summary carried from another conversation; it reaches the agent with the first message ([sent] after that). */
    @kotlinx.serialization.Serializable
    data class Context(override val id: String, val from: String, val summary: String, val sent: Boolean = false) : ChatItem
}

enum class ProposalState { PENDING, APPLYING, APPLIED, REJECTED, FAILED }

/** Consecutive turns of the same role become one (a carried context followed by the first message, a reply that failed…). */
internal fun mergeTurns(turns: List<Pair<String, String>>): List<Pair<String, String>> =
    turns.fold(mutableListOf()) { acc, turn ->
        val last = acc.lastOrNull()
        if (last != null && last.first == turn.first) acc[acc.lastIndex] = last.first to (last.second + "\n\n" + turn.second) else acc += turn
        acc
    }

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
    /** Estado verificado de cada encargo (misma fuente que ve el agente). */
    val orders: Map<String, com.vidadomu.nexus.agent.core.orders.Order> = emptyMap(),
    val thinking: Boolean = false,
    /** Monotonic start of the current turn. Survives LazyColumn disposal and chat switching. */
    val thinkingStartedAtElapsedMs: Long = 0L,
    /**
     * Monotonic time of the turn's last sign of life: its start, a step that started or ended, or text that arrived.
     * It lives with the turn, not with the row that paints it, so the note about a stuck step neither starts again
     * when the activity scrolls out of view nor moves when the wall clock is changed. 0 without a turn.
     */
    val lastActivityAtElapsedMs: Long = 0L,
    /** Texto parcial durante el streaming de un proveedor compatible (p. ej. Antigravity). */
    val streamingText: String = "",
    /** Pasos que el agente va haciendo en este turno (para «Pensando…»). */
    val activity: List<String> = emptyList(),
    /** The same turn step by step, as the orchestrator reports it: what runs now, what ended and how long it took. */
    val steps: List<AgentStep> = emptyList(),
    /** «Añade algo mientras trabaja»: the draft (text and attachments) goes out as the next message when this answer ends. */
    val queuedSend: Boolean = false,
    /**
     * The failure notice whose message the app still holds in memory: that one, and only that one, offers
     * «Reintentar», and it sends the same text and attachments. Null once anything else is sent or the conversation
     * changes; a notice restored from disk never has it.
     */
    val retryNoticeId: String? = null,
    /** Orders whose job is no longer tracked but still exists at the provider: their card offers «Recuperar». */
    val recoverable: Set<String> = emptySet(),
    /** Respuestas rápidas de la última respuesta del agente; se borran al enviar cualquier mensaje. */
    val replies: List<com.vidadomu.nexus.agent.core.tools.SuggestedReply> = emptyList(),
    val scrollRequest: Long = 0,
    val lastLatencyMs: Long? = null,
    /** «modelo 8,1 s · herramientas 0,4 s · 2 pasos» del último turno, para ver dónde se fue el tiempo. */
    val lastTurnBreakdown: String? = null,
    val catalog: List<com.vidadomu.nexus.agent.core.registry.ModelSummary> = emptyList(),
    val projectAssets: List<com.vidadomu.nexus.agent.core.assets.Asset> = emptyList(),
    val settings: AssistantSettings = AssistantSettings(),
    val mode: AgentMode = AgentMode.AGENT,
    val projects: List<Project> = emptyList(),
    val activeProjectId: String? = null,
    val conversationId: String = java.util.UUID.randomUUID().toString(),
    /** Conversation where the agent keeps working while another one is on screen (null = none). */
    val workingIn: String? = null,
    /** Preparing the summary for «Nueva con este contexto». */
    val carrying: Boolean = false,
    val draftRevision: Long = 0,
    val pendingAttachments: List<Attachment> = emptyList(),
    val resultsWall: List<Attachment> = emptyList(),
    val attaching: Boolean = false,
    val skillsCount: Int = 0,
    val skillErrors: List<String> = emptyList(),
    /** Mensaje efímero (snackbar) con id, para que dos iguales seguidos se muestren los dos. */
    val message: Pair<Long, String>? = null,
)

/** A step that started or ended is a sign of life of the turn in progress. */
internal fun AssistantUiState.withStep(step: AgentStep, nowElapsedMs: Long): AssistantUiState =
    copy(steps = AgentSteps.upsert(steps, step), lastActivityAtElapsedMs = nowElapsedMs)

/**
 * Text that arrived is a sign of life too. The same text again is not, and neither is the empty text that clears
 * the field before each call to the model: nothing has come back yet.
 */
internal fun AssistantUiState.withStreamingText(text: String, nowElapsedMs: Long): AssistantUiState =
    if (text == streamingText) this
    else copy(streamingText = text, lastActivityAtElapsedMs = if (text.isEmpty()) lastActivityAtElapsedMs else nowElapsedMs)
