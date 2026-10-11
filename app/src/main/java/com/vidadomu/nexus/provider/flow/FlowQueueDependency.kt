package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus

/** Release later distinct jobs, never retry the previous generation. */
internal object FlowQueueDependency {
    const val MAX_WAIT_MS = 10 * 60_000L
    fun canProceed(previous: GenerationJob?, hasRemote: Boolean, now: Long = System.currentTimeMillis(), waitingSince: Long = now): Boolean {
        if (previous == null) return !hasRemote || now - waitingSince >= MAX_WAIT_MS
        if (previous.status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELLED)) return true
        return now - maxOf(previous.startedAt, waitingSince) >= MAX_WAIT_MS
    }
}
