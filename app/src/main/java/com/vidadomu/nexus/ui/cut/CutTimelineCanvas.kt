package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.timeline.ClipParams

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.cut.media.ThumbnailRepository
import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.FrameRate
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.model.TrackKind
import com.vidadomu.nexus.cut.model.TransitionKind
import com.vidadomu.nexus.cut.storage.Waveform
import com.vidadomu.nexus.cut.timeline.Edge
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Zoom and vertical scroll of the timeline. Horizontal position is the playhead (centred). */
@Stable
internal class TimelineViewport(pxPerSecond: Float = CutViewPrefs.DEFAULT_PX_PER_SECOND) {
    var pxPerSecond by mutableFloatStateOf(pxPerSecond)
    var scrollY by mutableFloatStateOf(0f)

    /** Zoom por pasos (botones y accesibilidad), anclado al cabezal como el pellizco. */
    fun zoomBy(factor: Float): Boolean {
        val next = (pxPerSecond * factor).coerceIn(MIN_PX_PER_S, MAX_PX_PER_S)
        val changed = next != pxPerSecond
        pxPerSecond = next
        return changed
    }
}

/** Callbacks from the timeline to the editor. Times in microseconds. */
internal interface TimelineActions {
    fun seek(us: Long, scrubbing: Boolean)
    fun scrubEnd()
    /** Toque: selecciona (o, en selección múltiple, añade/quita). null = toque en vacío. */
    fun tap(clipId: String?, trackId: String?)
    /** Pulsación larga: coge el clip para moverlo. false si no se puede (pista bloqueada…). */
    fun pickUp(clipId: String): Boolean
    fun beginDrag(exclude: Set<String>)
    fun trim(clipId: String, edge: Edge, deltaUs: Long, snapUs: Long)
    fun move(clipId: String, deltaUs: Long, trackOffset: Int, snapUs: Long)
    fun fade(clipId: String, fadeIn: Boolean, fadeUs: Long)
    fun endDrag(commit: Boolean)
}

private enum class DragKind { PAN, SCROLL_Y, TRIM_HEAD, TRIM_TAIL, MOVE, FADE_IN, FADE_OUT, ZOOM }
private enum class Start { UP, DRAG, MULTI, LONG }

/** Desplazamiento automático al llevar un clip o un borde contra el lado de la línea de tiempo. */
private class EdgeScroll {
    var job: Job? = null
    /** -1 … 1: hacia dónde y con qué fuerza. */
    var speed = 0f
    var offsetUs = 0L
    fun stop() { job?.cancel(); job = null; speed = 0f }
}

private class FlingHolder {
    var job: Job? = null
    var stoppedByTouch = false
}

