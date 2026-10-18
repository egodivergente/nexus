package com.vidadomu.corte.timeline.render

import com.vidadomu.corte.model.ClipKind
import com.vidadomu.corte.model.Rational
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TrackKind

/** A contiguous piece of a lane: either a gap or a span of one clip's media. */
data class Segment(
    val timelineStartUs: Long,
    val durationUs: Long,
    val clipId: String? = null,
    val mediaId: String? = null,
    val kind: ClipKind? = null,
    val sourceStartUs: Long = 0,
    val speed: Rational = Rational.ONE,
) {
    val isGap: Boolean get() = clipId == null
    val endUs: Long get() = timelineStartUs + durationUs
}

/**
 * One render input. A track normally maps to one lane; a track with handle-based transitions gets a
 * second lane (A/B roll) so that the two clips of a dissolve can overlap in time. Every lane covers
 * [0, plan duration) exactly, gaps included, so that all inputs stay aligned.
 */
data class Lane(val trackId: String, val lane: Int, val kind: TrackKind, val segments: List<Segment>) {
    val key: String get() = "$trackId#$lane"
    private val starts: LongArray = LongArray(segments.size) { segments[it].timelineStartUs }

    fun segmentAt(timeUs: Long): Segment? {
        if (segments.isEmpty()) return null
        var i = java.util.Arrays.binarySearch(starts, timeUs)
        if (i < 0) i = -i - 2
        if (i < 0) return null
        val s = segments[i]
        return if (timeUs < s.endUs) s else null
    }
}

data class RenderPlan(
    val durationUs: Long,
    /** Top-most first: index 0 is drawn above everything else. */
    val videoLanes: List<Lane>,
    val audioLanes: List<Lane>,
    val notes: List<String>,
) {
    val isEmpty: Boolean get() = durationUs <= 0 || (videoLanes.isEmpty() && audioLanes.isEmpty())

    /**
     * The part of the plan from [startUs] on, re-timed so that [startUs] becomes 0. Segments that
     * straddle [startUs] are cut and their source start advanced (respecting speed). Lane order,
     * and therefore lane indices, is preserved; a lane may end up as a single gap.
     */
    fun window(startUs: Long): RenderPlan {
        if (startUs <= 0) return this
        val s = minOf(startUs, durationUs)
        fun cut(l: Lane) = l.copy(segments = l.segments.mapNotNull { seg ->
            when {
                seg.endUs <= s -> null
                seg.timelineStartUs >= s -> seg.copy(timelineStartUs = seg.timelineStartUs - s)
                else -> {
                    val skip = s - seg.timelineStartUs
                    seg.copy(
                        timelineStartUs = 0,
                        durationUs = seg.durationUs - skip,
                        sourceStartUs = if (seg.isGap) seg.sourceStartUs else seg.sourceStartUs + seg.speed.scale(skip),
                    )
                }
            }
        })
        return RenderPlan(durationUs - s, videoLanes.map(::cut), audioLanes.map(::cut), notes)
    }
}

/** True when a lane has no media at all (only gaps). */
val Lane.isAllGap: Boolean get() = segments.all { it.isGap }

object RenderPlanner {
    /**
     * @param isOnline media that is offline renders as a gap (never crashes, never drops the clip
     * from the project).
     */
    fun plan(timeline: Timeline, isOnline: (String) -> Boolean): RenderPlan {
        val duration = timeline.durationUs
        val notes = ArrayList<String>()
        val video = ArrayList<Lane>()
        val audio = ArrayList<Lane>()
        if (duration <= 0) return RenderPlan(0, video, audio, notes)

        for (track in timeline.allTracks) {
            if (track.kind == TrackKind.VIDEO && track.muted) continue
            val laneOf = HashMap<String, Int>()
            var prev: String? = null
            for (c in track.clips) {
                val handles = prev != null && track.transitions.any { it.leftClipId == prev && it.rightClipId == c.id && it.kind.needsHandles }
                laneOf[c.id] = if (handles) 1 - laneOf.getValue(prev) else 0
                prev = c.id
            }
            val pieces = Array(2) { ArrayList<Segment>() }
            for (c in track.clips) {
                if (c.kind == ClipKind.TEXT) continue // titles are drawn over the composite (TitleEvaluator)
                if (!isOnline(c.mediaId)) {
                    notes += "MEDIA OFFLINE: «${c.name.ifBlank { c.id }}» se muestra como hueco."
                    continue
                }
                val inTr = track.transitions.firstOrNull { it.rightClipId == c.id && it.kind.needsHandles }
                val outTr = track.transitions.firstOrNull { it.leftClipId == c.id && it.kind.needsHandles }
                val before = inTr?.beforeCutUs ?: 0
                val after = outTr?.afterCutUs ?: 0
                pieces[laneOf.getValue(c.id)] += Segment(
                    timelineStartUs = c.timelineStartUs - before,
                    durationUs = c.durationUs + before + after,
                    clipId = c.id,
                    mediaId = c.mediaId,
                    kind = c.kind,
                    sourceStartUs = c.sourceInUs - c.speed.scale(before),
                    speed = c.speed,
                )
            }
            val lanes = (0..1).mapNotNull { l ->
                if (pieces[l].isEmpty()) null else Lane(track.id, l, track.kind, fill(pieces[l], duration))
            }
            if (track.kind == TrackKind.VIDEO) video.addAll(0, lanes.reversed()) else audio.addAll(lanes)
        }
        return RenderPlan(duration, video, audio, notes.distinct())
    }

    private fun fill(pieces: List<Segment>, duration: Long): List<Segment> {
        val out = ArrayList<Segment>()
        var t = 0L
        for (p in pieces.sortedBy { it.timelineStartUs }) {
            check(p.timelineStartUs >= t) { "lane overlap at ${p.timelineStartUs}" }
            if (p.timelineStartUs > t) out += Segment(t, p.timelineStartUs - t)
            out += p
            t = p.endUs
        }
        if (t < duration) out += Segment(t, duration - t)
        return out
    }
}
