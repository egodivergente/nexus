package com.vidadomu.nexus.cut

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.Modality

/** Resolves narrative associations without changing the Library or the montage. */
internal fun storyCutAssetIds(index: IndexData, projectId: String, sceneId: String? = null): List<String> {
    if (index.projects.none { it.id == projectId }) return emptyList()
    val scenes = index.scenes.filter { it.projectId == projectId && (sceneId == null || it.id == sceneId) }
        .sortedWith(compareBy({ it.order }, { it.id }))
    if (sceneId != null && scenes.isEmpty()) return emptyList()
    val assets = index.assets.filter { !it.libraryDeleted && it.type != AssetType.OTHER }.associateBy { it.id }
    val links = index.story.links.filter { it.projectId == projectId }
    val shots = index.shots.filter { it.projectId == projectId }.sortedWith(compareBy({ it.order }, { it.id }))
    val result = linkedSetOf<String>()
    fun addTarget(scope: String, target: String, legacy: (Asset) -> Boolean = { false }) {
        val linked = links.filter { it.scope == scope && it.targetId == target }.map { it.assetId }.toSet()
        assets.values.filter { it.id in linked || legacy(it) }
            .sortedWith(compareBy({ it.createdAt }, { it.id })).forEach { result += it.id }
    }
    for (scene in scenes) {
        val sceneShots = shots.filter { it.sceneId == scene.id }
        val placedShots = mutableSetOf<String>()
        // A shot block fixes that shot's place among action/dialogue/material blocks.
        index.story.blocks.filter { it.projectId == projectId && it.sceneId == scene.id }
            .sortedWith(compareBy({ it.order }, { it.id })).forEach { block ->
                addTarget("block", block.id)
                block.shotId?.takeIf { id -> sceneShots.any { it.id == id } }?.let { id ->
                    addTarget("shot", id) { it.shotId == id }; placedShots += id
                }
            }
        sceneShots.filter { it.id !in placedShots }.forEach { shot -> addTarget("shot", shot.id) { it.shotId == shot.id } }
        addTarget("scene", scene.id) { it.sceneId == scene.id }
    }
    if (sceneId == null) {
        shots.filter { it.sceneId == null }.forEach { shot -> addTarget("shot", shot.id) { it.shotId == shot.id } }
        addTarget("project", projectId) { it.projectId == projectId }
    }
    return result.toList()
}

/** An ambiguous local copy must never resolve to a different generation's file. */
internal fun cutOutputAsset(
    index: IndexData, output: GenerationOutput, trustedLocalUri: String?,
    generationOutputs: List<GenerationOutput> = emptyList(),
): Asset? {
    val type = when (output.type) {
        Modality.IMAGE -> AssetType.IMAGE
        Modality.VIDEO -> AssetType.VIDEO
        Modality.AUDIO -> AssetType.AUDIO
        else -> return null
    }
    fun usable(asset: Asset): Boolean = !asset.libraryDeleted && asset.type == type && isLocalCutUri(asset.uri) &&
        !(trustedLocalUri == null && asset.uri == output.localUri)
    val uri = (trustedLocalUri ?: output.url).takeIf(::isLocalCutUri)
    index.assets.firstOrNull { usable(it) && it.uri == uri }?.let { return it }

    // AssetLocalizer can change the Asset URI after history has already been indexed. Only a
    // genuinely single-result job and generation give an unambiguous identity for that new URI.
    val onlyOutput = generationOutputs.singleOrNull() ?: return null
    if (onlyOutput.id != output.id || onlyOutput.jobId != output.jobId || onlyOutput.url != output.url) return null
    val generation = index.generations.singleOrNull { it.nexusJobId == output.jobId } ?: return null
    val assetId = generation.outputAssetIds.singleOrNull() ?: return null
    return index.assets.singleOrNull { it.id == assetId }?.takeIf(::usable)
}

internal fun isLocalCutUri(uri: String): Boolean = uri.startsWith("file://") || uri.startsWith("content://")
