package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*
import java.text.Normalizer
import com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice

internal enum class CatalogOrder(val label: String) {
    PRIORITY("Prioridad"), NAME("Nombre"), PRICE("Tarifa base")
}

internal fun modelBookmark(model: NexusModel) = "${model.providerId}:${model.id}"
private fun normalized(text: String) = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
private val searchCache = com.vidadomu.nexus.core.util.BoundedCache<NexusModel, String>(768)
internal fun modelSearchText(model: NexusModel): String = searchCache.getOrPut(model) {
    normalized(listOf(model.displayName, model.id, creator(model), catalogFamily(model),
        providerName(model.providerId), modelGuide(model).purpose, modelGuide(model).strength).joinToString(" "))
}

private val searchWarmup = CompletedSnapshotWarmup<NexusModel> { modelSearchText(it) }
internal suspend fun warmCatalogSearch(models: List<NexusModel>) = searchWarmup.warm(models)

/** A model may support several workflows; filters are not exclusive classifications. */
internal fun matchesUse(model: NexusModel, use: CreationUse): Boolean {
    if (use.modality !in model.modalities) return false
    if (modelUse(model) == use) return true
    return when (use) {
        CreationUse.VIDEO_TEXT -> ModelCapability.TEXT_TO_VIDEO in model.capabilities
        CreationUse.VIDEO_IMAGE -> ModelCapability.IMAGE_TO_VIDEO in model.capabilities
        CreationUse.VIDEO_REFS -> ModelCapability.REFERENCE_TO_VIDEO in model.capabilities
        CreationUse.VIDEO_EDIT -> model.capabilities.any { it in setOf(ModelCapability.VIDEO_EDIT, ModelCapability.VIDEO_TO_VIDEO, ModelCapability.VIDEO_EXTEND) }
        CreationUse.IMAGE_TEXT -> ModelCapability.TEXT_TO_IMAGE in model.capabilities
        CreationUse.IMAGE_EDIT -> model.capabilities.any { it in setOf(ModelCapability.IMAGE_EDITING, ModelCapability.IMAGE_TO_IMAGE) }
        CreationUse.IMAGE_REFS -> model.supportsReferenceImages || model.inputMode == ModelInputMode.MULTI_IMAGE
        else -> false
    }
}

internal fun browseCatalog(models: List<NexusModel>, modality: Modality, use: CreationUse?, query: String,
    manufacturer: String?, favoritesOnly: Boolean, favorites: Set<String>, order: CatalogOrder): List<NexusModel> {
    val words = normalized(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    val filtered = models.distinctBy(::modelBookmark).filter { model ->
        modality in model.modalities && (use == null || matchesUse(model, use)) &&
            (manufacturer == null || creator(model) == manufacturer) &&
            (!favoritesOnly || modelBookmark(model) in favorites) &&
            words.all { word -> modelSearchText(model).contains(word) }
    }
    return when (order) {
        CatalogOrder.PRIORITY -> filtered.sortedWith(compareByDescending<NexusModel> { modelBookmark(it) in favorites }
            .then(NexusModel.DEFAULT_COMPARATOR))
        CatalogOrder.NAME -> filtered.sortedBy { it.displayName.lowercase() }
        CatalogOrder.PRICE -> filtered.sortedWith(compareBy<NexusModel> { referencePrice(it) ?: Double.MAX_VALUE }
            .thenBy { it.displayName })
    }
}

internal fun referencePrice(model: NexusModel): Double? {
    val published = AtlasPublishedPrice.cached(model)
    if(published != null) return when {
        published.unit == "s" && Modality.VIDEO in model.modalities -> published.usd * 5
        published.unit == "imagen" && Modality.IMAGE in model.modalities -> published.usd
        else -> null
    }
    return fiveSecondPriceUsd(model)
}
