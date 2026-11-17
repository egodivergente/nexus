package com.vidadomu.nexus.ui.cut

import kotlin.math.abs

/** Screen-only preferences: resizing/zooming never edits the project or its undo history. */
internal data class CutPanelSizes(val preview: Float, val timeline: Float, val chat: Float)

/**
 * Reparto del alto de edición (dp) entre imagen, línea de tiempo y agente.
 *
 * [timelineFraction] null = automático: la imagen recibe el alto que necesita para verse entera a todo el
 * ancho ([frameFitDp] = ancho / proporción), entre el 30 % y el 62 % de lo que queda sin el agente; así un
 * 16:9 no lleva franjas negras encima y debajo y un 9:16 no se come el montaje. El resto es línea de tiempo.
 */
internal fun cutPanelSizes(
    available: Float, timelineFraction: Float?, chatFraction: Float, chatVisible: Boolean, compact: Boolean, frameFitDp: Float = 0f,
): CutPanelSizes {
    val total = available.coerceAtLeast(0f)
    val previewMin = minOf(96f, total * .28f)
    val timelineMin = minOf(72f, total * .22f)
    val chatMax = (total - previewMin - timelineMin).coerceAtLeast(0f)
    val chatMin = minOf(if (compact) 84f else 144f, chatMax)
    val chat = if (!chatVisible) 0f else (total * chatFraction.coerceIn(.15f, .7f)).coerceIn(chatMin, chatMax)
    val left = total - chat
    val fraction = timelineFraction ?: autoTimelineFraction(left, frameFitDp)
    val timeline = (left * fraction.coerceIn(.15f, .8f)).coerceIn(minOf(timelineMin, left), (left - previewMin).coerceAtLeast(minOf(timelineMin, left)))
    return CutPanelSizes((left - timeline).coerceAtLeast(0f), timeline, chat)
}

/** Parte de [spaceDp] para la línea de tiempo cuando el reparto es automático. */
internal fun autoTimelineFraction(spaceDp: Float, frameFitDp: Float): Float {
    if (spaceDp <= 0f || !frameFitDp.isFinite() || frameFitDp <= 0f) return CutViewPrefs.DEFAULT_TIMELINE_FRACTION
    val preview = frameFitDp.coerceIn(spaceDp * .30f, spaceDp * .62f)
    return CutViewPrefs.clampFraction(1f - preview / spaceDp)
}

/**
 * Las pistas crecen (hasta 1,5×, por pasos de 0,05) cuando la línea de tiempo tiene más alto del que
 * necesitan: miniaturas, ondas y asas más grandes en vez de un hueco vacío. Nunca encogen: si no caben,
 * la línea de tiempo se desplaza en vertical.
 */
internal fun timelineRowScale(paneDp: Float, videoRows: Int, audioRows: Int, collapsedRows: Int, emptyRows: Int = 0): Float {
    val base = videoRows * VIDEO_ROW_DP + audioRows * AUDIO_ROW_DP
    if (base <= 0 || !paneDp.isFinite()) return 1f
    // Se deja una franja libre debajo: sitio para tocar «en vacío» y para la píldora del agente.
    val room = paneDp - RULER_DP - collapsedRows * COLLAPSED_ROW_DP - emptyRows * EMPTY_ROW_DP - 36f
    val raw = (room / base).coerceIn(1f, 1.5f)
    return (raw * 20f).toInt() / 20f
}

/** Zoom con el que cabe todo el montaje a un lado del cabezal (que va en el centro). */
internal fun fitTimelineZoom(canvasWidthDp: Float, durationUs: Long, playheadUs: Long? = null): Float? {
    // El cabezal va fijo en el centro. Sin saber dónde está, la duración entera cabe en media pantalla (se ve
    // todo esté donde esté); sabiéndolo, basta con que quepa el lado más largo: hasta el doble de grande.
    val span = if (playheadUs == null) durationUs else maxOf(playheadUs.coerceIn(0, durationUs), durationUs - playheadUs.coerceIn(0, durationUs))
    val seconds = span / 1_000_000f
    if (durationUs <= 0L || seconds <= 0f || canvasWidthDp <= 0f) return null
    return CutViewPrefs.clampZoom(canvasWidthDp / 2f * .92f / seconds)
}

/**
 * Zoom inicial de un montaje que aún no se ha visto: entero a la vista si es corto; si es largo, unos ocho
 * segundos por media pantalla (lo bastante cerca para editar; «Ver todo» está a un toque).
 */
internal fun defaultTimelineZoom(canvasWidthDp: Float, durationUs: Long): Float? {
    val fit = fitTimelineZoom(canvasWidthDp, durationUs) ?: return null
    val floor = canvasWidthDp / 2f * .92f / 8f
    return CutViewPrefs.clampZoom(fit.coerceIn(minOf(floor, CutViewPrefs.DEFAULT_PX_PER_SECOND), CutViewPrefs.DEFAULT_PX_PER_SECOND))
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
