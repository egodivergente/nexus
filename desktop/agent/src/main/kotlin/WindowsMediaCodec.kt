import com.vidadomu.nexus.agent.core.media.*
import com.vidadomu.nexus.agent.core.net.JsonPaths
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Pure Android media protocol, shared by Windows manual and agent requests. No credentials. */
object WindowsMediaCodec {
    private val json = Json { encodeDefaults = true }
    fun command(a: JsonObject): JsonObject {
        val p = MediaProviderConfig.parse(a.getValue("config").toString())
        val problems = p.problems()
        require(problems.isEmpty()) { problems.joinToString("\n") }
        val action = a["action"]?.jsonPrimitive?.content ?: "validate"
        if (action == "validate") return buildJsonObject {
            put("ok", true); put("config", json.parseToJsonElement(json.encodeToString(p)))
        }
        if (action == "upload_result") return buildJsonObject {
            put("ok", true); put("url", JsonPaths.string(a.getValue("response"), p.uploadUrlPath))
        }
        val m = p.models.single { it.id == a.getValue("model").jsonPrimitive.content }
        return buildJsonObject {
            put("ok", true)
            when (action) {
                "create", "input" -> {
                    val v = a["input"]?.jsonObject ?: buildJsonObject {}
                    val input = MediaInput(v["prompt"]?.jsonPrimitive?.content ?: "",
                        v["params"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
                        v["imageUrls"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        v["videoUrl"]?.jsonPrimitive?.contentOrNull)
                    require(MediaEngine.inputProblems(m, input).isEmpty()) { MediaEngine.inputProblems(m, input).joinToString("\n") }
                    // The shared engine preserves provider types. Reject malformed numeric values before HTTP.
                    val typed = MediaEngine.typedParams(m, input.params)
                    m.params.forEach { param ->
                        val raw = (input.params[param.key] ?: param.default).trim()
                        if (raw.isNotEmpty()) {
                            val value = typed[param.key]?.jsonPrimitive
                            when (param.type) {
                                "integer", "duration", "seed" -> require(raw.toLongOrNull() != null) { "${param.label}: número entero requerido" }
                                "float" -> require(raw.replace(',', '.').toDoubleOrNull()?.isFinite() == true) { "${param.label}: número requerido" }
                                "boolean" -> require(raw.lowercase().toBooleanStrictOrNull() != null) { "${param.label}: booleano requerido" }
                            }
                            value?.doubleOrNull?.let { number ->
                                require((param.min == null || number >= param.min!!) && (param.max == null || number <= param.max!!)) { "${param.label}: fuera de límites" }
                            }
                        }
                    }
                    if (action == "create") call(MediaEngine.buildCreate(p, m, input))
                }
                "poll" -> call(MediaEngine.buildPoll(p, m, a.getValue("task_id").jsonPrimitive.content))
                "parse_create" -> when (val r = MediaEngine.parseCreate(m, a.getValue("response"))) {
                    is CreateOutcome.Done -> done(r.urls)
                    is CreateOutcome.Async -> { put("status", "PROCESSING"); put("remoteId", r.taskId) }
                    is CreateOutcome.Failed -> { put("status", "FAILED"); put("error", r.message) }
                }
                "parse_poll" -> when (val r = MediaEngine.parsePoll(m, a.getValue("response"))) {
                    is PollOutcome.Done -> done(r.urls)
                    is PollOutcome.Running -> { put("status", "PROCESSING"); r.progress?.let { put("progress", it) } }
                    is PollOutcome.Failed -> { put("status", "FAILED"); put("error", r.message) }
                }
                else -> error("Operación multimedia desconocida")
            }
        }
    }
    private fun JsonObjectBuilder.call(c: MediaHttpCall) {
        put("method", c.method); put("url", c.url); c.body?.let { put("body", it) }
    }
    private fun JsonObjectBuilder.done(urls: List<String>) {
        put("status", "COMPLETED"); put("outputs", JsonArray(urls.map(::JsonPrimitive)))
    }
}
