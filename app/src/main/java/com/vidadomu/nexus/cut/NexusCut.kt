package com.vidadomu.nexus.cut

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.cut.command.CoreCapabilities
import com.vidadomu.nexus.cut.command.CutFailure
import com.vidadomu.nexus.cut.command.CutLibrary
import com.vidadomu.nexus.cut.command.CutRegistry
import com.vidadomu.nexus.cut.command.CutRenderer
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.DeviceCapabilities
import com.vidadomu.nexus.cut.command.ErrorCode
import com.vidadomu.nexus.cut.command.ExportOverrides
import com.vidadomu.nexus.cut.command.LibraryMedia
import com.vidadomu.nexus.cut.command.cutFail
import com.vidadomu.nexus.cut.command.clipExportRanges
import com.vidadomu.nexus.cut.export.ExportJob
import com.vidadomu.nexus.cut.export.ExportManager
import com.vidadomu.nexus.cut.export.ExportMath
import com.vidadomu.nexus.cut.export.ExportService
import com.vidadomu.nexus.cut.export.ExportState
import com.vidadomu.nexus.cut.media.MediaImportException
import com.vidadomu.nexus.cut.media.MediaProbe
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Nexus Cut dentro de la app: un solo espacio de trabajo por proceso (montajes en `files/cut/projects`), las
 * capacidades del dispositivo y la navegación al editor. El editor y el agente piden aquí la misma sesión.
 */
@UnstableApi
object NexusCut {
    private const val TAG = "NexusCut"
    @Volatile private var instance: CutWorkspace? = null

    fun workspace(app: NexusApplication): CutWorkspace = instance ?: synchronized(this) {
        instance ?: run {
            val registry = CutRegistry { setOf(DeviceCapabilities.LIBRARY, DeviceCapabilities.EXPORT) }
                .register(CoreCapabilities.all() + DeviceCapabilities.all(Library(app), Renderer(app)))
            CutWorkspace(ProjectStore(File(app.filesDir, "cut/projects").apply { mkdirs() }), registry)
        }.also { instance = it }
    }

    // ------------------------------------------------------------------ navegación

    private val opens = Channel<String>(Channel.BUFFERED)
    private val imports = CutImports()
    /** Montajes que hay que abrir en pantalla (los recoge MainActivity). */
    val openRequests = opens.receiveAsFlow()

    fun open(projectId: String) { opens.trySend(projectId) }

    /**
     * Abre el montaje que corresponde a un proyecto de Nexus (y escena, si la hay) con estos archivos dentro.
     * Reutiliza el montaje existente en vez de crear otro, y un archivo que ya estaba no se duplica.
     * Devuelve el id del montaje o lanza [CutFailure] con un motivo para el usuario.
     */
    suspend fun openWith(app: NexusApplication, assetIds: List<String>, nexusProjectId: String?, sceneId: String? = null, name: String? = null): String {
        val ws = workspace(app)
        val session = imports.openWith(ws, assetIds) { montageFor(app, nexusProjectId, sceneId, name) }
        open(session.projectId)
        return session.projectId
    }

    /** El montaje de ese proyecto y escena (el más reciente) o uno nuevo con un nombre reconocible. */
    suspend fun montageFor(app: NexusApplication, nexusProjectId: String?, sceneId: String?, name: String? = null): CutSession {
        val ws = workspace(app)
        val existing = ws.list().firstOrNull { it.error == null && it.nexusProjectId == nexusProjectId && it.sceneId == sceneId }
        if (existing != null) return ws.session(existing.id)
        val index = AssistantRuntime.get(app).assets
        val title = name ?: listOfNotNull(
            nexusProjectId?.let { index.findProject(it)?.name },
            sceneId?.let { id -> index.snapshot().scenes.firstOrNull { it.id == id }?.let { s -> s.title ?: "Escena ${s.code}" } },
        ).joinToString(" · ").ifBlank { "Montaje" }
        return ws.create(title, nexusProjectId, sceneId)
    }

    // ------------------------------------------------------------------ Biblioteca

    private class Library(private val app: NexusApplication) : CutLibrary {
        override suspend fun probe(assetId: String): LibraryMedia {
            val asset = AssistantRuntime.get(app).assets.asset(assetId) ?: cutFail(ErrorCode.NOT_FOUND, "No existe el archivo «$assetId» en la Biblioteca.")
            if (asset.type == AssetType.OTHER) cutFail(ErrorCode.INVALID_ARGUMENT, "«${asset.displayName}» no es un vídeo, un audio ni una imagen.")
            val probed = withContext(Dispatchers.IO) {
                try {
                    MediaProbe.probe(app, Uri.parse(asset.uri), "", asset.mimeType)
                } catch (e: MediaImportException) {
                    cutFail(ErrorCode.INVALID_ARGUMENT, e.message ?: "«${asset.displayName}» no se puede usar en el montaje.")
                } catch (e: SecurityException) {
                    cutFail(ErrorCode.INVALID_ARGUMENT, "Nexus no tiene permiso para leer «${asset.displayName}».")
                }
            }
            return LibraryMedia(probed.ref.copy(displayName = asset.displayName, generationId = asset.generationId), asset.sceneId, asset.shotId, probed.warnings)
        }
    }

    // ------------------------------------------------------------------ exportación

