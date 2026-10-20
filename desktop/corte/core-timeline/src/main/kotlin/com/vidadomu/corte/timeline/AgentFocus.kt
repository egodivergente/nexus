package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.Timeline

/**
 * Dónde mirar después de una edición del agente: el primer instante que cambió de verdad.
 * Con ripple, los clips posteriores solo se DESPLAZAN; eso no cuenta como «lo editado» (no arrastra el foco al final).
 */
object AgentFocus {
    fun timeOf(changes: ChangeSet, after: Timeline): Long? {
        val times = ArrayList<Long>()
        for (c in changes.changes) when (c) {
            is Change.ClipAdded -> times += c.clip.timelineStartUs
            is Change.ClipRemoved -> times += c.clip.timelineStartUs
            is Change.ClipUpdated -> if (!onlyShifted(c.before, c.after)) times += editPoint(c.before, c.after)
            is Change.TransitionAdded -> after.clip(c.transition.leftClipId)?.let { times += it.endUs }
            is Change.TransitionUpdated -> after.clip(c.after.leftClipId)?.let { times += it.endUs }
            is Change.TransitionRemoved -> after.clip(c.transition.leftClipId)?.let { times += it.endUs }
            is Change.MarkerAdded -> times += c.marker.timeUs
            is Change.MarkerUpdated -> times += c.after.timeUs
            is Change.MarkerRemoved -> times += c.marker.timeUs
            is Change.TrackAdded, is Change.TrackRemoved, is Change.TrackHeaderUpdated -> Unit
        }
        if (times.isEmpty()) {
            // Solo desplazamientos (p. ej. mover un clip): el primer clip movido.
            changes.changes.filterIsInstance<Change.ClipUpdated>().minOfOrNull { minOf(it.before.timelineStartUs, it.after.timelineStartUs) }?.let { times += it }
        }
        return times.minOrNull()?.coerceAtLeast(0)
    }

    /** Clips creados o cambiados (no los que solo se desplazaron), que existen tras la edición. */
    fun touchedClips(changes: ChangeSet, after: Timeline): Set<String> = changes.changes.mapNotNull { c ->
        when (c) {
            is Change.ClipAdded -> c.clip.id
            is Change.ClipUpdated -> c.after.id.takeIf { !onlyShifted(c.before, c.after) }
            else -> null
        }
    }.filter { after.clip(it) != null }.toSet()

    private fun onlyShifted(b: Clip, a: Clip): Boolean =
        b.timelineStartUs != a.timelineStartUs && b.durationUs == a.durationUs && b.sourceInUs == a.sourceInUs &&
            b.copy(timelineStartUs = a.timelineStartUs, keyframes = a.keyframes) == a

    private fun editPoint(b: Clip, a: Clip): Long = when {
        b.timelineStartUs != a.timelineStartUs -> minOf(b.timelineStartUs, a.timelineStartUs)
        b.durationUs != a.durationUs -> minOf(b.endUs, a.endUs)
        else -> a.timelineStartUs
    }
}
