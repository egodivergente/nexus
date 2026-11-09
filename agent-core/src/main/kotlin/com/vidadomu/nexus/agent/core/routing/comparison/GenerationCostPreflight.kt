package com.vidadomu.nexus.agent.core.routing.comparison

import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Read-only adapter boundary. Implementations must never submit, upload media or activate services. */
fun interface ComparisonEvidenceSource {
    suspend fun load(plan: GenerationPlan): PreparedComparison?
}

/** Adapter attests normalization of this exact plan, including effective defaults and ordered media. */
data class PreparedComparison(
    val planDigest: String,
    val requested: RequestedConfiguration,
    val fal: ProviderRoute,
    val atlas: AtlasCatalog,
    val equivalences: List<EquivalenceEvidence>,
    val prices: List<PriceObservation>,
    val conversions: List<CurrencyConversion> = emptyList(),
)

/** Advisory only. Cannot change a selection, create a ticket, or invoke a generation backend. */
class GenerationCostPreflight(
    private val source: ComparisonEvidenceSource? = null,
    private val policy: ProviderRoutePolicy? = null,
    private val providerIds: Set<String> = setOf("atlas_cloud", "fal", "fal-extra"),
    private val timeoutMs: Long = 2_000,
) {
    init { require(timeoutMs > 0) }

    suspend fun warnings(plan: GenerationPlan): List<String> {
        if (plan.ref.providerId !in providerIds) return emptyList()
        val unavailable = listOf("Comparación de proveedores: evidencia insuficiente; no hay ahorro verificable para esta petición. Se conserva el modelo elegido.")
        val evaluator = policy ?: return emptyList()
        val adapter = source ?: return emptyList()
        // Isolate the live plan from an adapter retaining or modifying its collections.
        val snapshot = Json.decodeFromString<GenerationPlan>(Json.encodeToString(plan))
        val digest = digest(snapshot)
        val input = try {
            withTimeoutOrNull(timeoutMs) { adapter.load(snapshot) } ?: return unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Never expose exception messages: they may contain credentials or signed media URLs.
            return unavailable
        }
        if (digest(snapshot) != digest || input.planDigest != digest || input.requested.inputDigest != digest)
            return unavailable
        val selected = if (plan.ref == input.fal.key.model) input.fal
            else input.atlas.routes.singleOrNull { it.key.model == plan.ref } ?: return unavailable
        if (selected.hidden || selected.availability != Availability.AVAILABLE) return unavailable
        val decision = evaluator.evaluate(input.fal, input.atlas, input.requested,
            input.equivalences, input.prices, input.conversions)
        return buildList {
            add("Comparación al preparar el plan (${Instant.ofEpochMilli(plan.createdAt)}); se conserva ${plan.ref.providerId}/${plan.ref.modelId}. No es una reserva de tarifa.")
            add(when (decision.kind) {
                DecisionKind.MODEL_ABSENT_IN_ATLAS -> "Modelo ausente en el catálogo Atlas verificado; esto no acredita ahorro."
                DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS -> "Fal tiene un total inferior para las rutas equivalentes verificadas. Cambiar de proveedor requiere elegirlo y preparar otro plan."
                DecisionKind.EQUIVALENT_WITHOUT_SAVINGS -> "No hay ahorro global acreditado a favor de Fal."
                DecisionKind.INSUFFICIENT_EVIDENCE -> "Evidencia insuficiente para concluir un ahorro entre proveedores."
            })
            // Only the selected Atlas endpoint (or all pairs when Fal is selected). No silent switch.
            decision.comparisons.filter { plan.ref == input.fal.key.model || it.atlas == selected.key }.forEach { pair ->
                add("Par verificado: Fal ${pair.falTotal.toPlainString()} ${pair.currency}; Atlas ${pair.atlas.model.modelId} ${pair.atlasTotal.toPlainString()} ${pair.currency}; diferencia Atlas − Fal ${pair.savings.toPlainString()} ${pair.currency}. " +
                    "Fuentes: ${pair.falQuote.evidence!!.source} (${pair.falQuote.evidence.observedAt}), ${pair.atlasQuote.evidence!!.source} (${pair.atlasQuote.evidence.observedAt}).")
            }
            decision.unverified.forEach { add("${it.code}: ${it.message}") }
        }
    }

    companion object {
        /** No delimiter ambiguities; maps sorted, media order/role/field/MIME and defaults retained. */
        fun digest(plan: GenerationPlan): String {
            val canonical = plan.copy(id = "", createdAt = 0, warnings = emptyList(), estimate = null,
                params = plan.params.toSortedMap(), effectiveParams = plan.effectiveParams.toSortedMap())
            return MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(canonical).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
