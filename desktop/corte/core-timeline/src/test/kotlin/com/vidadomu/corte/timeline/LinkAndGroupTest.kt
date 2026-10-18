package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.Rational
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAndGroupTest {
    private val fx = Fx().also { it.media("long", 3600) }

    private val linked = Timeline(
        videoTracks = listOf(vtrack("V1", clip("v", 0, 10, link = "L")), vtrack("V2")),
        audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("a", 0, 10, link = "L")), atrack("A2", TrackRole.SFX)),
    )

    @Test
    fun movingVideoMovesLinkedAudio() {
        val t = TimelineEditor.move(linked, fx.ctx, setOf("v"), "v", s(5), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(t, "v", 5)
        assertStart(t, "a", 5)
    }

    @Test
    fun movingToAnotherTrackOnlyChangesTracksOfTheDraggedKind() {
        val t = TimelineEditor.move(linked, fx.ctx, setOf("v"), "v", 0, 1, EditMode.OVERWRITE).ok().timeline
        assertEquals("V2", t.trackOfClip("v")!!.id)
        assertEquals("A1", t.trackOfClip("a")!!.id)
    }

    @Test
    fun splitSplitsBothAndRightHalvesShareANewLink() {
        val t = TimelineEditor.split(linked, fx.ctx, setOf("v"), s(4)).ok().timeline
        val vRight = t.track("V1")!!.clips[1]
        val aRight = t.track("A1")!!.clips[1]
        assertEquals(s(4), vRight.timelineStartUs)
        assertEquals(s(4), aRight.timelineStartUs)
        assertNotNull(vRight.linkId)
        assertEquals(vRight.linkId, aRight.linkId)
        assertNotEquals("L", vRight.linkId)
        assertEquals("L", t.c("v").linkId)
        assertEquals("L", t.c("a").linkId)
    }

    @Test
    fun trimKeepsLinkedRelation() {
        val t = TimelineEditor.trim(linked, fx.ctx, "v", Edge.TAIL, -s(2), ripple = false).ok().timeline
        assertEquals(s(8), t.c("a").durationUs)
        val t2 = TimelineEditor.trim(t, fx.ctx, "a", Edge.HEAD, s(1), ripple = false).ok().timeline
        assertStart(t2, "v", 1)
        assertEquals(s(1), t2.c("v").sourceInUs)
    }

    @Test
    fun unlinkSeparatesAudio() {
        val t = TimelineEditor.unlink(linked, fx.ctx, setOf("v")).ok().timeline
        assertNull(t.c("a").linkId)
        val moved = TimelineEditor.move(t, fx.ctx, setOf("v"), "v", s(3), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(moved, "v", 3)
        assertStart(moved, "a", 0)
    }

    @Test
    fun rippleThatWouldTearALinkIsRejected() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("v", 10, 10, link = "L"))),
            audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("a", 10, 10, link = "L"), syncLock = false)),
        )
        val r = TimelineEditor.place(t0, fx.ctx, listOf(Placement("V1", ClipKind.VIDEO, "long", 0, s(2))), s(5), EditMode.INSERT).rejected()
        assertTrue(r.reason.contains("vínculo"))
    }

    @Test
    fun syncGroupMovesWithInternalOffsets() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("img", 10, 5, group = "G"))),
            audioTracks = listOf(
                atrack("A1", TrackRole.AMBIENCE, aclip("amb", 10, 4, group = "G")),
                atrack("A2", TrackRole.FOLEY, aclip("fol", 11, 1, group = "G"), aclip("other", 20, 1)),
            ),
        )
        val t = TimelineEditor.move(t0, fx.ctx, setOf("fol"), "fol", s(3), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(t, "img", 13)
        assertStart(t, "amb", 13)
        assertStart(t, "fol", 14)
        assertStart(t, "other", 20)
        val dissolved = TimelineEditor.dissolveSyncGroup(t, fx.ctx, setOf("img")).ok().timeline
        val single = TimelineEditor.move(dissolved, fx.ctx, setOf("fol"), "fol", s(1), 0, EditMode.OVERWRITE).ok().timeline
        assertStart(single, "img", 13)
        assertStart(single, "fol", 15)
    }

    @Test
    fun overwriteMoveTrimsWhatIsUnderneath() {
        val t0 = Timeline(videoTracks = listOf(vtrack("V1", clip("X", 0, 5), clip("Y", 10, 10, sourceIn = 50))))
        val r = TimelineEditor.move(t0, fx.ctx, setOf("X"), "X", s(12), 0, EditMode.OVERWRITE).ok()
        assertEquals(listOf(s(10) to s(12), s(12) to s(17), s(17) to s(20)), r.timeline.spans("V1"))
        assertEquals(s(57), r.timeline.track("V1")!!.clips[2].sourceInUs)
        assertValid(r.timeline, fx)
    }

    @Test
    fun insertMoveReordersAndKeepsSoundUnderTheMovedScene() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("B", 10, 10, sourceIn = 100, link = "LB"), clip("C", 20, 10, sourceIn = 200))),
            audioTracks = listOf(
                atrack("A1", TrackRole.DIALOGUE, aclip("aB", 10, 10, sourceIn = 100, link = "LB")),
                atrack("A3", TrackRole.SFX, aclip("sfxC", 22, 2)),
            ),
        )
        val t = TimelineEditor.move(t0, fx.ctx, setOf("B"), "B", s(20), 0, EditMode.INSERT).ok().timeline
        assertEquals(listOf("A", "C", "B"), t.track("V1")!!.clips.map { it.id })
        assertStart(t, "C", 10)
        assertStart(t, "B", 20)
        assertStart(t, "aB", 20)
        assertStart(t, "sfxC", 12)
        assertValid(t, fx)
    }

    @Test
    fun speedChangeRipplesAndAppliesToLinkedAudio() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("A", 0, 10, link = "L"), clip("B", 10, 10))),
            audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("aA", 0, 10, link = "L"))),
        )
        val t = TimelineEditor.setSpeed(t0, fx.ctx, "A", Rational(2, 1), ripple = true).ok().timeline
        assertEquals(s(5), t.c("A").durationUs)
        assertEquals(s(5), t.c("aA").durationUs)
        assertEquals(Rational(2, 1), t.c("aA").speed)
        assertEquals(s(10), t.c("A").sourceOutUs)
        assertStart(t, "B", 5)
        TimelineEditor.setSpeed(t0, fx.ctx, "A", Rational(1, 2), ripple = false).rejected()
    }

    @Test
    fun duplicatePlacesCopyAfterSelectionWithItsOwnLink() {
        val t = TimelineEditor.duplicate(linked, fx.ctx, setOf("v"), EditMode.INSERT).ok().timeline
        val v2 = t.track("V1")!!.clips[1]
        val a2 = t.track("A1")!!.clips[1]
        assertEquals(s(10), v2.timelineStartUs)
        assertEquals(v2.linkId, a2.linkId)
        assertNotEquals("L", v2.linkId)
    }
}
