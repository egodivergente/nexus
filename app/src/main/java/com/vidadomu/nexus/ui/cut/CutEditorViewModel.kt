package com.vidadomu.nexus.ui.cut

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.cut.NexusCut
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutState
import com.vidadomu.nexus.cut.command.ErrorCode
import com.vidadomu.nexus.cut.command.Op
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.command.Request
import com.vidadomu.nexus.cut.engine.PreviewController
import com.vidadomu.nexus.cut.engine.PreviewLevel
import com.vidadomu.nexus.cut.export.ExportManager
import com.vidadomu.nexus.cut.export.ExportState
import com.vidadomu.nexus.cut.media.MediaProbe
import com.vidadomu.nexus.cut.media.ThumbnailRepository
import com.vidadomu.nexus.cut.media.WaveformRepository
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.timeline.Edge
import com.vidadomu.nexus.cut.timeline.EditContext
import com.vidadomu.nexus.cut.timeline.EditMode
import com.vidadomu.nexus.cut.timeline.EditResult
import com.vidadomu.nexus.cut.timeline.Snapping
import com.vidadomu.nexus.cut.timeline.TimelineEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un aviso para el usuario: los errores del motor se leen tal cual, sin códigos. */
data class CutMessage(val text: String, val error: Boolean = false)

/**
 * El editor sobre la sesión compartida del montaje. No guarda una copia del proyecto: lo que pinta sale
 * siempre de [CutSession.state], que también cambia cuando edita el agente. Los gestos se previsualizan en
 * local mientras duran y se confirman como una sola operación del bus al soltar.
 */
@UnstableApi
class CutEditorViewModel(app: Application, val projectId: String) : AndroidViewModel(app) {
    private val nexus = app as NexusApplication
    private val workspace = NexusCut.workspace(nexus)

    private val _session = MutableStateFlow<CutSession?>(null)
    private val _state = MutableStateFlow<CutState?>(null)
    val state: StateFlow<CutState?> = _state.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    val preview = PreviewController(app, viewModelScope)
    val thumbnails = ThumbnailRepository(app, viewModelScope)
    private val waveformRepo = WaveformRepository(app, viewModelScope)
    val waveforms = waveformRepo.waveforms

    private val _offline = MutableStateFlow<Set<String>>(emptySet())
    val offline: StateFlow<Set<String>> = _offline.asStateFlow()

    /** Línea de tiempo provisional mientras dura un arrastre; null el resto del tiempo. */
    private val _dragTimeline = MutableStateFlow<Timeline?>(null)
    val dragTimeline: StateFlow<Timeline?> = _dragTimeline.asStateFlow()
    private val _snapLineUs = MutableStateFlow<Long?>(null)
    val snapLineUs: StateFlow<Long?> = _snapLineUs.asStateFlow()

    /** Ripple: las ediciones arrastran lo posterior. Imán: los bordes se pegan a cortes, marcadores y cabezal. */
    val ripple = MutableStateFlow(true)
    val snapping = MutableStateFlow(true)
    val previewLevel = MutableStateFlow(PreviewLevel.AUTO)

    private val _messages = MutableSharedFlow<CutMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<CutMessage> = _messages.asSharedFlow()

    val export: StateFlow<ExportState> = ExportManager.state

    private var lastPushedPlayhead = -1L

    init {
        viewModelScope.launch {
            val s = try {
                workspace.session(projectId)
            } catch (e: Exception) {
                _loadError.value = e.message ?: "No se pudo abrir el montaje."; return@launch
            }
            workspace.setActive(projectId)
            _session.value = s
            launch { s.state.collect { st -> onState(st) } }
            // El cabezal de la reproducción manda mientras se ve: se publica en la sesión para que el agente sepa dónde está «aquí».
            launch { preview.positionUs.collect { us -> lastPushedPlayhead = us; s.setPlayhead(us) } }
            launch { preview.playing.collect { s.setPlaying(it) } }
            launch {
                s.state.map { it.project.media }.distinctUntilChanged().collect { media ->
                    media.forEach { waveformRepo.request(it) }
                    _offline.value = withContext(Dispatchers.IO) { media.filterNot { MediaProbe.isReachable(nexus, it.uri) }.map { it.id }.toSet() }
                }
            }
        }
    }

    private fun onState(st: CutState) {
        _state.value = st
        preview.update(st.project, _offline.value, previewLevel.value)
        // Si otro (el agente, un deshacer que acota) movió el cabezal, la vista lo sigue.
        if (st.view.playheadUs != lastPushedPlayhead && !st.view.playing) {
            lastPushedPlayhead = st.view.playheadUs
            preview.seekTo(st.view.playheadUs)
        }
    }

    fun setPreviewLevel(level: PreviewLevel) {
        previewLevel.value = level
        _state.value?.let { preview.update(it.project, _offline.value, level) }
    }

