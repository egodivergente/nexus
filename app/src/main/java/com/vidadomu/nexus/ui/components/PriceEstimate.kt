package com.vidadomu.nexus.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.provider.atlas.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class EstimateUi(val amount: String = "…", val details: String = "", val usd: Double? = null,
    val retry: (() -> Unit)? = null, val hasPrice:Boolean = usd!=null)

@Composable
fun rememberPriceEstimate(model: NexusModel?, parameters: Map<String,String>? = null,
    images: Int? = null, enabled: Boolean = true, videoSeconds: Double? = null, promptLength: Int? = null, prompt: String = ""): EstimateUi {
    val app=LocalContext.current.applicationContext as NexusApplication
    val currency by app.currencyManager.state.collectAsState()
    var attempt by remember(model,parameters,images,enabled,videoSeconds) { mutableIntStateOf(0) }
    var quote by remember(model,parameters,images,enabled,videoSeconds) { mutableStateOf<PriceQuote?>(null) }
    var pending by remember(model,parameters,images,enabled,videoSeconds) { mutableStateOf(enabled) }
    LaunchedEffect(model,parameters,images,enabled,videoSeconds,attempt) {
        pending = enabled
        if(model==null || !enabled) { pending=false;return@LaunchedEffect }
        if(model.providerId != AtlasCloudProvider.PROVIDER_ID) { quote=null;pending=false;return@LaunchedEffect }
        delay(400)
        try { quote=AtlasPriceQuote.fetch(model,parameters,images,videoSeconds) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { quote = VideoPricePreview.fallback(model,parameters,videoSeconds) }
        finally { pending=false }
    }
    currency.preference // subscribe to instant currency/rate changes
    if(model?.providerId=="runway") {
        val q=com.vidadomu.nexus.provider.runway.RunwayPricing.quote(model,parameters,images ?: 0,videoSeconds,promptLength)
        return EstimateUi(q.usd?.let { "≈ "+app.currencyManager.formatUsd(it) } ?: "Precio por confirmar",q.detail,q.usd)
    }
    if(model?.providerId=="crun") return rememberCrunEstimate(model,parameters,prompt,enabled)
    val visibleQuote = quote ?: model?.let { AtlasPublishedPrice.cached(it) ?: VideoPricePreview.fallback(it, parameters, videoSeconds) }
    return visibleQuote?.let {
        val amount = if(it.unit != null) (if(it.baseTariff) "Desde " else "") + app.currencyManager.formatUnitPrice(it.usd) + " / " + it.unit
            else "≈ " + app.currencyManager.formatUsd(it.usd) + (it.clipSeconds?.let { seconds ->
                " / " + java.math.BigDecimal.valueOf(seconds).stripTrailingZeros().toPlainString() + " s"
            } ?: "")
        val rate = it.unitUsd?.let { value ->
            "Tarifa Atlas: " + java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + " USD" +
                (if(it.unit == null || it.unit == "s") "/s" else "/" + it.unit)
        }
        val details = if(it.baseTariff) {
            val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(it.checkedAt))
            "Base publicada · $date · varía según opciones"
        } else listOfNotNull(if(quote == null) "Tarifa guardada" else "Estimación", it.configuration.takeIf { text -> text.isNotBlank() }).joinToString(" · ")
        EstimateUi(amount, details, it.usd)
    } ?: EstimateUi(if(pending) "Consultando precio…" else "No se pudo consultar el precio",
        if(pending) "" else "Falló la consulta a Atlas. Reintenta para conocer el coste antes de generar.",
        retry = if(pending || model == null) null else ({ attempt++ }))
}


@Composable
private fun rememberCrunEstimate(model:NexusModel,parameters:Map<String,String>?,prompt:String,enabled:Boolean):EstimateUi {
    val app=LocalContext.current.applicationContext as NexusApplication
    var attempt by remember(model.id,parameters,prompt,enabled) {mutableIntStateOf(0)}
    var result by remember(model.id,parameters,prompt,enabled) { mutableStateOf(EstimateUi("Consultando tarifa…")) }
    LaunchedEffect(model.id,parameters,prompt,enabled,attempt) {
        if(!enabled) {result=EstimateUi("Preparando opciones…");return@LaunchedEffect}
        result=EstimateUi("Consultando tarifa…")
        delay(500)
        val provider=app.providerRegistry.getProviderById("crun") as? com.vidadomu.nexus.provider.crun.CrunProvider
        result=try {
            val credits=provider?.estimateCredits(model,parameters ?: model.parameters.associate { it.key to it.defaultValue },prompt)?.getOrThrow()
            if(credits!=null && credits.isFinite() && credits>=0) EstimateUi("≈ ${java.math.BigDecimal.valueOf(credits).stripTrailingZeros().toPlainString()} créditos",
                "Crun · cotización del proveedor. El valor monetario depende de los créditos comprados.",hasPrice=true)
            else EstimateUi("Precio por confirmar","Crun no ha devuelto una tarifa válida para estas opciones.",retry={attempt++})
        } catch(e:CancellationException) {throw e} catch(e:Exception) {
            val reason=(e as? com.vidadomu.nexus.core.model.ProviderError)?.humanReadableMessage
                ?: "Crun no ha devuelto una cotización para estas opciones."
            EstimateUi("Precio por confirmar",reason+" No equivale a coste cero.",retry={attempt++})
        }
    }
    return result
}
