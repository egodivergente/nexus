package com.vidadomu.nexus

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vidadomu.nexus.assistant.AssistantBubble
import com.vidadomu.nexus.assistant.ChatItem
import com.vidadomu.nexus.assistant.UserBubble
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** «Editar» on your last message and «Otra respuesta» on its answer; no other message offers them. */
class ChatTakeBackActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lastMessageOffersEditAndItsAnswerAnotherOne() {
        var edits = 0
        var again = 0
        compose.setContent { NexusTheme { Column {
            UserBubble(ChatItem.User("u", "Hazlo azul", emptyList()), onEdit = { edits++; true })
            AssistantBubble(ChatItem.Assistant("a", "Hecho", emptyList(), "END_TURN"), onAnswerAgain = { again++; true })
        } } }
        compose.onNodeWithContentDescription("Acciones del mensaje").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithContentDescription("Acciones de la respuesta").performClick()
        compose.onNodeWithText("Otra respuesta").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1), listOf(edits, again)) }
    }

    @Test fun otherMessagesDoNotOfferThem() {
        compose.setContent { NexusTheme { Column {
            UserBubble(ChatItem.User("u", "Hola", emptyList()))
            AssistantBubble(ChatItem.Assistant("a", "Buenas", emptyList(), "END_TURN"))
        } } }
        compose.onNodeWithContentDescription("Acciones del mensaje").performClick()
        compose.onNodeWithText("Copiar mensaje íntegro").assertIsDisplayed()
        compose.onNodeWithText("Editar").assertDoesNotExist()
    }
}
