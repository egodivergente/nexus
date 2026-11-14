package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.Project

data class ClipExportRange(val clipId: String, val startUs: Long, val endUs: Long) {
    val durationUs: Long get() = endUs - startUs
}

/** Resolve immutable timeline windows. Linked video/audio IDs identify a single visual segment. */
fun clipExportRanges(project: Project, ids: Set<String>, eachClip: Boolean): List<ClipExportRange> {
    if (!eachClip && ids.isEmpty()) return emptyList() // full montage, existing contract
    if (eachClip && ids.isNotEmpty()) cutFail(ErrorCode.INVALID_ARGUMENT, "Usa each_clip o clip_ids, no ambos.")
    val all = project.timeline.allTracks.flatMap { it.clips }
    val visual = project.timeline.videoTracks.flatMap { it.clips }.filter { it.kind == ClipKind.VIDEO || it.kind == ClipKind.IMAGE }
    val requested = if (eachClip) visual else ids.map { id ->
        val clip = all.firstOrNull { it.id == id } ?: cutFail(ErrorCode.NOT_FOUND, "No existe el clip $id.")
        if (clip in visual) clip else visual.firstOrNull { clip.linkId != null && it.linkId == clip.linkId }
            ?: cutFail(ErrorCode.INVALID_ARGUMENT, "El clip $id no tiene un segmento visual vinculado para exportar a MP4.")
    }
    if (requested.isEmpty()) cutFail(ErrorCode.PRECONDITION_FAILED, "No hay clips de vídeo o imagen para exportar.")
    return requested.distinctBy { it.id }.sortedBy { it.timelineStartUs }.map { ClipExportRange(it.id, it.timelineStartUs, it.endUs) }
}
