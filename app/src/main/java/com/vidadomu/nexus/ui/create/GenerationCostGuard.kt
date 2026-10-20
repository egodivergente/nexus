package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.components.EstimateUi
import com.vidadomu.nexus.ui.components.PriceAvailability

/** Everything that can shape the provider request: any difference voids a cost seen or accepted earlier. */
data class GenerationFingerprint(val model: NexusModel?, val modality: Modality, val prompt: String,
    val negativePrompt: String, val parameters: Map<String, String>, val media: List<AttachedMedia>)

fun generationFingerprint(state: CreateUiState) = GenerationFingerprint(state.selectedModel, state.selectedModality,
    state.prompt, state.negativePrompt, state.parameters, state.attachedMedia)

/** The cost the user saw for [fingerprint]. [amount] is display text only and is never parsed back into a number. */
data class GenerationCostQuote(val fingerprint: GenerationFingerprint, val known: Boolean,
    val amount: String, val details: String)

fun generationCostQuote(state: CreateUiState, estimate: EstimateUi) =
    GenerationCostQuote(generationFingerprint(state), isKnownCost(estimate), estimate.amount, estimate.details)

/**
 * Loading, failed, published/unit rates and missing tariffs are unknown, never zero.
 * A quote in credits (Crun) has no monetary total and requires explicit acceptance.
 */
internal fun isKnownCost(estimate: EstimateUi): Boolean {
    if (estimate.availability != PriceAvailability.KNOWN || !estimate.hasPrice || !estimate.isRequestTotal) return false
    return estimate.usd?.let { it.isFinite() && it >= 0 } ?: true
}

sealed interface CostGate {
    data object Proceed : CostGate
    data class AskAcceptance(val quote: GenerationCostQuote) : CostGate
    data class Reject(val message: String) : CostGate
}

/**
 * Single generations only: batches keep their own confirmation button. There is no personal per-generation
 * spending threshold in the app yet, so a known cost proceeds in one tap whatever its size.
 */
object GenerationCostGuard {
    const val OFFLINE_MESSAGE = "NEXUS Lab: validación correcta. Envío bloqueado; no se ha consumido saldo."
    const val STALE_QUOTE_MESSAGE = "La configuración cambió mientras se calculaba el importe. Revisa el importe y vuelve a pulsar Generar."
    const val CHANGED_AFTER_ACCEPTANCE_MESSAGE = "La configuración cambió después de abrir el aviso de coste. Revisa el importe y vuelve a pulsar Generar."

    fun onGenerate(quote: GenerationCostQuote, current: GenerationFingerprint, offlineQa: Boolean): CostGate = when {
        offlineQa -> CostGate.Reject(OFFLINE_MESSAGE)
        quote.fingerprint != current -> CostGate.Reject(STALE_QUOTE_MESSAGE)
        quote.known -> CostGate.Proceed
        else -> CostGate.AskAcceptance(quote)
    }

    fun onAccept(pending: GenerationCostQuote, current: GenerationFingerprint, offlineQa: Boolean): CostGate = when {
        offlineQa -> CostGate.Reject(OFFLINE_MESSAGE)
        pending.fingerprint != current -> CostGate.Reject(CHANGED_AFTER_ACCEPTANCE_MESSAGE)
        else -> CostGate.Proceed
    }
}
