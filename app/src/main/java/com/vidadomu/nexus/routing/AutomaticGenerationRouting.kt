package com.vidadomu.nexus.routing

import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.settings.RuntimePreferences
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.create.creationReadiness
import kotlinx.coroutines.*
import java.math.BigDecimal

/** Quotes are account/request-specific snapshots. A successful balance GET does NOT certify a generation quota. */
data class QuotedGenerationRoute(val state: CreateUiState, val quote: RouteQuote)
data class AutomaticRoutePlan(val routes: List<QuotedGenerationRoute>, val ceilingUsd: BigDecimal,
    val diagnostics: List<String>, val createdAt: Long = System.currentTimeMillis())

class AutomaticGenerationRouting(private val app: NexusApplication) {
    suspend fun prepare(original: CreateUiState, acceptedUsd:Double): AutomaticRoutePlan? {
        if(!RuntimePreferences.get(app).bool("routing.enabled")) return null
        val source=original.selectedModel ?: return null
        val images=original.attachedMedia.any { it.mimeType.startsWith("image/") }
        val identity=ExactRouteCompatibility.identity(source,images) ?: return null
        val candidates=app.modelCatalogRepository.models.value.filter {
            it.providerId!=source.providerId && ExactRouteCompatibility.identity(it,images)==identity
        }.distinctBy { it.providerId to it.id }
        if(candidates.isEmpty()) {
            RoutingDiagnostics.save(app,source.id,0,0,"Se conserva el proveedor elegido: no hay otra ruta de identidad exacta registrada.")
            return null
        }
        return coroutineScope {
            val quotes=ProviderRouteQuotes(app)
            val selected=async { withTimeoutOrNull(12_000) { quotes.quote(original) } }
            val mapped=candidates.map { candidate -> async {
                withTimeoutOrNull(12_000) {
                    val model=try {
                        if(candidate.providerId=="atlas_cloud") NativeModelSchema.load(candidate).model else candidate
                    } catch(e:CancellationException) { throw e } catch(_:Exception) { return@withTimeoutOrNull null }
                    val state=ExactRouteCompatibility.map(original,model) ?: return@withTimeoutOrNull null
                    if(!creationReadiness(state).canSubmit) return@withTimeoutOrNull null
                    quotes.quote(state)?.let { QuotedGenerationRoute(state,it) }
                }
            } }
            val acceptedCeiling=BigDecimal.valueOf(acceptedUsd)
            val sourceQuote=selected.await() ?: run {
                mapped.forEach {it.cancel()}
                RoutingDiagnostics.save(app,source.id,candidates.size,0,"Se conserva el proveedor elegido: falta total monetario o saldo numérico de origen.")
                return@coroutineScope null
            }
            val ceiling=minOf(sourceQuote.totalUsd,acceptedCeiling)
            val options=(listOf(QuotedGenerationRoute(original,sourceQuote))+mapped.awaitAll().filterNotNull())
            val ordered=options.filter { it.quote.funded && it.quote.totalUsd<=ceiling }
                .sortedWith(compareBy<QuotedGenerationRoute> { it.quote.totalUsd }.thenBy { it.state.selectedModel!!.providerId })
            RoutingDiagnostics.save(app,source.id,candidates.size,ordered.size,
                if(ordered.isEmpty()) "Ninguna alternativa acreditada dentro del importe aceptado." else "Selección por total de la petición y saldo; cuota no expuesta por la API, pendiente de admisión.")
            if(ordered.isEmpty() || ordered.size==1 && ordered.first().state.selectedModel==source) return@coroutineScope null
            AutomaticRoutePlan(ordered,ceiling,listOf(
                "Modelo exacto: $identity; parámetros efectivos, roles y orden de referencias conservados.",
                "Comparadas ${options.size} cotizaciones con saldo numérico. Límite: ${sourceQuote.totalUsd.toPlainString()} USD.",
                "Cuota de generación: no expuesta por estas APIs; se confirma al admitir la solicitud. Un rechazo explícito permite fallback; un estado incierto lo bloquea.",
                "Otras rutas: excluidas si falta compatibilidad completa, cotización, conversión monetaria o saldo verificable."
            ))
        }
    }
}
