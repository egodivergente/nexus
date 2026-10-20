package com.vidadomu.nexus.ui.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Deslizar en horizontal entre las pestañas principales: a la izquierda avanza (Biblioteca → Historial…),
 * a la derecha retrocede. Los carruseles y controles con desplazamiento horizontal consumen su propio
 * gesto, así que no compiten con esto. Solo se activa cuando [enabled] es verdadero.
 */
fun Modifier.tabSwipe(enabled: Boolean, onSwipe: (direction: Int) -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { _, dragAmount -> total += dragAmount },
            onDragEnd = { if (abs(total) > threshold) onSwipe(if (total < 0f) 1 else -1) },
            onDragCancel = { total = 0f }
        )
    }
}

/** Índice de la pestaña que queda tras deslizar, o null si no hay otra en esa dirección. */
internal fun adjacentTab(current: String?, direction: Int, routes: List<String>): String? {
    val index = routes.indexOf(current ?: return null)
    if (index < 0) return null
    return routes.getOrNull(index + direction)
}
