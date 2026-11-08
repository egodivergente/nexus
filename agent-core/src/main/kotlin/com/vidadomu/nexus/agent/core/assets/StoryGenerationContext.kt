package com.vidadomu.nexus.agent.core.assets

import kotlinx.serialization.Serializable

/** Immutable provenance captured when a draft is prepared, independent of subsequent selection. */
@Serializable
data class StoryGenerationContext(
    val projectId: String,
    val sceneId: String? = null,
    val shotId: String,
    val revision: Long,
    val context: String,
    val referenceAssetIds: List<String> = emptyList(),
    val fingerprint: String = "",
)
