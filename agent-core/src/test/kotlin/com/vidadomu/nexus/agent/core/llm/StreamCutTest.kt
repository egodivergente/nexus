package com.vidadomu.nexus.agent.core.llm

import com.vidadomu.nexus.agent.core.Harness
import com.vidadomu.nexus.agent.core.SEEDREAM
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Auditoría 13-11, hallazgo 02.2: un flujo que termina sin su marca de final es un corte. Una llamada a herramienta
 * cortada o con argumentos rotos nunca se ejecuta con `{}`; el texto recibido se entrega marcado como incompleto.
 * Cubre Chat Completions, Responses API y el puente AXEL (Antigravity, Codex y Grok).
 */
class StreamCutTest {
    private val schema = buildJsonObject { put("type", "object") }
    private fun user(text: String) = LlmMessage(Role.USER, listOf(ContentBlock.Text(text)))
    private fun request() = LlmRequest("modelo-prueba", listOf("Eres Nexus."), listOf(user("hola")),
        listOf(ToolSpec("prepare_orders", "Prepara encargos", schema)), 512, null)
    private fun streaming(server: MockWebServer, protocol: BrainProtocol) = BrainFactory.create(
        BrainProfile("prueba", "Prueba", protocol, server.url("/api/v3").toString().trimEnd('/'), modelId = "modelo-prueba",
            capabilities = BrainCapabilities(vision = false, reasoning = false, streaming = true, tools = true)), { "k" }) as StreamingLlmProvider
    private suspend fun chat(sse: String): LlmResponse = MockWebServer().use { server ->
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        streaming(server, BrainProtocol.OPENAI_CHAT).completeStreaming(request()) { }
    }
    private suspend fun responses(sse: String): LlmResponse = MockWebServer().use { server ->
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        streaming(server, BrainProtocol.OPENAI_RESPONSES).completeStreaming(request()) { }
    }
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    // ---- Chat Completions en streaming ---------------------------------------------------------------------

    @Test fun `Chat - un corte a mitad de una llamada a herramienta es un error de red y no devuelve la herramienta`() = runTest {
        val e = assertFailsWith<LlmException> { chat(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Preparo el lote\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"prepare_orders\",\"arguments\":\"{\\\"orders\\\":[{\\\"mod\"}}]}}]}\n\n") }
        assertEquals(LlmException.Kind.NETWORK, e.kind)
        assertTrue("no se ha ejecutado" in e.message.orEmpty(), e.message)
    }

    @Test fun `Chat - tambien una llamada con argumentos completos se descarta si falta el final`() = runTest {
        val e = assertFailsWith<LlmException> { chat(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"prepare_orders\",\"arguments\":\"{}\"}}]}}]}\n\n") }
        assertEquals(LlmException.Kind.NETWORK, e.kind)
    }

    @Test fun `Chat - un corte con solo texto entrega el texto marcado como incompleto`() = runTest {
        val r = chat("data: {\"choices\":[{\"delta\":{\"content\":\"Hola\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\", mun\"}}]}\n\n")
        assertEquals("Hola, mun", r.message.text())
        assertEquals(StopReason.OTHER, r.stopReason)
        assertTrue(r.notices.single().contains("incompleta"), r.notices.toString())
    }

    @Test fun `Chat - finish_reason sin DONE es un final valido y no lleva aviso`() = runTest {
        val r = chat("data: {\"choices\":[{\"delta\":{\"content\":\"Listo\"},\"finish_reason\":\"stop\"}]}\n\n")
        assertEquals("Listo", r.message.text())
        assertEquals(StopReason.END_TURN, r.stopReason)
        assertTrue(r.notices.isEmpty())
    }

    @Test fun `Chat - DONE sin finish_reason tambien es un final valido`() = runTest {
        val r = chat("data: {\"choices\":[{\"delta\":{\"content\":\"Listo\"}}]}\n\ndata: [DONE]\n\n")
        assertEquals(StopReason.END_TURN, r.stopReason)
        assertTrue(r.notices.isEmpty())
    }

    @Test fun `Chat - argumentos rotos con final explicito son error de protocolo`() = runTest {
        val e = assertFailsWith<LlmException> { chat(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"prepare_orders\",\"arguments\":\"{\\\"orders\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n" +
            "data: [DONE]\n\n") }
        assertEquals(LlmException.Kind.PROTOCOL, e.kind)
        assertTrue("prepare_orders" in e.message.orEmpty(), e.message)
    }

