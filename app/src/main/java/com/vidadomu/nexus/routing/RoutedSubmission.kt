package com.vidadomu.nexus.routing

import com.vidadomu.nexus.core.model.*
import java.math.BigDecimal

/** Request construction uploads only to the provider about to receive this attempt. */
data class GenerationRoute(val providerId: String, val modelId: String, val totalUsd: BigDecimal,
    val validUntil: Long, val prepareRequest: suspend () -> GenerationRequest)

object RoutingSubmissionPolicy {
    fun recoverableBeforeAcceptance(error: Throwable?): Boolean = when {
        error is java.net.UnknownHostException || error is java.net.ConnectException -> true
        (error as? ProviderError)?.errorCode == "NO_NETWORK" -> true
        (error as? ProviderError)?.httpStatusCode in setOf(401,402,403,404,429) -> true
        else -> false
    }
    fun canFallback(receipt: GenerationJob?, error: Throwable?): Boolean =
        receipt != null && receipt.status == GenerationStatus.FAILED && receipt.providerJobId.isNullOrBlank() &&
            receipt.parameters["__submission_uncertain"] == "false" && recoverableBeforeAcceptance(error)
    fun ordered(routes: List<GenerationRoute>, ceiling: BigDecimal, now: Long): List<GenerationRoute> =
        routes.filter { it.totalUsd.signum()>=0 && it.totalUsd<=ceiling && it.validUntil>now }
            .distinctBy { it.providerId to it.modelId }
            .sortedWith(compareBy<GenerationRoute> { it.totalUsd }.thenBy { it.providerId })
}
