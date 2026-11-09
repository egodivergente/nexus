package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.config.AgentMode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** The editor and every brain call the same transactional project operations. No provider submission here. */
object StoryTools {
    fun all(d: ToolDeps): List<AgentTool> = listOf(
        ReadStoryProjectTool(d), ReadStoryFragmentTool(d), EditStoryProjectTool(d),
        ProposeStoryChangesTool(d), ApplyStoryProposalTool(d), SearchStoryMaterialTool(d),
        StoryTasksTool(d), PrepareStoryGenerationTool(d),
    )
}

private fun ToolDeps.storyProject(input: JsonObject, session: AgentSession): String {
    val active = session.activeProjectId?.let(assets::findProject)
        ?: throw ToolInputException("Abre un proyecto con open_project antes de trabajar en su guion.")
    input.str("project_id")?.let { requested ->
        if (requested != active.id) throw ToolInputException("El proyecto solicitado no es el activo. Abre primero ese proyecto; el alcance actual se conserva.")
    }
    return active.id
}

private fun AgentSession.storyScope(projectId: String): StoryAgentSelection? = storySelection?.takeIf { it.projectId == projectId }
private fun AgentSession.storyDirect(projectId: String) = mode == AgentMode.AGENT && storyScope(projectId)?.mode != "proposal"

private fun selectionJson(scope: StoryAgentSelection?) = buildJsonObject {
    scope?.let {
        put("project_id", it.projectId)
        it.sceneId?.let { id -> put("scene_id", id) }; it.shotId?.let { id -> put("shot_id", id) }; it.blockId?.let { id -> put("block_id", id) }
        put("mode", it.mode); put("preserve_ids", JsonArray(it.preserveIds.sorted().map(::JsonPrimitive)))
    }
}

private fun scopedCommand(command: JsonObject, session: AgentSession, projectId: String): JsonObject {
    val scope = session.storyScope(projectId)
    return buildJsonObject {
        command.forEach { (key, value) ->
            if (key !in setOf("actor", "project_id", "scope_scene_id", "scope_shot_id", "scope_block_id", "preserve_ids")) put(key, value)
        }
        put("project_id", projectId)
        if (command.str("action") == "create_shot" && scope?.blockId != null && command.str("origin_block_id") == null && command.str("block_id") == null)
            put("origin_block_id", scope.blockId)
        if (command.str("action") in setOf("enqueue_task", "prepare_generation")) {
            if (command.str("scene_id") == null) scope?.sceneId?.let { put("scene_id", it) }
            if (command.str("shot_id") == null) scope?.shotId?.let { put("shot_id", it) }
        }
        val scene = scope?.sceneId ?: command.str("scope_scene_id")
        val shot = scope?.shotId ?: command.str("scope_shot_id")
        val block = scope?.blockId ?: command.str("scope_block_id")
        scene?.let { put("scope_scene_id", it) }; shot?.let { put("scope_shot_id", it) }; block?.let { put("scope_block_id", it) }
        val preserved = scope?.preserveIds.orEmpty() + command.strList("preserve_ids")
        if (preserved.isNotEmpty()) put("preserve_ids", JsonArray(preserved.sorted().map(::JsonPrimitive)))
    }
}

private suspend fun ToolDeps.performStory(projectId: String, command: JsonObject, session: AgentSession): ToolOutcome = try {
    val result = assets.storyCommand(projectId, scopedCommand(command, session, projectId), "agent:${session.id}")
    ToolOutcome(result, isError = result["ok"]?.jsonPrimitive?.booleanOrNull == false || result["isError"]?.jsonPrimitive?.booleanOrNull == true)
} catch (e: CancellationException) { throw e }
catch (e: IllegalArgumentException) { fail(e.message ?: "Cambio de proyecto no válido.") }
catch (e: IllegalStateException) { fail(e.message ?: "El contenido cambió; vuelve a leer el fragmento.") }

private val STORY_MUTATIONS = setOf(
    "update_project", "create_scene", "update_scene", "delete_scene", "move_scene",
    "create_block", "update_block", "delete_block", "move_block",
    "create_shot", "update_shot", "delete_shot", "move_shot", "duplicate_shot",
    "link_material", "unlink_material", "upsert_element", "set_preference", "undo",
)

private fun validatedChange(input: JsonObject): JsonObject {
    val change = input["change"] as? JsonObject ?: throw ToolInputException("Indica change como objeto estructurado.")
    if (change.req("action") !in STORY_MUTATIONS) throw ToolInputException("Acción editorial no admitida. Las generaciones y sus resultados usan su propio flujo verificado.")
    change.req("operation_id") // Stable across retries; never silently generate a new mutation id.
    return change
}

