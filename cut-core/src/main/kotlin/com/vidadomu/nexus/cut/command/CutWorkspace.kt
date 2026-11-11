package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.ProjectSettings
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * Los montajes de Nexus y sus sesiones vivas. La pantalla del editor y el agente piden aquí la sesión de
 * un montaje y reciben LA MISMA instancia: por eso un cambio del agente aparece en la línea de tiempo que
 * el usuario está viendo, y un «Deshacer» del usuario alcanza lo que hizo el agente.
 */
class CutWorkspace(
    val store: ProjectStore,
    val registry: CutRegistry,
    private val ids: IdGenerator = IdGenerator.RANDOM,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val lock = Mutex()
    private val sessions = HashMap<String, CutSession>()

    private val _active = MutableStateFlow<String?>(null)
    /** Montaje que el usuario tiene abierto en pantalla; es el destino por defecto de lo que pida al agente. */
    val active: StateFlow<String?> = _active.asStateFlow()

    fun setActive(projectId: String?) { _active.value = projectId }

    suspend fun list(): List<ProjectStore.Summary> = withContext(io) { store.list() }

    /** Sesión viva de un montaje; se abre del disco la primera vez. Lanza [CutFailure] NOT_FOUND si no existe o está dañado. */
    suspend fun session(projectId: String): CutSession = lock.withLock {
        sessions[projectId] ?: withContext(io) {
            try {
                val loaded = store.load(projectId)
                CutSession(loaded.project, registry, store, ActionLog(File(store.fileOf(projectId).parentFile, CutSession.ACTION_LOG)), ids, clock, io)
            } catch (e: Exception) {
                throw CutFailure(ErrorCode.NOT_FOUND, "No se puede abrir el montaje «$projectId»: ${e.message}")
            }
        }.also { sessions[projectId] = it }
    }

    suspend fun activeSession(): CutSession? = _active.value?.let { id -> runCatching { session(id) }.getOrNull() }

    /** Crea un montaje vacío, ya guardado y con su sesión abierta. */
    suspend fun create(name: String, nexusProjectId: String? = null, sceneId: String? = null, settings: ProjectSettings = ProjectSettings()): CutSession = lock.withLock {
        val project = CutProjects.create(name, clock(), ids, settings, nexusProjectId, sceneId)
        withContext(io) { store.save(project) }
        CutSession(project, registry, store, ActionLog(File(store.fileOf(project.id).parentFile, CutSession.ACTION_LOG)), ids, clock, io).also { sessions[project.id] = it }
    }

    /** Suelta la sesión (y su historial de deshacer) cuando nadie la tiene abierta. El montaje sigue guardado. */
    suspend fun close(projectId: String) = lock.withLock {
        sessions.remove(projectId)
        if (_active.value == projectId) _active.value = null
    }
}
