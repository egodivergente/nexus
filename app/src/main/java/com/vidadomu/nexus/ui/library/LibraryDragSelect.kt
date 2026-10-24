package com.vidadomu.nexus.ui.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Key of the grid item under [point] (grid coordinates), or null over headers, gaps or rows that are not files. */
internal fun LazyGridState.keyAt(point: Offset): Any? = layoutInfo.visibleItemsInfo.firstOrNull { item ->
    point.x >= item.offset.x && point.x < item.offset.x + item.size.width &&
        point.y >= item.offset.y && point.y < item.offset.y + item.size.height
}?.key

/**
 * Google-Photos-style marking: a long press on a file marks it and, without lifting the finger, sliding over other files
 * marks the whole range. Before the long press the gesture is left to the grid, so normal scrolling and taps are untouched;
 * once it starts, the events are taken so the tile underneath does not also open.
 * [onEdge] reports how fast to auto-scroll (negative = up) while the finger rests near the top or bottom edge.
 */
internal fun Modifier.dragToSelect(
    keyAt: (Offset) -> Any?,
    onStart: (Any) -> Boolean,
    onMove: (Any) -> Unit,
    onEdge: (speed: Float, at: Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = keyAt(down.position) ?: return@awaitEachGesture
        val stillHeld = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var keep = true
            while (keep) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                keep = change != null && change.pressed && event.changes.size == 1 &&
                    (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
            }
            false
        } ?: true
        if (!stillHeld || !onStart(start)) return@awaitEachGesture
        val edge = 72.dp.toPx()
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                event.changes.forEach { it.consume() }
                if (!change.pressed) break
                keyAt(change.position)?.let(onMove)
                val y = change.position.y
                onEdge(when {
                    y < edge -> -(edge - y) / 3f
                    y > size.height - edge -> (y - (size.height - edge)) / 3f
                    else -> 0f
                }, change.position)
            }
        } finally { onEdge(0f, Offset.Zero); onEnd() }
    }
}
