package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.tools.AgentStep
import com.vidadomu.nexus.agent.core.tools.AgentStepKind
import com.vidadomu.nexus.agent.core.tools.AgentStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which row skips the entrance of a new row: the reply that replaces the text that was being written, and the row
 * that closes the turn whose steps were on show (it settles from the live activity instead).
 */
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

    private fun step(id: String, status: AgentStepStatus = AgentStepStatus.DONE) =
        AgentStep(id, AgentStepKind.THINK, "Pensando la respuesta", status, startedAt = 1L, durationMs = 900)
    private val closed = reply.copy(steps = listOf(step("s1"), step("s2")))

    @Test fun theRowThatClosesTheTurnOnShowSettlesFromTheLiveActivity() {
        assertEquals("a1", turnClosingRowId(liveIn = "chat", conversationId = "chat", working = false, items = listOf(earlier, user, closed)))
        // What the turn adds after its reply (a verified note) does not take its place.
        assertEquals("a1", turnClosingRowId("chat", "chat", false, listOf(earlier, user, closed, ChatItem.Notice("n1", "He comprobado el encargo."))))
        // A turn that was stopped or failed closes with a notice that keeps its steps.
        val stopped = ChatItem.Notice("n2", "Cancelado.", steps = listOf(step("s1"), step("s2", AgentStepStatus.CANCELLED)))
        assertEquals("n2", turnClosingRowId("chat", "chat", false, listOf(earlier, user, stopped)))
        val failed = ChatItem.Notice("n3", "El proveedor está saturado.", error = true, steps = listOf(step("s1", AgentStepStatus.ISSUE)))
        assertEquals("n3", turnClosingRowId("chat", "chat", false, listOf(earlier, user, failed)))
    }

    @Test fun nothingSettlesUnlessTheStepsOfThisTurnWereOnShowAMomentAgo() {
        assertNull("no había pasos en vivo", turnClosingRowId(null, "chat", false, listOf(earlier, user, closed)))
        assertNull("el agente sigue trabajando", turnClosingRowId("chat", "chat", true, listOf(earlier, user, closed)))
        assertNull("los pasos eran de otra conversación", turnClosingRowId("otra", "chat", false, listOf(earlier, user, closed)))
        // An earlier reply keeps its own steps: they are not the ones that were on show.
        assertNull("el turno no guardó pasos", turnClosingRowId("chat", "chat", false, listOf(earlier.copy(steps = listOf(step("s0"))), user, reply)))
        assertNull("ya hay otro mensaje detrás", turnClosingRowId("chat", "chat", false, listOf(earlier, user, closed, user.copy(id = "u2"))))
        assertNull("conversación vacía", turnClosingRowId("chat", "chat", false, emptyList()))
    }

    @Test fun whenTheCardsOfTheTurnMoveAfterItsReplyTheRowsSlideAsTheyDid() {
        assertNull(turnClosingRowId("chat", "chat", false, listOf(earlier, user, closed, ChatItem.PlanCard("c1", "p1"))))
        // A card that stayed where it was, before the reply, changes nothing.
        assertEquals("a1", turnClosingRowId("chat", "chat", false, listOf(earlier, ChatItem.PlanCard("c0", "p0"), user, closed)))
        assertEquals("a1", turnClosingRowId("chat", "chat", false, listOf(earlier, user, ChatItem.PlanCard("c2", "p2"), closed)))
    }
}
