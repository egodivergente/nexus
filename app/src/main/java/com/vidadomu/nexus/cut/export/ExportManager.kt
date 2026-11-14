package com.vidadomu.nexus.cut.export

import com.vidadomu.nexus.cut.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val projectId: String, val projectName: String, val progress: Int, val stage: String,
        val item: Int = 1, val total: Int = 1, val assetIds: List<String> = emptyList()) : ExportState
    /** [assetId]: el archivo ya está en la Biblioteca de Nexus. */
    data class Done(val projectId: String, val assetId: String, val file: File, val bytes: Long, val durationUs: Long, val detail: String,
        val assetIds: List<String> = listOf(assetId)) : ExportState
    data class Failed(val projectId: String?, val message: String, val assetIds: List<String> = emptyList()) : ExportState
    data class Cancelled(val projectId: String?, val assetIds: List<String> = emptyList()) : ExportState
}

/**
 * Una exportación pedida. [save] recibe el archivo ya validado en su sitio definitivo y lo registra en la
 * Biblioteca; devuelve el id del archivo, o lanza con el motivo si no se pudo.
 */
class ExportJob(
    val project: Project,
    val plan: ExportPlan,
    val destination: File,
    val online: Set<String>,
    val fromUs: Long = 0,
    val untilUs: Long = project.timeline.durationUs,
    val save: suspend (File) -> String,
)

/** Estado de la exportación en curso, compartido por el editor, el agente y [ExportService]. Una a la vez. */
object ExportManager {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private val queue = ArrayDeque<ExportJob>()
    private val completed = mutableListOf<String>()
    private var total = 1
    private var item = 0
    private var active: ExportJob? = null

    fun enqueue(job: ExportJob): Boolean = enqueueBatch(listOf(job))

    fun enqueueBatch(jobs: List<ExportJob>): Boolean = synchronized(this) {
        require(jobs.isNotEmpty())
        if (_state.value is ExportState.Running) return false
        queue.clear(); completed.clear(); total = jobs.size; item = 0; active = null
        queue.addAll(jobs)
        val job = jobs.first()
        _state.value = ExportState.Running(job.project.id, job.project.name, 0, "Preparando…", item = 1, total = total)
        true
    }

    internal fun take(): ExportJob? = synchronized(this) { queue.removeFirstOrNull()?.also { item++; active = it } }

    internal fun complete(job: ExportJob, done: ExportState.Done): ExportJob? = synchronized(this) {
        // A cancelled save may finish after a new batch has started. Never mix their results.
        if (active !== job) return null
        active = null
        completed += done.assetId
        if (_state.value !is ExportState.Running) {
            update(_state.value)
            return null
        }
        val next = take()
        if (next == null) _state.value = done.copy(assetIds = completed.toList())
        else update(ExportState.Running(next.project.id, next.project.name, 0, "Preparando…"))
        next
    }

    internal fun update(s: ExportState) = synchronized(this) {
        _state.value = when (s) {
            is ExportState.Running -> s.copy(progress = ((item.coerceAtLeast(1) - 1) * 100 + s.progress) / total,
                stage = if (total > 1) "Clip ${item.coerceAtLeast(1)}/$total · ${s.stage}" else s.stage,
                item = item.coerceAtLeast(1), total = total, assetIds = completed.toList())
            is ExportState.Failed -> s.copy(assetIds = completed.toList()).also { queue.clear() }
            is ExportState.Cancelled -> s.copy(assetIds = completed.toList()).also { queue.clear() }
            else -> s
        }
    }

    fun acknowledge() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }
}
