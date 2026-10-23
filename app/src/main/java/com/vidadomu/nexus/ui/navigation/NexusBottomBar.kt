package com.vidadomu.nexus.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vidadomu.nexus.ui.components.StudioChrome
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val haptic = LocalHapticFeedback.current
    val context=androidx.compose.ui.platform.LocalContext.current
    // En el chat se esconde al bajar (si está activado en Ajustes) y vuelve al subir; en el resto de pestañas siempre se ve.
    val visible = ChromeVisibility.bottomBar.value || currentRoute != Screen.Assistant.route
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.expandVertically(), exit = androidx.compose.animation.shrinkVertically()) {
    StudioChrome(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness=0.5.dp,color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical=1.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Screen.bottomNavItems.forEach { screen ->
                    val selected=currentRoute==screen.route
                    val tone by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary.copy(alpha=.18f) else Color.Transparent, tween(NexusTokens.pressDuration), label="Navigation tone")
                    Column(Modifier.weight(1f).heightIn(min=48.dp)
                        .semantics { contentDescription=screen.title }
                        .selectable(selected=selected,role=Role.Tab,onClick={
                            if(currentRoute!=screen.route) {
                            if(com.vidadomu.nexus.settings.RuntimePreferences.get(context).bool("ui.haptics")) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            }
                        }),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        Box(Modifier.width(52.dp).height(24.dp).clip(NexusTokens.pill)
                            .background(tone),
                            contentAlignment=Alignment.Center) {
                            Icon(screen.icon,contentDescription=null,
                                tint=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier=Modifier.size(20.dp))
                        }
                        // «Biblioteca» cabe entera en 1/5 de 360 dp; solo con letra grande del sistema se abrevia.
                        // El texto no se anuncia: la pestaña ya lleva su nombre (antes se leía «Biblioteca Biblio»).
                        val compactLabel=screen==Screen.Library && LocalDensity.current.fontScale>1.15f
                        Text(if(compactLabel) "Biblio" else screen.title,style=MaterialTheme.typography.labelMedium,
                            color=if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines=1,softWrap=false,modifier=Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
    }
    }
}
