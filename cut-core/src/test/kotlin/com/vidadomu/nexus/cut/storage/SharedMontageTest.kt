package com.vidadomu.nexus.cut.storage

import com.vidadomu.nexus.cut.command.CutRegistry
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedMontageTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun linkedTimelineAndPlayheadSurviveTheWindowsFormat() = runTest {
        val media = MediaRef("m", "file:///take.mp4", "Toma", MediaKind.VIDEO, 10_000_000, 1920, 1080,
            assetId = "asset", generationId = "generation")
        val clip = Clip("clip", ClipKind.VIDEO, media.id, 1_000_000, 5_000_000, sourceInUs = 2_000_000,
            story = StoryLink("scene", "shot"))
        val timeline = Timeline(videoTracks = listOf(Track("v", TrackKind.VIDEO, "Vídeo", TrackRole.PICTURE, listOf(clip))))
        val source = Project(id = "android-local", name = "Continuidad", nexusProjectId = "nexus-project",
            media = listOf(media), timeline = timeline, revision = 8)
        val portable = SharedMontage.encode(source, 2_500_000)
        val project = portable.getValue("project").jsonObject
        assertEquals(1, project.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("nexus-project", project.getValue("id").jsonPrimitive.content)
        assertFalse(project.containsKey("revision"))
        assertFalse(project.getValue("media").jsonArray.single().jsonObject.containsKey("assetId"))
        val targetId = SharedMontage.localId("nexus-project")
        val incoming = SharedMontage.decode("nexus-project", portable, targetId)
        assertEquals(timeline, incoming.project.timeline)
        assertEquals(listOf(media), incoming.project.media)
        assertEquals(2_500_000L, incoming.playheadUs)
        val workspace = CutWorkspace(ProjectStore(directory.root), CutRegistry { emptySet() })
        workspace.applySynced(null, incoming.project, incoming.playheadUs)
        val opened = workspace.session(targetId)
        val first = opened.state.value.project
        assertEquals(portable, SharedMontage.encode(first, incoming.playheadUs, portable))
        val edited = incoming.project.copy(name = "Continuado desde Windows")
        workspace.applySynced(first, edited, 3_000_000)
        assertEquals("Continuado desde Windows", opened.state.value.project.name)
        assertEquals(3_000_000L, opened.state.value.view.playheadUs)
        assertEquals(first.timeline, workspace.store.load(targetId).project.timeline)
        assertTrue(runCatching { workspace.applySynced(first, incoming.project, 0) }.isFailure)
    }
}