@Composable
internal fun TimelineCanvas(
    timeline: Timeline,
    selection: Set<String>,
    /** Cabezal publicado (se lee al dibujar: moverlo no recompone el editor). */
    playhead: State<Long>,
    /** Reloj del reproductor, para avanzar a la frecuencia de la pantalla mientras se reproduce. */
    livePlayhead: () -> Long,
    playing: Boolean,
    frameRate: FrameRate,
    media: Map<String, MediaRef>,
    offline: Set<String>,
    waveforms: Map<String, Waveform>,
    thumbnails: ThumbnailRepository,
    thumbVersion: State<Int>,
    snapLineUs: Long?,
    dragFeedback: CutDragFeedback?,
    blocked: Boolean,
    snapping: Boolean,
    viewport: TimelineViewport,
    actions: TimelineActions,
    palette: CutPalette,
    modifier: Modifier = Modifier,
    /** Las pistas crecen cuando sobra alto (1 … 1,5): miniaturas y asas más grandes sin perder nada de vista. */
    rowScale: Float = 1f,
) {
    val Palette = palette
    val haptics = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    val density = LocalDensity.current
    val dp = density.density
    val rulerPx = RULER_DP * dp
    val collapsedPx = Math.round(COLLAPSED_ROW_DP * dp).toFloat()
    val handlePx = 16 * dp
    val handleTouchPx = com.vidadomu.nexus.ui.theme.NexusTokens.minimumTouch.value / 2f * dp
    val snapPx = 10 * dp
    val edgeZonePx = 44 * dp
    val viewConfiguration = LocalViewConfiguration.current
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val labelStyle = TextStyle(color = Palette.text, fontSize = 10.sp)
    val feedbackStyle = TextStyle(color = Palette.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val rulerStyle = TextStyle(
        color = Palette.textDim,
        fontSize = 9.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontFeatureSettings = "tnum",
    )
    val mins = remember { FloatArray(2048) }
    val maxs = remember { FloatArray(2048) }
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val edgeScroll = remember { EdgeScroll() }
    val fling = remember { FlingHolder() }
    // Al mover, dónde está el dedo: el clip se dibuja ya en el hueco donde caería y esto marca lo que se lleva.
    var moveGhost by remember { androidx.compose.runtime.mutableStateOf<MoveGhost?>(null) }

    // Háptica del imán: solo al entrar en él (no mientras se sigue pegado).
    var lastSnapUs by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    LaunchedEffect(snapLineUs) {
        if (snapLineUs != null && snapLineUs != lastSnapUs) haptics.perform(NexusHapticEvent.SNAP)
        lastSnapUs = snapLineUs
    }
    LaunchedEffect(blocked) { if (blocked) haptics.perform(NexusHapticEvent.BOUNDARY) }

    // Reproduciendo, el cabezal avanza en cada fotograma de pantalla (90/120 Hz) leyendo el reloj del reproductor.
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) { if (playing) while (isActive) withFrameNanos { frameNanos = it } }

    val latestTimeline by rememberUpdatedState(timeline)
    val latestSelection by rememberUpdatedState(selection)
    val latestPlaying by rememberUpdatedState(playing)
    val latestActions by rememberUpdatedState(actions)
    val latestSnapping by rememberUpdatedState(snapping)
    val latestLive by rememberUpdatedState(livePlayhead)
    val latestRowScale by rememberUpdatedState(rowScale)
    // Enlaces (vídeo + su audio) de lo seleccionado: el compañero se marca para que se vea que van juntos.
    val selectedLinks = remember(timeline, selection) {
        if (selection.isEmpty()) emptySet() else timeline.allTracks.flatMap { it.clips }.filter { it.id in selection }.mapNotNull { it.linkId }.toSet()
    }

    fun currentPlayhead(): Long = if (latestPlaying) latestLive() else playhead.value

    fun geometry(width: Float, t: Timeline, ph: Long) = TimelineGeometry(
        t, width, rulerPx, Math.round(VIDEO_ROW_DP * dp * latestRowScale).toFloat(), Math.round(AUDIO_ROW_DP * dp * latestRowScale).toFloat(), collapsedPx, ph,
        viewport.pxPerSecond * dp / 1_000_000.0, viewport.scrollY, handlePx, emptyRowPx = Math.round(EMPTY_ROW_DP * dp).toFloat(),
        touchPx = handleTouchPx,
    )

    Canvas(
        modifier
            .semantics {
                contentDescription = "Línea de tiempo del montaje"
                stateDescription = "Zoom ${viewport.pxPerSecond.roundToInt()} puntos por segundo"
                customActions = listOf(
                    CustomAccessibilityAction("Acercar la línea de tiempo") { viewport.zoomBy(1.5f) },
                    CustomAccessibilityAction("Alejar la línea de tiempo") { viewport.zoomBy(1 / 1.5f) },
                )
            }
            .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Tocar detiene la inercia y deja el fotograma exacto donde se paró.
                fling.job?.let { job ->
                    if (job.isActive) { fling.stoppedByTouch = true; job.cancel(); latestActions.scrubEnd() }
                    fling.job = null
                }
                val width = size.width.toFloat()
                val startPlayhead = currentPlayhead()
                val g0 = geometry(width, latestTimeline, startPlayhead)
                val hit = g0.hit(down.position.x, down.position.y, latestSelection)
                val inRuler = down.position.y < rulerPx
                val playingAtDown = latestPlaying
                val startScroll = viewport.scrollY
                val clipAtDown = hit?.clip
                val canPick = !inRuler && !playingAtDown && clipAtDown != null && hit?.row?.track?.locked == false
                var total = Offset.Zero

                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.classify(): Start {
                    var r: Start? = null
                    while (r == null) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        r = when {
                            ev.changes.count { it.pressed } >= 2 -> Start.MULTI
                            ch == null || !ch.pressed -> Start.UP
                            else -> {
                                total += ch.positionChange()
                                if (total.getDistance() > slop) Start.DRAG else null
                            }
                        }
                    }
                    return checkNotNull(r)
                }

                val start = if (canPick) withTimeoutOrNull(longPressMs) { classify() } ?: Start.LONG else classify()
                if (start == Start.UP) {
                    if (inRuler) latestActions.seek(g0.tOf(down.position.x).coerceAtLeast(0), false)
                    else latestActions.tap(clipAtDown?.id, hit?.row?.track?.id)
                    return@awaitEachGesture
                }

                var dragOpen = false
                var kind: DragKind = when (start) {
                    Start.MULTI -> DragKind.ZOOM
                    Start.LONG -> if (latestActions.pickUp(clipAtDown!!.id)) {
                        dragOpen = true
                        haptics.perform(NexusHapticEvent.PICK_UP)
                        DragKind.MOVE
                    } else DragKind.PAN
                    else -> {
                        val selected = clipAtDown != null && clipAtDown.id in latestSelection && !hit!!.row.track.locked && !playingAtDown && !inRuler
                        when {
                            selected && hit!!.zone == Zone.HEAD -> DragKind.TRIM_HEAD
                            selected && hit!!.zone == Zone.TAIL -> DragKind.TRIM_TAIL
                            selected && hit!!.zone == Zone.FADE_IN -> DragKind.FADE_IN
                            selected && hit!!.zone == Zone.FADE_OUT -> DragKind.FADE_OUT
                            // Arrastre claramente vertical: recorre las pistas sin mover el cabezal.
                            !inRuler && abs(total.y) > abs(total.x) * 1.4f && g0.contentHeight > size.height - rulerPx -> DragKind.SCROLL_Y
                            else -> DragKind.PAN
                        }
                    }
                }
                if (kind == DragKind.TRIM_HEAD || kind == DragKind.TRIM_TAIL || kind == DragKind.FADE_IN || kind == DragKind.FADE_OUT) {
                    latestActions.beginDrag(latestTimeline.allTracks.flatMap { it.clips }.filter { it.id in latestSelection || it.id == clipAtDown?.id }.map { it.id }.toSet())
                    dragOpen = true
                    haptics.perform(NexusHapticEvent.GESTURE_START)
                }

                val durationUs = latestTimeline.durationUs
                // Puntos de corte para los «clics» del scrub (sin el principio ni el final, que son límites).
                val cuts: LongArray = if (kind == DragKind.PAN) latestTimeline.allTracks.asSequence().flatMap { t -> t.clips.asSequence().flatMap { sequenceOf(it.timelineStartUs, it.endUs) } }
                    .filter { it in 1 until durationUs }.distinct().sorted().toList().toLongArray() else LongArray(0)
                var lastTarget = startPlayhead
                var atLimit = false
                var zoomAtLimit = false
                var scrubbed = false
                var flung = false
                val tracker = VelocityTracker()

                fun applyDrag() {
                    val clip = clipAtDown ?: return
                    val pxPerUs = viewport.pxPerSecond * dp / 1_000_000.0
                    val dUs = (total.x / pxPerUs).toLong() + edgeScroll.offsetUs
                    val snapUs = (snapPx / pxPerUs).toLong()
                    when (kind) {
                        DragKind.TRIM_HEAD -> latestActions.trim(clip.id, Edge.HEAD, dUs, snapUs)
                        DragKind.TRIM_TAIL -> latestActions.trim(clip.id, Edge.TAIL, dUs, snapUs)
                        DragKind.MOVE -> {
                            val rowH = hit!!.row.height
                            val steps = (total.y / rowH).roundToInt()
                            val offset = if (hit.row.track.kind == TrackKind.VIDEO) -steps else steps
                            moveGhost = MoveGhost(clip.id, hit.row.track.id, clip.timelineStartUs + dUs, clip.durationUs, total.y)
                            latestActions.move(clip.id, dUs, offset, snapUs)
                        }
                        else -> Unit
                    }
                }

                fun updateEdgeScroll(x: Float) {
                    val w = size.width.toFloat()
                    val s = when {
                        x < edgeZonePx -> -((edgeZonePx - x) / edgeZonePx).coerceIn(0f, 1f)
                        x > w - edgeZonePx -> ((x - (w - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    edgeScroll.speed = s
                    if (s == 0f) { edgeScroll.job?.cancel(); edgeScroll.job = null; return }
                    if (edgeScroll.job?.isActive == true) return
                    edgeScroll.job = scope.launch {
                        var last = 0L
                        while (isActive) {
                            withFrameNanos { now ->
                                val dtMs = if (last == 0L) 16f else ((now - last) / 1_000_000f).coerceIn(0f, 48f)
                                last = now
                                val pxPerUs = viewport.pxPerSecond * dp / 1_000_000.0
                                val stepUs = (edgeScroll.speed * EDGE_SCROLL_DP_PER_S * dp * dtMs / 1000f / pxPerUs).toLong()
                                val next = (edgeScroll.offsetUs + stepUs).coerceIn(-startPlayhead, maxOf(0L, durationUs - startPlayhead))
                                if (next != edgeScroll.offsetUs) {
                                    edgeScroll.offsetUs = next
                                    latestActions.seek(startPlayhead + next, true)
                                    applyDrag()
                                }
                            }
                        }
                    }
                }

                try {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2 && kind != DragKind.ZOOM) {
                            // Un segundo dedo convierte el gesto en zoom; lo que se arrastraba se suelta sin aplicar.
                            if (dragOpen) { latestActions.endDrag(commit = false); dragOpen = false }
                            edgeScroll.stop()
                            moveGhost = null
                            kind = DragKind.ZOOM
                        }
                        if (kind == DragKind.ZOOM) {
                            if (pressed >= 2) {
                                val z = ev.calculateZoom()
                                viewport.pxPerSecond = (viewport.pxPerSecond * z).coerceIn(MIN_PX_PER_S, MAX_PX_PER_S)
                                val limit = viewport.pxPerSecond <= MIN_PX_PER_S || viewport.pxPerSecond >= MAX_PX_PER_S
                                if (limit && !zoomAtLimit && z != 1f) haptics.perform(NexusHapticEvent.BOUNDARY)
                                zoomAtLimit = limit
                            }
                            ev.changes.forEach { it.consume() }
                            if (pressed == 0) break
                            continue
                        }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            when (kind) {
                                DragKind.PAN -> {
                                    val v = tracker.calculateVelocity().x
                                    if (abs(v) > FLING_MIN_DP_PER_S * dp && durationUs > 0) {
                                        flung = true
                                        val from = lastTarget
                                        fling.stoppedByTouch = false
                                        fling.job = scope.launch {
                                            try {
                                                AnimationState(initialValue = 0f, initialVelocity = -v).animateDecay(decay) {
                                                    val pxPerUs = viewport.pxPerSecond * dp / 1_000_000.0
                                                    val t = (from + (value / pxPerUs).toLong()).coerceIn(0L, durationUs)
                                                    latestActions.seek(t, true)
                                                    if (t == 0L || t == durationUs) { haptics.perform(NexusHapticEvent.BOUNDARY); cancelAnimation() }
                                                }
                                            } finally {
                                                if (!fling.stoppedByTouch) latestActions.scrubEnd()
                                            }
                                        }
                                    }
                                }
                                DragKind.SCROLL_Y, DragKind.ZOOM -> Unit
                                else -> if (dragOpen) { latestActions.endDrag(commit = true); dragOpen = false }
                            }
                            break
                        }
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        total += ch.positionChange()
                        ch.consume()
                        val pxPerUs = viewport.pxPerSecond * dp / 1_000_000.0
                        when (kind) {
                            DragKind.PAN -> {
                                scrubbed = true
                                var target = (startPlayhead - (total.x / pxPerUs).toLong()).coerceIn(0L, maxOf(0L, durationUs))
                                if (latestSnapping && cuts.isNotEmpty()) {
                                    val near = nearestCut(cuts, target)
                                    if (near != null && abs(near - target) * pxPerUs < PLAYHEAD_SNAP_DP * dp) target = near
                                }
                                if (target != lastTarget && crossesCut(cuts, lastTarget, target)) haptics.perform(NexusHapticEvent.GESTURE_TICK)
                                val limit = durationUs > 0 && (target == 0L || target == durationUs)
                                if (limit && !atLimit && target != lastTarget) haptics.perform(NexusHapticEvent.BOUNDARY)
                                atLimit = limit
                                lastTarget = target
                                latestActions.seek(target, true)
                            }
                            DragKind.SCROLL_Y -> {
                                val maxScroll = maxOf(0f, g0.contentHeight - (size.height - rulerPx))
                                viewport.scrollY = (startScroll - total.y).coerceIn(0f, maxScroll)
                            }
                            DragKind.TRIM_HEAD, DragKind.TRIM_TAIL, DragKind.MOVE -> {
                                updateEdgeScroll(ch.position.x)
                                applyDrag()
                            }
                            DragKind.FADE_IN -> {
                                val clip = clipAtDown!!
                                val x = down.position.x + total.x
                                latestActions.fade(clip.id, true, ((x - g0.xOf(clip.timelineStartUs)) / pxPerUs).toLong())
                            }
                            DragKind.FADE_OUT -> {
                                val clip = clipAtDown!!
                                val x = down.position.x + total.x
                                latestActions.fade(clip.id, false, ((g0.xOf(clip.endUs) - x) / pxPerUs).toLong())
                            }
                            DragKind.ZOOM -> Unit
                        }
                    }
                } finally {
                    edgeScroll.stop()
                    moveGhost = null
                    if (dragOpen) latestActions.endDrag(commit = false)
                    if ((scrubbed && !flung) || edgeScroll.offsetUs != 0L) latestActions.scrubEnd()
                    edgeScroll.offsetUs = 0L
                }
            }
        },
    ) {
        thumbVersion.value // las miniaturas nuevas solo repintan el lienzo
        val ph = if (playing) { frameNanos; livePlayhead() } else playhead.value
        val g = geometry(size.width, timeline, ph)
        drawRect(Palette.bg)
        clipRect(top = rulerPx) {
            for (row in g.rows) {
                val y = g.yOf(row)
                if (y > size.height || y + row.height < rulerPx) continue
                drawRect(if (row.track.kind == TrackKind.VIDEO) Palette.videoRow else Palette.audioRow, Offset(0f, y), Size(size.width, row.height))
                drawLine(Palette.line, Offset(0f, y + row.height - 0.5f), Offset(size.width, y + row.height - 0.5f), 1f)
                if (row.track.locked) drawRect(Color(0x22FFFFFF), Offset(0f, y), Size(size.width, row.height))
                for (c in row.track.clips) {
                    val x0 = g.xOf(c.timelineStartUs)
                    val x1 = g.xOf(c.endUs)
                    if (x1 < 0 || x0 > size.width) continue
                    drawClip(Palette, g, c, row, y, x0, x1, selection, selectedLinks, media, offline, waveforms, thumbnails, mins, maxs, measurer, labelStyle, dp)
                }
                for (tr in row.track.transitions) {
                    val left = row.track.clip(tr.leftClipId) ?: continue
                    val cut = left.endUs
                    val a = g.xOf(cut - tr.beforeCutUs)
                    val b = g.xOf(cut + tr.afterCutUs)
                    if (b < 0 || a > size.width) continue
                    val top = y + row.height * 0.55f
                    val h = row.height * 0.4f
                    drawRect(Color(0xCC000000), Offset(a, top), Size(maxOf(2f, b - a), h))
                    val col = if (tr.kind == TransitionKind.DIP_TO_BLACK) Color(0xFFBBBBBB) else Palette.accent
                    drawLine(col, Offset(a, top + h), Offset(b, top), 1.5f * dp)
                    drawLine(col, Offset(a, top), Offset(b, top + h), 1.5f * dp)
                }
            }
            moveGhost?.let { gh ->
                val row = g.rows.firstOrNull { it.track.id == gh.trackId } ?: return@let
                val x0 = g.xOf(gh.startUs)
                val x1 = g.xOf(gh.startUs + gh.durationUs)
                val placed = timeline.clip(gh.clipId)?.timelineStartUs
                // Si el clip va justo bajo el dedo (movimiento libre) no hace falta: solo cuando espera en un hueco.
                if (placed != null && abs(g.xOf(placed) - x0) < 2f * dp && abs(gh.dy) < row.height / 2) return@let
                val y = (g.yOf(row) + gh.dy).coerceIn(rulerPx, maxOf(rulerPx, size.height - row.height)) + 2f * dp
                val box = Size(maxOf(2f * dp, x1 - x0), row.height - 4f * dp)
                drawRoundRect(Palette.accent.copy(alpha = .16f), Offset(x0, y), box, CornerRadius(5f * dp))
                drawRoundRect(Palette.accent.copy(alpha = .85f), Offset(x0, y), box, CornerRadius(5f * dp),
                    style = Stroke(1.5f * dp, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f * dp, 4f * dp))))
            }
        }
        drawRuler(Palette, g, frameRate, measurer, rulerStyle, dp)
        snapLineUs?.let { s ->
            val x = g.xOf(s)
            drawLine(Palette.accent, Offset(x, rulerPx), Offset(x, size.height), 1.5f * dp)
        }
        dragFeedback?.let { drawFeedback(Palette, g, it, measurer, feedbackStyle, dp) }
        val cx = g.centerX
        drawLine(Palette.playhead, Offset(cx, rulerPx * 0.35f), Offset(cx, size.height), 2f * dp)
        val capW = 10f * dp
        val capH = rulerPx * 0.62f
        drawRoundRect(Palette.playhead, Offset(cx - capW / 2, 0f), Size(capW, capH), CornerRadius(3f * dp))
    }
}

