package com.vidadomu.nexus.agent.core.assets

/**
 * Current Library membership, shared by search and project projections. A generation's original
 * project is provenance, never a reason to reattach an output that was moved or unassigned later.
 * This projection reads existing identities and associations; it never changes their owner.
 */
class ProjectAssetMembership(index: IndexData) {
    private val scenes = index.scenes.associateBy { it.id }
    private val shots = index.shots.associateBy { it.id }
    private val blocks = index.story.blocks.associateBy { it.id }
    private val links = index.story.links.groupBy { it.assetId }

    fun projectIds(asset: Asset): Set<String> {
        if (asset.libraryDeleted) return emptySet()
        return buildSet {
            asset.projectId?.let(::add)
            scenes[asset.sceneId]?.projectId?.let(::add)
            shots[asset.shotId]?.projectId?.let(::add)
            links[asset.id].orEmpty().forEach { add(it.projectId) }
        }
    }

    fun inProject(asset: Asset, projectId: String): Boolean = !asset.libraryDeleted &&
        (asset.projectId == projectId || scenes[asset.sceneId]?.projectId == projectId ||
            shots[asset.shotId]?.projectId == projectId || links[asset.id].orEmpty().any { it.projectId == projectId })

    fun inScenes(asset: Asset, sceneIds: Set<String>): Boolean {
        if (asset.libraryDeleted) return false
        if (asset.sceneId in sceneIds || shots[asset.shotId]?.sceneId in sceneIds) return true
        return links[asset.id].orEmpty().any { link ->
            when (link.scope) {
                "scene" -> link.targetId in sceneIds
                "shot" -> shots[link.targetId]?.sceneId in sceneIds
                "block" -> blocks[link.targetId]?.sceneId in sceneIds
                else -> false
            }
        }
    }

    fun inShots(asset: Asset, shotIds: Set<String>): Boolean {
        if (asset.libraryDeleted) return false
        if (asset.shotId in shotIds) return true
        return links[asset.id].orEmpty().any { link ->
            when (link.scope) {
                "shot" -> link.targetId in shotIds
                "block" -> blocks[link.targetId]?.shotId in shotIds
                else -> false
            }
        }
    }
}
