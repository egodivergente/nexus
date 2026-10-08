package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus

/** A preference written before DB insertion must not leave all later requests waiting forever. */
internal object FlowQueueDependency {
    fun canProceed(previous: GenerationJob?, hasRemote: Boolean): Boolean {
        if(previous == null) return !hasRemote
        if(previous.parameters["__submission_uncertain"] == "true") return false
        return previous.status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELLED)
    }
}
