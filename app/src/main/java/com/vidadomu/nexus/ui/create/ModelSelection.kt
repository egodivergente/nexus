package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*

/** The same routing used after schema loading. Never guesses support from a display name. */
internal fun compatibleModelMedia(model: NexusModel, attached: List<AttachedMedia>): List<AttachedMedia> {
        if(model.contractEndpoint!=null) return attached.filter { media -> model.contractMedia.any { it.key==media.fieldKey } }
        val images = attached.filter { it.fieldKey==null && it.mimeType.startsWith("image/") }
        val videos = attached.filter { it.fieldKey==null && it.mimeType.startsWith("video/") }
        return buildList {
            addAll(attached.filter {media -> media.fieldKey!=null && model.contractMedia.any {it.key==media.fieldKey}})
            val remaining=images.toMutableList()
            fun take(preferred:MediaSlotType,allowLast:Boolean):AttachedMedia? {
                val match=remaining.firstOrNull {it.slotType==preferred}
                    ?: remaining.firstOrNull {allowLast || it.slotType!=MediaSlotType.LAST_FRAME}
                if(match!=null) remaining.remove(match)
                return match
            }
            if (model.firstFrameField != null) take(MediaSlotType.PRIMARY_IMAGE,false)?.let { add(it.copy(slotType = MediaSlotType.PRIMARY_IMAGE)) }
            if (model.lastFrameField != null) take(MediaSlotType.LAST_FRAME,true)?.let { add(it.copy(slotType = MediaSlotType.LAST_FRAME)) }
            if (model.imagesArrayField != null) addAll(remaining.take(model.maxImages).map { it.copy(slotType = MediaSlotType.REFERENCE_IMAGE) })
            if (model.videoField != null) videos.firstOrNull()?.let { add(it.copy(slotType = MediaSlotType.VIDEO_INPUT)) }
            if (model.videosArrayField != null) addAll(videos.drop(if(model.videoField != null) 1 else 0).take(model.maxVideos).map { it.copy(slotType = MediaSlotType.VIDEO_REFERENCE) })
        }
    }

internal fun retainsAllReferences(model: NexusModel, media: List<AttachedMedia>): Boolean {
    val kept=compatibleModelMedia(model,media).map {it.id}.toSet()
    if(!kept.containsAll(media.map {it.id})) return false
    return model.contractMedia.all {field ->
        val inputs=media.filter {it.fieldKey==field.key}
        inputs.size<=field.maximum && inputs.all {item ->
            field.mime=="*/*" || item.mimeType==field.mime ||
                field.mime.endsWith("/*") && item.mimeType.substringBefore('/')==field.mime.substringBefore('/')
        }
    }
}

internal fun filterCanvasModels(models: List<NexusModel>, modality: Modality?,
    media: List<AttachedMedia>, compatibleOnly: Boolean, organization: String?, query: String): List<NexusModel> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return models.filter { model ->
        (modality == null || modality in model.modalities) &&
            (!compatibleOnly || retainsAllReferences(model, media)) &&
            (organization == null || model.organization.equals(organization, true)) &&
            words.all { word -> listOf(model.displayName, model.id, model.organization,
                model.family.orEmpty(), com.vidadomu.nexus.ui.models.catalogFamily(model),
                com.vidadomu.nexus.ui.models.modelUse(model).label).any { word in it.lowercase() } }
    }
}
