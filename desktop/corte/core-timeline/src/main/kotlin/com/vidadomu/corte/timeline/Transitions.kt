package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.Transition
import com.vidadomu.corte.model.TransitionAlignment
import com.vidadomu.corte.model.TransitionKind
import com.vidadomu.corte.timeline.ClipOps.fmt
import com.vidadomu.corte.timeline.ClipOps.label

internal object Transitions {
    const val MIN_TRANSITION_US = 40_000L

    data class Fit(val durationUs: Long, val alignment: TransitionAlignment)

    /** Room for a transition on the cut between [l] and [r], given the other transitions of the track. */
    private fun caps(w: WorkingTimeline, track: WorkingTrack, l: Clip, r: Clip, kind: TransitionKind, ignoreId: String?): Pair<Long, Long> {
        val prevOnL = track.transitions.firstOrNull { it.rightClipId == l.id && it.id != ignoreId }
        val nextOnR = track.transitions.firstOrNull { it.leftClipId == r.id && it.id != ignoreId }
        var capBefore = l.durationUs - (prevOnL?.afterCutUs ?: 0)
        var capAfter = r.durationUs - (nextOnR?.beforeCutUs ?: 0)
        if (kind.needsHandles) {
            capBefore = minOf(capBefore, ClipOps.headHandleUs(r))
            capAfter = minOf(capAfter, ClipOps.tailHandleUs(l, w.ctx.media(l.mediaId)))
        }
        return capBefore.coerceAtLeast(0) to capAfter.coerceAtLeast(0)
    }

    fun fit(w: WorkingTimeline, track: WorkingTrack, l: Clip, r: Clip, kind: TransitionKind, requestedUs: Long, ignoreId: String?): Fit? {
        val (capBefore, capAfter) = caps(w, track, l, r, kind, ignoreId)
        val center = minOf(requestedUs, 2 * minOf(capBefore, capAfter))
        val candidates = listOf(
            Fit(center, TransitionAlignment.CENTER),
            Fit(minOf(requestedUs, capBefore), TransitionAlignment.END_AT_CUT),
            Fit(minOf(requestedUs, capAfter), TransitionAlignment.START_AT_CUT),
        )
        val best = candidates.firstOrNull { it.durationUs >= requestedUs } ?: candidates.maxBy { it.durationUs }
        return best.takeIf { it.durationUs >= MIN_TRANSITION_US }
    }

    fun isValid(w: WorkingTimeline, track: WorkingTrack, tr: Transition): String? {
        val l = track.clip(tr.leftClipId) ?: return "falta un clip"
        val r = track.clip(tr.rightClipId) ?: return "falta un clip"
        if (l.endUs != r.timelineStartUs) return "los clips ya no son contiguos"
        if (tr.kind.trackKind != track.kind) return "tipo de pista incorrecto"
        val (capBefore, capAfter) = caps(w, track, l, r, tr.kind, tr.id)
        if (tr.beforeCutUs > capBefore || tr.afterCutUs > capAfter) {
            return if (tr.kind.needsHandles) "no queda material (handles) o espacio suficiente" else "no cabe en los clips"
        }
        return null
    }

    /** Drops transitions an edit made invalid, and says so. */
    fun prune(w: WorkingTimeline) {
        for (t in w.tracks) {
            val it = t.transitions.iterator()
            while (it.hasNext()) {
                val tr = it.next()
                val why = isValid(w, t, tr) ?: continue
                it.remove()
                val l = t.clip(tr.leftClipId)?.let(::label) ?: tr.leftClipId
                w.warnings += "Transición eliminada en ${t.header.name} ($l): $why."
            }
        }
    }

    fun describe(fit: Fit, requestedUs: Long): String? =
        if (fit.durationUs < requestedUs) "Duración reducida a ${fmt(fit.durationUs)} por falta de material (handles)." else null
}
