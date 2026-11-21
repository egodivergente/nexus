package com.vidadomu.nexus.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusTextScaleTest {
    @Test
    fun theOldDoubleScaleBecomesOneScaleWithinTheRange() {
        // The S25: Android at 0,8 and Nexus at 0,8 drew 0,64; the single scale cannot go below the minimum.
        assertEquals(NexusTextScale.MIN, NexusTextScale.migrate(.8f, .8f), 0f)
        assertEquals(1.1f, NexusTextScale.migrate(1.1f, 1f), 1e-6f)
        assertEquals(NexusTextScale.MAX, NexusTextScale.migrate(1.4f, 1.3f), 0f)
    }

    @Test
    fun onlyStylesThatWouldFallBelowTheFloorGrow() {
        assertEquals(16f, NexusTextScale.flooredSp(16f, .8f), 0f)
        assertEquals(11f / .8f, NexusTextScale.flooredSp(12f, .8f), 1e-4f)
        assertEquals(12f, NexusTextScale.flooredSp(12f, 1f), 0f)
    }

    @Test
    fun noNexusStyleIsDrawnBelowElevenSpAtAnyScale() {
        val base = nexusTypography(FontFamily.Serif, FontFamily.SansSerif)
        listOf(.5f, .64f, .8f, .9f, 1f, 1.4f, 2f).forEach { scale ->
            val t = base.withReadableFloor(scale)
            listOf(t.displayLarge, t.headlineMedium, t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium,
                t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall).forEach { style ->
                assertTrue("$scale: ${style.fontSize}", style.fontSize.value * scale >= NexusTextScale.FLOOR_SP - 1e-3f)
                // The line still fits the letters it holds.
                assertTrue("$scale: ${style.lineHeight} < ${style.fontSize}", style.lineHeight.value >= style.fontSize.value)
            }
        }
    }

    @Test
    fun largerTextKeepsItsHierarchy() {
        val t = nexusTypography(FontFamily.Serif, FontFamily.SansSerif).withReadableFloor(.8f)
        assertTrue(t.titleLarge.fontSize.value > t.bodyLarge.fontSize.value)
        assertTrue(t.bodyLarge.fontSize.value > t.labelSmall.fontSize.value)
    }
}
