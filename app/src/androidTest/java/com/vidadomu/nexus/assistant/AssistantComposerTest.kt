package com.vidadomu.nexus.assistant

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Rule
import org.junit.Test

class AssistantComposerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun conventionalMicrophoneRemainsWithoutTheAdditionalLiveVoiceButton() {
        compose.setContent { NexusTheme { InputBar(AssistantUiState(), TextFieldValue(""), {}, {}, {}, {}, {}, onAudio = {}) } }
        compose.onNodeWithContentDescription("Grabar audio o dictar").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("Dictar texto").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithContentDescription("Hablar en directo").assertDoesNotExist()
    }

    @Test fun canKeepWritingWhileAgentWorks() {
        compose.setContent {
            var draft by remember { mutableStateOf(TextFieldValue("Borrador")) }
            NexusTheme { InputBar(AssistantUiState(thinking=true),draft,{draft=it},{},{},{},{}) }
        }
        compose.onNode(hasSetTextAction()).assertIsEnabled().performTextReplacement("Siguiente pedido")
        compose.onNodeWithText("Siguiente pedido").assertExists()
        compose.onNodeWithContentDescription("Parar respuesta").assertIsEnabled()
        compose.onNodeWithContentDescription("Enviar mensaje").assertDoesNotExist()
    }

    @Test fun aCatalogSuggestionInsertsTheExactName() {
        val model = ModelSummary(ModelRef("atlas_cloud","seedream-5-pro"),"Seedream 5.0 Pro",modalities=setOf("IMAGE"))
        compose.setContent {
            var draft by remember { mutableStateOf(TextFieldValue("seed",TextRange(4))) }
            NexusTheme { InputBar(AssistantUiState(catalog=listOf(model)),draft,{draft=it},{},{},{},{}) }
        }
        compose.onNodeWithText("Seedream 5.0 Pro").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Seedream 5.0 Pro ")
    }
}
