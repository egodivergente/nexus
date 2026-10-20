package com.vidadomu.nexus.provider.flow

/**
 * Reintento automático de encargos de Flow. Solo se repiten los fallos que el puente garantiza anteriores al clic
 * en «Generar» (navegador o proyecto no disponibles, controles aún no listos…), así que reintentar nunca puede
 * duplicar una generación. Un envío incierto («uncertain») o un error de referencias no se repite jamás.
 */
internal object FlowRetryPolicy {
    const val MAX_RETRIES = 2
    const val DELAY_MS = 90_000L

    val PRE_CLICK = setOf(
        "flow_browser_unavailable", "flow_project_not_open", "flow_project_ambiguous", "flow_controls_unavailable",
        "flow_composer_not_editable", "flow_composer_not_empty", "flow_submit_unavailable", "flow_submit_failed",
        "flow_not_ready", "flow_not_connected"
    )

    fun shouldRetry(code: String?, attempt: Int): Boolean = code in PRE_CLICK && attempt < MAX_RETRIES

    /** Cada intento es una petición distinta para el puente; el primero conserva el identificador de siempre. */
    fun requestId(jobId: String, block: Int, attempt: Int): String =
        if (attempt > 0) "${jobId}_${block}_r$attempt" else "${jobId}_$block"

    fun waiting(retryAt: Long?, now: Long = System.currentTimeMillis()): Boolean = retryAt != null && now < retryAt
}
