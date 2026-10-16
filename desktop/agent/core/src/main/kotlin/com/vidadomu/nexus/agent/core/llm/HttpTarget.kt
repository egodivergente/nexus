package com.vidadomu.nexus.agent.core.llm

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Destino HTTP de un proveedor configurable: URL, cómo viaja la clave y cabeceras extra. La clave se pide en
 * cada petición ([apiKey]) para no guardarla en memoria más de lo necesario y nunca se registra.
 */
class HttpTarget(
    val url: String,
    val auth: BrainAuth = BrainAuth(),
    val apiKey: () -> String = { "" },
    val headers: Map<String, String> = emptyMap(),
) {
    fun request(): Request.Builder {
        var http = try { url.toHttpUrl() } catch (e: IllegalArgumentException) {
            throw LlmException(LlmException.Kind.BAD_REQUEST, "La URL del proveedor no es válida.")
        }
        val key = apiKey().trim()
        if (key.any { it == '\r' || it == '\n' }) throw LlmException(LlmException.Kind.AUTH, "La clave de API contiene saltos de línea.")
        if (auth.mode == BrainAuthMode.QUERY && key.isNotEmpty()) http = http.newBuilder().setQueryParameter(auth.name, key).build()
        return Request.Builder().url(http).apply {
            when (auth.mode) {
                BrainAuthMode.BEARER -> if (key.isNotEmpty()) header("Authorization", "Bearer $key")
                BrainAuthMode.HEADER -> if (key.isNotEmpty()) header(auth.name, auth.prefix + key)
                BrainAuthMode.QUERY, BrainAuthMode.NONE -> Unit
            }
            headers.forEach { (k, v) -> header(k, v) }
        }
    }

    /** Misma ruta cambiando el final (p. ej. de /chat/completions a /models) para descubrir modelos. */
    fun sibling(path: String): HttpTarget {
        val base = url.substringBefore('?').trimEnd('/')
        val root = listOf("/chat/completions", "/responses").firstOrNull { base.endsWith(it) }?.let { base.removeSuffix(it) } ?: base
        return HttpTarget(root + "/" + path.trimStart('/'), auth, apiKey, headers)
    }
}
