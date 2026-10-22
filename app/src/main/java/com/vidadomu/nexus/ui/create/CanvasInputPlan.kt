package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*

/** One plan for the visible slots, picker limits and attachment routing. Schema fields win over catalog flags. */
internal data class CanvasInputField(
    val key: String, val label: String, val mime: String, val required: Boolean,
    val maximum: Int, val slot: MediaSlotType, val contract: ContractMediaField? = null
) {
    fun acceptsMime(mimeType: String): Boolean = mime == "*/*" || mimeType == mime ||
        (mime.endsWith("/*") && mimeType.startsWith(mime.substringBefore('/') + "/"))
    fun contains(media: AttachedMedia): Boolean = if (contract != null) media.fieldKey == key
        else media.fieldKey == null && media.slotType == slot
    fun remaining(media: List<AttachedMedia>): Int = if (maximum == 1) 1
        else (maximum - media.count(::contains)).coerceAtLeast(0)
}

internal object CanvasInputPlan {
    // A picker batch is bounded even when the provider does not publish maxItems.
    const val PICKER_BATCH = 50
    fun primaryParameters(model: NexusModel): List<ModelParameter> = FormContract.normalize(model.parameters).filter {
        !it.isAdvanced || it.key in setOf("scale", "upscale_factor", "scale_factor", "target_resolution", "output_format", "mode", "count")
    }
    fun fields(model: NexusModel, parameters: Map<String, String> = emptyMap()): List<CanvasInputField> {
        val fields = mutableListOf<CanvasInputField>()
        model.contractMedia.filter { it.maximum > 0 }.forEach { field ->
            if (model.providerId == "google_flow" && Modality.VIDEO in model.modalities) {
                val mode = parameters["mode"] ?: "Texto"
                if (mode == "Texto" || (mode == "Fotogramas" && field.key == "reference_ids") ||
                    (mode == "Referencias" && field.key != "reference_ids")) return@forEach
            }
            fields += CanvasInputField(field.key, field.label, field.mime, field.required ||
                (model.providerId == "google_flow" && Modality.VIDEO in model.modalities &&
                    (field.key == "initial_frame" && parameters["mode"] == "Fotogramas" || field.key == "reference_ids" && parameters["mode"] == "Referencias")), field.maximum,
                MediaSlotType.REFERENCE_IMAGE, field)
        }
        if (model.contractEndpoint != null || model.providerId == "google_flow") return fields
        fun add(key: String?, label: String, mime: String, maximum: Int, slot: MediaSlotType) {
            if (key != null && maximum > 0 && fields.none { it.key == key }) fields += CanvasInputField(
                key, label, mime, key in model.nativeRequiredFields ||
                    (model.nativeRequiredFields.isEmpty() && model.isMediaRequired && slot != MediaSlotType.LAST_FRAME), maximum, slot)
        }
        add(model.firstFrameField ?: model.inputImageField,
            if (model.lastFrameField != null) "Fotograma inicial" else "Imagen de origen", "image/*", 1, MediaSlotType.PRIMARY_IMAGE)
        add(model.lastFrameField, "Fotograma final", "image/*", 1, MediaSlotType.LAST_FRAME)
        add(model.imagesArrayField, "Imágenes de referencia", "image/*", model.maxImages, MediaSlotType.REFERENCE_IMAGE)
        add(model.videoField, "Vídeo de origen", "video/*", 1, MediaSlotType.VIDEO_INPUT)
        add(model.videosArrayField, "Vídeos de referencia", "video/*", model.maxVideos, MediaSlotType.VIDEO_REFERENCE)
        return fields
    }
}
