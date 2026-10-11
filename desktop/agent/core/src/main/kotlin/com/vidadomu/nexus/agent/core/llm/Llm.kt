package com.vidadomu.nexus.agent.core.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Tipos propios de conversación. No dependen de Bedrock: cualquier proveedor (Bedrock vía proxy,
 * otro gateway, un modelo local) los traduce en su borde. Así cambiar Opus por Sonnet/Haiku u otro
 * proveedor es configuración, no código.
 */
enum class Role { USER, ASSISTANT }

sealed interface ContentBlock {
    data class Text(val text: String) : ContentBlock
    /** Opaque, signed provider context. Never display or log its contents. */
    class Reasoning(val content: JsonObject) : ContentBlock {
        override fun toString() = "Reasoning([private provider context])"
    }
    class Document(val format: String, val name: String, val bytes: ByteArray) : ContentBlock {
        override fun toString() = "Document($format, ${bytes.size} bytes)"
    }

    data class ToolUse(val id: String, val name: String, val input: JsonObject) : ContentBlock

    data class ToolResult(
        val toolUseId: String,
        val content: JsonElement,
        val isError: Boolean = false,
    ) : ContentBlock

    /** Imagen para modelos con visión (p. ej. el keyframe adjunto). [format]: png, jpeg, webp, gif. */
    class Image(val format: String, val bytes: ByteArray) : ContentBlock {
        override fun toString(): String = "Image($format, ${bytes.size} bytes)"
    }
}

data class LlmMessage(val role: Role, val content: List<ContentBlock>) {
    fun text(): String = content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
    fun toolUses(): List<ContentBlock.ToolUse> = content.filterIsInstance<ContentBlock.ToolUse>()
}

data class ToolSpec(val name: String, val description: String, val inputSchema: JsonObject)

data class LlmRequest(
    val modelId: String,
    val system: List<String>,
    val messages: List<LlmMessage>,
    val tools: List<ToolSpec>,
    val maxTokens: Int,
    val temperature: Double? = null,
    val turnId: String? = null,
)

enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, STOP_SEQUENCE, GUARDRAIL, CONTENT_FILTERED, OTHER }

data class Usage(val inputTokens: Int, val outputTokens: Int)

data class LlmResponse(val message: LlmMessage, val stopReason: StopReason, val usage: Usage)

interface LlmProvider {
    val id: String
    suspend fun complete(request: LlmRequest): LlmResponse
    suspend fun countInputTokens(request: LlmRequest): Int? = null
}

/** Error de proveedor con mensaje apto para el usuario (sin secretos ni cuerpos de petición). */
class LlmException(
    val kind: Kind,
    message: String,
    val httpStatus: Int? = null,
) : Exception(message) {
    enum class Kind { AUTH, BUDGET, THROTTLED, BAD_REQUEST, MODEL_NOT_ALLOWED, UPSTREAM, NETWORK, PROTOCOL }
}