/** El corte más cercano a [t] (búsqueda binaria sobre cortes ordenados). */
internal fun nearestCut(cuts: LongArray, t: Long): Long? {
    if (cuts.isEmpty()) return null
    val i = java.util.Arrays.binarySearch(cuts, t)
    if (i >= 0) return cuts[i]
    val ins = -i - 1
    val a = cuts.getOrNull(ins - 1)
    val b = cuts.getOrNull(ins)
    return when {
        a == null -> b
        b == null -> a
        t - a <= b - t -> a
        else -> b
    }
}

/** Si al ir de [from] a [to] se pasa por encima de (o se llega a) algún corte. */
internal fun crossesCut(cuts: LongArray, from: Long, to: Long): Boolean {
    if (cuts.isEmpty() || from == to) return false
    val lo = minOf(from, to)
    val hi = maxOf(from, to)
    var i = java.util.Arrays.binarySearch(cuts, lo)
    i = if (i >= 0) i + 1 else -i - 1
    // Un corte exactamente en [from] ya se marcó al llegar a él.
    if (from > to) {
        val j = java.util.Arrays.binarySearch(cuts, lo)
        return j >= 0 || (i < cuts.size && cuts[i] < hi)
    }
    return i < cuts.size && cuts[i] <= hi
}

private fun DrawScope.drawClip(
    Palette: CutPalette, g: TimelineGeometry, c: Clip, row: Row, y: Float, x0: Float, x1: Float, selection: Set<String>, selectedLinks: Set<String>,
    media: Map<String, MediaRef>, offline: Set<String>, waveforms: Map<String, Waveform>, thumbs: ThumbnailRepository,
    mins: FloatArray, maxs: FloatArray, measurer: androidx.compose.ui.text.TextMeasurer, labelStyle: TextStyle, dp: Float,
) {
    // Titles have no media: they can never be offline.
    val isOffline = c.kind != ClipKind.TEXT && (c.mediaId in offline || media[c.mediaId] == null)
    val pad = 1.5f * dp
    val top = y + pad
    val h = row.height - pad * 2
    val w = maxOf(1f, x1 - x0 - 1f)
    val radius = CornerRadius(5f * dp)
    val selected = c.id in selection
    val handleW = 11f * dp
    val base = when {
        isOffline -> Palette.offline
        c.kind == ClipKind.AUDIO -> Palette.audioDark
        c.kind == ClipKind.IMAGE -> Palette.image
        c.kind == ClipKind.TEXT -> Palette.title
        else -> Palette.videoDark
    }
    drawRoundRect(base, Offset(x0, top), Size(w, h), radius)
    val ref = media[c.mediaId]
    val vx0 = maxOf(0f, x0)
    val vx1 = minOf(size.width, x1)
    clipRect(left = vx0, top = top, right = vx1, bottom = top + h) {
        if (!isOffline && ref != null && !row.track.collapsed) {
            if (c.kind != ClipKind.AUDIO) {
                val thumbH = h.toInt()
                // Se decodifica por escalones de alto (no una miniatura distinta por cada píxel al redimensionar).
                val decodeH = thumbnailDecodeHeight(h, dp)
                val aspect = if (ref.displayHeight > 0) ref.displayWidth.toFloat() / ref.displayHeight else 16f / 9f
                val tw = maxOf(16f, h * aspect)
                // Paso por potencias de dos: al cambiar el zoom se reutilizan las miniaturas ya decodificadas.
                val stepUs = thumbnailStepUs((tw / g.pxPerUs).toLong())
                var i = maxOf(0, ((vx0 - x0) / tw).toInt())
                while (true) {
                    val tx = x0 + i * tw
                    if (tx > vx1) break
                    val tlTime = c.timelineStartUs + ((i * tw + tw / 2) / g.pxPerUs).toLong()
                    val bmp = thumbs.get(ref, c.sourceTimeAt(minOf(tlTime, c.endUs - 1)), stepUs, decodeH)
                    if (bmp != null) {
                        drawImage(bmp.asImageBitmap(), dstOffset = IntOffset(tx.toInt(), top.toInt()), dstSize = IntSize(tw.toInt() + 1, thumbH), alpha = c.video.opacity.toFloat().coerceIn(0.35f, 1f))
                    }
                    i++
                }
            } else {
                waveforms[c.mediaId]?.let { wf -> drawWave(Palette, g, c, wf, vx0, vx1, top, h, mins, maxs, dp) }
            }
        }
        if (isOffline) {
            val stripe = 6f * dp
            var sx = vx0 - h
            while (sx < vx1) { drawLine(Color(0x55FFFFFF), Offset(sx, top + h), Offset(sx + h, top), 1f * dp); sx += stripe }
        }
        // Fades: ramps from the corner to where the fade ends.
        val fin = g.fadeInUs(c)
        val fout = g.fadeOutUs(c)
        if (fin > 0) drawLine(Color.White, Offset(x0, top + h), Offset(g.xOf(c.timelineStartUs + fin), top), 1.2f * dp)
        if (fout > 0) drawLine(Color.White, Offset(g.xOf(c.endUs - fout), top), Offset(x1, top + h), 1.2f * dp)
        val title = if (c.kind == ClipKind.TEXT) "T · " + c.name.ifBlank { c.text?.text?.lineSequence()?.firstOrNull().orEmpty() } else null
        val name = (if (isOffline) "NO DISPONIBLE · " else "") + (title ?: c.name.ifBlank { ref?.displayName ?: c.mediaId }) +
            (if (c.speed.num != c.speed.den) "  ${c.speed}x" else "") + (if (c.kind == ClipKind.AUDIO && c.audio.gainDb != 0.0) "  ${"%+.1f".format(c.audio.gainDb)} dB" else "")
        if (w > 24f * dp) {
            // Seleccionado, el nombre empieza después del asa y del tirador del fundido: no se tapan.
            val textX = maxOf(x0 + if (selected) handleW + (if (!row.track.collapsed && w > g.handlePx * 4) g.handlePx * 1.2f else 0f) + 5f * dp else 6f * dp, 4f * dp)
            val layout = measurer.measure(name, labelStyle, maxLines = 1)
            drawRoundRect(Color(0x99000000), Offset(textX - 3f * dp, top + 2f * dp), Size(minOf(layout.size.width + 6f * dp, vx1 - textX), layout.size.height.toFloat()), CornerRadius(3f * dp))
            drawText(layout, topLeft = Offset(textX, top + 2f * dp))
        }
        // Keyframes: small diamonds on the bottom edge (any parameter).
        if (!row.track.collapsed) {
            val ky = top + h - 5f * dp
            val r = 3.5f * dp
            for (k in ClipParams.keyTimes(c)) {
                val kx = g.xOf(k)
                if (kx < vx0 - r || kx > vx1 + r) continue
                val d = Path().apply { moveTo(kx, ky - r); lineTo(kx + r, ky); lineTo(kx, ky + r); lineTo(kx - r, ky); close() }
                drawPath(d, Palette.accent)
            }
        }
        if (c.linkId != null) {
            val lx = maxOf(x0, 0f) + 7f * dp
            val ly = top + h - 6f * dp
            drawCircle(Color.White, 2.5f * dp, Offset(lx, ly), style = Stroke(1f * dp))
            drawCircle(Color.White, 2.5f * dp, Offset(lx + 3.5f * dp, ly), style = Stroke(1f * dp))
        }
    }
    if (selected) {
        // Marco de selección: acento sobre un contorno oscuro, para que se lea igual sobre miniaturas claras u oscuras.
        drawRoundRect(Color(0xB3000000), Offset(x0, top), Size(w, h), radius, style = Stroke(4.5f * dp))
        drawRoundRect(Palette.accent, Offset(x0, top), Size(w, h), radius, style = Stroke(2.5f * dp))
        // Asas de recorte: anchas, con dos rayas de agarre, para que se vean y se acierten con el pulgar.
        val hw = minOf(handleW, w / 3f)
        drawRoundRect(Palette.accent, Offset(x0, top), Size(hw, h), CornerRadius(4f * dp))
        drawRoundRect(Palette.accent, Offset(x1 - hw - 1f, top), Size(hw, h), CornerRadius(4f * dp))
        if (hw > 6f * dp) {
            val gripTop = top + h * 0.34f
            val gripBottom = top + h * 0.66f
            for (gx in floatArrayOf(x0 + hw / 2, x1 - 1f - hw / 2)) {
                drawLine(Palette.onAccent, Offset(gx - 1.4f * dp, gripTop), Offset(gx - 1.4f * dp, gripBottom), 1.2f * dp, cap = StrokeCap.Round)
                drawLine(Palette.onAccent, Offset(gx + 1.4f * dp, gripTop), Offset(gx + 1.4f * dp, gripBottom), 1.2f * dp, cap = StrokeCap.Round)
            }
        }
        if (!row.track.collapsed && w > g.handlePx * 4) {
            // Tiradores de fundido: un punto con aro en la esquina de arriba, donde termina cada fundido.
            val (fx0, fx1) = g.fadeHandleXs(c)
            for (fx in floatArrayOf(fx0, fx1)) {
                drawCircle(Color(0xB3000000), 6.5f * dp, Offset(fx, top + 7f * dp))
                drawCircle(Palette.accent, 5.5f * dp, Offset(fx, top + 7f * dp))
                drawCircle(Color.White, 3f * dp, Offset(fx, top + 7f * dp))
            }
        }
    } else if (c.linkId != null && c.linkId in selectedLinks) {
        drawRoundRect(Palette.accent.copy(alpha = .8f), Offset(x0, top), Size(w, h), radius, style = Stroke(1.5f * dp))
    }
}

