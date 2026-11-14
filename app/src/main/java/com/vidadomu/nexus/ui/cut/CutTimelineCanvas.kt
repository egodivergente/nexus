package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.timeline.ClipParams

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
import kotlin.math.abs
import kotlin.math.roundToInt

/** Zoom and vertical scroll of the timeline. Horizontal position is the playhead (centred). */
@Stable
internal class TimelineViewport {
    var pxPerSecond by mutableFloatStateOf(120f)
    var scrollY by mutableFloatStateOf(0f)
}

/** Callbacks from the timeline to the editor. Times in microseconds. */
internal interface TimelineActions {
    fun seek(us: Long, scrubbing: Boolean)
    fun scrubEnd()
    fun tap(clipId: String?, trackId: String?, additive: Boolean)
    fun beginDrag(exclude: Set<String>)
    fun trim(clipId: String, edge: Edge, deltaUs: Long, snapUs: Long)
    fun move(clipId: String, deltaUs: Long, trackOffset: Int, snapUs: Long)
    fun fade(clipId: String, fadeIn: Boolean, fadeUs: Long)
    fun endDrag(commit: Boolean)
}

private enum class DragKind { PAN, TRIM_HEAD, TRIM_TAIL, MOVE, FADE_IN, FADE_OUT, ZOOM }

