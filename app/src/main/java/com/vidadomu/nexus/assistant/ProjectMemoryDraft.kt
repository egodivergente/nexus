package com.vidadomu.nexus.assistant

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.vidadomu.nexus.agent.core.assets.Project

/**
 * Edición local de la memoria de UN proyecto. Vive fuera del editor para que elegir otro
 * proyecto o cerrar la hoja no la pierda ni la escriba en otro proyecto.
 */
internal data class ProjectMemoryDraft(
    val projectId: String,
    val description: String,
    val synopsis: String,
    val tone: String,
    val style: String,
    val rules: String,
    val models: String,
    val removedNotes: List<Long> = emptyList(),
    /** `memory.updatedAt` del proyecto cuando empezó el borrador: detecta cambios hechos después por el agente. */
    val baseUpdatedAt: Long? = null,
    /** Se pulsó Guardar; el borrador se retira cuando el proyecto confirma una memoria más reciente. */
    val saveRequested: Boolean = false,
) {
    fun isDirty(project: Project): Boolean = comparable(project) != from(project).comparable(project)

    /** El proyecto cambió después de empezar el borrador y no por nuestro guardado. */
    fun isStale(project: Project): Boolean = !saveRequested && project.memory.updatedAt != baseUpdatedAt

    /**
     * Guardado confirmado: memoria posterior a la base Y con los valores del borrador. Un cambio externo
     * (p. ej. del agente) que mueva `updatedAt` sin contener lo escrito no retira el borrador.
     */
    fun saveConfirmed(project: Project): Boolean =
        saveRequested && project.memory.updatedAt != baseUpdatedAt && !isDirty(project)

    fun keepNotes(project: Project): Set<Long>? =
        if (removedNotes.isEmpty()) null else project.memory.notes.map { it.at }.filter { it !in removedNotes }.toSet()

    private fun comparable(project: Project): List<Any> = listOf(
        description.trim(), synopsis.trim(), tone.trim(), style.trim(),
        rules.lines().map { it.trim() }.filter { it.isNotEmpty() },
        models.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() },
        removedNotes.filter { at -> project.memory.notes.any { it.at == at } }.toSet(),
    )

    internal fun toSaved(): List<String> = listOf(projectId, description, synopsis, tone, style, rules, models,
        removedNotes.joinToString(","), baseUpdatedAt?.toString().orEmpty(), saveRequested.toString())

    companion object {
        const val DESCRIPTION_LIMIT = 400
        const val TEXT_LIMIT = 2000
        const val RULES_LIMIT = 6000
        const val MODELS_LIMIT = 800
        internal const val SAVED_FIELDS = 10

        fun from(project: Project): ProjectMemoryDraft = project.memory.let { m ->
            ProjectMemoryDraft(project.id, project.description.orEmpty(), m.synopsis.orEmpty(), m.tone.orEmpty(),
                m.visualStyle.orEmpty(), m.rules.joinToString("\n"), m.preferredModels.joinToString("\n"),
                baseUpdatedAt = m.updatedAt)
        }

        internal fun fromSaved(values: List<String>): ProjectMemoryDraft? {
            if (values.size != SAVED_FIELDS || values[0].isBlank()) return null
            return ProjectMemoryDraft(values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                values[7].split(',').mapNotNull { it.toLongOrNull() }, values[8].toLongOrNull(), values[9].toBoolean())
        }
    }
}

/** Borradores por proyecto; sobreviven a cerrar la hoja y a recreaciones de la actividad. */
internal val ProjectMemoryDraftsSaver: Saver<SnapshotStateMap<String, ProjectMemoryDraft>, ArrayList<String>> = Saver(
    save = { drafts -> ArrayList(drafts.values.flatMap { it.toSaved() }) },
    restore = { flat ->
        mutableStateMapOf<String, ProjectMemoryDraft>().apply {
            flat.chunked(ProjectMemoryDraft.SAVED_FIELDS).forEach { values -> ProjectMemoryDraft.fromSaved(values)?.let { put(it.projectId, it) } }
        }
    },
)

/** Retira borradores sin cambios, guardados con confirmación o de proyectos que ya no existen. */
internal fun pruneProjectDrafts(drafts: MutableMap<String, ProjectMemoryDraft>, projects: List<Project>, keep: String? = null) {
    val byId = projects.associateBy { it.id }
    drafts.keys.toList().forEach { id ->
        val draft = drafts.getValue(id)
        val project = byId[id]
        if (project == null || draft.saveConfirmed(project) || (id != keep && !draft.isDirty(project))) drafts.remove(id)
    }
}
