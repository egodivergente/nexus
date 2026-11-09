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
    Assistant("assistant", "Agente", Icons.Default.Assistant),
    Story("story", "Guion", Icons.Default.MovieFilter),
    Create("create", "Lanzadera", Icons.Default.AutoAwesome),
    Models("models", "ArtBox", Icons.Default.GridView),
    Generations("generations", "Historial", Icons.Default.Timelapse),
    Library("library", "Biblioteca", Icons.Default.Collections),
    Flow("flow", "Flow", Icons.Default.Movie),
    Settings("settings", "Ajustes", Icons.Default.Tune);

    companion object {
        // The audiovisual project is a primary workspace. Routes remain stable when tabs move.
        val bottomNavItems: List<Screen> get() = listOf(Assistant, Story, Models, Library, Flow)
    }
}
