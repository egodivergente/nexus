package com.vidadomu.nexus.agent.core.llm

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Opciones de Chat Completions; el valor por defecto reproduce exactamente el comportamiento anterior. */
data class ChatOptions(
    val reasoningEffort: String? = null,
    val thinking: String? = null,
    /** null = automático (`max_completion_tokens` solo en api.openai.com). */
    val maxTokensField: String? = null,
    val extraBody: JsonObject? = null,
    /** false = no se envían herramientas (el proveedor no soporta function calling). */
    val sendTools: Boolean = true,
)

/**
 * Cualquier API compatible con Chat Completions de OpenAI: OpenAI, OpenRouter, Gemini (endpoint
 * OpenAI), DeepSeek, Groq, Mistral, BytePlus o un servidor local. Las herramientas del agente viajan como
 * `tools` de tipo function; los resultados, como mensajes `tool`.
 */
open class OpenAiCompatibleProvider(
    protected val target: HttpTarget,
    protected val options: ChatOptions = ChatOptions(),
    protected val client: OkHttpClient = BedrockProxyProvider.defaultClient(),
    protected val who: String = "el proveedor",
) : LlmProvider {
    constructor(
        baseUrl: String,
        apiKey: () -> String,
        extraHeaders: Map<String, String> = emptyMap(),
        client: OkHttpClient = BedrockProxyProvider.defaultClient(),
    ) : this(HttpTarget(chatEndpoint(baseUrl), BrainAuth(), apiKey, extraHeaders), ChatOptions(), client)

    override val id: String = "openai-compatible"

    protected fun body(request: LlmRequest, stream: Boolean) =
        OpenAiCodec.encode(request, maxCompletionTokens = options.maxTokensField?.let { it == "max_completion_tokens" } ?: ("api.openai.com" in target.url),
            options = options, stream = stream).toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val http = target.request().post(body(request, stream = false)).build()
        try {
            client.newCall(http).awaitBody().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmHttpErrors.map(resp.code, text, who)
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "El proveedor devolvió una respuesta no válida.") }
                OpenAiCodec.decode(json)
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "No se pudo contactar con el proveedor del LLM (${e.javaClass.simpleName}).")
        }
    }

    companion object {
        fun chatEndpoint(baseUrl: String): String =
            baseUrl.trim().trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }
    }
}

