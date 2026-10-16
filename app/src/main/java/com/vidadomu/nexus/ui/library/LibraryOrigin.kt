package com.vidadomu.nexus.ui.library

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetSource

/** Distinguishes the source shown to users from a generation's technical provider. */
internal enum class LibraryOriginFilter(val label: String) {
    ALL("Todo"),
    GENERATED("Generado por Nexus"),
    IMPORTED("Importado por ti"),
    EDITED("Editado en Nexus"),
    SHARED("Compartido"),
}

internal fun libraryOrigin(asset: Asset): LibraryOriginFilter = when {
    asset.source == AssetSource.GENERATED -> LibraryOriginFilter.GENERATED
    asset.source == AssetSource.SHARED -> LibraryOriginFilter.SHARED
    asset.parentAssetId != null -> LibraryOriginFilter.EDITED
    else -> LibraryOriginFilter.IMPORTED
}

internal fun Asset.matchesLibraryOrigin(filter: LibraryOriginFilter): Boolean =
    filter == LibraryOriginFilter.ALL || libraryOrigin(this) == filter

internal fun LibraryOriginFilter.shortLabel(): String = when (this) {
    LibraryOriginFilter.ALL -> "Todo"
    LibraryOriginFilter.GENERATED -> "Generado"
    LibraryOriginFilter.IMPORTED -> "Importado"
    LibraryOriginFilter.EDITED -> "Editado"
    LibraryOriginFilter.SHARED -> "Compartido"
}