private fun DrawScope.drawWave(
    Palette: CutPalette, g: TimelineGeometry, c: Clip, wf: Waveform, vx0: Float, vx1: Float, top: Float, h: Float, mins: FloatArray, maxs: FloatArray, dp: Float,
) {
    val step = maxOf(2f, 1f * dp)
    val n = minOf(mins.size, ((vx1 - vx0) / step).toInt())
    if (n <= 0) return
    val t0 = g.tOf(vx0).coerceIn(c.timelineStartUs, c.endUs)
    val t1 = g.tOf(vx1).coerceIn(c.timelineStartUs, c.endUs)
    if (t1 <= t0) return
    val f0 = c.sourceTimeAt(t0) * wf.sampleRate / 1_000_000L
    val f1 = c.sourceTimeAt(t1) * wf.sampleRate / 1_000_000L
    wf.fill(f0, f1, n, mins, maxs)
    val gain = Math.pow(10.0, c.audio.gainDb / 20.0).toFloat().coerceAtMost(2f) * (if (c.audio.muted) 0.15f else 1f)
    val mid = top + h / 2
    val half = h / 2 - 2f * dp
    for (i in 0 until n) {
        val x = vx0 + i * step
        val a = (maxs[i] * gain).coerceIn(-1f, 1f)
        val b = (mins[i] * gain).coerceIn(-1f, 1f)
        drawLine(Palette.wave, Offset(x, mid - a * half), Offset(x, mid - b * half), step * 0.6f, cap = StrokeCap.Butt)
    }
}

