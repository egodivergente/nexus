package com.vidadomu.nexus.agent.core.routing.comparison

import com.vidadomu.nexus.agent.core.FakeRegistry
import com.vidadomu.nexus.agent.core.prices
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.spend.*
import com.vidadomu.nexus.agent.core.tools.*
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** All prices and equivalence attestations below are synthetic; no provider calls. */
class GenerationCostPreflightTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val age = Duration.ofHours(1)
    private val policy = ProviderRoutePolicy(FreshnessPolicy(age, age, age, age, Duration.ZERO), Clock.fixed(now, ZoneOffset.UTC))
    private val ref = ModelRef("atlas_cloud", "fixture/model")
    private val plan = GenerationPlan("p", ref, "Fixture", "IMAGE", prompt = "private prompt",
        params = mapOf("resolution" to "1024"), effectiveParams = mapOf("resolution" to "1024"), createdAt = now.toEpochMilli())

    private fun input(p: GenerationPlan, falPrice: String = "0.40"): PreparedComparison {
        val digest = GenerationCostPreflight.digest(p)
        val c = RequestedConfiguration("image", setOf("text_to_image"), p.effectiveParams, emptyList(), 1, digest, true)
        val e = Evidence("fixture:synthetic", now, "Synthetic test only")
        fun route(provider: String): ProviderRoute {
            val key = RouteKey(ModelRef(provider, ref.modelId), ExactModelIdentity("fixture", "model", "1"), "fixture:$provider", "1")
            return ProviderRoute(key, e, EndpointContract("image", c.capabilities, setOf("resolution"), emptySet(), ConfigurationReceipt(key, c, e)), false, Availability.AVAILABLE)
        }
        val fal = route("fal"); val atlas = route("atlas_cloud")
        fun price(r: ProviderRoute, n: String) = PriceObservation(r.key, c, BigDecimal(n), "USD", PriceBasis.REQUEST_TOTAL, true, e)
        return PreparedComparison(digest, c, fal, AtlasCatalog(listOf(atlas), true, true, true, Availability.AVAILABLE, e),
            listOf(EquivalenceEvidence(fal.key, atlas.key, c, true, e)), listOf(price(fal, falPrice), price(atlas, "0.50")))
    }
    private fun service(block: suspend (GenerationPlan) -> PreparedComparison?) = GenerationCostPreflight(ComparisonEvidenceSource(block), policy)

    @Test fun `verified savings retain selection and never issue an execution ticket`() = runTest {
        val guard = SpendGuard(); guard.register(plan)
        val before = plan.fingerprint()
        val result = service { input(it) }.warnings(plan)
        assertTrue(result.any { "0.10 USD" in it })
        assertTrue(result.any { "fixture:synthetic" in it })
        assertEquals(ref, plan.ref); assertEquals(before, plan.fingerprint())
        assertEquals(PlanState.PREPARED, guard.state(plan.id))
    }
    @Test fun `default reports missing evidence and leaves unrelated providers alone`() = runTest {
        val s = GenerationCostPreflight()
        assertTrue(s.warnings(plan).single().contains("evidencia insuficiente"))
        assertEquals(emptyList(), s.warnings(plan.copy(ref = ModelRef("byteplus", "fixture"))))
    }
    @Test fun `no savings is not presented as an alternative saving`() = runTest {
        assertTrue(service { input(it, "0.60") }.warnings(plan).any { "No hay ahorro global" in it })
    }
    @Test fun `stale prices and unverified equivalence cannot advertise savings`() = runTest {
        for (missing in listOf(true, false)) {
            val result = service { p -> input(p).let { i -> if (missing) i.copy(equivalences = emptyList()) else
                i.copy(prices = i.prices.map { it.copy(validUntil = now) }) } }.warnings(plan)
            assertTrue(result.any { "Evidencia insuficiente" in it })
            assertFalse(result.any { "Fal tiene un total inferior" in it })
        }
    }
    @Test fun `old request evidence rejected after parameter prompt default or reference changes`() = runTest {
        val old = input(plan)
        val variants = listOf(plan.copy(prompt = "changed"), plan.copy(params = mapOf("resolution" to "2048")),
            plan.copy(effectiveParams = mapOf("resolution" to "2048")),
            plan.copy(media = listOf(MediaRef("reference", "local:changed", "image/png"))))
        for (p in variants) assertTrue(service { old }.warnings(p).single().contains("evidencia insuficiente"))
    }
    @Test fun `digest handles map ordering and reference field ordering and delimiter ambiguity`() {
        val p = plan.copy(params = linkedMapOf("a" to "x", "b" to "y"))
        assertEquals(GenerationCostPreflight.digest(p), GenerationCostPreflight.digest(p.copy(params = linkedMapOf("b" to "y", "a" to "x"))))
        val m = listOf(MediaRef("reference", "a", fieldKey = "images"), MediaRef("reference", "b", fieldKey = "images"))
        assertNotEquals(GenerationCostPreflight.digest(p.copy(media = m)), GenerationCostPreflight.digest(p.copy(media = m.reversed())))
        assertNotEquals(GenerationCostPreflight.digest(p.copy(media = m)), GenerationCostPreflight.digest(p.copy(media = m.map { it.copy(fieldKey = "other") })))
        assertNotEquals(GenerationCostPreflight.digest(p), GenerationCostPreflight.digest(p.copy(params = mapOf("a" to "x, b=y"))))
    }
    @Test fun `adapter errors do not leak secrets and cancellation propagates`() = runTest {
        val result = service { error("secret-key") }.warnings(plan)
        assertFalse(result.joinToString().contains("secret-key"))
        assertFailsWith<CancellationException> { service { throw CancellationException("stop") }.warnings(plan) }
    }
    @Test fun `slow quote cannot hang preparation`() = runTest {
        val s = service { delay(10_000); input(it) }
        assertTrue(s.warnings(plan).single().contains("evidencia insuficiente"))
    }
    @Test fun `hidden or unrelated selected route is not offered`() = runTest {
        assertTrue(service { input(it).let { i -> i.copy(atlas = i.atlas.copy(routes = i.atlas.routes.map { it.copy(hidden = true) })) } }
            .warnings(plan).single().contains("evidencia insuficiente"))
        assertTrue(service { input(plan) }.warnings(plan.copy(ref = ref.copy(modelId = "other"))).single().contains("evidencia insuficiente"))
    }
    @Test fun `adapter mutation cannot alter the selected live plan`() = runTest {
        val mutable = plan.copy(params = mutableMapOf("resolution" to "1024"))
        val result = service { snapshot ->
            (snapshot.params as MutableMap<String, String>)["resolution"] = "2048"
            input(snapshot)
        }.warnings(mutable)
        assertEquals("1024", mutable.params["resolution"])
        assertTrue(result.single().contains("evidencia insuficiente"))
    }
    @Test fun `request digest cannot be rebound to a different normalized quote`() = runTest {
        val result = service { input(it).let { i -> i.copy(requested = i.requested.copy(inputDigest = "different")) } }.warnings(plan)
        assertTrue(result.single().contains("evidencia insuficiente"))
    }
    @Test fun `PlanBuilder runs preflight after validation and recomputes on edits`() = runTest {
        val model = ModelSummary(ref, "Fixture", modalities = setOf("IMAGE"), capabilities = setOf("TEXT_TO_IMAGE"))
        val schema = ModelSchema(ref, listOf(ParamSpec("resolution", "Resolution", "RESOLUTION", "string", options = listOf("1024", "2048"), default = "1024")), MediaSpec())
        val seen = mutableListOf<GenerationPlan>()
        val builder = PlanBuilder(FakeRegistry(listOf(model), mapOf(ref to schema)), prices(), AssetIndex(null), { now.toEpochMilli() },
            costPreflight = service { seen += it; input(it) })
        val built = assertIs<PlanBuild.Ready>(builder.build("p", model, "prompt", null, emptyMap(), emptyList())).plan
        assertEquals("1024", seen.single().effectiveParams["resolution"])
        assertTrue(built.warnings.any { "0.10 USD" in it })
        val edited = assertIs<PlanBuild.Ready>(builder.rebuild(built, PlanEdit(params = mapOf("resolution" to "2048")))).plan
        assertEquals(2, seen.size); assertEquals(ref, edited.ref)
        assertNotEquals(GenerationCostPreflight.digest(seen[0]), GenerationCostPreflight.digest(seen[1]))
        assertEquals(1, edited.warnings.count { "Comparación al preparar" in it })
        assertIs<PlanBuild.Invalid>(builder.rebuild(edited, PlanEdit(params = mapOf("resolution" to "invalid"))))
        assertEquals(2, seen.size)
    }
}
