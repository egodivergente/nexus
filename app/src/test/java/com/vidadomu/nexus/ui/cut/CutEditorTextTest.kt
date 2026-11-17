package com.vidadomu.nexus.ui.cut

import androidx.compose.runtime.State
import com.vidadomu.nexus.cut.model.FrameRate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutEditorTextTest {
    private val fps24 = FrameRate(24)

    @Test fun dragTextUsesSpanishDecimalsAndFramesBelowOneSecond() {
        assertEquals("1,50 s", CutDragText.seconds(1_500_000L))
        assertEquals("1:05,25", CutDragText.seconds(65_250_000L))
        assertEquals("+12 f", CutDragText.signed(500_000L, fps24))
        assertEquals("−1 f", CutDragText.signed(-41_667L, fps24))
        assertEquals("+2,00 s", CutDragText.signed(2_000_000L, fps24))
        assertEquals("3,00 s · −12 f", CutDragText.trim(3_000_000L, -500_000L, fps24))
        assertEquals("3,00 s", CutDragText.trim(3_000_000L, 0L, fps24))
        assertEquals("00:04 · +1,00 s · pista +1", CutDragText.move(4_000_000L, 1_000_000L, 1, fps24))
        assertEquals("Entrada 0,50 s", CutDragText.fade(500_000L, true))
    }

    @Test fun clockIsMinutesSecondsWithTheFrameApart() {
        assertEquals("00:00", CutDragText.Clock.short(-5L))
        assertEquals("01:05", CutDragText.Clock.short(65_400_000L))
        assertEquals("1:00:00", CutDragText.Clock.short(3_600_000_000L))
        assertEquals("00", CutDragText.Clock.frameInSecond(2_000_000L, fps24))
        assertEquals("12", CutDragText.Clock.frameInSecond(2_500_000L, fps24))
    }

    @Test fun turnSummaryComparesTheFirstBeforeWithTheLastAfter() {
        fun summary(d0: Long, d1: Long, c0: Long, c1: Long) = buildJsonObject {
            putJsonObject("duration_us") { put("before", d0); put("after", d1) }
            putJsonObject("clips") { put("before", c0); put("after", c1) }
        }
        val first = summary(20_000_000L, 18_000_000L, 6, 7)
        val last = summary(18_000_000L, 16_000_000L, 7, 5)
        assertEquals("Duración 20,00 s → 16,00 s · 6 → 5 clips", CutTurnSummary.describe(first, last))
        assertEquals("Comprobado en el montaje", CutTurnSummary.describe(summary(1, 1, 2, 2), summary(1, 1, 2, 2)))
        assertNull(CutTurnSummary.describe(null, last))
        assertEquals("Dividir clip", CutTurnSummary.headline(listOf("Dividir clip")))
        assertEquals("Dividir clip y 2 más", CutTurnSummary.headline(listOf("Dividir clip", "Velocidad", "Quitar silencios")))
    }

    @Test fun scrubMagnetFindsTheNearestCutAndDetectsCrossings() {
        val cuts = longArrayOf(1_000_000L, 4_000_000L, 9_000_000L)
        assertEquals(1_000_000L, nearestCut(cuts, 0L))
        assertEquals(4_000_000L, nearestCut(cuts, 3_000_000L))
        assertEquals(9_000_000L, nearestCut(cuts, 12_000_000L))
        assertNull(nearestCut(LongArray(0), 5L))
        assertTrue(crossesCut(cuts, 3_900_000L, 4_100_000L))
        assertTrue(crossesCut(cuts, 3_900_000L, 4_000_000L))
        assertTrue(crossesCut(cuts, 4_100_000L, 3_900_000L))
        assertFalse(crossesCut(cuts, 4_100_000L, 4_900_000L))
        assertFalse(crossesCut(cuts, 2_000_000L, 2_000_000L))
    }

    @Test fun thumbnailStepsArePowersOfTwoOfAQuarterSecond() {
        assertEquals(250_000L, thumbnailStepUs(1L))
        assertEquals(250_000L, thumbnailStepUs(250_000L))
        assertEquals(500_000L, thumbnailStepUs(250_001L))
        assertEquals(4_000_000L, thumbnailStepUs(3_100_000L))
    }

    @Test fun viewPreferencesStayWithinUsableLimits() {
        assertEquals(CutViewPrefs.MIN_TIMELINE_FRACTION, CutViewPrefs.clampFraction(0f))
        assertEquals(CutViewPrefs.MAX_TIMELINE_FRACTION, CutViewPrefs.clampFraction(2f))
        assertEquals(CutViewPrefs.DEFAULT_TIMELINE_FRACTION, CutViewPrefs.clampFraction(Float.NaN))
        assertEquals(MIN_PX_PER_S, CutViewPrefs.clampZoom(0f))
        assertEquals(MAX_PX_PER_S, CutViewPrefs.clampZoom(1e9f))
        assertEquals(CutViewPrefs.DEFAULT_PX_PER_SECOND, CutViewPrefs.clampZoom(Float.POSITIVE_INFINITY))
    }

    @Test fun headerAndAgentContextAreShortAndExact() {
        assertEquals("16:9", cutAspectLabel(1920, 1080))
        assertEquals("9:16", cutAspectLabel(1080, 1920))
        assertEquals("1000×700", cutAspectLabel(1000, 700))
        val at = object : State<Long> { override val value = 12_000_000L }
        assertEquals("Todo el montaje · 00:12", cutAgentContext(0, at))
        assertEquals("1 clip seleccionado · 00:12", cutAgentContext(1, at))
        assertEquals("3 clips seleccionados · 00:12", cutAgentContext(3, at))
    }

    @Test fun zoomStepsStopAtTheLimits() {
        val v = TimelineViewport(MAX_PX_PER_S / 1.2f)
        assertTrue(v.zoomBy(1.5f))
        assertEquals(MAX_PX_PER_S, v.pxPerSecond)
        assertFalse(v.zoomBy(1.5f))
    }
}
