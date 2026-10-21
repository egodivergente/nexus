package com.vidadomu.nexus.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Desplazamiento en curso del dedo: la pantalla lo acompaña para que se note que se está pasando de pestaña. */
class TabDragState {
    val offset = Animatable(0f)
}

@Composable
fun rememberTabDragState(): TabDragState = remember { TabDragState() }

/**
 * Deslizar en horizontal entre las pestañas principales: a la izquierda avanza (Biblioteca → Historial…),
 * a la derecha retrocede. Mientras arrastras, la pantalla se mueve con el dedo (con algo de resistencia);
 * al soltar pasado el umbral cambia de pestaña y la nueva entra por el lado; si no, vuelve a su sitio.
 * Los carruseles y controles con desplazamiento horizontal consumen su propio gesto, así que no compiten con esto.
 */
fun Modifier.tabSwipe(enabled: Boolean, state: TabDragState? = null, onSwipe: (direction: Int) -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val scope = rememberCoroutineScope()
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { _, dragAmount ->
                total += dragAmount
                state?.let { s -> scope.launch { s.offset.snapTo(total * DRAG_FOLLOW) } }
            },
            onDragEnd = {
                if (abs(total) > threshold) onSwipe(if (total < 0f) 1 else -1)
                state?.let { s -> scope.launch { s.offset.animateTo(0f, tween(TAB_SLIDE_MS)) } }
            },
            onDragCancel = {
                total = 0f
                state?.let { s -> scope.launch { s.offset.animateTo(0f, tween(TAB_SLIDE_MS)) } }
            }
        )
    }.then(if (state == null) Modifier else Modifier.graphicsLayer {
        // La capa va DESPUÉS del detector: así el gesto se mide sin el desplazamiento visual y no tiembla.
        translationX = state.offset.value
        alpha = 1f - (abs(state.offset.value) / size.width.coerceAtLeast(1f)).coerceAtMost(.35f)
    })
}

/** Índice de la pestaña que queda tras deslizar, o null si no hay otra en esa dirección. */
internal fun adjacentTab(current: String?, direction: Int, routes: List<String>): String? {
    val index = routes.indexOf(current ?: return null)
    if (index < 0) return null
    return routes.getOrNull(index + direction)
}

private const val DRAG_FOLLOW = .45f
private const val TAB_SLIDE_MS = 260

/** +1 si la pestaña de destino está a la derecha de la de origen, -1 si a la izquierda, 0 si no son pestañas. */
internal fun tabDirection(from: String?, to: String?, tabs: List<String>): Int {
    val a = tabs.indexOf(from)
    val b = tabs.indexOf(to)
    return if (a < 0 || b < 0) 0 else b.compareTo(a)
}

/** Entre pestañas, la nueva entra por el lado hacia el que te mueves (también al tocar la barra inferior). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(tabs: List<String>): EnterTransition {
    val d = tabDirection(initialState.destination.route, targetState.destination.route, tabs)
    return if (d == 0) fadeIn(tween(180)) else slideInHorizontally(tween(TAB_SLIDE_MS)) { it * d } + fadeIn(tween(TAB_SLIDE_MS))
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(tabs: List<String>): ExitTransition {
    val d = tabDirection(initialState.destination.route, targetState.destination.route, tabs)
    return if (d == 0) fadeOut(tween(180)) else slideOutHorizontally(tween(TAB_SLIDE_MS)) { -it * d / 3 } + fadeOut(tween(200))
}
