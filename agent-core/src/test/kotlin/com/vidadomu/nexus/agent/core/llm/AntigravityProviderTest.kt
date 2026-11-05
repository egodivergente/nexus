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

    @Test fun `empty completed turns report a protocol error instead of a fake ellipsis response`() = runTest {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(
                "data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"\"}\n\n"))
            val error=assertFailsWith<LlmException> {
                AntigravityProvider(server.url("/").toString()).complete(LlmRequest(modelId="fixture",
                    system=emptyList(),messages=listOf(LlmMessage(Role.USER,listOf(ContentBlock.Text("Prueba")))),tools=emptyList(),maxTokens=1024))
            }
            assertTrue(error.message.orEmpty().contains("sin devolver texto"))
            assertEquals(1,server.requestCount)
        }
    }

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

    private fun userTurn(text: String, model: String = "gemini-3.8-flash") = LlmRequest(
        modelId = model,
        system = listOf("Eres Nexus."),
        messages = listOf(LlmMessage(Role.USER, listOf(ContentBlock.Text(text)))),
        tools = emptyList(),
        maxTokens = 1024,
        conversationId = "conv-img",
    )

    private fun artifactEvent(model: String?, tool: String = "generate_image", fetchPath: String = "/v1/artifacts/abc123") =
        """data: {"type":"artifact","artifactId":"abc123","originatingTool":"$tool","kind":"image","mimeType":"image/png","filename":"gato.png",""" +
            """"fetchPath":"$fetchPath","sizeBytes":4,"width":1024,"height":768${model?.let { ",\"model\":\"$it\"" }.orEmpty()},"status":"completed"}""" + "\n\n"

    private fun imageServer(sse: String, png: ByteArray = byteArrayOf(1, 2, 3, 4)): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/artifacts/abc123" -> MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(png))
                else -> MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse)
            }
        }
    }

    @Test fun `codex usa el mismo puente con su proveedor, modelo y jobId propios`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"type\":\"text_delta\",\"text\":\"Hola desde Codex.\"}\n\n" +
                    "data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"Hola desde Codex.\",\"provider\":\"codex\",\"error\":null}\n\n"))
            val provider = AntigravityProvider(server.url("/").toString(), engine = AntigravityProvider.BridgeEngine.CODEX)
            val response = provider.complete(userTurn("Hola", model = ""))
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("codex", provider.id)
            assertEquals("codex", body["provider"]?.jsonPrimitive?.content)
            assertEquals(AntigravityProvider.CODEX_DEFAULT_MODEL, body["model"]?.jsonPrimitive?.content)
            assertTrue(body["jobId"]!!.jsonPrimitive.content.startsWith("nexus-codex-"))
            assertTrue("Nano Banana Pro" !in body["prompt"]!!.jsonPrimitive.content)
            assertEquals("Hola desde Codex.", response.message.text())
            assertTrue(response.generatedMedia.isEmpty())
        }
    }

    @Test fun `la imagen del motor propio llega con su modelo y se descarga del puente`() = runTest {
        val png = byteArrayOf(-119, 80, 78, 71)
        val sse = artifactEvent("gemini-3.1-flash-image") +
            "data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"Aquí tienes el gato.\",\"error\":null}\n\n"
        imageServer(sse, png).use { server ->
            val response = AntigravityProvider(server.url("/").toString(), nativeImages = true).complete(userTurn("Hazme un gato"))
            val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val prompt = sent["prompt"]!!.jsonPrimitive.content
            assertTrue(prompt.contains("generate_image"), "AGY debe saber que puede usar su motor")
            assertTrue(prompt.contains("Nano Banana Pro") && prompt.contains("evita las variantes Lite"), "Se le pide el modelo de más calidad")
            val image = response.generatedMedia.single()
            assertTrue(image.bytes.contentEquals(png))
            assertEquals("AGY", image.engine)
            assertEquals("gemini-3.1-flash-image", image.model)
            assertEquals("gato.png", image.filename)
            assertEquals(1024, image.width)
            assertEquals("Aquí tienes el gato.", response.message.text())
            assertTrue(response.notices.isEmpty())
        }
    }

    @Test fun `una imagen de Nano Banana Lite llega igual y solo se avisa`() = runTest {
        val png = byteArrayOf(-119, 80, 78, 71)
        val sse = artifactEvent("gemini-3.1-flash-image-lite") +
            "data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"Listo.\",\"error\":null}\n\n"
        imageServer(sse, png).use { server ->
            val response = AntigravityProvider(server.url("/").toString(), nativeImages = true).complete(userTurn("Hazme un gato"))
            val image = response.generatedMedia.single()
            assertTrue(image.bytes.contentEquals(png))
            assertEquals("gemini-3.1-flash-image-lite", image.model)
            assertEquals(2, server.requestCount, "La imagen Lite se descarga del puente")
            val notice = response.notices.single()
            assertTrue(notice.contains("Nano Banana 2 Lite") && notice.contains("Biblioteca"))
            assertTrue("descartada" !in notice)
            assertEquals("Listo.", response.message.text())
        }
    }

    @Test fun `sin permiso de imagenes o si la herramienta solo lee una imagen no se importa nada`() = runTest {
        val sse = artifactEvent("gemini-3.1-flash-image") + artifactEvent(null, tool = "view_file", fetchPath = "/v1/artifacts/other") +
            "data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"Listo.\",\"error\":null}\n\n"
        imageServer(sse).use { server ->
            val response = AntigravityProvider(server.url("/").toString()).complete(userTurn("Hola"))
            val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue("EXCEPCIÓN" !in sent["prompt"]!!.jsonPrimitive.content)
            assertTrue(response.generatedMedia.isEmpty())
            assertEquals(1, server.requestCount)
        }
        imageServer(artifactEvent(null, tool = "view_file") + "data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"Listo.\",\"error\":null}\n\n").use { server ->
            val response = AntigravityProvider(server.url("/").toString(), nativeImages = true).complete(userTurn("Mira esta foto"))
            assertTrue(response.generatedMedia.isEmpty())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `si el puente no entrega la imagen el texto llega y se avisa`() = runTest {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/v1/artifacts/abc123" -> MockResponse().setResponseCode(404).setBody("""{"error":"artifact_missing"}""")
                    else -> MockResponse().setHeader("Content-Type", "text/event-stream").setBody(artifactEvent(null) +
                        "data: {\"type\":\"final\",\"status\":\"completed\",\"text\":\"Hecho.\",\"error\":null}\n\n")
                }
            }
            val response = AntigravityProvider(server.url("/").toString(), nativeImages = true).complete(userTurn("Hazme un gato"))
            assertEquals("Hecho.", response.message.text())
            assertTrue(response.generatedMedia.isEmpty())
            assertTrue(response.notices.single().contains("HTTP 404"))
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
