import com.vidadomu.nexus.agent.core.llm.*
import kotlinx.serialization.json.*

/** Keep every capability available while respecting Sol's 64-tool transport limit. */
class WindowsToolProvider(private val delegate: LlmProvider) : LlmProvider {
    override val id get() = delegate.id
    override suspend fun complete(request: LlmRequest) = delegate.complete(select(request))
    override suspend fun countInputTokens(request: LlmRequest) = delegate.countInputTokens(select(request))
    companion object {
        fun wrap(delegate: LlmProvider): LlmProvider = if (delegate is StreamingLlmProvider)
            WindowsStreamingToolProvider(delegate) else WindowsToolProvider(delegate)
        val connections = setOf("web_search", "read_web_page", "list_mcp_servers", "connect_mcp_server", "list_mcp_tools",
            "call_mcp_tool", "disconnect_mcp_server", "get_active_generations", "recover_generation", "diagnose_flow",
            "repair_flow", "get_nexus_settings", "update_nexus_settings", "atlas_get_balance")
        val models = setOf("list_models", "search_models", "get_model", "get_model_schema", "recommend_models",
            "validate_parameters", "estimate_generation_cost", "create_generation_prompt", "compare_model_families",
            "prepare_generation", "prepare_image_upscale", "update_plan", "submit_generation", "get_generation_status",
            "cancel_generation", "get_model_brief")
        fun select(request: LlmRequest): LlmRequest {
            if (request.tools.size <= 64) return request
            val latestUser = request.messages.indexOfLast { message -> message.role == Role.USER &&
                message.content.none { it is ContentBlock.ToolResult } }
            val group = request.messages.drop((latestUser + 1).coerceAtLeast(0)).flatMap { it.content }
                .filterIsInstance<ContentBlock.ToolUse>().lastOrNull { it.name == "use_desktop_tools" }
                ?.input?.get("group")?.jsonPrimitive?.contentOrNull ?: "models"
            val hidden = if (group == "connections") models else connections
            val selected = request.tools.filterNot { it.name in hidden }
            require(selected.size <= 64) { "El catálogo de herramientas supera el límite del transporte; revisa los grupos de escritorio." }
            return request.copy(tools = selected)
        }
    }
}

private class WindowsStreamingToolProvider(private val delegate: StreamingLlmProvider) : StreamingLlmProvider {
    override val id get() = delegate.id
    override suspend fun complete(request: LlmRequest) = delegate.complete(WindowsToolProvider.select(request))
    override suspend fun countInputTokens(request: LlmRequest) = delegate.countInputTokens(WindowsToolProvider.select(request))
    override suspend fun completeStreaming(request: LlmRequest, onTextDelta: (String) -> Unit) =
        delegate.completeStreaming(WindowsToolProvider.select(request), onTextDelta)
}
