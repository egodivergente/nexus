package com.vidadomu.nexus.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class NexusHapticEvent { SELECTION, TOGGLE_DECISIVE, CANCEL_CONFIRMED }

object NexusHapticPolicy {
    /**
     * [changed]: the user's gesture actually altered state (re-tapping the active option does not).
     * [accepted]: the change came from a decisive user action and took effect; programmatic sync,
     * passive navigation or a cancel the system refused stay silent.
     */
    fun shouldEmit(event: NexusHapticEvent, changed: Boolean, accepted: Boolean): Boolean = when (event) {
        NexusHapticEvent.SELECTION,
        NexusHapticEvent.TOGGLE_DECISIVE,
        NexusHapticEvent.CANCEL_CONFIRMED -> changed && accepted
    }

    /** REJECT is deliberately never returned: every event here signals success. */
    fun feedbackConstant(event: NexusHapticEvent, sdkInt: Int): Int = when (event) {
        NexusHapticEvent.SELECTION ->
            if (sdkInt >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        NexusHapticEvent.TOGGLE_DECISIVE,
        NexusHapticEvent.CANCEL_CONFIRMED ->
            if (sdkInt >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    }
}

@Stable
class NexusHaptics internal constructor(private val view: View) {
    /** Uses no flags, so the system "touch feedback" setting is honoured. Returns whether a tick was requested. */
    fun perform(event: NexusHapticEvent, changed: Boolean = true, accepted: Boolean = true): Boolean {
        if (!NexusHapticPolicy.shouldEmit(event, changed, accepted)) return false
        return view.performHapticFeedback(NexusHapticPolicy.feedbackConstant(event, Build.VERSION.SDK_INT))
    }
}

@Composable
fun rememberNexusHaptics(): NexusHaptics {
    val view = LocalView.current
    return remember(view) { NexusHaptics(view) }
}
