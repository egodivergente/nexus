package com.vidadomu.nexus.provider.registry

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus

/** Providers removed from the app whose past jobs stay in the history but are never queried or sent again. */
object RetiredProviders {
    const val GOOGLE_FLOW = "google_flow"
    const val PREVIOUS_STATUS_KEY = "__retired_previous_status"
    const val SUBMIT_MESSAGE = "Google Flow ya no está disponible en Nexus. No se ha enviado nada; elige otro modelo."
    const val TRACKING_MESSAGE = "Google Flow ya no está disponible en Nexus: este trabajo se conserva, pero ya no se consulta. Revisa su estado directamente en Flow."

    private val ids = setOf(GOOGLE_FLOW)
    private val active = setOf(GenerationStatus.QUEUED, GenerationStatus.UPLOADING, GenerationStatus.GENERATING,
        GenerationStatus.PROCESSING, GenerationStatus.RENDERING)

    fun isRetired(providerId: String?): Boolean = providerId in ids

    /** Stops local tracking the same way «Detener seguimiento» does; prompt, outputs and remote id are kept. */
    fun pauseTracking(job: GenerationJob): GenerationJob =
        if (!isRetired(job.providerId) || job.status !in active) job
        else job.copy(status = GenerationStatus.FAILED, error = TRACKING_MESSAGE,
            parameters = job.parameters + mapOf("__tracking_paused" to "true", PREVIOUS_STATUS_KEY to job.status.name))
}
