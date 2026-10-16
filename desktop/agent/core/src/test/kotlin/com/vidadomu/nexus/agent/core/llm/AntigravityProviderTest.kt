package com.vidadomu.nexus.agent.core.llm

import com.vidadomu.nexus.agent.core.Harness
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.UiEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Verifica el proveedor Antigravity (AGY) sobre el puente local AXEL (127.0.0.1:8788). */
class AntigravityProviderTest {
    private val schema = buildJsonObject { put("type", "object") }

    @Test fun `stream sobre v1 chat emite deltas limpios y extrae NEXUS_TOOL_CALL`() = runTest {
        MockWebServer().use { server ->
            val sseBody = buildString {
                appendLine("""data: {"type":"text_delta","text":"Consultando el catálogo. "}""")
                appendLine()
                appendLine("""data: {"type":"text_delta","text":"<NEXUS_TOOL_CALL>{\"id\":\"agy_t1\",\"name\":\"list_models\",\"arguments\":{\"modality\":\"IMAGE\"}}</NEXUS_TOOL_CALL>"}""")
                appendLine()
                appendLine("""data: {"type":"final","status":"completed","text":"Consultando el catálogo. <NEXUS_TOOL_CALL>{\"id\":\"agy_t1\",\"name\":\"list_models\",\"arguments\":{\"modality\":\"IMAGE\"}}</NEXUS_TOOL_CALL>","error":null}""")
                appendLine()
            }
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(sseBody))

            val provider = AntigravityProvider(server.url("/").toString())
            val deltas = mutableListOf<String>()
            val req = LlmRequest(
                modelId = "gemini-3.8-flash",
                system = listOf("Eres Nexus."),
                messages = listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text("Lista los modelos de imagen")))),
                tools = listOf(ToolSpec("list_models", "Lista modelos", schema)),
                maxTokens = 1024,
                temperature = 0.2,
                conversationId = "conv-s25-1",
            )

            val response = provider.completeStreaming(req) { deltas += it }
            val sent = server.takeRequest()
            assertEquals("/v1/chat", sent.path)
            val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            assertEquals("gemini", body["provider"]?.jsonPrimitive?.content)
            assertEquals("conv-s25-1", body["conversationId"]?.jsonPrimitive?.content)
            assertEquals("gemini-3.8-flash", body["model"]?.jsonPrimitive?.content)
            assertTrue(body["prompt"]!!.jsonPrimitive.content.contains("NEXUS_TOOL_CALL"))
            assertEquals(2, body["messages"]!!.jsonArray.size)

            assertTrue(deltas.isNotEmpty())
            assertTrue(deltas.none { it.contains("<NEXUS_TOOL_CALL>") }, "El bloque de herramienta no debe filtrarse a la burbuja en vivo")
            assertEquals("Consultando el catálogo.", response.message.text())
            assertEquals(StopReason.TOOL_USE, response.stopReason)
            val toolUse = response.message.toolUses().single()
            assertEquals("agy_t1", toolUse.id)
            assertEquals("list_models", toolUse.name)
            assertEquals("IMAGE", toolUse.input["modality"]?.jsonPrimitive?.content)
        }
    }

    @Test fun `fallback automatico a v1 chat stream si v1 chat devuelve 404`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))
            val legacySse = buildString {
                appendLine("""data: {"type":"delta","text":"Hola desde "}""")
                appendLine("""data: {"type":"delta","text":"el puente clásico."}""")
                appendLine("""data: {"type":"usage","input_tokens":45,"output_tokens":12}""")
                appendLine("""data: {"type":"done","stop_reason":"end_turn"}""")
            }
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(legacySse))

            val provider = AntigravityProvider(server.url("/").toString())
            val deltas = mutableListOf<String>()
            val req = LlmRequest(
                modelId = "gemini-3.8-flash",
                system = listOf("Eres Nexus."),
                messages = listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text("Hola")))),
                tools = emptyList(),
                maxTokens = 1024,
                conversationId = "conv-legacy",
            )
            val response = provider.completeStreaming(req) { deltas += it }
            assertEquals("/v1/chat", server.takeRequest().path)
            assertEquals("/v1/chat/stream", server.takeRequest().path)
            assertEquals("Hola desde el puente clásico.", response.message.text())
            assertEquals(StopReason.END_TURN, response.stopReason)
            assertEquals(Usage(45, 12), response.usage)
            assertEquals("Hola desde el puente clásico.", deltas.last())
        }
    }

    @Test fun `orquestador ejecuta herramienta de Nexus con AntigravityProvider y mantiene sesion`() = runTest {
        MockWebServer().use { server ->
            val turn1Sse = buildString {
                appendLine("""data: {"type":"text_delta","text":"Voy a consultar los modelos disponibles."}""")
                appendLine("""data: {"type":"final","status":"completed","text":"Voy a consultar los modelos disponibles.\n<NEXUS_TOOL_CALL>{\"name\":\"list_models\",\"arguments\":{\"modality\":\"IMAGE\"}}</NEXUS_TOOL_CALL>","error":null}""")
            }
            val turn2Sse = buildString {
                appendLine("""data: {"type":"text_delta","text":"Tienes disponible Seedream 5.0 Pro para imagen."}""")
                appendLine("""data: {"type":"final","status":"completed","text":"Tienes disponible Seedream 5.0 Pro para imagen.","error":null}""")
            }
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(turn1Sse))
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(turn2Sse))

            val provider = AntigravityProvider(server.url("/").toString())
            val h = Harness()
            val cfg = AgentConfig(provider = "antigravity", modelId = "gemini-3.8-flash", region = "us-east-1", endpoint = server.url("/").toString(), maxToolRounds = 4)
            val orchestrator = AgentOrchestrator(provider, cfg, NexusToolkit.all(h.deps), h.skills, h.assets, today = { "2026-10-16" })
            val conv = orchestrator.newConversation()
            val events = mutableListOf<UiEvent>()
            val reply = orchestrator.send(conv, "Qué modelos de imagen tienes", onEvent = { events += it })

            assertEquals("Tienes disponible Seedream 5.0 Pro para imagen.", reply.text)
            assertTrue(events.any { it is UiEvent.StreamingText }, "Debe emitir eventos de streaming a la UI")

            val req1 = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val req2 = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals(conv.session.id, req1["conversationId"]?.jsonPrimitive?.content)
            assertEquals(conv.session.id, req2["conversationId"]?.jsonPrimitive?.content)
            assertTrue(req2["prompt"]!!.jsonPrimitive.content.contains("Resultado de herramienta"), "El segundo turno debe enviar el tool_result a AGY")
        }
    }

    @Test fun `errores del puente AGY informan con claridad sin exponer secretos`() = runTest {
        MockWebServer().use { server ->
            val failedSse = """data: {"type":"final","status":"failed","text":"","error":"AGY CLI exited with code 1: no active session"}""" + "\n"
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(failedSse))
            val provider = AntigravityProvider(server.url("/").toString())
            val req = LlmRequest("gemini-3.8-flash", listOf("Sistema"), listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text("Hola")))), emptyList(), maxTokens = 1024)
            val ex = assertFailsWith<LlmException> { provider.complete(req) }
            assertEquals(LlmException.Kind.UPSTREAM, ex.kind)
            assertTrue(ex.message.orEmpty().contains("no active session"))
        }
    }

    @Test fun `cancelar turno invoca v1 jobs cancel en el puente AGY`() = runTest {
        val cancelRequests = mutableListOf<String>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path) {
                        "/v1/jobs/cancel" -> {
                            cancelRequests += request.body.readUtf8()
                            MockResponse().setResponseCode(200).setBody("""{"ok":true}""")
                        }
                        else -> MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody("""data: {"type":"text_delta","text":"Generando..."}""" + "\n")
                            .setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                    }
                }
            }
            val provider = AntigravityProvider(server.url("/").toString())
            val req = LlmRequest(
                modelId = "gemini-3.8-flash",
                system = listOf("Eres Nexus."),
                messages = listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text("Cancélame")))),
                tools = emptyList(),
                maxTokens = 1024,
                conversationId = "conv-cancel-test",
            )
            val job = launch(kotlinx.coroutines.Dispatchers.IO) {
                assertFailsWith<CancellationException> {
                    provider.completeStreaming(req) {}
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { delay(250) }
            job.cancelAndJoin()
            assertTrue(cancelRequests.isNotEmpty(), "Debe haberse invocado /v1/jobs/cancel al cancelar la corrutina")
            val cancelBody = Json.parseToJsonElement(cancelRequests.first()).jsonObject
            assertEquals("conv-cancel-test", cancelBody["conversationId"]?.jsonPrimitive?.content)
            assertTrue(cancelBody["jobId"]?.jsonPrimitive?.content?.startsWith("nexus-agy-") == true)
        }
    }
}