private fun changes(input: JsonObject): List<JsonObject> {
    val list = (input["changes"] as? JsonArray)?.map { it as? JsonObject ?: throw ToolInputException("Cada cambio debe ser un objeto.") }
        ?: throw ToolInputException("Indica changes como lista de operaciones.")
    if (list.isEmpty()) throw ToolInputException("La propuesta no contiene cambios.")
    list.forEach { if (it.req("action") !in STORY_MUTATIONS - "undo") throw ToolInputException("Una propuesta solo admite cambios editoriales; no ejecuta tareas ni genera material.") }
    return list
}

private fun changeSchema(multiple: Boolean = false): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("project_id", buildJsonObject { put("type", "string"); put("description", "Id del proyecto activo; vacío = activo.") })
        if (multiple) {
            put("operation_id", buildJsonObject { put("type", "string"); put("description", "Id estable de esta propuesta; reutilízalo al reintentar.") })
            put("title", buildJsonObject { put("type", "string"); put("description", "Nombre breve del conjunto de cambios.") })
            put("changes", buildJsonObject { put("type", "array"); put("description", "Operaciones completas action + ids persistentes + expected_revision para contenido existente. Conserva el texto no modificado. Usa los campos y acciones documentados por read_story_project."); put("items", buildJsonObject { put("type", "object"); put("additionalProperties", true) }) })
        } else put("change", buildJsonObject { put("type", "object"); put("additionalProperties", true); put("description", "Operación action + operation_id estable y campos específicos. Para contenido existente incluye su expected_revision leído. Acciones y campos en read_story_project.") })
    })
    put("required", JsonArray((if (multiple) listOf("operation_id", "changes") else listOf("change")).map(::JsonPrimitive)))
}

private class ReadStoryProjectTool(private val d: ToolDeps) : BaseTool(
    "read_story_project", ToolCategory.READ,
    "Lee el proyecto audiovisual vivo, su estructura, propuestas, tareas, huecos y revisiones actuales. Guion y storyboard son el mismo documento. Lee antes de editar; los ids son persistentes. No autoriza gastos.",
    schema { string("project_id", "Id del proyecto activo.") },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session)
        return ok(buildJsonObject {
            put("project", d.assets.storyView(project))
            put("selected_scope", selectionJson(session.storyScope(project)))
            put("direct_changes", session.storyDirect(project))
            put("operation_contract", "edit_story_project(change={action,operation_id,...}); propose_story_changes(operation_id,changes=[...]). Existing entities require expected_revision from the current view. Actions and precise fields/examples are in project.operation_contract. Use persistent scene_id/shot_id/block_id/asset_id. Blocks kind=action|dialogue|note|reference|shot|material; text,speaker,narrative_state=idea|story|incorporated,order. Shots action_text,intent,character_ids,framing,camera,performance,dialogue,sound,duration_sec,narrative_state,production_state=pending|prepared|generated|chosen. create_shot can develop the selected note using origin_block_id while preserving its text. Links require scope=project|scene|shot|block|character|element,target_id,asset_id,role; selected and behavior=complement|replace belong to the association. upsert_element requires kind,name plus optional element_id,description,scene_id,character_id,fields. set_preference requires scope,target_id,text. The selected scope and preserve_ids cannot be widened. A proposal is not permission to spend.")
        })
    }
}

private class ReadStoryFragmentTool(private val d: ToolDeps) : BaseTool(
    "read_story_fragment", ToolCategory.READ,
    "Lee una escena o plano y sus referencias efectivas, continuidad, material y revisiones. Usa los ids de read_story_project; vacío toma la selección explícita. Vuelve a leer tras un conflicto o una propuesta aceptada.",
    schema { string("project_id", "Proyecto activo."); string("scene_id", "Id de escena."); string("shot_id", "Id de plano.") },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session); val scope = session.storyScope(project)
        val scene = input.str("scene_id") ?: scope?.sceneId
        val shot = input.str("shot_id") ?: scope?.shotId
        if (scene == null && shot == null) return fail("Selecciona o indica una escena o un plano.")
        val target = shot?.let { id -> d.assets.snapshot().shots.firstOrNull { it.id == id && it.projectId == project } ?: return fail("El plano no pertenece al proyecto activo.") }
        if (scene != null && target != null && target.sceneId != scene) return fail("El plano no pertenece a la escena indicada.")
        val effectiveScene = scene ?: target?.sceneId
        val view = d.assets.storyView(project, effectiveScene, shot)
        return ok(JsonObject(view.mapValues { (key, value) ->
            if (key !in setOf("scenes", "shots", "blocks", "tasks", "gaps")) value else JsonArray((value as? JsonArray).orEmpty().filter { element ->
                val obj = element as? JsonObject ?: return@filter false
                when (key) {
                    "scenes" -> obj.str("id") == effectiveScene
                    "shots" -> if (shot != null) obj.str("id") == shot else obj.str("scene_id") == effectiveScene
                    "blocks" -> if (shot != null) obj.str("shot_id") == shot || obj.str("id") == scope?.blockId else obj.str("scene_id") == effectiveScene
                    "tasks", "gaps" -> if (shot != null) obj.str("shot_id") == shot else obj.str("scene_id") == effectiveScene
                    else -> true
                }
            })
        }))
    }
}

