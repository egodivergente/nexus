package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.Marker
import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.Track
import com.vidadomu.corte.model.TrackKind
import com.vidadomu.corte.model.Transition

/** Mutable scratch copy used inside one edit. Never escapes the engine. */
internal class WorkingTrack(track: Track) {
    var header: Track = ChangeSet.header(track)
    val clips: MutableList<Clip> = track.clips.toMutableList()
    val transitions: MutableList<Transition> = track.transitions.toMutableList()

    val id: String get() = header.id
    val kind: TrackKind get() = header.kind

    fun sort() = clips.sortBy { it.timelineStartUs }
    fun indexOf(clipId: String) = clips.indexOfFirst { it.id == clipId }
    fun clip(clipId: String): Clip? = clips.firstOrNull { it.id == clipId }
    fun replace(clip: Clip) {
        val i = indexOf(clip.id)
        check(i >= 0) { "clip ${clip.id} not on track $id" }
        clips[i] = clip
    }
    fun remove(clipId: String): Clip? {
        val i = indexOf(clipId)
        return if (i >= 0) clips.removeAt(i) else null
    }

    fun build(): Track = Canonical.track(header.copy(clips = clips.toList(), transitions = transitions.toList()))
}

internal class WorkingTimeline(val base: Timeline, val ctx: EditContext) {
    val video: MutableList<WorkingTrack> = base.videoTracks.map(::WorkingTrack).toMutableList()
    val audio: MutableList<WorkingTrack> = base.audioTracks.map(::WorkingTrack).toMutableList()
    val markers: MutableList<Marker> = base.markers.toMutableList()

    val warnings = LinkedHashSet<String>()
    val collateral = ArrayList<Collateral>()
    val created = ArrayList<String>()

    /** newClipId -> clip it was split from; used to verify that link groups move rigidly. */
    val parentOf = HashMap<String, String>()

    /** (old linkId, split time) -> linkId shared by the right-hand halves. */
    private val splitLinkIds = HashMap<Pair<String, Long>, String>()

    val tracks: List<WorkingTrack> get() = video + audio

    fun track(id: String): WorkingTrack = tracks.firstOrNull { it.id == id } ?: reject("Pista inexistente ($id)")

    fun locate(clipId: String): Pair<WorkingTrack, Clip>? {
        for (t in tracks) {
            val c = t.clip(clipId)
            if (c != null) return t to c
        }
        return null
    }

    fun require(clipId: String): Pair<WorkingTrack, Clip> = locate(clipId) ?: reject("Clip inexistente ($clipId)")

    fun newId(prefix: String): String = ctx.ids.next(prefix).also { created += it }

    fun linkIdForRightHalf(linkId: String, atUs: Long): String =
        splitLinkIds.getOrPut(linkId to atUs) { ctx.ids.next("link") }

    fun build(): Timeline = Canonical.timeline(Timeline(video.map { it.build() }, audio.map { it.build() }, markers.toList()))
}
