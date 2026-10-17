package com.vidadomu.nexus.core.network

import java.io.InterruptedIOException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkErrorParserTimeoutTest {
    @Test fun `el corte por tiempo total de la llamada se explica como tiempo agotado, no como conexion interrumpida`() {
        val e = NetworkErrorParser.parse(InterruptedIOException("timeout"))
        assertEquals("TIMEOUT", e.errorCode)
    }
}