package com.vidadomu.nexus.agent.core.llm

import java.text.Normalizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Planificador determinista SIN LLM (modo demo / sin conexión con Bedrock). Recorre las mismas
 * herramientas que usaría el LLM: recommend_models → prepare_generation → submit_generation. El prompt
 * es el texto del usuario tal cual; no hay razonamiento ni reescritura. Nunca gasta: el envío real
 * sigue requiriendo GENERAR.
 */
class OfflinePlannerProvider : LlmProvider {
    override val id = "offline"
    private var seq = 0

    override suspend fun complete(request: LlmRequest): LlmResponse {
        val msgs = request.messages
        val userIdx = msgs.indexOfLast { m -> m.role == Role.USER && m.content.any { it is ContentBlock.Text } && m.content.none { it is ContentBlock.ToolResult } }
        if (userIdx < 0) return say("Escribe qué quieres crear.")
        val userText = msgs[userIdx].text()
        if (com.vidadomu.nexus.agent.core.agent.ExternalServices.mentionsFlow(userText.substringBefore("\n\n[Adjuntos:")))
            return say(com.vidadomu.nexus.agent.core.agent.ExternalServices.OFFLINE_FLOW_REPLY)
        val last = msgs.last()
        val tools = request.tools.map { it.name }.toSet()
        if (last.role == Role.USER && last.content.any { it is ContentBlock.ToolResult }) {
            val prev = msgs[msgs.lastIndex - 1].toolUses().associateBy { it.id }
            val results = last.content.filterIsInstance<ContentBlock.ToolResult>()
            val r = results.first()
            val name = prev[r.toolUseId]?.name
            val body = r.content as? JsonObject ?: JsonObject(emptyMap())
            return when (name) {
                "recommend_models" -> afterRecommend(body, r.isError, userText, tools)
                "prepare_generation" -> if (r.isError) say("No he podido preparar el plan: ${issues(body)}. Ajusta la petición o usa el Lienzo.")
                    else use("submit_generation", buildJsonObject { put("plan_id", (body["plan"] as JsonObject)["plan_id"]!!.jsonPrimitive.content) })
                "submit_generation" -> say("Plan listo (modo sin LLM: el prompt es tu texto tal cual). Revisa modelo, configuración y coste en la tarjeta y pulsa GENERAR solo si te convence.")
                else -> say("Hecho.")
            }
        }
        return intent(userText)
    }

    private fun intent(text: String): LlmResponse {
        val t = norm(text)
        val images = Regex("attachment_id=([^,\\]]+), image/").findAll(text).count()
        val videos = Regex("attachment_id=([^,\\]]+), video/").findAll(text).count()
        val wantsVideo = Regex("\\b(video|plano|clip|anima|movimiento|travelling|dolly|camara|escena)").containsMatchIn(t) || DURATION.containsMatchIn(t)
        val wantsImage = Regex("\\b(imagen|foto|ilustracion|retrato|poster|portada)").containsMatchIn(t)
        val modality = when { wantsVideo -> "VIDEO"; wantsImage -> "IMAGE"; videos > 0 -> "VIDEO"; else -> "IMAGE" }
        val task = when {
            modality == "VIDEO" && videos > 0 -> "video_edit"
            modality == "VIDEO" && images > 0 -> "image_to_video"
            modality == "VIDEO" -> "text_to_video"
            images > 1 -> "multi_reference_image"
            images == 1 -> "image_edit"
            else -> "text_to_image"
        }
        val priorities = buildList {
            if (Regex("(cara|identidad|sin cambiar|mismo personaje|mismos personajes|continuidad)").containsMatchIn(t)) { add("identity_preservation"); add("face_consistency") }
            if (Regex("(cinemato|cine|pelicula)").containsMatchIn(t)) add("cinematic_look")
            if (Regex("(sutil|en voz baja|natural|actuacion)").containsMatchIn(t)) add("subtle_performance")
            if (Regex("(travelling|dolly|camara|paneo|grua)").containsMatchIn(t)) add("camera_control")
            if (Regex("(texto|rotulo|letras)").containsMatchIn(t)) add("text_rendering")
        }
        val input = buildJsonObject {
            put("modality", modality); put("task", task)
            DURATION.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { put("duration_sec", it) }
            Regex("\\b(16:9|9:16|1:1|4:3|3:4|21:9)\\b").find(text)?.let { put("aspect_ratio", it.value) }
            Regex("\\b(480p|720p|1080p|4k)\\b", RegexOption.IGNORE_CASE).find(text)?.let { put("resolution", it.value.lowercase()) }
            put("image_inputs", images)
            if (Regex("(sin sonido|sin audio|mudo)").containsMatchIn(t)) put("needs_audio", false)
            else if (Regex("(con sonido|con audio|dialogo)").containsMatchIn(t)) put("needs_audio", true)
            if (priorities.isNotEmpty()) put("priorities", buildJsonArray { priorities.forEach { add(JsonPrimitive(it)) } })
            put("budget", if (Regex("(barato|economico|bajo coste)").containsMatchIn(t)) "low" else "medium")
        }
        return use("recommend_models", input)
    }

