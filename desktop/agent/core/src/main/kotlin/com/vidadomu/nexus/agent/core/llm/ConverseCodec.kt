package com.vidadomu.nexus.agent.core.llm

import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

/**
 * Traducción entre los tipos propios y el formato de la API Converse de Amazon Bedrock
 * (https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html).
 * Las imágenes viajan en base64 dentro de `source.bytes`; el proxy las decodifica antes de llamar
 * a Bedrock.
 */
object ConverseCodec {

    fun encodeRequest(request: LlmRequest): JsonObject = buildJsonObject {
        put("modelId", request.modelId)
        if (request.system.isNotEmpty()) {
            put("system", buildJsonArray { request.system.forEach { add(buildJsonObject { put("text", it) }) } })
        }
        put("messages", buildJsonArray { request.messages.forEach { add(encodeMessage(it)) } })
        put("inferenceConfig", buildJsonObject {
            put("maxTokens", request.maxTokens)
            request.temperature?.let { put("temperature", it) }
        })
        if (request.tools.isNotEmpty()) {
            put("toolConfig", buildJsonObject {
                put("tools", buildJsonArray {
                    request.tools.forEach { spec ->
                        add(buildJsonObject {
                            put("toolSpec", buildJsonObject {
                                put("name", spec.name)
                                put("description", spec.description)
                                put("inputSchema", buildJsonObject { put("json", spec.inputSchema) })
                            })
                        })
                    }
                })
            })
        }
    }

    fun encodeMessage(message: LlmMessage): JsonObject = buildJsonObject {
        put("role", if (message.role == Role.USER) "user" else "assistant")
        put("content", buildJsonArray { message.content.forEach { add(encodeBlock(it)) } })
    }

    private fun encodeBlock(block: ContentBlock): JsonObject = when (block) {
        is ContentBlock.Text -> buildJsonObject { put("text", block.text) }
        is ContentBlock.ToolUse -> buildJsonObject {
            put("toolUse", buildJsonObject {
                put("toolUseId", block.id)
                put("name", block.name)
                put("input", block.input)
            })
        }
        is ContentBlock.ToolResult -> buildJsonObject {
            put("toolResult", buildJsonObject {
                put("toolUseId", block.toolUseId)
                // Converse espera un documento JSON objeto; envolvemos listas y primitivos.
                val doc = block.content as? JsonObject ?: buildJsonObject { put("result", block.content) }
                put("content", buildJsonArray { add(buildJsonObject { put("json", doc) }) })
                if (block.isError) put("status", "error")
            })
        }
        is ContentBlock.Image -> buildJsonObject {
            put("image", buildJsonObject {
                put("format", block.format)
                put("source", buildJsonObject { put("bytes", Base64.getEncoder().encodeToString(block.bytes)) })
            })
        }
    }

    fun decodeResponse(json: JsonObject): LlmResponse {
        val message = json["output"]?.jsonObject?.get("message")?.jsonObject
            ?: throw LlmException(LlmException.Kind.PROTOCOL, "Respuesta del modelo sin mensaje.")
        val usage = json["usage"]?.jsonObject
        return LlmResponse(
            message = decodeMessage(message),
            stopReason = decodeStop(json["stopReason"]?.jsonPrimitive?.contentOrNull),
            usage = Usage(
                inputTokens = usage?.get("inputTokens")?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = usage?.get("outputTokens")?.jsonPrimitive?.intOrNull ?: 0,
            ),
        )
    }

    fun decodeMessage(json: JsonObject): LlmMessage {
        val role = if (json["role"]?.jsonPrimitive?.contentOrNull == "user") Role.USER else Role.ASSISTANT
        val blocks = (json["content"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            when {
                "text" in o -> ContentBlock.Text(o["text"]!!.jsonPrimitive.content)
                "toolUse" in o -> {
                    val t = o["toolUse"]!!.jsonObject
                    ContentBlock.ToolUse(
                        id = t["toolUseId"]!!.jsonPrimitive.content,
                        name = t["name"]!!.jsonPrimitive.content,
                        input = t["input"] as? JsonObject ?: JsonObject(emptyMap()),
                    )
                }
                "toolResult" in o -> {
                    val t = o["toolResult"]!!.jsonObject
                    val first = t["content"]?.jsonArray?.firstOrNull()?.jsonObject
                    ContentBlock.ToolResult(
                        toolUseId = t["toolUseId"]!!.jsonPrimitive.content,
                        content = first?.get("json") ?: first?.get("text") ?: JsonPrimitive(""),
                        isError = t["status"]?.jsonPrimitive?.contentOrNull == "error",
                    )
                }
                // Bloques de razonamiento u otros desconocidos: se ignoran sin romper el turno.
                else -> null
            }
        }
        return LlmMessage(role, blocks)
    }

    private fun decodeStop(value: String?): StopReason = when (value) {
        "end_turn" -> StopReason.END_TURN
        "tool_use" -> StopReason.TOOL_USE
        "max_tokens" -> StopReason.MAX_TOKENS
        "stop_sequence" -> StopReason.STOP_SEQUENCE
        "guardrail_intervened" -> StopReason.GUARDRAIL
        "content_filtered" -> StopReason.CONTENT_FILTERED
        else -> StopReason.OTHER
    }

}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
