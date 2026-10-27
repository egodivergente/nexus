package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Direct resizing of a logo, with no settings screen: hold the logo until it lifts, then pinch (two fingers) or slide
 * one finger up/down. Ticks every 5 %, a firmer click at the default size (which also attracts the value, so going
 * back to the original is easy) and a bump at both limits.
 *
 * [onHold] tells the card a hold started on the logo, so the card does not also open its options menu.
 */
internal fun Modifier.logoResizeGesture(
    enabled: Boolean,
    key: Any,
    current: () -> Float,
    min: Float,
    max: Float,
    haptic: HapticFeedback,
    onHold: (Boolean) -> Unit,
    onAdjusting: (Boolean) -> Unit,
    onScale: (Float) -> Unit,
    onCommit: () -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onHold(true)
        val long = awaitLongPressOrCancellation(down.id)
        if (long == null) { onHold(false); return@awaitEachGesture }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onAdjusting(true)
        var value = current()
        var lastTick = (value * 20).roundToInt()
        var atLimit = false
        var snapped = abs(value - 1f) < .001f
        val heightPx = size.height.toFloat().coerceAtLeast(1f)
        var lastY = long.position.y
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                value *= event.calculateZoom()
            } else if (pressed.size == 1) {
                val y = pressed[0].position.y
                value *= 1f + (lastY - y) / heightPx * 1.6f
                lastY = y
            }
            if (pressed.size == 1) lastY = pressed[0].position.y
            var v = value.coerceIn(min, max)
            // Magnet at the default size.
            if (abs(v - 1f) < .04f) {
                if (!snapped) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); snapped = true }
                v = 1f
            } else snapped = false
            val hit = v <= min + .001f || v >= max - .001f
            if (hit && !atLimit) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            atLimit = hit
            val tick = (v * 20).roundToInt()
            if (tick != lastTick) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastTick = tick }
            onScale(v)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        onAdjusting(false)
        onHold(false)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onCommit()
    }
}
