package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.text
import com.vidadomu.nexus.agent.core.llm.ScriptedLlmProvider.Companion.tools
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.SuggestedReply
import com.vidadomu.nexus.agent.core.tools.SuggestRepliesTool
import com.vidadomu.nexus.agent.core.tools.ToolCategory
import com.vidadomu.nexus.agent.core.tools.UiEvent
import com.vidadomu.nexus.agent.core.tools.AgentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SuggestedRepliesTest {
    private val config = AgentConfig(provider = "scripted", modelId = "test-model", region = "eu-west-1", endpoint = "http://proxy", maxToolRounds = 4)
    private fun input(json: String) = Json.parseToJsonElement(json).jsonObject
    private fun session() = AgentSession("s", AgentMode.AGENT)

    @Test fun `es solo lectura, no envia ni gasta y el prompt la menciona`() {
        val tool = SuggestRepliesTool()
        assertEquals(ToolCategory.READ, tool.category)
        assertTrue("suggest_replies" in SystemPrompt.BASE, "el prompt debe guiar su uso")
    }

    @Test fun `emite botones limpios, sin repetidos ni vacios y como mucho cuatro`() = runTest {
        val out = SuggestRepliesTool().run(input("""{"replies":[
            {"label":"Kling O3 · 0,84 €","text":"Quiero Kling O3 Std video-edit"},
            {"label":"kling o3 · 0,84 €","text":"repetido"},
            {"label":"   ","text":"vacío"},
            {"label":"Magnific","text":"Usa Magnific Creative"},
            {"label":"Conservar original"},
            {"label":"Cuarta"},
            {"label":"Quinta"}]}"""), session())
        val ev = out.events.single() as UiEvent.Replies
        assertEquals(
            listOf(
                SuggestedReply("Kling O3 · 0,84 €", "Quiero Kling O3 Std video-edit"),
                SuggestedReply("Magnific", "Usa Magnific Creative"),
                SuggestedReply("Conservar original", "Conservar original"),
                SuggestedReply("Cuarta", "Cuarta"),
            ), ev.replies)
    }

    @Test fun `rotulos y textos largos se acotan`() = runTest {
        val long = "x".repeat(500)
        val ev = SuggestRepliesTool().run(input("""{"replies":[{"label":"$long","text":"$long"},{"label":"b"}]}"""), session()).events.single() as UiEvent.Replies
        assertEquals(40, ev.replies[0].label.length)
        assertEquals(300, ev.replies[0].text.length)
    }

    @Test fun `con menos de dos respuestas validas el modelo recibe el error y no hay botones`() = runTest {
        val tool = SuggestRepliesTool()
        for (bad in listOf("""{"replies":[{"label":"Solo una"}]}""", """{"replies":[{"label":"a"},{"label":"A"}]}""", "{}")) {
            val out = tool.run(input(bad), session())
            assertTrue(out.isError, "debe devolver error al modelo: $bad")
            assertTrue(out.events.isEmpty(), "sin botones si no hay 2 respuestas válidas")
        }
    }

    @Test fun `el orquestador entrega el evento a la UI y la respuesta sigue siendo texto normal`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("r1", "suggest_replies", """{"replies":[{"label":"Opción A"},{"label":"Opción B"}]}""")) },
            { text("Tienes dos caminos. ¿Cuál prefieres?") },
        ))
        val o = AgentOrchestrator(llm, config, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-16" })
        val events = mutableListOf<UiEvent>()
        val result = o.send(o.newConversation(mode = AgentMode.AGENT), "Dime opciones") { events += it }
        assertEquals("Tienes dos caminos. ¿Cuál prefieres?", result.text)
        assertEquals(listOf("Opción A", "Opción B"), events.filterIsInstance<UiEvent.Replies>().single().replies.map { it.label })
    }
}
