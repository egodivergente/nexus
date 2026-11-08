package com.vidadomu.nexus.ui.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlowLiveGeometryTest {
    @Test fun `el fotograma con la misma proporcion llena la caja`() {
        val r = FlowLiveGeometry.fit(1080f, 2000f, 540, 1000)
        assertEquals(0f, r.left, 0.01f); assertEquals(1080f, r.width, 0.01f); assertEquals(2000f, r.height, 0.01f)
    }

    @Test fun `un fotograma mas ancho queda arriba y centrado`() {
        val r = FlowLiveGeometry.fit(1000f, 2000f, 2000, 1000)
        assertEquals(0f, r.top, 0.01f); assertEquals(500f, r.height, 0.01f); assertEquals(1000f, r.width, 0.01f)
    }

    @Test fun `un toque se traduce a la pagina deshaciendo el zoom local`() {
        val r = FrameRect(0f, 0f, 1000f, 2000f)
        val (x, y) = FlowLiveGeometry.normalize(500f, 1000f, r, 1f, 0f, 0f)!!
        assertEquals(0.5f, x, 1e-4f); assertEquals(0.5f, y, 1e-4f)
        // Zoom x2 centrado en el origen y desplazado -500,-1000: el centro de la pantalla es el punto (0.5, 0.5) de la página.
        val (zx, zy) = FlowLiveGeometry.normalize(500f, 1000f, r, 2f, -500f, -1000f)!!
        assertEquals(0.5f, zx, 1e-4f); assertEquals(0.5f, zy, 1e-4f)
    }

    @Test fun `fuera del fotograma no hay toque`() {
        assertNull(FlowLiveGeometry.normalize(10f, 900f, FrameRect(0f, 0f, 1000f, 500f), 1f, 0f, 0f))
    }

    @Test fun `desplazamiento y limites del zoom`() {
        assertEquals(1.024f, FlowLiveGeometry.cssPerPixel(1024, FrameRect(0f, 0f, 1000f, 2000f), 1f), 1e-4f)
        assertEquals(0.512f, FlowLiveGeometry.cssPerPixel(1024, FrameRect(0f, 0f, 1000f, 2000f), 2f), 1e-4f)
        assertEquals(-1000f, FlowLiveGeometry.clampPan(-5000f, 1000f, 2f), 0.01f)
        assertEquals(0f, FlowLiveGeometry.clampPan(300f, 1000f, 2f), 0.01f)
        assertEquals(412, FlowLiveGeometry.pageWidth("mobile", 0, 412f))
        assertEquals(1024, FlowLiveGeometry.pageWidth("desktop", 1024, 412f))
    }
}
