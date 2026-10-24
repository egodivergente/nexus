package com.vidadomu.nexus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.assistant.AssistantBubble
import com.vidadomu.nexus.assistant.ChatItem
import com.vidadomu.nexus.assistant.UserBubble
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Rule
import org.junit.Test

class ChatMessageActionsTest {
    @get:Rule val compose = createComposeRule()
    private val message = "Texto íntegro\nhttps://example.org/path?a=1\n```kotlin\nval value = 1\n```"

    @Test fun ownMessageOffersCopyShareAndUnmodifiedSelection() {
        compose.setContent { NexusTheme { UserBubble(ChatItem.User("own", message, emptyList())) } }
        compose.onNodeWithText(message).performTouchInput { longClick() }
        compose.onNodeWithText("Copiar mensaje íntegro").assertIsDisplayed()
        compose.onNodeWithText("Compartir mensaje").assertIsDisplayed()
        compose.onNodeWithText("Seleccionar fragmento").performClick()
        compose.onAllNodesWithText(message).assertCountEquals(2)
        compose.onNodeWithText("Cerrar").performClick()
    }

    @Test fun agentSelectionPreservesMarkdownAndLinks() {
        compose.setContent { NexusTheme { AssistantBubble(ChatItem.Assistant("agent", message, emptyList(), "END_TURN")) } }
        compose.onNodeWithText("Texto íntegro", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("Copiar respuesta").assertIsDisplayed()
        compose.onNodeWithText("Compartir respuesta").assertIsDisplayed()
        compose.onNodeWithText("Seleccionar fragmento").performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
    }
}
