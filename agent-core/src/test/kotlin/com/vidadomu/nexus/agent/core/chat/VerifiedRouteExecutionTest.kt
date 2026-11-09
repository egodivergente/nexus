package com.vidadomu.nexus.agent.core.chat

import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.routing.comparison.*
import java.math.BigDecimal
import java.time.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest

/** Synthetic evidence only; these amounts and identities are not provider tariffs. */
class VerifiedRouteExecutionTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val evidence = Evidence("https://example.test/contract", now, "Synthetic exact request receipt")
    private val identity = ExactModelIdentity("publisher", "model-pro", "1.0")
    private val selected = RouteKey(ModelRef("a", "model-pro"), identity, "/generate-pro", "sha256:a")
    private val other = selected.copy(model = ModelRef("b", "model-pro"), contractRevision = "sha256:b")
    private val configuration = RequestedConfiguration("video", setOf("first-last"),
        mapOf("resolution" to "1080p", "duration_seconds" to "5", "fps" to "24", "audio" to "true"),
        listOf(ReferenceInput("first", "image", "opaque-first"), ReferenceInput("last", "image", "opaque-last")), 1, "digest", true)
    private val age = Duration.ofMinutes(5)
    private val engine = VerifiedRouteExecution(Clock.fixed(now, ZoneOffset.UTC), FreshnessPolicy(age, age, age, age, Duration.ZERO))
    private val equivalences = listOf(ExecutionEquivalence(selected, other, configuration, true, evidence))
    private fun offer(key: RouteKey, amount: String) = FundedRoute(
        ProviderRoute(key, evidence, EndpointContract("video", configuration.capabilities, configuration.parameters.keys, emptySet(),
            ConfigurationReceipt(key, configuration, evidence)), false, Availability.AVAILABLE),
        PriceObservation(key, configuration, BigDecimal(amount), "USD", PriceBasis.REQUEST_TOTAL, true, evidence),
        BigDecimal("100"), true, evidence, now.plusSeconds(60))

    @Test fun sameRequestUsesTheCheapestFundedEquivalentRoute() {
        assertEquals(listOf(other, selected), engine.rank(selected, configuration, "USD",
            listOf(offer(selected, "2"), offer(other, "1")), equivalences).map { it.route.key })
    }
    @Test fun unknownEquivalenceDifferentReferencesAndTurboNeverQualify() {
        assertTrue(engine.rank(selected, configuration, "USD", listOf(offer(other, "1")), emptyList()).isEmpty())
        val turbo = other.copy(identity = identity.copy(model = "model-turbo"))
        assertTrue(engine.rank(selected, configuration, "USD", listOf(offer(turbo, "0.1")),
            listOf(ExecutionEquivalence(selected, turbo, configuration, true, evidence))).isEmpty())
        assertTrue(engine.rank(selected, configuration.copy(references = configuration.references.reversed()), "USD",
            listOf(offer(selected, "1")), equivalences).isEmpty())
    }
    @Test fun insufficientBalanceQuotaBaseRatesExpiredPricesAndUnknownAvailabilityAreExcluded() {
        val base = offer(selected, "2")
        val invalid = listOf(base.copy(spendable = BigDecimal.ONE), base.copy(quotaSufficient = false),
            base.copy(quote = base.quote.copy(basis = PriceBasis.BASE)), base.copy(validUntil = now),
            base.copy(route = base.route.copy(availability = Availability.UNKNOWN)),
            base.copy(quote = base.quote.copy(evidence = evidence.copy(observedAt = now.minusSeconds(301)))))
        invalid.forEach { assertTrue(engine.rank(selected, configuration, "USD", listOf(it), equivalences).isEmpty()) }
    }
    @Test fun rejectionIsReconciledBeforeFallbackAndBothAttemptsArePersisted() = runTest {
        val calls = mutableListOf<String>()
        val result = engine.execute(selected, configuration, "USD", listOf(offer(selected, "2"), offer(other, "1")), equivalences,
            persistAttempt = { calls += "persist:${it.model.providerId}" },
            submit = { calls += "submit:${it.model.providerId}"; if (it == other) RouteAttempt.RejectedBeforeAcceptance else RouteAttempt.Accepted("remote") },
            reconcile = { calls += "check:${it.model.providerId}"; RouteAttempt.RejectedBeforeAcceptance })
        assertEquals(RouteAttempt.Accepted("remote"), result.outcome)
        assertEquals(listOf("persist:b", "submit:b", "check:b", "persist:a", "submit:a"), calls)
    }
    @Test fun timeoutOrUncertainReconciliationNeverSubmitsToAnotherProvider() = runTest {
        for (throws in listOf(false, true)) {
            var posts = 0
            val result = engine.execute(selected, configuration, "USD", listOf(offer(selected, "2"), offer(other, "1")), equivalences,
                persistAttempt = {}, submit = { posts++; if (throws) throw java.net.SocketTimeoutException() else RouteAttempt.RejectedBeforeAcceptance },
                reconcile = { RouteAttempt.Uncertain })
            assertEquals(RouteAttempt.Uncertain, result.outcome)
            assertEquals(1, posts)
        }
    }
    @Test fun recoveringAnExistingRemoteJobReturnsItsReceiptWithoutFallback() = runTest {
        var posts = 0
        val result = engine.execute(selected, configuration, "USD", listOf(offer(selected, "2"), offer(other, "1")), equivalences,
            persistAttempt = {}, submit = { posts++; RouteAttempt.RejectedBeforeAcceptance },
            reconcile = { RouteAttempt.Accepted("already-running") })
        assertEquals(RouteAttempt.Accepted("already-running"), result.outcome)
        assertEquals(1, posts)
    }
}
