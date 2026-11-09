package com.vidadomu.nexus.agent.core.routing.comparison

import com.vidadomu.nexus.agent.core.registry.ModelRef
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Compile-tested integration example. Fixtures describe no real provider, capability or price. */
class IntegrationContractTest {
    private fun assessRoute(
        fal: ProviderRoute,
        atlas: AtlasCatalog,
        request: RequestedConfiguration,
        equivalence: List<EquivalenceEvidence>,
        totals: List<PriceObservation>,
        freshness: FreshnessPolicy,
        fx: List<CurrencyConversion> = emptyList(),
        clock: Clock = Clock.systemUTC(),
    ): RouteDecision = ProviderRoutePolicy(freshness, clock).evaluate(
        fal = fal, atlas = atlas, requested = request,
        equivalences = equivalence, prices = totals, conversions = fx,
    )

    @Test fun `noncanonical modality cannot bypass the required video configuration`() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        val age = Duration.ofHours(1)
        val policy = ProviderRoutePolicy(FreshnessPolicy(age, age, age, age, Duration.ZERO), Clock.fixed(now, ZoneOffset.UTC))
        val e = Evidence("fixture:modality", now, "Synthetic validation fixture")
        val key = RouteKey(ModelRef("fal", "fixture"), ExactModelIdentity("fixture", "model", "v1"), "fixture:endpoint", "revision")
        for (modality in listOf("Video", "VIDEO", " video", "video ")) {
            val request = RequestedConfiguration(modality, setOf("fixture"), emptyMap(), emptyList(), 1, "digest", true)
            val route = ProviderRoute(key, e, EndpointContract(modality, request.capabilities, emptySet(), emptySet(),
                ConfigurationReceipt(key, request, e)), false, Availability.AVAILABLE)
            val d = policy.evaluate(route, AtlasCatalog(emptyList(), true, true, true, Availability.AVAILABLE, e),
                request, emptyList(), emptyList())
            assertEquals(DecisionKind.INSUFFICIENT_EVIDENCE, d.kind)
            assertTrue(d.unverified.any { it.code == ReasonCode.INVALID_REQUEST })
        }
    }

    @Test fun `integration example uses one clock instant and does not require prices for absence`() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        var reads = 0
        val clock = object : Clock() {
            override fun instant(): Instant { reads++; return now }
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        }
        val evidence = Evidence("fixture:integration", now, "Synthetic test attestation, not provider documentation")
        val request = RequestedConfiguration("image", setOf("fixture-operation"), mapOf("resolution" to "fixture-dimensions"),
            emptyList(), 1, "fixture-content-digest", complete = true)
        val key = RouteKey(ModelRef("fal", "fixture-endpoint"), ExactModelIdentity("fixture", "model", "version"),
            "fixture:endpoint", "fixture-contract-revision")
        val fal = ProviderRoute(key, evidence,
            EndpointContract("image", request.capabilities, setOf("resolution"), emptySet(),
                ConfigurationReceipt(key, request, evidence)), false, Availability.AVAILABLE)
        val catalog = AtlasCatalog(emptyList(), true, true, true, Availability.AVAILABLE, evidence)
        val maxAge = Duration.ofHours(1)
        val result = assessRoute(fal, catalog, request, emptyList(), emptyList(),
            FreshnessPolicy(maxAge, maxAge, maxAge, maxAge, Duration.ZERO), clock = clock)
        assertEquals(DecisionKind.MODEL_ABSENT_IN_ATLAS, result.kind)
        assertEquals(1, reads)
        assertTrue(result.comparisons.isEmpty())
        assertTrue(result.facts.none { it.code == ReasonCode.SAVINGS_VERIFIED })
    }
}
