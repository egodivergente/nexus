package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationActivityTest {
    private fun order(
        status: OrderStatus = OrderStatus.PROCESSING,
        progress: Float? = null,
        modelRef: String = "atlas_cloud:tencent/video/upscaler",
        attempt: Int = 1,
    ) = Order(
        id = "ord_1", planId = "p1", status = status, modelName = "Tencent Video Upscaler", modelRef = modelRef,
        modality = "VIDEO", attempt = attempt, progress = progress, createdAt = 1_000, updatedAt = 9_000,
    )

    @Test fun onlyFiniteProgressInsideZeroToOneIsMeasured() {
        assertEquals(0.42f, requireNotNull(measuredOrderProgress(order(progress = 0.42f))), 0f)
        assertEquals(0f, requireNotNull(measuredOrderProgress(order(progress = 0f))), 0f)
        assertEquals(1f, requireNotNull(measuredOrderProgress(order(progress = 1f))), 0f)
        assertNull("sin dato del proveedor no hay avance", measuredOrderProgress(order(progress = null)))
    }

    @Test fun progressOutsideProcessingIsNeverShown() {
        val submitting = order(OrderStatus.SUBMITTING, progress = 0.7f, attempt = 2)
        assertNull("un avance heredado del intento anterior no se pinta", measuredOrderProgress(submitting))
        assertEquals(ProgressSource.NOT_STARTED, progressSource(submitting))
        val state = generationActivityState(submitting, animationsAllowed = true)
        assertNull(state.progress)
        assertFalse(state.progressText.contains("%"))
        listOf(OrderStatus.DRAFT, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.COMPLETED,
            OrderStatus.FAILED, OrderStatus.UNCERTAIN, OrderStatus.DISCARDED).forEach { status ->
            assertNull("$status no tiene avance en curso", measuredOrderProgress(order(status, progress = 1f)))
            assertEquals(ProgressSource.NOT_REPORTED, progressSource(order(status, progress = 1f)))
        }
    }

    @Test fun nanAndInfiniteProgressAreUnknownNotZero() {
        assertNull(measuredOrderProgress(order(progress = Float.NaN)))
        assertNull(measuredOrderProgress(order(progress = Float.POSITIVE_INFINITY)))
        assertNull(measuredOrderProgress(order(progress = Float.NEGATIVE_INFINITY)))
        assertEquals(ProgressSource.INVALID_VALUE, progressSource(order(OrderStatus.PROCESSING, progress = Float.NaN)))
        assertFalse(generationActivityState(order(progress = Float.NaN), true).progressText.contains("%"))
    }

    @Test fun outOfRangeProgressIsRejectedAndNeverClamped() {
        assertNull("1,5 no se convierte en 1", measuredOrderProgress(order(progress = 1.5f)))
        assertNull("-0,2 no se convierte en 0", measuredOrderProgress(order(progress = -0.2f)))
        assertEquals(ProgressSource.INVALID_VALUE, progressSource(order(progress = 1.5f)))
        val state = generationActivityState(order(progress = 1.5f), animationsAllowed = true)
        assertNull(state.progress)
        assertFalse("no se inventa porcentaje", state.progressText.contains("%"))
    }

    @Test fun flowProvidersReportMilestonesSoTheirValueIsNotAPercentage() {
        val flow = order(progress = 0.5f, modelRef = "google_flow:veo-3")
        assertNull(measuredOrderProgress(flow))
        assertEquals(ProgressSource.PROVIDER_MILESTONES, progressSource(flow))
        assertTrue(reportsMilestonesNotPercentage("google_flow"))
        assertFalse(reportsMilestonesNotPercentage("atlas_cloud"))
        assertEquals("google_flow", orderProviderId(flow))
        val state = generationActivityState(flow, animationsAllowed = true)
        assertFalse(state.progressText.contains("%"))
        assertTrue(state.progressText.contains("hitos"))
    }

    @Test fun measuredProgressIsShownAsTheProvidersOwnPercentage() {
        val state = generationActivityState(order(progress = 0.666f), animationsAllowed = true)
        assertEquals(0.666f, requireNotNull(state.progress), 0f)
        assertEquals(ProgressSource.MEASURED, state.source)
        assertEquals("67 % informado por el proveedor", state.progressText)
    }

    @Test fun submittingAndProcessingAreTheOnlyActivePhases() {
        assertEquals(RenderPhase.SUBMITTING, generationActivityState(order(OrderStatus.SUBMITTING), true).phase)
        assertEquals(RenderPhase.PROCESSING, generationActivityState(order(OrderStatus.PROCESSING), true).phase)
        val submitting = generationActivityState(order(OrderStatus.SUBMITTING), true)
        assertTrue(submitting.active)
        assertEquals(ProgressSource.NOT_STARTED, submitting.source)
        assertNull(submitting.progress)
        assertFalse(submitting.progressText.contains("%"))
    }

    @Test fun titlesClaimOnlyWhatTheStatusProves() {
        assertEquals("Enviando el encargo al proveedor", generationActivityState(order(OrderStatus.SUBMITTING), true).title)
        // PROCESSING solo dice que el proveedor aceptó el trabajo: puede estar en cola.
        assertEquals("Generación en curso", generationActivityState(order(OrderStatus.PROCESSING), true).title)
        assertTrue(generationActivityState(order(OrderStatus.PROCESSING), true).contentDescription.contains(ACTIVITY_DISCLAIMER))
    }

    @Test fun terminalAndIdleStatesNeitherPaintNorAnimate() {
        val idle = listOf(OrderStatus.DRAFT, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.COMPLETED,
            OrderStatus.FAILED, OrderStatus.UNCERTAIN, OrderStatus.DISCARDED)
        idle.forEach { status ->
            val state = generationActivityState(order(status, progress = 1f), animationsAllowed = true)
            assertEquals("$status no es actividad de render", RenderPhase.NONE, state.phase)
            assertFalse("$status no anima", state.animated)
            assertFalse(state.active)
            assertNull("$status no pinta avance", state.progress)
            assertEquals("", state.title)
        }
    }

    @Test fun animationsOffKeepsEveryTextAndTheMeasuredProgress() {
        val state = generationActivityState(order(progress = 0.3f), animationsAllowed = false)
        assertFalse(state.animated)
        assertEquals(0.3f, requireNotNull(state.progress), 0f)
        assertEquals("Generación en curso", state.title)
        assertTrue(state.contentDescription.contains("30 %"))
    }

    @Test fun noElapsedTimeIsEverDerivedFromTheOrder() {
        val retry = order(attempt = 3)
        val state = generationActivityState(retry, animationsAllowed = true)
        val texts = listOf(state.title, state.progressText, state.contentDescription)
        texts.forEach { text ->
            assertFalse("no se inventa cronómetro: «$text»", text.contains("min") || text.contains(" s ") || text.endsWith(" s"))
        }
    }

    @Test fun sweepStaysInsideZeroToOneAndNeverBreaksOnBadInput() {
        assertEquals(0f, sweepIntensity(0f), 0f)
        assertEquals(1f, sweepIntensity(0.5f), 0f)
        assertEquals(0f, sweepIntensity(1f), 0f)
        assertEquals("el pulso es cíclico", sweepIntensity(0.25f), sweepIntensity(1.25f), 0f)
        assertEquals("también con fase negativa", sweepIntensity(0.75f), sweepIntensity(-0.25f), 0f)
        assertEquals(0f, sweepIntensity(Float.NaN), 0f)
        assertEquals(0f, sweepIntensity(Float.POSITIVE_INFINITY), 0f)
        listOf(-2f, -0.3f, 0.1f, 0.9f, 3.7f).forEach { assertTrue(sweepIntensity(it) in 0f..1f) }
    }
    @Test fun mockProgressIsExplicitlySimulatedNotReportedAsReal() {
        val state = generationActivityState(order(progress = 0.5f, modelRef = "mock_provider:mock/cinema-video"), true)
        assertNull(state.progress)
        assertEquals(ProgressSource.SIMULATED, state.source)
        assertFalse(state.progressText.contains("%"))
        assertTrue(state.progressText.contains("simulada"))
    }
}
