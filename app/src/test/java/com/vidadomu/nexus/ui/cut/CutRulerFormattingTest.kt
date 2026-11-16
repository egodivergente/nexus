package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.model.FrameRate
import org.junit.Assert.assertEquals
import org.junit.Test

class CutRulerFormattingTest {
    @Test fun secondScaleRulerLabelsAreZeroPaddedTabularMinutesSeconds() {
        val fps24 = FrameRate(24)
        assertEquals("00:00", formatTimelineRulerLabel(0L, 1_000_000.0, fps24))
        assertEquals("00:05", formatTimelineRulerLabel(5_000_000L, 2_000_000.0, fps24))
        assertEquals("01:05", formatTimelineRulerLabel(65_000_000L, 5_000_000.0, fps24))
        assertEquals("1:00:05", formatTimelineRulerLabel(3_605_000_000L, 60_000_000.0, fps24))
    }

    @Test fun subSecondRulerLabelsIncludeFrames() {
        val fps24 = FrameRate(24)
        assertEquals("00:00:00", formatTimelineRulerLabel(0L, 250_000.0, fps24))
        assertEquals("00:01:12", formatTimelineRulerLabel(1_500_000L, 250_000.0, fps24))
    }
}
