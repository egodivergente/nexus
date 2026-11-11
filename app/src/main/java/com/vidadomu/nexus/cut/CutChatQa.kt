package com.vidadomu.nexus.cut

import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.cut.command.CutSession
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Debug-only, in-memory replacement for the next send in one montage's chat. Arming never runs a tool. */
internal object CutChatQa {
    private data class Armed(val name: String, val provider: LlmProvider)
    private val overrides = ConcurrentHashMap<String, Armed>()

    /** The armed provider is removed atomically when the UI creates its next turn. */
    fun take(projectId: String): LlmProvider? = if (BuildConfig.DEBUG) overrides.remove(projectId)?.provider else null
    fun isArmed(projectId: String): Boolean = BuildConfig.DEBUG && overrides.containsKey(projectId)

    @OptIn(UnstableApi::class)
    suspend fun handle(app: NexusApplication, input: JsonObject): JsonObject {
        check(BuildConfig.DEBUG) { "Prueba de chat disponible solo en depuración." }
        require(input.keys.all { it in setOf("action", "project_id", "name") }) { "La prueba solo admite action, project_id y name." }
        val action = input["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val projectId = input["project_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(projectId.matches(Regex("[A-Za-z0-9_\\-]+"))) { "Indica un montaje válido." }
        when (action) {
            "arm" -> {
                val rawName = input["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                require(rawName.isNotBlank() && rawName.length <= 100) { "El nombre debe tener entre 1 y 100 caracteres." }
                val name = rawName.trim()
                // Opening an existing session validates the montage without editing it or changing the active one.
                val session = NexusCut.workspace(app).session(projectId)
                overrides[projectId] = Armed(name, scripted(projectId, name, session))
            }
            "status" -> Unit
            "clear" -> overrides.remove(projectId)
            else -> throw IllegalArgumentException("Acción de prueba desconocida: usa arm, status o clear.")
        }
        val armed = overrides[projectId]
        return buildJsonObject {
            put("ok", true); put("project_id", projectId); put("armed", armed != null)
            armed?.let { put("name", it.name) }
        }
    }

    private fun scripted(projectId: String, name: String, session: CutSession): LlmProvider {
        val token = UUID.randomUUID().toString()
        val readId = "cut-qa-read-$token"
        val editId = "cut-qa-edit-$token"
        var readRevision: Long? = null
        return ScriptedLlmProvider(listOf(
            { request ->
                check(request.tools.any { it.name == "cut_read" } && request.tools.any { it.name == "cut_edit" }) {
                    "El chat de prueba no recibió las herramientas del montaje."
                }
                ScriptedLlmProvider.tools(ContentBlock.ToolUse(readId, "cut_read", buildJsonObject {
                    put("ops", buildJsonArray { add(buildJsonObject { put("id", "project.context") }) })
                }))
            },
            { request ->
                val read = successfulResult(request, readId)
                val context = (read["results"] as? JsonArray)?.singleOrNull() as? JsonObject
                val project = context?.get("project") as? JsonObject
                check(project?.get("project_id")?.jsonPrimitive?.contentOrNull == projectId) {
                    "La lectura de prueba no corresponde a este montaje."
                }
                readRevision = read["revision"]?.jsonPrimitive?.longOrNull
                check(readRevision != null) { "La lectura de prueba no confirmó la revisión del montaje." }
                ScriptedLlmProvider.tools(ContentBlock.ToolUse(editId, "cut_edit", buildJsonObject {
                    // project_id is intentionally omitted: the editor's bound tool must supply its own target.
                    put("ops", buildJsonArray { add(buildJsonObject {
                        put("id", "project.rename"); put("args", buildJsonObject { put("name", name) })
                    }) })
                    put("label", "Prueba de chat: renombrar montaje")
                    put("expected_revision", readRevision!!)
                    put("idempotency_key", "cut-chat-qa-$token")
                }))
            },
            { request ->
                successfulResult(request, readId)
                val edited = successfulResult(request, editId)
                check(edited["dry_run"]?.jsonPrimitive?.booleanOrNull != true) { "La edición de prueba fue solo un ensayo." }
                val current = session.state.value.project
                check(current.id == projectId && current.name == name && current.revision >= requireNotNull(readRevision)) {
                    "El montaje no confirma el nombre solicitado por la prueba."
                }
                ScriptedLlmProvider.text(if (edited["committed"]?.jsonPrimitive?.booleanOrNull == true)
                    "Prueba local sin modelo externo: he renombrado este montaje a «$name». La herramienta confirmó el cambio."
                else "Prueba local sin modelo externo: este montaje ya se llama «$name». La herramienta confirmó su estado.")
            },
        ))
    }

    private fun successfulResult(request: LlmRequest, toolId: String): JsonObject {
        val result = request.messages.flatMap { it.content }.filterIsInstance<ContentBlock.ToolResult>()
            .lastOrNull { it.toolUseId == toolId }
        check(result != null && !result.isError) { "La herramienta de prueba no devolvió un resultado correcto." }
        val content = result.content as? JsonObject ?: error("La herramienta de prueba no devolvió un objeto válido.")
        check(content["ok"]?.jsonPrimitive?.booleanOrNull == true && "error" !in content) {
            "La herramienta de prueba informó de un error."
        }
        return content
    }
}
