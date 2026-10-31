package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.core.model.SubmissionSafety
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitFailureTest {
    private val definitelyRejectedStatuses = setOf(400, 401, 402, 403, 404, 422, 429)

    private fun http(status: Int, message: String = "HTTP $status") =
        ProviderError(message, technicalDetails = "HTTP $status - body", httpStatusCode = status, errorCode = "HTTP_$status")

    private fun cyclic(inner: Throwable): Throwable {
        val outer = IllegalStateException("outer", inner)
        inner.initCause(outer)
        return outer
    }

    @Test fun everyHttpStatusFollowsSubmissionSafety() {
        for (status in 100..599) {
            val expected = if (status in definitelyRejectedStatuses) SubmitFailureKind.REJECTED else SubmitFailureKind.UNCERTAIN
            assertEquals("HTTP $status", expected, classifySubmitFailure(http(status)))
        }
    }

    @Test fun serverErrorsAndAmbiguousStatusesAreUncertain() {
        for (status in listOf(408, 409, 425, 500, 502, 503, 504, 520, 524))
            assertEquals("HTTP $status", SubmitFailureKind.UNCERTAIN, classifySubmitFailure(http(status)))
    }

    @Test fun serverErrorMessageNeverPromisesNoCharge() {
        val failure = describeSubmitFailure(http(502, "El proveedor ha devuelto un error del servidor. Revisa el historial antes de repetir la solicitud."))
        assertEquals(SubmitFailureKind.UNCERTAIN, failure.kind)
        assertFalse(failure.message.contains("No se ha enviado"))
        assertFalse(failure.message.contains("cobrado", ignoreCase = true))
        assertTrue(failure.message.contains("error del servidor"))
    }

    @Test fun unreachableHostIsNotSent() {
        assertEquals(SubmitFailureKind.NOT_SENT, classifySubmitFailure(UnknownHostException("api.example")))
        assertEquals(SubmitFailureKind.NOT_SENT, classifySubmitFailure(ConnectException("refused")))
        assertEquals(SubmitFailureKind.NOT_SENT, classifySubmitFailure(ProviderError("Sin red", errorCode = "NO_NETWORK")))
    }

    @Test fun failedRetryAfterAnEarlierAttemptIsUncertain() {
        val retry = UnknownHostException("api.example").apply { addSuppressed(IOException("unexpected end of stream")) }
        val reconnect = ConnectException("refused").apply { addSuppressed(SocketException("Connection reset")) }
        assertEquals(SubmitFailureKind.UNCERTAIN, classifySubmitFailure(retry))
        assertEquals(SubmitFailureKind.UNCERTAIN, classifySubmitFailure(reconnect))
    }

    @Test fun networkCodeWithHttpStatusIsJudgedByStatus() {
        assertEquals(SubmitFailureKind.REJECTED,
            classifySubmitFailure(ProviderError("x", httpStatusCode = 401, errorCode = "NO_NETWORK")))
        assertEquals(SubmitFailureKind.UNCERTAIN,
            classifySubmitFailure(ProviderError("x", httpStatusCode = 503, errorCode = "NO_NETWORK")))
    }

    @Test fun wrappedNetworkErrorsAreUncertainLikeTheStoredJob() {
        val wrapped = listOf(
            IOException("wrap", ConnectException("refused")),
            IOException("wrap", UnknownHostException("api.example")),
            RuntimeException(UnknownHostException("api.example")),
            IllegalStateException("upload", ProviderError("Sin red", errorCode = "NO_NETWORK")),
            RuntimeException(http(401)),
            IOException(IOException(ConnectException("refused"))),
        )
        for (error in wrapped) assertEquals(error.toString(), SubmitFailureKind.UNCERTAIN, classifySubmitFailure(error))
    }

    @Test fun ioFailuresAfterConnectingAreUncertain() {
        val errors = listOf(SocketTimeoutException("timeout"), InterruptedIOException("interrupted"),
            IOException("unexpected end of stream"), EOFException(), SocketException("Connection reset"),
            SSLHandshakeException("handshake"), ProviderError("Tiempo agotado", errorCode = "TIMEOUT"),
            ProviderError("Conexión interrumpida", errorCode = "IO_ERROR"),
            ProviderError("Puente Flow inalcanzable", errorCode = "FLOW_BRIDGE_UNREACHABLE"))
        for (error in errors) assertEquals(error.toString(), SubmitFailureKind.UNCERTAIN, classifySubmitFailure(error))
    }

    @Test fun numbersAndKeywordsInTextAreNotEvidence() {
        val texts = listOf("Uploaded 4000 bytes", "port 4001 closed", "HTTP 401 Unauthorized", "HTTP 400 Bad Request",
            "invalid json at offset 12", "Invalid response", "403 forbidden", "429 too many", "saldo insuficiente",
            "insufficient credits", "api key rejected", "rechazado", "balance 422", "job_402_abc")
        for (text in texts) {
            assertEquals("IO: $text", SubmitFailureKind.UNCERTAIN, classifySubmitFailure(IOException(text)))
            assertEquals("ISE: $text", SubmitFailureKind.UNCERTAIN, classifySubmitFailure(IllegalStateException(text)))
            assertEquals("PE: $text", SubmitFailureKind.UNCERTAIN, classifySubmitFailure(ProviderError(text)))
            assertEquals("cause: $text", SubmitFailureKind.UNCERTAIN,
                classifySubmitFailure(RuntimeException("x", IOException(text))))
        }
    }

    @Test fun unknownAndLocalErrorsAreUncertain() {
        val errors = listOf(IllegalStateException("No se pudo cargar el modelo."), IllegalArgumentException("Revisa la configuración."),
            NullPointerException(), RuntimeException(), Exception("Expected BEGIN_OBJECT"), ProviderError("Proveedor no disponible."),
            ProviderError("Error desconocido", errorCode = "UNKNOWN"), ProviderError("Flow ocupado", errorCode = "flow_busy"))
        for (error in errors) assertEquals(error.toString(), SubmitFailureKind.UNCERTAIN, classifySubmitFailure(error))
    }

    @Test fun cyclicCausesTerminateAndStayUncertain() {
        val errors = listOf(cyclic(IOException("loop")), cyclic(ConnectException("refused")),
            cyclic(UnknownHostException("api.example")), cyclic(RuntimeException("HTTP 401")))
        for (error in errors) {
            assertEquals(error.toString(), SubmitFailureKind.UNCERTAIN, classifySubmitFailure(error))
            assertEquals(SubmitFailureKind.UNCERTAIN, describeSubmitFailure(error).kind)
        }
        val ioWithSuppressed = IOException("retried").apply { addSuppressed(IOException("other")) }
        assertEquals(SubmitFailureKind.UNCERTAIN, describeSubmitFailure(ioWithSuppressed).kind)
    }

    @Test fun neverLessCautiousThanTheStoredJob() {
        val corpus = (100..599).map { http(it) } + listOf<Throwable>(UnknownHostException("h"), ConnectException("c"),
            ProviderError("n", errorCode = "NO_NETWORK"), IOException("400"), IllegalStateException("invalid"),
            RuntimeException(ConnectException("c")), cyclic(ConnectException("c")),
            UnknownHostException("h").apply { addSuppressed(IOException("sent")) })
        for (error in corpus) {
            if (classifySubmitFailure(error) != SubmitFailureKind.UNCERTAIN)
                assertTrue(error.toString(), SubmissionSafety.definitelyRejected(error))
            if (!SubmissionSafety.definitelyRejected(error))
                assertEquals(error.toString(), SubmitFailureKind.UNCERTAIN, classifySubmitFailure(error))
        }
    }

    @Test fun notSentMessageSaysSoWithoutClaimingNoCharge() {
        val text = submitFailureMessage(SubmitFailureKind.NOT_SENT, null)
        assertTrue(text.startsWith("No se ha enviado"))
        assertFalse(text.contains("cobrado", ignoreCase = true))
    }

    @Test fun uncertainMessageDoesNotInviteRetryNorDenyCharge() {
        for (detail in listOf(null, "", "timeout")) {
            val text = submitFailureMessage(SubmitFailureKind.UNCERTAIN, detail)
            assertTrue(text.startsWith("No se ha podido confirmar el envío"))
            assertTrue(text.contains("podría haber llegado"))
            assertFalse(text.contains("No se ha enviado"))
            assertFalse(text.contains("no se ha cobrado", ignoreCase = true))
            assertFalse(text.contains("vuelve a intentarlo", ignoreCase = true))
            assertFalse(text.contains("Reintentar", ignoreCase = true))
            assertFalse(text.contains("vuelve a generar", ignoreCase = true))
        }
    }

    @Test fun uncertainMessageKeepsReadableDetailButHidesRawIo() {
        assertTrue(describeSubmitFailure(IllegalStateException("No se pudo cargar el modelo.")).message.contains("Detalle: No se pudo cargar el modelo."))
        val io = describeSubmitFailure(IOException("stream was reset: CANCEL"))
        assertFalse(io.message.contains("stream was reset"))
        assertEquals("stream was reset: CANCEL", io.technicalDetails)
    }

    @Test fun rejectedMessageKeepsProviderDetail() {
        assertTrue(submitFailureMessage(SubmitFailureKind.REJECTED, "Saldo insuficiente").contains("Saldo insuficiente"))
        val failure = describeSubmitFailure(http(402, "El proveedor requiere saldo o autorización de pago. Revisa tu cuenta."))
        assertEquals(SubmitFailureKind.REJECTED, failure.kind)
        assertTrue(failure.message.contains("requiere saldo"))
    }

    @Test fun noMessagePromisesNoChargeWithoutEvidence() {
        for (kind in SubmitFailureKind.values())
            assertFalse(kind.name, submitFailureMessage(kind, null).contains("no se ha cobrado", ignoreCase = true))
    }
}