    @Test fun `Chat - un corte sin texto ni herramientas es un error claro`() = runTest {
        val e = assertFailsWith<LlmException> { chat("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n") }
        assertEquals(LlmException.Kind.PROTOCOL, e.kind)
    }

    // ---- Chat Completions sin streaming (mismo decodificador) ----------------------------------------------------

    @Test fun `Chat - el decodificador rechaza argumentos rotos y acepta vacios, null u objeto`() {
        fun decode(arguments: String, finish: String = "tool_calls") = OpenAiCodec.decode(json(
            """{"choices":[{"message":{"content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"prepare_orders","arguments":$arguments}}]},"finish_reason":"$finish"}]}"""))
        assertEquals(LlmException.Kind.PROTOCOL, assertFailsWith<LlmException> { decode("\"{\\\"orders\\\":[\"") }.kind)
        assertEquals(LlmException.Kind.PROTOCOL, assertFailsWith<LlmException> { decode("\"[1,2]\"") }.kind)
        assertTrue(decode("\"\"").message.toolUses().single().input.isEmpty())
        assertTrue(decode("\"null\"").message.toolUses().single().input.isEmpty())
        assertTrue(decode("null").message.toolUses().single().input.isEmpty())
        assertEquals("ord_1", decode("{\"order_id\":\"ord_1\"}").message.toolUses().single().input["order_id"]!!.jsonPrimitive.content)
        assertEquals("ord_1", decode("\"{\\\"order_id\\\":\\\"ord_1\\\"}\"").message.toolUses().single().input["order_id"]!!.jsonPrimitive.content)
    }

    @Test fun `Chat - cortada por el limite de tokens la llamada queda como MAX_TOKENS para que el orquestador no la ejecute`() {
        val r = OpenAiCodec.decode(json(
            """{"choices":[{"message":{"tool_calls":[{"id":"c1","type":"function","function":{"name":"prepare_orders","arguments":"{\"orders\":["}}]},"finish_reason":"length"}]}"""))
        assertEquals(StopReason.MAX_TOKENS, r.stopReason)
        assertTrue(r.message.toolUses().single().input.isEmpty())
    }

    // ---- Responses API -------------------------------------------------------------------------------------------

    @Test fun `Responses - un corte tras una function_call es un error de red y no la devuelve`() = runTest {
        val e = assertFailsWith<LlmException> { responses(
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Preparo\"}\n\n" +
            "data: {\"type\":\"response.output_item.done\",\"item\":{\"type\":\"function_call\",\"call_id\":\"c1\",\"name\":\"prepare_orders\",\"arguments\":\"{}\"}}\n\n") }
        assertEquals(LlmException.Kind.NETWORK, e.kind)
    }

    @Test fun `Responses - un corte con solo texto lo entrega marcado como incompleto`() = runTest {
        val r = responses("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Parcial\"}\n\n")
        assertEquals("Parcial", r.message.text())
        assertEquals(StopReason.OTHER, r.stopReason)
        assertTrue(r.notices.single().contains("incompleta"), r.notices.toString())
    }

    @Test fun `Responses - el texto llega aunque antes solo se cerrara un razonamiento`() = runTest {
        val r = responses("data: {\"type\":\"response.output_item.done\",\"item\":{\"type\":\"reasoning\",\"summary\":[]}}\n\n" +
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Parcial\"}\n\n")
        assertEquals("Parcial", r.message.text())
        assertEquals(StopReason.OTHER, r.stopReason)
    }

    @Test fun `Responses - con evento final la respuesta no lleva aviso`() = runTest {
        val r = responses("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hola\"}\n\n" +
            "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hola\"}]}]}}\n\n")
        assertEquals(StopReason.END_TURN, r.stopReason)
        assertTrue(r.notices.isEmpty())
    }

    @Test fun `Responses - DONE sin evento final es un cierre del servidor y la herramienta recibida vale`() = runTest {
        val r = responses("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Miro\"}\n\n" +
            "data: {\"type\":\"response.output_item.done\",\"item\":{\"type\":\"function_call\",\"call_id\":\"c1\",\"name\":\"prepare_orders\",\"arguments\":\"{\\\"n\\\":1}\"}}\n\n" +
            "data: [DONE]\n\n")
        assertEquals(StopReason.TOOL_USE, r.stopReason)
        assertEquals("1", r.message.toolUses().single().input["n"]!!.jsonPrimitive.content)
        assertEquals("Miro", r.message.text())
        assertTrue(r.notices.isEmpty())
    }

