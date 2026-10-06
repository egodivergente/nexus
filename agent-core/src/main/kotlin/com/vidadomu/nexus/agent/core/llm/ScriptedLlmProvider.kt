package com.vidadomu.nexus.agent.core.llm

/**
 * LLM de guion para pruebas y para el modo demo sin red: cada llamada recibe la petición completa
 * (para afirmar qué herramientas y mensajes ve el modelo) y devuelve la respuesta programada.
 */
class ScriptedLlmProvider(private val steps: List<(LlmRequest) -> LlmResponse>) : LlmProvider {
    override val id = "scripted"
    val requests = mutableListOf<LlmRequest>()
    private var next = 0

    override suspend fun complete(request: LlmRequest): LlmResponse {
        requests += request
        val step = steps.getOrNull(next++) ?: throw LlmException(LlmException.Kind.PROTOCOL, "Guion agotado en la llamada $next.")
        return step(request)
    }

    companion object {
        fun text(t: String) = LlmResponse(LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text(t))), StopReason.END_TURN, Usage(10, 5))
        fun tools(vararg uses: ContentBlock.ToolUse, text: String? = null) = LlmResponse(
            LlmMessage(Role.ASSISTANT, listOfNotNull(text?.let { ContentBlock.Text(it) }) + uses), StopReason.TOOL_USE, Usage(10, 5),
        )
    }
}
