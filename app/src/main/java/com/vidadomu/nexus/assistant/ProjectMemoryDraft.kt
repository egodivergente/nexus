package com.vidadomu.nexus.assistant

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import kotlinx.serialization.json.*
import java.security.MessageDigest

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
    /** Se pulsó Guardar; el borrador se retira cuando el proyecto contiene todos sus valores. */
    val saveRequested: Boolean = false,
    /**
     * SHA-256 per editable field (notes excluded) when the draft started; see [digestSnapshot].
     * A restored v2 draft may still hold the earlier raw JSON array, which is read as the same six fields.
     */
    val baseSnapshot: String? = null,
    /** Old saved drafts used commas as separators. Do not silently reinterpret them. */
    val legacyModelsNeedReview: Boolean = false,
) {
    fun isDirty(project: Project): Boolean = comparable(project) != from(project).comparable(project)

    /** El proyecto cambió después de empezar el borrador y no por nuestro guardado. */
    fun isStale(project: Project): Boolean = isDirty(project) && when (val base = baseSnapshot) {
        null -> project.memory.updatedAt != baseUpdatedAt
        else -> baseDigests(base) != fieldDigests(project)
    }

    /** Fields still equal to the base: the user never edited them, so a review adopts the current value. */
    fun untouchedFields(): Set<ProjectMemoryPart> {
        val base = baseSnapshot?.let { baseDigests(it) } ?: return emptySet()
        return ProjectMemoryPart.entries.filter { digest(it.canonical(this)) == base[it.ordinal] }.toSet()
    }

    /**
     * Explicit review accepts THIS snapshot, never a later unseen change, and writes nothing.
     * Three-way merge: untouched fields take the current value; edited fields keep the draft text.
     */
    fun afterReview(project: Project): ProjectMemoryDraft =
        untouchedFields().fold(this) { merged, field -> field.withText(merged, field.text(project)) }
            .copy(baseUpdatedAt = project.memory.updatedAt, baseSnapshot = digestSnapshot(project), saveRequested = false)

    fun toMemory(current: ProjectMemory): ProjectMemory = current.copy(
        synopsis = synopsis, tone = tone, visualStyle = style,
        rules = rules.lines(), preferredModels = models.lines(),
        notes = current.notes.filterNot { it.at in removedNotes },
    )

    /**
     * Guardado confirmado: el proyecto contiene los valores íntegros del borrador. Un cambio externo
     * (p. ej. del agente) que mueva `updatedAt` sin contener lo escrito no retira el borrador.
     */
    fun saveConfirmed(project: Project): Boolean =
        saveRequested && !isDirty(project)

    /** La comparación conserva los valores escritos; nunca encubre pérdidas al guardar. */
    fun saveWarning(): String? = when {
        legacyModelsNeedReview -> "Revisa los modelos de este borrador antiguo: ahora va uno por línea y las comas forman parte del nombre. Edita el campo para confirmar la lista."
        description.trim().length > DESCRIPTION_LIMIT -> "La descripción supera los $DESCRIPTION_LIMIT caracteres; acórtala antes de guardar."
        listOf(synopsis, tone, style).any { it.trim().length > TEXT_LIMIT } -> "Un campo de memoria supera los $TEXT_LIMIT caracteres; acórtalo antes de guardar."
        else -> ruleWarning() ?: modelWarning()
    }

    /** El core recorta y deduplica reglas; impedirlo antes de escribir. */
    fun ruleWarning(): String? {
        if (rules.length > RULES_LIMIT) return "Las reglas superan los $RULES_LIMIT caracteres. Reduce el texto antes de guardar."
        val enteredRules = rules.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            enteredRules.any { it.length > AssetIndex.MAX_RULE_LENGTH } -> "Cada regla admite hasta ${AssetIndex.MAX_RULE_LENGTH} caracteres. Divide o acorta la regla larga antes de guardar."
            enteredRules.size > AssetIndex.MAX_RULES -> "El proyecto admite como máximo ${AssetIndex.MAX_RULES} reglas. Reduce la lista antes de guardar."
            enteredRules.map(AssetIndex::norm).distinct().size != enteredRules.size -> "Hay reglas equivalentes para el almacén (ignora tildes, mayúsculas y signos; los textos sin letras latinas también pueden coincidir). Revísalas antes de guardar."
            else -> null
        }
    }

    fun modelWarning(): String? {
        if (models.length > MODELS_LIMIT) return "Los modelos superan los $MODELS_LIMIT caracteres. Reduce el texto antes de guardar."
        val enteredModels = models.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            enteredModels.any { it.length > AssetIndex.MAX_MODEL_LENGTH } -> "Cada modelo preferido admite hasta ${AssetIndex.MAX_MODEL_LENGTH} caracteres. Acorta el nombre largo antes de guardar."
            enteredModels.size > AssetIndex.MAX_PREFERRED_MODELS -> "El proyecto admite como máximo ${AssetIndex.MAX_PREFERRED_MODELS} modelos preferidos. Reduce la lista antes de guardar."
            enteredModels.distinct().size != enteredModels.size -> "Hay modelos preferidos repetidos. Revísalos antes de guardar."
            else -> null
        }
    }

    fun keepNotes(project: Project): Set<Long>? =
        if (removedNotes.isEmpty()) null else project.memory.notes.map { it.at }.filter { it !in removedNotes }.toSet()

    private fun comparable(project: Project): List<Any> = listOf(
        description.trim(), synopsis.trim(), tone.trim(), style.trim(),
        rules.lines().map { it.trim() }.filter { it.isNotEmpty() },
        models.lines().map { it.trim() }.filter { it.isNotEmpty() },
        removedNotes.filter { at -> project.memory.notes.any { it.at == at } }.toSet(),
    )

    internal fun toSaved(): List<String> = listOf(projectId, description, synopsis, tone, style, rules, models,
        removedNotes.joinToString(","), baseUpdatedAt?.toString().orEmpty(), saveRequested.toString(),
        baseSnapshot.orEmpty(), legacyModelsNeedReview.toString())

    companion object {
        const val DESCRIPTION_LIMIT = AssetIndex.MAX_DESCRIPTION
        const val TEXT_LIMIT = AssetIndex.MAX_FIELD
        const val RULES_LIMIT = AssetIndex.MAX_RULES * AssetIndex.MAX_RULE_LENGTH + AssetIndex.MAX_RULES - 1
        const val MODELS_LIMIT = AssetIndex.MAX_PREFERRED_MODELS * AssetIndex.MAX_MODEL_LENGTH + AssetIndex.MAX_PREFERRED_MODELS - 1
        internal const val SAVED_FIELDS = 12
        internal const val LEGACY_FIELDS = 10

        internal const val DIGEST_FORMAT = "nexus-memory-digest-v1"

        internal fun digest(canonical: JsonElement): String =
            MessageDigest.getInstance("SHA-256").digest(canonical.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        internal fun fieldDigests(project: Project): List<String> = ProjectMemoryPart.entries.map { digest(it.canonical(project)) }

        /** Fixed size whatever the memory length: never duplicates the field text in saved state. */
        internal fun digestSnapshot(project: Project): String = buildJsonObject {
            put("format", DIGEST_FORMAT)
            put("sha256", JsonArray(fieldDigests(project).map { JsonPrimitive(it) }))
        }.toString()

        /** Digest object, or the raw six-element array written by earlier v2 drafts. Unreadable means stale. */
        internal fun baseDigests(stored: String): List<String>? = runCatching {
            when (val json = Json.parseToJsonElement(stored)) {
                is JsonObject -> json.takeIf { it["format"]?.jsonPrimitive?.content == DIGEST_FORMAT }
                    ?.get("sha256")?.jsonArray?.map { it.jsonPrimitive.content }
                is JsonArray -> json.map { digest(it) }
                else -> null
            }
        }.getOrNull()?.takeIf { it.size == ProjectMemoryPart.entries.size }

        fun from(project: Project): ProjectMemoryDraft = project.memory.let { m ->
            ProjectMemoryDraft(project.id, project.description.orEmpty(), m.synopsis.orEmpty(), m.tone.orEmpty(),
                m.visualStyle.orEmpty(), m.rules.joinToString("\n"), m.preferredModels.joinToString("\n"),
                baseUpdatedAt = m.updatedAt, baseSnapshot = digestSnapshot(project))
        }

        internal fun fromSaved(values: List<String>): ProjectMemoryDraft? {
            if (values.size !in setOf(SAVED_FIELDS, LEGACY_FIELDS) || values[0].isBlank()) return null
            return ProjectMemoryDraft(values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                values[7].split(',').mapNotNull { it.toLongOrNull() }, values[8].toLongOrNull(), values[9].toBoolean(),
                values.getOrNull(10)?.ifBlank { null },
                if (values.size == LEGACY_FIELDS) ',' in values[6] else values[11].toBoolean())
        }
    }
}

