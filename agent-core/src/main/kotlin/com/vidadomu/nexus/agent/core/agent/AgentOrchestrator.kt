package com.vidadomu.nexus.agent.core.agent

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.LlmMessage
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.llm.StopReason
import com.vidadomu.nexus.agent.core.llm.ToolSpec
import com.vidadomu.nexus.agent.core.llm.Usage
import com.vidadomu.nexus.agent.core.log.AgentLogger
import com.vidadomu.nexus.agent.core.log.Redactor
import com.vidadomu.nexus.agent.core.skills.SkillStore
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.AgentTool
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.PendingAction
import com.vidadomu.nexus.agent.core.tools.ToolCategory
import com.vidadomu.nexus.agent.core.tools.UiEvent
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Miniatura que el LLM puede ver (keyframe adjunto). La app decide tamaño y formato. */
fun interface AttachmentImageLoader {
    suspend fun load(attachment: Attachment): ContentBlock.Image?
}

/** Una conversación con el asistente: historial para el LLM + estado compartido por las herramientas. */
class AgentConversation(val session: AgentSession) {
    internal val messages = mutableListOf<LlmMessage>()
    internal val mutex = Mutex()
    fun history(): List<LlmMessage> = messages.toList()
}

data class TurnResult(
    val text: String,
    val events: List<UiEvent>,
    val usage: Usage,
    val toolRounds: Int,
    val toolCalls: List<String>,
    /** END_TURN, MAX_ROUNDS, MAX_TOKENS… */
    val stop: String,
)

/**
 * Bucle de razonamiento con herramientas. Garantías:
 * - el modo decide qué herramientas ve el LLM (Asistente: solo lectura);
 * - en Copiloto, los cambios de organización no aditivos se convierten en propuestas;
 * - nunca hay camino del LLM al gasto: no existe herramienta que confirme (ver [GenerationLifecycle]);
 * - tope de rondas por mensaje para cortar bucles;
 * - los errores de herramientas vuelven al LLM sin secretos.
 */
