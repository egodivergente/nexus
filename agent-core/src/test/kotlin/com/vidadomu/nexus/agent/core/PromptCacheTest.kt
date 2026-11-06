package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.AwsCredentials
import com.vidadomu.nexus.agent.core.llm.BedrockDirectProvider
import com.vidadomu.nexus.agent.core.llm.BudgetedLlmProvider
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.llm.ConverseCodec
import com.vidadomu.nexus.agent.core.llm.LlmBudgetLedger
import com.vidadomu.nexus.agent.core.llm.LlmLimits
import com.vidadomu.nexus.agent.core.llm.LlmMessage
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import com.vidadomu.nexus.agent.core.llm.LlmRequest
import com.vidadomu.nexus.agent.core.llm.LlmResponse
import com.vidadomu.nexus.agent.core.llm.Role
import com.vidadomu.nexus.agent.core.llm.StopReason
import com.vidadomu.nexus.agent.core.llm.ToolSpec
import com.vidadomu.nexus.agent.core.llm.Usage
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

class PromptCacheTest {
    private val req = LlmRequest(
        modelId = "us.anthropic.claude-opus-4-6-v1",
        system = listOf("base", "reglas", "evidencia variable"),
        messages = listOf(
            LlmMessage(Role.USER, listOf(ContentBlock.Text("hola"))),
            LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text("dime"))),
            LlmMessage(Role.USER, listOf(ContentBlock.Text("sigue"))),
        ),
        tools = listOf(ToolSpec("list_models", "lista", buildJsonObject { put("type", "object") })),
        maxTokens = 100, cacheableSystemBlocks = 2,
    )
    private val ok = """{"output":{"message":{"role":"assistant","content":[{"text":"ok"}]}},"stopReason":"end_turn","usage":{"inputTokens":5,"outputTokens":1,"cacheReadInputTokens":900,"cacheWriteInputTokens":40}}"""

    @Test fun `con cache marca herramientas, bloques fijos del sistema y ultimo mensaje`() {
        val j = ConverseCodec.encodeRequest(req, cache = true)
        val system = j["system"]!!.jsonArray
        assertEquals(4, system.size)
        assertTrue("cachePoint" in system[2].jsonObject, "el punto va tras los bloques fijos, antes de la evidencia variable")
        assertTrue("cachePoint" in j["toolConfig"]!!.jsonObject["tools"]!!.jsonArray.last().jsonObject)
        val messages = j["messages"]!!.jsonArray
        assertTrue("cachePoint" in messages.last().jsonObject["content"]!!.jsonArray.last().jsonObject)
        assertFalse(messages.first().jsonObject["content"]!!.jsonArray.any { "cachePoint" in it.jsonObject })
        assertFalse("cachePoint" in ConverseCodec.encodeRequest(req).toString(), "sin cache no se marca nada")
    }

    @Test fun `solo los Claude que admiten cache la usan`() {
        for (m in listOf("us.anthropic.claude-opus-4-6-v1", "us.anthropic.claude-sonnet-5-5", "global.anthropic.claude-haiku-4-5-20251001-v1:0", "anthropic.claude-3-7-sonnet-20250219-v1:0"))
            assertTrue(ConverseCodec.supportsPromptCache(m), m)
        for (m in listOf("amazon.nova-pro-v1:0", "meta.llama3-70b", "global.anthropic.claude-opus-test", "anthropic.claude-3-haiku-20240307-v1:0"))
            assertFalse(ConverseCodec.supportsPromptCache(m), m)
    }

    @Test fun `lee los tokens de cache del uso`() {
        val r = ConverseCodec.decodeResponse(Json.parseToJsonElement(ok).jsonObject)
        assertEquals(5, r.usage.inputTokens)
        assertEquals(900, r.usage.cacheReadTokens)
        assertEquals(40, r.usage.cacheWriteTokens)
        assertEquals(945, r.usage.promptTokens)
    }

    @Test fun `si Bedrock rechaza la cache se repite sin ella y el modelo deja de usarla`() = runTest {
        val bodies = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val body = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            bodies += body
            val rejected = "cachePoint" in body
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (rejected) 400 else 200).message("x")
                .body((if (rejected) """{"message":"cachePoint is not supported"}""" else ok).toResponseBody("application/json".toMediaType())).build()
        }).build()
        val p = BedrockDirectProvider("us-east-1", { AwsCredentials("AKIDEXAMPLE", "secret") }, http,
            now = { ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, ZoneOffset.UTC) })
        val model = "us.anthropic.claude-sonnet-4-6-rechaza"
        assertEquals("ok", p.complete(req.copy(modelId = model)).message.text())
        assertEquals(2, bodies.size)
        assertEquals("ok", p.complete(req.copy(modelId = model)).message.text())
        assertEquals(3, bodies.size, "tras el rechazo ya no se intenta con cache")
    }

    @Test fun `sin documentos no cuenta tokens antes de cada paso y liquida con tarifas de cache`() = runTest {
        var counts = 0
        val backend = object : LlmProvider {
            override val id = "bedrock-direct"
            override suspend fun countInputTokens(request: LlmRequest): Int { counts++; return 100 }
            override suspend fun complete(request: LlmRequest) =
                LlmResponse(LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text("ok"))), StopReason.END_TURN, Usage(1000, 100, cacheReadTokens = 10_000, cacheWriteTokens = 2000))
        }
        val ledger = LlmBudgetLedger(Files.createTempDirectory("cache-budget").resolve("usage.json").toFile())
        val provider = BudgetedLlmProvider(backend, ledger) { LlmLimits() }
        provider.complete(req.copy(turnId = "t"))
        assertEquals(0, counts, "dentro de los límites se reserva con la cota por bytes")
        // Opus 4.6: 6 $/M entrada, 30 $/M salida. 1000 + 2000×1,25 + 10000×0,1 = 4500 tokens de entrada equivalentes.
        assertEquals(4500 * 6.0 / 1_000_000 + 100 * 30.0 / 1_000_000, ledger.spentToday(), 1e-9)
    }
}
