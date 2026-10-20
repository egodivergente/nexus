package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Timeline

/** Magnetism. Pure: the UI converts its pixel threshold to microseconds for the current zoom. */
object Snapping {
    enum class Kind { PLAYHEAD, CLIP_EDGE, MARKER, ZERO }

    data class Target(val timeUs: Long, val kind: Kind)

    fun targets(timeline: Timeline, playheadUs: Long, exclude: Set<String>): List<Target> {
        val out = ArrayList<Target>()
        out += Target(0, Kind.ZERO)
        out += Target(playheadUs, Kind.PLAYHEAD)
        for (t in timeline.allTracks) for (c in t.clips) {
            if (c.id in exclude) continue
            out += Target(c.timelineStartUs, Kind.CLIP_EDGE)
            out += Target(c.endUs, Kind.CLIP_EDGE)
        }
        for (m in timeline.markers) out += Target(m.timeUs, Kind.MARKER)
        return out.sortedBy { it.timeUs }
    }

    /** Nearest target to [timeUs] within [thresholdUs], or null. */
    fun nearest(targets: List<Target>, timeUs: Long, thresholdUs: Long): Target? {
        if (targets.isEmpty()) return null
        var lo = 0
        var hi = targets.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (targets[mid].timeUs < timeUs) lo = mid + 1 else hi = mid
        }
        var best: Target? = null
        for (i in maxOf(0, lo - 1)..minOf(targets.size - 1, lo)) {
            val d = Math.abs(targets[i].timeUs - timeUs)
            if (d <= thresholdUs && (best == null || d < Math.abs(best.timeUs - timeUs))) best = targets[i]
        }
        return best
    }

    /**
     * Snaps a moving span [startUs, startUs+durationUs): whichever edge is closer to a target wins.
     * Returns the adjusted start and the target used.
     */
    fun snapSpan(targets: List<Target>, startUs: Long, durationUs: Long, thresholdUs: Long): Pair<Long, Target?> {
        val a = nearest(targets, startUs, thresholdUs)
        val b = nearest(targets, startUs + durationUs, thresholdUs)
        val da = a?.let { Math.abs(it.timeUs - startUs) } ?: Long.MAX_VALUE
        val db = b?.let { Math.abs(it.timeUs - (startUs + durationUs)) } ?: Long.MAX_VALUE
        return when {
            a == null && b == null -> startUs to null
            da <= db -> a!!.timeUs to a
            else -> (b!!.timeUs - durationUs) to b
        }
    }
}
