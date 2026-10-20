package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.AnimParam
import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.Keyframe
import com.vidadomu.corte.model.KeyframeTrack
import com.vidadomu.corte.model.TextProps
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.Track
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.model.TrackRole
import com.vidadomu.corte.model.VideoProps
import com.vidadomu.corte.timeline.render.RenderPlanner
import com.vidadomu.corte.timeline.render.TitleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleTest {
    private fun title(id: String, start: Number, dur: Number, text: String = id, video: VideoProps = VideoProps()) = Clip(
        id = id, kind = ClipKind.TEXT, mediaId = "", timelineStartUs = s(start), durationUs = s(dur), name = id,
        text = TextProps(text = text), video = video,
    )

    private fun titles(id: String, vararg c: Clip, muted: Boolean = false) =
        Track(id, TrackKind.VIDEO, id, TrackRole.TITLES, c.toList(), muted = muted)

    @Test fun titlesAreNotRenderLanesButCountForDuration() {
        val t = Timeline(videoTracks = listOf(vtrack("V1", clip("a", 0, 2)), titles("T1", title("t", 1, 5))))
        val plan = RenderPlanner.plan(t) { true }
        assertEquals(s(6), plan.durationUs)
        assertEquals(listOf("V1"), plan.videoLanes.map { it.trackId })
        assertTrue(plan.notes.none { it.contains("OFFLINE") })
    }

    @Test fun visibleTitlesInDrawOrderWithFadesAndKeyframes() {
        val fadeIn = title("low", 0, 4, video = VideoProps(fadeInUs = s(1)))
        val keyed = title("high", 0, 4).copy(keyframes = mapOf(AnimParam.POSITION_Y to KeyframeTrack(listOf(Keyframe(0, 0.0), Keyframe(s(2), 0.4)))))
        val t = Timeline(videoTracks = listOf(titles("T1", fadeIn), vtrack("V2"), titles("T2", keyed)))
        val ev = TitleEvaluator(t)
        val at = ev.visibleAt(s(0.5))
        assertEquals(listOf("low", "high"), at.map { it.clipId })
        assertEquals(0.5f, at[0].layer.alpha, 1e-4f)
        assertEquals(0.1f, at[1].layer.positionY, 1e-4f)
        assertEquals(emptyList<String>(), ev.visibleAt(s(4)).map { it.clipId })
    }

    @Test fun mutedTrackAndBlankTextAreHidden() {
        val t = Timeline(videoTracks = listOf(titles("T1", title("a", 0, 2), muted = true), titles("T2", title("b", 0, 2, text = "  "))))
        assertTrue(TitleEvaluator(t).visibleAt(s(1)).isEmpty())
    }

    @Test fun titleTrimIsNotLimitedBySource() {
        val fx = Fx()
        val t = Timeline(videoTracks = listOf(titles("T1", title("t", 0, 2))))
        val r = TimelineEditor.trim(t, fx.ctx, "t", Edge.TAIL, s(60), ripple = true).ok()
        assertEquals(s(62), r.timeline.c("t").durationUs)
    }

    @Test fun addTitleUsesAFreeTitlesTrackOrCreatesOneOnTopWithoutRipple() {
        val fx = Fx()
        val base = Timeline(videoTracks = listOf(vtrack("V1", clip("a", 0, 10))))
        val r1 = TimelineEditor.addTitle(base, fx.ctx, s(2), s(3), TextProps(text = "Hola\nmundo")).ok()
        val t1 = r1.timeline
        assertEquals(2, t1.videoTracks.size)
        assertEquals(TrackRole.TITLES, t1.videoTracks.last().role)
        val title = t1.videoTracks.last().clips.single()
        assertEquals(ClipKind.TEXT, title.kind); assertEquals("Hola", title.name); assertEquals(s(2), title.timelineStartUs)
        assertEquals(s(0), t1.c("a").timelineStartUs) // nothing rippled
        // Free span on the same track: reused.
        val t2 = TimelineEditor.addTitle(t1, fx.ctx, s(6), s(2), TextProps()).ok().timeline
        assertEquals(2, t2.videoTracks.size); assertEquals(2, t2.videoTracks.last().clips.size)
        // Overlapping span: a new titles track on top.
        val t3 = TimelineEditor.addTitle(t2, fx.ctx, s(3), s(1), TextProps()).ok().timeline
        assertEquals(3, t3.videoTracks.size); assertEquals(1, t3.videoTracks.last().clips.size)
        // Undo of the first add removes both the track and the clip.
        val undone = r1.changes.inverse().applyTo(t1)
        assertEquals(base, undone)
    }
}