    private class Renderer(private val app: NexusApplication) : CutRenderer {
        override suspend fun start(session: CutSession, overrides: ExportOverrides): JsonObject {
            val project = session.state.value.project
            if (project.timeline.durationUs <= 0) cutFail(ErrorCode.PRECONDITION_FAILED, "La línea de tiempo está vacía: no hay nada que exportar.")
            if (ExportManager.state.value is ExportState.Running) cutFail(ErrorCode.PRECONDITION_FAILED, "Ya hay una exportación en curso.")
            val settings = project.export.copy(resolution = overrides.resolution ?: project.export.resolution, codec = overrides.codec ?: project.export.codec)
            val ranges = clipExportRanges(project, overrides.clipIds, overrides.eachClip)
            val offline = withContext(Dispatchers.IO) { project.media.filterNot { MediaProbe.isReachable(app, it.uri) }.map { it.id }.toSet() }
            val usedOffline = offline.filter { project.timeline.usesMedia(it) }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
            val slug = project.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(' ', '_').take(48).ifBlank { "montaje" }
            val token = java.util.UUID.randomUUID().toString().take(8)
            val windows = if (ranges.isEmpty()) listOf(null) else ranges
            val jobs = windows.mapIndexed { index, range ->
                val name = if (range == null) project.name else "${project.name} · Clip ${index + 1}"
                val snapshot = project.copy(name = name)
                val plan = ExportMath.plan(project.settings, settings, range?.durationUs ?: project.timeline.durationUs)
                val suffix = if (range == null) "" else "-clip-${(index + 1).toString().padStart(2, '0')}"
                val destination = File(app.filesDir, "cut/exports/$slug-$stamp-$token$suffix.mp4")
                ExportJob(snapshot, plan, destination, project.media.map { it.id }.toSet() - offline,
                    fromUs = range?.startUs ?: 0, untilUs = range?.endUs ?: project.timeline.durationUs) { file ->
                    register(app, file, snapshot, plan.durationUs)
                }
            }
            if (!ExportManager.enqueueBatch(jobs)) cutFail(ErrorCode.PRECONDITION_FAILED, "Ya hay una exportación en curso.")
            try {
                ExportService.start(app)
            } catch (e: IllegalStateException) {
                // Android no deja iniciar un servicio en primer plano con la app en segundo plano.
                ExportManager.take()
                ExportManager.update(ExportState.Failed(project.id, "Android no ha permitido exportar con Nexus en segundo plano. Abre Nexus y vuelve a exportar."))
                cutFail(ErrorCode.UNAVAILABLE, "Android no ha permitido exportar con Nexus en segundo plano. Abre Nexus y vuelve a exportar.")
            }
            return buildJsonObject {
                val plan = jobs.first().plan
                put("state", "running"); put("progress", 0); put("width", plan.width); put("height", plan.height)
                put("codec", settings.codec.label); put("estimated_bytes", jobs.sumOf { it.plan.estimatedBytes }); put("file_count", jobs.size)
                put("project_revision", project.revision)
                if (usedOffline.isNotEmpty()) put("warning", "${usedOffline.size} medio(s) no están disponibles: saldrán como negro o silencio.")
            }
        }

        override fun status(): JsonObject = when (val s = ExportManager.state.value) {
            ExportState.Idle -> buildJsonObject { put("state", "idle") }
            is ExportState.Running -> buildJsonObject { put("state", "running"); put("project_id", s.projectId); put("progress", s.progress); put("stage", s.stage); put("item", s.item); put("file_count", s.total); put("asset_ids", JsonArray(s.assetIds.map { JsonPrimitive(it) })) }
            is ExportState.Done -> buildJsonObject {
                put("state", "done"); put("project_id", s.projectId); put("asset_id", s.assetId); put("bytes", s.bytes); put("duration_us", s.durationUs); put("detail", s.detail)
                put("asset_ids", JsonArray(s.assetIds.map { JsonPrimitive(it) })); put("file_count", s.assetIds.size)
            }
            is ExportState.Failed -> buildJsonObject { put("state", "failed"); s.projectId?.let { put("project_id", it) }; put("error", s.message); put("asset_ids", JsonArray(s.assetIds.map { JsonPrimitive(it) })) }
            is ExportState.Cancelled -> buildJsonObject { put("state", "cancelled"); s.projectId?.let { put("project_id", it) }; put("asset_ids", JsonArray(s.assetIds.map { JsonPrimitive(it) })) }
        }

        override fun cancel(): Boolean {
            if (ExportManager.state.value !is ExportState.Running) return false
            ExportService.cancel(app); return true
        }
    }

    /** El vídeo exportado entra en la Biblioteca como final del proyecto y de la escena del montaje. */
    internal suspend fun register(app: NexusApplication, file: File, project: com.vidadomu.nexus.cut.model.Project, durationUs: Long): String {
        val (w, h) = withContext(Dispatchers.IO) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.path)
                (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()) to (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull())
            } catch (e: RuntimeException) {
                Log.w(TAG, "metadata of export", e); null to null
            } finally { mmr.release() }
        }
        val saved = AssistantRuntime.get(app).assets.addAsset(Asset(
            id = "", uri = Uri.fromFile(file).toString(), type = AssetType.VIDEO, role = AssetRole.FINAL, source = AssetSource.GENERATED,
            displayName = "${project.name.take(100)}.mp4", mimeType = "video/mp4", width = w, height = h, durationSec = durationUs / 1e6,
            projectId = project.nexusProjectId, sceneId = project.sceneId, tags = listOf("nexus-cut"), createdAt = System.currentTimeMillis(),
        ))
        return saved.id
    }
}
