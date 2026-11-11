package com.vidadomu.nexus.cut.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.vidadomu.nexus.cut.model.TextAlign
import com.vidadomu.nexus.cut.timeline.render.TitleState

/**
 * Draws titles onto a frame-sized canvas. Used by export/playback (through [TitleOverlay], onto a
 * transparent bitmap the size of the output) and by the paused preview (onto the composed frame),
 * so a title looks the same in both.
 *
 * Geometry matches video layers: position in frame units from the centre (x right, y down),
 * scale about the text centre, rotation counter-clockwise in degrees.
 */
object TitlePainter {
    fun draw(canvas: Canvas, width: Int, height: Int, titles: List<TitleState>) {
        for (t in titles) drawOne(canvas, width, height, t)
    }

    private fun drawOne(canvas: Canvas, w: Int, h: Int, t: TitleState) {
        val p = t.text
        val alpha = t.layer.alpha.coerceIn(0f, 1f)
        if (alpha <= 0f) return
        val size = (p.size * h).toFloat().coerceAtLeast(1f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            color = p.colorArgb
            typeface = if (p.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (p.shadow) setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0xC0000000.toInt())
        }
        val maxWidth = (w * 0.9f).toInt().coerceAtLeast(1)
        val textWidth = minOf(maxWidth, p.text.lines().maxOf { StaticLayout.getDesiredWidth(it, paint) }.toInt() + 1)
        val align = when (p.align) {
            TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder.obtain(p.text, 0, p.text.length, paint, textWidth)
            .setAlignment(align)
            .setIncludePad(false)
            .build()
        val lw = layout.width.toFloat()
        val lh = layout.height.toFloat()
        val save = canvas.saveLayerAlpha(null, (alpha * 255).toInt())
        canvas.translate(w / 2f + t.layer.positionX * w, h / 2f + t.layer.positionY * h)
        canvas.rotate(-t.layer.rotationDeg)
        canvas.scale(t.layer.scale, t.layer.scale)
        canvas.translate(-lw / 2f, -lh / 2f)
        if (p.boxOpacity > 0) {
            val pad = size * 0.25f
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.boxColorArgb
                this.alpha = (p.boxOpacity.coerceIn(0.0, 1.0) * 255).toInt()
            }
            canvas.drawRoundRect(RectF(-pad, -pad * 0.6f, lw + pad, lh + pad * 0.6f), pad * 0.5f, pad * 0.5f, box)
        }
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }
}
