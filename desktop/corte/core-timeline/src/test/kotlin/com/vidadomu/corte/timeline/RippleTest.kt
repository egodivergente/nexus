package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.MediaKind
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scene used by most tests (seconds):
 *   V1  [A 0-10][B 10-20][C 20-30]
 *   A1  [dlg 0-30]                  dialogue, SPLIT
 *   A2  [mus 0-32]                  music bed, EXTEND
 *   A3       [sfx1 5-7]     [sfx2 24-26]
 */
class RippleTest {
    private lateinit var fx: Fx
    private lateinit var scene: Timeline

    @Before
    fun setUp() {
        fx = Fx()
        fx.media("long", 3600)
        fx.media("music", 600, MediaKind.AUDIO)
        fx.media("x", 60)
        scene = Timeline(
            videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("B", 10, 10, sourceIn = 100), clip("C", 20, 10, sourceIn = 200))),
            audioTracks = listOf(
                atrack("A1", TrackRole.DIALOGUE, aclip("dlg", 0, 30)),
                atrack("A2", TrackRole.MUSIC, aclip("mus", 0, 32, media = "music")),
                atrack("A3", TrackRole.SFX, aclip("sfx1", 5, 2), aclip("sfx2", 24, 2)),
            ),
        )
    }

    private fun insertX(t: Timeline, at: Number, seconds: Number = 5, track: String = "V1", mode: EditMode = EditMode.INSERT) =
        TimelineEditor.place(t, fx.ctx, listOf(Placement(track, ClipKind.VIDEO, "x", 0, s(seconds), name = "X")), s(at), mode)

    @Test
    fun specExample_insert3sAt20_shiftsClipAndAudio() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("clipA", 25, 5))),
            audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("audioB", 30, 5))),
        )
        val r = insertX(t0, 20, 3).ok()
        assertStart(r.timeline, "clipA", 28)
        assertStart(r.timeline, "audioB", 33)
        assertValid(r.timeline, fx)
    }

    @Test
    fun rippleInsert_betweenBandC_followsTrackRules() {
        val r = insertX(scene, 20).ok()
        val t = r.timeline
        assertEquals(listOf(s(0) to s(10), s(10) to s(20), s(20) to s(25), s(25) to s(35)), t.spans("V1"))
        assertStart(t, "C", 25)
        // Dialogue (SPLIT) is cut at the edit point and its tail keeps sync with the picture.
        assertEquals(listOf(s(0) to s(20), s(25) to s(35)), t.spans("A1"))
        assertEquals(s(20), t.track("A1")!!.clips[1].sourceInUs)
        // Music (EXTEND bed) just keeps playing.
        assertEquals(listOf(s(0) to s(37)), t.spans("A2"))
        assertStart(t, "sfx1", 5)
        assertStart(t, "sfx2", 29)
        assertTrue(r.collateral.any { it.clipId == "dlg" })
        assertTrue(r.collateral.any { it.clipId == "mus" })
        assertValid(t, fx)
    }

    @Test
    fun rippleInsert_bedWithoutMediaLeft_fallsBackToSplitAndSaysSo() {
        fx.media("musShort", 32, MediaKind.AUDIO)
        val t0 = scene.replaceTrack(atrack("A2", TrackRole.MUSIC, aclip("mus", 0, 32, media = "musShort")))
        val r = insertX(t0, 20).ok()
        assertEquals(listOf(s(0) to s(20), s(25) to s(37)), r.timeline.spans("A2"))
        assertTrue(r.warnings.any { it.contains("no queda material") })
    }

    @Test
    fun overwrite_doesNotShiftAnything() {
        val r = insertX(scene, 12, 3, mode = EditMode.OVERWRITE).ok()
        val t = r.timeline
        assertEquals(listOf(s(0) to s(10), s(10) to s(12), s(12) to s(15), s(15) to s(20), s(20) to s(30)), t.spans("V1"))
        assertEquals(t.track("V1")!!.clips[3].sourceInUs, s(105))
        assertStart(t, "C", 20)
        assertStart(t, "sfx2", 24)
        assertEquals(scene.track("A1"), t.track("A1"))
    }

    @Test
    fun rippleDelete_closesGapAcrossTracks() {
        val r = TimelineEditor.delete(scene, fx.ctx, setOf("B"), ripple = true).ok()
        val t = r.timeline
        assertEquals(listOf(s(0) to s(10), s(10) to s(20)), t.spans("V1"))
        assertStart(t, "C", 10)
        assertEquals(listOf(s(0) to s(10), s(10) to s(20)), t.spans("A1"))
        assertEquals(s(20), t.track("A1")!!.clips[1].sourceInUs)
        assertEquals(listOf(s(0) to s(22)), t.spans("A2"))
        assertStart(t, "sfx1", 5)
        assertStart(t, "sfx2", 14)
        assertTrue("collateral must be reported", r.collateral.map { it.clipId }.containsAll(listOf("dlg", "mus")))
        assertValid(t, fx)
    }

    @Test
    fun liftDelete_leavesGap() {
        val t = TimelineEditor.delete(scene, fx.ctx, setOf("B"), ripple = false).ok().timeline
        assertEquals(listOf(s(0) to s(10), s(20) to s(30)), t.spans("V1"))
        assertEquals(scene.audioTracks, t.audioTracks)
    }

    @Test
    fun rippleTrimTail_shortenPullsEverythingAfter() {
        val r = TimelineEditor.trim(scene, fx.ctx, "B", Edge.TAIL, -s(2), ripple = true).ok()
        val t = r.timeline
        assertEquals(s(-2), r.appliedDeltaUs)
        assertEquals(listOf(s(0) to s(10), s(10) to s(18), s(18) to s(28)), t.spans("V1"))
        assertEquals(listOf(s(0) to s(18), s(18) to s(28)), t.spans("A1"))
        assertEquals(listOf(s(0) to s(30)), t.spans("A2"))
        assertStart(t, "sfx2", 22)
    }

    @Test
    fun rippleTrimTail_extendIsClampedToSourceMedia() {
        fx.media("short", 12)
        val t0 = scene.replaceTrack(vtrack("V1", clip("A", 0, 10), clip("B", 10, 10, media = "short"), clip("C", 20, 10, sourceIn = 200)))
        val r = TimelineEditor.trim(t0, fx.ctx, "B", Edge.TAIL, s(5), ripple = true).ok()
        assertEquals(s(2), r.appliedDeltaUs)
        assertStart(r.timeline, "C", 22)
        assertStart(r.timeline, "sfx2", 26)
    }

    @Test
    fun rippleTrimHead_keepsStartAndSlidesContent() {
        val r = TimelineEditor.trim(scene, fx.ctx, "C", Edge.HEAD, s(2), ripple = true).ok()
        val t = r.timeline
        val c = t.c("C")
        assertEquals(s(20), c.timelineStartUs)
        assertEquals(s(8), c.durationUs)
        assertEquals(s(202), c.sourceInUs)
        assertEquals(listOf(s(0) to s(20), s(20) to s(28)), t.spans("A1"))
        assertEquals(s(22), t.track("A1")!!.clips[1].sourceInUs)
        assertStart(t, "sfx2", 22)
    }

    @Test
    fun trimWithoutRipple_isClampedByNeighbour() {
        val t1 = TimelineEditor.trim(scene, fx.ctx, "B", Edge.TAIL, -s(2), ripple = false).ok().timeline
        assertStart(t1, "C", 20)
        val r = TimelineEditor.trim(t1, fx.ctx, "B", Edge.TAIL, s(5), ripple = false).ok()
        assertEquals(s(2), r.appliedDeltaUs)
        TimelineEditor.trim(r.timeline, fx.ctx, "B", Edge.TAIL, s(1), ripple = false).rejected()
    }

    @Test
    fun lockedTrack_cannotBeEdited() {
        val t0 = scene.replaceTrack(scene.track("A3")!!.copy(locked = true))
        TimelineEditor.trim(t0, fx.ctx, "sfx2", Edge.TAIL, -s(1), ripple = false).rejected()
        TimelineEditor.split(t0, fx.ctx, setOf("sfx2"), s(25)).rejected()
        TimelineEditor.delete(t0, fx.ctx, setOf("sfx1"), ripple = false).rejected()
        TimelineEditor.move(t0, fx.ctx, setOf("sfx1"), "sfx1", s(1), 0, EditMode.OVERWRITE).rejected()
        val all = TimelineEditor.split(t0, fx.ctx, emptySet(), s(25)).ok().timeline
        assertEquals(t0.track("A3"), all.track("A3"))
        assertEquals(4, all.track("V1")!!.clips.size)
    }

    @Test
    fun lockedTrackWithSyncLock_shiftsWholeButIsNeverCut() {
        val t0 = scene.replaceTrack(scene.track("A3")!!.copy(locked = true))
        val r = insertX(t0, 20).ok()
        assertStart(r.timeline, "sfx2", 29)
        val rej = insertX(t0, 6).rejected()
        assertTrue(rej.reason.contains("bloqueada"))
    }

    @Test
    fun trackWithoutSyncLock_staysAndWarns() {
        val t0 = scene.replaceTrack(scene.track("A3")!!.copy(syncLock = false))
        val r = insertX(t0, 20).ok()
        assertStart(r.timeline, "sfx2", 24)
        assertTrue(r.warnings.any { it.startsWith("A3") })
    }

    @Test
    fun closeGap_refusesToCutOtherTracks_butShortensBeds() {
        val t0 = Timeline(
            videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("C", 15, 10))),
            audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("dlg", 0, 30)), atrack("A2", TrackRole.MUSIC, aclip("mus", 0, 40, media = "music"))),
        )
        TimelineEditor.closeGap(t0, fx.ctx, "V1", s(12)).rejected()
        val t1 = t0.replaceTrack(atrack("A1", TrackRole.DIALOGUE, aclip("d1", 0, 10), aclip("d2", 15, 10)))
        val r = TimelineEditor.closeGap(t1, fx.ctx, "V1", s(12)).ok()
        assertStart(r.timeline, "C", 10)
        assertStart(r.timeline, "d2", 10)
        assertEquals(listOf(s(0) to s(35)), r.timeline.spans("A2"))
    }

    @Test
    fun markersFollowRipple() {
        val t0 = TimelineEditor.addMarker(scene, fx.ctx, s(25), "beat").ok().timeline
        val t1 = insertX(t0, 20).ok().timeline
        assertEquals(s(30), t1.markers.single().timeUs)
        val t2 = TimelineEditor.delete(t1, fx.ctx, setOf("A"), ripple = true).ok().timeline
        assertEquals(s(20), t2.markers.single().timeUs)
    }
}
