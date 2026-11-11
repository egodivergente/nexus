package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.tools.Attachment

/** A result remains an indexed asset identity, even if it was renamed, moved or deleted since the tool ran. */
internal fun currentCutLibraryResults(results: List<Attachment>, index: IndexData): List<Attachment> {
    val byId = index.assets.associateBy { it.id }
    return results.mapNotNull { result ->
        val asset = byId[result.assetId ?: result.id]?.takeIf {
            !it.libraryDeleted && it.type in setOf(AssetType.IMAGE, AssetType.VIDEO)
        } ?: return@mapNotNull null
        Attachment(asset.id, asset.uri,
            asset.mimeType ?: if (asset.type == AssetType.VIDEO) "video/mp4" else "image/*",
            asset.displayName, asset.width, asset.height, asset.durationSec, asset.id)
    }.distinctBy { it.assetId }
}

internal fun presentCutLibraryResults(state: CutChatUiState, results: List<Attachment>, index: IndexData): CutChatUiState {
    val current = currentCutLibraryResults(results, index)
    return state.copy(libraryResults = current, libraryResultsOpen = current.isNotEmpty())
}
