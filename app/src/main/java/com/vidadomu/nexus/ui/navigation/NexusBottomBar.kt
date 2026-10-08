package com.vidadomu.nexus.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vidadomu.nexus.ui.theme.*

@Composable
fun NexusBottomBar(navController: NavController) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    // Hide bottom bar on output details screen
    if (currentRoute?.startsWith("output") == true) {
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            HorizontalDivider(
                color = BorderSubtle,
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val itemColor by animateColorAsState(
                        targetValue = if (selected) PrimaryNeon else TextMuted,
                        label = "itemColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected=selected, role=Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            , onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        // The activity owns the conversation; restoring its nested stack can reopen Settings.
                                        restoreState = screen.route !in setOf(Screen.Models.route, Screen.Assistant.route)
                                    }
                                }
                            }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = null,
                            tint = itemColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = screen.title,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = itemColor,
                            letterSpacing = 0.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Subtle 2dp indicator dot/line
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (selected) PrimaryNeon else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
