package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.agent.GenerationLifecycle
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetQuery
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.Project
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val TYPES = AssetType.entries.map { it.name }
private val ROLES = AssetRole.entries.map { it.name }

fun AssetIndex.assetJson(a: Asset): JsonObject {
    val d = snapshot()
    return buildJsonObject {
        put("asset_id", a.id)
        put("name", a.displayName)
        put("type", a.type.name)
        put("role", a.role.name)
        put("source", a.source.name)
        put("version", a.version)
        put("uri", a.uri)
        a.projectId?.let { id -> put("project", d.projects.firstOrNull { it.id == id }?.name ?: id) }
        a.sceneId?.let { id -> d.scenes.firstOrNull { it.id == id }?.let { put("scene", it.code) } }
        a.shotId?.let { id -> d.shots.firstOrNull { it.id == id }?.let { put("shot", it.code) } }
        if (a.characterIds.isNotEmpty()) put("characters", strings(a.characterIds.mapNotNull { id -> d.characters.firstOrNull { it.id == id }?.name }))
        if (a.tags.isNotEmpty()) put("tags", strings(a.tags))
        a.generationId?.let { id ->
            put("generation_id", id)
            generation(id)?.let { origin ->
                put("provider_id", origin.providerId)
                put("model_id", origin.modelId)
                origin.nexusJobId?.let { put("nexus_job_id", it) }
            }
        }
        a.parentAssetId?.let { put("parent_asset_id", it) }
    }
}

private fun projectJson(p: Project) = buildJsonObject { put("project_id", p.id); put("name", p.name); p.description?.let { put("description", it) }; if (p.archived) put("archived", true) }

/** Asset del índice a partir de asset_id o de un adjunto (que se registra la primera vez). */
internal suspend fun ToolDeps.assetFromInput(input: JsonObject, session: AgentSession): Asset {
    input.str("asset_id")?.let { id -> return assets.asset(id) ?: throw ToolInputException("Asset desconocido «$id».") }
    val attId = input.str("attachment_id") ?: throw ToolInputException("Indica asset_id o attachment_id.")
    val att = session.attachment(attId) ?: throw ToolInputException("Adjunto desconocido «$attId».")
    att.assetId?.let { id -> assets.asset(id)?.let { return it } }
    val a = assets.assetByUri(att.uri) ?: assets.addAsset(
        Asset(
            id = "", uri = att.uri, type = GenerationLifecycle.typeOfMime(att.mimeType), source = AssetSource.IMPORTED,
            displayName = att.name ?: att.uri.substringAfterLast('/'), mimeType = att.mimeType,
            width = att.width, height = att.height, durationSec = att.durationSec, createdAt = clock(),
        ),
    )
    val i = session.attachments.indexOfFirst { it.id == attId }
    if (i >= 0) session.attachments[i] = att.copy(assetId = a.id)
    return a
}

class ListProjectsTool(private val d: ToolDeps) : BaseTool(
    "list_projects", ToolCategory.READ, "Proyectos del índice de Nexus.",
    schema { boolean("include_archived", "Incluir archivados.") },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession) = ok {
        put("projects", JsonArray(d.assets.projects(input.bool("include_archived") ?: false).map(::projectJson)))
        session.activeProjectId?.let { put("active_project", d.assets.findProject(it)?.name ?: it) }
    }
}

class CreateProjectTool(private val d: ToolDeps) : BaseTool(
    "create_project", ToolCategory.ORGANIZE, "Crea un proyecto (si ya existe uno con ese nombre, devuelve el existente). No mueve archivos.",
    schema {
        string("name", "Nombre del proyecto.", required = true)
        string("description", "Descripción breve.")
    },
) {
    override val additive = true
    override fun describe(input: JsonObject) = "Crear el proyecto «${input.str("name")}»"
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val p = d.assets.createProject(input.req("name"), input.str("description"))
        if (session.activeProjectId == null) session.activeProjectId = p.id
        return ok { put("project", projectJson(p)) }
    }
}