private fun DrawScope.drawRuler(Palette: CutPalette, g: TimelineGeometry, rate: FrameRate, measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, dp: Float) {
    drawRect(Palette.panel, Offset.Zero, Size(size.width, g.rulerPx))
    drawLine(Palette.line, Offset(0f, g.rulerPx), Offset(size.width, g.rulerPx), 1f)
    // Space majors by the real label width (density and font scale vary a lot between devices).
    val labelPx = measurer.measure("00:00", style).size.width + 14f * dp
    val step = rulerStep(g.pxPerUs, labelPx, rate)
    val major = step.majorUs
    val minor = major / step.minorPerMajor
    val tStart = g.tOf(0f).coerceAtLeast(0)
    val tEnd = g.tOf(size.width)
    var k = (tStart / minor).toLong()
    while (true) {
        val t = Math.round(k * minor)
        if (t > tEnd) break
        val x = g.xOf(t)
        val isMajor = k % step.minorPerMajor == 0L
        drawLine(Palette.textDim, Offset(x, g.rulerPx - if (isMajor) 7f * dp else 3.5f * dp), Offset(x, g.rulerPx), 1f)
        if (isMajor) {
            val label = formatTimelineRulerLabel(t, major, rate)
            drawText(measurer.measure(label, style), topLeft = Offset(x + 3f * dp, 2f * dp))
        }
        k++
    }
}

