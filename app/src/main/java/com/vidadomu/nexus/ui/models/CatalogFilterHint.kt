package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.Modality

/** Visible and spoken names of the filters cleared by «Quitar filtros activos». */
internal fun activeCatalogFilterLabels(
    providerLabel: String?, use: CreationUse?, favoritesOnly: Boolean, modality: Modality, imageReferences: Boolean
): List<String> = listOfNotNull(
    providerLabel?.let { "Proveedor: $it" },
    use?.let { "Uso: ${it.label}" },
    // The references switch only narrows the image catalog; elsewhere it is ignored by the browse filter.
    "Con referencias".takeIf { modality == Modality.IMAGE && imageReferences },
    "Solo favoritos".takeIf { favoritesOnly }
)

internal fun filterButtonLabel(activeCount: Int): String = if (activeCount > 0) "Filtros ($activeCount)" else "Filtros"

internal fun filterButtonDescription(activeCount: Int): String = when (activeCount) {
    0 -> "Filtros y orden, ninguno activo"
    1 -> "Filtros y orden, 1 filtro activo"
    else -> "Filtros y orden, $activeCount filtros activos"
}

/** Accessible alternative to dragging: moves one position using the same persistence as the drag gesture. */
internal fun moveCatalogItemBy(saved: List<String>, visible: List<String>, key: String, offset: Int): List<String> {
    val index = visible.indexOf(key)
    if (index < 0 || offset == 0) return saved
    val target = (index + offset).coerceIn(0, visible.lastIndex)
    if (target == index) return saved
    return moveCatalogItem(saved, visible, key, visible[target])
}
