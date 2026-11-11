package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.timeline.Collateral
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Quién pide el cambio. Todos pasan por el mismo bus; el origen solo se registra y se muestra. */
enum class Origin { USER, PEN, AGENT, SYSTEM }

/** Estado de trabajo que no forma parte del proyecto guardado ni del historial. */
data class ViewState(
    val playheadUs: Long = 0,
    val selection: Set<String> = emptySet(),
    val playing: Boolean = false,
    /** Pistas destino elegidas por el usuario; vacío = las que decida cada operación. */
    val targetTracks: Set<String> = emptySet(),
)

/** Fotografía inmutable de la sesión: lo que ven a la vez la interfaz, el agente, la preview y la exportación. */
data class CutState(
    val project: Project,
    val view: ViewState = ViewState(),
    val canUndo: Boolean = false,
    val undoLabel: String? = null,
    val canRedo: Boolean = false,
    val redoLabel: String? = null,
) {
    val revision: Long get() = project.revision
}

/** Errores tipados y estables: el agente decide con el código, la persona lee el mensaje. */
enum class ErrorCode {
    /** No existe una capacidad con ese id. */
    UNKNOWN_CAPABILITY,
    /** Falta un argumento o tiene un valor que el schema no admite. */
    INVALID_ARGUMENT,
    /** Un clip, pista, medio o marcador indicado no existe. */
    NOT_FOUND,
    /** El estado actual no permite la operación (p. ej. nada que deshacer). */
    PRECONDITION_FAILED,
    /** El motor rechazó la edición para no romper sincronía, bloqueos o límites del medio. */
    REJECTED_BY_ENGINE,
    /** El proyecto cambió desde la revisión que el llamante dijo haber leído. */
    REVISION_CONFLICT,
    /** La capacidad existe pero este dispositivo o motor no la ofrece ahora. */
    UNAVAILABLE,
    /** Fallo propio (guardado, estado inesperado). No cambió nada. */
    INTERNAL,
}

data class CutError(
    val code: ErrorCode,
    val message: String,
    /** Posición (desde 0) de la operación que falló dentro de la petición. */
    val opIndex: Int? = null,
    val opId: String? = null,
    val currentRevision: Long? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("code", code.name); put("message", message)
        opIndex?.let { put("op_index", it) }; opId?.let { put("op", it) }; currentRevision?.let { put("current_revision", it) }
    }
}

/** Control de flujo interno: cualquier comprobación puede anular la petición entera sin dejar nada a medias. */
class CutFailure(val code: ErrorCode, message: String) : Exception(message)

fun cutFail(code: ErrorCode, message: String): Nothing = throw CutFailure(code, message)

/** Una operación: el id de una capacidad del registro y sus argumentos. */
data class Op(val id: String, val args: JsonObject = JsonObject(emptyMap()))

/**
 * Una petición es una transacción: o se aplican todas sus operaciones y quedan como UN paso de «Deshacer»,
 * o no cambia nada.
 */
data class Request(
    val ops: List<Op>,
    val origin: Origin,
    /** Nombre del paso en el historial; por defecto, el de la operación. */
    val label: String? = null,
    /** Ejecuta y valida todo sin confirmar nada: devuelve lo que cambiaría. */
    val dryRun: Boolean = false,
    /** Si se indica y el proyecto ya va por otra revisión, se rechaza con REVISION_CONFLICT. */
    val expectedRevision: Long? = null,
    /** Repetir una petición con la misma clave no la aplica dos veces. */
    val idempotencyKey: String? = null,
) {
    constructor(op: Op, origin: Origin, label: String? = null, dryRun: Boolean = false) : this(listOf(op), origin, label, dryRun)
}

sealed interface Outcome {
    data class Done(
        /** Revisión del proyecto tras la petición (la misma si no cambió nada o era un ensayo). */
        val revision: Long,
        /** true si el proyecto cambió y quedó guardado. */
        val committed: Boolean,
        val dryRun: Boolean,
        val label: String,
        /** Resultado de cada operación, en el mismo orden. */
        val results: List<JsonObject>,
        val createdIds: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val collateral: List<Collateral> = emptyList(),
        /** Clips creados o cambiados de verdad (no los que solo se desplazaron). */
        val touchedClips: Set<String> = emptySet(),
        /** Primer instante que cambió: donde conviene mirar. */
        val focusUs: Long? = null,
        val changeCount: Int = 0,
        /** Solo en ensayo y para quien llama en proceso: el proyecto tal como quedaría. */
        val preview: Project? = null,
        /** La petición ya se había aplicado con esta clave de idempotencia; no se repitió. */
        val replayed: Boolean = false,
    ) : Outcome

    data class Failed(val error: CutError) : Outcome

    val ok: Boolean get() = this is Done

    fun toJson(): JsonObject = when (this) {
        is Failed -> buildJsonObject { put("ok", false); put("error", error.toJson()) }
        is Done -> buildJsonObject {
            put("ok", true); put("revision", revision); put("committed", committed)
            if (dryRun) put("dry_run", true)
            if (replayed) put("replayed", true)
            put("label", label)
            put("results", JsonArray(results))
            if (createdIds.isNotEmpty()) put("created_ids", strings(createdIds))
            if (touchedClips.isNotEmpty()) put("touched_clips", strings(touchedClips))
            focusUs?.let { put("focus_us", it) }
            if (dryRun || committed) put("changes", changeCount)
            if (warnings.isNotEmpty()) put("warnings", strings(warnings))
            if (collateral.isNotEmpty()) put("collateral", buildJsonArray {
                for (c in collateral) add(buildJsonObject { put("track_id", c.trackId); put("clip_id", c.clipId); put("what", c.what) })
            })
        }
    }

    companion object {
        internal fun strings(values: Iterable<String>) = JsonArray(values.map { JsonPrimitive(it) })
    }
}

/** Lo que pasa en la sesión, para quien quiera observarlo (interfaz, agente, registro). */
sealed interface CutEvent {
    val revision: Long
    val origin: Origin

    data class Committed(
        override val revision: Long, override val origin: Origin, val label: String, val ops: List<String>,
        val touchedClips: Set<String>, val focusUs: Long?, val warnings: List<String>,
    ) : CutEvent

    data class Undone(override val revision: Long, override val origin: Origin, val label: String) : CutEvent
    data class Redone(override val revision: Long, override val origin: Origin, val label: String) : CutEvent
    data class Rejected(override val revision: Long, override val origin: Origin, val error: CutError) : CutEvent
}
