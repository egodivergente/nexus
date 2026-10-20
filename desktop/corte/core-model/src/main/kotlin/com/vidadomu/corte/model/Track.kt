package com.vidadomu.corte.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrackKind { VIDEO, AUDIO }

/**
 * What happens to a clip on a sync-locked track that straddles a ripple point.
 * SPLIT keeps every frame in sync (the clip is cut and its tail shifts with the edit).
 * EXTEND is for beds (music, ambience): the clip is lengthened/shortened so it keeps playing
 * continuously; if the source has no media left it falls back to SPLIT and the edit reports it.
 */
@Serializable
enum class SpanPolicy { SPLIT, EXTEND }

@Serializable
enum class TrackRole(val kind: TrackKind, val defaultSpanPolicy: SpanPolicy, val label: String) {
    PICTURE(TrackKind.VIDEO, SpanPolicy.SPLIT, "Picture"),
    OVERLAY(TrackKind.VIDEO, SpanPolicy.SPLIT, "Overlay"),
    TITLES(TrackKind.VIDEO, SpanPolicy.SPLIT, "Titles"),
    DIALOGUE(TrackKind.AUDIO, SpanPolicy.SPLIT, "Dialogue"),
    ADR(TrackKind.AUDIO, SpanPolicy.SPLIT, "ADR"),
    MUSIC(TrackKind.AUDIO, SpanPolicy.EXTEND, "Music"),
    AMBIENCE(TrackKind.AUDIO, SpanPolicy.EXTEND, "Ambience"),
    FOLEY(TrackKind.AUDIO, SpanPolicy.SPLIT, "Foley"),
    SFX(TrackKind.AUDIO, SpanPolicy.SPLIT, "SFX"),
    VOICE_OVER(TrackKind.AUDIO, SpanPolicy.SPLIT, "Voice-over"),
    GENERIC_AUDIO(TrackKind.AUDIO, SpanPolicy.SPLIT, "Audio"),
}

@Serializable
enum class TransitionKind(val trackKind: TrackKind, val needsHandles: Boolean) {
    CROSS_DISSOLVE(TrackKind.VIDEO, true),
    DIP_TO_BLACK(TrackKind.VIDEO, false),
    AUDIO_CROSSFADE(TrackKind.AUDIO, true),
}

@Serializable
enum class TransitionAlignment { CENTER, START_AT_CUT, END_AT_CUT }

/**
 * A transition sits on the cut between two adjacent clips of the same track
 * (left.endUs == right.timelineStartUs). A plain cut is simply the absence of a transition.
 */
@Serializable
data class Transition(
    val id: String,
    val leftClipId: String,
    val rightClipId: String,
    val kind: TransitionKind,
    val durationUs: Long,
    val alignment: TransitionAlignment = TransitionAlignment.CENTER,
) {
    init {
        require(durationUs > 0) { "transition duration must be positive" }
    }

    /** How much of the transition lies before the cut. */
    val beforeCutUs: Long get() = when (alignment) {
        TransitionAlignment.CENTER -> durationUs / 2
        TransitionAlignment.START_AT_CUT -> 0
        TransitionAlignment.END_AT_CUT -> durationUs
    }
    val afterCutUs: Long get() = durationUs - beforeCutUs

    fun rangeAround(cutUs: Long): TimeRange = TimeRange(cutUs - beforeCutUs, cutUs + afterCutUs)
}

/**
 * Invariant (checked by [TimelineValidator]): clips are sorted by start and never overlap.
 * Overlap is expressed with more tracks or with a [Transition], never by stacking on one track.
 */
@Serializable
data class Track(
    val id: String,
    val kind: TrackKind,
    val name: String,
    val role: TrackRole,
    val clips: List<Clip> = emptyList(),
    val transitions: List<Transition> = emptyList(),
    /** Locked: no clip on this track can be created, removed, trimmed, split or moved. */
    val locked: Boolean = false,
    /** Video: hidden. Audio: silent. */
    val muted: Boolean = false,
    val solo: Boolean = false,
    /** Sync lock: the track shifts with ripple edits made elsewhere. Independent of [locked]. */
    val syncLock: Boolean = true,
    val gainDb: Double = 0.0,
    val spanPolicy: SpanPolicy? = null,
    /** UI only: collapsed rows are drawn short (no thumbnails/waveforms). */
    val collapsed: Boolean = false,
) {
    val effectiveSpanPolicy: SpanPolicy get() = spanPolicy ?: role.defaultSpanPolicy

    fun clip(id: String): Clip? = clips.firstOrNull { it.id == id }

    /** Clip covering timeline time [t], if any (binary search; clips are sorted). */
    fun clipAt(t: Long): Clip? {
        var lo = 0
        var hi = clips.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = clips[mid]
            when {
                t < c.timelineStartUs -> hi = mid - 1
                t >= c.endUs -> lo = mid + 1
                else -> return c
            }
        }
        return null
    }

    val endUs: Long get() = clips.lastOrNull()?.endUs ?: 0L
}
