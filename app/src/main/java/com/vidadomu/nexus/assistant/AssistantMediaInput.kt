package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.spend.MediaRef
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.NexusModel

/** Legacy native inputs use slots; provider contract inputs use explicit field keys. */
internal fun assistantMediaInput(model: NexusModel, media: MediaRef): AttachedMedia {
    val field = media.fieldKey ?: when (media.role) {
        "first_frame" -> model.firstFrameField ?: model.inputImageField ?: model.imagesArrayField
        "last_frame" -> model.lastFrameField
        "reference" -> model.imagesArrayField ?: model.inputImageField
        "video" -> model.videoField ?: model.videosArrayField
        else -> error("Rol de medio desconocido: ${media.role}")
    }
    val slot = when {
        field != null && field == model.imagesArrayField -> MediaSlotType.REFERENCE_IMAGE
        field != null && field == model.videosArrayField -> MediaSlotType.VIDEO_REFERENCE
        field != null && field == model.lastFrameField -> MediaSlotType.LAST_FRAME
        field != null && (field == model.firstFrameField || field == model.inputImageField) -> MediaSlotType.PRIMARY_IMAGE
        else -> when (media.role) {
            "first_frame" -> MediaSlotType.PRIMARY_IMAGE
            "last_frame" -> MediaSlotType.LAST_FRAME
            "reference" -> MediaSlotType.REFERENCE_IMAGE
            "video" -> MediaSlotType.VIDEO_INPUT
            else -> error("Rol de medio desconocido: ${media.role}")
        }
    }
    return AttachedMedia(uriString = media.uri, mimeType = media.mimeType ?: if(media.role == "video") "video/*" else "image/*",
        slotType = slot, fileName = media.uri.substringAfterLast('/'),
        fieldKey = field?.takeIf { key -> model.contractMedia.any { it.key == key } })
}
