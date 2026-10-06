package com.vidadomu.nexus.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Create : Screen("create", "Lienzo", Icons.Default.AutoAwesome)
    object Models : Screen("models", "Modelos", Icons.Default.GridView)
    object Generations : Screen("generations", "Historial", Icons.Default.Timelapse)
    object Library : Screen("library", "Biblioteca", Icons.Default.Collections)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Tune)

    companion object {
        val bottomNavItems = listOf(Create, Models, Generations, Library, Settings)
    }
}
