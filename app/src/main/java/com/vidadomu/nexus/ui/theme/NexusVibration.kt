package com.vidadomu.nexus.ui.theme

import android.view.View

/**
 * Adapter for existing logo gesture callbacks. All feedback uses the same policy and Android touch-feedback
 * setting as the rest of the app; gestures must never bypass a user's system intensity or disabled feedback.
 */
object NexusVibration {
    enum class Kind { TICK, CLICK, HEAVY, START, END }

    internal fun eventFor(kind: Kind): NexusHapticEvent = when (kind) {
        Kind.TICK -> NexusHapticEvent.GESTURE_TICK
        Kind.CLICK, Kind.HEAVY -> NexusHapticEvent.CONFIRMATION
        Kind.START -> NexusHapticEvent.GESTURE_START
        Kind.END -> NexusHapticEvent.GESTURE_END
    }

    fun play(view: View, kind: Kind) {
        NexusHaptics(view).perform(eventFor(kind))
    }
}
