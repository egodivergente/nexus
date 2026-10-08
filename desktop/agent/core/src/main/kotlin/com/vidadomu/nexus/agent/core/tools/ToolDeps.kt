package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.cost.CostEstimator
import com.vidadomu.nexus.agent.core.registry.ModelRegistry
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.router.ModelRouter
import com.vidadomu.nexus.agent.core.skills.SkillStore
import com.vidadomu.nexus.agent.core.spend.GuardedExecutor
import com.vidadomu.nexus.agent.core.spend.SpendGuard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Entrada del historial propio de Nexus (tabla generations), para get_generation_history. */
@Serializable
data class HistoryEntry(
    val jobId: String,
    val providerId: String,
    val modelId: String,
    val modality: String,
    val prompt: String,
    val status: String,
    val createdAt: Long,
    val costUsd: Double? = null,
    val outputs: List<String> = emptyList(),
)

fun interface GenerationHistorySource {
    suspend fun recent(limit: Int): List<HistoryEntry>
}

/** Todo lo que necesitan las herramientas. En Android lo montan adaptadores sobre los repositorios de Nexus. */
class ToolDeps(
    val registry: ModelRegistry,
    val skills: SkillStore,
    val cost: CostEstimator,
    val router: ModelRouter,
    val guard: SpendGuard,
    val executor: GuardedExecutor,
    val assets: AssetIndex,
    val history: GenerationHistorySource? = null,
    val clock: () -> Long = System::currentTimeMillis,
    val directFlow: Boolean = false,
) {
    val plans = PlanBuilder(registry, cost, assets, clock)
    val lifecycle = com.vidadomu.nexus.agent.core.agent.GenerationLifecycle(guard, executor, assets, clock)

    /** Resuelve provider_id + model_id contra el registro ACTUAL. Nunca acepta un modelo inventado. */
    suspend fun resolveModel(input: JsonObject): ModelSummary {
        val id = input.req("model_id")
        val provider = input.str("provider_id")
        val matches = registry.list().filter { it.ref.modelId == id && (provider == null || it.ref.providerId == provider) }
        return when {
            matches.size == 1 -> matches.single()
            matches.isEmpty() -> throw ToolInputException("«$id»${provider?.let { " ($it)" } ?: ""} no está en el registro actual de Nexus. Búscalo con search_models.")
            else -> throw ToolInputException("«$id» existe en varios proveedores (${matches.joinToString { it.ref.providerId }}): indica provider_id.")
        }
    }
}

/** Utilidad común: todas las herramientas devuelven errores de uso como resultado, no como excepción. */
abstract class BaseTool(
    final override val name: String,
    final override val category: ToolCategory,
    final override val description: String,
    final override val inputSchema: JsonObject,
) : AgentTool {
    final override suspend fun run(input: JsonObject, session: AgentSession): ToolOutcome =
        try { execute(input, session) } catch (e: ToolInputException) { fail(e.message ?: "Entrada no válida.") }

    protected abstract suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome
}
