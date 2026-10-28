package com.vidadomu.nexus.routing

import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasPriceQuote
import com.vidadomu.nexus.provider.crun.CrunProvider
import com.vidadomu.nexus.provider.runway.RunwayPricing
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.agent.core.config.RuleScope
import com.vidadomu.nexus.settings.RuntimePreferences
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException

/** Numeric quotes and account balances are kept separate from display labels. No media uploads or generation POSTs. */
data class RouteQuote(val totalUsd: BigDecimal, val availableUsd: BigDecimal, val checkedAt: Long,
    val source: String, val accountSource: String, val currency: String = "USD") {
    val funded: Boolean get() = availableUsd >= totalUsd
}

class ProviderRouteQuotes(private val app: NexusApplication) {
    suspend fun quote(state: CreateUiState): RouteQuote? {
        val model = state.selectedModel ?: return null
        val provider = app.providerRegistry.getProviderById(model.providerId) ?: return null
        return try {
            when(model.providerId) {
                "atlas_cloud" -> {
                    val price = AtlasPriceQuote.fetch(model,state.parameters,state.attachedMedia.count { it.mimeType.startsWith("image/") })
                        ?.takeIf { it.isRequestTotal && !it.baseTariff && it.unit == null && it.exactUsd != null } ?: return null
                    val balance = app.atlasProvider.fetchBalance().getOrThrow()
                    if(!balance.balanceCurrency.equals("USD",true)) return null
                    val amount = balance.availableUsdExact?.toBigDecimalOrNull() ?: return null
                    RouteQuote(price.exactUsd!!.toBigDecimal(),amount,price.checkedAt,
                        price.source ?: return null,"https://api.atlascloud.ai/public/v1/balance")
                }
                "runway", "crun" -> {
                    val key = app.secureStorage.getProviderApiKey(model.providerId)
                    if(key.isBlank()) return null
                    val account = provider.fetchAccountStatus(key)?.getOrThrow() ?: return null
                    if(account.amountUnit != "credits") return null
                    val balance = account.exactAmount?.toBigDecimalOrNull() ?: return null
                    val credits = if(model.providerId == "crun") {
                        (provider as? CrunProvider)?.estimateCredits(model,state.parameters,state.prompt)?.getOrThrow()?.let(BigDecimal::valueOf)
                    } else RunwayPricing.quote(model,state.parameters,
                        state.attachedMedia.count { it.mimeType.startsWith("image/") },promptLength=state.prompt.length).usd?.let(BigDecimal::valueOf)
                    credits ?: return null
                    // Crun credits reflect the user's purchase. Never silently invent an exchange rate.
                    val unitUsd = if(model.providerId == "runway") BigDecimal.ONE else RuntimePreferences.get(app)
                        .value("routing.creditUsd",RuleScope(provider=model.providerId)).toBigDecimalOrNull()?.takeIf { it.signum()>0 } ?: return null
                    val total = credits * unitUsd
                    val available = balance * if(model.providerId == "runway") BigDecimal("0.01") else unitUsd
                    RouteQuote(total,available,System.currentTimeMillis(),
                        if(model.providerId == "runway") "https://docs.dev.runwayml.com/guides/pricing/" else "https://api.crun.ai/api/v1/client/job/estimate-credits",
                        if(model.providerId == "runway") "https://api.dev.runwayml.com/v1/organization" else "https://api.crun.ai/api/v1/client/account/balance")
                }
                else -> null // Generic templates, Fal unit rates and unobservable balances are not certified totals.
            }?.takeIf { it.totalUsd.signum()>=0 && it.availableUsd.signum()>=0 }
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { null }
    }
}
