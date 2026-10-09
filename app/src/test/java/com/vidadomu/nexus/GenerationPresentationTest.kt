package com.vidadomu.nexus

import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class GenerationPresentationTest {
    private val currency=CurrencyManager(FakeSecureStorage())
    private val job=GenerationJob("test",providerId="crun",modelId="test",modality=Modality.VIDEO,prompt="")
    @Test fun missingChargeDoesNotBecomeFreeOrAnEstimate() {
        assertEquals("Por confirmar",confirmedJobCost(job.copy(status=GenerationStatus.FAILED,estimatedCostUsd=.05),currency))
    }
    @Test fun creditsUseTheSharedFormatter() {
        assertEquals("1.622 créditos",confirmedJobCost(job.copy(parameters=mapOf("__actual_credits" to "1622.28")),currency))
    }
    @Test fun uncertaintyAndTrackingErrorsRemainVisible() {
        assertEquals("Por confirmar",generationStatusLabel(job.copy(parameters=mapOf("__submission_uncertain" to "true"))))
        assertEquals("Consulta interrumpida",generationStatusLabel(job.copy(parameters=mapOf("__tracking_poll_error" to "true"))))
    }
}
