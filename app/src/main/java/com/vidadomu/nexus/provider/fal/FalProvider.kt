package com.vidadomu.nexus.provider.fal

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.network.awaitResponse
import com.vidadomu.nexus.provider.api.NexusProvider
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

data class FalTariff(val usd: Double, val unit: String, val checkedAt: Long,
    val source: String = "https://api.fal.ai/v1/models/pricing")

class FalProvider(client: OkHttpClient, private val key: () -> String,
    private val atlasCatalog: suspend () -> List<NexusModel>) : NexusProvider {
    // No automatic retry of a submission with an uncertain outcome, and no redirect of credentials.
    private val http = client.newBuilder().retryOnConnectionFailure(false).followRedirects(false)
        .followSslRedirects(false).callTimeout(90, TimeUnit.SECONDS).build()
    override val id = "fal"
    override val displayName = "fal.ai"
    override val description = "BiRefNet V2 complementario; tarifas autenticadas con fuente y fecha."
    @Volatile private var currentModel: NexusModel? = null

    private suspend fun read(request: Request): JsonObject = http.newCall(request).awaitResponse().use { response ->
        if(!response.isSuccessful) throw ProviderError("Fal no pudo completar la consulta (HTTP ${response.code}).", httpStatusCode = response.code)
        Json.parseToJsonElement(checkNotNull(response.body).string()).jsonObject
    }
    private fun auth(builder: Request.Builder, apiKey: String = key()): Request.Builder {
        require(apiKey.isNotBlank()) { "Configura la clave de Fal en Ajustes." }
        return builder.header("Authorization", "Key ${apiKey.trim()}")
    }
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { Result.failure(if(e is ProviderError || e is IllegalArgumentException) e else ProviderError("No se pudo confirmar la operación Fal.")) }

    override suspend fun getModels(): List<NexusModel> {
        if(key().isBlank()) { currentModel = null; return emptyList() }
        if(!FalContract.complementary(atlasCatalog())) { currentModel = null; return emptyList() }
        val root = read(Request.Builder().url(FalContract.SOURCE).build())
        val model = FalContract.decode(root.toString(), System.currentTimeMillis())
        currentModel = model
        return listOf(model)
    }

    suspend fun tariff(apiKey: String = key()): FalTariff {
        val url = "https://api.fal.ai/v1/models/pricing?endpoint_id=${FalContract.ENDPOINT}"
        val root = read(auth(Request.Builder().url(url), apiKey).build())
        val price = root["prices"]!!.jsonArray.map { it.jsonObject }.single { it["endpoint_id"]?.jsonPrimitive?.content == FalContract.ENDPOINT }
        require(price["currency"]?.jsonPrimitive?.content == "USD") { "La tarifa Fal no está expresada en USD." }
        val amount = price["unit_price"]!!.jsonPrimitive.double
        require(amount.isFinite() && amount >= 0)
        val unit = price["unit"]!!.jsonPrimitive.content.also { require(it.isNotBlank()) }
        return FalTariff(amount, unit, System.currentTimeMillis())
    }
    override suspend fun testConnection(apiKey: String): Result<Boolean> = attempt { tariff(apiKey); true }

    override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> = attempt {
        val model = currentModel?.takeIf { System.currentTimeMillis() - (it.contractCheckedAt ?: 0) < 3_600_000 }
            ?: getModels().singleOrNull() ?: error("No hay una ruta Fal complementaria verificada.")
        val body = FalContract.payload(model, request).toString().toRequestBody("application/json".toMediaType())
        val root = read(auth(Request.Builder().url("https://queue.fal.run/${FalContract.ENDPOINT}")).post(body).build())
        // Validation after POST must remain uncertain: the provider may already have accepted and billed it.
        val (requestId, status, result) = try {
            val remoteId = root["request_id"]!!.jsonPrimitive.content
            Triple(remoteId, FalContract.queueUrl(root["status_url"]!!.jsonPrimitive.content, remoteId, "/status"),
                FalContract.queueUrl(root["response_url"]!!.jsonPrimitive.content, remoteId, ""))
        } catch(_: Exception) { throw ProviderError("Fal recibió el envío pero no se pudo verificar su seguimiento. No lo repitas sin consultar el proveedor.") }
        GenerationJob(request.id, requestId, id, request.modelId, request.modality, request.prompt,
            parameters = request.parameters + mapOf("__fal_status_url" to status, "__fal_result_url" to result),
            inputMediaUri = request.mediaInputs.first().uriString, status = GenerationStatus.QUEUED)
    }

    override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> = attempt {
        require(job.providerId == id && job.modelId == FalContract.MODEL_ID)
        val requestId = requireNotNull(job.providerJobId)
        val statusUrl = FalContract.queueUrl(requireNotNull(job.parameters["__fal_status_url"]), requestId, "/status")
        val root = read(auth(Request.Builder().url(statusUrl)).build())
        require(root["request_id"]?.jsonPrimitive?.content == requestId) { "El resultado Fal pertenece a otro encargo." }
        when(root["status"]?.jsonPrimitive?.content) {
            "IN_QUEUE" -> job.copy(status = GenerationStatus.QUEUED)
            "IN_PROGRESS" -> job.copy(status = GenerationStatus.GENERATING)
            "COMPLETED" -> {
                val resultUrl = FalContract.queueUrl(requireNotNull(job.parameters["__fal_result_url"]), requestId, "")
                val result = try { read(auth(Request.Builder().url(resultUrl)).build()) }
                catch(e: ProviderError) {
                    if(e.httpStatusCode == 422) return@attempt job.copy(status = GenerationStatus.FAILED,
                        completedAt = System.currentTimeMillis(), error = "Fal terminó el encargo con un error de procesamiento.")
                    throw e
                }
                val outputs = listOf("image", "mask_image").mapNotNull { field ->
                    (result[field] as? JsonObject)?.let { image ->
                        val url = image["url"]!!.jsonPrimitive.content
                        require(url.startsWith("https://"))
                        GenerationOutput("${job.id}_$field", job.id, Modality.IMAGE, url,
                            mimeType = image["content_type"]?.jsonPrimitive?.contentOrNull ?: "image/png",
                            width = image["width"]?.jsonPrimitive?.intOrNull, height = image["height"]?.jsonPrimitive?.intOrNull)
                    }
                }
                require(outputs.any { it.id == "${job.id}_image" }) { "Fal no devolvió la imagen." }
                job.copy(status = GenerationStatus.COMPLETED, progress = 1f, completedAt = System.currentTimeMillis(), outputs = outputs, error = null)
            }
            else -> throw ProviderError("Fal devolvió un estado no reconocido; consulta de nuevo sin reenviar.")
        }
    }
    // The interface supplies only an id, without the persisted cancel URL. Do not invent its route.
    override suspend fun cancelJob(jobId: String): Result<Boolean> = Result.failure(ProviderError("Fal: cancelación remota no disponible; el seguimiento puede pausarse sin reenviar."))
    override suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = attempt {
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif"))
        require(fileBytes.isNotEmpty() && fileBytes.size <= 20 * 1024 * 1024) { "Esta ruta admite imágenes de hasta 20 MiB." }
        "data:$mimeType;base64," + Base64.getEncoder().encodeToString(fileBytes)
    }
    override suspend fun uploadFile(file: File, fileName: String, mimeType: String, onProgress: (Long,Long) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        if(file.length() !in 1..20L * 1024 * 1024) return@withContext Result.failure(IllegalArgumentException("Esta ruta admite imágenes de hasta 20 MiB."))
        uploadMedia(file.readBytes(), fileName, mimeType).also { if(it.isSuccess) onProgress(file.length(), file.length()) }
    }
}
