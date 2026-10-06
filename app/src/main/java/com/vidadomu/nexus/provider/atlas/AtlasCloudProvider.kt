package com.vidadomu.nexus.provider.atlas

import android.util.Log
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.network.NetworkErrorParser
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.api.NexusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

internal object AtlasUploadResponseParser {
    private val urlKeys = listOf(
        "url",
        "file_url",
        "download_url",
        "image_url",
        "media_url",
        "video_url",
        "audio_url",
        "uri",
        "path"
    )

    fun extractUrl(element: JsonElement): String? = findUrl(element, allowPrimitive = true)

    private fun findUrl(element: JsonElement, allowPrimitive: Boolean): String? {
        return when (element) {
            is JsonPrimitive -> if (allowPrimitive && element.isString) {
                element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            is JsonArray -> element.firstNotNullOfOrNull { findUrl(it, allowPrimitive = true) }
            is JsonObject -> {
                urlKeys.firstNotNullOfOrNull { key ->
                    element.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                        ?.value
                        ?.let { findUrl(it, allowPrimitive = true) }
                } ?: element.entries.firstOrNull { it.key.equals("data", ignoreCase = true) }
                    ?.value
                    ?.let { findUrl(it, allowPrimitive = true) }
                    ?: element.values.firstNotNullOfOrNull { child ->
                        when (child) {
                            is JsonObject, is JsonArray -> findUrl(child, allowPrimitive = false)
                            else -> null
                        }
                    }
            }
            else -> null
        }
    }
}

class AtlasCloudProvider(
    private val okHttpClient: OkHttpClient,
    private val secureStorage: SecureStorage
) : NexusProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Atlas Cloud"
    override val description: String = "Unified AI platform hosting 400+ leading generative media models."

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getModels(): List<NexusModel> = withContext(Dispatchers.IO) {
        val liveResult = fetchLiveCatalog()
        if (liveResult.isSuccess && liveResult.getOrThrow().isNotEmpty()) {
            liveResult.getOrThrow()
        } else {
            AtlasModelCatalog.curatedModels
        }
    }