private class EditStoryProjectTool(private val d: ToolDeps) : BaseTool(
    "edit_story_project", ToolCategory.ORGANIZE,
    "Edita el guion vivo mediante UNA operación validada, recuperable e idempotente. En Copiloto o alcance de propuestas guarda una propuesta persistente; en Agente con cambios directos aplica dentro del alcance. Respeta preserve_ids y expected_revision; nunca sobrescribas una edición reciente ni inventes material generado.",
    changeSchema(),
) {
    // Handles proposal persistence itself, rather than the generic in-memory PendingAction closure.
    override val additive = true
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session); val command = validatedChange(input)
        if (session.storyDirect(project)) return d.performStory(project, command, session)
        if (command.str("action") == "undo") return fail("Deshacer exige cambios directos autorizados o el control Deshacer del editor.")
        return d.performStory(project, buildJsonObject {
            put("action", "propose"); put("operation_id", command.req("operation_id"))
            put("title", input.str("title") ?: "Propuesta del agente")
            put("changes", JsonArray(listOf(scopedCommand(command, session, project))))
        }, session)
    }
}

private class ProposeStoryChangesTool(private val d: ToolDeps) : BaseTool(
    "propose_story_changes", ToolCategory.PLAN,
    "Guarda una alternativa editorial persistente sin sustituir el guion actual. changes es un lote coherente; se revisa en contexto y se acepta completo o por grupos. Incluye expected_revision para cada entidad existente. Nunca contiene envíos de generación o autorizaciones de gasto.",
    changeSchema(multiple = true),
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session); val changes = changes(input)
        return d.performStory(project, buildJsonObject {
            put("action", "propose"); put("operation_id", input.req("operation_id")); put("title", input.str("title") ?: "Propuesta del agente")
            put("changes", JsonArray(changes.map { scopedCommand(it, session, project) }))
        }, session)
    }
}

private class ApplyStoryProposalTool(private val d: ToolDeps) : BaseTool(
    "apply_story_proposal", ToolCategory.ORGANIZE,
    "Aplica o descarta una propuesta persistente ya revisada. Aplicar requiere modo Agente y alcance de cambios directos; verifica revisiones y conserva conflictos locales. Una aceptación narrativa nunca autoriza generación ni llamadas externas.",
    schema { string("project_id", "Proyecto activo."); string("proposal_id", "Id de propuesta.", required = true); string("operation_id", "Id estable de esta acción.", required = true); integer("expected_revision", "Revisión actual de la propuesta.", required = true); stringArray("change_ids", "Partes coherentes de la propuesta; vacío = todas."); boolean("discard", "Descartar en vez de aplicar.") },
) {
    override val additive = true
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session)
        if (!session.storyDirect(project)) return fail("La selección permite propuestas. El usuario puede aceptarla en el proyecto o autorizar cambios directos en este alcance.")
        return d.performStory(project, buildJsonObject {
            input.forEach { (k, v) -> if (k != "discard") put(k, v) }
            put("action", if (input.bool("discard") == true) "reject_proposal" else "apply_proposal")
        }, session)
    }
}