class SearchAssetsTool(private val d: ToolDeps) : BaseTool(
    "search_assets", ToolCategory.READ,
    "Busca assets por texto, proyecto, escena, plano, personaje, tipo, rol o modelo. Ignora tildes y mayúsculas.",
    schema {
        string("text", "Texto libre (nombre, prompt, etiquetas, personajes).")
        string("project", "Proyecto (nombre o id).")
        string("scene", "Código o título de escena.")
        string("shot", "Código de plano.")
        string("character", "Personaje.")
        string("type", "Tipo.", enum = TYPES)
        string("role", "Rol.", enum = ROLES)
        string("model_id", "Modelo que lo generó (subcadena).")
        integer("limit", "Máx. 50.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val q = AssetQuery(
            text = input.str("text"), projectId = input.str("project"), sceneCode = input.str("scene"), shotCode = input.str("shot"),
            character = input.str("character"),
            type = input.str("type")?.uppercase()?.let { t -> AssetType.entries.firstOrNull { it.name == t } ?: throw ToolInputException("Tipo «$t» no válido.") },
            role = input.str("role")?.uppercase()?.let { r -> AssetRole.entries.firstOrNull { it.name == r } ?: throw ToolInputException("Rol «$r» no válido.") },
            modelId = input.str("model_id"), limit = (input.int("limit") ?: 20).coerceIn(1, 50),
        )
        if (q.projectId != null && d.assets.findProject(q.projectId) == null) return fail("No existe el proyecto «${q.projectId}».")
        val found = d.assets.search(q)
        return ok {
            put("count", found.size)
            put("assets", JsonArray(found.map { d.assets.assetJson(it) }))
            if (session.attachments.isNotEmpty()) put("attachments_in_conversation", JsonArray(session.attachments.map { a ->
                buildJsonObject { put("attachment_id", a.id); put("mime", a.mimeType); a.name?.let { put("name", it) }; a.assetId?.let { put("asset_id", it) } }
            }))
        }
    }
}

class InspectAssetTool(private val d: ToolDeps) : BaseTool(
    "inspect_asset_metadata", ToolCategory.READ,
    "Metadatos completos de un asset o adjunto: proyecto, escena, plano, personajes, versión y lineage (modelo, prompt, parámetros, seed, referencias, coste, job).",
    schema {
        string("asset_id", "Id del asset.")
        string("attachment_id", "Id del adjunto de la conversación.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        input.str("attachment_id")?.let { id ->
            val att = session.attachment(id) ?: throw ToolInputException("Adjunto desconocido «$id».")
            val indexed = att.assetId?.let { d.assets.asset(it) } ?: d.assets.assetByUri(att.uri)
            if (indexed == null) return ok {
                put("attachment", att.toJson())
                put("indexed", false)
                put("note", "Aún no está en el índice; se registrará al asociarlo o al usarlo en una generación.")
            }
            return describeAsset(indexed)
        }
        val a = d.assets.asset(input.req("asset_id")) ?: throw ToolInputException("Asset desconocido.")
        return describeAsset(a)
    }

    private fun describeAsset(a: Asset): ToolOutcome {
        val gen = a.generationId?.let { d.assets.generation(it) }
        return ok {
            put("asset", d.assets.assetJson(a))
            a.width?.let { put("width", it) }; a.height?.let { put("height", it) }; a.durationSec?.let { put("duration_sec", it) }
            if (gen != null) put("generation", gen.toJson())
            a.shotId?.let { put("shot_status", d.assets.shotStatus(it)) }
        }
    }
}

class ListShotsTool(private val d: ToolDeps) : BaseTool(
    "list_shots", ToolCategory.READ,
    "Planos de un proyecto con su estado (PLANNED/KEYFRAME/VIDEO/FINAL). Con has/missing responde «planos con keyframe y sin vídeo».",
    schema {
        string("project", "Proyecto (nombre o id).")
        string("has", "Tipo de asset que deben tener.", enum = TYPES)
        string("missing", "Tipo de asset que les falta.", enum = TYPES)
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val project = (input.str("project") ?: session.activeProjectId)?.let { d.assets.findProject(it) ?: return fail("No existe el proyecto «$it».") }
        val has = input.str("has")?.uppercase()?.let { AssetType.valueOf(it) }
        val missing = input.str("missing")?.uppercase()?.let { AssetType.valueOf(it) }
        val shots = if (has != null && missing != null) d.assets.shotsWith(project?.id, has, missing)
        else d.assets.snapshot().shots.filter { project == null || it.projectId == project.id }
        return ok {
            project?.let { put("project", it.name) }
            put("shots", JsonArray(shots.map { s ->
                buildJsonObject {
                    put("shot", s.code); s.description?.let { put("description", it) }
                    s.sceneId?.let { id -> d.assets.snapshot().scenes.firstOrNull { it.id == id }?.let { put("scene", it.code) } }
                    put("status", d.assets.shotStatus(s.id))
                    put("versions", d.assets.snapshot().assets.count { it.shotId == s.id })
                }
            }))
        }
    }
}

