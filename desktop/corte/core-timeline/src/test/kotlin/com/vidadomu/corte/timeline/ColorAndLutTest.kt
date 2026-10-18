package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.ColorProps
import com.vidadomu.corte.timeline.render.ColorMath
import com.vidadomu.corte.timeline.render.CubeLut
import com.vidadomu.corte.timeline.render.CubeLutException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ColorAndLutTest {
    private fun apply(c: ColorProps, r: Float, g: Float, b: Float): FloatArray {
        val out = FloatArray(3)
        ColorMath.apply(ColorMath.matrix(c)!!, r, g, b, out)
        return out
    }

    @Test fun neutralHasNoMatrix() {
        assertNull(ColorMath.matrix(ColorProps()))
        // Fields that are not rendered do not create a matrix either.
        assertNull(ColorMath.matrix(ColorProps(highlights = 0.5f, sharpen = 1f)))
    }

    @Test fun exposureOneStopDoublesAndClamps() {
        val o = apply(ColorProps(exposure = 1f), 0.2f, 0.3f, 0.6f)
        assertArrayEquals(floatArrayOf(0.4f, 0.6f, 1f), o, 1e-5f)
    }

    @Test fun fullDesaturationGivesRec709Grey() {
        val o = apply(ColorProps(saturation = -1f), 1f, 0f, 0f)
        assertEquals(0.2126f, o[0], 1e-5f); assertEquals(o[0], o[1], 1e-6f); assertEquals(o[0], o[2], 1e-6f)
    }

    @Test fun contrastPivotsAroundMidGrey() {
        val c = ColorProps(contrast = 0.5f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), apply(c, 0.5f, 0.5f, 0.5f), 1e-5f)
        assertEquals(0.65f, apply(c, 0.6f, 0.6f, 0.6f)[0], 1e-5f)
    }

    @Test fun fadeLiftsBlackAndKeepsWhite() {
        val c = ColorProps(fade = 1f)
        assertEquals(0.25f, apply(c, 0f, 0f, 0f)[0], 1e-5f)
        assertEquals(1f, apply(c, 1f, 1f, 1f)[0], 1e-5f)
    }

    @Test fun warmTemperatureRaisesRedLowersBlue() {
        val o = apply(ColorProps(temperature = 1f), 0.5f, 0.5f, 0.5f)
        assertTrue(o[0] > 0.5f && o[2] < 0.5f && Math.abs(o[1] - 0.5f) < 1e-5f)
    }

    @Test fun matrixLayoutsAgree() {
        val m = ColorMath.matrix(ColorProps(exposure = 0.3f, saturation = 0.4f, temperature = -0.2f, fade = 0.1f))!!
        val gl = ColorMath.toColumnMajor4x4(m)
        val an = ColorMath.toAndroid4x5(m)
        val (r, g, b) = Triple(0.3f, 0.5f, 0.7f)
        val ref = FloatArray(3).also { ColorMath.apply(m, r, g, b, it) }
        for (row in 0..2) {
            val viaGl = gl[row] * r + gl[4 + row] * g + gl[8 + row] * b + gl[12 + row]
            val viaAndroid = (an[row * 5] * r * 255 + an[row * 5 + 1] * g * 255 + an[row * 5 + 2] * b * 255 + an[row * 5 + 4]) / 255
            assertEquals(ref[row], viaGl.coerceIn(0f, 1f), 1e-5f)
            assertEquals(ref[row], viaAndroid.coerceIn(0f, 1f), 1e-5f)
        }
    }

    private val identity2 = """
        TITLE "id"
        # comment
        LUT_3D_SIZE 2
        0 0 0
        1 0 0
        0 1 0
        1 1 0
        0 0 1
        1 0 1
        0 1 1
        1 1 1
    """.trimIndent()

    @Test fun parsesCubeAndSamplesTrilinearly() {
        val lut = CubeLut.parse(identity2)
        assertEquals(2, lut.size); assertEquals("id", lut.title)
        val o = FloatArray(3)
        lut.sample(0.25f, 0.5f, 0.75f, o)
        assertArrayEquals(floatArrayOf(0.25f, 0.5f, 0.75f), o, 1e-6f)
        // Red varies fastest in the file.
        assertEquals(1f, lut.at(1, 0, 0, 0), 0f); assertEquals(0f, lut.at(1, 0, 0, 2), 0f)
    }

    @Test fun invertLutInverts() {
        val inv = identity2.lines().joinToString("\n") { l ->
            val p = l.trim().split(" ")
            if (p.size == 3 && p.all { it.toFloatOrNull() != null }) p.joinToString(" ") { (1 - it.toFloat()).toString() } else l
        }
        val o = FloatArray(3)
        CubeLut.parse(inv).sample(0.2f, 0.6f, 1f, o)
        assertArrayEquals(floatArrayOf(0.8f, 0.4f, 0f), o, 1e-5f)
    }

    @Test fun domainIsNormalised() {
        // Domain [0, 2]: input 1.0 is the middle of the grid, so an identity grid outputs 0.5.
        val lut = CubeLut.parse("DOMAIN_MIN 0 0 0\nDOMAIN_MAX 2 2 2\n" + identity2)
        val o = FloatArray(3)
        lut.sample(1f, 1f, 1f, o)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), o, 1e-5f)
    }

    @Test fun rejectsBrokenFiles() {
        fun bad(text: String, needle: String) {
            try { CubeLut.parse(text); fail("accepted: $text") } catch (e: CubeLutException) {
                assertTrue("«${e.message}» should mention $needle", e.message!!.contains(needle))
            }
        }
        bad("LUT_1D_SIZE 16\n0 0 0", "1D")
        bad("0 0 0", "LUT_3D_SIZE")
        bad("LUT_3D_SIZE 2\n0 0 0\n1 1 1", "Faltan entradas")
        bad("LUT_3D_SIZE 2\n0 0 x", "no es un número")
        bad("LUT_3D_SIZE 99", "no admitido")
        bad("", "falta LUT_3D_SIZE")
    }

    @Test fun identityFactoryIsIdentity() {
        val o = FloatArray(3)
        CubeLut.identity(17).sample(0.123f, 0.456f, 0.789f, o)
        assertArrayEquals(floatArrayOf(0.123f, 0.456f, 0.789f), o, 1e-5f)
    }
}
