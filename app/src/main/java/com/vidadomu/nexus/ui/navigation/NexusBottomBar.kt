package com.vidadomu.nexus.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vidadomu.nexus.ui.theme.*

@Composable
fun NexusBottomBar(navController: NavController) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute=entry?.destination?.route
    if(currentRoute?.startsWith("output")==true) return
    Surface(color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical=NexusTokens.space8),
            verticalAlignment=Alignment.CenterVertically) {
            Screen.bottomNavItems.forEach { screen ->
                val selected=currentRoute==screen.route
                Column(Modifier.weight(1f).heightIn(min=NexusTokens.minimumTouch)
                    .semantics { contentDescription=screen.title }
                    .selectable(selected=selected,role=Role.Tab,onClick={
                        if(currentRoute!=screen.route) navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState=true }
                            launchSingleTop=true
                            // Preserve the fix preventing a return to Assistant from reopening Settings.
                            restoreState=screen.route !in setOf(Screen.Models.route,Screen.Assistant.route)
                        }
                    }),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                    Box(Modifier.width(56.dp).height(32.dp).clip(NexusTokens.filter)
                        .background(if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        contentAlignment=Alignment.Center) {
                        Icon(screen.icon,contentDescription=null,
                            tint=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.size(NexusTokens.space24))
                    }
                    Text(if(screen==Screen.Library) "Biblio" else screen.title,style=MaterialTheme.typography.labelMedium,
                        color=if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
