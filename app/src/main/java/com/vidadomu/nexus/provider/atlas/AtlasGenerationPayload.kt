package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.*

object AtlasGenerationPayload {
    fun build(request: GenerationRequest): JsonObject {
        val modelIdToSend = when {
            request.modelId == "bytedance/seedream-v3.0" -> "bytedance/seedream-v4"
            else -> request.modelId
        }

        return buildJsonObject {
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

            request.mediaInputs.filter { it.fieldKey!=null }.groupBy { it.fieldKey!! }.forEach { (key,media) ->
                val urls=media.mapNotNull { it.remoteUrl }
                if(urls.isNotEmpty()) {
                    if(request.parameters["__extra_media_"+key]=="array") put(key,JsonArray(urls.map(::JsonPrimitive)))
                    else put(key,urls.first())
                }
            }
            // Native Media Inputs injection according to model schema
            if (request.mediaInputs.isNotEmpty()) {
                val primaryMedia = request.mediaInputs.filter { it.fieldKey==null }.find { it.slotType == MediaSlotType.PRIMARY_IMAGE }
                val lastFrameMedia = request.mediaInputs.filter { it.fieldKey==null }.find { it.slotType == MediaSlotType.LAST_FRAME }
                val refImages = request.mediaInputs.filter { it.fieldKey==null && it.slotType == MediaSlotType.REFERENCE_IMAGE }
                val videoMedia = request.mediaInputs.filter { it.fieldKey==null }.find { it.slotType == MediaSlotType.VIDEO_INPUT }
                val refVideos = request.mediaInputs.filter { it.fieldKey==null && it.slotType == MediaSlotType.VIDEO_REFERENCE }

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
                        put(imagesArrayKey, NativeImagePayload.encode(allImages.mapNotNull { it.remoteUrl },
                            request.parameters["__structured_images"] == "true"))
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

    }
}
