package com.vidadomu.nexus.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientBackgroundPolicyTest {
    @Test fun galleryPreviewsNeverRequestAnIndependentClock() {
        for (mode in AmbientMode.entries) {
            assertFalse(ambientMotionRequested(mode, 1f, preview = true, powerSaving = false))
        }
    }

    @Test fun staticZeroAndInvalidIntensityCannotRunMotion() {
        assertFalse(ambientMotionRequested(AmbientMode.STATIC, 1f, false, false))
        for (value in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(ambientMotionRequested(AmbientMode.IMMERSIVE, value, false, false))
        }
        assertTrue(ambientMotionRequested(AmbientMode.SUBTLE, .55f, false, false))
    }

    @Test fun powerSavingStopsBothAnimatedModes() {
        for (mode in listOf(AmbientMode.SUBTLE, AmbientMode.IMMERSIVE)) {
            assertFalse(ambientMotionRequested(mode, 1f, false, true))
        }
    }

    @Test fun brightnessRemainsBoundedForEveryModeAndLightOrDarkTheme() {
        for (mode in AmbientMode.entries) for (dark in listOf(false, true)) {
            assertEquals(0f, ambientOpacity(mode, -1f, dark), 0f)
            assertEquals(0f, ambientOpacity(mode, Float.NaN, dark), 0f)
            assertEquals(0f, ambientOpacity(mode, Float.POSITIVE_INFINITY, dark), 0f)
            assertEquals(ambientOpacity(mode, 1f, dark), ambientOpacity(mode, 10f, dark), 0f)
            assertTrue(ambientOpacity(mode, 1f, dark) <= if (dark) .38f else .30f)
            assertEquals(ambientOpacity(mode, 1f, dark) * .5f, ambientOpacity(mode, .5f, dark), .0001f)
        }
    }

    @Test fun immersiveHasMorePresenceWithoutExceedingTheReadabilityLimit() {
        for (dark in listOf(false, true)) {
            assertTrue(ambientOpacity(AmbientMode.IMMERSIVE, .55f, dark) > ambientOpacity(AmbientMode.SUBTLE, .55f, dark))
            assertTrue(ambientOpacity(AmbientMode.SUBTLE, .55f, dark) > ambientOpacity(AmbientMode.STATIC, .55f, dark))
        }
    }

    @Test fun phaseIsContinuousAcrossTheOldCycleBoundaryAndNeverNegative() {
        assertEquals(0f, ambientPhase(-1L), 0f)
        assertEquals(.5f, ambientPhase(AMBIENT_CYCLE_MS / 2), .00001f)
        assertEquals(1f, ambientPhase(AMBIENT_CYCLE_MS), 0f)
        assertTrue(ambientPhase(AMBIENT_CYCLE_MS + 1L)-ambientPhase(AMBIENT_CYCLE_MS-1L) < .001f)
        assertTrue(ambientPhase(Long.MAX_VALUE).isFinite() && ambientPhase(Long.MAX_VALUE)>=0f)
        assertTrue(AMBIENT_FRAME_INTERVAL_MS >= 80L)
    }

    @Test fun aPersonalPhotoKeepsItsReadabilityVeilAtEveryIntensity() {
        for (intensity in listOf(-1f, 0f, .5f, 1f, 10f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val stops = photoVeilStops(intensity)
            assertEquals(3, stops.size)
            assertTrue(stops.all { it.isFinite() && it in PHOTO_VEIL_MINIMUM..1f })
            assertTrue(stops.first() >= stops[1])
            assertTrue(stops.last() >= stops[1])
        }
    }
}
