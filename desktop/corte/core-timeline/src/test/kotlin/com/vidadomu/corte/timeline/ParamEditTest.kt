package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.AnimParam
import com.vidadomu.corte.model.Interpolation
import com.vidadomu.corte.model.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamEditTest {
    private val fx = Fx()
    private val base = Timeline(videoTracks = listOf(vtrack("V1", clip("a", 10, 4))))

    @Test fun sliderOnStaticParamChangesTheStaticValue() {
        val t = TimelineEditor.setParam(base, fx.ctx, "a", AnimParam.SCALE, s(11), 1.5).ok().timeline
        assertEquals(1.5, t.c("a").video.scale, 0.0)
        assertTrue(t.c("a").keyframes.isEmpty())
        // Out-of-range values are clamped to the parameter's range.
        assertEquals(1.0, TimelineEditor.setParam(base, fx.ctx, "a", AnimParam.OPACITY, s(11), 3.0).ok().timeline.c("a").video.opacity, 0.0)
    }

    @Test fun keyframeButtonAnimatesThenSliderEditsKeyAtPlayhead() {
        var t = TimelineEditor.setParam(base, fx.ctx, "a", AnimParam.POSITION_X, s(10), 0.2).ok().timeline
        // First key holds the current value.
        t = TimelineEditor.toggleKeyframe(t, fx.ctx, "a", AnimParam.POSITION_X, s(10)).ok().timeline
        assertEquals(listOf(0L), t.c("a").keyframes[AnimParam.POSITION_X]!!.keys.map { it.timeUs })
        // Animated now: the slider at another time adds a key there instead of changing the static value.
        t = TimelineEditor.setParam(t, fx.ctx, "a", AnimParam.POSITION_X, s(12), -0.4).ok().timeline
        val c = t.c("a")
        assertEquals(0.2, c.video.positionX, 0.0)
        assertEquals(-0.1, ClipParams.valueAt(c, AnimParam.POSITION_X, s(11)), 1e-9)
        assertEquals(listOf(s(10), s(12)), ClipParams.keyTimes(c))
        // Pressing the button on an existing key removes it.
        t = TimelineEditor.toggleKeyframe(t, fx.ctx, "a", AnimParam.POSITION_X, s(12)).ok().timeline
        assertEquals(listOf(s(10)), ClipParams.keyTimes(t.c("a")))
    }

    @Test fun animatedParamOutsideTheClipIsRejected() {
        val t = TimelineEditor.toggleKeyframe(base, fx.ctx, "a", AnimParam.OPACITY, s(11)).ok().timeline
        TimelineEditor.setParam(t, fx.ctx, "a", AnimParam.OPACITY, s(2), 0.5).rejected()
        TimelineEditor.toggleKeyframe(t, fx.ctx, "a", AnimParam.OPACITY, s(14)).rejected() // clip end is exclusive
    }

    @Test fun interpolationAppliesToEveryKeyAtThePlayheadAndIsOneUndoStep() {
        var t = TimelineEditor.toggleKeyframe(base, fx.ctx, "a", AnimParam.OPACITY, s(11)).ok().timeline
        t = TimelineEditor.toggleKeyframe(t, fx.ctx, "a", AnimParam.SCALE, s(11)).ok().timeline
        t = TimelineEditor.setParam(t, fx.ctx, "a", AnimParam.SCALE, s(13), 2.0).ok().timeline
        val r = TimelineEditor.setKeyInterpolation(t, fx.ctx, "a", s(11), Interpolation.EASE_IN_OUT).ok()
        val c = r.timeline.c("a")
        assertEquals(Interpolation.EASE_IN_OUT, c.keyframes[AnimParam.OPACITY]!!.keys.single().interpolation)
        assertEquals(Interpolation.EASE_IN_OUT, c.keyframes[AnimParam.SCALE]!!.keys.first().interpolation)
        assertEquals(Interpolation.LINEAR, c.keyframes[AnimParam.SCALE]!!.keys.last().interpolation)
        assertEquals(t, r.changes.inverse().applyTo(r.timeline))
        TimelineEditor.setKeyInterpolation(t, fx.ctx, "a", s(12), Interpolation.HOLD).rejected()
    }

    @Test fun editingAnExistingKeyKeepsItsInterpolation() {
        var t = TimelineEditor.toggleKeyframe(base, fx.ctx, "a", AnimParam.ROTATION, s(11)).ok().timeline
        t = TimelineEditor.setKeyInterpolation(t, fx.ctx, "a", s(11), Interpolation.HOLD).ok().timeline
        t = TimelineEditor.setParam(t, fx.ctx, "a", AnimParam.ROTATION, s(11), 45.0).ok().timeline
        val k = t.c("a").keyframes[AnimParam.ROTATION]!!.keys.single()
        assertEquals(45.0, k.value, 0.0); assertEquals(Interpolation.HOLD, k.interpolation)
        assertFalse(ClipParams.hasKeyAt(t.c("a"), AnimParam.ROTATION, s(12)))
    }

    @Test fun cursorFindsAKeyOffTheFrameGridAndStaysInsideTheClip() {
        // A clip moved by a sub-frame amount: its keys no longer sit on frame boundaries.
        val off = Timeline(videoTracks = listOf(vtrack("V1", clip("a", 10, 4).copy(timelineStartUs = s(10) + 13))))
        val t = TimelineEditor.toggleKeyframe(off, fx.ctx, "a", AnimParam.OPACITY, s(10.5) + 13).ok().timeline
        val c = t.c("a")
        val halfFrame = 16_666L
        // The playhead on the grid (10.5 s) resolves to the key 13 us away, so the button removes it.
        val at = ClipParams.cursor(c, s(10.5), s(10.5), halfFrame)
        assertEquals(s(10.5) + 13, at)
        assertTrue(ClipParams.hasKeyAt(c, AnimParam.OPACITY, at))
        assertTrue(TimelineEditor.toggleKeyframe(t, fx.ctx, "a", AnimParam.OPACITY, at).ok().timeline.c("a").keyframes[AnimParam.OPACITY]?.isEmpty ?: true)
        // Far from any key: the snapped time.
        assertEquals(s(12), ClipParams.cursor(c, s(12) + 900, s(12), halfFrame))
        // Last partial frame: snapping would land on the clip end (outside), so the raw playhead is used.
        val end = c.endUs
        assertEquals(end - 5_000, ClipParams.cursor(c, end - 5_000, end, halfFrame))
    }

    @Test fun speedChangeRescalesKeyframesWithTheClip() {
        var t = TimelineEditor.toggleKeyframe(base, fx.ctx, "a", AnimParam.OPACITY, s(11)).ok().timeline
        t = TimelineEditor.setParam(t, fx.ctx, "a", AnimParam.OPACITY, s(13), 0.0).ok().timeline
        t = TimelineEditor.setSpeed(t, fx.ctx, "a", com.vidadomu.corte.model.Rational(2, 1), ripple = true).ok().timeline
        val c = t.c("a")
        assertEquals(s(2), c.durationUs)
        assertEquals(listOf(s(0.5), s(1.5)), c.keyframes[AnimParam.OPACITY]!!.keys.map { it.timeUs })
        // Slowing back down spreads them out again.
        val back = TimelineEditor.setSpeed(t, fx.ctx, "a", com.vidadomu.corte.model.Rational.ONE, ripple = true).ok().timeline.c("a")
        assertEquals(listOf(s(1), s(3)), back.keyframes[AnimParam.OPACITY]!!.keys.map { it.timeUs })
    }
}
