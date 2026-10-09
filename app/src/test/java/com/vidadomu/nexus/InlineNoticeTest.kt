package com.vidadomu.nexus

import com.vidadomu.nexus.ui.components.noticeMessage
import org.junit.Assert.*
import org.junit.Test

class InlineNoticeTest {
    @Test fun networkErrorIsActionableWithoutExposingHost() {
        val raw="Unable to resolve host private.server.example: no address associated"
        val message=noticeMessage(raw)
        assertTrue(message.contains("Comprueba la red"))
        assertFalse(message.contains("private.server"))
    }
    @Test fun timeoutDoesNotEncourageAnUnconditionalResubmission() {
        assertTrue(noticeMessage("SocketTimeoutException: timeout").contains("Comprueba el estado"))
    }
    @Test fun unknownErrorUsesTheProvidedHumanContext() {
        assertEquals("No se han podido cargar las opciones.",noticeMessage("IllegalStateException", "No se han podido cargar las opciones."))
    }
}
