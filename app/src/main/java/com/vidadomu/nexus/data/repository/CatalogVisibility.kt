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

/**
 * Every hide key that would remove [route] from the catalogue. Computed once per model, so hiding is a set lookup
 * instead of re-running the family/group/tool classifiers (regex based) for every hidden key.
 */
internal class CatalogVisibilityKeys(val route: String, val keys: Set<String>)

internal fun catalogVisibilityKeys(model: NexusModel): CatalogVisibilityKeys {
    val route = routeVisibilityKey(model)
    val tool = toolCategory(model)
    val keys = buildSet {
        add(familyVisibilityKey(catalogFamily(model)))
        add(groupVisibilityKey(model))
        add(route)
        if (tool != null) { add(toolVisibilityKey(tool)); add(familyVisibilityKey(tool)) }
    }
    return CatalogVisibilityKeys(route, keys)
}

internal fun isCatalogModelVisible(model: NexusModel, hidden: Set<String>): Boolean =
    hidden.isEmpty() || catalogVisibilityKeys(model).keys.none { it in hidden }

/** O(models + hidden): classify each model once, then match keys by membership. */
internal fun captureHiddenRoutes(models: List<NexusModel>, hidden: Set<String>, previous: Map<String, Set<String>>): Map<String, Set<String>> {
    if (hidden.isEmpty()) return emptyMap()
    val matches = hidden.associateWith { mutableSetOf<String>() }
    models.asSequence().distinctBy { routeVisibilityKey(it) }.forEach { model ->
        val keys = catalogVisibilityKeys(model)
        keys.keys.forEach { key -> matches[key]?.add(keys.route) }
    }
    return hidden.associateWith { key -> previous[key].orEmpty() + matches.getValue(key) }
}

internal fun visibleCatalog(models: List<NexusModel>, hidden: Set<String>, captured: Map<String, Set<String>>): List<NexusModel> {
    if (hidden.isEmpty()) return models
    val routes = hidden.flatMap { captured[it].orEmpty() }.toSet()
    return models.filter { model ->
        val keys = catalogVisibilityKeys(model)
        keys.route !in routes && keys.keys.none { it in hidden }
    }
}
