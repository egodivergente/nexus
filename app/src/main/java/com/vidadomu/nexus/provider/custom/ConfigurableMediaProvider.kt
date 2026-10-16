package com.vidadomu.nexus.provider.custom

import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.HttpTarget
import com.vidadomu.nexus.agent.core.media.CreateOutcome
import com.vidadomu.nexus.agent.core.media.MediaCapability
import com.vidadomu.nexus.agent.core.media.MediaEngine
import com.vidadomu.nexus.agent.core.media.MediaHttpCall
import com.vidadomu.nexus.agent.core.media.MediaInput
import com.vidadomu.nexus.agent.core.media.MediaMode
import com.vidadomu.nexus.agent.core.media.MediaModelConfig
import com.vidadomu.nexus.agent.core.media.MediaProviderConfig
import com.vidadomu.nexus.agent.core.media.MediaUploadMode
import com.vidadomu.nexus.agent.core.media.PollOutcome
import com.vidadomu.nexus.agent.core.net.JsonPaths
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.network.NetworkErrorParser
import com.vidadomu.nexus.core.network.awaitResponse
import com.vidadomu.nexus.provider.api.NexusProvider
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Proveedor multimedia definido por configuración (URL, autenticación, modelos, plantillas JSON, rutas de respuesta,
 * síncrono o asíncrono). Cumple el mismo contrato que Atlas, Crun o Runway, así que sus resultados usan las tarjetas,
 * el Historial, la Biblioteca y el sondeo de siempre. No sustituye a ninguno de ellos y no inventa compatibilidad:
 * solo habla REST/JSON con lo que el usuario describa.
 */
