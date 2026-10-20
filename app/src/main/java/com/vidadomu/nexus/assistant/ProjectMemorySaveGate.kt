package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException

/**
 * One in-flight memory save per project. Lives in the ViewModel so closing, reopening or recreating the
 * editor cannot start a second write for the same project while the first is still running.
 */
internal class ProjectMemorySaveGate {
    private val saving = MutableStateFlow<Set<String>>(emptySet())
    val savingIds: StateFlow<Set<String>> = saving.asStateFlow()

    fun isSaving(projectId: String): Boolean = projectId in saving.value

    fun tryAcquire(projectId: String): Boolean {
        while (true) {
            val current = saving.value
            if (projectId in current) return false
            if (saving.compareAndSet(current, current + projectId)) return true
        }
    }

    fun release(projectId: String) {
        while (true) {
            val current = saving.value
            if (projectId !in current || saving.compareAndSet(current, current - projectId)) return
        }
    }
}

internal const val PROJECT_MEMORY_STORAGE_ERROR =
    "No se pudo guardar la memoria en el almacenamiento del dispositivo. Tu borrador se conserva; inténtalo de nuevo."

/**
 * Only messages that [prepareSave] itself produces are shown: the draft's own validation warning or the
 * concurrent-change block re-raised under the store lock. Anything else (storage, parser, unknown project)
 * may carry paths or stored text, so it becomes a generic message.
 */
internal fun projectMemorySaveFailureMessage(error: Throwable, draft: ProjectMemoryDraft? = null): String {
    val message = error.message
    val known = setOfNotNull(draft?.saveWarning(), staleSaveMessage)
    return if (error is IllegalArgumentException && error !is SerializationException && message != null && message in known) message
        else PROJECT_MEMORY_STORAGE_ERROR
}

/** Derived from [prepareSave] rather than copied, so it follows any rewording there. */
private val staleSaveMessage: String? by lazy {
    runCatching {
        val current = Project("stale-probe", "probe", null, createdAt = 0, memory = ProjectMemory(synopsis = "actual", updatedAt = 2))
        val earlier = current.copy(memory = current.memory.copy(synopsis = "base", updatedAt = 1))
        val draft = ProjectMemoryDraft.from(earlier).copy(synopsis = "mío")
        (draft.prepareSave(current) as? ProjectMemorySaveDecision.Blocked)?.message
    }.getOrNull()
}