class AgentOrchestrator(
    private val llm: LlmProvider,
    private val config: AgentConfig,
    tools: List<AgentTool>,
    private val skills: SkillStore,
    private val assets: AssetIndex,
    private val logger: AgentLogger = AgentLogger { _, _ -> },
    private val images: AttachmentImageLoader? = null,
    private val today: () -> String = { LocalDate.now().toString() },
) {
    private val byName = tools.associateBy { it.name }

    init {
        require(byName.keys.none { FORBIDDEN_NAME.containsMatchIn(it) }) { "Ninguna herramienta puede confirmar o pagar." }
    }

    fun newConversation(id: String = AssetIndex.newId("conv"), mode: AgentMode = config.mode, activeProjectId: String? = null) =
        AgentConversation(AgentSession(id, mode, activeProjectId))

    fun toolsFor(mode: AgentMode): List<AgentTool> = byName.values.filter { allowed(it, mode) }

    private fun allowed(t: AgentTool, mode: AgentMode) = when (mode) {
        AgentMode.ASSISTANT -> t.category == ToolCategory.READ
        AgentMode.COPILOT, AgentMode.AGENT -> true
    }

    suspend fun send(
        conv: AgentConversation,
        text: String,
        newAttachments: List<Attachment> = emptyList(),
        onEvent: (UiEvent) -> Unit = {},
    ): TurnResult = conv.mutex.withLock {
        val session = conv.session
        val start = conv.messages.size
        newAttachments.forEach { a -> if (session.attachment(a.id) == null) session.attachments += a }

        val blocks = mutableListOf<ContentBlock>()
        blocks += ContentBlock.Text(userText(text, newAttachments))
        val loader = images
        if (config.sendImagesToLlm && loader != null) {
            newAttachments.filter { it.mimeType.startsWith("image/") }.take(MAX_IMAGES_PER_TURN).forEach { a ->
                runCatching { loader.load(a) }.getOrNull()?.let { blocks += it }
            }
        }
        appendUser(conv, blocks)
        trimHistory(conv)

        val events = mutableListOf<UiEvent>()
        val calls = mutableListOf<String>()
        var input = 0; var output = 0; var rounds = 0
        var stop = "END_TURN"
        val turnTexts = mutableListOf<String>()
        val tools = if (config.toolUse) toolsFor(session.mode) else emptyList()
        val specs = tools.map { ToolSpec(it.name, it.description, it.inputSchema) }
        val system = SystemPrompt.build(
            session.mode, config.systemPromptOverride, skills.catalogText(),
            session.activeProjectId?.let { assets.findProject(it)?.name ?: it }, today(),
            session.activeProjectId?.let { assets.projectContext(it) },
        )

        while (true) {
            val response = try {
                llm.complete(LlmRequest(config.modelId, system, conv.messages.toList(), specs, config.maxTokens, config.temperature))
            } catch (e: LlmException) {
                logger.log("W", "LLM ${e.kind}: ${Redactor.redact(e.message ?: "")}")
                if (rounds == 0) while (conv.messages.size > start) conv.messages.removeAt(conv.messages.lastIndex)
                throw e
            }
            input += response.usage.inputTokens; output += response.usage.outputTokens
            val msg = response.message.let { if (it.content.isEmpty()) LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text("…"))) else it.copy(role = Role.ASSISTANT) }
            conv.messages += msg
            msg.text().takeIf { it.isNotBlank() }?.let { turnTexts += it }
            val uses = msg.toolUses()
            if (uses.isEmpty()) { stop = response.stopReason.name; break }

            if (rounds >= config.maxToolRounds) {
                // Contestar a las llamadas pendientes para que el historial siga siendo válido, y parar.
                conv.messages += LlmMessage(Role.USER, uses.map {
                    ContentBlock.ToolResult(it.id, buildJsonObject { put("error", "Límite de pasos alcanzado; resume y espera al usuario.") }, isError = true)
                })
                stop = "MAX_ROUNDS"
                turnTexts += "He parado tras ${config.maxToolRounds} pasos para no entrar en bucle. Dime cómo seguir."
                break
            }
            val results = uses.map { use ->
                calls += use.name
                val (result, isError, evs) = runTool(use, session)
                evs.forEach { events += it; onEvent(it) }
                ContentBlock.ToolResult(use.id, result, isError)
            }
            conv.messages += LlmMessage(Role.USER, results)
            rounds++
            if (response.stopReason == StopReason.MAX_TOKENS) { stop = "MAX_TOKENS"; }
        }
        TurnResult(turnTexts.lastOrNull().orEmpty(), events, Usage(input, output), rounds, calls, stop)
    }

    private suspend fun runTool(use: ContentBlock.ToolUse, session: AgentSession): Triple<JsonElement, Boolean, List<UiEvent>> {
        val tool = byName[use.name]
        val t0 = System.nanoTime()
        if (tool == null || !allowed(tool, session.mode)) {
            logger.log("W", "tool ${use.name} rechazada en modo ${session.mode}")
            return Triple(buildJsonObject { put("error", "La herramienta ${use.name} no está disponible en modo ${session.mode.name.lowercase()}.") }, true, emptyList())
        }
        if (tool.category == ToolCategory.ORGANIZE && session.mode == AgentMode.COPILOT && !tool.additive) {
            val id = AssetIndex.newId("act")
            val description = runCatching { tool.describe(use.input) }.getOrDefault(tool.name)
            session.pendingActions[id] = PendingAction(id, description) {
                val out = tool.run(use.input, session)
                if (out.isError) throw IllegalStateException(out.result.toString())
                out.result
            }
            logger.log("I", "tool ${use.name} → propuesta $id")
            return Triple(
                buildJsonObject { put("proposed", true); put("action_id", id); put("description", description); put("message", "Propuesta mostrada al usuario; no se ha aplicado.") },
                false, listOf(UiEvent.ProposedAction(id, description)),
            )
        }
        val outcome = try {
            tool.run(use.input, session)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.log("E", "tool ${use.name} falló: ${e.javaClass.simpleName}: ${Redactor.redact(e.message ?: "")}")
            return Triple(buildJsonObject { put("error", Redactor.redact(e.message ?: e.javaClass.simpleName).take(400)) }, true, emptyList())
        }
        logger.log("I", "tool ${use.name} ${if (outcome.isError) "error" else "ok"} ${(System.nanoTime() - t0) / 1_000_000} ms")
        return Triple(outcome.result, outcome.isError, outcome.events)
    }

    /** La UI aplica una propuesta del Copiloto (toque del usuario en «Aplicar»). */
    suspend fun applyPending(conv: AgentConversation, actionId: String): JsonElement {
        val action = conv.session.pendingActions.remove(actionId) ?: throw IllegalArgumentException("Propuesta desconocida o ya aplicada.")
        return action.apply()
    }

    fun rejectPending(conv: AgentConversation, actionId: String) { conv.session.pendingActions.remove(actionId) }

    private fun userText(text: String, atts: List<Attachment>): String = if (atts.isEmpty()) text else buildString {
        append(text.trim())
        append("\n\n[Adjuntos: ")
        append(atts.joinToString("; ") { a ->
            listOfNotNull("attachment_id=${a.id}", a.mimeType, a.name, if (a.width != null && a.height != null) "${a.width}×${a.height}" else null,
                a.durationSec?.let { "%.1f s".format(java.util.Locale.ROOT, it) }).joinToString(", ")
        })
        append("]")
    }

    /** Si el último mensaje es del usuario (p. ej. tras cortar por rondas), se fusiona para alternar roles. */
    private fun appendUser(conv: AgentConversation, blocks: List<ContentBlock>) {
        val last = conv.messages.lastOrNull()
        if (last != null && last.role == Role.USER) conv.messages[conv.messages.lastIndex] = last.copy(content = last.content + blocks)
        else conv.messages += LlmMessage(Role.USER, blocks)
    }

    private fun trimHistory(conv: AgentConversation) {
        val m = conv.messages
        // Imágenes solo en los dos últimos mensajes de usuario: ahorra tokens sin perder el contexto textual.
        val userIdx = m.indices.filter { m[it].role == Role.USER }
        userIdx.dropLast(2).forEach { i ->
            if (m[i].content.any { it is ContentBlock.Image }) {
                m[i] = m[i].copy(content = m[i].content.map { if (it is ContentBlock.Image) ContentBlock.Text("[imagen enviada antes]") else it })
            }
        }
        while (m.size > MAX_MESSAGES || (m.isNotEmpty() && !isPlainUser(m.first()))) {
            m.removeAt(0)
            if (m.isEmpty()) break
        }
    }

    private fun isPlainUser(msg: LlmMessage) = msg.role == Role.USER && msg.content.none { it is ContentBlock.ToolResult }

    companion object {
        const val MAX_MESSAGES = 60
        const val MAX_IMAGES_PER_TURN = 4
        private val FORBIDDEN_NAME = Regex("confirm|approve|pay|charge|execute_paid", RegexOption.IGNORE_CASE)
    }
}
