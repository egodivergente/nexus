package com.vidadomu.corte.timeline.render

import com.vidadomu.corte.model.ColorProps

/**
 * Primary colour correction as one affine RGB matrix, applied to gamma-encoded (display) RGB in
 * [0, 1]. That is the space Media3 works in for SDR by default (WORKING_COLOR_SPACE_DEFAULT) and
 * the space of an Android Bitmap, so export and the paused preview use the very same numbers.
 *
 * Order: exposure, white balance (temperature, tint), contrast around mid grey, saturation
 * (Rec.709 luma kept), fade (lifts blacks). A LUT, if any, is applied before this matrix.
 */
object ColorMath {
    /** Rec.709 luma weights. */
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    /** Ranges offered by the UI; values are clamped to them. */
    val EXPOSURE = -2f..2f
    val UNIT = -1f..1f
    val FADE = 0f..1f

    /**
     * 3x4 row-major matrix M so that out = M * (r, g, b, 1). Null when the correction is neutral
     * (nothing to apply).
     */
    fun matrix(c: ColorProps): FloatArray? {
        if (c.exposure == 0f && c.contrast == 0f && c.saturation == 0f && c.temperature == 0f && c.tint == 0f && c.fade == 0f) return null
        var m = identity()
        // Exposure in stops: a gain on display values (a "brightness" stop, not scene-linear).
        val gain = Math.pow(2.0, c.exposure.coerceIn(EXPOSURE).toDouble()).toFloat()
        m = mul(scale(gain, gain, gain), m)
        // White balance: warm raises red and lowers blue; tint + is magenta (less green).
        val t = c.temperature.coerceIn(UNIT)
        val ti = c.tint.coerceIn(UNIT)
        m = mul(scale(1f + 0.15f * t, 1f - 0.12f * ti, 1f - 0.15f * t), m)
        // Contrast around 0.5.
        val k = 1f + c.contrast.coerceIn(UNIT)
        m = mul(floatArrayOf(k, 0f, 0f, 0.5f - 0.5f * k, 0f, k, 0f, 0.5f - 0.5f * k, 0f, 0f, k, 0.5f - 0.5f * k), m)
        // Saturation: lerp between luma and colour.
        val s = 1f + c.saturation.coerceIn(UNIT)
        val a = 1f - s
        m = mul(
            floatArrayOf(
                a * LR + s, a * LG, a * LB, 0f,
                a * LR, a * LG + s, a * LB, 0f,
                a * LR, a * LG, a * LB + s, 0f,
            ),
            m,
        )
        // Fade: black goes up to 0.25 * fade, white stays.
        val f = 0.25f * c.fade.coerceIn(FADE)
        m = mul(floatArrayOf(1 - f, 0f, 0f, f, 0f, 1 - f, 0f, f, 0f, 0f, 1 - f, f), m)
        return m
    }

    /** Applies [m] to one colour, clamping to [0, 1] like an 8-bit render target does. */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float, out: FloatArray) {
        out[0] = (m[0] * r + m[1] * g + m[2] * b + m[3]).coerceIn(0f, 1f)
        out[1] = (m[4] * r + m[5] * g + m[6] * b + m[7]).coerceIn(0f, 1f)
        out[2] = (m[8] * r + m[9] * g + m[10] * b + m[11]).coerceIn(0f, 1f)
    }

    /** The same matrix as an OpenGL/Media3 4x4 column-major matrix (for RgbMatrix). */
    fun toColumnMajor4x4(m: FloatArray): FloatArray = floatArrayOf(
        m[0], m[4], m[8], 0f,
        m[1], m[5], m[9], 0f,
        m[2], m[6], m[10], 0f,
        m[3], m[7], m[11], 1f,
    )

    /** The same matrix as an android.graphics.ColorMatrix array (4x5 row-major, offsets in 0..255). */
    fun toAndroid4x5(m: FloatArray): FloatArray = floatArrayOf(
        m[0], m[1], m[2], 0f, m[3] * 255f,
        m[4], m[5], m[6], 0f, m[7] * 255f,
        m[8], m[9], m[10], 0f, m[11] * 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)

    private fun scale(r: Float, g: Float, b: Float) = floatArrayOf(r, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, b, 0f)

    /** a * b for affine 3x4 matrices (b applied first). */
    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(12)
        for (row in 0..2) {
            for (col in 0..3) {
                var v = a[row * 4] * b[col] + a[row * 4 + 1] * b[4 + col] + a[row * 4 + 2] * b[8 + col]
                if (col == 3) v += a[row * 4 + 3]
                o[row * 4 + col] = v
            }
        }
        return o
    }
}
