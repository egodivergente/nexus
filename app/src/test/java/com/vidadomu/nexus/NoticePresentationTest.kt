package com.vidadomu.nexus

import com.vidadomu.nexus.ui.components.noticeMessage
import org.junit.Assert.*
import org.junit.Test

class NoticePresentationTest {
    @Test fun consumptionLimitsKeepAnActionableExplanation() {
        assertTrue(noticeMessage("Limite diario del agente alcanzado").contains("límite diario"))
        assertTrue(noticeMessage("Limite de consumo de este mensaje alcanzado").contains("trabajo se conserva"))
        assertTrue(noticeMessage("Permiso bedrock:CountTokens no disponible").contains("permisos de Bedrock"))
    }
    @Test fun transportFailuresDoNotExposeTechnicalHosts() {
        val text=noticeMessage("Unable to resolve host private.example")
        assertTrue(text.contains("Comprueba la red"))
        assertFalse(text.contains("private.example"))
        assertEquals("No se puede abrir este archivo.",noticeMessage("unexpected traceback","No se puede abrir este archivo."))
    }
}
