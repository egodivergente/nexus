package com.vidadomu.nexus.agent.core.media

import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.net.JsonTemplate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Qué hace un modelo. Determina qué adjuntos admite y cómo lo presenta Nexus. */
@Serializable
enum class MediaCapability(val label: String, val video: Boolean, val needsImage: Boolean, val needsVideo: Boolean = false) {
    TEXT_TO_IMAGE("Texto a imagen", false, false),
    IMAGE_TO_IMAGE("Imagen a imagen", false, true),
    TEXT_TO_VIDEO("Texto a vídeo", true, false),
    IMAGE_TO_VIDEO("Imagen a vídeo", true, true),
    UPSCALE_IMAGE("Escalar imagen", false, true),
    UPSCALE_VIDEO("Escalar vídeo", true, false, needsVideo = true),
}

/** El trabajo termina en la propia respuesta (SYNC) o devuelve un id que hay que consultar (ASYNC). */
@Serializable
enum class MediaMode { SYNC, ASYNC }

/** Cómo llega un archivo local al proveedor: incrustado en la petición (data URI) o subido a un endpoint. */
@Serializable
enum class MediaUploadMode { DATA_URI, ENDPOINT }

/** Un parámetro editable de un modelo. [type]: enum, integer, float, boolean, text, aspect_ratio, resolution, duration, seed. */
@Serializable
data class MediaParam(
    val key: String,
    val label: String = "",
    val type: String = "text",
    val default: String = "",
    val options: List<String> = emptyList(),
    val min: Float? = null,
    val max: Float? = null,
    val required: Boolean = false,
    val advanced: Boolean = false,
    val description: String = "",
)

@Serializable
data class MediaModelConfig(
    val id: String,
    val name: String,
    val capability: MediaCapability,
    /** Valor de {{model}} en la plantilla; por defecto, el propio [id]. */
    val providerModelId: String = "",
    val createPath: String,
    val method: String = "POST",
    /** JSON de la petición con marcadores {{prompt}}, {{model}}, {{<parámetro>}}, {{image_url}}, {{image_urls}}, {{video_url}}. */
    val requestTemplate: String,
    val params: List<MediaParam> = emptyList(),
    val mode: MediaMode = MediaMode.ASYNC,
    /** ASYNC: dónde está el id de la tarea en la respuesta de creación. */
    val taskIdPath: String = "",
    /** ASYNC: ruta de consulta; admite {{task_id}}. */
    val pollPath: String = "",
    val pollMethod: String = "GET",
    /** Ruta del estado en la respuesta de consulta y los valores (sin distinguir mayúsculas) de éxito y de fallo. El resto = en curso. */
    val statusPath: String = "",
    val successValues: List<String> = listOf("succeeded", "completed", "success", "done"),
    val failureValues: List<String> = listOf("failed", "error", "canceled", "cancelled"),
    val progressPath: String = "",
    val errorPath: String = "",
    /** Rutas de la(s) URL(s) del resultado (en la respuesta de creación si es SYNC; en la de consulta si es ASYNC). Admite `*`. */
    val resultPaths: List<String> = emptyList(),
    val maxImages: Int = 1,
    val priceUsd: Double? = null,
    val priceUnit: String = "",
    val description: String = "",
    /** Fal queue uses provider-returned status/result URLs, persisted in an opaque task token. */
    val falQueue: Boolean = false,
) {
    val modelValue get() = providerModelId.ifBlank { id }
}

