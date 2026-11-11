package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*
import org.junit.jupiter.api.Test

class ProjectAssetMembershipTest {
    private fun asset(id: String, owner: String? = null) = Asset(
        id, "file:///$id.mp4", AssetType.VIDEO, displayName = id, projectId = owner, createdAt = 10,
    )
    private fun data(assets: List<Asset>, links: List<StoryLink> = emptyList(), generations: List<GenerationRecord> = emptyList()) = IndexData(
        projects = listOf(Project("a", "Álpha", createdAt = 1), Project("b", "Beta", createdAt = 1)),
        scenes = listOf(Scene("scene-a", "a", "1", "Jardín"), Scene("scene-b", "b", "1", "Mar")),
        shots = listOf(Shot("shot-a", "a", "scene-a", "1"), Shot("shot-b", "b", "scene-b", "1")),
        assets = assets, generations = generations,
        story = StoryState(blocks = listOf(StoryBlock("block-a", "a", "scene-a", "shot", shotId = "shot-a")), links = links),
    )
    private fun link(id: String, asset: String, scope: String, target: String, project: String = "a") = StoryLink(id, project, asset, scope, target)
    private fun generation(id: String, owner: String, outputs: List<String>) = GenerationRecord(
        id, providerId = "fixture", modelId = "fixture-video", modality = "VIDEO", prompt = "luz suave",
        projectId = owner, status = "COMPLETED", outputAssetIds = outputs, createdAt = 1,
    )
    private fun <T> withIndex(snapshot: IndexData, block: (AssetIndex) -> T): T {
        val dir = Files.createTempDirectory("project-membership").toFile()
        try {
            val file = java.io.File(dir, "index.json").apply { writeText(Json.encodeToString(snapshot)) }
            return block(AssetIndex(file))
        } finally { dir.deleteRecursively() }
    }

    @Test fun currentOwnersSharedLinksAndLegacyAssociationsAgreeWithoutChangingOwnership() {
        val snapshot = data(listOf(asset("owned", "a"), asset("shared", "b"), asset("scene").copy(sceneId = "scene-a"),
            asset("shot").copy(shotId = "shot-a"), asset("foreign", "b"), asset("removed", "a").copy(libraryDeleted = true)),
            listOf(link("share", "shared", "project", "a"), link("same", "shared", "scene", "scene-a")))
        val membership = ProjectAssetMembership(snapshot)
        assertEquals(setOf("owned", "shared", "scene", "shot"), snapshot.assets.filter { membership.inProject(it, "a") }.map { it.id }.toSet())
        assertEquals(setOf("a", "b"), membership.projectIds(snapshot.assets.single { it.id == "shared" }))
        withIndex(snapshot) { index ->
            val found = index.search(AssetQuery(projectId = "alpha"))
            assertEquals(setOf("owned", "shared", "scene", "shot"), found.map { it.id }.toSet())
            assertEquals(4, found.size)
            assertEquals("b", found.single { it.id == "shared" }.projectId)
            assertEquals(snapshot, index.snapshot())
        }
    }

    @Test fun sceneAndShotQueriesFollowSharedNarrativeLinksIncludingBlocks() {
        val snapshot = data(listOf(asset("scene-link", "b"), asset("shot-link", "b"), asset("block-link", "b"),
            asset("legacy-scene").copy(sceneId = "scene-a"), asset("legacy-shot").copy(shotId = "shot-a"), asset("foreign", "b")),
            listOf(link("s", "scene-link", "scene", "scene-a"), link("p", "shot-link", "shot", "shot-a"),
                link("b", "block-link", "block", "block-a")))
        withIndex(snapshot) { index ->
            assertEquals(setOf("scene-link", "shot-link", "block-link", "legacy-scene", "legacy-shot"),
                index.search(AssetQuery(projectId = "a", sceneCode = "jardin")).map { it.id }.toSet())
            assertEquals(setOf("shot-link", "block-link", "legacy-shot"), index.search(AssetQuery(projectId = "a", shotCode = "1")).map { it.id }.toSet())
            assertTrue(index.search(AssetQuery(projectId = "b", sceneCode = "1")).isEmpty())
        }
    }

    @Test fun oldGenerationProvenanceDoesNotReattachMovedOrUnassignedOutputs() {
        val moved = asset("moved", "b").copy(generationId = "old-a")
        val unassigned = asset("unassigned").copy(generationId = "unassigned-a")
        val explicitlyShared = asset("shared", "b").copy(generationId = "shared-a")
        val snapshot = data(listOf(moved, unassigned, explicitlyShared), listOf(link("share", "shared", "project", "a")),
            listOf(generation("old-a", "a", listOf("moved")), generation("unassigned-a", "a", listOf("unassigned")), generation("shared-a", "a", listOf("shared"))))
        withIndex(snapshot) { index ->
            assertEquals(listOf("shared"), index.search(AssetQuery(projectId = "a")).map { it.id })
            assertEquals(setOf("moved", "shared"), index.search(AssetQuery(projectId = "b", modelId = "fixture-video", text = "luz suave")).map { it.id }.toSet())
            assertEquals(setOf("moved", "shared", "unassigned"), index.search(AssetQuery()).map { it.id }.toSet())
            assertEquals(snapshot, index.snapshot())
        }
    }

    @Test fun anUnknownProjectDoesNotTurnAFilteredSearchIntoAGlobalSearch() {
        withIndex(data(listOf(asset("private-a", "a"), asset("private-b", "b")))) { index ->
            assertTrue(index.search(AssetQuery(projectId = "does-not-exist")).isEmpty())
            assertEquals(2, index.search(AssetQuery()).size)
            assertEquals(listOf("private-a"), index.search(AssetQuery(projectId = "a", type = AssetType.VIDEO, limit = 1)).map { it.id })
        }
    }
}
