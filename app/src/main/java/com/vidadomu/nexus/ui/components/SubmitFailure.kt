package com.vidadomu.nexus.ui.components

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException

enum class SubmitFailureKind { NOT_SENT, UNCERTAIN, REJECTED }

private val rejectionMarkers = listOf("401", "403", "400", "402", "422", "429", "unauthorized", "forbidden",
    "api key", "clave api", "insufficient", "saldo", "credits", "balance", "rechaz", "rejected", "invalid")

/** A timeout after the request left the device may mean the provider accepted the job, so it must not invite a retry. */
fun classifySubmitFailure(error: Throwable): SubmitFailureKind {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    if (chain.any { it is UnknownHostException || it is ConnectException }) return SubmitFailureKind.NOT_SENT
    if (chain.any { it is InterruptedIOException }) return SubmitFailureKind.UNCERTAIN
    val text = chain.mapNotNull { it.message }.joinToString(" ").lowercase()
    if (rejectionMarkers.any(text::contains)) return SubmitFailureKind.REJECTED
    if (chain.any { it is IOException }) return SubmitFailureKind.UNCERTAIN
    return SubmitFailureKind.NOT_SENT
}

fun submitFailureMessage(kind: SubmitFailureKind, detail: String?): String = when (kind) {
    SubmitFailureKind.NOT_SENT ->
        "No se ha enviado: no se ha creado ningún trabajo ni se ha cobrado. " +
            (detail?.takeIf { it.isNotBlank() }?.let { "$it " } ?: "") + "Revisa la conexión y vuelve a generar."
    SubmitFailureKind.UNCERTAIN ->
        "Verificando: el envío salió pero el proveedor no ha confirmado si lo recibió. " +
            "No vuelvas a generar todavía; comprueba la actividad o la conexión del proveedor."
    SubmitFailureKind.REJECTED ->
        "El proveedor ha rechazado la solicitud" + (detail?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ".") +
            " Revisa la conexión del proveedor antes de volver a generar."
}

data class SubmitFailure(val kind: SubmitFailureKind, val message: String, val technicalDetails: String?)

fun describeSubmitFailure(error: Throwable): SubmitFailure {
    val kind = classifySubmitFailure(error)
    val readable = error.message?.takeIf { error !is IOException }
    return SubmitFailure(kind, submitFailureMessage(kind, readable), error.message)
}