@Composable
internal fun TimelineCanvas(
    timeline: Timeline,
    selection: Set<String>,
    playheadUs: Long,
    frameRate: FrameRate,
    media: Map<String, MediaRef>,
    offline: Set<String>,
    waveforms: Map<String, Waveform>,
    thumbnails: ThumbnailRepository,
    thumbVersion: Int,
    snapLineUs: Long?,
    viewport: TimelineViewport,
    actions: TimelineActions,
    palette: CutPalette,
    modifier: Modifier = Modifier,
) {
    val Palette = palette
    val haptics = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
    val density = LocalDensity.current
    val rulerPx = with(density) { RULER_DP.dp.toPx() }
    val videoRowPx = with(density) { VIDEO_ROW_DP.dp.toPx() }
    val audioRowPx = with(density) { AUDIO_ROW_DP.dp.toPx() }
    val collapsedPx = with(density) { COLLAPSED_ROW_DP.dp.toPx() }
    val handlePx = with(density) { 14.dp.toPx() }
    val snapPx = with(density) { 10.dp.toPx() }
    val pxPerDpSecond = density.density
    val slop = LocalViewConfiguration.current.touchSlop
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Palette.text, fontSize = 10.sp)
    val rulerStyle = TextStyle(
        color = Palette.textDim,
        fontSize = 9.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontFeatureSettings = "tnum",
    )
    val mins = remember { FloatArray(2048) }
    val maxs = remember { FloatArray(2048) }

    var lastSnapUs by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(snapLineUs) {
        if (snapLineUs != null && snapLineUs != lastSnapUs) {
            haptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
        }
        lastSnapUs = snapLineUs
    }

    val latestTimeline by rememberUpdatedState(timeline)
    val latestSelection by rememberUpdatedState(selection)
    val latestPlayhead by rememberUpdatedState(playheadUs)
    val latestActions by rememberUpdatedState(actions)

    fun geometry(width: Float, t: Timeline = latestTimeline, ph: Long = latestPlayhead) = TimelineGeometry(
        t, width, rulerPx, videoRowPx, audioRowPx, collapsedPx, ph,
        viewport.pxPerSecond * pxPerDpSecond / 1_000_000.0, viewport.scrollY, handlePx,
    )

    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val width = size.width.toFloat()
                val g0 = geometry(width)
                val hit = g0.hit(down.position.x, down.position.y, latestSelection)
                val inRuler = down.position.y < rulerPx
                val startPlayhead = latestPlayhead
                val startScroll = viewport.scrollY
                var kind: DragKind? = null
                var total = Offset.Zero
                var dragging = false
                var lastScrubBucket = startPlayhead / 250_000L
                var lastScrubTickMs = 0L
                while (true) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        if (dragging && kind != DragKind.PAN && kind != DragKind.ZOOM) latestActions.endDrag(commit = false)
                        kind = DragKind.ZOOM
                        dragging = true
                        val z = ev.calculateZoom()
                        viewport.pxPerSecond = (viewport.pxPerSecond * z).coerceIn(MIN_PX_PER_S, MAX_PX_PER_S)
                        ev.changes.forEach { it.consume() }
                        continue
                    }
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                    if (!ch.pressed) {
                        if (!dragging) {
                            if (inRuler) {
                                haptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.GESTURE_TICK)
                                latestActions.seek(g0.tOf(down.position.x).coerceAtLeast(0), false)
                            } else {
                                val long = ch.uptimeMillis - down.uptimeMillis > LONG_TAP_MS
                                latestActions.tap(hit?.clip?.id, hit?.row?.track?.id, long)
                            }
                        } else when (kind) {
                            DragKind.PAN -> latestActions.scrubEnd()
                            DragKind.ZOOM, null -> Unit
                            else -> latestActions.endDrag(commit = true)
                        }
                        break
                    }
                    if (kind == DragKind.ZOOM) { ch.consume(); continue }
                    total += ch.positionChange()
                    if (!dragging && total.getDistance() > slop) {
                        dragging = true
                        val c = hit?.clip
                        kind = when {
                            inRuler || c == null || c.id !in latestSelection -> DragKind.PAN
                            hit.row.track.locked -> DragKind.PAN
                            hit.zone == Zone.HEAD -> DragKind.TRIM_HEAD
                            hit.zone == Zone.TAIL -> DragKind.TRIM_TAIL
                            hit.zone == Zone.FADE_IN -> DragKind.FADE_IN
                            hit.zone == Zone.FADE_OUT -> DragKind.FADE_OUT
                            else -> DragKind.MOVE
                        }
                        if (kind != DragKind.PAN) {
                            latestActions.beginDrag(latestTimeline.allTracks.flatMap { it.clips }.filter { it.id in latestSelection || it.id == c?.id }.map { it.id }.toSet())
                        }
                    }
                    if (!dragging) continue
                    ch.consume()
                    val pxPerUs = viewport.pxPerSecond * pxPerDpSecond / 1_000_000.0
                    val dUs = (total.x / pxPerUs).toLong()
                    val snapUs = (snapPx / pxPerUs).toLong()
                    val clip = hit?.clip
                    when (kind) {
                        DragKind.PAN -> {
                            val targetUs = (startPlayhead - dUs).coerceAtLeast(0)
                            val bucket = targetUs / 250_000L
                            if (bucket != lastScrubBucket && ch.uptimeMillis - lastScrubTickMs >= 42L) {
                                haptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.GESTURE_TICK)
                                lastScrubTickMs = ch.uptimeMillis
                            }
                            lastScrubBucket = bucket
                            latestActions.seek(targetUs, true)
                            val maxScroll = maxOf(0f, g0.contentHeight - (size.height - rulerPx))
                            viewport.scrollY = (startScroll - total.y).coerceIn(0f, maxScroll)
                        }
                        DragKind.TRIM_HEAD -> latestActions.trim(clip!!.id, Edge.HEAD, dUs, snapUs)
                        DragKind.TRIM_TAIL -> latestActions.trim(clip!!.id, Edge.TAIL, dUs, snapUs)
                        DragKind.MOVE -> {
                            val rowH = hit!!.row.height
                            val steps = (total.y / rowH).roundToInt()
                            val offset = if (hit.row.track.kind == TrackKind.VIDEO) -steps else steps
                            latestActions.move(clip!!.id, dUs, offset, snapUs)
                        }
                        DragKind.FADE_IN -> {
                            val x = down.position.x + total.x
                            latestActions.fade(clip!!.id, true, ((x - g0.xOf(clip.timelineStartUs)) / pxPerUs).toLong())
                        }
                        DragKind.FADE_OUT -> {
                            val x = down.position.x + total.x
                            latestActions.fade(clip!!.id, false, ((g0.xOf(clip.endUs) - x) / pxPerUs).toLong())
                        }
                        else -> Unit
                    }
                }
            }
        },
    ) {
        thumbVersion.hashCode() // read so that new thumbnails trigger a redraw
        val g = geometry(size.width, timeline, playheadUs)
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
                    drawClip(Palette, g, c, row, y, x0, x1, selection, media, offline, waveforms, thumbnails, mins, maxs, measurer, labelStyle)
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
                    drawLine(col, Offset(a, top + h), Offset(b, top), 2f)
                    drawLine(col, Offset(a, top), Offset(b, top + h), 2f)
                }
            }
        }
        drawRuler(Palette, g, frameRate, measurer, rulerStyle)
        snapLineUs?.let { s ->
            val x = g.xOf(s)
            drawLine(Palette.accent, Offset(x, rulerPx), Offset(x, size.height), 2f)
        }
        val cx = g.centerX
        drawLine(Palette.playhead, Offset(cx, 0f), Offset(cx, size.height), 2.5f)
        val tri = Path().apply { moveTo(cx - 9f, 0f); lineTo(cx + 9f, 0f); lineTo(cx, 12f); close() }
        drawPath(tri, Palette.playhead)
    }
}

