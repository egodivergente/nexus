package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.*
import com.vidadomu.nexus.cut.timeline.render.*
import org.junit.Assert.*
import org.junit.Test

class ClipExportRangesTest {
    private val durations = listOf(22_080_000L, 26_720_000L, 19_440_000L, 28_360_000L, 166_480_000L)
    private fun montage(): Project {
        var cursor = 0L
        val clips = durations.mapIndexed { i, duration ->
            Clip("v$i", ClipKind.VIDEO, "source", cursor, duration, sourceInUs = cursor, linkId = "link$i").also { cursor += duration }
        }
        val audio = clips.mapIndexed { i, c -> c.copy(id = "a$i", kind = ClipKind.AUDIO) }
        return CutProjects.create("Montaje", 1, IdGenerator.sequential()).copy(revision = 6,
            timeline = Timeline(videoTracks = listOf(Track("v", TrackKind.VIDEO, "V2", TrackRole.PICTURE, clips)),
                audioTracks = listOf(Track("a", TrackKind.AUDIO, "Diálogo", TrackRole.DIALOGUE, audio))))
    }
    @Test fun fiveSeparateFilesKeepLinkedAudioAndOriginalRevision() {
        val project = montage()
        val original = project.copy()
        val ranges = clipExportRanges(project, emptySet(), true)
        assertEquals(durations, ranges.map { it.durationUs })
        assertEquals(263_080_000L, ranges.last().endUs)
        for ((i, range) in ranges.withIndex()) {
            val plan = RenderPlanner.plan(project.timeline) { true }.window(range.startUs, range.endUs)
            assertEquals(setOf("v$i"), plan.videoLanes.flatMap { it.segments }.filterNot { it.isGap }.map { it.clipId }.toSet())
            assertEquals(setOf("a$i"), plan.audioLanes.flatMap { it.segments }.filterNot { it.isGap }.map { it.clipId }.toSet())
            assertEquals(range.durationUs, plan.durationUs)
            assertEquals(0L, plan.videoLanes.first().segments.first().timelineStartUs)
        }
        assertEquals(original, project)
        assertEquals(6L, project.revision)
    }
    @Test fun linkedAudioAndVideoSelectionProducesOneChronologicalFile() {
        val selected = clipExportRanges(montage(), linkedSetOf("a3", "v3", "v0"), false)
        assertEquals(listOf("v0", "v3"), selected.map { it.clipId })
    }
    @Test fun missingClipFailsBeforeRendering() {
        assertThrows(CutFailure::class.java) { clipExportRanges(montage(), setOf("missing"), false) }
    }
    @Test fun boundedWindowTrimsBothEndsAndRespectsSpeed() {
        val segment = Segment(0, 5_000_000, "v", "m", ClipKind.VIDEO, 100_000, Rational(2, 1))
        val plan = RenderPlan(5_000_000, listOf(Lane("v", 0, TrackKind.VIDEO, listOf(segment))), emptyList(), emptyList())
        val window = plan.window(1_000_000, 2_500_000)
        assertEquals(1_500_000L, window.durationUs)
        assertEquals(2_100_000L, window.videoLanes.single().segments.single().sourceStartUs)
        assertEquals(1_500_000L, window.videoLanes.single().segments.single().durationUs)
        assertEquals(5_000_000L, plan.durationUs)
    }
}
