package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun GenerationPlan.cardJson(state: PlanState?): JsonObject = buildJsonObject {
    put("plan_id", id)
    put("state", (state ?: PlanState.PREPARED).name)
    put("model", "$modelName (${ref.providerId}:${ref.modelId})")
    put("modality", modality)
    task?.let { put("task", it) }
    put("prompt", prompt)
    negativePrompt?.let { put("negative_prompt", it) }
    put("params_sent", params.toJson())
    put("effective_config", effectiveParams.toJson())
    put("media", JsonArray(media.map { m -> buildJsonObject { put("role", m.role); m.fieldKey?.let { put("field", it) }; put("uri", m.uri) } }))
    estimate?.let { put("estimate", it.toJson()) }
    if (warnings.isNotEmpty()) put("warnings", strings(warnings))
}

private fun SchemaBuilder.mediaArray() = objectArray("media", "Archivos de entrada: adjuntos de la conversación o assets del índice.") {
    string("role", "Papel del archivo.", required = true, enum = PlanBuilder.ROLES)
    string("attachment_id", "Id de un adjunto de esta conversación.")
    string("asset_id", "Id de un asset del índice.")
}

internal fun resolveMedia(input: JsonObject, session: AgentSession, assets: AssetIndex): List<MediaRequest> = input.objList("media").map { m ->
    val role = m.str("role") ?: "first_frame"
    val att = m.str("attachment_id")?.let { id -> session.attachment(id) ?: throw ToolInputException("Adjunto desconocido «$id».") }
    if (att != null) return@map MediaRequest(role, att.uri, att.mimeType, att.assetId)
    val asset = m.str("asset_id")?.let { id -> assets.asset(id) ?: throw ToolInputException("Asset desconocido «$id».") }
        ?: throw ToolInputException("Cada medio necesita attachment_id o asset_id.")
    val mime = asset.mimeType ?: when (asset.type) { AssetType.IMAGE -> "image/*"; AssetType.VIDEO -> "video/*"; AssetType.AUDIO -> "audio/*"; else -> null }
    MediaRequest(role, asset.uri, mime, asset.id)
}

class PrepareGenerationTool(private val d: ToolDeps) : BaseTool(
    "prepare_generation", ToolCategory.PLAN,
    "Prepara UNA generación (modelo + prompt + parámetros + medios) validada contra el schema real y con coste estimado. NO envía nada ni gasta: muestra al usuario la tarjeta con botón GENERAR.",
    schema {
        string("model_id", "Id exacto del registro.", required = true)
        string("provider_id", "Proveedor del modelo.")
        string("prompt", "Prompt final según el schema: obligatorio solo cuando promptRequired=true; omitir para operaciones sin prompt.")
        string("negative_prompt", "Solo si el schema tiene negative_prompt.")
        freeObject("params", "Parámetros del schema (sin prompt ni medios).")
        mediaArray()
        string("task", "Tarea (image_to_video…).")
        string("rationale", "Por qué este modelo y esta configuración, en 1–3 frases para el usuario.")
        stringArray("alternatives", "Alternativas consideradas, como provider_id:model_id.")
        string("project", "Proyecto (nombre o id). Por defecto, el proyecto activo.")
        string("scene", "Código de escena, p. ej. 3.")
        string("shot", "Código de plano, p. ej. 12.")
        stringArray("characters", "Personajes que aparecen.")
        string("parent_generation_id", "Generación de la que deriva (nueva versión).")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val model = d.resolveModel(input)
        val projectName = input.str("project")
        val project = (projectName ?: session.activeProjectId)?.let { d.assets.findProject(it) }
        val alternatives = input.strList("alternatives").mapNotNull { s ->
            s.split(':', limit = 2).takeIf { it.size == 2 }?.let { ModelRef(it[0], it[1]) }
        }
        val built = d.plans.build(
            id = AssetIndex.newId("plan"),
            model = model,
            prompt = input.str("prompt") ?: "",
            negativePrompt = input.str("negative_prompt"),
            params = input.paramMap("params"),
            media = resolveMedia(input, session, d.assets),
            task = input.str("task"),
            rationale = input.str("rationale"),
            alternatives = alternatives,
            projectId = project?.id,
            sceneCode = input.str("scene"),
            shotCode = input.str("shot"),
            characters = input.strList("characters"),
            parentGenerationId = input.str("parent_generation_id"),
        )
        return when (built) {
            is PlanBuild.Invalid -> fail("El plan no es válido; corrige y vuelve a prepararlo.") { put("issues", built.issues.toJson()) }
            is PlanBuild.Ready -> {
                val plan = built.plan.let { p ->
                    if (projectName != null && project == null) p.copy(warnings = p.warnings + "El proyecto «$projectName» no existe todavía: créalo para que la generación quede asociada.") else p
                }
                d.guard.register(plan)
                ok(
                    buildJsonObject {
                        put("plan", plan.cardJson(PlanState.PREPARED))
                        put("next", "Tarjeta mostrada. Si el usuario quiere generar, llama a submit_generation con este plan_id; el envío real solo ocurre cuando el usuario pulsa GENERAR.")
                    },
                    listOf(UiEvent.PlanReady(plan, executable = true)),
                )
            }
        }
    }
}

