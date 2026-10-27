package com.vidadomu.nexus.assistant

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

/**
 * Long press that always reaches the message, wherever the finger lands.
 *
 * The old detector ran in the normal pass, so links, code blocks, scrollable tables or reply buttons inside the
 * Markdown consumed the touch first: depending on the spot, the message menu, the system text menu or nothing appeared
 * (revision video 1, 03:46 and 06:56). This one observes the Initial pass, which children cannot block, and it gives
 * up as soon as the finger moves beyond touch slop, so scrolling the conversation is never mistaken for a long press.
 * Once it fires it consumes the rest of the gesture, so no child click runs after the menu opens.
 */
/** Time of the last long press taken by a message: the chat-wide menu must not open on top of the message menu. */
internal object MessageLongPress {
    @Volatile var lastAt: Long = 0L
    fun recent(windowMs: Long = 600): Boolean = android.os.SystemClock.uptimeMillis() - lastAt < windowMs
}

internal fun Modifier.messageLongPress(label: String, onLongPress: () -> Unit): Modifier = this
    .pointerInput(onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    if (!change.pressed || change.isConsumed) return@withTimeoutOrNull true
                    if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                }
            }
            if (released == null) {
                MessageLongPress.lastAt = android.os.SystemClock.uptimeMillis()
                onLongPress()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
    .semantics { onLongClick(label) { onLongPress(); true } }

/** Very long messages of the user fold to a preview with «Expandir»; the agent's answers never fold. */
internal object UserMessageFold {
    const val PREVIEW_LINES = 8
    private const val MAX_CHARS = 700
    private const val MAX_LINES = 12
    fun shouldFold(text: String): Boolean = text.length > MAX_CHARS || text.count { it == '\n' } >= MAX_LINES
}
