package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Rational
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.timeline.render.Lane
import com.vidadomu.corte.timeline.render.RenderPlan
import com.vidadomu.corte.timeline.render.RenderPlanner
import com.vidadomu.corte.timeline.render.Segment
import com.vidadomu.corte.timeline.render.isAllGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preview playback starts from the playhead by rendering a window of the plan. */
class PlanWindowTest {
    private val plan = RenderPlanner.plan(
        Timeline(videoTracks = listOf(vtrack("V1", clip("a", 0, 3, sourceIn = 10), clip("b", 3, 2)), vtrack("V2", clip("o", 1, 1)))),
    ) { true }

    @Test fun zeroStartIsIdentity() = assertSame(plan, plan.window(0))

    @Test fun straddlingSegmentIsCutAndSourceAdvanced() {
        val w = plan.window(s(1.5))
        assertEquals(s(3.5), w.durationUs)
        val v1 = w.videoLanes.first { it.trackId == "V1" }.segments
        assertEquals(listOf(0L to s(1.5), s(1.5) to s(2)), v1.map { it.timelineStartUs to it.durationUs })
        assertEquals(s(11.5), v1[0].sourceStartUs) // 10 s source in + 1.5 s skipped
        assertEquals(0L, v1[1].sourceStartUs)
    }

    @Test fun lanesKeepTheirIndexEvenWhenOnlyGapsRemain() {
        val w = plan.window(s(2.5))
        assertEquals(plan.videoLanes.map { it.key }, w.videoLanes.map { it.key })
        val v2 = w.videoLanes.first { it.trackId == "V2" }
        assertTrue(v2.isAllGap)
        // Every lane still covers the whole window, so sequences stay aligned.
        for (l in w.videoLanes) assertEquals(w.durationUs, l.segments.sumOf { it.durationUs })
    }

    @Test fun speedIsRespectedWhenAdvancingTheSource() {
        val lane = Lane("V1", 0, TrackKind.VIDEO, listOf(Segment(0, s(4), "c", "m", null, s(1), Rational(2, 1))))
        val w = RenderPlan(s(4), listOf(lane), emptyList(), emptyList()).window(s(1))
        assertEquals(s(3), w.videoLanes[0].segments[0].sourceStartUs) // 1 s timeline at 2x = 2 s of source
    }
}
