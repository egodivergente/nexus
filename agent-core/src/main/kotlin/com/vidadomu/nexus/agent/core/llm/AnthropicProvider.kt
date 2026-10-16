package com.vidadomu.nexus.agent.core.llm

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

/** Claude por la API de Anthropic (Messages) con la clave del usuario: herramientas, imágenes y PDF nativos. */
class AnthropicProvider(
    private val apiKey: () -> String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val client: OkHttpClient = BedrockProxyProvider.defaultClient(),
) : LlmProvider {
    override val id: String = "anthropic"

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val body = AnthropicCodec.encode(request).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val http = Request.Builder().url(baseUrl.trimEnd('/') + "/v1/messages").post(body)
            .header("x-api-key", apiKey().trim()).header("anthropic-version", "2023-06-01").build()
        try {
            client.newCall(http).awaitBody().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmHttpErrors.map(resp.code, text, "Anthropic")
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "Anthropic devolvió una respuesta no válida.") }
                AnthropicCodec.decode(json)
            }
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "No se pudo contactar con Anthropic (${e.javaClass.simpleName}).")
        }
    }
}

object AnthropicCodec {
    private val b64: Base64.Encoder = Base64.getEncoder()

    fun encode(request: LlmRequest): JsonObject = buildJsonObject {
        put("model", request.modelId)
        put("max_tokens", request.maxTokens)
        if (request.system.isNotEmpty()) put("system", request.system.joinToString("\n\n"))
        request.temperature?.let { put("temperature", it) }
        put("messages", buildJsonArray {
            for (m in request.messages) {
                // El contexto firmado de Bedrock no es transferible a otro proveedor.
                val blocks = m.content.filter { it !is ContentBlock.Reasoning }.map { block(it) }
                if (blocks.isEmpty()) continue
                add(buildJsonObject { put("role", if (m.role == Role.USER) "user" else "assistant"); put("content", JsonArray(blocks)) })
            }
        })
        if (request.tools.isNotEmpty()) put("tools", JsonArray(request.tools.map { t ->
            buildJsonObject { put("name", t.name); put("description", t.description); put("input_schema", t.inputSchema) }
        }))
    }

    private fun block(b: ContentBlock): JsonObject = when (b) {
        is ContentBlock.Text -> buildJsonObject { put("type", "text"); put("text", b.text.ifEmpty { "…" }) }
        is ContentBlock.Image -> buildJsonObject {
            put("type", "image")
            put("source", buildJsonObject { put("type", "base64"); put("media_type", "image/${if (b.format == "jpg") "jpeg" else b.format}"); put("data", b64.encodeToString(b.bytes)) })
        }
        is ContentBlock.Document -> if (b.format == "pdf") buildJsonObject {
            put("type", "document")
            put("source", buildJsonObject { put("type", "base64"); put("media_type", "application/pdf"); put("data", b64.encodeToString(b.bytes)) })
        } else buildJsonObject { put("type", "text"); put("text", "[Documento ${b.name}]\n" + b.bytes.toString(Charsets.UTF_8)) }
        is ContentBlock.ToolUse -> buildJsonObject { put("type", "tool_use"); put("id", b.id); put("name", b.name); put("input", b.input) }
        is ContentBlock.ToolResult -> buildJsonObject {
            put("type", "tool_result"); put("tool_use_id", b.toolUseId); put("content", b.content.toString())
            if (b.isError) put("is_error", true)
        }
        is ContentBlock.Reasoning -> buildJsonObject { put("type", "text"); put("text", "…") }
    }

    fun decode(json: JsonObject): LlmResponse {
        val blocks = (json["content"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val o = raw.jsonObject
            when (o["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> o["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { ContentBlock.Text(it) }
                "tool_use" -> ContentBlock.ToolUse(o["id"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content, o["input"]?.jsonObject ?: JsonObject(emptyMap()))
                else -> null
            }
        }
        val stop = when (json["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "end_turn" -> StopReason.END_TURN
            "tool_use" -> StopReason.TOOL_USE
            "max_tokens" -> StopReason.MAX_TOKENS
            "stop_sequence" -> StopReason.STOP_SEQUENCE
            "refusal" -> StopReason.CONTENT_FILTERED
            else -> StopReason.OTHER
        }
        val usage = json["usage"]?.jsonObject
        return LlmResponse(LlmMessage(Role.ASSISTANT, blocks), stop,
            Usage(usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0, usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0))
    }
}
