package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastTurnTest {
    private fun user(id: String, text: String = id) = ChatItem.User(id, text, emptyList())
    private fun answer(id: String, vararg tools: String) = ChatItem.Assistant(id, id, tools.toList(), "END_TURN")
    private val harmless = setOf("search_assets", "prepare_generation")
    private val never: (String) -> Boolean = { false }

    @Test fun aPlainExchangeCanBeTakenBack() {
        val items = listOf(user("u1"), answer("a1"), user("u2"), answer("a2", "search_assets"), ChatItem.Notice("n", "aviso"))
        assertTrue(LastTurn.rewindable(items, harmless, never))
        assertEquals("u2" to "a2", LastTurn.ids(items))
        val (kept, message) = LastTurn.takeBack(items)!!
        assertEquals(listOf("u1", "a1"), kept.map { it.id })
        assertEquals("u2", message.id)
    }

    @Test fun aToolThatChangesNexusOrAnUnknownOneKeepsItDone() {
        assertFalse(LastTurn.rewindable(listOf(user("u"), answer("a", "rename_asset")), harmless, never))
        assertFalse(LastTurn.rewindable(listOf(user("u"), answer("a", "create_project")), emptySet(), never))
    }

    @Test fun anOrderSentOrAChangeAppliedKeepsItDone() {
        assertFalse(LastTurn.rewindable(listOf(user("u"), answer("a"), ChatItem.PlanCard("c", "p", orderId = "o")), harmless, never))
        assertFalse(LastTurn.rewindable(listOf(user("u"), ChatItem.PlanCard("c", "p")), harmless) { it == "p" })
        assertTrue(LastTurn.rewindable(listOf(user("u"), answer("a"), ChatItem.PlanCard("c", "p")), harmless, never))
        assertFalse(LastTurn.rewindable(listOf(user("u"), ChatItem.Proposal("x", "act", "Renombrar", ProposalState.APPLIED)), harmless, never))
        assertTrue(LastTurn.rewindable(listOf(user("u"), ChatItem.Proposal("x", "act", "Renombrar")), harmless, never))
        assertFalse(LastTurn.rewindable(listOf(user("u"), ChatItem.Board("b", "board")), harmless, never))
    }

    @Test fun nothingToTakeBackWithoutAMessageOrWhileWriting() {
        assertFalse(LastTurn.rewindable(listOf(answer("a")), harmless, never))
        assertNull(LastTurn.ids(emptyList()))
        assertFalse(LastTurn.rewindable(listOf(user("u"), ChatItem.Assistant("s", "…", emptyList(), "STREAMING")), harmless, never))
        assertEquals("u" to null, LastTurn.ids(listOf(user("u"), ChatItem.Notice("n", "falló", error = true))))
    }

    @Test fun aCarriedSummaryGoesAgainWithTheNextMessage() {
        val items = listOf(user("u0"), answer("a0"), ChatItem.Context("old", "A", "antes", sent = true),
            ChatItem.Context("c", "B", "resumen", sent = true), user("u1"), answer("a1"))
        val (kept, _) = LastTurn.takeBack(items)!!
        assertEquals(listOf(false, false), kept.filterIsInstance<ChatItem.Context>().map { it.sent })
        val first = listOf(ChatItem.Context("c", "B", "resumen", sent = true), user("u1"), answer("a1"))
        assertFalse((LastTurn.takeBack(first)!!.first.single() as ChatItem.Context).sent)
        val older = listOf(ChatItem.Context("c", "B", "resumen", sent = true), user("u1"), answer("a1"), user("u2"), answer("a2"))
        assertTrue((LastTurn.takeBack(older)!!.first.first() as ChatItem.Context).sent)
    }

    @Test fun attachmentsAloneComeBackWithNoWords() {
        val att = com.vidadomu.nexus.agent.core.tools.Attachment("f", "file:///f.jpg", "image/jpeg", "f.jpg")
        assertEquals("", LastTurn.editableText(ChatItem.User("u", "(adjuntos)", listOf(att))))
        assertEquals("Hazlo azul", LastTurn.editableText(ChatItem.User("u", "Hazlo azul", listOf(att))))
    }
}