    fun refreshOffline() {
        val st = _state.value ?: return
        viewModelScope.launch {
            _offline.value = withContext(Dispatchers.IO) { st.project.media.filterNot { MediaProbe.isReachable(nexus, it.uri) }.map { it.id }.toSet() }
            preview.update(st.project, _offline.value, previewLevel.value)
        }
    }

    // ------------------------------------------------------------------ operaciones

    /** Ejecuta en el bus como usuario (o lápiz). Devuelve el resultado; los fallos se avisan solos. */
    fun run(id: String, vararg args: Pair<String, Any?>, origin: Origin = Origin.USER, label: String? = null, quiet: Boolean = false, then: (Outcome.Done) -> Unit = {}) {
        val s = _session.value ?: return
        viewModelScope.launch {
            when (val out = s.execute(Request(listOf(Op(id, CutSession.argsOf(*args))), origin, label))) {
                is Outcome.Done -> {
                    if (!quiet) out.warnings.firstOrNull()?.let { _messages.tryEmit(CutMessage(it)) }
                    then(out)
                }
                is Outcome.Failed -> _messages.tryEmit(CutMessage(out.error.message, error = true))
            }
        }
    }

    fun undo() { val s = _session.value ?: return; viewModelScope.launch { (s.undo(Origin.USER) as? Outcome.Failed)?.let { _messages.tryEmit(CutMessage(it.error.message, true)) } } }
    fun redo() { val s = _session.value ?: return; viewModelScope.launch { (s.redo(Origin.USER) as? Outcome.Failed)?.let { _messages.tryEmit(CutMessage(it.error.message, true)) } } }

    fun select(clipId: String?, additive: Boolean) {
        val current = _state.value?.view?.selection.orEmpty()
        when {
            clipId == null -> if (current.isNotEmpty()) run("timeline.select", "mode" to "clear")
            additive -> run("timeline.select", "clip_ids" to listOf(clipId), "mode" to if (clipId in current) "remove" else "add")
            else -> run("timeline.select", "clip_ids" to listOf(clipId))
        }
    }

    fun split() = run("clip.split", "at_us" to preview.positionUs.value)
    fun deleteSelection() = run("clip.delete", "ripple" to ripple.value)
    fun duplicateSelection() = run("clip.duplicate")
    fun unlinkSelection() = run("clip.unlink")
    fun setSpeed(clipId: String, speed: String) = run("clip.set_speed", "clip_id" to clipId, "speed" to speed, "ripple" to ripple.value)
    fun setVolume(clipIds: Set<String>, gainDb: Double) = run("audio.set", "clip_ids" to clipIds.toList(), "gain_db" to gainDb)
    fun setMuted(clipIds: Set<String>, muted: Boolean) = run("audio.set", "clip_ids" to clipIds.toList(), "muted" to muted)
    fun addTitle(text: String) = run("subtitle.add", "text" to text, "at_us" to preview.positionUs.value)
    fun updateTitle(clipId: String, text: String) = run("subtitle.update", "clip_ids" to listOf(clipId), "text" to text)
    fun rename(name: String) = run("project.rename", "name" to name)
    fun setAspect(aspect: String) = run("project.set_format", "aspect" to aspect)
    fun toggleTrack(trackId: String, locked: Boolean? = null, muted: Boolean? = null) = run("timeline.update_track", "track_id" to trackId, "locked" to locked, "muted" to muted)