private fun DrawScope.drawFeedback(p: CutPalette, g: TimelineGeometry, f: CutDragFeedback, measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, dp: Float) {
    val layout = measurer.measure(f.text, style, maxLines = 1)
    val w = layout.size.width + 14f * dp
    val h = layout.size.height + 6f * dp
    val x = (g.xOf(f.atUs) - w / 2).coerceIn(4f * dp, maxOf(4f * dp, size.width - w - 4f * dp))
    val y = g.rulerPx + 4f * dp
    drawRoundRect(p.accent, Offset(x, y), Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = Offset(x + 7f * dp, y + 3f * dp))
}

/** Lo que se lleva el dedo al mover un clip: su intervalo según el arrastre y el desplazamiento vertical. */
private data class MoveGhost(val clipId: String, val trackId: String, val startUs: Long, val durationUs: Long, val dy: Float)

/** Marca mayor de la regla y en cuántas menores se divide. */
internal data class RulerStep(val majorUs: Double, val minorPerMajor: Int)

/**
 * Paso de la regla para un zoom. Por debajo del segundo solo valen pasos que dividen el segundo en partes
 * iguales (a 25 fps: 1 y 5 fotogramas; a 30: 1, 2, 3, 5, 6, 10 y 15…): así los segundos siempre llevan marca
 * y las etiquetas intermedias se repiten igual en cada segundo. De un segundo en adelante, pasos redondos.
 */
