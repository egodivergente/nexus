package com.vidadomu.nexus.ui.models

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Three distinct moments of a drag, each with the richest effect the phone's Android version offers. */
internal object DragHaptics {
    /** The card lifts off the grid. */
    fun lift(view: View) = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.LONG_PRESS)

    /** The card settles into a new slot: a light tick. */
    fun slot(view: View) = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** The card is dropped. */
    fun drop(view: View) = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CONTEXT_CLICK)
}