    @Test fun `Responses - DONE con solo texto es una respuesta completa sin aviso`() = runTest {
        val r = responses("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hecho\"}\n\ndata: [DONE]\n\n")
        assertEquals("Hecho", r.message.text())
        assertEquals(StopReason.END_TURN, r.stopReason)
        assertTrue(r.notices.isEmpty())
    }

    @Test fun `Responses - argumentos rotos en una respuesta completa son error de protocolo`() {
        val e = assertFailsWith<LlmException> { ResponsesCodec.decode(json(
            """{"status":"completed","output":[{"type":"function_call","call_id":"c1","name":"prepare_orders","arguments":"{\"orders\":["}]}""")) }
        assertEquals(LlmException.Kind.PROTOCOL, e.kind)
    }

    @Test fun `Responses - una respuesta cortada por tokens es MAX_TOKENS aunque traiga llamadas`() {
        val r = ResponsesCodec.decode(json(
            """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[{"type":"function_call","call_id":"c1","name":"prepare_orders","arguments":"{\"orders\":["}]}"""))
        assertEquals(StopReason.MAX_TOKENS, r.stopReason)
        assertTrue(r.message.toolUses().single().input.isEmpty())
    }

    // ---- Puente AXEL: Antigravity, Codex y Grok (AntigravityProvider) -------------------------------------------

    private fun agyEvent(type: String, text: String? = null, status: String? = null) = buildJsonObject {
        put("type", type)
        if (text != null) put("text", text)
        if (status != null) put("status", status)
    }.toString()
    private fun agyDelta(text: String) = agyEvent("text_delta", text)
    private fun agyFinal(text: String) = agyEvent("final", text, "completed")
    private fun agyCall(input: String) = "<NEXUS_TOOL_CALL>{\"name\":\"prepare_orders\",\"input\":$input}</NEXUS_TOOL_CALL>"
    private fun agyBody(vararg events: String) = events.joinToString("") { "data: $it\n\n" }
    private suspend fun agySse(sse: String): LlmResponse = MockWebServer().use { server ->
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        AntigravityProvider(server.url("/").toString()).completeStreaming(request()) { }
    }
    private suspend fun agy(vararg events: String): LlmResponse = agySse(agyBody(*events))

    @Test fun `AGY - un corte con solo texto lo entrega marcado como incompleto`() = runTest {
        val r = agy(agyDelta("Hola"), agyDelta(", mun"))
        assertEquals("Hola, mun", r.message.text())
        assertEquals(StopReason.OTHER, r.stopReason)
        assertTrue(r.notices.single().contains("incompleta"), r.notices.toString())
    }

    @Test fun `AGY - un corte tras una llamada ya cerrada es un error de red y no la devuelve`() = runTest {
        val e = assertFailsWith<LlmException> { agy(agyDelta("Preparo el lote. "), agyDelta(agyCall("{\"n\":1}"))) }
        assertEquals(LlmException.Kind.NETWORK, e.kind)
        assertTrue("no se ha ejecutado" in e.message.orEmpty(), e.message)
    }

    @Test fun `AGY - un corte a mitad de una llamada es un error de red y no una respuesta de solo texto`() = runTest {
        val e = assertFailsWith<LlmException> {
            agy(agyDelta("Preparo el lote. "), agyDelta("<NEXUS_TOOL_CALL>{\"name\":\"prepare_orders\",\"input\":{\"ord"))
        }
        assertEquals(LlmException.Kind.NETWORK, e.kind)
    }

    @Test fun `AGY - un corte sin texto ni herramientas es un error claro`() = runTest {
        val e = assertFailsWith<LlmException> { agy("{\"type\":\"usage\",\"input_tokens\":3,\"output_tokens\":0}") }
        assertEquals(LlmException.Kind.PROTOCOL, e.kind)
    }

    @Test fun `AGY - con evento final la respuesta es completa y no lleva aviso`() = runTest {
        val r = agy(agyDelta("Hola"), agyFinal("Hola"))
        assertEquals("Hola", r.message.text())
        assertEquals(StopReason.END_TURN, r.stopReason)
        assertTrue(r.notices.isEmpty(), r.notices.toString())
    }

    @Test fun `AGY - el final heredado done y DONE tambien cierran la respuesta`() = runTest {
        val ends = listOf("data: {\"type\":\"done\",\"stop_reason\":\"end_turn\"}\n\n", "event: done\ndata: {}\n\n", "event: done\n\n", "data: [DONE]\n\n")
        for (end in ends) {
            val r = agySse("data: {\"type\":\"delta\",\"text\":\"Hola\"}\n\n" + end)
            assertEquals("Hola", r.message.text(), end)
            assertEquals(StopReason.END_TURN, r.stopReason, end)
            assertTrue(r.notices.isEmpty(), end)
        }
    }

