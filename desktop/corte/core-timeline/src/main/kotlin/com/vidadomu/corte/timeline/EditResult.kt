package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.IdGenerator
import com.vidadomu.corte.model.MediaRef
import com.vidadomu.corte.model.Timeline

/** Everything an edit needs besides the timeline itself. */
class EditContext(
    val media: (String) -> MediaRef?,
    val ids: IdGenerator = IdGenerator.RANDOM,
    /** Shortest clip an edit may leave behind. */
    val minClipDurationUs: Long = 1_000,
)

enum class EditMode { INSERT, OVERWRITE }

enum class Edge { HEAD, TAIL }

/** Something an edit did outside what the user explicitly selected. Shown to the user, never hidden. */
data class Collateral(val trackId: String, val clipId: String, val what: String)

sealed interface EditResult {
    data class Applied(
        val timeline: Timeline,
        val changes: ChangeSet,
        val label: String,
        val warnings: List<String> = emptyList(),
        val collateral: List<Collateral> = emptyList(),
        /** For trims/moves: the delta actually applied after clamping. */
        val appliedDeltaUs: Long = 0,
        /** Ids created by the operation (new clips, tracks, transitions), in creation order. */
        val createdIds: List<String> = emptyList(),
    ) : EditResult

    data class Rejected(val reason: String) : EditResult
}

/** Internal control flow: any check can abort the whole operation atomically. */
internal class EditRejected(message: String) : Exception(message)

internal fun reject(message: String): Nothing = throw EditRejected(message)
