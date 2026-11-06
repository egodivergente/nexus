package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.orders.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationMosaicTest {
    @Test fun runningMediaAndFinishedMediaBecomeGridSlots() {
        assertTrue(mosaicEligible(OrderStatus.PROCESSING, "IMAGE"))
        assertTrue(mosaicEligible(OrderStatus.SUBMITTING, "VIDEO"))
        // Keep the placeholder while the completed job's output is being downloaded.
        assertTrue(mosaicEligible(OrderStatus.COMPLETED, "IMAGE"))
        assertFalse(mosaicEligible(OrderStatus.FAILED, "IMAGE"))
        assertFalse(mosaicEligible(OrderStatus.PROCESSING, "AUDIO"))
    }

    @Test fun columnsAdaptToAvailableChatWidth() {
        assertEquals(2, mosaicColumns(360f))
        assertEquals(3, mosaicColumns(600f))
        assertEquals(4, mosaicColumns(900f))
    }
}
