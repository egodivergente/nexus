package com.vidadomu.nexus.cut.export

import com.vidadomu.nexus.cut.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val projectId: String, val projectName: String, val progress: Int, val stage: String) : ExportState
    /** [assetId]: el archivo ya está en la Biblioteca de Nexus. */
    data class Done(val projectId: String, val assetId: String, val file: File, val bytes: Long, val durationUs: Long, val detail: String) : ExportState
    data class Failed(val projectId: String?, val message: String) : ExportState
    data class Cancelled(val projectId: String?) : ExportState
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
    val save: suspend (File) -> String,
)

/** Estado de la exportación en curso, compartido por el editor, el agente y [ExportService]. Una a la vez. */
object ExportManager {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    @Volatile
    var pending: ExportJob? = null
        private set

    fun enqueue(job: ExportJob): Boolean = synchronized(this) {
        if (_state.value is ExportState.Running) return false
        pending = job
        _state.value = ExportState.Running(job.project.id, job.project.name, 0, "Preparando…")
        true
    }

    internal fun take(): ExportJob? = synchronized(this) { pending.also { pending = null } }

    internal fun update(s: ExportState) {
        _state.value = s
    }

    fun acknowledge() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }
}
