package com.vidadomu.nexus.ui.theme

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusHapticsTest {
    @Test
    fun emitsOnlyWhenChangedAndAccepted() {
        NexusHapticEvent.values().forEach { event ->
            assertTrue(NexusHapticPolicy.shouldEmit(event, changed = true, accepted = true))
            assertFalse(NexusHapticPolicy.shouldEmit(event, changed = false, accepted = true))
            assertFalse(NexusHapticPolicy.shouldEmit(event, changed = true, accepted = false))
            assertFalse(NexusHapticPolicy.shouldEmit(event, changed = false, accepted = false))
        }
    }

    @Test
    fun reselectingActiveOptionIsSilent() {
        assertFalse(NexusHapticPolicy.shouldEmit(NexusHapticEvent.SELECTION, changed = false, accepted = true))
    }

    @Test
    fun automaticToggleIsSilent() {
        assertFalse(NexusHapticPolicy.shouldEmit(NexusHapticEvent.TOGGLE_DECISIVE, changed = true, accepted = false))
    }

    @Test
    fun cancelWithoutEffectIsSilent() {
        assertFalse(NexusHapticPolicy.shouldEmit(NexusHapticEvent.CANCEL_CONFIRMED, changed = false, accepted = false))
        assertFalse(NexusHapticPolicy.shouldEmit(NexusHapticEvent.CANCEL_CONFIRMED, changed = true, accepted = false))
    }

    @Test
    fun selectionUsesTicks() {
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, NexusHapticPolicy.feedbackConstant(NexusHapticEvent.SELECTION, 26))
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, NexusHapticPolicy.feedbackConstant(NexusHapticEvent.SELECTION, 34))
    }

    @Test
    fun confirmationsUseConfirmFromApi30AndKeyboardTapBefore() {
        listOf(NexusHapticEvent.TOGGLE_DECISIVE, NexusHapticEvent.CANCEL_CONFIRMED).forEach { event ->
            assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, NexusHapticPolicy.feedbackConstant(event, 29))
            assertEquals(HapticFeedbackConstants.CONFIRM, NexusHapticPolicy.feedbackConstant(event, 30))
        }
    }

    @Test
    fun rejectIsNeverUsedForSuccess() {
        NexusHapticEvent.values().forEach { event ->
            listOf(26, 29, 30, 33, 34, 36).forEach { sdk ->
                assertNotEquals(HapticFeedbackConstants.REJECT, NexusHapticPolicy.feedbackConstant(event, sdk))
            }
        }
    }
}
