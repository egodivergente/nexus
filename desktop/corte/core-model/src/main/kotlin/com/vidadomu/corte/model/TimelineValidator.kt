package com.vidadomu.corte.model

/**
 * Structural invariants of a timeline. The edit engine checks these after every operation (an
 * operation that would break one is a bug, never a silent state). Storage checks them on load.
 */
object TimelineValidator {
    /** Tolerance for source bounds: container durations are rounded by muxers. */
    const val SOURCE_TOLERANCE_US = 50_000L

    fun violations(timeline: Timeline, mediaLookup: (String) -> MediaRef? = { null }): List<String> {
        val out = mutableListOf<String>()
        val trackIds = HashSet<String>()
        val clipIds = HashSet<String>()
        for (track in timeline.allTracks) {
            if (!trackIds.add(track.id)) out += "duplicate track id ${track.id}"
            if (track.role.kind != track.kind) out += "track ${track.id}: role ${track.role} does not match kind ${track.kind}"
            var prev: Clip? = null
            for (clip in track.clips) {
                if (!clipIds.add(clip.id)) out += "duplicate clip id ${clip.id}"
                if (clip.kind.trackKind != track.kind) out += "clip ${clip.id} (${clip.kind}) on ${track.kind} track ${track.id}"
                if (clip.timelineStartUs < 0) out += "clip ${clip.id} starts before 0"
                if (prev != null) {
                    if (clip.timelineStartUs < prev.timelineStartUs) out += "track ${track.id}: clips not sorted (${prev.id}, ${clip.id})"
                    if (clip.timelineStartUs < prev.endUs) out += "track ${track.id}: ${prev.id} overlaps ${clip.id}"
                }
                val media = mediaLookup(clip.mediaId)
                val mediaDuration = media?.durationUs
                if (media != null && mediaDuration != null && clip.sourceOutUs > mediaDuration + SOURCE_TOLERANCE_US) {
                    out += "clip ${clip.id} reads past end of media (${clip.sourceOutUs} > $mediaDuration)"
                }
                prev = clip
            }
            for (tr in track.transitions) {
                val l = track.clip(tr.leftClipId)
                val r = track.clip(tr.rightClipId)
                if (l == null || r == null) {
                    out += "transition ${tr.id} references missing clip"
                    continue
                }
                if (l.endUs != r.timelineStartUs) out += "transition ${tr.id}: clips are not adjacent"
                if (tr.kind.trackKind != track.kind) out += "transition ${tr.id}: kind ${tr.kind} on ${track.kind} track"
                if (tr.beforeCutUs > l.durationUs || tr.afterCutUs > r.durationUs) out += "transition ${tr.id} longer than its clips"
            }
            val pairs = track.transitions.map { it.leftClipId to it.rightClipId }
            if (pairs.size != pairs.toSet().size) out += "track ${track.id}: duplicate transitions on one cut"
        }
        return out
    }
}
