package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Editing your last message or asking for another answer takes that turn back whole, once. */
class RewindLastTurnTest {
    private val base = AgentConfig(modelId = "test", region = "r", endpoint = "e")

    @Test fun `el ultimo turno se quita entero del historial y solo una vez`() = runTest {
        val h = Harness()
        val o = AgentOrchestrator(ScriptedLlmProvider(listOf({ text("uno") }, { text("dos") })), base, emptyList(), h.skills, h.assets)
        val conv = o.newConversation()
        assertFalse(conv.canRewindLastTurn())
        assertEquals("uno", o.send(conv, "Primero").text)
        val afterFirst = conv.history()
        assertEquals("dos", o.send(conv, "Segundo").text)
        assertTrue(conv.history().size > afterFirst.size)
        assertTrue(conv.rewindLastTurn())
        assertEquals(afterFirst, conv.history())
        assertFalse(conv.canRewindLastTurn())
        assertFalse(conv.rewindLastTurn())
    }

    @Test fun `una conversacion restaurada puede quitar su ultimo intercambio`() {
        val h = Harness()
        val conv = AgentOrchestrator(ScriptedLlmProvider(emptyList()), base, emptyList(), h.skills, h.assets).newConversation()
        conv.restoreTextHistory(listOf("user" to "a", "assistant" to "b", "user" to "c", "assistant" to "d"))
        assertTrue(conv.rewindLastTurn())
        assertEquals(listOf("a", "b"), conv.history().map { (it.content.single() as ContentBlock.Text).text })
    }

    @Test fun `sin mensajes no hay nada que quitar`() {
        val h = Harness()
        val conv = AgentOrchestrator(ScriptedLlmProvider(emptyList()), base, emptyList(), h.skills, h.assets).newConversation()
        conv.restoreTextHistory(emptyList())
        assertFalse(conv.canRewindLastTurn())
    }
}
