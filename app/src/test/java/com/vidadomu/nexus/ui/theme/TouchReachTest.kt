package com.vidadomu.nexus.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchReachTest {
    @Test fun aShortControlGetsTheMissingHeightMostlyBelow() {
        val reach = TouchReach()
        reach.measure(height = 60, minimum = 144, maxAbove = 18)
        assertEquals(84, reach.extra)
        assertEquals(18, reach.above)
        assertEquals(144, 60 + reach.extra)
    }

    @Test fun neverReachesAboveMoreThanHalfOfTheExtra() {
        val reach = TouchReach()
        reach.measure(height = 130, minimum = 144, maxAbove = 18)
        assertEquals(14, reach.extra)
        assertEquals(7, reach.above)
    }

    @Test fun aTallEnoughControlIsLeftAlone() {
        val reach = TouchReach()
        reach.measure(height = 150, minimum = 144, maxAbove = 18)
        assertEquals(0, reach.extra)
        assertEquals(0, reach.above)
    }
}
