package com.vidadomu.nexus.ui.models

import android.view.View
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.NexusHaptics

/** Three distinct moments of a drag, each with the richest effect the phone's Android version offers. */
internal object DragHaptics {
    /** The card lifts off the grid. */
    fun lift(view: View) = NexusHaptics(view).perform(NexusHapticEvent.GESTURE_START)

    /** The card settles into a new slot: a light tick. */
    fun slot(view: View) = NexusHaptics(view).perform(NexusHapticEvent.GESTURE_TICK)

    /** The card is dropped. */
    fun drop(view: View) = NexusHaptics(view).perform(NexusHapticEvent.GESTURE_END)
}
