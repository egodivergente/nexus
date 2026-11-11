package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.model.IdGenerator
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.model.ProjectSettings
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.model.Track
import com.vidadomu.nexus.cut.model.TrackKind
import com.vidadomu.nexus.cut.model.TrackRole

object CutProjects {
    /**
     * Montaje nuevo con la disposición de partida: imagen principal, una capa encima, diálogo y música.
     * [nexusProjectId] y [sceneId] lo atan desde el primer momento al proyecto audiovisual de Nexus.
     */
    fun create(
        name: String,
        nowMs: Long,
        ids: IdGenerator = IdGenerator.RANDOM,
        settings: ProjectSettings = ProjectSettings(),
        nexusProjectId: String? = null,
        sceneId: String? = null,
    ): Project = Project(
        id = ids.next("cut"),
        name = name.trim().ifEmpty { "Montaje" }.take(120),
        settings = settings,
        timeline = Timeline(
            videoTracks = listOf(
                Track("V1", TrackKind.VIDEO, "V1", TrackRole.PICTURE),
                Track("V2", TrackKind.VIDEO, "V2", TrackRole.OVERLAY),
            ),
            audioTracks = listOf(
                Track("A1", TrackKind.AUDIO, "A1 Diálogo", TrackRole.DIALOGUE),
                Track("A2", TrackKind.AUDIO, "A2 Música", TrackRole.MUSIC),
            ),
        ),
        createdAtMs = nowMs,
        modifiedAtMs = nowMs,
        nexusProjectId = nexusProjectId,
        sceneId = sceneId,
    )
}
