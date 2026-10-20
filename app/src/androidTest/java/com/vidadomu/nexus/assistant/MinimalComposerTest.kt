package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MinimalComposerTest {
    @get:Rule val compose = createComposeRule()
    private var attachmentPickerCalls = 0

    private fun showComposer() {
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            var draft by remember { mutableStateOf(TextFieldValue("Mi borrador")) }
            NexusTheme {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    RecentLibraryPanel(expanded, { expanded = false }) { Text("Resultado local de prueba") }
                    InputBar(AssistantUiState(), draft, { draft = it }, { attachmentPickerCalls++ }, {}, {}, {},
                        onShowLibrary = { expanded = true }, onHideLibrary = { expanded = false }, libraryExpanded = expanded,
                        reasoningControl = { ReasoningIcon("Medio", true) {} })
                }
            }
        }
    }

    @Test fun hiddenByDefaultAndLongPressDoesNotLaunchAttachmentPicker() {
        showComposer()
        compose.onNodeWithText("Archivos recientes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Adjuntar").performTouchInput { longClick() }
        compose.onNodeWithText("Archivos recientes").assertIsDisplayed()
        assertEquals(0, attachmentPickerCalls)
        compose.onNodeWithContentDescription("Ocultar archivos recientes").performClick()
        compose.onNodeWithText("Archivos recientes").assertDoesNotExist()
        compose.onNodeWithText("Mi borrador").assertExists()
        compose.onNodeWithContentDescription("Modo de razonamiento: Medio").assertIsDisplayed()
    }

    @Test fun normalClipTapKeepsExistingPicker() {
        showComposer()
        compose.onNodeWithContentDescription("Adjuntar").performClick()
        assertEquals(1, attachmentPickerCalls)
        compose.onNodeWithText("Archivos recientes").assertDoesNotExist()
    }

    @Test fun upwardSwipeRevealsAndDownwardSwipeCloses() {
        showComposer()
        compose.onNodeWithContentDescription("Mensaje para Nexus").performTouchInput {
            swipe(center, center + Offset(0f, -180f), 300)
        }
        compose.onNodeWithText("Archivos recientes").assertIsDisplayed()
        compose.onNodeWithText("Archivos recientes").performTouchInput {
            swipe(center, center + Offset(0f, 180f), 300)
        }
        compose.onNodeWithText("Archivos recientes").assertDoesNotExist()
        compose.onNodeWithText("Mi borrador").assertExists()
    }
}
