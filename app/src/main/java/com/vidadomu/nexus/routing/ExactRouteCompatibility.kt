package com.vidadomu.nexus.routing

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.CreateUiState
import java.math.BigDecimal
import java.util.Locale

/** Explicit publisher IDs, never display names, logos, family grouping, or a quality score. */
object ExactRouteCompatibility {
    private val aliases = buildMap<String, String> {
        for (v in listOf("4", "4.5", "5.0-pro")) {
            val engine = "bytedance/seedream-$v"
            put("atlas_cloud:bytedance/seedream-v$v", engine)
            put("atlas_cloud:bytedance/seedream-v$v/text-to-image", engine)
            put("atlas_cloud:bytedance/seedream-v$v/edit", engine)
            val crun = when(v) { "4.5" -> "4-5"; "5.0-pro" -> "5-pro"; else -> v }
            put("crun:bytedance/seedream-$crun", engine)
        }
        put("runway:seedream5_pro", "bytedance/seedream-5.0-pro")
        for (tier in listOf("std", "pro")) {
            for (workflow in listOf("text-to-video", "image-to-video"))
                put("atlas_cloud:kwaivgi/kling-v3.0-$tier/$workflow", "kuaishou/kling-3.0-$tier")
            put("crun:crun/kling-v3-$tier", "kuaishou/kling-3.0-$tier")
        }
        // Fal H3 Max is explicitly post-trained: it is not an alias for Atlas H3 Max.
    }
    fun identity(model: NexusModel, hasImages: Boolean): String? {
        val native = model.providerModelId ?: model.id
        val engine = aliases["${model.providerId}:$native"] ?: return null
        val workflow = when {
            Modality.IMAGE in model.modalities -> if(hasImages) "edit" else "text-to-image"
            Modality.VIDEO in model.modalities -> if(hasImages) "image-to-video" else "text-to-video"
            else -> return null
        }
        if (native.endsWith("/text-to-video") && hasImages || native.endsWith("/image-to-video") && !hasImages ||
            native.endsWith("/text-to-image") && hasImages || native.endsWith("/edit") && !hasImages) return null
        return "$engine:$workflow"
    }
    private fun key(key: String) = when(key) {
        "sound", "generate_audio" -> "audio"
        "num_outputs", "num_images", "n" -> "count"
        "multi_shots" -> "multi_shot"
        "ratio" -> "aspect_ratio"
        else -> key
    }
    private fun value(parameter: ModelParameter, value: String): String = when {
        parameter.nativeType in setOf("integer", "number") || parameter.type in setOf(ParameterType.INTEGER, ParameterType.FLOAT, ParameterType.DURATION, ParameterType.SEED) ->
            value.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: value
        parameter.key == "resolution" -> value.uppercase(Locale.ROOT)
        else -> value
    }
    private fun effective(model: NexusModel, values: Map<String, String>): Map<String, String>? {
        val declared = model.parameters.associateBy { it.key }
        if (values.any { (k,v) -> v.isNotBlank() && !k.startsWith("__") && k !in declared }) return null
        val out = linkedMapOf<String, String>()
        for(p in model.parameters) {
            if(p.key.startsWith("__") || p.key in setOf("model", "prompt")) continue
            val v = (values[p.key] ?: p.defaultValue).takeIf { it.isNotBlank() } ?: continue
            val k = key(p.key)
            if(out.put(k,value(p,v)) != null) return null
        }
        return out
    }
    /** Every effective field must survive. A missing target control is incompatibility, never permission to drop it. */
    fun map(state: CreateUiState, target: NexusModel): CreateUiState? {
        val source = state.selectedModel ?: return null
        val hasImages = state.attachedMedia.any { it.mimeType.startsWith("image/") }
        val id = identity(source,hasImages) ?: return null
        if(identity(target,hasImages) != id || source.providerId == target.providerId ||
            state.negativePrompt.isNotBlank() || source.promptAccepted != target.promptAccepted ||
            state.prompt.length > (target.promptMaxLength ?: Int.MAX_VALUE)) return null
        val original = effective(source,state.parameters) ?: return null
        val values = linkedMapOf<String,String>()
        for(p in target.parameters) {
            if(p.key in setOf("model","prompt") || p.key.startsWith("__")) continue
            val requested = original[key(p.key)] ?: continue
            val translated = if(p.options.isNotEmpty()) p.options.singleOrNull { value(p,it) == requested } ?: return null else requested
            if(p.minValue != null && translated.toBigDecimalOrNull()?.let { it < BigDecimal(p.minValue.toString()) } == true) return null
            if(p.maxValue != null && translated.toBigDecimalOrNull()?.let { it > BigDecimal(p.maxValue.toString()) } == true) return null
            values[p.key] = translated
        }
        if(effective(target,values) != original) return null
        // Transport-only fields are reconstructed by NativeGenerationRequest; references retain order and role.
        val media = state.attachedMedia.map { m ->
            val field = when(m.slotType) {
                MediaSlotType.LAST_FRAME -> target.lastFrameField
                MediaSlotType.PRIMARY_IMAGE -> target.firstFrameField ?: target.inputImageField
                MediaSlotType.REFERENCE_IMAGE -> target.imagesArrayField
                MediaSlotType.VIDEO_INPUT -> target.videoField
                MediaSlotType.VIDEO_REFERENCE -> target.videosArrayField
            } ?: return null
            if(m.fieldKey != null) {
                val expected = when(m.slotType) {
                    MediaSlotType.LAST_FRAME -> source.lastFrameField
                    MediaSlotType.PRIMARY_IMAGE -> source.firstFrameField ?: source.inputImageField
                    MediaSlotType.REFERENCE_IMAGE -> source.imagesArrayField
                    MediaSlotType.VIDEO_INPUT -> source.videoField
                    MediaSlotType.VIDEO_REFERENCE -> source.videosArrayField
                }
                if(m.fieldKey != expected) return null // masks/audio/other contract fields need an explicit adapter
            }
            m.copy(fieldKey=field,remoteUrl=null)
        }
        return state.copy(selectedModel=target,parameters=values,attachedMedia=media,schemaReady=true)
    }
}