/** Editable memory fields, in the order of the base digest. Notes are deliberately not part of it. */
internal enum class ProjectMemoryPart {
    DESCRIPTION, SYNOPSIS, TONE, STYLE, RULES, MODELS;

    fun text(draft: ProjectMemoryDraft): String = when (this) {
        DESCRIPTION -> draft.description; SYNOPSIS -> draft.synopsis; TONE -> draft.tone
        STYLE -> draft.style; RULES -> draft.rules; MODELS -> draft.models
    }

    fun text(project: Project): String = when (this) {
        DESCRIPTION -> project.description.orEmpty(); SYNOPSIS -> project.memory.synopsis.orEmpty()
        TONE -> project.memory.tone.orEmpty(); STYLE -> project.memory.visualStyle.orEmpty()
        RULES -> project.memory.rules.joinToString("\n"); MODELS -> project.memory.preferredModels.joinToString("\n")
    }

    fun withText(draft: ProjectMemoryDraft, value: String): ProjectMemoryDraft = when (this) {
        DESCRIPTION -> draft.copy(description = value); SYNOPSIS -> draft.copy(synopsis = value)
        TONE -> draft.copy(tone = value); STYLE -> draft.copy(style = value)
        RULES -> draft.copy(rules = value); MODELS -> draft.copy(models = value)
    }

