package com.vidadomu.nexus.core.network

import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.ui.components.noticeMessage
import com.vidadomu.nexus.ui.create.shouldRetryAfterNetworkChange
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Caso real 17-10: Seedream v5.0 Pro Edit en la wifi amzn-internet → "Trust anchor for certification path not found". */
class TlsInterceptionMessageTest {
    private val raw = "java.security.cert.CertPathValidatorException: Trust anchor for certification path not found."

    private fun androidLikeFailure(): SSLHandshakeException =
        SSLHandshakeException(raw).apply { initCause(CertificateException(CertPathValidatorException("Trust anchor for certification path not found."))) }

    @Test fun `el fallo de certificado se clasifica como red que intercepta, no como conexion interrumpida`() {
        val e = NetworkErrorParser.parse(androidLikeFailure())
        assertEquals("TLS_UNTRUSTED", e.errorCode)
        assertEquals(NetworkErrorParser.TLS_MESSAGE, e.humanReadableMessage)
        assertTrue(e.technicalDetails!!.contains("Trust anchor"))
    }

    @Test fun `la causa anidada tambien se detecta`() {
        assertTrue(NetworkErrorParser.isTlsTrustFailure(java.io.IOException("x", androidLikeFailure())))
        assertFalse(NetworkErrorParser.isTlsTrustFailure(java.io.IOException("Connection reset")))
    }

    @Test fun `un ProviderError ya traducido se respeta`() {
        val pe = ProviderError("Saldo", errorCode = "HTTP_402")
        assertEquals("HTTP_402", NetworkErrorParser.parse(pe).errorCode)
    }

    @Test fun `el aviso del lienzo explica la causa y como resolverla`() {
        val shown = noticeMessage(raw, "No se han podido cargar las opciones del modelo.")
        assertEquals(NetworkErrorParser.TLS_MESSAGE, shown)
        assertTrue(shown.contains("datos móviles"))
    }

    @Test fun `el mensaje ya traducido sobrevive a noticeMessage`() {
        assertEquals(NetworkErrorParser.TLS_MESSAGE, noticeMessage(NetworkErrorParser.TLS_MESSAGE, "fallback"))
    }

    @Test fun `solo los fallos de red se reintentan al cambiar de red`() {
        assertTrue(shouldRetryAfterNetworkChange(raw))
        assertTrue(shouldRetryAfterNetworkChange(NetworkErrorParser.TLS_MESSAGE))
        assertTrue(shouldRetryAfterNetworkChange("Unable to resolve host \"static.atlascloud.ai\""))
        assertTrue(shouldRetryAfterNetworkChange("timeout"))
        assertFalse(shouldRetryAfterNetworkChange(null))
        assertFalse(shouldRetryAfterNetworkChange("Este modelo requiere entradas aún no compatibles: mask"))
        assertFalse(shouldRetryAfterNetworkChange("El esquema no procede del dominio oficial de Atlas."))
        assertFalse(shouldRetryAfterNetworkChange("No se pudo cargar la configuración (HTTP 404)."))
    }
}
