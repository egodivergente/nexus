package com.vidadomu.nexus.core.model

/** A parse/validation exception may happen AFTER POST. It never proves that nothing was billed. */
object SubmissionSafety {
    const val DIGEST_KEY = "__submission_digest"

    fun digest(request: GenerationRequest): String {
        val canonical = request.copy(createdAt = 0L, parameters = request.parameters.toSortedMap())
        val text = kotlinx.serialization.json.Json.encodeToString(GenerationRequest.serializer(), canonical)
        return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun definitelyRejected(error: Throwable?): Boolean =
        error is java.net.UnknownHostException || error is java.net.ConnectException ||
            (error as? ProviderError)?.errorCode == "NO_NETWORK" ||
            (error as? ProviderError)?.httpStatusCode in setOf(400, 401, 402, 403, 404, 422, 429)

    /** Same id is a receipt lookup, never permission to POST again, even after a process restart. */
    fun reuse(existing: GenerationJob, request: GenerationRequest): Result<GenerationJob> {
        if (existing.parameters[DIGEST_KEY] != digest(request))
            return Result.failure(ProviderError("Este identificador ya está registrado. Consulta el trabajo guardado; no se ha reenviado."))
        if (existing.providerId != request.providerId || existing.modelId != request.modelId ||
            existing.modality != request.modality || existing.prompt != request.prompt ||
            existing.negativePrompt != request.negativePrompt ||
            request.parameters.any { (key, value) -> existing.parameters[key] != value })
            return Result.failure(ProviderError("Este identificador ya pertenece a otra solicitud. No se ha reenviado."))
        if (existing.parameters["__submission_uncertain"] == "true" || existing.status in setOf(GenerationStatus.FAILED, GenerationStatus.CANCELLED))
            return Result.failure(ProviderError(existing.error ?: "La solicitud ya se procesó. Comprueba su estado antes de preparar otra."))
        return Result.success(existing)
    }
}