private class SearchStoryMaterialTool(private val d: ToolDeps) : BaseTool(
    "search_story_material", ToolCategory.READ,
    "Busca material real reutilizable por texto y metadatos en el proyecto o Biblioteca global. Devuelve archivos únicos y sus asociaciones en este proyecto. Nunca clasifica visualmente un archivo sin examinarlo ni modifica vínculos.",
    schema { string("project_id", "Proyecto activo."); string("query", "Nombre, prompt, modelo o etiquetas."); string("scene_id", "Filtrar asociaciones de esta escena."); string("shot_id", "Filtrar asociaciones de este plano."); string("character_id", "Filtrar usos de este personaje."); string("role", "Papel contextual: identity,costume,location,composition,lighting,sketch,generated,candidate,audio,chosen,discarded,reference."); string("type", "Tipo.", enum = AssetType.entries.map { it.name }); boolean("unplaced", "Solo material Por colocar."); boolean("global", "Buscar también fuera del proyecto."); integer("limit", "Número de archivos que devolver.") },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session)
        val view = d.assets.storyView(project, input.str("scene_id"), input.str("shot_id"))
        val snapshot = d.assets.snapshot()
        val filtered = input.str("scene_id") != null || input.str("shot_id") != null || input.str("character_id") != null || input.str("role") != null
        val links = JsonArray((view["links"] as? JsonArray).orEmpty().filter { value ->
            val link = value as? JsonObject ?: return@filter false
            val target = link.str("target_id")
            val linkShot = when (link.str("scope")) {
                "shot" -> target
                "block" -> snapshot.story.blocks.firstOrNull { it.id == target }?.shotId
                else -> null
            }
            val linkScene = when (link.str("scope")) {
                "scene" -> target
                "shot" -> snapshot.shots.firstOrNull { it.id == target }?.sceneId
                "block" -> snapshot.story.blocks.firstOrNull { it.id == target }?.sceneId
                else -> null
            }
            (input.str("scene_id") == null || linkScene == input.str("scene_id")) &&
                (input.str("shot_id") == null || linkShot == input.str("shot_id")) &&
                (input.str("character_id") == null || link.str("character_id") == input.str("character_id") || link.str("scope") == "character" && target == input.str("character_id")) &&
                (input.str("role") == null || link.str("role") == input.str("role"))
        })
        val ids = links.mapNotNull { (it as? JsonObject)?.str("asset_id") ?: (it as? JsonObject)?.str("assetId") }.toSet()
        val type = input.str("type")?.let { name -> AssetType.entries.firstOrNull { it.name == name } ?: throw ToolInputException("Tipo de archivo no válido.") }
        val limit = d.limits().queryLimit(input.int("limit"), 40)
        val found = d.assets.search(AssetQuery(text = input.str("query"), type = type, limit = Int.MAX_VALUE))
            .filter { input.bool("global") == true || it.projectId == project || it.id in ids }
            .filter { !filtered || it.id in ids }
            .filter { input.bool("unplaced") != true || it.id in (view["unplaced_asset_ids"] as? JsonArray).orEmpty().mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull } }
        val visibleIds = found.take(limit).map { it.id }.toSet()
        return ok {
            put("total", found.size); put("assets", JsonArray(found.take(limit).map(d.assets::assetJson)))
            put("links", JsonArray(links.filter { link ->
                val assetId = (link as? JsonObject)?.let { it.str("asset_id") ?: it.str("assetId") }
                assetId != null && assetId in visibleIds
            }))
            put("classification", "Metadatos y asociaciones registradas; no es una clasificación visual inferida.")
        }
    }
}

private class StoryTasksTool(private val d: ToolDeps) : BaseTool(
    "story_tasks", ToolCategory.ORGANIZE,
    "Consulta o actualiza la cola persistente del proyecto. Puede preparar organización y propuestas; no invoca agentes ni proveedores, no aprueba gastos y no declara ejecución continua. Registra estados reales; una tarea guardada sigue pendiente hasta que un ejecutor la toma.",
    schema { string("project_id", "Proyecto activo."); string("action", "Acción.", enum = listOf("read", "enqueue_task", "update_task", "set_autonomy")); freeObject("task", "Datos de la operación: operation_id, task_id/expected_revision para actualizar, kind,title,scene_id,shot_id,status según el contrato del proyecto. set_autonomy almacena alcance y modo; no autoriza costes.") },
) {
    override val additive = true
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session); val action = input.str("action") ?: "read"
        if (action == "read") return ok(d.assets.storyView(project))
        if (action !in setOf("enqueue_task", "update_task", "set_autonomy")) return fail("Acción de tareas desconocida.")
        if (action == "set_autonomy") return fail("El alcance de autonomía lo fija el usuario desde el proyecto. Una herramienta no puede ampliar sus propios permisos.")
        val task = input["task"] as? JsonObject ?: return fail("Indica los datos de la tarea.")
        task.req("operation_id")
        if (task.str("status")?.lowercase() in setOf("running", "executing", "generating")) return fail("Esta herramienta registra preparación y seguimiento; no inicia un ejecutor ni una generación. No marques una tarea como ejecutándose sin ejecución verificable.")
        return d.performStory(project, JsonObject(task + ("action" to JsonPrimitive(action))), session)
    }
}

private class PrepareStoryGenerationTool(private val d: ToolDeps) : BaseTool(
    "prepare_story_generation", ToolCategory.PLAN,
    "Captura el contexto y las referencias efectivas de un plano como preparación persistente, sin generar ni gastar. Después prepara el modelo real con prepare_generation y su confirmación de coste habitual. La revisión capturada acompaña al resultado aunque el guion cambie.",
    schema { string("project_id", "Proyecto activo."); string("shot_id", "Id del plano; vacío = seleccionado."); string("operation_id", "Id estable de la preparación.", required = true); integer("expected_revision", "Revisión actual del plano.", required = true) },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = d.storyProject(input, session)
        val shot = input.str("shot_id") ?: session.storyScope(project)?.shotId ?: return fail("Selecciona o indica el plano que preparar.")
        return d.performStory(project, buildJsonObject { input.forEach { (k, v) -> put(k, v) }; put("action", "prepare_generation"); put("shot_id", shot) }, session)
    }
}
