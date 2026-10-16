package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.model.*

object NativeGenerationRequest {
    fun build(model: NexusModel, modality: Modality, prompt: String, parameters: Map<String, String>,
        media: List<MediaInput>, jobId: String): GenerationRequest {
        if (model.structuredImageReferences) {
            val imageCount = media.count { it.mimeType.startsWith("image/") &&
                (it.fieldKey == null || it.fieldKey == model.imagesArrayField) }
            val maximum = model.maxImages.coerceAtMost(AtlasReferenceLimits.images(model.id))
            require(imageCount <= maximum) { "Este modelo admite como máximo $maximum imágenes de referencia; has añadido $imageCount." }
        }
        val declared = parameters.filter { (key, value) ->
            value.isNotBlank() && model.parameters.any { it.key == key }
        }
        // Mismo contrato que el formulario y el agente: defaults incompatibles fuera, derivados dentro.
        val reconciled = NativeModelSchema.reconcile(model, declared)
        val parametersWithMeta = reconciled.toMutableMap()
        parametersWithMeta["__native_schema"] = "true"
        model.parameters.forEach { p -> p.nativeType?.let { parametersWithMeta["__type_" + p.key] = it } }
        (reconciled.keys - declared.keys).forEach { parametersWithMeta["__type_$it"] = "string" }
        if (!model.promptAccepted) parametersWithMeta["__prompt_accepted"] = "false"
        if (!model.promptRequired) parametersWithMeta["__prompt_optional"] = "true"
        model.contractMedia.forEach { parametersWithMeta["__extra_media_"+it.key]=it.encoding }
        model.firstFrameField?.let { parametersWithMeta["__first_frame_field"] = it }
        model.lastFrameField?.let { parametersWithMeta["__last_frame_field"] = it }
        model.imagesArrayField?.let { parametersWithMeta["__images_array_field"] = it }
        if(model.structuredImageReferences) parametersWithMeta["__structured_images"] = "true"
        model.videoField?.let { parametersWithMeta["__video_field"] = it }
        model.videosArrayField?.let { parametersWithMeta["__videos_array_field"] = it }
        model.inputImageField?.let { parametersWithMeta["__input_field_name"] = it }

        return GenerationRequest(
            id = jobId,
            providerId = model.providerId,
            modelId = model.id,
            modality = modality,
            prompt = prompt.trim(),
            negativePrompt = "",
            parameters = parametersWithMeta,
            mediaInputs = media
        )

    }
}
