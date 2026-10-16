package com.vidadomu.nexus.agent.core.llm

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/** Opciones de la petición Responses; todas opcionales salvo lo que fija el propio [LlmRequest]. */
data class ResponsesOptions(
    val reasoningEffort: String? = null,
    val thinking: String? = null,
    val store: Boolean? = null,
    val extraBody: JsonObject? = null,
    /** false = no se envían herramientas (el proveedor no soporta function calling). */
    val sendTools: Boolean = true,
)

/**
 * Responses API (OpenAI y compatibles, p. ej. BytePlus ModelArk). Sin estado en el servidor: cada petición lleva
 * la conversación completa, así que la continuidad del contexto no depende de `previous_response_id`. Las
 * herramientas del agente viajan como `function` y sus resultados como `function_call_output` por `call_id`.
 */
open class ResponsesApiProvider(
    protected val target: HttpTarget,
    protected val options: ResponsesOptions = ResponsesOptions(),
    protected val client: OkHttpClient = BedrockProxyProvider.defaultClient(),
    protected val who: String = "el proveedor",
    override val id: String = "responses",
) : LlmProvider {
    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val http = target.request().post(ResponsesCodec.encode(request, options, stream = false).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        try {
            client.newCall(http).awaitBody().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmHttpErrors.map(resp.code, text, who)
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "El proveedor devolvió una respuesta no válida.") }
                ResponsesCodec.decode(json)
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "No se pudo contactar con $who (${e.javaClass.simpleName}).")
        }
    }
}

