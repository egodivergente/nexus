package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType

/** Only imported media from this project; generated results remain in Biblioteca. */
internal fun projectMediaChoices(
    assets: List<Asset>, projectId: String?, mimeTypes: List<String>, query: String = "",
): List<Asset> {
    if (projectId == null) return emptyList()
    return assets.asSequence().filter { asset ->
        val mime = asset.mimeType ?: when (asset.type) {
            AssetType.IMAGE -> "image/*"
            AssetType.VIDEO -> "video/*"
            else -> "application/octet-stream"
        }
        asset.projectId == projectId && asset.source != AssetSource.GENERATED &&
            mimeTypes.any { allowed -> allowed == "*/*" || allowed == mime ||
                (allowed.endsWith("/*") && mime.startsWith(allowed.substringBefore('/') + "/")) } &&
            asset.displayName.contains(query.trim(), ignoreCase = true)
    }.distinctBy { it.uri }.sortedByDescending { it.createdAt }.toList()
}
