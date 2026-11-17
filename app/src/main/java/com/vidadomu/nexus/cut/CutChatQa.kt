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
    /** What each tool call of the last scripted run returned, per montage (see [scriptedSteps]). */
    private val results = ConcurrentHashMap<String, JsonArray>()

    /** The armed provider is removed atomically when the UI creates its next turn. */
    fun take(projectId: String): LlmProvider? = if (BuildConfig.DEBUG) overrides.remove(projectId)?.provider else null
    fun isArmed(projectId: String): Boolean = BuildConfig.DEBUG && overrides.containsKey(projectId)

    @OptIn(UnstableApi::class)
    suspend fun handle(app: NexusApplication, input: JsonObject): JsonObject {
        check(BuildConfig.DEBUG) { "Prueba de chat disponible solo en depuración." }
        require(input.keys.all { it in setOf("action", "project_id", "name", "steps") }) { "La prueba solo admite action, project_id, name y steps." }
        val action = input["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val projectId = input["project_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(projectId.matches(Regex("[A-Za-z0-9_\\-]+"))) { "Indica un montaje válido." }
        when (action) {
            "arm", "arm_search", "arm_edit", "arm_script" -> {
                val rawName = input["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                require(rawName.isNotBlank() && rawName.length <= 100) { "El nombre debe tener entre 1 y 100 caracteres." }
                val name = rawName.trim()
                // Opening an existing session validates the montage without editing it or changing the active one.
                val session = NexusCut.workspace(app).session(projectId)
                overrides[projectId] = Armed(name, when (action) {
                    "arm_search" -> scriptedSearch(name)
                    "arm_edit" -> scriptedEdit(projectId, session)
                    "arm_script" -> {
                        val steps = input["steps"] as? JsonArray ?: throw IllegalArgumentException("arm_script necesita steps.")
                        require(steps.size in 1..40) { "El guion admite entre 1 y 40 pasos." }
                        results.remove(projectId)
                        scriptedSteps(projectId, session, steps)
                    }
                    else -> scripted(projectId, name, session)
                })
            }
            "status" -> Unit
            "clear" -> { overrides.remove(projectId); results.remove(projectId) }
            else -> throw IllegalArgumentException("Acción de prueba desconocida: usa arm, arm_search, arm_edit, arm_script, status o clear.")
        }
        val armed = overrides[projectId]
        return buildJsonObject {
            put("ok", true); put("project_id", projectId); put("armed", armed != null)
            armed?.let { put("name", it.name) }
            results[projectId]?.let { put("results", it) }
        }
    }

    /**
     * Un turno con el guion que diga la prueba: cada paso es una llamada {tool, input} a las herramientas
     * del chat del montaje (cut_read, cut_edit...), en orden y por el mismo bucle que usa el agente. Las
     * ediciones sin expected_revision reciben la revisión vigente. Un paso que falla no detiene el guion:
     * lo que devolvió cada herramienta (también los errores) queda en «results» de la acción status.
     */
    private fun scriptedSteps(projectId: String, session: CutSession, steps: JsonArray): LlmProvider {
        val token = UUID.randomUUID().toString()
        val collected = ArrayList<JsonElement>()
        fun idOf(index: Int) = "cut-qa-step-$index-$token"
        fun collect(request: LlmRequest, index: Int) {
            val result = request.messages.flatMap { it.content }.filterIsInstance<ContentBlock.ToolResult>()
                .lastOrNull { it.toolUseId == idOf(index) }
            collected += buildJsonObject {
                put("step", index)
                put("is_error", result?.isError ?: true)
                put("content", result?.content ?: JsonPrimitive("La herramienta no devolvió resultado."))
            }
        }
        val turns = ArrayList<(LlmRequest) -> com.vidadomu.nexus.agent.core.llm.LlmResponse>()
        steps.forEachIndexed { index, raw ->
            val step = raw as? JsonObject ?: throw IllegalArgumentException("Cada paso es un objeto {tool, input}.")
            val tool = step["tool"]?.jsonPrimitive?.contentOrNull.orEmpty()
            require(tool.matches(Regex("[a-z_]{1,40}"))) { "Paso $index: falta tool." }
            val input = step["input"] as? JsonObject ?: JsonObject(emptyMap())
            turns += { request ->
                if (index > 0) collect(request, index - 1)
                check(request.tools.any { it.name == tool }) { "El chat del montaje no ofrece la herramienta $tool." }
                val filled = if (tool == "cut_edit" && "expected_revision" !in input)
                    JsonObject(input + ("expected_revision" to JsonPrimitive(session.state.value.project.revision)))
                else input
                ScriptedLlmProvider.tools(ContentBlock.ToolUse(idOf(index), tool, filled))
            }
        }
        turns += { request ->
            collect(request, steps.size - 1)
            results[projectId] = JsonArray(collected.toList())
            val failed = collected.count { it.jsonObject["is_error"]?.jsonPrimitive?.booleanOrNull == true }
            ScriptedLlmProvider.text(
                "Prueba local sin modelo externo: ${steps.size} llamadas a las herramientas del montaje" +
                    if (failed == 0) ", todas correctas." else ", $failed con error.",
            )
        }
        return ScriptedLlmProvider(turns)
    }

    private fun scriptedSearch(query: String): LlmProvider {
        val token = UUID.randomUUID().toString()
        val searchId = "cut-qa-search-$token"
        val showId = "cut-qa-show-$token"
        return ScriptedLlmProvider(listOf(
            { request ->
                check(request.tools.any { it.name == "search_assets" } && request.tools.any { it.name == "open_results_wall" }) {
                    "El chat no recibió las herramientas para buscar y mostrar la Biblioteca."
                }
                ScriptedLlmProvider.tools(ContentBlock.ToolUse(searchId, "search_assets", buildJsonObject { put("text", query) }))
            },
            { request ->
                val result = successfulResult(request, searchId)
                val found = result["assets"] as? JsonArray ?: error("La búsqueda no devolvió assets.")
                check(found.isNotEmpty()) { "La búsqueda local no encontró el archivo de prueba." }
                val ids = found.map { it.jsonObject["asset_id"]!!.jsonPrimitive.content }
                ScriptedLlmProvider.tools(ContentBlock.ToolUse(showId, "open_results_wall", buildJsonObject {
                    put("asset_ids", JsonArray(ids.map(::JsonPrimitive)))
                }))
            },
            { request ->
                successfulResult(request, showId)
                ScriptedLlmProvider.text("Prueba local sin modelo externo: he buscado en la Biblioteca y solicitado mostrar los resultados. No he añadido archivos al montaje.")
            },
        ))
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

    /**
     * Un turno de montaje completo sin modelo externo, por las mismas herramientas que usa el agente: lee el
     * contexto, divide un clip de vídeo y acelera su segunda mitad (dos pasos del mismo turno, revertibles
     * juntos), mide los silencios reales del audio y deja su borrado como propuesta (dry_run) para aplicarla
     * o descartarla desde el chat. Los clips se eligen del montaje abierto; las ediciones van por cut_edit.
     */
    private fun scriptedEdit(projectId: String, session: CutSession): LlmProvider {
        val token = UUID.randomUUID().toString()
        val readId = "cut-qa-ctx-$token"
        val splitId = "cut-qa-split-$token"
        val speedId = "cut-qa-speed-$token"
        val silenceId = "cut-qa-silence-$token"
        val proposeId = "cut-qa-propose-$token"
        var split: Pair<String, Long>? = null
        var silences = 0
        fun edit(id: String, label: String, revision: Long, dryRun: Boolean = false, op: JsonObjectBuilder.() -> Unit) =
            ScriptedLlmProvider.tools(ContentBlock.ToolUse(id, "cut_edit", buildJsonObject {
                put("ops", buildJsonArray { add(buildJsonObject(op)) })
                put("label", label); put("expected_revision", revision)
                if (dryRun) put("dry_run", true)
            }))
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
                val revision = read["revision"]?.jsonPrimitive?.longOrNull ?: error("La lectura no confirmó la revisión.")
                val timeline = session.state.value.project.timeline
                val clip = timeline.videoTracks.filterNot { it.locked }.flatMap { it.clips }
                    .firstOrNull { it.kind == com.vidadomu.nexus.cut.model.ClipKind.VIDEO && it.durationUs >= 1_500_000L }
                    ?: error("La prueba necesita un clip de vídeo de al menos 1,5 s en una pista desbloqueada.")
                val rate = session.state.value.project.settings.frameRate
                val at = rate.snap(clip.timelineStartUs + clip.durationUs / 2)
                split = clip.id to at
                edit(splitId, "Prueba: dividir el clip", revision) {
                    put("id", "clip.split"); put("args", buildJsonObject { put("clip_ids", buildJsonArray { add(clip.id) }); put("at_us", at) })
                }
            },
            { request ->
                val done = successfulResult(request, splitId)
                check(done["committed"]?.jsonPrimitive?.booleanOrNull == true) { "La división de prueba no se confirmó." }
                val revision = done["revision"]?.jsonPrimitive?.longOrNull ?: error("La división no devolvió la revisión.")
                val (originalId, at) = requireNotNull(split)
                val track = session.state.value.project.timeline.videoTracks.firstOrNull { t -> t.clips.any { it.id == originalId } }
                    ?: error("El clip dividido ya no está en el montaje.")
                val right = track.clips.firstOrNull { it.timelineStartUs == at && it.id != originalId }
                    ?: error("No aparece la segunda mitad del clip dividido.")
                edit(speedId, "Prueba: acelerar la segunda mitad", revision) {
                    put("id", "clip.set_speed"); put("args", buildJsonObject { put("clip_id", right.id); put("speed", "2") })
                }
            },
            { request ->
                val done = successfulResult(request, speedId)
                check(done["committed"]?.jsonPrimitive?.booleanOrNull == true) { "El cambio de velocidad de prueba no se confirmó." }
                ScriptedLlmProvider.tools(ContentBlock.ToolUse(silenceId, "cut_read", buildJsonObject {
                    put("ops", buildJsonArray { add(buildJsonObject { put("id", "audio.find_silences") }) })
                }))
            },
            { request ->
                val read = successfulResult(request, silenceId)
                val revision = read["revision"]?.jsonPrimitive?.longOrNull ?: error("La medición no confirmó la revisión.")
                val found = ((read["results"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("ranges") as? JsonArray ?: JsonArray(emptyList())
                silences = found.size
                if (found.isEmpty()) ScriptedLlmProvider.text("Prueba local sin modelo externo: he dividido un clip y acelerado su segunda mitad (dos pasos que puedes deshacer juntos). No he encontrado silencios que quitar.")
                else edit(proposeId, "Quitar ${found.size} silencios", revision, dryRun = true) {
                    put("id", "timeline.delete_ranges"); put("args", buildJsonObject { put("ranges", found) })
                }
            },
            { request ->
                val proposal = successfulResult(request, proposeId)
                check(proposal["dry_run"]?.jsonPrimitive?.booleanOrNull == true && proposal["committed"]?.jsonPrimitive?.booleanOrNull != true) {
                    "La propuesta de prueba se aplicó en vez de ensayarse."
                }
                ScriptedLlmProvider.text("Prueba local sin modelo externo: he dividido un clip y acelerado su segunda mitad (dos pasos que puedes deshacer juntos). He medido el audio: hay $silences silencios. Te propongo quitarlos; no lo he aplicado.")
            },
        ))
    }

    private fun successfulResult(request: LlmRequest, toolId: String): JsonObject {
        val result = request.messages.flatMap { it.content }.filterIsInstance<ContentBlock.ToolResult>()
            .lastOrNull { it.toolUseId == toolId }
        check(result != null && !result.isError) { "La herramienta de prueba no devolvió un resultado correcto." }
        val content = result.content as? JsonObject ?: error("La herramienta de prueba no devolvió un objeto válido.")
        check(content["ok"]?.jsonPrimitive?.booleanOrNull != false && "error" !in content) {
            "La herramienta de prueba informó de un error."
        }
        return content
    }
}
