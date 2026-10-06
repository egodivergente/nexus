package com.vidadomu.nexus.provider.atlas

import kotlinx.serialization.json.*

/** OpenAI-compatible Atlas chat protocol, separate from asynchronous media predictions. */
object AtlasChatProtocol {
    fun userMessages(prompt:String):JsonArray = buildJsonArray {
        add(buildJsonObject { put("role","user");put("content",prompt) })
    }
    fun responseText(root:JsonObject):String? {
        val choice=(root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val message=choice["message"] as? JsonObject ?: return null
        val content=message["content"]
        val text=when(content) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> content.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("\n")
            else -> null
        }
        return text?.takeIf { it.isNotBlank() } ?: (message["refusal"] as? JsonPrimitive)?.contentOrNull
    }
}
