package com.vidadomu.nexus.ui.cut

import kotlin.math.abs

/** Screen-only preferences: resizing/zooming never edits the project or its undo history. */
internal data class CutPanelSizes(val preview: Float, val timeline: Float, val chat: Float)

internal fun cutPanelSizes(available: Float, timelineFraction: Float, chatFraction: Float, chatVisible: Boolean, compact: Boolean): CutPanelSizes {
    val total = available.coerceAtLeast(0f)
    val previewMin = minOf(96f, total * .28f)
    val timelineMin = minOf(72f, total * .22f)
    val chatMax = (total - previewMin - timelineMin).coerceAtLeast(0f)
    val chatMin = minOf(if (compact) 84f else 144f, chatMax)
    val chat = if (!chatVisible) 0f else (total * chatFraction.coerceIn(.15f, .7f)).coerceIn(chatMin, chatMax)
    val left = total - chat
    val timeline = (left * timelineFraction.coerceIn(.15f, .8f)).coerceIn(minOf(timelineMin, left), (left - previewMin).coerceAtLeast(minOf(timelineMin, left)))
    return CutPanelSizes((left - timeline).coerceAtLeast(0f), timeline, chat)
}

internal data class CutPreviewTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun transform(zoom: Float, panX: Float, panY: Float, focusX: Float, focusY: Float, width: Float, height: Float): CutPreviewTransform {
        if (!zoom.isFinite() || zoom <= 0f) return this
        val next = (scale * zoom).coerceIn(1f, 6f)
        if (next == 1f) return CutPreviewTransform()
        val ratio = next / scale
        val maxX = width * (next - 1f) / 2f
        val maxY = height * (next - 1f) / 2f
        return CutPreviewTransform(next,
            ((x - focusX) * ratio + focusX + panX).coerceIn(-maxX, maxX),
            ((y - focusY) * ratio + focusY + panY).coerceIn(-maxY, maxY))
    }
    val isFit: Boolean get() = abs(scale - 1f) < .01f
}
