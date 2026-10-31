package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.core.model.ProviderError
import com.vidadomu.nexus.core.model.SubmissionSafety
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

enum class SubmitFailureKind { NOT_SENT, UNCERTAIN, REJECTED }

/**
 * Only evidence lifts a failure out of UNCERTAIN, and never further than [SubmissionSafety.definitelyRejected]
 * lets the stored job go. Messages and wrapped causes are not evidence: a "400" in a size or an "invalid"
 * from a parser may come after the provider accepted (and billed) the job.
 */
fun classifySubmitFailure(error: Throwable): SubmitFailureKind {
    // An HTTP response takes precedence over a contradictory transport label such as NO_NETWORK.
    val evidence = if (error is ProviderError && error.httpStatusCode != null) error.copy(errorCode = null) else error
    return when {
        !SubmissionSafety.definitelyRejected(evidence) -> SubmitFailureKind.UNCERTAIN
        neverConnected(error) -> SubmitFailureKind.NOT_SENT
        (error as? ProviderError)?.httpStatusCode != null -> SubmitFailureKind.REJECTED
        else -> SubmitFailureKind.UNCERTAIN
    }
}

// OkHttp may retry a POST that was already written; the failed retry carries the earlier attempt as suppressed.
private fun neverConnected(error: Throwable): Boolean =
    ((error is UnknownHostException || error is ConnectException) && error.suppressed.isEmpty()) ||
        (error as? ProviderError)?.let { it.errorCode == "NO_NETWORK" && it.httpStatusCode == null } == true

fun submitFailureMessage(kind: SubmitFailureKind, detail: String?): String {
    val shown = detail?.trim()?.takeIf { it.isNotEmpty() }
    return when (kind) {
        SubmitFailureKind.NOT_SENT ->
            "No se ha enviado: no se pudo conectar con el proveedor, así que la solicitud no llegó. " +
                (shown?.let { "$it " } ?: "") + "Revisa la conexión y vuelve a generar."
        SubmitFailureKind.UNCERTAIN ->
            "No se ha podido confirmar el envío: la solicitud podría haber llegado al proveedor y generar un cobro. " +
                (shown?.let { "Detalle: $it " } ?: "") +
                "No vuelvas a generar todavía; comprueba el historial o la actividad del proveedor."
        SubmitFailureKind.REJECTED ->
            "El proveedor ha rechazado la solicitud" + (shown?.let { ": $it" } ?: ".") +
                " Revisa la conexión del proveedor antes de volver a generar."
    }
}

data class SubmitFailure(val kind: SubmitFailureKind, val message: String, val technicalDetails: String?)

fun describeSubmitFailure(error: Throwable): SubmitFailure {
    val kind = classifySubmitFailure(error)
    val readable = (error as? ProviderError)?.humanReadableMessage ?: error.message?.takeIf { error !is IOException }
    return SubmitFailure(kind, submitFailureMessage(kind, readable), error.message)
}
