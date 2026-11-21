package com.vidadomu.nexus.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/** Índice de la pestaña que queda tras deslizar, o null si no hay otra en esa dirección. */
internal fun adjacentTab(current: String?, direction: Int, routes: List<String>): String? {
    val index = routes.indexOf(current ?: return null)
    if (index < 0) return null
    return routes.getOrNull(index + direction)
}

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
