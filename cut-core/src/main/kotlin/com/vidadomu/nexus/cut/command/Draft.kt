package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.model.Track
import com.vidadomu.nexus.cut.timeline.Collateral
import com.vidadomu.nexus.cut.timeline.EditContext
import com.vidadomu.nexus.cut.timeline.EditResult

/**
 * Copia de trabajo de una transacción. Las operaciones la van transformando; solo si todas terminan
 * bien se convierte en el nuevo estado de la sesión. Si una falla, se descarta entera.
 */
class Draft internal constructor(project: Project, view: ViewState, val ids: IdGenerator) {
    var project: Project = project
        private set
    var view: ViewState = view

    val warnings = ArrayList<String>()
    val collateral = ArrayList<Collateral>()
    val created = ArrayList<String>()
    /** Etiqueta de la última edición del motor: sirve de nombre del paso si la petición no trae uno. */
    var lastLabel: String? = null
        private set

    val timeline: Timeline get() = project.timeline
    /** El motor consulta los medios del borrador vigente, incluidos los añadidos en esta misma transacción. */
    val ctx: EditContext = EditContext(media = { id -> this.project.media(id) }, ids = ids)

    /** Aplica el resultado de una edición del motor o anula la transacción con su motivo. */
    fun edit(result: EditResult): EditResult.Applied = when (result) {
        is EditResult.Rejected -> cutFail(ErrorCode.REJECTED_BY_ENGINE, result.reason)
        is EditResult.Applied -> {
            project = project.copy(timeline = result.timeline)
            warnings += result.warnings; collateral += result.collateral; created += result.createdIds
            lastLabel = result.label
            result
        }
    }

    inline fun timeline(block: (Timeline, EditContext) -> EditResult): EditResult.Applied = edit(block(timeline, ctx))

    /** Cambios del proyecto que no son de la línea de tiempo (nombre, formato, medios, vínculos). También se deshacen. */
    fun header(label: String, transform: (Project) -> Project) {
        val next = transform(project)
        project = next.copy(id = project.id, timeline = project.timeline, revision = project.revision, schemaVersion = project.schemaVersion)
        lastLabel = label
    }

    fun clip(id: String): Clip = timeline.clip(id) ?: cutFail(ErrorCode.NOT_FOUND, "Clip inexistente ($id)")
    fun track(id: String): Track = timeline.track(id) ?: cutFail(ErrorCode.NOT_FOUND, "Pista inexistente ($id)")
    fun media(id: String): MediaRef = project.media(id) ?: cutFail(ErrorCode.NOT_FOUND, "Medio inexistente ($id)")
    fun requireClips(ids: Set<String>): Set<String> = ids.onEach { clip(it) }
}
