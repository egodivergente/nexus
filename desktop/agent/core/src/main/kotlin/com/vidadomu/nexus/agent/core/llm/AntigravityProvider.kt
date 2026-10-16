package com.vidadomu.nexus.agent.core.llm

import com.vidadomu.nexus.agent.core.log.Redactor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

/**
 * Proveedor LLM que reutiliza el puente local de AXEL (`/root/axel-native-v2/bridge/server.ts`)
 * hacia Antigravity CLI (`/root/.local/bin/agy --mode agent --output-format stream-json`).
 *
 * Compatible tanto con el contrato actual de AXEL Native (`POST /v1/chat`, SSE `text_delta` + `final`,
 * `POST /v1/jobs/cancel`, `GET /health`) como con el endpoint legado (`POST /v1/chat/stream`, SSE `done`).
 *
 * Las herramientas Android de Nexus se exponen mediante bloques `<NEXUS_TOOL_CALL>` que se convierten en
 * [ContentBlock.ToolUse] para que [com.vidadomu.nexus.agent.core.agent.AgentOrchestrator] las ejecute
 * con los mismos permisos, tarjetas y SpendGuard que el resto de proveedores.
 */
class AntigravityProvider(
    endpoint: String = DEFAULT_ENDPOINT,
    private val sessionId: () -> String = { DEFAULT_CONVERSATION_ID },
    private val client: OkHttpClient = defaultHttpClient(),
) : StreamingLlmProvider {
    override val id: String = "antigravity"
    private val baseUrl = endpoint.trim().trimEnd('/').ifBlank { DEFAULT_ENDPOINT }
    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(isAllowedEndpoint(baseUrl)) {
            "El puente Antigravity debe usar http://127.0.0.1:8788, una IP de Tailscale (100.x.y.z) o https://."
        }
    }

    override suspend fun complete(request: LlmRequest): LlmResponse =
        completeStreaming(request) { }

    override suspend fun completeStreaming(
        request: LlmRequest,
        onTextDelta: (String) -> Unit,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val conversationId = request.conversationId?.takeIf { it.isNotBlank() } ?: sessionId()
        val jobId = "nexus-agy-${UUID.randomUUID()}"
        val payload = encodeStreamRequest(request, conversationId, jobId)
        val bodyBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val activeCalls = java.util.Collections.synchronizedList(mutableListOf<Call>())
        val cancelSent = java.util.concurrent.atomic.AtomicBoolean(false)
        fun triggerCancel() {
            if (cancelSent.compareAndSet(false, true)) {
                synchronized(activeCalls) { activeCalls.forEach { runCatching { it.cancel() } } }
                cancelRemoteJob(jobId, conversationId)
            }
        }
        val completionHandle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                triggerCancel()
            }
        }

        try {
            val primaryResponse = executeStreamCall("$baseUrl/v1/chat", bodyBytes, activeCalls) { triggerCancel() }
            val activeResponse = if (primaryResponse.code == 404) {
                primaryResponse.close()
                executeStreamCall("$baseUrl/v1/chat/stream", bodyBytes, activeCalls) { triggerCancel() }
            } else {
                primaryResponse
            }

            activeResponse.use { res ->
                if (!res.isSuccessful) {
                    val rawErr = runCatching { res.body?.string().orEmpty() }.getOrDefault("")
                    throw mapHttpError(res.code, rawErr)
                }
                val body = res.body ?: throw LlmException(
                    LlmException.Kind.PROTOCOL,
                    "El puente Antigravity cerró la respuesta sin contenido.",
                )
                try {
                    BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)).use { reader ->
                        consumeSseStream(reader, onTextDelta)
                    }
                } catch (e: CancellationException) {
                    triggerCancel()
                    throw e
                } catch (e: LlmException) {
                    throw e
                } catch (e: IOException) {
                    if (!coroutineContext.isActive) {
                        triggerCancel()
                        throw CancellationException("Generación detenida.", e)
                    }
                    throw LlmException(
                        LlmException.Kind.NETWORK,
                        "Conexión interrumpida con el puente Antigravity: ${Redactor.redact(e.message ?: "stream cerrado")}",
                    )
                }
            }
        } finally {
            completionHandle?.dispose()
        }
    }

    private suspend fun executeStreamCall(
        url: String,
        bodyBytes: ByteArray,
        activeCalls: MutableList<Call>,
        onCancel: () -> Unit,
    ): Response {
        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(bodyBytes.toRequestBody(JSON_MEDIA))
            .build()
        val call = client.newCall(req)
        activeCalls += call
        return try {
            awaitResponse(call, onCancel)
        } catch (e: CancellationException) {
            onCancel()
            throw e
        } catch (e: ConnectException) {
            throw LlmException(
                LlmException.Kind.NETWORK,
                "Antigravity (AGY) no está disponible en $baseUrl. Verifica que el puente de Termux esté activo o elige otro proveedor LLM.",
            )
        } catch (e: SocketTimeoutException) {
            throw LlmException(
                LlmException.Kind.NETWORK,
                "Tiempo de espera agotado conectando con Antigravity (AGY) en $baseUrl.",
            )
        } catch (e: IOException) {
            if (!coroutineContext.isActive) {
                onCancel()
                throw CancellationException("Generación detenida.", e)
            }
            throw LlmException(
                LlmException.Kind.NETWORK,
                "No se pudo conectar con Antigravity (AGY) en $baseUrl: ${Redactor.redact(e.message ?: "error de red")}",
            )
        }
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/health").get().build()
        runCatching {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use false
                val obj = json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject
                val ok = obj["ok"]?.jsonPrimitive?.contentOrNull == "true"
                val geminiReady = obj["providers"]?.jsonObject
                    ?.get("gemini")?.jsonObject
                    ?.get("ready")?.jsonPrimitive?.contentOrNull
                ok && (geminiReady == null || geminiReady == "true")
            }
        }.getOrDefault(false)
    }

    private fun cancelRemoteJob(jobId: String, conversationId: String) {
        runCatching {
            val body = buildJsonObject {
                put("jobId", jobId)
                put("conversationId", conversationId)
            }.toString().toByteArray(StandardCharsets.UTF_8).toRequestBody(JSON_MEDIA)
            val req = Request.Builder()
                .url("$baseUrl/v1/jobs/cancel")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            val t = Thread {
                runCatching { client.newCall(req).execute().close() }
            }
            t.isDaemon = true
            t.start()
            t.join(500)
        }
    }

    private suspend fun awaitResponse(call: Call, onCancel: () -> Unit): Response =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                call.cancel()
                onCancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isCompleted) cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!cont.isCompleted) {
                        cont.resumeWith(Result.success(response))
                    } else {
                        response.close()
                    }
                }
            })
        }

    private suspend fun consumeSseStream(
        reader: BufferedReader,
        onTextDelta: (String) -> Unit,
    ): LlmResponse {
        val fullText = StringBuilder()
        var currentEvent = "message"
        val dataLines = mutableListOf<String>()
        var streamError: LlmException? = null
        var cancelledByBridge = false
        var lastEmittedVisible = ""
        var inputTokens = 0
        var outputTokens = 0

        fun flushEvent() {
            if (dataLines.isEmpty()) {
                currentEvent = "message"
                return
            }
            val rawData = dataLines.joinToString("\n").trim()
            dataLines.clear()
            val eventType = currentEvent
            currentEvent = "message"
            if (rawData.isEmpty() || rawData == "[DONE]") return
            val payload = runCatching { json.parseToJsonElement(rawData).jsonObject }.getOrNull() ?: return
            val effectiveType = payload["type"]?.jsonPrimitive?.contentOrNull ?: eventType
            when (effectiveType) {
                "text_delta", "delta" -> {
                    val delta = payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (delta.isNotEmpty()) {
                        fullText.append(delta)
                        val visible = stripToolCallsForPreview(fullText.toString())
                        if (visible != lastEmittedVisible) {
                            lastEmittedVisible = visible
                            onTextDelta(visible)
                        }
                    }
                }
                "usage" -> {
                    inputTokens = payload["input_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: inputTokens
                    outputTokens = payload["output_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: outputTokens
                }
                "error" -> {
                    val msg = payload["message"]?.jsonPrimitive?.contentOrNull
                        ?: payload["error"]?.jsonPrimitive?.contentOrNull
                        ?: "Antigravity devolvió un error."
                    val code = payload["code"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    streamError = mapBridgeError(code, msg)
                }
                "final" -> {
                    val status = payload["status"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
                    val finalText = payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val errorMsg = payload["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                    when (status) {
                        "cancelled", "canceled" -> cancelledByBridge = true
                        "failed", "error" -> {
                            streamError = mapBridgeError("final_failed", errorMsg ?: "El puente Antigravity no pudo completar la respuesta.")
                        }
                        else -> {
                            val current = fullText.toString()
                            if (finalText.isNotEmpty() && (current.isEmpty() || finalText.length > current.length || (TOOL_OPEN_TAG in finalText && TOOL_OPEN_TAG !in current))) {
                                fullText.setLength(0)
                                fullText.append(finalText)
                                val visible = stripToolCallsForPreview(fullText.toString())
                                if (visible != lastEmittedVisible) {
                                    lastEmittedVisible = visible
                                    onTextDelta(visible)
                                }
                            }
                        }
                    }
                }
            }
        }

        while (true) {
            coroutineContext.ensureActive()
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    flushEvent()
                    if (cancelledByBridge) throw CancellationException("Generación detenida.")
                    streamError?.let { throw it }
                }
                line.startsWith(":") -> Unit
                line.startsWith("event:") -> {
                    if (dataLines.isNotEmpty()) {
                        flushEvent()
                        if (cancelledByBridge) throw CancellationException("Generación detenida.")
                        streamError?.let { throw it }
                    }
                    currentEvent = line.removePrefix("event:").trim()
                }
                line.startsWith("data:") -> {
                    val dataPart = line.removePrefix("data:").trimStart()
                    if (dataLines.isNotEmpty() && dataLines.last().trimEnd().endsWith("}") && dataPart.startsWith("{")) {
                        flushEvent()
                        if (cancelledByBridge) throw CancellationException("Generación detenida.")
                        streamError?.let { throw it }
                    }
                    dataLines += dataPart
                }
            }
        }
        flushEvent()
        if (cancelledByBridge) throw CancellationException("Generación detenida.")
        streamError?.let { throw it }

        val raw = fullText.toString()
        val parsed = parseAssistantOutput(raw)
        if (parsed.visibleText != lastEmittedVisible) {
            onTextDelta(parsed.visibleText)
        }
        val content = mutableListOf<ContentBlock>()
        if (parsed.visibleText.isNotBlank()) {
            content += ContentBlock.Text(parsed.visibleText)
        }
        content += parsed.toolUses
        if (content.isEmpty()) {
            content += ContentBlock.Text("…")
        }
        val stopReason = if (parsed.toolUses.isNotEmpty()) StopReason.TOOL_USE else StopReason.END_TURN
        return LlmResponse(
            message = LlmMessage(Role.ASSISTANT, content),
            stopReason = stopReason,
            usage = Usage(inputTokens, outputTokens),
        )
    }

    internal fun encodeStreamRequest(
        request: LlmRequest,
        conversationId: String,
        jobId: String = "nexus-agy-turn",
    ): JsonObject {
        val systemPrompt = buildSystemInstructions(request.system, request.tools)
        val encodedMessages = mutableListOf<JsonObject>()
        if (systemPrompt.isNotBlank()) {
            encodedMessages += buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
        }
        var firstUserDecorated = false
        for (msg in request.messages) {
            val rendered = renderMessage(msg)
            if (rendered.isNotBlank()) {
                val roleStr = if (msg.role == Role.USER) "user" else "assistant"
                val contentStr = if (msg.role == Role.USER && !firstUserDecorated && systemPrompt.isNotBlank()) {
                    firstUserDecorated = true
                    "$systemPrompt\n\n[Mensaje del usuario]\n$rendered"
                } else {
                    rendered
                }
                encodedMessages += buildJsonObject {
                    put("role", roleStr)
                    put("content", contentStr)
                }
            }
        }
        val lastUserMessage = request.messages.lastOrNull { it.role == Role.USER }?.let(::renderMessage).orEmpty()
        val promptForTurn = when {
            request.messages.size <= 1 && systemPrompt.isNotBlank() ->
                "$systemPrompt\n\n[Mensaje del usuario]\n$lastUserMessage"
            request.tools.isNotEmpty() ->
                "$lastUserMessage\n\n$TOOL_REMINDER_FOOTER"
            else -> lastUserMessage
        }.trim()

        return buildJsonObject {
            put("jobId", jobId)
            put("conversationId", conversationId)
            put("provider", "gemini")
            put("model", request.modelId.ifBlank { DEFAULT_MODEL })
            put("prompt", promptForTurn)
            put("messages", buildJsonArray { encodedMessages.forEach { add(it) } })
        }
    }

    private fun renderMessage(message: LlmMessage): String = buildString {
        for (block in message.content) {
            when (block) {
                is ContentBlock.Text -> if (block.text.isNotBlank()) appendLine(block.text.trim())
                is ContentBlock.ToolUse -> {
                    val callJson = buildJsonObject {
                        put("id", block.id)
                        put("name", block.name)
                        put("input", block.input)
                    }
                    appendLine("$TOOL_OPEN_TAG$callJson$TOOL_CLOSE_TAG")
                }
                is ContentBlock.ToolResult -> {
                    val status = if (block.isError) "ERROR" else "OK"
                    appendLine("[Resultado de herramienta ${block.toolUseId} ($status)]: ${block.content}")
                }
                is ContentBlock.Document -> appendLine("[Documento adjunto: ${block.name} (${block.format}, ${block.bytes.size} bytes)]")
                is ContentBlock.Image -> appendLine("[Imagen adjunta (${block.format}, ${block.bytes.size} bytes)]")
                is ContentBlock.Reasoning -> Unit
            }
        }
    }.trim()

    private fun buildSystemInstructions(system: List<String>, tools: List<ToolSpec>): String = buildString {
        val base = system.joinToString("\n\n").trim()
        if (base.isNotEmpty()) {
            append(base)
            append("\n\n")
        }
        appendLine("IMPORTANTE: Estás ejecutándote como el cerebro integrado de la aplicación Android Nexus.")
        appendLine("NO ejecutes comandos bash del servidor ni herramientas de escritorio para responder sobre Nexus; utiliza exclusivamente las herramientas Android de Nexus descritas a continuación.")
        if (tools.isNotEmpty()) {
            appendLine()
            appendLine("## Herramientas Android de Nexus disponibles")
            appendLine("Cuando necesites invocar una o varias herramientas de Nexus, emite EXACTAMENTE un bloque por cada llamada con este formato XML (sin bloques de código markdown alrededor):")
            appendLine("$TOOL_OPEN_TAG{\"name\":\"nombre_herramienta\",\"input\":{...}}$TOOL_CLOSE_TAG")
            appendLine("Después de emitir los bloques $TOOL_OPEN_TAG...$TOOL_CLOSE_TAG, detén tu turno y espera a que Nexus te devuelva `[Resultado de herramienta ...]` antes de afirmar resultados.")
            appendLine()
            for (tool in tools) {
                appendLine("- `${tool.name}`: ${tool.description}")
                appendLine("  Esquema JSON de `input`: ${tool.inputSchema}")
            }
        }
    }.trim()

    internal data class ParsedAssistantOutput(
        val visibleText: String,
        val toolUses: List<ContentBlock.ToolUse>,
    )

    internal fun parseAssistantOutput(raw: String): ParsedAssistantOutput {
        val toolUses = mutableListOf<ContentBlock.ToolUse>()
        val cleaned = TOOL_CALL_REGEX.replace(raw) { match ->
            val body = match.groupValues[1].trim()
            val parsed = parseToolCallJson(body)
            if (parsed != null) {
                toolUses += parsed
                ""
            } else {
                match.value
            }
        }
        val visible = cleaned
            .replace(INCOMPLETE_TOOL_TAIL_REGEX, "")
            .trim()
        return ParsedAssistantOutput(visible, toolUses)
    }

    private fun stripToolCallsForPreview(raw: String): String =
        raw.replace(TOOL_CALL_REGEX, "")
            .replace(INCOMPLETE_TOOL_TAIL_REGEX, "")
            .trim()

    private fun parseToolCallJson(rawJson: String): ContentBlock.ToolUse? = runCatching {
        val normalized = rawJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = json.parseToJsonElement(normalized).jsonObject
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: obj["tool"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "agy_tool_${UUID.randomUUID().toString().take(8)}"
        val inputElement: JsonElement? = obj["input"] ?: obj["arguments"]
        val inputObj = when (inputElement) {
            is JsonObject -> inputElement
            is JsonPrimitive -> runCatching { json.parseToJsonElement(inputElement.content).jsonObject }.getOrNull() ?: buildJsonObject {}
            else -> buildJsonObject {}
        }
        ContentBlock.ToolUse(id = id, name = name, input = inputObj)
    }.getOrNull()

    private fun mapBridgeError(code: String, message: String): LlmException {
        val clean = Redactor.redact(message).take(320)
        val lower = "$code $clean".lowercase()
        val kind = when {
            "auth" in lower || "unauthorized" in lower || "login" in lower || "credential" in lower ->
                LlmException.Kind.AUTH
            "quota" in lower || "rate" in lower || "limit" in lower || "429" in lower || "throttl" in lower ->
                LlmException.Kind.THROTTLED
            "model" in lower && ("not found" in lower || "invalid" in lower || "unsupported" in lower) ->
                LlmException.Kind.MODEL_NOT_ALLOWED
            "enoent" in lower || "spawn" in lower || "unavailable" in lower ->
                LlmException.Kind.UPSTREAM
            else -> LlmException.Kind.UPSTREAM
        }
        return LlmException(kind, "Antigravity (AGY): $clean")
    }

    private fun mapHttpError(status: Int, rawBody: String): LlmException {
        val detail = runCatching {
            val obj = json.parseToJsonElement(rawBody).jsonObject
            obj["error"]?.jsonPrimitive?.contentOrNull ?: obj["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: rawBody.take(240)
        val clean = Redactor.redact(detail.ifBlank { "HTTP $status" }).take(300)
        val kind = when (status) {
            401, 403 -> LlmException.Kind.AUTH
            400, 422 -> LlmException.Kind.BAD_REQUEST
            404 -> LlmException.Kind.MODEL_NOT_ALLOWED
            429 -> LlmException.Kind.THROTTLED
            in 500..599 -> LlmException.Kind.UPSTREAM
            else -> LlmException.Kind.PROTOCOL
        }
        return LlmException(kind, "Antigravity (AGY) devolvió HTTP $status: $clean", status)
    }

    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8788"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        private const val DEFAULT_CONVERSATION_ID = "nexus-agy-default"
        private const val TOOL_OPEN_TAG = "<NEXUS_TOOL_CALL>"
        private const val TOOL_CLOSE_TAG = "</NEXUS_TOOL_CALL>"
        private const val TOOL_REMINDER_FOOTER =
            "[Recordatorio Nexus: si necesitas otra herramienta Android de Nexus, usa <NEXUS_TOOL_CALL>{\"name\":\"...\",\"input\":{...}}</NEXUS_TOOL_CALL>; si ya tienes los datos, responde directamente al usuario en español sin etiquetas XML.]"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val TOOL_CALL_REGEX = Regex("<NEXUS_TOOL_CALL>\\s*(.*?)\\s*</NEXUS_TOOL_CALL>", setOf(RegexOption.DOT_MATCHES_ALL))
        private val INCOMPLETE_TOOL_TAIL_REGEX = Regex("<NEXUS_TOOL_CALL>[\\s\\S]*$")
        private val ALLOWED_HTTP_HOST = Regex(
            "^http://(127\\.0\\.0\\.1|localhost|100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|[a-zA-Z0-9.-]+\\.ts\\.net)(:\\d+)?(/.*)?$"
        )

        fun isAllowedEndpoint(url: String): Boolean {
            val clean = url.trim().trimEnd('/')
            return clean.startsWith("https://") || ALLOWED_HTTP_HOST.matches(clean)
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