    @Test fun `AGY - con final la llamada se devuelve con sus argumentos`() = runTest {
        val r = agy(agyDelta("Preparo el lote. "), agyFinal("Preparo el lote. " + agyCall("{\"n\":1}")))
        assertEquals(StopReason.TOOL_USE, r.stopReason)
        assertEquals("prepare_orders", r.message.toolUses().single().name)
        assertEquals("1", r.message.toolUses().single().input["n"]!!.jsonPrimitive.content)
        assertEquals("Preparo el lote.", r.message.text())
        assertTrue(r.notices.isEmpty(), r.notices.toString())
    }

    @Test fun `AGY - argumentos que no son un objeto JSON son error de protocolo y nunca se ejecutan con vacio`() = runTest {
        for (bad in listOf("\"todos los pedidos\"", "\"{\\\"orders\\\":[\"", "[1,2]", "7", "true")) {
            val e = assertFailsWith<LlmException>(bad) { agy(agyFinal("Preparo. " + agyCall(bad))) }
            assertEquals(LlmException.Kind.PROTOCOL, e.kind, bad)
            assertTrue("prepare_orders" in e.message.orEmpty(), e.message)
        }
    }

    @Test fun `AGY - sin argumentos, null, vacios o JSON en cadena siguen valiendo`() = runTest {
        suspend fun input(call: String) = agy(agyFinal(call)).message.toolUses().single().input
        assertTrue(input("<NEXUS_TOOL_CALL>{\"name\":\"prepare_orders\"}</NEXUS_TOOL_CALL>").isEmpty())
        assertTrue(input(agyCall("null")).isEmpty())
        assertTrue(input(agyCall("\"\"")).isEmpty())
        assertEquals("ord_1", input(agyCall("{\"order_id\":\"ord_1\"}"))["order_id"]!!.jsonPrimitive.content)
        assertEquals("ord_1", input(agyCall("\"{\\\"order_id\\\":\\\"ord_1\\\"}\""))["order_id"]!!.jsonPrimitive.content)
        assertEquals("ord_1", input("<NEXUS_TOOL_CALL>{\"name\":\"prepare_orders\",\"arguments\":{\"order_id\":\"ord_1\"}}</NEXUS_TOOL_CALL>")["order_id"]!!.jsonPrimitive.content)
    }

    @Test fun `AGY - el orquestador ejecuta la herramienta con final y no la ejecuta si el puente se corta`() = runTest {
        val call = "<NEXUS_TOOL_CALL>{\"name\":\"get_model_schema\",\"input\":{\"provider_id\":\"" + SEEDREAM.providerId +
            "\",\"model_id\":\"" + SEEDREAM.modelId + "\"}}</NEXUS_TOOL_CALL>"
        fun sse(vararg events: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(agyBody(*events))
        fun orchestrator(server: MockWebServer, h: Harness) = AgentOrchestrator(AntigravityProvider(server.url("/").toString()),
            AgentConfig(provider = "antigravity", modelId = "gemini-3.8-flash", region = "us-east-1", endpoint = server.url("/").toString(), maxToolRounds = 4),
            NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-11-14" })

        // Con final: la herramienta se ejecuta (el registro recibe la consulta) y el segundo turno responde.
        MockWebServer().use { server ->
            server.enqueue(sse(agyDelta("Miro el schema. "), agyFinal("Miro el schema. $call")))
            server.enqueue(sse(agyFinal("Seedream admite dos formatos.")))
            val h = Harness()
            val o = orchestrator(server, h)
            val reply = o.send(o.newConversation(), "Qué formatos admite Seedream")
            assertTrue("Seedream admite dos formatos." in reply.text, reply.text)
            assertEquals(1, h.registry.schemaCalls)
            assertEquals(2, server.requestCount)
        }
        // Sin final: el mismo flujo cortado no ejecuta nada ni pide un segundo turno.
        MockWebServer().use { server ->
            server.enqueue(sse(agyDelta("Miro el schema. "), agyDelta(call)))
            val h = Harness()
            val o = orchestrator(server, h)
            val e = assertFailsWith<LlmException> { o.send(o.newConversation(), "Qué formatos admite Seedream") }
            assertEquals(LlmException.Kind.NETWORK, e.kind)
            assertEquals(0, h.registry.schemaCalls)
            assertEquals(1, server.requestCount)
        }
    }
}
