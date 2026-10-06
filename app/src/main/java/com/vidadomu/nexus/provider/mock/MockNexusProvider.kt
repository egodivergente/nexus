package com.vidadomu.nexus.provider.mock

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.api.NexusProvider
import kotlinx.coroutines.delay
import java.util.UUID

enum class MockBehavior {
    SUCCESS,
    PROCESSING,
    FAILURE,
    TIMEOUT
}

class MockNexusProvider(
    var simulatedBehavior: MockBehavior = MockBehavior.SUCCESS
) : NexusProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Proveedor Simulado (Mock)"
    override val description: String = "Proveedor de pruebas sin conexión para control de calidad, verificación de UI y coste cero."

    override suspend fun getModels(): List<NexusModel> {
        return listOf(
            NexusModel(
                id = "mock/turbo-image",
                displayName = "Simulador Imagen Turbo",
                providerId = id,
                organization = "Nexus Labs",
                modalities = listOf(Modality.IMAGE),
                capabilities = listOf(ModelCapability.TEXT_TO_IMAGE, ModelCapability.IMAGE_TO_IMAGE),
                badge = "Modo Local",
                description = "Generador de imágenes simulado a coste cero para pruebas y verificación de interfaz.",
                pricingInfo = "Gratis (0,00 €)",
                basePriceUsd = 0.0,
                priceUnit = "img",
                parameters = listOf(
                    ModelParameter(
                        key = "aspect_ratio",
                        displayName = "Relación de aspecto",
                        type = ParameterType.ASPECT_RATIO,
                        defaultValue = "1:1",
                        options = listOf("1:1", "16:9", "9:16"),
                        isAdvanced = false
                    ),
                    ModelParameter(
                        key = "seed",
                        displayName = "Semilla (Seed)",
                        type = ParameterType.SEED,
                        defaultValue = "-1",
                        isAdvanced = true
                    )
                )
            ),
            NexusModel(
                id = "mock/cinema-video",
                displayName = "Simulador Vídeo Cinema",
                providerId = id,
                organization = "Nexus Labs",
                modalities = listOf(Modality.VIDEO),
                capabilities = listOf(ModelCapability.TEXT_TO_VIDEO, ModelCapability.IMAGE_TO_VIDEO),
                badge = "Modo Local",
                description = "Generador de vídeo simulado con reproducción fluida de MP4 en ExoPlayer.",
                pricingInfo = "Gratis (0,00 €)",
                basePriceUsd = 0.0,
                priceUnit = "clip",
                parameters = listOf(
                    ModelParameter(
                        key = "duration",
                        displayName = "Duración (Segundos)",
                        type = ParameterType.DURATION,
                        defaultValue = "5",
                        options = listOf("5", "10"),
                        isAdvanced = false
                    )
                )
            ),
            NexusModel(
                id = "mock/sound-audio",
                displayName = "Simulador Audio Sound",
                providerId = id,
                organization = "Nexus Labs",
                modalities = listOf(Modality.AUDIO),
                capabilities = listOf(ModelCapability.TEXT_TO_AUDIO),
                badge = "Modo Local",
                description = "Generador de audio simulado con reproducción nativa sin coste.",
                pricingInfo = "Gratis (0,00 €)",
                basePriceUsd = 0.0,
                priceUnit = "min",
                parameters = emptyList()
            )
        )
    }

    override suspend fun testConnection(apiKey: String): Result<Boolean> {
        delay(200)
        return when (simulatedBehavior) {
            MockBehavior.FAILURE -> Result.failure(ProviderError("Simulated connection failure."))
            MockBehavior.TIMEOUT -> Result.failure(ProviderError("Simulated connection timeout."))
            else -> Result.success(true)
        }
    }

    override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> {
        delay(300)
        if (simulatedBehavior == MockBehavior.FAILURE) {
            return Result.failure(ProviderError("Simulated job submission failure."))
        }

        val jobId = request.id
        val mockOutput = when (request.modality) {
            Modality.IMAGE -> GenerationOutput(
                id = "${jobId}_out_0",
                jobId = jobId,
                type = Modality.IMAGE,
                url = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1200&auto=format&fit=crop&q=80",
                width = 1024,
                height = 1024
            )
            Modality.VIDEO -> GenerationOutput(
                id = "${jobId}_out_0",
                jobId = jobId,
                type = Modality.VIDEO,
                url = "android.resource://com.vidadomu.nexus/raw/sample_video",
                durationSeconds = 10f,
                width = 1280,
                height = 720
            )
            Modality.AUDIO -> GenerationOutput(
                id = "${jobId}_out_0",
                jobId = jobId,
                type = Modality.AUDIO,
                url = "android.resource://com.vidadomu.nexus/raw/sample_audio",
                durationSeconds = 30f
            )
            Modality.CHAT -> GenerationOutput(
                id = "${jobId}_out_0",
                jobId = jobId,
                type = Modality.CHAT,
                url = "Mock prompt synthesized response for: ${request.prompt}"
            )
        }

        val initialStatus = if (simulatedBehavior == MockBehavior.PROCESSING) {
            GenerationStatus.PROCESSING
        } else {
            GenerationStatus.GENERATING
        }

        return Result.success(
            GenerationJob(
                id = jobId,
                providerJobId = "mock_pred_${UUID.randomUUID()}",
                providerId = id,
                modelId = request.modelId,
                modality = request.modality,
                prompt = request.prompt,
                negativePrompt = request.negativePrompt,
                parameters = request.parameters,
                status = initialStatus,
                startedAt = System.currentTimeMillis(),
                outputs = listOf(mockOutput),
                technicalDetails = "Mock Pipeline simulation active"
            )
        )
    }

    override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> {
        delay(400)
        return when (simulatedBehavior) {
            MockBehavior.FAILURE -> Result.success(
                job.copy(
                    status = GenerationStatus.FAILED,
                    error = "Simulated polling failure on Mock Provider.",
                    completedAt = System.currentTimeMillis()
                )
            )
            MockBehavior.PROCESSING -> Result.success(
                job.copy(status = GenerationStatus.PROCESSING, progress = 0.5f)
            )
            else -> Result.success(
                job.copy(
                    status = GenerationStatus.COMPLETED,
                    completedAt = System.currentTimeMillis(),
                    progress = 1.0f
                )
            )
        }
    }

    override suspend fun cancelJob(jobId: String): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun uploadFile(file:java.io.File,fileName:String,mimeType:String,onProgress:(Long,Long)->Unit):Result<String> =
        uploadMedia(byteArrayOf(),fileName,mimeType)

    override suspend fun uploadMedia(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        delay(200)
        return Result.success("https://storage.nexus.mock/uploads/${UUID.randomUUID()}_$fileName")
    }

    companion object {
        const val PROVIDER_ID = "mock_provider"
    }
}
