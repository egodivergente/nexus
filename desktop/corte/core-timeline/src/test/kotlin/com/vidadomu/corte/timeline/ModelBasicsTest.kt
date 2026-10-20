package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.FrameRate
import com.vidadomu.corte.model.Rational
import com.vidadomu.corte.model.Timecode
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TrackRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelBasicsTest {
    @Test
    fun frameMathIsExactForNtscRates() {
        val r = FrameRate.FPS_23_976
        for (i in 0L..100_000L step 7) {
            assertEquals(i, r.frameAt(r.timeOf(i)))
            assertEquals(r.timeOf(i), r.snap(r.timeOf(i) + 3))
        }
        // One hour of 23.976 has exactly 86313 frames (+/- rounding of the boundary).
        assertEquals(86313L, r.frameAt(3_600_000_000L))
    }

    @Test
    fun noDriftOverManySplits() {
        val fx = Fx().also { it.media("long", 3600) }
        var t = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 1000))))
        val rate = FrameRate.FPS_29_97
        var i = 1L
        while (rate.timeOf(i * 37) < s(1000)) {
            t = TimelineEditor.split(t, fx.ctx, emptySet(), rate.timeOf(i * 37)).ok().timeline
            i++
        }
        val clips = t.track("V1")!!.clips
        assertEquals(s(1000), clips.sumOf { it.durationUs })
        for (k in 1 until clips.size) {
            assertEquals(clips[k - 1].endUs, clips[k].timelineStartUs)
            assertEquals(clips[k - 1].sourceOutUs, clips[k].sourceInUs)
        }
    }

    @Test
    fun rationalParsing() {
        assertEquals(Rational(1, 4), Rational.parse("0.25"))
        assertEquals(Rational(2, 1), Rational.parse("2"))
        assertEquals(Rational(1, 3), Rational.parse("2/6"))
        assertEquals(s(40), Rational(1, 4).inverseScale(s(10)))
    }

    @Test
    fun timecodes() {
        assertEquals("00:01:23.417", Timecode.formatMillis(83_417_000))
        assertEquals("00:00:01:12", Timecode.formatFrames(1_500_000, FrameRate.FPS_24))
    }

    @Test
    fun snapping() {
        val t = Timeline(audioTracks = listOf(atrack("A1", TrackRole.SFX, aclip("x", 10, 2))))
        val targets = Snapping.targets(t, s(5), emptySet())
        assertEquals(s(10), Snapping.nearest(targets, s(10.1), s(0.2))!!.timeUs)
        assertNull(Snapping.nearest(targets, s(8), s(0.2)))
        val (start, target) = Snapping.snapSpan(targets, s(3.1), s(2), s(0.2))
        assertEquals(s(3), start)
        assertEquals(Snapping.Kind.PLAYHEAD, target!!.kind)
    }
}
