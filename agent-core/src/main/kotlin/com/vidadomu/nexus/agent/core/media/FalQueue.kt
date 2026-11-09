package com.vidadomu.nexus.agent.core.media

import java.net.URI
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Only provider-returned, same-origin URLs. Never derive queue routes from a model alias. */
object FalQueue {
    @Serializable
    private data class Ticket(val id: String, val status: String, val result: String)
    private const val PREFIX = "falq:"

    private fun checked(url: String): String {
        val u = URI(url)
        require(u.scheme == "https" && u.host == "queue.fal.run" && u.port in listOf(-1, 443) &&
            u.userInfo == null && u.fragment == null && u.query == null &&
            u.rawPath == u.path && !u.path.contains("..") && u.path.startsWith("/")) { "URL de cola Fal no válida" }
        return url
    }
    fun ticket(body: JsonElement): String {
        val o = body.jsonObject
        fun value(k: String) = o[k]?.jsonPrimitive?.contentOrNull ?: error("Falta $k en la cola Fal")
        val id = value("request_id")
        require(Regex("[A-Za-z0-9_-]{1,200}").matches(id)) { "ID de cola Fal no válido" }
        val status = checked(value("status_url")); val result = checked(value("response_url"))
        require(URI(status).path.endsWith("/requests/$id/status") && URI(result).path.endsWith("/requests/$id"))
        require(status.removeSuffix("/status") == result)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(Ticket(id, status, result)).toByteArray(Charsets.UTF_8))
    }
    private fun read(token: String): Ticket {
        require(token.startsWith(PREFIX) && token.length < 8192)
        val t = Json.decodeFromString<Ticket>(String(Base64.getUrlDecoder().decode(token.removePrefix(PREFIX)), Charsets.UTF_8))
        // Revalidate persisted jobs as well as incoming responses.
        ticket(buildJsonObject { put("request_id", t.id); put("status_url", t.status); put("response_url", t.result) })
        return t
    }
    fun poll(token: String) = MediaHttpCall("GET", read(token).status, null)
    fun result(token: String) = MediaHttpCall("GET", read(token).result, null)
    fun completed(body: JsonElement) = body.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "COMPLETED"
    fun error(body: JsonElement) = body.jsonObject["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