    /** Same JSON shape as the earlier raw v2 snapshot, so its elements hash to the same digests. */
    fun canonical(draft: ProjectMemoryDraft): JsonElement = when (this) {
        RULES, MODELS -> JsonArray(text(draft).lines().map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) })
        else -> JsonPrimitive(text(draft).trim())
    }

    fun canonical(project: Project): JsonElement = when (this) {
        RULES -> JsonArray(project.memory.rules.map { JsonPrimitive(it.trim()) })
        MODELS -> JsonArray(project.memory.preferredModels.map { JsonPrimitive(it.trim()) })
        else -> JsonPrimitive(text(project).trim())
    }
}

/** What accepting a review would do: fields the draft overwrites and current values kept because untouched. */
internal data class ProjectMemoryReviewPlan(
    val merged: ProjectMemoryDraft,
    val overwritten: List<ProjectMemoryPart>,
    val keptCurrent: List<ProjectMemoryPart>,
    val fieldMergeAvailable: Boolean,
)

internal fun ProjectMemoryDraft.reviewPlan(project: Project): ProjectMemoryReviewPlan {
    val untouched = untouchedFields()
    val merged = afterReview(project)
    return ProjectMemoryReviewPlan(merged,
        overwritten = ProjectMemoryPart.entries.filter { it.canonical(merged) != it.canonical(project) },
        keptCurrent = ProjectMemoryPart.entries.filter { it in untouched && it.canonical(this) != it.canonical(project) },
        fieldMergeAvailable = baseSnapshot?.let { ProjectMemoryDraft.baseDigests(it) } != null)
}

/** Un guardado bloqueado nunca se presenta como solicitado ni pisa la memoria actual. */
internal sealed interface ProjectMemorySaveDecision {
    data class Blocked(val draft: ProjectMemoryDraft, val message: String) : ProjectMemorySaveDecision
    data class Ready(val draft: ProjectMemoryDraft) : ProjectMemorySaveDecision
}

internal fun ProjectMemoryDraft.prepareSave(project: Project): ProjectMemorySaveDecision {
    val reason = saveWarning() ?: if (isStale(project))
        "La memoria cambió después de empezar este borrador. Revísala antes de guardar para no sobrescribir cambios del agente."
        else null
    return if (reason == null) ProjectMemorySaveDecision.Ready(copy(saveRequested = true))
        else ProjectMemorySaveDecision.Blocked(copy(saveRequested = false), reason)
}

/** Same path for the ViewModel and JVM regressions: recheck under the store lock, then persist once. */
internal suspend fun persistProjectMemoryDraft(index: AssetIndex, draft: ProjectMemoryDraft): Project =
    index.updateProjectMemoryAndDescription(draft.projectId, draft.description) { current ->
        val decision = draft.prepareSave(current)
        require(decision is ProjectMemorySaveDecision.Ready) { (decision as ProjectMemorySaveDecision.Blocked).message }
        draft.toMemory(current.memory)
    }

/** Borradores por proyecto; sobreviven a cerrar la hoja y a recreaciones de la actividad. */
internal val ProjectMemoryDraftsSaver: Saver<SnapshotStateMap<String, ProjectMemoryDraft>, ArrayList<String>> = Saver(
    save = { drafts -> ArrayList(listOf("nexus-project-drafts-v2") + drafts.values.flatMap { it.toSaved() }) },
    restore = { flat ->
        mutableStateMapOf<String, ProjectMemoryDraft>().apply {
            val v2 = flat.firstOrNull() == "nexus-project-drafts-v2"
            (if (v2) flat.drop(1) else flat).chunked(if (v2) ProjectMemoryDraft.SAVED_FIELDS else ProjectMemoryDraft.LEGACY_FIELDS)
                .forEach { values -> ProjectMemoryDraft.fromSaved(values)?.let { put(it.projectId, it) } }
        }
    },
)

/** Retira borradores sin cambios, guardados con confirmación o de proyectos que ya no existen. */
internal fun pruneProjectDrafts(drafts: MutableMap<String, ProjectMemoryDraft>, projects: List<Project>, keep: String? = null) {
    val byId = projects.associateBy { it.id }
    drafts.keys.toList().forEach { id ->
        val draft = drafts.getValue(id)
        val project = byId[id]
        if (project == null || (id != keep && (draft.saveConfirmed(project) || (!draft.saveRequested && !draft.isDirty(project))))) drafts.remove(id)
    }
}
