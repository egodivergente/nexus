package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TimelineValidator

/**
 * Runs one edit atomically on a scratch copy: either the whole edit applies and every invariant
 * still holds, or nothing changes and the user gets a reason.
 */
internal object Engine {
    private const val ORIGIN_TOLERANCE_US = 2L

    fun run(base: Timeline, ctx: EditContext, label: String, block: (WorkingTimeline) -> Long): EditResult {
        val w = WorkingTimeline(base, ctx)
        val delta: Long
        try {
            delta = block(w)
            Transitions.prune(w)
            checkLinkRigidity(base, w)
        } catch (e: EditRejected) {
            return EditResult.Rejected(e.message ?: "Edición no permitida")
        }
        val result = w.build()
        val before = TimelineValidator.violations(base, ctx.media).toSet()
        val newViolations = TimelineValidator.violations(result, ctx.media).filter { it !in before }
        if (newViolations.isNotEmpty()) {
            return EditResult.Rejected("Error interno del motor (edición anulada): ${newViolations.first()}")
        }
        val changes = ChangeSet.diff(base, result)
        return EditResult.Applied(
            timeline = result,
            changes = changes,
            label = label,
            warnings = w.warnings.toList(),
            collateral = w.collateral.distinct(),
            appliedDeltaUs = delta,
            createdIds = w.created.toList(),
        )
    }

    /**
     * Safety net behind rule R3: every link group must have moved rigidly (all members' source
     * origins shifted by the same amount). Split halves are traced back to the clip they came from.
     */
    private fun checkLinkRigidity(base: Timeline, w: WorkingTimeline) {
        val beforeOrigin = HashMap<String, Long>()
        for (t in base.allTracks) for (c in t.clips) if (c.linkId != null) beforeOrigin[c.id] = ClipOps.originUs(c)
        if (beforeOrigin.isEmpty()) return
        val deltas = HashMap<String, MutableList<Long>>()
        for (t in w.tracks) for (c in t.clips) {
            val link = c.linkId ?: continue
            var ancestor: String? = c.id
            while (ancestor != null && ancestor !in beforeOrigin) ancestor = w.parentOf[ancestor]
            val origin = ancestor?.let { beforeOrigin[it] } ?: continue
            deltas.getOrPut(link) { ArrayList() } += ClipOps.originUs(c) - origin
        }
        for ((_, list) in deltas) {
            if (list.max() - list.min() > ORIGIN_TOLERANCE_US) {
                reject("La edición desincronizaría clips vinculados (vídeo/audio). Desvincúlalos primero si es intencionado.")
            }
        }
    }
}
