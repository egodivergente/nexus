package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.SystemPrompt
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentLimits
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.LlmResponse
import com.vidadomu.nexus.agent.core.llm.Role
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
import kotlin.test.assertFailsWith
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

    @Test fun `texto final con botones cierra el turno sin otra llamada y el siguiente mensaje sigue valido`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("r1", "suggest_replies", """{"replies":[{"label":"Opción A"},{"label":"Opción B"}]}"""), text = "Tienes dos caminos. ¿Cuál prefieres?") },
            { text("Vamos con la A.") },
        ))
        val o = AgentOrchestrator(llm, config, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-16" })
        val conv = o.newConversation(mode = AgentMode.AGENT)
        val events = mutableListOf<UiEvent>()
        val first = o.send(conv, "Dime opciones") { events += it }
        assertEquals(1, llm.requests.size, "no debe pedir otra respuesta al modelo tras los botones")
        assertEquals(1, first.llmCalls)
        assertEquals("Tienes dos caminos. ¿Cuál prefieres?", first.text)
        assertEquals("END_TURN", first.stop)
        assertEquals(listOf("Opción A", "Opción B"), events.filterIsInstance<UiEvent.Replies>().single().replies.map { it.label })
        val second = o.send(conv, "La A")
        assertEquals("Vamos con la A.", second.text)
        // El resultado de la herramienta y el nuevo texto van en el mismo mensaje de usuario, con el resultado delante.
        val user = llm.requests[1].messages.last()
        assertTrue(user.content.first() is com.vidadomu.nexus.agent.core.llm.ContentBlock.ToolResult)
        assertTrue(user.content.any { it is com.vidadomu.nexus.agent.core.llm.ContentBlock.Text && "La A" in it.text })
    }

    @Test fun `muchos turnos con botones recortan el historial sin vaciarlo ni dejar resultados huerfanos`() = runTest {
        val h = Harness()
        val turns = 40
        val steps: List<(LlmRequest) -> LlmResponse> = List(turns) { i ->
            { _: LlmRequest -> tools(use("r$i", "suggest_replies", """{"replies":[{"label":"Sí"},{"label":"No"}]}"""), text = "Respuesta $i. ¿Seguimos con lo siguiente?") }
        }
        val llm = ScriptedLlmProvider(steps)
        val o = AgentOrchestrator(llm, config.copy(limits = AgentLimits(maxMessages = 20)), NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-16" })
        val conv = o.newConversation(mode = AgentMode.AGENT)
        repeat(turns) { o.send(conv, "Mensaje $it") }
        assertEquals(turns, llm.requests.size, "una llamada por turno")
        for (r in llm.requests) {
            assertEquals(Role.USER, r.messages.first().role)
            assertTrue(r.messages.first().content.none { it is ContentBlock.ToolResult }, "el primer mensaje no lleva resultados huérfanos")
            r.messages.zipWithNext().forEach { (a, b) -> assertTrue(a.role != b.role, "los roles alternan") }
        }
        val last = llm.requests.last().messages
        assertTrue(last.size in 15..20, "recorta al límite sin vaciar: ${last.size}")
        assertTrue(last.last().content.any { it is ContentBlock.Text && "Mensaje ${turns - 1}" in it.text })
    }

    @Test fun `si el modelo falla tras los botones el historial queda como estaba y el reintento no duplica el texto`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("r1", "suggest_replies", """{"replies":[{"label":"Opción A"},{"label":"Opción B"}]}"""), text = "Tienes dos caminos. ¿Cuál prefieres?") },
            { throw LlmException(LlmException.Kind.UPSTREAM, "caído") },
            { text("Vamos con la A.") },
        ))
        val o = AgentOrchestrator(llm, config, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-16" })
        val conv = o.newConversation(mode = AgentMode.AGENT)
        o.send(conv, "Dime opciones")
        val before = conv.messages.toList()
        assertFailsWith<LlmException> { o.send(conv, "La A") }
        assertEquals(before, conv.messages.toList(), "el fallo deshace también el texto fusionado")
        assertEquals("Vamos con la A.", o.send(conv, "La A").text)
        val user = llm.requests[2].messages.last()
        assertEquals(1, user.content.count { it is ContentBlock.Text && "La A" in it.text }, "el texto va una sola vez")
    }

    @Test fun `un preambulo corto con botones no cierra el turno`() = runTest {
        val h = Harness()
        val llm = ScriptedLlmProvider(listOf(
            { tools(use("r1", "suggest_replies", """{"replies":[{"label":"Opción A"},{"label":"Opción B"}]}"""), text = "Te doy opciones:") },
            { text("Puedes elegir entre la A, más rápida, y la B, más barata.") },
        ))
        val o = AgentOrchestrator(llm, config, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-16" })
        val result = o.send(o.newConversation(mode = AgentMode.AGENT), "Dime opciones")
        assertEquals(2, llm.requests.size)
        assertEquals("Puedes elegir entre la A, más rápida, y la B, más barata.", result.text)
    }
}
