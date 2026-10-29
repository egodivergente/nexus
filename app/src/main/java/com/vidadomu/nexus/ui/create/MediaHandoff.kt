package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*

/**
 * Only a model switch has enough context to release an endpoint-specific binding.
 * The original source draft must be saved first. Unknown/ambiguous inputs and masks
 * keep their binding; all file identity and location metadata stays unchanged.
 */
internal fun canonicalModelMedia(source: NexusModel, media: List<AttachedMedia>): List<AttachedMedia> {
    val fields = modelMediaHandoffFields(source)
    val roles = fields.associate { it.key to modelMediaRole(source, it) }
    val unique = roles.values.filterNotNull().groupingBy { it }.eachCount()
    return media.map { item ->
        if (item.fieldKey == null || !mediaBindingMatches(source, item)) return@map item
        val field = fields.firstOrNull { it.key == item.fieldKey && it.acceptsMime(item.mimeType) }
        val role = field?.let { roles[it.key] }?.takeIf { unique[it] == 1 }
        if (role == null) item.copy(bindingOwner = item.bindingOwner ?: mediaBindingOwner(source))
        else item.copy(fieldKey = null, slotType = role, bindingOwner = null)
    }
}

private fun mediaBindingOwner(model: NexusModel) =
    MediaBindingOwner(model.providerId, model.id, model.contractEndpoint ?: model.atlasEndpoint)

internal fun mediaBindingMatches(model: NexusModel, media: AttachedMedia): Boolean =
    media.bindingOwner == null || media.bindingOwner == mediaBindingOwner(model)

/** Declared contract fields plus the existing native media metadata, without provider-name rules. */
internal fun modelMediaHandoffFields(model: NexusModel): List<CanvasInputField> = buildList {
    model.contractMedia.filter { it.maximum > 0 }.forEach { field ->
        add(CanvasInputField(field.key, field.label, field.mime, field.required, field.maximum,
            MediaSlotType.REFERENCE_IMAGE, field))
    }
    if (model.contractEndpoint == null) {
        fun addNative(key: String?, mime: String, maximum: Int, slot: MediaSlotType) {
            if (key != null && maximum > 0 && none { it.key == key })
                add(CanvasInputField(key, key, mime, key in model.nativeRequiredFields, maximum, slot))
        }
        addNative(model.firstFrameField ?: model.inputImageField?.takeIf { it != model.imagesArrayField },
            "image/*", 1, MediaSlotType.PRIMARY_IMAGE)
        addNative(model.lastFrameField, "image/*", 1, MediaSlotType.LAST_FRAME)
        addNative(model.imagesArrayField, "image/*", model.maxImages, MediaSlotType.REFERENCE_IMAGE)
        addNative(model.videoField, "video/*", 1, MediaSlotType.VIDEO_INPUT)
        addNative(model.videosArrayField, "video/*", model.maxVideos, MediaSlotType.VIDEO_REFERENCE)
    }
}.distinctBy { it.key }

/** A role comes from declared metadata/type/cardinality or an unambiguous semantic field. */
internal fun modelMediaRole(model: NexusModel, field: CanvasInputField): MediaSlotType? {
    fun tokens(key: String) = key.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase().split(Regex("[^a-z0-9]+"))
    fun mask(key: String) = tokens(key).any { it in setOf("mask", "masks") } ||
        model.parameters.any { it.key == key && it.type == ParameterType.MASK }
    if (mask(field.key)) return null

    val declared = buildSet {
        if (field.key == model.firstFrameField || field.key == model.inputImageField && field.key != model.imagesArrayField)
            add(MediaSlotType.PRIMARY_IMAGE)
        if (field.key == model.lastFrameField) add(MediaSlotType.LAST_FRAME)
        if (field.key == model.imagesArrayField) add(MediaSlotType.REFERENCE_IMAGE)
        if (field.key == model.videoField) add(MediaSlotType.VIDEO_INPUT)
        if (field.key == model.videosArrayField) add(MediaSlotType.VIDEO_REFERENCE)
        model.parameters.filter { it.key == field.key }.forEach { parameter ->
            when (parameter.type) {
                ParameterType.IMAGE, ParameterType.FIRST_FRAME -> add(MediaSlotType.PRIMARY_IMAGE)
                ParameterType.LAST_FRAME -> add(MediaSlotType.LAST_FRAME)
                ParameterType.REFERENCE_IMAGES -> add(MediaSlotType.REFERENCE_IMAGE)
                ParameterType.VIDEO -> add(MediaSlotType.VIDEO_INPUT)
                else -> Unit
            }
        }
    }
    if (declared.isNotEmpty()) return declared.singleOrNull()
    if (field.contract == null) return field.slot

    val words = tokens(field.key)
    val array = field.contract.encoding == "array" || field.maximum > 1
    if (field.mime.startsWith("image/")) {
        val first = words.any { it in setOf("first", "initial", "start") }
        val last = words.any { it in setOf("last", "final", "end") }
        if (first && last) return null
        if (last) return MediaSlotType.LAST_FRAME
        if (first) return MediaSlotType.PRIMARY_IMAGE
        if (array || words.any { it in setOf("reference", "references", "ref", "refs", "refers") })
            return MediaSlotType.REFERENCE_IMAGE
        if (model.contractMedia.count { it.mime.startsWith("image/") && !mask(it.key) } == 1)
            return MediaSlotType.PRIMARY_IMAGE
    }
    if (field.mime.startsWith("video/")) {
        if (array) return MediaSlotType.VIDEO_REFERENCE
        if (model.contractMedia.count { it.mime.startsWith("video/") } == 1) return MediaSlotType.VIDEO_INPUT
    }
    return null
}
