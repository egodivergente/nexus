package com.vidadomu.nexus.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Abre la MISMA pestaña Historial de la barra inferior (no otra pantalla ni Biblioteca),
 * con la misma conservación de estado que al tocarla en la barra.
 */
fun NavController.openHistoryTab() {
    if (currentDestination?.route == Screen.Generations.route) return
    navigate(Screen.Generations.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Detecta los avisos que remiten al destino Historial (pestaña [Screen.Generations]) para que
 * ofrezcan un acceso directo. No confunde el historial de la conversación («El historial se
 * conserva») ni la reindexación del agente («Indexar historial»).
 */
object HistoryLinks {
    // Sin el flag (?U): el motor de regex de Android (ICU) no lo admite y lanza
    // PatternSyntaxException al inicializar la clase. ICU ya trata \b con letras Unicode.
    private val SCREEN_NAME = Regex("""\bHistorial\b""")
    private val CHECK_HISTORY = Regex("""\b(revisa|revisar|comprueba|compruébalo en|compruébala en|consulta|ver en|aparece en|disponible en|guardad[ao] en tu)\s+(el\s+|su\s+|tu\s+)?historial\b""", RegexOption.IGNORE_CASE)
    private val NOT_THE_SCREEN = Regex("""historial (se conserva|indexado)|indexar historial""", RegexOption.IGNORE_CASE)

    fun mentionsHistoryScreen(text: String?): Boolean {
        if (text.isNullOrBlank() || NOT_THE_SCREEN.containsMatchIn(text)) return false
        return SCREEN_NAME.containsMatchIn(text) || CHECK_HISTORY.containsMatchIn(text)
    }
}
