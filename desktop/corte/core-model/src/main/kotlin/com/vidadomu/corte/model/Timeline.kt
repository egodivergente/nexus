package com.vidadomu.corte.model

import kotlinx.serialization.Serializable

@Serializable
data class Marker(val id: String, val timeUs: Long, val label: String = "", val color: Int = 0xFFE0A030.toInt())

/**
 * videoTracks[0] is V1 (bottom of the picture stack), audioTracks[0] is A1.
 * Any number of tracks is allowed; the practical limit is decoder capacity of the device.
 */
@Serializable
data class Timeline(
    val videoTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val markers: List<Marker> = emptyList(),
) {
    val allTracks: List<Track> get() = videoTracks + audioTracks

    val durationUs: Long get() = allTracks.maxOfOrNull { it.endUs } ?: 0L

    fun track(id: String): Track? = videoTracks.firstOrNull { it.id == id } ?: audioTracks.firstOrNull { it.id == id }

    fun trackOfClip(clipId: String): Track? = allTracks.firstOrNull { t -> t.clips.any { it.id == clipId } }

    fun clip(clipId: String): Clip? = trackOfClip(clipId)?.clip(clipId)

    fun clipsWithLink(linkId: String): List<Clip> = allTracks.flatMap { t -> t.clips.filter { it.linkId == linkId } }

    fun clipsInSyncGroup(groupId: String): List<Clip> = allTracks.flatMap { t -> t.clips.filter { it.syncGroupId == groupId } }

    fun replaceTrack(track: Track): Timeline = when (track.kind) {
        TrackKind.VIDEO -> copy(videoTracks = videoTracks.map { if (it.id == track.id) track else it })
        TrackKind.AUDIO -> copy(audioTracks = audioTracks.map { if (it.id == track.id) track else it })
    }

    fun usesMedia(mediaId: String): Boolean = allTracks.any { t -> t.clips.any { it.mediaId == mediaId } }
}