    suspend fun fetchLiveCatalog(apiKeyOverride: String? = null): Result<List<NexusModel>> = withContext(Dispatchers.IO) {
        val key = apiKeyOverride ?: secureStorage.getAtlasApiKey()
        val requestBuilder = Request.Builder()
            .url("$BASE_URL/api/v1/models")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()

        if (key.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${key.trim()}")
        }

        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext Result.failure(
                        NetworkErrorParser.parseHttpError(response.code, body)
                    )
                }
                val models = AtlasModelMapper.parseModelsResponse(body)
                if (models.isNotEmpty()) {
                    Result.success(models)
                } else {
                    Result.failure(ProviderError("No se pudieron extraer modelos válidos del catálogo de Atlas Cloud."))
                }
            }
        } catch (e: Exception) {
            Result.failure(NetworkErrorParser.parse(e))
        }
    }

    override suspend fun testConnection(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                ProviderError("API key cannot be empty. Please enter your Atlas Cloud key.")
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL/v1/models")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val body = response.body?.string()
                    Result.failure(NetworkErrorParser.parseHttpError(response.code, body))
                }
            }
        } catch (e: Exception) {
            Result.failure(NetworkErrorParser.parse(e))
        }
    }

    override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.getAtlasApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                ProviderError("Atlas Cloud API key not configured. Please add your key in Settings.")
            )
        }

        val endpoint = when (request.modality) {
            Modality.IMAGE -> "$BASE_URL/api/v1/model/generateImage"
            Modality.VIDEO -> "$BASE_URL/api/v1/model/generateVideo"
            Modality.AUDIO -> "$BASE_URL/api/v1/model/generateAudio"
            Modality.CHAT -> "$BASE_URL/v1/chat/completions"
        }

        val modelIdToSend = when {
            request.modelId == "bytedance/seedream-v3.0" -> "bytedance/seedream-v4"
            else -> request.modelId
        }

        val payload = buildJsonObject {
            put("model", modelIdToSend)
            if(request.modality==Modality.CHAT) {
                put("messages",AtlasChatProtocol.userMessages(request.prompt))
            } else put("prompt", request.prompt)
            if (request.negativePrompt.isNotBlank()) {
                put("negative_prompt", request.negativePrompt)
            }

            // Map parameters (skip Nexus-internal keys starting with __)
            for ((k, v) in request.parameters) {
                if (k.startsWith("__")) continue  // Skip internal metadata params
                when {
                    request.parameters["__type_" + k] == "string" -> put(k, v)
                    request.parameters["__type_" + k] == "integer" -> put(k, v.toLong())
                    request.parameters["__type_" + k] == "number" -> put(k, v.toDouble())
                    v == "true" -> put(k, true)
                    v == "false" -> put(k, false)
                    v.toIntOrNull() != null -> put(k, v.toInt())
                    v.toFloatOrNull() != null -> put(k, v.toFloat())
                    else -> put(k, v)
                }
            }
            if(request.modality==Modality.CHAT) put("stream",false)

            // Defaults required by specific model families
            if (request.parameters["__native_schema"] != "true" && modelIdToSend.contains("gpt-image-2", ignoreCase = true)) {
                if (!request.parameters.containsKey("size")) {
                    put("size", "1024x1024")
                }
                if (!request.parameters.containsKey("quality")) {
                    put("quality", "medium")
                }
            }

            // Native Media Inputs injection according to model schema
            if (request.mediaInputs.isNotEmpty()) {
                val primaryMedia = request.mediaInputs.find { it.slotType == MediaSlotType.PRIMARY_IMAGE }
                val lastFrameMedia = request.mediaInputs.find { it.slotType == MediaSlotType.LAST_FRAME }
                val refImages = request.mediaInputs.filter { it.slotType == MediaSlotType.REFERENCE_IMAGE }
                val videoMedia = request.mediaInputs.find { it.slotType == MediaSlotType.VIDEO_INPUT }
                val refVideos = request.mediaInputs.filter { it.slotType == MediaSlotType.VIDEO_REFERENCE }

                // 1. Primary frame / input image
                val firstFrameKey = request.parameters["__first_frame_field"]
                    ?: (if (refImages.isEmpty() && videoMedia == null) request.parameters["__input_field_name"] else null)
                if (firstFrameKey != null && primaryMedia?.remoteUrl != null) {
                    put(firstFrameKey, primaryMedia.remoteUrl)
                } else if (primaryMedia?.remoteUrl != null && request.parameters["__images_array_field"] == null) {
                    put(request.parameters["__input_field_name"] ?: "image", primaryMedia.remoteUrl)
                }

                // 2. Last frame (Dual Frame models)
                val lastFrameKey = request.parameters["__last_frame_field"]
                if (lastFrameKey != null && lastFrameMedia?.remoteUrl != null) {
                    put(lastFrameKey, lastFrameMedia.remoteUrl)
                }

                // 3. Array of images (images: array or reference_images: array)
                val imagesArrayKey = request.parameters["__images_array_field"]
                if (imagesArrayKey != null) {
                    val allImages = if (refImages.isNotEmpty()) {
                        refImages
                    } else if (primaryMedia != null) {
                        listOf(primaryMedia)
                    } else {
                        emptyList()
                    }
                    if (allImages.isNotEmpty()) {
                        put(imagesArrayKey, buildJsonArray {
                            allImages.forEach { item ->
                                item.remoteUrl?.let { add(it) }
                            }
                        })
                    }
                }

                // 4. Source Video (Video Extend / Video Edit)
                val videoKey = request.parameters["__video_field"] ?: "video"
                if (videoMedia?.remoteUrl != null) {
                    put(videoKey, videoMedia.remoteUrl)
                }

                // 5. Array of Reference Videos (Gemini Omni, etc.)
                val videosArrayKey = request.parameters["__videos_array_field"]
                if (videosArrayKey != null && refVideos.isNotEmpty()) {
                    put(videosArrayKey, buildJsonArray {
                        refVideos.forEach { item ->
                            item.remoteUrl?.let { add(it) }
                        }
                    })
                }
            }
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        try {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                val respBody = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        NetworkErrorParser.parseHttpError(response.code, respBody)
                    )
                }

                if (respBody == null) {
                    return@withContext Result.failure(
                        ProviderError("Empty response received from Atlas Cloud.")
                    )
                }

                val jsonElement = json.parseToJsonElement(respBody).jsonObject
                if(request.modality==Modality.CHAT) {
                    val reply=AtlasChatProtocol.responseText(jsonElement)
                        ?: return@withContext Result.failure(ProviderError("El proveedor no devolvió texto. Revisa el límite de tokens o prueba otro modelo."))
                    return@withContext Result.success(GenerationJob(
                        id=request.id,providerJobId=(jsonElement["id"] as? JsonPrimitive)?.contentOrNull,
                        providerId=id,modelId=request.modelId,modality=Modality.CHAT,prompt=request.prompt,
                        parameters=request.parameters,status=GenerationStatus.COMPLETED,progress=1f,
                        completedAt=System.currentTimeMillis(),
                        outputs=listOf(GenerationOutput(request.id+"_text",request.id,Modality.CHAT,reply,mimeType="text/plain")),
                        technicalDetails="Respuesta de texto completada. El proveedor no ha informado un importe final."
                    ))
                }
                val dataObj = (jsonElement["data"] as? JsonObject) ?: jsonElement
                val predictionId = (dataObj["id"] as? JsonPrimitive)?.contentOrNull
                    ?: UUID.randomUUID().toString()

                val statusStr = (dataObj["status"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: "processing"
                val serverProgress = listOf("progress", "percentage", "percent")
                    .firstNotNullOfOrNull { key ->
                        (dataObj[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                    }
                    ?.let { raw -> if (raw > 1f) raw / 100f else raw }
                    ?.coerceIn(0f, 1f)
                val initialOutputs = mutableListOf<GenerationOutput>()
                val outputsArray = dataObj["outputs"] as? JsonArray
                if (outputsArray != null) {
                    for ((index, item) in outputsArray.withIndex()) {
                        val outUrl = (item as? JsonPrimitive)?.contentOrNull ?: continue
                        initialOutputs.add(
                            GenerationOutput(
                                id = "${request.id}_out_$index",
                                jobId = request.id,
                                type = request.modality,
                                url = outUrl
                            )
                        )
                    }
                }

                val isCompleted = statusStr == "completed" || statusStr == "succeeded" || initialOutputs.isNotEmpty()
                val initialStatus = when {
                    isCompleted -> GenerationStatus.COMPLETED
                    statusStr in setOf("failed", "error") -> GenerationStatus.FAILED
                    statusStr in setOf("created", "queued", "pending") -> GenerationStatus.QUEUED
                    statusStr == "generating" -> GenerationStatus.GENERATING
                    statusStr in setOf("rendering", "transcoding") -> GenerationStatus.RENDERING
                    statusStr == "uploading" -> GenerationStatus.UPLOADING
                    else -> GenerationStatus.PROCESSING
                }
                val priceStr = (dataObj["price"] as? JsonPrimitive)?.contentOrNull
                val actualPriceUsd = priceStr?.toDoubleOrNull()

                val techDetails = buildString {
                    append("Atlas Prediction ID: $predictionId")
                    if (!priceStr.isNullOrBlank()) {
                        append("\nCoste de generación: $priceStr USD")
                    }
                }

                val job = GenerationJob(
                    id = request.id,
                    providerJobId = predictionId,
                    providerId = id,
                    modelId = request.modelId,
                    modality = request.modality,
                    prompt = request.prompt,
                    negativePrompt = request.negativePrompt,
                    parameters = request.parameters,
                    inputMediaUri = request.mediaInputs.firstOrNull()?.uriString,
                    status = initialStatus,
                    progress = if (isCompleted) 1f else serverProgress,
                    startedAt = System.currentTimeMillis(),
                    completedAt = if (initialStatus in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED)) {
                        System.currentTimeMillis()
                    } else {
                        null
                    },
                    outputs = initialOutputs,
                    actualCostUsd = actualPriceUsd,
                    estimatedCostUsd = null,
                    technicalDetails = techDetails
                )
                Result.success(job)
            }
        } catch (e: Exception) {
            Result.failure(NetworkErrorParser.parse(e))
        }
    }

    override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> = withContext(Dispatchers.IO) {
        val predictionId = job.providerJobId
        if (predictionId.isNullOrBlank()) {
            return@withContext Result.failure(
                ProviderError("Cannot poll job without Atlas Prediction ID.")
            )
        }

        val apiKey = secureStorage.getAtlasApiKey()
        val url = "$BASE_URL/api/v1/model/prediction/$predictionId"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        NetworkErrorParser.parseHttpError(response.code, respBody)
                    )
                }

                if (respBody == null) {
                    return@withContext Result.failure(
                        ProviderError("Empty polling response from Atlas Cloud.")
                    )
                }

                val jsonElement = json.parseToJsonElement(respBody).jsonObject
                val dataObj = (jsonElement["data"] as? JsonObject) ?: jsonElement
                val statusStr = (dataObj["status"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: "processing"
                val serverProgress = listOf("progress", "percentage", "percent")
                    .firstNotNullOfOrNull { key ->
                        (dataObj[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                    }
                    ?.let { raw -> if (raw > 1f) raw / 100f else raw }
                    ?.coerceIn(0f, 1f)

                val outputs = mutableListOf<GenerationOutput>()
                val outputsArray = dataObj["outputs"] as? JsonArray
                if (outputsArray != null) {
                    for ((index, item) in outputsArray.withIndex()) {
                        val outUrl = (item as? JsonPrimitive)?.contentOrNull ?: continue
                        outputs.add(
                            GenerationOutput(
                                id = "${job.id}_out_$index",
                                jobId = job.id,
                                type = job.modality,
                                url = outUrl
                            )
                        )
                    }
                }

                val newStatus = when (statusStr) {
                    "completed", "succeeded", "success" -> GenerationStatus.COMPLETED
                    "failed", "error" -> GenerationStatus.FAILED
                    "created", "queued", "pending" -> GenerationStatus.QUEUED
                    "generating" -> GenerationStatus.GENERATING
                    "rendering", "transcoding" -> GenerationStatus.RENDERING
                    "uploading" -> GenerationStatus.UPLOADING
                    else -> GenerationStatus.PROCESSING
                }

                val errorMessage = (dataObj["error"] as? JsonPrimitive)?.contentOrNull
                val priceStr = (dataObj["price"] as? JsonPrimitive)?.contentOrNull
                val actualPriceUsd = priceStr?.toDoubleOrNull() ?: job.actualCostUsd
                val techDetails = buildString {
                    append("Atlas Status: $statusStr")
                    if (!priceStr.isNullOrBlank()) {
                        append("\nCoste de generación: $priceStr USD")
                    }
                    append("\nPayload: ${SecureStorage.sanitizeForLogging(respBody)}")
                }
                val updatedJob = job.copy(
                    status = newStatus,
                    progress = when (newStatus) {
                        GenerationStatus.COMPLETED -> 1f
                        GenerationStatus.FAILED, GenerationStatus.CANCELLED -> job.progress
                        else -> serverProgress ?: job.progress
                    },
                    completedAt = if (newStatus == GenerationStatus.COMPLETED || newStatus == GenerationStatus.FAILED) System.currentTimeMillis() else null,
                    error = errorMessage,
                    actualCostUsd = actualPriceUsd,
                    outputs = if (outputs.isNotEmpty()) outputs else job.outputs,
                    technicalDetails = techDetails
                )

                Result.success(updatedJob)
            }
        } catch (e: Exception) {
            Result.failure(NetworkErrorParser.parse(e))
        }
    }

    override suspend fun cancelJob(jobId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        // Atlas prediction cancel if supported or marked locally
        Result.success(true)
    }

    override suspend fun uploadMedia(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.getAtlasApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                ProviderError("API key required to upload media to Atlas Cloud.")
            )
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                fileBytes.toRequestBody(mimeType.toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/model/uploadMedia")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string()
                Log.d("AtlasCloudProvider", "uploadMedia response [${response.code}]")

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        NetworkErrorParser.parseHttpError(response.code, respBody)
                    )
                }

                if (respBody.isNullOrBlank()) {
                    return@withContext Result.failure(ProviderError("Respuesta vacía del servidor al subir archivo."))
                }

                try {
                    val jsonElement = json.parseToJsonElement(respBody)
                    val extractedUrl = AtlasUploadResponseParser.extractUrl(jsonElement)

                    if (!extractedUrl.isNullOrBlank()) {
                        Result.success(extractedUrl)
                    } else {
                        Log.w("AtlasCloudProvider", "Could not find media URL in response: $respBody")
                        Result.failure(ProviderError("Subida correcta, pero Atlas Cloud no devolvió una URL de medios reconocible."))
                    }
                } catch (pe: Exception) {
                    Log.e("AtlasCloudProvider", "Failed to parse uploadMedia JSON: ${pe.message}", pe)
                    Result.failure(ProviderError("Error al interpretar respuesta de subida: ${pe.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(NetworkErrorParser.parse(e))
        }
    }

    suspend fun fetchBalance(): Result<com.vidadomu.nexus.core.model.AccountBalance> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.getAtlasApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(ProviderError("API Key de Atlas Cloud no configurada."))
        }
        val request = Request.Builder()
            .url("$BASE_URL/public/v1/balance")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    return@withContext Result.failure(
                        NetworkErrorParser.parseHttpError(response.code, bodyStr)
                    )
                }
                val jsonElement = json.parseToJsonElement(bodyStr).jsonObject
                val availObj = jsonElement["available"] as? JsonObject
                val cashObj = jsonElement["cash"] as? JsonObject
                val bonusObj = jsonElement["bonus"] as? JsonObject
                val frozenObj = jsonElement["frozen"] as? JsonObject

                val avail = (availObj?.get("value") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
                val cash = (cashObj?.get("value") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: avail
                val bonus = (bonusObj?.get("value") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
                val frozen = (frozenObj?.get("value") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0

                val balance = com.vidadomu.nexus.core.model.AccountBalance(
                    availableUsd = avail,
                    cashUsd = cash,
                    bonusUsd = bonus,
                    frozenUsd = frozen,
                    lastUpdated = System.currentTimeMillis()
                )
                secureStorage.saveCachedBalance(balance)
                Result.success(balance)
            }
        } catch (e: Exception) {
            Result.failure(NetworkErrorParser.parse(e))
        }
    }

    companion object {
        const val PROVIDER_ID = "atlas_cloud"
        private const val BASE_URL = "https://api.atlascloud.ai"
        private const val USER_AGENT = "Nexus/1.0.0 (Android; Mobile)"
    }
}
