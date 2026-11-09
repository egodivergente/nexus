package com.vidadomu.nexus.agent.core.routing.comparison

import com.vidadomu.nexus.agent.core.registry.ModelRef
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/*
 * SYNTHETIC FIXTURES ONLY. Every publisher, model, version, endpoint, revision, price, currency rate,
 * fee, capability and digest below is invented for deterministic tests. None of them describes a real
 * Fal or Atlas Cloud offering, price list or capability matrix.
 */
private val NOW: Instant = Instant.parse("2030-01-01T12:00:00Z")
private val FRESHNESS = FreshnessPolicy(
    quoteMaxAge = Duration.ofHours(1),
    catalogMaxAge = Duration.ofHours(1),
    evidenceMaxAge = Duration.ofDays(1),
    conversionMaxAge = Duration.ofHours(1),
    futureTolerance = Duration.ofMinutes(5),
)
private val POLICY = ProviderRoutePolicy(FRESHNESS, Clock.fixed(NOW, ZoneOffset.UTC))

private val IDENTITY = ExactModelIdentity("synthetic-publisher", "synthetic-video-model", "v1-synthetic")

private fun ev(at: Instant = NOW, source: String = "synthetic-fixture", statement: String = "SYNTHETIC test evidence") =
    Evidence(source, at, statement)

private fun key(
    provider: String,
    endpoint: String,
    identity: ExactModelIdentity = IDENTITY,
    revision: String = "rev-synthetic-1",
) = RouteKey(ModelRef(provider, "synthetic/$endpoint"), identity, endpoint, revision)

private val FAL = key("fal", "fal-synthetic/video")
private val ATLAS_A = key("atlas_cloud", "atlas-synthetic/video-a")
private val ATLAS_B = key("atlas_cloud", "atlas-synthetic/video-b")
private val ATLAS_C = key("atlas_cloud", "atlas-synthetic/video-c")
private val ATLAS_OTHER = key("atlas_cloud", "atlas-synthetic/other", IDENTITY.copy(model = "synthetic-other-model"))

private val REQ = RequestedConfiguration(
    modality = "video",
    capabilities = setOf("image_to_video"),
    parameters = mapOf(
        "duration_seconds" to "5",
        "resolution" to "1280x720",
        "fps" to "24",
        "audio" to "false",
        "quality" to "standard",
        "seed_default" to "random",
    ),
    references = listOf(
        ReferenceInput("first_frame", "image", "sha256:synthetic-ref-a"),
        ReferenceInput("style", "image", "sha256:synthetic-ref-b"),
    ),
    quantity = 1,
    inputDigest = "sha256:synthetic-input",
    complete = true,
)

private fun RequestedConfiguration.withParam(name: String, value: String?) =
    copy(parameters = if (value == null) parameters - name else parameters + (name to value))

private fun route(
    key: RouteKey,
    configuration: RequestedConfiguration = REQ,
    receipt: ConfigurationReceipt? = ConfigurationReceipt(key, configuration, ev()),
    hidden: Boolean = false,
    availability: Availability = Availability.AVAILABLE,
    identityEvidence: Evidence? = ev(),
    contractModality: String = configuration.modality,
    requiredParameters: Set<String> = configuration.parameters.keys,
    unresolved: Set<String> = emptySet(),
) = ProviderRoute(
    key, identityEvidence,
    EndpointContract(contractModality, configuration.capabilities, requiredParameters, unresolved, receipt),
    hidden, availability,
)

private fun atlasCatalog(
    routes: List<ProviderRoute>,
    complete: Boolean = true,
    includesHidden: Boolean = true,
    refreshSucceeded: Boolean = true,
    availability: Availability = Availability.AVAILABLE,
    evidence: Evidence? = ev(),
) = AtlasCatalog(routes, complete, includesHidden, refreshSucceeded, availability, evidence)

private fun equivalence(
    atlas: RouteKey = ATLAS_A,
    configuration: RequestedConfiguration = REQ,
    preservesQuality: Boolean = true,
    fal: RouteKey = FAL,
    evidence: Evidence = ev(),
) = EquivalenceEvidence(fal, atlas, configuration, preservesQuality, evidence)

private fun price(
    key: RouteKey,
    amount: String?,
    currency: String? = "USD",
    basis: PriceBasis = PriceBasis.REQUEST_TOTAL,
    allCharges: Boolean = true,
    configuration: RequestedConfiguration = REQ,
    evidence: Evidence? = ev(),
    validUntil: Instant? = null,
) = PriceObservation(key, configuration, amount?.let { BigDecimal(it) }, currency, basis, allCharges, evidence, validUntil)

private fun conversion(
    from: String = "EUR",
    to: String = "USD",
    rate: String = "1.10",
    fee: String = "0.02",
    allCosts: Boolean = true,
    evidence: Evidence = ev(),
    roundingScale: Int? = null,
    roundingMode: RoundingMode? = null,
) = CurrencyConversion(from, to, BigDecimal(rate), BigDecimal(fee), allCosts, evidence, roundingScale, roundingMode)

/** Defaults: Fal 0.40 USD vs one equivalent Atlas route at 0.50 USD (synthetic) -> verified savings. */
private fun evaluate(
    requested: RequestedConfiguration = REQ,
    fal: ProviderRoute = route(FAL, requested),
    atlasRoutes: List<ProviderRoute> = listOf(route(ATLAS_A, requested)),
    catalog: AtlasCatalog = atlasCatalog(atlasRoutes),
    equivalences: List<EquivalenceEvidence> = atlasRoutes.map { equivalence(it.key, requested) },
    prices: List<PriceObservation> = listOf(
        price(FAL, "0.40", configuration = requested),
        price(ATLAS_A, "0.50", configuration = requested),
    ),
    conversions: List<CurrencyConversion> = emptyList(),
    policy: ProviderRoutePolicy = POLICY,
): RouteDecision = policy.evaluate(fal, catalog, requested, equivalences, prices, conversions)

private fun codes(d: RouteDecision) = d.unverified.map { it.code }

private fun assertInsufficient(d: RouteDecision, code: ReasonCode, label: String = "") {
    assertEquals(DecisionKind.INSUFFICIENT_EVIDENCE, d.kind, "$label: ${codes(d)}")
    assertTrue(code in codes(d), "$label: expected $code in ${codes(d)}")
    assertFalse(d.complementsAtlas, label)
}

private fun assertNoComparison(d: RouteDecision, code: ReasonCode, label: String = "") {
    assertInsufficient(d, code, label)
    assertTrue(d.comparisons.isEmpty(), "$label: unexpected comparisons ${d.comparisons}")
}

private fun assertAmount(expected: String, actual: BigDecimal, label: String = "") =
    assertEquals(0, BigDecimal(expected).compareTo(actual), "$label: expected $expected but was $actual")

