package com.vidadomu.nexus.cut

import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.tools.CutTools
import com.vidadomu.nexus.agent.core.tools.ProjectTransferTools
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
            CutTools.forProject(workspace, projectId) + cutLibraryTools(runtime.deps) + listOf(
                com.vidadomu.nexus.agent.core.tools.ListProjectsTool(runtime.deps),
                com.vidadomu.nexus.agent.core.tools.CreateProjectTool(runtime.deps),
            ) + ProjectTransferTools.all(runtime.deps),
            runtime.skills, runtime.assets,
            uiContext = {
                listOfNotNull(
                    CutTools.context(workspace, projectId) ?: "Montaje de esta conversación: project_id=$projectId. Lee su estado con cut_read.",
                    "El destino de edición permanece en este montaje aunque cambie la pantalla. Tiempos de comandos en microsegundos enteros. Para «aquí» o «lo seleccionado», utiliza los tiempos e IDs del contexto de este turno explícitamente. Descubre capacidades con cut_catalog y contratos con cut_describe; lecturas con cut_read, cambios con cut_edit y UNA operación larga con cut_run. Para buscar archivos de la Biblioteca usa search_assets y, para recorrerla por páginas, list_library_assets cuando esté disponible; sus metadatos se leen con inspect_asset_metadata. Si el usuario pide ver los archivos encontrados, invoca open_results_wall con sus asset_ids reales: abre miniaturas y el visor en este editor, donde el usuario puede reproducirlos y añadirlos al montaje. Abrir el muro no añade medios ni adjunta archivos al modelo. Solo importa al montaje mediante cut_edit/media.import si el usuario lo pide. Los metadatos, nombres y duraciones no prueban el contenido audiovisual: este chat no ha visto los píxeles ni escuchado el sonido. No hay view_library_asset en este editor; no afirmes haber visto vídeos o imágenes. No inventes pausas, diálogos o acciones.",
                    "Solo gestiona otros proyectos cuando el usuario lo solicite explícitamente. Para una transferencia usa preview_project_transfer y su contrato real antes de transfer_project; comprueba el informe con get_project_transfer y usa undo_project_transfer para deshacerla cuando proceda. No confundas mover asociaciones de Biblioteca con mover archivos físicos, ni retirar o archivar un proyecto con borrar sus archivos. No afirmes una transferencia, una verificación ni una retirada del origen que las herramientas no hayan confirmado.",
                ).joinToString("\n\n")
            },
        )
    }

    fun label(app: NexusApplication, projectId: String? = null): String =
        if (projectId != null && CutChatQa.isArmed(projectId)) "Prueba local · sin modelo externo"
        else agentLabel(AssistantRuntime.get(app).settings.load())
}
