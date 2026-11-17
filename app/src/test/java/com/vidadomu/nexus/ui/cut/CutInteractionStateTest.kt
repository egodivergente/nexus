package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.engine.PreviewScrubState
import org.junit.Assert.*
import org.junit.Test

class CutInteractionStateTest {
    @Test fun scrubPreservesTransportIntentUntilReleaseOrExplicitPause() {
        val state = PreviewScrubState()
        state.begin(true); state.begin(false)
        assertTrue(state.finish())
        assertFalse(state.finish())
        state.begin(false); assertFalse(state.finish())
        state.begin(true); state.cancel(); assertFalse(state.finish())
    }
    @Test fun panelsStayWithinTheScreenAtSmallSizesAndWhileTyping() {
        for (height in listOf(100f, 220f, 480f, 900f)) for (compact in listOf(true, false)) {
            val p = cutPanelSizes(height, .8f, .7f, true, compact)
            assertTrue(p.preview >= 0 && p.timeline >= 0 && p.chat >= 0)
            assertEquals(height, p.preview + p.timeline + p.chat, .01f)
        }
    }
    @Test fun hidingAgentReturnsItsSpaceToPreviewAndTimeline() {
        val visible = cutPanelSizes(600f, .4f, .3f, true, false)
        val hidden = cutPanelSizes(600f, .4f, .3f, false, false)
        assertEquals(0f, hidden.chat, 0f)
        assertTrue(hidden.preview > visible.preview && hidden.timeline > visible.timeline)
    }
    @Test fun automaticSplitGivesThePictureTheHeightItsAspectNeeds() {
        // Pixel 8 Pro en vertical: 448 dp de ancho, 805 dp de edición.
        val wide = cutPanelSizes(805f, null, .4f, false, false, frameFitDp = 448f * 9 / 16)
        assertEquals(252f, wide.preview, .5f)          // 16:9 a todo el ancho, sin franjas negras
        assertEquals(805f, wide.preview + wide.timeline, .01f)
        val tall = cutPanelSizes(805f, null, .4f, false, false, frameFitDp = 448f * 16 / 9)
        assertEquals(805f * .62f, tall.preview, .5f)   // 9:16 no se come la línea de tiempo
        val square = cutPanelSizes(805f, null, .4f, false, false, frameFitDp = 448f)
        assertEquals(448f, square.preview, .5f)
        // Con el agente abierto el reparto automático se hace sobre lo que queda.
        val withAgent = cutPanelSizes(805f, null, .4f, true, false, frameFitDp = 252f)
        assertEquals(252f, withAgent.preview, .5f)
        assertEquals(805f, withAgent.preview + withAgent.timeline + withAgent.chat, .01f)
        // Sin datos de proporción no se rompe: reparto por defecto.
        val unknown = cutPanelSizes(805f, null, .4f, false, false)
        assertEquals(805f * CutViewPrefs.DEFAULT_TIMELINE_FRACTION, unknown.timeline, .5f)
        // Un reparto elegido a mano manda sobre el automático.
        assertEquals(805f * .5f, cutPanelSizes(805f, .5f, .4f, false, false, frameFitDp = 252f).timeline, .5f)
    }
    @Test fun tracksGrowIntoSpareHeightButNeverShrink() {
        // 2 de vídeo + 2 de audio = 184 dp de filas.
        assertEquals(1f, timelineRowScale(210f, 2, 2, 0), 0f)          // justo: tamaño base
        assertEquals(1.5f, timelineRowScale(553f, 2, 2, 0), 0f)        // sobra mucho: tope 1,5×
        val mid = timelineRowScale(300f, 2, 2, 0)
        assertTrue(mid > 1f && mid < 1.5f)
        assertEquals(mid, Math.round(mid * 20f) / 20f, 1e-4f)          // por pasos de 0,05
        assertEquals(1f, timelineRowScale(120f, 4, 4, 0), 0f)          // no caben: no encogen (se desplaza)
        assertEquals(1f, timelineRowScale(500f, 0, 0, 3), 0f)          // solo pistas plegadas
        assertEquals(1f, timelineRowScale(Float.NaN, 2, 2, 0), 0f)
    }
    @Test fun emptyTracksStayLowAndLeaveTheirHeightToTracksWithClips() {
        // Montaje típico: V1 y A1 con clips, V2 y A2 vacías. Antes las cuatro crecían por igual (1,3×).
        val allFull = timelineRowScale(300f, 2, 2, 0)
        val twoEmpty = timelineRowScale(300f, 1, 1, 0, emptyRows = 2)
        assertEquals(1.3f, allFull, 1e-4f)
        assertEquals(1.5f, twoEmpty, 0f)
        // Con poco alto las vacías no quitan sitio de más: 2 × 30 dp en vez de 2 × 46 dp.
        assertEquals(1f, timelineRowScale(150f, 1, 1, 0, emptyRows = 2), 0f)
        assertEquals(1f, timelineRowScale(500f, 0, 0, 0, emptyRows = 4), 0f)   // todo vacío: tamaño base
    }
    @Test fun initialZoomShowsShortMontagesWholeAndLongOnesCloseEnoughToEdit() {
        val width = 396f
        assertEquals(CutViewPrefs.DEFAULT_PX_PER_SECOND, defaultTimelineZoom(width, 1_000_000L)!!, 0f)   // 1 s: tope de acercamiento
        val five = defaultTimelineZoom(width, 5_000_000L)!!
        assertEquals(width / 2f * .92f / 5f, five, .01f)                                               // 5 s: entero a la vista
        val long = defaultTimelineZoom(width, 120_000_000L)!!
        assertEquals(width / 2f * .92f / 8f, long, .01f)                                               // 2 min: ~8 s por media pantalla
        assertTrue(fitTimelineZoom(width, 120_000_000L)!! < long)                                      // «Ver todo» aleja más
        assertNull(defaultTimelineZoom(width, 0L))
        assertNull(defaultTimelineZoom(0f, 5_000_000L))
    }
    @Test fun fitUsesTheLongerSideOfThePlayheadSoTheMontageFillsTheWidth() {
        val width = 396f
        val d = 30_000_000L
        val blind = fitTimelineZoom(width, d)!!
        assertEquals(blind, fitTimelineZoom(width, d, 0L)!!, .001f)              // cabezal al principio: media pantalla
        assertEquals(blind, fitTimelineZoom(width, d, d)!!, .001f)
        assertEquals(blind * 2f, fitTimelineZoom(width, d, d / 2)!!, .001f)      // en el centro: el doble de grande
        assertEquals(width / 2f * .92f / 20f, fitTimelineZoom(width, d, 10_000_000L)!!, .001f)
        assertEquals(blind, fitTimelineZoom(width, d, 99_000_000L)!!, .001f)     // cabezal fuera de rango: se acota
        assertNull(fitTimelineZoom(width, 0L, 0L))
    }
    @Test fun zoomAndPanAreBoundedAndZoomingBackFitsThePicture() {
        val zoomed = CutPreviewTransform().transform(2f, 0f, 0f, 0f, 0f, 400f, 300f)
        assertEquals(2f, zoomed.scale, 0f)
        val panned = zoomed.transform(1f, 10000f, -10000f, 0f, 0f, 400f, 300f)
        assertEquals(200f, panned.x, 0f); assertEquals(-150f, panned.y, 0f)
        assertEquals(CutPreviewTransform(), panned.transform(.1f, 0f, 0f, 0f, 0f, 400f, 300f))
    }
}
