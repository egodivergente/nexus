package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import kotlinx.serialization.json.*

/** Atlas can return HTTP 500 for a prediction that has definitively failed upstream. */
internal object AtlasPredictionFailure {
    fun decode(job: GenerationJob, body: String?, now: Long = System.currentTimeMillis()): GenerationJob? {
        val data = runCatching {
            Json.parseToJsonElement(body ?: return null).jsonObject["data"]?.jsonObject
        }.getOrNull() ?: return null
        val remoteId = (data["id"] as? JsonPrimitive)?.contentOrNull
        if (job.providerJobId.isNullOrBlank() || remoteId != job.providerJobId) return null
        val status = (data["status"] as? JsonPrimitive)?.contentOrNull?.lowercase()
        if (status !in setOf("failed", "error", "cancelled", "canceled")) return null
        val detail = (data["error"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (status in setOf("failed", "error") && detail.isBlank()) return null
        val maximum = Regex("at most (\\d+) reference images allowed", RegexOption.IGNORE_CASE)
            .find(detail)?.groupValues?.get(1)
        return job.copy(
            status = if (status in setOf("cancelled", "canceled")) GenerationStatus.CANCELLED else GenerationStatus.FAILED,
            completedAt = now,
            error = maximum?.let { "El proveedor rechazó las referencias: admite como máximo $it imágenes. Reduce las referencias antes de preparar otro intento." }
                ?: detail.take(1000).ifBlank { "El proveedor canceló este trabajo." },
            parameters = job.parameters - "__tracking_poll_error",
            technicalDetails = "Atlas Prediction ID: $remoteId · Estado confirmado: $status"
        )
    }
}
