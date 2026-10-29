package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
/** Set while a finger rests on a logo or name, so a pinch there resizes the logo instead of changing the grid. */
internal object GestureLock { @Volatile var onCard = false }

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
    /** Si se indica, un dedo mueve el logo dentro de su hueco (y solo el pellizco cambia el tamaño). */
    placement: (() -> Pair<Float, Float>)? = null,
    onMove: ((Float, Float) -> Unit)? = null,
    pxPerDp: () -> Float = { 1f },
): Modifier = if (!enabled) this else this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        try {
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
        if (start != true) return@awaitEachGesture
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.START)
        onAdjusting(true)
        var value = current()
        var lastTick = (value * 20).roundToInt()
        var atLimit = false
        var snapped = abs(value - 1f) < .001f
        val heightPx = size.height.toFloat().coerceAtLeast(1f)
        var lastY = down.position.y
        var lastPos = down.position
        val k = pxPerDp()
        val startPlace = placement?.invoke() ?: (0f to 0f)
        var moveX = startPlace.first * k
        var moveY = startPlace.second * k
        var nearCenter = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            var feedback: com.vidadomu.nexus.ui.theme.NexusVibration.Kind? = null
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                value *= event.calculateZoom()
            } else if (pressed.size == 1) {
                val pos = pressed[0].position
                if (onMove != null) {
                    moveX = (moveX + (pos.x - lastPos.x)).coerceIn(-400f, 400f)
                    moveY = (moveY + (pos.y - lastPos.y)).coerceIn(-400f, 400f)
                    val near = abs(moveX) < 8f && abs(moveY) < 8f
                    if (near && !nearCenter) feedback = com.vidadomu.nexus.ui.theme.NexusVibration.Kind.TICK
                    nearCenter = near
                    onMove(moveX / k, moveY / k)
                } else {
                    value *= 1f + (lastY - pos.y) / heightPx * 1.6f
                }
                lastY = pos.y
            }
            if (pressed.size == 1) { lastY = pressed[0].position.y; lastPos = pressed[0].position }
            var v = value.coerceIn(min, max)
            // Magnet at the default size.
            if (abs(v - 1f) < .04f) {
                if (!snapped) { feedback = com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK; snapped = true }
                v = 1f
            } else snapped = false
            val hit = v <= min + .001f || v >= max - .001f
            if (hit && !atLimit) feedback = com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK
            atLimit = hit
            val tick = (v * 20).roundToInt()
            if (tick != lastTick) {
                if (feedback == null) feedback = com.vidadomu.nexus.ui.theme.NexusVibration.Kind.TICK
                lastTick = tick
            }
            onScale(v)
            feedback?.let(vibrate)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.END)
        onCommit()
        } finally {
            // Navigation or pointer-input cancellation must not leave the shared grid gesture locked.
            onAdjusting(false)
            onHold(false)
        }
    }
}

/**
 * Free placement: hold the element until it lifts, then drag it anywhere (it follows the finger). Vibrates when it
 * lifts and when it lands, and a soft tick when it passes through its original position.
 */
internal fun Modifier.holdAndMoveGesture(
    key: Any,
    current: () -> Pair<Float, Float>,
    vibrate: (com.vidadomu.nexus.ui.theme.NexusVibration.Kind) -> Unit,
    onHold: (Boolean) -> Unit,
    onMoving: (Boolean) -> Unit,
    onMove: (Float, Float) -> Unit,
    onCommit: () -> Unit,
    limitPx: Float = 600f,
    pxPerDp: () -> Float,
): Modifier = this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        try {
        onHold(true)
        val slop = viewConfiguration.touchSlop
        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val c = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!c.pressed || c.isConsumed || (c.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (held != null) return@awaitEachGesture
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.START)
        onMoving(true)
        val start = current()
        val k = pxPerDp()
        var lastPos = down.position
        var x = start.first * k
        var y = start.second * k
        var nearOrigin = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val c = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
            x = (x + (c.position.x - lastPos.x)).coerceIn(-limitPx, limitPx)
            y = (y + (c.position.y - lastPos.y)).coerceIn(-limitPx, limitPx)
            lastPos = c.position
            val near = abs(x) < 8f && abs(y) < 8f
            if (near && !nearOrigin) vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.TICK)
            nearOrigin = near
            onMove(x / k, y / k)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.END)
        onCommit()
        } finally {
            onMoving(false)
            onHold(false)
        }
    }
}

/**
 * Pinch the grid to change how many columns it shows: spreading the fingers makes cards bigger (fewer columns),
 * pinching them closer shows more. Vibrates on every change. One finger never takes part, so scrolling is untouched.
 */
internal fun Modifier.pinchColumns(columns: Int, max: Int, onColumns: (Int) -> Unit): Modifier = composed {
    val view = androidx.compose.ui.platform.LocalView.current
    val current = androidx.compose.runtime.rememberUpdatedState(columns)
    pointerInput(max) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var accumulated = 1f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
                if (!GestureLock.onCard && event.changes.count { it.pressed } >= 2) {
                    accumulated *= event.calculateZoom()
                    val next = when {
                        accumulated < .82f -> (current.value + 1).coerceAtMost(max)
                        accumulated > 1.22f -> (current.value - 1).coerceAtLeast(1)
                        else -> current.value
                    }
                    if (next != current.value) {
                        onColumns(next)
                        com.vidadomu.nexus.ui.theme.NexusVibration.play(view, com.vidadomu.nexus.ui.theme.NexusVibration.Kind.TICK)
                        accumulated = 1f
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}
