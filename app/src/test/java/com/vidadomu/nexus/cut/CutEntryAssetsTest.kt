package com.vidadomu.nexus.cut

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.core.model.GenerationOutput
import com.vidadomu.nexus.core.model.Modality
import org.junit.Assert.*
import org.junit.Test

class CutEntryAssetsTest {
    private fun asset(id: String, project: String? = null, scene: String? = null, shot: String? = null) = Asset(
        id, "file:///$id.mp4", AssetType.VIDEO, displayName = id, projectId = project, sceneId = scene, shotId = shot, createdAt = 0,
    )
    private fun link(id: String, asset: String, scope: String, target: String, project: String = "p") =
        StoryLink(id, project, asset, scope, target)
    private fun index(assets: List<Asset>, links: List<StoryLink> = emptyList(), blocks: List<StoryBlock> = emptyList()) = IndexData(
        projects = listOf(Project("p", "Proyecto", createdAt = 0)),
        scenes = listOf(Scene("later", "p", "2", order = 2), Scene("first", "p", "1", order = 1)),
        shots = listOf(Shot("shot-later", "p", "first", "2", order = 2), Shot("shot-first", "p", "first", "1", order = 1)),
        assets = assets, story = StoryState(links = links, blocks = blocks),
    )

    @Test fun sceneIncludesCrossProjectLinksAndLegacyButExcludesOtherScenesAndUnavailableMedia() {
        val data = index(
            listOf(asset("cross", "other"), asset("legacy", scene = "first"), asset("shot", shot = "shot-first"),
                asset("foreign", scene = "later"), asset("deleted").copy(libraryDeleted = true), asset("text").copy(type = AssetType.OTHER)),
            listOf(link("l", "cross", "scene", "first"), link("d", "deleted", "scene", "first"), link("t", "text", "scene", "first")),
        )
        assertEquals(listOf("shot", "cross", "legacy"), storyCutAssetIds(data, "p", "first"))
        assertTrue(storyCutAssetIds(data, "p", "missing").isEmpty())
    }

    @Test fun shotBlocksControlNarrativeOrderAndRepeatedAssociationsDoNotDuplicateFiles() {
        val data = index(
            listOf(asset("a"), asset("b"), asset("c"), asset("scene")),
            listOf(link("a", "a", "shot", "shot-first"), link("b", "b", "shot", "shot-later"),
                link("c", "c", "block", "material"), link("again", "a", "scene", "first"), link("s", "scene", "scene", "first")),
            listOf(StoryBlock("first-shot", "p", "first", "shot", shotId = "shot-first", order = 3),
                StoryBlock("later-shot", "p", "first", "shot", shotId = "shot-later", order = 1),
                StoryBlock("material", "p", "first", "material", order = 2)),
        )
        assertEquals(listOf("b", "c", "a", "scene"), storyCutAssetIds(data, "p", "first"))
    }

    @Test fun projectMountFollowsSceneOrderThenIncludesProjectMaterialOnce() {
        val data = index(listOf(asset("later", scene = "later"), asset("first", scene = "first"), asset("project", "p"), asset("linked", "other")),
            listOf(link("p", "linked", "project", "p"), link("duplicate", "first", "project", "p")))
        assertEquals(listOf("first", "later", "linked", "project"), storyCutAssetIds(data, "p"))
        assertTrue(storyCutAssetIds(data, "other").isEmpty())
    }

    @Test fun outputResolutionRejectsAmbiguousLocalCopiesAndTombstones() {
        val output = GenerationOutput("out", "job", Modality.VIDEO, "https://example.invalid/right.mp4", "file:///shared.mp4")
        val wrong = asset("wrong").copy(uri = output.localUri!!)
        val remote = asset("right").copy(uri = output.url)
        val data = IndexData(assets = listOf(wrong, remote.copy(libraryDeleted = true)))
        assertNull(cutOutputAsset(data, output, null))
        assertNull(cutOutputAsset(data.copy(assets = listOf(wrong, remote)), output, null))
        assertEquals("wrong", cutOutputAsset(data, output, output.localUri)?.id)
        assertNull(cutOutputAsset(data.copy(assets = listOf(wrong.copy(libraryDeleted = true))), output, output.localUri))
    }

    private fun generation(ids: List<String>) = GenerationRecord(
        id = "gen", nexusJobId = "job", providerId = "fixture", modelId = "fixture", modality = "VIDEO",
        prompt = "local test", status = "COMPLETED", outputAssetIds = ids, createdAt = 0,
    )

    @Test fun singleResultLocalizedAfterHistoryIndexingResolvesByGenerationIdentity() {
        val output = GenerationOutput("out", "job", Modality.VIDEO, "https://example.invalid/result.mp4")
        val localized = asset("localized").copy(generationId = "gen")
        val data = IndexData(assets = listOf(localized), generations = listOf(generation(listOf(localized.id))))
        assertEquals(localized, cutOutputAsset(data, output, null, listOf(output)))
        assertNull(cutOutputAsset(data, output, null, listOf(output.copy(id = "another-output"))))
        assertNull(cutOutputAsset(data.copy(assets = listOf(localized.copy(libraryDeleted = true))), output, null, listOf(output)))
    }

    @Test fun localizedFallbackNeverGuessesMultioutputPositionsOrReacceptsAnAmbiguousUri() {
        val output = GenerationOutput("out", "job", Modality.VIDEO, "https://example.invalid/result.mp4", "file:///ambiguous.mp4")
        val localized = asset("localized").copy(generationId = "gen")
        val record = generation(listOf(localized.id))
        val data = IndexData(assets = listOf(localized), generations = listOf(record))
        val another = output.copy(id = "second", url = "https://example.invalid/second.mp4")
        assertNull(cutOutputAsset(data, output, null, listOf(output, another)))
        assertNull(cutOutputAsset(data.copy(generations = listOf(record.copy(outputAssetIds = listOf(localized.id, "second")))), output, null, listOf(output)))
        assertNull(cutOutputAsset(data.copy(generations = listOf(record, record.copy(id = "duplicate"))), output, null, listOf(output)))
        assertNull(cutOutputAsset(data.copy(assets = listOf(localized.copy(uri = output.localUri!!))), output, null, listOf(output)))
        // A different, explicitly localized URI is safe even if an old output URI was rejected.
        assertEquals(localized, cutOutputAsset(data, output, null, listOf(output)))
    }
}
