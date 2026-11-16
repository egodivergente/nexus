package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.chat.WidgetAnswer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WidgetAnswerPersistenceTest {
    @Test fun oldChatMessagesRemainReadableAndNewSelectionsSurviveHistoryRoundTrip() {
        val old = Json.decodeFromString<ChatItem.User>("""{"id":"u","text":"Kling Pro","attachments":[]}""")
        assertNull(old.widgetAnswer)
        val chosen = old.copy(widgetAnswer = WidgetAnswer("models", setOf("pro"), "a"))
        val saved = SavedAssistantChat(listOf(chosen), emptyMap())
        assertEquals(saved, Json.decodeFromString<SavedAssistantChat>(Json.encodeToString(saved)))
    }

    @Test fun historyUsesTheWholeConversationAndFindsTheFirstFollowingUserMessage() {
        val a = ChatItem.Assistant("a", "", emptyList(), "END_TURN")
        val u = ChatItem.User("u", "Kling Pro", emptyList(), widgetAnswer = WidgetAnswer("models", setOf("pro"), "a"))
        assertEquals(u, answerAfter(listOf(a, ChatItem.Notice("n", "", false), u, u.copy(id = "later")), a))
        assertNull(answerAfter(listOf(u), a))
        assertNull(answerAfter(listOf(a), a))
    }
}