class ConfigurableMediaProvider(
    val config: MediaProviderConfig,
    client: OkHttpClient,
    private val apiKey: () -> String,
    private val outputDir: File,
) : NexusProvider {
    override val id: String = idFor(config.id)
    override val displayName: String = config.name
    override val description: String = "Proveedor API personalizado · ${config.models.size} modelo(s)."

    // 0 = sin límite de espera, como en el resto de límites.
    private val http: OkHttpClient = config.httpTimeoutSeconds.toLong().let { t ->
        client.newBuilder().readTimeout(t, TimeUnit.SECONDS).callTimeout(t, TimeUnit.SECONDS).build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    private fun nexusId(m: MediaModelConfig) = "custom/${config.id}/${m.id}"
    private fun modelFor(nexusModelId: String) = config.models.firstOrNull { nexusId(it) == nexusModelId }

    override suspend fun getModels(): List<NexusModel> = config.models.map(::toNexusModel)

    private fun toNexusModel(m: MediaModelConfig): NexusModel {
        val cap = m.capability
        val modality = if (cap.video) Modality.VIDEO else Modality.IMAGE
        val parameters = m.params.map { p ->
            ModelParameter(
                key = p.key, displayName = p.label.ifBlank { p.key },
                type = when (p.type) {
                    "enum" -> if (p.options.isNotEmpty()) ParameterType.ENUM else ParameterType.TEXT
                    "integer" -> ParameterType.INTEGER; "float" -> ParameterType.FLOAT; "boolean" -> ParameterType.BOOLEAN
                    "aspect_ratio" -> ParameterType.ASPECT_RATIO; "resolution" -> ParameterType.RESOLUTION
                    "duration" -> ParameterType.DURATION; "seed" -> ParameterType.SEED; else -> ParameterType.TEXT
                },
                defaultValue = p.default, description = p.description, options = p.options, minValue = p.min, maxValue = p.max,
                isAdvanced = p.advanced, isRequired = p.required,
                nativeType = when (p.type) { "integer", "duration", "seed" -> "integer"; "float" -> "number"; "boolean" -> "boolean"; else -> "string" },
            )
        }
        fun options(type: String) = m.params.firstOrNull { it.type == type }?.options.orEmpty()
        val multi = cap.needsImage && m.maxImages > 1
        return NexusModel(
            id = nexusId(m), providerModelId = m.modelValue, displayName = m.name, providerId = id, organization = config.name,
            modalities = listOf(modality),
            capabilities = when (cap) {
                MediaCapability.TEXT_TO_IMAGE -> listOf(ModelCapability.TEXT_TO_IMAGE)
                MediaCapability.IMAGE_TO_IMAGE -> listOf(ModelCapability.IMAGE_TO_IMAGE, ModelCapability.IMAGE_EDITING)
                MediaCapability.TEXT_TO_VIDEO -> listOf(ModelCapability.TEXT_TO_VIDEO)
                MediaCapability.IMAGE_TO_VIDEO -> listOf(ModelCapability.IMAGE_TO_VIDEO)
                MediaCapability.UPSCALE_IMAGE -> listOf(ModelCapability.UPSCALE)
                MediaCapability.UPSCALE_VIDEO -> listOf(ModelCapability.UPSCALE, ModelCapability.VIDEO_TO_VIDEO)
            },
            parameters = parameters, description = m.description.ifBlank { "${cap.label} · ${config.name}" },
            pricingInfo = m.priceUsd?.let { "≈ ${it.toBigDecimal().stripTrailingZeros().toPlainString()} USD${m.priceUnit.takeIf { u -> u.isNotBlank() }?.let { u -> " / $u" }.orEmpty()} (indicado por ti)" } ?: "Tarifa no indicada",
            basePriceUsd = m.priceUsd, priceUnit = m.priceUnit.ifBlank { null }, priceUnitVerified = false,
            inputMode = when { cap.needsVideo -> ModelInputMode.VIDEO_INPUT; multi -> ModelInputMode.MULTI_IMAGE; cap.needsImage -> ModelInputMode.SINGLE_IMAGE; else -> ModelInputMode.TEXT_ONLY },
            maxImages = if (cap.needsImage) m.maxImages.coerceAtLeast(1) else 0, maxVideos = if (cap.needsVideo) 1 else 0,
            firstFrameField = if (cap.needsImage && !multi) "image" else null, imagesArrayField = if (multi) "images" else null,
            videoField = if (cap.needsVideo) "video" else null,
            isMediaRequired = cap.needsImage || cap.needsVideo,
            supportsImageInput = cap.needsImage, supportsFirstFrame = cap == MediaCapability.IMAGE_TO_VIDEO, supportsReferenceImages = multi,
            supportsVideoInput = cap.needsVideo, inputImageField = if (cap.needsImage) "image" else null,
            supportedAspectRatios = options("aspect_ratio"), supportedResolutions = options("resolution"), supportedDurations = options("duration"),
            shortDescription = cap.label, versionNumeric = 1.0, modelFamily = config.name, isPrimaryGeneration = !cap.name.startsWith("UPSCALE"),
            providerPriority = 60,
            promptRequired = !(cap.needsImage || cap.needsVideo), promptAccepted = "{{prompt}}" in m.requestTemplate,
        )
    }

    // ---- HTTP ------------------------------------------------------------------------------------------------------

    private suspend fun call(c: MediaHttpCall): Result<JsonElement> = withContext(Dispatchers.IO) {
        try {
            val builder = HttpTarget(c.url, config.auth, apiKey, config.extraHeaders).request()
            val body = c.body?.toString()?.toRequestBody(JSON)
            val request = when (c.method) {
                "GET" -> builder.get()
                "PUT" -> builder.put(body ?: "{}".toRequestBody(JSON))
                else -> builder.post(body ?: "{}".toRequestBody(JSON))
            }.build()
            http.newCall(request).awaitResponse().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use Result.failure<JsonElement>(NetworkErrorParser.parseHttpError(resp.code, text))
                runCatching { json.parseToJsonElement(text) }
                    .fold({ Result.success(it) }, { Result.failure(ProviderError("El proveedor devolvió una respuesta que no es JSON válido.", text.take(300))) })
            }
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) { Result.failure(NetworkErrorParser.parse(e)) }
        catch (e: com.vidadomu.nexus.agent.core.llm.LlmException) { Result.failure(ProviderError(e.message.orEmpty())) }
    }

    override suspend fun testConnection(apiKey: String): Result<Boolean> {
        if (config.healthPath.isBlank()) return Result.failure(ProviderError("Este proveedor no tiene ruta de comprobación. La primera generación confirmará que funciona."))
        return call(MediaHttpCall("GET", config.url(config.healthPath), null)).map { true }
    }

    // ---- generación -------------------------------------------------------------------------------------------------

    override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> {
        val model = modelFor(request.modelId) ?: return Result.failure(IllegalArgumentException("El modelo ${request.modelId} no pertenece a $displayName."))
        val input = MediaInput(
            prompt = request.prompt, params = request.parameters.filterKeys { !it.startsWith("__") },
            imageUrls = request.mediaInputs.filter { it.type == Modality.IMAGE }.mapNotNull { it.remoteUrl },
            videoUrl = request.mediaInputs.firstOrNull { it.type == Modality.VIDEO }?.remoteUrl,
        )
        // Antes de enviar nada: lo que falte se dice con claridad (y no cuesta nada).
        MediaEngine.inputProblems(model, input).firstOrNull()?.let { return Result.failure(IllegalArgumentException(it)) }
        val call = try { MediaEngine.buildCreate(config, model, input) } catch (e: IllegalArgumentException) { return Result.failure(e) }
        val body = call(call).getOrElse { return Result.failure(it) }
        val base = GenerationJob(id = request.id, providerId = id, modelId = request.modelId, modality = request.modality, prompt = request.prompt,
            parameters = request.parameters, inputMediaUri = request.mediaInputs.firstOrNull()?.uriString, estimatedCostUsd = model.priceUsd)
        return when (val outcome = MediaEngine.parseCreate(model, body)) {
            is CreateOutcome.Failed -> Result.failure(IllegalArgumentException(outcome.message))
            is CreateOutcome.Async -> Result.success(base.copy(providerJobId = outcome.taskId, status = GenerationStatus.GENERATING, technicalDetails = "Tarea creada en $displayName."))
            // Un resultado inmediato también pasa por el sondeo: así se descarga y se archiva igual que los asíncronos.
            is CreateOutcome.Done -> Result.success(base.copy(providerJobId = "sync_${request.id}", status = GenerationStatus.GENERATING,
                outputs = outputs(request.id, request.modality, outcome.urls), technicalDetails = "Resultado inmediato de $displayName."))
        }
    }

    override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> {
        val model = modelFor(job.modelId) ?: return Result.failure(ProviderError("El modelo ya no está configurado."))
        val taskId = job.providerJobId ?: return Result.failure(ProviderError("Falta el identificador de la tarea."))
        if (taskId.startsWith("sync_") && job.outputs.isNotEmpty())
            return Result.success(job.copy(status = GenerationStatus.COMPLETED, progress = 1f, completedAt = System.currentTimeMillis(), error = null))
        val body = call(MediaEngine.buildPoll(config, model, taskId)).getOrElse { return Result.failure(it) }
        return Result.success(when (val outcome = MediaEngine.parsePoll(model, body)) {
            is PollOutcome.Done -> job.copy(status = GenerationStatus.COMPLETED, progress = 1f, completedAt = System.currentTimeMillis(), error = null,
                outputs = outputs(job.id, job.modality, outcome.urls), technicalDetails = "$displayName: completado.")
            is PollOutcome.Failed -> job.copy(status = GenerationStatus.FAILED, completedAt = System.currentTimeMillis(), error = outcome.message, technicalDetails = "$displayName: fallo.")
            is PollOutcome.Running -> {
                val limit = config.maxWaitMinutes
                if (limit > 0 && System.currentTimeMillis() - job.startedAt > limit * 60_000L)
                    // Espera máxima de ESTE proveedor (la elige el usuario). No cancela nada: «Recuperar» vuelve a consultar.
                    job.copy(status = GenerationStatus.FAILED, parameters = job.parameters + ("__tracking_paused" to "true"),
                        error = "Superó la espera máxima configurada ($limit min). El proveedor puede seguir trabajando; usa Recuperar para consultar sin generar otra vez.")
                else job.copy(status = GenerationStatus.GENERATING, progress = outcome.progress ?: job.progress, technicalDetails = "$displayName: en curso.")
            }
        })
    }

    override suspend fun cancelJob(jobId: String): Result<Boolean> =
        Result.failure(ProviderError("Este proveedor personalizado no tiene cancelación remota configurada."))

    private fun outputs(jobId: String, modality: Modality, urls: List<String>): List<GenerationOutput> = urls.mapIndexed { i, raw ->
        val url = if (raw.startsWith("data:")) saveDataUri(jobId, i, raw) ?: raw else raw
        GenerationOutput("${jobId}_out_$i", jobId, modality, url, mimeType = guessMime(url, modality))
    }

    private fun guessMime(url: String, modality: Modality): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"
            "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mov" -> "video/quicktime"
            else -> if (modality == Modality.VIDEO) "video/mp4" else "image/png"
        }
    }

    /** Un resultado en base64 (algunas APIs síncronas) se guarda como archivo: los visores y descargas esperan una dirección. */
    private fun saveDataUri(jobId: String, index: Int, dataUri: String): String? = runCatching {
        val header = dataUri.substringBefore(',')
        val ext = when (header.substringAfter("data:").substringBefore(';')) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "video/mp4" -> "mp4"; "video/webm" -> "webm"; else -> "png" }
        outputDir.mkdirs()
        val file = File(outputDir, "${jobId}_$index.$ext")
        file.writeBytes(Base64.getDecoder().decode(dataUri.substringAfter(',')))
        file.toURI().toString()
    }.getOrNull()

    // ---- adjuntos ---------------------------------------------------------------------------------------------------

    override suspend fun uploadFile(file: File, fileName: String, mimeType: String, onProgress: (Long, Long) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        val maxBytes = config.maxUploadMb.toLong() * 1024 * 1024
        if (maxBytes > 0 && file.length() > maxBytes)
            return@withContext Result.failure(IllegalArgumentException("El archivo pesa ${file.length() / 1024 / 1024} MB y tu límite para $displayName es ${config.maxUploadMb} MB (se cambia en el proveedor)."))
        when (config.uploadMode) {
            MediaUploadMode.DATA_URI -> runCatching {
                onProgress(0, file.length())
                "data:$mimeType;base64," + Base64.getEncoder().encodeToString(file.readBytes()).also { onProgress(file.length(), file.length()) }
            }.fold({ Result.success(it) }, { Result.failure(ProviderError("No se pudo leer el archivo.")) })
            MediaUploadMode.ENDPOINT -> upload(file, fileName, mimeType)
        }
    }

    override suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val maxBytes = config.maxUploadMb.toLong() * 1024 * 1024
        if (maxBytes > 0 && fileBytes.size > maxBytes) return@withContext Result.failure(IllegalArgumentException("El archivo supera tu límite de ${config.maxUploadMb} MB para $displayName."))
        if (config.uploadMode == MediaUploadMode.DATA_URI) Result.success("data:$mimeType;base64," + Base64.getEncoder().encodeToString(fileBytes))
        else File.createTempFile("up", null, outputDir.apply { mkdirs() }).let { f -> try { f.writeBytes(fileBytes); upload(f, fileName, mimeType) } finally { f.delete() } }
    }

    private suspend fun upload(file: File, fileName: String, mimeType: String): Result<String> = try {
        val builder = HttpTarget(config.url(config.uploadPath), config.auth, apiKey, config.extraHeaders).request()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(config.uploadFieldName, fileName, file.asRequestBody(mimeType.toMediaType())).build()
        http.newCall(builder.post(body).build()).awaitResponse().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) Result.failure(NetworkErrorParser.parseHttpError(resp.code, text))
            else JsonPaths.string(runCatching { json.parseToJsonElement(text) }.getOrNull() ?: kotlinx.serialization.json.JsonNull, config.uploadUrlPath)
                ?.let { Result.success(it) } ?: Result.failure(ProviderError("No encuentro la URL del archivo subido (ruta «${config.uploadUrlPath}»)."))
        }
    } catch (e: CancellationException) { throw e } catch (e: IOException) { Result.failure(NetworkErrorParser.parse(e)) }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun idFor(configId: String) = "usr_$configId"
    }
}
