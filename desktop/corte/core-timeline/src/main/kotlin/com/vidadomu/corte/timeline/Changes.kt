package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.Marker
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.Track
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.model.Transition

/**
 * A primitive, invertible change. Undo history stores only these (what changed), never copies of
 * the whole project.
 */
sealed interface Change {
    fun inverse(): Change

    data class ClipAdded(val trackId: String, val clip: Clip) : Change {
        override fun inverse() = ClipRemoved(trackId, clip)
    }

    data class ClipRemoved(val trackId: String, val clip: Clip) : Change {
        override fun inverse() = ClipAdded(trackId, clip)
    }

    data class ClipUpdated(val trackId: String, val before: Clip, val after: Clip) : Change {
        override fun inverse() = ClipUpdated(trackId, after, before)
    }

    data class TransitionAdded(val trackId: String, val transition: Transition) : Change {
        override fun inverse() = TransitionRemoved(trackId, transition)
    }

    data class TransitionRemoved(val trackId: String, val transition: Transition) : Change {
        override fun inverse() = TransitionAdded(trackId, transition)
    }

    data class TransitionUpdated(val trackId: String, val before: Transition, val after: Transition) : Change {
        override fun inverse() = TransitionUpdated(trackId, after, before)
    }

    /** [track] is the full track including clips, so that removing and re-adding round-trips. */
    data class TrackAdded(val index: Int, val track: Track) : Change {
        override fun inverse() = TrackRemoved(index, track)
    }

    data class TrackRemoved(val index: Int, val track: Track) : Change {
        override fun inverse() = TrackAdded(index, track)
    }

    /** Header-only change (flags, name, gain...). Headers carry no clips. */
    data class TrackHeaderUpdated(val before: Track, val after: Track) : Change {
        override fun inverse() = TrackHeaderUpdated(after, before)
    }

    data class MarkerAdded(val marker: Marker) : Change {
        override fun inverse() = MarkerRemoved(marker)
    }

    data class MarkerRemoved(val marker: Marker) : Change {
        override fun inverse() = MarkerAdded(marker)
    }

    data class MarkerUpdated(val before: Marker, val after: Marker) : Change {
        override fun inverse() = MarkerUpdated(after, before)
    }
}

data class ChangeSet(val changes: List<Change>) {
    val isEmpty: Boolean get() = changes.isEmpty()

    fun inverse(): ChangeSet = ChangeSet(changes.asReversed().map { it.inverse() })

    fun applyTo(timeline: Timeline): Timeline = ChangeApplier.apply(timeline, this)

    companion object {
        val EMPTY = ChangeSet(emptyList())

        /** Minimal change list turning [before] into [after]. Cost is linear in the number of clips. */
        fun diff(before: Timeline, after: Timeline): ChangeSet {
            val out = ArrayList<Change>()
            val beforeTracks = before.allTracks.associateBy { it.id }
            val afterTracks = after.allTracks.associateBy { it.id }
            for ((kindList, kind) in listOf(after.videoTracks to TrackKind.VIDEO, after.audioTracks to TrackKind.AUDIO)) {
                kindList.forEachIndexed { index, t -> if (t.id !in beforeTracks) out += Change.TrackAdded(index, t) }
                check(kindList.all { it.kind == kind })
            }
            for (a in after.allTracks) {
                val b = beforeTracks[a.id] ?: continue
                if (b === a) continue
                val hb = header(b)
                val ha = header(a)
                if (hb != ha) out += Change.TrackHeaderUpdated(hb, ha)
                if (b.clips != a.clips) diffClips(a.id, b.clips, a.clips, out)
                if (b.transitions != a.transitions) diffTransitions(a.id, b.transitions, a.transitions, out)
            }
            for ((list, _) in listOf(before.videoTracks to 0, before.audioTracks to 0)) {
                list.forEachIndexed { index, t -> if (t.id !in afterTracks) out += Change.TrackRemoved(index, t) }
            }
            if (before.markers != after.markers) {
                val bm = before.markers.associateBy { it.id }
                val am = after.markers.associateBy { it.id }
                for (m in after.markers) {
                    val old = bm[m.id]
                    if (old == null) out += Change.MarkerAdded(m) else if (old != m) out += Change.MarkerUpdated(old, m)
                }
                for (m in before.markers) if (m.id !in am) out += Change.MarkerRemoved(m)
            }
            return ChangeSet(out)
        }

        fun header(t: Track): Track = if (t.clips.isEmpty() && t.transitions.isEmpty()) t else t.copy(clips = emptyList(), transitions = emptyList())

        private fun diffClips(trackId: String, before: List<Clip>, after: List<Clip>, out: MutableList<Change>) {
            val b = before.associateBy { it.id }
            val a = after.associateBy { it.id }
            for (c in before) if (c.id !in a) out += Change.ClipRemoved(trackId, c)
            for (c in after) {
                val old = b[c.id]
                if (old == null) out += Change.ClipAdded(trackId, c) else if (old != c) out += Change.ClipUpdated(trackId, old, c)
            }
        }

        private fun diffTransitions(trackId: String, before: List<Transition>, after: List<Transition>, out: MutableList<Change>) {
            val b = before.associateBy { it.id }
            val a = after.associateBy { it.id }
            for (t in before) if (t.id !in a) out += Change.TransitionRemoved(trackId, t)
            for (t in after) {
                val old = b[t.id]
                if (old == null) out += Change.TransitionAdded(trackId, t) else if (old != t) out += Change.TransitionUpdated(trackId, old, t)
            }
        }
    }
}

