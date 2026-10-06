package com.vidadomu.nexus

import com.vidadomu.nexus.core.network.NetworkErrorParser
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkMessagesTest {
    @Test fun timeoutWarnsAgainstBlindResubmission() {
        val error = NetworkErrorParser.parse(SocketTimeoutException("sensitive URL"))
        assertTrue(error.humanReadableMessage.contains("historial"))
        assertFalse(error.humanReadableMessage.contains("sensitive"))
    }
    @Test fun rateLimitIsNotPresentedAsInsufficientCredit() {
        val error = NetworkErrorParser.parseHttpError(429, null)
        assertTrue(error.humanReadableMessage.contains("límite de solicitudes"))
        assertFalse(error.humanReadableMessage.contains("saldo"))
    }
    @Test fun networkMessageIsSpanishAndKeepsErrorCode() {
        val error = NetworkErrorParser.parse(UnknownHostException())
        assertTrue(error.humanReadableMessage.contains("conexión"))
        assertEquals("NO_NETWORK", error.errorCode)
    }
}
