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
        requestJson(
            request = authorizedRequest("$BASE_URL/status").get().build(),
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
                    detail = statusDetail(payload.reason)
                )
            }
        )
    }

    suspend fun submit(
        prompt: String,
        expectedOutput: String,
        clientRequestId: String
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
                confirmed = true
            )
        ).toRequestBody(JSON_MEDIA_TYPE)
        return guarded {
            requestJson(
                request = authorizedRequest("$BASE_URL/jobs").post(body).build(),
                decode = ::decodeJob
            )
        }
    }

    suspend fun job(jobId: String): Result<FlowBridgeJob> {
        if (!JOB_ID.matches(jobId)) return Result.failure(ProviderError("El identificador del trabajo de Flow no es válido."))
        return guarded {
            requestJson(
                request = authorizedRequest("$BASE_URL/jobs/$jobId").get().build(),
                decode = ::decodeJob
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
            authorizedRequest("$BASE_URL/jobs/$jobId/downloads/$index").get().build()
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
        decode: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).awaitResponse().use { response ->
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
            humanReadableMessage = "No se encuentra el ASUS privado. Activa Tailscale y vuelve a comprobar Flow.",
            technicalDetails = error.javaClass.simpleName,
            errorCode = "FLOW_PRIVATE_DNS"
        )
        is ConnectException -> ProviderError(
            humanReadableMessage = "No se puede contactar con el puente de Flow en el ASUS. Comprueba que está encendido y conectado a Tailscale.",
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

    private fun decodeJob(raw: String): FlowBridgeJob {
        val payload = json.decodeFromString<JobPayload>(raw)
        require(JOB_ID.matches(payload.jobId)) { "Flow devolvió un identificador de trabajo no válido." }
        val state = payload.state.lowercase()
        require(state in JOB_STATES) { "Flow devolvió un estado de trabajo no reconocido." }
        val expectedOutput = payload.expectedOutput.lowercase()
        require(expectedOutput in EXPECTED_OUTPUTS) { "Flow devolvió un tipo de resultado no reconocido." }
        require(payload.resultCount >= 0) { "Flow devolvió un número de resultados no válido." }
        return FlowBridgeJob(
            id = payload.jobId,
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
        val message = when (status) {
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
        "flow_not_connected" -> "El ASUS no tiene una sesión de Flow disponible. Ábrela o vuelve a conectar Flow."
        "flow_project_not_open" -> "El proyecto de Flow no está abierto. Ábrelo y vuelve a comprobar el puente."
        "flow_project_ambiguous" -> "Hay más de un proyecto de Flow abierto. Deja abierto solo el proyecto que quieres usar."
        "flow_controls_unavailable" -> "Flow está abierto, pero todavía no muestra controles para crear. Espera o revisa el proyecto."
        "flow_composer_not_empty" -> "Hay un borrador escrito en Flow. Revísalo o bórralo antes de enviar desde Nexus."
        "flow_composer_not_editable" -> "El cuadro de texto de Flow no está disponible todavía."
        "flow_submit_unavailable" -> "Flow no habilitó la generación para ese proyecto. Revisa sus ajustes."
        "flow_browser_unavailable" -> "El navegador privado de Flow no está disponible en el ASUS."
        "flow_not_ready" -> "Flow no está listo para crear en este momento."
        "auth_required" -> "Flow necesita volver a iniciar sesión en el ASUS."
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
        val confirmed: Boolean
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
        const val BASE_URL = "http://asusvidal.tail6ac6b4.ts.net:8899/api/flow"
        private const val DEFAULT_PROMPT_LIMIT = 4_000
        private const val MAX_PROMPT_LIMIT = 4_000
        private const val MAX_RESULT_COUNT = 12
        private const val MAX_DOWNLOAD_BYTES = 1_024L * 1_024L * 1_024L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EXPECTED_OUTPUTS = setOf("image", "video")
        private val CLIENT_REQUEST_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{15,95}")
        private val JOB_ID = Regex("[A-Za-z0-9_-]{2,128}")
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
