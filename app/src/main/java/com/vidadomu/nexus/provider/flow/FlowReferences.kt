package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.*

/** Transport limits belong to Nexus, not to the model selected in Flow. */
internal object FlowReferences {
    const val MAX_IMAGES = 12
    const val MAX_BYTES = 16L * 1024 * 1024
    const val PARAMETER = "__flow_reference_ids"
    const val INITIAL_FRAME = "__flow_initial_frame_id"
    const val END_FRAME = "__flow_end_frame_id"
    const val MODE = "__flow_video_mode"
    val MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
    val ID = Regex("ref_[a-f0-9]{64}")

    fun swapFrames(start: AttachedMedia, end: AttachedMedia): Pair<AttachedMedia, AttachedMedia> =
        end.copy(id = start.id, fieldKey = "initial_frame", slotType = MediaSlotType.PRIMARY_IMAGE) to
            start.copy(id = end.id, fieldKey = "end_frame", slotType = MediaSlotType.LAST_FRAME)

    data class Inputs(val references: List<String>, val initialFrame: String? = null, val endFrame: String? = null)

    fun inputs(inputs: List<MediaInput>, mode: String? = null): Inputs {
        val references = mutableListOf<String>()
        var initial: String? = null
        var end: String? = null
        inputs.forEach { item ->
            require(item.type == Modality.IMAGE && item.mimeType in MIME_TYPES) { "Flow necesita referencias PNG, JPEG o WebP." }
            val id = requireNotNull(item.remoteUrl) { "La referencia todavía no se ha subido al puente privado." }
            require(ID.matches(id)) { "La referencia todavía no se ha subido al puente privado." }
            when (item.fieldKey) {
                "initial_frame" -> { require(initial == null); initial = id }
                "end_frame" -> { require(end == null); end = id }
                else -> {
                    require(item.slotType in setOf(MediaSlotType.REFERENCE_IMAGE, MediaSlotType.PRIMARY_IMAGE)) {
                        "Flow admite referencias de imagen o los fotogramas Inicio y Fin."
                    }
                    // Upload IDs identify the file bytes. Different picker URIs can
                    // therefore refer to the same image; send that ingredient once.
                    if (id !in references) references += id
                }
            }
        }
        require(references.size <= MAX_IMAGES) { "El puente de Nexus permite hasta $MAX_IMAGES referencias por encargo." }
        require(initial == null || end == null || initial != end) { "No se pueden repetir los fotogramas Inicio y Fin de Flow." }
        require(end == null || initial != null) { "Añade primero el fotograma Inicio antes de Fin." }
        val effectiveMode = mode ?: when {
            initial != null || end != null -> "Fotogramas"
            references.isNotEmpty() -> "Referencias"
            else -> "Texto"
        }
        require(effectiveMode in setOf("Texto", "Fotogramas", "Referencias")) { "El modo de vídeo Flow no es válido." }
        when (effectiveMode) {
            "Texto" -> require(initial == null && end == null && references.isEmpty()) { "El modo Texto no admite fotogramas ni referencias." }
            "Fotogramas" -> require(initial != null && references.isEmpty()) { "Fotogramas requiere Inicio y admite Fin opcional, sin referencias." }
            "Referencias" -> require(references.isNotEmpty() && initial == null && end == null) { "Añade imágenes de referencia para este modo." }
        }
        return Inputs(references, initial, end)
    }

    fun ids(inputs: List<MediaInput>): List<String> = inputs(inputs).references

    fun savedIds(parameters: Map<String, String>): List<String> =
        parameters[PARAMETER]?.takeIf { it.isNotBlank() }?.split(',').orEmpty().distinct().also { ids ->
            require(ids.size <= MAX_IMAGES && ids.all { ID.matches(it) }) { "Referencias de Flow no válidas." }
        }

    fun model(modality: Modality): NexusModel = NexusModel(
        id = "current_project_${modality.name.lowercase()}",
        displayName = "Google Flow · ${if (modality == Modality.IMAGE) "Imagen" else "Vídeo"} · proyecto activo",
        providerId = FlowBridgeProvider.PROVIDER_ID, organization = "Google", modalities = listOf(modality),
        capabilities = if (modality == Modality.IMAGE)
            listOf(ModelCapability.TEXT_TO_IMAGE, ModelCapability.IMAGE_TO_IMAGE)
        else listOf(ModelCapability.TEXT_TO_VIDEO, ModelCapability.IMAGE_TO_VIDEO, ModelCapability.REFERENCE_TO_VIDEO),
        parameters = (if (modality == Modality.VIDEO) listOf(ModelParameter("mode", "Vídeo Flow", ParameterType.ENUM,
            "Texto", options = listOf("Texto", "Fotogramas", "Referencias"), isRequired = true, nativeType = "string")) else emptyList()) + listOf(ModelParameter("count", "Resultados solicitados", ParameterType.INTEGER,
            "4", minValue = 1f, maxValue = FlowBridgeClient.MAX_RESULT_COUNT.toFloat(), nativeType = "integer"),
            ModelParameter("per_run", "Resultados por envío (el «xN» puesto en Flow)", ParameterType.INTEGER,
            "4", minValue = 1f, maxValue = 4f, nativeType = "integer")),
        description = "Texto e imágenes de referencia en el proyecto abierto en Flow. Se conservan sus ajustes; Flow valida los ingredientes.",
        inputMode = ModelInputMode.MULTI_IMAGE, imagesArrayField = "reference_ids",
        supportsImageInput = true, supportsReferenceImages = true, maxImages = MAX_IMAGES,
        isMediaRequired = false,
        contractMedia = if (modality == Modality.VIDEO) listOf(
            ContractMediaField("initial_frame", "Fotograma Inicio", "image/*", required = false, maximum = 1),
            ContractMediaField("end_frame", "Fotograma Fin (opcional)", "image/*", required = false, maximum = 1),
            ContractMediaField("reference_ids", "Imágenes de referencia", "image/*", required = false, maximum = MAX_IMAGES, encoding = "array")
        ) else listOf(ContractMediaField("reference_ids", "Imágenes de referencia", "image/*",
            required = false, maximum = MAX_IMAGES, encoding = "array"))
    )
}
