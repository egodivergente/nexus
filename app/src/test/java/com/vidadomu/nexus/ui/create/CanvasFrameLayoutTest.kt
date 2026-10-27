package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.MediaSlotType
import org.junit.Assert.*
import org.junit.Test

class CanvasFrameLayoutTest {
    private val fields = listOf(
        CanvasInputField("start", "Inicio", "image/*", true, 1, MediaSlotType.PRIMARY_IMAGE),
        CanvasInputField("end", "Final", "image/*", false, 1, MediaSlotType.LAST_FRAME))
    @Test fun availableWidthRatherThanDeviceNameControlsPairing() {
        assertTrue(CanvasInputPlan.pairedFrames(fields, null, 360f, 1f))
        assertFalse(CanvasInputPlan.pairedFrames(fields, null, 280f, 1f))
        assertFalse(CanvasInputPlan.pairedFrames(fields, null, 360f, 1.5f))
    }
    @Test fun masksAndMultipleReferencesDoNotBecomeFramePairs() {
        assertFalse(CanvasInputPlan.pairedFrames(fields.map { it.copy(slot = MediaSlotType.REFERENCE_IMAGE) }, null, 360f, 1f))
        assertFalse(CanvasInputPlan.pairedFrames(fields.map { it.copy(maximum = 4) }, null, 360f, 1f))
    }
}
