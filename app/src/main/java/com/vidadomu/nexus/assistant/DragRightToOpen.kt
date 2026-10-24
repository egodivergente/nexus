package com.vidadomu.nexus.assistant

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Opens the conversations panel when a single finger drags clearly to the right; watches without taking the gesture. */
internal fun Modifier.dragRightToOpen(onOpen: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // The S Pen draws on boards; it never opens the panel.
        if (down.type == PointerType.Stylus) return@awaitEachGesture
        var dx = 0f; var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed || event.changes.size > 1) break
            dx += change.position.x - change.previousPosition.x; dy += change.position.y - change.previousPosition.y
            if (dx > 64.dp.toPx() && dx > abs(dy) * 2) { onOpen(); break }
            if (dx < -16.dp.toPx() || abs(dy) > 40.dp.toPx()) break
        }
    }
}
