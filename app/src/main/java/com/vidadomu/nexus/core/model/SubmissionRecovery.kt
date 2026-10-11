package com.vidadomu.nexus.core.model

/** A process can die after sending but before recording the provider id. Never resubmit that job. */
fun GenerationJob.recoverInterruptedSubmission(): GenerationJob =
    if (providerJobId.isNullOrBlank() && status in setOf(GenerationStatus.QUEUED, GenerationStatus.GENERATING, GenerationStatus.PROCESSING, GenerationStatus.RENDERING))
        copy(status = GenerationStatus.FAILED, error = "Envio interrumpido sin confirmacion. Consulta el proveedor antes de repetir: podria haberse iniciado.",
            parameters = parameters + ("__submission_uncertain" to "true"))
    else this

fun GenerationJob.pauseExpiredTracking(now: Long = System.currentTimeMillis()): GenerationJob {
    val since = parameters["__tracking_started_at"]?.toLongOrNull() ?: startedAt
    if (status !in setOf(GenerationStatus.QUEUED, GenerationStatus.GENERATING, GenerationStatus.PROCESSING, GenerationStatus.RENDERING) ||
        providerJobId.isNullOrBlank() || providerJobId.startsWith("queue_") || now - since < 30 * 60_000L) return this
    return copy(status = GenerationStatus.FAILED, parameters = parameters + ("__tracking_paused" to "true"),
        error = "Seguimiento pausado tras 30 minutos. El proveedor puede seguir trabajando; usa Recuperar para consultar sin generar otra vez.")
}
