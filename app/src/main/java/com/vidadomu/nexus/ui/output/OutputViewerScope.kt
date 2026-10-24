package com.vidadomu.nexus.ui.output

/** A navigation hand-off only: each viewer captures its collection once, then owns it. */
internal object OutputViewerScope {
    private var pending: Pair<String, List<Pair<String, Int>>>? = null
    @Synchronized fun prepare(jobId: String, outputs: List<Pair<String, Int>>) {
        pending=jobId to outputs.distinct()
    }
    @Synchronized fun take(jobId: String): List<Pair<String, Int>> {
        val value=pending
        pending=null
        return value?.takeIf {it.first==jobId}?.second.orEmpty()
    }
}
