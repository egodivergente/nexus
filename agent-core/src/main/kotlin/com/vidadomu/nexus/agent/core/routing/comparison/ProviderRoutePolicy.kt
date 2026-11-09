package com.vidadomu.nexus.agent.core.routing.comparison

import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Currency

/** Pure assessment: no I/O, fuzzy model matching, unit conversion, scoring or automatic selection. */
class ProviderRoutePolicy(
    private val freshness: FreshnessPolicy,
    private val clock: Clock,
    private val atlasProviderId: String = "atlas_cloud",
    private val falProviderId: String = "fal",
) {
    init { require(atlasProviderId.isNotBlank() && falProviderId.isNotBlank() && atlasProviderId != falProviderId) }

    fun evaluate(
        fal: ProviderRoute,
        atlas: AtlasCatalog,
        requested: RequestedConfiguration,
        equivalences: List<EquivalenceEvidence>,
        prices: List<PriceObservation>,
        conversions: List<CurrencyConversion> = emptyList(),
    ): RouteDecision {
        val now = clock.instant() // one instant for the entire decision
        val facts = mutableListOf<Reason>()
        val unknown = mutableListOf<Reason>()
        val comparisons = mutableListOf<VerifiedComparison>()
        var presence = CatalogPresence.UNKNOWN
        fun result(kind: DecisionKind = DecisionKind.INSUFFICIENT_EVIDENCE) = RouteDecision(
            kind, fal.key, presence, facts.toList(), unknown.toList(), comparisons.toList(),
        )
        fun fail(code: ReasonCode, text: String, route: RouteKey? = null) { unknown += Reason(code, text, route) }

        requestProblem(requested)?.let { unknown += it; return result() }
        if (fal.key.model.providerId != falProviderId) {
            fail(ReasonCode.WRONG_PROVIDER, "El candidato no pertenece al proveedor Fal configurado.", fal.key)
            return result()
        }
        val falProblems = routeProblems(fal, requested, now)
        if (falProblems.isNotEmpty()) { unknown += falProblems; return result() }
        if (atlas.availability != Availability.AVAILABLE) {
            fail(ReasonCode.PROVIDER_UNAVAILABLE, "Atlas no está disponible o no se ha comprobado su disponibilidad.")
            return result()
        }
        if (!atlas.refreshSucceeded) {
            fail(ReasonCode.CATALOG_REFRESH_FAILED, "Falló la actualización de Atlas; no acredita ausencia ni precios actuales.")
            return result()
        }
        evidenceProblem(atlas.evidence, freshness.catalogMaxAge, now, "catálogo Atlas")?.let {
            unknown += it; return result()
        }
        if (atlas.routes.any { it.key.model.providerId != atlasProviderId }) {
            fail(ReasonCode.WRONG_PROVIDER, "El inventario Atlas contiene rutas de otro proveedor.")
            return result()
        }
        if (atlas.routes.map { it.key }.distinct().size != atlas.routes.size) {
            fail(ReasonCode.DUPLICATE_ROUTE, "El inventario contiene rutas duplicadas; debe reconciliarse antes de comparar.")
            return result()
        }
        val identityProblems = atlas.routes.flatMap { identityProblems(it, now) }
        // Raw identity presence blocks an absence claim even when that entry cannot be verified.
        val matching = atlas.routes.filter { it.key.identity == fal.key.identity }
        if (matching.isEmpty()) {
            if (!atlas.complete || !atlas.includesHidden) {
                fail(ReasonCode.CATALOG_INCOMPLETE, "Un inventario parcial o filtrado no demuestra que el modelo falte en Atlas.")
            }
            unknown += identityProblems
            if (unknown.isNotEmpty()) return result()
            presence = CatalogPresence.ABSENT
            facts += Reason(ReasonCode.MODEL_ABSENT, "La identidad y versión exactas no figuran en el inventario completo y vigente de Atlas.", fal.key)
            return result(DecisionKind.MODEL_ABSENT_IN_ATLAS)
        }
        if (!atlas.complete || !atlas.includesHidden) {
            fail(ReasonCode.CATALOG_INCOMPLETE, "Catálogo parcial o filtrado: los pares conocidos no acreditan ahorro frente al conjunto de Atlas.")
        }
        presence = CatalogPresence.PRESENT
        facts += Reason(ReasonCode.MODEL_PRESENT, "Atlas incluye una entrada con la misma identidad y versión; eso no demuestra equivalencia de ejecución.")
        unknown += atlas.routes.filter { it.key.identity != fal.key.identity }.flatMap { identityProblems(it, now) }
        val falPriceProblems = mutableListOf<Reason>()
        val falQuote = quote(fal.key, requested, prices, now, falPriceProblems)
        unknown += falPriceProblems
        for (candidate in matching) {
            val problems = routeProblems(candidate, requested, now)
            if (problems.isNotEmpty()) { unknown += problems; continue }
            val scoped = equivalences.filter {
                it.fal == fal.key && it.atlas == candidate.key && it.configuration == requested
            }
            // Conflicting quality attestations must be reconciled, not cherry-picked.
            if (scoped.any { !it.preservesQuality }) {
                fail(ReasonCode.QUALITY_NOT_PRESERVED, "La evidencia no garantiza preservar la calidad; no se propone ahorrar degradándola.", candidate.key)
                continue
            }
            val valid = scoped.filter { evidenceProblem(it.evidence, freshness.evidenceMaxAge, now, "equivalencia") == null }
            val equivalence = valid.maxByOrNull { it.evidence.observedAt }
            if (equivalence == null) {
                fail(ReasonCode.EQUIVALENCE_UNVERIFIED, "Falta evidencia vigente para estos dos endpoints, sus versiones y la configuración exacta.", candidate.key)
                scoped.forEach { e -> evidenceProblem(e.evidence, freshness.evidenceMaxAge, now, "equivalencia")?.let { unknown += it } }
                continue
            }
            val atlasQuote = quote(candidate.key, requested, prices, now, unknown)
            if (falQuote == null || atlasQuote == null) continue
            var conversion: CurrencyConversion? = null
            val falTotal = if (falQuote.currency == atlasQuote.currency) falQuote.amount!! else {
                val eligible = conversions.filter { it.from == falQuote.currency && it.to == atlasQuote.currency }
                val usable = eligible.filter {
                    it.rate.signum() > 0 && it.feeInTargetCurrency.signum() >= 0 && it.includesAllConversionCosts &&
                        (it.roundingScale == null) == (it.roundingMode == null) &&
                        (it.roundingScale == null || it.roundingScale in 0..12) &&
                        evidenceProblem(it.evidence, freshness.conversionMaxAge, now, "conversión") == null
                }
                // Caller supplies one documented conversion; conflicting rates cannot select the cheapest.
                if (usable.size != 1) {
                    fail(ReasonCode.CURRENCY_MISMATCH, "Monedas distintas sin una única conversión directa, vigente y documentada con todos los costes.", candidate.key)
                    eligible.forEach { e -> evidenceProblem(e.evidence, freshness.conversionMaxAge, now, "conversión")?.let { unknown += it } }
                    continue
                }
                conversion = usable.single()
                val converted = convert(falQuote.amount!!, conversion)
                if (converted == null) {
                    fail(ReasonCode.CONVERSION_UNVERIFIED, "El total no se puede expresar con la regla de redondeo documentada.", candidate.key)
                    continue
                }
                converted
            }
            val saving = atlasQuote.amount!!.subtract(falTotal)
            comparisons += VerifiedComparison(candidate.key, falTotal, atlasQuote.amount, atlasQuote.currency!!,
                saving, falQuote, atlasQuote, equivalence, conversion)
            facts += if (saving.signum() > 0) Reason(ReasonCode.SAVINGS_VERIFIED,
                "Ahorro de ${saving.toPlainString()} ${atlasQuote.currency} frente a esta ruta Atlas, para la misma configuración y cantidad.", candidate.key)
            else Reason(ReasonCode.NO_SAVINGS, "El total de Fal no es inferior al de esta ruta Atlas para la misma configuración y cantidad.", candidate.key)
        }
        if (comparisons.isEmpty()) {
            fail(ReasonCode.NO_ELIGIBLE_ATLAS_ROUTE, "No hay una pareja de rutas elegibles con equivalencia y totales verificados.")
            return result()
        }
        // Pairwise facts remain available; the aggregate never cherry-picks an expensive Atlas endpoint.
        if (comparisons.any { it.savings.signum() <= 0 }) return result(DecisionKind.EQUIVALENT_WITHOUT_SAVINGS)
        if (unknown.isNotEmpty()) return result()
        return result(DecisionKind.EQUIVALENT_WITH_VERIFIED_SAVINGS)
    }

    private fun convert(amount: BigDecimal, fx: CurrencyConversion): BigDecimal? = runCatching {
        val total = amount.multiply(fx.rate).add(fx.feeInTargetCurrency)
        if (fx.roundingScale != null && fx.roundingMode != null) total.setScale(fx.roundingScale, fx.roundingMode) else total
    }.getOrNull()

    private fun requestProblem(c: RequestedConfiguration): Reason? {
        if (!c.complete) return Reason(ReasonCode.INCOMPLETE_CONFIGURATION, "La configuración efectiva no está completa.")
        if (c.modality !in setOf("image", "video", "audio", "text", "3d") || c.capabilities.isEmpty() || c.capabilities.any { it.isBlank() } ||
            c.quantity <= 0 || c.inputDigest.isBlank() || c.parameters.any { it.key.isBlank() || it.value.isBlank() } ||
            c.references.any { it.role.isBlank() || it.modality.isBlank() || it.contentId.isBlank() }) {
            return Reason(ReasonCode.INVALID_REQUEST, "Faltan identidad de entradas, modalidad, capacidades, cantidad positiva o valores explícitos.")
        }
        val required = when (c.modality) {
            "video" -> setOf("duration_seconds", "resolution", "fps", "audio")
            "image" -> setOf("resolution")
            else -> emptySet()
        }
        if (!c.parameters.keys.containsAll(required)) return Reason(ReasonCode.INCOMPLETE_CONFIGURATION,
            "Faltan parámetros explícitos de ${c.modality}: ${(required - c.parameters.keys).sorted().joinToString()}.")
        if (c.modality == "video" && (c.parameters["audio"] !in setOf("true", "false") ||
                !positiveDecimal(c.parameters["duration_seconds"]) || !positiveDecimal(c.parameters["fps"]))) {
            return Reason(ReasonCode.INVALID_REQUEST, "Duración y FPS deben ser positivos; audio debe ser true o false.")
        }
        return null
    }

    private fun positiveDecimal(value: String?): Boolean = value?.toBigDecimalOrNull()?.signum() == 1

    private fun identityProblems(route: ProviderRoute, now: Instant): List<Reason> = buildList {
        val k = route.key
        if (listOf(k.model.providerId, k.model.modelId, k.identity.publisher, k.identity.model, k.identity.version,
                k.endpoint, k.contractRevision).any { it.isBlank() }) {
            add(Reason(ReasonCode.INVALID_ROUTE, "Falta identidad exacta, versión o contrato del endpoint.", k))
        }
        evidenceProblem(route.identityEvidence, freshness.evidenceMaxAge, now, "identidad del modelo", k)?.let { add(it) }
    }

    private fun routeProblems(route: ProviderRoute, c: RequestedConfiguration, now: Instant): List<Reason> = buildList {
        addAll(identityProblems(route, now))
        if (route.hidden) add(Reason(ReasonCode.HIDDEN_ROUTE, "Ruta oculta: no se ofrece ni se utiliza como alternativa de ahorro.", route.key))
        if (route.availability != Availability.AVAILABLE) add(Reason(ReasonCode.PROVIDER_UNAVAILABLE, "La ruta no está disponible o su disponibilidad es desconocida.", route.key))
        val contract = route.contract
        if (contract.modality != c.modality || !contract.capabilities.containsAll(c.capabilities) ||
            contract.unresolvedRequiredParameters.isNotEmpty() || !c.parameters.keys.containsAll(contract.requiredParameters)) {
            add(Reason(ReasonCode.CONFIGURATION_INCOMPATIBLE, "El contrato no cubre modalidad, capacidades o parámetros requeridos.", route.key))
        }
        val receipt = contract.receipt
        if (receipt == null || receipt.route != route.key || receipt.configuration != c) {
            add(Reason(ReasonCode.CONTRACT_UNVERIFIED, "Falta validación del contrato para esta ruta y configuración, incluidas referencias y opciones.", route.key))
        } else evidenceProblem(receipt.evidence, freshness.evidenceMaxAge, now, "validación del contrato", route.key)?.let { add(it) }
    }

    private fun quote(
        route: RouteKey, config: RequestedConfiguration, prices: List<PriceObservation>, now: Instant,
        problems: MutableList<Reason>,
    ): PriceObservation? {
        val forRoute = prices.filter { it.route == route }
        val scoped = forRoute.filter { it.configuration == config }
        if (scoped.isEmpty()) {
            problems += Reason(if (forRoute.isEmpty()) ReasonCode.MISSING_PRICE else ReasonCode.QUOTE_SCOPE_MISMATCH,
                "Falta un precio para esta ruta, revisión, configuración y cantidad exactas.", route)
            return null
        }
        // Multiple observations are ambiguous; adapters reconcile them explicitly before evaluation.
        if (scoped.size != 1) {
            problems += Reason(ReasonCode.INVALID_PRICE, "Hay varias cotizaciones para la misma petición; no se elige la más barata.", route)
            return null
        }
        val q = scoped.single()
        val errors = mutableListOf<Reason>()
        if (q.basis != PriceBasis.REQUEST_TOTAL || !q.includesAllMandatoryCharges) errors += Reason(
            ReasonCode.NOT_A_TOTAL, "Una tarifa base, por unidad o por segundo no es el total completo de la petición.", route)
        if (q.amount == null || q.amount.signum() < 0 || q.currency == null ||
            !Regex("[A-Z]{3}").matches(q.currency) ||
            runCatching { Currency.getInstance(q.currency).defaultFractionDigits < 0 }.getOrDefault(true)) {
            errors += Reason(ReasonCode.INVALID_PRICE, "Importe desconocido/negativo o moneda ISO no identificada; desconocido no significa cero.", route)
        }
        if (q.validUntil != null && (q.validUntil <= now || q.evidence?.let { q.validUntil <= it.observedAt } == true)) {
            errors += Reason(ReasonCode.STALE_EVIDENCE, "La cotización ha vencido o declara un intervalo de validez imposible.", route)
        }
        evidenceProblem(q.evidence, freshness.quoteMaxAge, now, "cotización", route)?.let { errors += it }
        problems += errors
        return q.takeIf { errors.isEmpty() }
    }

    private fun evidenceProblem(e: Evidence?, maxAge: Duration, now: Instant, label: String, route: RouteKey? = null): Reason? {
        if (e == null || e.source.isBlank() || e.statement.isBlank()) return Reason(
            ReasonCode.MISSING_EVIDENCE, "Falta fuente o justificación de $label.", route)
        // Duration arithmetic also handles Instant.MIN/MAX without overflowing Instant.plus/minus.
        if (Duration.between(now, e.observedAt) > freshness.futureTolerance) return Reason(
            ReasonCode.FUTURE_EVIDENCE, "La fecha de $label está en el futuro fuera de tolerancia.", route)
        if (Duration.between(e.observedAt, now) > maxAge) return Reason(
            ReasonCode.STALE_EVIDENCE, "La evidencia de $label ha caducado según la vigencia configurada.", route)
        return null
    }
}
