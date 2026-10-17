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

/** Un elemento de la cuadrícula: salida de un trabajo de Nexus o archivo local; [time] los ordena en una sola línea de tiempo. */
internal sealed interface LibraryEntry {
    val time: Long
    val key: String
    data class Out(val job: com.vidadomu.nexus.core.model.GenerationJob, val index: Int) : LibraryEntry {
        override val time get() = job.startedAt
        override val key get() = "${job.id}:${job.outputs[index].id}:$index"
    }
    data class Local(val asset: Asset) : LibraryEntry {
        override val time get() = asset.createdAt
        override val key get() = "asset:" + asset.id
    }
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
