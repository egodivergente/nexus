package com.vidadomu.nexus.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    Create("create", "Lienzo", Icons.Default.AutoAwesome),
    Models("models", "Modelos", Icons.Default.GridView),
    Generations("generations", "Historial", Icons.Default.Timelapse),
    Library("library", "Biblioteca", Icons.Default.Collections),
    Settings("settings", "Ajustes", Icons.Default.Tune);

    companion object {
        val bottomNavItems: List<Screen> get() = entries
    }
}
