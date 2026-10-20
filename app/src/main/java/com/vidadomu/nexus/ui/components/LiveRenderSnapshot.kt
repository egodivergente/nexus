package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus

internal data class LiveRenderSnapshot(val active: List<GenerationJob>, val completed: GenerationJob?)
internal fun liveRenderSnapshot(jobs: List<GenerationJob>, now: Long, dismissed: Set<String>): LiveRenderSnapshot {
    val activeStates = setOf(GenerationStatus.QUEUED, GenerationStatus.UPLOADING, GenerationStatus.GENERATING,
        GenerationStatus.PROCESSING, GenerationStatus.RENDERING)
    return LiveRenderSnapshot(
        jobs.filter { it.status in activeStates && it.parameters["__tracking_paused"] != "true" }
            .sortedByDescending { it.startedAt },
        jobs.filter { it.status == GenerationStatus.COMPLETED && it.id !in dismissed && it.outputs.isNotEmpty() &&
            it.completedAt?.let { completed -> now >= completed && now - completed <= 20_000L } == true }
            .maxByOrNull { it.completedAt ?: 0L })
}
