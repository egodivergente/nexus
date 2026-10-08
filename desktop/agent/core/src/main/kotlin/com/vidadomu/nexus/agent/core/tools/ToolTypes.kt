package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Clase de efecto de una herramienta; el modo del agente decide qué se permite. */
enum class ToolCategory {
    /** Consulta sin efectos. */
    READ,
    /** Prepara planes (no gasta, no cambia Nexus). */
    PLAN,
    /** Cambia metadatos (proyectos, asociaciones, nombres lógicos) o cancela jobs. */
    ORGANIZE,
}

/** Archivo adjunto por el usuario en esta conversación. */
@Serializable
data class Attachment(
    val id: String,
    val uri: String,
    val mimeType: String,
    val name: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSec: Double? = null,
    val assetId: String? = null,
)

/** Eventos para la UI: tarjetas de plan, propuestas a aplicar, etc. */
sealed interface UiEvent {
    data class PlanReady(val plan: GenerationPlan, val executable: Boolean) : UiEvent
    data class AwaitingConfirmation(val planId: String) : UiEvent
    data class ProposedAction(val id: String, val description: String) : UiEvent
    data class Info(val text: String) : UiEvent
}

class ToolOutcome(val result: JsonElement, val isError: Boolean = false, val events: List<UiEvent> = emptyList())

/** Estado por conversación que comparten las herramientas. */
class AgentSession(
    val id: String,
    var mode: AgentMode,
    var activeProjectId: String? = null,
) {
    val attachments = mutableListOf<Attachment>()
    /** Acciones de organización propuestas en modo Copiloto, pendientes de que el usuario las aplique. */
    val pendingActions = linkedMapOf<String, PendingAction>()
    var flowSubmittedResults = 0

    fun attachment(id: String): Attachment? = attachments.firstOrNull { it.id == id }
}

class PendingAction(val id: String, val description: String, val apply: suspend () -> JsonElement)

interface AgentTool {
    val name: String
    val description: String
    val category: ToolCategory
    val inputSchema: JsonObject

    /**
     * Solo para ORGANIZE: cambios aditivos y reversibles (crear un proyecto) que el Copiloto puede
     * aplicar sin proponerlos. Lo que modifica algo existente siempre es propuesta en Copiloto.
     */
    val additive: Boolean get() = false

    /** Texto de la propuesta que ve el usuario en modo Copiloto. */
    fun describe(input: JsonObject): String = name

    suspend fun run(input: JsonObject, session: AgentSession): ToolOutcome
}
