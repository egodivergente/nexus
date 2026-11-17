package com.vidadomu.nexus.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView

enum class NexusHapticEvent {
    SELECTION, TOGGLE_DECISIVE, CANCEL_CONFIRMED, CONFIRMATION, ERROR,
    GESTURE_START, GESTURE_TICK, GESTURE_END,
    /** A dragged edge or clip locks onto a cut, marker or the playhead (only on entering the magnet). */
    SNAP,
    /** A gesture reached a hard limit: the media has no more frames, a clip cannot pass a neighbour, max zoom. */
    BOUNDARY,
    /** An object is picked up to be moved (long press on a clip). */
    PICK_UP,
}

object NexusHapticPolicy {
    /**
     * [changed]: the user's gesture actually altered state (re-tapping the active option does not).
     * [accepted]: the change came from a decisive user action and took effect; programmatic sync,
     * passive updates or a cancel the system refused stay silent. ERROR is only for an explicit user
     * action that failed; background/provider polling errors never generate feedback on their own.
     */
    fun shouldEmit(event: NexusHapticEvent, changed: Boolean, accepted: Boolean): Boolean = when (event) {
        NexusHapticEvent.SELECTION,
        NexusHapticEvent.TOGGLE_DECISIVE,
        NexusHapticEvent.CANCEL_CONFIRMED,
        NexusHapticEvent.CONFIRMATION,
        NexusHapticEvent.ERROR,
        NexusHapticEvent.GESTURE_START,
        NexusHapticEvent.GESTURE_TICK,
        NexusHapticEvent.GESTURE_END,
        NexusHapticEvent.SNAP,
        NexusHapticEvent.BOUNDARY,
        NexusHapticEvent.PICK_UP -> changed && accepted
    }

    /**
     * Minimum spacing between two identical events. Movement may cross many snap points or detents per
     * second; feedback is a sequence of distinct taps, never a continuous buzz.
     */
    fun minIntervalMs(event: NexusHapticEvent): Long = when (event) {
        NexusHapticEvent.GESTURE_TICK -> 70
        NexusHapticEvent.SNAP -> 90
        NexusHapticEvent.BOUNDARY -> 250
        else -> 0
    }

    fun throttled(event: NexusHapticEvent, nowMs: Long, lastMs: Long?): Boolean =
        lastMs != null && nowMs - lastMs in 0 until minIntervalMs(event)

    /** Success, failure and continuous gestures use distinct platform effects, with API 26 fallbacks. */
    fun feedbackConstant(event: NexusHapticEvent, sdkInt: Int): Int = when (event) {
        NexusHapticEvent.SELECTION ->
            if (sdkInt >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        NexusHapticEvent.TOGGLE_DECISIVE,
        NexusHapticEvent.CANCEL_CONFIRMED,
        NexusHapticEvent.CONFIRMATION ->
            if (sdkInt >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        NexusHapticEvent.ERROR ->
            if (sdkInt >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        NexusHapticEvent.GESTURE_START ->
            if (sdkInt >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.LONG_PRESS
        NexusHapticEvent.GESTURE_TICK ->
            if (sdkInt >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        NexusHapticEvent.GESTURE_END ->
            if (sdkInt >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CONTEXT_CLICK
        NexusHapticEvent.SNAP ->
            if (sdkInt >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
        NexusHapticEvent.BOUNDARY -> HapticFeedbackConstants.CONTEXT_CLICK
        NexusHapticEvent.PICK_UP ->
            if (sdkInt >= 34) HapticFeedbackConstants.DRAG_START else HapticFeedbackConstants.LONG_PRESS
    }

    internal fun allowsFeedback(appEnabled: Boolean, viewEnabled: Boolean) = appEnabled && viewEnabled
}

@Stable
class NexusHaptics internal constructor(private val view: View) {
    private val last = java.util.EnumMap<NexusHapticEvent, Long>(NexusHapticEvent::class.java)

    /** Uses no flags, so the system "touch feedback" setting is honoured. Returns whether a tick was requested. */
    fun perform(event: NexusHapticEvent, changed: Boolean = true, accepted: Boolean = true): Boolean {
        if (!isEnabled()) return false
        if (!NexusHapticPolicy.shouldEmit(event, changed, accepted)) return false
        val now = android.os.SystemClock.uptimeMillis()
        if (NexusHapticPolicy.throttled(event, now, last[event])) return false
        last[event] = now
        return view.performHapticFeedback(NexusHapticPolicy.feedbackConstant(event, Build.VERSION.SDK_INT))
    }

    internal fun isEnabled() = NexusHapticPolicy.allowsFeedback(
        com.vidadomu.nexus.settings.RuntimePreferences.get(view.context).bool("ui.haptics"),
        view.isHapticFeedbackEnabled
    )
}

@Composable
fun rememberNexusHaptics(): NexusHaptics {
    val view = LocalView.current
    return remember(view) { NexusHaptics(view) }
}

/** Material text selection and long-press controls use the same live app preference as custom gestures. */
@Composable
internal fun rememberNexusPlatformHaptics(platform: HapticFeedback): HapticFeedback {
    val haptics = rememberNexusHaptics()
    return remember(haptics, platform) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                when (hapticFeedbackType) {
                    HapticFeedbackType.LongPress -> haptics.perform(NexusHapticEvent.GESTURE_START)
                    HapticFeedbackType.TextHandleMove -> haptics.perform(NexusHapticEvent.GESTURE_TICK)
                    else -> if (haptics.isEnabled()) platform.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
}
