package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.*

/** Transport limits belong to Nexus, not to the model selected in Flow. */
internal object FlowReferences {
    const val MAX_IMAGES = 12
    const val MAX_BYTES = 16L * 1024 * 1024
    const val PARAMETER = "__flow_reference_ids"
    val MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
    val ID = Regex("ref_[a-f0-9]{64}")

    fun ids(inputs: List<MediaInput>): List<String> {
        require(inputs.size <= MAX_IMAGES) { "El puente de Nexus permite hasta $MAX_IMAGES referencias por encargo." }
        return inputs.map {
            require(it.type == Modality.IMAGE && it.mimeType in MIME_TYPES) { "Flow necesita referencias PNG, JPEG o WebP." }
            require(it.slotType in setOf(MediaSlotType.REFERENCE_IMAGE, MediaSlotType.PRIMARY_IMAGE)) {
                "Esta ruta envía ingredientes; los fotogramas de inicio/final necesitan una ruta específica."
            }
            requireNotNull(it.remoteUrl).also { id ->
                require(ID.matches(id)) { "La referencia todavía no se ha subido al puente privado." }
            }
        }
    }

    fun savedIds(parameters: Map<String, String>): List<String> =
        parameters[PARAMETER]?.takeIf { it.isNotBlank() }?.split(',').orEmpty().also { ids ->
            require(ids.size <= MAX_IMAGES && ids.all { ID.matches(it) }) { "Referencias de Flow no válidas." }
        }

    fun model(modality: Modality): NexusModel = NexusModel(
        id = "current_project_${modality.name.lowercase()}",
        displayName = "Google Flow · ${if (modality == Modality.IMAGE) "Imagen" else "Vídeo"} · proyecto activo",
        providerId = FlowBridgeProvider.PROVIDER_ID, organization = "Google", modalities = listOf(modality),
        capabilities = if (modality == Modality.IMAGE)
            listOf(ModelCapability.TEXT_TO_IMAGE, ModelCapability.IMAGE_TO_IMAGE)
        else listOf(ModelCapability.TEXT_TO_VIDEO, ModelCapability.REFERENCE_TO_VIDEO),
        parameters = listOf(ModelParameter("count", "Resultados solicitados", ParameterType.INTEGER,
            "1", minValue = 1f, maxValue = 20f, nativeType = "integer")),
        description = "Texto e imágenes de referencia en el proyecto abierto en Flow. Se conservan sus ajustes; Flow valida los ingredientes.",
        inputMode = ModelInputMode.MULTI_IMAGE, imagesArrayField = "reference_ids",
        supportsImageInput = true, supportsReferenceImages = true, maxImages = MAX_IMAGES,
        isMediaRequired = false,
        contractMedia = listOf(ContractMediaField("reference_ids", "Imágenes de referencia", "image/*",
            required = false, maximum = MAX_IMAGES, encoding = "array"))
    )
}
