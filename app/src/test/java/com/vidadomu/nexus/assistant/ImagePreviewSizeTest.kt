package com.vidadomu.nexus.assistant

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewSizeTest {
    private fun assertSize(aspect: Float?, width: Float, height: Float) {
        val size = imagePreviewSize(aspect)
        assertEquals("ancho para $aspect", width, size.width.value, 0.5f)
        assertEquals("alto para $aspect", height, size.height.value, 0.5f)
    }

    @Test fun `una foto vertical ocupa su propia caja, sin franjas a los lados`() {
        assertSize(4f / 5f, 288f, 360f)       // 1717 × 2146
        assertSize(9f / 16f, 202.5f, 360f)    // 2160 × 3840
    }

    @Test fun `una apaisada se limita por el ancho`() {
        assertSize(16f / 9f, 320f, 180f)
    }

    @Test fun `sin dimensiones o con valores raros usa 4 a 5 y limites razonables`() {
        assertSize(null, 288f, 360f)
        assertSize(Float.NaN, 288f, 360f)
        assertSize(0f, 288f, 360f)
        assertSize(10f, 320f, 106.7f)          // se limita a 3:1
        assertEquals(144f, imagePreviewSize(0.1f).width.value, 0.5f) // se limita a 0,4
        assertEquals(360f, imagePreviewSize(0.1f).height.value, 0.5f)
        assertEquals(100f, imagePreviewSize(1f, maxWidth = 100.dp).width.value, 0.5f)
    }
}
