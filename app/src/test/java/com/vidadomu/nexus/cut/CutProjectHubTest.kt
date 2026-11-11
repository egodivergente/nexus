package com.vidadomu.nexus.cut

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.GenerationRecord
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.Scene
import com.vidadomu.nexus.agent.core.assets.Shot
import com.vidadomu.nexus.agent.core.assets.StoryLink
import com.vidadomu.nexus.agent.core.assets.StoryState
import com.vidadomu.nexus.cut.command.CoreCapabilities
import com.vidadomu.nexus.cut.command.CutLibrary
import com.vidadomu.nexus.cut.command.CutRegistry
import com.vidadomu.nexus.cut.command.CutRenderer
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.DeviceCapabilities
import com.vidadomu.nexus.cut.command.ExportOverrides
import com.vidadomu.nexus.cut.command.LibraryMedia
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CutProjectHubTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun project(id: String, archived: Boolean = false, at: Long = 1) = Project(id, "Proyecto $id", createdAt = at, archived = archived)
    private fun montage(id: String, projectId: String?, at: Long = 1, error: String? = null) =
        ProjectStore.Summary(id, "Montaje $id", at, error = error, nexusProjectId = projectId)
    private fun asset(id: String, projectId: String? = null, at: Long = 1) =
        Asset(id, "file:///$id.mp4", AssetType.VIDEO, displayName = id, projectId = projectId, createdAt = at)
    private fun generation(id: String, projectId: String?, outputs: List<String> = emptyList(), at: Long = 1) =
        GenerationRecord(id, providerId = "saved", modelId = "saved", modality = "VIDEO", prompt = "Saved", projectId = projectId,
            status = "COMPLETED", outputAssetIds = outputs, createdAt = at, completedAt = at)

    private fun fixture(data: IndexData): Triple<CutProjectHub, AssetIndex, CutWorkspace> {
        val file = temporary.newFile("index-${System.nanoTime()}.json").apply { writeText(Json.encodeToString(data)) }
        val index = AssetIndex(file, clock = { 100L })
        val registry = CutRegistry { setOf(DeviceCapabilities.LIBRARY, DeviceCapabilities.EXPORT) }
            .register(CoreCapabilities.all() + DeviceCapabilities.all(CutLibrary { id ->
                val media = index.asset(id) ?: error("Unknown fixture")
                LibraryMedia(MediaRef("", media.uri, media.displayName, MediaKind.VIDEO, 2_000_000, 1280, 720, hasVideo = true))
            }, object : CutRenderer {
                override suspend fun start(session: CutSession, overrides: ExportOverrides): JsonObject = error("No renderer in hub tests")
                override fun status(): JsonObject = error("No renderer in hub tests")
                override fun cancel(): Boolean = error("No renderer in hub tests")
            }))
        val workspace = CutWorkspace(ProjectStore(temporary.newFolder()), registry, clock = { 200L }, io = Dispatchers.Unconfined)
        return Triple(CutProjectHub(index, workspace), index, workspace)
    }

    @Test fun everyProjectRemainsVisibleAndMissingOwnersStayAccessibleWithoutReassignment() {
        val data = IndexData(projects = listOf(project("empty"), project("active"), project("archived", archived = true)))
        val summaries = listOf(montage("owned", "active"), montage("archived-edit", "archived"),
            montage("loose", null), montage("orphan", "missing", error = "No se pudo abrir"))
        val before = data.copy()
        val result = projectCutHub(data, summaries)
        assertEquals(setOf("empty", "active", "archived"), result.projects.map { it.id }.toSet())
        assertTrue(result.projects.single { it.id == "empty" }.montages.isEmpty())
        assertEquals(listOf("owned"), result.projects.single { it.id == "active" }.montages.map { it.id })
        val archived = result.projects.single { it.id == "archived" }
        assertTrue(archived.archived)
        assertEquals(listOf("archived-edit"), archived.montages.map { it.id })
        assertEquals(setOf("loose", "orphan"), result.looseMontages.map { it.id }.toSet())
        assertEquals("missing", result.looseMontages.single { it.id == "orphan" }.nexusProjectId)
        assertEquals("No se pudo abrir", result.looseMontages.single { it.id == "orphan" }.error)
        assertEquals(before, data)
    }

    @Test fun sharedMaterialIsCountedOncePerProjectWithoutLeakingUnrelatedAssets() {
        val material = listOf(asset("own", "a"), asset("shared", "b"), asset("scene").copy(sceneId = "scene-a"),
            asset("shot").copy(shotId = "shot-a"), asset("output", "a"), asset("foreign", "b"), asset("deleted", "a").copy(libraryDeleted = true))
        val data = IndexData(
            projects = listOf(project("a"), project("b")), assets = material,
            scenes = listOf(Scene("scene-a", "a", "1")), shots = listOf(Shot("shot-a", "a", "scene-a", "1")),
            generations = listOf(generation("a-gen", "a", listOf("output")), generation("foreign-gen", "b", listOf("foreign"))),
            story = StoryState(links = listOf(StoryLink("one", "a", "shared", "project", "a"),
                StoryLink("again", "a", "shared", "scene", "scene-a"))),
        )
        val result = projectCutHub(data, emptyList())
        val a = result.projects.single { it.id == "a" }
        assertEquals(setOf("own", "shared", "scene", "shot", "output"), a.material.map { it.id }.toSet())
        assertEquals(5, a.materialCount)
        assertEquals(1, a.sceneCount)
        assertEquals(1, a.shotCount)
        assertEquals(listOf("a-gen"), a.generations.map { it.id })
        val b = result.projects.single { it.id == "b" }
        assertEquals(setOf("shared", "foreign"), b.material.map { it.id }.toSet())
        assertEquals("b", a.material.single { it.id == "shared" }.projectId)
        assertEquals(material, data.assets)
    }

    @Test fun currentOutputAssignmentsOverrideOldGenerationOwnershipUnlessThereIsAnExplicitSharedLink() {
        val material = listOf(asset("moved", "b").copy(generationId = "old-a"),
            asset("unassigned").copy(generationId = "unassigned-a"), asset("shared", "b").copy(generationId = "shared-b"))
        val data = IndexData(
            projects = listOf(project("a"), project("b")), assets = material,
            generations = listOf(generation("old-a", "a", listOf("moved")), generation("unassigned-a", "a", listOf("unassigned")),
                generation("shared-b", "b", listOf("shared")), generation("pending-a", "a")),
            story = StoryState(links = listOf(StoryLink("share", "a", "shared", "project", "a"))),
        )
        val result = projectCutHub(data, emptyList())
        val a = result.projects.single { it.id == "a" }
        assertEquals(listOf("shared"), a.material.map { it.id })
        assertEquals(setOf("pending-a", "shared-b"), a.generations.map { it.id }.toSet())
        val b = result.projects.single { it.id == "b" }
        assertEquals(setOf("moved", "shared"), b.material.map { it.id }.toSet())
        assertEquals(setOf("old-a", "shared-b"), b.generations.map { it.id }.toSet())
        assertEquals(material, data.assets)
        assertNull(data.assets.single { it.id == "unassigned" }.projectId)
    }

    @Test fun activityAndCoversUseOnlyKnownTimesAndLocalVisualMedia() {
        val data = IndexData(
            projects = listOf(project("old"), project("material"), project("montage"), project("remote")),
            assets = listOf(asset("picture", "material", 80).copy(type = AssetType.IMAGE),
                asset("sound", "material", 100).copy(type = AssetType.AUDIO),
                asset("remote", "remote", 90).copy(uri = "https://example.invalid/video.mp4")),
        )
        val result = projectCutHub(data, listOf(montage("cut", "montage", 120)), mapOf("cut" to CutHubCover("file:///cut.mp4", AssetType.VIDEO)))
        assertEquals(listOf("montage", "material", "remote", "old"), result.projects.map { it.id })
        assertEquals("file:///cut.mp4", result.projects.first().coverUri)
        assertEquals(120L, result.projects.first().activityAt)
        val material = result.projects.single { it.id == "material" }
        assertEquals(100L, material.activityAt)
        assertEquals("file:///picture.mp4", material.coverUri)
        assertEquals(AssetType.IMAGE, material.coverType)
        assertNull(result.projects.single { it.id == "remote" }.coverUri)
    }

    @Test fun metadataArchiveRestoreAndRenamePreserveAssetsAndMontagesOnDisk() = runTest {
        val data = IndexData(projects = listOf(project("a"), project("b")), assets = listOf(asset("take", "a")),
            generations = listOf(generation("gen", "a", listOf("take"))), scenes = listOf(Scene("scene", "a", "1")))
        val (hub, index, workspace) = fixture(data)
        val montage = hub.createMontage("a", "  Primer montaje  ")
        val imported = montage.run("media.import", Origin.USER, "asset_ids" to listOf("take"))
        assertTrue(imported is Outcome.Done)
        val montageFile = workspace.store.fileOf(montage.projectId)
        val montageBefore = montageFile.readText()
        val indexBefore = index.snapshot()
        assertTrue(hub.setArchived("a", true).archived)
        assertEquals(indexBefore.assets, index.snapshot().assets)
        assertEquals(indexBefore.generations, index.snapshot().generations)
        assertEquals(indexBefore.scenes, index.snapshot().scenes)
        assertEquals(montageBefore, montageFile.readText())
        val archived = hub.snapshot().projects.single { it.id == "a" }
        assertTrue(archived.archived)
        assertEquals(1, archived.materialCount)
        assertEquals(1, archived.montageCount)
        assertEquals("file:///take.mp4", archived.coverUri)
        assertFails { hub.createMontage("a", "No crear todavía") }
        assertFalse(hub.setArchived("a", false).archived)
        val renamed = hub.renameProject("a", "  Nueva historia  ")
        assertEquals("Nueva historia", renamed.name)
        assertEquals("Proyecto b", index.snapshot().projects.single { it.id == "b" }.name)
        assertEquals(indexBefore.assets, index.snapshot().assets)
        assertEquals(montageBefore, montageFile.readText())
        assertEquals(1, hub.snapshot().projects.single { it.id == "a" }.montageCount)
    }

    @Test fun createProjectReusesIdentityButEachNewMontageIsIndependentAndNamesAreRequired() = runTest {
        val (hub, index, workspace) = fixture(IndexData())
        val project = hub.createProject("  Historia  ")
        assertEquals(project.id, hub.createProject("historia").id)
        assertEquals(1, index.snapshot().projects.size)
        val first = hub.createMontage(project.id, "Versión uno")
        val second = hub.createMontage(project.id, "Versión dos")
        assertNotEquals(first.projectId, second.projectId)
        assertEquals(project.id, first.state.value.project.nexusProjectId)
        assertEquals(project.id, second.state.value.project.nexusProjectId)
        assertEquals(2, hub.snapshot().projects.single().montageCount)
        assertFails { hub.createProject(" \n ") }
        assertFails { hub.renameProject(project.id, " ") }
        assertFails { hub.createMontage(project.id, " ") }
        assertFails { hub.createMontage("missing", "No reasignar") }
        assertEquals(2, workspace.list().size)
        val loose = hub.createMontage(null, "Sin proyecto")
        assertNull(loose.state.value.project.nexusProjectId)
        assertEquals(listOf(loose.projectId), hub.snapshot().looseMontages.map { it.id })
    }

    @Test fun creatingWithAnArchivedNameExplainsRestorationAndPreservesTheExistingIdentity() = runTest {
        val (hub, index, _) = fixture(IndexData())
        val project = hub.createProject("Historia")
        hub.setArchived(project.id, true)
        val failure = assertFails { hub.createProject("historia") }
        assertTrue(failure.message!!.contains("archivado"))
        assertTrue(failure.message!!.contains("Restáuralo"))
        assertEquals(listOf(project.id), index.snapshot().projects.map { it.id })
        assertTrue(index.snapshot().projects.single().archived)
        hub.setArchived(project.id, false)
        assertEquals(project.id, hub.createProject("Historia").id)
    }

    private suspend fun assertFails(block: suspend () -> Unit): IllegalArgumentException {
        try { block() } catch (failure: IllegalArgumentException) { return failure }
        throw AssertionError("Expected a validation error")
    }
}
