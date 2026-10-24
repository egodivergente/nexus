package com.vidadomu.nexus.data.repository

import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.models.catalogFamily
import com.vidadomu.nexus.ui.models.modelGroupTitle
import com.vidadomu.nexus.ui.models.toolCategory
import java.util.Locale

internal fun familyVisibilityKey(family: String) = "family:$family"
internal fun groupVisibilityKey(model: NexusModel) = "group:${catalogFamily(model)}:${modelGroupTitle(model).lowercase(Locale.ROOT)}"
internal fun routeVisibilityKey(model: NexusModel) = "route:${model.providerId}:${model.id}"
internal fun toolVisibilityKey(category: String) = "tool:$category"
internal fun isCatalogModelVisible(model: NexusModel, hidden: Set<String>): Boolean =
    familyVisibilityKey(catalogFamily(model)) !in hidden && groupVisibilityKey(model) !in hidden && routeVisibilityKey(model) !in hidden &&
        toolCategory(model)?.let { toolVisibilityKey(it) !in hidden && familyVisibilityKey(it) !in hidden } != false

internal fun captureHiddenRoutes(models: List<NexusModel>, hidden: Set<String>, previous: Map<String, Set<String>>): Map<String, Set<String>> =
    hidden.associateWith { key ->
        previous[key].orEmpty() + models.filterNot { isCatalogModelVisible(it, setOf(key)) }.map(::routeVisibilityKey)
    }

internal fun visibleCatalog(models: List<NexusModel>, hidden: Set<String>, captured: Map<String, Set<String>>): List<NexusModel> {
    val routes = hidden.flatMap { captured[it].orEmpty() }.toSet()
    return models.filter { isCatalogModelVisible(it, hidden) && routeVisibilityKey(it) !in routes }
}