private fun DrawScope.drawClip(
    Palette: CutPalette, g: TimelineGeometry, c: Clip, row: Row, y: Float, x0: Float, x1: Float, selection: Set<String>,
    media: Map<String, MediaRef>, offline: Set<String>, waveforms: Map<String, Waveform>, thumbs: ThumbnailRepository,
    mins: FloatArray, maxs: FloatArray, measurer: androidx.compose.ui.text.TextMeasurer, labelStyle: TextStyle,
) {
    // Titles have no media: they can never be offline.
    val isOffline = c.kind != ClipKind.TEXT && (c.mediaId in offline || media[c.mediaId] == null)
    val pad = 2f
    val top = y + pad
    val h = row.height - pad * 2
    val w = maxOf(1f, x1 - x0 - 1f)
    val base = when {
        isOffline -> Palette.offline
        c.kind == ClipKind.AUDIO -> Palette.audioDark
        c.kind == ClipKind.IMAGE -> Palette.image
        c.kind == ClipKind.TEXT -> Palette.title
        else -> Palette.videoDark
    }
    drawRoundRect(base, Offset(x0, top), Size(w, h), CornerRadius(6f))
    val ref = media[c.mediaId]
    val vx0 = maxOf(0f, x0)
    val vx1 = minOf(size.width, x1)
    clipRect(left = vx0, top = top, right = vx1, bottom = top + h) {
        if (!isOffline && ref != null && !row.track.collapsed) {
            if (c.kind != ClipKind.AUDIO) {
                val thumbH = h.toInt()
                val aspect = if (ref.displayHeight > 0) ref.displayWidth.toFloat() / ref.displayHeight else 16f / 9f
                val tw = maxOf(16f, h * aspect)
                val stepUs = maxOf(250_000L, (tw / g.pxPerUs).toLong())
                var i = maxOf(0, ((vx0 - x0) / tw).toInt())
                while (true) {
                    val tx = x0 + i * tw
                    if (tx > vx1) break
                    val tlTime = c.timelineStartUs + ((i * tw + tw / 2) / g.pxPerUs).toLong()
                    val bmp = thumbs.get(ref, c.sourceTimeAt(minOf(tlTime, c.endUs - 1)), stepUs, thumbH)
                    if (bmp != null) {
                        drawImage(bmp.asImageBitmap(), dstOffset = IntOffset(tx.toInt(), top.toInt()), dstSize = IntSize(tw.toInt() + 1, thumbH), alpha = c.video.opacity.toFloat().coerceIn(0.35f, 1f))
                    }
                    i++
                }
            } else {
                waveforms[c.mediaId]?.let { wf -> drawWave(Palette, g, c, wf, vx0, vx1, top, h, mins, maxs) }
            }
        }
        if (isOffline) {
            val stripe = 14f
            var sx = vx0 - h
            while (sx < vx1) { drawLine(Color(0x55FFFFFF), Offset(sx, top + h), Offset(sx + h, top), 1.5f); sx += stripe }
        }
        // Fades: ramps from the corner to where the fade ends.
        val fin = g.fadeInUs(c)
        val fout = g.fadeOutUs(c)
        if (fin > 0) drawLine(Color.White, Offset(x0, top + h), Offset(g.xOf(c.timelineStartUs + fin), top), 1.5f)
        if (fout > 0) drawLine(Color.White, Offset(g.xOf(c.endUs - fout), top), Offset(x1, top + h), 1.5f)
        val title = if (c.kind == ClipKind.TEXT) "T · " + c.name.ifBlank { c.text?.text?.lineSequence()?.firstOrNull().orEmpty() } else null
        val name = (if (isOffline) "NO DISPONIBLE · " else "") + (title ?: c.name.ifBlank { ref?.displayName ?: c.mediaId }) +
            (if (c.speed.num != c.speed.den) "  ${c.speed}x" else "") + (if (c.kind == ClipKind.AUDIO && c.audio.gainDb != 0.0) "  ${"%+.1f".format(c.audio.gainDb)} dB" else "")
        if (w > 36f) {
            val textX = maxOf(x0 + 6f, 4f)
            val layout = measurer.measure(name, labelStyle, maxLines = 1)
            drawRect(Color(0x99000000), Offset(textX - 3f, top + 2f), Size(minOf(layout.size.width + 6f, vx1 - textX), layout.size.height.toFloat()))
            drawText(layout, topLeft = Offset(textX, top + 2f))
        }
        // Keyframes: small diamonds on the bottom edge (any parameter).
        if (!row.track.collapsed) {
            val ky = top + h - 5f
            for (k in ClipParams.keyTimes(c)) {
                val kx = g.xOf(k)
                if (kx < vx0 - 5f || kx > vx1 + 5f) continue
                val d = Path().apply { moveTo(kx, ky - 4.5f); lineTo(kx + 4.5f, ky); lineTo(kx, ky + 4.5f); lineTo(kx - 4.5f, ky); close() }
                drawPath(d, Palette.accent)
            }
        }
        if (c.linkId != null) {
            val lx = maxOf(x0, 0f) + 6f
            val ly = top + h - 7f
            drawCircle(Color.White, 3.5f, Offset(lx, ly), style = Stroke(1.4f))
            drawCircle(Color.White, 3.5f, Offset(lx + 5f, ly), style = Stroke(1.4f))
        }
    }
    if (c.id in selection) {
        drawRoundRect(Color.White, Offset(x0, top), Size(w, h), CornerRadius(6f), style = Stroke(2.5f))
        val hw = 7f
        drawRoundRect(Palette.accent, Offset(x0, top), Size(hw, h), CornerRadius(3f))
        drawRoundRect(Palette.accent, Offset(x1 - hw - 1f, top), Size(hw, h), CornerRadius(3f))
        if (!row.track.collapsed && w > g.handlePx * 4) {
            val (fx0, fx1) = g.fadeHandleXs(c)
            drawCircle(Color.White, 6f, Offset(fx0, top + 7f))
            drawCircle(Color.White, 6f, Offset(fx1, top + 7f))
        }
    }
}