/** Chat Completions con `stream:true`: entrega el texto según llega y reensambla las llamadas a herramientas. */
class OpenAiCompatibleStreamingProvider(
    target: HttpTarget, options: ChatOptions = ChatOptions(),
    client: OkHttpClient = BedrockProxyProvider.defaultClient(), who: String = "el proveedor",
) : OpenAiCompatibleProvider(target, options, client, who), StreamingLlmProvider {
    override suspend fun complete(request: LlmRequest): LlmResponse = completeStreaming(request) { }

    override suspend fun completeStreaming(request: LlmRequest, onTextDelta: (String) -> Unit): LlmResponse = withContext(Dispatchers.IO) {
        val http = target.request().header("Accept", "text/event-stream").post(body(request, stream = true)).build()
        try {
            SseStream.open(client, http).use { resp ->
                if (!resp.isSuccessful) throw LlmHttpErrors.map(resp.code, resp.body?.string().orEmpty(), who)
                val text = StringBuilder()
                val calls = sortedMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>() // id, nombre, argumentos
                var finish: String? = null
                var usage: JsonObject? = null
                var sawChunk = false
                SseStream.read(resp) { ev ->
                    if (ev.data == "[DONE]") return@read false
                    val chunk = runCatching { Json.parseToJsonElement(ev.data).jsonObject }.getOrNull() ?: return@read true
                    (chunk["error"] as? JsonObject)?.let { throw ResponsesCodec.failure(it, who) }
                    sawChunk = true
                    (chunk["usage"] as? JsonObject)?.let { usage = it }
                    val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@read true
                    choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finish = it }
                    val delta = choice["delta"] as? JsonObject ?: return@read true
                    (delta["content"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { text.append(it); onTextDelta(it) }
                    (delta["tool_calls"] as? JsonArray)?.forEach { tc ->
                        val o = tc as? JsonObject ?: return@forEach
                        val slot = calls.getOrPut(o["index"]?.jsonPrimitive?.intOrNull ?: calls.size) { Triple(StringBuilder(), StringBuilder(), StringBuilder()) }
                        o["id"]?.jsonPrimitive?.contentOrNull?.let { slot.first.append(it) }
                        (o["function"] as? JsonObject)?.let { fn ->
                            fn["name"]?.jsonPrimitive?.contentOrNull?.let { slot.second.append(it) }
                            fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { slot.third.append(it) }
                        }
                    }
                    true
                }
                if (!sawChunk) throw LlmException(LlmException.Kind.PROTOCOL, "La respuesta en streaming terminó sin contenido.")
                OpenAiCodec.decode(buildJsonObject {
                    put("choices", buildJsonArray { add(buildJsonObject {
                        put("message", buildJsonObject {
                            put("content", text.toString())
                            if (calls.isNotEmpty()) put("tool_calls", buildJsonArray {
                                calls.values.forEach { (cid, name, args) -> add(buildJsonObject {
                                    put("id", cid.toString()); put("type", "function")
                                    put("function", buildJsonObject { put("name", name.toString()); put("arguments", args.toString()) })
                                }) }
                            })
                        })
                        finish?.let { put("finish_reason", it) }
                    }) })
                    usage?.let { put("usage", it) }
                })
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "Se perdió la conexión con $who (${e.javaClass.simpleName}).")
        }
    }
}
/** Traducción entre los tipos propios del agente y Chat Completions. */
object OpenAiCodec {
    fun encode(request: LlmRequest, maxCompletionTokens: Boolean = false, options: ChatOptions = ChatOptions(), stream: Boolean = false): JsonObject {
        val base = encodeBase(request, maxCompletionTokens, options, stream)
        val extra = options.extraBody?.filterKeys { it !in BrainProfile.RESERVED_BODY_KEYS && it !in base }.orEmpty()
        return JsonObject(base + extra)
    }

    private fun encodeBase(request: LlmRequest, maxCompletionTokens: Boolean, options: ChatOptions, stream: Boolean): JsonObject = buildJsonObject {
        put("model", request.modelId)
        put("messages", buildJsonArray {
            if (request.system.isNotEmpty()) add(buildJsonObject { put("role", "system"); put("content", request.system.joinToString("\n\n")) })
            for (m in request.messages) when (m.role) {
                Role.ASSISTANT -> add(buildJsonObject {
                    put("role", "assistant")
                    val text = m.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
                    put("content", if (text.isBlank() && m.toolUses().isNotEmpty()) kotlinx.serialization.json.JsonNull else JsonPrimitive(text))
                    if (m.toolUses().isNotEmpty()) put("tool_calls", JsonArray(m.toolUses().map { u ->
                        buildJsonObject {
                            put("id", u.id); put("type", "function")
                            put("function", buildJsonObject { put("name", u.name); put("arguments", u.input.toString()) })
                        }
                    }))
                })
                Role.USER -> {
                    // Los resultados de herramienta van como mensajes «tool» justo tras la llamada.
                    m.content.filterIsInstance<ContentBlock.ToolResult>().forEach { r ->
                        add(buildJsonObject {
                            put("role", "tool"); put("tool_call_id", r.toolUseId)
                            put("content", (if (r.isError) "ERROR: " else "") + r.content.toString())
                        })
                    }
                    val parts = m.content.filter { it !is ContentBlock.ToolResult && it !is ContentBlock.Reasoning }.map { block ->
                        when (block) {
                            is ContentBlock.Text -> buildJsonObject { put("type", "text"); put("text", block.text) }
                            is ContentBlock.Image -> buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject { put("url", "data:image/${block.format};base64," + Base64.getEncoder().encodeToString(block.bytes)) })
                            }
                            is ContentBlock.Document -> buildJsonObject {
                                put("type", "text")
                                put("text", if (block.format in setOf("txt", "md", "csv", "html")) "[Documento ${block.name}]\n" + block.bytes.toString(Charsets.UTF_8)
                                    else "[Documento ${block.name} (${block.format}): este proveedor no lo recibe como archivo; pide al usuario el texto si lo necesitas.]")
                            }
                            else -> buildJsonObject { put("type", "text"); put("text", "") }
                        }
                    }
                    if (parts.isNotEmpty()) add(buildJsonObject {
                        put("role", "user")
                        if (parts.all { it["type"]?.jsonPrimitive?.content == "text" }) put("content", parts.joinToString("\n") { it["text"]!!.jsonPrimitive.content })
                        else put("content", JsonArray(parts))
                    })
                }
            }
        })
        if (request.tools.isNotEmpty() && options.sendTools) put("tools", JsonArray(request.tools.map { t ->
            buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject { put("name", t.name); put("description", t.description); put("parameters", t.inputSchema) })
            }
        }))
        put(if (maxCompletionTokens) "max_completion_tokens" else "max_tokens", request.maxTokens)
        request.temperature?.let { put("temperature", it) }
        // Mismo criterio que la Responses API: pensamiento desactivado y esfuerzo se excluyen (400 en ModelArk).
        if (options.thinking != null) put("thinking", buildJsonObject { put("type", options.thinking) })
        if (options.reasoningEffort != null && options.thinking != "disabled") put("reasoning_effort", options.reasoningEffort)
        if (stream) { put("stream", true); put("stream_options", buildJsonObject { put("include_usage", true) }) }
    }

    fun decode(json: JsonObject): LlmResponse {
        val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LlmException(LlmException.Kind.PROTOCOL, "El proveedor no devolvió ninguna respuesta.")
        val message = choice["message"]?.jsonObject ?: JsonObject(emptyMap())
        val blocks = mutableListOf<ContentBlock>()
        (message["content"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { blocks += ContentBlock.Text(it) }
        (message["tool_calls"] as? JsonArray)?.forEach { call ->
            val o = call.jsonObject
            val fn = o["function"]?.jsonObject ?: return@forEach
            val args = fn["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val input = runCatching { Json.parseToJsonElement(args.ifBlank { "{}" }).jsonObject }.getOrElse { JsonObject(emptyMap()) }
            blocks += ContentBlock.ToolUse(o["id"]?.jsonPrimitive?.contentOrNull ?: "call_${blocks.size}", fn["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), input)
        }
        val stop = when (choice["finish_reason"]?.jsonPrimitive?.contentOrNull) {
            "stop" -> StopReason.END_TURN
            "tool_calls", "function_call" -> StopReason.TOOL_USE
            "length" -> StopReason.MAX_TOKENS
            "content_filter" -> StopReason.CONTENT_FILTERED
            null -> if (blocks.any { it is ContentBlock.ToolUse }) StopReason.TOOL_USE else StopReason.END_TURN
            else -> StopReason.OTHER
        }.let { if (it == StopReason.END_TURN && blocks.any { b -> b is ContentBlock.ToolUse }) StopReason.TOOL_USE else it }
        val usage = json["usage"]?.jsonObject
        return LlmResponse(LlmMessage(Role.ASSISTANT, blocks), stop,
            Usage(usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0, usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0))
    }
}

/** Errores HTTP de proveedores de LLM traducidos a mensajes sin secretos. */
internal object LlmHttpErrors {
    fun map(code: Int, body: String, who: String): LlmException {
        val detail = runCatching {
            val e = Json.parseToJsonElement(body).jsonObject["error"]
            (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: (e as? JsonPrimitive)?.contentOrNull
        }.getOrNull()?.take(200)
        val errCode = runCatching { ((Json.parseToJsonElement(body).jsonObject["error"] as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull }.getOrNull()?.take(80)
        val kind = when (code) {
            401, 403 -> LlmException.Kind.AUTH
            402 -> LlmException.Kind.BUDGET
            404, 422 -> LlmException.Kind.MODEL_NOT_ALLOWED
            429 -> LlmException.Kind.THROTTLED
            400, 413 -> LlmException.Kind.BAD_REQUEST
            else -> LlmException.Kind.UPSTREAM
        }
        val msg = when (kind) {
            LlmException.Kind.AUTH -> "La clave de API no es válida o no tiene permiso${detail?.let { ": $it" } ?: "."}"
            LlmException.Kind.BUDGET -> "La cuenta de $who no tiene saldo o crédito${detail?.let { ": $it" } ?: "."}"
            LlmException.Kind.THROTTLED -> "Límite de peticiones de $who; reintenta en unos segundos."
            // «Sin activar» no es «no existe»: la clave es válida y falta activar el modelo en la consola del proveedor.
            LlmException.Kind.MODEL_NOT_ALLOWED -> if (errCode == "ModelNotOpen")
                "El modelo no está activado en tu cuenta de $who${detail?.let { ": $it" } ?: "."} Ajustes → Cerebros → $who → Probar conexión → Resolver problema."
                else "El modelo no existe o no está disponible en $who${detail?.let { ": $it" } ?: "."}"
            else -> detail ?: "Error de $who (HTTP $code)."
        }
        return LlmException(kind, msg, code, errCode)
    }
}