private class Mutation(val name: String, val apply: (RequestedConfiguration) -> RequestedConfiguration)

private val RECEIPT_MUTATIONS = listOf(
    Mutation("reference role") { c -> c.copy(references = listOf(c.references[0].copy(role = "last_frame"), c.references[1])) },
    Mutation("reference count (missing)") { c -> c.copy(references = c.references.take(1)) },
    Mutation("reference count (extra)") { c -> c.copy(references = c.references + ReferenceInput("style", "image", "sha256:synthetic-ref-c")) },
    Mutation("reference count (none)") { c -> c.copy(references = emptyList()) },
    Mutation("reference order") { c -> c.copy(references = c.references.reversed()) },
    Mutation("reference content") { c -> c.copy(references = listOf(c.references[0].copy(contentId = "sha256:synthetic-other"), c.references[1])) },
    Mutation("reference modality") { c -> c.copy(references = listOf(c.references[0].copy(modality = "video"), c.references[1])) },
    Mutation("duration") { c -> c.withParam("duration_seconds", "10") },
    Mutation("duration non-canonical spelling") { c -> c.withParam("duration_seconds", "5.0") },
    Mutation("resolution") { c -> c.withParam("resolution", "1920x1080") },
    Mutation("fps") { c -> c.withParam("fps", "30") },
    Mutation("audio") { c -> c.withParam("audio", "true") },
    Mutation("effective default omitted") { c -> c.withParam("seed_default", null) },
    Mutation("extra effective default") { c -> c.withParam("negative_prompt_default", "synthetic-empty") },
    Mutation("quality parameter") { c -> c.withParam("quality", "draft") },
    Mutation("input digest") { c -> c.copy(inputDigest = "sha256:synthetic-other-input") },
    Mutation("output quantity") { c -> c.copy(quantity = 2) },
    Mutation("capabilities") { c -> c.copy(capabilities = c.capabilities + "text_to_video") },
    Mutation("modality") { c -> c.copy(modality = "image") },
    Mutation("complete flag") { c -> c.copy(complete = false) },
)

class ProviderRoutePolicyTest {

    // ---- Verified comparisons -------------------------------------------------------------

    @Test fun `exact identity, matching receipts, quality-preserving equivalence and current totals verify savings`() {
        val d = evaluate()
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertTrue(d.complementsAtlas)
        assertEquals(CatalogPresence.PRESENT, d.catalogPresence)
        assertEquals(FAL, d.fal)
        assertTrue(d.unverified.isEmpty(), codes(d).toString())
        val c = d.comparisons.single()
        assertEquals(ATLAS_A, c.atlas)
        assertAmount("0.40", c.falTotal)
        assertAmount("0.50", c.atlasTotal)
        assertAmount("0.10", c.savings)
        assertEquals("USD", c.currency)
        assertEquals(null, c.conversion)
        assertEquals(equivalence(), c.equivalence)
        assertEquals(FAL, c.falQuote.route)
        assertEquals(ATLAS_A, c.atlasQuote.route)
        assertEquals(listOf(ReasonCode.MODEL_PRESENT, ReasonCode.SAVINGS_VERIFIED), d.facts.map { it.code })
        assertEquals(ATLAS_A, d.facts.last().route)
    }

