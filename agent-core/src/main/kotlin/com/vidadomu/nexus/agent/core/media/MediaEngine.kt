package com.vidadomu.nexus.agent.core.media

import com.vidadomu.nexus.agent.core.net.JsonPaths
import com.vidadomu.nexus.agent.core.net.JsonTemplate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Lo que se le pide a un modelo: texto, parámetros elegidos y URLs de los adjuntos (ya subidos o incrustados). */
data class MediaInput(
    val prompt: String,
    val params: Map<String, String> = emptyMap(),
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
)

/** Petición HTTP sin autenticación: la añade quien la envía (la clave nunca pasa por aquí). */
data class MediaHttpCall(val method: String, val url: String, val body: JsonObject?)

sealed interface CreateOutcome {
    data class Done(val urls: List<String>) : CreateOutcome
    data class Async(val taskId: String) : CreateOutcome
    data class Failed(val message: String) : CreateOutcome
}

sealed interface PollOutcome {
    data class Running(val progress: Float?) : PollOutcome
    data class Done(val urls: List<String>) : PollOutcome
    data class Failed(val message: String) : PollOutcome
}

/**
 * Construye las peticiones y lee las respuestas de un proveedor multimedia definido por configuración.
 * Es deliberadamente simple: JSON in, JSON out, rutas para extraer. Un protocolo que necesite firmas,
 * subida en varios pasos o websockets requiere un adaptador propio; esto no lo simula.
 */
object MediaEngine {
    val BUILTIN_VARS = setOf("model", "prompt", "image_url", "image_urls", "video_url", "task_id")

    /** Problemas del encargo concreto (antes de gastar nada): parámetros obligatorios y adjuntos que faltan. */
    fun inputProblems(m: MediaModelConfig, input: MediaInput): List<String> = buildList {
        if (input.prompt.isBlank() && !m.capability.needsImage && !m.capability.needsVideo) add("Escribe una descripción.")
        if (m.capability.needsImage && input.imageUrls.isEmpty()) add("Este modelo necesita una imagen.")
        if (m.capability.needsVideo && input.videoUrl == null) add("Este modelo necesita un vídeo.")
        if (input.imageUrls.size > m.maxImages.coerceAtLeast(if (m.capability.needsImage) 1 else 0)) add("Este modelo admite como máximo ${m.maxImages} imagen(es).")
        m.params.filter { it.required }.forEach { p -> if ((input.params[p.key] ?: p.default).isBlank()) add("Falta «${p.label.ifBlank { p.key }}».") }
        m.params.filter { it.options.isNotEmpty() && it.type == "enum" }.forEach { p ->
            val v = input.params[p.key]
            if (!v.isNullOrBlank() && v !in p.options) add("«${p.label.ifBlank { p.key }}» debe ser una de: ${p.options.joinToString()}.")
        }
    }

    /** Valor tipado de cada parámetro: números como números, booleanos como booleanos; vacío o semilla -1 = se omite. */
    fun typedParams(m: MediaModelConfig, given: Map<String, String>): Map<String, JsonElement?> =
        m.params.associate { p ->
            val raw = (given[p.key] ?: p.default).trim()
            p.key to when {
                raw.isEmpty() -> null
                p.type == "seed" -> raw.toLongOrNull()?.takeIf { it >= 0 }?.let { JsonPrimitive(it) }
                p.type == "integer" || p.type == "duration" -> raw.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
                p.type == "float" -> raw.replace(',', '.').toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
                p.type == "boolean" -> raw.lowercase().toBooleanStrictOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
                else -> JsonPrimitive(raw)
            }
        }

    fun buildCreate(p: MediaProviderConfig, m: MediaModelConfig, input: MediaInput): MediaHttpCall {
        val vars = typedParams(m, input.params).toMutableMap<String, JsonElement?>()
        vars["model"] = JsonPrimitive(m.modelValue)
        vars["prompt"] = JsonPrimitive(input.prompt)
        vars["image_url"] = input.imageUrls.firstOrNull()?.let { JsonPrimitive(it) }
        vars["image_urls"] = input.imageUrls.takeIf { it.isNotEmpty() }?.let { JsonArray(it.map(::JsonPrimitive)) }
        vars["video_url"] = input.videoUrl?.let { JsonPrimitive(it) }
        val body = JsonTemplate.render(JsonTemplate.parse(m.requestTemplate), vars) as? JsonObject
            ?: throw IllegalArgumentException("La petición debe ser un objeto JSON.")
        return MediaHttpCall(m.method.uppercase(), p.url(JsonTemplate.renderText(m.createPath, vars)), body.takeIf { m.method.uppercase() != "GET" })
    }

    fun parseCreate(m: MediaModelConfig, body: JsonElement): CreateOutcome {
        errorMessage(m, body)?.let { return CreateOutcome.Failed(it) }
        val urls = results(m, body)
        if (m.mode == MediaMode.SYNC) return if (urls.isNotEmpty()) CreateOutcome.Done(urls)
            else CreateOutcome.Failed("La respuesta no contiene la URL del resultado (rutas probadas: ${m.resultPaths.joinToString()}).")
        JsonPaths.string(body, m.taskIdPath)?.let { return CreateOutcome.Async(it) }
        // Algunas APIs asíncronas devuelven el resultado enseguida si ya está hecho.
        if (urls.isNotEmpty()) return CreateOutcome.Done(urls)
        return CreateOutcome.Failed("No encuentro el id de la tarea en la respuesta (ruta «${m.taskIdPath}»).")
    }

    fun buildPoll(p: MediaProviderConfig, m: MediaModelConfig, taskId: String): MediaHttpCall {
        val vars = mapOf<String, JsonElement?>("task_id" to JsonPrimitive(taskId), "model" to JsonPrimitive(m.modelValue))
        return MediaHttpCall(m.pollMethod.uppercase(), p.url(JsonTemplate.renderText(m.pollPath, vars)), null)
    }

    fun parsePoll(m: MediaModelConfig, body: JsonElement): PollOutcome {
        val status = JsonPaths.string(body, m.statusPath)?.trim()?.lowercase()
        val urls = results(m, body)
        return when {
            status != null && m.failureValues.any { it.equals(status, true) } ->
                PollOutcome.Failed(errorMessage(m, body) ?: "El proveedor indicó un fallo (estado «$status»).")
            status != null && m.successValues.any { it.equals(status, true) } ->
                if (urls.isNotEmpty()) PollOutcome.Done(urls)
                else PollOutcome.Failed("La tarea terminó pero no encuentro la URL del resultado (rutas probadas: ${m.resultPaths.joinToString()}).")
            // Sin estado configurado, la presencia del resultado es la señal de fin.
            m.statusPath.isBlank() && urls.isNotEmpty() -> PollOutcome.Done(urls)
            else -> PollOutcome.Running(progress(m, body))
        }
    }

    private fun results(m: MediaModelConfig, body: JsonElement) =
        m.resultPaths.flatMap { JsonPaths.strings(body, it) }.filter { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("data:") }.distinct()

    private fun errorMessage(m: MediaModelConfig, body: JsonElement): String? =
        m.errorPath.takeIf { it.isNotBlank() }?.let { JsonPaths.string(body, it) }?.take(300)

    /** Progreso 0..1; admite 0–1 y 0–100. */
    private fun progress(m: MediaModelConfig, body: JsonElement): Float? =
        m.progressPath.takeIf { it.isNotBlank() }?.let { JsonPaths.number(body, it) }?.let { v -> (if (v > 1.0) v / 100.0 else v).coerceIn(0.0, 1.0).toFloat() }
}
