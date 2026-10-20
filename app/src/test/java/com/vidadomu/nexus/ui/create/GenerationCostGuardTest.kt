package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.EstimateUi
import com.vidadomu.nexus.ui.components.PriceAvailability
import org.junit.Assert.*
import org.junit.Test

class GenerationCostGuardTest {
    private val model=NexusModel("fixture","Modelo","test",organization="Fixture",modalities=listOf(Modality.IMAGE),
        capabilities=emptyList(),parameters=emptyList(),promptRequired=false)
    private val media=AttachedMedia("a","file:///a.png","image/png",MediaSlotType.PRIMARY_IMAGE)
    private val state=CreateUiState(selectedModel=model,prompt="gato",parameters=mapOf("size" to "1024"),
        attachedMedia=listOf(media),schemaReady=true)
    private val known=EstimateUi("≈ 0,04 €",usd=0.04,isRequestTotal=true)
    private val unknown=EstimateUi(PriceAvailability.UNAVAILABLE.label,"No hay una tarifa utilizable.",availability=PriceAvailability.UNAVAILABLE)
    private fun gate(estimate:EstimateUi,current:CreateUiState=state,offline:Boolean=false)=
        GenerationCostGuard.onGenerate(generationCostQuote(state,estimate),generationFingerprint(current),offline)

    @Test fun knownCostProceedsInOneTap() {assertEquals(CostGate.Proceed,gate(known))}
    @Test fun knownZeroIsARealQuoteAndProceeds() {assertEquals(CostGate.Proceed,gate(EstimateUi("0 €",usd=0.0,isRequestTotal=true)))}
    @Test fun creditQuoteWithoutUsdIsNotMonetaryTotal() {assertFalse(isKnownCost(EstimateUi("3 créditos",hasPrice=true)))}
    @Test fun creditsNeedAcceptanceWithoutARequestTotal() { assertTrue(gate(EstimateUi("3 créditos",hasPrice=true)) is CostGate.AskAcceptance) }
    @Test fun cachedUnitAndFallbackPricesNeedAcceptanceEvenIfTheyHaveUsd() {
        listOf(EstimateUi("Desde 0,04 € / s",usd=0.04), EstimateUi("≈ 0,04 €",usd=0.04),
            EstimateUi("0 € / imagen",usd=0.0)).forEach { assertTrue(gate(it) is CostGate.AskAcceptance) }
    }

    @Test fun unavailableLoadingAndFailedCostsAskForAcceptance() {
        listOf(PriceAvailability.UNAVAILABLE,PriceAvailability.LOADING,PriceAvailability.ERROR).forEach {availability ->
            val estimate=EstimateUi(availability.label,availability=availability)
            assertFalse(isKnownCost(estimate))
            assertTrue(gate(estimate) is CostGate.AskAcceptance)
        }
    }
    @Test fun missingUsdIsNeverTreatedAsZero() {
        val quote=generationCostQuote(state,EstimateUi("Precio por confirmar"))
        assertFalse(quote.known)
        assertEquals("Precio por confirmar",quote.amount)
    }
    @Test fun inconsistentOrInvalidEstimatesAreUnknown() {
        assertFalse(isKnownCost(EstimateUi("?",availability=PriceAvailability.KNOWN)))
        assertFalse(isKnownCost(EstimateUi("?",usd=Double.NaN)))
        assertFalse(isKnownCost(EstimateUi("?",usd=-1.0)))
        assertFalse(isKnownCost(EstimateUi("≈ 1 €",usd=1.0,availability=PriceAvailability.LOADING)))
    }

    @Test fun offlineQaNeverAsksForAPayableConfirmation() {
        assertEquals(CostGate.Reject(GenerationCostGuard.OFFLINE_MESSAGE),gate(unknown,offline=true))
        assertEquals(CostGate.Reject(GenerationCostGuard.OFFLINE_MESSAGE),gate(known,offline=true))
        val pending=generationCostQuote(state,unknown)
        assertEquals(CostGate.Reject(GenerationCostGuard.OFFLINE_MESSAGE),GenerationCostGuard.onAccept(pending,generationFingerprint(state),true))
    }

    @Test fun quoteForAnOlderConfigurationIsRejectedEvenWhenKnown() {
        assertEquals(CostGate.Reject(GenerationCostGuard.STALE_QUOTE_MESSAGE),gate(known,state.copy(prompt="perro")))
    }

    @Test fun acceptanceProceedsOnlyForTheSameConfiguration() {
        val pending=(gate(unknown) as CostGate.AskAcceptance).quote
        assertEquals(CostGate.Proceed,GenerationCostGuard.onAccept(pending,generationFingerprint(state),false))
    }

    @Test fun anyRelevantChangeAfterOpeningTheDialogVoidsAcceptance() {
        val pending=(gate(unknown) as CostGate.AskAcceptance).quote
        listOf(state.copy(selectedModel=model.copy(id="other")),
            state.copy(selectedModel=model.copy(parameters=listOf(ModelParameter("size","Tamaño",ParameterType.TEXT)))),
            state.copy(selectedModality=Modality.VIDEO),
            state.copy(prompt="gato "),
            state.copy(negativePrompt="borroso"),
            state.copy(parameters=mapOf("size" to "2048")),
            state.copy(parameters=state.parameters+("seed" to "1")),
            state.copy(attachedMedia=emptyList()),
            state.copy(attachedMedia=listOf(media.copy(uriString="file:///b.png"))),
            state.copy(attachedMedia=listOf(media.copy(remoteUrl="https://cdn/a.png")))).forEach {changed ->
            assertEquals(CostGate.Reject(GenerationCostGuard.CHANGED_AFTER_ACCEPTANCE_MESSAGE),
                GenerationCostGuard.onAccept(pending,generationFingerprint(changed),false))
        }
    }

    @Test fun transientUiFlagsDoNotVoidAcceptance() {
        val pending=(gate(unknown) as CostGate.AskAcceptance).quote
        val sameRequest=state.copy(errorMessage="aviso",showAdvanced=true)
        assertEquals(CostGate.Proceed,GenerationCostGuard.onAccept(pending,generationFingerprint(sameRequest),false))
    }

    @Test fun parameterOrderDoesNotMatter() {
        val a=state.copy(parameters=linkedMapOf("a" to "1","b" to "2"))
        val b=state.copy(parameters=linkedMapOf("b" to "2","a" to "1"))
        assertEquals(generationFingerprint(a),generationFingerprint(b))
    }
}