internal fun rulerStep(pxPerUs: Double, minLabelPx: Float, rate: FrameRate): RulerStep {
    val fps = Math.round(rate.fps).toInt().coerceAtLeast(1)
    // Fracción exacta del segundo, no `frameDurationUs` (redondeado al microsegundo: 33 334 µs a 30 fps),
    // que acumulaba medio fotograma de deriva a los ~14 min y dejaba los segundos sin rotular.
    val frameUs = 1e6 / fps
    for (frames in 1 until fps) {
        if (fps % frames != 0 || frames * frameUs * pxPerUs < minLabelPx) continue
        val minors = when {
            frames <= 6 -> frames
            frames % 5 == 0 -> 5
            frames % 4 == 0 -> 4
            frames % 3 == 0 -> 3
            else -> 2
        }
        return RulerStep(frames * frameUs, minors)
    }
    for ((seconds, minors) in RULER_SECOND_STEPS) {
        if (seconds * 1e6 * pxPerUs >= minLabelPx) return RulerStep(seconds * 1e6, if (seconds == 1 && fps % 5 != 0) 4 else minors)
    }
    return RulerStep(3600e6, 6)
}

private val RULER_SECOND_STEPS = listOf(1 to 5, 2 to 4, 5 to 5, 10 to 5, 15 to 3, 30 to 6, 60 to 6, 120 to 4, 300 to 5, 600 to 5, 1800 to 6, 3600 to 6)