class UpdatePlanTool(private val d: ToolDeps) : BaseTool(
    "update_plan", ToolCategory.PLAN,
    "Modifica prompt, prompt negativo o parámetros de un plan ya preparado y lo revalida. Cualquier confirmación previa queda anulada.",
    schema {
        string("plan_id", "Plan a modificar.", required = true)
        string("prompt", "Nuevo prompt.")
        string("negative_prompt", "Nuevo prompt negativo.")
        freeObject("params", "Parámetros a cambiar; null o «–» para quitar uno.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val id = input.req("plan_id")
        val plan = d.guard.plan(id) ?: throw ToolInputException("Plan desconocido «$id».")
        when (d.guard.state(id)) {
            PlanState.SUBMITTED -> throw ToolInputException("Ese plan ya se envió; prepara uno nuevo.")
            PlanState.DISCARDED -> throw ToolInputException("El usuario descartó ese plan; no lo reutilices. Prepara uno nuevo si lo pide.")
            PlanState.UNCERTAIN -> throw ToolInputException("Ese plan pudo enviarse; pide al usuario que lo compruebe en Historial.")
            else -> Unit
        }
        return when (val r = d.plans.rebuild(plan, PlanEdit(input.str("prompt"), input.str("negative_prompt"), input.paramMap("params")))) {
            is PlanBuild.Invalid -> fail("El cambio no es válido; el plan sigue como estaba.") { put("issues", r.issues.toJson()) }
            is PlanBuild.Ready -> {
                d.guard.replace(r.plan)
                ok(buildJsonObject { put("plan", r.plan.cardJson(PlanState.PREPARED)) }, listOf(UiEvent.PlanReady(r.plan, executable = true)))
            }
        }
    }
}

class SubmitGenerationTool(private val d: ToolDeps) : BaseTool(
    "submit_generation", ToolCategory.PLAN,
    "Pide al usuario confirmación para ejecutar un plan preparado. NO ejecuta: deja el plan esperando a que el usuario pulse GENERAR en la tarjeta. Cada generación necesita su propia confirmación; no lo llames para varias a la vez.",
    schema { string("plan_id", "Plan preparado con prepare_generation.", required = true) },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val id = input.req("plan_id")
        val plan = d.guard.plan(id) ?: throw ToolInputException("Plan desconocido «$id».")
        val state = d.guard.requestConfirmation(id)
        if (state == PlanState.SUBMITTED) return fail("Ese plan ya se envió. Para otra generación prepara un plan nuevo.")
        if (state == PlanState.DISCARDED) return fail("El usuario descartó ese plan.")
        if (state == PlanState.UNCERTAIN) return fail("Ese plan pudo enviarse ya; que el usuario lo compruebe en Historial.")
        return ok(
            buildJsonObject {
                put("plan_id", id)
                put("state", state.name)
                put("submitted", false)
                put("message", "No se ha enviado nada. La tarjeta muestra modelo, configuración y coste (${plan.estimate?.basis ?: "sin estimación"}). Espera a que el usuario pulse GENERAR; no repitas la llamada.")
            },
            listOf(UiEvent.AwaitingConfirmation(id)),
        )
    }
}

class GetGenerationStatusTool(private val d: ToolDeps) : BaseTool(
    "get_generation_status", ToolCategory.READ,
    "Estado de una generación enviada (por job_id de Nexus). Si terminó, importa los resultados al índice con su lineage.",
    schema { string("job_id", "Id del job de Nexus.", required = true) },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val st = runCatching { d.lifecycle.sync(input.req("job_id")) }.getOrElse { return fail("No se pudo consultar: ${it.message}") }
        return ok(st.toJson())
    }
}

class CancelGenerationTool(private val d: ToolDeps) : BaseTool(
    "cancel_generation", ToolCategory.ORGANIZE,
    "Cancela una generación en curso. En Copiloto queda como propuesta que el usuario aplica.",
    schema { string("job_id", "Id del job de Nexus.", required = true) },
) {
    override fun describe(input: JsonObject) = "Cancelar la generación ${input.str("job_id")}"

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val ok = runCatching { d.executor.cancel(input.req("job_id")) }.getOrElse { return fail("No se pudo cancelar: ${it.message}") }
        return ok { put("cancelled", ok) }
    }
}
