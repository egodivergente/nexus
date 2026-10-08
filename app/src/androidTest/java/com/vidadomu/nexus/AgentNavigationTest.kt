package com.vidadomu.nexus

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.*
import com.vidadomu.nexus.ui.navigation.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Rule
import org.junit.Test

class AgentNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun returningToAssistantDoesNotRestoreItsPreviouslyOpenedSettings() {
        compose.setContent {
            NexusTheme {
                val nav = rememberNavController()
                Scaffold(bottomBar = { NexusBottomBar(nav) }) {
                    NavHost(nav, startDestination = Screen.Models.route) {
                        composable(Screen.Models.route) { Text("Modelos abiertos") }
                        composable(Screen.Assistant.route) {
                            Column {
                                Text("Conversación abierta", Modifier.testTag("assistant"))
                                Button(onClick = { nav.navigate(Screen.Settings.route) }) { Text("Abrir configuración") }
                            }
                        }
                        composable(Screen.Settings.route) { Text("Configuración abierta", Modifier.testTag("settings")) }
                        composable(Screen.Create.route) { Text("Lienzo abierto") }
                        composable(Screen.Generations.route) { Text("Historial abierto") }
                        composable(Screen.Library.route) { Text("Biblioteca abierta") }
                    }
                }
            }
        }
        compose.onNodeWithText(Screen.Assistant.title).performClick()
        compose.onNodeWithText("Abrir configuración").performClick()
        compose.onNodeWithTag("settings").assertExists()
        compose.onNodeWithText(Screen.Models.title).performClick()
        compose.onNodeWithText(Screen.Assistant.title).performClick()
        compose.onNodeWithTag("assistant").assertExists()
        compose.onNodeWithTag("settings").assertDoesNotExist()
    }
}