/** Igual que [ResponsesApiProvider] pero con `stream:true`: entrega el texto según llega. */
class ResponsesApiStreamingProvider(
    target: HttpTarget, options: ResponsesOptions = ResponsesOptions(),
    client: OkHttpClient = BedrockProxyProvider.defaultClient(), who: String = "el proveedor", id: String = "responses",
) : ResponsesApiProvider(target, options, client, who, id), StreamingLlmProvider {
    override suspend fun complete(request: LlmRequest): LlmResponse = completeStreaming(request) { }

    override suspend fun completeStreaming(request: LlmRequest, onTextDelta: (String) -> Unit): LlmResponse = withContext(Dispatchers.IO) {
        val http = target.request().header("Accept", "text/event-stream").post(ResponsesCodec.encode(request, options, stream = true).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        try {
            SseStream.open(client, http).use { resp ->
                if (!resp.isSuccessful) throw LlmHttpErrors.map(resp.code, resp.body?.string().orEmpty(), who)
                var finalResponse: JsonObject? = null
                val items = mutableListOf<JsonElement>()
                val text = StringBuilder()
                SseStream.read(resp) { ev ->
                    if (ev.data == "[DONE]") return@read false
                    val data = runCatching { Json.parseToJsonElement(ev.data).jsonObject }.getOrNull() ?: return@read true
                    when (data["type"]?.jsonPrimitive?.contentOrNull ?: ev.event) {
                        "response.output_text.delta" -> data["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                            ?.let { text.append(it); onTextDelta(it) }
                        "response.output_item.done" -> data["item"]?.let { items += it }
                        "response.completed", "response.incomplete" -> { finalResponse = data["response"] as? JsonObject; return@read false }
                        "response.failed" -> throw ResponsesCodec.failure(data["response"]?.jsonObject?.get("error"), who)
                        "error" -> throw ResponsesCodec.failure(data["error"] ?: data, who)
                    }
                    true
                }
                finalResponse?.let { return@use ResponsesCodec.decode(it) }
                // El servidor cortó antes del evento final: se aprovecha lo recibido si hay algo útil.
                if (items.isEmpty() && text.isEmpty()) throw LlmException(LlmException.Kind.PROTOCOL, "La respuesta en streaming terminó sin contenido.")
                ResponsesCodec.decode(buildJsonObject {
                    put("status", "completed")
                    put("output", JsonArray(if (items.isNotEmpty()) items else listOf(buildJsonObject {
                        put("type", "message")
                        put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", text.toString()) }) })
                    })))
                })
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "Se perdió la conexión con $who (${e.javaClass.simpleName}).")
        }
    }
}

/** Traducción entre los tipos del agente y la Responses API. */
object ResponsesCodec {
    fun encode(request: LlmRequest, options: ResponsesOptions, stream: Boolean): JsonObject {
        val base = encodeBase(request, options, stream)
        // Parámetros avanzados del usuario: nunca pisan lo que ya fija el agente.
        val extra = options.extraBody?.filterKeys { it !in BrainProfile.RESERVED_BODY_KEYS && it !in base }.orEmpty()
        return JsonObject(base + extra)
    }

    private fun encodeBase(request: LlmRequest, options: ResponsesOptions, stream: Boolean): JsonObject = buildJsonObject {
        put("model", request.modelId)
        if (request.system.isNotEmpty()) put("instructions", request.system.joinToString("\n\n"))
        put("input", buildJsonArray {
            for (m in request.messages) when (m.role) {
                Role.ASSISTANT -> {
                    val text = m.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
                    if (text.isNotBlank()) add(buildJsonObject { put("role", "assistant"); put("content", text) })
                    m.toolUses().forEach { u ->
                        add(buildJsonObject {
                            put("type", "function_call"); put("call_id", u.id); put("name", u.name); put("arguments", u.input.toString())
                        })
                    }
                }
                Role.USER -> {
                    // Los resultados de herramienta van como function_call_output, antes del texto nuevo.
                    m.content.filterIsInstance<ContentBlock.ToolResult>().forEach { r ->
                        add(buildJsonObject {
                            put("type", "function_call_output"); put("call_id", r.toolUseId)
                            put("output", (if (r.isError) "ERROR: " else "") + r.content.toString())
                        })
                    }
                    val parts = m.content.filter { it !is ContentBlock.ToolResult && it !is ContentBlock.Reasoning }.map { block ->
                        when (block) {
                            is ContentBlock.Text -> buildJsonObject { put("type", "input_text"); put("text", block.text) }
                            is ContentBlock.Image -> buildJsonObject {
                                put("type", "input_image")
                                put("image_url", "data:image/${block.format};base64," + Base64.getEncoder().encodeToString(block.bytes))
                            }
                            is ContentBlock.Document -> buildJsonObject {
                                put("type", "input_text")
                                put("text", if (block.format in setOf("txt", "md", "csv", "html")) "[Documento ${block.name}]\n" + block.bytes.toString(Charsets.UTF_8)
                                    else "[Documento ${block.name} (${block.format}): este proveedor no lo recibe como archivo; pide al usuario el texto si lo necesitas.]")
                            }
                            else -> buildJsonObject { put("type", "input_text"); put("text", "") }
                        }
                    }
                    if (parts.isNotEmpty()) add(buildJsonObject {
                        put("role", "user")
                        if (parts.all { it["type"]?.jsonPrimitive?.content == "input_text" }) put("content", parts.joinToString("\n") { it["text"]!!.jsonPrimitive.content })
                        else put("content", JsonArray(parts))
                    })
                }
            }
        })
        if (request.tools.isNotEmpty() && options.sendTools) put("tools", JsonArray(request.tools.map { t ->
            buildJsonObject { put("type", "function"); put("name", t.name); put("description", t.description); put("parameters", t.inputSchema) }
        }))
        put("max_output_tokens", request.maxTokens)
        request.temperature?.let { put("temperature", it) }
        // Esfuerzo y pensamiento desactivado se excluyen entre sí en ModelArk (400): gana el desactivado.
        if (options.thinking != null) put("thinking", buildJsonObject { put("type", options.thinking) })
        if (options.reasoningEffort != null && options.thinking != "disabled") put("reasoning", buildJsonObject { put("effort", options.reasoningEffort) })
        options.store?.let { put("store", it) }
        if (stream) put("stream", true)
    }

    fun failure(error: JsonElement?, who: String): LlmException {
        val msg = ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull ?: (error as? JsonPrimitive)?.contentOrNull
        return LlmException(LlmException.Kind.UPSTREAM, (msg?.take(200) ?: "Error de $who."))
    }

    fun decode(json: JsonObject): LlmResponse {
        (json["error"] as? JsonObject)?.let { throw failure(it, "el proveedor") }
        val blocks = mutableListOf<ContentBlock>()
        val output = (json["output"] as? JsonArray).orEmpty()
        for (item in output) {
            val o = item as? JsonObject ?: continue
            when (o["type"]?.jsonPrimitive?.contentOrNull) {
                "message" -> (o["content"] as? JsonArray)?.forEach { part ->
                    val p = part as? JsonObject ?: return@forEach
                    when (p["type"]?.jsonPrimitive?.contentOrNull) {
                        "output_text", "text" -> p["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { blocks += ContentBlock.Text(it) }
                        "refusal" -> p["refusal"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { blocks += ContentBlock.Text(it) }
                    }
                }
                "function_call" -> {
                    val args = o["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val input = runCatching { Json.parseToJsonElement(args.ifBlank { "{}" }).jsonObject }.getOrElse { JsonObject(emptyMap()) }
                    blocks += ContentBlock.ToolUse(
                        o["call_id"]?.jsonPrimitive?.contentOrNull ?: o["id"]?.jsonPrimitive?.contentOrNull ?: "call_${blocks.size}",
                        o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), input)
                }
                // El razonamiento no se reenvía: cada petición es autosuficiente.
                else -> Unit
            }
        }
        if (blocks.none { it is ContentBlock.Text }) (json["output_text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { blocks.add(0, ContentBlock.Text(it)) }
        if (blocks.isEmpty() && json["status"]?.jsonPrimitive?.contentOrNull == "failed")
            throw LlmException(LlmException.Kind.UPSTREAM, "El proveedor no pudo completar la respuesta.")
        val reason = ((json["incomplete_details"] as? JsonObject)?.get("reason") as? JsonPrimitive)?.contentOrNull
        val stop = when {
            blocks.any { it is ContentBlock.ToolUse } -> StopReason.TOOL_USE
            json["status"]?.jsonPrimitive?.contentOrNull == "incomplete" && reason in setOf("max_output_tokens", "max_tokens", "length") -> StopReason.MAX_TOKENS
            reason == "content_filter" -> StopReason.CONTENT_FILTERED
            json["status"]?.jsonPrimitive?.contentOrNull == "incomplete" -> StopReason.OTHER
            else -> StopReason.END_TURN
        }
        val usage = json["usage"] as? JsonObject
        return LlmResponse(LlmMessage(Role.ASSISTANT, blocks), stop,
            Usage(usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0))
    }
}
