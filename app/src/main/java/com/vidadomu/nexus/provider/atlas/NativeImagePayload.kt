package com.vidadomu.nexus.provider.atlas

import kotlinx.serialization.json.*

object NativeImagePayload {
    fun encode(urls: List<String>, structured: Boolean): JsonArray = buildJsonArray {
        urls.forEach { url ->
            if(structured) add(buildJsonObject { put("url",url); put("type","image") })
            else add(JsonPrimitive(url))
        }
    }
}
