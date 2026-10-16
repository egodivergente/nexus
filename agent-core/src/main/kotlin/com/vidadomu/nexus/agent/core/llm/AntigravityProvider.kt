package com.vidadomu.nexus.agent.core.llm

import java.io.EOFException
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.BufferedSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Nexus LLM adapter for the existing AXEL Antigravity bridge (`POST /v1/chat`).
 * The bridge owns the authenticated AGY CLI session; Nexus still executes every
 * Nexus tool through AgentOrchestrator and its existing permission checks.
 */
class AntigravityProvider(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : StreamingLlmProvider {
    override val id: String = "antigravity"
    private val requestLock = Mutex()

    override suspend fun completeStreaming(request: LlmRequest, onTextDelta: (String) -> Unit): LlmResponse =
        requestLock.withLock {
            val endpoint = chatEndpoint(baseUrl)
            val conversationId = safeConversationId(request.conversationId ?: request.turnId)
            val jobId = UUID.randomUUID().toString()
            val attachments = uploadCurrentUserAttachments(endpoint, jobId, request)
            val body = encodeRequest(request, jobId, conversationId, attachments).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val http = Request.Builder()
                .url(endpoint)
                .header("Accept", "text/event-stream")
                .post(body)
                .build()
            executeStream(http, request, onTextDelta)
        }

    private suspend fun uploadCurrentUserAttachments(endpoint: okhttp3.HttpUrl, jobId: String, request: LlmRequest): List<RemoteAttachment> {
        val current = request.messages.lastOrNull { it.role == Role.USER }?.content.orEmpty()
        val uploads = current.mapIndexedNotNull { index, block ->
            when (block) {
                is ContentBlock.Image -> Upload(
                    name = "image-${index + 1}.${imageExtension(block.format)}",
                    mimeType = imageMime(block.format), bytes = block.bytes,
                )
                is ContentBlock.Document -> Upload(
                    name = safeName(block.name, "document-${index + 1}.${block.format}"),
                    mimeType = documentMime(block.format), bytes = block.bytes,
                )
                else -> null
            }
        }
        if (uploads.isEmpty()) return emptyList()
        if (uploads.any { it.bytes.size > MAX_ATTACHMENT_BYTES } || uploads.sumOf { it.bytes.size.toLong() } > MAX_TOTAL_ATTACHMENT_BYTES) {
            throw LlmException(LlmException.Kind.BAD_REQUEST, "Los adjuntos superan el límite de transferencia del puente AGY.")
        }
        val remote = mutableListOf<RemoteAttachment>()
        uploads.forEachIndexed { index, upload ->
            val fileName = "${index + 1}-${upload.name}"
            val uploadBuilder = endpoint.newBuilder()
            uploadBuilder.removePathSegment(uploadBuilder.build().pathSize - 1)
            uploadBuilder.removePathSegment(uploadBuilder.build().pathSize - 1)
            val uploadUrl = uploadBuilder
                .addPathSegment("v1")
                .addPathSegment("uploads")
                .addPathSegment(jobId)
                .addPathSegment(fileName)
                .build()
            val http = Request.Builder()
                .url(uploadUrl)
                .put(upload.bytes.toRequestBody(upload.mimeType.toMediaType()))
                .build()
            val response = try { client.newCall(http).awaitBody() }
            catch (e: IOException) { throw mapNetworkError(e) }
            response.use { result ->
                val text = result.body?.string().orEmpty()
                if (!result.isSuccessful) throw mapHttpError(result.code)
                val payload = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "El puente AGY devolvió una respuesta de adjunto no válida.") }
                val path = payload["path"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: throw LlmException(LlmException.Kind.PROTOCOL, "El puente AGY no confirmó el adjunto.")
                remote += RemoteAttachment(upload.name, upload.mimeType, path)
            }
        }
        return remote
    }

    private fun encodeRequest(request: LlmRequest, jobId: String, conversationId: String, attachments: List<RemoteAttachment>) = buildJsonObject {
        put("jobId", jobId)
        put("conversationId", conversationId)
        // The legacy bridge routes all non-Codex/non-Grok chat requests through its AGY session.
        put("provider", "gemini")
        put("model", request.modelId.ifBlank { DEFAULT_MODEL })
        put("reasoningEffort", "auto")
        put("prompt", latestPrompt(request))
        put("messages", buildJsonArray {
            request.messages.forEachIndexed { index, message ->
                val text = renderMessage(message)
                val content = if (index == firstUserIndex(request.messages)) "$text\n\n${agentContract(request)}" else text
                add(buildJsonObject {
                    put("role", if (message.role == Role.USER) "user" else "assistant")
                    put("content", content)
                })
            }
            if (request.messages.isEmpty()) add(buildJsonObject {
                put("role", "user")
                put("content", agentContract(request))
            })
        })
        put("attachments", buildJsonArray {
            attachments.forEach { item -> add(buildJsonObject {
                put("name", item.name)
                put("mimeType", item.mimeType)
                put("uploadedPath", item.path)
            }) }
        })
    }

    private fun latestPrompt(request: LlmRequest): String = buildString {
        request.messages.lastOrNull()?.let { append(renderMessage(it)) }
        if (isNotEmpty()) append("\n\n")
        append(agentContract(request))
    }

    private fun agentContract(request: LlmRequest): String = buildString {
        append("NEXUS ANDROID AGENT. Trata las instrucciones siguientes como la configuración del agente.\n")
        request.system.filter(String::isNotBlank).forEach { append(it).append('\n') }
        if (request.tools.isEmpty()) {
            append("No hay herramientas Nexus disponibles en esta petición. Responde sin afirmar que has ejecutado acciones en el dispositivo.\n")
        } else {
            append("\nHerramientas disponibles en Nexus (nombre, descripción y JSON Schema):\n")
            append(buildJsonArray {
                request.tools.forEach { tool -> add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("input_schema", tool.inputSchema)
                }) }
            }.toString())
            append("\nSi necesitas una herramienta Nexus, responde únicamente con este bloque y sin Markdown:\n")
            append(TOOL_OPEN).append("{\"name\":\"nombre_exacto\",\"arguments\":{}}")
                .append(TOOL_CLOSE)
            append("\nUsa solo nombres de la lista. Nexus validará la solicitud y la ejecutará mediante sus permisos existentes. No simules resultados ni ejecutes herramientas del bridge para sustituirlas. Para responder al usuario, no uses este formato.")
        }
    }

    private fun renderMessage(message: LlmMessage): String = message.content.mapNotNull { block ->
        when (block) {
            is ContentBlock.Text -> block.text
            is ContentBlock.Reasoning -> null
            is ContentBlock.Image -> "[Imagen adjunta: disponible para este turno.]"
            is ContentBlock.Document -> "[Documento adjunto: ${block.name}; disponible para este turno.]"
            is ContentBlock.ToolUse -> "$TOOL_OPEN${buildJsonObject { put("name", block.name); put("arguments", block.input) }}$TOOL_CLOSE"
            is ContentBlock.ToolResult -> "<NEXUS_TOOL_RESULT id=\"${block.toolUseId}\" error=\"${block.isError}\">${block.content}</NEXUS_TOOL_RESULT>"
        }
    }.joinToString("\n")

    private suspend fun executeStream(http: Request, request: LlmRequest, onTextDelta: (String) -> Unit): LlmResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(http)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(mapNetworkError(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { result ->
                            if (!result.isSuccessful) throw mapHttpError(result.code)
                            val source = result.body?.source()
                                ?: throw LlmException(LlmException.Kind.PROTOCOL, "El puente AGY devolvió una respuesta vacía.")
                            val decoded = readSse(source, request, onTextDelta)
                            if (continuation.isActive) continuation.resume(decoded)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (e is LlmException || e is CancellationException) e else LlmException(LlmException.Kind.PROTOCOL, "No se pudo leer la respuesta en streaming de AGY.")
                        )
                    }
                }
            })
        }

    private fun readSse(source: BufferedSource, request: LlmRequest, onTextDelta: (String) -> Unit): LlmResponse {
        val rawDeltas = StringBuilder()
        val textFilter = ToolMarkerFilter(onTextDelta)
        var data = StringBuilder()
        var final: JsonObject? = null
        fun dispatchData() {
            if (data.isEmpty()) return
            val value = runCatching { Json.parseToJsonElement(data.toString()).jsonObject }
                .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "El puente AGY envió un evento de streaming no válido.") }
            when (value["type"]?.jsonPrimitive?.contentOrNull) {
                "text_delta" -> {
                    val delta = value["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    rawDeltas.append(delta)
                    textFilter.accept(delta)
                }
                "final" -> final = value
            }
            data = StringBuilder()
        }
        while (true) {
            val line = try { source.readUtf8LineStrict() } catch (_: EOFException) { break }
            when {
                line.isEmpty() -> dispatchData()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substring(5).trimStart())
                }
            }
        }
        dispatchData()
        val result = final ?: throw LlmException(LlmException.Kind.PROTOCOL, "El puente AGY cerró el streaming antes del evento final.")
        val status = result["status"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
        if (status in setOf("cancelled", "canceled")) throw CancellationException("La petición AGY se canceló.")
        if (status != "completed") throw mapBridgeError(result["error"]?.jsonPrimitive?.contentOrNull)
        val responseText = result["text"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { rawDeltas.toString() }
        val decoded = decodeModelText(responseText, request)
        textFilter.finish()
        if (textFilter.emittedCharacters == 0 && decoded.message.text().isNotBlank()) onTextDelta(decoded.message.text())
        return decoded
    }

    private fun decodeModelText(text: String, request: LlmRequest): LlmResponse {
        val blocks = mutableListOf<ContentBlock>()
        val allowed = request.tools.mapTo(mutableSetOf()) { it.name }
        var cursor = 0
        while (true) {
            val start = text.indexOf(TOOL_OPEN, cursor)
            if (start < 0) break
            text.substring(cursor, start).takeIf(String::isNotBlank)?.let { blocks += ContentBlock.Text(it) }
            val end = text.indexOf(TOOL_CLOSE, start + TOOL_OPEN.length)
            if (end < 0) throw LlmException(LlmException.Kind.PROTOCOL, "AGY devolvió una llamada a herramienta incompleta.")
            val call = runCatching { Json.parseToJsonElement(text.substring(start + TOOL_OPEN.length, end)).jsonObject }
                .getOrElse { throw LlmException(LlmException.Kind.PROTOCOL, "AGY devolvió una llamada a herramienta no válida.") }
            val name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (name !in allowed) throw LlmException(LlmException.Kind.PROTOCOL, "AGY solicitó una herramienta Nexus que no está disponible.")
            val input = (call["arguments"] ?: call["input"]) as? JsonObject
                ?: throw LlmException(LlmException.Kind.PROTOCOL, "AGY devolvió argumentos de herramienta no válidos.")
            val id = call["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
            blocks += ContentBlock.ToolUse(id, name, input)
            cursor = end + TOOL_CLOSE.length
        }
        text.substring(cursor).takeIf(String::isNotBlank)?.let { blocks += ContentBlock.Text(it) }
        val hasTools = blocks.any { it is ContentBlock.ToolUse }
        if (blocks.isEmpty()) blocks += ContentBlock.Text("")
        return LlmResponse(LlmMessage(Role.ASSISTANT, blocks), if (hasTools) StopReason.TOOL_USE else StopReason.END_TURN, Usage(0, 0))
    }

    private fun mapBridgeError(raw: String?): LlmException {
        val detail = raw.orEmpty()
        val lower = detail.lowercase()
        val message = when {
            lower.contains("enoent") || lower.contains("not found") || lower.contains("no such file") -> "AGY no está instalado o no se encuentra en el bridge."
            lower.contains("auth") || lower.contains("login") || lower.contains("credential") -> "AGY no tiene una sesión válida en el bridge."
            lower.contains("timeout") || lower.contains("timed out") -> "AGY superó el tiempo de espera."
            detail.isBlank() -> "El bridge AGY no pudo completar la solicitud."
            else -> "AGY no pudo completar la solicitud. Comprueba la sesión del CLI en el bridge."
        }
        val kind = when {
            lower.contains("auth") || lower.contains("login") || lower.contains("credential") -> LlmException.Kind.AUTH
            lower.contains("timeout") || lower.contains("timed out") -> LlmException.Kind.UPSTREAM
            else -> LlmException.Kind.UPSTREAM
        }
        return LlmException(kind, message)
    }

    private fun mapHttpError(status: Int): LlmException = when (status) {
        401, 403 -> LlmException(LlmException.Kind.AUTH, "El bridge AGY rechazó la solicitud.", status)
        402 -> LlmException(LlmException.Kind.BUDGET, "La cuenta conectada a AGY no tiene saldo disponible.", status)
        404 -> LlmException(LlmException.Kind.NETWORK, "El bridge AGY no ofrece el endpoint /v1/chat.", status)
        429 -> LlmException(LlmException.Kind.THROTTLED, "AGY está ocupado; vuelve a intentarlo.", status)
        400, 413, 422 -> LlmException(LlmException.Kind.BAD_REQUEST, "El bridge AGY rechazó el formato de la solicitud.", status)
        else -> LlmException(LlmException.Kind.UPSTREAM, "El bridge AGY respondió con HTTP $status.", status)
    }

    private fun mapNetworkError(error: IOException) = LlmException(
        LlmException.Kind.NETWORK,
        if (error is java.net.SocketTimeoutException) "El bridge AGY agotó el tiempo de espera. Comprueba que siga activo."
        else "No se pudo conectar con el bridge AGY. Comprueba su URL y que el servicio siga activo."
    )

    private fun chatEndpoint(raw: String): okhttp3.HttpUrl {
        val value = raw.trim().trimEnd('/')
        if (value.isBlank()) throw LlmException(LlmException.Kind.NETWORK, "Configura la URL del bridge AGY en los ajustes del asistente.")
        val uri = try { URI(value) } catch (_: Exception) { throw LlmException(LlmException.Kind.BAD_REQUEST, "La URL del bridge AGY no es válida.") }
        val localHttp = uri.scheme.equals("http", true) && uri.host in setOf("127.0.0.1", "localhost")
        if ((!uri.scheme.equals("https", true) && !localHttp) || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
            throw LlmException(LlmException.Kind.BAD_REQUEST, "AGY requiere HTTPS; HTTP solo se permite para localhost.")
        }
        return runCatching { "$value/v1/chat".toHttpUrl() }
            .getOrElse { throw LlmException(LlmException.Kind.BAD_REQUEST, "La URL del bridge AGY no es válida.") }
    }

    private fun safeConversationId(value: String?): String =
        (value ?: "nexus-${UUID.randomUUID()}").replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "nexus-${UUID.randomUUID()}" }

    private fun firstUserIndex(messages: List<LlmMessage>): Int = messages.indexOfFirst { it.role == Role.USER }

    private fun safeName(value: String, fallback: String): String =
        value.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100).ifBlank { fallback }

    private fun imageExtension(format: String) = when (format.lowercase()) { "jpeg" -> "jpg"; else -> format.lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "bin" }
    private fun imageMime(format: String) = when (format.lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png", "webp", "gif" -> "image/${format.lowercase()}"; else -> "application/octet-stream" }
    private fun documentMime(format: String) = when (format.lowercase()) {
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "csv" -> "text/csv"
        "html", "htm" -> "text/html"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    private data class Upload(val name: String, val mimeType: String, val bytes: ByteArray)
    private data class RemoteAttachment(val name: String, val mimeType: String, val path: String)

    /** Hides structured Nexus tool markers from the live assistant bubble. */
    private class ToolMarkerFilter(private val emit: (String) -> Unit) {
        private val pending = StringBuilder()
        private var inToolCall = false
        var emittedCharacters: Int = 0
            private set

        fun accept(delta: String) {
            if (delta.isEmpty()) return
            pending.append(delta)
            drain(final = false)
        }

        fun finish() = drain(final = true)

        private fun drain(final: Boolean) {
            while (true) {
                if (inToolCall) {
                    val end = pending.indexOf(TOOL_CLOSE)
                    if (end < 0) {
                        if (final) pending.clear()
                        return
                    }
                    pending.delete(0, end + TOOL_CLOSE.length)
                    inToolCall = false
                    continue
                }
                val start = pending.indexOf(TOOL_OPEN)
                if (start >= 0) {
                    emitSafe(pending.substring(0, start))
                    pending.delete(0, start + TOOL_OPEN.length)
                    inToolCall = true
                    continue
                }
                if (final) {
                    emitSafe(pending.toString())
                    pending.clear()
                    return
                }
                val keep = longestMarkerPrefixSuffix(pending.toString(), TOOL_OPEN)
                val safeLength = pending.length - keep
                if (safeLength > 0) {
                    emitSafe(pending.substring(0, safeLength))
                    pending.delete(0, safeLength)
                }
                return
            }
        }

        private fun emitSafe(value: String) {
            if (value.isNotEmpty()) {
                emittedCharacters += value.length
                emit(value)
            }
        }

        private fun longestMarkerPrefixSuffix(value: String, marker: String): Int {
            val limit = minOf(value.length, marker.length - 1)
            for (size in limit downTo 1) if (value.endsWith(marker.substring(0, size))) return size
            return 0
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-fast"
        private const val TOOL_OPEN = "<NEXUS_TOOL_CALL>"
        private const val TOOL_CLOSE = "</NEXUS_TOOL_CALL>"
        private const val MAX_ATTACHMENT_BYTES = 32 * 1024 * 1024
        private const val MAX_TOTAL_ATTACHMENT_BYTES = 64 * 1024 * 1024L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.MINUTES)
            .retryOnConnectionFailure(false)
            .build()
    }
}