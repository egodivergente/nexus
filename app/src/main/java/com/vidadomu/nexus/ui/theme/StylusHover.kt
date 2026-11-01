package com.vidadomu.nexus.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp

/** Observed once by NexusTheme; a catalog of logos must not register one settings observer per icon. */
internal val LocalNexusDecorativeMotion = staticCompositionLocalOf { false }

/**
 * Reports when an S Pen starts or stops hovering over this element (without touching the screen).
 * Passive like [stylusHover]: it observes on the Initial pass and never consumes the events.
 */
fun Modifier.onStylusHover(onChange: (Boolean) -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onChange)
    this.pointerInput(Unit) {
        var inside = false
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val hovering = event.type in setOf(PointerEventType.Enter, PointerEventType.Move) &&
                    event.changes.any { it.type == PointerType.Stylus && !it.pressed }
                if (hovering != inside) { inside = hovering; current(hovering) }
            }
        }
    }
}

/** S Pen hover is local and passive. It never consumes touch, clicks, dragging or emits vibration. */
fun Modifier.stylusHover(enabled: Boolean = true): Modifier = composed {
    val motion = enabled && LocalNexusDecorativeMotion.current
    var hovering by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (hovering && motion) 1f else 0f,
        spring(dampingRatio = .82f, stiffness = 420f), label = "S Pen lift")
    this.pointerInput(enabled, motion) {
        hovering = false
        if (enabled && motion) awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                hovering = event.type in setOf(PointerEventType.Enter, PointerEventType.Move) &&
                    event.changes.any { it.type == PointerType.Stylus && !it.pressed }
            }
        }
    }.graphicsLayer {
        val amount = if (motion) lift else 0f
        translationY = -2.dp.toPx() * amount
        rotationX = 1.2f * amount
        rotationY = -.8f * amount
        scaleX = 1f + .015f * amount
        scaleY = scaleX
    }
}