/** Alto al que se decodifican las miniaturas de una fila: por escalones de 8 dp, para compartir caché. */
internal fun thumbnailDecodeHeight(rowPx: Float, density: Float): Int {
    val stepPx = (8f * density).coerceAtLeast(1f)
    return (Math.ceil((rowPx / stepPx).toDouble()) * stepPx).toInt().coerceAtLeast(1)
}

/** Paso de muestreo de miniaturas, redondeado a 250 ms × 2ⁿ para compartir caché entre niveles de zoom. */
internal fun thumbnailStepUs(rawUs: Long): Long {
    var step = 250_000L
    while (step < rawUs && step < 3_600_000_000L) step *= 2
    return step
}

/**
 * Etiquetas de la regla: en los segundos enteros, «mm:ss» (o «h:mm:ss»); entre segundos, solo el
 * fotograma dentro del segundo («12f»), que es lo que se lee al afinar sin confundirlo con minutos.
 */
internal fun formatTimelineRulerLabel(us: Long, majorUs: Double, rate: FrameRate): String {
    val half = rate.frameDurationUs / 2
    val second = Math.round(us / 1_000_000.0) * 1_000_000L
    if (majorUs >= 1e6 || abs(us - second) <= half) return CutDragText.Clock.short(second)
    val floorSecond = us / 1_000_000L * 1_000_000L
    val f = rate.frameAt(us + half) - rate.frameAt(floorSecond)
    return "${f}f"
}

internal const val RULER_DP = 24
internal const val VIDEO_ROW_DP = 52
internal const val AUDIO_ROW_DP = 40
internal const val COLLAPSED_ROW_DP = 22
/** Pista sin clips: fila baja (se ve y se puede tocar), y su alto pasa a las pistas que tienen contenido. */
internal const val EMPTY_ROW_DP = 30
internal const val MIN_PX_PER_S = 2f
internal const val MAX_PX_PER_S = 2400f
private const val FLING_MIN_DP_PER_S = 400f
private const val EDGE_SCROLL_DP_PER_S = 520f
private const val PLAYHEAD_SNAP_DP = 6f
