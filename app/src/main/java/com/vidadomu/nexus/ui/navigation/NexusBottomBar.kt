package com.vidadomu.nexus.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import com.vidadomu.nexus.ui.components.StudioChrome
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vidadomu.nexus.ui.theme.*

@Composable
fun NexusBottomBar(navController: NavController, selectedRoute: String? = null, onSelect: ((Screen)->Unit)? = null) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute=selectedRoute ?: entry?.destination?.route
    if(currentRoute?.startsWith("output")==true) return
    val haptic = rememberNexusHaptics()
    // Visible by default; the optional hidden mode retains the edge-swipe shortcut.
    val visible = ChromeVisibility.shown
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.expandVertically(), exit = androidx.compose.animation.shrinkVertically()) {
    StudioChrome(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness=NexusTokens.divider,color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
            Row(Modifier.fillMaxWidth().navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(vertical=NexusTokens.space4),
                verticalAlignment=Alignment.CenterVertically) {
                Screen.bottomNavItems.forEach { screen ->
                    val selected=currentRoute==screen.route
                    val tone by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary.copy(alpha=.18f) else Color.Transparent, tween(NexusTokens.pressDuration), label="Navigation tone")
                    Column(Modifier.weight(1f).heightIn(min=NexusTokens.minimumTouch)
                        .semantics { contentDescription=screen.title }
                        .selectable(selected=selected,role=Role.Tab,onClick={
                            if(currentRoute!=screen.route) {
                            // Settings/Flow are pushed over the current tab. Return to that exact
                            // tab when possible; restoring a saved tab stack here could put Settings
                            // back on top and make the tab appear not to respond.
                            if(onSelect!=null) onSelect(screen) else {
                            val poppedToExistingTab = currentRoute !in Screen.bottomNavItems.map { it.route } &&
                                navController.popBackStack(screen.route, inclusive = false)
                            if (!poppedToExistingTab) navController.navigate(screen.route) {
                                val fromTab = currentRoute in Screen.bottomNavItems.map { it.route }
                                popUpTo(navController.graph.findStartDestination().id) { saveState = fromTab }
                                launchSingleTop = true
                                // Only restore saved state when switching between actual tabs.
                                restoreState = fromTab && screen.route !in setOf(Screen.Models.route, Screen.Assistant.route)
                            }
                            }
                            haptic.perform(NexusHapticEvent.SELECTION)
                            }
                        }),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        Box(Modifier.width(52.dp).height(NexusTokens.space24).clip(MaterialTheme.shapes.small)
                            .background(tone),
                            contentAlignment=Alignment.Center) {
                            Icon(screen.icon,contentDescription=null,
                                tint=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier=Modifier.size(NexusTokens.iconMedium))
                        }
                        // Let labels wrap at the user's font scale; TalkBack reads the tab name once.
                        Text(screen.title,style=MaterialTheme.typography.labelMedium,
                            color=if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis,
                            modifier=Modifier.padding(horizontal=NexusTokens.space4).clearAndSetSemantics {})
                    }
                }
            }
        }
    }
    }
}

/**
 * Swipe up from the lower edge of any tab to bring the tab bar in. Only a mostly vertical drag that starts in the band
 * just above the system gesture area counts, so ordinary scrolling and sideways swiping between tabs are untouched.
 * The gesture is observed, never consumed.
 */
fun Modifier.revealBarOnSwipeUp(): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        // The band stops well above the bottom edge: a swipe from the edge belongs to the system (Home/Recents on
        // Samsung) and used to trigger both, or neither on the first try. In the Agent tab the composer handle is the main way.
        val bandTop = size.height - 220.dp.toPx()
        val bandBottom = size.height - 64.dp.toPx()
        if (down.position.y !in bandTop..bandBottom) return@awaitEachGesture
        var dx = 0f; var dy = 0f
        do {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: break
            dx = change.position.x - down.position.x
            dy = change.position.y - down.position.y
            if (dy < -64.dp.toPx() && kotlin.math.abs(dy) > 1.6f * kotlin.math.abs(dx)) {
                ChromeVisibility.bottomBar.value = true
                break
            }
        } while (event.changes.any { it.pressed })
    }
}
