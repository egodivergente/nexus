package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which row skips the entrance of a new row: the reply that replaces the text that was being written. */
class ChatRowEntranceTest {
    private val user = ChatItem.User("u1", "Busca planos del faro", emptyList())
    private val earlier = ChatItem.Assistant("a0", "Respuesta anterior", emptyList(), "STOP")
    private val reply = ChatItem.Assistant("a1", "Las tres más recientes son…", emptyList(), "STOP")

    @Test fun theReplyThatReplacesTheTextBeingWrittenDoesNotEnterAgain() {
        assertEquals("a1", settledReplyId(wasStreaming = true, streamingNow = false, items = listOf(earlier, user, reply)))
        // The cards of the turn are moved after the reply when it ends: the reply is still the one that settles.
        assertEquals("a1", settledReplyId(true, false, listOf(earlier, user, reply, ChatItem.PlanCard("c1", "p1"))))
    }

    @Test fun whileTheTextArrivesOrWhenNoneDidEveryNewRowEntersAsUsual() {
        assertNull("sigue escribiendo", settledReplyId(wasStreaming = true, streamingNow = true, items = listOf(earlier, user)))
        assertNull("empieza a escribir", settledReplyId(wasStreaming = false, streamingNow = true, items = listOf(earlier, user)))
        assertNull("una respuesta que llega de golpe es una fila nueva", settledReplyId(false, false, listOf(earlier, user, reply)))
        assertNull("sin respuestas no hay nada que asentar", settledReplyId(true, false, listOf(user)))
    }

    @Test fun aStoppedTurnOnlyPointsAtAReplyThatWasAlreadyThere() {
        // «Cancelado.» is a notice, not a reply: the last reply is the earlier one, already seen, so nothing changes.
        assertEquals("a0", settledReplyId(true, false, listOf(earlier, user, ChatItem.Notice("n1", "Cancelado."))))
    }
}
