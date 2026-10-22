package com.vidadomu.nexus.provider.flow

import org.junit.Assert.*
import org.junit.Test

class FlowProgressTest {
    @Test fun elapsedTimeIsBoundedAndReadable() {
        assertEquals("0 s", FlowProgress.elapsed(-8))
        assertEquals("59 s", FlowProgress.elapsed(59))
        assertEquals("2 min 5 s", FlowProgress.elapsed(125))
    }

    @Test fun preparationAndWaitingAreDifferentStages() {
        assertTrue(FlowProgress.stage("recovering_session", "submitting").contains("sesión"))
        assertTrue(FlowProgress.stage("attaching_references", "submitting").contains("imágenes"))
        assertTrue(FlowProgress.stage("waiting_results", "generating").contains("esperando archivos"))
        assertTrue(FlowProgress.stage(null, "completed").contains("copiando"))
    }

    @Test fun verificationAndDelaysHaveActionableMessages() {
        assertTrue(FlowProgress.problem("verification_required")!!.contains("Abrir navegador"))
        assertTrue(FlowProgress.problem("generation_delayed")!!.contains("sin generar otra vez"))
        assertNull(FlowProgress.problem("unknown"))
        assertFalse(FlowRetryPolicy.shouldRetry("flow_composer_not_empty", 0))
        assertFalse(FlowRetryPolicy.shouldRetry("verification_required", 0))
    }

    @Test fun loginFailuresExposeTheSpecificCauseAndKeepPreSubmitStateClear() {
        assertTrue(FlowProgress.problem("credential_expired")!!.contains("ha caducado"))
        assertTrue(FlowProgress.problem("sign_in_control_missing")!!.contains("botón"))
        assertTrue(FlowProgress.problem("unexpected_destination")!!.contains("no reconocida"))
        for(code in listOf("credential_expired", "sign_in_control_missing", "unexpected_destination")) {
            assertTrue(FlowProgress.problem(code)!!.contains("no se ha enviado"))
            assertFalse(FlowRetryPolicy.shouldRetry(code, 0))
        }
    }
}