    @Test fun `equal totals, including different scales, are not savings`() {
        for ((falAmount, atlasAmount) in listOf("0.50" to "0.50", "0.5" to "0.500", "0" to "0")) {
            val d = evaluate(prices = listOf(price(FAL, falAmount), price(ATLAS_A, atlasAmount)))
            assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, d.kind, "$falAmount vs $atlasAmount")
            assertFalse(d.complementsAtlas)
            assertEquals(0, d.comparisons.single().savings.signum())
            assertTrue(d.facts.any { it.code == ReasonCode.NO_SAVINGS && it.route == ATLAS_A })
            assertTrue(d.facts.none { it.code == ReasonCode.SAVINGS_VERIFIED })
        }
    }

    @Test fun `higher Fal total is not savings`() {
        val d = evaluate(prices = listOf(price(FAL, "0.75"), price(ATLAS_A, "0.50")))
        assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, d.kind)
        assertAmount("-0.25", d.comparisons.single().savings)
        assertFalse(d.complementsAtlas)
    }

    @Test fun `explicit fully evidenced zero total is a valid total`() {
        val d = evaluate(prices = listOf(price(FAL, "0"), price(ATLAS_A, "0.50")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertAmount("0.50", d.comparisons.single().savings)
    }

    @Test fun `zero total without evidence or as a non-total is not accepted`() {
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0", evidence = null), price(ATLAS_A, "0.50"))), ReasonCode.MISSING_EVIDENCE)
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0", basis = PriceBasis.BASE), price(ATLAS_A, "0.50"))), ReasonCode.NOT_A_TOTAL)
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0", allCharges = false), price(ATLAS_A, "0.50"))), ReasonCode.NOT_A_TOTAL)
    }

    @Test fun `same-currency micro totals keep exact arithmetic`() {
        val d = evaluate(prices = listOf(price(FAL, "0.0001"), price(ATLAS_A, "0.0002")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertAmount("0.0001", d.comparisons.single().savings)
    }

    @Test fun `multi-output request compares totals for the exact quantity`() {
        val batch = REQ.copy(quantity = 3)
        val d = evaluate(requested = batch, prices = listOf(
            price(FAL, "1.20", configuration = batch), price(ATLAS_A, "1.50", configuration = batch)))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertAmount("0.30", d.comparisons.single().savings)
    }

    @Test fun `evaluation is deterministic and independent of price list order`() {
        val prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50"))
        val first = evaluate(prices = prices)
        assertEquals(first, evaluate(prices = prices))
        assertEquals(first, evaluate(prices = prices.reversed()))
    }

    // ---- Price basis and quote validity ------------------------------------------------------

    @Test fun `non-total price bases are rejected even for a single output`() {
        val nonTotals = PriceBasis.values().filter { it != PriceBasis.REQUEST_TOTAL }
        assertEquals(setOf(PriceBasis.COMPUTE_SECOND, PriceBasis.VIDEO_SECOND, PriceBasis.UNIT, PriceBasis.BASE, PriceBasis.UNKNOWN), nonTotals.toSet())
        assertEquals(1, REQ.quantity)
        for (basis in nonTotals) {
            assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", basis = basis), price(ATLAS_A, "0.50"))), ReasonCode.NOT_A_TOTAL, "Fal $basis")
            assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50", basis = basis))), ReasonCode.NOT_A_TOTAL, "Atlas $basis")
        }
    }

    @Test fun `totals missing mandatory charges are rejected`() {
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", allCharges = false), price(ATLAS_A, "0.50"))), ReasonCode.NOT_A_TOTAL)
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50", allCharges = false))), ReasonCode.NOT_A_TOTAL)
    }

    @Test fun `null amount is unknown, never zero, and negative amounts are invalid`() {
        for ((label, fal, atlas) in listOf(
            Triple("Fal null", null, "0.50"), Triple("Atlas null", "0.40", null),
            Triple("Fal negative", "-0.01", "0.50"), Triple("Atlas negative", "0.40", "-0.50"),
        )) {
            assertNoComparison(evaluate(prices = listOf(price(FAL, fal), price(ATLAS_A, atlas))), ReasonCode.INVALID_PRICE, label)
        }
    }

    @Test fun `missing quotes are reported per route`() {
        val falMissing = evaluate(prices = listOf(price(ATLAS_A, "0.50")))
        assertNoComparison(falMissing, ReasonCode.MISSING_PRICE)
        assertTrue(falMissing.unverified.any { it.code == ReasonCode.MISSING_PRICE && it.route == FAL })
        val atlasMissing = evaluate(prices = listOf(price(FAL, "0.40")))
        assertNoComparison(atlasMissing, ReasonCode.MISSING_PRICE)
        assertTrue(atlasMissing.unverified.any { it.code == ReasonCode.MISSING_PRICE && it.route == ATLAS_A })
        assertNoComparison(evaluate(prices = emptyList()), ReasonCode.NO_ELIGIBLE_ATLAS_ROUTE)
    }

    @Test fun `quote for another contract revision or endpoint does not apply`() {
        val otherRevision = ATLAS_A.copy(contractRevision = "rev-synthetic-2")
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(otherRevision, "0.50"))), ReasonCode.MISSING_PRICE)
        assertNoComparison(evaluate(prices = listOf(price(FAL.copy(endpoint = "fal-synthetic/video-fast"), "0.40"), price(ATLAS_A, "0.50"))), ReasonCode.MISSING_PRICE)
    }

    @Test fun `quote scoped to a different quantity or configuration is a scope mismatch`() {
        val batch = REQ.copy(quantity = 2)
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", configuration = batch), price(ATLAS_A, "0.50"))), ReasonCode.QUOTE_SCOPE_MISMATCH, "Fal qty")
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "1.00", configuration = batch))), ReasonCode.QUOTE_SCOPE_MISMATCH, "Atlas qty")
        val hd = REQ.withParam("resolution", "1920x1080")
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", configuration = hd), price(ATLAS_A, "0.50"))), ReasonCode.QUOTE_SCOPE_MISMATCH, "resolution")
        // A single-output quote is never multiplied up to a batch request.
        val triple = REQ.copy(quantity = 3)
        assertNoComparison(evaluate(requested = triple, prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "1.50", configuration = triple))),
            ReasonCode.QUOTE_SCOPE_MISMATCH, "unit quote for batch")
    }

    @Test fun `multiple quotes for the same request are ambiguous rather than cherry-picked`() {
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(FAL, "0.10"), price(ATLAS_A, "0.50"))), ReasonCode.INVALID_PRICE)
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50"), price(ATLAS_A, "0.90"))), ReasonCode.INVALID_PRICE)
    }

    @Test fun `quote evidence must exist, be sourced, not future and not stale`() {
        val cases = listOf(
            Triple("null", null as Evidence?, ReasonCode.MISSING_EVIDENCE),
            Triple("blank source", ev(source = " "), ReasonCode.MISSING_EVIDENCE),
            Triple("blank statement", ev(statement = ""), ReasonCode.MISSING_EVIDENCE),
            Triple("future", ev(NOW.plus(Duration.ofMinutes(5)).plusSeconds(1)), ReasonCode.FUTURE_EVIDENCE),
            Triple("stale", ev(NOW.minus(Duration.ofHours(1)).minusSeconds(1)), ReasonCode.STALE_EVIDENCE),
            Triple("Instant.MAX", ev(Instant.MAX), ReasonCode.FUTURE_EVIDENCE),
            Triple("Instant.MIN", ev(Instant.MIN), ReasonCode.STALE_EVIDENCE),
        )
        for ((label, evidence, code) in cases) {
            assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", evidence = evidence), price(ATLAS_A, "0.50"))), code, "Fal $label")
            assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50", evidence = evidence))), code, "Atlas $label")
        }
    }

    @Test fun `quote freshness boundaries are inclusive`() {
        for (at in listOf(NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofMinutes(5)))) {
            val d = evaluate(prices = listOf(price(FAL, "0.40", evidence = ev(at)), price(ATLAS_A, "0.50", evidence = ev(at))))
            assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, "$at ${codes(d)}")
        }
    }

    @Test fun `quote validUntil must be in the future and after its observation`() {
        val ok = evaluate(prices = listOf(price(FAL, "0.40", validUntil = NOW.plusSeconds(1)), price(ATLAS_A, "0.50", validUntil = NOW.plusSeconds(60))))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, ok.kind, codes(ok).toString())
        for ((label, validUntil, observed) in listOf(
            Triple("expires now", NOW, NOW.minusSeconds(60)),
            Triple("expired", NOW.minusSeconds(1), NOW.minusSeconds(60)),
            Triple("before observation", NOW.plusSeconds(60), NOW.plusSeconds(120)),
            Triple("equals observation", NOW.plusSeconds(60), NOW.plusSeconds(60)),
        )) {
            assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", evidence = ev(observed), validUntil = validUntil), price(ATLAS_A, "0.50"))),
                ReasonCode.STALE_EVIDENCE, "Fal $label")
            assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50", evidence = ev(observed), validUntil = validUntil))),
                ReasonCode.STALE_EVIDENCE, "Atlas $label")
        }
    }

    @Test fun `long validUntil does not extend the configured quote max age`() {
        val old = ev(NOW.minus(Duration.ofHours(2)))
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", evidence = old, validUntil = NOW.plus(Duration.ofDays(30))), price(ATLAS_A, "0.50"))),
            ReasonCode.STALE_EVIDENCE)
    }

    @Test fun `currencies must be identified ISO codes, not pseudo-currencies or metals`() {
        val invalid = listOf(null, "", "usd", "US", "USDT", "ABC", "XXX", "XTS", "XAU", "XAG", "XPT", "XPD")
        for (code in invalid) {
            assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", currency = code), price(ATLAS_A, "0.50", currency = code))),
                ReasonCode.INVALID_PRICE, "currency $code")
        }
        val jpy = evaluate(prices = listOf(price(FAL, "40", currency = "JPY"), price(ATLAS_A, "50", currency = "JPY")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, jpy.kind, codes(jpy).toString())
    }

    // ---- Currency conversion -----------------------------------------------------------------

    @Test fun `currency mismatch without a conversion is unverified`() {
        assertNoComparison(evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50"))), ReasonCode.CURRENCY_MISMATCH)
    }

    @Test fun `documented direct conversion applies rate and fee to the Fal total`() {
        val fx = conversion(rate = "1.10", fee = "0.02")
        val d = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50")), conversions = listOf(fx))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        val c = d.comparisons.single()
        assertAmount("0.46", c.falTotal)
        assertAmount("0.04", c.savings)
        assertEquals("USD", c.currency)
        assertEquals(fx, c.conversion)
        assertEquals("EUR", c.falQuote.currency)
    }

    @Test fun `conversion fee can remove apparent savings`() {
        val equal = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50")), conversions = listOf(conversion(fee = "0.06")))
        assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, equal.kind)
        assertAmount("0", equal.comparisons.single().savings)
        val worse = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50")), conversions = listOf(conversion(fee = "0.07")))
        assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, worse.kind)
        assertTrue(worse.comparisons.single().savings.signum() < 0)
    }

    @Test fun `without a documented rounding rule the converted total stays exact`() {
        // 0.40 * 1.2345 = 0.4938 exactly; no rounding is inferred from the currency.
        val d = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50")),
            conversions = listOf(conversion(rate = "1.2345", fee = "0")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertAmount("0.4938", d.comparisons.single().falTotal)
        assertAmount("0.0062", d.comparisons.single().savings)
        // Micro-prices are not inflated into no-savings by an implicit currency scale.
        val micro = evaluate(prices = listOf(price(FAL, "0.0001", currency = "EUR"), price(ATLAS_A, "0.0002")),
            conversions = listOf(conversion(rate = "1.10", fee = "0")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, micro.kind, codes(micro).toString())
        assertAmount("0.00011", micro.comparisons.single().falTotal)
    }

    @Test fun `documented settlement rounding scale 2 CEILING removes sub-cent savings`() {
        val fx = conversion(rate = "1.2345", fee = "0", roundingScale = 2, roundingMode = RoundingMode.CEILING)
        val d = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50")), conversions = listOf(fx))
        assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, d.kind, codes(d).toString())
        assertAmount("0.50", d.comparisons.single().falTotal)
        assertEquals(2, d.comparisons.single().falTotal.scale())
        assertEquals(fx, d.comparisons.single().conversion)
        val saves = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.51")), conversions = listOf(fx))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, saves.kind, codes(saves).toString())
        assertAmount("0.01", saves.comparisons.single().savings)
    }

    @Test fun `documented scale 0 rounding applies to zero-decimal target currencies`() {
        // 0.40 * 150.5 = 60.2 -> 61 JPY under CEILING.
        val fx = conversion(from = "USD", to = "JPY", rate = "150.5", fee = "0", roundingScale = 0, roundingMode = RoundingMode.CEILING)
        val equal = evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "61", currency = "JPY")), conversions = listOf(fx))
        assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, equal.kind, codes(equal).toString())
        assertAmount("61", equal.comparisons.single().falTotal)
        val saves = evaluate(prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "62", currency = "JPY")), conversions = listOf(fx))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, saves.kind, codes(saves).toString())
        assertAmount("1", saves.comparisons.single().savings)
    }

    @Test fun `UNNECESSARY rounding accepts exact totals and rejects inexact ones`() {
        val exact = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.60")),
            conversions = listOf(conversion(rate = "1.25", fee = "0", roundingScale = 2, roundingMode = RoundingMode.UNNECESSARY)))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, exact.kind, codes(exact).toString())
        assertAmount("0.50", exact.comparisons.single().falTotal)
        val inexact = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.60")),
            conversions = listOf(conversion(rate = "1.2345", fee = "0", roundingScale = 2, roundingMode = RoundingMode.UNNECESSARY)))
        assertNoComparison(inexact, ReasonCode.CONVERSION_UNVERIFIED)
        assertTrue(inexact.unverified.any { it.code == ReasonCode.CONVERSION_UNVERIFIED && it.route == ATLAS_A })
    }

    @Test fun `rounding scale and mode must be paired and the scale bounded`() {
        val prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50"))
        for ((label, fx) in listOf(
            "scale without mode" to conversion(roundingScale = 2),
            "mode without scale" to conversion(roundingMode = RoundingMode.CEILING),
            "negative scale" to conversion(roundingScale = -1, roundingMode = RoundingMode.CEILING),
            "scale above 12" to conversion(roundingScale = 13, roundingMode = RoundingMode.CEILING),
        )) {
            assertNoComparison(evaluate(prices = prices, conversions = listOf(fx)), ReasonCode.CURRENCY_MISMATCH, label)
        }
        for (scale in listOf(0, 12)) {
            val d = evaluate(prices = prices, conversions = listOf(conversion(roundingScale = scale, roundingMode = RoundingMode.CEILING)))
            assertTrue(d.comparisons.isNotEmpty(), "scale $scale ${codes(d)}")
        }
    }

    @Test fun `undocumented, incomplete, invalid, reversed or ambiguous conversions are rejected`() {
        val prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50"))
        val stale = listOf(conversion(evidence = ev(NOW.minus(Duration.ofHours(2)))))
        val future = listOf(conversion(evidence = ev(NOW.plus(Duration.ofHours(1)))))
        val cases = listOf(
            "excludes conversion costs" to listOf(conversion(allCosts = false)),
            "zero rate" to listOf(conversion(rate = "0")),
            "negative rate" to listOf(conversion(rate = "-1.10")),
            "negative fee" to listOf(conversion(fee = "-0.01")),
            "reverse direction only" to listOf(conversion(from = "USD", to = "EUR")),
            "unrelated pair" to listOf(conversion(from = "GBP", to = "USD")),
            "stale" to stale,
            "future" to future,
            "blank source" to listOf(conversion(evidence = ev(source = ""))),
            "conflicting usable rates" to listOf(conversion(rate = "1.10"), conversion(rate = "0.90")),
            "identical duplicates" to listOf(conversion(), conversion()),
        )
        for ((label, fx) in cases) {
            assertNoComparison(evaluate(prices = prices, conversions = fx), ReasonCode.CURRENCY_MISMATCH, label)
        }
        assertTrue(ReasonCode.STALE_EVIDENCE in codes(evaluate(prices = prices, conversions = stale)))
        assertTrue(ReasonCode.FUTURE_EVIDENCE in codes(evaluate(prices = prices, conversions = future)))
    }

    @Test fun `one usable conversion among unusable ones is accepted without choosing a rate`() {
        val d = evaluate(prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50")),
            conversions = listOf(conversion(rate = "0.50", allCosts = false), conversion(rate = "1.10", fee = "0.02")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertAmount("0.46", d.comparisons.single().falTotal)
    }

    // ---- Identity ----------------------------------------------------------------------------

    @Test fun `missing or blank identity, version, endpoint or revision invalidates the Fal route`() {
        for (k in listOf(
            FAL.copy(identity = IDENTITY.copy(version = "")),
            FAL.copy(identity = IDENTITY.copy(version = "  ")),
            FAL.copy(identity = IDENTITY.copy(model = "")),
            FAL.copy(identity = IDENTITY.copy(publisher = "")),
            FAL.copy(endpoint = ""),
            FAL.copy(contractRevision = ""),
            FAL.copy(model = ModelRef("fal", "")),
        )) {
            assertNoComparison(evaluate(fal = route(k), prices = listOf(price(k, "0.40"), price(ATLAS_A, "0.50"))), ReasonCode.INVALID_ROUTE, k.toString())
        }
    }

    @Test fun `pro, fast, renamed or differently versioned Atlas models are never equivalent`() {
        for (identity in listOf(
            IDENTITY.copy(model = "synthetic-video-model-pro"),
            IDENTITY.copy(model = "synthetic-video-model-fast"),
            IDENTITY.copy(model = "Synthetic-Video-Model"),
            IDENTITY.copy(model = "synthetic-video"),
            IDENTITY.copy(version = "v2-synthetic"),
            IDENTITY.copy(version = "v1-synthetic-fast"),
            IDENTITY.copy(publisher = "synthetic-reseller"),
        )) {
            val variant = key("atlas_cloud", "atlas-synthetic/variant", identity)
            val d = evaluate(atlasRoutes = listOf(route(variant)), equivalences = listOf(equivalence(variant)),
                prices = listOf(price(FAL, "0.40"), price(variant, "0.90")))
            assertEquals(DecisionKind.MODEL_ABSENT_IN_ATLAS, d.kind, "$identity ${codes(d)}")
            assertEquals(CatalogPresence.ABSENT, d.catalogPresence)
            assertTrue(d.comparisons.isEmpty(), identity.toString())
        }
    }

    @Test fun `similar Atlas variant beside the exact route is ignored for comparison`() {
        val pro = key("atlas_cloud", "atlas-synthetic/video-pro", IDENTITY.copy(model = "synthetic-video-model-pro"))
        val d = evaluate(atlasRoutes = listOf(route(ATLAS_A), route(pro)),
            equivalences = listOf(equivalence(ATLAS_A), equivalence(pro)),
            prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50"), price(pro, "0.10")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertEquals(listOf(ATLAS_A), d.comparisons.map { it.atlas })
    }

    @Test fun `version-less Atlas entry for the same model blocks an absence claim`() {
        val versionless = key("atlas_cloud", "atlas-synthetic/video", IDENTITY.copy(version = ""))
        val d = evaluate(atlasRoutes = listOf(route(versionless)), prices = listOf(price(FAL, "0.40")))
        assertNoComparison(d, ReasonCode.INVALID_ROUTE)
        assertEquals(CatalogPresence.UNKNOWN, d.catalogPresence)
    }

    @Test fun `Fal and matching Atlas identity evidence must be present and current`() {
        for ((label, evidence, code) in listOf(
            Triple("missing", null as Evidence?, ReasonCode.MISSING_EVIDENCE),
            Triple("stale", ev(NOW.minus(Duration.ofDays(2))), ReasonCode.STALE_EVIDENCE),
            Triple("future", ev(NOW.plus(Duration.ofHours(1))), ReasonCode.FUTURE_EVIDENCE),
        )) {
            assertNoComparison(evaluate(fal = route(FAL, identityEvidence = evidence)), code, "Fal $label")
            val atlas = evaluate(atlasRoutes = listOf(route(ATLAS_A, identityEvidence = evidence)))
            assertNoComparison(atlas, code, "Atlas $label")
            assertEquals(CatalogPresence.PRESENT, atlas.catalogPresence, label)
        }
    }

    @Test fun `unrelated Atlas entry with unverified identity blocks aggregate savings but not existential no-savings`() {
        for ((label, bad, code) in listOf(
            Triple("missing evidence", route(ATLAS_OTHER, identityEvidence = null), ReasonCode.MISSING_EVIDENCE),
            Triple("stale evidence", route(ATLAS_OTHER, identityEvidence = ev(NOW.minus(Duration.ofDays(2)))), ReasonCode.STALE_EVIDENCE),
            Triple("blank version", route(ATLAS_OTHER.copy(identity = ATLAS_OTHER.identity.copy(version = ""))), ReasonCode.INVALID_ROUTE),
        )) {
            val routes = listOf(route(ATLAS_A), bad)
            val eqs = listOf(equivalence(ATLAS_A))
            val saves = evaluate(atlasRoutes = routes, equivalences = eqs)
            assertInsufficient(saves, code, label)
            assertEquals(listOf(ATLAS_A), saves.comparisons.map { it.atlas }, label)
            val noSaving = evaluate(atlasRoutes = routes, equivalences = eqs, prices = listOf(price(FAL, "0.60"), price(ATLAS_A, "0.50")))
            assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, noSaving.kind, label)
            assertTrue(code in codes(noSaving), label)
        }
    }

    // ---- Providers, catalog and absence ------------------------------------------------------

    @Test fun `absence requires a complete, unfiltered, current, successful catalog with verified identities`() {
        val d = evaluate(atlasRoutes = listOf(route(ATLAS_OTHER)), equivalences = emptyList(), prices = emptyList())
        assertEquals(DecisionKind.MODEL_ABSENT_IN_ATLAS, d.kind, codes(d).toString())
        assertEquals(CatalogPresence.ABSENT, d.catalogPresence)
        assertTrue(d.complementsAtlas)
        assertTrue(d.unverified.isEmpty())
        assertTrue(d.comparisons.isEmpty())
        assertTrue(d.facts.any { it.code == ReasonCode.MODEL_ABSENT && it.route == FAL })
        val empty = evaluate(atlasRoutes = emptyList(), prices = emptyList())
        assertEquals(DecisionKind.MODEL_ABSENT_IN_ATLAS, empty.kind, codes(empty).toString())
    }

    @Test fun `absence is not claimed from partial, filtered, failed, stale, undocumented or unavailable catalogs`() {
        val other = listOf(route(ATLAS_OTHER))
        for ((label, catalog, code) in listOf(
            Triple("incomplete", atlasCatalog(other, complete = false), ReasonCode.CATALOG_INCOMPLETE),
            Triple("hidden filtered", atlasCatalog(other, includesHidden = false), ReasonCode.CATALOG_INCOMPLETE),
            Triple("refresh failed", atlasCatalog(other, refreshSucceeded = false), ReasonCode.CATALOG_REFRESH_FAILED),
            Triple("stale", atlasCatalog(other, evidence = ev(NOW.minus(Duration.ofHours(1)).minusSeconds(1))), ReasonCode.STALE_EVIDENCE),
            Triple("future", atlasCatalog(other, evidence = ev(NOW.plus(Duration.ofHours(1)))), ReasonCode.FUTURE_EVIDENCE),
            Triple("no evidence", atlasCatalog(other, evidence = null), ReasonCode.MISSING_EVIDENCE),
            Triple("unavailable", atlasCatalog(other, availability = Availability.UNAVAILABLE), ReasonCode.PROVIDER_UNAVAILABLE),
            Triple("availability unknown", atlasCatalog(other, availability = Availability.UNKNOWN), ReasonCode.PROVIDER_UNAVAILABLE),
            Triple("empty and incomplete", atlasCatalog(emptyList(), complete = false), ReasonCode.CATALOG_INCOMPLETE),
            Triple("unverified unrelated identity", atlasCatalog(listOf(route(ATLAS_OTHER, identityEvidence = null))), ReasonCode.MISSING_EVIDENCE),
        )) {
            val d = evaluate(catalog = catalog, equivalences = emptyList(), prices = emptyList())
            assertNoComparison(d, code, label)
            assertEquals(CatalogPresence.UNKNOWN, d.catalogPresence, label)
        }
    }

    @Test fun `hidden or unavailable routes are never savings alternatives`() {
        for ((label, r, code) in listOf(
            Triple("hidden", route(ATLAS_A, hidden = true), ReasonCode.HIDDEN_ROUTE),
            Triple("unavailable", route(ATLAS_A, availability = Availability.UNAVAILABLE), ReasonCode.PROVIDER_UNAVAILABLE),
            Triple("unknown availability", route(ATLAS_A, availability = Availability.UNKNOWN), ReasonCode.PROVIDER_UNAVAILABLE),
        )) {
            val atlas = evaluate(atlasRoutes = listOf(r))
            assertNoComparison(atlas, code, "Atlas $label")
            assertEquals(CatalogPresence.PRESENT, atlas.catalogPresence, label)
            assertTrue(atlas.unverified.any { it.code == code && it.route == ATLAS_A }, label)
        }
        for ((label, r, code) in listOf(
            Triple("hidden", route(FAL, hidden = true), ReasonCode.HIDDEN_ROUTE),
            Triple("unavailable", route(FAL, availability = Availability.UNAVAILABLE), ReasonCode.PROVIDER_UNAVAILABLE),
            Triple("unknown availability", route(FAL, availability = Availability.UNKNOWN), ReasonCode.PROVIDER_UNAVAILABLE),
        )) {
            val fal = evaluate(fal = r)
            assertNoComparison(fal, code, "Fal $label")
            assertEquals(CatalogPresence.UNKNOWN, fal.catalogPresence, label)
        }
    }

    @Test fun `partial or filtered catalog keeps pair facts but only existential no-savings is decided`() {
        for (catalog in listOf(atlasCatalog(listOf(route(ATLAS_A)), complete = false), atlasCatalog(listOf(route(ATLAS_A)), includesHidden = false))) {
            val d = evaluate(catalog = catalog)
            assertInsufficient(d, ReasonCode.CATALOG_INCOMPLETE)
            assertEquals(CatalogPresence.PRESENT, d.catalogPresence)
            assertEquals(listOf(ATLAS_A), d.comparisons.map { it.atlas })
            assertTrue(d.facts.any { it.code == ReasonCode.SAVINGS_VERIFIED && it.route == ATLAS_A })
            val noSaving = evaluate(catalog = catalog, prices = listOf(price(FAL, "0.60"), price(ATLAS_A, "0.50")))
            assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, noSaving.kind)
            assertTrue(ReasonCode.CATALOG_INCOMPLETE in codes(noSaving))
        }
    }

    @Test fun `stale or failed catalog blocks comparisons even when the model is present`() {
        assertNoComparison(evaluate(catalog = atlasCatalog(listOf(route(ATLAS_A)), evidence = ev(NOW.minus(Duration.ofHours(2))))), ReasonCode.STALE_EVIDENCE)
        assertNoComparison(evaluate(catalog = atlasCatalog(listOf(route(ATLAS_A)), refreshSucceeded = false)), ReasonCode.CATALOG_REFRESH_FAILED)
    }

    @Test fun `wrong providers and duplicate Atlas routes are rejected`() {
        assertNoComparison(evaluate(fal = route(ATLAS_A), prices = listOf(price(ATLAS_A, "0.40"))), ReasonCode.WRONG_PROVIDER, "Fal slot")
        assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A), route(key("fal", "fal-synthetic/other")))), ReasonCode.WRONG_PROVIDER, "Atlas catalog")
        assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A), route(ATLAS_A, hidden = true))), ReasonCode.DUPLICATE_ROUTE, "duplicate")
    }

    @Test fun `policy construction rejects ambiguous providers and negative freshness`() {
        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        assertFailsWith<IllegalArgumentException> { ProviderRoutePolicy(FRESHNESS, clock, atlasProviderId = "fal", falProviderId = "fal") }
        assertFailsWith<IllegalArgumentException> { ProviderRoutePolicy(FRESHNESS, clock, atlasProviderId = " ") }
        assertFailsWith<IllegalArgumentException> { ProviderRoutePolicy(FRESHNESS, clock, falProviderId = "") }
        val minus = Duration.ofSeconds(-1)
        val ok = Duration.ofHours(1)
        assertFailsWith<IllegalArgumentException> { FreshnessPolicy(minus, ok, ok, ok, ok) }
        assertFailsWith<IllegalArgumentException> { FreshnessPolicy(ok, minus, ok, ok, ok) }
        assertFailsWith<IllegalArgumentException> { FreshnessPolicy(ok, ok, minus, ok, ok) }
        assertFailsWith<IllegalArgumentException> { FreshnessPolicy(ok, ok, ok, minus, ok) }
        assertFailsWith<IllegalArgumentException> { FreshnessPolicy(ok, ok, ok, ok, minus) }
    }

    // ---- Configuration receipts and contracts ------------------------------------------------

    @Test fun `receipts must match references, parameters, defaults, quality, quantity and input digest on both routes`() {
        for (m in RECEIPT_MUTATIONS) {
            val mutated = m.apply(REQ)
            assertNotEquals(REQ, mutated, "fixture ${m.name} must differ")
            val atlas = evaluate(atlasRoutes = listOf(route(ATLAS_A, receipt = ConfigurationReceipt(ATLAS_A, mutated, ev()))))
            assertNoComparison(atlas, ReasonCode.CONTRACT_UNVERIFIED, "Atlas ${m.name}")
            assertTrue(atlas.unverified.any { it.code == ReasonCode.CONTRACT_UNVERIFIED && it.route == ATLAS_A }, m.name)
            val fal = evaluate(fal = route(FAL, receipt = ConfigurationReceipt(FAL, mutated, ev())))
            assertNoComparison(fal, ReasonCode.CONTRACT_UNVERIFIED, "Fal ${m.name}")
        }
    }

    @Test fun `receipt is bound to the exact RouteKey and is mandatory`() {
        for ((label, k) in listOf(
            "other revision" to ATLAS_A.copy(contractRevision = "rev-synthetic-2"),
            "other endpoint" to ATLAS_B,
            "Fal key" to FAL,
            "other model ref" to ATLAS_A.copy(model = ModelRef("atlas_cloud", "synthetic/other")),
            "other version" to ATLAS_A.copy(identity = IDENTITY.copy(version = "v2-synthetic")),
        )) {
            assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A, receipt = ConfigurationReceipt(k, REQ, ev())))), ReasonCode.CONTRACT_UNVERIFIED, "Atlas $label")
        }
        assertNoComparison(evaluate(fal = route(FAL, receipt = ConfigurationReceipt(ATLAS_A, REQ, ev()))), ReasonCode.CONTRACT_UNVERIFIED, "Fal bound to Atlas")
        assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A, receipt = null))), ReasonCode.CONTRACT_UNVERIFIED, "Atlas none")
        assertNoComparison(evaluate(fal = route(FAL, receipt = null)), ReasonCode.CONTRACT_UNVERIFIED, "Fal none")
    }

    @Test fun `receipt evidence must be sourced and current`() {
        for ((label, evidence, code) in listOf(
            Triple("stale", ev(NOW.minus(Duration.ofDays(2))), ReasonCode.STALE_EVIDENCE),
            Triple("future", ev(NOW.plus(Duration.ofHours(1))), ReasonCode.FUTURE_EVIDENCE),
            Triple("blank source", ev(source = ""), ReasonCode.MISSING_EVIDENCE),
        )) {
            assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A, receipt = ConfigurationReceipt(ATLAS_A, REQ, evidence)))), code, "Atlas $label")
            assertNoComparison(evaluate(fal = route(FAL, receipt = ConfigurationReceipt(FAL, REQ, evidence))), code, "Fal $label")
        }
    }

    @Test fun `contract must cover modality and all required parameters`() {
        assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A, contractModality = "image"))), ReasonCode.CONFIGURATION_INCOMPATIBLE, "modality")
        assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A, unresolved = setOf("motion_strength")))), ReasonCode.CONFIGURATION_INCOMPATIBLE, "unresolved")
        assertNoComparison(evaluate(atlasRoutes = listOf(route(ATLAS_A, requiredParameters = REQ.parameters.keys + "camera_motion"))),
            ReasonCode.CONFIGURATION_INCOMPATIBLE, "required parameter missing in request")
        assertNoComparison(evaluate(fal = route(FAL, unresolved = setOf("motion_strength"))), ReasonCode.CONFIGURATION_INCOMPATIBLE, "Fal unresolved")
    }

    // ---- Request validation ------------------------------------------------------------------

    @Test fun `incomplete or invalid requests are rejected before any comparison`() {
        for ((label, req, code) in listOf(
            Triple("not complete", REQ.copy(complete = false), ReasonCode.INCOMPLETE_CONFIGURATION),
            Triple("missing duration", REQ.withParam("duration_seconds", null), ReasonCode.INCOMPLETE_CONFIGURATION),
            Triple("missing resolution", REQ.withParam("resolution", null), ReasonCode.INCOMPLETE_CONFIGURATION),
            Triple("missing fps", REQ.withParam("fps", null), ReasonCode.INCOMPLETE_CONFIGURATION),
            Triple("missing audio", REQ.withParam("audio", null), ReasonCode.INCOMPLETE_CONFIGURATION),
            Triple("image without resolution", REQ.copy(modality = "image", parameters = mapOf("quality" to "standard")), ReasonCode.INCOMPLETE_CONFIGURATION),
            Triple("audio not boolean", REQ.withParam("audio", "yes"), ReasonCode.INVALID_REQUEST),
            Triple("zero duration", REQ.withParam("duration_seconds", "0"), ReasonCode.INVALID_REQUEST),
            Triple("negative duration", REQ.withParam("duration_seconds", "-5"), ReasonCode.INVALID_REQUEST),
            Triple("non-numeric duration", REQ.withParam("duration_seconds", "five"), ReasonCode.INVALID_REQUEST),
            Triple("zero fps", REQ.withParam("fps", "0"), ReasonCode.INVALID_REQUEST),
            Triple("blank parameter value", REQ.withParam("quality", " "), ReasonCode.INVALID_REQUEST),
            Triple("blank parameter key", REQ.withParam("", "x"), ReasonCode.INVALID_REQUEST),
            Triple("zero quantity", REQ.copy(quantity = 0), ReasonCode.INVALID_REQUEST),
            Triple("negative quantity", REQ.copy(quantity = -1), ReasonCode.INVALID_REQUEST),
            Triple("blank digest", REQ.copy(inputDigest = ""), ReasonCode.INVALID_REQUEST),
            Triple("no capabilities", REQ.copy(capabilities = emptySet()), ReasonCode.INVALID_REQUEST),
            Triple("blank capability", REQ.copy(capabilities = setOf(" ")), ReasonCode.INVALID_REQUEST),
            Triple("blank modality", REQ.copy(modality = ""), ReasonCode.INVALID_REQUEST),
            Triple("unknown modality", REQ.copy(modality = "hologram"), ReasonCode.INVALID_REQUEST),
            Triple("blank reference content", REQ.copy(references = listOf(ReferenceInput("first_frame", "image", ""))), ReasonCode.INVALID_REQUEST),
            Triple("blank reference role", REQ.copy(references = listOf(ReferenceInput("", "image", "sha256:synthetic-ref-a"))), ReasonCode.INVALID_REQUEST),
        )) {
            // Routes, receipts, equivalence and quotes are all built for the broken request, so only validation can reject it.
            val d = evaluate(requested = req)
            assertNoComparison(d, code, label)
            assertEquals(CatalogPresence.UNKNOWN, d.catalogPresence, label)
            assertTrue(d.facts.isEmpty(), label)
        }
    }

    // ---- Equivalence -------------------------------------------------------------------------

    @Test fun `equivalence must be explicit, scoped, directional and current`() {
        val stale = listOf(equivalence(evidence = ev(NOW.minus(Duration.ofDays(2)))))
        val future = listOf(equivalence(evidence = ev(NOW.plus(Duration.ofHours(1)))))
        for ((label, eqs) in listOf(
            "none" to emptyList<EquivalenceEvidence>(),
            "other Atlas endpoint" to listOf(equivalence(ATLAS_B)),
            "other Atlas revision" to listOf(equivalence(ATLAS_A.copy(contractRevision = "rev-synthetic-2"))),
            "other Fal revision" to listOf(equivalence(fal = FAL.copy(contractRevision = "rev-synthetic-2"))),
            "swapped direction" to listOf(equivalence(atlas = FAL, fal = ATLAS_A)),
            "other configuration" to listOf(equivalence(configuration = REQ.withParam("resolution", "1920x1080"))),
            "other quantity" to listOf(equivalence(configuration = REQ.copy(quantity = 2))),
            "stale" to stale,
            "future" to future,
            "blank statement" to listOf(equivalence(evidence = ev(statement = " "))),
        )) {
            assertNoComparison(evaluate(equivalences = eqs), ReasonCode.EQUIVALENCE_UNVERIFIED, label)
        }
        assertTrue(ReasonCode.STALE_EVIDENCE in codes(evaluate(equivalences = stale)))
        assertTrue(ReasonCode.FUTURE_EVIDENCE in codes(evaluate(equivalences = future)))
    }

    @Test fun `quality downgrade is never proposed as savings, even beside a positive attestation`() {
        for (eqs in listOf(
            listOf(equivalence(preservesQuality = false)),
            listOf(equivalence(), equivalence(preservesQuality = false, evidence = ev(NOW.minusSeconds(60)))),
            listOf(equivalence(preservesQuality = false, evidence = ev(NOW.minus(Duration.ofDays(3)))), equivalence()),
        )) {
            val d = evaluate(equivalences = eqs, prices = listOf(price(FAL, "0.01"), price(ATLAS_A, "0.99")))
            assertNoComparison(d, ReasonCode.QUALITY_NOT_PRESERVED, eqs.toString())
            assertTrue(d.unverified.any { it.code == ReasonCode.QUALITY_NOT_PRESERVED && it.route == ATLAS_A })
        }
    }

    @Test fun `newest current equivalence is recorded when several valid ones exist`() {
        val older = equivalence(evidence = ev(NOW.minus(Duration.ofHours(3)), statement = "SYNTHETIC older"))
        val newer = equivalence(evidence = ev(NOW.minus(Duration.ofHours(1)), statement = "SYNTHETIC newer"))
        val d = evaluate(equivalences = listOf(older, newer, equivalence(evidence = ev(NOW.minus(Duration.ofDays(2))))))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertEquals(newer, d.comparisons.single().equivalence)
    }

    // ---- Multiple eligible Atlas endpoints ---------------------------------------------------

    @Test fun `any valid no-saving pair makes the aggregate no-savings regardless of order or unverified siblings`() {
        val routes = listOf(route(ATLAS_A), route(ATLAS_B))
        val prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50"), price(ATLAS_B, "0.40"))
        for (ordered in listOf(routes, routes.reversed())) {
            val d = evaluate(atlasRoutes = ordered, prices = prices)
            assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, d.kind, codes(d).toString())
            assertFalse(d.complementsAtlas)
            assertEquals(setOf(ATLAS_A, ATLAS_B), d.comparisons.map { it.atlas }.toSet())
            assertTrue(d.facts.any { it.code == ReasonCode.SAVINGS_VERIFIED && it.route == ATLAS_A })
            assertTrue(d.facts.any { it.code == ReasonCode.NO_SAVINGS && it.route == ATLAS_B })
        }
        val withUnverified = evaluate(atlasRoutes = routes + route(ATLAS_C, receipt = null), prices = prices + price(ATLAS_C, "0.90"))
        assertEquals(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS, withUnverified.kind)
        assertTrue(withUnverified.unverified.any { it.code == ReasonCode.CONTRACT_UNVERIFIED && it.route == ATLAS_C })
    }

    @Test fun `all valid pairs saving with another unverified candidate is insufficient evidence`() {
        val cheapB = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50"), price(ATLAS_B, "0.10"))
        for ((label, b, code) in listOf(
            Triple("no receipt", route(ATLAS_B, receipt = null), ReasonCode.CONTRACT_UNVERIFIED),
            Triple("hidden", route(ATLAS_B, hidden = true), ReasonCode.HIDDEN_ROUTE),
            Triple("unavailable", route(ATLAS_B, availability = Availability.UNAVAILABLE), ReasonCode.PROVIDER_UNAVAILABLE),
        )) {
            val d = evaluate(atlasRoutes = listOf(route(ATLAS_A), b), prices = cheapB)
            assertInsufficient(d, code, label)
            assertEquals(listOf(ATLAS_A), d.comparisons.map { it.atlas }, label)
        }
        val both = listOf(route(ATLAS_A), route(ATLAS_B))
        assertInsufficient(evaluate(atlasRoutes = both, prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50"))), ReasonCode.MISSING_PRICE, "B unpriced")
        val noEq = evaluate(atlasRoutes = both, equivalences = listOf(equivalence(ATLAS_A)), prices = cheapB)
        assertInsufficient(noEq, ReasonCode.EQUIVALENCE_UNVERIFIED, "B without equivalence")
        assertEquals(listOf(ATLAS_A), noEq.comparisons.map { it.atlas })
    }

    @Test fun `savings are verified only when every eligible Atlas endpoint is verified and more expensive`() {
        val d = evaluate(atlasRoutes = listOf(route(ATLAS_A), route(ATLAS_B)),
            prices = listOf(price(FAL, "0.40"), price(ATLAS_A, "0.50"), price(ATLAS_B, "0.45")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        assertEquals(listOf(ATLAS_A, ATLAS_B), d.comparisons.map { it.atlas })
        assertAmount("0.10", d.comparisons[0].savings)
        assertAmount("0.05", d.comparisons[1].savings)
    }

    @Test fun `mixed currencies across Atlas endpoints are compared pairwise`() {
        val d = evaluate(atlasRoutes = listOf(route(ATLAS_A), route(ATLAS_B)),
            prices = listOf(price(FAL, "0.40", currency = "EUR"), price(ATLAS_A, "0.50"), price(ATLAS_B, "0.45", currency = "EUR")),
            conversions = listOf(conversion(rate = "1.10", fee = "0.02")))
        assertEquals(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS, d.kind, codes(d).toString())
        val byRoute = d.comparisons.associateBy { it.atlas }
        assertAmount("0.46", byRoute.getValue(ATLAS_A).falTotal)
        assertEquals(null, byRoute.getValue(ATLAS_B).conversion)
        assertAmount("0.05", byRoute.getValue(ATLAS_B).savings)
    }
}
