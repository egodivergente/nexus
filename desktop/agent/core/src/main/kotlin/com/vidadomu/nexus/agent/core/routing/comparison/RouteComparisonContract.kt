package com.vidadomu.nexus.agent.core.routing.comparison

import com.vidadomu.nexus.agent.core.registry.ModelRef
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/** A citation supplied by an adapter, not evidence fetched or authenticated by this module. */
data class Evidence(val source: String, val observedAt: Instant, val statement: String)

/** Publisher-assigned identity. Never populate by stripping fast/pro, family names or versions. */
data class ExactModelIdentity(val publisher: String, val model: String, val version: String)

/** contractRevision must identify the complete endpoint contract (a revision or content digest). */
data class RouteKey(
    val model: ModelRef,
    val identity: ExactModelIdentity,
    val endpoint: String,
    val contractRevision: String,
)

enum class Availability { AVAILABLE, UNAVAILABLE, UNKNOWN }

/** Opaque content digest/local ID, not a signed URL, credential, prompt or media bytes. Order matters. */
data class ReferenceInput(val role: String, val modality: String, val contentId: String)

/** Canonical, effective values, including defaults, quality options and all price-affecting inputs. */
data class RequestedConfiguration(
    val modality: String,
    val capabilities: Set<String>,
    val parameters: Map<String, String>,
    val references: List<ReferenceInput>,
    val quantity: Int,
    val inputDigest: String,
    val complete: Boolean = false,
)

/**
 * Request-specific receipt from the adapter's complete schema validation, NOT a hint from a catalog.
 * It certifies parameter combinations, reference roles/count/order, output count and effective defaults.
 * No receipt means unknown support. This pure policy deliberately does not interpret provider schemas.
 */
data class ConfigurationReceipt(val route: RouteKey, val configuration: RequestedConfiguration, val evidence: Evidence)

data class EndpointContract(
    val modality: String,
    val capabilities: Set<String>,
    val requiredParameters: Set<String>,
    val unresolvedRequiredParameters: Set<String>,
    val receipt: ConfigurationReceipt?,
)

data class ProviderRoute(
    val key: RouteKey,
    val identityEvidence: Evidence?,
    val contract: EndpointContract,
    val hidden: Boolean,
    val availability: Availability,
)

/** Full unfiltered inventory, including hidden/unavailable entries; registry.list() alone is not one. */
data class AtlasCatalog(
    val routes: List<ProviderRoute>,
    val complete: Boolean,
    val includesHidden: Boolean,
    val refreshSucceeded: Boolean,
    val availability: Availability,
    val evidence: Evidence?,
)

/** Explicit, request-scoped attestation of equivalent execution without a quality downgrade. */
data class EquivalenceEvidence(
    val fal: RouteKey,
    val atlas: RouteKey,
    val configuration: RequestedConfiguration,
    val preservesQuality: Boolean,
    val evidence: Evidence,
)

enum class PriceBasis { REQUEST_TOTAL, COMPUTE_SECOND, VIDEO_SECOND, UNIT, BASE, UNKNOWN }

/** REQUEST_TOTAL covers the entire batch, not a unit rate or a lower bound. Null is never zero. */
data class PriceObservation(
    val route: RouteKey,
    val configuration: RequestedConfiguration,
    val amount: BigDecimal?,
    val currency: String?,
    val basis: PriceBasis,
    val includesAllMandatoryCharges: Boolean,
    val evidence: Evidence?,
    val validUntil: Instant? = null,
)

/** Direct conversion only: target total = source total * rate + feeInTargetCurrency. */
data class CurrencyConversion(
    val from: String,
    val to: String,
    val rate: BigDecimal,
    val feeInTargetCurrency: BigDecimal,
    val includesAllConversionCosts: Boolean,
    val evidence: Evidence,
    val roundingScale: Int? = null,
    val roundingMode: RoundingMode? = null,
)

data class FreshnessPolicy(
    val quoteMaxAge: Duration,
    val catalogMaxAge: Duration,
    val evidenceMaxAge: Duration,
    val conversionMaxAge: Duration,
    val futureTolerance: Duration,
) {
    init {
        require(listOf(quoteMaxAge, catalogMaxAge, evidenceMaxAge, conversionMaxAge, futureTolerance)
            .all { !it.isNegative }) { "Freshness windows cannot be negative" }
    }
}

enum class DecisionKind {
    MODEL_ABSENT_IN_ATLAS, EQUIVALENT_WITH_VERIFIED_SAVINGS,
    EQUIVALENT_WITHOUT_SAVINGS, INSUFFICIENT_EVIDENCE,
}

enum class CatalogPresence { ABSENT, PRESENT, UNKNOWN }

enum class ReasonCode {
    INVALID_REQUEST, INCOMPLETE_CONFIGURATION, HIDDEN_ROUTE, PROVIDER_UNAVAILABLE,
    INVALID_ROUTE, WRONG_PROVIDER, IDENTITY_UNVERIFIED, CONTRACT_UNVERIFIED,
    CONFIGURATION_INCOMPATIBLE, CATALOG_INCOMPLETE, CATALOG_REFRESH_FAILED,
    MISSING_EVIDENCE, STALE_EVIDENCE, FUTURE_EVIDENCE, MODEL_PRESENT, MODEL_ABSENT,
    NO_ELIGIBLE_ATLAS_ROUTE, EQUIVALENCE_UNVERIFIED, QUALITY_NOT_PRESERVED,
    MISSING_PRICE, NOT_A_TOTAL, INVALID_PRICE, QUOTE_SCOPE_MISMATCH,
    CURRENCY_MISMATCH, CONVERSION_UNVERIFIED, SAVINGS_VERIFIED, NO_SAVINGS,
    DUPLICATE_ROUTE,
}

data class Reason(val code: ReasonCode, val message: String, val route: RouteKey? = null)

/** A comparison against one identified Atlas route, never a claim about the entire market. */
data class VerifiedComparison(
    val atlas: RouteKey,
    val falTotal: BigDecimal,
    val atlasTotal: BigDecimal,
    val currency: String,
    val savings: BigDecimal,
    val falQuote: PriceObservation,
    val atlasQuote: PriceObservation,
    val equivalence: EquivalenceEvidence,
    val conversion: CurrencyConversion?,
)

data class RouteDecision(
    val kind: DecisionKind,
    val fal: RouteKey,
    val catalogPresence: CatalogPresence,
    val facts: List<Reason>,
    val unverified: List<Reason>,
    val comparisons: List<VerifiedComparison>,
) {
    /** No automatic model selection, quality score or speed tie-break is exposed. */
    val complementsAtlas: Boolean get() = kind == DecisionKind.MODEL_ABSENT_IN_ATLAS ||
        kind == DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS
}
