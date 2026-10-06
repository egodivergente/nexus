package com.vidadomu.nexus.provider.crun

import com.vidadomu.nexus.core.network.awaitResponse
import okhttp3.RequestBody.Companion.asRequestBody
import android.content.Context
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.network.NetworkErrorParser
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.api.NexusProvider
import com.vidadomu.nexus.provider.api.ProviderAccountStatus
import com.vidadomu.nexus.provider.api.ProviderBalanceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class CrunProvider(
    context: Context,
    private val client: OkHttpClient,
    private val secureStorage: SecureStorage
) : NexusProvider {
    override val id = PROVIDER_ID
    override val displayName = "Crun"
    override val description = "Kling, Seedance, Wan y MiniMax mediante el contrato unificado de Crun."
    private val json = Json { ignoreUnknownKeys = true }
    private val executableModels by lazy {
        val featuredKling = CrunModelCatalog.models.filter { it.id.startsWith("crun/kling-") }
        val snapshot = CrunCatalogSnapshot.load(context.applicationContext)
            .filterNot { it.providerModelId in setOf("kling/v3", "kling/v3-motion-control") }
        (featuredKling + snapshot + CrunTextCatalog.suno)
            .distinctBy { it.id }
    }

    private var textModels:List<NexusModel> = emptyList()
    override var catalogNotice:String? = null
        private set
    override suspend fun getModels(): List<NexusModel> = withContext(Dispatchers.IO) {
        val key=secureStorage.getProviderApiKey(PROVIDER_ID)
        if(key.isBlank()) catalogNotice="Crun: catálogo multimedia documentado; configura la clave para consultar los modelos de texto de tu cuenta."
        else {
            val live=execute(Request.Builder().url("$BASE_URL/api/v1/models").header("x-api-key",key).get().build(),CrunTextCatalog::decode)
            live.onSuccess { textModels=it;catalogNotice=null }.onFailure { catalogNotice="Crun: no se pudo verificar el catálogo de texto de tu cuenta. El catálogo multimedia sigue disponible." }
        }
        (executableModels+textModels).also { android.util.Log.i("NexusCatalog","Crun multimedia=${executableModels.size} text=${textModels.size} verified=${catalogNotice==null}") }
    }

    override suspend fun testConnection(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Crun."))
        val url = "$BASE_URL/api/v1/client/files/upload-url".toHttpUrl().newBuilder()
            .addQueryParameter("content_type", "image/png")
            .addQueryParameter("ext", ".png")
            .build()
        execute(Request.Builder().url(url).header("x-api-key", apiKey.trim()).get().build()) { true }
    }

    override suspend fun fetchAccountStatus(apiKey: String): Result<ProviderAccountStatus> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Crun."))
        execute(Request.Builder().url("$BASE_URL/api/v1/client/account/balance")
            .header("x-api-key", apiKey.trim()).get().build()) { body ->
            ProviderBalanceParser.decode(body, listOf("balance", "credits", "credit_balance", "remaining_credits", "available_credits", "available"))
        }
    }

    suspend fun estimateCredits(model:NexusModel,values:Map<String,String>,prompt:String=""):Result<Double> = withContext(Dispatchers.IO) {
        val key=secureStorage.getProviderApiKey(PROVIDER_ID)
        if(key.isBlank()) return@withContext Result.failure(ProviderError("Configura Crun para consultar su tarifa."))
        if(model.modalities.contains(Modality.CHAT)) return@withContext Result.failure(ProviderError("El texto se factura por tokens."))
        val request=GenerationRequest("quote",PROVIDER_ID,model.id,model.modalities.first(),prompt,parameters=values)
        val payload=try { CrunPayloadCodec.encode(model,request).toString() } catch(e:IllegalArgumentException) { return@withContext Result.failure(ProviderError("La cotización requiere los archivos de entrada.")) }
        execute(Request.Builder().url("$BASE_URL/api/v1/client/job/estimate-credits")
            .header("x-api-key",key).post(payload.toRequestBody(JSON_MEDIA_TYPE)).build()) { body ->
            val credits=Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("credits")?.jsonPrimitive?.doubleOrNull
            requireNotNull(credits?.takeIf { it.isFinite() && it>=0 }) { "Crun no devolvió una cotización válida." }
        }
    }

    override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> = withContext(Dispatchers.IO) {
        val key = secureStorage.getProviderApiKey(PROVIDER_ID)
        if (key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Crun en Ajustes."))
        val model = (executableModels+textModels).firstOrNull { it.id == request.modelId }
            ?: return@withContext Result.failure(ProviderError("El modelo ${request.modelId} no pertenece al catálogo ejecutable de Crun."))
        if(request.modality==Modality.CHAT) return@withContext submitChat(model,request,key)
        try { CrunTextCatalog.validateSuno(model,request) } catch(e:IllegalArgumentException) { return@withContext Result.failure(ProviderError(e.message ?: "Revisa la composición.")) }
        val payload = CrunPayloadCodec.encode(model, request).toString()
        val httpRequest = Request.Builder()
            .url("$BASE_URL/api/v1/client/job/CreateTask")
            .header("x-api-key", key)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(httpRequest) { body ->
            GenerationJob(
                id = request.id,
                providerJobId = CrunPayloadCodec.parseCreatedTaskId(body),
                providerId = PROVIDER_ID,
                modelId = request.modelId,
                modality = request.modality,
                prompt = request.prompt,
                negativePrompt = request.negativePrompt,
                parameters = request.parameters,
                inputMediaUri = request.mediaInputs.firstOrNull()?.uriString,
                status = GenerationStatus.QUEUED,
                estimatedCostUsd = null,
                technicalDetails = "Crun task creada correctamente."
            )
        }
    }

    private suspend fun submitChat(model:NexusModel,request:GenerationRequest,key:String):Result<GenerationJob> {
        val payload=buildJsonObject {
            put("model",model.providerModelId)
            put("messages",buildJsonArray { add(buildJsonObject { put("role","user");put("content",request.prompt) }) })
            put("stream",false)
            request.parameters["max_tokens"]?.toIntOrNull()?.takeIf { it>0 }?.let { put("max_tokens",it) }
        }
        return execute(Request.Builder().url("$BASE_URL/api/v1/chat/completions").header("x-api-key",key)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build()) { body ->
            val root=Json.parseToJsonElement(body).jsonObject
            val reply=(root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?: error("Crun no devolvió una respuesta de texto.")
            GenerationJob(id=request.id,providerId=PROVIDER_ID,modelId=request.modelId,modality=Modality.CHAT,prompt=request.prompt,
                parameters=request.parameters,status=GenerationStatus.COMPLETED,progress=1f,completedAt=System.currentTimeMillis(),
                outputs=listOf(GenerationOutput(request.id+"_text",request.id,Modality.CHAT,reply,mimeType="text/plain")))
        }
    }

    override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> = withContext(Dispatchers.IO) {
        val taskId = job.providerJobId
            ?: return@withContext Result.failure(ProviderError("La tarea no contiene el identificador de Crun."))
        val key = secureStorage.getProviderApiKey(PROVIDER_ID)
        if (key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Crun en Ajustes."))
        val url = "$BASE_URL/api/v1/client/job/TaskInfo".toHttpUrl().newBuilder()
            .addQueryParameter("task_id", taskId).build()
        val httpRequest = Request.Builder().url(url).header("x-api-key", key).get().build()
        execute(httpRequest) { body ->
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: throw IllegalStateException("Respuesta de estado de Crun incompleta.")
            val statusText = data["status"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val result = data["result"] as? JsonObject
            val urls = (result?.get("media_urls") as? JsonArray).orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull }
            val status = when (statusText) {
                "success", "succeeded", "completed" -> GenerationStatus.COMPLETED
                "failed", "error" -> GenerationStatus.FAILED
                "pending", "created", "queued" -> GenerationStatus.QUEUED
                else -> GenerationStatus.GENERATING
            }
            job.copy(
                parameters = job.parameters + if(status in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED)) {
                    data["credits"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it>=0 }
                        ?.let { mapOf("__actual_credits" to it.toString()) }.orEmpty()
                } else emptyMap(),
                status = status,
                progress = if (status == GenerationStatus.COMPLETED) 1f else job.progress,
                completedAt = if (status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED)) System.currentTimeMillis() else null,
                error = if (status == GenerationStatus.FAILED) result?.get("message")?.jsonPrimitive?.contentOrNull ?: "La generación falló en Crun." else null,
                outputs = if (urls.isEmpty()) job.outputs else urls.mapIndexed { index, urlValue ->
                    GenerationOutput("${job.id}_out_$index", job.id, job.modality, urlValue)
                },
                technicalDetails = "Crun status: $statusText"
            )
        }
    }

    override suspend fun cancelJob(jobId: String): Result<Boolean> = Result.success(false)

    override suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val key = secureStorage.getProviderApiKey(PROVIDER_ID)
        if (key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Crun en Ajustes."))
        val extension = fileName.substringAfterLast('.', "bin").lowercase().replace(Regex("[^a-z0-9]"), "").take(12).ifBlank { "bin" }
        val ticketUrl = "$BASE_URL/api/v1/client/files/upload-url".toHttpUrl().newBuilder()
            .addQueryParameter("content_type", mimeType)
            .addQueryParameter("ext", ".$extension")
            .build()
        val ticketRequest = Request.Builder().url(ticketUrl).header("x-api-key", key).get().build()
        execute(ticketRequest) { body -> CrunPayloadCodec.parseUploadTicket(body) }.fold(
            onSuccess = { ticket ->
                val putRequest = Request.Builder()
                    .url(ticket.presignedUrl)
                    .put(fileBytes.toRequestBody(mimeType.toMediaType()))
                    .build()
                try {
                    client.newCall(putRequest).awaitResponse().use { response ->
                        if (!response.isSuccessful) Result.failure(NetworkErrorParser.parseHttpError(response.code, response.body?.string()))
                        else Result.success(ticket.fileUrl)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
                    Result.failure(NetworkErrorParser.parse(e))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun uploadFile(file: java.io.File, fileName: String, mimeType: String, onProgress:(Long,Long)->Unit): Result<String> = withContext(Dispatchers.IO) {
        val key = secureStorage.getProviderApiKey(PROVIDER_ID)
        if (key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Crun en Ajustes."))
        val extension = fileName.substringAfterLast('.', "bin").lowercase().replace(Regex("[^a-z0-9]"), "").take(12).ifBlank { "bin" }
        val ticketUrl = "$BASE_URL/api/v1/client/files/upload-url".toHttpUrl().newBuilder()
            .addQueryParameter("content_type", mimeType)
            .addQueryParameter("ext", ".$extension")
            .build()
        val ticketRequest = Request.Builder().url(ticketUrl).header("x-api-key", key).get().build()
        execute(ticketRequest) { body -> CrunPayloadCodec.parseUploadTicket(body) }.fold(
            onSuccess = { ticket ->
                val putRequest = Request.Builder()
                    .url(ticket.presignedUrl)
                    .put(com.vidadomu.nexus.core.network.ProgressRequestBody(file.asRequestBody(mimeType.toMediaType()),onProgress))
                    .build()
                try {
                    client.newCall(putRequest).awaitResponse().use { response ->
                        if (!response.isSuccessful) Result.failure(NetworkErrorParser.parseHttpError(response.code, response.body?.string()))
                        else Result.success(ticket.fileUrl)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
                    Result.failure(NetworkErrorParser.parse(e))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun <T> execute(request: Request, parser: suspend (String) -> T): Result<T> = try {
        client.newCall(request).awaitResponse().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) Result.failure(NetworkErrorParser.parseHttpError(response.code, body))
            else Result.success(parser(body))
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
        Result.failure(NetworkErrorParser.parse(e))
    }

    companion object {
        const val PROVIDER_ID = "crun"
        private const val BASE_URL = "https://api.crun.ai"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
