package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.cut.command.CapabilityKind
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Request
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** El destino vive en las herramientas, no en el proyecto que otra pantalla tenga activo. */
internal fun boundCutTools(w: CutWorkspace, projectId: String): List<AgentTool> = listOf(
    BoundCutTool(CutCatalogTool(w), projectId),
    BoundCutTool(CutDescribeTool(w), projectId),
    BoundCutTool(CutReadTool(w), projectId),
    BoundCutTool(CutEditTool(w), projectId),
    BoundCutTool(CutRunTool(w), projectId),
)

private class BoundCutTool(private val tool: AgentTool, private val projectId: String) : AgentTool by tool {
    override val description: String = tool.description + " Esta conversación trabaja únicamente en el montaje $projectId."

    override suspend fun run(input: JsonObject, session: AgentSession): ToolOutcome {
        val requested = input.str("project_id")
        if (requested != null && requested != projectId) return fail("Esta conversación pertenece a otro montaje. Abre ese montaje para editarlo.")
        val bound = JsonObject(input + ("project_id" to JsonPrimitive(projectId)))
        val out = tool.run(bound, session)
        if (tool.name != "cut_catalog" || out.isError) return out
        val catalog = out.result as? JsonObject ?: return out
        return ToolOutcome(JsonObject(catalog + mapOf(
            "active_project_id" to JsonPrimitive(projectId),
            "next" to JsonPrimitive("Pide contratos con cut_describe; lee con cut_read; edita con cut_edit; ejecuta un trabajo largo con cut_run. El destino de esta conversación es fijo."),
        )), events = out.events)
    }
}

/** Solo aparece en el editor. Los trabajos locales pasan por el mismo bus que el botón Exportar. */
private class CutRunTool(private val w: CutWorkspace) : BaseTool(
    "cut_run", ToolCategory.ORGANIZE,
    "Ejecuta UNA capacidad de tipo job del montaje, como render.export o render.cancel. render.export admite each_clip=true o clip_ids para sacar archivos separados con audio SIN borrar clips ni hacer undo. Consulta el contrato con cut_describe; el estado y los asset_ids se leen con cut_read (render.status). Es local, no genera medios con proveedores ni autoriza gastos.",
    schema {
        string("project_id", "Montaje de esta conversación; no se permite otro.")
        objectArray("ops", "Exactamente una operación de tipo job.", required = true) {
            string("id", "Id de capacidad, por ejemplo render.export.", required = true)
            freeObject("args", "Argumentos según cut_describe.")
        }
    },
) {
    override fun describe(input: JsonObject): String = "Ejecutar trabajo del montaje: " + runCatching { CutTools.ops(input).single().id }.getOrDefault("trabajo local")

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val ops = CutTools.ops(input)
        if (ops.size != 1) throw ToolInputException("cut_run admite exactamente una operación de tipo job.")
        if (w.registry[ops.single().id]?.kind != CapabilityKind.JOB) throw ToolInputException("«${ops.single().id}» no es un trabajo de tipo job. Usa cut_read o cut_edit según su contrato.")
        return CutTools.result(CutTools.session(w, input).execute(Request(ops, Origin.AGENT)))
    }
}
