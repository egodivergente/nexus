package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
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
    vibrate: (com.vidadomu.nexus.ui.theme.NexusVibration.Kind) -> Unit,
    onHold: (Boolean) -> Unit,
    onAdjusting: (Boolean) -> Unit,
    onScale: (Float) -> Unit,
    onCommit: () -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onHold(true)
        val slop = viewConfiguration.touchSlop
        // true → start adjusting (second finger landed, or the hold completed); false → a tap or a scroll, do nothing.
        var start: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) return@withTimeoutOrNull false
                if (pressed.size >= 2) { event.changes.forEach { it.consume() }; return@withTimeoutOrNull true }
                val first = pressed[0]
                if (first.isConsumed || (first.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (start == null) start = true
        if (start != true) { onHold(false); return@awaitEachGesture }
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK)
        onAdjusting(true)
        var value = current()
        var lastTick = (value * 20).roundToInt()
        var atLimit = false
        var snapped = abs(value - 1f) < .001f
        val heightPx = size.height.toFloat().coerceAtLeast(1f)
        var lastY = down.position.y
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
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
                if (!snapped) { vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK); snapped = true }
                v = 1f
            } else snapped = false
            val hit = v <= min + .001f || v >= max - .001f
            if (hit && !atLimit) vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK)
            atLimit = hit
            val tick = (v * 20).roundToInt()
            if (tick != lastTick) { vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.TICK); lastTick = tick }
            onScale(v)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        onAdjusting(false)
        onHold(false)
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK)
        onCommit()
    }
}
