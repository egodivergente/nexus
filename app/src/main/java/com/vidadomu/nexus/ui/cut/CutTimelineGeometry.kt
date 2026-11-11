package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.Timeline
import com.vidadomu.nexus.cut.model.Track
import com.vidadomu.nexus.cut.model.TrackKind

/** One visible track row. Video tracks are listed top-down from the highest (Vn … V1), then A1 … An. */
internal data class Row(val track: Track, val top: Float, val height: Float) {
    val bottom: Float get() = top + height
}

internal enum class Zone { BODY, HEAD, TAIL, FADE_IN, FADE_OUT }

internal data class Hit(val row: Row, val clip: Clip?, val zone: Zone)

/** Pure layout + hit testing of the timeline canvas (no Compose state), in pixels. */
internal class TimelineGeometry(
    val timeline: Timeline,
    val widthPx: Float,
    val rulerPx: Float,
    val videoRowPx: Float,
    val audioRowPx: Float,
    val collapsedRowPx: Float,
    val playheadUs: Long,
    val pxPerUs: Double,
    val scrollY: Float,
    val handlePx: Float,
) {
    val centerX: Float get() = widthPx / 2f

    val rows: List<Row> = run {
        val out = ArrayList<Row>()
        var y = 0f
        for (t in timeline.videoTracks.asReversed() + timeline.audioTracks) {
            val h = if (t.collapsed) collapsedRowPx else if (t.kind == TrackKind.VIDEO) videoRowPx else audioRowPx
            out += Row(t, y, h)
            y += h
        }
        out
    }
    val contentHeight: Float get() = rows.lastOrNull()?.bottom ?: 0f

    fun xOf(t: Long): Float = (centerX + (t - playheadUs) * pxPerUs).toFloat()
    fun tOf(x: Float): Long = playheadUs + ((x - centerX) / pxPerUs).toLong()
    fun usOf(px: Float): Long = (px / pxPerUs).toLong()

    /** Screen y of a row top (below the ruler, scrolled). */
    fun yOf(row: Row): Float = rulerPx + row.top - scrollY

    fun rowAt(y: Float): Row? {
        val cy = y - rulerPx + scrollY
        return rows.firstOrNull { cy >= it.top && cy < it.bottom }
    }

    fun fadeInUs(c: Clip): Long = if (c.kind == com.vidadomu.nexus.cut.model.ClipKind.AUDIO) c.audio.fadeInUs else c.video.fadeInUs
    fun fadeOutUs(c: Clip): Long = if (c.kind == com.vidadomu.nexus.cut.model.ClipKind.AUDIO) c.audio.fadeOutUs else c.video.fadeOutUs

    /** Screen x of the fade-in and fade-out handles (they sit where each fade ends). */
    fun fadeHandleXs(c: Clip): Pair<Float, Float> {
        val x0 = xOf(c.timelineStartUs); val x1 = xOf(c.endUs)
        val inset = handlePx * 1.2f
        return maxOf(x0 + inset, xOf(c.timelineStartUs + fadeInUs(c))) to minOf(x1 - inset, xOf(c.endUs - fadeOutUs(c)))
    }

    fun hit(x: Float, y: Float, selection: Set<String>): Hit? {
        val row = rowAt(y) ?: return null
        val t = tOf(x)
        // Selected clips get edge handles that may extend slightly outside the clip.
        val candidates = row.track.clips.filter { c ->
            val x0 = xOf(c.timelineStartUs); val x1 = xOf(c.endUs)
            x >= x0 - (if (c.id in selection) handlePx else 0f) && x <= x1 + (if (c.id in selection) handlePx else 0f)
        }
        val clip = candidates.firstOrNull { it.id in selection } ?: candidates.firstOrNull { it.contains(t) } ?: return Hit(row, null, Zone.BODY)
        if (clip.id !in selection) return Hit(row, clip, Zone.BODY)
        val x0 = xOf(clip.timelineStartUs); val x1 = xOf(clip.endUs)
        val top = yOf(row)
        val w = x1 - x0
        val edge = minOf(handlePx, w / 3f)
        val inTopBand = y < top + row.height * 0.40f && !row.track.collapsed && w > handlePx * 4
        val (fx0, fx1) = fadeHandleXs(clip)
        return when {
            inTopBand && Math.abs(x - fx0) < handlePx * 0.9f && x - x0 > edge * 0.6f -> Hit(row, clip, Zone.FADE_IN)
            inTopBand && Math.abs(x - fx1) < handlePx * 0.9f && x1 - x > edge * 0.6f -> Hit(row, clip, Zone.FADE_OUT)
            x < x0 + edge -> Hit(row, clip, Zone.HEAD)
            x > x1 - edge -> Hit(row, clip, Zone.TAIL)
            else -> Hit(row, clip, Zone.BODY)
        }
    }
}
