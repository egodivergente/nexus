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
    @Test fun zoomAndPanAreBoundedAndZoomingBackFitsThePicture() {
        val zoomed = CutPreviewTransform().transform(2f, 0f, 0f, 0f, 0f, 400f, 300f)
        assertEquals(2f, zoomed.scale, 0f)
        val panned = zoomed.transform(1f, 10000f, -10000f, 0f, 0f, 400f, 300f)
        assertEquals(200f, panned.x, 0f); assertEquals(-150f, panned.y, 0f)
        assertEquals(CutPreviewTransform(), panned.transform(.1f, 0f, 0f, 0f, 0f, 400f, 300f))
    }
}
