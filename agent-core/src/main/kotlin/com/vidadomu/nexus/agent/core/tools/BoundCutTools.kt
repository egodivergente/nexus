package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.cut.command.CapabilityKind
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.HistoryStep
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    BoundCutTool(CutHistoryTool(w), projectId),
    BoundCutTool(CutRevertTool(w), projectId),
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
        // Los demás montajes no pintan nada en esta conversación: solo se deja el suyo.
        val own = (catalog["montages"] as? kotlinx.serialization.json.JsonArray)?.filter { (it as? JsonObject)?.get("project_id")?.let { id -> (id as? JsonPrimitive)?.content } == projectId }
        return ToolOutcome(JsonObject(catalog + mapOf(
            "active_project_id" to JsonPrimitive(projectId),
            "montages" to kotlinx.serialization.json.JsonArray(own.orEmpty()),
            "next" to JsonPrimitive("Pide contratos con cut_describe; lee con cut_read; edita con cut_edit; ejecuta un trabajo largo con cut_run; repasa o revierte lo hecho con cut_history y cut_revert. El destino de esta conversación es fijo."),
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

/** Lo que se ha hecho en el montaje y quién: para responder «¿qué has cambiado?» sin adivinar. */
private class CutHistoryTool(private val w: CutWorkspace) : BaseTool(
    "cut_history", ToolCategory.READ,
    "Historial compartido de «Deshacer» del montaje: los últimos pasos (del más reciente al más antiguo) con quién los hizo (user/agent) y el turno del agente al que pertenecen. this_turn marca los de este mensaje. Úsalo antes de explicar o revertir cambios.",
    schema {
        string("project_id", "Montaje de esta conversación; no se permite otro.")
        integer("limit", "Pasos como máximo; por defecto 15.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val s = CutTools.session(w, input)
        val turn = CutTools.turnGroup()
        val h = s.history(input.int("limit") ?: 15)
        fun steps(list: List<HistoryStep>) = buildJsonArray {
            for (step in list) add(buildJsonObject {
                put("label", step.label); put("by", step.origin.name.lowercase()); put("changes", step.changes)
                step.group?.let { put("turn_id", it) }
                if (turn != null && step.group == turn) put("this_turn", true)
            })
        }
        return ok(buildJsonObject {
            put("revision", s.state.value.revision)
            put("undo", steps(h.undo)); put("redo", steps(h.redo))
        })
    }
}

/**
 * Revierte de una vez lo que el agente hizo en un turno. Si el usuario editó después, no lo arrastra: devuelve
 * el error y el usuario decide (en la tarjeta del chat puede confirmarlo).
 */
private class CutRevertTool(private val w: CutWorkspace) : BaseTool(
    "cut_revert", ToolCategory.ORGANIZE,
    "Deshace de una vez TODOS los pasos que el agente hizo en un turno (por defecto, el más reciente que siga aplicado). Queda en «Rehacer». Si el usuario hizo cambios después, se niega y lo explica: no los deshace. Para un solo paso usa cut_edit con project.undo.",
    schema {
        string("project_id", "Montaje de esta conversación; no se permite otro.")
        string("turn_id", "Turno que revertir (cut_history); vacío = el último del agente.")
    },
) {
    override fun describe(input: JsonObject): String = "Revertir los cambios del agente en el montaje"

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val s = CutTools.session(w, input)
        val group = input.str("turn_id") ?: s.history(200).undo.firstOrNull { it.origin == Origin.AGENT && it.group != null }?.group
            ?: throw ToolInputException("No queda ningún cambio del agente por deshacer.")
        return CutTools.result(s.undoGroup(group, Origin.AGENT))
    }
}
