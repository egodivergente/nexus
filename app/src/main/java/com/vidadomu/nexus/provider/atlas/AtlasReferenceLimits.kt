package com.vidadomu.nexus.provider.atlas

/** Verified provider limits omitted from Atlas's published Input schema.
 * MiniMax H3: https://github.com/MiniMax-AI/MiniMax-H3
 * Atlas confirmed this limit in failed prediction responses on 2026-10-09.
 * Keep stricter published schema limits; other models retain their own contracts.
 */
internal object AtlasReferenceLimits {
    private val imageMaximums = mapOf("minimax/h3/reference-to-video" to 9)
    fun images(modelId: String): Int = imageMaximums[modelId] ?: Int.MAX_VALUE
}
