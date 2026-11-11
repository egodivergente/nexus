package com.vidadomu.nexus.cut.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.vidadomu.nexus.cut.timeline.render.TitleEvaluator
import com.vidadomu.nexus.cut.timeline.render.TitleState

/**
 * All titles of the timeline as one full-frame overlay on the composited output. The bitmap is only
 * redrawn when what is visible changes (text, look or animated state), so static titles cost one
 * texture upload.
 */
@UnstableApi
class TitleOverlay(
    private val evaluator: TitleEvaluator,
    width: Int,
    height: Int,
    /** Timeline time of the composition's 0 (preview windows). */
    private val fromUs: Long,
) : BitmapOverlay() {
    private val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private var last: List<TitleState>? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val visible = evaluator.visibleAt(presentationTimeUs + fromUs)
        if (visible != last) {
            bitmap.eraseColor(Color.TRANSPARENT)
            TitlePainter.draw(canvas, bitmap.width, bitmap.height, visible)
            last = visible
        }
        return bitmap
    }
}
