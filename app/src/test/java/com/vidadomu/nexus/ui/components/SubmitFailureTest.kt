package com.vidadomu.nexus.ui.components

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitFailureTest {
    @Test fun unreachableHostIsNotSent() {
        assertEquals(SubmitFailureKind.NOT_SENT, classifySubmitFailure(UnknownHostException("api.example")))
        assertEquals(SubmitFailureKind.NOT_SENT, classifySubmitFailure(IOException("wrap", ConnectException("refused"))))
    }

    @Test fun localValidationIsNotSent() {
        assertEquals(SubmitFailureKind.NOT_SENT, classifySubmitFailure(IllegalStateException("No se pudo cargar el modelo.")))
    }

    @Test fun timeoutAfterSendingIsUncertain() {
        assertEquals(SubmitFailureKind.UNCERTAIN, classifySubmitFailure(SocketTimeoutException("timeout")))
        assertEquals(SubmitFailureKind.UNCERTAIN, classifySubmitFailure(IOException("unexpected end of stream")))
    }

    @Test fun providerErrorIsRejected() {
        assertEquals(SubmitFailureKind.REJECTED, classifySubmitFailure(IllegalStateException("HTTP 401 Unauthorized")))
    }

    @Test fun notSentMessageSaysSo() {
        assertTrue(submitFailureMessage(SubmitFailureKind.NOT_SENT, null).startsWith("No se ha enviado"))
    }

    @Test fun uncertainMessageDoesNotInviteRetry() {
        val text = submitFailureMessage(SubmitFailureKind.UNCERTAIN, "timeout")
        assertTrue(text.startsWith("Verificando"))
        assertFalse(text.contains("vuelve a intentarlo", ignoreCase = true))
        assertFalse(text.contains("Reintentar", ignoreCase = true))
        assertFalse(text.contains("vuelve a generar", ignoreCase = true))
    }

    @Test fun rejectedMessageKeepsProviderDetail() {
        assertTrue(submitFailureMessage(SubmitFailureKind.REJECTED, "Saldo insuficiente").contains("Saldo insuficiente"))
    }
}
