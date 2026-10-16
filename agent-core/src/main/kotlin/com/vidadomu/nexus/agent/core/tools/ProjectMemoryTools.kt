package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import com.vidadomu.nexus.agent.core.assets.ProjectNote
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

internal fun JsonObjectBuilder.putMemory(p: Project) {
    val m = p.memory
    put("project_id", p.id); put("name", p.name)
    p.description?.let { put("description", it) }
    m.synopsis?.let { put("synopsis", it) }
    m.tone?.let { put("tone", it) }
    m.visualStyle?.let { put("visual_style", it) }
    if (m.rules.isNotEmpty()) put("rules", strings(m.rules))
    if (m.preferredModels.isNotEmpty()) put("preferred_models", strings(m.preferredModels))
    if (m.notes.isNotEmpty()) put("notes", strings(m.notes.takeLast(20).map { it.text }))
    if (m.notes.size > 20) put("older_notes", m.notes.size - 20)
}

/** Activa un proyecto en la conversación y devuelve su memoria (biblia) y su estado. */
class OpenProjectTool(private val d: ToolDeps) : BaseTool(
    "open_project", ToolCategory.READ,
    "Entra en un proyecto (lo activa en esta conversación) y devuelve su memoria: sinopsis, tono, estilo visual, reglas, personajes y notas. Úsalo cuando el usuario diga «vamos con / métete en el proyecto X». Lo generado mientras está activo queda etiquetado con él.",
    schema { string("project", "Nombre o id del proyecto.", required = true) },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val name = input.req("project")
        val p = d.assets.findProject(name)
            ?: return fail("No existe el proyecto «$name». Proyectos: ${d.assets.projects().joinToString { it.name }.ifEmpty { "ninguno" }}. Puedes crearlo con create_project.")
        session.activeProjectId = p.id
        return ok {
            put("active", true)
            putMemory(p)
            d.assets.projectContext(p.id)?.let { put("context", it) }
        }
    }
}

/**
 * Guarda en la memoria del proyecto lo que el usuario fija como duradero. Solo metadatos del índice
 * de Nexus: no toca archivos ni gasta. En Copiloto se aplica directamente (es aditivo y visible en ⚙ Memoria).
 */
class UpdateProjectMemoryTool(private val d: ToolDeps) : BaseTool(
    "update_project_memory", ToolCategory.ORGANIZE,
    "Guarda en la memoria del proyecto (por defecto el activo) lo que el usuario fija como duradero: sinopsis, tono, estilo visual, reglas fijas, modelos preferidos, personajes (nombre + descripción visual estable) y notas de decisiones. Los campos de texto que pases sustituyen al anterior: si amplías, reescribe el texto completo. No guardes cosas efímeras de un solo plano.",
    schema {
        string("project", "Proyecto (nombre o id). Por defecto el activo.")
        string("description", "Descripción corta del proyecto (una línea).")
        string("synopsis", "Sinopsis completa (sustituye a la anterior).")
        string("tone", "Género, tono, público, duración prevista.")
        string("visual_style", "Estética: paleta, luz, óptica, textura, referencias, ratio preferido.")
        stringArray("add_rules", "Reglas fijas nuevas.")
        stringArray("remove_rules", "Reglas a quitar (texto o parte del texto).")
        stringArray("preferred_models", "Modelos/familias preferidos (sustituye la lista).")
        string("add_note", "Nota o decisión a recordar (se fecha sola).")
        objectArray("characters", "Personajes a crear o actualizar en este proyecto.") {
            string("name", "Nombre.", required = true)
            string("description", "Descripción visual y de carácter estable (edad, rasgos, ropa habitual…).")
            stringArray("aliases", "Otros nombres.")
        }
    },
) {
    override val additive = true

    override fun describe(input: JsonObject) = "Actualizar la memoria del proyecto" + (input.str("project")?.let { " «$it»" } ?: "")

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val ref = input.str("project") ?: session.activeProjectId
            ?: return fail("No hay proyecto activo. Usa open_project o create_project, o indica «project».")
        val p = d.assets.findProject(ref) ?: return fail("No existe el proyecto «$ref».")
        val changed = mutableListOf<String>()
        input.str("description")?.let { d.assets.updateProject(p.id, it); changed += "descripción" }
        val remove = input.strList("remove_rules").map(AssetIndex::norm)
        val now = d.clock()
        val updated = d.assets.updateProjectMemory(p.id) { m ->
            var n: ProjectMemory = m
            input.str("synopsis")?.let { n = n.copy(synopsis = it); changed += "sinopsis" }
            input.str("tone")?.let { n = n.copy(tone = it); changed += "tono" }
            input.str("visual_style")?.let { n = n.copy(visualStyle = it); changed += "estilo visual" }
            if (remove.isNotEmpty()) {
                val before = n.rules.size
                n = n.copy(rules = n.rules.filterNot { r -> remove.any { it.isNotEmpty() && it in AssetIndex.norm(r) } })
                if (n.rules.size != before) changed += "reglas quitadas: ${before - n.rules.size}"
            }
            input.strList("add_rules").takeIf { it.isNotEmpty() }?.let { n = n.copy(rules = (n.rules + it).distinctBy(AssetIndex::norm)); changed += "reglas" }
            if (input.containsKey("preferred_models")) { n = n.copy(preferredModels = input.strList("preferred_models")); changed += "modelos preferidos" }
            input.str("add_note")?.take(600)?.let { note ->
                if(n.notes.none {AssetIndex.norm(it.text)==AssetIndex.norm(note)}) n = n.copy(notes = n.notes + ProjectNote(now,note))
                changed += "nota"
            }
            n
        }
        val chars = input.objList("characters").mapNotNull { c ->
            val name = c.str("name") ?: return@mapNotNull null
            val existing = d.assets.findCharacter(name, p.id)?.takeIf { it.projectId == p.id || it.projectId == null }
            val ch = existing ?: d.assets.ensureCharacter(name, p.id, c.str("description"))
            d.assets.updateCharacter(ch.id) { cur ->
                cur.copy(
                    projectId = cur.projectId ?: p.id,
                    description = c.str("description") ?: cur.description,
                    aliases = (cur.aliases + c.strList("aliases")).distinctBy(AssetIndex::norm).filterNot { AssetIndex.norm(it) == AssetIndex.norm(cur.name) },
                )
            }.name
        }
        if (chars.isNotEmpty()) changed += "personajes: ${chars.joinToString()}"
        if (changed.isEmpty()) return fail("No has indicado nada que guardar.")
        val fresh = d.assets.findProject(p.id) ?: updated
        return ok {
            put("saved", JsonArray(changed.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            putMemory(fresh)
            put("note", "Díselo al usuario en una línea («Guardado en la memoria de ${fresh.name}: …»).")
        }
    }
}