@Serializable
data class MediaProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val auth: BrainAuth = BrainAuth(),
    val extraHeaders: Map<String, String> = emptyMap(),
    val models: List<MediaModelConfig> = emptyList(),
    /** GET opcional para «Probar conexión» (p. ej. `/account` o `/models`). Vacío = no hay comprobación posible. */
    val healthPath: String = "",
    val uploadMode: MediaUploadMode = MediaUploadMode.DATA_URI,
    val uploadPath: String = "",
    val uploadFieldName: String = "file",
    val uploadUrlPath: String = "url",
    // --- Límites propios, todos configurables. 0 = sin límite. ---
    val pollIntervalSeconds: Int = 5,
    val maxWaitMinutes: Int = 30,
    val httpTimeoutSeconds: Int = 60,
    val maxUploadMb: Int = 20,
) {
    fun url(path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val p = path.trim()
        return if (p.isEmpty()) base else if (p.startsWith("http://") || p.startsWith("https://")) p else base + (if (p.startsWith("/")) p else "/$p")
    }

    /** Problemas que impiden guardar; mensajes para el usuario. */
    fun problems(): List<String> = buildList {
        if (!BrainProfile.ID.matches(id)) add("El identificador solo admite minúsculas, números y guiones (2–40).")
        if (name.isBlank() || name.length > 60) add("Escribe un nombre (hasta 60 caracteres).")
        if (!BrainProfile.isAllowedUrl(baseUrl)) add("La URL base debe empezar por https:// (o http://127.0.0.1 / localhost para un servidor local).")
        if (auth.mode in setOf(BrainAuthMode.HEADER, BrainAuthMode.QUERY) && !Regex("^[A-Za-z0-9][A-Za-z0-9_\\-]{0,63}$").matches(auth.name)) add("Indica el nombre de la cabecera o del parámetro de la clave.")
        extraHeaders.forEach { (k, v) ->
            if (!Regex("^[A-Za-z0-9][A-Za-z0-9_\\-]{0,63}$").matches(k)) add("Cabecera «$k» no válida.")
            if (v.any { it == '\r' || it == '\n' }) add("El valor de la cabecera «$k» no puede tener saltos de línea.")
        }
        if (uploadMode == MediaUploadMode.ENDPOINT && uploadPath.isBlank()) add("Indica la ruta de subida de archivos.")
        listOf(pollIntervalSeconds, maxWaitMinutes, httpTimeoutSeconds, maxUploadMb).forEach { if (it < 0) add("Los límites no pueden ser negativos (0 = sin límite).") }
        if (models.isEmpty()) add("Añade al menos un modelo.")
        if (models.map { it.id }.toSet().size != models.size) add("Hay modelos con el mismo identificador.")
        models.forEach { m ->
            if (m.falQueue && (baseUrl != "https://queue.fal.run" || m.mode != MediaMode.ASYNC)) add("Fal requiere cola HTTPS y modo asíncrono.")
            addAll(modelProblems(m).map { "${m.name.ifBlank { m.id }}: $it" }) }
    }

    private fun modelProblems(m: MediaModelConfig): List<String> = buildList {
        if (!Regex("^[A-Za-z0-9][A-Za-z0-9._\\-/]{0,79}$").matches(m.id) || ".." in m.id) add("identificador de modelo no válido (letras, números, punto, guion y barra).")
        if (m.name.isBlank()) add("falta el nombre.")
        if (m.createPath.isBlank()) add("falta la ruta de creación.")
        if (m.method.uppercase() !in setOf("POST", "PUT", "GET")) add("el método debe ser POST, PUT o GET.")
        val template = runCatching { Json.parseToJsonElement(m.requestTemplate) }.getOrNull()
        if (template !is JsonObject) add("la petición debe ser un objeto JSON válido.")
        else {
            val known = MediaEngine.BUILTIN_VARS + m.params.map { it.key }
            JsonTemplate.placeholders(template).filter { it !in known }.takeIf { it.isNotEmpty() }
                ?.let { add("la petición usa marcadores sin valor: ${it.joinToString { p -> "{{$p}}" }}. Añade el parámetro o quita el marcador.") }
        }
        if (m.params.map { it.key }.toSet().size != m.params.size) add("hay parámetros repetidos.")
        m.params.forEach { p -> if (!Regex("^[A-Za-z0-9_\\-]{1,40}$").matches(p.key)) add("clave de parámetro «${p.key}» no válida.") }
        if (m.resultPaths.isEmpty()) add("indica al menos una ruta de la URL del resultado.")
        if (m.mode == MediaMode.ASYNC) {
            if (m.taskIdPath.isBlank()) add("falta la ruta del id de la tarea en la respuesta.")
            if (m.pollPath.isBlank()) add("falta la ruta de consulta del estado.")
            if (m.statusPath.isBlank()) add("falta la ruta del estado en la consulta.")
            if (m.successValues.isEmpty()) add("indica al menos un valor de estado que signifique éxito.")
        }
        if (m.priceUsd != null && !(m.priceUsd.isFinite() && m.priceUsd >= 0.0)) add("el precio debe ser un número ≥ 0.")
    }

    companion object {
        fun parse(json: String): MediaProviderConfig = Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), json)
    }
}
