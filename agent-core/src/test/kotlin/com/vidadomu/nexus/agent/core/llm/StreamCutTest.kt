package com.vidadomu.nexus.agent.core.llm

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
}
