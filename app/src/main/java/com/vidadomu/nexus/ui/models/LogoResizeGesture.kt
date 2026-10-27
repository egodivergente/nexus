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
        if (held != null) { onHold(false); return@awaitEachGesture }
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK)
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
        onMoving(false)
        onHold(false)
        vibrate(com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK)
        onCommit()
    }
}

/**
 * Pinch the grid to change how many columns it shows: spreading the fingers makes cards bigger (fewer columns),
 * pinching them closer shows more. Vibrates on every change. One finger never takes part, so scrolling is untouched.
 */
internal fun Modifier.pinchColumns(columns: Int, max: Int, onColumns: (Int) -> Unit): Modifier = composed {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
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
                        com.vidadomu.nexus.ui.theme.NexusVibration.play(context, com.vidadomu.nexus.ui.theme.NexusVibration.Kind.CLICK)
                        accumulated = 1f
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}
