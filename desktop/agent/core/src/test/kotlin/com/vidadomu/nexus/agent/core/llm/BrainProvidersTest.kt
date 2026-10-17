package com.vidadomu.nexus.agent.core.llm

import com.vidadomu.nexus.agent.core.config.LimitOverrides
import com.vidadomu.nexus.agent.core.net.JsonPaths
import com.vidadomu.nexus.agent.core.net.JsonTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class BrainProvidersTest {
    private val schema = buildJsonObject { put("type", "object") }
    private fun tool() = ToolSpec("get_ui_state", "Estado real", schema)
    private fun user(text: String) = LlmMessage(Role.USER, listOf(ContentBlock.Text(text)))
    private fun request(vararg msgs: LlmMessage, tools: List<ToolSpec> = listOf(tool())) =
        LlmRequest("dola-seed-2-1-turbo-260628", listOf("Eres Nexus."), msgs.toList(), tools, 512, null)
    private fun body(req: okhttp3.mockwebserver.RecordedRequest) = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
    private fun profile(server: MockWebServer, protocol: BrainProtocol = BrainProtocol.OPENAI_RESPONSES, tweak: (BrainProfile) -> BrainProfile = { it }) =
        tweak(BrainProfile("prueba", "Prueba", protocol, server.url("/api/v3").toString().trimEnd('/'), modelId = "dola-seed-2-1-turbo-260628",
            capabilities = BrainCapabilities(vision = true, reasoning = true, streaming = false, tools = true)))

    // ---- BytePlus / Responses API ---------------------------------------------------------------------------

    @Test fun `BytePlus viene preconfigurado con Responses, la URL y el modelo pedidos`() {
        val p = BrainPresets.byteplus()
        assertEquals("https://ark.ap-southeast.bytepluses.com/api/v3/responses", p.endpointUrl())
        assertEquals("dola-seed-2-1-turbo-260628", p.modelId)
        assertEquals(BrainProtocol.OPENAI_RESPONSES, p.protocol)
        assertTrue(p.capabilities.tools && p.capabilities.vision && p.capabilities.reasoning && p.capabilities.streaming)
        assertTrue(p.problems().isEmpty(), p.problems().toString())
        assertTrue(p.builtin)
    }

    @Test fun `Responses envia instrucciones, herramientas y resultados por call_id y decodifica texto, herramienta y consumo`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status":"completed","output":[
                {"type":"reasoning","summary":[]},
                {"type":"message","content":[{"type":"output_text","text":"Miro la interfaz."}]},
                {"type":"function_call","call_id":"call_9","name":"get_ui_state","arguments":"{\"order_id\":\"ord_1\"}"}],
                "usage":{"input_tokens":120,"output_tokens":9}}"""))
            server.enqueue(MockResponse().setBody("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Listo."}]}],"usage":{"input_tokens":160,"output_tokens":4}}"""))
            val provider = BrainFactory.create(profile(server, tweak = { it.copy(reasoningEffort = "low", thinking = "enabled") }), { "clave-secreta" })

            val first = provider.complete(request(user("¿Cómo va?")))
            val sent = server.takeRequest()
            assertEquals("/api/v3/responses", sent.path)
            assertEquals("Bearer clave-secreta", sent.getHeader("Authorization"))
            val b = body(sent)
            assertEquals("dola-seed-2-1-turbo-260628", b["model"]!!.jsonPrimitive.content)
            assertEquals("Eres Nexus.", b["instructions"]!!.jsonPrimitive.content)
            assertEquals("get_ui_state", b["tools"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals("function", b["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals(512, b["max_output_tokens"]!!.jsonPrimitive.content.toInt())
            assertEquals("enabled", b["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("low", b["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertNull(b["stream"], "sin streaming no se pide stream")

            assertEquals(StopReason.TOOL_USE, first.stopReason)
            assertEquals(120, first.usage.inputTokens)
            val call = first.message.toolUses().single()
            assertEquals("call_9", call.id)
            assertEquals("ord_1", call.input["order_id"]!!.jsonPrimitive.content)
            assertEquals("Miro la interfaz.", first.message.text())

            // Segundo turno: el resultado vuelve como function_call_output con el mismo call_id y la conversación completa.
            val result = LlmMessage(Role.USER, listOf(ContentBlock.ToolResult("call_9", buildJsonObject { put("status", "AWAITING_CONFIRMATION") })))
            val second = provider.complete(request(user("¿Cómo va?"), first.message, result))
            assertEquals("Listo.", second.message.text())
            val input = body(server.takeRequest())["input"]!!.jsonArray.map { it.jsonObject }
            assertEquals("function_call", input.first { it["type"]?.jsonPrimitive?.content == "function_call" }["type"]!!.jsonPrimitive.content)
            val output = input.first { it["type"]?.jsonPrimitive?.content == "function_call_output" }
            assertEquals("call_9", output["call_id"]!!.jsonPrimitive.content)
            assertTrue("AWAITING_CONFIRMATION" in output["output"]!!.jsonPrimitive.content)
        }
    }

    @Test fun `pensamiento desactivado descarta el esfuerzo (ModelArk lo rechaza) y los parametros avanzados no pisan lo del agente`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"ok"}]}]}"""))
            val extra = """{"top_p":0.8,"model":"otro","max_output_tokens":1,"service_tier":"fast"}"""
            // Perfil con extraBody que intenta definir «model»: se rechaza al validar...
            assertTrue(profile(server) { it.copy(extraBody = extra) }.problems().any { "model" in it })
            // ...y en el codec, aunque llegara, no pisa nada.
            val encoded = ResponsesCodec.encode(request(user("hola")), ResponsesOptions("high", "disabled", null,
                Json.parseToJsonElement(extra).jsonObject), stream = false)
            assertEquals("dola-seed-2-1-turbo-260628", encoded["model"]!!.jsonPrimitive.content)
            assertEquals(512, encoded["max_output_tokens"]!!.jsonPrimitive.content.toInt())
            assertNull(encoded["reasoning"], "esfuerzo + pensamiento desactivado = 400 en ModelArk")
            assertEquals("disabled", encoded["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("fast", encoded["service_tier"]!!.jsonPrimitive.content)
            assertEquals("0.8", encoded["top_p"]!!.jsonPrimitive.content)
        }
    }

    @Test fun `una imagen viaja como input_image y sin herramientas no se envian`() = runTest {
        val img = LlmMessage(Role.USER, listOf(ContentBlock.Text("¿Qué ves?"), ContentBlock.Image("png", byteArrayOf(1, 2, 3))))
        val encoded = ResponsesCodec.encode(request(img), ResponsesOptions(sendTools = false), stream = false)
        val parts = encoded["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("input_text", "input_image"), parts.map { it["type"]!!.jsonPrimitive.content })
        assertTrue(parts[1]["image_url"]!!.jsonPrimitive.content.startsWith("data:image/png;base64,"))
        assertNull(encoded["tools"])
    }

    @Test fun `streaming de Responses entrega los trozos en orden y reensambla la llamada a herramienta del evento final`() = runTest {
        MockWebServer().use { server ->
            val sse = buildString {
                append("event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"Hola\"}\n\n")
                append("event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\", mundo\"}\n\n")
                append("event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[")
                append("{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hola, mundo\"}]},")
                append("{\"type\":\"function_call\",\"call_id\":\"c1\",\"name\":\"get_ui_state\",\"arguments\":\"{}\"}],")
                append("\"usage\":{\"input_tokens\":30,\"output_tokens\":7}}}\n\n")
            }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
            val provider = BrainFactory.create(profile(server) { it.copy(capabilities = it.capabilities.copy(streaming = true)) }, { "k" })
            assertTrue(provider is StreamingLlmProvider)
            val deltas = mutableListOf<String>()
            val r = (provider as StreamingLlmProvider).completeStreaming(request(user("hola"))) { deltas += it }
            assertEquals(listOf("Hola", ", mundo"), deltas)
            assertEquals("Hola, mundo", r.message.text())
            assertEquals("c1", r.message.toolUses().single().id)
            assertEquals(StopReason.TOOL_USE, r.stopReason)
            assertEquals(7, r.usage.outputTokens)
            val b = body(server.takeRequest())
            assertEquals("true", b["stream"]!!.jsonPrimitive.content)
        }
    }

    @Test fun `si el servidor corta el streaming sin evento final se aprovecha lo recibido y si no hay nada es un error claro`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Parcial\"}\n\n"))
            server.enqueue(MockResponse().setBody(""))
            val provider = BrainFactory.create(profile(server) { it.copy(capabilities = it.capabilities.copy(streaming = true)) }, { "k" }) as StreamingLlmProvider
            assertEquals("Parcial", provider.completeStreaming(request(user("x"))) { }.message.text())
            val e = assertFailsWith<LlmException> { provider.completeStreaming(request(user("x"))) { } }
            assertEquals(LlmException.Kind.PROTOCOL, e.kind)
        }
    }

    @Test fun `un evento de error en el stream llega como error de proveedor sin la clave`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("data: {\"type\":\"error\",\"error\":{\"message\":\"cuota agotada\"}}\n\n"))
            val provider = BrainFactory.create(profile(server) { it.copy(capabilities = it.capabilities.copy(streaming = true)) }, { "clave-secreta" }) as StreamingLlmProvider
            val e = assertFailsWith<LlmException> { provider.completeStreaming(request(user("x"))) { } }
            assertTrue("cuota agotada" in e.message.orEmpty())
            assertFalse("clave-secreta" in e.message.orEmpty())
        }
    }

    // ---- Chat Completions con streaming ----------------------------------------------------------------------

    @Test fun `streaming de Chat Completions reensambla argumentos de herramienta troceados y el consumo`() = runTest {
        MockWebServer().use { server ->
            val sse = """
                data: {"choices":[{"delta":{"content":"Voy"}}]}

                data: {"choices":[{"delta":{"content":" a mirar"}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c7","function":{"name":"get_ui_state","arguments":"{\"order"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"_id\":\"o1\"}"}}]},"finish_reason":"tool_calls"}]}

                data: {"choices":[],"usage":{"prompt_tokens":50,"completion_tokens":11}}

                data: [DONE]

            """.trimIndent() + "\n\n"
            server.enqueue(MockResponse().setBody(sse))
            val provider = BrainFactory.create(profile(server, BrainProtocol.OPENAI_CHAT) { it.copy(capabilities = it.capabilities.copy(streaming = true)) }, { "k" }) as StreamingLlmProvider
            val deltas = mutableListOf<String>()
            val r = provider.completeStreaming(request(user("hola"))) { deltas += it }
            assertEquals(listOf("Voy", " a mirar"), deltas)
            val call = r.message.toolUses().single()
            assertEquals("c7", call.id)
            assertEquals("o1", call.input["order_id"]!!.jsonPrimitive.content)
            assertEquals(StopReason.TOOL_USE, r.stopReason)
            assertEquals(11, r.usage.outputTokens)
            val sent = server.takeRequest()
            assertEquals("/api/v3/chat/completions", sent.path)
            assertEquals("true", body(sent)["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content)
        }
    }

    // ---- Autenticación configurable ---------------------------------------------------------------------------

    @Test fun `la clave puede ir en Bearer, en una cabecera propia, en la URL o no ir`() = runTest {
        val ok = """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}"""
        MockWebServer().use { server ->
            repeat(4) { server.enqueue(MockResponse().setBody(ok)) }
            fun p(auth: BrainAuth) = BrainFactory.create(profile(server, BrainProtocol.OPENAI_CHAT) { it.copy(auth = auth, extraHeaders = mapOf("X-Org" to "nexus")) }, { "K123" })
            p(BrainAuth(BrainAuthMode.BEARER)).complete(request(user("a")))
            assertEquals("Bearer K123", server.takeRequest().getHeader("Authorization"))
            p(BrainAuth(BrainAuthMode.HEADER, "x-api-key")).complete(request(user("a")))
            server.takeRequest().let { assertEquals("K123", it.getHeader("x-api-key")); assertNull(it.getHeader("Authorization")); assertEquals("nexus", it.getHeader("X-Org")) }
            p(BrainAuth(BrainAuthMode.QUERY, "key")).complete(request(user("a")))
            server.takeRequest().let { assertTrue(it.path!!.endsWith("?key=K123"), it.path); assertNull(it.getHeader("Authorization")) }
            p(BrainAuth(BrainAuthMode.NONE)).complete(request(user("a")))
            server.takeRequest().let { assertNull(it.getHeader("Authorization")); assertFalse("K123" in it.path.orEmpty()) }
        }
    }

    @Test fun `el proveedor OpenAI-compatible de siempre no cambia - sin opciones nuevas envia lo mismo que antes`() {
        val base = OpenAiCodec.encode(request(user("hola")))
        assertNull(base["stream"]); assertNull(base["thinking"]); assertNull(base["reasoning_effort"])
        assertEquals(512, base["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals("function", base["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    // ---- Descubrimiento y prueba de conexión -----------------------------------------------------------------

    @Test fun `descubrimiento de modelos entiende OpenAI, listas de cadenas y Gemini nativo`() {
        fun ids(s: String) = BrainDiscovery.parseModelIds(Json.parseToJsonElement(s))
        assertEquals(listOf("a", "b"), ids("""{"data":[{"id":"b"},{"id":"a"},{"id":"a"}]}"""))
        assertEquals(listOf("x", "y"), ids("""{"models":["y","x"]}"""))
        assertEquals(listOf("gemini-3-pro"), ids("""{"models":[{"name":"models/gemini-3-pro"}]}"""))
        assertTrue(ids("""{"oops":1}""").isEmpty())
    }

    @Test fun `probar conexion - lista modelos, comprueba que el modelo responde y avisa si no esta en la lista`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"dola-seed-2-1-turbo-260628"},{"id":"otro"}]}"""))
            server.enqueue(MockResponse().setBody("""{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"OK"}]}]}"""))
            val r = BrainDiscovery.test(profile(server), { "k" })
            assertTrue(r.ok, r.message)
            assertEquals(true, r.modelListed)
            assertEquals(listOf("dola-seed-2-1-turbo-260628", "otro"), r.models)
            assertEquals("/api/v3/models", server.takeRequest().path)
            assertEquals("/api/v3/responses", server.takeRequest().path)
        }
    }

    @Test fun `clave valida pero modelo sin activar (ModelNotOpen) se explica y no se da por bueno`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"dola-seed-2-1-turbo-260628"}]}"""))
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":"ModelNotOpen","message":"Your account has not activated the model dola-seed-2-1-turbo-260628. Please activate the model service in the Ark Console."}}"""))
            val r = BrainDiscovery.test(profile(server), { "k" })
            assertFalse(r.ok)
            assertTrue("activated the model" in r.message, r.message)
            assertEquals(1, r.models.size)
            assertEquals(BrainProblemKind.NOT_ACTIVATED, r.problem?.kind)
        }
    }

    @Test fun `el diagnostico de BytePlus da el enlace directo de activacion de su region y el nombre base del modelo`() {
        val p = BrainPresets.byteplus()
        val err = LlmException(LlmException.Kind.MODEL_NOT_ALLOWED, "x", 404, "ModelNotOpen")
        val d = BrainDiagnosis.of(p, err)
        assertEquals(BrainProblemKind.NOT_ACTIVATED, d.kind)
        assertEquals("https://console.byteplus.com/ark/region:ap-southeast-1/openManagement", d.actionUrl)
        assertEquals("Activar modelo", d.actionLabel)
        assertEquals("dola-seed-2-1-turbo", BrainDiagnosis.foundationName("dola-seed-2-1-turbo-260628"))
        assertTrue(d.steps.any { "ActivateModels" in it && "dola-seed-2-1-turbo\"" in it }, d.steps.toString())
        assertEquals("https://console.byteplus.com/ark/region:ap-southeast-1/model/detail?Id=dola-seed-2-1-turbo", BrainDiagnosis.modelPageUrl(p))
    }

    @Test fun `un ID mal escrito se corrige con el mas parecido de la lista y nunca se inventa`() {
        val known = listOf("dola-seed-2-1-turbo-260628", "seed-1-6-250915", "deepseek-v3-2-251201")
        assertEquals("dola-seed-2-1-turbo-260628", BrainDiagnosis.closest("dola-seed-2-1-turbo-260682", known))
        assertNull(BrainDiagnosis.closest("modelo-que-no-existe-para-nada", known))
        assertFalse(BrainDiagnosis.isTextModel("seedream-4-0-250828"))
        assertTrue(BrainDiagnosis.isTextModel("dola-seed-2-1-turbo-260628"))
    }

    @Test fun `comprobar la activacion marca cada modelo segun lo que responde de verdad`() = runTest {
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    val model = body(request)["model"]?.jsonPrimitive?.content.orEmpty()
                    return when (model) {
                        "ok-model" -> MockResponse().setBody("""{"id":"r","status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"OK"}]}],"usage":{"input_tokens":1,"output_tokens":1}}""")
                        "closed-model" -> MockResponse().setResponseCode(404).setBody("""{"error":{"code":"ModelNotOpen","message":"not activated"}}""")
                        "ghost-model" -> MockResponse().setResponseCode(404).setBody("""{"error":{"code":"InvalidEndpointOrModel.NotFound","message":"missing"}}""")
                        else -> MockResponse().setResponseCode(500).setBody("""{"error":{"code":"Boom","message":"x"}}""")
                    }
                }
            }
            val states = BrainDiscovery.probeModels(profile(server), { "k" }, listOf("ok-model", "closed-model", "ghost-model", "odd-model"))
            assertEquals(ModelState.ACTIVE, states["ok-model"])
            assertEquals(ModelState.NOT_ACTIVATED, states["closed-model"])
            assertEquals(ModelState.NOT_FOUND, states["ghost-model"])
            assertEquals(ModelState.UNKNOWN, states["odd-model"])
        }
    }

    @Test fun `sin endpoint de modelos la prueba sigue con la peticion real y una clave mala corta enseguida`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"OK"},"finish_reason":"stop"}]}"""))
            assertTrue(BrainDiscovery.test(profile(server, BrainProtocol.OPENAI_CHAT), { "k" }).ok)
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
            val r = BrainDiscovery.test(profile(server), { "mala-clave" })
            assertFalse(r.ok)
            assertFalse("mala-clave" in r.message)
            assertEquals(1, server.requestCount, "una clave rechazada no genera más peticiones")
        }
    }

    // ---- REST personalizado -----------------------------------------------------------------------------------

    @Test fun `REST personalizado rellena la plantilla, lee la ruta indicada y no envia herramientas`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"result":{"answer":"Hola desde REST"}}"""))
            val p = profile(server, BrainProtocol.CUSTOM_REST) { it.copy(endpointPath = "/chat", capabilities = BrainCapabilities(tools = false),
                restRequestTemplate = """{"model":"{{model}}","q":"{{prompt}}","sys":"Contexto: {{system}}","history":"{{messages}}","t":"{{temperature}}"}""",
                restTextPath = "result.answer") }
            assertTrue(p.problems().isEmpty(), p.problems().toString())
            val r = BrainFactory.create(p, { "k" }).complete(request(user("¿Qué tal?")))
            assertEquals("Hola desde REST", r.message.text())
            val sent = server.takeRequest()
            assertEquals("/api/v3/chat", sent.path)
            val b = body(sent)
            assertEquals("¿Qué tal?", b["q"]!!.jsonPrimitive.content)
            assertEquals("Contexto: Eres Nexus.", b["sys"]!!.jsonPrimitive.content)
            assertEquals("user", b["history"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
            assertNull(b["t"], "una variable sin valor omite la clave")
            assertNull(b["tools"])
        }
    }

    @Test fun `REST personalizado con ruta de respuesta equivocada dice cual y no inventa texto`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"otra":"cosa"}"""))
            val p = profile(server, BrainProtocol.CUSTOM_REST) { it.copy(restRequestTemplate = """{"q":"{{prompt}}"}""", restTextPath = "result.answer") }
            val e = assertFailsWith<LlmException> { BrainFactory.create(p, { "k" }).complete(request(user("x"))) }
            assertEquals(LlmException.Kind.PROTOCOL, e.kind)
            assertTrue("result.answer" in e.message.orEmpty())
        }
    }

    // ---- Validación -------------------------------------------------------------------------------------------

    @Test fun `la validacion exige https o servidor local, sin credenciales en la URL`() {
        fun url(u: String) = BrainProfile.isAllowedUrl(u)
        assertTrue(url("https://api.openai.com/v1")); assertTrue(url("http://127.0.0.1:11434/v1")); assertTrue(url("http://localhost:8080"))
        assertFalse(url("http://api.openai.com/v1")); assertFalse(url("https://usuario:clave@api.x.com/v1"))
        assertFalse(url("https://")); assertFalse(url("ftp://x.com")); assertFalse(url("https://a b.com"))
    }

    @Test fun `el perfil rechaza combinaciones que el proveedor tumbaria, pero no impone topes artificiales`() {
        val base = BrainPresets.byteplus().copy(builtin = false, id = "x1")
        assertTrue(base.copy(thinking = "disabled", reasoningEffort = "high").problems().isNotEmpty())
        assertTrue(base.copy(reasoningEffort = "extreme").problems().isNotEmpty())
        assertTrue(base.copy(auth = BrainAuth(BrainAuthMode.HEADER, "")).problems().isNotEmpty())
        assertTrue(base.copy(extraHeaders = mapOf("X-A" to "a\r\nEvil: 1")).problems().isNotEmpty())
        assertTrue(base.copy(extraBody = "[1,2]").problems().isNotEmpty())
        assertTrue(base.copy(id = "Mayúsculas").problems().isNotEmpty())
        // sin topes inventados: temperatura alta, muchos tokens y gasto sin límite son decisión del usuario
        assertTrue(base.copy(temperature = 1.7, limits = LimitOverrides(maxTokens = 2_000_000, turnUsd = 0.0, dailyUsd = 0.0, maxToolRounds = 0, httpTimeoutSeconds = 0)).problems().isEmpty())
    }

    @Test fun `todas las plantillas de proveedor son validas y listas para poner la clave`() {
        BrainPresets.templates.filter { it.profile.protocol != BrainProtocol.CUSTOM_REST }.forEach {
            assertTrue(it.profile.problems().isEmpty(), "${it.label}: ${it.profile.problems()}")
            assertFalse(it.profile.usable(hasKey = true), "sin modelo no es utilizable: ${it.label}")
        }
        assertTrue(BrainPresets.byteplus().usable(hasKey = true))
        assertFalse(BrainPresets.byteplus().usable(hasKey = false), "BytePlus necesita clave")
    }

    @Test fun `el perfil se guarda y se lee tal cual como JSON (persistencia) y nunca lleva la clave`() {
        val p = BrainPresets.byteplus().copy(limits = LimitOverrides(maxToolRounds = 0, turnUsd = 2.5), reasoningEffort = "medium")
        val json = Json.encodeToString(BrainProfile.serializer(), p)
        assertEquals(p, Json.decodeFromString(BrainProfile.serializer(), json))
        assertFalse("key" in json.lowercase().replace("keyword", ""), "el perfil no debe tener campos de clave: $json")
    }

    // ---- Plantillas y rutas JSON ------------------------------------------------------------------------------

    @Test fun `la plantilla conserva el tipo, omite lo que falta e interpola dentro de textos`() {
        val t = JsonTemplate.parse("""{"prompt":"{{prompt}}","n":"{{count}}","opt":"{{missing}}","texto":"Ratio {{ratio}} fijo","lista":["{{a}}","{{missing}}"],"fijo":5}""")
        val out = JsonTemplate.render(t, mapOf("prompt" to JsonPrimitive("hola \"mundo\""), "count" to JsonPrimitive(3), "ratio" to JsonPrimitive("16:9"), "a" to JsonPrimitive(true))).jsonObject
        assertEquals("hola \"mundo\"", out["prompt"]!!.jsonPrimitive.content)
        assertEquals(3, out["n"]!!.jsonPrimitive.content.toInt())
        assertFalse(out["n"]!!.jsonPrimitive.isString, "un marcador entero conserva el número")
        assertNull(out["opt"])
        assertEquals("Ratio 16:9 fijo", out["texto"]!!.jsonPrimitive.content)
        assertEquals(1, out["lista"]!!.jsonArray.size)
        assertEquals(setOf("prompt", "count", "missing", "ratio", "a"), JsonTemplate.placeholders(t))
    }

    @Test fun `las rutas JSON admiten indices, corchetes y comodin`() {
        val j = Json.parseToJsonElement("""{"data":[{"url":"u1"},{"url":"u2"}],"output":{"video":["v1"]},"x":null}""")
        assertEquals("u1", JsonPaths.string(j, "data.0.url"))
        assertEquals("u2", JsonPaths.string(j, "data[1].url"))
        assertEquals(listOf("u1", "u2"), JsonPaths.strings(j, "data.*.url"))
        assertEquals(listOf("v1"), JsonPaths.strings(j, "output.video"))
        assertNull(JsonPaths.string(j, "x")); assertNull(JsonPaths.string(j, "nope.0"))
    }

    // ---- Presupuesto -------------------------------------------------------------------------------------------

    @Test fun `el envoltorio de gasto conserva el streaming solo si el proveedor lo tiene`() {
        val ledger = LlmBudgetLedger(java.io.File.createTempFile("ledger", ".json").apply { delete() })
        val streaming = ResponsesApiStreamingProvider(HttpTarget("https://x.test/responses"))
        val plain = ResponsesApiProvider(HttpTarget("https://x.test/responses"))
        assertTrue(budgeted(streaming, ledger) { LlmLimits() } is StreamingLlmProvider)
        assertFalse(budgeted(plain, ledger) { LlmLimits() } is StreamingLlmProvider)
        assertNotNull(budgeted(plain, ledger) { LlmLimits() })
    }
}
