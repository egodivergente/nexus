package com.vidadomu.nexus.agent.core.routing.comparison

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** A trusted adapter supplies request-specific evidence, never a parsed balance label or unit rate. */
data class FundedRoute(
    val route: ProviderRoute,
    val quote: PriceObservation,
    val spendable: BigDecimal,
    val quotaSufficient: Boolean,
    val fundingEvidence: Evidence,
    val validUntil: Instant,
)

data class ExecutionEquivalence(val selected: RouteKey, val alternative: RouteKey,
    val configuration: RequestedConfiguration, val preservesQuality: Boolean, val evidence: Evidence)

sealed interface RouteAttempt {
    data class Accepted(val remoteId: String) : RouteAttempt
    /** The adapter must attest that rejection happened before accepting a billable task. */
    data object RejectedBeforeAcceptance : RouteAttempt
    data object Uncertain : RouteAttempt
}

data class ExecutionTrace(val route: RouteKey, val outcome: RouteAttempt)
data class RouteExecution(val outcome: RouteAttempt, val trace: List<ExecutionTrace>)

/**
 * Execution boundary for NEX-UX-006. It is intentionally separate from the advisory comparison.
 * No production adapter may opt in without identity, equivalence, full-price and funding evidence.
 * The caller persists one logical job BEFORE calling this and durably records every attempt.
 */
class VerifiedRouteExecution(private val clock: Clock, private val freshness: FreshnessPolicy) {
    fun rank(selected: RouteKey, configuration: RequestedConfiguration, currency: String,
        offers: List<FundedRoute>, equivalences: List<ExecutionEquivalence>): List<FundedRoute> {
        val now = clock.instant()
        if (!configuration.complete || configuration.inputDigest.isBlank() || configuration.quantity <= 0 ||
            !currency.matches(Regex("[A-Z]{3}")) || offers.map { it.route.key }.distinct().size != offers.size) return emptyList()
        fun fresh(e: Evidence?, age: java.time.Duration): Boolean = e != null && e.source.isNotBlank() &&
            e.statement.isNotBlank() && !e.observedAt.isBefore(now.minus(age)) && !e.observedAt.isAfter(now.plus(freshness.futureTolerance))
        return offers.filter { offer ->
            val route = offer.route
            val price = offer.quote
            val receipt = route.contract.receipt
            val equivalence = equivalences.singleOrNull { it.selected == selected && it.alternative == route.key && it.configuration == configuration }
            val conflicting = equivalences.any { it.selected == selected && it.alternative == route.key &&
                it.configuration == configuration && !it.preservesQuality }
            route.key.identity == selected.identity && route.key.endpoint.isNotBlank() && route.key.contractRevision.isNotBlank() &&
                !route.hidden && route.availability == Availability.AVAILABLE &&
                fresh(route.identityEvidence, freshness.evidenceMaxAge) &&
                route.contract.modality == configuration.modality &&
                route.contract.capabilities.containsAll(configuration.capabilities) &&
                route.contract.unresolvedRequiredParameters.isEmpty() &&
                configuration.parameters.keys.containsAll(route.contract.requiredParameters) &&
                receipt?.route == route.key && receipt.configuration == configuration && fresh(receipt.evidence, freshness.evidenceMaxAge) &&
                !conflicting && (route.key == selected || equivalence?.let { it.preservesQuality && fresh(it.evidence, freshness.evidenceMaxAge) } == true) &&
                price.route == route.key && price.configuration == configuration && price.currency == currency &&
                price.basis == PriceBasis.REQUEST_TOTAL && price.includesAllMandatoryCharges &&
                price.amount?.let { it.signum() >= 0 && offer.spendable >= it } == true &&
                fresh(price.evidence, freshness.quoteMaxAge) && price.validUntil?.let { now.isBefore(it) } != false &&
                offer.quotaSufficient && fresh(offer.fundingEvidence, freshness.quoteMaxAge) && now.isBefore(offer.validUntil)
        }.sortedWith(compareBy<FundedRoute> { it.quote.amount }.thenBy { it.route.key.model.toString() })
    }

    /** A timeout, cancellation, server error or invalid response never triggers another billable POST. */
    suspend fun execute(selected: RouteKey, configuration: RequestedConfiguration, currency: String,
        offers: List<FundedRoute>, equivalences: List<ExecutionEquivalence>,
        persistAttempt: suspend (RouteKey) -> Unit,
        submit: suspend (RouteKey) -> RouteAttempt,
        reconcile: suspend (RouteKey) -> RouteAttempt): RouteExecution {
        val candidates = rank(selected, configuration, currency, offers, equivalences)
        val trace = mutableListOf<ExecutionTrace>()
        for (offer in candidates) {
            // Recheck expiry immediately before each attempt; earlier network work may take minutes.
            if (rank(selected, configuration, currency, listOf(offer), equivalences).isEmpty()) continue
            persistAttempt(offer.route.key)
            val outcome = try { submit(offer.route.key) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { RouteAttempt.Uncertain }
            trace += ExecutionTrace(offer.route.key, outcome)
            if (outcome != RouteAttempt.RejectedBeforeAcceptance) return RouteExecution(outcome, trace.toList())
            // Check the same provider before considering another. Failure to reconcile stops here.
            val reconciled = try { reconcile(offer.route.key) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { RouteAttempt.Uncertain }
            trace += ExecutionTrace(offer.route.key, reconciled)
            if (reconciled != RouteAttempt.RejectedBeforeAcceptance) return RouteExecution(reconciled, trace.toList())
        }
        return RouteExecution(RouteAttempt.RejectedBeforeAcceptance, trace.toList())
    }
}
