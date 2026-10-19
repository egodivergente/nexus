package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.core.network.NetworkErrorParser
import com.vidadomu.nexus.core.network.awaitResponse
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.ui.settings.FLOW_RECOVERY_KEY
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Client for the private Tailscale-only bridge that operates the already authenticated Flow tab.
 * The pairing token stays in [SecureStorage] and is only sent as an Authorization header.
 */
internal class FlowBridgeClient(
    private val client: OkHttpClient,
    private val secureStorage: SecureStorage
) {
    suspend fun status(): Result<FlowBridgeStatus> = guarded {
        // Comprobar el estado vuelve a elegir puente: si AXELPC se encendió o se apagó, se nota aquí.
        pinned = null
        val chosen = base()
        val where = if (tagFor(chosen) == HOSTS.first().first) "AXELPC" else "ASUS (respaldo; AXELPC no está listo)"
        requestJson(
            request = authorizedRequest("$chosen/status").get().build(),
            decode = { raw ->
                val payload = json.decodeFromString<StatusPayload>(raw)
                FlowBridgeStatus(
                    connected = payload.bridgeStatus.equals("connected", ignoreCase = true),
                    generationEnabled = payload.generationReady,
                    promptLimit = payload.promptLimit.coerceIn(1, MAX_PROMPT_LIMIT),
                    expectedOutputs = payload.expectedOutputs
                        .map { it.lowercase() }
                        .filter { it in EXPECTED_OUTPUTS }
                        .toSet()
                        .ifEmpty { EXPECTED_OUTPUTS },
                    checkedAt = System.currentTimeMillis(),
                    detail = listOfNotNull("Puente: $where.", statusDetail(payload.reason)).joinToString(" ")
                )
            }
        )
    }

    suspend fun submit(
        prompt: String,
        expectedOutput: String,
        clientRequestId: String,
        referenceIds: List<String> = emptyList(),
        runs: Int = 1,
        expectedResults: Int = 0
    ): Result<FlowBridgeJob> {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return Result.failure(ProviderError("Escribe una instrucción para Flow."))
        if (cleanPrompt.length > MAX_PROMPT_LIMIT) {
            return Result.failure(ProviderError("La instrucción supera el límite local de $MAX_PROMPT_LIMIT caracteres."))
        }
        if (expectedOutput !in EXPECTED_OUTPUTS) {
            return Result.failure(ProviderError("El tipo de resultado esperado no es compatible con el puente de Flow."))
        }
        if (!CLIENT_REQUEST_ID.matches(clientRequestId)) {
            return Result.failure(ProviderError("El identificador de la solicitud de Flow no es válido."))
        }

        val body = json.encodeToString(
            CreateJobPayload.serializer(),
            CreateJobPayload(
                prompt = cleanPrompt,
                expectedOutput = expectedOutput,
                clientRequestId = clientRequestId,
                confirmed = true,
                referenceIds = referenceIds,
                runs = runs.coerceIn(1, MAX_RUNS),
                expectedResults = expectedResults.coerceIn(0, MAX_RESULT_COUNT)
            )
        ).toRequestBody(JSON_MEDIA_TYPE)
        return guarded {
            val chosen = base()
            requestJson(
                request = authorizedRequest("$chosen/jobs").post(body).build(),
                decode = { decodeJob(it, tagFor(chosen)) }
            )
        }
    }

    suspend fun uploadReference(file: File, mimeType: String): Result<String> {
        if (mimeType !in FlowReferences.MIME_TYPES || !file.isFile || file.length() !in 1..FlowReferences.MAX_BYTES)
            return Result.failure(ProviderError("Adjunta PNG, JPEG o WebP de hasta 16 MB.", httpStatusCode = 400))
        return guarded {
            requestJson(authorizedRequest("${base()}/references")
                .post(file.asRequestBody(mimeType.toMediaType())).build()) { raw ->
                val id = json.decodeFromString<ReferencePayload>(raw).referenceId
                require(FlowReferences.ID.matches(id))
                id
            }
        }
    }

    @Serializable
    private data class ReferencePayload(@SerialName("reference_id") val referenceId: String)

    // --- Conversación con el agente de Flow (panel «Sesión …» del proyecto abierto) ---

    @Serializable
    private data class AgentMessagePayload(val message: String)

    @Serializable
    data class AgentMedia(val row: Int, val index: Int, val kind: String = "image", val label: String = "",
                          val width: Int = 0, val height: Int = 0)

    @Serializable
    data class AgentReplyPayload(val text: String = "", val options: List<String> = emptyList(), val media: List<AgentMedia> = emptyList())

    @Serializable
    private data class AgentJobPayload(@SerialName("job_id") val jobId: String, val state: String,
                                       val reply: AgentReplyPayload = AgentReplyPayload())

    data class AgentTurn(val id: String, val state: String, val reply: AgentReplyPayload)

    suspend fun agentSend(message: String): Result<AgentTurn> {
        val clean = message.trim()
        if (clean.isBlank()) return Result.failure(ProviderError("Escribe el mensaje para el agente de Flow."))
        if (clean.length > MAX_PROMPT_LIMIT) return Result.failure(ProviderError("El mensaje supera $MAX_PROMPT_LIMIT caracteres."))
        val body = json.encodeToString(AgentMessagePayload.serializer(), AgentMessagePayload(clean)).toRequestBody(JSON_MEDIA_TYPE)
        return guarded {
            val chosen = base()
            requestJson(authorizedRequest("$chosen/agent").post(body).build()) { raw ->
                json.decodeFromString<AgentJobPayload>(raw).let { AgentTurn("${tagFor(chosen)}.${it.jobId}", it.state, it.reply) }
            }
        }
    }

    suspend fun agentStatus(id: String): Result<AgentTurn> {
        if (!JOB_ID.matches(id)) return Result.failure(ProviderError("El identificador de la conversación con Flow no es válido."))
        return guarded {
            requestJson(authorizedRequest("${baseFor(id)}/agent/${rawId(id)}").get().build()) { raw ->
                json.decodeFromString<AgentJobPayload>(raw).let { AgentTurn(id, it.state, it.reply) }
            }
        }
    }

    /** Descarga una imagen o vídeo que generó el agente de Flow en esa respuesta. */
    suspend fun agentMedia(id: String, media: AgentMedia, destinationDir: File): Result<FlowBridgeDownload> = withContext(Dispatchers.IO) {
        if (!JOB_ID.matches(id) || media.row !in 0..10_000 || media.index !in 0..200)
            return@withContext Result.failure(ProviderError("El archivo del agente de Flow no es válido."))
        try {
            destinationDir.mkdirs()
            client.newCall(authorizedRequest("${baseFor(id)}/agent/${rawId(id)}/media/${media.row}/${media.index}").get().build()).awaitResponse().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(bridgeError(response.code, response.body?.string().orEmpty()))
                val body = response.body ?: return@withContext Result.failure(ProviderError("Flow no devolvió el archivo."))
                val expected = if (media.kind == "video") "video" else "image"
                val mime = body.contentType()?.toString()?.substringBefore(';')?.takeIf { it.matches(MIME_TYPE) } ?: fallbackMimeType(expected)
                val target = File(destinationDir, "FlowAgente-${rawId(id).take(24)}-${media.row}-${media.index}.${extensionFor(mime, expected)}")
                body.byteStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                if (target.length() <= 0L) return@withContext Result.failure(ProviderError("Flow devolvió un archivo vacío."))
                Result.success(FlowBridgeDownload(target, mime))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(flowNetworkError(error))
        }
    }

    @Serializable
    data class FlowProject(val id: String, val title: String = "")

    @Serializable
    data class FlowProjectMedia(val key: String, val kind: String = "image", val width: Int = 0, val height: Int = 0)

    @Serializable
    private data class ProjectsPayload(val projects: List<FlowProject> = emptyList())

    @Serializable
    private data class ProjectMediaPayload(val media: List<FlowProjectMedia> = emptyList())

    /** Proyectos de la galería de Flow, para traer a la Biblioteca lo que ya se generó allí. */
    suspend fun projects(): Result<List<FlowProject>> = guarded {
        requestJson(authorizedRequest("$IMPORT_BASE/projects").get().build(), importClient) { raw ->
            json.decodeFromString<ProjectsPayload>(raw).projects.filter { PROJECT_ID.matches(it.id) }
        }
    }

    /** Imágenes y vídeos ya generados en un proyecto de Flow (sin descargarlos todavía). */
    suspend fun projectMedia(projectId: String): Result<List<FlowProjectMedia>> {
        if (!PROJECT_ID.matches(projectId)) return Result.failure(ProviderError("El proyecto de Flow no es válido."))
        return guarded {
            requestJson(authorizedRequest("$IMPORT_BASE/projects/$projectId/media").get().build(), importClient) { raw ->
                json.decodeFromString<ProjectMediaPayload>(raw).media.filter { MEDIA_KEY.matches(it.key) }
            }
        }
    }

    /**
     * Descarga un archivo ya generado de un proyecto de Flow. Sin [size], la versión que muestra Flow (rápida);
     * con size (1K, 2K, 4K para imágenes; 720p, 1080p, 4K para vídeo), el menú «Descargar» de Flow, que mejora
     * la resolución si se pide 2K/4K. No genera contenido nuevo.
     */
    suspend fun projectMediaFile(projectId: String, media: FlowProjectMedia, destinationDir: File, size: String? = null): Result<FlowBridgeDownload> =
        withContext(Dispatchers.IO) {
            if (!PROJECT_ID.matches(projectId) || !MEDIA_KEY.matches(media.key) || (size != null && size !in DOWNLOAD_SIZES))
                return@withContext Result.failure(ProviderError("El archivo de Flow no es válido."))
            try {
                destinationDir.mkdirs()
                val query = size?.let { "?size=$it" }.orEmpty()
                importClient.newCall(authorizedRequest("$IMPORT_BASE/projects/$projectId/media/${media.key}$query").get().build())
                    .awaitResponse().use { response ->
                        if (!response.isSuccessful) return@withContext Result.failure(bridgeError(response.code, response.body?.string().orEmpty()))
                        val body = response.body ?: return@withContext Result.failure(ProviderError("Flow no devolvió el archivo."))
                        val expected = if (media.kind == "video") "video" else "image"
                        val mime = body.contentType()?.toString()?.substringBefore(';')?.takeIf { it.matches(MIME_TYPE) } ?: fallbackMimeType(expected)
                        val target = File(destinationDir, "Flow-${projectId.take(8)}-${media.key}${size?.let { "-$it" }.orEmpty()}.${extensionFor(mime, expected)}")
                        body.byteStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                        if (target.length() <= 0L) return@withContext Result.failure(ProviderError("Flow devolvió un archivo vacío."))
                        Result.success(FlowBridgeDownload(target, mime))
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(flowNetworkError(error))
            }
        }

    // Recorrer un proyecto largo en el navegador del ASUS lleva más que una consulta normal.
    private val importClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(3, java.util.concurrent.TimeUnit.MINUTES).callTimeout(4, java.util.concurrent.TimeUnit.MINUTES).build()
    }

    suspend fun job(jobId: String): Result<FlowBridgeJob> {
        if (!JOB_ID.matches(jobId)) return Result.failure(ProviderError("El identificador del trabajo de Flow no es válido."))
        return guarded {
            requestJson(
                request = authorizedRequest("${baseFor(jobId)}/jobs/${rawId(jobId)}").get().build(),
                decode = { decodeJob(it, tagOf(jobId)) }
            )
        }
    }

    /**
     * Downloads a result only after the bridge has confirmed that Flow exposes an unambiguous
     * download control. The file is staged atomically in the caller-selected private folder.
     */
    suspend fun download(
        jobId: String,
        index: Int,
        destinationDir: File,
        expectedOutput: String
    ): Result<FlowBridgeDownload> = withContext(Dispatchers.IO) {
        if (!JOB_ID.matches(jobId) || index !in 0 until MAX_RESULT_COUNT) {
            return@withContext Result.failure(ProviderError("El resultado de Flow solicitado no es válido."))
        }
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            return@withContext Result.failure(ProviderError("No se pudo preparar la carpeta para el resultado de Flow."))
        }
        if (!destinationDir.isDirectory) {
            return@withContext Result.failure(ProviderError("La carpeta de resultados de Flow no está disponible."))
        }

        val request = try {
            authorizedRequest("${baseFor(jobId)}/jobs/${rawId(jobId)}/downloads/$index").get().build()
        } catch (error: Exception) {
            return@withContext Result.failure(error)
        }
        var temporary: File? = null
        try {
            client.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(bridgeError(response.code, response.body?.string().orEmpty()))
                }
                val responseBody = response.body
                    ?: return@withContext Result.failure(ProviderError("Flow no devolvió ningún archivo."))
                val declaredLength = responseBody.contentLength()
                if (declaredLength > MAX_DOWNLOAD_BYTES) {
                    return@withContext Result.failure(ProviderError("El resultado de Flow es demasiado grande para copiarlo de forma segura."))
                }
                val mimeType = responseBody.contentType()?.toString()?.substringBefore(';')
                    ?.takeIf { it.matches(MIME_TYPE) }
                    ?: fallbackMimeType(expectedOutput)
                val extension = extensionFor(mimeType, expectedOutput)
                val safeId = jobId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
                val target = File(destinationDir, "Flow-$safeId-$index.$extension")
                val temp = File.createTempFile(".flow-", ".download", destinationDir)
                temporary = temp
                var copied = 0L
                responseBody.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_DOWNLOAD_BYTES) {
                                throw ProviderError("El resultado de Flow supera el límite de copia permitido.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (copied <= 0L) {
                    return@withContext Result.failure(ProviderError("Flow devolvió un archivo vacío."))
                }
                if (target.exists() && !target.delete()) {
                    return@withContext Result.failure(ProviderError("No se pudo reemplazar el resultado anterior de Flow."))
                }
                if (!temp.renameTo(target)) {
                    return@withContext Result.failure(ProviderError("No se pudo finalizar la copia del resultado de Flow."))
                }
                temporary = null
                Result.success(
                    FlowBridgeDownload(
                        file = target,
                        mimeType = mimeType
                    )
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(flowNetworkError(error))
        } finally {
            temporary?.delete()
        }
    }

    private fun authorizedRequest(url: String): Request.Builder {
        val token = secureStorage.getProviderApiKey(FLOW_RECOVERY_KEY)
        if (token.isBlank()) {
            throw ProviderError("Vincula el puente privado de Flow en Ajustes antes de continuar.")
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
    }

    private suspend fun <T> requestJson(
        request: Request,
        http: OkHttpClient = client,
        decode: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).awaitResponse().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(bridgeError(response.code, raw))
                } else if (raw.isBlank()) {
                    Result.failure(ProviderError("El puente de Flow devolvió una respuesta vacía."))
                } else {
                    try {
                        Result.success(decode(raw))
                    } catch (error: Exception) {
                        Result.failure(ProviderError("El puente de Flow devolvió una respuesta no reconocible."))
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(flowNetworkError(error))
        }
    }

    private suspend fun <T> guarded(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(flowNetworkError(error))
    }

    private fun flowNetworkError(error: Throwable): ProviderError = when (error) {
        is ProviderError -> error
        is UnknownHostException -> ProviderError(
            humanReadableMessage = "No se encuentra el puente de Flow (AXELPC o ASUS). Activa Tailscale y vuelve a comprobar Flow.",
            technicalDetails = error.javaClass.simpleName,
            errorCode = "FLOW_PRIVATE_DNS"
        )
        is ConnectException -> ProviderError(
            humanReadableMessage = "No se puede contactar con el puente de Flow (AXELPC ni ASUS). Comprueba que alguno está encendido y en Tailscale.",
            technicalDetails = error.javaClass.simpleName,
            errorCode = "FLOW_BRIDGE_UNREACHABLE"
        )
        is SocketTimeoutException -> ProviderError(
            humanReadableMessage = "El puente de Flow no respondió a tiempo. Revisa el historial de Flow antes de volver a enviar.",
            technicalDetails = error.javaClass.simpleName,
            errorCode = "FLOW_BRIDGE_TIMEOUT"
        )
        is IOException -> ProviderError(
            humanReadableMessage = "Se interrumpió la conexión privada con Flow. Revisa el historial antes de repetir la solicitud.",
            technicalDetails = error.javaClass.simpleName,
            errorCode = "FLOW_BRIDGE_IO"
        )
        else -> NetworkErrorParser.parse(error)
    }

    /**
     * AXELPC es el puente principal (más memoria, varios envíos seguidos); el ASUS, siempre encendido, es el respaldo.
     * Se elige AXELPC solo si responde y tiene Flow listo para crear; la elección se mantiene unos minutos para que
     * la subida de referencias y el envío vayan al mismo puente. Cada trabajo lleva la marca de su puente (`a.`/`s.`)
     * y siempre se consulta y descarga allí.
     */
    private suspend fun base(): String {
        pinned?.let { (url, at) -> if (System.currentTimeMillis() - at < PIN_MS) return url }
        val primary = HOSTS.first().second
        val ready = withContext(Dispatchers.IO) {
            runCatching {
                // AXELPC tarda 1-3 s en comprobar Flow (abre la página por CDP); con poco margen se caía al ASUS sin motivo.
                val probe = client.newBuilder().callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
                probe.newCall(authorizedRequest("$primary/status").get().build()).execute().use { response ->
                    response.isSuccessful && json.decodeFromString<StatusPayload>(response.body?.string().orEmpty()).generationReady
                }
            }.getOrDefault(false)
        }
        val chosen = if (ready) primary else HOSTS.last().second
        pinned = chosen to System.currentTimeMillis()
        return chosen
    }

    private fun tagFor(base: String): String = HOSTS.firstOrNull { it.second == base }?.first ?: HOSTS.last().first
    private fun tagOf(jobId: String): String = jobId.substringBefore('.', "").takeIf { t -> HOSTS.any { it.first == t } } ?: HOSTS.last().first
    private fun rawId(jobId: String): String = if ('.' in jobId) jobId.substringAfter('.') else jobId
    /** Trabajos antiguos sin marca son del ASUS. */
    private fun baseFor(jobId: String): String = HOSTS.first { it.first == tagOf(jobId) }.second

    /** Nombre del puente en uso, para los mensajes. */
    fun hostLabel(jobId: String? = null): String =
        if ((jobId?.let(::tagOf) ?: pinned?.first?.let(::tagFor)) == HOSTS.first().first) "AXELPC" else "el ASUS"

    private fun decodeJob(raw: String, tag: String): FlowBridgeJob {
        val payload = json.decodeFromString<JobPayload>(raw)
        require(JOB_ID.matches(payload.jobId)) { "Flow devolvió un identificador de trabajo no válido." }
        val state = payload.state.lowercase()
        require(state in JOB_STATES) { "Flow devolvió un estado de trabajo no reconocido." }
        val expectedOutput = payload.expectedOutput.lowercase()
        require(expectedOutput in EXPECTED_OUTPUTS) { "Flow devolvió un tipo de resultado no reconocido." }
        require(payload.resultCount >= 0) { "Flow devolvió un número de resultados no válido." }
        return FlowBridgeJob(
            id = "$tag.${payload.jobId}",
            state = state,
            expectedOutput = expectedOutput,
            resultCount = payload.resultCount,
            downloadAvailable = payload.downloadAvailable,
            error = payload.error,
            idempotentReplay = payload.idempotentReplay
        )
    }

    private fun bridgeError(status: Int, raw: String): ProviderError {
        val errorCode = runCatching {
            json.decodeFromString<ErrorPayload>(raw).error.takeIf { it.isNotBlank() }
        }.getOrNull()
        val message = referenceErrorMessage(errorCode) ?: when (status) {
            400 -> "Flow no aceptó la solicitud. Revisa la instrucción y el tipo de salida."
            401, 403 -> "La vinculación privada con Flow ya no es válida. Vuelve a conectarla en Ajustes."
            404 -> "El trabajo o el proyecto de Flow ya no está disponible."
            409 -> "No se pudo confirmar el envío a Flow. Revisa Flow antes de volver a enviar."
            413 -> "La instrucción es demasiado larga para el puente de Flow."
            503 -> "Flow no está listo ahora. Abre el proyecto conectado y vuelve a intentarlo."
            else -> "El puente privado de Flow respondió con un error."
        }
        return ProviderError(
            humanReadableMessage = message,
            technicalDetails = errorCode?.let { "Flow bridge: $it" },
            httpStatusCode = status,
            errorCode = errorCode ?: "FLOW_HTTP_$status"
        )
    }

    private fun referenceErrorMessage(code: String?): String? = when (code) {
        "auth_required" -> "Flow ha cerrado la sesión en el ASUS. Vuelve a entrar desde Ajustes › Google Flow."
        "upscale_failed" -> "Flow no pudo mejorar la resolución de este archivo (no cobra en ese caso)."
        "option_unavailable" -> "Flow no ofrece ese tamaño para este archivo."
        "reference_too_large" -> "La referencia supera 16 MB. Reduce su tamaño y vuelve a adjuntarla."
        "invalid_reference_type", "invalid_reference_image" -> "Adjunta una imagen PNG, JPEG o WebP válida."
        "reference_not_found", "reference_integrity_failed", "invalid_reference_ids" -> "La copia privada de una referencia ya no está disponible. Vuelve a adjuntarla."
        "duplicate_reference" -> "La misma referencia aparece dos veces. Selecciónala una sola vez."
        "flow_references_not_empty" -> "Hay ingredientes pendientes en el compositor de Flow. Revísalos antes de enviar."
        "flow_reference_controls_unavailable", "flow_reference_multiple_unavailable" -> "No se pudo usar el selector de ingredientes del proyecto Flow. Revisa el compositor; no se ha generado nada."
        "flow_reference_upload_failed", "flow_references_unconfirmed" -> "Flow no confirmó todas las referencias. Revisa los ingredientes en el proyecto; no se ha generado solo con texto."
        "client_request_conflict" -> "Este identificador ya pertenece a otro encargo de Flow. Revisa su historial antes de repetir."
        else -> null
    }

    private fun fallbackMimeType(expectedOutput: String): String =
        if (expectedOutput == "video") "video/mp4" else "image/png"

    private fun extensionFor(mimeType: String, expectedOutput: String): String = when (mimeType.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/webm" -> "webm"
        "video/mp4" -> "mp4"
        else -> if (expectedOutput == "video") "mp4" else "png"
    }

    private fun statusDetail(reason: String?): String? = when (reason) {
        null, "" -> null
        "flow_not_connected" -> "El puente (AXELPC o ASUS) no tiene una sesión de Flow disponible. Ábrela o vuelve a conectar Flow."
        "flow_project_not_open" -> "El proyecto de Flow no está abierto. Ábrelo y vuelve a comprobar el puente."
        "flow_project_ambiguous" -> "Hay más de un proyecto de Flow abierto. Deja abierto solo el proyecto que quieres usar."
        "flow_controls_unavailable" -> "Flow está abierto, pero todavía no muestra controles para crear. Espera o revisa el proyecto."
        "flow_composer_not_empty" -> "Hay un borrador escrito en Flow. Revísalo o bórralo antes de enviar desde Nexus."
        "flow_composer_not_editable" -> "El cuadro de texto de Flow no está disponible todavía."
        "flow_submit_unavailable" -> "Flow no habilitó la generación para ese proyecto. Revisa sus ajustes."
        "flow_browser_unavailable" -> "El navegador privado de Flow no está disponible en el puente (AXELPC o ASUS)."
        "flow_not_ready" -> "Flow no está listo para crear en este momento."
        "auth_required" -> "Flow necesita volver a iniciar sesión en el navegador del puente (AXELPC o ASUS)."
        else -> "El puente de Flow todavía no está listo para crear."
    }

    @Serializable
    private data class StatusPayload(
        @SerialName("bridge_status") val bridgeStatus: String = "",
        @SerialName("generation_ready") val generationReady: Boolean = false,
        @SerialName("prompt_limit") val promptLimit: Int = DEFAULT_PROMPT_LIMIT,
        @SerialName("expected_outputs") val expectedOutputs: List<String> = emptyList(),
        @SerialName("reason") val reason: String? = null
    )

    @Serializable
    private data class CreateJobPayload(
        val prompt: String,
        @SerialName("expected_output") val expectedOutput: String,
        @SerialName("client_request_id") val clientRequestId: String,
        val confirmed: Boolean,
        @SerialName("reference_ids") val referenceIds: List<String> = emptyList(),
        val runs: Int = 1,
        @SerialName("expected_results") val expectedResults: Int = 0
    )

    @Serializable
    private data class JobPayload(
        @SerialName("job_id") val jobId: String,
        val state: String,
        @SerialName("expected_output") val expectedOutput: String,
        @SerialName("result_count") val resultCount: Int = 0,
        @SerialName("download_available") val downloadAvailable: Boolean = false,
        val error: String? = null,
        @SerialName("idempotent_replay") val idempotentReplay: Boolean = false
    )

    @Serializable
    private data class ErrorPayload(val error: String = "")

    companion object {
        // Direcciones Tailscale verificadas (independientes del DNS privado de Android): AXELPC primero, ASUS de respaldo.
        val HOSTS = listOf("a" to "http://100.72.111.97:8899/api/flow", "s" to "http://100.112.57.2:8899/api/flow")
        const val BASE_URL = "http://100.112.57.2:8899/api/flow"
        // La importación desde Flow va siempre al ASUS: está siempre encendido y su puente tiene esta función.
        private val IMPORT_BASE = HOSTS.last().second
        private val PROJECT_ID = Regex("[A-Za-z0-9-]{8,64}")
        private val MEDIA_KEY = Regex("[0-9a-f]{24}")
        val DOWNLOAD_SIZES = setOf("1K", "2K", "4K", "720p", "1080p")
        private const val PIN_MS = 10 * 60_000L
        @Volatile private var pinned: Pair<String, Long>? = null
        private const val DEFAULT_PROMPT_LIMIT = 4_000
        private const val MAX_PROMPT_LIMIT = 4_000
        const val MAX_RUNS = 10
        const val MAX_RESULT_COUNT = 40
        private const val MAX_DOWNLOAD_BYTES = 1_024L * 1_024L * 1_024L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EXPECTED_OUTPUTS = setOf("image", "video")
        private val CLIENT_REQUEST_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{15,95}")
        private val JOB_ID = Regex("(?:[as]\\.)?[A-Za-z0-9_-]{2,128}")
        private val MIME_TYPE = Regex("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")
        private val JOB_STATES = setOf("submitting", "submitted", "generating", "completed", "uncertain", "failed")
        private val json = Json { ignoreUnknownKeys = true }
    }
}

data class FlowBridgeStatus(
    val connected: Boolean,
    val generationEnabled: Boolean,
    val promptLimit: Int = 4_000,
    val expectedOutputs: Set<String> = setOf("image", "video"),
    val checkedAt: Long? = null,
    /** The bridge deliberately does not expose the account or project title. */
    val projectName: String? = null,
    val detail: String? = null
) {
    val isReady: Boolean get() = connected && generationEnabled
}

data class FlowBridgeJob(
    val id: String,
    val state: String,
    val expectedOutput: String,
    val resultCount: Int,
    val downloadAvailable: Boolean,
    val error: String? = null,
    val idempotentReplay: Boolean = false
)

data class FlowBridgeDownload(
    val file: File,
    val mimeType: String
)
