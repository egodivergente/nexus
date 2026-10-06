package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.BedrockProxyProvider
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.ConverseCodec
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.agent.core.llm.LlmMessage
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.llm.StopReason
import com.vidadomu.nexus.agent.core.llm.ToolSpec
import com.vidadomu.nexus.agent.core.log.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class CodecRedactorTest {
    private val req = LlmRequest(
        modelId = "global.anthropic.claude-opus-test",
        system = listOf("sistema"),
        messages = listOf(
            LlmMessage(Role.USER, listOf(ContentBlock.Text("hola"), ContentBlock.Image("png", byteArrayOf(1, 2, 3)))),
            LlmMessage(Role.ASSISTANT, listOf(ContentBlock.ToolUse("t1", "list_models", buildJsonObject { put("modality", "VIDEO") }))),
            LlmMessage(Role.USER, listOf(ContentBlock.ToolResult("t1", buildJsonObject { put("total", 3) }))),
        ),
        tools = listOf(ToolSpec("list_models", "lista", buildJsonObject { put("type", "object") })),
        maxTokens = 1000, temperature = 0.3,
    )

    @Test fun `codifica formato Converse`() {
        val j = ConverseCodec.encodeRequest(req)
        assertEquals("global.anthropic.claude-opus-test", j["modelId"]!!.jsonPrimitive.content)
        assertEquals("sistema", j["system"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val tool = j["toolConfig"]!!.jsonObject["tools"]!!.jsonArray[0].jsonObject["toolSpec"]!!.jsonObject
        assertTrue("json" in tool["inputSchema"]!!.jsonObject)
        val img = j["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[1].jsonObject["image"]!!.jsonObject
        assertEquals("AQID", img["source"]!!.jsonObject["bytes"]!!.jsonPrimitive.content)
        val tr = j["messages"]!!.jsonArray[2].jsonObject["content"]!!.jsonArray[0].jsonObject["toolResult"]!!.jsonObject
        assertEquals("t1", tr["toolUseId"]!!.jsonPrimitive.content)
        assertEquals(1000, j["inferenceConfig"]!!.jsonObject["maxTokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun `decodifica tool_use e ignora bloques desconocidos`() {
        val resp = Json.parseToJsonElement("""{"output":{"message":{"role":"assistant","content":[
            {"reasoningContent":{"reasoningText":{"text":"..."}}},{"text":"Voy a mirar"},
            {"toolUse":{"toolUseId":"a","name":"search_models","input":{"query":"kling"}}}]}},
            "stopReason":"tool_use","usage":{"inputTokens":12,"outputTokens":7}}""").jsonObject
        val r = ConverseCodec.decodeResponse(resp)
        assertEquals(StopReason.TOOL_USE, r.stopReason)
        assertEquals("search_models", r.message.toolUses().single().name)
        assertEquals("Voy a mirar", r.message.text())
        assertEquals(12, r.usage.inputTokens)
    }

    @Test fun `proxy usa token de dispositivo y mapea errores`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"output":{"message":{"role":"assistant","content":[{"text":"ok"}]}},"stopReason":"end_turn","usage":{"inputTokens":1,"outputTokens":1}}"""))
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"code":"budget","message":"Presupuesto"}}"""))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":{"code":"model","message":"Modelo no permitido por el proxy"}}"""))
        server.start()
        try {
            val p = BedrockProxyProvider(server.url("/").toString(), { "dev-token-123" })
            assertEquals("ok", p.complete(req).message.text())
            val recorded = server.takeRequest()
            assertEquals("/v1/converse", recorded.path)
            assertEquals("Bearer dev-token-123", recorded.getHeader("Authorization"))
            assertTrue(recorded.body.readUtf8().contains("toolConfig"))
            assertEquals(LlmException.Kind.BUDGET, assertFailsWith<LlmException> { p.complete(req) }.kind)
            val e = assertFailsWith<LlmException> { p.complete(req) }
            assertEquals(LlmException.Kind.MODEL_NOT_ALLOWED, e.kind)
            assertEquals("Modelo no permitido por el proxy", e.message)
        } finally { server.shutdown() }
    }

    @Test fun `red caida es NETWORK`() = runTest {
        val p = BedrockProxyProvider("http://127.0.0.1:9", { "t" })
        assertEquals(LlmException.Kind.NETWORK, assertFailsWith<LlmException> { p.complete(req) }.kind)
    }

    @Test fun `redactor oculta secretos`() {
        val raw = "key AKIAABCDEFGHIJKLMNOP secret aws_secret_access_key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY Authorization: Bearer abc.def-123 x-api-key: sk-live-999999"
        val out = Redactor.redact(raw)
        assertFalse(out.contains("ABCDEFGHIJKLMNOP"))
        assertFalse(out.contains("wJalrXUtnFEMI"))
        assertFalse(out.contains("abc.def-123"))
        assertFalse(out.contains("sk-live-999999"))
        assertTrue(out.contains("AKIA"))
    }
}
