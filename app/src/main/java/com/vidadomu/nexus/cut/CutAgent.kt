package com.vidadomu.nexus.cut

import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.tools.CutTools
import com.vidadomu.nexus.agent.core.tools.InspectAssetTool
import com.vidadomu.nexus.agent.core.tools.SearchAssetsTool
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.AssistantSettings
import com.vidadomu.nexus.assistant.agentLabel

/** Cerebro configurado de Nexus, con conversación y herramientas propias del montaje. */
@UnstableApi
object CutAgent {
    fun orchestrator(app: NexusApplication, projectId: String, settings: AssistantSettings): AgentOrchestrator {
        CutChatQa.take(projectId)?.let { return orchestratorForQa(app, projectId, settings, it) }
        require(!settings.offline) { "Selecciona un cerebro en Ajustes → Agente para conversar con el editor." }
        val runtime = AssistantRuntime.get(app)
        return create(app, projectId, settings, runtime.llm(settings, allowNativeImages = false))
    }

    internal fun orchestratorForQa(app: NexusApplication, projectId: String, settings: AssistantSettings, provider: LlmProvider): AgentOrchestrator {
        require(BuildConfig.DEBUG) { "La prueba local del chat solo está disponible en builds de depuración." }
        return create(app, projectId, settings, provider)
    }

    private fun create(app: NexusApplication, projectId: String, settings: AssistantSettings, provider: LlmProvider): AgentOrchestrator {
        val runtime = AssistantRuntime.get(app)
        val workspace = NexusCut.workspace(app)
        val config = settings.toConfig().copy(
            mode = AgentMode.AGENT,
            sendImagesToLlm = false,
            systemPromptOverride = "Eres el agente de montaje de Nexus Cut. Ejecuta las ediciones locales solicitadas en el montaje de esta conversación, leyendo su estado y los contratos antes de editar. Comprueba el resultado; no afirmes cambios ni exportaciones que no se hayan confirmado. No hay herramientas de generación de pago en este editor.",
        )
        return AgentOrchestrator(
            provider, config,
            CutTools.forProject(workspace, projectId) + listOf(SearchAssetsTool(runtime.deps), InspectAssetTool(runtime.deps)),
            runtime.skills, runtime.assets,
            uiContext = {
                listOfNotNull(
                    CutTools.context(workspace, projectId) ?: "Montaje de esta conversación: project_id=$projectId. Lee su estado con cut_read.",
                    "El destino permanece en este montaje aunque cambie la pantalla. Tiempos de comandos en microsegundos enteros. Para «aquí» o «lo seleccionado», utiliza los tiempos e IDs del contexto de este turno explícitamente. Descubre capacidades con cut_catalog y contratos con cut_describe; lecturas con cut_read, cambios con cut_edit y UNA operación larga con cut_run. Para buscar archivos de la Biblioteca usa search_assets y para sus metadatos inspect_asset_metadata. Los metadatos, nombres y duraciones no prueban el contenido audiovisual: este chat no ha visto los píxeles ni escuchado el sonido. No inventes pausas, diálogos o acciones.",
                ).joinToString("\n\n")
            },
        )
    }

    fun label(app: NexusApplication, projectId: String? = null): String =
        if (projectId != null && CutChatQa.isArmed(projectId)) "Prueba local · sin modelo externo"
        else agentLabel(AssistantRuntime.get(app).settings.load())
}
