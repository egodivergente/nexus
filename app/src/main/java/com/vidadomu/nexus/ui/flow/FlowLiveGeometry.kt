package com.vidadomu.nexus.ui.flow

import kotlin.math.max
import kotlin.math.min

/** Rectángulo donde se dibuja el fotograma dentro de la caja (en píxeles de pantalla, sin zoom local). */
internal data class FrameRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Cálculos puros de la vista en directo: dónde cae el fotograma, cómo un toque en pantalla se convierte en una
 * posición de la página (0–1) y cuántos píxeles CSS hay que desplazar por cada píxel de dedo.
 */
internal object FlowLiveGeometry {
    /** Encaja el fotograma (proporción [frameW]/[frameH]) arriba y centrado en la caja, sin recortar. */
    fun fit(boxW: Float, boxH: Float, frameW: Int, frameH: Int): FrameRect {
        if (boxW <= 0f || boxH <= 0f || frameW <= 0 || frameH <= 0) return FrameRect(0f, 0f, max(boxW, 0f), max(boxH, 0f))
        val scale = min(boxW / frameW, boxH / frameH)
        val w = frameW * scale
        val h = frameH * scale
        return FrameRect((boxW - w) / 2f, 0f, w, h)
    }

    /**
     * Punto de la caja → posición normalizada en la página, deshaciendo el zoom local
     * (origen arriba a la izquierda, `p' = p * zoom + pan`). Devuelve null si cae fuera del fotograma.
     */
    fun normalize(x: Float, y: Float, rect: FrameRect, zoom: Float, panX: Float, panY: Float): Pair<Float, Float>? {
        if (rect.width <= 0f || rect.height <= 0f || zoom <= 0f) return null
        val cx = (x - panX) / zoom
        val cy = (y - panY) / zoom
        val nx = (cx - rect.left) / rect.width
        val ny = (cy - rect.top) / rect.height
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null
        return nx to ny
    }

    /** Píxeles CSS de la página por cada píxel que se mueve el dedo. */
    fun cssPerPixel(pageCssWidth: Int, rect: FrameRect, zoom: Float): Float =
        if (pageCssWidth <= 0 || rect.width <= 0f || zoom <= 0f) 1f else pageCssWidth / (rect.width * zoom)

    /** Mantiene el contenido ampliado dentro de la caja: el desplazamiento va de `box*(1-zoom)` a 0. */
    fun clampPan(pan: Float, box: Float, zoom: Float): Float = pan.coerceIn(min(0f, box * (1f - zoom)), 0f)

    /** Ancho CSS de la página según la vista elegida (el alto sale de la proporción de la caja). */
    fun pageWidth(mode: String, presetWidth: Int, boxWidthDp: Float): Int =
        if (mode == "mobile") boxWidthDp.toInt().coerceAtLeast(320) else presetWidth.coerceIn(480, 1920)
}
