package com.vidadomu.nexus.cut.engine

import android.graphics.Bitmap
import android.graphics.Color
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SingleColorLut
import com.vidadomu.nexus.cut.model.ColorProps
import com.vidadomu.nexus.cut.timeline.render.ColorMath
import com.vidadomu.nexus.cut.timeline.render.CubeLut

/**
 * Colour correction for both render paths, from the same numbers ([ColorMath], [CubeLut]):
 * Media3 effects for playback/export, and a CPU pass for the paused still frame.
 * Order in both: LUT, then the correction matrix.
 */
object ColorEffects {
    @UnstableApi
    fun media3(color: ColorProps, lut: CubeLut?): List<Effect> {
        val out = ArrayList<Effect>(2)
        if (lut != null) out += SingleColorLut.createFromCube(cubeOf(lut))
        ColorMath.matrix(color)?.let { m ->
            val gl = ColorMath.toColumnMajor4x4(m)
            out += RgbMatrix { _, _ -> gl }
        }
        return out
    }

    /** Media3 wants cube[R][G][B] as ARGB ints. */
    fun cubeOf(lut: CubeLut): Array<Array<IntArray>> {
        val n = lut.size
        return Array(n) { r ->
            Array(n) { g ->
                IntArray(n) { b ->
                    Color.argb(255, to8(lut.at(r, g, b, 0)), to8(lut.at(r, g, b, 1)), to8(lut.at(r, g, b, 2)))
                }
            }
        }
    }

    fun isNeutral(color: ColorProps, lut: CubeLut?): Boolean = lut == null && ColorMath.matrix(color) == null

    /** Applies the correction to an opaque ARGB_8888 bitmap in place (returns it). */
    fun applyCpu(bitmap: Bitmap, color: ColorProps, lut: CubeLut?): Bitmap {
        val m = ColorMath.matrix(color)
        if (lut == null && m == null) return bitmap
        val target = if (bitmap.isMutable && bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== bitmap) bitmap.recycle() }
        val w = target.width
        val h = target.height
        val row = IntArray(w)
        val px = FloatArray(3)
        val o = FloatArray(3)
        for (y in 0 until h) {
            target.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                var r = ((c shr 16) and 0xFF) / 255f
                var g = ((c shr 8) and 0xFF) / 255f
                var b = (c and 0xFF) / 255f
                if (lut != null) { lut.sample(r, g, b, px); r = px[0]; g = px[1]; b = px[2] }
                if (m != null) { ColorMath.apply(m, r, g, b, o); r = o[0]; g = o[1]; b = o[2] }
                row[x] = (c and 0xFF000000.toInt()) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
            }
            target.setPixels(row, 0, w, 0, y, w, 1)
        }
        return target
    }

    private fun to8(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
