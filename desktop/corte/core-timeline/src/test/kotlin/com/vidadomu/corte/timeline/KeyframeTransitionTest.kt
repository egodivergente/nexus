package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.AnimParam
import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.Interpolation
import com.vidadomu.corte.model.MediaKind
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TrackRole
import com.vidadomu.corte.model.TransitionAlignment
import com.vidadomu.corte.model.TransitionKind
import com.vidadomu.corte.timeline.render.AudioEvaluator
import com.vidadomu.corte.timeline.render.RenderPlanner
import com.vidadomu.corte.timeline.render.VideoEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeTransitionTest {
    private val fx = Fx().also {
        it.media("long", 3600)
        it.media("ten", 10)
        it.media("music", 600, MediaKind.AUDIO)
    }

    private fun opacityAt(t: Timeline, id: String, sec: Number) = t.c(id).keyframeValue(AnimParam.OPACITY, s(sec))!!

    @Test
    fun keyframesTravelWithTheirClipOnRipple() {
        var t = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("C", 20, 10))))
        t = TimelineEditor.setKeyframe(t, fx.ctx, "C", AnimParam.OPACITY, 0, 1.0).ok().timeline
        t = TimelineEditor.setKeyframe(t, fx.ctx, "C", AnimParam.OPACITY, s(2), 0.0).ok().timeline
        assertEquals(0.5, opacityAt(t, "C", 21), 1e-9)
        val r = TimelineEditor.place(t, fx.ctx, listOf(Placement("V1", ClipKind.VIDEO, "long", 0, s(3))), s(5), EditMode.INSERT).ok()
        assertStart(r.timeline, "C", 23)
        assertEquals(0.5, opacityAt(r.timeline, "C", 24), 1e-9)
        assertEquals(1.0, opacityAt(r.timeline, "C", 23), 1e-9)
    }

    @Test
    fun splitPreservesEasedCurvesExactly() {
        var t = Timeline(videoTracks = listOf(vtrack("V1", clip("C", 20, 10))))
        t = TimelineEditor.setKeyframe(t, fx.ctx, "C", AnimParam.OPACITY, 0, 0.0, Interpolation.EASE_IN_OUT).ok().timeline
        t = TimelineEditor.setKeyframe(t, fx.ctx, "C", AnimParam.OPACITY, s(4), 1.0).ok().timeline
        val before = (0..40).map { opacityAt(t, "C", 20 + it * 0.25) }
        val split = TimelineEditor.split(t, fx.ctx, setOf("C"), s(21)).ok().timeline
        val right = split.track("V1")!!.clips[1]
        val after = (0..40).map { i ->
            val time = s(20 + i * 0.25)
            val c = if (time < s(21)) split.c("C") else right
            c.keyframeValue(AnimParam.OPACITY, time)!!
        }
        for (i in before.indices) assertEquals(before[i], after[i], 1e-9)
    }

    @Test
    fun crossDissolveNeedsHandles() {
        val noHandles = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 10, media = "ten"), clip("B", 10, 10, media = "ten"))))
        val rej = TimelineEditor.setTransition(noHandles, fx.ctx, "V1", "A", "B", TransitionKind.CROSS_DISSOLVE, s(1)).rejected()
        assertTrue(rej.reason.contains("handles"))
        val dip = TimelineEditor.setTransition(noHandles, fx.ctx, "V1", "A", "B", TransitionKind.DIP_TO_BLACK, s(1)).ok()
        val tr = dip.timeline.track("V1")!!.transitions.single()
        assertEquals(TransitionAlignment.CENTER, tr.alignment)
    }

    @Test
    fun crossDissolveRendersOnTwoLanesWithComplementaryAlpha() {
        val t0 = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("B", 10, 10, sourceIn = 100))))
        val t = TimelineEditor.setTransition(t0, fx.ctx, "V1", "A", "B", TransitionKind.CROSS_DISSOLVE, s(1)).ok().timeline
        val plan = RenderPlanner.plan(t) { true }
        assertEquals(2, plan.videoLanes.size)
        val upper = plan.videoLanes[0]
        val lower = plan.videoLanes[1]
        assertEquals(1, upper.lane)
        val bSeg = upper.segments.first { !it.isGap }
        assertEquals(s(9.5), bSeg.timelineStartUs)
        assertEquals(s(99.5), bSeg.sourceStartUs)
        val aSeg = lower.segments.first { !it.isGap }
        assertEquals(s(10.5), aSeg.endUs)
        for (lane in plan.videoLanes) assertEquals(plan.durationUs, lane.segments.sumOf { it.durationUs })
        val ev = VideoEvaluator(t, plan)
        assertEquals(0.5f, ev.evaluate(0, s(10)).alpha, 1e-4f)
        assertEquals(1f, ev.evaluate(1, s(10)).alpha, 1e-4f)
        assertEquals(0f, ev.evaluate(0, s(9)).alpha, 1e-4f)
        assertEquals(1f, ev.evaluate(0, s(12)).alpha, 1e-4f)
    }

    @Test
    fun partialHandlesChooseAnAlignmentThatFits() {
        fx.media("a10_2", 10.2)
        val t0 = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 10, media = "a10_2"), clip("B", 10, 10, sourceIn = 5))))
        val r = TimelineEditor.setTransition(t0, fx.ctx, "V1", "A", "B", TransitionKind.CROSS_DISSOLVE, s(1)).ok()
        val tr = r.timeline.track("V1")!!.transitions.single()
        assertEquals(TransitionAlignment.END_AT_CUT, tr.alignment)
        assertEquals(s(1), tr.durationUs)
    }

    @Test
    fun dipToBlackGoesThroughBlackAtTheCut() {
        val t0 = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 10, media = "ten"), clip("B", 10, 10, media = "ten"))))
        val t = TimelineEditor.setTransition(t0, fx.ctx, "V1", "A", "B", TransitionKind.DIP_TO_BLACK, s(1)).ok().timeline
        val plan = RenderPlanner.plan(t) { true }
        assertEquals(1, plan.videoLanes.size)
        val ev = VideoEvaluator(t, plan)
        assertEquals(0f, ev.evaluate(0, s(10)).alpha, 1e-4f)
        assertEquals(0.5f, ev.evaluate(0, s(10.25)).alpha, 1e-4f)
        assertEquals(0.5f, ev.evaluate(0, s(9.75)).alpha, 1e-4f)
        assertEquals(1f, ev.evaluate(0, s(5)).alpha, 1e-4f)
    }

    @Test
    fun transitionIsDroppedWithAWarningWhenItsCutDisappears() {
        val t0 = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("B", 10, 10, sourceIn = 100))))
        val t = TimelineEditor.setTransition(t0, fx.ctx, "V1", "A", "B", TransitionKind.CROSS_DISSOLVE, s(1)).ok().timeline
        val r = TimelineEditor.move(t, fx.ctx, setOf("B"), "B", s(2), 0, EditMode.OVERWRITE).ok()
        assertTrue(r.timeline.track("V1")!!.transitions.isEmpty())
        assertTrue(r.warnings.any { it.startsWith("Transición eliminada") })
    }

    @Test
    fun audioCrossfadeIsEqualPower() {
        val t0 = Timeline(audioTracks = listOf(atrack("A1", TrackRole.MUSIC, aclip("a1", 0, 10, media = "music"), aclip("a2", 10, 10, media = "music", sourceIn = 100))))
        val t = TimelineEditor.setTransition(t0, fx.ctx, "A1", "a1", "a2", TransitionKind.AUDIO_CROSSFADE, s(1)).ok().timeline
        val ev = AudioEvaluator(t)
        val g1 = ev.gainAt("a1", s(10))
        val g2 = ev.gainAt("a2", s(10))
        assertEquals(Math.sqrt(0.5), g1.toDouble(), 1e-4)
        assertEquals(1.0, (g1 * g1 + g2 * g2).toDouble(), 1e-4)
        val plan = RenderPlanner.plan(t) { true }
        assertEquals(2, plan.audioLanes.size)
    }

    @Test
    fun audioGainFadesMuteAndSolo() {
        var t = Timeline(
            audioTracks = listOf(
                atrack("A1", TrackRole.DIALOGUE, aclip("d", 0, 10)),
                atrack("A2", TrackRole.MUSIC, aclip("m", 0, 10, media = "music")),
            ),
        )
        t = TimelineEditor.updateClips(t, fx.ctx, setOf("d"), "Fade") { it.copy(audio = it.audio.copy(gainDb = -6.0, fadeInUs = s(2))) }.ok().timeline
        val ev = AudioEvaluator(t)
        assertEquals(0f, ev.gainAt("d", 0), 1e-6f)
        assertEquals(Math.pow(10.0, -6.0 / 20), ev.gainAt("d", s(5)).toDouble(), 1e-6)
        t = TimelineEditor.updateTrack(t, fx.ctx, "A1", "Solo") { it.copy(solo = true) }.ok().timeline
        assertEquals(0f, AudioEvaluator(t).gainAt("m", s(5)), 0f)
        t = TimelineEditor.setKeyframe(t, fx.ctx, "d", AnimParam.GAIN_DB, s(4), -20.0).ok().timeline
        assertEquals(Math.pow(10.0, -1.0), AudioEvaluator(t).gainAt("d", s(6)).toDouble(), 1e-6)
    }

    @Test
    fun offlineMediaRendersAsGapNotCrash() {
        val t = Timeline(videoTracks = listOf(vtrack("V1", clip("A", 0, 10), clip("B", 10, 10, media = "gone"))))
        val plan = RenderPlanner.plan(t) { it != "gone" }
        assertEquals(s(20), plan.durationUs)
        assertTrue(plan.notes.single().startsWith("MEDIA OFFLINE"))
        assertTrue(plan.videoLanes.single().segments.last().isGap)
    }
}
