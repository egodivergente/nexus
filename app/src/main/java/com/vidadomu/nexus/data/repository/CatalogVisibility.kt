package com.vidadomu.nexus.data.repository

import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.models.catalogFamily
import com.vidadomu.nexus.ui.models.modelGroupTitle
import java.util.Locale

internal fun familyVisibilityKey(family: String) = "family:$family"
internal fun groupVisibilityKey(model: NexusModel) = "group:${catalogFamily(model)}:${modelGroupTitle(model).lowercase(Locale.ROOT)}"
internal fun routeVisibilityKey(model: NexusModel) = "route:${model.providerId}:${model.id}"
internal fun isCatalogModelVisible(model: NexusModel, hidden: Set<String>): Boolean =
    familyVisibilityKey(catalogFamily(model)) !in hidden && groupVisibilityKey(model) !in hidden && routeVisibilityKey(model) !in hidden