    /** Archivos elegidos en la Biblioteca: entran donde está el cabezal si hay algo debajo, si no al final. */
    fun importAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val st = _state.value ?: return
        val at = preview.positionUs.value.takeIf { it > 0 && it < st.project.timeline.durationUs }
        run("media.import", "asset_ids" to assetIds, "at_us" to at, label = "Añadir desde la Biblioteca") { out ->
            val n = (out.results.single()["clip_ids"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
            if (n > 0) _messages.tryEmit(CutMessage(if (assetIds.size == 1) "Añadido al montaje." else "${assetIds.size} archivos añadidos al montaje."))
        }
    }

    fun export() = run("render.export")
    fun cancelExport() = run("render.cancel", quiet = true)

    // ------------------------------------------------------------------ arrastres

    private var dragBase: Timeline? = null
    private var dragRevision = 0L
    private var snapTargets: List<Snapping.Target> = emptyList()
    private var pendingOp: Op? = null
    private var dragReject: String? = null

    private fun ctx(): EditContext? = _state.value?.project?.let { p -> EditContext(media = { id -> p.media(id) }) }

    fun beginDrag(exclude: Set<String>) {
        val st = _state.value ?: return
        dragBase = st.project.timeline
        dragRevision = st.revision
        pendingOp = null; dragReject = null
        snapTargets = if (snapping.value) Snapping.targets(st.project.timeline, preview.positionUs.value, exclude) else emptyList()
    }

    private fun show(r: EditResult, op: Op, snapUs: Long?) = when (r) {
        is EditResult.Applied -> { _dragTimeline.value = r.timeline; _snapLineUs.value = snapUs; pendingOp = op; dragReject = null }
        is EditResult.Rejected -> { dragReject = r.reason; _snapLineUs.value = null; pendingOp = null }
    }

    fun dragTrim(clipId: String, edge: Edge, rawDeltaUs: Long, snapThresholdUs: Long) {
        val base = dragBase ?: return; val c = ctx() ?: return
        val clip = base.clip(clipId) ?: return
        val edgeAt = if (edge == Edge.HEAD) clip.timelineStartUs else clip.endUs
        val snap = Snapping.nearest(snapTargets, edgeAt + rawDeltaUs, snapThresholdUs)
        val delta = snap?.let { it.timeUs - edgeAt } ?: rawDeltaUs
        if (delta == 0L) { _dragTimeline.value = base; _snapLineUs.value = snap?.timeUs; pendingOp = null; return }
        show(TimelineEditor.trim(base, c, clipId, edge, delta, ripple.value),
            Op("clip.trim", CutSession.argsOf("clip_id" to clipId, "edge" to edge.name.lowercase(), "delta_us" to delta, "ripple" to ripple.value)), snap?.timeUs)
    }

    fun dragMove(clipId: String, rawDeltaUs: Long, trackOffset: Int, snapThresholdUs: Long) {
        val base = dragBase ?: return; val c = ctx() ?: return
        val clip = base.clip(clipId) ?: return
        val (start, snap) = Snapping.snapSpan(snapTargets, clip.timelineStartUs + rawDeltaUs, clip.durationUs, snapThresholdUs)
        val delta = (start - clip.timelineStartUs).coerceAtLeast(-clip.timelineStartUs)
        if (delta == 0L && trackOffset == 0) { _dragTimeline.value = base; _snapLineUs.value = null; pendingOp = null; return }
        val ids = (_state.value?.view?.selection.orEmpty() + clipId)
        val mode = if (ripple.value) EditMode.INSERT else EditMode.OVERWRITE
        show(TimelineEditor.move(base, c, ids, clipId, delta, trackOffset, mode),
            Op("clip.move", CutSession.argsOf("clip_ids" to ids.toList(), "anchor_clip_id" to clipId, "delta_us" to delta, "track_offset" to trackOffset, "mode" to mode.name.lowercase())), snap?.timeUs)
    }

    fun dragFade(clipId: String, fadeIn: Boolean, fadeUs: Long) {
        val base = dragBase ?: return; val c = ctx() ?: return
        val clip = base.clip(clipId) ?: return
        val f = fadeUs.coerceIn(0, clip.durationUs)
        val audio = clip.kind == ClipKind.AUDIO
        val key = if (fadeIn) "fade_in_us" else "fade_out_us"
        val r = TimelineEditor.updateClips(base, c, setOf(clipId), "Fundido") { x ->
            if (audio) x.copy(audio = if (fadeIn) x.audio.copy(fadeInUs = f) else x.audio.copy(fadeOutUs = f))
            else x.copy(video = if (fadeIn) x.video.copy(fadeInUs = f) else x.video.copy(fadeOutUs = f))
        }
        show(r, Op(if (audio) "audio.set" else "effects.set_fades", CutSession.argsOf("clip_ids" to listOf(clipId), key to f)), null)
    }

    fun endDrag(commit: Boolean) {
        val op = pendingOp; val reject = dragReject; val revision = dragRevision
        dragBase = null; pendingOp = null; dragReject = null
        if (!commit || op == null) {
            _dragTimeline.value = null; _snapLineUs.value = null
            if (commit && reject != null) _messages.tryEmit(CutMessage(reject, true))
            return
        }
        val s = _session.value ?: return
        viewModelScope.launch {
            val out = s.execute(Request(listOf(op), Origin.USER, expectedRevision = revision))
            // La vista provisional se quita cuando ya está el estado confirmado: sin parpadeo.
            _dragTimeline.value = null; _snapLineUs.value = null
            if (out is Outcome.Failed) _messages.tryEmit(CutMessage(
                if (out.error.code == ErrorCode.REVISION_CONFLICT) "El montaje cambió mientras arrastrabas (lo editó el agente). Vuelve a intentarlo." else out.error.message, true))
        }
    }

    override fun onCleared() {
        preview.release()
        thumbnails.release()
        if (workspace.active.value == projectId) workspace.setActive(null)
        super.onCleared()
    }

    class Factory(private val app: Application, private val projectId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CutEditorViewModel(app, projectId) as T
    }
}
