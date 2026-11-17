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
import com.vidadomu.nexus.cut.command.CutEvent
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

/** Lo que se enseña junto al borde o clip que se arrastra: duración nueva y cuánto se ha movido. */
data class CutDragFeedback(val atUs: Long, val text: String)

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

    private val prefs = CutViewPrefs(app)
    /** Ripple: las ediciones arrastran lo posterior. Imán: los bordes se pegan a cortes, marcadores y cabezal. */
    val ripple = MutableStateFlow(prefs.ripple)
    val snapping = MutableStateFlow(prefs.snapping)
    val previewLevel = MutableStateFlow(PreviewLevel.AUTO)
    /** Selección de varios clips con toques sucesivos (en vez de sustituir la selección). */
    val multiSelect = MutableStateFlow(false)
    /** Zoom guardado de este montaje, para volver a él donde se dejó. */
    /** Se lee al crear la vista (también tras girar el móvil, cuando ya se guardó el zoom de antes). */
    /** null: este montaje aún no tiene zoom propio (el editor elige uno según su duración). */
    val savedZoom: Float? get() = prefs.zoom(projectId)
    /** null: reparto automático entre imagen y línea de tiempo. */
    val savedTimelineFraction: Float? get() = prefs.timelineFraction

    private val _dragFeedback = MutableStateFlow<CutDragFeedback?>(null)
    val dragFeedback: StateFlow<CutDragFeedback?> = _dragFeedback.asStateFlow()
    /** El arrastre ha topado con un límite (fin del medio, un vecino bloqueado): la vista no avanza. */
    private val _dragBlocked = MutableStateFlow(false)
    val dragBlocked: StateFlow<Boolean> = _dragBlocked.asStateFlow()

    private val _messages = MutableSharedFlow<CutMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<CutMessage> = _messages.asSharedFlow()

    val export: StateFlow<ExportState> = ExportManager.state
    private val _agentActivity = MutableStateFlow<String?>(null)
    val agentActivity: StateFlow<String?> = _agentActivity.asStateFlow()

    private var lastPushedPlayhead = -1L
    private var lastPushAtMs = 0L
    /** El estado más reciente de la sesión, también cuando solo cambia el cabezal (que no se repinta desde aquí). */
    private var latest: CutState? = null

    init {
        viewModelScope.launch {
            val s = try {
                workspace.session(projectId)
            } catch (e: Exception) {
                _loadError.value = e.message ?: "No se pudo abrir el montaje."; return@launch
            }
            workspace.setActive(projectId)
            _session.value = s
            // Volver donde se dejó: si la sesión ya estaba viva (el agente la usa), manda su cabezal.
            val live = s.state.value
            val resume = live.view.playheadUs.takeIf { it > 0 } ?: prefs.playhead(projectId)
            if (resume in 1 until live.project.timeline.durationUs) {
                lastPushedPlayhead = resume
                s.setPlayhead(resume)
                preview.setPositionOnly(resume)
            }
            launch { s.state.collect { st -> onState(st) } }
            launch {
                s.events.collect { event ->
                    if (event.origin == Origin.AGENT) {
                        _agentActivity.value = when (event) {
                            is CutEvent.Committed -> "Agente · ${event.label}"
                            is CutEvent.Undone -> "Agente · Deshacer: ${event.label}"
                            is CutEvent.Redone -> "Agente · Rehacer: ${event.label}"
                            is CutEvent.Rejected -> "No aplicado · ${event.error.message}"
                            is CutEvent.Proposed -> "Agente propone · ${event.label}"
                        }
                    }
                }
            }
            // El cabezal de la reproducción manda mientras se ve: se publica en la sesión para que el agente sepa
            // dónde está «aquí». Reproduciendo basta con cuatro veces por segundo; parado, cada posición.
            launch {
                preview.positionUs.collect { us ->
                    val now = android.os.SystemClock.uptimeMillis()
                    if (!preview.playing.value || now - lastPushAtMs >= 250) {
                        lastPushAtMs = now; lastPushedPlayhead = us; s.setPlayhead(us)
                    }
                }
            }
            launch { preview.playing.collect { s.setPlaying(it) } }
            launch { ExportManager.state.collect { preview.setExporting(it is ExportState.Running) } }
            launch {
                s.state.map { it.project.media }.distinctUntilChanged().collect { media ->
                    media.forEach { waveformRepo.request(it) }
                    _offline.value = withContext(Dispatchers.IO) { media.filterNot { MediaProbe.isReachable(nexus, it.uri) }.map { it.id }.toSet() }
                }
            }
        }
    }

    private fun onState(st: CutState) {
        val previous = latest
        latest = st
        // Solo el cabezal: no se repinta el editor entero (la línea de tiempo lo lee al dibujar).
        if (previous == null || previous.withoutPlayhead() != st.withoutPlayhead()) _state.value = st
        if (previous?.project !== st.project) preview.update(st.project, _offline.value, previewLevel.value)
        // Si otro (el agente, un deshacer que acota) movió el cabezal, la vista lo sigue.
        if (st.view.playheadUs != lastPushedPlayhead && !st.view.playing) {
            lastPushedPlayhead = st.view.playheadUs
            preview.seekTo(st.view.playheadUs)
        }
    }

    private fun CutState.withoutPlayhead() = if (view.playheadUs == 0L && !view.playing) this else copy(view = view.copy(playheadUs = 0L, playing = false))

    fun setRipple(on: Boolean) { ripple.value = on; prefs.ripple = on }
    fun setSnapping(on: Boolean) { snapping.value = on; prefs.snapping = on }
    fun setTimelineFraction(fraction: Float?) { prefs.timelineFraction = fraction }
    fun setMultiSelect(on: Boolean) { multiSelect.value = on }

    /** Al salir de la pantalla: se para la imagen y se guarda dónde estaba el usuario. */
    fun onBackground(pxPerSecond: Float?) {
        preview.pause()
        preview.setForeground(false)
        saveView(pxPerSecond)
    }

    /** De vuelta en pantalla: la reproducción vuelve a quedar preparada para arrancar al instante. */
    fun onForeground() = preview.setForeground(true)

    /** Hay un dedo en el editor: la reproducción no se prepara ni se libera en mitad del gesto. */
    fun onTouching(value: Boolean) = preview.setTouching(value)

    /** [pxPerSecond] null mientras el zoom inicial no se ha elegido (montaje vacío): no se guarda uno de relleno. */
    fun saveView(pxPerSecond: Float?) {
        prefs.saveProjectView(projectId, pxPerSecond, preview.positionUs.value)
        _session.value?.setPlayhead(preview.positionUs.value)
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
    fun run(id: String, vararg args: Pair<String, Any?>, origin: Origin = Origin.USER, label: String? = null, quiet: Boolean = false, then: (Outcome.Done) -> Unit = {}) =
        runOps(listOf(Op(id, CutSession.argsOf(*args))), origin, label, quiet, then)

    /** Varias operaciones como un solo paso de «Deshacer». */
    fun runOps(ops: List<Op>, origin: Origin = Origin.USER, label: String? = null, quiet: Boolean = false, then: (Outcome.Done) -> Unit = {}) {
        val s = _session.value ?: return
        viewModelScope.launch {
            when (val out = s.execute(Request(ops, origin, label))) {
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

    fun select(clipId: String?, additive: Boolean = multiSelect.value) {
        val current = _state.value?.view?.selection.orEmpty()
        when {
            clipId == null -> { multiSelect.value = false; if (current.isNotEmpty()) run("timeline.select", "mode" to "clear") }
            additive -> run("timeline.select", "clip_ids" to listOf(clipId), "mode" to if (clipId in current) "remove" else "add")
            else -> run("timeline.select", "clip_ids" to listOf(clipId))
        }
    }

    fun split() = run("clip.split", "at_us" to preview.positionUs.value)
    /** Lleva el principio o el final del clip seleccionado al cabezal: recorte exacto sin arrastrar. */
    fun trimToPlayhead(clipId: String, edge: Edge) =
        run("clip.trim", "clip_id" to clipId, "edge" to edge.name.lowercase(), "to_us" to preview.positionUs.value, "ripple" to ripple.value,
            label = if (edge == Edge.HEAD) "Empezar aquí" else "Terminar aquí")
    fun selectAllOnTrack(trackId: String) {
        val ids = _state.value?.project?.timeline?.allTracks?.firstOrNull { it.id == trackId }?.clips?.map { it.id }.orEmpty()
        if (ids.isNotEmpty()) { multiSelect.value = true; run("timeline.select", "clip_ids" to ids) }
    }
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
    fun setFrameRate(fps: String) = run("project.set_format", "fps" to fps)
    fun toggleTrack(trackId: String, locked: Boolean? = null, muted: Boolean? = null) = run("timeline.update_track", "track_id" to trackId, "locked" to locked, "muted" to muted)

    /** Archivos elegidos en la Biblioteca: entran donde está el cabezal si hay algo debajo, si no al final. */
    fun importAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val st = _state.value ?: return
        val at = preview.positionUs.value.takeIf { it > 0 && it < st.project.timeline.durationUs }
        // Lo importado no queda seleccionado (seis clips marcados de golpe confunden); el cabezal va a lo nuevo.
        runOps(listOf(Op("media.import", CutSession.argsOf("asset_ids" to assetIds, "at_us" to at)), Op("timeline.select", CutSession.argsOf("mode" to "clear"))),
            label = "Añadir desde la Biblioteca") { out ->
            val created = (out.results.first()["clip_ids"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            if (created.isNotEmpty()) {
                val fps = (out.results.first()["project_fps"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                _messages.tryEmit(CutMessage((if (assetIds.size == 1) "Añadido al montaje." else "${assetIds.size} archivos añadidos al montaje.") +
                    (fps?.let { " El montaje va a $it fps, como el vídeo." } ?: "")))
                val start = latest?.project?.timeline?.let { t -> created.mapNotNull { t.clip(it)?.timelineStartUs }.minOrNull() }
                if (start != null && at == null) preview.seekTo(start)
            }
        }
    }

    fun export(resolution: com.vidadomu.nexus.cut.model.ExportResolution? = null,
        codec: com.vidadomu.nexus.cut.model.VideoCodec? = null, eachClip: Boolean = false, clipIds: Set<String> = emptySet()) =
        run("render.export", "resolution" to resolution?.label, "codec" to codec?.name?.lowercase(), "each_clip" to eachClip, "clip_ids" to clipIds.toList())
    fun cancelExport() = run("render.cancel", quiet = true)

    // ------------------------------------------------------------------ arrastres

    private var dragBase: Timeline? = null
    private var dragRevision = 0L
    private var snapTargets: List<Snapping.Target> = emptyList()
    private var pendingOp: Op? = null
    private var dragReject: String? = null
    private var moveIds: Set<String> = emptySet()
    /** Reordenando: el bloque ya saltó a otro hueco (al volver a su sitio se marca, como cualquier otro encaje). */
    private var reorderAway = false

    private fun ctx(): EditContext? = _state.value?.project?.let { p -> EditContext(media = { id -> p.media(id) }) }

    fun beginDrag(exclude: Set<String>) {
        val st = _state.value ?: return
        dragBase = st.project.timeline
        dragRevision = st.revision
        pendingOp = null; dragReject = null; reorderAway = false
        _dragBlocked.value = false; _dragFeedback.value = null
        snapTargets = if (snapping.value) Snapping.targets(st.project.timeline, preview.positionUs.value, exclude) else emptyList()
    }

    /**
     * Pulsación larga sobre un clip: se «coge» para moverlo. Si era parte de la selección se mueve todo lo
     * seleccionado; si no, solo él (y queda seleccionado). Devuelve false si no se puede mover.
     */
    fun beginMove(clipId: String): Boolean {
        val st = latest ?: return false
        val track = st.project.timeline.trackOfClip(clipId) ?: return false
        if (track.locked) return false
        val sel = st.view.selection
        moveIds = if (clipId in sel) sel else setOf(clipId)
        if (clipId !in sel) run("timeline.select", "clip_ids" to listOf(clipId), quiet = true)
        beginDrag(moveIds + clipId)
        return true
    }

    private fun show(r: EditResult, op: Op, snapUs: Long?, feedback: ((Timeline) -> CutDragFeedback?)? = null) = when (r) {
        is EditResult.Applied -> {
            _dragTimeline.value = r.timeline; _snapLineUs.value = snapUs; pendingOp = op; dragReject = null
            _dragBlocked.value = false; _dragFeedback.value = feedback?.invoke(r.timeline)
        }
        // La vista se queda en la última posición válida: el borde «topa» en vez de saltar.
        is EditResult.Rejected -> { dragReject = r.reason; _snapLineUs.value = null; _dragBlocked.value = true }
    }

    private val frameRate get() = latest?.project?.settings?.frameRate

    /** Sin imán que mande, lo que se arrastra cae en un fotograma del montaje (nunca entre dos). */
    private fun toFrame(us: Long): Long = frameRate?.snap(us) ?: us

    fun dragTrim(clipId: String, edge: Edge, rawDeltaUs: Long, snapThresholdUs: Long) {
        val base = dragBase ?: return; val c = ctx() ?: return
        val clip = base.clip(clipId) ?: return
        val edgeAt = if (edge == Edge.HEAD) clip.timelineStartUs else clip.endUs
        val snap = Snapping.nearest(snapTargets, edgeAt + rawDeltaUs, snapThresholdUs)
        val delta = (snap?.timeUs ?: toFrame(edgeAt + rawDeltaUs)) - edgeAt
        if (delta == 0L) { _dragTimeline.value = base; _snapLineUs.value = snap?.timeUs; pendingOp = null; _dragFeedback.value = CutDragFeedback(edgeAt, CutDragText.trim(clip.durationUs, 0, frameRate)); return }
        show(TimelineEditor.trim(base, c, clipId, edge, delta, ripple.value),
            Op("clip.trim", CutSession.argsOf("clip_id" to clipId, "edge" to edge.name.lowercase(), "delta_us" to delta, "ripple" to ripple.value)), snap?.timeUs) { t ->
            t.clip(clipId)?.let { n -> CutDragFeedback(if (edge == Edge.HEAD) n.timelineStartUs else n.endUs, CutDragText.trim(n.durationUs, n.durationUs - clip.durationUs, frameRate)) }
        }
    }

    fun dragMove(clipId: String, rawDeltaUs: Long, trackOffset: Int, snapThresholdUs: Long) {
        val base = dragBase ?: return; val c = ctx() ?: return
        val clip = base.clip(clipId) ?: return
        val ids = moveIds.ifEmpty { _state.value?.view?.selection.orEmpty() } + clipId
        val mode = if (ripple.value) EditMode.INSERT else EditMode.OVERWRITE
        val home = clip.timelineStartUs
        // Con ripple, mover en la misma pista es reordenar: el bloque salta de hueco en hueco entre sus vecinos
        // (cuando su borde pasa la mitad del vecino) y nunca cae dentro de uno partiéndolo.
        val slot = if (mode == EditMode.INSERT && trackOffset == 0) TimelineEditor.reorderSlot(base, c, ids, clipId, rawDeltaUs) else null
        val delta: Long
        val snapUs: Long?
        if (slot == null) {
            val (snapped, snap) = Snapping.snapSpan(snapTargets, home + rawDeltaUs, clip.durationUs, snapThresholdUs)
            delta = ((if (snap != null) snapped else toFrame(snapped)) - home).coerceAtLeast(-home)
            snapUs = snap?.timeUs
        } else if (slot.landsAtUs != null || slot.deltaUs == 0L) {
            delta = slot.deltaUs; snapUs = slot.landsAtUs
        } else {
            // Sobre un hueco se coloca libre. Al insertar solo tiene sentido imantar el inicio del bloque.
            val near = Snapping.nearest(snapTargets, home + rawDeltaUs, snapThresholdUs)
            val free = ((near?.timeUs ?: toFrame(home + rawDeltaUs)) - home).coerceAtLeast(-home)
            val guarded = TimelineEditor.reorderSlot(base, c, ids, clipId, free) ?: slot
            delta = guarded.deltaUs
            snapUs = guarded.landsAtUs ?: near?.timeUs?.takeIf { guarded.deltaUs == free }
        }
        if (delta == 0L && trackOffset == 0) {
            _dragTimeline.value = base; pendingOp = null; dragReject = null; _dragBlocked.value = false; _dragFeedback.value = null
            _snapLineUs.value = if (reorderAway) home else null
            return
        }
        if (slot != null) reorderAway = true
        show(TimelineEditor.move(base, c, ids, clipId, delta, trackOffset, mode),
            Op("clip.move", CutSession.argsOf("clip_ids" to ids.toList(), "anchor_clip_id" to clipId, "delta_us" to delta, "track_offset" to trackOffset, "mode" to mode.name.lowercase())), snapUs) { t ->
            t.clip(clipId)?.let { n -> CutDragFeedback(n.timelineStartUs, CutDragText.move(n.timelineStartUs, n.timelineStartUs - clip.timelineStartUs, trackOffset, frameRate)) }
        }
    }

    fun dragFade(clipId: String, fadeIn: Boolean, fadeUs: Long) {
        val base = dragBase ?: return; val c = ctx() ?: return
        val clip = base.clip(clipId) ?: return
        val f = toFrame(fadeUs).coerceIn(0, clip.durationUs)
        val audio = clip.kind == ClipKind.AUDIO
        val key = if (fadeIn) "fade_in_us" else "fade_out_us"
        val r = TimelineEditor.updateClips(base, c, setOf(clipId), "Fundido") { x ->
            if (audio) x.copy(audio = if (fadeIn) x.audio.copy(fadeInUs = f) else x.audio.copy(fadeOutUs = f))
            else x.copy(video = if (fadeIn) x.video.copy(fadeInUs = f) else x.video.copy(fadeOutUs = f))
        }
        show(r, Op(if (audio) "audio.set" else "effects.set_fades", CutSession.argsOf("clip_ids" to listOf(clipId), key to f)), null) {
            CutDragFeedback(if (fadeIn) clip.timelineStartUs + f else clip.endUs - f, CutDragText.fade(f, fadeIn))
        }
    }

    fun endDrag(commit: Boolean) {
        val op = pendingOp; val reject = dragReject; val revision = dragRevision
        dragBase = null; pendingOp = null; dragReject = null; moveIds = emptySet(); reorderAway = false
        _dragFeedback.value = null; _dragBlocked.value = false
        if (!commit || op == null) {
            _dragTimeline.value = null; _snapLineUs.value = null
            if (commit && reject != null && op == null) _messages.tryEmit(CutMessage(reject, true))
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
        prefs.saveProjectView(projectId, null, preview.positionUs.value)
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
