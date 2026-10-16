package com.vidadomu.nexus.agent.core.llm

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** Las herramientas del agente funcionan igual con OpenAI-compatible y con Anthropic. */
class OtherLlmProvidersTest {
    private val schema = buildJsonObject { put("type", "object") }
    private val conversation = listOf(
        LlmMessage(Role.USER, listOf(ContentBlock.Text("Estado del upscale"))),
        LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text("Lo consulto."), ContentBlock.ToolUse("t1", "get_ui_state", buildJsonObject { put("order_id", "ord_1") }))),
        LlmMessage(Role.USER, listOf(ContentBlock.ToolResult("t1", buildJsonObject { put("status", "COMPLETED") }))),
    )
    private fun request(model: String) = LlmRequest(model, listOf("Eres Nexus."), conversation, listOf(ToolSpec("get_ui_state", "Estado real", schema)), 1024, 0.2)

    @Test fun `openai compatible envia tools y lee tool_calls`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"c9","type":"function","function":{"name":"get_ui_state","arguments":"{\"order_id\":\"ord_2\"}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":120,"completion_tokens":9}}"""))
            val provider = OpenAiCompatibleProvider(server.url("/v1").toString(), { "sk-test" })
            val response = provider.complete(request("deepseek-chat"))
            val sent = server.takeRequest()
            assertEquals("/v1/chat/completions", sent.path)
            assertEquals("Bearer sk-test", sent.getHeader("Authorization"))
            val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf("system", "user", "assistant", "tool"), messages.map { it["role"]!!.jsonPrimitive.content })
            assertEquals("t1", messages[3]["tool_call_id"]!!.jsonPrimitive.content)
            assertEquals("get_ui_state", messages[2]["tool_calls"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals("function", body["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals(StopReason.TOOL_USE, response.stopReason)
            val use = response.message.toolUses().single()
            assertEquals("ord_2", use.input["order_id"]!!.jsonPrimitive.content)
            assertEquals(Usage(120, 9), response.usage)
        }
    }

    @Test fun `anthropic usa tool_use y tool_result nativos`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"Completado."}],"stop_reason":"end_turn","usage":{"input_tokens":200,"output_tokens":12}}"""))
            val provider = AnthropicProvider({ "sk-ant-test" }, server.url("/").toString())
            val response = provider.complete(request("claude-sonnet-5-5"))
            val sent = server.takeRequest()
            assertEquals("/v1/messages", sent.path)
            assertEquals("sk-ant-test", sent.getHeader("x-api-key"))
            val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            assertEquals("Eres Nexus.", body["system"]!!.jsonPrimitive.content)
            val assistant = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
            assertEquals(listOf("text", "tool_use"), assistant)
            assertEquals("tool_result", body["messages"]!!.jsonArray[2].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("Completado.", response.message.text())
            assertEquals(StopReason.END_TURN, response.stopReason)
        }
    }

    @Test fun `errores de clave y modelo se explican sin secretos`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Incorrect API key provided"}}"""))
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"model not found"}}"""))
            val provider = OpenAiCompatibleProvider(server.url("/v1").toString(), { "sk-secreta" })
            val auth = assertFailsWith<LlmException> { provider.complete(request("x")) }
            assertEquals(LlmException.Kind.AUTH, auth.kind)
            assertTrue("sk-secreta" !in auth.message.orEmpty())
            assertEquals(LlmException.Kind.MODEL_NOT_ALLOWED, assertFailsWith<LlmException> { provider.complete(request("x")) }.kind)
        }
    }

    @Test fun `el presupuesto local admite modelos sin tarifa conocida`() = runTest {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val ledger = LlmBudgetLedger(File(dir, "budget.json"))
        val fake = object : LlmProvider {
            override val id = "openai-compatible"
            override suspend fun complete(request: LlmRequest) = LlmResponse(LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text("ok"))), StopReason.END_TURN, Usage(1_000_000, 0))
        }
        BudgetedLlmProvider(fake, ledger, limits = { LlmLimits(perTurnUsd = 1.0, dailyUsd = 5.0) }).complete(request("deepseek-chat"))
        assertEquals(0.0, ledger.spentToday(), "sin tarifa: no se inventa un importe")
        BudgetedLlmProvider(fake, ledger, customRates = { 0.5 to 2.0 }, limits = { LlmLimits(perTurnUsd = 10.0, dailyUsd = 50.0) }).complete(request("deepseek-chat"))
        assertEquals(0.5, ledger.spentToday(), 1e-9)
        dir.deleteRecursively()
    }
}
