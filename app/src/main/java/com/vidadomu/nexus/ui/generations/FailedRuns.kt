package com.vidadomu.nexus.ui.generations

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus

/** Fila de Historial: un trabajo, o la cabecera plegable de una racha de fallos repetidos. */
internal sealed interface HistoryRow {
    val key: String
    data class Job(val job: GenerationJob) : HistoryRow { override val key: String get() = job.id }
    data class Header(val runId: String, val jobs: List<GenerationJob>) : HistoryRow { override val key: String get() = "run-$runId" }
}

/** Mismo modelo y mismo texto: es el mismo intento repetido (los archivos no se guardan en el trabajo). */
private fun GenerationJob.sameAttemptAs(other: GenerationJob) =
    modelId == other.modelId && prompt.trim() == other.prompt.trim()

/**
 * Agrupa los fallos seguidos del mismo intento. Solo se pliegan rachas de [minRun] o más; una racha
 * desplegada ([expanded]) muestra su cabecera y debajo cada intento. Respeta el orden recibido.
 */
internal fun groupFailedRuns(jobs: List<GenerationJob>, expanded: Set<String> = emptySet(), minRun: Int = 3): List<HistoryRow> {
    val rows = mutableListOf<HistoryRow>()
    var i = 0
    while (i < jobs.size) {
        val first = jobs[i]
        var end = i + 1
        if (first.status == GenerationStatus.FAILED) {
            while (end < jobs.size && jobs[end].status == GenerationStatus.FAILED && jobs[end].sameAttemptAs(first)) end++
        }
        val run = jobs.subList(i, end)
        if (run.size >= minRun) {
            rows += HistoryRow.Header(first.id, run.toList())
            if (first.id in expanded) rows += run.map { HistoryRow.Job(it) }
        } else rows += run.map { HistoryRow.Job(it) }
        i = end
    }
    return rows
}