private fun DrawScope.drawWave(
    Palette: CutPalette, g: TimelineGeometry, c: Clip, wf: Waveform, vx0: Float, vx1: Float, top: Float, h: Float, mins: FloatArray, maxs: FloatArray,
) {
    val step = 2f
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
    val half = h / 2 - 2f
    for (i in 0 until n) {
        val x = vx0 + i * step
        val a = (maxs[i] * gain).coerceIn(-1f, 1f)
        val b = (mins[i] * gain).coerceIn(-1f, 1f)
        drawLine(Palette.wave, Offset(x, mid - a * half), Offset(x, mid - b * half), 1.2f, cap = StrokeCap.Butt)
    }
}

private fun DrawScope.drawRuler(Palette: CutPalette, g: TimelineGeometry, rate: FrameRate, measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle) {
    drawRect(Palette.panel, Offset.Zero, Size(size.width, g.rulerPx))
    drawLine(Palette.line, Offset(0f, g.rulerPx), Offset(size.width, g.rulerPx), 1f)
    val frameUs = rate.frameDurationUs.toDouble()
    val candidates = listOf(frameUs, frameUs * 2, frameUs * 5, 250_000.0, 500_000.0, 1e6, 2e6, 5e6, 10e6, 15e6, 30e6, 60e6, 120e6, 300e6, 600e6, 1800e6, 3600e6)
    // Space majors by the real label width (density and font scale vary a lot between devices).
    val labelPx = measurer.measure("00:00:00", style).size.width + 12f
    val major = candidates.firstOrNull { it * g.pxPerUs >= labelPx } ?: 3600e6
    val minor = major / 5
    val tStart = g.tOf(0f).coerceAtLeast(0)
    val tEnd = g.tOf(size.width)
    var k = (tStart / minor).toLong()
    while (true) {
        val t = (k * minor).toLong()
        if (t > tEnd) break
        val x = g.xOf(t)
        val isMajor = abs((t / major) - Math.round(t / major)) < 1e-6
        drawLine(Palette.textDim, Offset(x, g.rulerPx - if (isMajor) 10f else 5f), Offset(x, g.rulerPx), 1f)
        if (isMajor) {
            val label = formatTimelineRulerLabel(t, major, rate)
            drawText(measurer.measure(label, style), topLeft = Offset(x + 3f, 2f))
        }
        k++
    }
}

internal fun formatTimelineRulerLabel(us: Long, majorUs: Double, rate: FrameRate): String =
    if (majorUs < 1e6) com.vidadomu.nexus.cut.model.Timecode.formatFrames(us, rate).substring(3)
    else formatShort(us)

private fun formatShort(us: Long): String {
    val s = (us / 1_000_000).coerceAtLeast(0L)
    return if (s >= 3600) "%d:%02d:%02d".format(java.util.Locale.ROOT, s / 3600, (s / 60) % 60, s % 60)
    else "%02d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
}

internal const val RULER_DP = 22
internal const val VIDEO_ROW_DP = 52
internal const val AUDIO_ROW_DP = 40
internal const val COLLAPSED_ROW_DP = 22
internal const val MIN_PX_PER_S = 2f
internal const val MAX_PX_PER_S = 2400f
private const val LONG_TAP_MS = 450L
