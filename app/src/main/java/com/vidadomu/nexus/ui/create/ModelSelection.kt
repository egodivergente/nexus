package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*

/** The same routing used after schema loading. Never guesses support from a display name. */
internal fun compatibleModelMedia(model: NexusModel, attached: List<AttachedMedia>): List<AttachedMedia> {
    val fields = modelMediaHandoffFields(model)
    val roles = fields.associate { it.key to modelMediaRole(model, it) }
    val unique = roles.values.filterNotNull().groupingBy { it }.eachCount()
    val remaining = attached.distinctBy { it.id }.toMutableList()
    return buildList {
        // A foreign non-null key is never reinterpreted without canonicalModelMedia's source context.
        for (field in fields) {
            val bound = remaining.filter { field.contract != null && it.fieldKey == field.key &&
                mediaBindingMatches(model, it) && field.acceptsMime(it.mimeType) }
                .take(field.maximum)
            addAll(bound)
            remaining.removeAll { item -> bound.any { it.id == item.id } }
        }
        for (field in fields) {
            val role = roles[field.key]?.takeIf { unique[it] == 1 } ?: continue
            val occupied = count { field.contains(it) }
            val room = (field.maximum - occupied).coerceAtLeast(0)
            if (room == 0) continue
            val initialPresent = any { item -> fields.any { roles[it.key] == MediaSlotType.PRIMARY_IMAGE && it.contains(item) } }
            val preferred = remaining.filter { it.fieldKey == null && mediaBindingMatches(model, it) &&
                it.slotType == role && field.acceptsMime(it.mimeType) }
            val fallbacks = remaining.filter { item ->
                item.fieldKey == null && mediaBindingMatches(model, item) && item !in preferred && field.acceptsMime(item.mimeType) && when (role) {
                    MediaSlotType.PRIMARY_IMAGE -> item.slotType == MediaSlotType.REFERENCE_IMAGE
                    MediaSlotType.LAST_FRAME -> item.slotType in setOf(MediaSlotType.PRIMARY_IMAGE, MediaSlotType.REFERENCE_IMAGE) &&
                        initialPresent
                    MediaSlotType.REFERENCE_IMAGE -> item.slotType == MediaSlotType.PRIMARY_IMAGE
                    MediaSlotType.VIDEO_INPUT -> item.slotType == MediaSlotType.VIDEO_REFERENCE
                    MediaSlotType.VIDEO_REFERENCE -> item.slotType == MediaSlotType.VIDEO_INPUT
                }
            }
            val selected = (preferred + fallbacks).take(room)
            addAll(selected.map { it.copy(fieldKey = field.contract?.key, slotType = role) })
            remaining.removeAll { item -> selected.any { it.id == item.id } }
        }
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