class GenerationHistoryTool(private val d: ToolDeps) : BaseTool(
    "get_generation_history", ToolCategory.READ,
    "Historial de generaciones: las del índice (con proyecto/plano y lineage) y las recientes de Nexus.",
    schema {
        string("project", "Filtra por proyecto.")
        integer("limit", "Máx. 50.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val limit = (input.int("limit") ?: 15).coerceIn(1, 50)
        val project = input.str("project")
        val indexed = d.assets.history(project, limit)
        val nexus = if (project == null) runCatching { d.history?.recent(limit) }.getOrNull().orEmpty() else emptyList()
        return ok {
            put("indexed", indexed.toJson())
            if (nexus.isNotEmpty()) put("nexus_recent", nexus.toJson())
        }
    }
}

class AssociateAssetTool(private val d: ToolDeps) : BaseTool(
    "associate_asset_to_project", ToolCategory.ORGANIZE,
    "Asocia un asset o adjunto a proyecto/escena/plano/personajes (metadatos lógicos; el archivo no se mueve). En Copiloto es una propuesta.",
    schema {
        string("asset_id", "Id del asset.")
        string("attachment_id", "O id del adjunto.")
        string("project", "Proyecto (nombre o id).", required = true)
        string("scene", "Código de escena.")
        string("shot", "Código de plano.")
        stringArray("characters", "Personajes que aparecen.")
        string("role", "Rol del asset.", enum = ROLES)
    },
) {
    override fun describe(input: JsonObject) = buildString {
        append("Asociar ").append(input.str("asset_id") ?: input.str("attachment_id") ?: "el archivo")
        append(" al proyecto «").append(input.str("project")).append("»")
        input.str("scene")?.let { append(", escena ").append(it) }
        input.str("shot")?.let { append(", plano ").append(it) }
        input.strList("characters").takeIf { it.isNotEmpty() }?.let { append(" (").append(it.joinToString()).append(")") }
    }

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val p = d.assets.findProject(input.req("project")) ?: return fail("No existe el proyecto «${input.str("project")}». Créalo antes.")
        val a = d.assetFromInput(input, session)
        val scene = input.str("scene")?.let { d.assets.ensureScene(p.id, it) }
        val shot = input.str("shot")?.let { d.assets.ensureShot(p.id, it, scene?.id) }
        val chars = input.strList("characters").map { d.assets.ensureCharacter(it, p.id).id }
        val role = input.str("role")?.uppercase()?.let { r -> AssetRole.entries.firstOrNull { it.name == r } }
        val updated = d.assets.updateAsset(a.id) {
            it.copy(projectId = p.id, sceneId = scene?.id ?: it.sceneId, shotId = shot?.id ?: it.shotId,
                characterIds = (it.characterIds + chars).distinct(), role = role ?: it.role)
        }
        return ok { put("asset", d.assets.assetJson(updated)) }
    }
}

class MoveAssetTool(private val d: ToolDeps) : BaseTool(
    "move_asset", ToolCategory.ORGANIZE,
    "Mueve un asset a otro proyecto/escena/plano DENTRO del índice. Nunca mueve el archivo físico. En Copiloto es una propuesta.",
    schema {
        string("asset_id", "Id del asset.", required = true)
        string("project", "Proyecto destino.")
        string("scene", "Escena destino.")
        string("shot", "Plano destino.")
    },
) {
    override fun describe(input: JsonObject) = "Mover ${input.str("asset_id")} a " + listOfNotNull(
        input.str("project")?.let { "proyecto «$it»" }, input.str("scene")?.let { "escena $it" }, input.str("shot")?.let { "plano $it" },
    ).joinToString(", ") + " (solo en el índice; el archivo no se toca)"

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val a = d.assets.asset(input.req("asset_id")) ?: throw ToolInputException("Asset desconocido.")
        val projectId = input.str("project")?.let { d.assets.findProject(it)?.id ?: return fail("No existe el proyecto «$it».") } ?: a.projectId
            ?: return fail("El asset no tiene proyecto: indica project.")
        val scene = input.str("scene")?.let { d.assets.ensureScene(projectId, it) }
        val shot = input.str("shot")?.let { d.assets.ensureShot(projectId, it, scene?.id) }
        val projectChanged = projectId != a.projectId
        val updated = d.assets.updateAsset(a.id) {
            it.copy(projectId = projectId,
                sceneId = scene?.id ?: if (projectChanged) null else it.sceneId,
                shotId = shot?.id ?: if (projectChanged) null else it.shotId)
        }
        return ok { put("asset", d.assets.assetJson(updated)); put("file_moved", false) }
    }
}

class RenameAssetTool(private val d: ToolDeps) : BaseTool(
    "rename_asset", ToolCategory.ORGANIZE,
    "Cambia el nombre lógico de un asset en el índice. El archivo físico conserva su nombre. En Copiloto es una propuesta.",
    schema {
        string("asset_id", "Id del asset.", required = true)
        string("display_name", "Nombre nuevo.", required = true)
    },
) {
    override fun describe(input: JsonObject) = "Renombrar ${input.str("asset_id")} a «${input.str("display_name")}» (solo nombre lógico)"
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val name = input.req("display_name").take(120)
        val updated = d.assets.updateAsset(input.req("asset_id")) { it.copy(displayName = name) }
        return ok { put("asset", d.assets.assetJson(updated)); put("file_renamed", false) }
    }
}
