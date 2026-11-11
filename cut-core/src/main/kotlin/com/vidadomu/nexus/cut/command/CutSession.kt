package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.storage.ProjectStore
import com.vidadomu.nexus.cut.timeline.AgentFocus
import com.vidadomu.nexus.cut.timeline.ChangeSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * El bus de comandos de un montaje abierto y su único escritor. La interfaz, el S Pen y el agente piden
 * lo mismo por el mismo camino: `Request → transacción sobre un borrador → motor → estado guardado → eventos`.
 * Nadie más modifica el proyecto, así que lo que hace el agente se ve al instante en la línea de tiempo
 * del usuario y comparte su «Deshacer».
 */
class CutSession(
    project: Project,
    val registry: CutRegistry,
    private val store: ProjectStore? = null,
    private val log: ActionLog? = null,
    private val ids: IdGenerator = IdGenerator.RANDOM,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineContext = Dispatchers.IO,
    private val historyLimit: Int = 500,
) {
    private class Entry(val label: String, val origin: Origin, val changes: ChangeSet, val headBefore: Project?, val headAfter: Project?)

    private val mutex = Mutex()
    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()
    private val replays = object : LinkedHashMap<String, Outcome.Done>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Outcome.Done>?) = size > 256
    }

    private val _state = MutableStateFlow(CutState(project))
    val state: StateFlow<CutState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CutEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<CutEvent> = _events.asSharedFlow()

    val projectId: String get() = _state.value.project.id

    init {
        log?.knownKeys()?.forEach { k ->
            replays[k.key] = Outcome.Done(revision = k.revision, committed = true, dryRun = false, label = k.label, results = emptyList(), replayed = true)
        }
    }

    // ------------------------------------------------------------------ entrada única

    suspend fun execute(request: Request): Outcome {
        if (request.ops.isEmpty()) return reject(request, CutError(ErrorCode.INVALID_ARGUMENT, "La petición no trae operaciones."))
        val caps = ArrayList<Capability>(request.ops.size)
        for ((i, op) in request.ops.withIndex()) {
            val c = registry[op.id]
                ?: return reject(request, CutError(ErrorCode.UNKNOWN_CAPABILITY, "No existe la capacidad «${op.id}». Consulta el catálogo.", i, op.id))
            if (!registry.available(c)) {
                return reject(request, CutError(ErrorCode.UNAVAILABLE, "«${op.id}» no está disponible aquí (requiere ${c.requires.joinToString()}).", i, op.id))
            }
            caps += c
        }
        val alone = caps.size == 1
        return when {
            caps.any { it.kind == CapabilityKind.JOB } ->
                if (alone) startJob(request, caps[0])
                else reject(request, CutError(ErrorCode.INVALID_ARGUMENT, "Un trabajo largo se pide solo, no dentro de una transacción."))
            caps.any { it.handler is Handler.Undo || it.handler is Handler.Redo } ->
                if (alone) mutex.withLock { travel(request, undo = caps[0].handler is Handler.Undo) }
                else reject(request, CutError(ErrorCode.INVALID_ARGUMENT, "Deshacer y rehacer se piden solos."))
            caps.all { it.kind == CapabilityKind.READ } -> read(request, caps)
            else -> mutex.withLock { transact(request, caps) }
        }
    }

    /** Atajo para código de la app: `run("clip.split", Origin.USER, "at_us" to cabezal)`. */
    suspend fun run(id: String, origin: Origin, vararg args: Pair<String, Any?>, dryRun: Boolean = false): Outcome =
        execute(Request(Op(id, argsOf(*args)), origin, dryRun = dryRun))

    suspend fun undo(origin: Origin): Outcome = mutex.withLock { travel(Request(Op("project.undo"), origin), undo = true) }
    suspend fun redo(origin: Origin): Outcome = mutex.withLock { travel(Request(Op("project.redo"), origin), undo = false) }

    // ------------------------------------------------------------------ vista de alta frecuencia

    /** El cabezal se mueve muchas veces por segundo al reproducir: no pasa por la transacción ni por el historial. */
    fun setPlayhead(timeUs: Long) = _state.update { s -> s.copy(view = s.view.copy(playheadUs = timeUs.coerceIn(0, maxOf(0, s.project.timeline.durationUs)))) }
    fun setPlaying(playing: Boolean) = _state.update { s -> if (s.view.playing == playing) s else s.copy(view = s.view.copy(playing = playing)) }

    // ------------------------------------------------------------------ consultas

    private suspend fun read(request: Request, caps: List<Capability>): Outcome {
        val snapshot = _state.value
        val results = ArrayList<JsonObject>(caps.size)
        for ((i, op) in request.ops.withIndex()) {
            results += try {
                (caps[i].handler as Handler.Read).read(snapshot, Args(op.args))
            } catch (e: CutFailure) {
                return Outcome.Failed(CutError(e.code, e.message ?: "Consulta no válida", i, op.id, snapshot.revision))
            } catch (e: IllegalArgumentException) {
                return Outcome.Failed(CutError(ErrorCode.INVALID_ARGUMENT, e.message ?: "Argumentos no válidos", i, op.id, snapshot.revision))
            }
        }
        return Outcome.Done(revision = snapshot.revision, committed = false, dryRun = false, label = "Consulta", results = results)
    }

    // ------------------------------------------------------------------ transacción

    private suspend fun transact(request: Request, caps: List<Capability>): Outcome {
        val before = _state.value
        request.idempotencyKey?.let { key -> replays[key]?.let { return it.copy(replayed = true) } }
        if (request.expectedRevision != null && request.expectedRevision != before.revision) {
            return reject(request, CutError(ErrorCode.REVISION_CONFLICT,
                "El montaje va por la revisión ${before.revision} y la petición se preparó sobre la ${request.expectedRevision}. Vuelve a leerlo.",
                currentRevision = before.revision))
        }
        val draft = Draft(before.project, before.view, ids)
        val results = ArrayList<JsonObject>(caps.size)
        for ((i, op) in request.ops.withIndex()) {
            val fail = { code: ErrorCode, message: String -> reject(request, CutError(code, message, i, op.id, before.revision)) }
            results += try {
                when (val h = caps[i].handler) {
                    is Handler.Edit -> h.apply(draft, Args(op.args))
                    is Handler.Read -> h.read(before.copy(project = draft.project, view = draft.view), Args(op.args))
                    else -> return fail(ErrorCode.INTERNAL, "Manejador inesperado")
                }
            } catch (e: CutFailure) {
                return fail(e.code, e.message ?: "Operación no válida")
            } catch (e: IllegalArgumentException) {
                // Invariantes del modelo (duraciones, resoluciones…): es un argumento que no se puede representar.
                return fail(ErrorCode.INVALID_ARGUMENT, e.message ?: "Argumentos no válidos")
            } catch (e: ArithmeticException) {
                return fail(ErrorCode.INVALID_ARGUMENT, "Un valor numérico está fuera de rango.")
            }
        }
        val after = draft.project
        val changes = ChangeSet.diff(before.project.timeline, after.timeline)
        val headChanged = head(before.project) != head(after)
        val changed = !changes.isEmpty || headChanged
        val label = request.label?.takeIf { it.isNotBlank() }
            ?: (if (request.ops.size == 1) draft.lastLabel else null)
            ?: if (request.origin == Origin.AGENT) "Edición del agente" else if (request.ops.size == 1) request.ops[0].id else "Edición múltiple"
        val touched = AgentFocus.touchedClips(changes, after.timeline)
        val focus = if (changes.isEmpty) null else AgentFocus.timeOf(changes, after.timeline)
        fun done(revision: Long, committed: Boolean, preview: Project? = null) = Outcome.Done(
            revision = revision, committed = committed, dryRun = request.dryRun, label = label, results = results,
            createdIds = draft.created.toList(), warnings = draft.warnings.distinct(), collateral = draft.collateral.distinct(),
            touchedClips = touched, focusUs = focus, changeCount = changes.changes.size + if (headChanged) 1 else 0, preview = preview,
        )
        if (request.dryRun) return done(before.revision, committed = false, preview = after)
        if (!changed) {
            // Solo cambió la vista (cabezal, selección): se ve al instante y no toca el historial.
            publish(before.project, draft.view, before.view)
            return done(before.revision, committed = false)
        }
        val committed = after.copy(revision = before.revision + 1, modifiedAtMs = clock())
        persist(committed)?.let { return reject(request, CutError(ErrorCode.INTERNAL, it, currentRevision = before.revision)) }
        undoStack.addLast(Entry(label, request.origin, changes, head(before.project).takeIf { headChanged }, head(after).takeIf { headChanged }))
        while (undoStack.size > historyLimit) undoStack.removeFirst()
        redoStack.clear()
        publish(committed, draft.view, before.view)
        log?.committed(committed.revision, committed.modifiedAtMs, request.origin, label, request.idempotencyKey, request.ops, draft.created)
        val outcome = done(committed.revision, committed = true)
        request.idempotencyKey?.let { replays[it] = outcome }
        _events.tryEmit(CutEvent.Committed(committed.revision, request.origin, label, request.ops.map { it.id }, touched, focus, outcome.warnings))
        return outcome
    }

    // ------------------------------------------------------------------ historial compartido

    private suspend fun travel(request: Request, undo: Boolean): Outcome {
        val current = _state.value
        val from = if (undo) undoStack else redoStack
        val entry = from.removeLastOrNull()
            ?: return reject(request, CutError(ErrorCode.PRECONDITION_FAILED, if (undo) "No hay nada que deshacer." else "No hay nada que rehacer.", 0, request.ops[0].id, current.revision))
        val timeline = (if (undo) entry.changes.inverse() else entry.changes).applyTo(current.project.timeline)
        val base = (if (undo) entry.headBefore else entry.headAfter) ?: current.project
        val project = base.copy(timeline = timeline, revision = current.revision + 1, modifiedAtMs = clock())
        persist(project)?.let { from.addLast(entry); return reject(request, CutError(ErrorCode.INTERNAL, it, currentRevision = current.revision)) }
        (if (undo) redoStack else undoStack).addLast(entry)
        publish(project, current.view, current.view)
        val label = (if (undo) "Deshacer: " else "Rehacer: ") + entry.label
        log?.committed(project.revision, project.modifiedAtMs, request.origin, label, null, request.ops, emptyList())
        _events.tryEmit(if (undo) CutEvent.Undone(project.revision, request.origin, entry.label) else CutEvent.Redone(project.revision, request.origin, entry.label))
        val touched = AgentFocus.touchedClips(if (undo) entry.changes.inverse() else entry.changes, timeline)
        return Outcome.Done(revision = project.revision, committed = true, dryRun = false, label = label,
            results = listOf(JsonObject(mapOf("label" to JsonPrimitive(entry.label), "by" to JsonPrimitive(entry.origin.name.lowercase())))),
            touchedClips = touched, changeCount = entry.changes.changes.size)
    }

    // ------------------------------------------------------------------ trabajos largos

    private suspend fun startJob(request: Request, cap: Capability): Outcome {
        val op = request.ops[0]
        val revision = _state.value.revision
        return try {
            val result = (cap.handler as Handler.Job).start(this, Args(op.args), request.origin)
            Outcome.Done(revision = _state.value.revision, committed = false, dryRun = false, label = request.label ?: op.id, results = listOf(result))
        } catch (e: CancellationException) {
            throw e
        } catch (e: CutFailure) {
            reject(request, CutError(e.code, e.message ?: "No se pudo iniciar el trabajo", 0, op.id, revision))
        } catch (e: IllegalArgumentException) {
            reject(request, CutError(ErrorCode.INVALID_ARGUMENT, e.message ?: "Argumentos no válidos", 0, op.id, revision))
        }
    }

    // ------------------------------------------------------------------ utilidades

    private suspend fun persist(project: Project): String? {
        val s = store ?: return null
        return try {
            withContext(io) { s.save(project) }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "No se pudo guardar el montaje y no se ha cambiado nada: ${e.message}"
        }
    }

    private fun reject(request: Request, error: CutError): Outcome.Failed {
        val revision = _state.value.revision
        log?.rejected(revision, clock(), request.origin, error, request.ops)
        _events.tryEmit(CutEvent.Rejected(revision, request.origin, error))
        return Outcome.Failed(error)
    }

    /**
     * Publica el nuevo estado. El cabezal puede haberse movido por la reproducción mientras se preparaba la
     * transacción: si la petición no lo cambió, se respeta el que hay ahora en vez de devolverlo atrás.
     */
    private fun publish(project: Project, wanted: ViewState, base: ViewState) = _state.update { live ->
        val view = wanted.copy(playheadUs = if (wanted.playheadUs == base.playheadUs) live.view.playheadUs else wanted.playheadUs, playing = live.view.playing)
        snapshot(project, tidy(view, project))
    }

    private fun snapshot(project: Project, view: ViewState) = CutState(
        project = project, view = view,
        canUndo = undoStack.isNotEmpty(), undoLabel = undoStack.lastOrNull()?.label,
        canRedo = redoStack.isNotEmpty(), redoLabel = redoStack.lastOrNull()?.label,
    )

    /** La vista nunca apunta a algo que ya no existe. */
    private fun tidy(view: ViewState, project: Project): ViewState {
        val clips = project.timeline.allTracks.flatMapTo(HashSet()) { t -> t.clips.map { it.id } }
        val tracks = project.timeline.allTracks.mapTo(HashSet()) { it.id }
        return view.copy(
            playheadUs = view.playheadUs.coerceIn(0, maxOf(0, project.timeline.durationUs)),
            selection = view.selection.filterTo(LinkedHashSet()) { it in clips },
            targetTracks = view.targetTracks.filterTo(LinkedHashSet()) { it in tracks },
        )
    }

    companion object {
        const val ACTION_LOG = "actions.jsonl"

        /** Todo lo que no es línea de tiempo, normalizado para comparar y restaurar. */
        private fun head(p: Project): Project = p.copy(timeline = Timeline(), revision = 0, modifiedAtMs = 0)

        class Opened(val session: CutSession, val recoveredFromBackup: Boolean, val primaryError: String?)

        /** Abre un montaje guardado con su registro de acciones (recupera la copia de seguridad si el principal está dañado). */
        fun open(store: ProjectStore, projectId: String, registry: CutRegistry, ids: IdGenerator = IdGenerator.RANDOM,
                 clock: () -> Long = System::currentTimeMillis, io: CoroutineContext = Dispatchers.IO): Opened {
            val loaded = store.load(projectId)
            val log = ActionLog(File(store.fileOf(projectId).parentFile, ACTION_LOG))
            return Opened(CutSession(loaded.project, registry, store, log, ids, clock, io), loaded.recoveredFromBackup, loaded.primaryError)
        }

        fun argsOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.mapNotNull { (k, v) -> v?.let { k to json(it) } }.toMap())

        private fun json(v: Any): JsonElement = when (v) {
            is JsonElement -> v
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is Enum<*> -> JsonPrimitive(v.name.lowercase())
            is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to (x?.let(::json) ?: JsonNull) })
            is Iterable<*> -> JsonArray(v.map { it?.let(::json) ?: JsonNull })
            else -> throw IllegalArgumentException("argumento no admitido: ${v::class.simpleName}")
        }
    }
}