internal object ChangeApplier {
    fun apply(timeline: Timeline, set: ChangeSet): Timeline {
        if (set.isEmpty) return timeline
        val video = timeline.videoTracks.toMutableList()
        val audio = timeline.audioTracks.toMutableList()
        val markers = timeline.markers.toMutableList()

        fun listOf(kind: TrackKind) = if (kind == TrackKind.VIDEO) video else audio
        fun locate(trackId: String): Pair<MutableList<Track>, Int> {
            val vi = video.indexOfFirst { it.id == trackId }
            if (vi >= 0) return video to vi
            val ai = audio.indexOfFirst { it.id == trackId }
            check(ai >= 0) { "unknown track $trackId" }
            return audio to ai
        }
        fun update(trackId: String, f: (Track) -> Track) {
            val (list, i) = locate(trackId)
            list[i] = f(list[i])
        }

        for (c in set.changes) when (c) {
            is Change.TrackAdded -> {
                val list = listOf(c.track.kind)
                check(list.none { it.id == c.track.id }) { "track ${c.track.id} already exists" }
                list.add(c.index.coerceIn(0, list.size), c.track)
            }
            is Change.TrackRemoved -> {
                val (list, i) = locate(c.track.id)
                list.removeAt(i)
            }
            is Change.TrackHeaderUpdated -> update(c.after.id) { it.copy(
                name = c.after.name, role = c.after.role, locked = c.after.locked, muted = c.after.muted,
                solo = c.after.solo, syncLock = c.after.syncLock, gainDb = c.after.gainDb,
                spanPolicy = c.after.spanPolicy, collapsed = c.after.collapsed,
            ) }
            is Change.ClipAdded -> update(c.trackId) { t ->
                check(t.clips.none { it.id == c.clip.id }) { "clip ${c.clip.id} already on ${t.id}" }
                t.copy(clips = t.clips + c.clip)
            }
            is Change.ClipRemoved -> update(c.trackId) { t ->
                check(t.clips.any { it.id == c.clip.id }) { "clip ${c.clip.id} not on ${t.id}" }
                t.copy(clips = t.clips.filter { it.id != c.clip.id })
            }
            is Change.ClipUpdated -> update(c.trackId) { t ->
                check(t.clips.any { it.id == c.before.id }) { "clip ${c.before.id} not on ${t.id}" }
                t.copy(clips = t.clips.map { if (it.id == c.before.id) c.after else it })
            }
            is Change.TransitionAdded -> update(c.trackId) { t -> t.copy(transitions = t.transitions + c.transition) }
            is Change.TransitionRemoved -> update(c.trackId) { t -> t.copy(transitions = t.transitions.filter { it.id != c.transition.id }) }
            is Change.TransitionUpdated -> update(c.trackId) { t ->
                t.copy(transitions = t.transitions.map { if (it.id == c.before.id) c.after else it })
            }
            is Change.MarkerAdded -> markers += c.marker
            is Change.MarkerRemoved -> markers.removeAll { it.id == c.marker.id }
            is Change.MarkerUpdated -> markers.replaceAll { if (it.id == c.before.id) c.after else it }
        }
        return Canonical.timeline(Timeline(video, audio, markers))
    }
}

/** Canonical ordering so that structurally equal timelines compare equal. */
internal object Canonical {
    fun timeline(t: Timeline): Timeline = Timeline(
        t.videoTracks.map(::track), t.audioTracks.map(::track), t.markers.sortedWith(compareBy({ it.timeUs }, { it.id })),
    )

    fun track(t: Track): Track {
        val clips = t.clips.sortedBy { it.timelineStartUs }
        val startOf = clips.associate { it.id to it.timelineStartUs }
        val transitions = t.transitions.sortedWith(compareBy({ startOf[it.leftClipId] ?: Long.MAX_VALUE }, { it.id }))
        return if (clips == t.clips && transitions == t.transitions) t else t.copy(clips = clips, transitions = transitions)
    }
}
