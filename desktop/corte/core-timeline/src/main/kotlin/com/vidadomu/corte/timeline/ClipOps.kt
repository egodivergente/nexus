package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.MediaRef
import com.vidadomu.corte.model.Timecode

internal object ClipOps {
    fun label(c: Clip): String = c.name.ifBlank { c.id }

    fun fmt(us: Long): String = Timecode.formatMillis(us)

    /** Keeps fades inside the clip after its duration changed. */
    fun clampFades(c: Clip): Clip {
        val v = c.video
        val a = c.audio
        val vIn = v.fadeInUs.coerceIn(0, c.durationUs)
        val vOut = v.fadeOutUs.coerceIn(0, c.durationUs - vIn)
        val aIn = a.fadeInUs.coerceIn(0, c.durationUs)
        val aOut = a.fadeOutUs.coerceIn(0, c.durationUs - aIn)
        if (vIn == v.fadeInUs && vOut == v.fadeOutUs && aIn == a.fadeInUs && aOut == a.fadeOutUs) return c
        return c.copy(video = v.copy(fadeInUs = vIn, fadeOutUs = vOut), audio = a.copy(fadeInUs = aIn, fadeOutUs = aOut))
    }

    private fun pruneKeys(c: Clip): Clip =
        if (c.keyframes.isEmpty()) c else c.copy(keyframes = c.keyframes.mapValues { it.value.prunedTo(c.durationUs) })

    /**
     * Splits [clip] at [atUs] (strictly inside it). The left half keeps the id; the right half gets a
     * new id, and, if linked, a link id shared with the right halves of its partners split at the same
     * time. Transitions on the clip's tail move to the right half.
     */
    fun split(w: WorkingTimeline, track: WorkingTrack, clip: Clip, atUs: Long): Pair<Clip, Clip> {
        check(atUs > clip.timelineStartUs && atUs < clip.endUs) { "split point outside clip" }
        val leftDur = atUs - clip.timelineStartUs
        val rightId = w.newId("clip")
        w.parentOf[rightId] = clip.id
        val left = pruneKeys(
            clip.copy(
                durationUs = leftDur,
                video = clip.video.copy(fadeOutUs = 0),
                audio = clip.audio.copy(fadeOutUs = 0),
            ),
        ).let(::clampFades)
        val right = pruneKeys(
            clip.copy(
                id = rightId,
                timelineStartUs = atUs,
                durationUs = clip.durationUs - leftDur,
                sourceInUs = clip.sourceInUs + clip.speed.scale(leftDur),
                linkId = clip.linkId?.let { w.linkIdForRightHalf(it, atUs) },
                video = clip.video.copy(fadeInUs = 0),
                audio = clip.audio.copy(fadeInUs = 0),
            ).withKeyframesShifted(-leftDur),
        ).let(::clampFades)
        track.replace(left)
        track.clips += right
        track.sort()
        track.transitions.replaceAll { if (it.leftClipId == clip.id) it.copy(leftClipId = rightId) else it }
        return left to right
    }

    /** Moves the head edge to [newStartUs], keeping the content where it is on the timeline. */
    fun trimHeadTo(clip: Clip, newStartUs: Long): Clip {
        val d = newStartUs - clip.timelineStartUs
        if (d == 0L) return clip
        return clampFades(
            pruneKeys(
                clip.copy(
                    timelineStartUs = newStartUs,
                    durationUs = clip.durationUs - d,
                    sourceInUs = clip.sourceInUs + clip.speed.scale(d),
                ).withKeyframesShifted(-d),
            ),
        )
    }

    /** Moves the tail edge to [newEndUs]. */
    fun trimTailTo(clip: Clip, newEndUs: Long): Clip {
        if (newEndUs == clip.endUs) return clip
        return clampFades(pruneKeys(clip.copy(durationUs = newEndUs - clip.timelineStartUs)))
    }

    fun shift(clip: Clip, deltaUs: Long): Clip =
        if (deltaUs == 0L) clip else clip.copy(timelineStartUs = clip.timelineStartUs + deltaUs)

    /** Timeline time of extra media available after the clip's out point (Long.MAX for stills). */
    fun tailHandleUs(clip: Clip, media: MediaRef?): Long {
        if (clip.kind.unboundedSource) return Long.MAX_VALUE / 4
        val total = media?.durationUs ?: return 0
        return clip.speed.inverseScale((total - clip.sourceOutUs).coerceAtLeast(0))
    }

    /** Timeline time of extra media available before the clip's in point. */
    fun headHandleUs(clip: Clip): Long {
        if (clip.kind.unboundedSource) return Long.MAX_VALUE / 4
        return clip.speed.inverseScale(clip.sourceInUs)
    }

    /** Timeline position where source time 0 would sit; equal for clips that are in sync. */
    fun originUs(clip: Clip): Long = clip.timelineStartUs - clip.speed.inverseScale(clip.sourceInUs)
}