    private fun afterRecommend(body: JsonObject, isError: Boolean, userText: String, tools: Set<String>): LlmResponse {
        val rec = body["recommended"] as? JsonObject
        if (isError || rec == null) {
            val notes = (body["notes"] as? JsonArray)?.joinToString(" ") { it.jsonPrimitive.content }.orEmpty()
            return say("No encuentro un modelo del registro que encaje. $notes".trim())
        }
        val ref = rec["ref"] as JsonObject
        val modelId = ref["modelId"]!!.jsonPrimitive.content
        val providerId = ref["providerId"]!!.jsonPrimitive.content
        val reasons = (rec["reasons"] as? JsonArray)?.take(3)?.joinToString("; ") { it.jsonPrimitive.content }.orEmpty()
        val alternatives = (body["alternatives"] as? JsonArray).orEmpty().mapNotNull { a ->
            ((a as? JsonObject)?.get("ref") as? JsonObject)?.let { "${it["providerId"]!!.jsonPrimitive.content}:${it["modelId"]!!.jsonPrimitive.content}" }
        }
        if ("prepare_generation" !in tools) {
            return say("Recomiendo ${rec["name"]?.jsonPrimitive?.contentOrNull ?: modelId}. $reasons. Alternativas: ${alternatives.joinToString().ifBlank { "ninguna" }}. Para prepararlo cambia a Copiloto o usa el Lienzo.")
        }
        val attachment = Regex("attachment_id=([^,\\]]+), (image|video)/").find(userText)
        val task = (body["task"] as? JsonObject)?.get("task")?.jsonPrimitive?.contentOrNull
        val prompt = userText.substringBefore("\n\n[Adjuntos:").trim()
        return use("prepare_generation", buildJsonObject {
            put("provider_id", providerId); put("model_id", modelId); put("prompt", prompt)
            rec["suggestedParams"]?.let { put("params", it) }
            task?.let { put("task", it) }
            if (attachment != null && task != "text_to_video" && task != "text_to_image") {
                put("media", buildJsonArray {
                    add(buildJsonObject {
                        put("role", if (attachment.groupValues[2] == "video") "video" else "first_frame")
                        put("attachment_id", attachment.groupValues[1].trim())
                    })
                })
            }
            if (reasons.isNotBlank()) put("rationale", reasons)
            if (alternatives.isNotEmpty()) put("alternatives", buildJsonArray { alternatives.forEach { add(JsonPrimitive(it)) } })
        })
    }

    private fun issues(body: JsonObject): String =
        (body["issues"] as? JsonArray)?.joinToString("; ") { (it as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: it.toString() }
            ?: body["error"]?.jsonPrimitive?.contentOrNull ?: "error desconocido"

    private fun use(name: String, input: JsonObject) = LlmResponse(
        LlmMessage(Role.ASSISTANT, listOf(ContentBlock.ToolUse("offline_${++seq}", name, input))), StopReason.TOOL_USE, Usage(0, 0),
    )

    private fun say(text: String) = LlmResponse(LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text(text))), StopReason.END_TURN, Usage(0, 0))

    companion object {
        private val DURATION = Regex("\\b(\\d{1,2})\\s?(s|seg|segs|segundos|sec|seconds)\\b")
        private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
        private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
    }
}
