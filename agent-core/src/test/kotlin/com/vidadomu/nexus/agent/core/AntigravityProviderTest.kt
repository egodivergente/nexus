package com.vidadomu.nexus.agent.core

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.vidadomu.nexus.agent.core.llm.AntigravityProvider
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.LlmMessage
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.llm.StopReason
import com.vidadomu.nexus.agent.core.llm.ToolSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AntigravityProviderTest {
    private lateinit var server: MockWebServer

    @BeforeTest fun setUp() { server = MockWebServer().apply { start() } }
    @AfterTest fun tearDown() { server.shutdown() }

    @Test fun streamsTextAndReturnsNexusToolCallForTheExistingOrchestrator() = runBlocking {
        val full = "Prepararé la lista.\n<NEXUS_TOOL_CALL>{\"name\":\"list_projects\",\"arguments\":{\"query\":\"Mateo\"}}</NEXUS_TOOL_CALL>"
        val events = full.chunked(5).joinToString("") { chunk ->
            sse(buildJsonObject { put("type", "text_delta"); put("text", chunk) })
        } + sse(buildJsonObject {
            put("type", "final"); put("status", "completed"); put("text", full); put("provider", "gemini"); put("model", "gemini-fast")
        })
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setChunkedBody(events, 11))

        val deltas = StringBuilder()
        val response = provider().completeStreaming(request(), deltas::append)

        assertEquals("Prepararé la lista.\n", deltas.toString())
        assertEquals(StopReason.TOOL_USE, response.stopReason)
        assertEquals("Prepararé la lista.", response.message.text().trim())
        val tool = response.message.content.filterIsInstance<ContentBlock.ToolUse>().single()
        assertEquals("list_projects", tool.name)
        assertEquals("Mateo", tool.input["query"]?.jsonPrimitive?.content)
        assertEquals(0, response.usage.inputTokens)

        val wire = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", wire.method)
        assertEquals("/v1/chat", wire.path)
        val body = kotlinx.serialization.json.Json.parseToJsonElement(wire.body.readUtf8()).jsonObject
        assertEquals("chat-persistente", body["conversationId"]?.jsonPrimitive?.content)
        assertEquals("gemini", body["provider"]?.jsonPrimitive?.content)
        assertEquals("gemini-fast", body["model"]?.jsonPrimitive?.content)
        assertTrue(body["prompt"]!!.jsonPrimitive.content.contains("Herramientas disponibles en Nexus"))
        assertTrue(body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content.contains("NEXUS_TOOL_CALL"))
    }

    @Test fun conversationHistoryAndStableIdAreSentAgainAfterReconnect() = runBlocking {
        server.enqueue(final("Primera respuesta"))
        server.enqueue(final("Segunda respuesta"))
        val provider = provider()
        provider.completeStreaming(request(messages = listOf(user("Primer turno")))) {}
        provider.completeStreaming(request(messages = listOf(
            user("Primer turno"),
            LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text("Primera respuesta"))),
            user("Segundo turno"),
        ))) {}

        val first = kotlinx.serialization.json.Json.parseToJsonElement(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()).jsonObject
        val second = kotlinx.serialization.json.Json.parseToJsonElement(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()).jsonObject
        assertEquals("chat-persistente", first["conversationId"]?.jsonPrimitive?.content)
        assertEquals("chat-persistente", second["conversationId"]?.jsonPrimitive?.content)
        assertEquals(3, second["messages"]!!.jsonArray.size)
        assertTrue(second["prompt"]!!.jsonPrimitive.content.contains("Segundo turno"))
    }

    @Test fun uploadsCurrentImageUsingTheLegacyBridgeRoute() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ok":true,"path":"/root/.local/state/axel-native-v2/uploads/job-image.png"}"""))
        server.enqueue(final("Imagen recibida"))
        val imageRequest = request(messages = listOf(LlmMessage(Role.USER, listOf(
            ContentBlock.Text("Describe la imagen"), ContentBlock.Image("png", byteArrayOf(1, 2, 3)),
        ))))

        provider().completeStreaming(imageRequest) {}

        val upload = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("PUT", upload.method)
        assertTrue(upload.path!!.matches(Regex("/v1/uploads/[^/]+/1-image-2\\.png")))
        assertEquals(byteArrayOf(1, 2, 3).toList(), upload.body.readByteArray().toList())
        val chat = kotlinx.serialization.json.Json.parseToJsonElement(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()).jsonObject
        val attachment = chat["attachments"]!!.jsonArray.single().jsonObject
        assertEquals("image/png", attachment["mimeType"]?.jsonPrimitive?.content)
        assertEquals("/root/.local/state/axel-native-v2/uploads/job-image.png", attachment["uploadedPath"]?.jsonPrimitive?.content)
    }

    @Test fun bridgeErrorIsReportedWithoutChangingProviderConfiguration() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val failure = runCatching { provider().completeStreaming(request()) {} }.exceptionOrNull()
        val llm = assertIs<LlmException>(failure)
        assertEquals(LlmException.Kind.UPSTREAM, llm.kind)
        assertTrue(llm.message.orEmpty().contains("HTTP 503"))
    }

    @Test fun cancellationCancelsTheActiveHttpStream() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBodyDelay(300, TimeUnit.MILLISECONDS).setBody(sse(buildJsonObject {
                put("type", "final"); put("status", "completed"); put("text", "demasiado tarde")
            })))
        val job = async(Dispatchers.IO) { provider().completeStreaming(request()) {} }
        assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun remoteCleartextEndpointsAreRejected() = runBlocking {
        val failure = runCatching {
            AntigravityProvider("http://192.168.1.20:8788").completeStreaming(request()) {}
        }.exceptionOrNull()
        val llm = assertIs<LlmException>(failure)
        assertEquals(LlmException.Kind.BAD_REQUEST, llm.kind)
    }

    private fun provider() = AntigravityProvider(server.url("/").toString().trimEnd('/'))

    private fun request(
        messages: List<LlmMessage> = listOf(user("Consulta Nexus")),
    ) = LlmRequest(
        modelId = "gemini-fast",
        system = listOf("Usa las capacidades del agente Nexus."),
        messages = messages,
        tools = listOf(ToolSpec("list_projects", "Lista proyectos", buildJsonObject { put("type", "object") })),
        maxTokens = 1024,
        conversationId = "chat-persistente",
    )

    private fun user(text: String) = LlmMessage(Role.USER, listOf(ContentBlock.Text(text)))

    private fun final(text: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setChunkedBody(
        sse(buildJsonObject { put("type", "text_delta"); put("text", text) }) +
            sse(buildJsonObject { put("type", "final"); put("status", "completed"); put("text", text) }), 32,
    )

    private fun sse(value: kotlinx.serialization.json.JsonObject) = "data: $value\n\n"
}
