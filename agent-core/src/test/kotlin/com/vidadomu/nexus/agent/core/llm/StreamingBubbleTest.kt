package com.vidadomu.nexus.agent.core.llm

import com.vidadomu.nexus.agent.core.Harness
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.tools.UiEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * 16-11 (S25, vc218): con un cerebro propio en streaming la burbuja en vivo mostraba una sola palabra que sustituía
 * a la anterior («t», «fuerza»…). La interfaz sustituye la burbuja con cada `UiEvent.StreamingText`, así que cada
 * evento debe llevar el texto acumulado, igual que ya hacía el puente de Antigravity.
 */
class StreamingBubbleTest {
    private val config = AgentConfig(modelId = "modelo-prueba", region = "us-east-1", endpoint = "http://proxy")

    private fun brain(server: MockWebServer, protocol: BrainProtocol) = BrainFactory.create(
        BrainProfile("prueba", "AKI", protocol, server.url("/api/v3").toString().trimEnd('/'), modelId = "modelo-prueba",
            capabilities = BrainCapabilities(vision = false, reasoning = false, streaming = true, tools = true)), { "k" })

    private suspend fun bubbleTexts(protocol: BrainProtocol, sse: String): Pair<String, List<String>> = MockWebServer().use { server ->
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        val h = Harness()
        val orchestrator = AgentOrchestrator(brain(server, protocol), config, emptyList(), h.skills, h.assets)
        val events = mutableListOf<UiEvent>()
        val reply = orchestrator.send(orchestrator.newConversation(), "hola", onEvent = { events += it })
        reply.text to events.filterIsInstance<UiEvent.StreamingText>().map { it.text }.filter { it.isNotEmpty() }
    }

    private fun assertGrowsToFullReply(reply: String, bubble: List<String>) {
        assertTrue(bubble.size >= 3, "Debe haber varios eventos de streaming: $bubble")
        bubble.zipWithNext().forEach { (before, after) ->
            assertTrue(after.startsWith(before), "La burbuja no debe perder lo ya mostrado: «$before» → «$after»")
        }
        assertEquals("Mantengo la fuerza del primer plano.", bubble.last())
        assertTrue(reply.startsWith(bubble.last()), "La respuesta final debe coincidir con la burbuja: «$reply»")
    }

    @Test fun `Chat Completions - la burbuja en vivo acumula el texto en vez de mostrar solo el ultimo trozo`() = runTest {
        val sse = listOf("Mantengo", " la", " fuerza", " del primer plano.").joinToString("") {
            "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n"
        } + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":8}}\n\n" +
            "data: [DONE]\n\n"
        val (reply, bubble) = bubbleTexts(BrainProtocol.OPENAI_CHAT, sse)
        assertGrowsToFullReply(reply, bubble)
    }

    @Test fun `Responses - la burbuja en vivo acumula el texto en vez de mostrar solo el ultimo trozo`() = runTest {
        val sse = listOf("Mantengo", " la", " fuerza", " del primer plano.").joinToString("") {
            "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"$it\"}\n\n"
        } + "event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[" +
            "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Mantengo la fuerza del primer plano.\"}]}]," +
            "\"usage\":{\"input_tokens\":20,\"output_tokens\":8}}}\n\n"
        val (reply, bubble) = bubbleTexts(BrainProtocol.OPENAI_RESPONSES, sse)
        assertGrowsToFullReply(reply, bubble)
    }
}
