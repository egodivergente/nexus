package com.vidadomu.nexus.sync

import android.content.SharedPreferences
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.storage.SharedMontage
import com.vidadomu.nexus.ui.cut.CutViewPrefs
import java.io.File
import java.net.URI
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One shared main montage per Nexus project. Local scene montages remain independently editable. */
internal class CutMontageSync(
    private val workspace: CutWorkspace,
    private val prefs: SharedPreferences,
    private val view: CutViewPrefs,
    private val blobs: File,
) {
    private val captured = mutableMapOf<String, Project?>()
    private val localIds = mutableMapOf<String, String>()
    private var ambiguous = emptySet<String>()

    suspend fun capture(selected: Set<String>, previous: Map<String, JsonObject>): Map<String, JsonObject> {
        val grouped = workspace.list().filter { it.nexusProjectId != null && it.sceneId == null }
            .groupBy { requireNotNull(it.nexusProjectId) }
        ambiguous = grouped.filter { (projectId, rows) ->
            rows.size > 1 && rows.none { it.id == prefs.getString("cut_montage.$projectId", null) }
        }.keys
        return buildMap {
            for (projectId in selected - ambiguous) {
                val rows = grouped[projectId].orEmpty()
                val preferred = prefs.getString("cut_montage.$projectId", null)
                val id = rows.firstOrNull { it.id == preferred }?.id ?: rows.singleOrNull()?.id
                    ?: SharedMontage.localId(projectId)
                localIds[projectId] = id
                val session = workspace.opened(id)?.state?.value
                val project = session?.project ?: rows.firstOrNull { it.id == id }?.let { workspace.store.load(id).project }
                captured[projectId] = project
                if (project != null && (project.revision > 0 || project.timeline.durationUs > 0)) {
                    val time = session?.view?.playheadUs ?: view.playhead(id)
                    put(projectId, SharedMontage.encode(project, time, previous[projectId]))
                }
            }
        }
    }

    suspend fun apply(rows: Map<String, JsonObject>) {
        // Validate the batch before touching the store. A missing blob or a foreign local path
        // must never replace an already editable montage with an offline version.
        val staged = rows.mapNotNull { (key, row) ->
            if (!key.startsWith("montages:")) return@mapNotNull null
            val projectId = key.substringAfter(':')
            val id = localIds[projectId] ?: return@mapNotNull null
            val previous = captured[projectId]
            val incoming = SharedMontage.decode(projectId, row.getValue("document").jsonObject, id)
            val ownSources = previous?.media.orEmpty().associate { it.id to it.uri }
            require(incoming.project.media.all { media ->
                ownSources[media.id] == media.uri || runCatching {
                    val path = File(URI(media.uri)).canonicalFile
                    path.parentFile == blobs.canonicalFile && path.isFile
                }.getOrDefault(false)
            }) { "Faltan originales del montaje. Tu edición local se conserva." }
            Triple(id, previous, incoming)
        }
        for ((id, previous, incoming) in staged) {
            workspace.applySynced(previous, incoming.project, incoming.playheadUs)
            view.saveProjectView(id, pxPerSecond = null, playheadUs = incoming.playheadUs)
        }
    }
}
